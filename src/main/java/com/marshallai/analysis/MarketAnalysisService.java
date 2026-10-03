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

        if (indicatorService.detectBullishRectangle(closingPrices)) {
            detectedPatterns.add("Bullish Rectangle");
        }

        // =========================
        // BEARISH RECTANGLE
        // =========================

        if (indicatorService.detectBearishRectangle(closingPrices)) {
            detectedPatterns.add("Bearish Rectangle");
        }

        // =========================
        // ASCENDING CHANNEL
        // =========================

        if (indicatorService.detectAscendingChannel(closingPrices)) {
            detectedPatterns.add("Ascending Channel");
        }

        // =========================
        // DESCENDING CHANNEL
        // =========================

        if (indicatorService.detectDescendingChannel(closingPrices)) {
            detectedPatterns.add("Descending Channel");
        }

        // =========================
        // BULLISH PENNANT
        // =========================

        if (indicatorService.detectBullishPennant(closingPrices)) {
            detectedPatterns.add("Bullish Pennant");
        }

        // =========================
        // BEARISH PENNANT
        // =========================

        if (indicatorService.detectBearishPennant(closingPrices)) {
            detectedPatterns.add("Bearish Pennant");
        }

        // =========================
        // BULL FLAG
        // =========================

        if (indicatorService.detectBullFlag(closingPrices)) {
            detectedPatterns.add("Bull Flag");
        }

        // =========================
        // BEAR FLAG
        // =========================

        if (indicatorService.detectBearFlag(closingPrices)) {
            detectedPatterns.add("Bear Flag");
        }

        // =========================
        // BULLISH BREAKOUT
        // =========================

        if (indicatorService.detectBullishBreakout(closingPrices)) {
            detectedPatterns.add("Bullish Breakout");
        }

        // =========================
        // BEARISH BREAKDOWN
        // =========================

        if (indicatorService.detectBearishBreakdown(closingPrices)) {
            detectedPatterns.add("Bearish Breakdown");
        }

        // =========================
        // DOUBLE TOP
        // =========================

        if (indicatorService.detectDoubleTop(closingPrices)) {
            detectedPatterns.add("Double Top");
        }

        // =========================
        // DOUBLE BOTTOM
        // =========================

        if (indicatorService.detectDoubleBottom(closingPrices)) {
            detectedPatterns.add("Double Bottom");
        }

        // =========================
        // TRIPLE TOP
        // =========================

        if (indicatorService.detectTripleTop(closingPrices)) {
            detectedPatterns.add("Triple Top");
        }

        // =========================
        // TRIPLE BOTTOM
        // =========================

        if (indicatorService.detectTripleBottom(closingPrices)) {
            detectedPatterns.add("Triple Bottom");
        }

        // =========================
        // HEAD AND SHOULDERS
        // =========================

        if (indicatorService.detectHeadAndShoulders(closingPrices)) {
            detectedPatterns.add("Head and Shoulders");
        }

        // =========================
        // INVERSE HEAD AND SHOULDERS
        // =========================

        if (indicatorService.detectInverseHeadAndShoulders(closingPrices)) {
            detectedPatterns.add("Inverse Head and Shoulders");
        }

        // =========================
        // ASCENDING TRIANGLE
        // =========================

        if (indicatorService.detectAscendingTriangle(closingPrices)) {
            detectedPatterns.add("Ascending Triangle");
        }

        // =========================
        // DESCENDING TRIANGLE
        // =========================

        if (indicatorService.detectDescendingTriangle(closingPrices)) {
            detectedPatterns.add("Descending Triangle");
        }

        // =========================
        // SYMMETRICAL TRIANGLE
        // =========================

        if (indicatorService.detectSymmetricalTriangle(closingPrices)) {
            detectedPatterns.add("Symmetrical Triangle");
        }

        // =========================
        // RISING WEDGE
        // =========================

        if (indicatorService.detectRisingWedge(closingPrices)) {
            detectedPatterns.add("Rising Wedge");
        }

        // =========================
        // FALLING WEDGE
        // =========================

        if (indicatorService.detectFallingWedge(closingPrices)) {
            detectedPatterns.add("Falling Wedge");
        }

        // =========================
        // MARKET DIRECTION
        // =========================

        boolean bullishSignal =
                detectedPatterns.contains("Bullish Rectangle")
                        || detectedPatterns.contains("Ascending Channel")
                        || detectedPatterns.contains("Bullish Pennant")
                        || detectedPatterns.contains("Bull Flag")
                        || detectedPatterns.contains("Bullish Breakout")
                        || detectedPatterns.contains("Double Bottom")
                        || detectedPatterns.contains("Triple Bottom")
                        || detectedPatterns.contains("Inverse Head and Shoulders")
                        || detectedPatterns.contains("Ascending Triangle")
                        || detectedPatterns.contains("Falling Wedge");

        boolean bearishSignal =
                detectedPatterns.contains("Bearish Rectangle")
                        || detectedPatterns.contains("Descending Channel")
                        || detectedPatterns.contains("Bearish Pennant")
                        || detectedPatterns.contains("Bear Flag")
                        || detectedPatterns.contains("Bearish Breakdown")
                        || detectedPatterns.contains("Double Top")
                        || detectedPatterns.contains("Triple Top")
                        || detectedPatterns.contains("Head and Shoulders")
                        || detectedPatterns.contains("Descending Triangle")
                        || detectedPatterns.contains("Rising Wedge");

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