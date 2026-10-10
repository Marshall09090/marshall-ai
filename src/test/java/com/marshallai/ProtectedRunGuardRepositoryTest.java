package com.marshallai;

import com.marshallai.market.MarketDataProviderType;
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
import java.sql.Timestamp;
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
                ledger.state()
        );

        assertNull(
                guard.runId()
        );

        assertNull(
                guard.instanceId()
        );

        assertNull(
                ledger.governedVerdict()
        );

        assertNotNull(
                ledger.eventHash()
        );

        assertEquals(
                64,
                ledger.eventHash().length()
        );

        Integer eventCount =
                countLedgerEvents(
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
                        MarketDataProviderType.REAL,
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

        assertNull(
                ledger.governedVerdict()
        );

        ProtectedRunGuardRepository.AcquisitionResult second =
                repository.acquireReservedTarget(
                        guardId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "configuration-hash-v2",
                        MarketDataProviderType.REAL,
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

        assertEquals(
                2,
                countLedgerEvents(
                        guardId
                )
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
                guardCount
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

        installForcedRunningLedgerFailureTrigger();

        try {

            assertThrows(
                    DataAccessException.class,
                    () ->
                            repository.acquireReservedTarget(
                                    guardId,
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    "rollback-configuration-hash",
                                    MarketDataProviderType.REAL,
                                    Instant.parse(
                                            "2026-01-04T12:01:00Z"
                                    )
                            )
            );

        } finally {

            removeForcedRunningLedgerFailureTrigger();
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

        assertNull(
                guard.runId()
        );

        assertNull(
                guard.instanceId()
        );

        assertNull(
                guard.configurationHash()
        );

        assertEquals(
                1,
                countLedgerEvents(
                        guardId
                )
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
                                         configuration_hash = ?,
                                         data_source_type = ?
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

                    statement.setString(
                            4,
                            MarketDataProviderType.REAL.name()
                    );

                    statement.setLong(
                            5,
                            guardId
                    );

                    assertEquals(
                            1,
                            statement.executeUpdate()
                    );
                }

                assertThrows(
                        SQLException.class,
                        connection::commit
                );

            } finally {

                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // PostgreSQL may already consider the transaction aborted.
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


    @Test
    void exposedTransitionCommitsGuardFingerprintAndLedgerTogether() {

        ExposedFixture fixture =
                createExposedFixture(
                        "strategy-exposure-v1",
                        "validation-exposure-v1",
                        2022
                );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        fixture.guardId()
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        fixture.guardId()
                );

        assertEquals(
                ProtectedRunState.EXPOSED,
                guard.state()
        );

        assertEquals(
                ProtectedRunState.EXPOSED,
                ledger.state()
        );

        assertEquals(
                fixture.runId(),
                guard.runId()
        );

        assertEquals(
                fixture.instanceId(),
                guard.instanceId()
        );

        assertEquals(
                fixture.configurationHash(),
                guard.configurationHash()
        );

        assertEquals(
                fixture.marketDataFingerprint(),
                guard.marketDataFingerprint()
        );

        assertNull(
                ledger.governedVerdict()
        );

        assertEquals(
                3,
                countLedgerEvents(
                        fixture.guardId()
                )
        );
    }

    @Test
    void exposureRequiresSameProtectedRunOwnership() {

        long guardId =
                repository.createReservation(
                        "strategy-ownership-v1",
                        "validation-ownership-v1",
                        ProtectedPeriodType.VALIDATION,
                        LocalDate.of(2021, 1, 1),
                        LocalDate.of(2021, 12, 31),
                        Instant.parse(
                                "2026-01-07T12:00:00Z"
                        )
                );

        UUID runId =
                UUID.randomUUID();

        UUID instanceId =
                UUID.randomUUID();

        String configurationHash =
                "configuration-hash-ownership-v1";

        assertTrue(
                repository.acquireReservedTarget(
                        guardId,
                        runId,
                        instanceId,
                        configurationHash,
                        MarketDataProviderType.REAL,
                        Instant.parse(
                                "2026-01-07T12:01:00Z"
                        )
                ).acquired()
        );

        ProtectedRunGuardRepository.ExposureResult wrongRun =
                repository.persistExposed(
                        guardId,
                        UUID.randomUUID(),
                        instanceId,
                        configurationHash,
                        "wrong-run-data-fingerprint",
                        Instant.parse(
                                "2026-01-07T12:02:00Z"
                        )
                );

        assertFalse(
                wrongRun.exposed()
        );

        assertEquals(
                "PROTECTED_RUN_OWNERSHIP_MISMATCH",
                wrongRun.refusalReason()
        );

        ProtectedRunGuardRepository.ExposureResult wrongInstance =
                repository.persistExposed(
                        guardId,
                        runId,
                        UUID.randomUUID(),
                        configurationHash,
                        "wrong-instance-data-fingerprint",
                        Instant.parse(
                                "2026-01-07T12:03:00Z"
                        )
                );

        assertFalse(
                wrongInstance.exposed()
        );

        assertEquals(
                "PROTECTED_RUN_OWNERSHIP_MISMATCH",
                wrongInstance.refusalReason()
        );

        ProtectedRunGuardRepository.ExposureResult wrongConfiguration =
                repository.persistExposed(
                        guardId,
                        runId,
                        instanceId,
                        "different-configuration-hash",
                        "wrong-config-data-fingerprint",
                        Instant.parse(
                                "2026-01-07T12:04:00Z"
                        )
                );

        assertFalse(
                wrongConfiguration.exposed()
        );

        assertEquals(
                "PROTECTED_RUN_OWNERSHIP_MISMATCH",
                wrongConfiguration.refusalReason()
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        assertEquals(
                ProtectedRunState.RUNNING_UNEXPOSED,
                guard.state()
        );

        assertNull(
                guard.marketDataFingerprint()
        );

        assertEquals(
                2,
                countLedgerEvents(
                        guardId
                )
        );
    }

    @Test
    void secondExposureAttemptIsRefusedWithoutChangingFingerprint() {

        ExposedFixture fixture =
                createExposedFixture(
                        "strategy-second-exposure-v1",
                        "validation-second-exposure-v1",
                        2020
                );

        ProtectedRunGuardRepository.ExposureResult second =
                repository.persistExposed(
                        fixture.guardId(),
                        fixture.runId(),
                        fixture.instanceId(),
                        fixture.configurationHash(),
                        "replacement-market-data-fingerprint",
                        Instant.parse(
                                "2026-01-08T12:03:00Z"
                        )
                );

        assertFalse(
                second.exposed()
        );

        assertEquals(
                ProtectedRunState.EXPOSED,
                second.state()
        );

        assertEquals(
                "EXPOSURE_TRANSITION_REFUSED",
                second.refusalReason()
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        fixture.guardId()
                );

        assertEquals(
                fixture.marketDataFingerprint(),
                guard.marketDataFingerprint()
        );

        Integer exposedEventCount =
                countLedgerEventsByState(
                        fixture.guardId(),
                        ProtectedRunState.EXPOSED
                );

        assertEquals(
                1,
                exposedEventCount
        );
    }

    @Test
    void failedExposedLedgerWriteRollsBackGuardTransition() {

        long guardId =
                repository.createReservation(
                        "strategy-exposed-rollback-v1",
                        "validation-exposed-rollback-v1",
                        ProtectedPeriodType.VALIDATION,
                        LocalDate.of(2019, 1, 1),
                        LocalDate.of(2019, 12, 31),
                        Instant.parse(
                                "2026-01-09T12:00:00Z"
                        )
                );

        UUID runId =
                UUID.randomUUID();

        UUID instanceId =
                UUID.randomUUID();

        String configurationHash =
                "configuration-hash-exposed-rollback-v1";

        assertTrue(
                repository.acquireReservedTarget(
                        guardId,
                        runId,
                        instanceId,
                        configurationHash,
                        MarketDataProviderType.REAL,
                        Instant.parse(
                                "2026-01-09T12:01:00Z"
                        )
                ).acquired()
        );

        installForcedExposedLedgerFailureTrigger();

        try {

            assertThrows(
                    DataAccessException.class,
                    () ->
                            repository.persistExposed(
                                    guardId,
                                    runId,
                                    instanceId,
                                    configurationHash,
                                    "rollback-market-data-fingerprint",
                                    Instant.parse(
                                            "2026-01-09T12:02:00Z"
                                    )
                            )
            );

        } finally {

            removeForcedExposedLedgerFailureTrigger();
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
                ProtectedRunState.RUNNING_UNEXPOSED,
                guard.state()
        );

        assertEquals(
                ProtectedRunState.RUNNING_UNEXPOSED,
                ledger.state()
        );

        assertNull(
                guard.marketDataFingerprint()
        );

        assertEquals(
                2,
                countLedgerEvents(
                        guardId
                )
        );
    }

    // =============================================================
    // EXPOSED INCOMPLETE DETERMINISTIC RECOVERY TESTS
    // =============================================================

    @Test
    void deterministicRecoveryAllowsIdenticalExposedInputsWithoutNewAttempt() {

        ExposedFixture fixture =
                createExposedFixture(
                        "strategy-deterministic-recovery-v1",
                        "validation-deterministic-recovery-v1",
                        2014
                );

        int ledgerCountBefore =
                countLedgerEvents(
                        fixture.guardId()
                );

        ProtectedRunGuardRepository.DeterministicRecoveryResult
                recovery =
                repository.authorizeDeterministicRecovery(
                        fixture.guardId(),
                        fixture.runId(),
                        fixture.configurationHash(),
                        fixture.marketDataFingerprint()
                );

        assertTrue(
                recovery.recoveryAllowed()
        );

        assertEquals(
                fixture.guardId(),
                recovery.guardId()
        );

        assertEquals(
                fixture.runId(),
                recovery.runId()
        );

        assertNull(
                recovery.refusalReason()
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        fixture.guardId()
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        fixture.guardId()
                );

        assertEquals(
                ProtectedRunState.EXPOSED,
                guard.state()
        );

        assertEquals(
                ProtectedRunState.EXPOSED,
                ledger.state()
        );

        assertEquals(
                fixture.runId(),
                guard.runId()
        );

        assertEquals(
                fixture.configurationHash(),
                guard.configurationHash()
        );

        assertEquals(
                fixture.marketDataFingerprint(),
                guard.marketDataFingerprint()
        );

        assertNull(
                ledger.governedVerdict()
        );

        /*
         * Authorization for deterministic recomputation must not
         * create another protected attempt or append another event.
         */
        assertEquals(
                ledgerCountBefore,
                countLedgerEvents(
                        fixture.guardId()
                )
        );
    }

    @Test
    void deterministicRecoveryRefusesChangedRunId() {

        ExposedFixture fixture =
                createExposedFixture(
                        "strategy-recovery-run-mismatch-v1",
                        "validation-recovery-run-mismatch-v1",
                        2013
                );

        int ledgerCountBefore =
                countLedgerEvents(
                        fixture.guardId()
                );

        ProtectedRunGuardRepository.DeterministicRecoveryResult
                recovery =
                repository.authorizeDeterministicRecovery(
                        fixture.guardId(),
                        UUID.randomUUID(),
                        fixture.configurationHash(),
                        fixture.marketDataFingerprint()
                );

        assertFalse(
                recovery.recoveryAllowed()
        );

        assertEquals(
                "PROTECTED_RUN_OWNERSHIP_MISMATCH",
                recovery.refusalReason()
        );

        assertNull(
                recovery.runId()
        );

        assertEquals(
                ProtectedRunState.EXPOSED,
                repository.requireGuard(
                        fixture.guardId()
                ).state()
        );

        assertEquals(
                fixture.runId(),
                repository.requireGuard(
                        fixture.guardId()
                ).runId()
        );

        assertEquals(
                ledgerCountBefore,
                countLedgerEvents(
                        fixture.guardId()
                )
        );
    }

    @Test
    void deterministicRecoveryRefusesChangedConfigurationHash() {

        ExposedFixture fixture =
                createExposedFixture(
                        "strategy-recovery-config-mismatch-v1",
                        "validation-recovery-config-mismatch-v1",
                        2012
                );

        int ledgerCountBefore =
                countLedgerEvents(
                        fixture.guardId()
                );

        ProtectedRunGuardRepository.DeterministicRecoveryResult
                recovery =
                repository.authorizeDeterministicRecovery(
                        fixture.guardId(),
                        fixture.runId(),
                        "different-configuration-hash",
                        fixture.marketDataFingerprint()
                );

        assertFalse(
                recovery.recoveryAllowed()
        );

        assertEquals(
                "PROTECTED_RUN_INPUT_MISMATCH",
                recovery.refusalReason()
        );

        assertNull(
                recovery.runId()
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        fixture.guardId()
                );

        assertEquals(
                ProtectedRunState.EXPOSED,
                guard.state()
        );

        assertEquals(
                fixture.configurationHash(),
                guard.configurationHash()
        );

        assertEquals(
                ledgerCountBefore,
                countLedgerEvents(
                        fixture.guardId()
                )
        );
    }

    @Test
    void deterministicRecoveryRefusesChangedMarketDataFingerprint() {

        ExposedFixture fixture =
                createExposedFixture(
                        "strategy-recovery-data-mismatch-v1",
                        "validation-recovery-data-mismatch-v1",
                        2011
                );

        int ledgerCountBefore =
                countLedgerEvents(
                        fixture.guardId()
                );

        ProtectedRunGuardRepository.DeterministicRecoveryResult
                recovery =
                repository.authorizeDeterministicRecovery(
                        fixture.guardId(),
                        fixture.runId(),
                        fixture.configurationHash(),
                        "different-market-data-fingerprint"
                );

        assertFalse(
                recovery.recoveryAllowed()
        );

        assertEquals(
                "PROTECTED_RUN_INPUT_MISMATCH",
                recovery.refusalReason()
        );

        assertNull(
                recovery.runId()
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        fixture.guardId()
                );

        assertEquals(
                ProtectedRunState.EXPOSED,
                guard.state()
        );

        assertEquals(
                fixture.marketDataFingerprint(),
                guard.marketDataFingerprint()
        );

        assertEquals(
                ledgerCountBefore,
                countLedgerEvents(
                        fixture.guardId()
                )
        );
    }


    // =============================================================
    // RUNNING_UNEXPOSED -> RESERVED RECOVERY TESTS
    // =============================================================

    @Test
    void staleUnexposedRunRecoversToReservedWithAbortedRunEvent() {

        long guardId =
                repository.createReservation(
                        "strategy-recovery-v1",
                        "validation-recovery-v1",
                        ProtectedPeriodType.VALIDATION,
                        LocalDate.of(
                                2018,
                                1,
                                1
                        ),
                        LocalDate.of(
                                2018,
                                12,
                                31
                        ),
                        Instant.parse(
                                "2026-01-10T12:00:00Z"
                        )
                );

        UUID abandonedRunId =
                UUID.randomUUID();

        UUID abandonedInstanceId =
                UUID.randomUUID();

        String configurationHash =
                "recovery-configuration-hash-v1";

        ProtectedRunGuardRepository.AcquisitionResult acquisition =
                repository.acquireReservedTarget(
                        guardId,
                        abandonedRunId,
                        abandonedInstanceId,
                        configurationHash,
                        MarketDataProviderType.REAL,
                        Instant.parse(
                                "2026-01-10T12:01:00Z"
                        )
                );

        assertTrue(
                acquisition.acquired()
        );

        UUID restartedInstanceId =
                UUID.randomUUID();

        assertNotEquals(
                abandonedInstanceId,
                restartedInstanceId
        );

        ProtectedRunGuardRepository.RecoveryResult recovery =
                repository.recoverStaleUnexposedRun(
                        guardId,
                        restartedInstanceId,
                        Instant.parse(
                                "2026-01-10T12:02:00Z"
                        )
                );

        assertTrue(
                recovery.recovered()
        );

        assertEquals(
                guardId,
                recovery.guardId()
        );

        assertEquals(
                abandonedRunId,
                recovery.abortedRunId()
        );

        assertEquals(
                abandonedInstanceId,
                recovery.previousInstanceId()
        );

        assertNull(
                recovery.refusalReason()
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                guard.state()
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

        assertNull(
                guard.marketDataFingerprint()
        );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                ledger.state()
        );

        assertNull(
                ledger.governedVerdict()
        );

        String latestEventType =
                jdbcTemplate.queryForObject(
                        """
                        SELECT event_type
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                        ORDER BY sequence_number DESC
                        LIMIT 1
                        """,
                        String.class,
                        guardId
                );

        assertEquals(
                "ABORTED_RUN",
                latestEventType
        );

        Integer abortedEventCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                          AND event_type = 'ABORTED_RUN'
                        """,
                        Integer.class,
                        guardId
                );

        assertEquals(
                1,
                abortedEventCount
        );

        assertEquals(
                3,
                countLedgerEvents(
                        guardId
                )
        );
    }

    @Test
    void currentApplicationInstanceCannotRecoverItsOwnUnexposedRun() {

        long guardId =
                repository.createReservation(
                        "strategy-recovery-owner-v1",
                        "validation-recovery-owner-v1",
                        ProtectedPeriodType.VALIDATION,
                        LocalDate.of(
                                2017,
                                1,
                                1
                        ),
                        LocalDate.of(
                                2017,
                                12,
                                31
                        ),
                        Instant.parse(
                                "2026-01-11T12:00:00Z"
                        )
                );

        UUID runId =
                UUID.randomUUID();

        UUID instanceId =
                UUID.randomUUID();

        String configurationHash =
                "recovery-owner-hash-v1";

        assertTrue(
                repository.acquireReservedTarget(
                        guardId,
                        runId,
                        instanceId,
                        configurationHash,
                        MarketDataProviderType.REAL,
                        Instant.parse(
                                "2026-01-11T12:01:00Z"
                        )
                ).acquired()
        );

        ProtectedRunGuardRepository.RecoveryResult recovery =
                repository.recoverStaleUnexposedRun(
                        guardId,
                        instanceId,
                        Instant.parse(
                                "2026-01-11T12:02:00Z"
                        )
                );

        assertFalse(
                recovery.recovered()
        );

        assertEquals(
                "RECOVERY_REQUIRES_EARLIER_INSTANCE",
                recovery.refusalReason()
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        assertEquals(
                ProtectedRunState.RUNNING_UNEXPOSED,
                guard.state()
        );

        assertEquals(
                runId,
                guard.runId()
        );

        assertEquals(
                instanceId,
                guard.instanceId()
        );

        assertEquals(
                configurationHash,
                guard.configurationHash()
        );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        assertEquals(
                ProtectedRunState.RUNNING_UNEXPOSED,
                ledger.state()
        );

        Integer abortedEventCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                          AND event_type = 'ABORTED_RUN'
                        """,
                        Integer.class,
                        guardId
                );

        assertEquals(
                0,
                abortedEventCount
        );

        assertEquals(
                2,
                countLedgerEvents(
                        guardId
                )
        );
    }

    // =============================================================
    // EXPOSED -> SPENT TESTS
    // =============================================================

    @Test
    void governedPassVerdictTransitionsExposedRunToSpent() {

        ExposedFixture fixture =
                createExposedFixture(
                        "strategy-spent-pass-v1",
                        "validation-spent-pass-v1",
                        2018
                );

        ProtectedRunGuardRepository.CompletionResult completion =
                repository.persistGovernedVerdict(
                        fixture.guardId(),
                        fixture.runId(),
                        fixture.instanceId(),
                        fixture.configurationHash(),
                        fixture.marketDataFingerprint(),
                        "pass",
                        Instant.parse(
                                "2026-01-10T12:03:00Z"
                        )
                );

        assertTrue(
                completion.spent()
        );

        assertEquals(
                ProtectedRunState.SPENT,
                completion.state()
        );

        assertEquals(
                "PASS",
                completion.governedVerdict()
        );

        assertEquals(
                fixture.runId(),
                completion.runId()
        );

        assertEquals(
                fixture.instanceId(),
                completion.instanceId()
        );

        assertNull(
                completion.refusalReason()
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        fixture.guardId()
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        fixture.guardId()
                );

        assertEquals(
                ProtectedRunState.SPENT,
                guard.state()
        );

        assertEquals(
                ProtectedRunState.SPENT,
                ledger.state()
        );

        assertEquals(
                "PASS",
                ledger.governedVerdict()
        );

        assertNotNull(
                ledger.eventHash()
        );

        assertEquals(
                64,
                ledger.eventHash().length()
        );

        assertEquals(
                4,
                countLedgerEvents(
                        fixture.guardId()
                )
        );

        assertEquals(
                1,
                countLedgerEventsByState(
                        fixture.guardId(),
                        ProtectedRunState.SPENT
                )
        );
    }

    @Test
    void governedFailVerdictTransitionsExposedRunToSpent() {

        ExposedFixture fixture =
                createExposedFixture(
                        "strategy-spent-fail-v1",
                        "validation-spent-fail-v1",
                        2017
                );

        ProtectedRunGuardRepository.CompletionResult completion =
                repository.persistGovernedVerdict(
                        fixture.guardId(),
                        fixture.runId(),
                        fixture.instanceId(),
                        fixture.configurationHash(),
                        fixture.marketDataFingerprint(),
                        "FAIL",
                        Instant.parse(
                                "2026-01-11T12:03:00Z"
                        )
                );

        assertTrue(
                completion.spent()
        );

        assertEquals(
                "FAIL",
                completion.governedVerdict()
        );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        fixture.guardId()
                );

        assertEquals(
                ProtectedRunState.SPENT,
                ledger.state()
        );

        assertEquals(
                "FAIL",
                ledger.governedVerdict()
        );
    }

    @Test
    void secondCompletionAttemptIsRefusedAndOriginalVerdictRemainsFrozen() {

        ExposedFixture fixture =
                createExposedFixture(
                        "strategy-double-spent-v1",
                        "validation-double-spent-v1",
                        2016
                );

        ProtectedRunGuardRepository.CompletionResult first =
                repository.persistGovernedVerdict(
                        fixture.guardId(),
                        fixture.runId(),
                        fixture.instanceId(),
                        fixture.configurationHash(),
                        fixture.marketDataFingerprint(),
                        "PASS",
                        Instant.parse(
                                "2026-01-12T12:03:00Z"
                        )
                );

        assertTrue(
                first.spent()
        );

        ProtectedRunGuardRepository.CompletionResult second =
                repository.persistGovernedVerdict(
                        fixture.guardId(),
                        fixture.runId(),
                        fixture.instanceId(),
                        fixture.configurationHash(),
                        fixture.marketDataFingerprint(),
                        "FAIL",
                        Instant.parse(
                                "2026-01-12T12:04:00Z"
                        )
                );

        assertFalse(
                second.spent()
        );

        assertEquals(
                ProtectedRunState.SPENT,
                second.state()
        );

        assertEquals(
                "SPENT_TRANSITION_REFUSED",
                second.refusalReason()
        );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        fixture.guardId()
                );

        assertEquals(
                ProtectedRunState.SPENT,
                ledger.state()
        );

        assertEquals(
                "PASS",
                ledger.governedVerdict(),
                "The original governed verdict must remain immutable"
        );

        assertEquals(
                1,
                countLedgerEventsByState(
                        fixture.guardId(),
                        ProtectedRunState.SPENT
                )
        );
    }

    @Test
    void completionRequiresMatchingProtectedRunInputs() {

        ExposedFixture fixture =
                createExposedFixture(
                        "strategy-completion-inputs-v1",
                        "validation-completion-inputs-v1",
                        2015
                );

        ProtectedRunGuardRepository.CompletionResult wrongRun =
                repository.persistGovernedVerdict(
                        fixture.guardId(),
                        UUID.randomUUID(),
                        fixture.instanceId(),
                        fixture.configurationHash(),
                        fixture.marketDataFingerprint(),
                        "PASS",
                        Instant.parse(
                                "2026-01-13T12:03:00Z"
                        )
                );

        assertFalse(
                wrongRun.spent()
        );

        assertEquals(
                "PROTECTED_RUN_OWNERSHIP_MISMATCH",
                wrongRun.refusalReason()
        );

        ProtectedRunGuardRepository.CompletionResult wrongInstance =
                repository.persistGovernedVerdict(
                        fixture.guardId(),
                        fixture.runId(),
                        UUID.randomUUID(),
                        fixture.configurationHash(),
                        fixture.marketDataFingerprint(),
                        "PASS",
                        Instant.parse(
                                "2026-01-13T12:04:00Z"
                        )
                );

        assertFalse(
                wrongInstance.spent()
        );

        assertEquals(
                "PROTECTED_RUN_OWNERSHIP_MISMATCH",
                wrongInstance.refusalReason()
        );

        ProtectedRunGuardRepository.CompletionResult wrongConfiguration =
                repository.persistGovernedVerdict(
                        fixture.guardId(),
                        fixture.runId(),
                        fixture.instanceId(),
                        "different-configuration-hash",
                        fixture.marketDataFingerprint(),
                        "PASS",
                        Instant.parse(
                                "2026-01-13T12:05:00Z"
                        )
                );

        assertFalse(
                wrongConfiguration.spent()
        );

        assertEquals(
                "PROTECTED_RUN_OWNERSHIP_MISMATCH",
                wrongConfiguration.refusalReason()
        );

        ProtectedRunGuardRepository.CompletionResult wrongFingerprint =
                repository.persistGovernedVerdict(
                        fixture.guardId(),
                        fixture.runId(),
                        fixture.instanceId(),
                        fixture.configurationHash(),
                        "different-market-data-fingerprint",
                        "PASS",
                        Instant.parse(
                                "2026-01-13T12:06:00Z"
                        )
                );

        assertFalse(
                wrongFingerprint.spent()
        );

        assertEquals(
                "PROTECTED_RUN_INPUT_MISMATCH",
                wrongFingerprint.refusalReason()
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        fixture.guardId()
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        fixture.guardId()
                );

        assertEquals(
                ProtectedRunState.EXPOSED,
                guard.state()
        );

        assertEquals(
                ProtectedRunState.EXPOSED,
                ledger.state()
        );

        assertNull(
                ledger.governedVerdict()
        );

        assertEquals(
                3,
                countLedgerEvents(
                        fixture.guardId()
                )
        );
    }

    @Test
    void failedSpentLedgerWriteRollsBackGuardTransition() {

        ExposedFixture fixture =
                createExposedFixture(
                        "strategy-spent-rollback-v1",
                        "validation-spent-rollback-v1",
                        2014
                );

        installForcedSpentLedgerFailureTrigger();

        try {

            assertThrows(
                    DataAccessException.class,
                    () ->
                            repository.persistGovernedVerdict(
                                    fixture.guardId(),
                                    fixture.runId(),
                                    fixture.instanceId(),
                                    fixture.configurationHash(),
                                    fixture.marketDataFingerprint(),
                                    "PASS",
                                    Instant.parse(
                                            "2026-01-14T12:03:00Z"
                                    )
                            )
            );

        } finally {

            removeForcedSpentLedgerFailureTrigger();
        }

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        fixture.guardId()
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        fixture.guardId()
                );

        assertEquals(
                ProtectedRunState.EXPOSED,
                guard.state(),
                "Failed SPENT ledger persistence must roll the guard back to EXPOSED"
        );

        assertEquals(
                ProtectedRunState.EXPOSED,
                ledger.state()
        );

        assertNull(
                ledger.governedVerdict()
        );

        assertEquals(
                fixture.marketDataFingerprint(),
                guard.marketDataFingerprint()
        );

        assertEquals(
                3,
                countLedgerEvents(
                        fixture.guardId()
                )
        );

        assertEquals(
                0,
                countLedgerEventsByState(
                        fixture.guardId(),
                        ProtectedRunState.SPENT
                )
        );
    }

    @Test
    void databaseRefusesSpentLedgerEventWithoutGovernedVerdict() {

        ExposedFixture fixture =
                createExposedFixture(
                        "strategy-null-verdict-v1",
                        "validation-null-verdict-v1",
                        2013
                );

        ProtectedRunGuardRepository.LedgerSnapshot exposedLedger =
                repository.requireLatestLedgerSnapshot(
                        fixture.guardId()
                );

        String fakeEventHash =
                "a".repeat(
                        64
                );

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbcTemplate.update(
                                """
                                INSERT INTO protected_run_event_ledger (
                                    event_id,
                                    guard_id,
                                    run_id,
                                    event_type,
                                    previous_state,
                                    new_state,
                                    instance_id,
                                    configuration_hash,
                                    market_data_fingerprint,
                                    data_source_type,
                                    governed_verdict,
                                    previous_event_hash,
                                    event_hash,
                                    event_timestamp
                                )
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                                UUID.randomUUID(),
                                fixture.guardId(),
                                fixture.runId(),
                                ProtectedRunState.SPENT.name(),
                                ProtectedRunState.EXPOSED.name(),
                                ProtectedRunState.SPENT.name(),
                                fixture.instanceId(),
                                fixture.configurationHash(),
                                fixture.marketDataFingerprint(),
                                MarketDataProviderType.REAL.name(),
                                null,
                                exposedLedger.eventHash(),
                                fakeEventHash,
                                Timestamp.from(
                                        Instant.parse(
                                                "2026-01-15T12:03:00Z"
                                        )
                                )
                        )
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        fixture.guardId()
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        fixture.guardId()
                );

        assertEquals(
                ProtectedRunState.EXPOSED,
                guard.state()
        );

        assertEquals(
                ProtectedRunState.EXPOSED,
                ledger.state()
        );

        assertNull(
                ledger.governedVerdict()
        );

        assertEquals(
                3,
                countLedgerEvents(
                        fixture.guardId()
                )
        );
    }

    @Test
    void governedVerdictMustBePassOrFail() {

        ExposedFixture fixture =
                createExposedFixture(
                        "strategy-invalid-verdict-v1",
                        "validation-invalid-verdict-v1",
                        2012
                );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        repository.persistGovernedVerdict(
                                fixture.guardId(),
                                fixture.runId(),
                                fixture.instanceId(),
                                fixture.configurationHash(),
                                fixture.marketDataFingerprint(),
                                "MAYBE",
                                Instant.parse(
                                        "2026-01-16T12:03:00Z"
                                )
                        )
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        fixture.guardId()
                );

        assertEquals(
                ProtectedRunState.EXPOSED,
                guard.state()
        );

        assertEquals(
                3,
                countLedgerEvents(
                        fixture.guardId()
                )
        );
    }

    // =============================================================
    // HELPERS
    // =============================================================

    private static ExposedFixture createExposedFixture(
            String strategyVersion,
            String protectedPeriodId,
            int year) {

        long guardId =
                repository.createReservation(
                        strategyVersion,
                        protectedPeriodId,
                        ProtectedPeriodType.VALIDATION,
                        LocalDate.of(year, 1, 1),
                        LocalDate.of(year, 12, 31),
                        Instant.parse(
                                "2026-02-01T12:00:00Z"
                        )
                );

        UUID runId =
                UUID.randomUUID();

        UUID instanceId =
                UUID.randomUUID();

        String configurationHash =
                strategyVersion
                        + "-configuration-hash";

        String marketDataFingerprint =
                strategyVersion
                        + "-market-data-fingerprint";

        ProtectedRunGuardRepository.AcquisitionResult acquisition =
                repository.acquireReservedTarget(
                        guardId,
                        runId,
                        instanceId,
                        configurationHash,
                        MarketDataProviderType.REAL,
                        Instant.parse(
                                "2026-02-01T12:01:00Z"
                        )
                );

        assertTrue(
                acquisition.acquired()
        );

        ProtectedRunGuardRepository.ExposureResult exposure =
                repository.persistExposed(
                        guardId,
                        runId,
                        instanceId,
                        configurationHash,
                        marketDataFingerprint,
                        Instant.parse(
                                "2026-02-01T12:02:00Z"
                        )
                );

        assertTrue(
                exposure.exposed()
        );

        return new ExposedFixture(
                guardId,
                runId,
                instanceId,
                configurationHash,
                marketDataFingerprint
        );
    }

    private static Integer countLedgerEvents(
            long guardId) {

        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM protected_run_event_ledger
                WHERE guard_id = ?
                """,
                Integer.class,
                guardId
        );
    }

    private static Integer countLedgerEventsByState(
            long guardId,
            ProtectedRunState state) {

        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM protected_run_event_ledger
                WHERE guard_id = ?
                  AND new_state = ?
                """,
                Integer.class,
                guardId,
                state.name()
        );
    }

    private static void installForcedRunningLedgerFailureTrigger() {

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

    private static void removeForcedRunningLedgerFailureTrigger() {

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

    private static void installForcedExposedLedgerFailureTrigger() {

        jdbcTemplate.execute(
                """
                CREATE OR REPLACE FUNCTION
                    test_fail_exposed_ledger_insert()
                RETURNS TRIGGER
                LANGUAGE plpgsql
                AS
                $$
                BEGIN

                    IF NEW.new_state = 'EXPOSED' THEN
                        RAISE EXCEPTION
                            'Forced EXPOSED ledger failure for transaction rollback test';
                    END IF;

                    RETURN NEW;

                END;
                $$
                """
        );

        jdbcTemplate.execute(
                """
                CREATE TRIGGER
                    test_fail_exposed_ledger_insert_trigger
                BEFORE INSERT
                ON protected_run_event_ledger
                FOR EACH ROW
                EXECUTE FUNCTION
                    test_fail_exposed_ledger_insert()
                """
        );
    }

    private static void removeForcedExposedLedgerFailureTrigger() {

        jdbcTemplate.execute(
                """
                DROP TRIGGER IF EXISTS
                    test_fail_exposed_ledger_insert_trigger
                ON protected_run_event_ledger
                """
        );

        jdbcTemplate.execute(
                """
                DROP FUNCTION IF EXISTS
                    test_fail_exposed_ledger_insert()
                """
        );
    }

    private static void installForcedSpentLedgerFailureTrigger() {

        jdbcTemplate.execute(
                """
                CREATE OR REPLACE FUNCTION
                    test_fail_spent_ledger_insert()
                RETURNS TRIGGER
                LANGUAGE plpgsql
                AS
                $$
                BEGIN

                    IF NEW.new_state = 'SPENT' THEN
                        RAISE EXCEPTION
                            'Forced SPENT ledger failure for transaction rollback test';
                    END IF;

                    RETURN NEW;

                END;
                $$
                """
        );

        jdbcTemplate.execute(
                """
                CREATE TRIGGER
                    test_fail_spent_ledger_insert_trigger
                BEFORE INSERT
                ON protected_run_event_ledger
                FOR EACH ROW
                EXECUTE FUNCTION
                    test_fail_spent_ledger_insert()
                """
        );
    }

    private static void removeForcedSpentLedgerFailureTrigger() {

        jdbcTemplate.execute(
                """
                DROP TRIGGER IF EXISTS
                    test_fail_spent_ledger_insert_trigger
                ON protected_run_event_ledger
                """
        );

        jdbcTemplate.execute(
                """
                DROP FUNCTION IF EXISTS
                    test_fail_spent_ledger_insert()
                """
        );
    }

    private record ExposedFixture(
            long guardId,
            UUID runId,
            UUID instanceId,
            String configurationHash,
            String marketDataFingerprint) {
    }
}