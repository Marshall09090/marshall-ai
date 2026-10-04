package com.marshallai.signal;

import com.marshallai.analysis.MarketAnalysisResult;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SignalService {

    private static final int MIN_TRADE_CONFIDENCE_PERCENT = 80;

    private static final Map<String, Integer> BULLISH_PATTERN_WEIGHTS =
            Map.ofEntries(

                    Map.entry("Bullish Breakout", 3),
                    Map.entry("Double Bottom", 3),
                    Map.entry("Triple Bottom", 3),
                    Map.entry("Inverse Head and Shoulders", 3),
                    Map.entry("Cup and Handle", 3),
                    Map.entry("Rounding Bottom", 3),

                    Map.entry("Bullish Rectangle", 2),
                    Map.entry("Bullish Pennant", 2),
                    Map.entry("Bull Flag", 2),
                    Map.entry("Ascending Triangle", 2),
                    Map.entry("Falling Wedge", 2),

                    Map.entry("Ascending Channel", 1)
            );

    private static final Map<String, Integer> BEARISH_PATTERN_WEIGHTS =
            Map.ofEntries(

                    Map.entry("Bearish Breakdown", 3),
                    Map.entry("Double Top", 3),
                    Map.entry("Triple Top", 3),
                    Map.entry("Head and Shoulders", 3),
                    Map.entry("Inverse Cup and Handle", 3),
                    Map.entry("Rounding Top", 3),

                    Map.entry("Bearish Rectangle", 2),
                    Map.entry("Bearish Pennant", 2),
                    Map.entry("Bear Flag", 2),
                    Map.entry("Descending Triangle", 2),
                    Map.entry("Rising Wedge", 2),

                    Map.entry("Descending Channel", 1)
            );

    public MarketSignal evaluate(
            MarketAnalysisResult marketAnalysisResult) {

        if (marketAnalysisResult == null) {
            throw new IllegalArgumentException(
                    "Market analysis result cannot be null"
            );
        }

        int bullishSignalCount = 0;
        int bearishSignalCount = 0;

        int bullishWeightedScore = 0;
        int bearishWeightedScore = 0;

        for (String pattern :
                marketAnalysisResult.getDetectedPatterns()) {

            Integer bullishWeight =
                    BULLISH_PATTERN_WEIGHTS.get(pattern);

            if (bullishWeight != null) {

                bullishSignalCount++;

                bullishWeightedScore +=
                        bullishWeight;
            }

            Integer bearishWeight =
                    BEARISH_PATTERN_WEIGHTS.get(pattern);

            if (bearishWeight != null) {

                bearishSignalCount++;

                bearishWeightedScore +=
                        bearishWeight;
            }
        }

        int totalWeightedScore =
                bullishWeightedScore
                        + bearishWeightedScore;

        if (totalWeightedScore == 0) {

            return new MarketSignal(
                    MarketSignal.Decision.HOLD,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        int dominantWeightedScore =
                Math.max(
                        bullishWeightedScore,
                        bearishWeightedScore
                );

        int confidencePercent =
                (int) Math.round(
                        dominantWeightedScore
                                * 100.0
                                / totalWeightedScore
                );

        MarketSignal.Decision decision =
                MarketSignal.Decision.HOLD;

        if (confidencePercent >= MIN_TRADE_CONFIDENCE_PERCENT) {

            if (bullishWeightedScore
                    > bearishWeightedScore) {

                decision =
                        MarketSignal.Decision.BUY;

            } else if (bearishWeightedScore
                    > bullishWeightedScore) {

                decision =
                        MarketSignal.Decision.SELL;
            }
        }

        return new MarketSignal(
                decision,
                confidencePercent,
                bullishSignalCount,
                bearishSignalCount,
                bullishWeightedScore,
                bearishWeightedScore
        );
    }
}