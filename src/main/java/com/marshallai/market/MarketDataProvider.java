package com.marshallai.market;

import java.time.Instant;
import java.util.List;

public interface MarketDataProvider {

    /**
     * Provider classification.
     *
     * REAL is deliberately the default so that a newly added
     * provider fails closed unless it explicitly identifies
     * itself as simulated.
     */
    default MarketDataProviderType providerType() {

        return MarketDataProviderType.REAL;
    }

    /**
     * Retrieves the most recent historical market candles
     * for a symbol.
     *
     * @param symbol market symbol such as TSLA, AAPL, or BTCUSD
     * @param candleCount number of historical candles requested
     * @return historical candles ordered from oldest to newest
     */
    List<MarketCandle> getHistoricalCandles(
            String symbol,
            int candleCount
    );

    /**
     * Retrieves historical market candles for a specific
     * time range.
     *
     * Range semantics are:
     *
     *     [from, to)
     *
     * "from" is inclusive.
     * "to" is exclusive.
     *
     * @param symbol market symbol
     * @param timeframe candle timeframe
     * @param from inclusive beginning
     * @param to exclusive end
     * @return candles ordered oldest to newest
     */
    List<MarketCandle> getHistoricalCandles(
            String symbol,
            String timeframe,
            Instant from,
            Instant to
    );
}