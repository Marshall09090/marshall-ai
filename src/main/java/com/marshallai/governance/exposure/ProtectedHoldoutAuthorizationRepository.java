package com.marshallai.governance.exposure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ProtectedHoldoutAuthorizationRepository {

    private final JdbcTemplate jdbcTemplate;

    private final TransactionTemplate transactionTemplate;

    public ProtectedHoldoutAuthorizationRepository(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {

        if (jdbcTemplate == null) {
            throw new IllegalArgumentException(
                    "JdbcTemplate cannot be null"
            );
        }

        if (transactionManager == null) {
            throw new IllegalArgumentException(
                    "Transaction manager cannot be null"
            );
        }

        this.jdbcTemplate = jdbcTemplate;

        this.transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );
    }

    /**
     * Grants explicit authorization to read a protected
     * holdout target.
     *
     * Authorization is separate from protected-run
     * reservation and acquisition.
     *
     * The guard must exist, represent a HOLDOUT target,
     * and remain RESERVED.
     */
    public AuthorizationSnapshot grantExplicitAuthorization(
            long guardId,
            Instant authorizationTimestamp) {

        requirePositiveGuardId(
                guardId
        );

        if (authorizationTimestamp == null) {
            throw new IllegalArgumentException(
                    "Authorization timestamp cannot be null"
            );
        }

        AuthorizationSnapshot result =
                transactionTemplate.execute(status -> {

                    /*
                     * Lock the protected target while creating
                     * its separate authorization record.
                     */
                    List<GuardTarget> targets =
                            jdbcTemplate.query(
                                    """
                                    SELECT
                                        strategy_version,
                                        protected_period_id,
                                        protected_period_type,
                                        state
                                    FROM protected_run_guard
                                    WHERE id = ?
                                    FOR UPDATE
                                    """,
                                    (resultSet, rowNumber) ->
                                            new GuardTarget(
                                                    resultSet.getString(
                                                            "strategy_version"
                                                    ),
                                                    resultSet.getString(
                                                            "protected_period_id"
                                                    ),
                                                    ProtectedPeriodType.valueOf(
                                                            resultSet.getString(
                                                                    "protected_period_type"
                                                            )
                                                    ),
                                                    ProtectedRunState.valueOf(
                                                            resultSet.getString(
                                                                    "state"
                                                            )
                                                    )
                                            ),
                                    guardId
                            );

                    if (targets.size() != 1) {

                        throw new IllegalStateException(
                                "Protected holdout target does not exist: "
                                        + guardId
                        );
                    }

                    GuardTarget target =
                            targets.getFirst();

                    /*
                     * A validation target cannot receive
                     * explicit holdout authorization.
                     */
                    if (target.periodType()
                            != ProtectedPeriodType.HOLDOUT) {

                        throw new IllegalStateException(
                                "Explicit holdout authorization requires "
                                        + "a HOLDOUT protected target"
                        );
                    }

                    /*
                     * Authorization must be granted before
                     * the protected holdout is acquired.
                     */
                    if (target.state()
                            != ProtectedRunState.RESERVED) {

                        throw new IllegalStateException(
                                "Holdout authorization requires "
                                        + "a RESERVED protected target"
                        );
                    }

                    /*
                     * The database uniqueness constraint
                     * prevents duplicate authorization.
                     */
                    int inserted =
                            jdbcTemplate.update(
                                    """
                                    INSERT INTO
                                        protected_holdout_authorization (
                                            guard_id,
                                            strategy_version,
                                            protected_period_id,
                                            authorization_timestamp
                                        )
                                    VALUES (?, ?, ?, ?)
                                    ON CONFLICT (guard_id)
                                    DO NOTHING
                                    """,
                                    guardId,
                                    target.strategyVersion(),
                                    target.protectedPeriodId(),
                                    Timestamp.from(
                                            authorizationTimestamp
                                    )
                            );

                    if (inserted == 0) {

                        throw new IllegalStateException(
                                "Explicit holdout authorization "
                                        + "already exists for guard: "
                                        + guardId
                        );
                    }

                    if (inserted != 1) {

                        throw new IllegalStateException(
                                "Holdout authorization insert affected "
                                        + inserted
                                        + " rows"
                        );
                    }

                    /*
                     * Read the actual persisted record.
                     *
                     * This confirms PostgreSQL accepted
                     * the separate authorization.
                     */
                    return findAuthorization(
                            guardId
                    ).orElseThrow(
                            () -> new IllegalStateException(
                                    "Persisted holdout authorization "
                                            + "could not be retrieved"
                            )
                    );
                });

        if (result == null) {

            throw new IllegalStateException(
                    "Holdout authorization transaction "
                            + "returned no result"
            );
        }

        return result;
    }

    /**
     * Grants authorization using the current timestamp.
     */
    public AuthorizationSnapshot grantExplicitAuthorization(
            long guardId) {

        return grantExplicitAuthorization(
                guardId,
                Instant.now()
        );
    }

    /**
     * Retrieves the separate holdout authorization record.
     *
     * IMPORTANT:
     *
     * PostgreSQL reserves AUTHORIZATION as a SQL keyword.
     *
     * We therefore use "ha" as the table alias.
     *
     * The query also verifies that the authorization
     * matches the underlying protected HOLDOUT guard.
     */
    public Optional<AuthorizationSnapshot> findAuthorization(
            long guardId) {

        requirePositiveGuardId(
                guardId
        );

        List<AuthorizationSnapshot> authorizations =
                jdbcTemplate.query(
                        """
                        SELECT
                            ha.id,
                            ha.guard_id,
                            ha.strategy_version,
                            ha.protected_period_id,
                            ha.authorization_timestamp
                        FROM protected_holdout_authorization AS ha
                        JOIN protected_run_guard AS guard
                          ON guard.id = ha.guard_id
                        WHERE ha.guard_id = ?
                          AND guard.protected_period_type = 'HOLDOUT'
                          AND ha.strategy_version =
                              guard.strategy_version
                          AND ha.protected_period_id =
                              guard.protected_period_id
                        """,
                        (resultSet, rowNumber) ->
                                new AuthorizationSnapshot(
                                        resultSet.getLong(
                                                "id"
                                        ),
                                        resultSet.getLong(
                                                "guard_id"
                                        ),
                                        resultSet.getString(
                                                "strategy_version"
                                        ),
                                        resultSet.getString(
                                                "protected_period_id"
                                        ),
                                        resultSet.getObject(
                                                "authorization_timestamp",
                                                OffsetDateTime.class
                                        ).toInstant()
                                ),
                        guardId
                );

        if (authorizations.size() > 1) {

            throw new IllegalStateException(
                    "Multiple holdout authorizations "
                            + "exist for guard: "
                            + guardId
            );
        }

        return authorizations.stream()
                .findFirst();
    }

    /**
     * Checks whether the exact protected holdout target
     * has its own durable authorization record.
     */
    public boolean isExplicitlyAuthorized(
            long guardId) {

        return findAuthorization(
                guardId
        ).isPresent();
    }

    private static void requirePositiveGuardId(
            long guardId) {

        if (guardId <= 0) {

            throw new IllegalArgumentException(
                    "Guard ID must be greater than zero"
            );
        }
    }

    private record GuardTarget(
            String strategyVersion,
            String protectedPeriodId,
            ProtectedPeriodType periodType,
            ProtectedRunState state) {
    }

    public record AuthorizationSnapshot(
            long authorizationId,
            long guardId,
            String strategyVersion,
            String protectedPeriodId,
            Instant authorizationTimestamp) {
    }
}