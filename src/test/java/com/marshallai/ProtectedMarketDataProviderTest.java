package com.marshallai;

import com.marshallai.market.AssetType;
import com.marshallai.market.MarketCandle;
import com.marshallai.market.MarketDataProviderSource;
import com.marshallai.market.MarketDataProviderType;
import com.marshallai.market.ProtectedMarketDataProvider;
import com.marshallai.market.SimulatedMarketDataProvider;
import com.marshallai.market.protection.ProtectedMarketDataAccessException;
import com.marshallai.market.protection.ProtectedMarketDataBoundary;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtectedMarketDataProviderTest {

    @Test
    void futureRealProviderIsProtectedByDefaultBeforeDelegateRuns() {

        RecordingRealProvider delegate =
                new RecordingRealProvider();

        ProtectedMarketDataProvider provider =
                new ProtectedMarketDataProvider(
                        delegate,
                        ProtectedMarketDataBoundary
                                .failClosedWithoutDurableStore()
                );

        ProtectedMarketDataAccessException exception =
                assertThrows(
                        ProtectedMarketDataAccessException.class,
                        () ->
                                provider.getHistoricalCandles(
                                        "SPY",
                                        "1d",
                                        Instant.parse(
                                                "2050-01-03T05:00:00Z"
                                        ),
                                        Instant.parse(
                                                "2050-01-04T05:00:00Z"
                                        )
                                )
                );

        assertEquals(
                ProtectedMarketDataBoundary
                        .DURABLE_STORE_REQUIRED,
                exception.getRefusalReason()
        );

        assertEquals(
                0,
                delegate.rangeRequestCount
        );

        assertEquals(
                MarketDataProviderType.REAL,
                provider.providerType()
        );
    }

    @Test
    void realFixedCountRequestCannotBypassProtectedBoundary() {

        RecordingRealProvider delegate =
                new RecordingRealProvider();

        ProtectedMarketDataProvider provider =
                new ProtectedMarketDataProvider(
                        delegate,
                        ProtectedMarketDataBoundary
                                .failClosedWithoutDurableStore()
                );

        ProtectedMarketDataAccessException exception =
                assertThrows(
                        ProtectedMarketDataAccessException.class,
                        () ->
                                provider.getHistoricalCandles(
                                        "SPY",
                                        200
                                )
                );

        assertEquals(
                ProtectedMarketDataBoundary
                        .DURABLE_STORE_REQUIRED,
                exception.getRefusalReason()
        );

        assertEquals(
                0,
                delegate.fixedCountRequestCount
        );

        assertEquals(
                0,
                delegate.rangeRequestCount
        );
    }

    @Test
    void simulatedProviderExemptionComesFromProviderType() {

        SimulatedMarketDataProvider delegate =
                new SimulatedMarketDataProvider();

        ProtectedMarketDataProvider provider =
                new ProtectedMarketDataProvider(
                        delegate,
                        ProtectedMarketDataBoundary
                                .failClosedWithoutDurableStore()
                );

        Instant from =
                Instant.parse(
                        "2050-01-03T05:00:00Z"
                );

        Instant to =
                Instant.parse(
                        "2050-01-05T05:00:00Z"
                );

        List<MarketCandle> candles =
                provider.getHistoricalCandles(
                        "SPY",
                        "1d",
                        from,
                        to
                );

        assertFalse(
                candles.isEmpty()
        );

        assertEquals(
                MarketDataProviderType.SIMULATED,
                provider.providerType()
        );

        assertFalse(
                provider.providerType()
                        .requiresProtectedDateEnforcement()
        );

        assertFalse(
                provider.providerType()
                        .allowsGovernedVerdict()
        );
    }

    @Test
    void simulatedProviderCannotBeUsedForGovernedVerdict() {

        MarketDataProviderType type =
                MarketDataProviderType.SIMULATED;

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        type::requireGovernedVerdictAllowed
                );

        assertEquals(
                "SIMULATED_PROVIDER_CANNOT_EMIT_GOVERNED_VERDICT",
                exception.getMessage()
        );
    }

    @Test
    void realProviderTypeAllowsGovernedVerdictEligibility() {

        assertDoesNotThrow(
                MarketDataProviderType.REAL
                        ::requireGovernedVerdictAllowed
        );
    }

    private static final class RecordingRealProvider
            implements MarketDataProviderSource {

        private int fixedCountRequestCount;

        private int rangeRequestCount;

        @Override
        public List<MarketCandle> getHistoricalCandles(
                String symbol,
                int candleCount) {

            fixedCountRequestCount++;

            return List.of(
                    candle()
            );
        }

        @Override
        public List<MarketCandle> getHistoricalCandles(
                String symbol,
                String timeframe,
                Instant from,
                Instant to) {

            rangeRequestCount++;

            return List.of(
                    candle()
            );
        }

        private static MarketCandle candle() {

            return new MarketCandle(
                    "SPY",
                    AssetType.STOCK,
                    "1d",
                    new BigDecimal(
                            "100.00"
                    ),
                    new BigDecimal(
                            "101.00"
                    ),
                    new BigDecimal(
                            "99.00"
                    ),
                    new BigDecimal(
                            "100.50"
                    ),
                    new BigDecimal(
                            "100000"
                    ),
                    Instant.parse(
                            "2050-01-03T05:00:00Z"
                    )
            );
        }
    }
}