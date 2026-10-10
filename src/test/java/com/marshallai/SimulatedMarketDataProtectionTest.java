package com.marshallai;

import com.marshallai.governance.exposure.ProtectedPeriodType;
import com.marshallai.governance.exposure.ProtectedRunGuardRepository;
import com.marshallai.governance.exposure.ProtectedRunState;
import com.marshallai.market.MarketCandle;
import com.marshallai.market.SimulatedMarketDataProvider;
import com.marshallai.market.protection.ProtectedMarketDataAccessContext;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SimulatedMarketDataProtectionTest {

    private static final Instant TEST_TIMESTAMP =
            Instant.parse("2026-10-07T12:00:00Z");

    private static PostgreSQLContainer postgres;

    private static JdbcTemplate jdbcTemplate;

    private static ProtectedRunGuardRepository
            guardRepository;

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

        guardRepository =
                new ProtectedRunGuardRepository(
                        jdbcTemplate,
                        transactionManager
                );
    }

    @AfterEach
    void clearAuthorization() {

        ProtectedMarketDataAccessContext.clear();
    }

    @AfterAll
    static void stopDatabase() {

        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void simulatedProviderMayOverlapProtectedDatesWithoutRecordingRealExposure() {

        String strategyVersion =
                "simulated-protection-test-"
                        + UUID.randomUUID();

        LocalDate validationStart =
                LocalDate.of(
                        2050,
                        1,
                        1
                );

        LocalDate validationEnd =
                LocalDate.of(
                        2050,
                        1,
                        10
                );

        LocalDate holdoutStart =
                validationEnd.plusDays(
                        1
                );

        LocalDate holdoutEnd =
                LocalDate.of(
                        2050,
                        1,
                        20
                );

        long validationGuardId =
                guardRepository.createReservation(
                        strategyVersion,
                        "validation-" + UUID.randomUUID(),
                        ProtectedPeriodType.VALIDATION,
                        validationStart,
                        validationEnd,
                        TEST_TIMESTAMP
                );

        long holdoutGuardId =
                guardRepository.createReservation(
                        strategyVersion,
                        "holdout-" + UUID.randomUUID(),
                        ProtectedPeriodType.HOLDOUT,
                        holdoutStart,
                        holdoutEnd,
                        TEST_TIMESTAMP.plusSeconds(
                                1
                        )
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                guardRepository.requireGuard(
                        validationGuardId
                ).state()
        );

        assertEquals(
                ProtectedRunState.RESERVED,
                guardRepository.requireGuard(
                        holdoutGuardId
                ).state()
        );

        /*
         * Each reservation creates its initial immutable
         * governance ledger event.
         *
         * Capture the counts before simulated market-data
         * access so we can prove no real-data exposure event
         * was added afterward.
         */
        int validationLedgerEventsBefore =
                ledgerEventCount(
                        validationGuardId
                );

        int holdoutLedgerEventsBefore =
                ledgerEventCount(
                        holdoutGuardId
                );

        assertNull(
                ProtectedMarketDataAccessContext
                        .currentAuthorization(),
                "Simulated market data should not require "
                        + "protected real-data authorization"
        );

        SimulatedMarketDataProvider provider =
                new SimulatedMarketDataProvider();

        /*
         * Deliberately request one continuous range that
         * overlaps both protected validation and holdout
         * periods.
         *
         * This would be illegal through the real Alpaca
         * provider without governed authorization.
         */
        Instant from =
                startOfDay(
                        validationStart
                );

        Instant to =
                endExclusive(
                        holdoutEnd
                );

        List<MarketCandle> candles =
                provider.getHistoricalCandles(
                        "SPY",
                        "1d",
                        from,
                        to
                );

        assertFalse(
                candles.isEmpty(),
                "Simulated provider should return market candles"
        );

        assertTrue(
                containsDate(
                        candles,
                        validationStart
                ),
                "Simulated candles should be allowed to overlap "
                        + "the protected validation period"
        );

        assertTrue(
                containsDate(
                        candles,
                        holdoutStart
                ),
                "Simulated candles should be allowed to overlap "
                        + "the protected holdout period"
        );

        for (MarketCandle candle : candles) {

            assertFalse(
                    candle.timestamp()
                            .isBefore(
                                    from
                            )
            );

            assertTrue(
                    candle.timestamp()
                            .isBefore(
                                    to
                            ),
                    "Simulated provider must still honor "
                            + "the exclusive range end"
            );
        }

        /*
         * No protected authorization context should have
         * been created merely because simulated dates
         * overlap protected periods.
         */
        assertNull(
                ProtectedMarketDataAccessContext
                        .currentAuthorization()
        );

        /*
         * Most importantly, simulated access cannot mutate
         * either protected real-market-data guard.
         */
        ProtectedRunGuardRepository.GuardSnapshot
                validationGuard =
                guardRepository.requireGuard(
                        validationGuardId
                );

        ProtectedRunGuardRepository.GuardSnapshot
                holdoutGuard =
                guardRepository.requireGuard(
                        holdoutGuardId
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                validationGuard.state(),
                "Simulated validation overlap must not expose "
                        + "the real protected target"
        );

        assertEquals(
                ProtectedRunState.RESERVED,
                holdoutGuard.state(),
                "Simulated holdout overlap must not expose "
                        + "the real protected target"
        );

        assertNull(
                validationGuard.marketDataFingerprint(),
                "Simulated validation data must not create "
                        + "a real-market-data fingerprint"
        );

        assertNull(
                holdoutGuard.marketDataFingerprint(),
                "Simulated holdout data must not create "
                        + "a real-market-data fingerprint"
        );

        assertEquals(
                validationLedgerEventsBefore,
                ledgerEventCount(
                        validationGuardId
                ),
                "Simulated validation access must not append "
                        + "a protected exposure event"
        );

        assertEquals(
                holdoutLedgerEventsBefore,
                ledgerEventCount(
                        holdoutGuardId
                ),
                "Simulated holdout access must not append "
                        + "a protected exposure event"
        );
    }

    private static int ledgerEventCount(
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

    private static boolean containsDate(
            List<MarketCandle> candles,
            LocalDate expectedDate) {

        return candles.stream()
                .map(
                        MarketCandle::timestamp
                )
                .map(
                        timestamp ->
                                timestamp.atZone(
                                                ZoneOffset.UTC
                                        )
                                        .toLocalDate()
                )
                .anyMatch(
                        expectedDate::equals
                );
    }

    private static Instant startOfDay(
            LocalDate date) {

        return date.atStartOfDay()
                .toInstant(
                        ZoneOffset.UTC
                );
    }

    private static Instant endExclusive(
            LocalDate date) {

        return date.plusDays(
                        1
                )
                .atStartOfDay()
                .toInstant(
                        ZoneOffset.UTC
                );
    }
}