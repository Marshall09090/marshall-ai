package com.marshallai.market;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;

@Service
@Profile("!alpaca")
public class SimulatedMarketDataProvider
        implements MarketDataProvider {

    private static final String DEFAULT_TIMEFRAME = "1m";

    public enum MarketMode {
        STRONGLY_BULLISH,
        MIXED
    }

    private MarketMode marketMode =
            MarketMode.STRONGLY_BULLISH;

    public void useStronglyBullishMarket() {
        marketMode =
                MarketMode.STRONGLY_BULLISH;
    }

    public void useMixedMarket() {
        marketMode =
                MarketMode.MIXED;
    }

    @Override
    public List<MarketCandle> getHistoricalCandles(
            String symbol,
            int candleCount) {

        validateRequest(
                symbol,
                candleCount
        );

        Instant endTime = Instant.now();

        Instant startTime =
                endTime.minus(
                        candleCount,
                        ChronoUnit.MINUTES
                );

        return buildMarket(
                symbol,
                candleCount,
                DEFAULT_TIMEFRAME,
                startTime,
                1
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

        long intervalMinutes =
                timeframeToMinutes(timeframe);

        long requestedMinutes =
                Duration.between(
                        from,
                        to
                ).toMinutes();

        long calculatedCandleCount =
                (requestedMinutes / intervalMinutes) + 1;

        if (calculatedCandleCount <= 0) {
            throw new IllegalArgumentException(
                    "Historical range must contain at least one candle"
            );
        }

        if (calculatedCandleCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Requested historical range is too large"
            );
        }

        int candleCount =
                (int) calculatedCandleCount;

        return buildMarket(
                symbol,
                candleCount,
                timeframe,
                from,
                intervalMinutes
        );
    }

    private List<MarketCandle> buildMarket(
            String symbol,
            int candleCount,
            String timeframe,
            Instant startingTimestamp,
            long intervalMinutes) {

        List<BigDecimal> closingPrices;

        if (marketMode == MarketMode.MIXED) {

            closingPrices =
                    buildMixedClosingPrices(
                            candleCount
                    );

        } else {

            closingPrices =
                    buildStronglyBullishClosingPrices(
                            candleCount
                    );
        }

        return buildCandles(
                symbol,
                timeframe,
                closingPrices,
                startingTimestamp,
                intervalMinutes
        );
    }

    private List<BigDecimal> buildStronglyBullishClosingPrices(
            int candleCount) {

        List<BigDecimal> closingPrices =
                new ArrayList<>();

        for (int i = 0;
             i < candleCount;
             i++) {

            closingPrices.add(
                    BigDecimal.valueOf(
                            100L + i
                    )
            );
        }

        return closingPrices;
    }

    private List<BigDecimal> buildMixedClosingPrices(
            int candleCount) {

        List<BigDecimal> closingPrices =
                new ArrayList<>();

        /*
         * This sequence intentionally creates
         * conflicting technical evidence.
         *
         * For a 30-candle request:
         *
         * 100 ... 125
         * then
         * 122, 119, 116, 113
         *
         * Expected technical evidence:
         *
         * bullish = 2
         * bearish = 3
         * confidence = 60%
         *
         * This should remain below the
         * 80% trading threshold.
         */

        if (candleCount >= 30) {

            int normalTrendCount =
                    candleCount - 4;

            for (int i = 0;
                 i < normalTrendCount;
                 i++) {

                closingPrices.add(
                        BigDecimal.valueOf(
                                100L + i
                        )
                );
            }

            BigDecimal peak =
                    closingPrices.get(
                            closingPrices.size() - 1
                    );

            closingPrices.add(
                    peak.subtract(
                            BigDecimal.valueOf(3)
                    )
            );

            closingPrices.add(
                    peak.subtract(
                            BigDecimal.valueOf(6)
                    )
            );

            closingPrices.add(
                    peak.subtract(
                            BigDecimal.valueOf(9)
                    )
            );

            closingPrices.add(
                    peak.subtract(
                            BigDecimal.valueOf(12)
                    )
            );

        } else {

            for (int i = 0;
                 i < candleCount;
                 i++) {

                long movement =
                        i % 2 == 0
                                ? i
                                : -i;

                closingPrices.add(
                        BigDecimal.valueOf(
                                100L + movement
                        )
                );
            }
        }

        return closingPrices;
    }

    private List<MarketCandle> buildCandles(
            String symbol,
            String timeframe,
            List<BigDecimal> closingPrices,
            Instant startingTimestamp,
            long intervalMinutes) {

        List<MarketCandle> candles =
                new ArrayList<>();

        String normalizedSymbol =
                symbol
                        .trim()
                        .toUpperCase();

        for (int i = 0;
             i < closingPrices.size();
             i++) {

            BigDecimal close =
                    closingPrices.get(i);

            BigDecimal open;

            if (i == 0) {

                open =
                        close.subtract(
                                BigDecimal.valueOf(
                                        0.25
                                )
                        );

            } else {

                open =
                        closingPrices.get(
                                i - 1
                        );
            }

            BigDecimal high =
                    open.max(close)
                            .add(
                                    BigDecimal.ONE
                            );

            BigDecimal low =
                    open.min(close)
                            .subtract(
                                    BigDecimal.ONE
                            );

            BigDecimal volume =
                    BigDecimal.valueOf(
                            1000L
                                    + (i * 10L)
                    );

            Instant timestamp =
                    startingTimestamp.plus(
                            i * intervalMinutes,
                            ChronoUnit.MINUTES
                    );

            candles.add(
                    new MarketCandle(
                            normalizedSymbol,
                            AssetType.STOCK,
                            timeframe,
                            open,
                            high,
                            low,
                            close,
                            volume,
                            timestamp
                    )
            );
        }

        return candles;
    }

    private long timeframeToMinutes(
            String timeframe) {

        return switch (
                timeframe
                        .trim()
                        .toLowerCase()
                ) {
            case "1m" -> 1;
            case "5m" -> 5;
            case "15m" -> 15;
            case "30m" -> 30;
            case "1h" -> 60;
            case "4h" -> 240;
            case "1d" -> 1440;

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported timeframe: "
                                    + timeframe
                    );
        };
    }

    private void validateRequest(
            String symbol,
            int candleCount) {

        validateSymbol(symbol);

        if (candleCount <= 0) {

            throw new IllegalArgumentException(
                    "Candle count must be greater than zero"
            );
        }
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

        /*
         * Validate the timeframe before attempting
         * to calculate the historical candle range.
         */
        timeframeToMinutes(timeframe);
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
}