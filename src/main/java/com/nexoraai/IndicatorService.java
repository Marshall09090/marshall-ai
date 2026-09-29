package com.nexoraai.indicator;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class IndicatorService {

    private static final int SCALE = 8;

    // =========================
    // SMA
    // =========================

    public BigDecimal calculateSma(
            List<BigDecimal> prices,
            int period) {

        validatePricesAndPeriod(prices, period, "SMA");

        int startIndex = prices.size() - period;

        BigDecimal sum = prices
                .subList(startIndex, prices.size())
                .stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(
                BigDecimal.valueOf(period),
                SCALE,
                RoundingMode.HALF_UP
        );
    }

    // =========================
    // EMA
    // =========================

    public BigDecimal calculateEma(
            List<BigDecimal> prices,
            int period) {

        validatePricesAndPeriod(prices, period, "EMA");

        BigDecimal multiplier = BigDecimal.valueOf(2)
                .divide(
                        BigDecimal.valueOf(period + 1L),
                        SCALE,
                        RoundingMode.HALF_UP
                );

        BigDecimal ema = calculateInitialSma(
                prices,
                period
        );

        for (int i = period; i < prices.size(); i++) {

            BigDecimal price = prices.get(i);

            ema = price
                    .subtract(ema)
                    .multiply(multiplier)
                    .add(ema);
        }

        return ema.setScale(
                SCALE,
                RoundingMode.HALF_UP
        );
    }

    // =========================
    // RSI
    // =========================

    public BigDecimal calculateRsi(
            List<BigDecimal> prices,
            int period) {

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "Period must be greater than zero"
            );
        }

        if (prices == null || prices.size() < period + 1) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate RSI"
            );
        }

        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;

        for (int i = 1; i <= period; i++) {

            BigDecimal change = prices.get(i)
                    .subtract(prices.get(i - 1));

            if (change.signum() > 0) {
                gains = gains.add(change);
            } else if (change.signum() < 0) {
                losses = losses.add(change.abs());
            }
        }

        BigDecimal averageGain = gains.divide(
                BigDecimal.valueOf(period),
                SCALE,
                RoundingMode.HALF_UP
        );

        BigDecimal averageLoss = losses.divide(
                BigDecimal.valueOf(period),
                SCALE,
                RoundingMode.HALF_UP
        );

        for (int i = period + 1; i < prices.size(); i++) {

            BigDecimal change = prices.get(i)
                    .subtract(prices.get(i - 1));

            BigDecimal gain = change.signum() > 0
                    ? change
                    : BigDecimal.ZERO;

            BigDecimal loss = change.signum() < 0
                    ? change.abs()
                    : BigDecimal.ZERO;

            averageGain = averageGain
                    .multiply(BigDecimal.valueOf(period - 1L))
                    .add(gain)
                    .divide(
                            BigDecimal.valueOf(period),
                            SCALE,
                            RoundingMode.HALF_UP
                    );

            averageLoss = averageLoss
                    .multiply(BigDecimal.valueOf(period - 1L))
                    .add(loss)
                    .divide(
                            BigDecimal.valueOf(period),
                            SCALE,
                            RoundingMode.HALF_UP
                    );
        }

        if (averageLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100)
                    .setScale(
                            SCALE,
                            RoundingMode.HALF_UP
                    );
        }

        BigDecimal relativeStrength = averageGain.divide(
                averageLoss,
                SCALE,
                RoundingMode.HALF_UP
        );

        BigDecimal rsi = BigDecimal.valueOf(100)
                .subtract(
                        BigDecimal.valueOf(100)
                                .divide(
                                        BigDecimal.ONE.add(relativeStrength),
                                        SCALE,
                                        RoundingMode.HALF_UP
                                )
                );

        return rsi.setScale(
                SCALE,
                RoundingMode.HALF_UP
        );
    }

    // =========================
    // MACD LINE
    // =========================

    public BigDecimal calculateMacd(
            List<BigDecimal> prices,
            int fastPeriod,
            int slowPeriod) {

        validateMacdPeriods(
                prices,
                fastPeriod,
                slowPeriod
        );

        BigDecimal fastEma =
                calculateEma(prices, fastPeriod);

        BigDecimal slowEma =
                calculateEma(prices, slowPeriod);

        return fastEma
                .subtract(slowEma)
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // MACD SIGNAL LINE
    // =========================

    public BigDecimal calculateMacdSignalLine(
            List<BigDecimal> prices,
            int fastPeriod,
            int slowPeriod,
            int signalPeriod) {

        validateMacdPeriods(
                prices,
                fastPeriod,
                slowPeriod
        );

        validateSignalPeriod(signalPeriod);

        List<BigDecimal> macdValues =
                buildMacdSeries(
                        prices,
                        fastPeriod,
                        slowPeriod
                );

        if (macdValues.size() < signalPeriod) {
            throw new IllegalArgumentException(
                    "Not enough MACD values to calculate signal line"
            );
        }

        return calculateEma(
                macdValues,
                signalPeriod
        ).setScale(
                SCALE,
                RoundingMode.HALF_UP
        );
    }

    // =========================
    // MACD HISTOGRAM
    // =========================

    public BigDecimal calculateMacdHistogram(
            List<BigDecimal> prices,
            int fastPeriod,
            int slowPeriod,
            int signalPeriod) {

        validateMacdPeriods(
                prices,
                fastPeriod,
                slowPeriod
        );

        validateSignalPeriod(signalPeriod);

        BigDecimal macdLine =
                calculateMacd(
                        prices,
                        fastPeriod,
                        slowPeriod
                );

        BigDecimal signalLine =
                calculateMacdSignalLine(
                        prices,
                        fastPeriod,
                        slowPeriod,
                        signalPeriod
                );

        return macdLine
                .subtract(signalLine)
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // BOLLINGER BANDS
    // =========================

    public List<BigDecimal> calculateBollingerBands(
            List<BigDecimal> prices,
            int period,
            BigDecimal standardDeviationMultiplier) {

        validatePricesAndPeriod(
                prices,
                period,
                "Bollinger Bands"
        );

        if (standardDeviationMultiplier == null
                || standardDeviationMultiplier.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Standard deviation multiplier must be greater than zero"
            );
        }

        int startIndex = prices.size() - period;

        List<BigDecimal> recentPrices =
                prices.subList(
                        startIndex,
                        prices.size()
                );

        BigDecimal middleBand =
                calculateSma(
                        prices,
                        period
                );

        BigDecimal squaredDifferenceSum =
                BigDecimal.ZERO;

        for (BigDecimal price : recentPrices) {

            BigDecimal difference =
                    price.subtract(middleBand);

            BigDecimal squaredDifference =
                    difference.multiply(difference);

            squaredDifferenceSum =
                    squaredDifferenceSum.add(
                            squaredDifference
                    );
        }

        BigDecimal variance =
                squaredDifferenceSum.divide(
                        BigDecimal.valueOf(period),
                        SCALE,
                        RoundingMode.HALF_UP
                );

        BigDecimal standardDeviation =
                BigDecimal.valueOf(
                        Math.sqrt(
                                variance.doubleValue()
                        )
                );

        BigDecimal bandDistance =
                standardDeviation.multiply(
                        standardDeviationMultiplier
                );

        BigDecimal upperBand =
                middleBand
                        .add(bandDistance)
                        .setScale(
                                SCALE,
                                RoundingMode.HALF_UP
                        );

        BigDecimal lowerBand =
                middleBand
                        .subtract(bandDistance)
                        .setScale(
                                SCALE,
                                RoundingMode.HALF_UP
                        );

        return List.of(
                middleBand.setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                ),
                upperBand,
                lowerBand
        );
    }

    // =========================
    // AVERAGE TRUE RANGE (ATR)
    // =========================

    /**
     * Calculates the Average True Range (ATR).
     *
     * True Range is the greatest of:
     * 1. High - Low
     * 2. |High - Previous Close|
     * 3. |Low - Previous Close|
     */
    public BigDecimal calculateAtr(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            List<BigDecimal> closingPrices,
            int period) {

        if (highPrices == null
                || lowPrices == null
                || closingPrices == null) {

            throw new IllegalArgumentException(
                    "ATR price lists cannot be null"
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "ATR period must be greater than zero"
            );
        }

        if (highPrices.size() != lowPrices.size()
                || highPrices.size() != closingPrices.size()) {

            throw new IllegalArgumentException(
                    "ATR price lists must have the same size"
            );
        }

        if (highPrices.size() < period + 1) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate ATR"
            );
        }

        List<BigDecimal> trueRanges =
                new ArrayList<>();

        for (int i = 1; i < highPrices.size(); i++) {

            BigDecimal high =
                    highPrices.get(i);

            BigDecimal low =
                    lowPrices.get(i);

            BigDecimal previousClose =
                    closingPrices.get(i - 1);

            BigDecimal highLow =
                    high.subtract(low).abs();

            BigDecimal highPreviousClose =
                    high.subtract(previousClose).abs();

            BigDecimal lowPreviousClose =
                    low.subtract(previousClose).abs();

            BigDecimal trueRange =
                    highLow.max(
                            highPreviousClose.max(
                                    lowPreviousClose
                            )
                    );

            trueRanges.add(trueRange);
        }

        int startIndex =
                trueRanges.size() - period;

        BigDecimal sum = trueRanges
                .subList(
                        startIndex,
                        trueRanges.size()
                )
                .stream()
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );

        return sum.divide(
                BigDecimal.valueOf(period),
                SCALE,
                RoundingMode.HALF_UP
        );
    }

    // =========================
    // MACD HELPER
    // =========================

    private List<BigDecimal> buildMacdSeries(
            List<BigDecimal> prices,
            int fastPeriod,
            int slowPeriod) {

        List<BigDecimal> macdValues =
                new ArrayList<>();

        for (int end = slowPeriod;
             end <= prices.size();
             end++) {

            List<BigDecimal> priceWindow =
                    new ArrayList<>(
                            prices.subList(0, end)
                    );

            BigDecimal fastEma =
                    calculateEma(
                            priceWindow,
                            fastPeriod
                    );

            BigDecimal slowEma =
                    calculateEma(
                            priceWindow,
                            slowPeriod
                    );

            BigDecimal macd =
                    fastEma
                            .subtract(slowEma)
                            .setScale(
                                    SCALE,
                                    RoundingMode.HALF_UP
                            );

            macdValues.add(macd);
        }

        return macdValues;
    }

    // =========================
    // EMA HELPER
    // =========================

    private BigDecimal calculateInitialSma(
            List<BigDecimal> prices,
            int period) {

        BigDecimal sum = prices
                .subList(0, period)
                .stream()
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );

        return sum.divide(
                BigDecimal.valueOf(period),
                SCALE,
                RoundingMode.HALF_UP
        );
    }

    // =========================
    // VALIDATION
    // =========================

    private void validatePricesAndPeriod(
            List<BigDecimal> prices,
            int period,
            String indicatorName) {

        if (prices == null || prices.isEmpty()) {
            throw new IllegalArgumentException(
                    "Prices cannot be null or empty"
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "Period must be greater than zero"
            );
        }

        if (prices.size() < period) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate "
                            + indicatorName
                            + " for period "
                            + period
            );
        }
    }

    private void validateMacdPeriods(
            List<BigDecimal> prices,
            int fastPeriod,
            int slowPeriod) {

        if (fastPeriod <= 0 || slowPeriod <= 0) {
            throw new IllegalArgumentException(
                    "MACD periods must be greater than zero"
            );
        }

        if (fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException(
                    "MACD fast period must be less than slow period"
            );
        }

        validatePricesAndPeriod(
                prices,
                slowPeriod,
                "MACD"
        );
    }

    private void validateSignalPeriod(
            int signalPeriod) {

        if (signalPeriod <= 0) {
            throw new IllegalArgumentException(
                    "Signal period must be greater than zero"
            );
        }
    }
}