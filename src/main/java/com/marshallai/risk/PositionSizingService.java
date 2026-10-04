package com.marshallai.risk;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PositionSizingService {

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    public PositionSizeResult calculate(
            BigDecimal accountBalance,
            BigDecimal riskPercent,
            BigDecimal entryPrice,
            BigDecimal stopLossPrice) {

        validateInputs(
                accountBalance,
                riskPercent,
                entryPrice,
                stopLossPrice
        );

        BigDecimal riskAmount =
                accountBalance
                        .multiply(riskPercent)
                        .divide(
                                ONE_HUNDRED,
                                2,
                                RoundingMode.DOWN
                        );

        BigDecimal riskPerUnit =
                entryPrice
                        .subtract(stopLossPrice)
                        .abs();

        if (riskPerUnit.signum() == 0) {
            throw new IllegalArgumentException(
                    "Entry price and stop-loss price cannot be equal"
            );
        }

        int riskBasedQuantity =
                riskAmount
                        .divide(
                                riskPerUnit,
                                0,
                                RoundingMode.DOWN
                        )
                        .intValue();

        int capitalBasedQuantity =
                accountBalance
                        .divide(
                                entryPrice,
                                0,
                                RoundingMode.DOWN
                        )
                        .intValue();

        int quantity =
                Math.min(
                        riskBasedQuantity,
                        capitalBasedQuantity
                );

        BigDecimal positionValue =
                entryPrice
                        .multiply(
                                BigDecimal.valueOf(quantity)
                        )
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        return new PositionSizeResult(
                accountBalance,
                riskPercent,
                riskAmount,
                entryPrice,
                stopLossPrice,
                riskPerUnit,
                quantity,
                positionValue
        );
    }

    private void validateInputs(
            BigDecimal accountBalance,
            BigDecimal riskPercent,
            BigDecimal entryPrice,
            BigDecimal stopLossPrice) {

        if (accountBalance == null
                || riskPercent == null
                || entryPrice == null
                || stopLossPrice == null) {

            throw new IllegalArgumentException(
                    "Position sizing inputs cannot be null"
            );
        }

        if (accountBalance.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Account balance must be greater than zero"
            );
        }

        if (riskPercent.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Risk percent must be greater than zero"
            );
        }

        if (riskPercent.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException(
                    "Risk percent cannot exceed 100"
            );
        }

        if (entryPrice.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Entry price must be greater than zero"
            );
        }

        if (stopLossPrice.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Stop-loss price must be greater than zero"
            );
        }

        if (entryPrice.compareTo(stopLossPrice) == 0) {
            throw new IllegalArgumentException(
                    "Entry price and stop-loss price cannot be equal"
            );
        }
    }
}