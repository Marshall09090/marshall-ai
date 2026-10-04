package com.marshallai.market;

import java.util.List;

public interface MarketDataProvider {

    /**
     * Retrieves historical market candles for a symbol.
     *
     * The provider implementation may obtain these candles from
     * an external market-data API, database, cache, or another
     * supported source.
     *
     * @param symbol market symbol such as TSLA, AAPL, or BTCUSD
     * @param candleCount number of historical candles requested
     * @return historical candles ordered from oldest to newest
     */
    List<MarketCandle> getHistoricalCandles(
            String symbol,
            int candleCount
    );
}