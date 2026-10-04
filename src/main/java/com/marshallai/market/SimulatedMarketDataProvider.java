package com.marshallai.market;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class SimulatedMarketDataProvider implements MarketDataProvider {

    @Override
    public List<MarketCandle> getHistoricalCandles(
            String symbol,
            int candleCount) {

        validateRequest(symbol, candleCount);

        List<MarketCandle> candles = new ArrayList<>();

        BigDecimal startingPrice =
                BigDecimal.valueOf(100.00);

        Instant startingTimestamp =
                Instant.now()
                        .minus(candleCount, ChronoUnit.MINUTES);

        for (int i = 0; i < candleCount; i++) {

            BigDecimal priceIncrease =
                    BigDecimal.valueOf(i)
                            .multiply(
                                    BigDecimal.valueOf(0.50)
                            );

            BigDecimal open =
                    startingPrice.add(priceIncrease);

            BigDecimal close =
                    open.add(
                            BigDecimal.valueOf(0.25)
                    );

            BigDecimal high =
                    close.add(
                            BigDecimal.valueOf(0.50)
                    );

            BigDecimal low =
                    open.subtract(
                            BigDecimal.valueOf(0.50)
                    );

            BigDecimal volume =
                    BigDecimal.valueOf(
                            1000L + (i * 10L)
                    );

            Instant timestamp =
                    startingTimestamp.plus(
                            i,
                            ChronoUnit.MINUTES
                    );

            MarketCandle candle =
                    new MarketCandle(
                            symbol.toUpperCase(),
                            AssetType.STOCK,
                            "1m",
                            open,
                            high,
                            low,
                            close,
                            volume,
                            timestamp
                    );

            candles.add(candle);
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