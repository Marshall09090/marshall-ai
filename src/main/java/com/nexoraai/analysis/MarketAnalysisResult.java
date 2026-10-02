package com.nexoraai.analysis;

import java.util.List;
import java.util.Objects;

public class MarketAnalysisResult {

    public enum Direction {
        BULLISH,
        BEARISH,
        NEUTRAL
    }

    private final List<String> detectedPatterns;
    private final Direction direction;

    public MarketAnalysisResult(
            List<String> detectedPatterns,
            Direction direction) {

        this.detectedPatterns =
                List.copyOf(
                        Objects.requireNonNull(
                                detectedPatterns,
                                "Detected patterns cannot be null"
                        )
                );

        this.direction =
                Objects.requireNonNull(
                        direction,
                        "Market direction cannot be null"
                );
    }

    public List<String> getDetectedPatterns() {
        return detectedPatterns;
    }

    public Direction getDirection() {
        return direction;
    }
}