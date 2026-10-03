package com.marshallai.market;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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

    public List<MarketCandle> getHistoricalCandles(
            String symbol,
            AssetType assetType,
            String timeframe
    ) {

        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be empty");
        }

        if (assetType == null) {
            throw new IllegalArgumentException("Asset type cannot be null");
        }

        if (timeframe == null || timeframe.isBlank()) {
            throw new IllegalArgumentException("Timeframe cannot be empty");
        }

        BigDecimal basePrice = getSimulatedPrice(symbol, assetType);

        return List.of(
                new MarketCandle(
                        symbol.toUpperCase(),
                        assetType,
                        timeframe,
                        basePrice,
                        basePrice.add(new BigDecimal("2.00")),
                        basePrice.subtract(new BigDecimal("1.00")),
                        basePrice.add(new BigDecimal("1.00")),
                        new BigDecimal("1000000"),
                        Instant.now()
                )
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