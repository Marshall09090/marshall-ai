package com.marshallai.market;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@Profile("alpaca")
public class AlpacaMarketDataProvider implements MarketDataProvider {

    private static final String BASE_URL =
            "https://data.alpaca.markets";

    /*
     * IEX is used for development and paper trading so that
     * market-data requests do not require a SIP subscription.
     */
    private static final String DATA_FEED = "iex";

    /*
     * Historical bars used by Nexora should be split-adjusted.
     *
     * This prevents stock splits from creating artificial price
     * discontinuities in indicator and pattern calculations.
     */
    private static final String BAR_ADJUSTMENT = "split";

    private static final int MAX_PAGE_SIZE = 10_000;

    /*
     * The fixed-count analysis pipeline uses completed daily bars.
     *
     * Intraday timeframes remain available through the explicit
     * timeframe/range method, but the default strategy baseline
     * operates on daily candles.
     */
    private static final String DEFAULT_ANALYSIS_TIMEFRAME = "1d";

    /*
     * Nexora currently requires at least 200 historical candles
     * before technical analysis can run.
     *
     * 400 calendar days gives enough room for weekends, normal
     * market holidays and other non-trading days while still
     * providing comfortably more than 200 trading sessions.
     */
    private static final long MINIMUM_LOOKBACK_DAYS = 400L;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AlpacaMarketDataProvider(ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;

        String apiKey =
                requireEnvironmentVariable("ALPACA_API_KEY");

        String secretKey =
                requireEnvironmentVariable("ALPACA_SECRET_KEY");

        this.restClient =
                RestClient.builder()
                        .baseUrl(BASE_URL)
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

    @Override
    public List<MarketCandle> getHistoricalCandles(
            String symbol,
            int candleCount) {

        validateSymbol(symbol);

        if (candleCount <= 0) {
            throw new IllegalArgumentException(
                    "Candle count must be greater than zero"
            );
        }

        /*
         * Use the start of the current UTC day as the exclusive
         * upper boundary.
         *
         * A daily candle belonging to the current day may still be
         * forming. Ending the request at today's UTC boundary keeps
         * the fixed-count analysis dataset limited to completed
         * historical daily candles.
         */
        Instant to =
                LocalDate.now(ZoneOffset.UTC)
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC);

        /*
         * Scale the lookback for unusually large candle requests,
         * while always maintaining the 400-day minimum required by
         * the standard 200-candle analysis pipeline.
         *
         * Two calendar days per requested candle provides room for
         * weekends and market holidays.
         */
        long requestedLookbackDays =
                Math.max(
                        candleCount * 2L,
                        MINIMUM_LOOKBACK_DAYS
                );

        Instant from =
                to.minus(
                        requestedLookbackDays,
                        ChronoUnit.DAYS
                );

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
                            + symbol.trim().toUpperCase(Locale.ROOT)
                            + ". Requested "
                            + candleCount
                            + " but received "
                            + candles.size()
            );
        }

        /*
         * Keep the newest requested number of completed candles.
         * The range method sorts the result oldest-to-newest, so
         * this sub-list remains in chronological order.
         */
        return List.copyOf(
                candles.subList(
                        candles.size() - candleCount,
                        candles.size()
                )
        );
    }

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

        String normalizedSymbol =
                symbol.trim().toUpperCase(Locale.ROOT);

        String normalizedTimeframe =
                timeframe.trim().toLowerCase(Locale.ROOT);

        String alpacaTimeframe =
                toAlpacaTimeframe(normalizedTimeframe);

        List<MarketCandle> candles =
                new ArrayList<>();

        String pageToken = null;

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
                    parseResponse(response);

            JsonNode bars =
                    root.path("bars");

            if (bars.isArray()) {

                for (JsonNode bar : bars) {

                    candles.add(
                            toMarketCandle(
                                    normalizedSymbol,
                                    normalizedTimeframe,
                                    bar
                            )
                    );
                }
            }

            JsonNode tokenNode =
                    root.get("next_page_token");

            if (tokenNode == null
                    || tokenNode.isNull()
                    || tokenNode.asText().isBlank()) {

                pageToken = null;

            } else {

                pageToken =
                        tokenNode.asText();
            }

        } while (pageToken != null);

        /*
         * All downstream technical-analysis calculations expect
         * candles in chronological order.
         */
        candles.sort(
                Comparator.comparing(
                        MarketCandle::timestamp
                )
        );

        return List.copyOf(candles);
    }

    private String requestBarsPage(
            String symbol,
            String alpacaTimeframe,
            Instant from,
            Instant to,
            String pageToken) {

        try {

            String response =
                    restClient
                            .get()
                            .uri(uriBuilder -> {

                                uriBuilder
                                        .path("/v2/stocks/{symbol}/bars")
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
                                                to.toString()
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

                                return uriBuilder.build(symbol);
                            })
                            .retrieve()
                            .body(String.class);

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

            return objectMapper.readTree(response);

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

        validateBar(bar);

        return new MarketCandle(
                symbol,
                AssetType.STOCK,
                timeframe,
                decimal(bar, "o"),
                decimal(bar, "h"),
                decimal(bar, "l"),
                decimal(bar, "c"),
                decimal(bar, "v"),
                Instant.parse(
                        bar.get("t").asText()
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
                    bar.get(field);

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
                .get(field)
                .decimalValue();
    }

    private String toAlpacaTimeframe(
            String timeframe) {

        return switch (
                timeframe
                        .trim()
                        .toLowerCase(Locale.ROOT)
                ) {
            case "1m" -> "1Min";
            case "5m" -> "5Min";
            case "15m" -> "15Min";
            case "30m" -> "30Min";

            case "1h" -> "1Hour";
            case "4h" -> "4Hour";

            case "1d" -> "1Day";
            case "1w" -> "1Week";

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

        validateSymbol(symbol);

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

        if (to.isBefore(from)) {

            throw new IllegalArgumentException(
                    "Historical end time cannot be before start time"
            );
        }

        toAlpacaTimeframe(timeframe);
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

    private String requireEnvironmentVariable(
            String name) {

        String value =
                System.getenv(name);

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