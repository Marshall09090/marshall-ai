package com.nexoraai.market;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketCandle(
        String symbol,
        AssetType assetType,
        String timeframe,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        Instant timestamp
) {
}