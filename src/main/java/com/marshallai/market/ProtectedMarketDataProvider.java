package com.marshallai.market;

import com.marshallai.market.calendar.UsEquityTradingCalendar;
import com.marshallai.market.protection.ProtectedMarketDataBoundary;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@Primary
public class ProtectedMarketDataProvider
        implements MarketDataProvider {

    private static final String
            DEFAULT_REAL_FIXED_COUNT_TIMEFRAME =
            "1d";

    private static final long
            MINIMUM_REAL_LOOKBACK_DAYS =
            400L;

    private final MarketDataProviderSource delegate;

    private final ProtectedMarketDataBoundary
            protectedMarketDataBoundary;

    public ProtectedMarketDataProvider(
            MarketDataProviderSource delegate,
            ProtectedMarketDataBoundary protectedMarketDataBoundary) {

        if (delegate == null) {

            throw new IllegalArgumentException(
                    "Market-data provider source cannot be null"
            );
        }

        if (protectedMarketDataBoundary == null) {

            throw new IllegalArgumentException(
                    "Protected market-data boundary cannot be null"
            );
        }

        this.delegate =
                delegate;

        this.protectedMarketDataBoundary =
                protectedMarketDataBoundary;
    }

    @Override
    public MarketDataProviderType providerType() {

        MarketDataProviderType type =
                delegate.providerType();

        if (type == null) {

            throw new IllegalStateException(
                    "Market-data provider type cannot be null"
            );
        }

        return type;
    }

    /**
     * Fixed-count simulated requests preserve the simulator's
     * existing behavior.
     *
     * Fixed-count REAL requests are converted here into an
     * explicit governed daily range so no real provider can
     * create a second unprotected entry point.
     */
    @Override
    public List<MarketCandle> getHistoricalCandles(
            String symbol,
            int candleCount) {

        requireSymbol(
                symbol
        );

        if (candleCount <= 0) {

            throw new IllegalArgumentException(
                    "Candle count must be greater than zero"
            );
        }

        if (providerType()
                == MarketDataProviderType.SIMULATED) {

            return delegate.getHistoricalCandles(
                    symbol,
                    candleCount
            );
        }

        LocalDate currentTradingDate =
                LocalDate.now(
                        UsEquityTradingCalendar.MARKET_ZONE
                );

        Instant to =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                currentTradingDate
                        );

        long lookbackDays =
                Math.max(
                        candleCount * 2L,
                        MINIMUM_REAL_LOOKBACK_DAYS
                );

        Instant from =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                currentTradingDate.minusDays(
                                        lookbackDays
                                )
                        );

        List<MarketCandle> candles =
                getHistoricalCandles(
                        symbol,
                        DEFAULT_REAL_FIXED_COUNT_TIMEFRAME,
                        from,
                        to
                );

        if (candles.size()
                < candleCount) {

            throw new IllegalStateException(
                    "Market-data provider returned insufficient "
                            + "completed daily candles for symbol "
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

    /**
     * Every explicit REAL market-data request crosses the
     * protected-date boundary before the underlying provider
     * is invoked.
     *
     * SIMULATED exemption is derived solely from provider type.
     */
    @Override
    public List<MarketCandle> getHistoricalCandles(
            String symbol,
            String timeframe,
            Instant from,
            Instant to) {

        if (providerType()
                .requiresProtectedDateEnforcement()) {

            protectedMarketDataBoundary
                    .authorizeRealMarketDataRequest(
                            from,
                            to
                    );
        }

        return delegate.getHistoricalCandles(
                symbol,
                timeframe,
                from,
                to
        );
    }

    private static void requireSymbol(
            String symbol) {

        if (symbol == null
                || symbol.isBlank()) {

            throw new IllegalArgumentException(
                    "Market symbol cannot be blank"
            );
        }
    }
}