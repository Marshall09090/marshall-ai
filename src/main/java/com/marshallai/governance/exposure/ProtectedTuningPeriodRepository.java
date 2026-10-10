package com.marshallai.governance.exposure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ProtectedTuningPeriodRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProtectedTuningPeriodRepository(
            JdbcTemplate jdbcTemplate) {

        if (jdbcTemplate == null) {
            throw new IllegalArgumentException(
                    "JdbcTemplate cannot be null"
            );
        }

        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Registers a tuning period for a strategy version.
     *
     * Tuning periods are stored separately from protected
     * validation and holdout periods.
     *
     * A strategy version may have only one registered
     * tuning period.
     *
     * Database uniqueness constraints prevent a second
     * registration from silently replacing the original.
     */
    public TuningPeriodSnapshot registerTuningPeriod(
            String strategyVersion,
            String tuningPeriodId,
            LocalDate periodStart,
            LocalDate periodEnd) {

        requireText(
                strategyVersion,
                "Strategy version"
        );

        requireText(
                tuningPeriodId,
                "Tuning period ID"
        );

        requireNotNull(
                periodStart,
                "Period start"
        );

        requireNotNull(
                periodEnd,
                "Period end"
        );

        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException(
                    "Tuning period start cannot be after end"
            );
        }

        String normalizedStrategyVersion =
                strategyVersion.trim();

        String normalizedTuningPeriodId =
                tuningPeriodId.trim();

        Long tuningPeriodRecordId =
                jdbcTemplate.queryForObject(
                        """
                        INSERT INTO protected_tuning_period (
                            strategy_version,
                            tuning_period_id,
                            period_start,
                            period_end
                        )
                        VALUES (?, ?, ?, ?)
                        RETURNING id
                        """,
                        Long.class,
                        normalizedStrategyVersion,
                        normalizedTuningPeriodId,
                        periodStart,
                        periodEnd
                );

        if (tuningPeriodRecordId == null) {
            throw new IllegalStateException(
                    "Tuning period registration returned no ID"
            );
        }

        return requireTuningPeriod(
                normalizedStrategyVersion
        );
    }

    /**
     * Retrieves the registered tuning period for
     * a strategy version.
     *
     * All information comes from durable PostgreSQL
     * state rather than temporary application memory.
     */
    public Optional<TuningPeriodSnapshot> findTuningPeriod(
            String strategyVersion) {

        requireText(
                strategyVersion,
                "Strategy version"
        );

        List<TuningPeriodSnapshot> results =
                jdbcTemplate.query(
                        """
                        SELECT
                            id,
                            strategy_version,
                            tuning_period_id,
                            period_start,
                            period_end,
                            created_at
                        FROM protected_tuning_period
                        WHERE strategy_version = ?
                        """,
                        (resultSet, rowNumber) ->
                                new TuningPeriodSnapshot(
                                        resultSet.getLong(
                                                "id"
                                        ),
                                        resultSet.getString(
                                                "strategy_version"
                                        ),
                                        resultSet.getString(
                                                "tuning_period_id"
                                        ),
                                        resultSet.getObject(
                                                "period_start",
                                                LocalDate.class
                                        ),
                                        resultSet.getObject(
                                                "period_end",
                                                LocalDate.class
                                        ),
                                        resultSet.getObject(
                                                "created_at",
                                                OffsetDateTime.class
                                        ).toInstant()
                                ),
                        strategyVersion.trim()
                );

        if (results.size() > 1) {
            throw new IllegalStateException(
                    "Multiple tuning periods exist for strategy: "
                            + strategyVersion
            );
        }

        return results.stream()
                .findFirst();
    }

    /**
     * Requires an existing tuning-period registration.
     *
     * This prevents validation warm-up preparation
     * from proceeding without a registered tuning
     * period.
     */
    public TuningPeriodSnapshot requireTuningPeriod(
            String strategyVersion) {

        return findTuningPeriod(
                strategyVersion
        ).orElseThrow(
                () -> new IllegalStateException(
                        "No registered tuning period for strategy: "
                                + strategyVersion
                )
        );
    }

    /**
     * Verifies that a requested calendar-date range
     * is entirely inside the registered tuning period.
     *
     * Both date boundaries are inclusive.
     *
     * Intraday timestamp validation remains the
     * responsibility of ProtectedMarketDataBoundary.
     */
    public boolean isRangeInsideTuningPeriod(
            String strategyVersion,
            LocalDate requestedStart,
            LocalDate requestedEnd) {

        requireNotNull(
                requestedStart,
                "Requested start"
        );

        requireNotNull(
                requestedEnd,
                "Requested end"
        );

        if (requestedStart.isAfter(requestedEnd)) {
            return false;
        }

        Optional<TuningPeriodSnapshot> optionalPeriod =
                findTuningPeriod(
                        strategyVersion
                );

        if (optionalPeriod.isEmpty()) {
            return false;
        }

        TuningPeriodSnapshot period =
                optionalPeriod.get();

        return !requestedStart.isBefore(
                period.periodStart()
        ) && !requestedEnd.isAfter(
                period.periodEnd()
        );
    }

    private static void requireText(
            String value,
            String fieldName) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be blank"
            );
        }
    }

    private static void requireNotNull(
            Object value,
            String fieldName) {

        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be null"
            );
        }
    }

    /**
     * Snapshot of a registered tuning period.
     */
    public record TuningPeriodSnapshot(
            long tuningPeriodRecordId,
            String strategyVersion,
            String tuningPeriodId,
            LocalDate periodStart,
            LocalDate periodEnd,
            Instant createdAt) {
    }
}