package com.marshallai;

import com.marshallai.market.MarketDataProviderType;
import com.marshallai.governance.exposure.ProtectedPeriodType;
import com.marshallai.governance.exposure.ProtectedResultExposureService;
import com.marshallai.governance.exposure.ProtectedRunGuardRepository;
import com.marshallai.governance.exposure.ProtectedRunState;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProtectedResultExposureServiceTest {

    private static PostgreSQLContainer postgres;

    private JdbcTemplate jdbcTemplate;

    private ProtectedRunGuardRepository repository;

    private ProtectedResultExposureService service;

    @BeforeAll
    static void startPostgres() {

        postgres =
                new PostgreSQLContainer(
                        "postgres:16-alpine"
                );

        postgres.start();
    }

    @AfterAll
    static void stopPostgres() {

        if (postgres != null) {

            postgres.stop();
        }
    }

    @BeforeEach
    void setUp() {

        Flyway.configure()
                .dataSource(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword()
                )
                .locations(
                        "classpath:db/migration"
                )
                .load()
                .migrate();

        DataSource dataSource =
                createDataSource();

        jdbcTemplate =
                new JdbcTemplate(
                        dataSource
                );

        repository =
                new ProtectedRunGuardRepository(
                        jdbcTemplate,
                        new DataSourceTransactionManager(
                                dataSource
                        )
                );

        service =
                new ProtectedResultExposureService(
                        repository
                );

        removeForcedExposedLedgerFailureTrigger();
    }

    @Test
    void protectedResultsBecomeVisibleOnlyAfterExposedIsDurable() {

        Fixture fixture =
                createRunningUnexposedFixture(
                        "result-exposure-success"
                );

        AtomicInteger computationCount =
                new AtomicInteger();

        ProtectedResultExposureService.ResultExposure<ProtectedResult>
                exposure =
                service.computeAndExpose(
                        fixture.guardId(),
                        fixture.runId(),
                        fixture.instanceId(),
                        fixture.configurationHash(),
                        fixture.marketDataFingerprint(),
                        () -> {

                            computationCount.incrementAndGet();

                            /*
                             * During protected computation the run
                             * must still be RUNNING_UNEXPOSED.
                             *
                             * The result exists only inside this
                             * service call at this point.
                             */
                            assertEquals(
                                    ProtectedRunState.RUNNING_UNEXPOSED,
                                    repository.requireGuard(
                                            fixture.guardId()
                                    ).state()
                            );

                            return new ProtectedResult(
                                    "one protected trade",
                                    new BigDecimal(
                                            "125.50"
                                    ),
                                    new BigDecimal(
                                            "0.42"
                                    ),
                                    new BigDecimal(
                                            "0.08"
                                    ),
                                    "PASS"
                            );
                        },
                        Instant.parse(
                                "2026-02-01T12:02:00Z"
                        ),
                        Instant.parse(
                                "2026-02-01T12:03:00Z"
                        )
                );

        assertEquals(
                1,
                computationCount.get()
        );

        /*
         * The caller receives the result only after
         * persistExposed(...) has completed.
         */
        assertTrue(
                exposure.resultsVisible()
        );

        assertFalse(
                exposure.resultsDiscarded()
        );

        assertFalse(
                exposure.runAborted()
        );

        assertNotNull(
                exposure.result()
        );

        assertNull(
                exposure.refusalReason()
        );

        assertEquals(
                "one protected trade",
                exposure.result()
                        .tradeSummary()
        );

        assertEquals(
                new BigDecimal(
                        "125.50"
                ),
                exposure.result()
                        .profitAndLoss()
        );

        /*
         * By the time the result is visible outside the
         * service, EXPOSED must already be durable.
         */
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

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        fixture.guardId()
                );

        assertEquals(
                ProtectedRunState.EXPOSED,
                ledger.state()
        );

        String ledgerMarketDataFingerprint =
                jdbcTemplate.queryForObject(
                        """
                        SELECT market_data_fingerprint
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                        ORDER BY sequence_number DESC
                        LIMIT 1
                        """,
                        String.class,
                        fixture.guardId()
                );

        assertEquals(
                fixture.marketDataFingerprint(),
                ledgerMarketDataFingerprint
        );

        assertEquals(
                1,
                countLedgerEventsByState(
                        fixture.guardId(),
                        ProtectedRunState.EXPOSED
                )
        );
    }

    @Test
    void failedExposedPersistenceDiscardsResultsAndAbortsRun() {

        Fixture fixture =
                createRunningUnexposedFixture(
                        "result-exposure-failure"
                );

        AtomicInteger computationCount =
                new AtomicInteger();

        installForcedExposedLedgerFailureTrigger();

        ProtectedResultExposureService.ResultExposure<ProtectedResult>
                exposure;

        try {

            exposure =
                    service.computeAndExpose(
                            fixture.guardId(),
                            fixture.runId(),
                            fixture.instanceId(),
                            fixture.configurationHash(),
                            fixture.marketDataFingerprint(),
                            () -> {

                                computationCount.incrementAndGet();

                                assertEquals(
                                        ProtectedRunState.RUNNING_UNEXPOSED,
                                        repository.requireGuard(
                                                fixture.guardId()
                                        ).state()
                                );

                                return new ProtectedResult(
                                        "protected trade that must be discarded",
                                        new BigDecimal(
                                                "999.99"
                                        ),
                                        new BigDecimal(
                                                "9.99"
                                        ),
                                        new BigDecimal(
                                                "0.01"
                                        ),
                                        "PASS"
                                );
                            },
                            Instant.parse(
                                    "2026-02-02T12:02:00Z"
                            ),
                            Instant.parse(
                                    "2026-02-02T12:03:00Z"
                            )
                    );

        } finally {

            removeForcedExposedLedgerFailureTrigger();
        }

        assertEquals(
                1,
                computationCount.get()
        );

        /*
         * Nothing result-bearing may escape the failed
         * EXPOSED persistence path.
         */
        assertFalse(
                exposure.resultsVisible()
        );

        assertTrue(
                exposure.resultsDiscarded()
        );

        assertTrue(
                exposure.runAborted()
        );

        assertNull(
                exposure.result()
        );

        assertNotNull(
                exposure.refusalReason()
        );

        /*
         * The failed EXPOSED transaction rolled back, then
         * abortUnexposedRun(...) returned the target safely
         * to RESERVED.
         */
        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        fixture.guardId()
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
                        fixture.guardId()
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                ledger.state()
        );

        assertNull(
                ledger.governedVerdict()
        );

        /*
         * The failed EXPOSED transition must leave no durable
         * EXPOSED event behind.
         */
        assertEquals(
                0,
                countLedgerEventsByState(
                        fixture.guardId(),
                        ProtectedRunState.EXPOSED
                )
        );

        assertEquals(
                1,
                countLedgerEventsByType(
                        fixture.guardId(),
                        "ABORTED_RUN"
                )
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
                        fixture.guardId()
                );

        assertEquals(
                "ABORTED_RUN",
                latestEventType
        );
    }

    private Fixture createRunningUnexposedFixture(
            String suffix) {

        long guardId =
                repository.createReservation(
                        "strategy-" + suffix,
                        "validation-" + suffix
                                + "-"
                                + UUID.randomUUID(),
                        ProtectedPeriodType.VALIDATION,
                        LocalDate.of(
                                2021,
                                1,
                                1
                        ),
                        LocalDate.of(
                                2021,
                                12,
                                31
                        ),
                        Instant.parse(
                                "2026-02-01T12:00:00Z"
                        )
                );

        UUID runId =
                UUID.randomUUID();

        UUID instanceId =
                UUID.randomUUID();

        String configurationHash =
                "configuration-" + suffix;

        String marketDataFingerprint =
                "fingerprint-" + suffix;

        ProtectedRunGuardRepository.AcquisitionResult
                acquisition =
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

        assertEquals(
                ProtectedRunState.RUNNING_UNEXPOSED,
                repository.requireGuard(
                        guardId
                ).state()
        );

        return new Fixture(
                guardId,
                runId,
                instanceId,
                configurationHash,
                marketDataFingerprint
        );
    }

    private int countLedgerEventsByState(
            long guardId,
            ProtectedRunState state) {

        Integer count =
                jdbcTemplate.queryForObject(
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

        assertNotNull(
                count
        );

        return count;
    }

    private int countLedgerEventsByType(
            long guardId,
            String eventType) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                          AND event_type = ?
                        """,
                        Integer.class,
                        guardId,
                        eventType
                );

        assertNotNull(
                count
        );

        return count;
    }

    private void installForcedExposedLedgerFailureTrigger() {

        removeForcedExposedLedgerFailureTrigger();

        jdbcTemplate.execute(
                """
                CREATE FUNCTION
                    test_fail_exposed_ledger_insert()
                RETURNS TRIGGER
                LANGUAGE plpgsql
                AS
                $$
                BEGIN

                    IF NEW.new_state = 'EXPOSED' THEN

                        RAISE EXCEPTION
                            'Forced EXPOSED ledger failure';

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

    private void removeForcedExposedLedgerFailureTrigger() {

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

    private DataSource createDataSource() {

        DriverManagerDataSource dataSource =
                new DriverManagerDataSource();

        dataSource.setDriverClassName(
                "org.postgresql.Driver"
        );

        dataSource.setUrl(
                postgres.getJdbcUrl()
        );

        dataSource.setUsername(
                postgres.getUsername()
        );

        dataSource.setPassword(
                postgres.getPassword()
        );

        return dataSource;
    }

    private record Fixture(
            long guardId,
            UUID runId,
            UUID instanceId,
            String configurationHash,
            String marketDataFingerprint) {
    }

    private record ProtectedResult(
            String tradeSummary,
            BigDecimal profitAndLoss,
            BigDecimal expectancy,
            BigDecimal drawdown,
            String governedVerdict) {
    }
}