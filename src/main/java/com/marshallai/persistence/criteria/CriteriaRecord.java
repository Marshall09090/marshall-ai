package com.marshallai.persistence.criteria;

import java.time.Instant;
import java.util.Map;

public record CriteriaRecord(
        String criteriaVersion,
        Map<String, Object> criteriaSnapshot,
        Instant registeredAt
) {

    public CriteriaRecord {

        if (criteriaVersion == null || criteriaVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "Criteria version cannot be blank"
            );
        }

        if (registeredAt == null) {
            throw new IllegalArgumentException(
                    "Criteria registration timestamp cannot be null"
            );
        }

        criteriaSnapshot =
                criteriaSnapshot == null
                        ? Map.of()
                        : Map.copyOf(criteriaSnapshot);
    }
}