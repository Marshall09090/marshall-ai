package com.marshallai;

import com.marshallai.market.MarketDataProviderType;
import com.marshallai.governance.exposure.ProtectedPeriodType;
import com.marshallai.governance.exposure.ProtectedRunGuardRepository;
import com.marshallai.governance.exposure.ProtectedTuningPeriodRepository;
import com.marshallai.governance.validation.ValidationMarketDataPreparationService;
import com.marshallai.market.AssetType;
import com.marshallai.market.MarketCandle;
import com.marshallai.market.MarketDataProvider;
import com.marshallai.market.protection.ProtectedMarketDataAccessContext;
import com.marshallai.market.protection.ProtectedMarketDataAuthorization;
import com.marshallai.market.protection.ProtectedMarketDataBoundary;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.marshallai.market.calendar.UsEquityTradingCalendar;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ValidationMarketDataPreparationServiceTest {

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

    private static ProtectedMarketDataBoundary
            boundary;

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

        boundary =
                ProtectedMarketDataBoundary.forJdbcTemplate(
                        jdbcTemplate
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
    void governedValidationPreparationUsesExactlyNewestTwoHundredWarmUpBars() {

        String strategyVersion =
                "warm-up-service-test-"
                        + UUID.randomUUID();

        LocalDate tuningStart =
                LocalDate.of(
                        2035,
                        1,
                        1
                );

        LocalDate tuningEnd =
                LocalDate.of(
                        2035,
                        12,
                        31
                );

        LocalDate validationStart =
                LocalDate.of(
                        2036,
                        1,
                        1
                );

        LocalDate validationEnd =
                LocalDate.of(
                        2036,
                        6,
                        30
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

        ProtectedMarketDataAuthorization authorization =
                acquireTarget(
                        validationGuardId
                );

        /*
         * Supply 205 tuning candles.
         *
         * Preparation must keep only the newest 200.
         */
        List<MarketCandle> tuningCandles =
                dailyCandles(
                        tuningEnd.minusDays(204),
                        205
                );

        /*
         * Three validation evaluation candles are enough to
         * prove that evaluation data remains separate from
         * warm-up-only history.
         */
        List<MarketCandle> validationCandles =
                dailyCandles(
                        validationStart,
                        3
                );

        RecordingMarketDataProvider provider =
                new RecordingMarketDataProvider(
                        startOfDay(
                                tuningStart
                        ),
                        endExclusive(
                                tuningEnd
                        ),
                        tuningCandles,
                        startOfDay(
                                validationStart
                        ),
                        endExclusive(
                                validationEnd
                        ),
                        validationCandles
                );

        ValidationMarketDataPreparationService service =
                new ValidationMarketDataPreparationService(
                        provider,
                        boundary,
                        tuningPeriodRepository
                );

        ValidationMarketDataPreparationService.PreparedValidationDataset
                dataset =
                service.prepare(
                        strategyVersion,
                        "SPY",
                        "1d",
                        200,
                        validationStart,
                        validationEnd,
                        authorization
                );

        assertEquals(
                200,
                dataset.warmUpCandles().size(),
                "Exactly 200 tuning candles must be classified as warm-up"
        );

        assertEquals(
                3,
                dataset.evaluationCandles().size(),
                "Validation candles must remain evaluation candles"
        );

        assertEquals(
                203,
                dataset.allCandles().size(),
                "Prepared dataset should contain warm-up plus evaluation candles"
        );

        /*
         * We supplied 205 tuning candles.
         *
         * The first five must therefore be discarded and the
         * newest 200 retained.
         */
        assertEquals(
                tuningCandles.get(5).timestamp(),
                dataset.warmUpCandles()
                        .get(0)
                        .timestamp()
        );

        assertEquals(
                tuningCandles
                        .get(
                                tuningCandles.size() - 1
                        )
                        .timestamp(),
                dataset.warmUpCandles()
                        .get(
                                dataset.warmUpCandles()
                                        .size() - 1
                        )
                        .timestamp()
        );

        assertEquals(
                validationStart,
                dataset.validationStart(),
                "Warm-up preparation must not move validation start"
        );

        assertEquals(
                validationEnd,
                dataset.validationEnd(),
                "Warm-up preparation must not move validation end"
        );

        long markedWarmUp =
                dataset.candles()
                        .stream()
                        .filter(
                                candle ->
                                        candle.usage()
                                                == ValidationMarketDataPreparationService
                                                .CandleUsage
                                                .WARM_UP_ONLY
                        )
                        .count();

        assertEquals(
                200,
                markedWarmUp,
                "Every selected warm-up candle must be WARM_UP_ONLY"
        );

        assertEquals(
                2,
                provider.rangeRequestCount(),
                "Preparation must perform one tuning request "
                        + "and one validation request"
        );
    }

    @Test
    void warmUpSignalsAreNeverEligibleForValidationTrades() {

        String strategyVersion =
                "warm-up-eligibility-test-"
                        + UUID.randomUUID();

        LocalDate tuningStart =
                LocalDate.of(
                        2040,
                        1,
                        1
                );

        LocalDate tuningEnd =
                LocalDate.of(
                        2040,
                        12,
                        31
                );

        LocalDate validationStart =
                LocalDate.of(
                        2041,
                        1,
                        1
                );

        LocalDate validationEnd =
                LocalDate.of(
                        2041,
                        6,
                        30
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

        ProtectedMarketDataAuthorization authorization =
                acquireTarget(
                        validationGuardId
                );

        List<MarketCandle> tuningCandles =
                dailyCandles(
                        tuningEnd.minusDays(199),
                        200
                );

        List<MarketCandle> validationCandles =
                dailyCandles(
                        validationStart,
                        5
                );

        RecordingMarketDataProvider provider =
                new RecordingMarketDataProvider(
                        startOfDay(
                                tuningStart
                        ),
                        endExclusive(
                                tuningEnd
                        ),
                        tuningCandles,
                        startOfDay(
                                validationStart
                        ),
                        endExclusive(
                                validationEnd
                        ),
                        validationCandles
                );

        ValidationMarketDataPreparationService service =
                new ValidationMarketDataPreparationService(
                        provider,
                        boundary,
                        tuningPeriodRepository
                );

        ValidationMarketDataPreparationService.PreparedValidationDataset
                dataset =
                service.prepare(
                        strategyVersion,
                        "SPY",
                        "1d",
                        200,
                        validationStart,
                        validationEnd,
                        authorization
                );

        Instant warmUpSignalTimestamp =
                dataset.warmUpCandles()
                        .get(
                                dataset.warmUpCandles()
                                        .size() - 1
                        )
                        .timestamp();

        Instant validationSignalTimestamp =
                dataset.evaluationCandles()
                        .get(0)
                        .timestamp();

        assertFalse(
                dataset.isSignalEligibleForValidation(
                        warmUpSignalTimestamp
                ),
                "A signal produced by a WARM_UP_ONLY candle "
                        + "must never create a validation trade"
        );

        assertTrue(
                dataset.isSignalEligibleForValidation(
                        validationSignalTimestamp
                ),
                "A signal produced inside the validation "
                        + "evaluation period should be eligible"
        );

        /*
         * A timestamp that is not represented by an evaluation
         * candle must also fail closed.
         */
        assertFalse(
                dataset.isSignalEligibleForValidation(
                        UsEquityTradingCalendar
                                .startOfTradingDate(
                                        tuningStart.minusDays(1)
                                )
                )
        );
    }

    private static ProtectedMarketDataAuthorization
    acquireTarget(
            long guardId) {

        UUID runId =
                UUID.randomUUID();

        UUID instanceId =
                UUID.randomUUID();

        String configurationHash =
                "warm-up-config-"
                        + UUID.randomUUID();

        ProtectedRunGuardRepository.AcquisitionResult
                acquisition =
                guardRepository.acquireReservedTarget(
                        guardId,
                        runId,
                        instanceId,
                        configurationHash,
                        MarketDataProviderType.REAL,
                        TEST_TIMESTAMP.plusSeconds(
                                60
                        )
                );

        assertTrue(
                acquisition.acquired(),
                "Validation target acquisition must succeed"
        );

        return new ProtectedMarketDataAuthorization(
                guardId,
                runId,
                instanceId,
                configurationHash
        );
    }

    private static List<MarketCandle> dailyCandles(
            LocalDate firstDate,
            int count) {

        List<MarketCandle> candles =
                new ArrayList<>();

        for (int index = 0;
             index < count;
             index++) {

            BigDecimal price =
                    new BigDecimal(
                            "100.00"
                    ).add(
                            BigDecimal.valueOf(
                                    index
                            )
                    );

            candles.add(
                    new MarketCandle(
                            "SPY",
                            AssetType.STOCK,
                            "1d",
                            price,
                            price.add(
                                    BigDecimal.ONE
                            ),
                            price.subtract(
                                    BigDecimal.ONE
                            ),
                            price,
                            new BigDecimal(
                                    "100000"
                            ),
                            UsEquityTradingCalendar
                                    .startOfTradingDate(
                                            firstDate.plusDays(
                                                    index
                                            )
                                    )
                    )
            );
        }

        return List.copyOf(
                candles
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

    private static final class RecordingMarketDataProvider
            implements MarketDataProvider {

        private final Instant tuningFrom;

        private final Instant tuningTo;

        private final List<MarketCandle>
                tuningCandles;

        private final Instant validationFrom;

        private final Instant validationTo;

        private final List<MarketCandle>
                validationCandles;

        private int rangeRequestCount;

        private RecordingMarketDataProvider(
                Instant tuningFrom,
                Instant tuningTo,
                List<MarketCandle> tuningCandles,
                Instant validationFrom,
                Instant validationTo,
                List<MarketCandle> validationCandles) {

            this.tuningFrom =
                    tuningFrom;

            this.tuningTo =
                    tuningTo;

            this.tuningCandles =
                    List.copyOf(
                            tuningCandles
                    );

            this.validationFrom =
                    validationFrom;

            this.validationTo =
                    validationTo;

            this.validationCandles =
                    List.copyOf(
                            validationCandles
                    );
        }

        @Override
        public List<MarketCandle> getHistoricalCandles(
                String symbol,
                int candleCount) {

            throw new UnsupportedOperationException(
                    "Fixed-count requests are not used "
                            + "by validation preparation"
            );
        }

        @Override
        public List<MarketCandle> getHistoricalCandles(
                String symbol,
                String timeframe,
                Instant from,
                Instant to) {

            rangeRequestCount++;

            assertEquals(
                    "SPY",
                    symbol
            );

            assertEquals(
                    "1d",
                    timeframe
            );

            if (from.equals(
                    tuningFrom
            ) && to.equals(
                    tuningTo
            )) {

                return tuningCandles;
            }

            if (from.equals(
                    validationFrom
            ) && to.equals(
                    validationTo
            )) {

                return validationCandles;
            }

            fail(
                    "Unexpected market-data request range: "
                            + from
                            + " -> "
                            + to
            );

            return List.of();
        }

        private int rangeRequestCount() {

            return rangeRequestCount;
        }
    }
}