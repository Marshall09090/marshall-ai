package com.marshallai;

import com.marshallai.market.MarketDataProviderType;
import com.marshallai.governance.exposure.ProtectedHoldoutAuthorizationRepository;
import com.marshallai.governance.exposure.ProtectedPeriodType;
import com.marshallai.governance.exposure.ProtectedRunGuardRepository;
import com.marshallai.market.AlpacaMarketDataProvider;
import com.marshallai.market.MarketCandle;
import com.marshallai.market.calendar.UsEquityTradingCalendar;
import com.marshallai.market.protection.ProtectedMarketDataAccessContext;
import com.marshallai.market.protection.ProtectedMarketDataAccessException;
import com.marshallai.market.protection.ProtectedMarketDataAuthorization;
import com.marshallai.market.protection.ProtectedMarketDataBoundary;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AlpacaMarketDataProviderTest {

    private static PostgreSQLContainer postgres;

    private static ProtectedRunGuardRepository guardRepository;

    private static ProtectedHoldoutAuthorizationRepository
            holdoutAuthorizationRepository;

    private static ProtectedMarketDataBoundary boundary;

    /*
     * Start a real PostgreSQL database and apply the
     * production Flyway migrations.
     */
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

        JdbcTemplate jdbcTemplate =
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

        holdoutAuthorizationRepository =
                new ProtectedHoldoutAuthorizationRepository(
                        jdbcTemplate,
                        transactionManager
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

    /*
     * =====================================================
     * EXISTING ALPACA CONFIGURATION TESTS
     * =====================================================
     */

    @Test
    void fixedCountAnalysisShouldUseDailyCandles()
            throws Exception {

        Field timeframeField =
                AlpacaMarketDataProvider.class
                        .getDeclaredField(
                                "DEFAULT_ANALYSIS_TIMEFRAME"
                        );

        timeframeField.setAccessible(
                true
        );

        String timeframe =
                (String) timeframeField.get(
                        null
                );

        assertEquals(
                "1d",
                timeframe,
                "Fixed-count market analysis must use daily candles"
        );
    }

    @Test
    void historicalRequestsShouldUseSplitAdjustedPrices()
            throws Exception {

        Field adjustmentField =
                AlpacaMarketDataProvider.class
                        .getDeclaredField(
                                "BAR_ADJUSTMENT"
                        );

        adjustmentField.setAccessible(
                true
        );

        String adjustment =
                (String) adjustmentField.get(
                        null
                );

        assertEquals(
                "split",
                adjustment,
                "Historical Alpaca bars must use split-adjusted prices"
        );
    }

    @Test
    void fixedCountAnalysisShouldUseEnoughCalendarHistoryForDailyBars()
            throws Exception {

        Field lookbackField =
                AlpacaMarketDataProvider.class
                        .getDeclaredField(
                                "MINIMUM_LOOKBACK_DAYS"
                        );

        lookbackField.setAccessible(
                true
        );

        long minimumLookbackDays =
                lookbackField.getLong(
                        null
                );

        assertEquals(
                400L,
                minimumLookbackDays,
                "Daily analysis should request enough history for 200+ trading bars"
        );
    }

    /*
     * =====================================================
     * UNGOVERNED VALIDATION ACCESS
     * =====================================================
     */

    @Test
    void directProviderMustRejectProtectedValidationBeforeHttp() {

        LocalDate start =
                LocalDate.of(
                        2035,
                        1,
                        1
                );

        LocalDate end =
                LocalDate.of(
                        2035,
                        1,
                        31
                );

        reserveTarget(
                ProtectedPeriodType.VALIDATION,
                start,
                end
        );

        MockProvider mock =
                createMockProvider();

        ProtectedMarketDataAccessException exception =
                assertThrows(
                        ProtectedMarketDataAccessException.class,
                        () ->
                                mock.provider()
                                        .getHistoricalCandles(
                                                "SPY",
                                                "1d",
                                                startOfDay(
                                                        start
                                                ),
                                                endExclusive(
                                                        end
                                                )
                                        )
                );

        assertEquals(
                ProtectedMarketDataBoundary.PROTECTED_VALIDATION_PERIOD,
                exception.getRefusalReason()
        );

        mock.server().verify();
    }

    /*
     * =====================================================
     * UNGOVERNED HOLDOUT ACCESS
     * =====================================================
     */

    @Test
    void directProviderMustRejectProtectedHoldoutBeforeHttp() {

        LocalDate start =
                LocalDate.of(
                        2036,
                        1,
                        1
                );

        LocalDate end =
                LocalDate.of(
                        2036,
                        1,
                        31
                );

        reserveTarget(
                ProtectedPeriodType.HOLDOUT,
                start,
                end
        );

        MockProvider mock =
                createMockProvider();

        ProtectedMarketDataAccessException exception =
                assertThrows(
                        ProtectedMarketDataAccessException.class,
                        () ->
                                mock.provider()
                                        .getHistoricalCandles(
                                                "SPY",
                                                "1d",
                                                startOfDay(
                                                        start
                                                ),
                                                endExclusive(
                                                        end
                                                )
                                        )
                );

        assertEquals(
                ProtectedMarketDataBoundary.PROTECTED_HOLDOUT_PERIOD,
                exception.getRefusalReason()
        );

        mock.server().verify();
    }

    /*
     * =====================================================
     * GOVERNED VALIDATION ACCESS
     * =====================================================
     */

    @Test
    void governedValidationCanRetrieveAuthorizedCandles() {

        LocalDate start =
                LocalDate.of(
                        2037,
                        1,
                        1
                );

        LocalDate end =
                LocalDate.of(
                        2037,
                        1,
                        31
                );

        long guardId =
                reserveTarget(
                        ProtectedPeriodType.VALIDATION,
                        start,
                        end
                );

        ProtectedMarketDataAuthorization authorization =
                acquireTarget(
                        guardId
                );

        MockProvider mock =
                createMockProvider();

        Instant firstCandle =
                Instant.parse(
                        "2037-01-05T00:00:00Z"
                );

        Instant secondCandle =
                Instant.parse(
                        "2037-01-20T00:00:00Z"
                );

        mock.server()
                .expect(request -> {

                    assertEquals(
                            "/v2/stocks/SPY/bars",
                            request.getURI()
                                    .getPath()
                    );

                    String query =
                            request.getURI()
                                    .getRawQuery();

                    assertNotNull(
                            query
                    );

                    assertTrue(
                            query.contains(
                                    "adjustment=split"
                            )
                    );

                    assertTrue(
                            query.contains(
                                    "feed=iex"
                            )
                    );
                })
                .andRespond(
                        withSuccess(
                                barsResponse(
                                        firstCandle,
                                        secondCandle
                                ),
                                MediaType.APPLICATION_JSON
                        )
                );

        try (ProtectedMarketDataAccessContext.AuthorizationScope
                     scope =
                     ProtectedMarketDataAccessContext.authorize(
                             authorization
                     )) {

            List<MarketCandle> candles =
                    mock.provider()
                            .getHistoricalCandles(
                                    "SPY",
                                    "1d",
                                    startOfDay(
                                            start
                                    ),
                                    endExclusive(
                                            end
                                    )
                            );

            assertEquals(
                    2,
                    candles.size()
            );

            assertEquals(
                    firstCandle,
                    candles.get(0)
                            .timestamp()
            );

            assertEquals(
                    secondCandle,
                    candles.get(1)
                            .timestamp()
            );

            for (MarketCandle candle :
                    candles) {

                assertFalse(
                        candle.timestamp()
                                .isBefore(
                                        startOfDay(
                                                start
                                        )
                                )
                );

                assertTrue(
                        candle.timestamp()
                                .isBefore(
                                        endExclusive(
                                                end
                                        )
                                )
                );
            }
        }

        mock.server().verify();
    }

    /*
     * =====================================================
     * HOLDOUT WITHOUT EXPLICIT AUTHORIZATION
     * =====================================================
     */

    @Test
    void acquiredHoldoutWithoutExplicitAuthorizationMustBeRefused() {

        LocalDate start =
                LocalDate.of(
                        2038,
                        1,
                        1
                );

        LocalDate end =
                LocalDate.of(
                        2038,
                        1,
                        31
                );

        long guardId =
                reserveTarget(
                        ProtectedPeriodType.HOLDOUT,
                        start,
                        end
                );

        ProtectedMarketDataAuthorization authorization =
                acquireTarget(
                        guardId
                );

        MockProvider mock =
                createMockProvider();

        try (ProtectedMarketDataAccessContext.AuthorizationScope
                     scope =
                     ProtectedMarketDataAccessContext.authorize(
                             authorization
                     )) {

            ProtectedMarketDataAccessException exception =
                    assertThrows(
                            ProtectedMarketDataAccessException.class,
                            () ->
                                    mock.provider()
                                            .getHistoricalCandles(
                                                    "SPY",
                                                    "1d",
                                                    startOfDay(
                                                            start
                                                    ),
                                                    endExclusive(
                                                            end
                                                    )
                                            )
                    );

            assertEquals(
                    ProtectedMarketDataBoundary.HOLDOUT_AUTHORIZATION_REQUIRED,
                    exception.getRefusalReason()
            );
        }

        mock.server().verify();
    }

    /*
     * =====================================================
     * EXPLICITLY AUTHORIZED HOLDOUT
     * =====================================================
     */

    @Test
    void explicitlyAuthorizedHoldoutCanRetrieveItsOwnCandles() {

        LocalDate start =
                LocalDate.of(
                        2039,
                        1,
                        1
                );

        LocalDate end =
                LocalDate.of(
                        2039,
                        1,
                        31
                );

        long guardId =
                reserveTarget(
                        ProtectedPeriodType.HOLDOUT,
                        start,
                        end
                );

        holdoutAuthorizationRepository
                .grantExplicitAuthorization(
                        guardId,
                        Instant.parse(
                                "2026-10-07T12:00:00Z"
                        )
                );

        ProtectedMarketDataAuthorization authorization =
                acquireTarget(
                        guardId
                );

        MockProvider mock =
                createMockProvider();

        Instant candleTimestamp =
                Instant.parse(
                        "2039-01-10T00:00:00Z"
                );

        mock.server()
                .expect(request ->
                        assertEquals(
                                "/v2/stocks/SPY/bars",
                                request.getURI()
                                        .getPath()
                        )
                )
                .andRespond(
                        withSuccess(
                                barsResponse(
                                        candleTimestamp
                                ),
                                MediaType.APPLICATION_JSON
                        )
                );

        try (ProtectedMarketDataAccessContext.AuthorizationScope
                     scope =
                     ProtectedMarketDataAccessContext.authorize(
                             authorization
                     )) {

            List<MarketCandle> candles =
                    mock.provider()
                            .getHistoricalCandles(
                                    "SPY",
                                    "1d",
                                    startOfDay(
                                            start
                                    ),
                                    endExclusive(
                                            end
                                    )
                            );

            assertEquals(
                    1,
                    candles.size()
            );

            assertEquals(
                    candleTimestamp,
                    candles.getFirst()
                            .timestamp()
            );
        }

        mock.server().verify();
    }

    /*
     * =====================================================
     * RESPONSE CANNOT EXCEED AUTHORIZED PERIOD
     * =====================================================
     */

    @Test
    void providerMustRejectCandlesOutsideAuthorizedRequestRange() {

        LocalDate start =
                LocalDate.of(
                        2040,
                        1,
                        1
                );

        LocalDate end =
                LocalDate.of(
                        2040,
                        1,
                        31
                );

        long guardId =
                reserveTarget(
                        ProtectedPeriodType.VALIDATION,
                        start,
                        end
                );

        ProtectedMarketDataAuthorization authorization =
                acquireTarget(
                        guardId
                );

        MockProvider mock =
                createMockProvider();

        mock.server()
                .expect(request ->
                        assertEquals(
                                "/v2/stocks/SPY/bars",
                                request.getURI()
                                        .getPath()
                        )
                )
                .andRespond(
                        withSuccess(
                                barsResponse(
                                        Instant.parse(
                                                "2040-02-01T05:00:00Z"
                                        )
                                ),
                                MediaType.APPLICATION_JSON
                        )
                );

        try (ProtectedMarketDataAccessContext.AuthorizationScope
                     scope =
                     ProtectedMarketDataAccessContext.authorize(
                             authorization
                     )) {

            IllegalStateException exception =
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    mock.provider()
                                            .getHistoricalCandles(
                                                    "SPY",
                                                    "1d",
                                                    startOfDay(
                                                            start
                                                    ),
                                                    endExclusive(
                                                            end
                                                    )
                                            )
                    );

            assertTrue(
                    exception.getMessage()
                            .contains(
                                    "outside the authorized request range"
                            )
            );
        }

        mock.server().verify();
    }

    /*
     * =====================================================
     * DAILY EXCLUSIVE-END TO ALPACA INCLUSIVE-END MAPPING
     * =====================================================
     */

    @Test
    void dailyMondayExclusiveEndShouldMapToPreviousFriday() {

        MockProvider mock =
                createMockProvider();

        LocalDate startDate =
                LocalDate.of(
                        2026,
                        9,
                        28
                );

        LocalDate exclusiveMonday =
                LocalDate.of(
                        2026,
                        10,
                        5
                );

        Instant from =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                startDate
                        );

        Instant to =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                exclusiveMonday
                        );

        Instant fridayCandle =
                Instant.parse(
                        "2026-10-02T04:00:00Z"
                );

        mock.server()
                .expect(request -> {

                    assertEquals(
                            "/v2/stocks/SPY/bars",
                            request.getURI()
                                    .getPath()
                    );

                    String query =
                            request.getURI()
                                    .getRawQuery();

                    assertNotNull(
                            query
                    );

                    assertTrue(
                            query.contains(
                                    "timeframe=1Day"
                            )
                    );

                    assertTrue(
                            query.contains(
                                    "end=2026-10-02"
                            ),
                            "Monday exclusive end must map to previous Friday"
                    );

                    assertFalse(
                            query.contains(
                                    "end=2026-10-05"
                            ),
                            "Exclusive Monday must not be sent as Alpaca's inclusive end"
                    );
                })
                .andRespond(
                        withSuccess(
                                barsResponse(
                                        fridayCandle
                                ),
                                MediaType.APPLICATION_JSON
                        )
                );

        List<MarketCandle> candles =
                mock.provider()
                        .getHistoricalCandles(
                                "SPY",
                                "1d",
                                from,
                                to
                        );

        assertEquals(
                1,
                candles.size()
        );

        assertEquals(
                fridayCandle,
                candles.getFirst()
                        .timestamp()
        );

        mock.server().verify();
    }

    @Test
    void dailyEndAfterObservedHolidayShouldMapToPreviousTradingDate() {

        MockProvider mock =
                createMockProvider();

        LocalDate startDate =
                LocalDate.of(
                        2026,
                        6,
                        29
                );

        /*
         * July 4, 2026 is Saturday.
         *
         * Friday July 3 is therefore the observed
         * Independence Day market holiday.
         *
         * An exclusive Monday July 6 boundary must map
         * to Thursday July 2.
         */
        LocalDate exclusiveMonday =
                LocalDate.of(
                        2026,
                        7,
                        6
                );

        Instant from =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                startDate
                        );

        Instant to =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                exclusiveMonday
                        );

        Instant thursdayCandle =
                Instant.parse(
                        "2026-07-02T04:00:00Z"
                );

        mock.server()
                .expect(request -> {

                    assertEquals(
                            "/v2/stocks/SPY/bars",
                            request.getURI()
                                    .getPath()
                    );

                    String query =
                            request.getURI()
                                    .getRawQuery();

                    assertNotNull(
                            query
                    );

                    assertTrue(
                            query.contains(
                                    "end=2026-07-02"
                            ),
                            "Exclusive end after the observed holiday "
                                    + "must map to Thursday July 2"
                    );

                    assertFalse(
                            query.contains(
                                    "end=2026-07-03"
                            ),
                            "Observed market holiday must not be used as Alpaca end"
                    );

                    assertFalse(
                            query.contains(
                                    "end=2026-07-06"
                            ),
                            "Exclusive Monday must not be sent as Alpaca end"
                    );
                })
                .andRespond(
                        withSuccess(
                                barsResponse(
                                        thursdayCandle
                                ),
                                MediaType.APPLICATION_JSON
                        )
                );

        List<MarketCandle> candles =
                mock.provider()
                        .getHistoricalCandles(
                                "SPY",
                                "1d",
                                from,
                                to
                        );

        assertEquals(
                1,
                candles.size()
        );

        assertEquals(
                thursdayCandle,
                candles.getFirst()
                        .timestamp()
        );

        mock.server().verify();
    }

    /*
     * =====================================================
     * SHARED TEST HELPERS
     * =====================================================
     */

    private static MockProvider createMockProvider() {

        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl(
                                "https://mock-alpaca.invalid"
                        );

        MockRestServiceServer server =
                MockRestServiceServer.bindTo(
                        builder
                ).build();

        AlpacaMarketDataProvider provider =
                new AlpacaMarketDataProvider(
                        new ObjectMapper(),
                        boundary,
                        builder.build()
                );

        return new MockProvider(
                provider,
                server
        );
    }

    private static long reserveTarget(
            ProtectedPeriodType periodType,
            LocalDate start,
            LocalDate end) {

        return guardRepository.createReservation(
                "alpaca-provider-test-"
                        + UUID.randomUUID(),
                periodType.name()
                        .toLowerCase()
                        + "-"
                        + UUID.randomUUID(),
                periodType,
                start,
                end,
                Instant.parse(
                        "2026-10-07T12:00:00Z"
                )
        );
    }

    private static ProtectedMarketDataAuthorization acquireTarget(
            long guardId) {

        UUID runId =
                UUID.randomUUID();

        UUID instanceId =
                UUID.randomUUID();

        String configurationHash =
                "alpaca-provider-config-"
                        + UUID.randomUUID();

        ProtectedRunGuardRepository.AcquisitionResult acquisition =
                guardRepository.acquireReservedTarget(
                        guardId,
                        runId,
                        instanceId,
                        configurationHash,
                        MarketDataProviderType.REAL,
                        Instant.parse(
                                "2026-10-07T12:01:00Z"
                        )
                );

        assertTrue(
                acquisition.acquired(),
                "Protected target acquisition must succeed"
        );

        return new ProtectedMarketDataAuthorization(
                guardId,
                runId,
                instanceId,
                configurationHash
        );
    }

    /*
     * These existing helpers remain UTC temporarily.
     *
     * In Step 2B2 we will replace them together with
     * ProtectedMarketDataBoundary so the governance layer
     * moves atomically to America/New_York semantics.
     */
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

    /*
     * Generates controlled Alpaca-format historical bars.
     *
     * These are test HTTP responses, not real market data.
     */
    private static String barsResponse(
            Instant... timestamps) {

        StringBuilder json =
                new StringBuilder(
                        "{\"bars\":["
                );

        for (int index = 0;
             index < timestamps.length;
             index++) {

            if (index > 0) {

                json.append(
                        ","
                );
            }

            json.append(
                    """
                    {
                        "o": 100.00,
                        "h": 105.00,
                        "l": 98.00,
                        "c": 103.00,
                        "v": 100000,
                        "t": "
                    """
                            .trim()
            );

            json.append(
                    timestamps[index]
            );

            json.append(
                    "\"}"
            );
        }

        json.append(
                "],\"next_page_token\":null}"
        );

        return json.toString();
    }

    private record MockProvider(
            AlpacaMarketDataProvider provider,
            MockRestServiceServer server) {
    }
}