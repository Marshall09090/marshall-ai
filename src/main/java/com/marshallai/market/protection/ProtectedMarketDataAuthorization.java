package com.marshallai.market.protection;

import java.util.UUID;

public record ProtectedMarketDataAuthorization(
        long guardId,
        UUID runId,
        UUID instanceId,
        String configurationHash
) {

    public ProtectedMarketDataAuthorization {

        if (guardId <= 0) {
            throw new IllegalArgumentException(
                    "Guard ID must be greater than zero"
            );
        }

        if (runId == null) {
            throw new IllegalArgumentException(
                    "Run ID cannot be null"
            );
        }

        if (instanceId == null) {
            throw new IllegalArgumentException(
                    "Instance ID cannot be null"
            );
        }

        if (configurationHash == null
                || configurationHash.isBlank()) {

            throw new IllegalArgumentException(
                    "Configuration hash cannot be blank"
            );
        }
    }
}