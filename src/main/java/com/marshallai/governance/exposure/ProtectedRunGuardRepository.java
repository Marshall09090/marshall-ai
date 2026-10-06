package com.marshallai.governance.exposure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProtectedRunGuardRepository {

    private static final String ACQUIRE_REFUSAL_REASON =
            "TARGET_NOT_RESERVED_OR_GOVERNANCE_INTEGRITY_MISMATCH";

    private static final String GOVERNANCE_INTEGRITY_MISMATCH =
            "GOVERNANCE_INTEGRITY_MISMATCH";

    private static final String EXPOSURE_TRANSITION_REFUSED =
            "EXPOSURE_TRANSITION_REFUSED";

    private static final String PROTECTED_RUN_OWNERSHIP_MISMATCH =
            "PROTECTED_RUN_OWNERSHIP_MISMATCH";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public ProtectedRunGuardRepository(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {

        if (jdbcTemplate == null) {
            throw new IllegalArgumentException(
                    "JdbcTemplate cannot be null"
            );
        }

        if (transactionManager == null) {
            throw new IllegalArgumentException(
                    "Transaction manager cannot be null"
            );
        }

        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }

    /**
     * Creates a protected validation or holdout reservation.
     *
     * The mutable guard row and the initial RESERVED ledger event
     * are created in the same database transaction.
     */
    public long createReservation(
            String strategyVersion,
            String protectedPeriodId,
            ProtectedPeriodType protectedPeriodType,
            LocalDate periodStart,
            LocalDate periodEnd,
            Instant eventTimestamp) {

        requireText(
                strategyVersion,
                "Strategy version"
        );

        requireText(
                protectedPeriodId,
                "Protected period ID"
        );

        requireNotNull(
                protectedPeriodType,
                "Protected period type"
        );

        requireNotNull(
                periodStart,
                "Period start"
        );

        requireNotNull(
                periodEnd,
                "Period end"
        );

        requireNotNull(
                eventTimestamp,
                "Event timestamp"
        );

        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException(
                    "Protected period start cannot be after period end"
            );
        }

        Long guardId =
                transactionTemplate.execute(status -> {

                    Long createdGuardId =
                            jdbcTemplate.queryForObject(
                                    """
                                    INSERT INTO protected_run_guard (
                                        strategy_version,
                                        protected_period_id,
                                        protected_period_type,
                                        period_start,
                                        period_end,
                                        state
                                    )
                                    VALUES (?, ?, ?, ?, ?, 'RESERVED')
                                    RETURNING id
                                    """,
                                    Long.class,
                                    strategyVersion,
                                    protectedPeriodId,
                                    protectedPeriodType.name(),
                                    periodStart,
                                    periodEnd
                            );

                    if (createdGuardId == null) {
                        throw new IllegalStateException(
                                "Protected run reservation did not return a guard ID"
                        );
                    }

                    UUID eventId =
                            UUID.randomUUID();

                    String eventHash =
                            calculateEventHash(
                                    eventId,
                                    createdGuardId,
                                    null,
                                    ProtectedRunState.RESERVED.name(),
                                    null,
                                    ProtectedRunState.RESERVED,
                                    null,
                                    null,
                                    null,
                                    eventTimestamp,
                                    null
                            );

                    insertLedgerEvent(
                            eventId,
                            createdGuardId,
                            null,
                            ProtectedRunState.RESERVED.name(),
                            null,
                            ProtectedRunState.RESERVED,
                            null,
                            null,
                            null,
                            eventTimestamp,
                            null,
                            eventHash
                    );

                    return createdGuardId;
                });

        if (guardId == null) {
            throw new IllegalStateException(
                    "Protected run reservation transaction returned no guard ID"
            );
        }

        return guardId;
    }

    /**
     * Atomically acquires a protected target.
     *
     * IMPORTANT:
     *
     * We deliberately do not perform:
     *
     *     SELECT state
     *     Java validation
     *     UPDATE state
     *
     * because two concurrent requests could both observe RESERVED.
     *
     * Instead the state check and transition happen in one
     * conditional UPDATE.
     *
     * The UPDATE also refuses acquisition when the mutable guard
     * disagrees with the latest append-only ledger state.
     */
    public AcquisitionResult acquireReservedTarget(
            long guardId,
            UUID runId,
            UUID instanceId,
            String configurationHash,
            Instant eventTimestamp) {

        requirePositiveGuardId(
                guardId
        );

        requireNotNull(
                runId,
                "Run ID"
        );

        requireNotNull(
                instanceId,
                "Instance ID"
        );

        requireText(
                configurationHash,
                "Configuration hash"
        );

        requireNotNull(
                eventTimestamp,
                "Event timestamp"
        );

        AcquisitionResult result =
                transactionTemplate.execute(status -> {

                    int affectedRows =
                            jdbcTemplate.update(
                                    """
                                    UPDATE protected_run_guard AS guard
                                    SET
                                        state = 'RUNNING_UNEXPOSED',
                                        run_id = ?,
                                        instance_id = ?,
                                        configuration_hash = ?,
                                        market_data_fingerprint = NULL
                                    WHERE guard.id = ?
                                      AND guard.state = 'RESERVED'
                                      AND EXISTS (
                                          SELECT 1
                                          FROM protected_run_event_ledger AS ledger
                                          WHERE ledger.guard_id = guard.id
                                            AND ledger.sequence_number = (
                                                SELECT MAX(latest.sequence_number)
                                                FROM protected_run_event_ledger AS latest
                                                WHERE latest.guard_id = guard.id
                                            )
                                            AND ledger.new_state = guard.state
                                      )
                                    """,
                                    runId,
                                    instanceId,
                                    configurationHash,
                                    guardId
                            );

                    if (affectedRows == 0) {

                        return AcquisitionResult.refused(
                                guardId,
                                classifyAcquisitionRefusal(
                                        guardId
                                )
                        );
                    }

                    if (affectedRows != 1) {
                        throw new IllegalStateException(
                                "Atomic protected-run acquisition affected "
                                        + affectedRows
                                        + " rows"
                        );
                    }

                    /*
                     * At this point the guard row is already
                     * RUNNING_UNEXPOSED inside this transaction.
                     *
                     * The latest ledger event is intentionally still
                     * RESERVED until we append the matching transition.
                     */
                    LatestLedgerEvent previousEvent =
                            requireLatestLedgerEvent(
                                    guardId
                            );

                    if (previousEvent.state()
                            != ProtectedRunState.RESERVED) {

                        throw new IllegalStateException(
                                "Latest ledger state must be RESERVED before acquisition"
                        );
                    }

                    UUID eventId =
                            UUID.randomUUID();

                    String eventHash =
                            calculateEventHash(
                                    eventId,
                                    guardId,
                                    runId,
                                    ProtectedRunState.RUNNING_UNEXPOSED.name(),
                                    ProtectedRunState.RESERVED,
                                    ProtectedRunState.RUNNING_UNEXPOSED,
                                    instanceId,
                                    configurationHash,
                                    null,
                                    eventTimestamp,
                                    previousEvent.eventHash()
                            );

                    insertLedgerEvent(
                            eventId,
                            guardId,
                            runId,
                            ProtectedRunState.RUNNING_UNEXPOSED.name(),
                            ProtectedRunState.RESERVED,
                            ProtectedRunState.RUNNING_UNEXPOSED,
                            instanceId,
                            configurationHash,
                            null,
                            eventTimestamp,
                            previousEvent.eventHash(),
                            eventHash
                    );

                    return AcquisitionResult.acquired(
                            guardId,
                            runId,
                            instanceId
                    );
                });

        if (result == null) {
            throw new IllegalStateException(
                    "Protected-run acquisition transaction returned no result"
            );
        }

        return result;
    }

    /**
     * Persists the critical RUNNING_UNEXPOSED -> EXPOSED boundary.
     *
     * Protected results must NOT be logged, returned, or otherwise
     * made visible before this method completes successfully.
     *
     * The guard state change and EXPOSED ledger event commit in the
     * same transaction.
     *
     * The market-data fingerprint is frozen at exposure time because
     * deterministic recovery of an incomplete exposed run must later
     * prove that the exact same data was used.
     */
    public ExposureResult persistExposed(
            long guardId,
            UUID runId,
            UUID instanceId,
            String configurationHash,
            String marketDataFingerprint,
            Instant eventTimestamp) {

        requirePositiveGuardId(
                guardId
        );

        requireNotNull(
                runId,
                "Run ID"
        );

        requireNotNull(
                instanceId,
                "Instance ID"
        );

        requireText(
                configurationHash,
                "Configuration hash"
        );

        requireText(
                marketDataFingerprint,
                "Market-data fingerprint"
        );

        requireNotNull(
                eventTimestamp,
                "Event timestamp"
        );

        ExposureResult result =
                transactionTemplate.execute(status -> {

                    int affectedRows =
                            jdbcTemplate.update(
                                    """
                                    UPDATE protected_run_guard AS guard
                                    SET
                                        state = 'EXPOSED',
                                        market_data_fingerprint = ?
                                    WHERE guard.id = ?
                                      AND guard.state = 'RUNNING_UNEXPOSED'
                                      AND guard.run_id = ?
                                      AND guard.instance_id = ?
                                      AND guard.configuration_hash = ?
                                      AND EXISTS (
                                          SELECT 1
                                          FROM protected_run_event_ledger AS ledger
                                          WHERE ledger.guard_id = guard.id
                                            AND ledger.sequence_number = (
                                                SELECT MAX(latest.sequence_number)
                                                FROM protected_run_event_ledger AS latest
                                                WHERE latest.guard_id = guard.id
                                            )
                                            AND ledger.new_state = guard.state
                                      )
                                    """,
                                    marketDataFingerprint,
                                    guardId,
                                    runId,
                                    instanceId,
                                    configurationHash
                            );

                    if (affectedRows == 0) {

                        ExposureRefusal refusal =
                                classifyExposureRefusal(
                                        guardId,
                                        runId,
                                        instanceId,
                                        configurationHash
                                );

                        return ExposureResult.refused(
                                guardId,
                                refusal.currentState(),
                                refusal.reason()
                        );
                    }

                    if (affectedRows != 1) {
                        throw new IllegalStateException(
                                "EXPOSED transition affected "
                                        + affectedRows
                                        + " rows"
                        );
                    }

                    /*
                     * The guard is EXPOSED inside this transaction,
                     * while the latest ledger event is still
                     * RUNNING_UNEXPOSED until the matching event
                     * below is appended.
                     */
                    LatestLedgerEvent previousEvent =
                            requireLatestLedgerEvent(
                                    guardId
                            );

                    if (previousEvent.state()
                            != ProtectedRunState.RUNNING_UNEXPOSED) {

                        throw new IllegalStateException(
                                "Latest ledger state must be RUNNING_UNEXPOSED before exposure"
                        );
                    }

                    UUID eventId =
                            UUID.randomUUID();

                    String eventHash =
                            calculateEventHash(
                                    eventId,
                                    guardId,
                                    runId,
                                    ProtectedRunState.EXPOSED.name(),
                                    ProtectedRunState.RUNNING_UNEXPOSED,
                                    ProtectedRunState.EXPOSED,
                                    instanceId,
                                    configurationHash,
                                    marketDataFingerprint,
                                    eventTimestamp,
                                    previousEvent.eventHash()
                            );

                    insertLedgerEvent(
                            eventId,
                            guardId,
                            runId,
                            ProtectedRunState.EXPOSED.name(),
                            ProtectedRunState.RUNNING_UNEXPOSED,
                            ProtectedRunState.EXPOSED,
                            instanceId,
                            configurationHash,
                            marketDataFingerprint,
                            eventTimestamp,
                            previousEvent.eventHash(),
                            eventHash
                    );

                    return ExposureResult.exposed(
                            guardId,
                            runId,
                            instanceId,
                            marketDataFingerprint
                    );
                });

        if (result == null) {
            throw new IllegalStateException(
                    "Protected exposure transaction returned no result"
            );
        }

        return result;
    }

    /**
     * Retrieves the current mutable guard snapshot.
     */
    public GuardSnapshot requireGuard(
            long guardId) {

        requirePositiveGuardId(
                guardId
        );

        return jdbcTemplate.query(
                        """
                        SELECT
                            id,
                            strategy_version,
                            protected_period_id,
                            protected_period_type,
                            period_start,
                            period_end,
                            state,
                            run_id,
                            instance_id,
                            configuration_hash,
                            market_data_fingerprint
                        FROM protected_run_guard
                        WHERE id = ?
                        """,
                        (resultSet, rowNumber) ->
                                new GuardSnapshot(
                                        resultSet.getLong(
                                                "id"
                                        ),
                                        resultSet.getString(
                                                "strategy_version"
                                        ),
                                        resultSet.getString(
                                                "protected_period_id"
                                        ),
                                        ProtectedPeriodType.valueOf(
                                                resultSet.getString(
                                                        "protected_period_type"
                                                )
                                        ),
                                        resultSet.getObject(
                                                "period_start",
                                                LocalDate.class
                                        ),
                                        resultSet.getObject(
                                                "period_end",
                                                LocalDate.class
                                        ),
                                        ProtectedRunState.valueOf(
                                                resultSet.getString(
                                                        "state"
                                                )
                                        ),
                                        resultSet.getObject(
                                                "run_id",
                                                UUID.class
                                        ),
                                        resultSet.getObject(
                                                "instance_id",
                                                UUID.class
                                        ),
                                        resultSet.getString(
                                                "configuration_hash"
                                        ),
                                        resultSet.getString(
                                                "market_data_fingerprint"
                                        )
                                ),
                        guardId
                )
                .stream()
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Protected run guard does not exist: "
                                        + guardId
                        )
                );
    }

    /**
     * Returns a compact snapshot of the latest ledger state.
     */
    public LedgerSnapshot requireLatestLedgerSnapshot(
            long guardId) {

        requirePositiveGuardId(
                guardId
        );

        LatestLedgerEvent event =
                requireLatestLedgerEvent(
                        guardId
                );

        return new LedgerSnapshot(
                guardId,
                event.state(),
                event.eventHash()
        );
    }

    /**
     * Classifies a failed atomic acquisition.
     *
     * This classification occurs only AFTER the atomic conditional
     * UPDATE has failed.
     *
     * It therefore does not introduce the unsafe
     * read-state-then-update race.
     */
    private String classifyAcquisitionRefusal(
            long guardId) {

        GuardSnapshot guard =
                requireGuard(
                        guardId
                );

        Optional<LatestLedgerEvent> latestLedgerEvent =
                findLatestLedgerEvent(
                        guardId
                );

        if (latestLedgerEvent.isEmpty()) {
            return GOVERNANCE_INTEGRITY_MISMATCH;
        }

        if (guard.state()
                != latestLedgerEvent.get().state()) {

            return GOVERNANCE_INTEGRITY_MISMATCH;
        }

        /*
         * Preserve the existing generic refusal for normal
         * non-RESERVED acquisition failures so the existing
         * repository contract remains compatible.
         */
        return ACQUIRE_REFUSAL_REASON;
    }

    /**
     * Classifies a failed RUNNING_UNEXPOSED -> EXPOSED transition.
     */
    private ExposureRefusal classifyExposureRefusal(
            long guardId,
            UUID requestedRunId,
            UUID requestedInstanceId,
            String requestedConfigurationHash) {

        GuardSnapshot guard =
                requireGuard(
                        guardId
                );

        Optional<LatestLedgerEvent> latestLedgerEvent =
                findLatestLedgerEvent(
                        guardId
                );

        if (latestLedgerEvent.isEmpty()) {

            return new ExposureRefusal(
                    guard.state(),
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }

        if (guard.state()
                != latestLedgerEvent.get().state()) {

            return new ExposureRefusal(
                    guard.state(),
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }

        if (guard.state()
                != ProtectedRunState.RUNNING_UNEXPOSED) {

            return new ExposureRefusal(
                    guard.state(),
                    EXPOSURE_TRANSITION_REFUSED
            );
        }

        if (!requestedRunId.equals(
                guard.runId())) {

            return new ExposureRefusal(
                    guard.state(),
                    PROTECTED_RUN_OWNERSHIP_MISMATCH
            );
        }

        if (!requestedInstanceId.equals(
                guard.instanceId())) {

            return new ExposureRefusal(
                    guard.state(),
                    PROTECTED_RUN_OWNERSHIP_MISMATCH
            );
        }

        if (!requestedConfigurationHash.equals(
                guard.configurationHash())) {

            return new ExposureRefusal(
                    guard.state(),
                    PROTECTED_RUN_OWNERSHIP_MISMATCH
            );
        }

        return new ExposureRefusal(
                guard.state(),
                EXPOSURE_TRANSITION_REFUSED
        );
    }

    private Optional<LatestLedgerEvent> findLatestLedgerEvent(
            long guardId) {

        return jdbcTemplate.query(
                        """
                        SELECT
                            new_state,
                            event_hash
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                        ORDER BY sequence_number DESC
                        LIMIT 1
                        """,
                        (resultSet, rowNumber) ->
                                new LatestLedgerEvent(
                                        ProtectedRunState.valueOf(
                                                resultSet.getString(
                                                        "new_state"
                                                )
                                        ),
                                        resultSet.getString(
                                                "event_hash"
                                        )
                                ),
                        guardId
                )
                .stream()
                .findFirst();
    }

    private LatestLedgerEvent requireLatestLedgerEvent(
            long guardId) {

        return findLatestLedgerEvent(
                guardId
        )
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Protected run guard has no ledger history: "
                                        + guardId
                        )
                );
    }

    /**
     * Appends one immutable ledger event.
     *
     * PostgreSQL JDBC does not reliably infer the SQL type for
     * java.time.Instant when JdbcTemplate receives it as a generic
     * argument, so the value is converted to Timestamp only at the
     * JDBC boundary.
     *
     * The original Instant remains the canonical value used by the
     * hash chain.
     */
    private void insertLedgerEvent(
            UUID eventId,
            long guardId,
            UUID runId,
            String eventType,
            ProtectedRunState previousState,
            ProtectedRunState newState,
            UUID instanceId,
            String configurationHash,
            String marketDataFingerprint,
            Instant eventTimestamp,
            String previousEventHash,
            String eventHash) {

        Timestamp jdbcEventTimestamp =
                Timestamp.from(
                        eventTimestamp
                );

        int inserted =
                jdbcTemplate.update(
                        """
                        INSERT INTO protected_run_event_ledger (
                            event_id,
                            guard_id,
                            run_id,
                            event_type,
                            previous_state,
                            new_state,
                            instance_id,
                            configuration_hash,
                            market_data_fingerprint,
                            previous_event_hash,
                            event_hash,
                            event_timestamp
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        eventId,
                        guardId,
                        runId,
                        eventType,
                        previousState == null
                                ? null
                                : previousState.name(),
                        newState.name(),
                        instanceId,
                        configurationHash,
                        marketDataFingerprint,
                        previousEventHash,
                        eventHash,
                        jdbcEventTimestamp
                );

        if (inserted != 1) {
            throw new IllegalStateException(
                    "Protected ledger insert affected "
                            + inserted
                            + " rows"
            );
        }
    }

    /**
     * Calculates the tamper-evident hash for one ledger event.
     *
     * previousEventHash links this event to the event before it,
     * producing the governance hash chain.
     */
    private String calculateEventHash(
            UUID eventId,
            long guardId,
            UUID runId,
            String eventType,
            ProtectedRunState previousState,
            ProtectedRunState newState,
            UUID instanceId,
            String configurationHash,
            String marketDataFingerprint,
            Instant eventTimestamp,
            String previousEventHash) {

        String canonical =
                canonical(eventId)
                        + "|"
                        + guardId
                        + "|"
                        + canonical(runId)
                        + "|"
                        + canonical(eventType)
                        + "|"
                        + canonical(
                        previousState == null
                                ? null
                                : previousState.name()
                )
                        + "|"
                        + canonical(
                        newState.name()
                )
                        + "|"
                        + canonical(instanceId)
                        + "|"
                        + canonical(configurationHash)
                        + "|"
                        + canonical(marketDataFingerprint)
                        + "|"
                        + canonical(eventTimestamp)
                        + "|"
                        + canonical(previousEventHash);

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            canonical.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat.of()
                    .formatHex(
                            hash
                    );

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Unable to calculate protected ledger event hash",
                    exception
            );
        }
    }

    private String canonical(
            Object value) {

        return value == null
                ? ""
                : value.toString();
    }

    private static void requirePositiveGuardId(
            long guardId) {

        if (guardId <= 0) {
            throw new IllegalArgumentException(
                    "Guard ID must be greater than zero"
            );
        }
    }

    private static void requireText(
            String value,
            String fieldName) {

        if (value == null ||
                value.isBlank()) {

            throw new IllegalArgumentException(
                    fieldName
                            + " cannot be blank"
            );
        }
    }

    private static void requireNotNull(
            Object value,
            String fieldName) {

        if (value == null) {

            throw new IllegalArgumentException(
                    fieldName
                            + " cannot be null"
            );
        }
    }

    private record LatestLedgerEvent(
            ProtectedRunState state,
            String eventHash) {
    }

    private record ExposureRefusal(
            ProtectedRunState currentState,
            String reason) {
    }

    public record GuardSnapshot(
            long guardId,
            String strategyVersion,
            String protectedPeriodId,
            ProtectedPeriodType protectedPeriodType,
            LocalDate periodStart,
            LocalDate periodEnd,
            ProtectedRunState state,
            UUID runId,
            UUID instanceId,
            String configurationHash,
            String marketDataFingerprint) {
    }

    public record LedgerSnapshot(
            long guardId,
            ProtectedRunState state,
            String eventHash) {
    }

    public record AcquisitionResult(
            boolean acquired,
            long guardId,
            UUID runId,
            UUID instanceId,
            String refusalReason) {

        public static AcquisitionResult acquired(
                long guardId,
                UUID runId,
                UUID instanceId) {

            return new AcquisitionResult(
                    true,
                    guardId,
                    runId,
                    instanceId,
                    null
            );
        }

        public static AcquisitionResult refused(
                long guardId,
                String refusalReason) {

            return new AcquisitionResult(
                    false,
                    guardId,
                    null,
                    null,
                    refusalReason
            );
        }
    }

    public record ExposureResult(
            boolean exposed,
            long guardId,
            UUID runId,
            UUID instanceId,
            ProtectedRunState state,
            String marketDataFingerprint,
            String refusalReason) {

        public static ExposureResult exposed(
                long guardId,
                UUID runId,
                UUID instanceId,
                String marketDataFingerprint) {

            return new ExposureResult(
                    true,
                    guardId,
                    runId,
                    instanceId,
                    ProtectedRunState.EXPOSED,
                    marketDataFingerprint,
                    null
            );
        }

        public static ExposureResult refused(
                long guardId,
                ProtectedRunState currentState,
                String refusalReason) {

            return new ExposureResult(
                    false,
                    guardId,
                    null,
                    null,
                    currentState,
                    null,
                    refusalReason
            );
        }
    }
}