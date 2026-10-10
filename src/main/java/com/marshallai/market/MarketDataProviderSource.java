package com.marshallai.market;

/**
 * Internal market-data source behind the governed
 * MarketDataProvider facade.
 *
 * All real provider implementations default to REAL.
 *
 * Only an explicitly simulated provider may override its
 * provider type to SIMULATED.
 */
public interface MarketDataProviderSource
        extends MarketDataProvider {
}