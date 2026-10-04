package com.marshallai.controller.trading;

import java.math.BigDecimal;
import java.util.List;

public record TradingAnalysisRequest(

        String symbol,

        List<BigDecimal> highPrices,
        List<BigDecimal> lowPrices,
        List<BigDecimal> closingPrices,

        String eventType,
        String eventDescription,
        int minutesUntilEvent,
        boolean highImpactEvent,

        BigDecimal accountBalance,
        BigDecimal riskPercent,
        BigDecimal entryPrice,
        BigDecimal stopLossPrice

) {
}