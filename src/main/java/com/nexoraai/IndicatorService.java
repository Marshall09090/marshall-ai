package com.nexoraai.indicator;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class IndicatorService {

    private static final int SCALE = 8;

    public BigDecimal calculateSma(List<BigDecimal> prices, int period) {
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

    public BigDecimal calculateEma(List<BigDecimal> prices, int period) {
        validatePricesAndPeriod(prices, period, "EMA");

        BigDecimal multiplier = BigDecimal.valueOf(2)
                .divide(
                        BigDecimal.valueOf(period + 1L),
                        SCALE,
                        RoundingMode.HALF_UP
                );

        BigDecimal ema = calculateInitialSma(prices, period);

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

            BigDecimal change =
                    prices.get(i)
                            .subtract(prices.get(i - 1));

            if (change.signum() > 0) {
                gains = gains.add(change);
            } else if (change.signum() < 0) {
                losses = losses.add(change.abs());
            }
        }

        BigDecimal averageGain =
                gains.divide(
                        BigDecimal.valueOf(period),
                        SCALE,
                        RoundingMode.HALF_UP
                );

        BigDecimal averageLoss =
                losses.divide(
                        BigDecimal.valueOf(period),
                        SCALE,
                        RoundingMode.HALF_UP
                );

        for (int i = period + 1;
             i < prices.size();
             i++) {

            BigDecimal change =
                    prices.get(i)
                            .subtract(prices.get(i - 1));

            BigDecimal gain =
                    change.signum() > 0
                            ? change
                            : BigDecimal.ZERO;

            BigDecimal loss =
                    change.signum() < 0
                            ? change.abs()
                            : BigDecimal.ZERO;

            averageGain =
                    averageGain
                            .multiply(
                                    BigDecimal.valueOf(period - 1L)
                            )
                            .add(gain)
                            .divide(
                                    BigDecimal.valueOf(period),
                                    SCALE,
                                    RoundingMode.HALF_UP
                            );

            averageLoss =
                    averageLoss
                            .multiply(
                                    BigDecimal.valueOf(period - 1L)
                            )
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

        BigDecimal relativeStrength =
                averageGain.divide(
                        averageLoss,
                        SCALE,
                        RoundingMode.HALF_UP
                );

        BigDecimal rsi =
                BigDecimal.valueOf(100)
                        .subtract(
                                BigDecimal.valueOf(100)
                                        .divide(
                                                BigDecimal.ONE
                                                        .add(relativeStrength),
                                                SCALE,
                                                RoundingMode.HALF_UP
                                        )
                        );

        return rsi.setScale(
                SCALE,
                RoundingMode.HALF_UP
        );
    }

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
                calculateEma(
                        prices,
                        fastPeriod
                );

        BigDecimal slowEma =
                calculateEma(
                        prices,
                        slowPeriod
                );

        return fastEma
                .subtract(slowEma)
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

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

        int requiredPrices =
                kPeriod + dPeriod - 1;

        if (highPrices.size() < requiredPrices) {

            throw new IllegalArgumentException(
                    "Not enough prices to calculate Stochastic Oscillator percent D"
            );
        }

        List<BigDecimal> percentKValues =
                new ArrayList<>();

        int firstEndIndex =
                highPrices.size() - dPeriod;

        for (int endIndex = firstEndIndex;
             endIndex < highPrices.size();
             endIndex++) {

            int startIndex =
                    endIndex - kPeriod + 1;

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
                || standardDeviationMultiplier.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            throw new IllegalArgumentException(
                    "Standard deviation multiplier must be greater than zero"
            );
        }

        int startIndex =
                prices.size() - period;

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

        for (int i = 1;
             i < highPrices.size();
             i++) {

            BigDecimal high =
                    highPrices.get(i);

            BigDecimal low =
                    lowPrices.get(i);

            BigDecimal previousClose =
                    closingPrices.get(i - 1);

            BigDecimal highLow =
                    high.subtract(low).abs();

            BigDecimal highPreviousClose =
                    high.subtract(
                            previousClose
                    ).abs();

            BigDecimal lowPreviousClose =
                    low.subtract(
                            previousClose
                    ).abs();

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

        BigDecimal sum =
                trueRanges
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

        int startIndex =
                highPrices.size() - period;

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

        List<BigDecimal> dxValues =
                new ArrayList<>();

        for (int endIndex = period;
             endIndex < highPrices.size();
             endIndex++) {

            int startIndex =
                    endIndex - period + 1;

            BigDecimal trueRangeSum =
                    BigDecimal.ZERO;

            BigDecimal positiveDirectionalMovementSum =
                    BigDecimal.ZERO;

            BigDecimal negativeDirectionalMovementSum =
                    BigDecimal.ZERO;

            for (int i = startIndex;
                 i <= endIndex;
                 i++) {

                BigDecimal currentHigh =
                        highPrices.get(i);

                BigDecimal currentLow =
                        lowPrices.get(i);

                BigDecimal previousHigh =
                        highPrices.get(i - 1);

                BigDecimal previousLow =
                        lowPrices.get(i - 1);

                BigDecimal previousClose =
                        closingPrices.get(i - 1);

                BigDecimal highLow =
                        currentHigh
                                .subtract(currentLow)
                                .abs();

                BigDecimal highPreviousClose =
                        currentHigh
                                .subtract(previousClose)
                                .abs();

                BigDecimal lowPreviousClose =
                        currentLow
                                .subtract(previousClose)
                                .abs();

                BigDecimal trueRange =
                        highLow.max(
                                highPreviousClose.max(
                                        lowPreviousClose
                                )
                        );

                trueRangeSum =
                        trueRangeSum.add(
                                trueRange
                        );

                BigDecimal upwardMove =
                        currentHigh.subtract(
                                previousHigh
                        );

                BigDecimal downwardMove =
                        previousLow.subtract(
                                currentLow
                        );

                BigDecimal positiveDirectionalMovement =
                        upwardMove.compareTo(
                                downwardMove
                        ) > 0
                                && upwardMove.compareTo(
                                BigDecimal.ZERO
                        ) > 0
                                ? upwardMove
                                : BigDecimal.ZERO;

                BigDecimal negativeDirectionalMovement =
                        downwardMove.compareTo(
                                upwardMove
                        ) > 0
                                && downwardMove.compareTo(
                                BigDecimal.ZERO
                        ) > 0
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

            if (trueRangeSum.compareTo(
                    BigDecimal.ZERO
            ) == 0) {

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
                            .multiply(
                                    BigDecimal.valueOf(100)
                            )
                            .divide(
                                    trueRangeSum,
                                    SCALE,
                                    RoundingMode.HALF_UP
                            );

            BigDecimal negativeDi =
                    negativeDirectionalMovementSum
                            .multiply(
                                    BigDecimal.valueOf(100)
                            )
                            .divide(
                                    trueRangeSum,
                                    SCALE,
                                    RoundingMode.HALF_UP
                            );

            BigDecimal directionalIndexSum =
                    positiveDi.add(
                            negativeDi
                    );

            BigDecimal dx;

            if (directionalIndexSum.compareTo(
                    BigDecimal.ZERO
            ) == 0) {

                dx = BigDecimal.ZERO.setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );

            } else {

                dx = positiveDi
                        .subtract(negativeDi)
                        .abs()
                        .multiply(
                                BigDecimal.valueOf(100)
                        )
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
                Math.min(
                        period,
                        dxValues.size()
                );

        int startIndex =
                dxValues.size()
                        - valuesToAverage;

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
                BigDecimal.valueOf(
                        valuesToAverage
                ),
                SCALE,
                RoundingMode.HALF_UP
        );
    }

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

        int startIndex =
                highPrices.size() - period;

        BigDecimal trueRangeSum =
                BigDecimal.ZERO;

        BigDecimal positiveDirectionalMovementSum =
                BigDecimal.ZERO;

        for (int i = startIndex;
             i < highPrices.size();
             i++) {

            BigDecimal currentHigh =
                    highPrices.get(i);

            BigDecimal currentLow =
                    lowPrices.get(i);

            BigDecimal previousHigh =
                    highPrices.get(i - 1);

            BigDecimal previousLow =
                    lowPrices.get(i - 1);

            BigDecimal previousClose =
                    closingPrices.get(i - 1);

            BigDecimal highLow =
                    currentHigh
                            .subtract(currentLow)
                            .abs();

            BigDecimal highPreviousClose =
                    currentHigh
                            .subtract(previousClose)
                            .abs();

            BigDecimal lowPreviousClose =
                    currentLow
                            .subtract(previousClose)
                            .abs();

            BigDecimal trueRange =
                    highLow.max(
                            highPreviousClose.max(
                                    lowPreviousClose
                            )
                    );

            trueRangeSum =
                    trueRangeSum.add(
                            trueRange
                    );

            BigDecimal upwardMove =
                    currentHigh.subtract(
                            previousHigh
                    );

            BigDecimal downwardMove =
                    previousLow.subtract(
                            currentLow
                    );

            if (upwardMove.compareTo(
                    downwardMove
            ) > 0
                    && upwardMove.compareTo(
                    BigDecimal.ZERO
            ) > 0) {

                positiveDirectionalMovementSum =
                        positiveDirectionalMovementSum.add(
                                upwardMove
                        );
            }
        }

        if (trueRangeSum.compareTo(
                BigDecimal.ZERO
        ) == 0) {

            return BigDecimal.ZERO.setScale(
                    SCALE,
                    RoundingMode.HALF_UP
            );
        }

        return positiveDirectionalMovementSum
                .multiply(
                        BigDecimal.valueOf(100)
                )
                .divide(
                        trueRangeSum,
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

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

        int startIndex =
                highPrices.size() - period;

        BigDecimal trueRangeSum =
                BigDecimal.ZERO;

        BigDecimal negativeDirectionalMovementSum =
                BigDecimal.ZERO;

        for (int i = startIndex;
             i < highPrices.size();
             i++) {

            BigDecimal currentHigh =
                    highPrices.get(i);

            BigDecimal currentLow =
                    lowPrices.get(i);

            BigDecimal previousHigh =
                    highPrices.get(i - 1);

            BigDecimal previousLow =
                    lowPrices.get(i - 1);

            BigDecimal previousClose =
                    closingPrices.get(i - 1);

            BigDecimal highLow =
                    currentHigh
                            .subtract(currentLow)
                            .abs();

            BigDecimal highPreviousClose =
                    currentHigh
                            .subtract(previousClose)
                            .abs();

            BigDecimal lowPreviousClose =
                    currentLow
                            .subtract(previousClose)
                            .abs();

            BigDecimal trueRange =
                    highLow.max(
                            highPreviousClose.max(
                                    lowPreviousClose
                            )
                    );

            trueRangeSum =
                    trueRangeSum.add(
                            trueRange
                    );

            BigDecimal upwardMove =
                    currentHigh.subtract(
                            previousHigh
                    );

            BigDecimal downwardMove =
                    previousLow.subtract(
                            currentLow
                    );

            if (downwardMove.compareTo(
                    upwardMove
            ) > 0
                    && downwardMove.compareTo(
                    BigDecimal.ZERO
            ) > 0) {

                negativeDirectionalMovementSum =
                        negativeDirectionalMovementSum.add(
                                downwardMove
                        );
            }
        }

        if (trueRangeSum.compareTo(
                BigDecimal.ZERO
        ) == 0) {

            return BigDecimal.ZERO.setScale(
                    SCALE,
                    RoundingMode.HALF_UP
            );
        }

        return negativeDirectionalMovementSum
                .multiply(
                        BigDecimal.valueOf(100)
                )
                .divide(
                        trueRangeSum,
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

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

        int startIndex =
                highPrices.size() - period;

        List<BigDecimal> typicalPrices =
                new ArrayList<>();

        for (int i = startIndex;
             i < highPrices.size();
             i++) {

            BigDecimal typicalPrice =
                    highPrices.get(i)
                            .add(
                                    lowPrices.get(i)
                            )
                            .add(
                                    closingPrices.get(i)
                            )
                            .divide(
                                    BigDecimal.valueOf(3),
                                    SCALE,
                                    RoundingMode.HALF_UP
                            );

            typicalPrices.add(
                    typicalPrice
            );
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

        for (BigDecimal typicalPrice :
                typicalPrices) {

            meanDeviationSum =
                    meanDeviationSum.add(
                            typicalPrice
                                    .subtract(
                                            typicalPriceSma
                                    )
                                    .abs()
                    );
        }

        BigDecimal meanDeviation =
                meanDeviationSum.divide(
                        BigDecimal.valueOf(period),
                        SCALE,
                        RoundingMode.HALF_UP
                );

        if (meanDeviation.compareTo(
                BigDecimal.ZERO
        ) == 0) {

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
                .subtract(
                        typicalPriceSma
                )
                .divide(
                        constant.multiply(
                                meanDeviation
                        ),
                        SCALE,
                        RoundingMode.HALF_UP
                )
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

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

        int startIndex =
                highPrices.size() - period;

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
                highestHigh.subtract(
                        lowestLow
                );

        if (range.compareTo(
                BigDecimal.ZERO
        ) == 0) {

            return BigDecimal.ZERO.setScale(
                    SCALE,
                    RoundingMode.HALF_UP
            );
        }

        return highestHigh
                .subtract(
                        currentClose
                )
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

    public BigDecimal calculateRoc(
            List<BigDecimal> prices,
            int period) {

        if (prices == null
                || prices.isEmpty()) {

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
                prices.get(
                        prices.size() - 1
                );

        BigDecimal previousPrice =
                prices.get(
                        prices.size() - 1 - period
                );

        if (previousPrice.compareTo(
                BigDecimal.ZERO
        ) == 0) {

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
                            .add(
                                    lowPrices.get(i - 1)
                            )
                            .add(
                                    closingPrices.get(i - 1)
                            )
                            .divide(
                                    BigDecimal.valueOf(3),
                                    SCALE,
                                    RoundingMode.HALF_UP
                            );

            BigDecimal currentTypicalPrice =
                    highPrices.get(i)
                            .add(
                                    lowPrices.get(i)
                            )
                            .add(
                                    closingPrices.get(i)
                            )
                            .divide(
                                    BigDecimal.valueOf(3),
                                    SCALE,
                                    RoundingMode.HALF_UP
                            );

            BigDecimal rawMoneyFlow =
                    currentTypicalPrice.multiply(
                            volumes.get(i)
                    );

            if (currentTypicalPrice.compareTo(
                    previousTypicalPrice
            ) > 0) {

                positiveMoneyFlow =
                        positiveMoneyFlow.add(
                                rawMoneyFlow
                        );

            } else if (currentTypicalPrice.compareTo(
                    previousTypicalPrice
            ) < 0) {

                negativeMoneyFlow =
                        negativeMoneyFlow.add(
                                rawMoneyFlow
                        );
            }
        }

        if (negativeMoneyFlow.compareTo(
                BigDecimal.ZERO
        ) == 0) {

            if (positiveMoneyFlow.compareTo(
                    BigDecimal.ZERO
            ) == 0) {

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

        if (positiveMoneyFlow.compareTo(
                BigDecimal.ZERO
        ) == 0) {

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

    public BigDecimal calculateObv(
            List<BigDecimal> closingPrices,
            List<BigDecimal> volumes) {

        if (closingPrices == null
                || volumes == null) {

            throw new IllegalArgumentException(
                    "OBV price and volume lists cannot be null"
            );
        }

        if (closingPrices.isEmpty()) {

            throw new IllegalArgumentException(
                    "OBV prices cannot be empty"
            );
        }

        if (closingPrices.size()
                != volumes.size()) {

            throw new IllegalArgumentException(
                    "OBV price and volume lists must have the same size"
            );
        }

        BigDecimal obv =
                BigDecimal.ZERO;

        for (int i = 1;
             i < closingPrices.size();
             i++) {

            BigDecimal currentClose =
                    closingPrices.get(i);

            BigDecimal previousClose =
                    closingPrices.get(i - 1);

            BigDecimal currentVolume =
                    volumes.get(i);

            if (currentClose.compareTo(
                    previousClose
            ) > 0) {

                obv = obv.add(
                        currentVolume
                );

            } else if (currentClose.compareTo(
                    previousClose
            ) < 0) {

                obv = obv.subtract(
                        currentVolume
                );
            }
        }

        return obv.setScale(
                SCALE,
                RoundingMode.HALF_UP
        );
    }

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

        int startIndex =
                highPrices.size() - period;

        BigDecimal moneyFlowVolumeSum =
                BigDecimal.ZERO;

        BigDecimal volumeSum =
                BigDecimal.ZERO;

        for (int i = startIndex;
             i < highPrices.size();
             i++) {

            BigDecimal high =
                    highPrices.get(i);

            BigDecimal low =
                    lowPrices.get(i);

            BigDecimal close =
                    closingPrices.get(i);

            BigDecimal volume =
                    volumes.get(i);

            BigDecimal range =
                    high.subtract(low);

            BigDecimal moneyFlowMultiplier;

            if (range.compareTo(
                    BigDecimal.ZERO
            ) == 0) {

                moneyFlowMultiplier =
                        BigDecimal.ZERO;

            } else {

                moneyFlowMultiplier =
                        close.subtract(low)
                                .subtract(
                                        high.subtract(close)
                                )
                                .divide(
                                        range,
                                        SCALE,
                                        RoundingMode.HALF_UP
                                );
            }

            BigDecimal moneyFlowVolume =
                    moneyFlowMultiplier.multiply(
                            volume
                    );

            moneyFlowVolumeSum =
                    moneyFlowVolumeSum.add(
                            moneyFlowVolume
                    );

            volumeSum =
                    volumeSum.add(
                            volume
                    );
        }

        if (volumeSum.compareTo(
                BigDecimal.ZERO
        ) == 0) {

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

        BigDecimal accumulationDistributionLine =
                BigDecimal.ZERO;

        for (int i = 0;
             i < highPrices.size();
             i++) {

            BigDecimal high =
                    highPrices.get(i);

            BigDecimal low =
                    lowPrices.get(i);

            BigDecimal close =
                    closingPrices.get(i);

            BigDecimal volume =
                    volumes.get(i);

            BigDecimal range =
                    high.subtract(low);

            BigDecimal moneyFlowMultiplier;

            if (range.compareTo(
                    BigDecimal.ZERO
            ) == 0) {

                moneyFlowMultiplier =
                        BigDecimal.ZERO;

            } else {

                moneyFlowMultiplier =
                        close.subtract(low)
                                .subtract(
                                        high.subtract(close)
                                )
                                .divide(
                                        range,
                                        SCALE,
                                        RoundingMode.HALF_UP
                                );
            }

            BigDecimal moneyFlowVolume =
                    moneyFlowMultiplier.multiply(
                            volume
                    );

            accumulationDistributionLine =
                    accumulationDistributionLine.add(
                            moneyFlowVolume
                    );
        }

        return accumulationDistributionLine
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

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

        for (int i = 0;
             i < highPrices.size();
             i++) {

            BigDecimal high =
                    highPrices.get(i);

            BigDecimal low =
                    lowPrices.get(i);

            BigDecimal close =
                    closingPrices.get(i);

            BigDecimal volume =
                    volumes.get(i);

            BigDecimal typicalPrice =
                    high.add(low)
                            .add(close)
                            .divide(
                                    BigDecimal.valueOf(3),
                                    SCALE,
                                    RoundingMode.HALF_UP
                            );

            BigDecimal typicalPriceVolume =
                    typicalPrice.multiply(
                            volume
                    );

            cumulativeTypicalPriceVolume =
                    cumulativeTypicalPriceVolume.add(
                            typicalPriceVolume
                    );

            cumulativeVolume =
                    cumulativeVolume.add(
                            volume
                    );
        }

        if (cumulativeVolume.compareTo(
                BigDecimal.ZERO
        ) == 0) {

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

    public BigDecimal calculateAverageVolume(
            List<BigDecimal> volumes,
            int period) {

        if (volumes == null
                || volumes.isEmpty()) {

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

        int startIndex =
                volumes.size() - period;

        BigDecimal volumeSum =
                volumes
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

    public BigDecimal calculateParabolicSar(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            BigDecimal initialAccelerationFactor,
            BigDecimal maximumAccelerationFactor) {

        if (highPrices == null
                || lowPrices == null) {

            throw new IllegalArgumentException(
                    "Parabolic SAR price lists cannot be null"
            );
        }

        if (highPrices.size()
                != lowPrices.size()) {

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
                || initialAccelerationFactor.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            throw new IllegalArgumentException(
                    "Parabolic SAR initial acceleration factor must be greater than zero"
            );
        }

        if (maximumAccelerationFactor == null
                || maximumAccelerationFactor.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            throw new IllegalArgumentException(
                    "Parabolic SAR maximum acceleration factor must be greater than zero"
            );
        }

        if (initialAccelerationFactor.compareTo(
                maximumAccelerationFactor
        ) > 0) {

            throw new IllegalArgumentException(
                    "Parabolic SAR initial acceleration factor cannot exceed maximum acceleration factor"
            );
        }

        BigDecimal accelerationStep =
                initialAccelerationFactor;

        boolean upTrend =
                highPrices.get(1)
                        .compareTo(
                                highPrices.get(0)
                        ) >= 0;

        BigDecimal sar;
        BigDecimal extremePoint;

        if (upTrend) {

            sar = lowPrices.get(0);

            extremePoint =
                    highPrices.get(0)
                            .max(
                                    highPrices.get(1)
                            );

        } else {

            sar = highPrices.get(0);

            extremePoint =
                    lowPrices.get(0)
                            .min(
                                    lowPrices.get(1)
                            );
        }

        BigDecimal accelerationFactor =
                initialAccelerationFactor;

        for (int i = 1;
             i < highPrices.size();
             i++) {

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

                if (currentLow.compareTo(
                        nextSar
                ) < 0) {

                    upTrend = false;

                    nextSar =
                            extremePoint;

                    extremePoint =
                            currentLow;

                    accelerationFactor =
                            initialAccelerationFactor;

                } else if (currentHigh.compareTo(
                        extremePoint
                ) > 0) {

                    extremePoint =
                            currentHigh;

                    accelerationFactor =
                            accelerationFactor
                                    .add(
                                            accelerationStep
                                    )
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

                if (currentHigh.compareTo(
                        nextSar
                ) > 0) {

                    upTrend = true;

                    nextSar =
                            extremePoint;

                    extremePoint =
                            currentHigh;

                    accelerationFactor =
                            initialAccelerationFactor;

                } else if (currentLow.compareTo(
                        extremePoint
                ) < 0) {

                    extremePoint =
                            currentLow;

                    accelerationFactor =
                            accelerationFactor
                                    .add(
                                            accelerationStep
                                    )
                                    .min(
                                            maximumAccelerationFactor
                                    );
                }
            }

            sar = nextSar.setScale(
                    SCALE,
                    RoundingMode.HALF_UP
            );
        }

        return sar.setScale(
                SCALE,
                RoundingMode.HALF_UP
        );
    }

    public BigDecimal calculateIchimokuTenkanSen(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            int period) {

        if (highPrices == null
                || lowPrices == null) {

            throw new IllegalArgumentException(
                    "Ichimoku Tenkan-sen price lists cannot be null"
            );
        }

        if (period <= 0) {

            throw new IllegalArgumentException(
                    "Ichimoku Tenkan-sen period must be greater than zero"
            );
        }

        if (highPrices.size()
                != lowPrices.size()) {

            throw new IllegalArgumentException(
                    "Ichimoku Tenkan-sen price lists must have the same size"
            );
        }

        if (highPrices.size() < period) {

            throw new IllegalArgumentException(
                    "Not enough prices to calculate Ichimoku Tenkan-sen"
            );
        }

        int startIndex =
                highPrices.size() - period;

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

    public BigDecimal calculateIchimokuKijunSen(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            int period) {

        if (highPrices == null
                || lowPrices == null) {

            throw new IllegalArgumentException(
                    "Ichimoku Kijun-sen price lists cannot be null"
            );
        }

        if (period <= 0) {

            throw new IllegalArgumentException(
                    "Ichimoku Kijun-sen period must be greater than zero"
            );
        }

        if (highPrices.size()
                != lowPrices.size()) {

            throw new IllegalArgumentException(
                    "Ichimoku Kijun-sen price lists must have the same size"
            );
        }

        if (highPrices.size() < period) {

            throw new IllegalArgumentException(
                    "Not enough prices to calculate Ichimoku Kijun-sen"
            );
        }

        int startIndex =
                highPrices.size() - period;

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

    public BigDecimal calculateIchimokuSenkouSpanA(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            int tenkanPeriod,
            int kijunPeriod) {

        if (highPrices == null
                || lowPrices == null) {

            throw new IllegalArgumentException(
                    "Ichimoku Senkou Span A price lists cannot be null"
            );
        }

        if (tenkanPeriod <= 0
                || kijunPeriod <= 0) {

            throw new IllegalArgumentException(
                    "Ichimoku Senkou Span A periods must be greater than zero"
            );
        }

        if (highPrices.size()
                != lowPrices.size()) {

            throw new IllegalArgumentException(
                    "Ichimoku Senkou Span A price lists must have the same size"
            );
        }

        int requiredPrices =
                Math.max(
                        tenkanPeriod,
                        kijunPeriod
                );

        if (highPrices.size()
                < requiredPrices) {

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

    public BigDecimal calculateIchimokuSenkouSpanB(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            int period) {

        if (highPrices == null
                || lowPrices == null) {

            throw new IllegalArgumentException(
                    "Ichimoku Senkou Span B price lists cannot be null"
            );
        }

        if (period <= 0) {

            throw new IllegalArgumentException(
                    "Ichimoku Senkou Span B period must be greater than zero"
            );
        }

        if (highPrices.size()
                != lowPrices.size()) {

            throw new IllegalArgumentException(
                    "Ichimoku Senkou Span B price lists must have the same size"
            );
        }

        if (highPrices.size() < period) {

            throw new IllegalArgumentException(
                    "Not enough prices to calculate Ichimoku Senkou Span B"
            );
        }

        int startIndex =
                highPrices.size() - period;

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

    public BigDecimal calculateIchimokuChikouSpan(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null
                || closingPrices.isEmpty()) {

            throw new IllegalArgumentException(
                    "Ichimoku Chikou Span closing prices cannot be null or empty"
            );
        }

        return closingPrices
                .get(
                        closingPrices.size() - 1
                )
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    public BigDecimal calculateAroonUp(
            List<BigDecimal> highPrices,
            int period) {

        if (highPrices == null
                || highPrices.isEmpty()) {

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

        int startIndex =
                highPrices.size() - period;

        int highestHighIndex =
                startIndex;

        BigDecimal highestHigh =
                highPrices.get(startIndex);

        for (int i = startIndex + 1;
             i < highPrices.size();
             i++) {

            if (highPrices.get(i)
                    .compareTo(
                            highestHigh
                    ) >= 0) {

                highestHigh =
                        highPrices.get(i);

                highestHighIndex =
                        i;
            }
        }

        int periodsSinceHighestHigh =
                highPrices.size()
                        - 1
                        - highestHighIndex;

        return BigDecimal.valueOf(
                        period
                                - periodsSinceHighestHigh
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

    public BigDecimal calculateAroonDown(
            List<BigDecimal> lowPrices,
            int period) {

        if (lowPrices == null
                || lowPrices.isEmpty()) {

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

        int startIndex =
                lowPrices.size() - period;

        int lowestLowIndex =
                startIndex;

        BigDecimal lowestLow =
                lowPrices.get(startIndex);

        for (int i = startIndex + 1;
             i < lowPrices.size();
             i++) {

            if (lowPrices.get(i)
                    .compareTo(
                            lowestLow
                    ) <= 0) {

                lowestLow =
                        lowPrices.get(i);

                lowestLowIndex =
                        i;
            }
        }

        int periodsSinceLowestLow =
                lowPrices.size()
                        - 1
                        - lowestLowIndex;

        return BigDecimal.valueOf(
                        period
                                - periodsSinceLowestLow
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

    public BigDecimal calculateAroonOscillator(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            int period) {

        if (highPrices == null
                || lowPrices == null) {

            throw new IllegalArgumentException(
                    "Aroon Oscillator price lists cannot be null"
            );
        }

        if (period <= 0) {

            throw new IllegalArgumentException(
                    "Aroon Oscillator period must be greater than zero"
            );
        }

        if (highPrices.size()
                != lowPrices.size()) {

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
                .subtract(
                        aroonDown
                )
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

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

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
                    closingPrices.get(
                            peakIndex
                    );

            boolean localPeak =
                    polePeak.compareTo(
                            closingPrices.get(
                                    peakIndex - 1
                            )
                    ) > 0
                            && polePeak.compareTo(
                            closingPrices.get(
                                    peakIndex + 1
                            )
                    ) > 0;

            if (!localPeak) {
                continue;
            }

            BigDecimal poleGainPercent =
                    polePeak
                            .subtract(
                                    startingPrice
                            )
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

            boolean poleMostlyRising =
                    true;

            for (int i = 1;
                 i <= peakIndex;
                 i++) {

                if (closingPrices.get(i)
                        .compareTo(
                                closingPrices.get(
                                        i - 1
                                )
                        ) < 0) {

                    poleMostlyRising =
                            false;

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

                if (closingPrices.get(i)
                        .compareTo(
                                polePeak
                        ) > 0) {

                    breakoutIndex =
                            i;

                    break;
                }
            }

            if (breakoutIndex == -1) {
                continue;
            }

            BigDecimal pullbackLow =
                    closingPrices.get(
                            peakIndex + 1
                    );

            int pullbackLowIndex =
                    peakIndex + 1;

            for (int i = peakIndex + 2;
                 i < breakoutIndex;
                 i++) {

                if (closingPrices.get(i)
                        .compareTo(
                                pullbackLow
                        ) < 0) {

                    pullbackLow =
                            closingPrices.get(i);

                    pullbackLowIndex =
                            i;
                }
            }

            BigDecimal poleHeight =
                    polePeak.subtract(
                            startingPrice
                    );

            BigDecimal pullbackDepth =
                    polePeak.subtract(
                            pullbackLow
                    );

            BigDecimal maximumPullbackDepth =
                    poleHeight.multiply(
                            new BigDecimal("0.50")
                    );

            if (pullbackDepth.compareTo(
                    BigDecimal.ZERO
            ) <= 0
                    || pullbackDepth.compareTo(
                    maximumPullbackDepth
            ) > 0) {

                continue;
            }

            boolean pullbackDeclines =
                    closingPrices.get(
                            peakIndex + 1
                    ).compareTo(
                            polePeak
                    ) < 0;

            if (!pullbackDeclines) {
                continue;
            }

            boolean recoveryAfterPullback =
                    false;

            for (int i = pullbackLowIndex + 1;
                 i <= breakoutIndex;
                 i++) {

                if (closingPrices.get(i)
                        .compareTo(
                                closingPrices.get(
                                        i - 1
                                )
                        ) > 0) {

                    recoveryAfterPullback =
                            true;

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
    // BEAR FLAG PATTERN
    // =========================

    public boolean detectBearFlag(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Bear Flag closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 7) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Bear Flag closing prices cannot contain null values"
                );
            }

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Bear Flag closing prices must be greater than zero"
                );
            }
        }

        BigDecimal startingPrice =
                closingPrices.get(0);

        for (int troughIndex = 2;
             troughIndex <= closingPrices.size() - 4;
             troughIndex++) {

            BigDecimal poleTrough =
                    closingPrices.get(
                            troughIndex
                    );

            boolean localTrough =
                    poleTrough.compareTo(
                            closingPrices.get(
                                    troughIndex - 1
                            )
                    ) < 0
                            && poleTrough.compareTo(
                            closingPrices.get(
                                    troughIndex + 1
                            )
                    ) < 0;

            if (!localTrough) {
                continue;
            }

            BigDecimal poleDeclinePercent =
                    startingPrice
                            .subtract(
                                    poleTrough
                            )
                            .divide(
                                    startingPrice,
                                    SCALE,
                                    RoundingMode.HALF_UP
                            )
                            .multiply(
                                    BigDecimal.valueOf(100)
                            );

            if (poleDeclinePercent.compareTo(
                    BigDecimal.valueOf(5)
            ) < 0) {

                continue;
            }

            boolean poleMostlyFalling =
                    true;

            for (int i = 1;
                 i <= troughIndex;
                 i++) {

                if (closingPrices.get(i)
                        .compareTo(
                                closingPrices.get(
                                        i - 1
                                )
                        ) > 0) {

                    poleMostlyFalling =
                            false;

                    break;
                }
            }

            if (!poleMostlyFalling) {
                continue;
            }

            int breakdownIndex = -1;

            for (int i = troughIndex + 3;
                 i < closingPrices.size();
                 i++) {

                if (closingPrices.get(i)
                        .compareTo(
                                poleTrough
                        ) < 0) {

                    breakdownIndex =
                            i;

                    break;
                }
            }

            if (breakdownIndex == -1) {
                continue;
            }

            BigDecimal reboundHigh =
                    closingPrices.get(
                            troughIndex + 1
                    );

            int reboundHighIndex =
                    troughIndex + 1;

            for (int i = troughIndex + 2;
                 i < breakdownIndex;
                 i++) {

                if (closingPrices.get(i)
                        .compareTo(
                                reboundHigh
                        ) > 0) {

                    reboundHigh =
                            closingPrices.get(i);

                    reboundHighIndex =
                            i;
                }
            }

            BigDecimal poleHeight =
                    startingPrice.subtract(
                            poleTrough
                    );

            BigDecimal reboundDepth =
                    reboundHigh.subtract(
                            poleTrough
                    );

            BigDecimal maximumReboundDepth =
                    poleHeight.multiply(
                            new BigDecimal("0.50")
                    );

            if (reboundDepth.compareTo(
                    BigDecimal.ZERO
            ) <= 0
                    || reboundDepth.compareTo(
                    maximumReboundDepth
            ) > 0) {

                continue;
            }

            boolean reboundRises =
                    closingPrices.get(
                            troughIndex + 1
                    ).compareTo(
                            poleTrough
                    ) > 0;

            if (!reboundRises) {
                continue;
            }

            boolean declineAfterRebound =
                    false;

            for (int i = reboundHighIndex + 1;
                 i <= breakdownIndex;
                 i++) {

                if (closingPrices.get(i)
                        .compareTo(
                                closingPrices.get(
                                        i - 1
                                )
                        ) < 0) {

                    declineAfterRebound =
                            true;

                    break;
                }
            }

            if (!declineAfterRebound) {
                continue;
            }

            return true;
        }

        return false;
    }

    // =========================
    // BULLISH BREAKOUT
    // =========================

    public boolean detectBullishBreakout(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Bullish Breakout closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 4) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Bullish Breakout closing prices cannot contain null values"
                );
            }

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Bullish Breakout closing prices must be greater than zero"
                );
            }
        }

        BigDecimal currentPrice =
                closingPrices.get(
                        closingPrices.size() - 1
                );

        BigDecimal previousResistance =
                closingPrices.get(0);

        for (int i = 1;
             i < closingPrices.size() - 1;
             i++) {

            previousResistance =
                    previousResistance.max(
                            closingPrices.get(i)
                    );
        }

        if (currentPrice.compareTo(
                previousResistance
        ) <= 0) {

            return false;
        }

        BigDecimal breakoutPercent =
                currentPrice
                        .subtract(
                                previousResistance
                        )
                        .divide(
                                previousResistance,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        return breakoutPercent.compareTo(
                BigDecimal.ONE
        ) >= 0;
    }

    // =========================
    // BEARISH BREAKDOWN
    // =========================

    public boolean detectBearishBreakdown(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Bearish Breakdown closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 4) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Bearish Breakdown closing prices cannot contain null values"
                );
            }

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Bearish Breakdown closing prices must be greater than zero"
                );
            }
        }

        BigDecimal currentPrice =
                closingPrices.get(
                        closingPrices.size() - 1
                );

        BigDecimal previousSupport =
                closingPrices.get(0);

        for (int i = 1;
             i < closingPrices.size() - 1;
             i++) {

            previousSupport =
                    previousSupport.min(
                            closingPrices.get(i)
                    );
        }

        if (currentPrice.compareTo(
                previousSupport
        ) >= 0) {

            return false;
        }

        BigDecimal breakdownPercent =
                previousSupport
                        .subtract(
                                currentPrice
                        )
                        .divide(
                                previousSupport,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        return breakdownPercent.compareTo(
                BigDecimal.ONE
        ) >= 0;
    }

    // =========================
    // DOUBLE TOP PATTERN
    // =========================

    public boolean detectDoubleTop(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Double Top closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 7) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Double Top closing prices cannot contain null values"
                );
            }

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Double Top closing prices must be greater than zero"
                );
            }
        }

        for (int firstPeakIndex = 1;
             firstPeakIndex < closingPrices.size() - 4;
             firstPeakIndex++) {

            BigDecimal firstPeak =
                    closingPrices.get(
                            firstPeakIndex
                    );

            boolean firstLocalPeak =
                    firstPeak.compareTo(
                            closingPrices.get(
                                    firstPeakIndex - 1
                            )
                    ) > 0
                            && firstPeak.compareTo(
                            closingPrices.get(
                                    firstPeakIndex + 1
                            )
                    ) > 0;

            if (!firstLocalPeak) {
                continue;
            }

            for (int secondPeakIndex =
                 firstPeakIndex + 2;
                 secondPeakIndex < closingPrices.size() - 1;
                 secondPeakIndex++) {

                BigDecimal secondPeak =
                        closingPrices.get(
                                secondPeakIndex
                        );

                boolean secondLocalPeak =
                        secondPeak.compareTo(
                                closingPrices.get(
                                        secondPeakIndex - 1
                                )
                        ) > 0
                                && secondPeak.compareTo(
                                closingPrices.get(
                                        secondPeakIndex + 1
                                )
                        ) > 0;

                if (!secondLocalPeak) {
                    continue;
                }

                BigDecimal largerPeak =
                        firstPeak.max(
                                secondPeak
                        );

                BigDecimal peakDifferencePercent =
                        firstPeak
                                .subtract(
                                        secondPeak
                                )
                                .abs()
                                .divide(
                                        largerPeak,
                                        SCALE,
                                        RoundingMode.HALF_UP
                                )
                                .multiply(
                                        BigDecimal.valueOf(100)
                                );

                if (peakDifferencePercent.compareTo(
                        new BigDecimal("1.50")
                ) > 0) {

                    continue;
                }

                BigDecimal valley =
                        closingPrices.get(
                                firstPeakIndex + 1
                        );

                for (int i = firstPeakIndex + 2;
                     i < secondPeakIndex;
                     i++) {

                    valley =
                            valley.min(
                                    closingPrices.get(i)
                            );
                }

                BigDecimal averagePeak =
                        firstPeak
                                .add(
                                        secondPeak
                                )
                                .divide(
                                        BigDecimal.valueOf(2),
                                        SCALE,
                                        RoundingMode.HALF_UP
                                );

                BigDecimal valleyDepthPercent =
                        averagePeak
                                .subtract(
                                        valley
                                )
                                .divide(
                                        averagePeak,
                                        SCALE,
                                        RoundingMode.HALF_UP
                                )
                                .multiply(
                                        BigDecimal.valueOf(100)
                                );

                if (valleyDepthPercent.compareTo(
                        BigDecimal.valueOf(2)
                ) < 0) {

                    continue;
                }

                boolean necklineBreak =
                        false;

                for (int i = secondPeakIndex + 1;
                     i < closingPrices.size();
                     i++) {

                    if (closingPrices.get(i)
                            .compareTo(
                                    valley
                            ) < 0) {

                        necklineBreak =
                                true;

                        break;
                    }
                }

                if (necklineBreak) {
                    return true;
                }
            }
        }

        return false;
    }

    // =========================
    // DOUBLE BOTTOM PATTERN
    // =========================

    public boolean detectDoubleBottom(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Double Bottom closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 7) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Double Bottom closing prices cannot contain null values"
                );
            }

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Double Bottom closing prices must be greater than zero"
                );
            }
        }

        for (int firstBottomIndex = 1;
             firstBottomIndex < closingPrices.size() - 4;
             firstBottomIndex++) {

            BigDecimal firstBottom =
                    closingPrices.get(
                            firstBottomIndex
                    );

            boolean firstLocalBottom =
                    firstBottom.compareTo(
                            closingPrices.get(
                                    firstBottomIndex - 1
                            )
                    ) < 0
                            && firstBottom.compareTo(
                            closingPrices.get(
                                    firstBottomIndex + 1
                            )
                    ) < 0;

            if (!firstLocalBottom) {
                continue;
            }

            for (int secondBottomIndex =
                 firstBottomIndex + 2;
                 secondBottomIndex < closingPrices.size() - 1;
                 secondBottomIndex++) {

                BigDecimal secondBottom =
                        closingPrices.get(
                                secondBottomIndex
                        );

                boolean secondLocalBottom =
                        secondBottom.compareTo(
                                closingPrices.get(
                                        secondBottomIndex - 1
                                )
                        ) < 0
                                && secondBottom.compareTo(
                                closingPrices.get(
                                        secondBottomIndex + 1
                                )
                        ) < 0;

                if (!secondLocalBottom) {
                    continue;
                }

                BigDecimal smallerBottom =
                        firstBottom.min(
                                secondBottom
                        );

                BigDecimal bottomDifferencePercent =
                        firstBottom
                                .subtract(
                                        secondBottom
                                )
                                .abs()
                                .divide(
                                        smallerBottom,
                                        SCALE,
                                        RoundingMode.HALF_UP
                                )
                                .multiply(
                                        BigDecimal.valueOf(100)
                                );

                if (bottomDifferencePercent.compareTo(
                        new BigDecimal("1.50")
                ) > 0) {

                    continue;
                }

                BigDecimal neckline =
                        closingPrices.get(
                                firstBottomIndex + 1
                        );

                for (int i = firstBottomIndex + 2;
                     i < secondBottomIndex;
                     i++) {

                    neckline =
                            neckline.max(
                                    closingPrices.get(i)
                            );
                }

                BigDecimal averageBottom =
                        firstBottom
                                .add(
                                        secondBottom
                                )
                                .divide(
                                        BigDecimal.valueOf(2),
                                        SCALE,
                                        RoundingMode.HALF_UP
                                );

                BigDecimal reboundPercent =
                        neckline
                                .subtract(
                                        averageBottom
                                )
                                .divide(
                                        averageBottom,
                                        SCALE,
                                        RoundingMode.HALF_UP
                                )
                                .multiply(
                                        BigDecimal.valueOf(100)
                                );

                if (reboundPercent.compareTo(
                        BigDecimal.valueOf(2)
                ) < 0) {

                    continue;
                }

                boolean necklineBreak =
                        false;

                for (int i = secondBottomIndex + 1;
                     i < closingPrices.size();
                     i++) {

                    if (closingPrices.get(i)
                            .compareTo(
                                    neckline
                            ) > 0) {

                        necklineBreak =
                                true;

                        break;
                    }
                }

                if (necklineBreak) {
                    return true;
                }
            }
        }

        return false;
    }

    // =========================
    // HEAD AND SHOULDERS PATTERN
    // =========================

    public boolean detectHeadAndShoulders(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Head and Shoulders closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 7) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Head and Shoulders closing prices cannot contain null values"
                );
            }

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Head and Shoulders closing prices must be greater than zero"
                );
            }
        }

        for (int leftShoulderIndex = 1;
             leftShoulderIndex <= closingPrices.size() - 6;
             leftShoulderIndex++) {

            BigDecimal leftShoulder =
                    closingPrices.get(
                            leftShoulderIndex
                    );

            boolean leftShoulderIsPeak =
                    leftShoulder.compareTo(
                            closingPrices.get(
                                    leftShoulderIndex - 1
                            )
                    ) > 0
                            && leftShoulder.compareTo(
                            closingPrices.get(
                                    leftShoulderIndex + 1
                            )
                    ) > 0;

            if (!leftShoulderIsPeak) {
                continue;
            }

            for (int headIndex =
                 leftShoulderIndex + 2;
                 headIndex <= closingPrices.size() - 4;
                 headIndex++) {

                BigDecimal head =
                        closingPrices.get(
                                headIndex
                        );

                boolean headIsPeak =
                        head.compareTo(
                                closingPrices.get(
                                        headIndex - 1
                                )
                        ) > 0
                                && head.compareTo(
                                closingPrices.get(
                                        headIndex + 1
                                )
                        ) > 0;

                if (!headIsPeak) {
                    continue;
                }

                if (head.compareTo(
                        leftShoulder
                ) <= 0) {

                    continue;
                }

                BigDecimal leftNeckline =
                        closingPrices.get(
                                leftShoulderIndex + 1
                        );

                for (int i = leftShoulderIndex + 2;
                     i < headIndex;
                     i++) {

                    leftNeckline =
                            leftNeckline.min(
                                    closingPrices.get(i)
                            );
                }

                for (int rightShoulderIndex =
                     headIndex + 2;
                     rightShoulderIndex < closingPrices.size() - 1;
                     rightShoulderIndex++) {

                    BigDecimal rightShoulder =
                            closingPrices.get(
                                    rightShoulderIndex
                            );

                    boolean rightShoulderIsPeak =
                            rightShoulder.compareTo(
                                    closingPrices.get(
                                            rightShoulderIndex - 1
                                    )
                            ) > 0
                                    && rightShoulder.compareTo(
                                    closingPrices.get(
                                            rightShoulderIndex + 1
                                    )
                            ) > 0;

                    if (!rightShoulderIsPeak) {
                        continue;
                    }

                    if (head.compareTo(
                            rightShoulder
                    ) <= 0) {

                        continue;
                    }

                    BigDecimal largerShoulder =
                            leftShoulder.max(
                                    rightShoulder
                            );

                    BigDecimal shoulderDifferencePercent =
                            leftShoulder
                                    .subtract(
                                            rightShoulder
                                    )
                                    .abs()
                                    .divide(
                                            largerShoulder,
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(100)
                                    );

                    if (shoulderDifferencePercent.compareTo(
                            BigDecimal.valueOf(3)
                    ) > 0) {

                        continue;
                    }

                    BigDecimal headProminencePercent =
                            head
                                    .subtract(
                                            largerShoulder
                                    )
                                    .divide(
                                            largerShoulder,
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(100)
                                    );

                    if (headProminencePercent.compareTo(
                            BigDecimal.valueOf(2)
                    ) < 0) {

                        continue;
                    }

                    BigDecimal rightNeckline =
                            closingPrices.get(
                                    headIndex + 1
                            );

                    for (int i = headIndex + 2;
                         i < rightShoulderIndex;
                         i++) {

                        rightNeckline =
                                rightNeckline.min(
                                        closingPrices.get(i)
                                );
                    }

                    BigDecimal largerNeckline =
                            leftNeckline.max(
                                    rightNeckline
                            );

                    BigDecimal necklineDifferencePercent =
                            leftNeckline
                                    .subtract(
                                            rightNeckline
                                    )
                                    .abs()
                                    .divide(
                                            largerNeckline,
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(100)
                                    );

                    if (necklineDifferencePercent.compareTo(
                            BigDecimal.valueOf(3)
                    ) > 0) {

                        continue;
                    }

                    BigDecimal neckline =
                            leftNeckline
                                    .add(
                                            rightNeckline
                                    )
                                    .divide(
                                            BigDecimal.valueOf(2),
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    );

                    boolean necklineBreak =
                            false;

                    for (int i = rightShoulderIndex + 1;
                         i < closingPrices.size();
                         i++) {

                        if (closingPrices.get(i)
                                .compareTo(
                                        neckline
                                ) < 0) {

                            necklineBreak =
                                    true;

                            break;
                        }
                    }

                    if (necklineBreak) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // =========================
    // INVERSE HEAD AND SHOULDERS
    // =========================

    public boolean detectInverseHeadAndShoulders(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Inverse Head and Shoulders closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 7) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Inverse Head and Shoulders closing prices cannot contain null values"
                );
            }

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Inverse Head and Shoulders closing prices must be greater than zero"
                );
            }
        }

        for (int leftShoulderIndex = 1;
             leftShoulderIndex <= closingPrices.size() - 6;
             leftShoulderIndex++) {

            BigDecimal leftShoulder =
                    closingPrices.get(
                            leftShoulderIndex
                    );

            boolean leftShoulderIsTrough =
                    leftShoulder.compareTo(
                            closingPrices.get(
                                    leftShoulderIndex - 1
                            )
                    ) < 0
                            && leftShoulder.compareTo(
                            closingPrices.get(
                                    leftShoulderIndex + 1
                            )
                    ) < 0;

            if (!leftShoulderIsTrough) {
                continue;
            }

            for (int headIndex =
                 leftShoulderIndex + 2;
                 headIndex <= closingPrices.size() - 4;
                 headIndex++) {

                BigDecimal head =
                        closingPrices.get(
                                headIndex
                        );

                boolean headIsTrough =
                        head.compareTo(
                                closingPrices.get(
                                        headIndex - 1
                                )
                        ) < 0
                                && head.compareTo(
                                closingPrices.get(
                                        headIndex + 1
                                )
                        ) < 0;

                if (!headIsTrough) {
                    continue;
                }

                if (head.compareTo(
                        leftShoulder
                ) >= 0) {

                    continue;
                }

                BigDecimal leftNeckline =
                        closingPrices.get(
                                leftShoulderIndex + 1
                        );

                for (int i = leftShoulderIndex + 2;
                     i < headIndex;
                     i++) {

                    leftNeckline =
                            leftNeckline.max(
                                    closingPrices.get(i)
                            );
                }

                for (int rightShoulderIndex =
                     headIndex + 2;
                     rightShoulderIndex < closingPrices.size() - 1;
                     rightShoulderIndex++) {

                    BigDecimal rightShoulder =
                            closingPrices.get(
                                    rightShoulderIndex
                            );

                    boolean rightShoulderIsTrough =
                            rightShoulder.compareTo(
                                    closingPrices.get(
                                            rightShoulderIndex - 1
                                    )
                            ) < 0
                                    && rightShoulder.compareTo(
                                    closingPrices.get(
                                            rightShoulderIndex + 1
                                    )
                            ) < 0;

                    if (!rightShoulderIsTrough) {
                        continue;
                    }

                    if (head.compareTo(
                            rightShoulder
                    ) >= 0) {

                        continue;
                    }

                    BigDecimal largerShoulder =
                            leftShoulder.max(
                                    rightShoulder
                            );

                    BigDecimal shoulderDifferencePercent =
                            leftShoulder
                                    .subtract(
                                            rightShoulder
                                    )
                                    .abs()
                                    .divide(
                                            largerShoulder,
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(100)
                                    );

                    if (shoulderDifferencePercent.compareTo(
                            BigDecimal.valueOf(3)
                    ) > 0) {

                        continue;
                    }

                    BigDecimal smallerShoulder =
                            leftShoulder.min(
                                    rightShoulder
                            );

                    BigDecimal headProminencePercent =
                            smallerShoulder
                                    .subtract(
                                            head
                                    )
                                    .divide(
                                            smallerShoulder,
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(100)
                                    );

                    if (headProminencePercent.compareTo(
                            BigDecimal.valueOf(2)
                    ) < 0) {

                        continue;
                    }

                    BigDecimal rightNeckline =
                            closingPrices.get(
                                    headIndex + 1
                            );

                    for (int i = headIndex + 2;
                         i < rightShoulderIndex;
                         i++) {

                        rightNeckline =
                                rightNeckline.max(
                                        closingPrices.get(i)
                                );
                    }

                    BigDecimal largerNeckline =
                            leftNeckline.max(
                                    rightNeckline
                            );

                    BigDecimal necklineDifferencePercent =
                            leftNeckline
                                    .subtract(
                                            rightNeckline
                                    )
                                    .abs()
                                    .divide(
                                            largerNeckline,
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(100)
                                    );

                    if (necklineDifferencePercent.compareTo(
                            BigDecimal.valueOf(3)
                    ) > 0) {

                        continue;
                    }

                    BigDecimal neckline =
                            leftNeckline
                                    .add(
                                            rightNeckline
                                    )
                                    .divide(
                                            BigDecimal.valueOf(2),
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    );

                    boolean necklineBreak =
                            false;

                    for (int i = rightShoulderIndex + 1;
                         i < closingPrices.size();
                         i++) {

                        if (closingPrices.get(i)
                                .compareTo(
                                        neckline
                                ) > 0) {

                            necklineBreak =
                                    true;

                            break;
                        }
                    }

                    if (necklineBreak) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // =========================
    // ASCENDING TRIANGLE PATTERN
    // =========================

    public boolean detectAscendingTriangle(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Ascending Triangle closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 7) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Ascending Triangle closing prices cannot contain null values"
                );
            }

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Ascending Triangle closing prices must be greater than zero"
                );
            }
        }

        int breakoutIndex =
                closingPrices.size() - 1;

        List<BigDecimal> resistancePeaks =
                new ArrayList<>();

        List<BigDecimal> risingLows =
                new ArrayList<>();

        for (int i = 1;
             i < breakoutIndex;
             i++) {

            BigDecimal currentPrice =
                    closingPrices.get(i);

            BigDecimal previousPrice =
                    closingPrices.get(i - 1);

            BigDecimal nextPrice =
                    closingPrices.get(i + 1);

            boolean localPeak =
                    currentPrice.compareTo(
                            previousPrice
                    ) > 0
                            && currentPrice.compareTo(
                            nextPrice
                    ) > 0;

            if (localPeak) {
                resistancePeaks.add(
                        currentPrice
                );
            }

            boolean localTrough =
                    currentPrice.compareTo(
                            previousPrice
                    ) < 0
                            && currentPrice.compareTo(
                            nextPrice
                    ) < 0;

            if (localTrough) {
                risingLows.add(
                        currentPrice
                );
            }
        }

        if (resistancePeaks.size() < 2
                || risingLows.size() < 2) {

            return false;
        }

        BigDecimal highestResistance =
                resistancePeaks.get(0);

        BigDecimal lowestResistance =
                resistancePeaks.get(0);

        for (BigDecimal peak :
                resistancePeaks) {

            highestResistance =
                    highestResistance.max(
                            peak
                    );

            lowestResistance =
                    lowestResistance.min(
                            peak
                    );
        }

        BigDecimal resistanceVariationPercent =
                highestResistance
                        .subtract(
                                lowestResistance
                        )
                        .divide(
                                highestResistance,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (resistanceVariationPercent.compareTo(
                BigDecimal.ONE
        ) > 0) {

            return false;
        }

        for (int i = 1;
             i < risingLows.size();
             i++) {

            if (risingLows.get(i)
                    .compareTo(
                            risingLows.get(
                                    i - 1
                            )
                    ) <= 0) {

                return false;
            }
        }

        BigDecimal breakoutPrice =
                closingPrices.get(
                        breakoutIndex
                );

        if (breakoutPrice.compareTo(
                highestResistance
        ) <= 0) {

            return false;
        }

        BigDecimal breakoutPercent =
                breakoutPrice
                        .subtract(
                                highestResistance
                        )
                        .divide(
                                highestResistance,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        return breakoutPercent.compareTo(
                BigDecimal.ONE
        ) >= 0;
    }

    // =========================
    // DESCENDING TRIANGLE PATTERN
    // =========================

    public boolean detectDescendingTriangle(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Descending Triangle closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 7) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Descending Triangle closing prices cannot contain null values"
                );
            }

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Descending Triangle closing prices must be greater than zero"
                );
            }
        }

        int breakdownIndex =
                closingPrices.size() - 1;

        List<BigDecimal> supportTroughs =
                new ArrayList<>();

        List<BigDecimal> fallingHighs =
                new ArrayList<>();

        for (int i = 1;
             i < breakdownIndex;
             i++) {

            BigDecimal currentPrice =
                    closingPrices.get(i);

            BigDecimal previousPrice =
                    closingPrices.get(i - 1);

            BigDecimal nextPrice =
                    closingPrices.get(i + 1);

            boolean localTrough =
                    currentPrice.compareTo(
                            previousPrice
                    ) < 0
                            && currentPrice.compareTo(
                            nextPrice
                    ) < 0;

            if (localTrough) {
                supportTroughs.add(
                        currentPrice
                );
            }

            boolean localPeak =
                    currentPrice.compareTo(
                            previousPrice
                    ) > 0
                            && currentPrice.compareTo(
                            nextPrice
                    ) > 0;

            if (localPeak) {
                fallingHighs.add(
                        currentPrice
                );
            }
        }

        if (supportTroughs.size() < 2
                || fallingHighs.size() < 2) {

            return false;
        }

        BigDecimal highestSupport =
                supportTroughs.get(0);

        BigDecimal lowestSupport =
                supportTroughs.get(0);

        for (BigDecimal trough :
                supportTroughs) {

            highestSupport =
                    highestSupport.max(
                            trough
                    );

            lowestSupport =
                    lowestSupport.min(
                            trough
                    );
        }

        BigDecimal supportVariationPercent =
                highestSupport
                        .subtract(
                                lowestSupport
                        )
                        .divide(
                                highestSupport,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (supportVariationPercent.compareTo(
                BigDecimal.ONE
        ) > 0) {

            return false;
        }

        for (int i = 1;
             i < fallingHighs.size();
             i++) {

            if (fallingHighs.get(i)
                    .compareTo(
                            fallingHighs.get(
                                    i - 1
                            )
                    ) >= 0) {

                return false;
            }
        }

        BigDecimal breakdownPrice =
                closingPrices.get(
                        breakdownIndex
                );

        if (breakdownPrice.compareTo(
                lowestSupport
        ) >= 0) {

            return false;
        }

        BigDecimal breakdownPercent =
                lowestSupport
                        .subtract(
                                breakdownPrice
                        )
                        .divide(
                                lowestSupport,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        return breakdownPercent.compareTo(
                BigDecimal.ONE
        ) >= 0;
    }

    // =========================
    // SYMMETRICAL TRIANGLE PATTERN
    // =========================

    public boolean detectSymmetricalTriangle(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Symmetrical Triangle closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 7) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Symmetrical Triangle closing prices cannot contain null values"
                );
            }

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Symmetrical Triangle closing prices must be greater than zero"
                );
            }
        }

        int breakoutIndex =
                closingPrices.size() - 1;

        List<BigDecimal> fallingHighs =
                new ArrayList<>();

        List<BigDecimal> risingLows =
                new ArrayList<>();

        for (int i = 1;
             i < breakoutIndex;
             i++) {

            BigDecimal currentPrice =
                    closingPrices.get(i);

            BigDecimal previousPrice =
                    closingPrices.get(i - 1);

            BigDecimal nextPrice =
                    closingPrices.get(i + 1);

            boolean localPeak =
                    currentPrice.compareTo(
                            previousPrice
                    ) > 0
                            && currentPrice.compareTo(
                            nextPrice
                    ) > 0;

            if (localPeak) {
                fallingHighs.add(
                        currentPrice
                );
            }

            boolean localTrough =
                    currentPrice.compareTo(
                            previousPrice
                    ) < 0
                            && currentPrice.compareTo(
                            nextPrice
                    ) < 0;

            if (localTrough) {
                risingLows.add(
                        currentPrice
                );
            }
        }

        if (fallingHighs.size() < 2
                || risingLows.size() < 2) {

            return false;
        }

        for (int i = 1;
             i < fallingHighs.size();
             i++) {

            if (fallingHighs.get(i)
                    .compareTo(
                            fallingHighs.get(
                                    i - 1
                            )
                    ) >= 0) {

                return false;
            }
        }

        for (int i = 1;
             i < risingLows.size();
             i++) {

            if (risingLows.get(i)
                    .compareTo(
                            risingLows.get(
                                    i - 1
                            )
                    ) <= 0) {

                return false;
            }
        }

        BigDecimal firstRange =
                fallingHighs.get(0)
                        .subtract(
                                risingLows.get(0)
                        );

        BigDecimal lastRange =
                fallingHighs
                        .get(
                                fallingHighs.size() - 1
                        )
                        .subtract(
                                risingLows.get(
                                        risingLows.size() - 1
                                )
                        );

        if (firstRange.compareTo(
                BigDecimal.ZERO
        ) <= 0
                || lastRange.compareTo(
                BigDecimal.ZERO
        ) <= 0
                || lastRange.compareTo(
                firstRange
        ) >= 0) {

            return false;
        }

        BigDecimal lastResistance =
                fallingHighs.get(
                        fallingHighs.size() - 1
                );

        BigDecimal lastSupport =
                risingLows.get(
                        risingLows.size() - 1
                );

        BigDecimal breakoutPrice =
                closingPrices.get(
                        breakoutIndex
                );

        if (breakoutPrice.compareTo(
                lastResistance
        ) > 0) {

            BigDecimal breakoutPercent =
                    breakoutPrice
                            .subtract(
                                    lastResistance
                            )
                            .divide(
                                    lastResistance,
                                    SCALE,
                                    RoundingMode.HALF_UP
                            )
                            .multiply(
                                    BigDecimal.valueOf(100)
                            );

            return breakoutPercent.compareTo(
                    BigDecimal.ONE
            ) >= 0;
        }

        if (breakoutPrice.compareTo(
                lastSupport
        ) < 0) {

            BigDecimal breakdownPercent =
                    lastSupport
                            .subtract(
                                    breakoutPrice
                            )
                            .divide(
                                    lastSupport,
                                    SCALE,
                                    RoundingMode.HALF_UP
                            )
                            .multiply(
                                    BigDecimal.valueOf(100)
                            );

            return breakdownPercent.compareTo(
                    BigDecimal.ONE
            ) >= 0;
        }

        return false;
    }

    // =========================
    // RISING WEDGE PATTERN
    // =========================

    public boolean detectRisingWedge(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Rising Wedge closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 7) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Rising Wedge closing prices cannot contain null values"
                );
            }

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Rising Wedge closing prices must be greater than zero"
                );
            }
        }

        int breakdownIndex =
                closingPrices.size() - 1;

        List<BigDecimal> fallingHighs =
                new ArrayList<>();

        List<BigDecimal> risingLows =
                new ArrayList<>();

        for (int i = 1;
             i < breakdownIndex;
             i++) {

            BigDecimal currentPrice =
                    closingPrices.get(i);

            BigDecimal previousPrice =
                    closingPrices.get(i - 1);

            BigDecimal nextPrice =
                    closingPrices.get(i + 1);

            boolean localPeak =
                    currentPrice.compareTo(
                            previousPrice
                    ) > 0
                            && currentPrice.compareTo(
                            nextPrice
                    ) > 0;

            if (localPeak) {
                fallingHighs.add(
                        currentPrice
                );
            }

            boolean localTrough =
                    currentPrice.compareTo(
                            previousPrice
                    ) < 0
                            && currentPrice.compareTo(
                            nextPrice
                    ) < 0;

            if (localTrough) {
                risingLows.add(
                        currentPrice
                );
            }
        }

        if (fallingHighs.size() < 2
                || risingLows.size() < 2) {

            return false;
        }

        for (int i = 1;
             i < fallingHighs.size();
             i++) {

            if (fallingHighs.get(i)
                    .compareTo(
                            fallingHighs.get(
                                    i - 1
                            )
                    ) >= 0) {

                return false;
            }
        }

        for (int i = 1;
             i < risingLows.size();
             i++) {

            if (risingLows.get(i)
                    .compareTo(
                            risingLows.get(
                                    i - 1
                            )
                    ) <= 0) {

                return false;
            }
        }

        int rangeCount =
                Math.min(
                        fallingHighs.size(),
                        risingLows.size()
                );

        BigDecimal firstRange =
                fallingHighs.get(0)
                        .subtract(
                                risingLows.get(0)
                        );

        BigDecimal lastRange =
                fallingHighs.get(
                        rangeCount - 1
                ).subtract(
                        risingLows.get(
                                rangeCount - 1
                        )
                );

        if (firstRange.compareTo(
                BigDecimal.ZERO
        ) <= 0
                || lastRange.compareTo(
                BigDecimal.ZERO
        ) <= 0
                || lastRange.compareTo(
                firstRange
        ) >= 0) {

            return false;
        }

        BigDecimal lastSupport =
                risingLows.get(
                        risingLows.size() - 1
                );

        BigDecimal breakdownPrice =
                closingPrices.get(
                        breakdownIndex
                );

        if (breakdownPrice.compareTo(
                lastSupport
        ) >= 0) {

            return false;
        }

        BigDecimal breakdownPercent =
                lastSupport
                        .subtract(
                                breakdownPrice
                        )
                        .divide(
                                lastSupport,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        return breakdownPercent.compareTo(
                BigDecimal.ONE
        ) >= 0;
    }

    // =========================
    // FALLING WEDGE PATTERN
    // =========================

    public boolean detectFallingWedge(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Falling Wedge closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 7) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Falling Wedge closing prices cannot contain null values"
                );
            }

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Falling Wedge closing prices must be greater than zero"
                );
            }
        }

        int breakoutIndex =
                closingPrices.size() - 1;

        List<BigDecimal> fallingHighs =
                new ArrayList<>();

        List<BigDecimal> fallingLows =
                new ArrayList<>();

        for (int i = 1;
             i < breakoutIndex;
             i++) {

            BigDecimal currentPrice =
                    closingPrices.get(i);

            BigDecimal previousPrice =
                    closingPrices.get(i - 1);

            BigDecimal nextPrice =
                    closingPrices.get(i + 1);

            boolean localPeak =
                    currentPrice.compareTo(
                            previousPrice
                    ) > 0
                            && currentPrice.compareTo(
                            nextPrice
                    ) > 0;

            if (localPeak) {
                fallingHighs.add(
                        currentPrice
                );
            }

            boolean localTrough =
                    currentPrice.compareTo(
                            previousPrice
                    ) < 0
                            && currentPrice.compareTo(
                            nextPrice
                    ) < 0;

            if (localTrough) {
                fallingLows.add(
                        currentPrice
                );
            }
        }

        if (fallingHighs.size() < 2
                || fallingLows.size() < 2) {

            return false;
        }

        for (int i = 1;
             i < fallingHighs.size();
             i++) {

            if (fallingHighs.get(i)
                    .compareTo(
                            fallingHighs.get(
                                    i - 1
                            )
                    ) >= 0) {

                return false;
            }
        }

        for (int i = 1;
             i < fallingLows.size();
             i++) {

            if (fallingLows.get(i)
                    .compareTo(
                            fallingLows.get(
                                    i - 1
                            )
                    ) >= 0) {

                return false;
            }
        }

        int rangeCount =
                Math.min(
                        fallingHighs.size(),
                        fallingLows.size()
                );

        BigDecimal firstRange =
                fallingHighs.get(0)
                        .subtract(
                                fallingLows.get(0)
                        );

        BigDecimal lastRange =
                fallingHighs.get(
                        rangeCount - 1
                ).subtract(
                        fallingLows.get(
                                rangeCount - 1
                        )
                );

        if (firstRange.compareTo(
                BigDecimal.ZERO
        ) <= 0
                || lastRange.compareTo(
                BigDecimal.ZERO
        ) <= 0
                || lastRange.compareTo(
                firstRange
        ) >= 0) {

            return false;
        }

        BigDecimal lastResistance =
                fallingHighs.get(
                        fallingHighs.size() - 1
                );

        BigDecimal breakoutPrice =
                closingPrices.get(
                        breakoutIndex
                );

        if (breakoutPrice.compareTo(
                lastResistance
        ) <= 0) {

            return false;
        }

        BigDecimal breakoutPercent =
                breakoutPrice
                        .subtract(
                                lastResistance
                        )
                        .divide(
                                lastResistance,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        return breakoutPercent.compareTo(
                new BigDecimal("0.50")
        ) >= 0;
    }

    // =========================
    // BULLISH PENNANT PATTERN
    // =========================

    public boolean detectBullishPennant(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Bullish Pennant closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 9) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Bullish Pennant closing prices cannot contain null values"
                );
            }

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Bullish Pennant closing prices must be greater than zero"
                );
            }
        }

        int breakoutIndex =
                closingPrices.size() - 1;

        int polePeakIndex = 0;

        BigDecimal polePeak =
                closingPrices.get(0);

        for (int i = 1;
             i < breakoutIndex;
             i++) {

            if (closingPrices.get(i)
                    .compareTo(
                            polePeak
                    ) > 0) {

                polePeak =
                        closingPrices.get(i);

                polePeakIndex =
                        i;
            }
        }

        if (polePeakIndex < 2
                || breakoutIndex - polePeakIndex < 5) {

            return false;
        }

        BigDecimal startingPrice =
                closingPrices.get(0);

        BigDecimal poleGainPercent =
                polePeak
                        .subtract(
                                startingPrice
                        )
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

            return false;
        }

        for (int i = 1;
             i <= polePeakIndex;
             i++) {

            if (closingPrices.get(i)
                    .compareTo(
                            closingPrices.get(
                                    i - 1
                            )
                    ) <= 0) {

                return false;
            }
        }

        int consolidationStart =
                polePeakIndex + 1;

        int consolidationEnd =
                breakoutIndex - 1;

        int consolidationSize =
                consolidationEnd
                        - consolidationStart
                        + 1;

        if (consolidationSize < 4) {
            return false;
        }

        BigDecimal consolidationHigh =
                closingPrices.get(
                        consolidationStart
                );

        BigDecimal consolidationLow =
                closingPrices.get(
                        consolidationStart
                );

        for (int i = consolidationStart;
             i <= consolidationEnd;
             i++) {

            BigDecimal price =
                    closingPrices.get(i);

            consolidationHigh =
                    consolidationHigh.max(
                            price
                    );

            consolidationLow =
                    consolidationLow.min(
                            price
                    );

            if (price.compareTo(
                    polePeak
            ) >= 0) {

                return false;
            }
        }

        BigDecimal poleHeight =
                polePeak.subtract(
                        startingPrice
                );

        BigDecimal consolidationRange =
                consolidationHigh.subtract(
                        consolidationLow
                );

        if (consolidationRange.compareTo(
                BigDecimal.ZERO
        ) <= 0
                || consolidationRange.compareTo(
                poleHeight.multiply(
                        new BigDecimal("0.50")
                )
        ) > 0) {

            return false;
        }

        int midpoint =
                consolidationStart
                        + consolidationSize / 2;

        BigDecimal firstHalfHigh =
                closingPrices.get(
                        consolidationStart
                );

        BigDecimal firstHalfLow =
                closingPrices.get(
                        consolidationStart
                );

        for (int i = consolidationStart;
             i < midpoint;
             i++) {

            firstHalfHigh =
                    firstHalfHigh.max(
                            closingPrices.get(i)
                    );

            firstHalfLow =
                    firstHalfLow.min(
                            closingPrices.get(i)
                    );
        }

        BigDecimal secondHalfHigh =
                closingPrices.get(
                        midpoint
                );

        BigDecimal secondHalfLow =
                closingPrices.get(
                        midpoint
                );

        for (int i = midpoint;
             i <= consolidationEnd;
             i++) {

            secondHalfHigh =
                    secondHalfHigh.max(
                            closingPrices.get(i)
                    );

            secondHalfLow =
                    secondHalfLow.min(
                            closingPrices.get(i)
                    );
        }

        BigDecimal firstHalfRange =
                firstHalfHigh.subtract(
                        firstHalfLow
                );

        BigDecimal secondHalfRange =
                secondHalfHigh.subtract(
                        secondHalfLow
                );

        if (firstHalfRange.compareTo(
                BigDecimal.ZERO
        ) <= 0
                || secondHalfRange.compareTo(
                BigDecimal.ZERO
        ) <= 0
                || secondHalfRange.compareTo(
                firstHalfRange
        ) >= 0) {

            return false;
        }

        BigDecimal breakoutPrice =
                closingPrices.get(
                        breakoutIndex
                );

        if (breakoutPrice.compareTo(
                polePeak
        ) <= 0) {

            return false;
        }

        BigDecimal breakoutPercent =
                breakoutPrice
                        .subtract(
                                polePeak
                        )
                        .divide(
                                polePeak,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        return breakoutPercent.compareTo(
                BigDecimal.ONE
        ) >= 0;
    }

    // =========================
    // BEARISH PENNANT PATTERN
    // =========================

    public boolean detectBearishPennant(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Bearish Pennant closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 9) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Bearish Pennant closing prices cannot contain null values"
                );
            }

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Bearish Pennant closing prices must be greater than zero"
                );
            }
        }

        int breakdownIndex =
                closingPrices.size() - 1;

        int poleTroughIndex = 0;

        BigDecimal poleTrough =
                closingPrices.get(0);

        for (int i = 1;
             i < breakdownIndex;
             i++) {

            if (closingPrices.get(i)
                    .compareTo(
                            poleTrough
                    ) < 0) {

                poleTrough =
                        closingPrices.get(i);

                poleTroughIndex =
                        i;
            }
        }

        if (poleTroughIndex < 2
                || breakdownIndex - poleTroughIndex < 5) {

            return false;
        }

        BigDecimal startingPrice =
                closingPrices.get(0);

        BigDecimal poleLossPercent =
                startingPrice
                        .subtract(
                                poleTrough
                        )
                        .divide(
                                startingPrice,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (poleLossPercent.compareTo(
                BigDecimal.valueOf(5)
        ) < 0) {

            return false;
        }

        for (int i = 1;
             i <= poleTroughIndex;
             i++) {

            if (closingPrices.get(i)
                    .compareTo(
                            closingPrices.get(
                                    i - 1
                            )
                    ) >= 0) {

                return false;
            }
        }

        int consolidationStart =
                poleTroughIndex + 1;

        int consolidationEnd =
                breakdownIndex - 1;

        int consolidationSize =
                consolidationEnd
                        - consolidationStart
                        + 1;

        if (consolidationSize < 4) {
            return false;
        }

        BigDecimal consolidationHigh =
                closingPrices.get(
                        consolidationStart
                );

        BigDecimal consolidationLow =
                closingPrices.get(
                        consolidationStart
                );

        for (int i = consolidationStart;
             i <= consolidationEnd;
             i++) {

            BigDecimal price =
                    closingPrices.get(i);

            consolidationHigh =
                    consolidationHigh.max(
                            price
                    );

            consolidationLow =
                    consolidationLow.min(
                            price
                    );

            if (price.compareTo(
                    poleTrough
            ) <= 0) {

                return false;
            }
        }

        BigDecimal poleHeight =
                startingPrice.subtract(
                        poleTrough
                );

        BigDecimal consolidationRange =
                consolidationHigh.subtract(
                        consolidationLow
                );

        if (consolidationRange.compareTo(
                BigDecimal.ZERO
        ) <= 0
                || consolidationRange.compareTo(
                poleHeight.multiply(
                        new BigDecimal("0.50")
                )
        ) > 0) {

            return false;
        }

        int midpoint =
                consolidationStart
                        + consolidationSize / 2;

        BigDecimal firstHalfHigh =
                closingPrices.get(
                        consolidationStart
                );

        BigDecimal firstHalfLow =
                closingPrices.get(
                        consolidationStart
                );

        for (int i = consolidationStart;
             i < midpoint;
             i++) {

            firstHalfHigh =
                    firstHalfHigh.max(
                            closingPrices.get(i)
                    );

            firstHalfLow =
                    firstHalfLow.min(
                            closingPrices.get(i)
                    );
        }

        BigDecimal secondHalfHigh =
                closingPrices.get(
                        midpoint
                );

        BigDecimal secondHalfLow =
                closingPrices.get(
                        midpoint
                );

        for (int i = midpoint;
             i <= consolidationEnd;
             i++) {

            secondHalfHigh =
                    secondHalfHigh.max(
                            closingPrices.get(i)
                    );

            secondHalfLow =
                    secondHalfLow.min(
                            closingPrices.get(i)
                    );
        }

        BigDecimal firstHalfRange =
                firstHalfHigh.subtract(
                        firstHalfLow
                );

        BigDecimal secondHalfRange =
                secondHalfHigh.subtract(
                        secondHalfLow
                );

        if (firstHalfRange.compareTo(
                BigDecimal.ZERO
        ) <= 0
                || secondHalfRange.compareTo(
                BigDecimal.ZERO
        ) <= 0
                || secondHalfRange.compareTo(
                firstHalfRange
        ) >= 0) {

            return false;
        }

        BigDecimal breakdownPrice =
                closingPrices.get(
                        breakdownIndex
                );

        if (breakdownPrice.compareTo(
                poleTrough
        ) >= 0) {

            return false;
        }

        BigDecimal breakdownPercent =
                poleTrough
                        .subtract(
                                breakdownPrice
                        )
                        .divide(
                                poleTrough,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        return breakdownPercent.compareTo(
                BigDecimal.ONE
        ) >= 0;
    }

    // =========================
    // CUP AND HANDLE PATTERN
    // =========================

    public boolean detectCupAndHandle(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Cup and Handle closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 10) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Cup and Handle closing prices cannot contain null values"
                );
            }

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Cup and Handle closing prices must be greater than zero"
                );
            }
        }

        int breakoutIndex =
                closingPrices.size() - 1;

        int cupBottomIndex = 1;

        BigDecimal cupBottom =
                closingPrices.get(
                        cupBottomIndex
                );

        for (int i = 2;
             i <= breakoutIndex - 3;
             i++) {

            if (closingPrices.get(i)
                    .compareTo(
                            cupBottom
                    ) < 0) {

                cupBottom =
                        closingPrices.get(i);

                cupBottomIndex =
                        i;
            }
        }

        if (cupBottomIndex < 2
                || cupBottomIndex > breakoutIndex - 4) {

            return false;
        }

        int leftRimIndex = 0;

        BigDecimal leftRim =
                closingPrices.get(
                        leftRimIndex
                );

        for (int i = 1;
             i < cupBottomIndex;
             i++) {

            if (closingPrices.get(i)
                    .compareTo(
                            leftRim
                    ) > 0) {

                leftRim =
                        closingPrices.get(i);

                leftRimIndex =
                        i;
            }
        }

        int rightRimIndex =
                cupBottomIndex + 1;

        BigDecimal rightRim =
                closingPrices.get(
                        rightRimIndex
                );

        for (int i = cupBottomIndex + 2;
             i <= breakoutIndex - 3;
             i++) {

            if (closingPrices.get(i)
                    .compareTo(
                            rightRim
                    ) > 0) {

                rightRim =
                        closingPrices.get(i);

                rightRimIndex =
                        i;
            }
        }

        if (leftRimIndex >= cupBottomIndex
                || rightRimIndex <= cupBottomIndex) {

            return false;
        }

        for (int i = leftRimIndex + 1;
             i <= cupBottomIndex;
             i++) {

            if (closingPrices.get(i)
                    .compareTo(
                            closingPrices.get(
                                    i - 1
                            )
                    ) >= 0) {

                return false;
            }
        }

        for (int i = cupBottomIndex + 1;
             i <= rightRimIndex;
             i++) {

            if (closingPrices.get(i)
                    .compareTo(
                            closingPrices.get(
                                    i - 1
                            )
                    ) <= 0) {

                return false;
            }
        }

        BigDecimal largerRim =
                leftRim.max(
                        rightRim
                );

        BigDecimal rimDifferencePercent =
                leftRim
                        .subtract(
                                rightRim
                        )
                        .abs()
                        .divide(
                                largerRim,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (rimDifferencePercent.compareTo(
                BigDecimal.valueOf(3)
        ) > 0) {

            return false;
        }

        BigDecimal averageRim =
                leftRim
                        .add(
                                rightRim
                        )
                        .divide(
                                BigDecimal.valueOf(2),
                                SCALE,
                                RoundingMode.HALF_UP
                        );

        BigDecimal cupDepth =
                averageRim.subtract(
                        cupBottom
                );

        BigDecimal cupDepthPercent =
                cupDepth
                        .divide(
                                averageRim,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (cupDepthPercent.compareTo(
                BigDecimal.valueOf(5)
        ) < 0) {

            return false;
        }

        int handleStart =
                rightRimIndex + 1;

        int handleEnd =
                breakoutIndex - 1;

        if (handleEnd - handleStart + 1 < 2) {
            return false;
        }

        BigDecimal handleLow =
                closingPrices.get(
                        handleStart
                );

        BigDecimal handleHigh =
                closingPrices.get(
                        handleStart
                );

        for (int i = handleStart;
             i <= handleEnd;
             i++) {

            BigDecimal price =
                    closingPrices.get(i);

            handleLow =
                    handleLow.min(
                            price
                    );

            handleHigh =
                    handleHigh.max(
                            price
                    );

            if (price.compareTo(
                    rightRim
            ) >= 0) {

                return false;
            }
        }

        BigDecimal handleDepth =
                rightRim.subtract(
                        handleLow
                );

        BigDecimal maximumHandleDepth =
                cupDepth.multiply(
                        new BigDecimal("0.50")
                );

        if (handleDepth.compareTo(
                maximumHandleDepth
        ) > 0) {

            return false;
        }

        if (closingPrices.get(
                handleEnd
        ).compareTo(
                handleLow
        ) <= 0) {

            return false;
        }

        BigDecimal breakoutPrice =
                closingPrices.get(
                        breakoutIndex
                );

        BigDecimal breakoutLevel =
                leftRim.max(
                        rightRim
                );

        if (breakoutPrice.compareTo(
                breakoutLevel
        ) <= 0) {

            return false;
        }

        BigDecimal breakoutPercent =
                breakoutPrice
                        .subtract(
                                breakoutLevel
                        )
                        .divide(
                                breakoutLevel,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        return breakoutPercent.compareTo(
                BigDecimal.ONE
        ) >= 0;
    }

    // =========================
    // INVERSE CUP AND HANDLE PATTERN
    // =========================

    public boolean detectInverseCupAndHandle(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Inverse Cup and Handle closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 10) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Inverse Cup and Handle closing prices cannot contain null values"
                );
            }

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Inverse Cup and Handle closing prices must be greater than zero"
                );
            }
        }

        int breakdownIndex =
                closingPrices.size() - 1;

        int cupTopIndex = 1;

        BigDecimal cupTop =
                closingPrices.get(
                        cupTopIndex
                );

        for (int i = 2;
             i <= breakdownIndex - 3;
             i++) {

            if (closingPrices.get(i)
                    .compareTo(
                            cupTop
                    ) > 0) {

                cupTop =
                        closingPrices.get(i);

                cupTopIndex =
                        i;
            }
        }

        if (cupTopIndex < 2
                || cupTopIndex > breakdownIndex - 4) {

            return false;
        }

        int leftRimIndex = 0;

        BigDecimal leftRim =
                closingPrices.get(
                        leftRimIndex
                );

        for (int i = 1;
             i < cupTopIndex;
             i++) {

            if (closingPrices.get(i)
                    .compareTo(
                            leftRim
                    ) < 0) {

                leftRim =
                        closingPrices.get(i);

                leftRimIndex =
                        i;
            }
        }

        int rightRimIndex =
                cupTopIndex + 1;

        BigDecimal rightRim =
                closingPrices.get(
                        rightRimIndex
                );

        for (int i = cupTopIndex + 2;
             i <= breakdownIndex - 3;
             i++) {

            if (closingPrices.get(i)
                    .compareTo(
                            rightRim
                    ) < 0) {

                rightRim =
                        closingPrices.get(i);

                rightRimIndex =
                        i;
            }
        }

        if (leftRimIndex >= cupTopIndex
                || rightRimIndex <= cupTopIndex) {

            return false;
        }

        for (int i = leftRimIndex + 1;
             i <= cupTopIndex;
             i++) {

            if (closingPrices.get(i)
                    .compareTo(
                            closingPrices.get(
                                    i - 1
                            )
                    ) <= 0) {

                return false;
            }
        }

        for (int i = cupTopIndex + 1;
             i <= rightRimIndex;
             i++) {

            if (closingPrices.get(i)
                    .compareTo(
                            closingPrices.get(
                                    i - 1
                            )
                    ) >= 0) {

                return false;
            }
        }

        BigDecimal largerRim =
                leftRim.max(
                        rightRim
                );

        BigDecimal rimDifferencePercent =
                leftRim
                        .subtract(
                                rightRim
                        )
                        .abs()
                        .divide(
                                largerRim,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (rimDifferencePercent.compareTo(
                BigDecimal.valueOf(3)
        ) > 0) {

            return false;
        }

        BigDecimal averageRim =
                leftRim
                        .add(
                                rightRim
                        )
                        .divide(
                                BigDecimal.valueOf(2),
                                SCALE,
                                RoundingMode.HALF_UP
                        );

        BigDecimal cupHeight =
                cupTop.subtract(
                        averageRim
                );

        BigDecimal cupHeightPercent =
                cupHeight
                        .divide(
                                averageRim,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (cupHeightPercent.compareTo(
                BigDecimal.valueOf(5)
        ) < 0) {

            return false;
        }

        int handleStart =
                rightRimIndex + 1;

        int handleEnd =
                breakdownIndex - 1;

        if (handleEnd - handleStart + 1 < 2) {
            return false;
        }

        BigDecimal handleHigh =
                closingPrices.get(
                        handleStart
                );

        BigDecimal handleLow =
                closingPrices.get(
                        handleStart
                );

        for (int i = handleStart;
             i <= handleEnd;
             i++) {

            BigDecimal price =
                    closingPrices.get(i);

            handleHigh =
                    handleHigh.max(
                            price
                    );

            handleLow =
                    handleLow.min(
                            price
                    );

            if (price.compareTo(
                    rightRim
            ) <= 0) {

                return false;
            }
        }

        BigDecimal handleRise =
                handleHigh.subtract(
                        rightRim
                );

        BigDecimal maximumHandleRise =
                cupHeight.multiply(
                        new BigDecimal("0.50")
                );

        if (handleRise.compareTo(
                maximumHandleRise
        ) > 0) {

            return false;
        }

        if (closingPrices.get(
                handleEnd
        ).compareTo(
                handleHigh
        ) >= 0) {

            return false;
        }

        BigDecimal breakdownPrice =
                closingPrices.get(
                        breakdownIndex
                );

        BigDecimal breakdownLevel =
                leftRim.min(
                        rightRim
                );

        if (breakdownPrice.compareTo(
                breakdownLevel
        ) >= 0) {

            return false;
        }

        BigDecimal breakdownPercent =
                breakdownLevel
                        .subtract(
                                breakdownPrice
                        )
                        .divide(
                                breakdownLevel,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        return breakdownPercent.compareTo(
                BigDecimal.ONE
        ) >= 0;
    }

    // =========================
    // ROUNDING BOTTOM PATTERN
    // =========================

    public boolean detectRoundingBottom(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Rounding Bottom closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 9) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Rounding Bottom closing prices cannot contain null values"
                );
            }

            if (price.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Rounding Bottom closing prices must be greater than zero"
                );
            }
        }

        int breakoutIndex =
                closingPrices.size() - 1;

        int bottomIndex = 1;

        BigDecimal bottomPrice =
                closingPrices.get(
                        bottomIndex
                );

        for (int i = 2;
             i < breakoutIndex;
             i++) {

            if (closingPrices.get(i)
                    .compareTo(
                            bottomPrice
                    ) < 0) {

                bottomPrice =
                        closingPrices.get(i);

                bottomIndex =
                        i;
            }
        }

        if (bottomIndex < 3
                || breakoutIndex - bottomIndex < 4) {

            return false;
        }

        BigDecimal leftRim =
                closingPrices.get(0);

        BigDecimal rightRim =
                closingPrices.get(
                        breakoutIndex - 1
                );

        for (int i = 1;
             i <= bottomIndex;
             i++) {

            if (closingPrices.get(i)
                    .compareTo(
                            closingPrices.get(
                                    i - 1
                            )
                    ) >= 0) {

                return false;
            }
        }

        for (int i = bottomIndex + 1;
             i < breakoutIndex;
             i++) {

            if (closingPrices.get(i)
                    .compareTo(
                            closingPrices.get(
                                    i - 1
                            )
                    ) <= 0) {

                return false;
            }
        }

        BigDecimal largerRim =
                leftRim.max(
                        rightRim
                );

        BigDecimal rimDifferencePercent =
                leftRim
                        .subtract(
                                rightRim
                        )
                        .abs()
                        .divide(
                                largerRim,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (rimDifferencePercent.compareTo(
                BigDecimal.valueOf(5)
        ) > 0) {

            return false;
        }

        BigDecimal averageRim =
                leftRim
                        .add(
                                rightRim
                        )
                        .divide(
                                BigDecimal.valueOf(2),
                                SCALE,
                                RoundingMode.HALF_UP
                        );

        BigDecimal patternDepth =
                averageRim.subtract(
                        bottomPrice
                );

        BigDecimal patternDepthPercent =
                patternDepth
                        .divide(
                                averageRim,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (patternDepthPercent.compareTo(
                BigDecimal.valueOf(5)
        ) < 0) {

            return false;
        }

        boolean slowingIntoBottom =
                true;

        for (int i = 2;
             i <= bottomIndex;
             i++) {

            BigDecimal previousDecline =
                    closingPrices.get(i - 2)
                            .subtract(
                                    closingPrices.get(
                                            i - 1
                                    )
                            )
                            .abs();

            BigDecimal currentDecline =
                    closingPrices.get(i - 1)
                            .subtract(
                                    closingPrices.get(i)
                            )
                            .abs();

            if (currentDecline.compareTo(
                    previousDecline
            ) > 0) {

                slowingIntoBottom =
                        false;

                break;
            }
        }

        if (!slowingIntoBottom) {
            return false;
        }

        boolean acceleratingOutOfBottom =
                true;

        for (int i = bottomIndex + 2;
             i < breakoutIndex;
             i++) {

            BigDecimal previousRise =
                    closingPrices.get(i - 1)
                            .subtract(
                                    closingPrices.get(
                                            i - 2
                                    )
                            )
                            .abs();

            BigDecimal currentRise =
                    closingPrices.get(i)
                            .subtract(
                                    closingPrices.get(
                                            i - 1
                                    )
                            )
                            .abs();

            if (currentRise.compareTo(
                    previousRise
            ) < 0) {

                acceleratingOutOfBottom =
                        false;

                break;
            }
        }

        if (!acceleratingOutOfBottom) {
            return false;
        }

        BigDecimal breakoutPrice =
                closingPrices.get(
                        breakoutIndex
                );

        BigDecimal resistanceLevel =
                leftRim.max(
                        rightRim
                );

        if (breakoutPrice.compareTo(
                resistanceLevel
        ) <= 0) {

            return false;
        }

        BigDecimal breakoutPercent =
                breakoutPrice
                        .subtract(
                                resistanceLevel
                        )
                        .divide(
                                resistanceLevel,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        return breakoutPercent.compareTo(
                new BigDecimal("0.50")
        ) >= 0;
    }

    // =========================
    // ROUNDING TOP PATTERN
    // =========================

    public boolean detectRoundingTop(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Rounding Top closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 9) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Rounding Top closing prices cannot contain null values"
                );
            }

            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Rounding Top closing prices must be greater than zero"
                );
            }
        }

        int breakdownIndex =
                closingPrices.size() - 1;

        int topIndex = 1;

        BigDecimal topPrice =
                closingPrices.get(topIndex);

        for (int i = 2;
             i < breakdownIndex;
             i++) {

            if (closingPrices.get(i).compareTo(topPrice) > 0) {

                topPrice =
                        closingPrices.get(i);

                topIndex = i;
            }
        }

        if (topIndex < 3
                || breakdownIndex - topIndex < 4) {

            return false;
        }

        BigDecimal leftSupport =
                closingPrices.get(0);

        BigDecimal rightSupport =
                closingPrices.get(
                        breakdownIndex - 1
                );

        for (int i = 1;
             i <= topIndex;
             i++) {

            if (closingPrices.get(i).compareTo(
                    closingPrices.get(i - 1)
            ) <= 0) {

                return false;
            }
        }

        for (int i = topIndex + 1;
             i < breakdownIndex;
             i++) {

            if (closingPrices.get(i).compareTo(
                    closingPrices.get(i - 1)
            ) >= 0) {

                return false;
            }
        }

        BigDecimal largerSupport =
                leftSupport.max(rightSupport);

        BigDecimal supportDifferencePercent =
                leftSupport
                        .subtract(rightSupport)
                        .abs()
                        .divide(
                                largerSupport,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (supportDifferencePercent.compareTo(
                BigDecimal.valueOf(5)
        ) > 0) {

            return false;
        }

        BigDecimal averageSupport =
                leftSupport
                        .add(rightSupport)
                        .divide(
                                BigDecimal.valueOf(2),
                                SCALE,
                                RoundingMode.HALF_UP
                        );

        BigDecimal patternHeight =
                topPrice.subtract(averageSupport);

        BigDecimal patternHeightPercent =
                patternHeight
                        .divide(
                                averageSupport,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (patternHeightPercent.compareTo(
                BigDecimal.valueOf(5)
        ) < 0) {

            return false;
        }

        boolean slowingIntoTop = true;

        for (int i = 2;
             i <= topIndex;
             i++) {

            BigDecimal previousRise =
                    closingPrices.get(i - 1)
                            .subtract(
                                    closingPrices.get(i - 2)
                            )
                            .abs();

            BigDecimal currentRise =
                    closingPrices.get(i)
                            .subtract(
                                    closingPrices.get(i - 1)
                            )
                            .abs();

            if (currentRise.compareTo(previousRise) > 0) {

                slowingIntoTop = false;
                break;
            }
        }

        if (!slowingIntoTop) {
            return false;
        }

        boolean acceleratingOutOfTop = true;

        for (int i = topIndex + 2;
             i < breakdownIndex;
             i++) {

            BigDecimal previousDecline =
                    closingPrices.get(i - 2)
                            .subtract(
                                    closingPrices.get(i - 1)
                            )
                            .abs();

            BigDecimal currentDecline =
                    closingPrices.get(i - 1)
                            .subtract(
                                    closingPrices.get(i)
                            )
                            .abs();

            if (currentDecline.compareTo(previousDecline) < 0) {

                acceleratingOutOfTop = false;
                break;
            }
        }

        if (!acceleratingOutOfTop) {
            return false;
        }

        BigDecimal breakdownPrice =
                closingPrices.get(breakdownIndex);

        BigDecimal supportLevel =
                leftSupport.min(rightSupport);

        if (breakdownPrice.compareTo(supportLevel) >= 0) {
            return false;
        }

        BigDecimal breakdownPercent =
                supportLevel
                        .subtract(breakdownPrice)
                        .divide(
                                supportLevel,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        return breakdownPercent.compareTo(
                new BigDecimal("0.50")
        ) >= 0;
    }

    // =========================
    // TRIPLE TOP PATTERN
    // =========================

    public boolean detectTripleTop(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Triple Top closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 8) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Triple Top closing prices cannot contain null values"
                );
            }

            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Triple Top closing prices must be greater than zero"
                );
            }
        }

        int breakdownIndex =
                closingPrices.size() - 1;

        List<Integer> peakIndexes =
                new ArrayList<>();

        for (int i = 1;
             i < breakdownIndex;
             i++) {

            BigDecimal current =
                    closingPrices.get(i);

            BigDecimal previous =
                    closingPrices.get(i - 1);

            BigDecimal next =
                    closingPrices.get(i + 1);

            if (current.compareTo(previous) > 0
                    && current.compareTo(next) > 0) {

                peakIndexes.add(i);
            }
        }

        if (peakIndexes.size() < 3) {
            return false;
        }

        for (int first = 0;
             first < peakIndexes.size() - 2;
             first++) {

            for (int second = first + 1;
                 second < peakIndexes.size() - 1;
                 second++) {

                for (int third = second + 1;
                     third < peakIndexes.size();
                     third++) {

                    int firstPeakIndex =
                            peakIndexes.get(first);

                    int secondPeakIndex =
                            peakIndexes.get(second);

                    int thirdPeakIndex =
                            peakIndexes.get(third);

                    BigDecimal firstPeak =
                            closingPrices.get(firstPeakIndex);

                    BigDecimal secondPeak =
                            closingPrices.get(secondPeakIndex);

                    BigDecimal thirdPeak =
                            closingPrices.get(thirdPeakIndex);

                    BigDecimal highestPeak =
                            firstPeak
                                    .max(secondPeak)
                                    .max(thirdPeak);

                    BigDecimal lowestPeak =
                            firstPeak
                                    .min(secondPeak)
                                    .min(thirdPeak);

                    BigDecimal peakVariationPercent =
                            highestPeak
                                    .subtract(lowestPeak)
                                    .divide(
                                            highestPeak,
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(100)
                                    );

                    if (peakVariationPercent.compareTo(
                            BigDecimal.valueOf(2)
                    ) > 0) {

                        continue;
                    }

                    BigDecimal firstValley =
                            closingPrices.get(
                                    firstPeakIndex + 1
                            );

                    for (int i = firstPeakIndex + 1;
                         i < secondPeakIndex;
                         i++) {

                        firstValley =
                                firstValley.min(
                                        closingPrices.get(i)
                                );
                    }

                    BigDecimal secondValley =
                            closingPrices.get(
                                    secondPeakIndex + 1
                            );

                    for (int i = secondPeakIndex + 1;
                         i < thirdPeakIndex;
                         i++) {

                        secondValley =
                                secondValley.min(
                                        closingPrices.get(i)
                                );
                    }

                    BigDecimal averagePeak =
                            firstPeak
                                    .add(secondPeak)
                                    .add(thirdPeak)
                                    .divide(
                                            BigDecimal.valueOf(3),
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    );

                    BigDecimal firstValleyDepthPercent =
                            averagePeak
                                    .subtract(firstValley)
                                    .divide(
                                            averagePeak,
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(100)
                                    );

                    BigDecimal secondValleyDepthPercent =
                            averagePeak
                                    .subtract(secondValley)
                                    .divide(
                                            averagePeak,
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(100)
                                    );

                    if (firstValleyDepthPercent.compareTo(
                            BigDecimal.valueOf(2)
                    ) < 0
                            || secondValleyDepthPercent.compareTo(
                            BigDecimal.valueOf(2)
                    ) < 0) {

                        continue;
                    }

                    BigDecimal largerValley =
                            firstValley.max(secondValley);

                    BigDecimal valleyVariationPercent =
                            firstValley
                                    .subtract(secondValley)
                                    .abs()
                                    .divide(
                                            largerValley,
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(100)
                                    );

                    if (valleyVariationPercent.compareTo(
                            BigDecimal.valueOf(3)
                    ) > 0) {

                        continue;
                    }

                    BigDecimal neckline =
                            firstValley.min(secondValley);

                    BigDecimal breakdownPrice =
                            closingPrices.get(
                                    breakdownIndex
                            );

                    if (breakdownPrice.compareTo(
                            neckline
                    ) >= 0) {

                        continue;
                    }

                    BigDecimal breakdownPercent =
                            neckline
                                    .subtract(breakdownPrice)
                                    .divide(
                                            neckline,
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(100)
                                    );

                    if (breakdownPercent.compareTo(
                            BigDecimal.ONE
                    ) >= 0) {

                        return true;
                    }
                }
            }
        }

        return false;
    }

    // =========================
    // TRIPLE BOTTOM PATTERN
    // =========================

    public boolean detectTripleBottom(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Triple Bottom closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 8) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Triple Bottom closing prices cannot contain null values"
                );
            }

            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Triple Bottom closing prices must be greater than zero"
                );
            }
        }

        int breakoutIndex =
                closingPrices.size() - 1;

        List<Integer> troughIndexes =
                new ArrayList<>();

        for (int i = 1;
             i < breakoutIndex;
             i++) {

            BigDecimal current =
                    closingPrices.get(i);

            BigDecimal previous =
                    closingPrices.get(i - 1);

            BigDecimal next =
                    closingPrices.get(i + 1);

            if (current.compareTo(previous) < 0
                    && current.compareTo(next) < 0) {

                troughIndexes.add(i);
            }
        }

        if (troughIndexes.size() < 3) {
            return false;
        }

        for (int first = 0;
             first < troughIndexes.size() - 2;
             first++) {

            for (int second = first + 1;
                 second < troughIndexes.size() - 1;
                 second++) {

                for (int third = second + 1;
                     third < troughIndexes.size();
                     third++) {

                    int firstBottomIndex =
                            troughIndexes.get(first);

                    int secondBottomIndex =
                            troughIndexes.get(second);

                    int thirdBottomIndex =
                            troughIndexes.get(third);

                    BigDecimal firstBottom =
                            closingPrices.get(firstBottomIndex);

                    BigDecimal secondBottom =
                            closingPrices.get(secondBottomIndex);

                    BigDecimal thirdBottom =
                            closingPrices.get(thirdBottomIndex);

                    BigDecimal highestBottom =
                            firstBottom
                                    .max(secondBottom)
                                    .max(thirdBottom);

                    BigDecimal lowestBottom =
                            firstBottom
                                    .min(secondBottom)
                                    .min(thirdBottom);

                    BigDecimal bottomVariationPercent =
                            highestBottom
                                    .subtract(lowestBottom)
                                    .divide(
                                            lowestBottom,
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(100)
                                    );

                    if (bottomVariationPercent.compareTo(
                            BigDecimal.valueOf(2)
                    ) > 0) {

                        continue;
                    }

                    BigDecimal firstRebound =
                            closingPrices.get(
                                    firstBottomIndex + 1
                            );

                    for (int i = firstBottomIndex + 1;
                         i < secondBottomIndex;
                         i++) {

                        firstRebound =
                                firstRebound.max(
                                        closingPrices.get(i)
                                );
                    }

                    BigDecimal secondRebound =
                            closingPrices.get(
                                    secondBottomIndex + 1
                            );

                    for (int i = secondBottomIndex + 1;
                         i < thirdBottomIndex;
                         i++) {

                        secondRebound =
                                secondRebound.max(
                                        closingPrices.get(i)
                                );
                    }

                    BigDecimal averageBottom =
                            firstBottom
                                    .add(secondBottom)
                                    .add(thirdBottom)
                                    .divide(
                                            BigDecimal.valueOf(3),
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    );

                    BigDecimal firstReboundPercent =
                            firstRebound
                                    .subtract(averageBottom)
                                    .divide(
                                            averageBottom,
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(100)
                                    );

                    BigDecimal secondReboundPercent =
                            secondRebound
                                    .subtract(averageBottom)
                                    .divide(
                                            averageBottom,
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(100)
                                    );

                    if (firstReboundPercent.compareTo(
                            BigDecimal.valueOf(2)
                    ) < 0
                            || secondReboundPercent.compareTo(
                            BigDecimal.valueOf(2)
                    ) < 0) {

                        continue;
                    }

                    BigDecimal largerRebound =
                            firstRebound.max(secondRebound);

                    BigDecimal reboundVariationPercent =
                            firstRebound
                                    .subtract(secondRebound)
                                    .abs()
                                    .divide(
                                            largerRebound,
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(100)
                                    );

                    if (reboundVariationPercent.compareTo(
                            BigDecimal.valueOf(3)
                    ) > 0) {

                        continue;
                    }

                    BigDecimal neckline =
                            firstRebound.max(secondRebound);

                    BigDecimal breakoutPrice =
                            closingPrices.get(
                                    breakoutIndex
                            );

                    if (breakoutPrice.compareTo(
                            neckline
                    ) <= 0) {

                        continue;
                    }

                    BigDecimal breakoutPercent =
                            breakoutPrice
                                    .subtract(neckline)
                                    .divide(
                                            neckline,
                                            SCALE,
                                            RoundingMode.HALF_UP
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(100)
                                    );

                    if (breakoutPercent.compareTo(
                            BigDecimal.ONE
                    ) >= 0) {

                        return true;
                    }
                }
            }
        }

        return false;
    }

    // =========================
    // BULLISH RECTANGLE PATTERN
    // =========================

    public boolean detectBullishRectangle(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Bullish Rectangle closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 8) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Bullish Rectangle closing prices cannot contain null values"
                );
            }

            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Bullish Rectangle closing prices must be greater than zero"
                );
            }
        }

        int breakoutIndex =
                closingPrices.size() - 1;

        List<BigDecimal> resistancePeaks =
                new ArrayList<>();

        List<BigDecimal> supportTroughs =
                new ArrayList<>();

        for (int i = 1;
             i < breakoutIndex;
             i++) {

            BigDecimal previous =
                    closingPrices.get(i - 1);

            BigDecimal current =
                    closingPrices.get(i);

            BigDecimal next =
                    closingPrices.get(i + 1);

            if (current.compareTo(previous) > 0
                    && current.compareTo(next) > 0) {

                resistancePeaks.add(current);
            }

            if (current.compareTo(previous) < 0
                    && current.compareTo(next) < 0) {

                supportTroughs.add(current);
            }
        }

        if (resistancePeaks.size() < 2
                || supportTroughs.size() < 2) {

            return false;
        }

        BigDecimal highestResistance =
                resistancePeaks.get(0);

        BigDecimal lowestResistance =
                resistancePeaks.get(0);

        for (BigDecimal peak : resistancePeaks) {

            highestResistance =
                    highestResistance.max(peak);

            lowestResistance =
                    lowestResistance.min(peak);
        }

        BigDecimal resistanceVariationPercent =
                highestResistance
                        .subtract(lowestResistance)
                        .divide(
                                highestResistance,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (resistanceVariationPercent.compareTo(
                BigDecimal.ONE
        ) > 0) {

            return false;
        }

        BigDecimal highestSupport =
                supportTroughs.get(0);

        BigDecimal lowestSupport =
                supportTroughs.get(0);

        for (BigDecimal trough : supportTroughs) {

            highestSupport =
                    highestSupport.max(trough);

            lowestSupport =
                    lowestSupport.min(trough);
        }

        BigDecimal supportVariationPercent =
                highestSupport
                        .subtract(lowestSupport)
                        .divide(
                                highestSupport,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (supportVariationPercent.compareTo(
                BigDecimal.ONE
        ) > 0) {

            return false;
        }

        BigDecimal averageResistance =
                highestResistance
                        .add(lowestResistance)
                        .divide(
                                BigDecimal.valueOf(2),
                                SCALE,
                                RoundingMode.HALF_UP
                        );

        BigDecimal averageSupport =
                highestSupport
                        .add(lowestSupport)
                        .divide(
                                BigDecimal.valueOf(2),
                                SCALE,
                                RoundingMode.HALF_UP
                        );

        if (averageResistance.compareTo(
                averageSupport
        ) <= 0) {

            return false;
        }

        BigDecimal rectangleHeightPercent =
                averageResistance
                        .subtract(averageSupport)
                        .divide(
                                averageSupport,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (rectangleHeightPercent.compareTo(
                BigDecimal.valueOf(2)
        ) < 0) {

            return false;
        }

        BigDecimal breakoutPrice =
                closingPrices.get(
                        breakoutIndex
                );

        if (breakoutPrice.compareTo(
                highestResistance
        ) <= 0) {

            return false;
        }

        BigDecimal breakoutPercent =
                breakoutPrice
                        .subtract(highestResistance)
                        .divide(
                                highestResistance,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        return breakoutPercent.compareTo(
                BigDecimal.ONE
        ) >= 0;
    }
    // =========================
    // BEARISH RECTANGLE PATTERN
    // =========================

    public boolean detectBearishRectangle(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Bearish Rectangle closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 8) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Bearish Rectangle closing prices cannot contain null values"
                );
            }

            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Bearish Rectangle closing prices must be greater than zero"
                );
            }
        }

        int breakdownIndex =
                closingPrices.size() - 1;

        List<BigDecimal> resistancePeaks =
                new ArrayList<>();

        List<BigDecimal> supportTroughs =
                new ArrayList<>();

        for (int i = 1;
             i < breakdownIndex;
             i++) {

            BigDecimal previous =
                    closingPrices.get(i - 1);

            BigDecimal current =
                    closingPrices.get(i);

            BigDecimal next =
                    closingPrices.get(i + 1);

            if (current.compareTo(previous) > 0
                    && current.compareTo(next) > 0) {

                resistancePeaks.add(current);
            }

            if (current.compareTo(previous) < 0
                    && current.compareTo(next) < 0) {

                supportTroughs.add(current);
            }
        }

        if (resistancePeaks.size() < 2
                || supportTroughs.size() < 2) {

            return false;
        }

        BigDecimal highestResistance =
                resistancePeaks.get(0);

        BigDecimal lowestResistance =
                resistancePeaks.get(0);

        for (BigDecimal peak : resistancePeaks) {

            highestResistance =
                    highestResistance.max(peak);

            lowestResistance =
                    lowestResistance.min(peak);
        }

        BigDecimal resistanceVariationPercent =
                highestResistance
                        .subtract(lowestResistance)
                        .divide(
                                highestResistance,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (resistanceVariationPercent.compareTo(
                BigDecimal.ONE
        ) > 0) {

            return false;
        }

        BigDecimal highestSupport =
                supportTroughs.get(0);

        BigDecimal lowestSupport =
                supportTroughs.get(0);

        for (BigDecimal trough : supportTroughs) {

            highestSupport =
                    highestSupport.max(trough);

            lowestSupport =
                    lowestSupport.min(trough);
        }

        BigDecimal supportVariationPercent =
                highestSupport
                        .subtract(lowestSupport)
                        .divide(
                                highestSupport,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (supportVariationPercent.compareTo(
                BigDecimal.ONE
        ) > 0) {

            return false;
        }

        BigDecimal averageResistance =
                highestResistance
                        .add(lowestResistance)
                        .divide(
                                BigDecimal.valueOf(2),
                                SCALE,
                                RoundingMode.HALF_UP
                        );

        BigDecimal averageSupport =
                highestSupport
                        .add(lowestSupport)
                        .divide(
                                BigDecimal.valueOf(2),
                                SCALE,
                                RoundingMode.HALF_UP
                        );

        if (averageResistance.compareTo(
                averageSupport
        ) <= 0) {

            return false;
        }

        BigDecimal rectangleHeightPercent =
                averageResistance
                        .subtract(averageSupport)
                        .divide(
                                averageSupport,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (rectangleHeightPercent.compareTo(
                BigDecimal.valueOf(2)
        ) < 0) {

            return false;
        }

        BigDecimal breakdownPrice =
                closingPrices.get(
                        breakdownIndex
                );

        if (breakdownPrice.compareTo(
                lowestSupport
        ) >= 0) {

            return false;
        }

        BigDecimal breakdownPercent =
                lowestSupport
                        .subtract(breakdownPrice)
                        .divide(
                                lowestSupport,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        return breakdownPercent.compareTo(
                BigDecimal.ONE
        ) >= 0;
    }
    // =========================
    // ASCENDING CHANNEL PATTERN
    // =========================

    public boolean detectAscendingChannel(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Ascending Channel closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 7) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Ascending Channel closing prices cannot contain null values"
                );
            }

            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Ascending Channel closing prices must be greater than zero"
                );
            }
        }

        List<BigDecimal> channelHighs =
                new ArrayList<>();

        List<BigDecimal> channelLows =
                new ArrayList<>();

        for (int i = 1;
             i < closingPrices.size() - 1;
             i++) {

            BigDecimal previous =
                    closingPrices.get(i - 1);

            BigDecimal current =
                    closingPrices.get(i);

            BigDecimal next =
                    closingPrices.get(i + 1);

            if (current.compareTo(previous) > 0
                    && current.compareTo(next) > 0) {

                channelHighs.add(current);
            }

            if (current.compareTo(previous) < 0
                    && current.compareTo(next) < 0) {

                channelLows.add(current);
            }
        }

        if (channelHighs.size() < 3
                || channelLows.size() < 3) {

            return false;
        }

        // Higher highs
        for (int i = 1;
             i < channelHighs.size();
             i++) {

            if (channelHighs.get(i).compareTo(
                    channelHighs.get(i - 1)
            ) <= 0) {

                return false;
            }
        }

        // Higher lows
        for (int i = 1;
             i < channelLows.size();
             i++) {

            if (channelLows.get(i).compareTo(
                    channelLows.get(i - 1)
            ) <= 0) {

                return false;
            }
        }

        BigDecimal highSlope =
                channelHighs
                        .get(channelHighs.size() - 1)
                        .subtract(channelHighs.get(0))
                        .divide(
                                BigDecimal.valueOf(
                                        channelHighs.size() - 1L
                                ),
                                SCALE,
                                RoundingMode.HALF_UP
                        );

        BigDecimal lowSlope =
                channelLows
                        .get(channelLows.size() - 1)
                        .subtract(channelLows.get(0))
                        .divide(
                                BigDecimal.valueOf(
                                        channelLows.size() - 1L
                                ),
                                SCALE,
                                RoundingMode.HALF_UP
                        );

        if (highSlope.compareTo(BigDecimal.ZERO) <= 0
                || lowSlope.compareTo(BigDecimal.ZERO) <= 0) {

            return false;
        }

        BigDecimal largerSlope =
                highSlope.max(lowSlope);

        BigDecimal slopeDifferencePercent =
                highSlope
                        .subtract(lowSlope)
                        .abs()
                        .divide(
                                largerSlope,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        // Upper and lower channel lines should be roughly parallel.
        if (slopeDifferencePercent.compareTo(
                BigDecimal.valueOf(25)
        ) > 0) {

            return false;
        }

        int pairCount =
                Math.min(
                        channelHighs.size(),
                        channelLows.size()
                );

        BigDecimal minimumWidth = null;
        BigDecimal maximumWidth = null;

        for (int i = 0;
             i < pairCount;
             i++) {

            BigDecimal width =
                    channelHighs.get(i)
                            .subtract(
                                    channelLows.get(i)
                            );

            if (width.compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }

            if (minimumWidth == null
                    || width.compareTo(minimumWidth) < 0) {

                minimumWidth = width;
            }

            if (maximumWidth == null
                    || width.compareTo(maximumWidth) > 0) {

                maximumWidth = width;
            }
        }

        BigDecimal widthVariationPercent =
                maximumWidth
                        .subtract(minimumWidth)
                        .divide(
                                maximumWidth,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (widthVariationPercent.compareTo(
                BigDecimal.valueOf(25)
        ) > 0) {

            return false;
        }

        BigDecimal startingPrice =
                closingPrices.get(0);

        BigDecimal endingPrice =
                closingPrices.get(
                        closingPrices.size() - 1
                );

        if (endingPrice.compareTo(startingPrice) <= 0) {
            return false;
        }

        BigDecimal overallGainPercent =
                endingPrice
                        .subtract(startingPrice)
                        .divide(
                                startingPrice,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        return overallGainPercent.compareTo(
                BigDecimal.valueOf(3)
        ) >= 0;
    }
    // =========================
    // DESCENDING CHANNEL PATTERN
    // =========================

    public boolean detectDescendingChannel(
            List<BigDecimal> closingPrices) {

        if (closingPrices == null) {
            throw new IllegalArgumentException(
                    "Descending Channel closing prices cannot be null"
            );
        }

        if (closingPrices.size() < 7) {
            return false;
        }

        for (BigDecimal price : closingPrices) {

            if (price == null) {
                throw new IllegalArgumentException(
                        "Descending Channel closing prices cannot contain null values"
                );
            }

            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Descending Channel closing prices must be greater than zero"
                );
            }
        }

        List<BigDecimal> channelHighs =
                new ArrayList<>();

        List<BigDecimal> channelLows =
                new ArrayList<>();

        for (int i = 1;
             i < closingPrices.size() - 1;
             i++) {

            BigDecimal previous =
                    closingPrices.get(i - 1);

            BigDecimal current =
                    closingPrices.get(i);

            BigDecimal next =
                    closingPrices.get(i + 1);

            if (current.compareTo(previous) > 0
                    && current.compareTo(next) > 0) {

                channelHighs.add(current);
            }

            if (current.compareTo(previous) < 0
                    && current.compareTo(next) < 0) {

                channelLows.add(current);
            }
        }

        if (channelHighs.size() < 3
                || channelLows.size() < 3) {

            return false;
        }

        // Lower highs
        for (int i = 1;
             i < channelHighs.size();
             i++) {

            if (channelHighs.get(i).compareTo(
                    channelHighs.get(i - 1)
            ) >= 0) {

                return false;
            }
        }

        // Lower lows
        for (int i = 1;
             i < channelLows.size();
             i++) {

            if (channelLows.get(i).compareTo(
                    channelLows.get(i - 1)
            ) >= 0) {

                return false;
            }
        }

        BigDecimal highSlope =
                channelHighs
                        .get(channelHighs.size() - 1)
                        .subtract(channelHighs.get(0))
                        .divide(
                                BigDecimal.valueOf(
                                        channelHighs.size() - 1L
                                ),
                                SCALE,
                                RoundingMode.HALF_UP
                        );

        BigDecimal lowSlope =
                channelLows
                        .get(channelLows.size() - 1)
                        .subtract(channelLows.get(0))
                        .divide(
                                BigDecimal.valueOf(
                                        channelLows.size() - 1L
                                ),
                                SCALE,
                                RoundingMode.HALF_UP
                        );

        if (highSlope.compareTo(BigDecimal.ZERO) >= 0
                || lowSlope.compareTo(BigDecimal.ZERO) >= 0) {

            return false;
        }

        BigDecimal absoluteHighSlope =
                highSlope.abs();

        BigDecimal absoluteLowSlope =
                lowSlope.abs();

        BigDecimal largerSlope =
                absoluteHighSlope.max(
                        absoluteLowSlope
                );

        BigDecimal slopeDifferencePercent =
                absoluteHighSlope
                        .subtract(absoluteLowSlope)
                        .abs()
                        .divide(
                                largerSlope,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        // Upper and lower channel lines should be roughly parallel.
        if (slopeDifferencePercent.compareTo(
                BigDecimal.valueOf(25)
        ) > 0) {

            return false;
        }

        int pairCount =
                Math.min(
                        channelHighs.size(),
                        channelLows.size()
                );

        BigDecimal minimumWidth = null;
        BigDecimal maximumWidth = null;

        for (int i = 0;
             i < pairCount;
             i++) {

            BigDecimal width =
                    channelHighs.get(i)
                            .subtract(
                                    channelLows.get(i)
                            );

            if (width.compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }

            if (minimumWidth == null
                    || width.compareTo(minimumWidth) < 0) {

                minimumWidth = width;
            }

            if (maximumWidth == null
                    || width.compareTo(maximumWidth) > 0) {

                maximumWidth = width;
            }
        }

        BigDecimal widthVariationPercent =
                maximumWidth
                        .subtract(minimumWidth)
                        .divide(
                                maximumWidth,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        if (widthVariationPercent.compareTo(
                BigDecimal.valueOf(25)
        ) > 0) {

            return false;
        }

        BigDecimal startingPrice =
                closingPrices.get(0);

        BigDecimal endingPrice =
                closingPrices.get(
                        closingPrices.size() - 1
                );

        if (endingPrice.compareTo(startingPrice) >= 0) {
            return false;
        }

        BigDecimal overallLossPercent =
                startingPrice
                        .subtract(endingPrice)
                        .divide(
                                startingPrice,
                                SCALE,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                BigDecimal.valueOf(100)
                        );

        return overallLossPercent.compareTo(
                BigDecimal.valueOf(3)
        ) >= 0;
    }
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
                            prices.subList(
                                    0,
                                    end
                            )
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
                            .subtract(
                                    slowEma
                            )
                            .setScale(
                                    SCALE,
                                    RoundingMode.HALF_UP
                            );

            macdValues.add(
                    macd
            );
        }

        return macdValues;
    }

    private BigDecimal calculateInitialSma(
            List<BigDecimal> prices,
            int period) {

        BigDecimal sum =
                prices
                        .subList(
                                0,
                                period
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
                    indicatorName
                            + " price lists cannot be null"
            );
        }

        if (period <= 0) {

            throw new IllegalArgumentException(
                    indicatorName
                            + " period must be greater than zero"
            );
        }

        if (highPrices.size()
                != lowPrices.size()
                || highPrices.size()
                != closingPrices.size()) {

            throw new IllegalArgumentException(
                    indicatorName
                            + " price lists must have the same size"
            );
        }

        if (highPrices.size()
                < period + 1) {

            throw new IllegalArgumentException(
                    "Not enough prices to calculate "
                            + indicatorName
            );
        }
    }

    private void validatePricesAndPeriod(
            List<BigDecimal> prices,
            int period,
            String indicatorName) {

        if (prices == null
                || prices.isEmpty()) {

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

        if (fastPeriod <= 0
                || slowPeriod <= 0) {

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