package com.marshallai.signal;

import com.marshallai.analysis.MarketAnalysisResult;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class SignalService {

    private static final int MIN_TRADE_CONFIDENCE_PERCENT = 80;

    private static final Set<String> BULLISH_PATTERNS =
            Set.of(
                    "Bullish Rectangle",
                    "Ascending Channel",
                    "Bullish Pennant",
                    "Bull Flag",
                    "Bullish Breakout",
                    "Double Bottom",
                    "Triple Bottom",
                    "Inverse Head and Shoulders",
                    "Ascending Triangle",
                    "Falling Wedge",
                    "Cup and Handle",
                    "Rounding Bottom"
            );

    private static final Set<String> BEARISH_PATTERNS =
            Set.of(
                    "Bearish Rectangle",
                    "Descending Channel",
                    "Bearish Pennant",
                    "Bear Flag",
                    "Bearish Breakdown",
                    "Double Top",
                    "Triple Top",
                    "Head and Shoulders",
                    "Descending Triangle",
                    "Rising Wedge",
                    "Inverse Cup and Handle",
                    "Rounding Top"
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

        for (String pattern :
                marketAnalysisResult.getDetectedPatterns()) {

            if (BULLISH_PATTERNS.contains(pattern)) {
                bullishSignalCount++;
            }

            if (BEARISH_PATTERNS.contains(pattern)) {
                bearishSignalCount++;
            }
        }

        int totalDirectionalSignals =
                bullishSignalCount + bearishSignalCount;

        if (totalDirectionalSignals == 0) {

            return new MarketSignal(
                    MarketSignal.Decision.HOLD,
                    0,
                    0,
                    0
            );
        }

        int dominantSignalCount =
                Math.max(
                        bullishSignalCount,
                        bearishSignalCount
                );

        int confidencePercent =
                (int) Math.round(
                        dominantSignalCount
                                * 100.0
                                / totalDirectionalSignals
                );

        MarketSignal.Decision decision =
                MarketSignal.Decision.HOLD;

        if (confidencePercent >= MIN_TRADE_CONFIDENCE_PERCENT) {

            if (bullishSignalCount > bearishSignalCount) {

                decision =
                        MarketSignal.Decision.BUY;

            } else if (bearishSignalCount > bullishSignalCount) {

                decision =
                        MarketSignal.Decision.SELL;
            }
        }

        return new MarketSignal(
                decision,
                confidencePercent,
                bullishSignalCount,
                bearishSignalCount
        );
    }
}