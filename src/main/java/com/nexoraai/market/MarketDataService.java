package com.nexoraai.market;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class MarketDataService {

    public MarketQuote getQuote(String symbol, AssetType assetType) {

        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be empty");
        }

        if (assetType == null) {
            throw new IllegalArgumentException("Asset type cannot be null");
        }

        BigDecimal price = getSimulatedPrice(symbol, assetType);

        return new MarketQuote(
                symbol.toUpperCase(),
                assetType,
                price,
                Instant.now()
        );
    }

    private BigDecimal getSimulatedPrice(
            String symbol,
            AssetType assetType
    ) {

        if (assetType == AssetType.FOREX) {
            return new BigDecimal("1.0850");
        }

        return new BigDecimal("150.00");
    }
}