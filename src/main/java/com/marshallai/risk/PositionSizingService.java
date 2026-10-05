package com.marshallai.risk;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PositionSizingService {

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    /*
     * Maximum percentage of the account that may be committed
     * to a single position.
     *
     * This is independent from the stop-loss risk percentage.
     */
    private static final BigDecimal MAXIMUM_POSITION_EXPOSURE_PERCENT =
            new BigDecimal("5");

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

        // =====================================================
        // 1. MAXIMUM DOLLAR RISK
        // =====================================================

        BigDecimal riskAmount =
                accountBalance
                        .multiply(riskPercent)
                        .divide(
                                ONE_HUNDRED,
                                2,
                                RoundingMode.DOWN
                        );

        // =====================================================
        // 2. RISK PER UNIT
        // =====================================================

        BigDecimal riskPerUnit =
                entryPrice
                        .subtract(stopLossPrice)
                        .abs();

        if (riskPerUnit.signum() == 0) {
            throw new IllegalArgumentException(
                    "Entry price and stop-loss price cannot be equal"
            );
        }

        // =====================================================
        // 3. RISK-BASED QUANTITY
        //
        // Example:
        // $1,000 risk / $10 risk per share = 100 shares
        // =====================================================

        int riskBasedQuantity =
                riskAmount
                        .divide(
                                riskPerUnit,
                                0,
                                RoundingMode.DOWN
                        )
                        .intValue();

        // =====================================================
        // 4. MAXIMUM ACCOUNT EXPOSURE
        //
        // A single position may use no more than 5% of the
        // account balance.
        //
        // Example:
        // $100,000 account * 5% = $5,000 maximum exposure
        // =====================================================

        BigDecimal maximumPositionExposure =
                accountBalance
                        .multiply(
                                MAXIMUM_POSITION_EXPOSURE_PERCENT
                        )
                        .divide(
                                ONE_HUNDRED,
                                2,
                                RoundingMode.DOWN
                        );

        // =====================================================
        // 5. EXPOSURE-BASED QUANTITY
        //
        // Example:
        // $5,000 / $450 = 11 whole shares
        // =====================================================

        int exposureBasedQuantity =
                maximumPositionExposure
                        .divide(
                                entryPrice,
                                0,
                                RoundingMode.DOWN
                        )
                        .intValue();

        // =====================================================
        // 6. FINAL QUANTITY
        //
        // The safer/smaller quantity wins.
        // =====================================================

        int quantity =
                Math.min(
                        riskBasedQuantity,
                        exposureBasedQuantity
                );

        // =====================================================
        // 7. FINAL POSITION VALUE
        // =====================================================

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