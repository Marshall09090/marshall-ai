package com.marshallai.persistence.run;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RunRecord(
        UUID runId,
        RunType runType,
        String strategyVersion,
        String configurationVersion,
        String buildVersion,
        String criteriaVersion,
        String symbol,
        String timeframe,
        String dataSource,
        String dataFeed,
        String adjustmentMethod,
        String marketDataFingerprint,
        Instant marketDataFetchedAt,
        Map<String, Object> configurationSnapshot,
        Map<String, Object> criteriaSnapshot,
        Instant createdAt
) {

    public RunRecord {

        if (runId == null) {
            throw new IllegalArgumentException(
                    "Run ID cannot be null"
            );
        }

        if (runType == null) {
            throw new IllegalArgumentException(
                    "Run type cannot be null"
            );
        }

        if (configurationVersion == null
                || configurationVersion.isBlank()) {

            throw new IllegalArgumentException(
                    "Configuration version cannot be blank"
            );
        }

        if (createdAt == null) {
            throw new IllegalArgumentException(
                    "Run creation timestamp cannot be null"
            );
        }

        configurationSnapshot =
                configurationSnapshot == null
                        ? Map.of()
                        : Map.copyOf(configurationSnapshot);

        criteriaSnapshot =
                criteriaSnapshot == null
                        ? Map.of()
                        : Map.copyOf(criteriaSnapshot);
    }
}