package com.marshallai;

import com.marshallai.governance.exposure.ProtectedPeriodType;
import com.marshallai.governance.exposure.ProtectedRunGuardRepository;
import com.marshallai.governance.exposure.ProtectedRunState;
import com.marshallai.market.MarketDataProviderType;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProtectedRunDataSourceGovernanceDatabaseTest {

    private static final Instant BASE_TIME =
            Instant.parse(
                    "2026-10-09T12:00:00Z"
            );

    private static PostgreSQLContainer postgres;

    private static JdbcTemplate jdbcTemplate;

    private static TransactionTemplate transactionTemplate;

    private static ProtectedRunGuardRepository repository;

    @BeforeAll
    static void initializeDatabase() {

        postgres =
                new PostgreSQLContainer(
                        "postgres:16-alpine"
                );

        postgres.start();

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

        jdbcTemplate =
                new JdbcTemplate(
                        dataSource
                );

        DataSourceTransactionManager transactionManager =
                new DataSourceTransactionManager(
                        dataSource
                );

        transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        repository =
                new ProtectedRunGuardRepository(
                        jdbcTemplate,
                        transactionManager
                );
    }

    @AfterAll
    static void stopDatabase() {

        if (postgres != null) {

            postgres.stop();
        }
    }

    @Test
    void databaseRefusesGovernedVerdictForSimulatedRun() {

        ExposedFixture fixture =
                createExposedFixture(
                        MarketDataProviderType.SIMULATED
                );

        DataAccessException exception =
                assertThrows(
                        DataAccessException.class,
                        () ->
                                persistSpentDirectly(
                                        fixture,
                                        "PASS"
                                )
                );

        assertNotNull(
                exception
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        fixture.guardId()
                );

        assertEquals(
                ProtectedRunState.EXPOSED,
                guard.state(),
                "Failed simulated verdict must roll back "
                        + "the SPENT transition"
        );

        assertEquals(
                "SIMULATED",
                guardDataSourceType(
                        fixture.guardId()
                )
        );

        assertEquals(
                0,
                countLedgerEventsByState(
                        fixture.guardId(),
                        "SPENT"
                )
        );
    }

    @Test
    void databaseAllowsGovernedVerdictForRealRun() {

        ExposedFixture fixture =
                createExposedFixture(
                        MarketDataProviderType.REAL
                );

        persistSpentDirectly(
                fixture,
                "PASS"
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        fixture.guardId()
                );

        assertEquals(
                ProtectedRunState.SPENT,
                guard.state()
        );

        assertEquals(
                "REAL",
                guardDataSourceType(
                        fixture.guardId()
                )
        );

        String verdict =
                jdbcTemplate.queryForObject(
                        """
                        SELECT governed_verdict
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                          AND new_state = 'SPENT'
                        ORDER BY sequence_number DESC
                        LIMIT 1
                        """,
                        String.class,
                        fixture.guardId()
                );

        assertEquals(
                "PASS",
                verdict
        );

        assertEquals(
                1,
                countLedgerEventsByState(
                        fixture.guardId(),
                        "SPENT"
                )
        );
    }

    @Test
    void databaseRejectsLedgerEventWithWrongPreviousHash() {

        long guardId =
                createReservedTarget();

        UUID runId =
                UUID.randomUUID();

        UUID instanceId =
                UUID.randomUUID();

        String configurationHash =
                "wrong-chain-config-"
                        + UUID.randomUUID();

        String actualPreviousHash =
                latestEventHash(
                        guardId
                );

        String wrongPreviousHash =
                "0".repeat(
                        64
                );

        if (wrongPreviousHash.equals(
                actualPreviousHash
        )) {

            wrongPreviousHash =
                    "1".repeat(
                            64
                    );
        }

        String finalWrongPreviousHash =
                wrongPreviousHash;

        DataAccessException exception =
                assertThrows(
                        DataAccessException.class,
                        () ->
                                transactionTemplate.execute(
                                        status -> {

                                            int updated =
                                                    jdbcTemplate.update(
                                                            """
                                                            UPDATE protected_run_guard
                                                            SET
                                                                state = 'RUNNING_UNEXPOSED',
                                                                run_id = ?,
                                                                instance_id = ?,
                                                                configuration_hash = ?,
                                                                data_source_type = 'REAL',
                                                                market_data_fingerprint = NULL
                                                            WHERE id = ?
                                                              AND state = 'RESERVED'
                                                            """,
                                                            runId,
                                                            instanceId,
                                                            configurationHash,
                                                            guardId
                                                    );

                                            assertEquals(
                                                    1,
                                                    updated
                                            );

                                            /*
                                             * Deliberately use a valid-looking
                                             * 64-character hash that is NOT the
                                             * actual previous ledger hash.
                                             *
                                             * PostgreSQL must reject this insert
                                             * through the V1 hash-chain trigger.
                                             */
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
                                                    VALUES (
                                                        ?,
                                                        ?,
                                                        ?,
                                                        'RUNNING_UNEXPOSED',
                                                        'RESERVED',
                                                        'RUNNING_UNEXPOSED',
                                                        ?,
                                                        ?,
                                                        NULL,
                                                        'REAL',
                                                        NULL,
                                                        ?,
                                                        ?,
                                                        ?
                                                    )
                                                    """,
                                                    UUID.randomUUID(),
                                                    guardId,
                                                    runId,
                                                    instanceId,
                                                    configurationHash,
                                                    finalWrongPreviousHash,
                                                    fakeHash(),
                                                    Timestamp.from(
                                                            BASE_TIME.plusSeconds(
                                                                    10
                                                            )
                                                    )
                                            );

                                            return null;
                                        }
                                )
                );

        assertNotNull(
                exception
        );

        /*
         * The failed ledger insert must roll the guard update
         * back as part of the same transaction.
         */
        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                guard.state()
        );

        assertEquals(
                1,
                countLedgerEvents(
                        guardId
                ),
                "Only the original RESERVED event should remain"
        );

        assertEquals(
                actualPreviousHash,
                latestEventHash(
                        guardId
                )
        );
    }

    private static ExposedFixture createExposedFixture(
            MarketDataProviderType dataSourceType) {

        long guardId =
                createReservedTarget();

        UUID runId =
                UUID.randomUUID();

        UUID instanceId =
                UUID.randomUUID();

        String configurationHash =
                "data-source-governance-config-"
                        + UUID.randomUUID();

        String marketDataFingerprint =
                "data-source-governance-market-data-"
                        + UUID.randomUUID();

        String reservationHash =
                latestEventHash(
                        guardId
                );

        String runningHash =
                fakeHash();

        transactionTemplate.execute(
                status -> {

                    int updated =
                            jdbcTemplate.update(
                                    """
                                    UPDATE protected_run_guard
                                    SET
                                        state = 'RUNNING_UNEXPOSED',
                                        run_id = ?,
                                        instance_id = ?,
                                        configuration_hash = ?,
                                        data_source_type = ?,
                                        market_data_fingerprint = NULL
                                    WHERE id = ?
                                      AND state = 'RESERVED'
                                    """,
                                    runId,
                                    instanceId,
                                    configurationHash,
                                    dataSourceType.name(),
                                    guardId
                            );

                    assertEquals(
                            1,
                            updated
                    );

                    int inserted =
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
                                    VALUES (
                                        ?,
                                        ?,
                                        ?,
                                        'RUNNING_UNEXPOSED',
                                        'RESERVED',
                                        'RUNNING_UNEXPOSED',
                                        ?,
                                        ?,
                                        NULL,
                                        ?,
                                        NULL,
                                        ?,
                                        ?,
                                        ?
                                    )
                                    """,
                                    UUID.randomUUID(),
                                    guardId,
                                    runId,
                                    instanceId,
                                    configurationHash,
                                    dataSourceType.name(),
                                    reservationHash,
                                    runningHash,
                                    Timestamp.from(
                                            BASE_TIME.plusSeconds(
                                                    1
                                            )
                                    )
                            );

                    assertEquals(
                            1,
                            inserted
                    );

                    return null;
                }
        );

        String exposedHash =
                fakeHash();

        transactionTemplate.execute(
                status -> {

                    int updated =
                            jdbcTemplate.update(
                                    """
                                    UPDATE protected_run_guard
                                    SET
                                        state = 'EXPOSED',
                                        market_data_fingerprint = ?
                                    WHERE id = ?
                                      AND state = 'RUNNING_UNEXPOSED'
                                      AND run_id = ?
                                      AND instance_id = ?
                                      AND configuration_hash = ?
                                      AND data_source_type = ?
                                    """,
                                    marketDataFingerprint,
                                    guardId,
                                    runId,
                                    instanceId,
                                    configurationHash,
                                    dataSourceType.name()
                            );

                    assertEquals(
                            1,
                            updated
                    );

                    int inserted =
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
                                    VALUES (
                                        ?,
                                        ?,
                                        ?,
                                        'EXPOSED',
                                        'RUNNING_UNEXPOSED',
                                        'EXPOSED',
                                        ?,
                                        ?,
                                        ?,
                                        ?,
                                        NULL,
                                        ?,
                                        ?,
                                        ?
                                    )
                                    """,
                                    UUID.randomUUID(),
                                    guardId,
                                    runId,
                                    instanceId,
                                    configurationHash,
                                    marketDataFingerprint,
                                    dataSourceType.name(),
                                    runningHash,
                                    exposedHash,
                                    Timestamp.from(
                                            BASE_TIME.plusSeconds(
                                                    2
                                            )
                                    )
                            );

                    assertEquals(
                            1,
                            inserted
                    );

                    return null;
                }
        );

        return new ExposedFixture(
                guardId,
                runId,
                instanceId,
                configurationHash,
                marketDataFingerprint,
                dataSourceType,
                exposedHash
        );
    }

    private static void persistSpentDirectly(
            ExposedFixture fixture,
            String governedVerdict) {

        transactionTemplate.execute(
                status -> {

                    int updated =
                            jdbcTemplate.update(
                                    """
                                    UPDATE protected_run_guard
                                    SET state = 'SPENT'
                                    WHERE id = ?
                                      AND state = 'EXPOSED'
                                      AND run_id = ?
                                      AND instance_id = ?
                                      AND configuration_hash = ?
                                      AND market_data_fingerprint = ?
                                      AND data_source_type = ?
                                    """,
                                    fixture.guardId(),
                                    fixture.runId(),
                                    fixture.instanceId(),
                                    fixture.configurationHash(),
                                    fixture.marketDataFingerprint(),
                                    fixture.dataSourceType().name()
                            );

                    assertEquals(
                            1,
                            updated
                    );

                    int inserted =
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
                                    VALUES (
                                        ?,
                                        ?,
                                        ?,
                                        'SPENT',
                                        'EXPOSED',
                                        'SPENT',
                                        ?,
                                        ?,
                                        ?,
                                        ?,
                                        ?,
                                        ?,
                                        ?,
                                        ?
                                    )
                                    """,
                                    UUID.randomUUID(),
                                    fixture.guardId(),
                                    fixture.runId(),
                                    fixture.instanceId(),
                                    fixture.configurationHash(),
                                    fixture.marketDataFingerprint(),
                                    fixture.dataSourceType().name(),
                                    governedVerdict,
                                    fixture.exposedEventHash(),
                                    fakeHash(),
                                    Timestamp.from(
                                            BASE_TIME.plusSeconds(
                                                    3
                                            )
                                    )
                            );

                    assertEquals(
                            1,
                            inserted
                    );

                    return null;
                }
        );
    }

    private static long createReservedTarget() {

        String suffix =
                UUID.randomUUID()
                        .toString();

        return repository.createReservation(
                "data-source-governance-"
                        + suffix,
                "validation-"
                        + suffix,
                ProtectedPeriodType.VALIDATION,
                LocalDate.of(
                        2055,
                        1,
                        1
                ),
                LocalDate.of(
                        2055,
                        12,
                        31
                ),
                BASE_TIME
        );
    }

    private static String latestEventHash(
            long guardId) {

        String value =
                jdbcTemplate.queryForObject(
                        """
                        SELECT event_hash
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                        ORDER BY sequence_number DESC
                        LIMIT 1
                        """,
                        String.class,
                        guardId
                );

        assertNotNull(
                value
        );

        return value;
    }

    private static String guardDataSourceType(
            long guardId) {

        return jdbcTemplate.queryForObject(
                """
                SELECT data_source_type
                FROM protected_run_guard
                WHERE id = ?
                """,
                String.class,
                guardId
        );
    }

    private static int countLedgerEvents(
            long guardId) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                        """,
                        Integer.class,
                        guardId
                );

        assertNotNull(
                count
        );

        return count;
    }

    private static int countLedgerEventsByState(
            long guardId,
            String state) {

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
                        state
                );

        assertNotNull(
                count
        );

        return count;
    }

    private static String fakeHash() {

        String value =
                UUID.randomUUID()
                        .toString()
                        .replace(
                                "-",
                                ""
                        );

        return value + value;
    }

    private record ExposedFixture(
            long guardId,
            UUID runId,
            UUID instanceId,
            String configurationHash,
            String marketDataFingerprint,
            MarketDataProviderType dataSourceType,
            String exposedEventHash) {
    }
}