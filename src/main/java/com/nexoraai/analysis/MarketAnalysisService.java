package com.nexoraai.analysis;

import com.nexoraai.indicator.IndicatorService;
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
        // MARKET DIRECTION
        // =========================

        boolean bullishSignal =
                detectedPatterns.contains(
                        "Bullish Rectangle"
                )
                        || detectedPatterns.contains(
                        "Ascending Channel"
                );

        boolean bearishSignal =
                detectedPatterns.contains(
                        "Bearish Rectangle"
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