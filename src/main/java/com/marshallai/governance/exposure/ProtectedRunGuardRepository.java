package com.marshallai.governance.exposure;

import com.marshallai.market.MarketDataProviderType;

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

    private static final String SPENT_TRANSITION_REFUSED =
            "SPENT_TRANSITION_REFUSED";

    private static final String PROTECTED_RUN_OWNERSHIP_MISMATCH =
            "PROTECTED_RUN_OWNERSHIP_MISMATCH";

    private static final String PROTECTED_RUN_INPUT_MISMATCH =
            "PROTECTED_RUN_INPUT_MISMATCH";

    private static final String
            GOVERNED_VERDICT_REQUIRES_REAL_DATA_SOURCE =
            "GOVERNED_VERDICT_REQUIRES_REAL_DATA_SOURCE";

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

        this.jdbcTemplate =
                jdbcTemplate;

        this.transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );
    }

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

        if (periodStart.isAfter(
                periodEnd
        )) {

            throw new IllegalArgumentException(
                    "Protected period start cannot be after period end"
            );
        }

        Long guardId =
                transactionTemplate.execute(
                        status -> {

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
                                        "Protected run reservation did not "
                                                + "return a guard ID"
                                );
                            }

                            UUID eventId =
                                    UUID.randomUUID();

                            String eventHash =
                                    calculateEventHash(
                                            eventId,
                                            createdGuardId,
                                            null,
                                            ProtectedRunState.RESERVED
                                                    .name(),
                                            null,
                                            ProtectedRunState.RESERVED,
                                            null,
                                            null,
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
                                    ProtectedRunState.RESERVED
                                            .name(),
                                    null,
                                    ProtectedRunState.RESERVED,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    eventTimestamp,
                                    null,
                                    eventHash
                            );

                            return createdGuardId;
                        }
                );

        if (guardId == null) {

            throw new IllegalStateException(
                    "Protected run reservation transaction "
                            + "returned no guard ID"
            );
        }

        return guardId;
    }

    public AcquisitionResult acquireReservedTarget(
            long guardId,
            UUID runId,
            UUID instanceId,
            String configurationHash,
            MarketDataProviderType dataSourceType,
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
                dataSourceType,
                "Data source type"
        );

        requireNotNull(
                eventTimestamp,
                "Event timestamp"
        );

        AcquisitionResult result =
                transactionTemplate.execute(
                        status -> {

                            int affectedRows =
                                    jdbcTemplate.update(
                                            """
                                            UPDATE protected_run_guard AS guard
                                            SET
                                                state = 'RUNNING_UNEXPOSED',
                                                run_id = ?,
                                                instance_id = ?,
                                                configuration_hash = ?,
                                                data_source_type = ?,
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
                                            dataSourceType.name(),
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
                                        "Atomic protected-run acquisition "
                                                + "affected "
                                                + affectedRows
                                                + " rows"
                                );
                            }

                            LatestLedgerEvent previousEvent =
                                    requireLatestLedgerEvent(
                                            guardId
                                    );

                            if (previousEvent.state()
                                    != ProtectedRunState.RESERVED) {

                                throw new IllegalStateException(
                                        "Latest ledger state must be "
                                                + "RESERVED before acquisition"
                                );
                            }

                            UUID eventId =
                                    UUID.randomUUID();

                            String eventHash =
                                    calculateEventHash(
                                            eventId,
                                            guardId,
                                            runId,
                                            ProtectedRunState
                                                    .RUNNING_UNEXPOSED
                                                    .name(),
                                            ProtectedRunState.RESERVED,
                                            ProtectedRunState
                                                    .RUNNING_UNEXPOSED,
                                            instanceId,
                                            configurationHash,
                                            null,
                                            dataSourceType,
                                            null,
                                            eventTimestamp,
                                            previousEvent.eventHash()
                                    );

                            insertLedgerEvent(
                                    eventId,
                                    guardId,
                                    runId,
                                    ProtectedRunState
                                            .RUNNING_UNEXPOSED
                                            .name(),
                                    ProtectedRunState.RESERVED,
                                    ProtectedRunState
                                            .RUNNING_UNEXPOSED,
                                    instanceId,
                                    configurationHash,
                                    null,
                                    dataSourceType,
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
                        }
                );

        if (result == null) {

            throw new IllegalStateException(
                    "Protected-run acquisition transaction "
                            + "returned no result"
            );
        }

        return result;
    }

    public AbortResult abortUnexposedRun(
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

        AbortResult result =
                transactionTemplate.execute(
                        status -> {

                            GuardSnapshot guard =
                                    requireGuard(
                                            guardId
                                    );

                            LatestLedgerEvent previousEvent =
                                    requireLatestLedgerEvent(
                                            guardId
                                    );

                            if (guard.state()
                                    != previousEvent.state()) {

                                return AbortResult.refused(
                                        guardId,
                                        GOVERNANCE_INTEGRITY_MISMATCH
                                );
                            }

                            if (guard.dataSourceType() == null
                                    || previousEvent.dataSourceType()
                                    == null
                                    || guard.dataSourceType()
                                    != previousEvent.dataSourceType()) {

                                return AbortResult.refused(
                                        guardId,
                                        GOVERNANCE_INTEGRITY_MISMATCH
                                );
                            }

                            if (guard.state()
                                    != ProtectedRunState
                                    .RUNNING_UNEXPOSED) {

                                return AbortResult.refused(
                                        guardId,
                                        "ABORT_REQUIRES_RUNNING_UNEXPOSED"
                                );
                            }

                            if (!runId.equals(
                                    guard.runId()
                            )
                                    || !instanceId.equals(
                                    guard.instanceId()
                            )
                                    || !configurationHash.equals(
                                    guard.configurationHash()
                            )) {

                                return AbortResult.refused(
                                        guardId,
                                        PROTECTED_RUN_OWNERSHIP_MISMATCH
                                );
                            }

                            if (guard.marketDataFingerprint()
                                    != null) {

                                return AbortResult.refused(
                                        guardId,
                                        "ABORT_AFTER_EXPOSURE_REFUSED"
                                );
                            }

                            int affectedRows =
                                    jdbcTemplate.update(
                                            """
                                            UPDATE protected_run_guard
                                            SET
                                                state = 'RESERVED',
                                                run_id = NULL,
                                                instance_id = NULL,
                                                configuration_hash = NULL,
                                                data_source_type = NULL,
                                                market_data_fingerprint = NULL
                                            WHERE id = ?
                                              AND state = 'RUNNING_UNEXPOSED'
                                              AND run_id = ?
                                              AND instance_id = ?
                                              AND configuration_hash = ?
                                              AND data_source_type = ?
                                              AND market_data_fingerprint IS NULL
                                            """,
                                            guardId,
                                            runId,
                                            instanceId,
                                            configurationHash,
                                            guard.dataSourceType()
                                                    .name()
                                    );

                            if (affectedRows == 0) {

                                return AbortResult.refused(
                                        guardId,
                                        "ABORT_STATE_CHANGED"
                                );
                            }

                            if (affectedRows != 1) {

                                throw new IllegalStateException(
                                        "Protected-run abort affected "
                                                + affectedRows
                                                + " rows"
                                );
                            }

                            UUID eventId =
                                    UUID.randomUUID();

                            String eventHash =
                                    calculateEventHash(
                                            eventId,
                                            guardId,
                                            runId,
                                            "ABORTED_RUN",
                                            ProtectedRunState
                                                    .RUNNING_UNEXPOSED,
                                            ProtectedRunState.RESERVED,
                                            instanceId,
                                            configurationHash,
                                            null,
                                            guard.dataSourceType(),
                                            null,
                                            eventTimestamp,
                                            previousEvent.eventHash()
                                    );

                            insertLedgerEvent(
                                    eventId,
                                    guardId,
                                    runId,
                                    "ABORTED_RUN",
                                    ProtectedRunState
                                            .RUNNING_UNEXPOSED,
                                    ProtectedRunState.RESERVED,
                                    instanceId,
                                    configurationHash,
                                    null,
                                    guard.dataSourceType(),
                                    null,
                                    eventTimestamp,
                                    previousEvent.eventHash(),
                                    eventHash
                            );

                            return AbortResult.completed(
                                    guardId,
                                    runId,
                                    instanceId
                            );
                        }
                );

        if (result == null) {

            throw new IllegalStateException(
                    "Protected-run abort transaction "
                            + "returned no result"
            );
        }

        return result;
    }

    public RecoveryResult recoverStaleUnexposedRun(
            long guardId,
            UUID currentInstanceId,
            Instant eventTimestamp) {

        requirePositiveGuardId(
                guardId
        );

        requireNotNull(
                currentInstanceId,
                "Current instance ID"
        );

        requireNotNull(
                eventTimestamp,
                "Event timestamp"
        );

        RecoveryResult result =
                transactionTemplate.execute(
                        status -> {

                            GuardSnapshot guard =
                                    requireGuard(
                                            guardId
                                    );

                            LatestLedgerEvent previousEvent =
                                    requireLatestLedgerEvent(
                                            guardId
                                    );

                            if (guard.state()
                                    != previousEvent.state()) {

                                return RecoveryResult.refused(
                                        guardId,
                                        GOVERNANCE_INTEGRITY_MISMATCH
                                );
                            }

                            if (guard.dataSourceType() == null
                                    || previousEvent.dataSourceType()
                                    == null
                                    || guard.dataSourceType()
                                    != previousEvent.dataSourceType()) {

                                return RecoveryResult.refused(
                                        guardId,
                                        GOVERNANCE_INTEGRITY_MISMATCH
                                );
                            }

                            if (guard.state()
                                    != ProtectedRunState
                                    .RUNNING_UNEXPOSED) {

                                return RecoveryResult.refused(
                                        guardId,
                                        "RECOVERY_REQUIRES_RUNNING_UNEXPOSED"
                                );
                            }

                            if (guard.runId() == null
                                    || guard.instanceId() == null
                                    || guard.configurationHash() == null
                                    || guard.configurationHash()
                                    .isBlank()) {

                                return RecoveryResult.refused(
                                        guardId,
                                        GOVERNANCE_INTEGRITY_MISMATCH
                                );
                            }

                            if (currentInstanceId.equals(
                                    guard.instanceId()
                            )) {

                                return RecoveryResult.refused(
                                        guardId,
                                        "RECOVERY_REQUIRES_EARLIER_INSTANCE"
                                );
                            }

                            if (guard.marketDataFingerprint()
                                    != null) {

                                return RecoveryResult.refused(
                                        guardId,
                                        "RECOVERY_AFTER_EXPOSURE_REFUSED"
                                );
                            }

                            UUID abandonedRunId =
                                    guard.runId();

                            UUID abandonedInstanceId =
                                    guard.instanceId();

                            String abandonedConfigurationHash =
                                    guard.configurationHash();

                            MarketDataProviderType
                                    abandonedDataSourceType =
                                    guard.dataSourceType();

                            int affectedRows =
                                    jdbcTemplate.update(
                                            """
                                            UPDATE protected_run_guard
                                            SET
                                                state = 'RESERVED',
                                                run_id = NULL,
                                                instance_id = NULL,
                                                configuration_hash = NULL,
                                                data_source_type = NULL,
                                                market_data_fingerprint = NULL
                                            WHERE id = ?
                                              AND state = 'RUNNING_UNEXPOSED'
                                              AND run_id = ?
                                              AND instance_id = ?
                                              AND configuration_hash = ?
                                              AND data_source_type = ?
                                              AND market_data_fingerprint IS NULL
                                            """,
                                            guardId,
                                            abandonedRunId,
                                            abandonedInstanceId,
                                            abandonedConfigurationHash,
                                            abandonedDataSourceType
                                                    .name()
                                    );

                            if (affectedRows == 0) {

                                return RecoveryResult.refused(
                                        guardId,
                                        "RECOVERY_STATE_CHANGED"
                                );
                            }

                            if (affectedRows != 1) {

                                throw new IllegalStateException(
                                        "Protected-run recovery affected "
                                                + affectedRows
                                                + " rows"
                                );
                            }

                            UUID eventId =
                                    UUID.randomUUID();

                            String eventHash =
                                    calculateEventHash(
                                            eventId,
                                            guardId,
                                            abandonedRunId,
                                            "ABORTED_RUN",
                                            ProtectedRunState
                                                    .RUNNING_UNEXPOSED,
                                            ProtectedRunState.RESERVED,
                                            abandonedInstanceId,
                                            abandonedConfigurationHash,
                                            null,
                                            abandonedDataSourceType,
                                            null,
                                            eventTimestamp,
                                            previousEvent.eventHash()
                                    );

                            insertLedgerEvent(
                                    eventId,
                                    guardId,
                                    abandonedRunId,
                                    "ABORTED_RUN",
                                    ProtectedRunState
                                            .RUNNING_UNEXPOSED,
                                    ProtectedRunState.RESERVED,
                                    abandonedInstanceId,
                                    abandonedConfigurationHash,
                                    null,
                                    abandonedDataSourceType,
                                    null,
                                    eventTimestamp,
                                    previousEvent.eventHash(),
                                    eventHash
                            );

                            return RecoveryResult.success(
                                    guardId,
                                    abandonedRunId,
                                    abandonedInstanceId
                            );
                        }
                );

        if (result == null) {

            throw new IllegalStateException(
                    "Protected-run recovery transaction "
                            + "returned no result"
            );
        }

        return result;
    }

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
                transactionTemplate.execute(
                        status -> {

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
                                              AND guard.data_source_type IS NOT NULL
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
                                                    AND ledger.data_source_type = guard.data_source_type
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

                            LatestLedgerEvent previousEvent =
                                    requireLatestLedgerEvent(
                                            guardId
                                    );

                            if (previousEvent.state()
                                    != ProtectedRunState
                                    .RUNNING_UNEXPOSED) {

                                throw new IllegalStateException(
                                        "Latest ledger state must be "
                                                + "RUNNING_UNEXPOSED "
                                                + "before exposure"
                                );
                            }

                            UUID eventId =
                                    UUID.randomUUID();

                            String eventHash =
                                    calculateEventHash(
                                            eventId,
                                            guardId,
                                            runId,
                                            ProtectedRunState.EXPOSED
                                                    .name(),
                                            ProtectedRunState
                                                    .RUNNING_UNEXPOSED,
                                            ProtectedRunState.EXPOSED,
                                            instanceId,
                                            configurationHash,
                                            marketDataFingerprint,
                                            previousEvent
                                                    .dataSourceType(),
                                            null,
                                            eventTimestamp,
                                            previousEvent.eventHash()
                                    );

                            insertLedgerEvent(
                                    eventId,
                                    guardId,
                                    runId,
                                    ProtectedRunState.EXPOSED
                                            .name(),
                                    ProtectedRunState
                                            .RUNNING_UNEXPOSED,
                                    ProtectedRunState.EXPOSED,
                                    instanceId,
                                    configurationHash,
                                    marketDataFingerprint,
                                    previousEvent.dataSourceType(),
                                    null,
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
                        }
                );

        if (result == null) {

            throw new IllegalStateException(
                    "Protected exposure transaction "
                            + "returned no result"
            );
        }

        return result;
    }

    /**
     * Authorizes deterministic recomputation of an already
     * EXPOSED protected run whose governed verdict has not yet
     * been durably persisted.
     *
     * This operation does not acquire a new protected attempt
     * and does not mutate the guard or ledger.
     *
     * Recovery is allowed only when the existing EXPOSED run is
     * reproduced with exactly the same:
     *
     * - run ID
     * - configuration hash
     * - market-data fingerprint
     */
    public DeterministicRecoveryResult
    authorizeDeterministicRecovery(
            long guardId,
            UUID requestedRunId,
            String requestedConfigurationHash,
            String requestedMarketDataFingerprint) {

        requirePositiveGuardId(
                guardId
        );

        requireNotNull(
                requestedRunId,
                "Recovery run ID"
        );

        requireText(
                requestedConfigurationHash,
                "Recovery configuration hash"
        );

        requireText(
                requestedMarketDataFingerprint,
                "Recovery market-data fingerprint"
        );

        GuardSnapshot guard =
                requireGuard(
                        guardId
                );

        LatestLedgerEvent latestEvent =
                requireLatestLedgerEvent(
                        guardId
                );

        if (guard.state()
                != latestEvent.state()) {

            return DeterministicRecoveryResult.refused(
                    guardId,
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }

        if (guard.dataSourceType() == null
                || latestEvent.dataSourceType() == null
                || guard.dataSourceType()
                != latestEvent.dataSourceType()) {

            return DeterministicRecoveryResult.refused(
                    guardId,
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }

        if (guard.state()
                != ProtectedRunState.EXPOSED) {

            return DeterministicRecoveryResult.refused(
                    guardId,
                    "DETERMINISTIC_RECOVERY_REQUIRES_EXPOSED"
            );
        }

        if (latestEvent.governedVerdict()
                != null) {

            return DeterministicRecoveryResult.refused(
                    guardId,
                    "GOVERNED_VERDICT_ALREADY_PERSISTED"
            );
        }

        if (guard.runId() == null
                || guard.configurationHash() == null
                || guard.configurationHash().isBlank()
                || guard.marketDataFingerprint() == null
                || guard.marketDataFingerprint().isBlank()) {

            return DeterministicRecoveryResult.refused(
                    guardId,
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }

        if (!requestedRunId.equals(
                guard.runId()
        )) {

            return DeterministicRecoveryResult.refused(
                    guardId,
                    PROTECTED_RUN_OWNERSHIP_MISMATCH
            );
        }

        if (!requestedConfigurationHash.equals(
                guard.configurationHash()
        )) {

            return DeterministicRecoveryResult.refused(
                    guardId,
                    PROTECTED_RUN_INPUT_MISMATCH
            );
        }

        if (!requestedMarketDataFingerprint.equals(
                guard.marketDataFingerprint()
        )) {

            return DeterministicRecoveryResult.refused(
                    guardId,
                    PROTECTED_RUN_INPUT_MISMATCH
            );
        }

        return DeterministicRecoveryResult.approved(
                guardId,
                guard.runId()
        );
    }

    /**
     * Persists the governed PASS/FAIL verdict and permanently
     * consumes the protected attempt.
     *
     * EXPOSED -> SPENT and the matching immutable ledger event
     * are committed in one transaction.
     *
     * The verdict is also included in the event hash so the
     * hash-chained audit record covers the governed outcome.
     */
    public CompletionResult persistGovernedVerdict(
            long guardId,
            UUID runId,
            UUID instanceId,
            String configurationHash,
            String marketDataFingerprint,
            String governedVerdict,
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

        String normalizedVerdict =
                normalizeGovernedVerdict(
                        governedVerdict
                );

        requireNotNull(
                eventTimestamp,
                "Event timestamp"
        );

        CompletionResult result =
                transactionTemplate.execute(
                        status -> {

                            int affectedRows =
                                    jdbcTemplate.update(
                                            """
                                            UPDATE protected_run_guard AS guard
                                            SET state = 'SPENT'
                                            WHERE guard.id = ?
                                              AND guard.state = 'EXPOSED'
                                              AND guard.run_id = ?
                                              AND guard.instance_id = ?
                                              AND guard.configuration_hash = ?
                                              AND guard.market_data_fingerprint = ?
                                              AND guard.data_source_type = 'REAL'
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
                                                    AND ledger.data_source_type = guard.data_source_type
                                              )
                                            """,
                                            guardId,
                                            runId,
                                            instanceId,
                                            configurationHash,
                                            marketDataFingerprint
                                    );

                            if (affectedRows == 0) {

                                CompletionRefusal refusal =
                                        classifyCompletionRefusal(
                                                guardId,
                                                runId,
                                                instanceId,
                                                configurationHash,
                                                marketDataFingerprint
                                        );

                                return CompletionResult.refused(
                                        guardId,
                                        refusal.currentState(),
                                        refusal.reason()
                                );
                            }

                            if (affectedRows != 1) {

                                throw new IllegalStateException(
                                        "SPENT transition affected "
                                                + affectedRows
                                                + " rows"
                                );
                            }

                            LatestLedgerEvent previousEvent =
                                    requireLatestLedgerEvent(
                                            guardId
                                    );

                            if (previousEvent.state()
                                    != ProtectedRunState.EXPOSED) {

                                throw new IllegalStateException(
                                        "Latest ledger state must be "
                                                + "EXPOSED before completion"
                                );
                            }

                            UUID eventId =
                                    UUID.randomUUID();

                            String eventHash =
                                    calculateEventHash(
                                            eventId,
                                            guardId,
                                            runId,
                                            ProtectedRunState.SPENT
                                                    .name(),
                                            ProtectedRunState.EXPOSED,
                                            ProtectedRunState.SPENT,
                                            instanceId,
                                            configurationHash,
                                            marketDataFingerprint,
                                            previousEvent
                                                    .dataSourceType(),
                                            normalizedVerdict,
                                            eventTimestamp,
                                            previousEvent.eventHash()
                                    );

                            insertLedgerEvent(
                                    eventId,
                                    guardId,
                                    runId,
                                    ProtectedRunState.SPENT
                                            .name(),
                                    ProtectedRunState.EXPOSED,
                                    ProtectedRunState.SPENT,
                                    instanceId,
                                    configurationHash,
                                    marketDataFingerprint,
                                    previousEvent.dataSourceType(),
                                    normalizedVerdict,
                                    eventTimestamp,
                                    previousEvent.eventHash(),
                                    eventHash
                            );

                            return CompletionResult.spent(
                                    guardId,
                                    runId,
                                    instanceId,
                                    normalizedVerdict
                            );
                        }
                );

        if (result == null) {

            throw new IllegalStateException(
                    "Protected completion transaction "
                            + "returned no result"
            );
        }

        return result;
    }

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
                            data_source_type,
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
                                        parseDataSourceType(
                                                resultSet.getString(
                                                        "data_source_type"
                                                )
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
                        () ->
                                new IllegalStateException(
                                        "Protected run guard "
                                                + "does not exist: "
                                                + guardId
                                )
                );
    }

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
                event.dataSourceType(),
                event.governedVerdict(),
                event.eventHash()
        );
    }

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

        if (!sourceTypeMatchesCurrentState(
                guard,
                latestLedgerEvent.get()
        )) {

            return GOVERNANCE_INTEGRITY_MISMATCH;
        }

        return ACQUIRE_REFUSAL_REASON;
    }

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

        if (!sourceTypeMatchesCurrentState(
                guard,
                latestLedgerEvent.get()
        )) {

            return new ExposureRefusal(
                    guard.state(),
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }

        if (guard.state()
                != ProtectedRunState
                .RUNNING_UNEXPOSED) {

            return new ExposureRefusal(
                    guard.state(),
                    EXPOSURE_TRANSITION_REFUSED
            );
        }

        if (!requestedRunId.equals(
                guard.runId()
        )) {

            return new ExposureRefusal(
                    guard.state(),
                    PROTECTED_RUN_OWNERSHIP_MISMATCH
            );
        }

        if (!requestedInstanceId.equals(
                guard.instanceId()
        )) {

            return new ExposureRefusal(
                    guard.state(),
                    PROTECTED_RUN_OWNERSHIP_MISMATCH
            );
        }

        if (!requestedConfigurationHash.equals(
                guard.configurationHash()
        )) {

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

    private CompletionRefusal classifyCompletionRefusal(
            long guardId,
            UUID requestedRunId,
            UUID requestedInstanceId,
            String requestedConfigurationHash,
            String requestedMarketDataFingerprint) {

        GuardSnapshot guard =
                requireGuard(
                        guardId
                );

        Optional<LatestLedgerEvent> latestLedgerEvent =
                findLatestLedgerEvent(
                        guardId
                );

        if (latestLedgerEvent.isEmpty()) {

            return new CompletionRefusal(
                    guard.state(),
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }

        if (guard.state()
                != latestLedgerEvent.get().state()) {

            return new CompletionRefusal(
                    guard.state(),
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }

        if (!sourceTypeMatchesCurrentState(
                guard,
                latestLedgerEvent.get()
        )) {

            return new CompletionRefusal(
                    guard.state(),
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }

        if (guard.state()
                != ProtectedRunState.EXPOSED) {

            return new CompletionRefusal(
                    guard.state(),
                    SPENT_TRANSITION_REFUSED
            );
        }

        if (!guard.dataSourceType()
                .allowsGovernedVerdict()) {

            return new CompletionRefusal(
                    guard.state(),
                    GOVERNED_VERDICT_REQUIRES_REAL_DATA_SOURCE
            );
        }

        if (!requestedRunId.equals(
                guard.runId()
        )) {

            return new CompletionRefusal(
                    guard.state(),
                    PROTECTED_RUN_OWNERSHIP_MISMATCH
            );
        }

        if (!requestedInstanceId.equals(
                guard.instanceId()
        )) {

            return new CompletionRefusal(
                    guard.state(),
                    PROTECTED_RUN_OWNERSHIP_MISMATCH
            );
        }

        if (!requestedConfigurationHash.equals(
                guard.configurationHash()
        )) {

            return new CompletionRefusal(
                    guard.state(),
                    PROTECTED_RUN_OWNERSHIP_MISMATCH
            );
        }

        if (!requestedMarketDataFingerprint.equals(
                guard.marketDataFingerprint()
        )) {

            return new CompletionRefusal(
                    guard.state(),
                    PROTECTED_RUN_INPUT_MISMATCH
            );
        }

        return new CompletionRefusal(
                guard.state(),
                SPENT_TRANSITION_REFUSED
        );
    }

    private static boolean sourceTypeMatchesCurrentState(
            GuardSnapshot guard,
            LatestLedgerEvent latestLedgerEvent) {

        if (guard.state()
                == ProtectedRunState.RESERVED) {

            /*
             * After ABORTED_RUN, the mutable guard returns to
             * RESERVED and clears its source type while the
             * immutable ledger event intentionally preserves
             * the abandoned run's source provenance.
             */
            return guard.dataSourceType()
                    == null;
        }

        return guard.dataSourceType() != null
                && latestLedgerEvent.dataSourceType()
                != null
                && guard.dataSourceType()
                == latestLedgerEvent.dataSourceType();
    }

    private Optional<LatestLedgerEvent>
    findLatestLedgerEvent(
            long guardId) {

        return jdbcTemplate.query(
                        """
                        SELECT
                            new_state,
                            data_source_type,
                            governed_verdict,
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
                                        parseDataSourceType(
                                                resultSet.getString(
                                                        "data_source_type"
                                                )
                                        ),
                                        resultSet.getString(
                                                "governed_verdict"
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
                        () ->
                                new IllegalStateException(
                                        "Protected run guard "
                                                + "has no ledger history: "
                                                + guardId
                                )
                );
    }

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
            MarketDataProviderType dataSourceType,
            String governedVerdict,
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
                            data_source_type,
                            governed_verdict,
                            previous_event_hash,
                            event_hash,
                            event_timestamp
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                        dataSourceType == null
                                ? null
                                : dataSourceType.name(),
                        governedVerdict,
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
            MarketDataProviderType dataSourceType,
            String governedVerdict,
            Instant eventTimestamp,
            String previousEventHash) {

        String canonical =
                canonical(
                        eventId
                )
                        + "|"
                        + guardId
                        + "|"
                        + canonical(
                        runId
                )
                        + "|"
                        + canonical(
                        eventType
                )
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
                        + canonical(
                        instanceId
                )
                        + "|"
                        + canonical(
                        configurationHash
                )
                        + "|"
                        + canonical(
                        marketDataFingerprint
                )
                        + "|"
                        + canonical(
                        dataSourceType == null
                                ? null
                                : dataSourceType.name()
                )
                        + "|"
                        + canonical(
                        governedVerdict
                )
                        + "|"
                        + canonical(
                        eventTimestamp
                )
                        + "|"
                        + canonical(
                        previousEventHash
                );

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
                    "Unable to calculate protected "
                            + "ledger event hash",
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

    private static MarketDataProviderType
    parseDataSourceType(
            String dataSourceType) {

        if (dataSourceType == null
                || dataSourceType.isBlank()) {

            return null;
        }

        return MarketDataProviderType.valueOf(
                dataSourceType
                        .trim()
                        .toUpperCase()
        );
    }

    private static String normalizeGovernedVerdict(
            String governedVerdict) {

        requireText(
                governedVerdict,
                "Governed verdict"
        );

        String normalized =
                governedVerdict
                        .trim()
                        .toUpperCase();

        if (!normalized.equals(
                "PASS"
        )
                && !normalized.equals(
                "FAIL"
        )) {

            throw new IllegalArgumentException(
                    "Governed verdict must be PASS or FAIL"
            );
        }

        return normalized;
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

        if (value == null
                || value.isBlank()) {

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
            MarketDataProviderType dataSourceType,
            String governedVerdict,
            String eventHash) {
    }

    private record ExposureRefusal(
            ProtectedRunState currentState,
            String reason) {
    }

    private record CompletionRefusal(
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
            MarketDataProviderType dataSourceType,
            String marketDataFingerprint) {
    }

    public record LedgerSnapshot(
            long guardId,
            ProtectedRunState state,
            MarketDataProviderType dataSourceType,
            String governedVerdict,
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

    public record AbortResult(
            boolean aborted,
            long guardId,
            UUID runId,
            UUID instanceId,
            String refusalReason) {

        public static AbortResult completed(
                long guardId,
                UUID runId,
                UUID instanceId) {

            return new AbortResult(
                    true,
                    guardId,
                    runId,
                    instanceId,
                    null
            );
        }

        public static AbortResult refused(
                long guardId,
                String refusalReason) {

            return new AbortResult(
                    false,
                    guardId,
                    null,
                    null,
                    refusalReason
            );
        }
    }

    public record RecoveryResult(
            boolean recovered,
            long guardId,
            UUID abortedRunId,
            UUID previousInstanceId,
            String refusalReason) {

        public static RecoveryResult success(
                long guardId,
                UUID abortedRunId,
                UUID previousInstanceId) {

            return new RecoveryResult(
                    true,
                    guardId,
                    abortedRunId,
                    previousInstanceId,
                    null
            );
        }

        public static RecoveryResult refused(
                long guardId,
                String refusalReason) {

            return new RecoveryResult(
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

    public record DeterministicRecoveryResult(
            boolean recoveryAllowed,
            long guardId,
            UUID runId,
            String refusalReason) {

        public static DeterministicRecoveryResult approved(
                long guardId,
                UUID runId) {

            return new DeterministicRecoveryResult(
                    true,
                    guardId,
                    runId,
                    null
            );
        }

        public static DeterministicRecoveryResult refused(
                long guardId,
                String refusalReason) {

            return new DeterministicRecoveryResult(
                    false,
                    guardId,
                    null,
                    refusalReason
            );
        }
    }

    public record CompletionResult(
            boolean spent,
            long guardId,
            UUID runId,
            UUID instanceId,
            ProtectedRunState state,
            String governedVerdict,
            String refusalReason) {

        public static CompletionResult spent(
                long guardId,
                UUID runId,
                UUID instanceId,
                String governedVerdict) {

            return new CompletionResult(
                    true,
                    guardId,
                    runId,
                    instanceId,
                    ProtectedRunState.SPENT,
                    governedVerdict,
                    null
            );
        }

        public static CompletionResult refused(
                long guardId,
                ProtectedRunState currentState,
                String refusalReason) {

            return new CompletionResult(
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