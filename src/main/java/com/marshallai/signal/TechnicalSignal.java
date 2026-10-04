package com.marshallai.signal;

public class TechnicalSignal {

    private final int bullishWeightedScore;
    private final int bearishWeightedScore;
    private final int confidencePercent;

    public TechnicalSignal(
            int bullishWeightedScore,
            int bearishWeightedScore,
            int confidencePercent) {

        if (bullishWeightedScore < 0
                || bearishWeightedScore < 0) {

            throw new IllegalArgumentException(
                    "Technical weighted scores cannot be negative"
            );
        }

        if (confidencePercent < 0
                || confidencePercent > 100) {

            throw new IllegalArgumentException(
                    "Confidence percent must be between 0 and 100"
            );
        }

        this.bullishWeightedScore =
                bullishWeightedScore;

        this.bearishWeightedScore =
                bearishWeightedScore;

        this.confidencePercent =
                confidencePercent;
    }

    public int getBullishWeightedScore() {
        return bullishWeightedScore;
    }

    public int getBearishWeightedScore() {
        return bearishWeightedScore;
    }

    public int getConfidencePercent() {
        return confidencePercent;
    }

    public boolean isBullish() {
        return bullishWeightedScore
                > bearishWeightedScore;
    }

    public boolean isBearish() {
        return bearishWeightedScore
                > bullishWeightedScore;
    }

    public boolean isNeutral() {
        return bullishWeightedScore
                == bearishWeightedScore;
    }
}