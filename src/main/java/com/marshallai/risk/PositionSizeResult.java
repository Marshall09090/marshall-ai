package com.marshallai.risk;

import java.math.BigDecimal;

public class PositionSizeResult {

    private final BigDecimal accountBalance;
    private final BigDecimal riskPercent;
    private final BigDecimal riskAmount;
    private final BigDecimal entryPrice;
    private final BigDecimal stopLossPrice;
    private final BigDecimal riskPerUnit;
    private final int quantity;
    private final BigDecimal positionValue;

    public PositionSizeResult(
            BigDecimal accountBalance,
            BigDecimal riskPercent,
            BigDecimal riskAmount,
            BigDecimal entryPrice,
            BigDecimal stopLossPrice,
            BigDecimal riskPerUnit,
            int quantity,
            BigDecimal positionValue) {

        if (accountBalance == null
                || riskPercent == null
                || riskAmount == null
                || entryPrice == null
                || stopLossPrice == null
                || riskPerUnit == null
                || positionValue == null) {

            throw new IllegalArgumentException(
                    "Position size values cannot be null"
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

        if (riskAmount.signum() < 0
                || riskPerUnit.signum() <= 0
                || positionValue.signum() < 0) {

            throw new IllegalArgumentException(
                    "Calculated risk values are invalid"
            );
        }

        if (entryPrice.signum() <= 0
                || stopLossPrice.signum() <= 0) {

            throw new IllegalArgumentException(
                    "Prices must be greater than zero"
            );
        }

        if (quantity < 0) {
            throw new IllegalArgumentException(
                    "Quantity cannot be negative"
            );
        }

        this.accountBalance = accountBalance;
        this.riskPercent = riskPercent;
        this.riskAmount = riskAmount;
        this.entryPrice = entryPrice;
        this.stopLossPrice = stopLossPrice;
        this.riskPerUnit = riskPerUnit;
        this.quantity = quantity;
        this.positionValue = positionValue;
    }

    public BigDecimal getAccountBalance() {
        return accountBalance;
    }

    public BigDecimal getRiskPercent() {
        return riskPercent;
    }

    public BigDecimal getRiskAmount() {
        return riskAmount;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public BigDecimal getStopLossPrice() {
        return stopLossPrice;
    }

    public BigDecimal getRiskPerUnit() {
        return riskPerUnit;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getPositionValue() {
        return positionValue;
    }
}