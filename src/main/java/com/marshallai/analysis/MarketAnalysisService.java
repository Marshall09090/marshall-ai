package com.marshallai.analysis;

import com.marshallai.indicator.IndicatorService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class MarketAnalysisService {

    private final IndicatorService indicatorService;

    public MarketAnalysisService(
            IndicatorService indicatorService) {

        this.indicatorService = indicatorService;
    }

    public MarketAnalysisResult analyze(
            List<BigDecimal> closingPrices) {

        List<String> detectedPatterns =
                new ArrayList<>();

        // =========================
        // BULLISH RECTANGLE
        // =========================

        if (indicatorService.detectBullishRectangle(
                closingPrices)) {

            detectedPatterns.add(
                    "Bullish Rectangle"
            );
        }

        // =========================
        // BEARISH RECTANGLE
        // =========================

        if (indicatorService.detectBearishRectangle(
                closingPrices)) {

            detectedPatterns.add(
                    "Bearish Rectangle"
            );
        }

        // =========================
        // ASCENDING CHANNEL
        // =========================

        if (indicatorService.detectAscendingChannel(
                closingPrices)) {

            detectedPatterns.add(
                    "Ascending Channel"
            );
        }

        // =========================
        // DESCENDING CHANNEL
        // =========================

        if (indicatorService.detectDescendingChannel(
                closingPrices)) {

            detectedPatterns.add(
                    "Descending Channel"
            );
        }

        // =========================
        // BULLISH PENNANT
        // =========================

        if (indicatorService.detectBullishPennant(
                closingPrices)) {

            detectedPatterns.add(
                    "Bullish Pennant"
            );
        }

        // =========================
        // BEARISH PENNANT
        // =========================

        if (indicatorService.detectBearishPennant(
                closingPrices)) {

            detectedPatterns.add(
                    "Bearish Pennant"
            );
        }

        // =========================
        // BULL FLAG
        // =========================

        if (indicatorService.detectBullFlag(
                closingPrices)) {

            detectedPatterns.add(
                    "Bull Flag"
            );
        }

        // =========================
        // BEAR FLAG
        // =========================

        if (indicatorService.detectBearFlag(
                closingPrices)) {

            detectedPatterns.add(
                    "Bear Flag"
            );
        }

        // =========================
        // BULLISH BREAKOUT
        // =========================

        if (indicatorService.detectBullishBreakout(
                closingPrices)) {

            detectedPatterns.add(
                    "Bullish Breakout"
            );
        }

        // =========================
        // MARKET DIRECTION
        // =========================

        boolean bullishSignal =
                detectedPatterns.contains(
                        "Bullish Rectangle"
                )
                        || detectedPatterns.contains(
                        "Ascending Channel"
                )
                        || detectedPatterns.contains(
                        "Bullish Pennant"
                )
                        || detectedPatterns.contains(
                        "Bull Flag"
                )
                        || detectedPatterns.contains(
                        "Bullish Breakout"
                );

        boolean bearishSignal =
                detectedPatterns.contains(
                        "Bearish Rectangle"
                )
                        || detectedPatterns.contains(
                        "Descending Channel"
                )
                        || detectedPatterns.contains(
                        "Bearish Pennant"
                )
                        || detectedPatterns.contains(
                        "Bear Flag"
                );

        MarketAnalysisResult.Direction direction;

        if (bullishSignal && !bearishSignal) {

            direction =
                    MarketAnalysisResult.Direction.BULLISH;

        } else if (bearishSignal && !bullishSignal) {

            direction =
                    MarketAnalysisResult.Direction.BEARISH;

        } else {

            direction =
                    MarketAnalysisResult.Direction.NEUTRAL;
        }

        return new MarketAnalysisResult(
                detectedPatterns,
                direction
        );
    }
}