package com.marshallai;

import com.marshallai.governance.exposure.ProtectedPeriodType;
import com.marshallai.governance.exposure.ProtectedRunGuardRepository;
import com.marshallai.governance.exposure.ProtectedRunState;
import com.marshallai.governance.exposure.ProtectedTuningPeriodRepository;
import com.marshallai.governance.research.CapitalSensitivityResearchService;
import com.marshallai.market.AssetType;
import com.marshallai.market.MarketCandle;
import com.marshallai.market.MarketDataProvider;
import com.marshallai.persistence.config.ConfigurationFingerprintService;
import com.marshallai.persistence.criteria.CriteriaRegistry;
import com.marshallai.persistence.run.RunLedger;
import com.marshallai.persistence.run.RunRecord;
import com.marshallai.persistence.run.RunService;
import com.marshallai.persistence.run.RunType;
import com.marshallai.risk.PositionSizingService;
import com.marshallai.market.calendar.UsEquityTradingCalendar;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CapitalSensitivityResearchServiceTest {

    private static final Instant TEST_TIMESTAMP =
            Instant.parse("2026-10-07T12:00:00Z");

    private static PostgreSQLContainer postgres;

    private static JdbcTemplate jdbcTemplate;

    private static DataSourceTransactionManager
            transactionManager;

    private static ProtectedRunGuardRepository
            guardRepository;

    private static ProtectedTuningPeriodRepository
            tuningPeriodRepository;

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

        transactionManager =
                new DataSourceTransactionManager(
                        dataSource
                );

        guardRepository =
                new ProtectedRunGuardRepository(
                        jdbcTemplate,
                        transactionManager
                );

        tuningPeriodRepository =
                new ProtectedTuningPeriodRepository(
                        jdbcTemplate
                );
    }

    @AfterAll
    static void stopDatabase() {

        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void capitalSensitivityUsesOnlyTuningDataAndPersistsResearchRun() {

        String strategyVersion =
                "capital-sensitivity-test-"
                        + UUID.randomUUID();

        LocalDate tuningStart =
                LocalDate.of(
                        2050,
                        1,
                        1
                );

        LocalDate tuningEnd =
                LocalDate.of(
                        2050,
                        1,
                        3
                );

        LocalDate validationStart =
                LocalDate.of(
                        2050,
                        2,
                        1
                );

        LocalDate validationEnd =
                LocalDate.of(
                        2050,
                        2,
                        28
                );

        LocalDate holdoutStart =
                LocalDate.of(
                        2050,
                        3,
                        1
                );

        LocalDate holdoutEnd =
                LocalDate.of(
                        2050,
                        3,
                        31
                );

        tuningPeriodRepository.registerTuningPeriod(
                strategyVersion,
                "tuning-" + UUID.randomUUID(),
                tuningStart,
                tuningEnd
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

        ProtectedRunGuardRepository.GuardSnapshot
                validationBefore =
                guardRepository.requireGuard(
                        validationGuardId
                );

        ProtectedRunGuardRepository.GuardSnapshot
                holdoutBefore =
                guardRepository.requireGuard(
                        holdoutGuardId
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                validationBefore.state()
        );

        assertEquals(
                ProtectedRunState.RESERVED,
                holdoutBefore.state()
        );

        int validationLedgerCountBefore =
                ledgerEventCount(
                        validationGuardId
                );

        int holdoutLedgerCountBefore =
                ledgerEventCount(
                        holdoutGuardId
                );

        List<MarketCandle> tuningCandles =
                List.of(
                        candle(
                                "400.00",
                                tuningStart
                        ),
                        candle(
                                "1000.00",
                                tuningStart.plusDays(
                                        1
                                )
                        ),
                        candle(
                                "2000.00",
                                tuningStart.plusDays(
                                        2
                                )
                        )
                );

        RecordingTuningMarketDataProvider provider =
                new RecordingTuningMarketDataProvider(
                        startOfDay(
                                tuningStart
                        ),
                        endExclusive(
                                tuningEnd
                        ),
                        tuningCandles
                );

        RunLedger runLedger =
                new RunLedger();

        RunService runService =
                new RunService(
                        runLedger,
                        new CriteriaRegistry(),
                        new ConfigurationFingerprintService()
                );

        CapitalSensitivityResearchService service =
                new CapitalSensitivityResearchService(
                        provider,
                        tuningPeriodRepository,
                        new PositionSizingService(),
                        runService
                );

        CapitalSensitivityResearchService.CapitalSensitivityResult
                result =
                service.evaluate(
                        strategyVersion,
                        "test-build-v1",
                        "SPY",
                        "1d",
                        new BigDecimal(
                                "100000.00"
                        ),
                        new BigDecimal(
                                "10000.00"
                        ),
                        new BigDecimal(
                                "1.00"
                        ),
                        new BigDecimal(
                                "5.00"
                        ),
                        "TEST",
                        "TEST",
                        "NONE"
                );

        /*
         * =====================================================
         * TUNING DATA ONLY
         * =====================================================
         */

        assertEquals(
                1,
                provider.requestCount(),
                "Capital sensitivity should perform exactly "
                        + "one market-data request"
        );

        assertEquals(
                startOfDay(
                        tuningStart
                ),
                provider.requestedFrom()
        );

        assertEquals(
                endExclusive(
                        tuningEnd
                ),
                provider.requestedTo()
        );

        assertEquals(
                3,
                result.marketDataCandleCount()
        );

        assertEquals(
                tuningStart,
                result.tuningPeriodStart()
        );

        assertEquals(
                tuningEnd,
                result.tuningPeriodEnd()
        );

        /*
         * =====================================================
         * CAPITAL LEVELS
         * =====================================================
         */

        assertEquals(
                new BigDecimal(
                        "100000.00"
                ),
                result.baselineCapital()
        );

        assertEquals(
                new BigDecimal(
                        "10000.00"
                ),
                result.plannedCapital()
        );

        /*
         * =====================================================
         * POSITION-SIZING SENSITIVITY
         * =====================================================
         *
         * Existing PositionSizingService allows at most 5%
         * account exposure in one position.
         *
         * Baseline $100,000:
         *
         *     maximum exposure = $5,000
         *
         * Planned $10,000:
         *
         *     maximum exposure = $500
         *
         * Closing prices:
         *
         *     $400  -> planned quantity 1
         *     $1000 -> planned quantity 0
         *     $2000 -> planned quantity 0
         */

        assertEquals(
                3,
                result.observations()
                        .size()
        );

        assertEquals(
                12,
                result.observations()
                        .get(0)
                        .baselineQuantity()
        );

        assertEquals(
                1,
                result.observations()
                        .get(0)
                        .plannedQuantity()
        );

        assertEquals(
                5,
                result.observations()
                        .get(1)
                        .baselineQuantity()
        );

        assertEquals(
                0,
                result.observations()
                        .get(1)
                        .plannedQuantity()
        );

        assertEquals(
                2,
                result.observations()
                        .get(2)
                        .baselineQuantity()
        );

        assertEquals(
                0,
                result.observations()
                        .get(2)
                        .plannedQuantity()
        );

        assertEquals(
                0,
                result.baselineZeroQuantityCount()
        );

        assertEquals(
                2,
                result.plannedZeroQuantityCount()
        );

        assertEquals(
                new BigDecimal(
                        "0.66666667"
                ),
                result.zeroQuantitySkipRate()
        );

        /*
         * =====================================================
         * RESEARCH RUN PERSISTENCE
         * =====================================================
         */

        RunRecord runRecord =
                result.runRecord();

        assertNotNull(
                runRecord
        );

        assertEquals(
                RunType.RESEARCH,
                runRecord.runType()
        );

        assertEquals(
                strategyVersion,
                runRecord.strategyVersion()
        );

        assertEquals(
                "SPY",
                runRecord.symbol()
        );

        assertEquals(
                "1d",
                runRecord.timeframe()
        );

        assertNull(
                runRecord.criteriaVersion(),
                "RESEARCH runs must not receive governed criteria"
        );

        assertTrue(
                runRecord.criteriaSnapshot()
                        .isEmpty(),
                "RESEARCH runs must not persist governed criteria"
        );

        assertEquals(
                1,
                runLedger.size()
        );

        assertEquals(
                runRecord,
                runLedger.requireById(
                        runRecord.runId()
                )
        );

        /*
         * The zero-quantity information must be persisted
         * with the research run.
         */

        assertEquals(
                2,
                runRecord.configurationSnapshot()
                        .get(
                                "plannedZeroQuantityCount"
                        )
        );

        assertEquals(
                new BigDecimal(
                        "0.66666667"
                ),
                runRecord.configurationSnapshot()
                        .get(
                                "zeroQuantitySkipRate"
                        )
        );

        assertEquals(
                "CAPITAL_SENSITIVITY",
                runRecord.configurationSnapshot()
                        .get(
                                "researchType"
                        )
        );

        /*
         * =====================================================
         * NO GOVERNED VERDICT
         * =====================================================
         */

        assertNull(
                result.governedVerdict(),
                "Capital-sensitivity research must not "
                        + "produce PASS or FAIL"
        );

        /*
         * =====================================================
         * VALIDATION / HOLDOUT REMAIN UNEXPOSED
         * =====================================================
         */

        ProtectedRunGuardRepository.GuardSnapshot
                validationAfter =
                guardRepository.requireGuard(
                        validationGuardId
                );

        ProtectedRunGuardRepository.GuardSnapshot
                holdoutAfter =
                guardRepository.requireGuard(
                        holdoutGuardId
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                validationAfter.state(),
                "Capital sensitivity must not acquire "
                        + "or expose validation"
        );

        assertEquals(
                ProtectedRunState.RESERVED,
                holdoutAfter.state(),
                "Capital sensitivity must not acquire "
                        + "or expose holdout"
        );

        assertNull(
                validationAfter
                        .marketDataFingerprint()
        );

        assertNull(
                holdoutAfter
                        .marketDataFingerprint()
        );

        assertEquals(
                validationLedgerCountBefore,
                ledgerEventCount(
                        validationGuardId
                ),
                "Capital sensitivity must not append "
                        + "validation exposure events"
        );

        assertEquals(
                holdoutLedgerCountBefore,
                ledgerEventCount(
                        holdoutGuardId
                ),
                "Capital sensitivity must not append "
                        + "holdout exposure events"
        );

        assertNull(
                guardRepository
                        .requireLatestLedgerSnapshot(
                                validationGuardId
                        )
                        .governedVerdict()
        );

        assertNull(
                guardRepository
                        .requireLatestLedgerSnapshot(
                                holdoutGuardId
                        )
                        .governedVerdict()
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

    private static MarketCandle candle(
            String closePrice,
            LocalDate date) {

        BigDecimal close =
                new BigDecimal(
                        closePrice
                );

        return new MarketCandle(
                "SPY",
                AssetType.STOCK,
                "1d",
                close.subtract(
                        BigDecimal.ONE
                ),
                close.add(
                        BigDecimal.ONE
                ),
                close.subtract(
                        new BigDecimal(
                                "2.00"
                        )
                ),
                close,
                new BigDecimal(
                        "100000"
                ),
                startOfDay(
                        date
                )
        );
    }

    private static Instant startOfDay(
            LocalDate date) {

        return UsEquityTradingCalendar
                .startOfTradingDate(
                        date
                );
    }

    private static Instant endExclusive(
            LocalDate date) {

        return UsEquityTradingCalendar
                .endExclusiveAfter(
                        date
                );
    }

    private static final class RecordingTuningMarketDataProvider
            implements MarketDataProvider {

        private final Instant expectedFrom;

        private final Instant expectedTo;

        private final List<MarketCandle> candles;

        private int requestCount;

        private Instant requestedFrom;

        private Instant requestedTo;

        private RecordingTuningMarketDataProvider(
                Instant expectedFrom,
                Instant expectedTo,
                List<MarketCandle> candles) {

            this.expectedFrom =
                    expectedFrom;

            this.expectedTo =
                    expectedTo;

            this.candles =
                    List.copyOf(
                            candles
                    );
        }

        @Override
        public List<MarketCandle> getHistoricalCandles(
                String symbol,
                int candleCount) {

            throw new AssertionError(
                    "Capital sensitivity must not use "
                            + "fixed-count market-data access"
            );
        }

        @Override
        public List<MarketCandle> getHistoricalCandles(
                String symbol,
                String timeframe,
                Instant from,
                Instant to) {

            requestCount++;

            requestedFrom =
                    from;

            requestedTo =
                    to;

            assertEquals(
                    "SPY",
                    symbol
            );

            assertEquals(
                    "1d",
                    timeframe
            );

            assertEquals(
                    expectedFrom,
                    from,
                    "Capital sensitivity must begin "
                            + "at tuning-period start"
            );

            assertEquals(
                    expectedTo,
                    to,
                    "Capital sensitivity must end "
                            + "at tuning-period end-exclusive"
            );

            return candles;
        }

        private int requestCount() {

            return requestCount;
        }

        private Instant requestedFrom() {

            return requestedFrom;
        }

        private Instant requestedTo() {

            return requestedTo;
        }
    }
}