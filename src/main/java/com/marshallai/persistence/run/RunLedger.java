package com.marshallai.persistence.run;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class RunLedger {

    private final Map<UUID, RunRecord> runs =
            new LinkedHashMap<>();

    public synchronized RunRecord append(
            RunRecord runRecord) {

        if (runRecord == null) {
            throw new IllegalArgumentException(
                    "Run record cannot be null"
            );
        }

        if (runs.containsKey(runRecord.runId())) {
            throw new IllegalStateException(
                    "Run already exists: "
                            + runRecord.runId()
            );
        }

        runs.put(
                runRecord.runId(),
                runRecord
        );

        return runRecord;
    }

    public synchronized Optional<RunRecord> findById(
            UUID runId) {

        if (runId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                runs.get(runId)
        );
    }

    public synchronized RunRecord requireById(
            UUID runId) {

        return findById(runId)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Run does not exist: " + runId
                        )
                );
    }

    public synchronized int size() {
        return runs.size();
    }
}