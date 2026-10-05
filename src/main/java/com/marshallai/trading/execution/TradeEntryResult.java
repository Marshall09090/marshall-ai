package com.marshallai.trading.execution;

import java.math.BigDecimal;

/**
 * Immutable result of evaluating a Trade Definition v1 entry.
 */
public record TradeEntryResult(
        boolean filled,
        boolean skipped,
        int quantity,
        int riskBasedQuantity,
        int exposureBasedQuantity,
        BigDecimal riskAmount,
        BigDecimal frozenStopDistance,
        BigDecimal entryLimitPrice,
        BigDecimal fillPrice,
        BigDecimal initialStopPrice,
        BigDecimal initialRDenominator,
        BigDecimal openingGapAtr
) {

    public TradeEntryResult {
        if (quantity < 0) {
            throw new IllegalArgumentException(
                    "Quantity cannot be negative"
            );
        }

        if (riskBasedQuantity < 0) {
            throw new IllegalArgumentException(
                    "Risk-based quantity cannot be negative"
            );
        }

        if (exposureBasedQuantity < 0) {
            throw new IllegalArgumentException(
                    "Exposure-based quantity cannot be negative"
            );
        }

        if (riskAmount == null) {
            riskAmount = BigDecimal.ZERO;
        }

        if (frozenStopDistance == null) {
            frozenStopDistance = BigDecimal.ZERO;
        }

        if (entryLimitPrice == null) {
            entryLimitPrice = BigDecimal.ZERO;
        }

        if (initialRDenominator == null) {
            initialRDenominator = BigDecimal.ZERO;
        }

        if (openingGapAtr == null) {
            openingGapAtr = BigDecimal.ZERO;
        }

        if (filled) {
            if (skipped) {
                throw new IllegalArgumentException(
                        "A filled trade cannot also be skipped"
                );
            }

            if (quantity <= 0) {
                throw new IllegalArgumentException(
                        "A filled trade must have positive quantity"
                );
            }

            if (fillPrice == null ||
                    fillPrice.signum() <= 0) {
                throw new IllegalArgumentException(
                        "A filled trade must have a positive fill price"
                );
            }

            if (initialStopPrice == null) {
                throw new IllegalArgumentException(
                        "A filled trade must have an initial stop price"
                );
            }
        }
    }

    public static TradeEntryResult skipped(
            int quantity,
            int riskBasedQuantity,
            int exposureBasedQuantity,
            BigDecimal riskAmount,
            BigDecimal frozenStopDistance,
            BigDecimal entryLimitPrice,
            BigDecimal openingGapAtr) {

        return new TradeEntryResult(
                false,
                true,
                quantity,
                riskBasedQuantity,
                exposureBasedQuantity,
                riskAmount,
                frozenStopDistance,
                entryLimitPrice,
                null,
                null,
                BigDecimal.ZERO,
                openingGapAtr
        );
    }

    public static TradeEntryResult filled(
            int quantity,
            int riskBasedQuantity,
            int exposureBasedQuantity,
            BigDecimal riskAmount,
            BigDecimal frozenStopDistance,
            BigDecimal entryLimitPrice,
            BigDecimal fillPrice,
            BigDecimal initialStopPrice,
            BigDecimal initialRDenominator,
            BigDecimal openingGapAtr) {

        return new TradeEntryResult(
                true,
                false,
                quantity,
                riskBasedQuantity,
                exposureBasedQuantity,
                riskAmount,
                frozenStopDistance,
                entryLimitPrice,
                fillPrice,
                initialStopPrice,
                initialRDenominator,
                openingGapAtr
        );
    }
}