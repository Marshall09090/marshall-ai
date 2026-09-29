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

        if (highPrices == null
                || lowPrices == null
                || closingPrices == null) {

            throw new IllegalArgumentException(
                    "ADX price lists cannot be null"
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "ADX period must be greater than zero"
            );
        }

        if (highPrices.size() != lowPrices.size()
                || highPrices.size() != closingPrices.size()) {

            throw new IllegalArgumentException(
                    "ADX price lists must have the same size"
            );
        }

        if (highPrices.size() < period + 1) {
            throw new IllegalArgumentException(
                    "Not enough prices to calculate ADX"
            );
        }

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