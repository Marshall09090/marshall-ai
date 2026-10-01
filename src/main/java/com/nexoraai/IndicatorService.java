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
    // STOCHASTIC OSCILLATOR %D
    // =========================

    public BigDecimal calculateStochasticPercentD(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            List<BigDecimal> closingPrices,
            int kPeriod,
            int dPeriod) {

        if (highPrices == null
                || lowPrices == null
                || closingPrices == null) {

            throw new IllegalArgumentException(
                    "Stochastic price lists cannot be null"
            );
        }

        if (kPeriod <= 0 || dPeriod <= 0) {
            throw new IllegalArgumentException(
                    "Stochastic K and D periods must be greater than zero"
            );
        }

        if (highPrices.size() != lowPrices.size()
                || highPrices.size() != closingPrices.size()) {

            throw new IllegalArgumentException(
                    "Stochastic price lists must have the same size"
            );
        }

        int requiredPrices = kPeriod + dPeriod - 1;

        if (highPrices.size() < requiredPrices) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate Stochastic Oscillator percent D"
            );
        }

        List<BigDecimal> percentKValues = new ArrayList<>();

        int firstEndIndex = highPrices.size() - dPeriod;

        for (int endIndex = firstEndIndex;
             endIndex < highPrices.size();
             endIndex++) {

            int startIndex = endIndex - kPeriod + 1;

            List<BigDecimal> highWindow =
                    new ArrayList<>(
                            highPrices.subList(
                                    startIndex,
                                    endIndex + 1
                            )
                    );

            List<BigDecimal> lowWindow =
                    new ArrayList<>(
                            lowPrices.subList(
                                    startIndex,
                                    endIndex + 1
                            )
                    );

            List<BigDecimal> closeWindow =
                    new ArrayList<>(
                            closingPrices.subList(
                                    startIndex,
                                    endIndex + 1
                            )
                    );

            BigDecimal percentK =
                    calculateStochasticPercentK(
                            highWindow,
                            lowWindow,
                            closeWindow,
                            kPeriod
                    );

            percentKValues.add(percentK);
        }

        BigDecimal sum =
                percentKValues.stream()
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        return sum.divide(
                BigDecimal.valueOf(dPeriod),
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
    // STOCHASTIC OSCILLATOR %K
    // =========================

    public BigDecimal calculateStochasticPercentK(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            List<BigDecimal> closingPrices,
            int period) {

        if (highPrices == null
                || lowPrices == null
                || closingPrices == null) {

            throw new IllegalArgumentException(
                    "Stochastic price lists cannot be null"
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "Stochastic period must be greater than zero"
            );
        }

        if (highPrices.size() != lowPrices.size()
                || highPrices.size() != closingPrices.size()) {

            throw new IllegalArgumentException(
                    "Stochastic price lists must have the same size"
            );
        }

        if (highPrices.size() < period) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate Stochastic Oscillator"
            );
        }

        int startIndex = highPrices.size() - period;

        BigDecimal highestHigh =
                highPrices.get(startIndex);

        BigDecimal lowestLow =
                lowPrices.get(startIndex);

        for (int i = startIndex + 1;
             i < highPrices.size();
             i++) {

            highestHigh =
                    highestHigh.max(
                            highPrices.get(i)
                    );

            lowestLow =
                    lowestLow.min(
                            lowPrices.get(i)
                    );
        }

        BigDecimal currentClose =
                closingPrices.get(
                        closingPrices.size() - 1
                );

        BigDecimal range =
                highestHigh.subtract(lowestLow);

        if (range.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(
                    SCALE,
                    RoundingMode.HALF_UP
            );
        }

        return currentClose
                .subtract(lowestLow)
                .divide(
                        range,
                        SCALE,
                        RoundingMode.HALF_UP
                )
                .multiply(
                        BigDecimal.valueOf(100)
                )
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // AVERAGE DIRECTIONAL INDEX (ADX)
    // =========================

    public BigDecimal calculateAdx(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            List<BigDecimal> closingPrices,
            int period) {

        validateDirectionalPrices(
                highPrices,
                lowPrices,
                closingPrices,
                period,
                "ADX"
        );

        List<BigDecimal> dxValues = new ArrayList<>();

        for (int endIndex = period;
             endIndex < highPrices.size();
             endIndex++) {

            int startIndex = endIndex - period + 1;

            BigDecimal trueRangeSum = BigDecimal.ZERO;
            BigDecimal positiveDirectionalMovementSum = BigDecimal.ZERO;
            BigDecimal negativeDirectionalMovementSum = BigDecimal.ZERO;

            for (int i = startIndex;
                 i <= endIndex;
                 i++) {

                BigDecimal currentHigh = highPrices.get(i);
                BigDecimal currentLow = lowPrices.get(i);
                BigDecimal previousHigh = highPrices.get(i - 1);
                BigDecimal previousLow = lowPrices.get(i - 1);
                BigDecimal previousClose = closingPrices.get(i - 1);

                BigDecimal highLow =
                        currentHigh.subtract(currentLow).abs();

                BigDecimal highPreviousClose =
                        currentHigh.subtract(previousClose).abs();

                BigDecimal lowPreviousClose =
                        currentLow.subtract(previousClose).abs();

                BigDecimal trueRange =
                        highLow.max(
                                highPreviousClose.max(
                                        lowPreviousClose
                                )
                        );

                trueRangeSum =
                        trueRangeSum.add(trueRange);

                BigDecimal upwardMove =
                        currentHigh.subtract(previousHigh);

                BigDecimal downwardMove =
                        previousLow.subtract(currentLow);

                BigDecimal positiveDirectionalMovement =
                        upwardMove.compareTo(downwardMove) > 0
                                && upwardMove.compareTo(BigDecimal.ZERO) > 0
                                ? upwardMove
                                : BigDecimal.ZERO;

                BigDecimal negativeDirectionalMovement =
                        downwardMove.compareTo(upwardMove) > 0
                                && downwardMove.compareTo(BigDecimal.ZERO) > 0
                                ? downwardMove
                                : BigDecimal.ZERO;

                positiveDirectionalMovementSum =
                        positiveDirectionalMovementSum.add(
                                positiveDirectionalMovement
                        );

                negativeDirectionalMovementSum =
                        negativeDirectionalMovementSum.add(
                                negativeDirectionalMovement
                        );
            }

            if (trueRangeSum.compareTo(BigDecimal.ZERO) == 0) {
                dxValues.add(
                        BigDecimal.ZERO.setScale(
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                );
                continue;
            }

            BigDecimal positiveDi =
                    positiveDirectionalMovementSum
                            .multiply(BigDecimal.valueOf(100))
                            .divide(
                                    trueRangeSum,
                                    SCALE,
                                    RoundingMode.HALF_UP
                            );

            BigDecimal negativeDi =
                    negativeDirectionalMovementSum
                            .multiply(BigDecimal.valueOf(100))
                            .divide(
                                    trueRangeSum,
                                    SCALE,
                                    RoundingMode.HALF_UP
                            );

            BigDecimal directionalIndexSum =
                    positiveDi.add(negativeDi);

            BigDecimal dx;

            if (directionalIndexSum.compareTo(BigDecimal.ZERO) == 0) {

                dx = BigDecimal.ZERO.setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );

            } else {

                dx = positiveDi
                        .subtract(negativeDi)
                        .abs()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(
                                directionalIndexSum,
                                SCALE,
                                RoundingMode.HALF_UP
                        );
            }

            dxValues.add(dx);
        }

        if (dxValues.isEmpty()) {
            throw new IllegalArgumentException(
                    "Not enough directional index values to calculate ADX"
            );
        }

        int valuesToAverage =
                Math.min(period, dxValues.size());

        int startIndex =
                dxValues.size() - valuesToAverage;

        BigDecimal dxSum =
                dxValues
                        .subList(
                                startIndex,
                                dxValues.size()
                        )
                        .stream()
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        return dxSum.divide(
                BigDecimal.valueOf(valuesToAverage),
                SCALE,
                RoundingMode.HALF_UP
        );
    }

    // =========================
    // POSITIVE DIRECTIONAL INDICATOR (+DI)
    // =========================

    public BigDecimal calculatePositiveDi(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            List<BigDecimal> closingPrices,
            int period) {

        validateDirectionalPrices(
                highPrices,
                lowPrices,
                closingPrices,
                period,
                "Positive Directional Indicator"
        );

        int startIndex = highPrices.size() - period;

        BigDecimal trueRangeSum = BigDecimal.ZERO;
        BigDecimal positiveDirectionalMovementSum = BigDecimal.ZERO;

        for (int i = startIndex;
             i < highPrices.size();
             i++) {

            BigDecimal currentHigh = highPrices.get(i);
            BigDecimal currentLow = lowPrices.get(i);
            BigDecimal previousHigh = highPrices.get(i - 1);
            BigDecimal previousLow = lowPrices.get(i - 1);
            BigDecimal previousClose = closingPrices.get(i - 1);

            BigDecimal highLow =
                    currentHigh.subtract(currentLow).abs();

            BigDecimal highPreviousClose =
                    currentHigh.subtract(previousClose).abs();

            BigDecimal lowPreviousClose =
                    currentLow.subtract(previousClose).abs();

            BigDecimal trueRange =
                    highLow.max(
                            highPreviousClose.max(
                                    lowPreviousClose
                            )
                    );

            trueRangeSum =
                    trueRangeSum.add(trueRange);

            BigDecimal upwardMove =
                    currentHigh.subtract(previousHigh);

            BigDecimal downwardMove =
                    previousLow.subtract(currentLow);

            if (upwardMove.compareTo(downwardMove) > 0
                    && upwardMove.compareTo(BigDecimal.ZERO) > 0) {

                positiveDirectionalMovementSum =
                        positiveDirectionalMovementSum.add(
                                upwardMove
                        );
            }
        }

        if (trueRangeSum.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(
                    SCALE,
                    RoundingMode.HALF_UP
            );
        }

        return positiveDirectionalMovementSum
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        trueRangeSum,
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // NEGATIVE DIRECTIONAL INDICATOR (-DI)
    // =========================

    public BigDecimal calculateNegativeDi(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            List<BigDecimal> closingPrices,
            int period) {

        validateDirectionalPrices(
                highPrices,
                lowPrices,
                closingPrices,
                period,
                "Negative Directional Indicator"
        );

        int startIndex = highPrices.size() - period;

        BigDecimal trueRangeSum = BigDecimal.ZERO;
        BigDecimal negativeDirectionalMovementSum = BigDecimal.ZERO;

        for (int i = startIndex;
             i < highPrices.size();
             i++) {

            BigDecimal currentHigh = highPrices.get(i);
            BigDecimal currentLow = lowPrices.get(i);
            BigDecimal previousHigh = highPrices.get(i - 1);
            BigDecimal previousLow = lowPrices.get(i - 1);
            BigDecimal previousClose = closingPrices.get(i - 1);

            BigDecimal highLow =
                    currentHigh.subtract(currentLow).abs();

            BigDecimal highPreviousClose =
                    currentHigh.subtract(previousClose).abs();

            BigDecimal lowPreviousClose =
                    currentLow.subtract(previousClose).abs();

            BigDecimal trueRange =
                    highLow.max(
                            highPreviousClose.max(
                                    lowPreviousClose
                            )
                    );

            trueRangeSum =
                    trueRangeSum.add(trueRange);

            BigDecimal upwardMove =
                    currentHigh.subtract(previousHigh);

            BigDecimal downwardMove =
                    previousLow.subtract(currentLow);

            if (downwardMove.compareTo(upwardMove) > 0
                    && downwardMove.compareTo(BigDecimal.ZERO) > 0) {

                negativeDirectionalMovementSum =
                        negativeDirectionalMovementSum.add(
                                downwardMove
                        );
            }
        }

        if (trueRangeSum.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(
                    SCALE,
                    RoundingMode.HALF_UP
            );
        }

        return negativeDirectionalMovementSum
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        trueRangeSum,
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // COMMODITY CHANNEL INDEX (CCI)
    // =========================

    public BigDecimal calculateCci(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            List<BigDecimal> closingPrices,
            int period) {

        if (highPrices == null
                || lowPrices == null
                || closingPrices == null) {

            throw new IllegalArgumentException(
                    "CCI price lists cannot be null"
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "CCI period must be greater than zero"
            );
        }

        if (highPrices.size() != lowPrices.size()
                || highPrices.size() != closingPrices.size()) {

            throw new IllegalArgumentException(
                    "CCI price lists must have the same size"
            );
        }

        if (highPrices.size() < period) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate CCI"
            );
        }

        int startIndex = highPrices.size() - period;

        List<BigDecimal> typicalPrices = new ArrayList<>();

        for (int i = startIndex;
             i < highPrices.size();
             i++) {

            BigDecimal typicalPrice =
                    highPrices.get(i)
                            .add(lowPrices.get(i))
                            .add(closingPrices.get(i))
                            .divide(
                                    BigDecimal.valueOf(3),
                                    SCALE,
                                    RoundingMode.HALF_UP
                            );

            typicalPrices.add(typicalPrice);
        }

        BigDecimal typicalPriceSum =
                typicalPrices.stream()
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal typicalPriceSma =
                typicalPriceSum.divide(
                        BigDecimal.valueOf(period),
                        SCALE,
                        RoundingMode.HALF_UP
                );

        BigDecimal meanDeviationSum =
                BigDecimal.ZERO;

        for (BigDecimal typicalPrice : typicalPrices) {

            meanDeviationSum =
                    meanDeviationSum.add(
                            typicalPrice
                                    .subtract(typicalPriceSma)
                                    .abs()
                    );
        }

        BigDecimal meanDeviation =
                meanDeviationSum.divide(
                        BigDecimal.valueOf(period),
                        SCALE,
                        RoundingMode.HALF_UP
                );

        if (meanDeviation.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(
                    SCALE,
                    RoundingMode.HALF_UP
            );
        }

        BigDecimal currentTypicalPrice =
                typicalPrices.get(
                        typicalPrices.size() - 1
                );

        BigDecimal constant =
                new BigDecimal("0.015");

        return currentTypicalPrice
                .subtract(typicalPriceSma)
                .divide(
                        constant.multiply(meanDeviation),
                        SCALE,
                        RoundingMode.HALF_UP
                )
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // WILLIAMS PERCENT R
    // =========================

    public BigDecimal calculateWilliamsPercentR(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            List<BigDecimal> closingPrices,
            int period) {

        if (highPrices == null
                || lowPrices == null
                || closingPrices == null) {

            throw new IllegalArgumentException(
                    "Williams Percent R price lists cannot be null"
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "Williams Percent R period must be greater than zero"
            );
        }

        if (highPrices.size() != lowPrices.size()
                || highPrices.size() != closingPrices.size()) {

            throw new IllegalArgumentException(
                    "Williams Percent R price lists must have the same size"
            );
        }

        if (highPrices.size() < period) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate Williams Percent R"
            );
        }

        int startIndex = highPrices.size() - period;

        BigDecimal highestHigh =
                highPrices.get(startIndex);

        BigDecimal lowestLow =
                lowPrices.get(startIndex);

        for (int i = startIndex + 1;
             i < highPrices.size();
             i++) {

            highestHigh =
                    highestHigh.max(
                            highPrices.get(i)
                    );

            lowestLow =
                    lowestLow.min(
                            lowPrices.get(i)
                    );
        }

        BigDecimal currentClose =
                closingPrices.get(
                        closingPrices.size() - 1
                );

        BigDecimal range =
                highestHigh.subtract(lowestLow);

        if (range.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(
                    SCALE,
                    RoundingMode.HALF_UP
            );
        }

        return highestHigh
                .subtract(currentClose)
                .divide(
                        range,
                        SCALE,
                        RoundingMode.HALF_UP
                )
                .multiply(
                        BigDecimal.valueOf(-100)
                )
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // RATE OF CHANGE (ROC)
    // =========================

    public BigDecimal calculateRoc(
            List<BigDecimal> prices,
            int period) {

        if (prices == null || prices.isEmpty()) {
            throw new IllegalArgumentException(
                    "ROC prices cannot be null or empty"
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "ROC period must be greater than zero"
            );
        }

        if (prices.size() < period + 1) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate ROC"
            );
        }

        BigDecimal currentPrice =
                prices.get(prices.size() - 1);

        BigDecimal previousPrice =
                prices.get(prices.size() - 1 - period);

        if (previousPrice.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException(
                    "Previous price cannot be zero when calculating ROC"
            );
        }

        return currentPrice
                .subtract(previousPrice)
                .divide(
                        previousPrice,
                        SCALE,
                        RoundingMode.HALF_UP
                )
                .multiply(
                        BigDecimal.valueOf(100)
                )
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // MONEY FLOW INDEX (MFI)
    // =========================

    public BigDecimal calculateMfi(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            List<BigDecimal> closingPrices,
            List<BigDecimal> volumes,
            int period) {

        if (highPrices == null
                || lowPrices == null
                || closingPrices == null
                || volumes == null) {

            throw new IllegalArgumentException(
                    "MFI price and volume lists cannot be null"
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "MFI period must be greater than zero"
            );
        }

        if (highPrices.size() != lowPrices.size()
                || highPrices.size() != closingPrices.size()
                || highPrices.size() != volumes.size()) {

            throw new IllegalArgumentException(
                    "MFI price and volume lists must have the same size"
            );
        }

        if (highPrices.size() < period + 1) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate MFI"
            );
        }

        int startIndex =
                highPrices.size() - period;

        BigDecimal positiveMoneyFlow =
                BigDecimal.ZERO;

        BigDecimal negativeMoneyFlow =
                BigDecimal.ZERO;

        for (int i = startIndex;
             i < highPrices.size();
             i++) {

            BigDecimal previousTypicalPrice =
                    highPrices.get(i - 1)
                            .add(lowPrices.get(i - 1))
                            .add(closingPrices.get(i - 1))
                            .divide(
                                    BigDecimal.valueOf(3),
                                    SCALE,
                                    RoundingMode.HALF_UP
                            );

            BigDecimal currentTypicalPrice =
                    highPrices.get(i)
                            .add(lowPrices.get(i))
                            .add(closingPrices.get(i))
                            .divide(
                                    BigDecimal.valueOf(3),
                                    SCALE,
                                    RoundingMode.HALF_UP
                            );

            BigDecimal rawMoneyFlow =
                    currentTypicalPrice.multiply(
                            volumes.get(i)
                    );

            if (currentTypicalPrice.compareTo(previousTypicalPrice) > 0) {

                positiveMoneyFlow =
                        positiveMoneyFlow.add(
                                rawMoneyFlow
                        );

            } else if (currentTypicalPrice.compareTo(previousTypicalPrice) < 0) {

                negativeMoneyFlow =
                        negativeMoneyFlow.add(
                                rawMoneyFlow
                        );
            }
        }

        if (negativeMoneyFlow.compareTo(BigDecimal.ZERO) == 0) {

            if (positiveMoneyFlow.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.valueOf(50)
                        .setScale(
                                SCALE,
                                RoundingMode.HALF_UP
                        );
            }

            return BigDecimal.valueOf(100)
                    .setScale(
                            SCALE,
                            RoundingMode.HALF_UP
                    );
        }

        if (positiveMoneyFlow.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(
                    SCALE,
                    RoundingMode.HALF_UP
            );
        }

        BigDecimal moneyFlowRatio =
                positiveMoneyFlow.divide(
                        negativeMoneyFlow,
                        SCALE,
                        RoundingMode.HALF_UP
                );

        return BigDecimal.valueOf(100)
                .subtract(
                        BigDecimal.valueOf(100)
                                .divide(
                                        BigDecimal.ONE.add(
                                                moneyFlowRatio
                                        ),
                                        SCALE,
                                        RoundingMode.HALF_UP
                                )
                )
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // ON-BALANCE VOLUME (OBV)
    // =========================

    public BigDecimal calculateObv(
            List<BigDecimal> closingPrices,
            List<BigDecimal> volumes) {

        if (closingPrices == null || volumes == null) {
            throw new IllegalArgumentException(
                    "OBV price and volume lists cannot be null"
            );
        }

        if (closingPrices.isEmpty()) {
            throw new IllegalArgumentException(
                    "OBV prices cannot be empty"
            );
        }

        if (closingPrices.size() != volumes.size()) {
            throw new IllegalArgumentException(
                    "OBV price and volume lists must have the same size"
            );
        }

        BigDecimal obv = BigDecimal.ZERO;

        for (int i = 1; i < closingPrices.size(); i++) {

            BigDecimal currentClose =
                    closingPrices.get(i);

            BigDecimal previousClose =
                    closingPrices.get(i - 1);

            BigDecimal currentVolume =
                    volumes.get(i);

            if (currentClose.compareTo(previousClose) > 0) {

                obv = obv.add(currentVolume);

            } else if (currentClose.compareTo(previousClose) < 0) {

                obv = obv.subtract(currentVolume);
            }
        }

        return obv.setScale(
                SCALE,
                RoundingMode.HALF_UP
        );
    }

    // =========================
    // CHAIKIN MONEY FLOW (CMF)
    // =========================

    public BigDecimal calculateCmf(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            List<BigDecimal> closingPrices,
            List<BigDecimal> volumes,
            int period) {

        if (highPrices == null
                || lowPrices == null
                || closingPrices == null
                || volumes == null) {

            throw new IllegalArgumentException(
                    "CMF price and volume lists cannot be null"
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "CMF period must be greater than zero"
            );
        }

        if (highPrices.size() != lowPrices.size()
                || highPrices.size() != closingPrices.size()
                || highPrices.size() != volumes.size()) {

            throw new IllegalArgumentException(
                    "CMF price and volume lists must have the same size"
            );
        }

        if (highPrices.size() < period) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate CMF"
            );
        }

        int startIndex = highPrices.size() - period;

        BigDecimal moneyFlowVolumeSum = BigDecimal.ZERO;
        BigDecimal volumeSum = BigDecimal.ZERO;

        for (int i = startIndex;
             i < highPrices.size();
             i++) {

            BigDecimal high = highPrices.get(i);
            BigDecimal low = lowPrices.get(i);
            BigDecimal close = closingPrices.get(i);
            BigDecimal volume = volumes.get(i);

            BigDecimal range = high.subtract(low);

            BigDecimal moneyFlowMultiplier;

            if (range.compareTo(BigDecimal.ZERO) == 0) {

                moneyFlowMultiplier = BigDecimal.ZERO;

            } else {

                moneyFlowMultiplier = close
                        .subtract(low)
                        .subtract(high.subtract(close))
                        .divide(
                                range,
                                SCALE,
                                RoundingMode.HALF_UP
                        );
            }

            BigDecimal moneyFlowVolume =
                    moneyFlowMultiplier.multiply(volume);

            moneyFlowVolumeSum =
                    moneyFlowVolumeSum.add(
                            moneyFlowVolume
                    );

            volumeSum =
                    volumeSum.add(volume);
        }

        if (volumeSum.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(
                    SCALE,
                    RoundingMode.HALF_UP
            );
        }

        return moneyFlowVolumeSum
                .divide(
                        volumeSum,
                        SCALE,
                        RoundingMode.HALF_UP
                )
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // ACCUMULATION/DISTRIBUTION LINE (A/D LINE)
    // =========================

    public BigDecimal calculateAccumulationDistributionLine(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            List<BigDecimal> closingPrices,
            List<BigDecimal> volumes) {

        if (highPrices == null
                || lowPrices == null
                || closingPrices == null
                || volumes == null) {

            throw new IllegalArgumentException(
                    "Accumulation Distribution Line price and volume lists cannot be null"
            );
        }

        if (highPrices.isEmpty()) {
            throw new IllegalArgumentException(
                    "Accumulation Distribution Line prices cannot be empty"
            );
        }

        if (highPrices.size() != lowPrices.size()
                || highPrices.size() != closingPrices.size()
                || highPrices.size() != volumes.size()) {

            throw new IllegalArgumentException(
                    "Accumulation Distribution Line price and volume lists must have the same size"
            );
        }

        BigDecimal accumulationDistributionLine = BigDecimal.ZERO;

        for (int i = 0; i < highPrices.size(); i++) {

            BigDecimal high = highPrices.get(i);
            BigDecimal low = lowPrices.get(i);
            BigDecimal close = closingPrices.get(i);
            BigDecimal volume = volumes.get(i);

            BigDecimal range = high.subtract(low);

            BigDecimal moneyFlowMultiplier;

            if (range.compareTo(BigDecimal.ZERO) == 0) {

                moneyFlowMultiplier = BigDecimal.ZERO;

            } else {

                moneyFlowMultiplier = close
                        .subtract(low)
                        .subtract(high.subtract(close))
                        .divide(
                                range,
                                SCALE,
                                RoundingMode.HALF_UP
                        );
            }

            BigDecimal moneyFlowVolume =
                    moneyFlowMultiplier.multiply(volume);

            accumulationDistributionLine =
                    accumulationDistributionLine.add(
                            moneyFlowVolume
                    );
        }

        return accumulationDistributionLine.setScale(
                SCALE,
                RoundingMode.HALF_UP
        );
    }

    // =========================
    // VOLUME WEIGHTED AVERAGE PRICE (VWAP)
    // =========================

    public BigDecimal calculateVwap(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            List<BigDecimal> closingPrices,
            List<BigDecimal> volumes) {

        if (highPrices == null
                || lowPrices == null
                || closingPrices == null
                || volumes == null) {

            throw new IllegalArgumentException(
                    "VWAP price and volume lists cannot be null"
            );
        }

        if (highPrices.isEmpty()) {
            throw new IllegalArgumentException(
                    "VWAP prices cannot be empty"
            );
        }

        if (highPrices.size() != lowPrices.size()
                || highPrices.size() != closingPrices.size()
                || highPrices.size() != volumes.size()) {

            throw new IllegalArgumentException(
                    "VWAP price and volume lists must have the same size"
            );
        }

        BigDecimal cumulativeTypicalPriceVolume =
                BigDecimal.ZERO;

        BigDecimal cumulativeVolume =
                BigDecimal.ZERO;

        for (int i = 0; i < highPrices.size(); i++) {

            BigDecimal high =
                    highPrices.get(i);

            BigDecimal low =
                    lowPrices.get(i);

            BigDecimal close =
                    closingPrices.get(i);

            BigDecimal volume =
                    volumes.get(i);

            BigDecimal typicalPrice =
                    high
                            .add(low)
                            .add(close)
                            .divide(
                                    BigDecimal.valueOf(3),
                                    SCALE,
                                    RoundingMode.HALF_UP
                            );

            BigDecimal typicalPriceVolume =
                    typicalPrice.multiply(volume);

            cumulativeTypicalPriceVolume =
                    cumulativeTypicalPriceVolume.add(
                            typicalPriceVolume
                    );

            cumulativeVolume =
                    cumulativeVolume.add(volume);
        }

        if (cumulativeVolume.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(
                    SCALE,
                    RoundingMode.HALF_UP
            );
        }

        return cumulativeTypicalPriceVolume
                .divide(
                        cumulativeVolume,
                        SCALE,
                        RoundingMode.HALF_UP
                )
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // AVERAGE VOLUME
    // =========================

    public BigDecimal calculateAverageVolume(
            List<BigDecimal> volumes,
            int period) {

        if (volumes == null || volumes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Average Volume values cannot be null or empty"
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "Average Volume period must be greater than zero"
            );
        }

        if (volumes.size() < period) {
            throw new IllegalArgumentException(
                    "Not enough volume values to calculate Average Volume"
            );
        }

        int startIndex = volumes.size() - period;

        BigDecimal volumeSum = volumes
                .subList(
                        startIndex,
                        volumes.size()
                )
                .stream()
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );

        return volumeSum
                .divide(
                        BigDecimal.valueOf(period),
                        SCALE,
                        RoundingMode.HALF_UP
                )
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // PARABOLIC SAR
    // =========================

    public BigDecimal calculateParabolicSar(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            BigDecimal initialAccelerationFactor,
            BigDecimal maximumAccelerationFactor) {

        if (highPrices == null || lowPrices == null) {
            throw new IllegalArgumentException(
                    "Parabolic SAR price lists cannot be null"
            );
        }

        if (highPrices.size() != lowPrices.size()) {
            throw new IllegalArgumentException(
                    "Parabolic SAR price lists must have the same size"
            );
        }

        if (highPrices.size() < 2) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate Parabolic SAR"
            );
        }

        if (initialAccelerationFactor == null
                || initialAccelerationFactor.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Parabolic SAR initial acceleration factor must be greater than zero"
            );
        }

        if (maximumAccelerationFactor == null
                || maximumAccelerationFactor.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Parabolic SAR maximum acceleration factor must be greater than zero"
            );
        }

        if (initialAccelerationFactor.compareTo(maximumAccelerationFactor) > 0) {
            throw new IllegalArgumentException(
                    "Parabolic SAR initial acceleration factor cannot exceed maximum acceleration factor"
            );
        }

        BigDecimal accelerationStep =
                initialAccelerationFactor;

        boolean upTrend =
                highPrices.get(1).compareTo(highPrices.get(0)) >= 0;

        BigDecimal sar;
        BigDecimal extremePoint;

        if (upTrend) {

            sar = lowPrices.get(0);

            extremePoint =
                    highPrices.get(0).max(
                            highPrices.get(1)
                    );

        } else {

            sar = highPrices.get(0);

            extremePoint =
                    lowPrices.get(0).min(
                            lowPrices.get(1)
                    );
        }

        BigDecimal accelerationFactor =
                initialAccelerationFactor;

        for (int i = 1; i < highPrices.size(); i++) {

            BigDecimal currentHigh =
                    highPrices.get(i);

            BigDecimal currentLow =
                    lowPrices.get(i);

            BigDecimal nextSar =
                    sar.add(
                            accelerationFactor.multiply(
                                    extremePoint.subtract(sar)
                            )
                    );

            if (upTrend) {

                if (i >= 1) {
                    nextSar =
                            nextSar.min(
                                    lowPrices.get(i - 1)
                            );
                }

                if (i >= 2) {
                    nextSar =
                            nextSar.min(
                                    lowPrices.get(i - 2)
                            );
                }

                if (currentLow.compareTo(nextSar) < 0) {

                    upTrend = false;

                    nextSar =
                            extremePoint;

                    extremePoint =
                            currentLow;

                    accelerationFactor =
                            initialAccelerationFactor;

                } else if (currentHigh.compareTo(extremePoint) > 0) {

                    extremePoint =
                            currentHigh;

                    accelerationFactor =
                            accelerationFactor
                                    .add(accelerationStep)
                                    .min(
                                            maximumAccelerationFactor
                                    );
                }

            } else {

                if (i >= 1) {
                    nextSar =
                            nextSar.max(
                                    highPrices.get(i - 1)
                            );
                }

                if (i >= 2) {
                    nextSar =
                            nextSar.max(
                                    highPrices.get(i - 2)
                            );
                }

                if (currentHigh.compareTo(nextSar) > 0) {

                    upTrend = true;

                    nextSar =
                            extremePoint;

                    extremePoint =
                            currentHigh;

                    accelerationFactor =
                            initialAccelerationFactor;

                } else if (currentLow.compareTo(extremePoint) < 0) {

                    extremePoint =
                            currentLow;

                    accelerationFactor =
                            accelerationFactor
                                    .add(accelerationStep)
                                    .min(
                                            maximumAccelerationFactor
                                    );
                }
            }

            sar =
                    nextSar.setScale(
                            SCALE,
                            RoundingMode.HALF_UP
                    );
        }

        return sar.setScale(
                SCALE,
                RoundingMode.HALF_UP
        );
    }

    // =========================
    // ICHIMOKU TENKAN-SEN
    // =========================

    public BigDecimal calculateIchimokuTenkanSen(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            int period) {

        if (highPrices == null || lowPrices == null) {
            throw new IllegalArgumentException(
                    "Ichimoku Tenkan-sen price lists cannot be null"
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "Ichimoku Tenkan-sen period must be greater than zero"
            );
        }

        if (highPrices.size() != lowPrices.size()) {
            throw new IllegalArgumentException(
                    "Ichimoku Tenkan-sen price lists must have the same size"
            );
        }

        if (highPrices.size() < period) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate Ichimoku Tenkan-sen"
            );
        }

        int startIndex = highPrices.size() - period;

        BigDecimal highestHigh =
                highPrices.get(startIndex);

        BigDecimal lowestLow =
                lowPrices.get(startIndex);

        for (int i = startIndex + 1;
             i < highPrices.size();
             i++) {

            highestHigh =
                    highestHigh.max(
                            highPrices.get(i)
                    );

            lowestLow =
                    lowestLow.min(
                            lowPrices.get(i)
                    );
        }

        return highestHigh
                .add(lowestLow)
                .divide(
                        BigDecimal.valueOf(2),
                        SCALE,
                        RoundingMode.HALF_UP
                )
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // ICHIMOKU KIJUN-SEN
    // =========================

    public BigDecimal calculateIchimokuKijunSen(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            int period) {

        if (highPrices == null || lowPrices == null) {
            throw new IllegalArgumentException(
                    "Ichimoku Kijun-sen price lists cannot be null"
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "Ichimoku Kijun-sen period must be greater than zero"
            );
        }

        if (highPrices.size() != lowPrices.size()) {
            throw new IllegalArgumentException(
                    "Ichimoku Kijun-sen price lists must have the same size"
            );
        }

        if (highPrices.size() < period) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate Ichimoku Kijun-sen"
            );
        }

        int startIndex = highPrices.size() - period;

        BigDecimal highestHigh =
                highPrices.get(startIndex);

        BigDecimal lowestLow =
                lowPrices.get(startIndex);

        for (int i = startIndex + 1;
             i < highPrices.size();
             i++) {

            highestHigh =
                    highestHigh.max(
                            highPrices.get(i)
                    );

            lowestLow =
                    lowestLow.min(
                            lowPrices.get(i)
                    );
        }

        return highestHigh
                .add(lowestLow)
                .divide(
                        BigDecimal.valueOf(2),
                        SCALE,
                        RoundingMode.HALF_UP
                )
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // ICHIMOKU SENKOU SPAN A
    // =========================

    public BigDecimal calculateIchimokuSenkouSpanA(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            int tenkanPeriod,
            int kijunPeriod) {

        if (highPrices == null || lowPrices == null) {
            throw new IllegalArgumentException(
                    "Ichimoku Senkou Span A price lists cannot be null"
            );
        }

        if (tenkanPeriod <= 0 || kijunPeriod <= 0) {
            throw new IllegalArgumentException(
                    "Ichimoku Senkou Span A periods must be greater than zero"
            );
        }

        if (highPrices.size() != lowPrices.size()) {
            throw new IllegalArgumentException(
                    "Ichimoku Senkou Span A price lists must have the same size"
            );
        }

        int requiredPrices = Math.max(
                tenkanPeriod,
                kijunPeriod
        );

        if (highPrices.size() < requiredPrices) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate Ichimoku Senkou Span A"
            );
        }

        BigDecimal tenkanSen =
                calculateIchimokuTenkanSen(
                        highPrices,
                        lowPrices,
                        tenkanPeriod
                );

        BigDecimal kijunSen =
                calculateIchimokuKijunSen(
                        highPrices,
                        lowPrices,
                        kijunPeriod
                );

        return tenkanSen
                .add(kijunSen)
                .divide(
                        BigDecimal.valueOf(2),
                        SCALE,
                        RoundingMode.HALF_UP
                )
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // ICHIMOKU SENKOU SPAN B
    // =========================

    public BigDecimal calculateIchimokuSenkouSpanB(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            int period) {

        if (highPrices == null || lowPrices == null) {
            throw new IllegalArgumentException(
                    "Ichimoku Senkou Span B price lists cannot be null"
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "Ichimoku Senkou Span B period must be greater than zero"
            );
        }

        if (highPrices.size() != lowPrices.size()) {
            throw new IllegalArgumentException(
                    "Ichimoku Senkou Span B price lists must have the same size"
            );
        }

        if (highPrices.size() < period) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate Ichimoku Senkou Span B"
            );
        }

        int startIndex = highPrices.size() - period;

        BigDecimal highestHigh =
                highPrices.get(startIndex);

        BigDecimal lowestLow =
                lowPrices.get(startIndex);

        for (int i = startIndex + 1;
             i < highPrices.size();
             i++) {

            highestHigh =
                    highestHigh.max(
                            highPrices.get(i)
                    );

            lowestLow =
                    lowestLow.min(
                            lowPrices.get(i)
                    );
        }

        return highestHigh
                .add(lowestLow)
                .divide(
                        BigDecimal.valueOf(2),
                        SCALE,
                        RoundingMode.HALF_UP
                )
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // ICHIMOKU CHIKOU SPAN
    // =========================

    public BigDecimal calculateIchimokuChikouSpan(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null || closingPrices.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ichimoku Chikou Span closing prices cannot be null or empty"
            );
        }

        return closingPrices
                .get(closingPrices.size() - 1)
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // AROON UP
    // =========================

    public BigDecimal calculateAroonUp(
            List<BigDecimal> highPrices,
            int period) {

        if (highPrices == null || highPrices.isEmpty()) {
            throw new IllegalArgumentException(
                    "Aroon Up high prices cannot be null or empty"
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "Aroon Up period must be greater than zero"
            );
        }

        if (highPrices.size() < period) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate Aroon Up"
            );
        }

        int startIndex = highPrices.size() - period;

        int highestHighIndex = startIndex;

        BigDecimal highestHigh =
                highPrices.get(startIndex);

        for (int i = startIndex + 1;
             i < highPrices.size();
             i++) {

            if (highPrices.get(i).compareTo(highestHigh) >= 0) {

                highestHigh =
                        highPrices.get(i);

                highestHighIndex = i;
            }
        }

        int periodsSinceHighestHigh =
                highPrices.size() - 1 - highestHighIndex;

        return BigDecimal.valueOf(
                        period - periodsSinceHighestHigh
                )
                .multiply(
                        BigDecimal.valueOf(100)
                )
                .divide(
                        BigDecimal.valueOf(period),
                        SCALE,
                        RoundingMode.HALF_UP
                )
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // AROON DOWN
    // =========================

    public BigDecimal calculateAroonDown(
            List<BigDecimal> lowPrices,
            int period) {

        if (lowPrices == null || lowPrices.isEmpty()) {
            throw new IllegalArgumentException(
                    "Aroon Down low prices cannot be null or empty"
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "Aroon Down period must be greater than zero"
            );
        }

        if (lowPrices.size() < period) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate Aroon Down"
            );
        }

        int startIndex = lowPrices.size() - period;

        int lowestLowIndex = startIndex;

        BigDecimal lowestLow =
                lowPrices.get(startIndex);

        for (int i = startIndex + 1;
             i < lowPrices.size();
             i++) {

            if (lowPrices.get(i).compareTo(lowestLow) <= 0) {

                lowestLow =
                        lowPrices.get(i);

                lowestLowIndex = i;
            }
        }

        int periodsSinceLowestLow =
                lowPrices.size() - 1 - lowestLowIndex;

        return BigDecimal.valueOf(
                        period - periodsSinceLowestLow
                )
                .multiply(
                        BigDecimal.valueOf(100)
                )
                .divide(
                        BigDecimal.valueOf(period),
                        SCALE,
                        RoundingMode.HALF_UP
                )
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // AROON OSCILLATOR
    // =========================

    public BigDecimal calculateAroonOscillator(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            int period) {

        if (highPrices == null || lowPrices == null) {
            throw new IllegalArgumentException(
                    "Aroon Oscillator price lists cannot be null"
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "Aroon Oscillator period must be greater than zero"
            );
        }

        if (highPrices.size() != lowPrices.size()) {
            throw new IllegalArgumentException(
                    "Aroon Oscillator price lists must have the same size"
            );
        }

        if (highPrices.size() < period) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate Aroon Oscillator"
            );
        }

        BigDecimal aroonUp =
                calculateAroonUp(
                        highPrices,
                        period
                );

        BigDecimal aroonDown =
                calculateAroonDown(
                        lowPrices,
                        period
                );

        return aroonUp
                .subtract(aroonDown)
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    // =========================
    // BULL FLAG PATTERN
    // =========================

    public boolean detectBullFlag(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Bull Flag closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 7) {
            return false;
        }

        for (BigDecimal price : closingPrices) {
            if (price == null) {
                throw new IllegalArgumentException(
                        "Bull Flag closing prices cannot contain null values"
                );
            }

            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Bull Flag closing prices must be greater than zero"
                );
            }
        }

        BigDecimal startingPrice =
                closingPrices.get(0);

        for (int peakIndex = 2;
             peakIndex <= closingPrices.size() - 4;
             peakIndex++) {

            BigDecimal polePeak =
                    closingPrices.get(peakIndex);

            boolean localPeak =
                    polePeak.compareTo(
                            closingPrices.get(peakIndex - 1)
                    ) > 0
                            && polePeak.compareTo(
                            closingPrices.get(peakIndex + 1)
                    ) > 0;

            if (!localPeak) {
                continue;
            }

            BigDecimal poleGainPercent =
                    polePeak
                            .subtract(startingPrice)
                            .divide(
                                    startingPrice,
                                    SCALE,
                                    RoundingMode.HALF_UP
                            )
                            .multiply(
                                    BigDecimal.valueOf(100)
                            );

            if (poleGainPercent.compareTo(
                    BigDecimal.valueOf(5)
            ) < 0) {
                continue;
            }

            boolean poleMostlyRising = true;

            for (int i = 1; i <= peakIndex; i++) {

                if (closingPrices.get(i).compareTo(
                        closingPrices.get(i - 1)
                ) < 0) {
                    poleMostlyRising = false;
                    break;
                }
            }

            if (!poleMostlyRising) {
                continue;
            }

            int breakoutIndex = -1;

            for (int i = peakIndex + 3;
                 i < closingPrices.size();
                 i++) {

                if (closingPrices.get(i).compareTo(polePeak) > 0) {
                    breakoutIndex = i;
                    break;
                }
            }

            if (breakoutIndex == -1) {
                continue;
            }

            BigDecimal pullbackLow =
                    closingPrices.get(peakIndex + 1);

            int pullbackLowIndex =
                    peakIndex + 1;

            for (int i = peakIndex + 2;
                 i < breakoutIndex;
                 i++) {

                if (closingPrices.get(i).compareTo(pullbackLow) < 0) {
                    pullbackLow =
                            closingPrices.get(i);

                    pullbackLowIndex = i;
                }
            }

            BigDecimal poleHeight =
                    polePeak.subtract(startingPrice);

            BigDecimal pullbackDepth =
                    polePeak.subtract(pullbackLow);

            BigDecimal maximumPullbackDepth =
                    poleHeight.multiply(
                            new BigDecimal("0.50")
                    );

            if (pullbackDepth.compareTo(BigDecimal.ZERO) <= 0
                    || pullbackDepth.compareTo(maximumPullbackDepth) > 0) {
                continue;
            }

            boolean pullbackDeclines =
                    closingPrices.get(peakIndex + 1)
                            .compareTo(polePeak) < 0;

            if (!pullbackDeclines) {
                continue;
            }

            boolean recoveryAfterPullback = false;

            for (int i = pullbackLowIndex + 1;
                 i <= breakoutIndex;
                 i++) {

                if (closingPrices.get(i).compareTo(
                        closingPrices.get(i - 1)
                ) > 0) {
                    recoveryAfterPullback = true;
                    break;
                }
            }

            if (!recoveryAfterPullback) {
                continue;
            }

            return true;
        }

        return false;
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
    // DIRECTIONAL INDICATOR VALIDATION
    // =========================

    private void validateDirectionalPrices(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            List<BigDecimal> closingPrices,
            int period,
            String indicatorName) {

        if (highPrices == null
                || lowPrices == null
                || closingPrices == null) {

            throw new IllegalArgumentException(
                    indicatorName + " price lists cannot be null"
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    indicatorName + " period must be greater than zero"
            );
        }

        if (highPrices.size() != lowPrices.size()
                || highPrices.size() != closingPrices.size()) {

            throw new IllegalArgumentException(
                    indicatorName + " price lists must have the same size"
            );
        }

        if (highPrices.size() < period + 1) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate " + indicatorName
            );
        }
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