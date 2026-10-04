package com.marshallai.signal;

import java.util.Objects;

public class MarketSignal {

    public enum Decision {
        BUY,
        SELL,
        HOLD
    }

    private final Decision decision;
    private final int confidencePercent;
    private final int bullishSignalCount;
    private final int bearishSignalCount;

    public MarketSignal(
            Decision decision,
            int confidencePercent,
            int bullishSignalCount,
            int bearishSignalCount) {

        this.decision =
                Objects.requireNonNull(
                        decision,
                        "Decision cannot be null"
                );

        if (confidencePercent < 0 || confidencePercent > 100) {
            throw new IllegalArgumentException(
                    "Confidence percent must be between 0 and 100"
            );
        }

        if (bullishSignalCount < 0 || bearishSignalCount < 0) {
            throw new IllegalArgumentException(
                    "Signal counts cannot be negative"
            );
        }

        this.confidencePercent = confidencePercent;
        this.bullishSignalCount = bullishSignalCount;
        this.bearishSignalCount = bearishSignalCount;
    }

    public Decision getDecision() {
        return decision;
    }

    public int getConfidencePercent() {
        return confidencePercent;
    }

    public int getBullishSignalCount() {
        return bullishSignalCount;
    }

    public int getBearishSignalCount() {
        return bearishSignalCount;
    }
}