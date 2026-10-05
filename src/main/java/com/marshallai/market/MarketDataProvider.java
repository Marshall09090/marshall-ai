package com.marshallai.market;

import java.time.Instant;
import java.util.List;

public interface MarketDataProvider {

    /**
     * Retrieves the most recent historical market candles for a symbol.
     *
     * This method is primarily useful for the current symbol-driven
     * analysis pipeline and for callers that only need a fixed number
     * of candles.
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
     * Retrieves historical market candles for a specific time range.
     *
     * This method is intended for backtesting and historical analysis.
     * Implementations may obtain the candles from an external market-data
     * API, database, cache, or another supported source.
     *
     * The returned candles must be ordered from oldest to newest.
     *
     * @param symbol market symbol such as TSLA, AAPL, or BTCUSD
     * @param timeframe candle timeframe such as 1m, 5m, 15m, 1h, or 1d
     * @param from inclusive beginning of the requested historical period
     * @param to inclusive end of the requested historical period
     * @return historical candles ordered from oldest to newest
     */
    List<MarketCandle> getHistoricalCandles(
            String symbol,
            String timeframe,
            Instant from,
            Instant to
    );
}