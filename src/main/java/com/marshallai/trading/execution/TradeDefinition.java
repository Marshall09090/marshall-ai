package com.marshallai.trading.execution;

import java.math.BigDecimal;

/**
 * Immutable configuration for the MarshallAI Trade Definition v1.
 *
 * These values define the trade mechanics used by research,
 * backtesting and execution. Changing any governed value requires
 * a new trade-definition/configuration version.
 */
public record TradeDefinition(
        String version,
        int atrPeriods,
        BigDecimal riskPercent,
        BigDecimal maximumPositionExposurePercent,
        BigDecimal initialStopAtrMultiplier,
        BigDecimal entryLimitAtrMultiplier,
        BigDecimal trailingStopAtrMultiplier,
        int staleCheckBar,
        BigDecimal staleMinimumR
) {

    public TradeDefinition {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException(
                    "Trade definition version cannot be blank"
            );
        }

        if (atrPeriods <= 0) {
            throw new IllegalArgumentException(
                    "ATR periods must be greater than zero"
            );
        }

        requirePositive(riskPercent, "Risk percent");
        requirePositive(
                maximumPositionExposurePercent,
                "Maximum position exposure percent"
        );
        requirePositive(
                initialStopAtrMultiplier,
                "Initial stop ATR multiplier"
        );
        requirePositive(
                entryLimitAtrMultiplier,
                "Entry limit ATR multiplier"
        );
        requirePositive(
                trailingStopAtrMultiplier,
                "Trailing stop ATR multiplier"
        );

        if (staleCheckBar <= 0) {
            throw new IllegalArgumentException(
                    "Stale check bar must be greater than zero"
            );
        }

        if (staleMinimumR == null ||
                staleMinimumR.signum() < 0) {
            throw new IllegalArgumentException(
                    "Stale minimum R cannot be negative"
            );
        }
    }

    public static TradeDefinition v1() {
        return new TradeDefinition(
                "trade-definition-v1",
                14,
                new BigDecimal("0.01"),
                new BigDecimal("0.05"),
                new BigDecimal("2"),
                new BigDecimal("1"),
                new BigDecimal("3"),
                20,
                new BigDecimal("1")
        );
    }

    public boolean longOnly() {
        return true;
    }

    public String atrSmoothing() {
        return "WILDER";
    }

    public boolean openingOnlyEntry() {
        return true;
    }

    public String entryTimeInForce() {
        return "OPG";
    }

    public boolean hasFixedProfitTarget() {
        return false;
    }

    private static void requirePositive(
            BigDecimal value,
            String fieldName) {

        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be greater than zero"
            );
        }
    }
}