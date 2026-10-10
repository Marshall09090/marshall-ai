package com.marshallai.market;

import com.marshallai.market.calendar.UsEquityTradingCalendar;
import com.marshallai.market.protection.ProtectedMarketDataBoundary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@Profile("alpaca")
public class AlpacaMarketDataProvider
        implements MarketDataProviderSource {

    private static final String BASE_URL =
            "https://data.alpaca.markets";

    private static final String DATA_FEED =
            "iex";

    private static final String BAR_ADJUSTMENT =
            "split";

    private static final int MAX_PAGE_SIZE =
            10_000;

    private static final String DEFAULT_ANALYSIS_TIMEFRAME =
            "1d";

    private static final long MINIMUM_LOOKBACK_DAYS =
            400L;

    private final RestClient restClient;

    private final ObjectMapper objectMapper;

    private final ProtectedMarketDataBoundary
            protectedMarketDataBoundary;

    /*
     * Production constructor.
     *
     * Spring uses this constructor when the Alpaca profile
     * is active.
     *
     * Real credentials are read from environment variables.
     *
     * ProtectedMarketDataBoundary remains mandatory.
     */
    @Autowired
    public AlpacaMarketDataProvider(
            ObjectMapper objectMapper,
            ProtectedMarketDataBoundary protectedMarketDataBoundary) {

        this(
                objectMapper,
                protectedMarketDataBoundary,
                createAlpacaRestClient()
        );
    }

    /*
     * Existing compatibility constructor.
     *
     * Without a supplied durable governance boundary,
     * real market-data access must fail closed.
     */
    public AlpacaMarketDataProvider(
            ObjectMapper objectMapper) {

        this(
                objectMapper,
                ProtectedMarketDataBoundary
                        .failClosedWithoutDurableStore()
        );
    }

    /*
     * Explicit HTTP-client injection.
     *
     * Integration tests can supply a controlled RestClient
     * without contacting Alpaca or requiring API credentials.
     *
     * The authorization boundary is still mandatory.
     *
     * No HTTP client can bypass the protected-date check
     * through this constructor.
     */
    public AlpacaMarketDataProvider(
            ObjectMapper objectMapper,
            ProtectedMarketDataBoundary protectedMarketDataBoundary,
            RestClient restClient) {

        if (objectMapper == null) {

            throw new IllegalArgumentException(
                    "ObjectMapper cannot be null"
            );
        }

        if (protectedMarketDataBoundary == null) {

            throw new IllegalArgumentException(
                    "Protected market-data boundary cannot be null"
            );
        }

        if (restClient == null) {

            throw new IllegalArgumentException(
                    "RestClient cannot be null"
            );
        }

        this.objectMapper =
                objectMapper;

        this.protectedMarketDataBoundary =
                protectedMarketDataBoundary;

        this.restClient =
                restClient;
    }

    /*
     * Production Alpaca HTTP configuration.
     *
     * IEX feed and split-adjusted pricing remain unchanged.
     */
    private static RestClient createAlpacaRestClient() {

        String apiKey =
                requireEnvironmentVariable(
                        "ALPACA_API_KEY"
                );

        String secretKey =
                requireEnvironmentVariable(
                        "ALPACA_SECRET_KEY"
                );

        return RestClient.builder()
                .baseUrl(
                        BASE_URL
                )
                .defaultHeader(
                        "APCA-API-KEY-ID",
                        apiKey
                )
                .defaultHeader(
                        "APCA-API-SECRET-KEY",
                        secretKey
                )
                .build();
    }

    /*
     * Fixed-count historical market-data request.
     *
     * Uses completed daily candles.
     *
     * The request delegates to the explicit range method,
     * which enforces protected-date governance.
     */
    @Override
    public List<MarketCandle> getHistoricalCandles(
            String symbol,
            int candleCount) {

        validateSymbol(
                symbol
        );

        if (candleCount <= 0) {

            throw new IllegalArgumentException(
                    "Candle count must be greater than zero"
            );
        }

        /*
         * Daily market-data ranges are defined by the
         * US-equity calendar in America/New_York.
         *
         * The beginning of the current New York calendar date
         * is the exclusive upper boundary.
         *
         * This prevents the current day's still-forming daily
         * candle from entering fixed-count analysis.
         */
        LocalDate currentMarketDate =
                LocalDate.now(
                        UsEquityTradingCalendar.MARKET_ZONE
                );

        Instant to =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                currentMarketDate
                        );

        long requestedLookbackDays =
                Math.max(
                        candleCount * 2L,
                        MINIMUM_LOOKBACK_DAYS
                );

        LocalDate fromMarketDate =
                currentMarketDate.minusDays(
                        requestedLookbackDays
                );

        Instant from =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                fromMarketDate
                        );

        /*
         * IMPORTANT:
         *
         * Always delegate through the governed range method.
         *
         * Otherwise fixed-count requests could become
         * an alternative entry point around date protection.
         */
        List<MarketCandle> candles =
                getHistoricalCandles(
                        symbol,
                        DEFAULT_ANALYSIS_TIMEFRAME,
                        from,
                        to
                );

        if (candles.isEmpty()) {

            throw new IllegalStateException(
                    "Alpaca returned no market candles for symbol: "
                            + symbol
            );
        }

        if (candles.size() < candleCount) {

            throw new IllegalStateException(
                    "Alpaca returned insufficient completed daily candles "
                            + "for symbol "
                            + symbol.trim()
                            .toUpperCase(
                                    Locale.ROOT
                            )
                            + ". Requested "
                            + candleCount
                            + " but received "
                            + candles.size()
            );
        }

        return List.copyOf(
                candles.subList(
                        candles.size() - candleCount,
                        candles.size()
                )
        );
    }

    /*
     * Explicit historical market-data request.
     *
     * MarshallAI range semantics are:
     *
     *     [from, to)
     *
     * The internal exclusive end is preserved through
     * authorization and response validation.
     *
     * The Alpaca HTTP adapter translates that range into
     * Alpaca's inclusive end semantics only at the final
     * external-provider boundary.
     */
    @Override
    public List<MarketCandle> getHistoricalCandles(
            String symbol,
            String timeframe,
            Instant from,
            Instant to) {

        validateRangeRequest(
                symbol,
                timeframe,
                from,
                to
        );

        /*
         * ====================================================
         * PROTECTED MARKET-DATA AUTHORIZATION
         * ====================================================
         *
         * This must remain before requestBarsPage().
         *
         * Authorization always sees MarshallAI's original
         * [from, to) range.
         *
         * The provider-specific inclusive-end translation
         * happens only after authorization.
         */
        protectedMarketDataBoundary
                .authorizeRealMarketDataRequest(
                        from,
                        to
                );

        String normalizedSymbol =
                symbol.trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        String normalizedTimeframe =
                timeframe.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        String alpacaTimeframe =
                toAlpacaTimeframe(
                        normalizedTimeframe
                );

        List<MarketCandle> candles =
                new ArrayList<>();

        String pageToken =
                null;

        do {

            String response =
                    requestBarsPage(
                            normalizedSymbol,
                            alpacaTimeframe,
                            from,
                            to,
                            pageToken
                    );

            JsonNode root =
                    parseResponse(
                            response
                    );

            JsonNode bars =
                    root.path(
                            "bars"
                    );

            if (bars.isArray()) {

                for (JsonNode bar : bars) {

                    MarketCandle candle =
                            toMarketCandle(
                                    normalizedSymbol,
                                    normalizedTimeframe,
                                    bar
                            );

                    /*
                     * =================================================
                     * RESPONSE RANGE ENFORCEMENT
                     * =================================================
                     *
                     * The external provider may use different
                     * query-boundary semantics, but returned data
                     * must still satisfy MarshallAI's authorized:
                     *
                     *     [from, to)
                     *
                     * An out-of-range response fails closed.
                     */
                    requireCandleInsideAuthorizedRange(
                            candle,
                            from,
                            to
                    );

                    candles.add(
                            candle
                    );
                }
            }

            JsonNode tokenNode =
                    root.get(
                            "next_page_token"
                    );

            if (tokenNode == null
                    || tokenNode.isNull()
                    || tokenNode.asText().isBlank()) {

                pageToken =
                        null;

            } else {

                pageToken =
                        tokenNode.asText();
            }

        } while (pageToken != null);

        /*
         * Every returned candle has passed the request-range
         * check before entering this final result.
         */
        candles.sort(
                Comparator.comparing(
                        MarketCandle::timestamp
                )
        );

        return List.copyOf(
                candles
        );
    }

    /*
     * Enforces the exact authorized request range against
     * each returned candle.
     *
     * A provider response cannot extend access into
     * another validation or holdout period.
     */
    private void requireCandleInsideAuthorizedRange(
            MarketCandle candle,
            Instant from,
            Instant to) {

        if (candle == null) {

            throw new IllegalStateException(
                    "Alpaca returned a null market candle"
            );
        }

        Instant timestamp =
                candle.timestamp();

        if (timestamp == null) {

            throw new IllegalStateException(
                    "Alpaca returned a candle without a timestamp"
            );
        }

        if (timestamp.isBefore(from)
                || !timestamp.isBefore(to)) {

            throw new IllegalStateException(
                    "Alpaca returned a market candle outside "
                            + "the authorized request range: "
                            + timestamp
            );
        }
    }

    /*
     * Performs a single Alpaca historical-bars HTTP request.
     *
     * This method must only be reached after market-data
     * authorization has completed successfully.
     *
     * MarshallAI uses [from, to).
     *
     * Alpaca's end parameter is inclusive.
     *
     * Daily-bar requests therefore translate the exclusive
     * internal end into the final valid US-equity trading date
     * strictly before that boundary.
     */
    private String requestBarsPage(
            String symbol,
            String alpacaTimeframe,
            Instant from,
            Instant to,
            String pageToken) {

        String alpacaInclusiveEnd;

        if ("1Day".equals(
                alpacaTimeframe
        )) {

            alpacaInclusiveEnd =
                    UsEquityTradingCalendar
                            .lastTradingDateBefore(
                                    to
                            )
                            .toString();

        } else {

            /*
             * Non-daily ranges retain the same [from, to)
             * contract by translating the exclusive instant
             * to the immediately preceding instant.
             */
            alpacaInclusiveEnd =
                    to.minusNanos(
                                    1
                            )
                            .toString();
        }

        try {

            String response =
                    restClient
                            .get()
                            .uri(uriBuilder -> {

                                uriBuilder
                                        .path(
                                                "/v2/stocks/{symbol}/bars"
                                        )
                                        .queryParam(
                                                "timeframe",
                                                alpacaTimeframe
                                        )
                                        .queryParam(
                                                "start",
                                                from.toString()
                                        )
                                        .queryParam(
                                                "end",
                                                alpacaInclusiveEnd
                                        )
                                        .queryParam(
                                                "limit",
                                                MAX_PAGE_SIZE
                                        )
                                        .queryParam(
                                                "adjustment",
                                                BAR_ADJUSTMENT
                                        )
                                        .queryParam(
                                                "feed",
                                                DATA_FEED
                                        )
                                        .queryParam(
                                                "sort",
                                                "asc"
                                        );

                                if (pageToken != null
                                        && !pageToken.isBlank()) {

                                    uriBuilder.queryParam(
                                            "page_token",
                                            pageToken
                                    );
                                }

                                return uriBuilder.build(
                                        symbol
                                );
                            })
                            .retrieve()
                            .body(
                                    String.class
                            );

            if (response == null
                    || response.isBlank()) {

                throw new IllegalStateException(
                        "Alpaca returned an empty response"
                );
            }

            return response;

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Unable to retrieve Alpaca market data for "
                            + symbol,
                    exception
            );
        }
    }

    private JsonNode parseResponse(
            String response) {

        try {

            return objectMapper.readTree(
                    response
            );

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Unable to parse Alpaca market-data response",
                    exception
            );
        }
    }

    private MarketCandle toMarketCandle(
            String symbol,
            String timeframe,
            JsonNode bar) {

        validateBar(
                bar
        );

        return new MarketCandle(
                symbol,
                AssetType.STOCK,
                timeframe,
                decimal(
                        bar,
                        "o"
                ),
                decimal(
                        bar,
                        "h"
                ),
                decimal(
                        bar,
                        "l"
                ),
                decimal(
                        bar,
                        "c"
                ),
                decimal(
                        bar,
                        "v"
                ),
                Instant.parse(
                        bar.get(
                                "t"
                        ).asText()
                )
        );
    }

    private void validateBar(
            JsonNode bar) {

        String[] requiredFields = {
                "o",
                "h",
                "l",
                "c",
                "v",
                "t"
        };

        for (String field : requiredFields) {

            JsonNode value =
                    bar.get(
                            field
                    );

            if (value == null
                    || value.isNull()) {

                throw new IllegalStateException(
                        "Alpaca bar is missing required field: "
                                + field
                );
            }
        }
    }

    private BigDecimal decimal(
            JsonNode node,
            String field) {

        return node
                .get(
                        field
                )
                .decimalValue();
    }

    private String toAlpacaTimeframe(
            String timeframe) {

        return switch (
                timeframe
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        )
                ) {

            case "1m" ->
                    "1Min";

            case "5m" ->
                    "5Min";

            case "15m" ->
                    "15Min";

            case "30m" ->
                    "30Min";

            case "1h" ->
                    "1Hour";

            case "4h" ->
                    "4Hour";

            case "1d" ->
                    "1Day";

            case "1w" ->
                    "1Week";

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported timeframe: "
                                    + timeframe
                    );
        };
    }

    private void validateRangeRequest(
            String symbol,
            String timeframe,
            Instant from,
            Instant to) {

        validateSymbol(
                symbol
        );

        if (timeframe == null
                || timeframe.isBlank()) {

            throw new IllegalArgumentException(
                    "Timeframe cannot be blank"
            );
        }

        if (from == null) {

            throw new IllegalArgumentException(
                    "Historical start time cannot be null"
            );
        }

        if (to == null) {

            throw new IllegalArgumentException(
                    "Historical end time cannot be null"
            );
        }

        if (!to.isAfter(
                from
        )) {

            throw new IllegalArgumentException(
                    "Historical end time must be after start time"
            );
        }

        toAlpacaTimeframe(
                timeframe
        );
    }

    private void validateSymbol(
            String symbol) {

        if (symbol == null
                || symbol.isBlank()) {

            throw new IllegalArgumentException(
                    "Market symbol cannot be blank"
            );
        }
    }

    private static String requireEnvironmentVariable(
            String name) {

        String value =
                System.getenv(
                        name
                );

        if (value == null
                || value.isBlank()) {

            throw new IllegalStateException(
                    "Required environment variable is missing: "
                            + name
            );
        }

        return value;
    }
}