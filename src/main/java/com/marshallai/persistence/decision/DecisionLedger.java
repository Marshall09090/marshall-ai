package com.marshallai.persistence.decision;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DecisionLedger {

    private final List<DecisionRecord> decisions =
            new ArrayList<>();

    public synchronized DecisionRecord append(
            DecisionRecord decisionRecord) {

        if (decisionRecord == null) {
            throw new IllegalArgumentException(
                    "Decision record cannot be null"
            );
        }

        boolean duplicate =
                decisions.stream()
                        .anyMatch(
                                existing ->
                                        existing.decisionId()
                                                .equals(
                                                        decisionRecord.decisionId()
                                                )
                        );

        if (duplicate) {
            throw new IllegalStateException(
                    "Decision already exists: "
                            + decisionRecord.decisionId()
            );
        }

        decisions.add(decisionRecord);

        return decisionRecord;
    }

    public synchronized List<DecisionRecord> findByRunId(
            UUID runId) {

        if (runId == null) {
            return List.of();
        }

        return decisions.stream()
                .filter(
                        decision ->
                                decision.runId().equals(runId)
                )
                .toList();
    }

    public synchronized int size() {
        return decisions.size();
    }
}