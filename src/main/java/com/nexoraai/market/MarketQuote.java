package com.nexoraai.market;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketQuote(
        String symbol,
        AssetType assetType,
        BigDecimal price,
        Instant timestamp
) {
}