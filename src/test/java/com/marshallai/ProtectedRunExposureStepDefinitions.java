package com.marshallai;

import com.marshallai.governance.exposure.ApplicationInstanceIdentity;
import com.marshallai.governance.exposure.GovernedModeAdvisoryLock;
import com.marshallai.governance.exposure.ProtectedPeriodType;
import com.marshallai.governance.exposure.ProtectedResultExposureService;
import com.marshallai.governance.exposure.ProtectedRunOperationalLogger;
import com.marshallai.governance.exposure.ProtectedRunGuardRepository;
import com.marshallai.governance.exposure.ProtectedRunState;
import com.marshallai.market.MarketCandle;
import com.marshallai.market.MarketDataProviderSource;
import com.marshallai.market.MarketDataProviderType;
import com.marshallai.market.ProtectedMarketDataProvider;
import com.marshallai.market.protection.ProtectedMarketDataAccessException;
import com.marshallai.market.protection.ProtectedMarketDataBoundary;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.flywaydb.core.Flyway;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProtectedRunExposureStepDefinitions {

    /*
     * One PostgreSQL container is shared by all scenarios in this
     * Cucumber test JVM.
     *
     * Each protected target receives a unique protected-period ID,
     * so scenarios remain isolated while avoiding the cost of
     * starting a new PostgreSQL container for every scenario.
     */
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    private JdbcTemplate jdbcTemplate;
    private ProtectedRunGuardRepository repository;

    private long guardId;

    private UUID runId;
    private UUID instanceId;

    private String configurationHash;
    private String marketDataFingerprint;

    private ProtectedRunGuardRepository.AcquisitionResult acquisitionResult;
    private ProtectedRunGuardRepository.ExposureResult exposureResult;
    private ProtectedRunGuardRepository.CompletionResult completionResult;

    private ProtectedRunGuardRepository.RecoveryResult recoveryResult;

    private UUID abandonedApplicationInstanceId;

    private UUID startupRecoveryInstanceId;

    private boolean acquisitionLedgerFailureObserved;

    private boolean governanceIntegrityMismatchPrepared;

    /*
     * Step 4E fail-closed result exposure state.
     */
    private ProtectedResultExposureService resultExposureService;

    private ProtectedResultExposureService.ResultExposure<String>
            protectedResultExposure;

    private boolean protectedComputationInvoked;

    private String protectedResultPayload;

    private String capturedResultBearingOutput;

    /*
     * Step 4F protected-run logging policy state.
     */
    private ProtectedRunOperationalLogger operationalLogger;

    private List<String> operationalLogMessages;

    private IllegalStateException resultBearingLoggingFailure;

    /*
     * Final exposure-state-machine database invariant state.
     */
    private DataAccessException illegalTransitionFailure;

    private DataAccessException guardDeletionFailure;

    private ProtectedRunGuardRepository.AcquisitionResult
            concurrentAcquisitionOne;

    private ProtectedRunGuardRepository.AcquisitionResult
            concurrentAcquisitionTwo;

    private int protectedMarketDataReadCount;

    private String refusalReason;

    /*
     * Step 4G deterministic EXPOSED recovery state.
     */
    private ProtectedRunGuardRepository.DeterministicRecoveryResult
            deterministicRecoveryResult;

    private UUID deterministicRecoveryRunId;

    private String deterministicRecoveryConfigurationHash;

    private String deterministicRecoveryMarketDataFingerprint;

    private int deterministicRecoveryLedgerCountBefore;

    /*
     * Step 4A/4B runtime-governance state.
     */
    private ProtectedMarketDataProvider
            protectedRunMarketDataProvider;

    private boolean protectedRunRefused;

    private ApplicationInstanceIdentity
            applicationInstance;

    private UUID previousApplicationInstanceId;

    private UUID restartedApplicationInstanceId;

    private GovernedModeAdvisoryLock
            firstGovernedModeLock;

    private GovernedModeAdvisoryLock
            secondGovernedModeLock;

    private GovernedModeAdvisoryLock.LockResult
            secondGovernedModeLockResult;

    @Before
    public void setUpProtectedRunGovernance() {

        Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                )
                .locations(
                        "classpath:db/migration"
                )
                .load()
                .migrate();

        DataSource dataSource =
                createDataSource();

        jdbcTemplate =
                new JdbcTemplate(
                        dataSource
                );

        repository =
                new ProtectedRunGuardRepository(
                        jdbcTemplate,
                        new DataSourceTransactionManager(
                                dataSource
                        )
                );

        resultExposureService =
                new ProtectedResultExposureService(
                        repository
                );

        runId =
                UUID.randomUUID();

        instanceId =
                UUID.randomUUID();

        configurationHash =
                "cucumber-configuration-hash";

        marketDataFingerprint =
                "cucumber-market-data-fingerprint";

        refusalReason = null;

        acquisitionResult = null;
        exposureResult = null;
        completionResult = null;

        concurrentAcquisitionOne = null;
        concurrentAcquisitionTwo = null;

        protectedMarketDataReadCount = 0;

        deterministicRecoveryResult = null;
        deterministicRecoveryRunId = null;
        deterministicRecoveryConfigurationHash = null;
        deterministicRecoveryMarketDataFingerprint = null;
        deterministicRecoveryLedgerCountBefore = 0;

        protectedRunMarketDataProvider = null;
        protectedRunRefused = false;

        applicationInstance = null;
        previousApplicationInstanceId = null;
        restartedApplicationInstanceId = null;

        firstGovernedModeLock = null;
        secondGovernedModeLock = null;
        secondGovernedModeLockResult = null;

        recoveryResult = null;
        abandonedApplicationInstanceId = null;
        startupRecoveryInstanceId = null;
        acquisitionLedgerFailureObserved = false;
        governanceIntegrityMismatchPrepared = false;

        protectedResultExposure = null;
        protectedComputationInvoked = false;
        protectedResultPayload = null;
        capturedResultBearingOutput = "";

        operationalLogger = null;
        operationalLogMessages = null;
        resultBearingLoggingFailure = null;

        illegalTransitionFailure = null;
        guardDeletionFailure = null;
    }

    @After
    public void releaseGovernedModeLocks() {

        if (secondGovernedModeLock != null
                && secondGovernedModeLock.isHeld()) {

            secondGovernedModeLock.release();
        }

        if (firstGovernedModeLock != null
                && firstGovernedModeLock.isHeld()) {

            firstGovernedModeLock.release();
        }
    }

    /*
     * ============================================================
     * RUNTIME / DURABLE-STORE GOVERNANCE
     * ============================================================
     */

    @Given("a protected validation run uses real market data")
    public void aProtectedValidationRunUsesRealMarketData() {

        protectedRunMarketDataProvider =
                new ProtectedMarketDataProvider(
                        new RefusingRealMarketDataSource(),
                        ProtectedMarketDataBoundary
                                .failClosedWithoutDurableStore()
                );

        assertEquals(
                MarketDataProviderType.REAL,
                protectedRunMarketDataProvider
                        .providerType()
        );
    }

    @Given("only in-memory governance storage is active")
    public void onlyInMemoryGovernanceStorageIsActive() {

        /*
         * There is intentionally no JDBC-backed durable
         * governance store in this request path.
         *
         * The production boundary must therefore fail closed
         * before the real provider is invoked.
         */
        assertNotNull(
                protectedRunMarketDataProvider
        );
    }

    /*
     * ============================================================
     * GUARD / LEDGER INTEGRITY MISMATCH
     * ============================================================
     */

    @Given("the guard state is {string}")
    public void theGuardStateIs(
            String expectedState) {

        createReservedTarget();

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        assertEquals(
                ProtectedRunState.valueOf(
                        expectedState
                ),
                guard.state()
        );
    }

    @Given("the latest ledger state is {string}")
    public void theLatestLedgerStateIs(
            String requestedLedgerState) {

        ProtectedRunState requestedState =
                ProtectedRunState.valueOf(
                        requestedLedgerState
                );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        ProtectedRunGuardRepository.LedgerSnapshot previousLedger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        /*
         * This scenario intentionally simulates durable-store
         * corruption.
         *
         * PostgreSQL normally prevents this mismatch through its
         * deferred guard/ledger consistency trigger. The trigger is
         * disabled only around this test fixture insertion so the
         * production repository can be tested against already-corrupt
         * durable state.
         */
        jdbcTemplate.execute(
                """
                ALTER TABLE protected_run_event_ledger
                DISABLE TRIGGER trg_ledger_matches_guard
                """
        );

        try {

            String eventHash =
                    UUID.randomUUID()
                            .toString()
                            .replace(
                                    "-",
                                    ""
                            )
                            + UUID.randomUUID()
                            .toString()
                            .replace(
                                    "-",
                                    ""
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
                                governed_verdict,
                                previous_event_hash,
                                event_hash,
                                event_timestamp
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            UUID.randomUUID(),
                            guardId,
                            null,
                            "TEST_GOVERNANCE_INTEGRITY_MISMATCH",
                            previousLedger.state().name(),
                            requestedState.name(),
                            null,
                            null,
                            null,
                            null,
                            previousLedger.eventHash(),
                            eventHash,
                            java.sql.Timestamp.from(
                                    Instant.parse(
                                            "2026-01-05T12:00:00Z"
                                    )
                            )
                    );

            assertEquals(
                    1,
                    inserted
            );

        } finally {

            jdbcTemplate.execute(
                    """
                    ALTER TABLE protected_run_event_ledger
                    ENABLE TRIGGER trg_ledger_matches_guard
                    """
            );
        }

        /*
         * Confirm the corruption fixture is exactly what the
         * scenario requires:
         *
         * guard  = RESERVED
         * ledger = EXPOSED
         */
        assertEquals(
                guard.state(),
                repository.requireGuard(
                        guardId
                ).state()
        );

        assertEquals(
                requestedState,
                repository.requireLatestLedgerSnapshot(
                        guardId
                ).state()
        );

        assertFalse(
                guard.state()
                        == requestedState
        );

        governanceIntegrityMismatchPrepared = true;
    }

    @When("the protected run is requested")
    public void theProtectedRunIsRequested() {

        /*
         * Integrity-mismatch path.
         *
         * Acquisition is the governance gate. Protected market
         * data may be read only after successful acquisition.
         */
        if (governanceIntegrityMismatchPrepared) {

            acquisitionResult =
                    repository.acquireReservedTarget(
                            guardId,
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            "integrity-mismatch-request",
                            MarketDataProviderType.REAL,
                            Instant.parse(
                                    "2026-01-05T12:01:00Z"
                            )
                    );

            if (acquisitionResult.acquired()) {

                /*
                 * This counter represents reaching the downstream
                 * protected-data read. Integrity mismatch must make
                 * this branch impossible.
                 */
                protectedMarketDataReadCount++;

            } else {

                protectedRunRefused = true;

                refusalReason =
                        acquisitionResult.refusalReason();
            }

            return;
        }

        /*
         * Durable-store refusal path.
         */
        if (protectedRunMarketDataProvider == null) {

            throw new AssertionError(
                    "Protected run request context "
                            + "has not been configured"
            );
        }

        try {

            protectedRunMarketDataProvider
                    .getHistoricalCandles(
                            "SPY",
                            "1d",
                            Instant.parse(
                                    "2026-01-05T05:00:00Z"
                            ),
                            Instant.parse(
                                    "2026-01-06T05:00:00Z"
                            )
                    );

        } catch (ProtectedMarketDataAccessException exception) {

            protectedRunRefused = true;

            refusalReason =
                    exception.getRefusalReason();
        }
    }

    @When("a protected run is requested")
    public void aProtectedRunIsRequested() {

        theProtectedRunIsRequested();
    }

    @Then("the protected run should be refused")
    public void theProtectedRunShouldBeRefused() {

        assertTrue(
                protectedRunRefused
        );

        assertNotNull(
                refusalReason
        );
    }

    @Then("the exposure refusal reason should be {string}")
    public void theRefusalReasonShouldBe(
            String expectedReason) {

        assertEquals(
                expectedReason,
                refusalReason
        );
    }

    /*
     * ============================================================
     * APPLICATION INSTANCE IDENTITY
     * ============================================================
     */

    @Given("an application instance has started")
    public void anApplicationInstanceHasStarted() {

        applicationInstance =
                new ApplicationInstanceIdentity();

        assertNotNull(
                applicationInstance.instanceId()
        );
    }

    @Given("its instance ID has been recorded")
    public void itsInstanceIdHasBeenRecorded() {

        assertNotNull(
                applicationInstance
        );

        previousApplicationInstanceId =
                applicationInstance.instanceId();

        assertNotNull(
                previousApplicationInstanceId
        );
    }

    @When("the application is restarted")
    public void theApplicationIsRestarted() {

        applicationInstance =
                new ApplicationInstanceIdentity();

        restartedApplicationInstanceId =
                applicationInstance.instanceId();
    }

    @Then("the new instance ID should differ from the previous instance ID")
    public void theNewInstanceIdShouldDifferFromThePreviousInstanceId() {

        assertNotNull(
                previousApplicationInstanceId
        );

        assertNotNull(
                restartedApplicationInstanceId
        );

        assertFalse(
                previousApplicationInstanceId.equals(
                        restartedApplicationInstanceId
                )
        );
    }

    /*
     * ============================================================
     * GOVERNED-MODE SINGLE-INSTANCE LOCK
     * ============================================================
     */

    @Given("one application instance holds the governed-mode advisory lock")
    public void oneApplicationInstanceHoldsTheGovernedModeAdvisoryLock() {

        DataSource governedDataSource =
                createDataSource();

        firstGovernedModeLock =
                new GovernedModeAdvisoryLock(
                        governedDataSource
                );

        secondGovernedModeLock =
                new GovernedModeAdvisoryLock(
                        governedDataSource
                );

        GovernedModeAdvisoryLock.LockResult
                firstResult =
                firstGovernedModeLock.acquire();

        assertTrue(
                firstResult.acquired()
        );

        assertTrue(
                firstGovernedModeLock.isHeld()
        );
    }

    @When("another application instance requests governed mode")
    public void anotherApplicationInstanceRequestsGovernedMode() {

        assertNotNull(
                secondGovernedModeLock
        );

        secondGovernedModeLockResult =
                secondGovernedModeLock.acquire();

        if (!secondGovernedModeLockResult.acquired()) {

            refusalReason =
                    secondGovernedModeLockResult
                            .refusalReason();
        }
    }

    @Then("the second instance should be refused")
    public void theSecondInstanceShouldBeRefused() {

        assertNotNull(
                secondGovernedModeLockResult
        );

        assertFalse(
                secondGovernedModeLockResult.acquired()
        );

        assertFalse(
                secondGovernedModeLock.isHeld()
        );
    }

    @Given("a protected target is in state {string}")
    public void aProtectedTargetIsInState(
            String state) {

        createReservedTarget();

        ProtectedRunState requestedState =
                ProtectedRunState.valueOf(
                        state
                );

        if (requestedState ==
                ProtectedRunState.RESERVED) {

            return;
        }

        acquisitionResult =
                repository.acquireReservedTarget(
                        guardId,
                        runId,
                        instanceId,
                        configurationHash,
                        MarketDataProviderType.REAL,
                        Instant.parse(
                                "2026-01-01T12:01:00Z"
                        )
                );

        assertTrue(
                acquisitionResult.acquired()
        );

        if (requestedState ==
                ProtectedRunState.RUNNING_UNEXPOSED) {

            return;
        }

        exposureResult =
                repository.persistExposed(
                        guardId,
                        runId,
                        instanceId,
                        configurationHash,
                        marketDataFingerprint,
                        Instant.parse(
                                "2026-01-01T12:02:00Z"
                        )
                );

        assertTrue(
                exposureResult.exposed()
        );

        if (requestedState ==
                ProtectedRunState.EXPOSED) {

            return;
        }

        if (requestedState ==
                ProtectedRunState.SPENT) {

            completionResult =
                    repository.persistGovernedVerdict(
                            guardId,
                            runId,
                            instanceId,
                            configurationHash,
                            marketDataFingerprint,
                            "PASS",
                            Instant.parse(
                                    "2026-01-01T12:03:00Z"
                            )
                    );

            assertTrue(
                    completionResult.spent()
            );

            return;
        }

        throw new IllegalArgumentException(
                "Unsupported protected run state: "
                        + state
        );
    }

    @Given("the guard state matches the latest ledger state")
    public void theGuardStateMatchesTheLatestLedgerState() {

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        assertEquals(
                guard.state(),
                ledger.state()
        );
    }

    @When("the protected target is atomically acquired")
    public void theProtectedTargetIsAtomicallyAcquired() {

        acquisitionResult =
                repository.acquireReservedTarget(
                        guardId,
                        runId,
                        instanceId,
                        configurationHash,
                        MarketDataProviderType.REAL,
                        Instant.parse(
                                "2026-01-02T12:00:00Z"
                        )
                );

        if (!acquisitionResult.acquired()) {

            refusalReason =
                    acquisitionResult.refusalReason();
        }
    }

    @Then("the protected target state should be {string}")
    public void theProtectedTargetStateShouldBe(
            String expectedState) {

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        assertEquals(
                ProtectedRunState.valueOf(
                        expectedState
                ),
                guard.state()
        );
    }

    @Then("the acquiring instance ID should be recorded")
    public void theAcquiringInstanceIdShouldBeRecorded() {

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        assertNotNull(
                guard.instanceId()
        );

        assertEquals(
                instanceId,
                guard.instanceId()
        );
    }

    @Then("a matching {string} ledger event should exist")
    public void aMatchingLedgerEventShouldExist(
            String expectedState) {

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        assertEquals(
                ProtectedRunState.valueOf(
                        expectedState
                ),
                ledger.state()
        );
    }

    /*
     * -----------------------------------------------------------------
     * CONCURRENT ACQUISITION
     * -----------------------------------------------------------------
     *
     * Both requests are released at the same time against the same
     * RESERVED guard.
     *
     * PostgreSQL is responsible for ensuring the conditional
     * RESERVED -> RUNNING_UNEXPOSED update succeeds for exactly one
     * request.
     */
    /*
     * ============================================================
     * ATOMIC ACQUISITION ROLLBACK
     * ============================================================
     */

    @When("the ledger event cannot be persisted during acquisition")
    public void theLedgerEventCannotBePersistedDuringAcquisition() {

        installForcedRunningLedgerFailureTrigger();

        try {

            assertThrows(
                    DataAccessException.class,
                    () ->
                            repository.acquireReservedTarget(
                                    guardId,
                                    runId,
                                    instanceId,
                                    configurationHash,
                                    MarketDataProviderType.REAL,
                                    Instant.parse(
                                            "2026-01-03T12:00:00Z"
                                    )
                            )
            );

            acquisitionLedgerFailureObserved = true;

        } finally {

            removeForcedRunningLedgerFailureTrigger();
        }
    }

    @Then("the guard should remain in state {string}")
    public void theGuardShouldRemainInState(
            String expectedState) {

        assertTrue(
                acquisitionLedgerFailureObserved
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        ProtectedRunState expected =
                ProtectedRunState.valueOf(
                        expectedState
                );

        assertEquals(
                expected,
                guard.state()
        );

        assertEquals(
                expected,
                ledger.state()
        );
    }

    @Then("no {string} transition should be committed")
    public void noTransitionShouldBeCommitted(
            String forbiddenState) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                          AND new_state = ?
                        """,
                        Integer.class,
                        guardId,
                        forbiddenState
                );

        assertEquals(
                0,
                count
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        assertNull(
                guard.runId()
        );

        assertNull(
                guard.instanceId()
        );

        assertNull(
                guard.configurationHash()
        );
    }

    /*
     * ============================================================
     * STARTUP RECOVERY
     * ============================================================
     */

    @Given("the run belongs to an earlier application instance")
    public void theRunBelongsToAnEarlierApplicationInstance() {

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        assertEquals(
                ProtectedRunState.RUNNING_UNEXPOSED,
                guard.state()
        );

        abandonedApplicationInstanceId =
                guard.instanceId();

        assertNotNull(
                abandonedApplicationInstanceId
        );

        startupRecoveryInstanceId =
                UUID.randomUUID();

        assertFalse(
                abandonedApplicationInstanceId.equals(
                        startupRecoveryInstanceId
                )
        );
    }

    @Given("no protected results were exposed")
    public void noProtectedResultsWereExposed() {

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        assertEquals(
                ProtectedRunState.RUNNING_UNEXPOSED,
                guard.state()
        );

        assertNull(
                guard.marketDataFingerprint()
        );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        assertEquals(
                ProtectedRunState.RUNNING_UNEXPOSED,
                ledger.state()
        );

        assertNull(
                ledger.governedVerdict()
        );
    }

    @When("startup recovery runs")
    public void startupRecoveryRuns() {

        assertNotNull(
                startupRecoveryInstanceId
        );

        recoveryResult =
                repository.recoverStaleUnexposedRun(
                        guardId,
                        startupRecoveryInstanceId,
                        Instant.parse(
                                "2026-01-04T12:00:00Z"
                        )
                );

        assertTrue(
                recoveryResult.recovered()
        );
    }

    @Then("an {string} ledger event should be recorded")
    public void anLedgerEventShouldBeRecorded(
            String expectedEventType) {

        String actualEventType =
                jdbcTemplate.queryForObject(
                        """
                        SELECT event_type
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                        ORDER BY sequence_number DESC
                        LIMIT 1
                        """,
                        String.class,
                        guardId
                );

        assertEquals(
                expectedEventType,
                actualEventType
        );

        assertNotNull(
                recoveryResult
        );

        assertEquals(
                abandonedApplicationInstanceId,
                recoveryResult.previousInstanceId()
        );
    }

    @Then("the protected target should return to state {string}")
    public void theProtectedTargetShouldReturnToState(
            String expectedState) {

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        ProtectedRunState expected =
                ProtectedRunState.valueOf(
                        expectedState
                );

        assertEquals(
                expected,
                guard.state()
        );

        assertEquals(
                expected,
                ledger.state()
        );

        assertNull(
                guard.runId()
        );

        assertNull(
                guard.instanceId()
        );

        assertNull(
                guard.configurationHash()
        );

        assertNull(
                guard.marketDataFingerprint()
        );
    }

    @When("two validation requests attempt to acquire it concurrently")
    public void twoValidationRequestsAttemptToAcquireItConcurrently()
            throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(
                        2
                );

        CountDownLatch ready =
                new CountDownLatch(
                        2
                );

        CountDownLatch start =
                new CountDownLatch(
                        1
                );

        AtomicInteger marketDataReads =
                new AtomicInteger(
                        0
                );

        UUID firstRunId =
                UUID.randomUUID();

        UUID firstInstanceId =
                UUID.randomUUID();

        UUID secondRunId =
                UUID.randomUUID();

        UUID secondInstanceId =
                UUID.randomUUID();

        Callable<ProtectedRunGuardRepository.AcquisitionResult>
                firstRequest =
                () -> acquireConcurrently(
                        firstRunId,
                        firstInstanceId,
                        "concurrent-configuration-one",
                        ready,
                        start,
                        marketDataReads
                );

        Callable<ProtectedRunGuardRepository.AcquisitionResult>
                secondRequest =
                () -> acquireConcurrently(
                        secondRunId,
                        secondInstanceId,
                        "concurrent-configuration-two",
                        ready,
                        start,
                        marketDataReads
                );

        try {

            Future<ProtectedRunGuardRepository.AcquisitionResult>
                    firstFuture =
                    executor.submit(
                            firstRequest
                    );

            Future<ProtectedRunGuardRepository.AcquisitionResult>
                    secondFuture =
                    executor.submit(
                            secondRequest
                    );

            boolean bothReady =
                    ready.await(
                            10,
                            TimeUnit.SECONDS
                    );

            assertTrue(
                    bothReady,
                    "Both concurrent requests should reach the acquisition boundary"
            );

            start.countDown();

            concurrentAcquisitionOne =
                    firstFuture.get(
                            30,
                            TimeUnit.SECONDS
                    );

            concurrentAcquisitionTwo =
                    secondFuture.get(
                            30,
                            TimeUnit.SECONDS
                    );

            protectedMarketDataReadCount =
                    marketDataReads.get();

        } finally {

            executor.shutdownNow();
        }
    }

    @Then("exactly one request should acquire the target")
    public void exactlyOneRequestShouldAcquireTheTarget() {

        assertNotNull(
                concurrentAcquisitionOne
        );

        assertNotNull(
                concurrentAcquisitionTwo
        );

        boolean firstAcquired =
                concurrentAcquisitionOne.acquired();

        boolean secondAcquired =
                concurrentAcquisitionTwo.acquired();

        assertTrue(
                firstAcquired ^ secondAcquired,
                "Exactly one concurrent request must acquire the protected target"
        );

        assertEquals(
                ProtectedRunState.RUNNING_UNEXPOSED,
                repository.requireGuard(
                        guardId
                ).state()
        );
    }

    @Then("the other request should be refused")
    public void theOtherRequestShouldBeRefused() {

        int refusalCount = 0;

        if (!concurrentAcquisitionOne.acquired()) {
            refusalCount++;
        }

        if (!concurrentAcquisitionTwo.acquired()) {
            refusalCount++;
        }

        assertEquals(
                1,
                refusalCount,
                "Exactly one concurrent request should be refused"
        );
    }

    @Then("protected market data should be read by only the successful request")
    public void protectedMarketDataShouldBeReadByOnlyTheSuccessfulRequest() {

        assertEquals(
                1,
                protectedMarketDataReadCount,
                "Only the request that acquired the protected target may proceed to the protected-data read"
        );
    }

    @Then("protected market data should not be read")
    public void protectedMarketDataShouldNotBeRead() {

        assertTrue(
                governanceIntegrityMismatchPrepared
        );

        assertEquals(
                0,
                protectedMarketDataReadCount,
                "Governance integrity mismatch must refuse the run "
                        + "before protected market data is read"
        );

        assertNotNull(
                acquisitionResult
        );

        assertFalse(
                acquisitionResult.acquired()
        );
    }

    @When("another validation attempt is requested")
    public void anotherValidationAttemptIsRequested() {

        acquisitionResult =
                repository.acquireReservedTarget(
                        guardId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "second-attempt-configuration",
                        MarketDataProviderType.REAL,
                        Instant.parse(
                                "2026-01-03T12:00:00Z"
                        )
                );

        if (!acquisitionResult.acquired()) {

            refusalReason =
                    acquisitionResult.refusalReason();
        }
    }

    @Then("the new attempt should be refused")
    public void theNewAttemptShouldBeRefused() {

        assertNotNull(
                acquisitionResult
        );

        assertFalse(
                acquisitionResult.acquired()
        );
    }

    @Then("the protected target should remain in state {string}")
    public void theProtectedTargetShouldRemainInState(
            String expectedState) {

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        assertEquals(
                ProtectedRunState.valueOf(
                        expectedState
                ),
                guard.state()
        );
    }

    @Given("a protected run is in state {string}")
    public void aProtectedRunIsInState(
            String state) {

        aProtectedTargetIsInState(
                state
        );
    }

    /*
     * ============================================================
     * FAIL-CLOSED RESULT EXPOSURE
     * ============================================================
     */

    @Given("protected results have been computed internally")
    public void protectedResultsHaveBeenComputedInternally() {

        /*
         * The protected payload is deliberately kept inside the
         * scenario's internal computation fixture. It will be
         * supplied to ProtectedResultExposureService and must not
         * become caller-visible until EXPOSED is durable.
         */
        assertEquals(
                ProtectedRunState.RUNNING_UNEXPOSED,
                repository.requireGuard(
                        guardId
                ).state()
        );

        assertEquals(
                ProtectedRunState.RUNNING_UNEXPOSED,
                repository.requireLatestLedgerSnapshot(
                        guardId
                ).state()
        );

        protectedResultPayload =
                "trade=PROTECTED_TRADE;"
                        + "pnl=125.50;"
                        + "expectancy=0.42;"
                        + "drawdown=0.08;"
                        + "verdict=PASS";

        protectedComputationInvoked = false;
        protectedResultExposure = null;
        capturedResultBearingOutput = "";
    }

    @When("the system prepares to expose result-bearing information")
    public void theSystemPreparesToExposeResultBearingInformation() {

        assertNotNull(
                resultExposureService
        );

        assertNotNull(
                protectedResultPayload
        );

        protectedResultExposure =
                resultExposureService.computeAndExpose(
                        guardId,
                        runId,
                        instanceId,
                        configurationHash,
                        marketDataFingerprint,
                        () -> {

                            /*
                             * The protected computation is still
                             * unexposed at the instant its value is
                             * produced.
                             */
                            assertEquals(
                                    ProtectedRunState.RUNNING_UNEXPOSED,
                                    repository.requireGuard(
                                            guardId
                                    ).state()
                            );

                            protectedComputationInvoked = true;

                            return protectedResultPayload;
                        },
                        Instant.parse(
                                "2026-01-06T12:00:00Z"
                        ),
                        Instant.parse(
                                "2026-01-06T12:01:00Z"
                        )
                );
    }

    @Then("state {string} should be persisted first")
    public void stateShouldBePersistedFirst(
            String expectedState) {

        assertTrue(
                protectedComputationInvoked
        );

        assertNotNull(
                protectedResultExposure
        );

        ProtectedRunState expected =
                ProtectedRunState.valueOf(
                        expectedState
                );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        assertEquals(
                expected,
                guard.state()
        );

        assertEquals(
                expected,
                ledger.state()
        );

        assertEquals(
                marketDataFingerprint,
                guard.marketDataFingerprint()
        );

        /*
         * The service cannot return a visible result until the
         * durable EXPOSED transaction has completed.
         */
        assertTrue(
                protectedResultExposure.resultsVisible()
        );
    }

    @Then("only after that should protected results become visible")
    public void onlyAfterThatShouldProtectedResultsBecomeVisible() {

        assertNotNull(
                protectedResultExposure
        );

        assertTrue(
                protectedResultExposure.resultsVisible()
        );

        assertFalse(
                protectedResultExposure.resultsDiscarded()
        );

        assertFalse(
                protectedResultExposure.runAborted()
        );

        assertEquals(
                protectedResultPayload,
                protectedResultExposure.result()
        );

        assertEquals(
                ProtectedRunState.EXPOSED,
                repository.requireGuard(
                        guardId
                ).state()
        );
    }

    @When("persisting state {string} fails")
    public void persistingStateFails(
            String requestedState) {

        assertEquals(
                "EXPOSED",
                requestedState
        );

        assertNotNull(
                protectedResultPayload
        );

        installForcedExposedLedgerFailureTrigger();

        /*
         * Capture direct console output during the fail-closed path.
         *
         * The separate result-bearing logging scenario will test
         * the broader operational logging policy. Here we prove that
         * this production exposure service does not emit the protected
         * payload while EXPOSED persistence is failing.
         */
        java.io.PrintStream originalOut =
                System.out;

        java.io.PrintStream originalErr =
                System.err;

        java.io.ByteArrayOutputStream captured =
                new java.io.ByteArrayOutputStream();

        java.io.PrintStream captureStream =
                new java.io.PrintStream(
                        captured,
                        true
                );

        try {

            synchronized (
                    ProtectedRunExposureStepDefinitions.class
            ) {

                System.setOut(
                        captureStream
                );

                System.setErr(
                        captureStream
                );

                try {

                    protectedResultExposure =
                            resultExposureService.computeAndExpose(
                                    guardId,
                                    runId,
                                    instanceId,
                                    configurationHash,
                                    marketDataFingerprint,
                                    () -> {

                                        assertEquals(
                                                ProtectedRunState.RUNNING_UNEXPOSED,
                                                repository.requireGuard(
                                                        guardId
                                                ).state()
                                        );

                                        protectedComputationInvoked = true;

                                        return protectedResultPayload;
                                    },
                                    Instant.parse(
                                            "2026-01-07T12:00:00Z"
                                    ),
                                    Instant.parse(
                                            "2026-01-07T12:01:00Z"
                                    )
                            );

                } finally {

                    System.setOut(
                            originalOut
                    );

                    System.setErr(
                            originalErr
                    );
                }
            }

        } finally {

            captureStream.close();

            capturedResultBearingOutput =
                    captured.toString(
                            java.nio.charset.StandardCharsets.UTF_8
                    );

            removeForcedExposedLedgerFailureTrigger();
        }
    }

    @Then("the computed protected results should be discarded")
    public void theComputedProtectedResultsShouldBeDiscarded() {

        assertTrue(
                protectedComputationInvoked
        );

        assertNotNull(
                protectedResultExposure
        );

        assertTrue(
                protectedResultExposure.resultsDiscarded()
        );

        assertFalse(
                protectedResultExposure.resultsVisible()
        );

        assertNull(
                protectedResultExposure.result()
        );
    }

    @Then("no result-bearing information should be logged")
    public void noResultBearingInformationShouldBeLogged() {

        assertNotNull(
                capturedResultBearingOutput
        );

        assertFalse(
                capturedResultBearingOutput.contains(
                        "PROTECTED_TRADE"
                )
        );

        assertFalse(
                capturedResultBearingOutput.contains(
                        "125.50"
                )
        );

        assertFalse(
                capturedResultBearingOutput.contains(
                        "0.42"
                )
        );

        assertFalse(
                capturedResultBearingOutput.contains(
                        "0.08"
                )
        );

        assertFalse(
                capturedResultBearingOutput.contains(
                        "verdict=PASS"
                )
        );
    }

    @Then("no protected results should be returned")
    public void noProtectedResultsShouldBeReturned() {

        assertNotNull(
                protectedResultExposure
        );

        assertFalse(
                protectedResultExposure.resultsVisible()
        );

        assertNull(
                protectedResultExposure.result()
        );
    }

    @Then("the run should be recorded as aborted")
    public void theRunShouldBeRecordedAsAborted() {

        assertNotNull(
                protectedResultExposure
        );

        assertTrue(
                protectedResultExposure.runAborted()
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        /*
         * Failed EXPOSED persistence rolls back first.
         * abortUnexposedRun(...) then durably returns the
         * protected target to RESERVED.
         */
        assertEquals(
                ProtectedRunState.RESERVED,
                guard.state()
        );

        assertEquals(
                ProtectedRunState.RESERVED,
                ledger.state()
        );

        assertNull(
                guard.runId()
        );

        assertNull(
                guard.instanceId()
        );

        assertNull(
                guard.configurationHash()
        );

        assertNull(
                guard.marketDataFingerprint()
        );

        String latestEventType =
                jdbcTemplate.queryForObject(
                        """
                        SELECT event_type
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                        ORDER BY sequence_number DESC
                        LIMIT 1
                        """,
                        String.class,
                        guardId
                );

        assertEquals(
                "ABORTED_RUN",
                latestEventType
        );

        Integer exposedEventCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                          AND new_state = 'EXPOSED'
                        """,
                        Integer.class,
                        guardId
                );

        assertEquals(
                0,
                exposedEventCount
        );
    }

    /*
     * ============================================================
     * RESULT-BEARING LOGS FORBIDDEN BEFORE EXPOSURE
     * ============================================================
     */

    @When("operational progress is logged")
    public void operationalProgressIsLogged() {

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        assertEquals(
                ProtectedRunState.RUNNING_UNEXPOSED,
                guard.state()
        );

        operationalLogMessages =
                new java.util.ArrayList<>();

        operationalLogger =
                ProtectedRunOperationalLogger.forSink(
                        operationalLogMessages::add
                );

        /*
         * Ordinary progress is permitted while the run is still
         * RUNNING_UNEXPOSED.
         */
        operationalLogger.logProgress(
                runId,
                guard.state(),
                "Loading protected validation bars"
        );

        /*
         * Attempt to push the complete protected result-bearing
         * payload through the same production logging boundary.
         *
         * This must fail before the sink receives anything.
         */
        ProtectedRunOperationalLogger.ResultBearingInformation
                resultBearingInformation =
                new ProtectedRunOperationalLogger
                        .ResultBearingInformation(
                        "BUY SPY -> SELL SPY",
                        new java.math.BigDecimal(
                                "125.50"
                        ),
                        new java.math.BigDecimal(
                                "0.42"
                        ),
                        new java.math.BigDecimal(
                                "0.08"
                        ),
                        "PASS"
                );

        resultBearingLoggingFailure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                operationalLogger
                                        .logResultBearingInformation(
                                                runId,
                                                guard.state(),
                                                resultBearingInformation
                                        )
                );

        assertEquals(
                ProtectedRunOperationalLogger
                        .RESULT_BEARING_LOG_FORBIDDEN_BEFORE_EXPOSURE,
                resultBearingLoggingFailure.getMessage()
        );
    }

    @Then("run identifiers and non-result progress may be logged")
    public void runIdentifiersAndNonResultProgressMayBeLogged() {

        assertNotNull(
                operationalLogMessages
        );

        assertEquals(
                1,
                operationalLogMessages.size()
        );

        String message =
                operationalLogMessages.get(
                        0
                );

        assertTrue(
                message.contains(
                        runId.toString()
                )
        );

        assertTrue(
                message.contains(
                        "state=RUNNING_UNEXPOSED"
                )
        );

        assertTrue(
                message.contains(
                        "Loading protected validation bars"
                )
        );
    }

    @Then("trades should not be logged")
    public void tradesShouldNotBeLogged() {

        assertResultBearingLoggingWasBlocked();

        assertNoOperationalLogContains(
                "trades="
        );

        assertNoOperationalLogContains(
                "BUY SPY -> SELL SPY"
        );
    }

    @Then("profit and loss should not be logged")
    public void profitAndLossShouldNotBeLogged() {

        assertResultBearingLoggingWasBlocked();

        assertNoOperationalLogContains(
                "pnl="
        );

        assertNoOperationalLogContains(
                "125.50"
        );
    }

    @Then("expectancy should not be logged")
    public void expectancyShouldNotBeLogged() {

        assertResultBearingLoggingWasBlocked();

        assertNoOperationalLogContains(
                "expectancy="
        );

        assertNoOperationalLogContains(
                "0.42"
        );
    }

    @Then("drawdown should not be logged")
    public void drawdownShouldNotBeLogged() {

        assertResultBearingLoggingWasBlocked();

        assertNoOperationalLogContains(
                "drawdown="
        );

        assertNoOperationalLogContains(
                "0.08"
        );
    }

    @Then("governed PASS or FAIL should not be logged")
    public void governedPassOrFailShouldNotBeLogged() {

        assertResultBearingLoggingWasBlocked();

        assertNoOperationalLogContains(
                "verdict="
        );

        assertNoOperationalLogContains(
                "verdict=PASS"
        );

        assertNoOperationalLogContains(
                "verdict=FAIL"
        );
    }

    /*
     * ============================================================
     * EXPOSED INCOMPLETE DETERMINISTIC RECOVERY
     * ============================================================
     */

    @Given("its verdict was not persisted")
    public void itsVerdictWasNotPersisted() {

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        assertEquals(
                ProtectedRunState.EXPOSED,
                guard.state()
        );

        assertEquals(
                ProtectedRunState.EXPOSED,
                ledger.state()
        );

        assertNull(
                ledger.governedVerdict()
        );

        assertEquals(
                runId,
                guard.runId()
        );

        assertEquals(
                configurationHash,
                guard.configurationHash()
        );

        assertEquals(
                marketDataFingerprint,
                guard.marketDataFingerprint()
        );
    }

    @Given("the recovery uses the same run ID")
    public void theRecoveryUsesTheSameRunId() {

        deterministicRecoveryRunId =
                runId;
    }

    @Given("the recovery uses the same configuration hash")
    public void theRecoveryUsesTheSameConfigurationHash() {

        deterministicRecoveryConfigurationHash =
                configurationHash;
    }

    @Given("the recovery uses the same market-data fingerprint")
    public void theRecoveryUsesTheSameMarketDataFingerprint() {

        deterministicRecoveryMarketDataFingerprint =
                marketDataFingerprint;
    }

    @Given("the recovery run ID match is {word}")
    public void theRecoveryRunIdMatchIs(
            String match) {

        boolean matches =
                Boolean.parseBoolean(
                        match
                );

        deterministicRecoveryRunId =
                matches
                        ? runId
                        : UUID.randomUUID();

        if (!matches) {

            assertFalse(
                    deterministicRecoveryRunId.equals(
                            runId
                    )
            );
        }
    }

    @Given("the recovery configuration hash match is {word}")
    public void theRecoveryConfigurationHashMatchIs(
            String match) {

        boolean matches =
                Boolean.parseBoolean(
                        match
                );

        deterministicRecoveryConfigurationHash =
                matches
                        ? configurationHash
                        : configurationHash
                        + "-changed";
    }

    @Given("the recovery market-data fingerprint match is {word}")
    public void theRecoveryMarketDataFingerprintMatchIs(
            String match) {

        boolean matches =
                Boolean.parseBoolean(
                        match
                );

        deterministicRecoveryMarketDataFingerprint =
                matches
                        ? marketDataFingerprint
                        : marketDataFingerprint
                        + "-changed";
    }

    @When("deterministic recovery is requested")
    public void deterministicRecoveryIsRequested() {

        assertNotNull(
                deterministicRecoveryRunId
        );

        assertNotNull(
                deterministicRecoveryConfigurationHash
        );

        assertNotNull(
                deterministicRecoveryMarketDataFingerprint
        );

        deterministicRecoveryLedgerCountBefore =
                countProtectedRunLedgerEvents();

        deterministicRecoveryResult =
                repository.authorizeDeterministicRecovery(
                        guardId,
                        deterministicRecoveryRunId,
                        deterministicRecoveryConfigurationHash,
                        deterministicRecoveryMarketDataFingerprint
                );
    }

    @Then("recovery should be allowed for the existing run")
    public void recoveryShouldBeAllowedForTheExistingRun() {

        assertNotNull(
                deterministicRecoveryResult
        );

        assertTrue(
                deterministicRecoveryResult.recoveryAllowed()
        );

        assertEquals(
                guardId,
                deterministicRecoveryResult.guardId()
        );

        assertEquals(
                runId,
                deterministicRecoveryResult.runId()
        );

        assertNull(
                deterministicRecoveryResult.refusalReason()
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        assertEquals(
                ProtectedRunState.EXPOSED,
                guard.state()
        );

        assertEquals(
                runId,
                guard.runId()
        );
    }

    @Then("no new protected attempt should be created")
    public void noNewProtectedAttemptShouldBeCreated() {

        assertNotNull(
                deterministicRecoveryResult
        );

        assertTrue(
                deterministicRecoveryResult.recoveryAllowed()
        );

        assertEquals(
                deterministicRecoveryLedgerCountBefore,
                countProtectedRunLedgerEvents()
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        assertEquals(
                ProtectedRunState.EXPOSED,
                guard.state()
        );

        assertEquals(
                ProtectedRunState.EXPOSED,
                ledger.state()
        );

        assertEquals(
                runId,
                guard.runId()
        );

        assertNull(
                ledger.governedVerdict()
        );
    }

    @Then("recovery should be refused")
    public void recoveryShouldBeRefused() {

        assertNotNull(
                deterministicRecoveryResult
        );

        assertFalse(
                deterministicRecoveryResult.recoveryAllowed()
        );

        assertNotNull(
                deterministicRecoveryResult.refusalReason()
        );

        assertNull(
                deterministicRecoveryResult.runId()
        );

        /*
         * A refused deterministic recovery is authorization-only.
         * It must not mutate the existing protected attempt.
         */
        assertEquals(
                deterministicRecoveryLedgerCountBefore,
                countProtectedRunLedgerEvents()
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        assertEquals(
                ProtectedRunState.EXPOSED,
                guard.state()
        );

        assertEquals(
                ProtectedRunState.EXPOSED,
                ledger.state()
        );

        assertEquals(
                runId,
                guard.runId()
        );

        assertEquals(
                configurationHash,
                guard.configurationHash()
        );

        assertEquals(
                marketDataFingerprint,
                guard.marketDataFingerprint()
        );

        assertNull(
                ledger.governedVerdict()
        );
    }

    /*
     * ============================================================
     * FINAL DATABASE INVARIANTS
     * ============================================================
     */

    @When("a transition to {string} is attempted")
    public void aTransitionToIsAttempted(
            String requestedState) {

        ProtectedRunGuardRepository.GuardSnapshot before =
                repository.requireGuard(
                        guardId
                );

        assertEquals(
                ProtectedRunState.SPENT,
                before.state()
        );

        illegalTransitionFailure =
                assertThrows(
                        DataAccessException.class,
                        () ->
                                jdbcTemplate.update(
                                        """
                                        UPDATE protected_run_guard
                                        SET state = ?
                                        WHERE id = ?
                                        """,
                                        requestedState,
                                        guardId
                                )
                );
    }

    @Then("the transition should be refused")
    public void theTransitionShouldBeRefused() {

        assertNotNull(
                illegalTransitionFailure
        );

        /*
         * PostgreSQL rejected SPENT -> RESERVED before any
         * durable state change could occur.
         */
        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                repository.requireLatestLedgerSnapshot(
                        guardId
                );

        assertEquals(
                ProtectedRunState.SPENT,
                guard.state()
        );

        assertEquals(
                ProtectedRunState.SPENT,
                ledger.state()
        );
    }

    @Given("a protected target guard exists")
    public void aProtectedTargetGuardExists() {

        createReservedTarget();

        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                guard.state()
        );

        assertEquals(
                1,
                countProtectedRunLedgerEvents()
        );
    }

    @When("deletion of the guard is attempted")
    public void deletionOfTheGuardIsAttempted() {

        guardDeletionFailure =
                assertThrows(
                        DataAccessException.class,
                        () ->
                                jdbcTemplate.update(
                                        """
                                        DELETE FROM protected_run_guard
                                        WHERE id = ?
                                        """,
                                        guardId
                                )
                );
    }

    @Then("the deletion should be refused")
    public void theDeletionShouldBeRefused() {

        assertNotNull(
                guardDeletionFailure
        );

        /*
         * The guard must still exist after PostgreSQL rejects
         * the DELETE.
         */
        ProtectedRunGuardRepository.GuardSnapshot guard =
                repository.requireGuard(
                        guardId
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                guard.state()
        );

        /*
         * Delete refusal must not alter the immutable ledger.
         */
        assertEquals(
                1,
                countProtectedRunLedgerEvents()
        );

        assertEquals(
                ProtectedRunState.RESERVED,
                repository.requireLatestLedgerSnapshot(
                        guardId
                ).state()
        );
    }

    @Given("its governed verdict is ready")
    public void itsGovernedVerdictIsReady() {

        assertEquals(
                ProtectedRunState.EXPOSED,
                repository.requireGuard(
                        guardId
                ).state()
        );
    }

    @When("the verdict is durably persisted")
    public void theVerdictIsDurablyPersisted() {

        completionResult =
                repository.persistGovernedVerdict(
                        guardId,
                        runId,
                        instanceId,
                        configurationHash,
                        marketDataFingerprint,
                        "PASS",
                        Instant.parse(
                                "2026-01-04T12:00:00Z"
                        )
                );
    }

    @Then("the protected target state should become {string}")
    public void theProtectedTargetStateShouldBecome(
            String expectedState) {

        assertNotNull(
                completionResult
        );

        assertTrue(
                completionResult.spent()
        );

        assertEquals(
                ProtectedRunState.valueOf(
                        expectedState
                ),
                completionResult.state()
        );

        assertEquals(
                ProtectedRunState.valueOf(
                        expectedState
                ),
                repository.requireGuard(
                        guardId
                ).state()
        );
    }

    private void assertResultBearingLoggingWasBlocked() {

        assertNotNull(
                resultBearingLoggingFailure
        );

        assertEquals(
                ProtectedRunOperationalLogger
                        .RESULT_BEARING_LOG_FORBIDDEN_BEFORE_EXPOSURE,
                resultBearingLoggingFailure.getMessage()
        );
    }

    private void assertNoOperationalLogContains(
            String forbiddenValue) {

        assertNotNull(
                operationalLogMessages
        );

        boolean found =
                operationalLogMessages.stream()
                        .anyMatch(
                                message ->
                                        message.contains(
                                                forbiddenValue
                                        )
                        );

        assertFalse(
                found,
                "Forbidden result-bearing value was logged: "
                        + forbiddenValue
        );
    }

    private int countProtectedRunLedgerEvents() {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                        """,
                        Integer.class,
                        guardId
                );

        assertNotNull(
                count
        );

        return count;
    }

    private ProtectedRunGuardRepository.AcquisitionResult
    acquireConcurrently(
            UUID requestedRunId,
            UUID requestedInstanceId,
            String requestedConfigurationHash,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger marketDataReads)
            throws Exception {

        ready.countDown();

        boolean released =
                start.await(
                        10,
                        TimeUnit.SECONDS
                );

        if (!released) {

            throw new IllegalStateException(
                    "Concurrent protected-run acquisition was not released"
            );
        }

        ProtectedRunGuardRepository.AcquisitionResult result =
                repository.acquireReservedTarget(
                        guardId,
                        requestedRunId,
                        requestedInstanceId,
                        requestedConfigurationHash,
                        MarketDataProviderType.REAL,
                        Instant.parse(
                                "2026-01-05T12:00:00Z"
                        )
                );

        /*
         * This counter is the test double for the downstream protected
         * market-data read. A refused request must never reach this
         * point.
         *
         * The separate protected-date-enforcement feature will verify
         * the actual market-data boundary itself.
         */
        if (result.acquired()) {

            marketDataReads.incrementAndGet();
        }

        return result;
    }

    private void createReservedTarget() {

        guardId =
                repository.createReservation(
                        "cucumber-strategy-v1",
                        "cucumber-validation-"
                                + UUID.randomUUID(),
                        ProtectedPeriodType.VALIDATION,
                        LocalDate.of(
                                2020,
                                1,
                                1
                        ),
                        LocalDate.of(
                                2020,
                                12,
                                31
                        ),
                        Instant.parse(
                                "2026-01-01T12:00:00Z"
                        )
                );
    }

    /*
     * A REAL provider source used to prove that durable-store
     * refusal happens before the underlying provider can read
     * market data.
     */
    private static final class RefusingRealMarketDataSource
            implements MarketDataProviderSource {

        @Override
        public List<MarketCandle> getHistoricalCandles(
                String symbol,
                int candleCount) {

            throw new AssertionError(
                    "Real market-data source must not be invoked "
                            + "without durable governance storage"
            );
        }

        @Override
        public List<MarketCandle> getHistoricalCandles(
                String symbol,
                String timeframe,
                Instant from,
                Instant to) {

            throw new AssertionError(
                    "Real market-data source must not be invoked "
                            + "without durable governance storage"
            );
        }
    }

    private void installForcedRunningLedgerFailureTrigger() {

        jdbcTemplate.execute(
                """
                DROP TRIGGER IF EXISTS
                    cucumber_fail_running_ledger_insert_trigger
                ON protected_run_event_ledger
                """
        );

        jdbcTemplate.execute(
                """
                DROP FUNCTION IF EXISTS
                    cucumber_fail_running_ledger_insert()
                """
        );

        jdbcTemplate.execute(
                """
                CREATE FUNCTION
                    cucumber_fail_running_ledger_insert()
                RETURNS TRIGGER
                LANGUAGE plpgsql
                AS
                $$
                BEGIN

                    IF NEW.new_state = 'RUNNING_UNEXPOSED' THEN

                        RAISE EXCEPTION
                            'Forced Cucumber ledger failure';

                    END IF;

                    RETURN NEW;

                END;
                $$
                """
        );

        jdbcTemplate.execute(
                """
                CREATE TRIGGER
                    cucumber_fail_running_ledger_insert_trigger
                BEFORE INSERT
                ON protected_run_event_ledger
                FOR EACH ROW
                EXECUTE FUNCTION
                    cucumber_fail_running_ledger_insert()
                """
        );
    }

    private void removeForcedRunningLedgerFailureTrigger() {

        jdbcTemplate.execute(
                """
                DROP TRIGGER IF EXISTS
                    cucumber_fail_running_ledger_insert_trigger
                ON protected_run_event_ledger
                """
        );

        jdbcTemplate.execute(
                """
                DROP FUNCTION IF EXISTS
                    cucumber_fail_running_ledger_insert()
                """
        );
    }

    private void installForcedExposedLedgerFailureTrigger() {

        removeForcedExposedLedgerFailureTrigger();

        jdbcTemplate.execute(
                """
                CREATE FUNCTION
                    cucumber_fail_exposed_ledger_insert()
                RETURNS TRIGGER
                LANGUAGE plpgsql
                AS
                $$
                BEGIN

                    IF NEW.new_state = 'EXPOSED' THEN

                        RAISE EXCEPTION
                            'Forced Cucumber EXPOSED ledger failure';

                    END IF;

                    RETURN NEW;

                END;
                $$
                """
        );

        jdbcTemplate.execute(
                """
                CREATE TRIGGER
                    cucumber_fail_exposed_ledger_insert_trigger
                BEFORE INSERT
                ON protected_run_event_ledger
                FOR EACH ROW
                EXECUTE FUNCTION
                    cucumber_fail_exposed_ledger_insert()
                """
        );
    }

    private void removeForcedExposedLedgerFailureTrigger() {

        jdbcTemplate.execute(
                """
                DROP TRIGGER IF EXISTS
                    cucumber_fail_exposed_ledger_insert_trigger
                ON protected_run_event_ledger
                """
        );

        jdbcTemplate.execute(
                """
                DROP FUNCTION IF EXISTS
                    cucumber_fail_exposed_ledger_insert()
                """
        );
    }

    private DataSource createDataSource() {

        org.springframework.jdbc.datasource.DriverManagerDataSource
                dataSource =
                new org.springframework.jdbc.datasource.DriverManagerDataSource();

        dataSource.setDriverClassName(
                "org.postgresql.Driver"
        );

        dataSource.setUrl(
                POSTGRES.getJdbcUrl()
        );

        dataSource.setUsername(
                POSTGRES.getUsername()
        );

        dataSource.setPassword(
                POSTGRES.getPassword()
        );

        return dataSource;
    }
}