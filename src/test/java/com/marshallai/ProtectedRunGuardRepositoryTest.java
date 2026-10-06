package com.marshallai;

import com.marshallai.governance.exposure.ProtectedPeriodType;
import com.marshallai.governance.exposure.ProtectedRunGuardRepository;
import com.marshallai.governance.exposure.ProtectedRunState;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ProtectedRunGuardRepositoryTest {

    private static PostgreSQLContainer postgres;

    private static DataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static ProtectedRunGuardRepository repository;

    @BeforeAll
    static void startPostgresqlAndMigrate() {

        postgres =
                new PostgreSQLContainer(
                        "postgres:16-alpine"
                );

        postgres.start();

        DriverManagerDataSource driverManagerDataSource =
                new DriverManagerDataSource();

        driverManagerDataSource.setDriverClassName(
                "org.postgresql.Driver"
        );

        driverManagerDataSource.setUrl(
                postgres.getJdbcUrl()
        );

        driverManagerDataSource.setUsername(
                postgres.getUsername()
        );

        driverManagerDataSource.setPassword(
                postgres.getPassword()
        );

        dataSource =
                driverManagerDataSource;

        Flyway.configure()
                .dataSource(dataSource)
                .locations(
                        "classpath:db/migration"
                )
                .load()
                .migrate();

        jdbcTemplate =
                new JdbcTemplate(
                        dataSource
                );

        DataSourceTransactionManager transactionManager =
                new DataSourceTransactionManager(
                        dataSource
                );

        repository =
                new ProtectedRunGuardRepository(
                        jdbcTemplate,
                        transactionManager
                );
    }

    @AfterAll
    static void stopPostgresql() {

        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void reservationAndInitialLedgerEventCommitTogether() {

        long guardId =
                repository.createReservation(
                        "strategy-reservation-v1",
                        "validation-reservation-v1",
                        ProtectedPeriodType.VALIDATION,
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 6, 30),
                        Instant.parse(
                                "2026-01-01T12:00:00Z"
                        )
                );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                guard.state()
        );

        assertEquals(
                ProtectedRunState.RESERVED,
                ledger.state()
        );

        assertEquals(
                guard.state(),
                ledger.state(),
                "Guard and ledger must agree"
        );

        assertNull(
                guard.runId()
        );

        assertNull(
                guard.instanceId()
        );

        assertNotNull(
                ledger.eventHash()
        );

        assertEquals(
                64,
                ledger.eventHash().length(),
                "SHA-256 ledger hash should contain 64 hexadecimal characters"
        );

        Integer eventCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                        """,
                        Integer.class,
                        guardId
                );

        assertEquals(
                1,
                eventCount
        );
    }

    @Test
    void reservedTargetIsAcquiredAtomicallyOnlyOnce() {

        long guardId =
                repository.createReservation(
                        "strategy-acquire-v1",
                        "validation-acquire-v1",
                        ProtectedPeriodType.VALIDATION,
                        LocalDate.of(2025, 7, 1),
                        LocalDate.of(2025, 12, 31),
                        Instant.parse(
                                "2026-01-02T12:00:00Z"
                        )
                );

        UUID firstRunId =
                UUID.randomUUID();

        UUID firstInstanceId =
                UUID.randomUUID();

        ProtectedRunGuardRepository.AcquisitionResult first =
                repository.acquireReservedTarget(
                        guardId,
                        firstRunId,
                        firstInstanceId,
                        "configuration-hash-v1",
                        Instant.parse(
                                "2026-01-02T12:01:00Z"
                        )
                );

        assertTrue(
                first.acquired()
        );

        assertEquals(
                guardId,
                first.guardId()
        );

        assertEquals(
                firstRunId,
                first.runId()
        );

        assertEquals(
                firstInstanceId,
                first.instanceId()
        );

        assertNull(
                first.refusalReason()
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        assertEquals(
                ProtectedRunState.RUNNING_UNEXPOSED,
                guard.state()
        );

        assertEquals(
                ProtectedRunState.RUNNING_UNEXPOSED,
                ledger.state()
        );

        assertEquals(
                guard.state(),
                ledger.state()
        );

        assertEquals(
                firstRunId,
                guard.runId()
        );

        assertEquals(
                firstInstanceId,
                guard.instanceId()
        );

        assertEquals(
                "configuration-hash-v1",
                guard.configurationHash()
        );

        /*
         * A second acquisition must not get another look
         * at the same protected target.
         */
        ProtectedRunGuardRepository.AcquisitionResult second =
                repository.acquireReservedTarget(
                        guardId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "configuration-hash-v2",
                        Instant.parse(
                                "2026-01-02T12:02:00Z"
                        )
                );

        assertFalse(
                second.acquired()
        );

        assertEquals(
                "TARGET_NOT_RESERVED_OR_GOVERNANCE_INTEGRITY_MISMATCH",
                second.refusalReason()
        );

        /*
         * Nothing from the refused attempt may replace
         * the ownership of the successful attempt.
         */
        ProtectedRunGuardRepository.GuardSnapshot afterRefusal =
                repository.requireGuard(
                        guardId
                );

        assertEquals(
                ProtectedRunState.RUNNING_UNEXPOSED,
                afterRefusal.state()
        );

        assertEquals(
                firstRunId,
                afterRefusal.runId()
        );

        assertEquals(
                firstInstanceId,
                afterRefusal.instanceId()
        );

        Integer eventCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                        """,
                        Integer.class,
                        guardId
                );

        assertEquals(
                2,
                eventCount,
                "The refused second acquisition must not append another state event"
        );
    }

    @Test
    void duplicateReservationForSameTargetIsRefused() {

        repository.createReservation(
                "strategy-unique-v1",
                "holdout-unique-v1",
                ProtectedPeriodType.HOLDOUT,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                Instant.parse(
                        "2026-01-03T12:00:00Z"
                )
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        repository.createReservation(
                                "strategy-unique-v1",
                                "holdout-unique-v1",
                                ProtectedPeriodType.HOLDOUT,
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 6, 30),
                                Instant.parse(
                                        "2026-01-03T12:01:00Z"
                                )
                        )
        );

        Integer guardCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM protected_run_guard
                        WHERE strategy_version = ?
                          AND protected_period_id = ?
                        """,
                        Integer.class,
                        "strategy-unique-v1",
                        "holdout-unique-v1"
                );

        assertEquals(
                1,
                guardCount,
                "Only one reservation may exist for the governed target"
        );

        Integer ledgerCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM protected_run_event_ledger AS ledger
                        JOIN protected_run_guard AS guard
                          ON guard.id = ledger.guard_id
                        WHERE guard.strategy_version = ?
                          AND guard.protected_period_id = ?
                        """,
                        Integer.class,
                        "strategy-unique-v1",
                        "holdout-unique-v1"
                );

        assertEquals(
                1,
                ledgerCount,
                "Failed duplicate reservation must not create a ledger event"
        );
    }

    @Test
    void failedLedgerWriteRollsBackGuardAcquisition() {

        long guardId =
                repository.createReservation(
                        "strategy-rollback-v1",
                        "validation-rollback-v1",
                        ProtectedPeriodType.VALIDATION,
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2024, 12, 31),
                        Instant.parse(
                                "2026-01-04T12:00:00Z"
                        )
                );

        installForcedLedgerFailureTrigger();

        try {

            assertThrows(
                    DataAccessException.class,
                    () ->
                            repository.acquireReservedTarget(
                                    guardId,
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    "rollback-configuration-hash",
                                    Instant.parse(
                                            "2026-01-04T12:01:00Z"
                                    )
                            )
            );

        } finally {

            removeForcedLedgerFailureTrigger();
        }

        /*
         * The UPDATE happened before the ledger INSERT inside
         * the repository transaction.
         *
         * Because the ledger insert failed, the guard update
         * must also have rolled back.
         */
        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                guard.state()
        );

        assertEquals(
                ProtectedRunState.RESERVED,
                ledger.state()
        );

        assertNull(
                guard.runId()
        );

        assertNull(
                guard.instanceId()
        );

        assertNull(
                guard.configurationHash()
        );

        Integer eventCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                        """,
                        Integer.class,
                        guardId
                );

        assertEquals(
                1,
                eventCount,
                "Failed acquisition must leave only the original RESERVED ledger event"
        );
    }

    @Test
    void databaseRefusesGuardLedgerMismatchAtCommit()
            throws Exception {

        long guardId =
                repository.createReservation(
                        "strategy-integrity-v1",
                        "validation-integrity-v1",
                        ProtectedPeriodType.VALIDATION,
                        LocalDate.of(2023, 1, 1),
                        LocalDate.of(2023, 12, 31),
                        Instant.parse(
                                "2026-01-05T12:00:00Z"
                        )
                );

        UUID runId =
                UUID.randomUUID();

        UUID instanceId =
                UUID.randomUUID();

        /*
         * Simulate a database client attempting to move only
         * the mutable guard without appending its ledger event.
         *
         * The state transition itself is otherwise legal, so
         * the deferred consistency trigger is what must reject
         * the COMMIT.
         */
        try (Connection connection =
                     dataSource.getConnection()) {

            connection.setAutoCommit(
                    false
            );

            try {

                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     """
                                     UPDATE protected_run_guard
                                     SET
                                         state = 'RUNNING_UNEXPOSED',
                                         run_id = ?,
                                         instance_id = ?,
                                         configuration_hash = ?
                                     WHERE id = ?
                                     """
                             )) {

                    statement.setObject(
                            1,
                            runId
                    );

                    statement.setObject(
                            2,
                            instanceId
                    );

                    statement.setString(
                            3,
                            "direct-db-edit-hash"
                    );

                    statement.setLong(
                            4,
                            guardId
                    );

                    assertEquals(
                            1,
                            statement.executeUpdate()
                    );
                }

                assertThrows(
                        SQLException.class,
                        connection::commit,
                        "Commit must fail when the guard and latest ledger state disagree"
                );

            } finally {

                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    /*
                     * PostgreSQL may already consider the
                     * failed transaction aborted.
                     */
                }
            }
        }

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                guard.state()
        );

        assertEquals(
                ProtectedRunState.RESERVED,
                ledger.state()
        );

        assertEquals(
                guard.state(),
                ledger.state()
        );
    }

    private static void installForcedLedgerFailureTrigger() {

        jdbcTemplate.execute(
                """
                CREATE OR REPLACE FUNCTION
                    test_fail_running_ledger_insert()
                RETURNS TRIGGER
                LANGUAGE plpgsql
                AS
                $$
                BEGIN

                    IF NEW.new_state = 'RUNNING_UNEXPOSED' THEN
                        RAISE EXCEPTION
                            'Forced ledger failure for transaction rollback test';
                    END IF;

                    RETURN NEW;

                END;
                $$
                """
        );

        jdbcTemplate.execute(
                """
                CREATE TRIGGER
                    test_fail_running_ledger_insert_trigger
                BEFORE INSERT
                ON protected_run_event_ledger
                FOR EACH ROW
                EXECUTE FUNCTION
                    test_fail_running_ledger_insert()
                """
        );
    }

    private static void removeForcedLedgerFailureTrigger() {

        jdbcTemplate.execute(
                """
                DROP TRIGGER IF EXISTS
                    test_fail_running_ledger_insert_trigger
                ON protected_run_event_ledger
                """
        );

        jdbcTemplate.execute(
                """
                DROP FUNCTION IF EXISTS
                    test_fail_running_ledger_insert()
                """
        );
    }
}