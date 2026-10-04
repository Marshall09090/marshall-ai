package com.marshallai.market;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class SimulatedMarketDataProvider
        implements MarketDataProvider {

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

        if (marketMode == MarketMode.MIXED) {

            return buildMixedMarket(
                    symbol,
                    candleCount
            );
        }

        return buildStronglyBullishMarket(
                symbol,
                candleCount
        );
    }

    private List<MarketCandle> buildStronglyBullishMarket(
            String symbol,
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

        return buildCandles(
                symbol,
                closingPrices
        );
    }

    private List<MarketCandle> buildMixedMarket(
            String symbol,
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

        return buildCandles(
                symbol,
                closingPrices
        );
    }

    private List<MarketCandle> buildCandles(
            String symbol,
            List<BigDecimal> closingPrices) {

        List<MarketCandle> candles =
                new ArrayList<>();

        Instant startingTimestamp =
                Instant.now()
                        .minus(
                                closingPrices.size(),
                                ChronoUnit.MINUTES
                        );

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
                            i,
                            ChronoUnit.MINUTES
                    );

            candles.add(
                    new MarketCandle(
                            symbol
                                    .trim()
                                    .toUpperCase(),
                            AssetType.STOCK,
                            "1m",
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

    private void validateRequest(
            String symbol,
            int candleCount) {

        if (symbol == null
                || symbol.isBlank()) {

            throw new IllegalArgumentException(
                    "Market symbol cannot be blank"
            );
        }

        if (candleCount <= 0) {

            throw new IllegalArgumentException(
                    "Candle count must be greater than zero"
            );
        }
    }
}