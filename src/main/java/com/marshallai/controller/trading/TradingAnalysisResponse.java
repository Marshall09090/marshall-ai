package com.marshallai.controller.trading;

import java.math.BigDecimal;
import java.util.List;

public record TradingAnalysisResponse(

        String symbol,

        List<String> detectedPatterns,
        String marketDirection,

        int technicalBullishScore,
        int technicalBearishScore,
        int technicalConfidencePercent,

        String tradingDecision,
        int confidencePercent,
        int bullishWeightedScore,
        int bearishWeightedScore,

        String eventRiskLevel,
        boolean eventTradeBlocked,

        String tradeApprovalStatus,
        String tradeApprovalReason,

        int quantity,
        BigDecimal positionValue,
        BigDecimal riskAmount

) {
}