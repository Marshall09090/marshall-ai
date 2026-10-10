package com.marshallai.governance.exposure;

import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Holds a PostgreSQL session advisory lock for the lifetime
 * of governed mode.
 *
 * A dedicated JDBC connection is intentionally retained while
 * the lock is held because PostgreSQL session advisory locks
 * belong to the database session that acquired them.
 */
@Component
public class GovernedModeAdvisoryLock {

    public static final String
            GOVERNED_INSTANCE_ALREADY_ACTIVE =
            "GOVERNED_INSTANCE_ALREADY_ACTIVE";

    /*
     * Stable application-specific advisory-lock key.
     *
     * Every MarshallAI application instance attempting governed
     * mode uses this same PostgreSQL advisory-lock key.
     */
    private static final long
            GOVERNED_MODE_LOCK_KEY =
            4_809_721_135_771L;

    private final DataSource dataSource;

    private Connection lockConnection;

    public GovernedModeAdvisoryLock(
            DataSource dataSource) {

        if (dataSource == null) {

            throw new IllegalArgumentException(
                    "DataSource cannot be null"
            );
        }

        this.dataSource =
                dataSource;
    }

    /**
     * Attempts to acquire exclusive governed mode.
     *
     * The PostgreSQL advisory lock is session-scoped, so the
     * exact connection that acquires the lock remains open
     * until release().
     */
    public synchronized LockResult acquire() {

        /*
         * This application instance already owns the lock.
         */
        if (lockConnection != null) {

            return LockResult.granted();
        }

        Connection connection = null;

        try {

            connection =
                    dataSource.getConnection();

            boolean acquired;

            try (PreparedStatement statement =
                         connection.prepareStatement(
                                 """
                                 SELECT pg_try_advisory_lock(?)
                                 """
                         )) {

                statement.setLong(
                        1,
                        GOVERNED_MODE_LOCK_KEY
                );

                try (ResultSet resultSet =
                             statement.executeQuery()) {

                    if (!resultSet.next()) {

                        throw new IllegalStateException(
                                "PostgreSQL advisory lock "
                                        + "returned no result"
                        );
                    }

                    acquired =
                            resultSet.getBoolean(
                                    1
                            );
                }
            }

            if (!acquired) {

                connection.close();

                return LockResult.refused(
                        GOVERNED_INSTANCE_ALREADY_ACTIVE
                );
            }

            /*
             * Keep this exact database session alive.
             *
             * Returning the connection to a pool while a
             * session-level advisory lock is held could allow
             * another borrower to inherit that lock.
             */
            lockConnection =
                    connection;

            return LockResult.granted();

        } catch (SQLException exception) {

            closeQuietly(
                    connection
            );

            throw new IllegalStateException(
                    "Unable to acquire governed-mode "
                            + "PostgreSQL advisory lock",
                    exception
            );
        }
    }

    public synchronized boolean isHeld() {

        return lockConnection != null;
    }

    /**
     * Releases the advisory lock and closes the owning
     * PostgreSQL session.
     */
    @PreDestroy
    public synchronized void release() {

        if (lockConnection == null) {

            return;
        }

        Connection connection =
                lockConnection;

        /*
         * Clear local ownership first so this object cannot
         * continue claiming the lock after cleanup begins.
         */
        lockConnection =
                null;

        SQLException failure = null;

        try {

            try (PreparedStatement statement =
                         connection.prepareStatement(
                                 """
                                 SELECT pg_advisory_unlock(?)
                                 """
                         )) {

                statement.setLong(
                        1,
                        GOVERNED_MODE_LOCK_KEY
                );

                statement.executeQuery();
            }

        } catch (SQLException exception) {

            failure =
                    exception;

        } finally {

            try {

                connection.close();

            } catch (SQLException closeException) {

                if (failure == null) {

                    failure =
                            closeException;

                } else {

                    failure.addSuppressed(
                            closeException
                    );
                }
            }
        }

        if (failure != null) {

            throw new IllegalStateException(
                    "Unable to release governed-mode "
                            + "PostgreSQL advisory lock",
                    failure
            );
        }
    }

    private static void closeQuietly(
            Connection connection) {

        if (connection == null) {

            return;
        }

        try {

            connection.close();

        } catch (SQLException ignored) {

            /*
             * Preserve the original database failure.
             */
        }
    }

    public record LockResult(
            boolean acquired,
            String refusalReason) {

        /*
         * Named "granted" rather than "acquired" because
         * Java records automatically generate acquired()
         * as the boolean component accessor.
         */
        public static LockResult granted() {

            return new LockResult(
                    true,
                    null
            );
        }

        public static LockResult refused(
                String reason) {

            if (reason == null
                    || reason.isBlank()) {

                throw new IllegalArgumentException(
                        "Refusal reason cannot be blank"
                );
            }

            return new LockResult(
                    false,
                    reason
            );
        }
    }
}