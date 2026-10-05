package com.marshallai.persistence.criteria;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class CriteriaRegistry {

    private final Map<String, CriteriaRecord> registeredCriteria =
            new LinkedHashMap<>();

    public synchronized CriteriaRecord register(
            CriteriaRecord criteriaRecord) {

        if (criteriaRecord == null) {
            throw new IllegalArgumentException(
                    "Criteria record cannot be null"
            );
        }

        if (registeredCriteria.containsKey(
                criteriaRecord.criteriaVersion())) {

            throw new IllegalStateException(
                    "Criteria version is already registered: "
                            + criteriaRecord.criteriaVersion()
            );
        }

        registeredCriteria.put(
                criteriaRecord.criteriaVersion(),
                criteriaRecord
        );

        return criteriaRecord;
    }

    public synchronized Optional<CriteriaRecord> find(
            String criteriaVersion) {

        if (criteriaVersion == null
                || criteriaVersion.isBlank()) {

            return Optional.empty();
        }

        return Optional.ofNullable(
                registeredCriteria.get(criteriaVersion)
        );
    }

    public synchronized CriteriaRecord requireRegistered(
            String criteriaVersion) {

        return find(criteriaVersion)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Acceptance criteria must be registered "
                                        + "before a governed run can start"
                        )
                );
    }

    public synchronized int size() {
        return registeredCriteria.size();
    }
}