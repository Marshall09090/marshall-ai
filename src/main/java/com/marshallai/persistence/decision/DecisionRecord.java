package com.marshallai.persistence.decision;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DecisionRecord(
        UUID decisionId,
        UUID runId,
        String symbol,
        String tradingDecision,
        int confidencePercent,
        int bullishWeightedScore,
        int bearishWeightedScore,
        boolean approved,
        String rejectionGate,
        String rejectionReason,
        Map<String, Object> evidenceSnapshot,
        Instant createdAt
) {

    public DecisionRecord {

        if (decisionId == null) {
            throw new IllegalArgumentException(
                    "Decision ID cannot be null"
            );
        }

        if (runId == null) {
            throw new IllegalArgumentException(
                    "Run ID cannot be null"
            );
        }

        if (tradingDecision == null
                || tradingDecision.isBlank()) {

            throw new IllegalArgumentException(
                    "Trading decision cannot be blank"
            );
        }

        if (confidencePercent < 0
                || confidencePercent > 100) {

            throw new IllegalArgumentException(
                    "Confidence must be between 0 and 100"
            );
        }

        if (!approved) {

            if (rejectionGate == null
                    || rejectionGate.isBlank()) {

                throw new IllegalArgumentException(
                        "Rejected decisions must record the rejection gate"
                );
            }

            if (rejectionReason == null
                    || rejectionReason.isBlank()) {

                throw new IllegalArgumentException(
                        "Rejected decisions must record the rejection reason"
                );
            }
        }

        if (createdAt == null) {
            throw new IllegalArgumentException(
                    "Decision creation timestamp cannot be null"
            );
        }

        evidenceSnapshot =
                evidenceSnapshot == null
                        ? Map.of()
                        : Map.copyOf(evidenceSnapshot);
    }
}