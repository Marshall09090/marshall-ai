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

        if (indicatorService.detectBullishRectangle(
                closingPrices)) {

            detectedPatterns.add(
                    "Bullish Rectangle"
            );
        }

        MarketAnalysisResult.Direction direction =
                detectedPatterns.isEmpty()
                        ? MarketAnalysisResult.Direction.NEUTRAL
                        : MarketAnalysisResult.Direction.BULLISH;

        return new MarketAnalysisResult(
                detectedPatterns,
                direction
        );
    }
}