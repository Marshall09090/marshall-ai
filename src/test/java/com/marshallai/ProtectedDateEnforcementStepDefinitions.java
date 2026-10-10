package com.marshallai;

import com.marshallai.market.MarketDataProviderType;
import com.marshallai.governance.exposure.ProtectedHoldoutAuthorizationRepository;
import com.marshallai.governance.exposure.ProtectedPeriodType;
import com.marshallai.governance.exposure.ProtectedRunGuardRepository;
import com.marshallai.governance.exposure.ProtectedRunState;
import com.marshallai.governance.exposure.ProtectedTuningPeriodRepository;
import com.marshallai.governance.research.CapitalSensitivityResearchService;
import com.marshallai.governance.validation.ValidationMarketDataPreparationService;
import com.marshallai.market.AlpacaMarketDataProvider;
import com.marshallai.market.AssetType;
import com.marshallai.market.MarketCandle;
import com.marshallai.market.MarketDataProvider;
import com.marshallai.market.SimulatedMarketDataProvider;
import com.marshallai.market.calendar.UsEquityTradingCalendar;
import com.marshallai.market.protection.ProtectedMarketDataAccessContext;
import com.marshallai.market.protection.ProtectedMarketDataAccessException;
import com.marshallai.market.protection.ProtectedMarketDataAuthorization;
import com.marshallai.market.protection.ProtectedMarketDataBoundary;
import com.marshallai.persistence.config.ConfigurationFingerprintService;
import com.marshallai.persistence.criteria.CriteriaRegistry;
import com.marshallai.persistence.run.RunLedger;
import com.marshallai.persistence.run.RunService;
import com.marshallai.persistence.run.RunType;
import com.marshallai.risk.PositionSizingService;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import org.flywaydb.core.Flyway;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class ProtectedDateEnforcementStepDefinitions {

    private static final AtomicInteger SCENARIO_SEQUENCE =
            new AtomicInteger();

    private static final Instant TEST_TIMESTAMP =
            Instant.parse("2026-10-07T12:00:00Z");

    private static final String TEST_SYMBOL =
            "SPY";

    private JdbcTemplate jdbcTemplate;

    private ProtectedRunGuardRepository guardRepository;

    private ProtectedHoldoutAuthorizationRepository
            holdoutAuthorizationRepository;

    private ProtectedTuningPeriodRepository
            tuningPeriodRepository;

    private ProtectedMarketDataBoundary boundary;

    private String strategyVersion;

    private LocalDate tuningStart;
    private LocalDate tuningEnd;

    private LocalDate validationStart;
    private LocalDate validationEnd;

    private LocalDate holdoutStart;
    private LocalDate holdoutEnd;

    private Long validationGuardId;
    private Long holdoutGuardId;

    private ProtectedMarketDataAuthorization
            validationAuthorization;

    private ProtectedMarketDataAuthorization
            holdoutAuthorization;

    private ProtectedHoldoutAuthorizationRepository.AuthorizationSnapshot
            holdoutAuthorizationSnapshot;

    private Instant requestedFrom;
    private Instant requestedTo;

    private String refusalReason;

    private boolean requestAttempted;
    private boolean requestAllowed;
    private boolean httpVerificationCompleted;

    private List<MarketCandle> returnedCandles;

    private int requiredWarmUpBars;
    private int suppliedTuningBarCountOverride;
    private boolean warmUpRangeOutsideTuning;

    private ValidationMarketDataPreparationService.PreparedValidationDataset
            preparedValidationDataset;

    private List<Instant> calculatedSignalTimestamps;

    private List<Instant> eligibleValidationSignalTimestamps;

    /*
     * Simulated-provider scenario state.
     */
    private SimulatedMarketDataProvider
            simulatedMarketDataProvider;

    private int simulatedValidationLedgerEventsBefore;
    private int simulatedHoldoutLedgerEventsBefore;

    /*
     * Capital-sensitivity scenario state.
     */
    private BigDecimal governedResearchBaselineCapital;

    private BigDecimal plannedControlledLiveCapital;

    private CapitalSensitivityResearchService.CapitalSensitivityResult
            capitalSensitivityResult;

    private CapitalSensitivityRecordingMarketDataProvider
            capitalSensitivityMarketDataProvider;

    private RunLedger capitalSensitivityRunLedger;

    private int capitalSensitivityValidationLedgerEventsBefore;

    private int capitalSensitivityHoldoutLedgerEventsBefore;

    /*
     * ============================================================
     * REAL POSTGRESQL TEST DATABASE
     * ============================================================
     */

    private static final class TestDatabase {

        private static final PostgreSQLContainer POSTGRES =
                new PostgreSQLContainer(
                        "postgres:16-alpine"
                );

        private static final JdbcTemplate JDBC;

        private static final DataSourceTransactionManager
                TRANSACTION_MANAGER;

        static {

            POSTGRES.start();

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

            DriverManagerDataSource dataSource =
                    new DriverManagerDataSource();

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

            JDBC =
                    new JdbcTemplate(
                            dataSource
                    );

            TRANSACTION_MANAGER =
                    new DataSourceTransactionManager(
                            dataSource
                    );
        }

        private TestDatabase() {
        }
    }

    /*
     * ============================================================
     * SCENARIO INITIALIZATION
     * ============================================================
     */

    @Before
    public void resetProtectedDateScenario() {

        ProtectedMarketDataAccessContext.clear();

        jdbcTemplate = null;
        guardRepository = null;

        holdoutAuthorizationRepository = null;
        tuningPeriodRepository = null;

        boundary = null;

        strategyVersion =
                "protected-date-bdd-"
                        + UUID.randomUUID();

        int scenarioNumber =
                SCENARIO_SEQUENCE.getAndIncrement();

        LocalDate base =
                LocalDate.of(
                        2100 + scenarioNumber * 2,
                        1,
                        1
                );

        tuningStart =
                base;

        tuningEnd =
                base.plusYears(1)
                        .minusDays(1);

        validationStart =
                base.plusYears(1);

        validationEnd =
                validationStart.plusMonths(6)
                        .minusDays(1);

        holdoutStart =
                validationEnd.plusDays(1);

        holdoutEnd =
                base.plusYears(2)
                        .minusDays(1);

        validationGuardId = null;
        holdoutGuardId = null;

        validationAuthorization = null;
        holdoutAuthorization = null;

        holdoutAuthorizationSnapshot = null;

        requestedFrom = null;
        requestedTo = null;

        refusalReason = null;

        requestAttempted = false;
        requestAllowed = false;
        httpVerificationCompleted = false;

        returnedCandles =
                List.of();

        requiredWarmUpBars = 0;
        suppliedTuningBarCountOverride = 0;
        warmUpRangeOutsideTuning = false;

        preparedValidationDataset = null;

        calculatedSignalTimestamps =
                List.of();

        eligibleValidationSignalTimestamps =
                List.of();

        simulatedMarketDataProvider = null;

        simulatedValidationLedgerEventsBefore = 0;
        simulatedHoldoutLedgerEventsBefore = 0;

        governedResearchBaselineCapital = null;
        plannedControlledLiveCapital = null;

        capitalSensitivityResult = null;

        capitalSensitivityMarketDataProvider = null;

        capitalSensitivityRunLedger = null;

        capitalSensitivityValidationLedgerEventsBefore = 0;

        capitalSensitivityHoldoutLedgerEventsBefore = 0;
    }

    @After
    public void clearProtectedDateAuthorization() {

        ProtectedMarketDataAccessContext.clear();
    }

    private void initializeDatabase() {

        if (guardRepository != null) {
            return;
        }

        jdbcTemplate =
                TestDatabase.JDBC;

        guardRepository =
                new ProtectedRunGuardRepository(
                        jdbcTemplate,
                        TestDatabase.TRANSACTION_MANAGER
                );

        holdoutAuthorizationRepository =
                new ProtectedHoldoutAuthorizationRepository(
                        jdbcTemplate,
                        TestDatabase.TRANSACTION_MANAGER
                );

        tuningPeriodRepository =
                new ProtectedTuningPeriodRepository(
                        jdbcTemplate
                );

        boundary =
                ProtectedMarketDataBoundary.forJdbcTemplate(
                        jdbcTemplate
                );
    }

    /*
     * ============================================================
     * PROTECTED PERIOD REGISTRATION
     * ============================================================
     */

    @Given("a validation period is protected")
    public void aValidationPeriodIsProtected() {

        initializeDatabase();

        if (validationGuardId != null) {
            return;
        }

        validationGuardId =
                guardRepository.createReservation(
                        strategyVersion,
                        "validation-" + UUID.randomUUID(),
                        ProtectedPeriodType.VALIDATION,
                        validationStart,
                        validationEnd,
                        TEST_TIMESTAMP
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                guardRepository.requireGuard(
                        validationGuardId
                ).state()
        );
    }

    @Given("the validation period is protected")
    public void theValidationPeriodIsProtected() {

        aValidationPeriodIsProtected();
    }

    @Given("a holdout period is protected")
    public void aHoldoutPeriodIsProtected() {

        initializeDatabase();

        if (holdoutGuardId != null) {
            return;
        }

        holdoutGuardId =
                guardRepository.createReservation(
                        strategyVersion,
                        "holdout-" + UUID.randomUUID(),
                        ProtectedPeriodType.HOLDOUT,
                        holdoutStart,
                        holdoutEnd,
                        TEST_TIMESTAMP
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                guardRepository.requireGuard(
                        holdoutGuardId
                ).state()
        );
    }

    @Given("validation and holdout periods are protected")
    public void validationAndHoldoutPeriodsAreProtected() {

        aValidationPeriodIsProtected();

        aHoldoutPeriodIsProtected();
    }

    /*
     * ============================================================
     * UNGOVERNED MARKET-DATA ACCESS
     * ============================================================
     */

    @Given("an ad hoc analysis uses real market data")
    public void anAdHocAnalysisUsesRealMarketData() {

        assertNull(
                ProtectedMarketDataAccessContext
                        .currentAuthorization()
        );
    }

    @When("the analysis requests bars overlapping the validation period")
    public void analysisRequestsValidationBars() {

        requestRealMarketData(
                validationStart,
                validationEnd,
                null,
                false
        );
    }

    @When("the analysis requests bars overlapping the holdout period")
    public void analysisRequestsHoldoutBars() {

        requestRealMarketData(
                holdoutStart,
                holdoutEnd,
                null,
                false
        );
    }

    @Then("the market-data boundary should refuse the request")
    public void marketDataBoundaryShouldRefuseRequest() {

        assertTrue(
                requestAttempted
        );

        assertFalse(
                requestAllowed
        );

        assertNotNull(
                refusalReason
        );
    }

    @Then("the refusal reason should be {string}")
    public void refusalReasonShouldBe(
            String expectedReason) {

        assertEquals(
                expectedReason,
                refusalReason
        );
    }

    @Then("no protected validation bars should be returned")
    public void noProtectedValidationBarsShouldBeReturned() {

        assertFalse(
                requestAllowed
        );

        assertTrue(
                returnedCandles.isEmpty()
        );

        assertTrue(
                httpVerificationCompleted
        );
    }

    @Then("no protected holdout bars should be returned")
    public void noProtectedHoldoutBarsShouldBeReturned() {

        assertFalse(
                requestAllowed
        );

        assertTrue(
                returnedCandles.isEmpty()
        );

        assertTrue(
                httpVerificationCompleted
        );
    }

    /*
     * ============================================================
     * DIRECT PROVIDER ACCESS
     * ============================================================
     */

    @Given("a service calls the real market-data provider directly")
    public void serviceCallsRealMarketDataProvider() {

        initializeDatabase();
    }

    @When("the service requests protected validation bars")
    public void serviceRequestsProtectedValidationBars() {

        requestRealMarketData(
                validationStart,
                validationEnd,
                null,
                false
        );
    }

    @Then("protected validation bars should not be returned")
    public void protectedValidationBarsShouldNotBeReturned() {

        assertFalse(
                requestAllowed
        );

        assertTrue(
                returnedCandles.isEmpty()
        );

        assertTrue(
                httpVerificationCompleted
        );
    }

    /*
     * ============================================================
     * GOVERNED VALIDATION
     * ============================================================
     */

    @Given("a governed validation run has acquired that protected target")
    public void governedValidationRunHasAcquiredTarget() {

        aValidationPeriodIsProtected();

        validationAuthorization =
                acquireProtectedTarget(
                        validationGuardId
                );

        assertEquals(
                ProtectedRunState.RUNNING_UNEXPOSED,
                guardRepository.requireGuard(
                        validationGuardId
                ).state()
        );
    }

    @When("the governed run requests its authorized validation bars")
    public void governedRunRequestsValidationBars() {

        requestRealMarketData(
                validationStart,
                validationEnd,
                validationAuthorization,
                true
        );
    }

    @Then("the market-data boundary should allow the request")
    public void marketDataBoundaryShouldAllowRequest() {

        assertTrue(
                requestAttempted
        );

        assertTrue(
                requestAllowed
        );

        assertNull(
                refusalReason
        );

        assertTrue(
                httpVerificationCompleted
        );
    }

    @Then("only the authorized protected period should be returned")
    public void onlyAuthorizedProtectedPeriodShouldBeReturned() {

        assertTrue(
                requestAllowed
        );

        assertEquals(
                startOfDay(
                        validationStart
                ),
                requestedFrom
        );

        assertEquals(
                endExclusive(
                        validationEnd
                ),
                requestedTo
        );

        assertReturnedCandlesInsideRange(
                validationStart,
                validationEnd
        );
    }

    /*
     * ============================================================
     * VALIDATION WARM-UP
     * ============================================================
     */

    /*
     * Period-boundary acceptance aliases.
     *
     * These deliberately reuse the governed validation
     * preparation fixture above instead of creating a second
     * implementation of validation warm-up behavior.
     */

    @Given("the validation period requires {int} warm-up bars")
    public void validationPeriodRequiresWarmUpBars(
            int warmUpBars) {

        governedValidationRequiresWarmUpBars(
                warmUpBars
        );
    }

    @Given(
            "exactly {int} completed bars are available "
                    + "immediately before validation"
    )
    public void exactlyCompletedBarsAreAvailableBeforeValidation(
            int completedBars) {

        assertTrue(
                completedBars > 0
        );

        assertEquals(
                requiredWarmUpBars,
                completedBars
        );

        suppliedTuningBarCountOverride =
                completedBars;
    }

    @Given("those bars belong to the tuning period")
    public void thoseBarsBelongToTuningPeriod() {

        warmUpBarsAreInsideTuningPeriod();
    }

    @When("the validation dataset is prepared")
    public void validationDatasetIsPreparedForPeriodBoundary() {

        validationMarketDataIsPrepared();
    }

    @Then(
            "exactly {int} warm-up bars should precede "
                    + "the validation evaluation period"
    )
    public void exactlyWarmUpBarsShouldPrecedeValidation(
            int expectedCount) {

        assertNotNull(
                preparedValidationDataset
        );

        assertEquals(
                expectedCount,
                preparedValidationDataset
                        .warmUpCandles()
                        .size()
        );

        assertEquals(
                expectedCount,
                preparedValidationDataset
                        .warmUpBarCount()
        );

        assertFalse(
                preparedValidationDataset
                        .evaluationCandles()
                        .isEmpty()
        );

        Instant evaluationStart =
                startOfDay(
                        validationStart
                );

        for (MarketCandle candle :
                preparedValidationDataset
                        .warmUpCandles()) {

            assertTrue(
                    candle.timestamp()
                            .isBefore(
                                    evaluationStart
                            ),
                    "Warm-up candle must precede validation"
            );
        }

        for (MarketCandle candle :
                preparedValidationDataset
                        .evaluationCandles()) {

            assertFalse(
                    candle.timestamp()
                            .isBefore(
                                    evaluationStart
                            ),
                    "Validation candle cannot precede validation"
            );
        }
    }

    @Then(
            "the warm-up bars should not count as validation bars"
    )
    public void warmUpBarsShouldNotCountAsValidationBars() {

        assertNotNull(
                preparedValidationDataset
        );

        List<MarketCandle> warmUpCandles =
                preparedValidationDataset
                        .warmUpCandles();

        List<MarketCandle> evaluationCandles =
                preparedValidationDataset
                        .evaluationCandles();

        assertEquals(
                requiredWarmUpBars,
                warmUpCandles.size()
        );

        assertTrue(
                warmUpCandles
                        .stream()
                        .noneMatch(
                                evaluationCandles::contains
                        )
        );

        assertEquals(
                preparedValidationDataset
                        .candles()
                        .size(),
                warmUpCandles.size()
                        + evaluationCandles.size()
        );
    }

    @Then(
            "no trade from the warm-up bars should be included "
                    + "in validation results"
    )
    public void noWarmUpTradeShouldBeIncludedInValidationResults() {

        assertNotNull(
                preparedValidationDataset
        );

        for (MarketCandle candle :
                preparedValidationDataset
                        .warmUpCandles()) {

            assertFalse(
                    preparedValidationDataset
                            .isSignalEligibleForValidation(
                                    candle.timestamp()
                            ),
                    "Warm-up candle must never be "
                            + "validation-trade eligible"
            );
        }
    }

    @Given("a governed validation run requires {int} warm-up bars")
    public void governedValidationRequiresWarmUpBars(
            int warmUpBars) {

        assertTrue(
                warmUpBars > 0
        );

        requiredWarmUpBars =
                warmUpBars;

        aValidationPeriodIsProtected();

        governedValidationRunHasAcquiredTarget();

        registerTuningPeriod();
    }

    @Given("those warm-up bars are inside the tuning period")
    public void warmUpBarsAreInsideTuningPeriod() {

        assertTrue(
                requiredWarmUpBars > 0
        );

        warmUpRangeOutsideTuning =
                false;
    }

    @Given("the requested warm-up range reaches outside the registered tuning period")
    public void warmUpRangeReachesOutsideTuningPeriod() {

        assertTrue(
                requiredWarmUpBars > 0
        );

        warmUpRangeOutsideTuning =
                true;
    }

    @When("validation market data is prepared")
    public void validationMarketDataIsPrepared() {

        initializeDatabase();

        if (warmUpRangeOutsideTuning) {

            requestWarmUpMarketData(
                    startOfDay(
                            tuningStart.minusDays(1)
                    ),
                    endExclusive(
                            tuningEnd
                    )
            );

            return;
        }

        prepareGovernedValidationDataset();
    }

    @Then("the {int} tuning-period warm-up bars should be allowed")
    public void tuningPeriodWarmUpBarsShouldBeAllowed(
            int expectedCount) {

        assertEquals(
                expectedCount,
                requiredWarmUpBars
        );

        assertTrue(
                requestAttempted
        );

        assertTrue(
                requestAllowed
        );

        assertNull(
                refusalReason
        );

        assertNotNull(
                preparedValidationDataset
        );

        assertEquals(
                expectedCount,
                preparedValidationDataset
                        .warmUpCandles()
                        .size()
        );

        assertEquals(
                expectedCount,
                preparedValidationDataset
                        .warmUpBarCount()
        );
    }

    @Then("the warm-up bars should be marked as {string}")
    public void warmUpBarsShouldBeMarkedAs(
            String expectedMarker) {

        assertEquals(
                "WARM_UP_ONLY",
                expectedMarker
        );

        assertNotNull(
                preparedValidationDataset
        );

        long count =
                preparedValidationDataset
                        .candles()
                        .stream()
                        .filter(
                                candle ->
                                        candle.usage()
                                                .name()
                                                .equals(
                                                        expectedMarker
                                                )
                        )
                        .count();

        assertEquals(
                requiredWarmUpBars,
                count
        );
    }

    @Then("the validation evaluation period should remain unchanged")
    public void validationEvaluationPeriodShouldRemainUnchanged() {

        ProtectedRunGuardRepository.GuardSnapshot guard =
                guardRepository.requireGuard(
                        validationGuardId
                );

        assertEquals(
                validationStart,
                guard.periodStart()
        );

        assertEquals(
                validationEnd,
                guard.periodEnd()
        );

        if (preparedValidationDataset != null) {

            assertEquals(
                    validationStart,
                    preparedValidationDataset
                            .validationStart()
            );

            assertEquals(
                    validationEnd,
                    preparedValidationDataset
                            .validationEnd()
            );
        }
    }

    @Given("validation includes tuning-period warm-up bars")
    public void validationIncludesWarmUpBars() {

        requiredWarmUpBars =
                200;

        aValidationPeriodIsProtected();

        governedValidationRunHasAcquiredTarget();

        registerTuningPeriod();

        warmUpRangeOutsideTuning =
                false;

        prepareGovernedValidationDataset();

        assertTrue(
                requestAllowed
        );

        assertNotNull(
                preparedValidationDataset
        );

        assertEquals(
                requiredWarmUpBars,
                preparedValidationDataset
                        .warmUpCandles()
                        .size()
        );

        assertFalse(
                preparedValidationDataset
                        .evaluationCandles()
                        .isEmpty()
        );
    }

    @When("signals are calculated across the prepared dataset")
    public void signalsAreCalculatedAcrossPreparedDataset() {

        assertNotNull(
                preparedValidationDataset
        );

        calculatedSignalTimestamps =
                preparedValidationDataset
                        .allCandles()
                        .stream()
                        .map(
                                MarketCandle::timestamp
                        )
                        .toList();

        eligibleValidationSignalTimestamps =
                calculatedSignalTimestamps
                        .stream()
                        .filter(
                                preparedValidationDataset
                                        ::isSignalEligibleForValidation
                        )
                        .toList();

        assertEquals(
                preparedValidationDataset
                        .allCandles()
                        .size(),
                calculatedSignalTimestamps.size()
        );
    }

    @Then("signals from warm-up-only bars should not create validation trades")
    public void warmUpSignalsShouldNotCreateValidationTrades() {

        assertNotNull(
                preparedValidationDataset
        );

        for (MarketCandle candle :
                preparedValidationDataset
                        .warmUpCandles()) {

            Instant timestamp =
                    candle.timestamp();

            assertFalse(
                    preparedValidationDataset
                            .isSignalEligibleForValidation(
                                    timestamp
                            )
            );

            assertFalse(
                    eligibleValidationSignalTimestamps
                            .contains(
                                    timestamp
                            )
            );
        }
    }

    @Then("only signals inside the validation evaluation period should be eligible for validation")
    public void onlyValidationSignalsShouldBeEligible() {

        assertNotNull(
                preparedValidationDataset
        );

        List<Instant> evaluationSignalTimestamps =
                preparedValidationDataset
                        .evaluationCandles()
                        .stream()
                        .map(
                                MarketCandle::timestamp
                        )
                        .toList();

        assertEquals(
                evaluationSignalTimestamps.size(),
                eligibleValidationSignalTimestamps.size()
        );

        Instant start =
                startOfDay(
                        validationStart
                );

        Instant end =
                endExclusive(
                        validationEnd
                );

        for (Instant timestamp :
                eligibleValidationSignalTimestamps) {

            assertFalse(
                    timestamp.isBefore(
                            start
                    )
            );

            assertTrue(
                    timestamp.isBefore(
                            end
                    )
            );

            assertTrue(
                    evaluationSignalTimestamps
                            .contains(
                                    timestamp
                            )
            );
        }
    }

    /*
     * ============================================================
     * HOLDOUT AUTHORIZATION
     * ============================================================
     */

    @Given("a validation run is authorized")
    public void validationRunIsAuthorized() {

        aValidationPeriodIsProtected();

        governedValidationRunHasAcquiredTarget();
    }

    @When("the validation run requests holdout market data")
    public void validationRunRequestsHoldoutData() {

        requestRealMarketData(
                holdoutStart,
                holdoutEnd,
                validationAuthorization,
                false
        );
    }

    @Given("the holdout has not been authorized")
    public void holdoutHasNotBeenAuthorized() {

        aHoldoutPeriodIsProtected();

        assertTrue(
                holdoutAuthorizationRepository
                        .findAuthorization(
                                holdoutGuardId
                        )
                        .isEmpty()
        );
    }

    @When("an explicit holdout authorization is granted")
    public void explicitHoldoutAuthorizationIsGranted() {

        grantHoldoutAuthorization();
    }

    @Then("a separate holdout authorization record should be created")
    public void separateHoldoutAuthorizationShouldBeCreated() {

        assertNotNull(
                holdoutAuthorizationSnapshot
        );

        assertTrue(
                holdoutAuthorizationRepository
                        .findAuthorization(
                                holdoutGuardId
                        )
                        .isPresent()
        );
    }

    @Then("the authorization should identify the strategy version")
    public void authorizationShouldIdentifyStrategyVersion() {

        assertEquals(
                strategyVersion,
                holdoutAuthorizationSnapshot
                        .strategyVersion()
        );
    }

    @Then("the authorization should identify the protected holdout period")
    public void authorizationShouldIdentifyHoldoutPeriod() {

        ProtectedRunGuardRepository.GuardSnapshot guard =
                guardRepository.requireGuard(
                        holdoutGuardId
                );

        assertEquals(
                guard.protectedPeriodId(),
                holdoutAuthorizationSnapshot
                        .protectedPeriodId()
        );

        assertEquals(
                guard.guardId(),
                holdoutAuthorizationSnapshot
                        .guardId()
        );
    }

    @Then("the authorization should have its own authorization timestamp")
    public void authorizationShouldHaveItsOwnTimestamp() {

        assertEquals(
                TEST_TIMESTAMP.plusSeconds(
                        60
                ),
                holdoutAuthorizationSnapshot
                        .authorizationTimestamp()
        );
    }

    @Given("an explicit holdout authorization exists")
    public void explicitHoldoutAuthorizationExists() {

        grantHoldoutAuthorization();
    }

    @Given("the governed holdout run has acquired that protected target")
    public void governedHoldoutRunHasAcquiredTarget() {

        aHoldoutPeriodIsProtected();

        holdoutAuthorization =
                acquireProtectedTarget(
                        holdoutGuardId
                );

        assertEquals(
                ProtectedRunState.RUNNING_UNEXPOSED,
                guardRepository.requireGuard(
                        holdoutGuardId
                ).state()
        );
    }

    @When("the holdout run requests its authorized market data")
    public void holdoutRunRequestsAuthorizedMarketData() {

        requestRealMarketData(
                holdoutStart,
                holdoutEnd,
                holdoutAuthorization,
                true
        );
    }

    @Then("only the authorized holdout period should be returned")
    public void onlyAuthorizedHoldoutPeriodShouldBeReturned() {

        assertTrue(
                requestAllowed
        );

        assertEquals(
                startOfDay(
                        holdoutStart
                ),
                requestedFrom
        );

        assertEquals(
                endExclusive(
                        holdoutEnd
                ),
                requestedTo
        );

        assertReturnedCandlesInsideRange(
                holdoutStart,
                holdoutEnd
        );
    }

    /*
     * ============================================================
     * SIMULATED MARKET DATA
     * ============================================================
     */

    @Given("the simulated market-data provider is active")
    public void simulatedMarketDataProviderIsActive() {

        initializeDatabase();

        assertNotNull(
                validationGuardId
        );

        assertNotNull(
                holdoutGuardId
        );

        assertEquals(
                ProtectedRunState.RESERVED,
                guardRepository.requireGuard(
                        validationGuardId
                ).state()
        );

        assertEquals(
                ProtectedRunState.RESERVED,
                guardRepository.requireGuard(
                        holdoutGuardId
                ).state()
        );

        simulatedValidationLedgerEventsBefore =
                ledgerEventCount(
                        validationGuardId
                );

        simulatedHoldoutLedgerEventsBefore =
                ledgerEventCount(
                        holdoutGuardId
                );

        simulatedMarketDataProvider =
                new SimulatedMarketDataProvider();

        assertNull(
                ProtectedMarketDataAccessContext
                        .currentAuthorization()
        );

        returnedCandles =
                List.of();

        requestAttempted =
                false;

        requestAllowed =
                false;

        refusalReason =
                null;
    }

    @When("simulated bars overlap protected dates")
    public void simulatedBarsOverlapProtectedDates() {

        assertNotNull(
                simulatedMarketDataProvider
        );

        requestedFrom =
                startOfDay(
                        validationStart
                );

        requestedTo =
                endExclusive(
                        holdoutEnd
                );

        requestAttempted =
                true;

        returnedCandles =
                simulatedMarketDataProvider
                        .getHistoricalCandles(
                                TEST_SYMBOL,
                                "1d",
                                requestedFrom,
                                requestedTo
                        );

        requestAllowed =
                true;
    }

    @Then("the simulated market-data request should be allowed")
    public void simulatedMarketDataRequestShouldBeAllowed() {

        assertTrue(
                requestAttempted
        );

        assertTrue(
                requestAllowed
        );

        assertNull(
                refusalReason
        );

        assertFalse(
                returnedCandles.isEmpty()
        );

        assertTrue(
                containsCandleDate(
                        returnedCandles,
                        validationStart
                )
        );

        assertTrue(
                containsCandleDate(
                        returnedCandles,
                        holdoutStart
                )
        );

        for (MarketCandle candle :
                returnedCandles) {

            assertFalse(
                    candle.timestamp()
                            .isBefore(
                                    requestedFrom
                            )
            );

            assertTrue(
                    candle.timestamp()
                            .isBefore(
                                    requestedTo
                            )
            );
        }

        assertNull(
                ProtectedMarketDataAccessContext
                        .currentAuthorization()
        );
    }

    @Then("no protected real-market-data exposure should be recorded")
    public void noProtectedRealMarketDataExposureShouldBeRecorded() {

        ProtectedRunGuardRepository.GuardSnapshot
                validationGuard =
                guardRepository.requireGuard(
                        validationGuardId
                );

        ProtectedRunGuardRepository.GuardSnapshot
                holdoutGuard =
                guardRepository.requireGuard(
                        holdoutGuardId
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                validationGuard.state()
        );

        assertEquals(
                ProtectedRunState.RESERVED,
                holdoutGuard.state()
        );

        assertNull(
                validationGuard.marketDataFingerprint()
        );

        assertNull(
                holdoutGuard.marketDataFingerprint()
        );

        assertEquals(
                simulatedValidationLedgerEventsBefore,
                ledgerEventCount(
                        validationGuardId
                )
        );

        assertEquals(
                simulatedHoldoutLedgerEventsBefore,
                ledgerEventCount(
                        holdoutGuardId
                )
        );

        assertNull(
                ProtectedMarketDataAccessContext
                        .currentAuthorization()
        );
    }

    /*
     * ============================================================
     * ALTERNATIVE MARKET-DATA ENTRY POINTS
     * ============================================================
     */

    @Given("real market data is requested outside the governed run path")
    public void realMarketDataRequestedOutsideGovernedPath() {

        assertNull(
                ProtectedMarketDataAccessContext
                        .currentAuthorization()
        );
    }

    @When("any market-data entry point requests overlapping validation bars")
    public void marketDataEntryPointRequestsValidationBars() {

        requestRealMarketData(
                validationStart,
                validationEnd,
                null,
                false
        );
    }

    @Then("the request should be refused before bars are returned")
    public void requestShouldBeRefusedBeforeBarsAreReturned() {

        assertTrue(
                requestAttempted
        );

        assertFalse(
                requestAllowed
        );

        assertTrue(
                returnedCandles.isEmpty()
        );

        assertTrue(
                httpVerificationCompleted
        );

        assertNotNull(
                refusalReason
        );
    }

    /*
     * ============================================================
     * CAPITAL SENSITIVITY
     * ============================================================
     */

    @Given("the governed research baseline capital is {double}")
    public void governedResearchBaselineCapitalIs(
            double amount) {

        assertTrue(
                amount > 0
        );

        governedResearchBaselineCapital =
                BigDecimal.valueOf(
                                amount
                        )
                        .setScale(
                                2
                        );
    }

    @Given("planned controlled-live capital is {double}")
    public void plannedControlledLiveCapitalIs(
            double amount) {

        assertTrue(
                amount > 0
        );

        plannedControlledLiveCapital =
                BigDecimal.valueOf(
                                amount
                        )
                        .setScale(
                                2
                        );
    }

    @Given("the tuning period is available")
    public void tuningPeriodIsAvailable() {

        registerTuningPeriod();

        /*
         * The scenario verifies that research leaves both
         * protected future partitions untouched.
         */
        aValidationPeriodIsProtected();

        aHoldoutPeriodIsProtected();
    }

    @When("capital sensitivity is evaluated")
    public void capitalSensitivityIsEvaluated() {

        initializeDatabase();

        assertNotNull(
                governedResearchBaselineCapital,
                "Research baseline capital must be configured"
        );

        assertNotNull(
                plannedControlledLiveCapital,
                "Planned controlled-live capital must be configured"
        );

        registerTuningPeriod();

        /*
         * Ensure there are real protected targets against which
         * non-exposure can be proved.
         */
        aValidationPeriodIsProtected();

        aHoldoutPeriodIsProtected();

        assertEquals(
                ProtectedRunState.RESERVED,
                guardRepository.requireGuard(
                        validationGuardId
                ).state()
        );

        assertEquals(
                ProtectedRunState.RESERVED,
                guardRepository.requireGuard(
                        holdoutGuardId
                ).state()
        );

        capitalSensitivityValidationLedgerEventsBefore =
                ledgerEventCount(
                        validationGuardId
                );

        capitalSensitivityHoldoutLedgerEventsBefore =
                ledgerEventCount(
                        holdoutGuardId
                );

        /*
         * These prices deliberately create a planned-capital
         * zero-quantity condition with the existing production
         * PositionSizingService.
         *
         * $100,000 baseline:
         * maximum 5% position exposure = $5,000.
         *
         * $10,000 planned capital:
         * maximum 5% position exposure = $500.
         *
         * Therefore:
         *
         * $400  -> planned quantity > 0
         * $1000 -> planned quantity = 0
         * $2000 -> planned quantity = 0
         *
         * Planned zero-quantity skip rate = 2 / 3.
         */
        List<MarketCandle> tuningCandles =
                List.of(
                        capitalSensitivityCandle(
                                "400.00",
                                tuningStart
                        ),
                        capitalSensitivityCandle(
                                "1000.00",
                                tuningStart.plusDays(
                                        1
                                )
                        ),
                        capitalSensitivityCandle(
                                "2000.00",
                                tuningStart.plusDays(
                                        2
                                )
                        )
                );

        capitalSensitivityMarketDataProvider =
                new CapitalSensitivityRecordingMarketDataProvider(
                        startOfDay(
                                tuningStart
                        ),
                        endExclusive(
                                tuningEnd
                        ),
                        tuningCandles
                );

        capitalSensitivityRunLedger =
                new RunLedger();

        RunService runService =
                new RunService(
                        capitalSensitivityRunLedger,
                        new CriteriaRegistry(),
                        new ConfigurationFingerprintService()
                );

        CapitalSensitivityResearchService service =
                new CapitalSensitivityResearchService(
                        capitalSensitivityMarketDataProvider,
                        tuningPeriodRepository,
                        new PositionSizingService(),
                        runService
                );

        assertNull(
                ProtectedMarketDataAccessContext
                        .currentAuthorization(),
                "Capital-sensitivity RESEARCH should not acquire "
                        + "validation or holdout authorization"
        );

        capitalSensitivityResult =
                service.evaluate(
                        strategyVersion,
                        "bdd-capital-sensitivity-v1",
                        TEST_SYMBOL,
                        "1d",
                        governedResearchBaselineCapital,
                        plannedControlledLiveCapital,
                        new BigDecimal(
                                "1.00"
                        ),
                        new BigDecimal(
                                "5.00"
                        ),
                        "BDD",
                        "TUNING_RESEARCH",
                        "NONE"
                );

        assertNotNull(
                capitalSensitivityResult
        );

        assertNull(
                ProtectedMarketDataAccessContext
                        .currentAuthorization()
        );
    }

    @Then("only tuning-period market data should be used")
    public void onlyTuningPeriodMarketDataShouldBeUsed() {

        assertNotNull(
                capitalSensitivityResult
        );

        assertNotNull(
                capitalSensitivityMarketDataProvider
        );

        assertEquals(
                1,
                capitalSensitivityMarketDataProvider
                        .requestCount(),
                "Capital sensitivity must perform exactly "
                        + "one market-data request"
        );

        assertEquals(
                startOfDay(
                        tuningStart
                ),
                capitalSensitivityMarketDataProvider
                        .requestedFrom()
        );

        assertEquals(
                endExclusive(
                        tuningEnd
                ),
                capitalSensitivityMarketDataProvider
                        .requestedTo()
        );

        assertEquals(
                tuningStart,
                capitalSensitivityResult
                        .tuningPeriodStart()
        );

        assertEquals(
                tuningEnd,
                capitalSensitivityResult
                        .tuningPeriodEnd()
        );

        assertEquals(
                3,
                capitalSensitivityResult
                        .marketDataCandleCount()
        );

        Instant allowedStart =
                startOfDay(
                        tuningStart
                );

        Instant allowedEnd =
                endExclusive(
                        tuningEnd
                );

        for (CapitalSensitivityResearchService
                .CapitalSensitivityObservation observation :
                capitalSensitivityResult.observations()) {

            assertFalse(
                    observation.timestamp()
                            .isBefore(
                                    allowedStart
                            )
            );

            assertTrue(
                    observation.timestamp()
                            .isBefore(
                                    allowedEnd
                            )
            );
        }
    }

    @Then("protected validation data should not be exposed")
    public void protectedValidationDataShouldNotBeExposed() {

        assertNotNull(
                capitalSensitivityResult
        );

        assertNotNull(
                validationGuardId
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                guardRepository.requireGuard(
                        validationGuardId
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                guard.state(),
                "Capital sensitivity must not acquire or expose validation"
        );

        assertNull(
                guard.marketDataFingerprint()
        );

        assertEquals(
                capitalSensitivityValidationLedgerEventsBefore,
                ledgerEventCount(
                        validationGuardId
                ),
                "Capital sensitivity must not append "
                        + "validation exposure events"
        );

        assertNull(
                guardRepository
                        .requireLatestLedgerSnapshot(
                                validationGuardId
                        )
                        .governedVerdict()
        );
    }

    @Then("protected holdout data should not be exposed")
    public void protectedHoldoutDataShouldNotBeExposed() {

        assertNotNull(
                capitalSensitivityResult
        );

        assertNotNull(
                holdoutGuardId
        );

        ProtectedRunGuardRepository.GuardSnapshot guard =
                guardRepository.requireGuard(
                        holdoutGuardId
                );

        assertEquals(
                ProtectedRunState.RESERVED,
                guard.state(),
                "Capital sensitivity must not acquire or expose holdout"
        );

        assertNull(
                guard.marketDataFingerprint()
        );

        assertEquals(
                capitalSensitivityHoldoutLedgerEventsBefore,
                ledgerEventCount(
                        holdoutGuardId
                ),
                "Capital sensitivity must not append "
                        + "holdout exposure events"
        );

        assertNull(
                guardRepository
                        .requireLatestLedgerSnapshot(
                                holdoutGuardId
                        )
                        .governedVerdict()
        );
    }

    @Then("zero-quantity skip rate should be recorded")
    public void zeroQuantitySkipRateShouldBeRecorded() {

        assertNotNull(
                capitalSensitivityResult
        );

        assertEquals(
                0,
                capitalSensitivityResult
                        .baselineZeroQuantityCount()
        );

        assertEquals(
                2,
                capitalSensitivityResult
                        .plannedZeroQuantityCount()
        );

        BigDecimal expectedSkipRate =
                new BigDecimal(
                        "0.66666667"
                );

        assertEquals(
                expectedSkipRate,
                capitalSensitivityResult
                        .zeroQuantitySkipRate()
        );

        Object persistedSkipRate =
                capitalSensitivityResult
                        .runRecord()
                        .configurationSnapshot()
                        .get(
                                "zeroQuantitySkipRate"
                        );

        assertEquals(
                expectedSkipRate,
                persistedSkipRate,
                "Zero-quantity skip rate must be persisted "
                        + "on the RESEARCH run"
        );

        assertEquals(
                2,
                capitalSensitivityResult
                        .runRecord()
                        .configurationSnapshot()
                        .get(
                                "plannedZeroQuantityCount"
                        )
        );
    }

    @Then("the run should be recorded as {string}")
    public void runShouldBeRecordedAs(
            String expectedRunType) {

        assertEquals(
                "RESEARCH",
                expectedRunType
        );

        assertNotNull(
                capitalSensitivityResult
        );

        assertNotNull(
                capitalSensitivityRunLedger
        );

        assertEquals(
                RunType.RESEARCH,
                capitalSensitivityResult
                        .runRecord()
                        .runType()
        );

        assertEquals(
                expectedRunType,
                capitalSensitivityResult
                        .runRecord()
                        .runType()
                        .name()
        );

        assertNull(
                capitalSensitivityResult
                        .runRecord()
                        .criteriaVersion(),
                "RESEARCH must not receive governed criteria"
        );

        assertTrue(
                capitalSensitivityResult
                        .runRecord()
                        .criteriaSnapshot()
                        .isEmpty(),
                "RESEARCH must not persist governed criteria"
        );

        assertEquals(
                1,
                capitalSensitivityRunLedger
                        .size()
        );

        assertEquals(
                capitalSensitivityResult
                        .runRecord(),
                capitalSensitivityRunLedger
                        .requireById(
                                capitalSensitivityResult
                                        .runRecord()
                                        .runId()
                        )
        );

        assertEquals(
                "CAPITAL_SENSITIVITY",
                capitalSensitivityResult
                        .runRecord()
                        .configurationSnapshot()
                        .get(
                                "researchType"
                        )
        );
    }

    @Then("no governed PASS or FAIL verdict should be produced")
    public void noGovernedPassOrFailVerdictShouldBeProduced() {

        assertNotNull(
                capitalSensitivityResult
        );

        assertNull(
                capitalSensitivityResult
                        .governedVerdict(),
                "Capital-sensitivity RESEARCH cannot emit PASS or FAIL"
        );

        assertNotEquals(
                "PASS",
                capitalSensitivityResult
                        .governedVerdict()
        );

        assertNotEquals(
                "FAIL",
                capitalSensitivityResult
                        .governedVerdict()
        );

        assertNull(
                guardRepository
                        .requireLatestLedgerSnapshot(
                                validationGuardId
                        )
                        .governedVerdict()
        );

        assertNull(
                guardRepository
                        .requireLatestLedgerSnapshot(
                                holdoutGuardId
                        )
                        .governedVerdict()
        );
    }

    @When("capital sensitivity requests validation-period market data")
    public void capitalSensitivityRequestsValidationData() {

        requestRealMarketData(
                validationStart,
                validationEnd,
                null,
                false
        );
    }

    @Then("no governed verdict should be produced")
    public void noGovernedVerdictShouldBeProduced() {

        assertNotNull(
                validationGuardId
        );

        ProtectedRunGuardRepository.LedgerSnapshot ledger =
                guardRepository.requireLatestLedgerSnapshot(
                        validationGuardId
                );

        assertNull(
                ledger.governedVerdict()
        );
    }

    /*
     * ============================================================
     * GOVERNANCE HELPERS
     * ============================================================
     */

    private ProtectedMarketDataAuthorization acquireProtectedTarget(
            long guardId) {

        initializeDatabase();

        UUID runId =
                UUID.randomUUID();

        UUID instanceId =
                UUID.randomUUID();

        String configurationHash =
                "protected-date-config-"
                        + UUID.randomUUID();

        ProtectedRunGuardRepository.AcquisitionResult result =
                guardRepository.acquireReservedTarget(
                        guardId,
                        runId,
                        instanceId,
                        configurationHash,
                        MarketDataProviderType.REAL,
                        TEST_TIMESTAMP.plusSeconds(
                                120
                        )
                );

        assertTrue(
                result.acquired()
        );

        return new ProtectedMarketDataAuthorization(
                guardId,
                runId,
                instanceId,
                configurationHash
        );
    }

    private void registerTuningPeriod() {

        initializeDatabase();

        if (tuningPeriodRepository
                .findTuningPeriod(
                        strategyVersion
                )
                .isPresent()) {

            return;
        }

        tuningPeriodRepository.registerTuningPeriod(
                strategyVersion,
                "tuning-" + UUID.randomUUID(),
                tuningStart,
                tuningEnd
        );
    }

    private void grantHoldoutAuthorization() {

        aHoldoutPeriodIsProtected();

        holdoutAuthorizationSnapshot =
                holdoutAuthorizationRepository
                        .grantExplicitAuthorization(
                                holdoutGuardId,
                                TEST_TIMESTAMP.plusSeconds(
                                        60
                                )
                        );
    }

    /*
     * ============================================================
     * REAL ALPACA PROVIDER HELPER
     * ============================================================
     */

    private void requestRealMarketData(
            LocalDate start,
            LocalDate end,
            ProtectedMarketDataAuthorization authorization,
            boolean expectedHttpCall) {

        initializeDatabase();

        Instant from =
                startOfDay(
                        start
                );

        Instant to =
                endExclusive(
                        end
                );

        requestedFrom =
                from;

        requestedTo =
                to;

        refusalReason =
                null;

        requestAttempted =
                true;

        requestAllowed =
                false;

        httpVerificationCompleted =
                false;

        returnedCandles =
                List.of();

        MockProvider mock =
                createMockProvider();

        if (expectedHttpCall) {

            Instant firstCandle =
                    startOfDay(
                            start.plusDays(
                                    1
                            )
                    );

            Instant secondCandle =
                    startOfDay(
                            start.plusDays(
                                    2
                            )
                    );

            mock.server()
                    .expect(request -> {

                        assertEquals(
                                "/v2/stocks/SPY/bars",
                                request.getURI()
                                        .getPath()
                        );

                        String query =
                                request.getURI()
                                        .getRawQuery();

                        assertNotNull(
                                query
                        );

                        assertTrue(
                                query.contains(
                                        "adjustment=split"
                                )
                        );

                        assertTrue(
                                query.contains(
                                        "feed=iex"
                                )
                        );
                    })
                    .andRespond(
                            withSuccess(
                                    barsResponse(
                                            firstCandle,
                                            secondCandle
                                    ),
                                    MediaType.APPLICATION_JSON
                            )
                    );
        }

        try {

            if (authorization == null) {

                returnedCandles =
                        mock.provider()
                                .getHistoricalCandles(
                                        TEST_SYMBOL,
                                        "1d",
                                        from,
                                        to
                                );

            } else {

                try (ProtectedMarketDataAccessContext.AuthorizationScope
                             scope =
                             ProtectedMarketDataAccessContext.authorize(
                                     authorization
                             )) {

                    returnedCandles =
                            mock.provider()
                                    .getHistoricalCandles(
                                            TEST_SYMBOL,
                                            "1d",
                                            from,
                                            to
                                    );
                }
            }

            requestAllowed =
                    true;

        } catch (ProtectedMarketDataAccessException exception) {

            refusalReason =
                    exception.getRefusalReason();

        } finally {

            mock.server()
                    .verify();

            httpVerificationCompleted =
                    true;
        }
    }

    private MockProvider createMockProvider() {

        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl(
                                "https://mock-alpaca.invalid"
                        );

        MockRestServiceServer server =
                MockRestServiceServer.bindTo(
                        builder
                ).build();

        AlpacaMarketDataProvider provider =
                new AlpacaMarketDataProvider(
                        new ObjectMapper(),
                        boundary,
                        builder.build()
                );

        return new MockProvider(
                provider,
                server
        );
    }

    private void assertReturnedCandlesInsideRange(
            LocalDate start,
            LocalDate end) {

        assertTrue(
                requestAllowed
        );

        assertTrue(
                httpVerificationCompleted
        );

        assertEquals(
                2,
                returnedCandles.size()
        );

        Instant allowedStart =
                startOfDay(
                        start
                );

        Instant allowedEnd =
                endExclusive(
                        end
                );

        for (MarketCandle candle :
                returnedCandles) {

            assertFalse(
                    candle.timestamp()
                            .isBefore(
                                    allowedStart
                            )
            );

            assertTrue(
                    candle.timestamp()
                            .isBefore(
                                    allowedEnd
                            )
            );

            assertEquals(
                    TEST_SYMBOL,
                    candle.symbol()
            );
        }
    }

    /*
     * ============================================================
     * VALIDATION PREPARATION HELPER
     * ============================================================
     */

    private void prepareGovernedValidationDataset() {

        initializeDatabase();

        assertTrue(
                requiredWarmUpBars > 0
        );

        assertNotNull(
                validationAuthorization
        );

        registerTuningPeriod();

        int suppliedTuningBarCount =
                suppliedTuningBarCountOverride > 0
                        ? suppliedTuningBarCountOverride
                        : requiredWarmUpBars + 5;

        LocalDate firstTuningCandleDate =
                tuningEnd.minusDays(
                        suppliedTuningBarCount - 1L
                );

        List<MarketCandle> tuningCandles =
                dailyCandles(
                        firstTuningCandleDate,
                        suppliedTuningBarCount
                );

        List<MarketCandle> validationCandles =
                dailyCandles(
                        validationStart,
                        5
                );

        MarketDataProvider provider =
                new PreparedValidationMarketDataProvider(
                        startOfDay(
                                tuningStart
                        ),
                        endExclusive(
                                tuningEnd
                        ),
                        tuningCandles,
                        startOfDay(
                                validationStart
                        ),
                        endExclusive(
                                validationEnd
                        ),
                        validationCandles
                );

        ValidationMarketDataPreparationService service =
                new ValidationMarketDataPreparationService(
                        provider,
                        boundary,
                        tuningPeriodRepository
                );

        refusalReason =
                null;

        requestAttempted =
                true;

        requestAllowed =
                false;

        preparedValidationDataset =
                null;

        calculatedSignalTimestamps =
                List.of();

        eligibleValidationSignalTimestamps =
                List.of();

        try {

            preparedValidationDataset =
                    service.prepare(
                            strategyVersion,
                            TEST_SYMBOL,
                            "1d",
                            requiredWarmUpBars,
                            validationStart,
                            validationEnd,
                            validationAuthorization
                    );

            requestAllowed =
                    true;

        } catch (ProtectedMarketDataAccessException exception) {

            refusalReason =
                    exception.getRefusalReason();
        }
    }

    /*
     * ============================================================
     * WARM-UP BOUNDARY HELPER
     * ============================================================
     */

    private void requestWarmUpMarketData(
            Instant from,
            Instant to) {

        initializeDatabase();

        requestedFrom =
                from;

        requestedTo =
                to;

        refusalReason =
                null;

        requestAttempted =
                true;

        requestAllowed =
                false;

        returnedCandles =
                List.of();

        try (ProtectedMarketDataAccessContext.AuthorizationScope
                     scope =
                     ProtectedMarketDataAccessContext.authorize(
                             validationAuthorization
                     )) {

            boundary.authorizeWarmUpMarketDataRequest(
                    from,
                    to
            );

            requestAllowed =
                    true;

        } catch (ProtectedMarketDataAccessException exception) {

            refusalReason =
                    exception.getRefusalReason();
        }
    }

    /*
     * ============================================================
     * LEDGER / SIMULATION HELPERS
     * ============================================================
     */

    private int ledgerEventCount(
            long guardId) {

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

    private static boolean containsCandleDate(
            List<MarketCandle> candles,
            LocalDate expectedDate) {

        return candles.stream()
                .map(
                        MarketCandle::timestamp
                )
                .map(
                        UsEquityTradingCalendar::tradingDate
                )
                .anyMatch(
                        expectedDate::equals
                );
    }

    /*
     * ============================================================
     * CAPITAL-SENSITIVITY TEST DATA
     * ============================================================
     */

    private static MarketCandle capitalSensitivityCandle(
            String closePrice,
            LocalDate date) {

        BigDecimal close =
                new BigDecimal(
                        closePrice
                );

        return new MarketCandle(
                TEST_SYMBOL,
                AssetType.STOCK,
                "1d",
                close.subtract(
                        BigDecimal.ONE
                ),
                close.add(
                        BigDecimal.ONE
                ),
                close.subtract(
                        new BigDecimal(
                                "2.00"
                        )
                ),
                close,
                new BigDecimal(
                        "100000"
                ),
                startOfDay(
                        date
                )
        );
    }

    /*
     * ============================================================
     * CAPITAL-SENSITIVITY RECORDING PROVIDER
     * ============================================================
     */

    private static final class CapitalSensitivityRecordingMarketDataProvider
            implements MarketDataProvider {

        private final Instant expectedFrom;

        private final Instant expectedTo;

        private final List<MarketCandle> candles;

        private int requestCount;

        private Instant requestedFrom;

        private Instant requestedTo;

        private CapitalSensitivityRecordingMarketDataProvider(
                Instant expectedFrom,
                Instant expectedTo,
                List<MarketCandle> candles) {

            this.expectedFrom =
                    expectedFrom;

            this.expectedTo =
                    expectedTo;

            this.candles =
                    List.copyOf(
                            candles
                    );
        }

        @Override
        public List<MarketCandle> getHistoricalCandles(
                String symbol,
                int candleCount) {

            throw new AssertionError(
                    "Capital sensitivity must not use fixed-count "
                            + "market-data access"
            );
        }

        @Override
        public List<MarketCandle> getHistoricalCandles(
                String symbol,
                String timeframe,
                Instant from,
                Instant to) {

            requestCount++;

            requestedFrom =
                    from;

            requestedTo =
                    to;

            assertEquals(
                    TEST_SYMBOL,
                    symbol
            );

            assertEquals(
                    "1d",
                    timeframe
            );

            assertEquals(
                    expectedFrom,
                    from,
                    "Capital sensitivity must begin at "
                            + "the registered tuning-period start"
            );

            assertEquals(
                    expectedTo,
                    to,
                    "Capital sensitivity must end at "
                            + "the registered tuning-period end-exclusive"
            );

            return candles;
        }

        private int requestCount() {

            return requestCount;
        }

        private Instant requestedFrom() {

            return requestedFrom;
        }

        private Instant requestedTo() {

            return requestedTo;
        }
    }

    /*
     * ============================================================
     * DETERMINISTIC VALIDATION PROVIDER
     * ============================================================
     */

    private static final class PreparedValidationMarketDataProvider
            implements MarketDataProvider {

        private final Instant tuningFrom;
        private final Instant tuningTo;

        private final List<MarketCandle>
                tuningCandles;

        private final Instant validationFrom;
        private final Instant validationTo;

        private final List<MarketCandle>
                validationCandles;

        private PreparedValidationMarketDataProvider(
                Instant tuningFrom,
                Instant tuningTo,
                List<MarketCandle> tuningCandles,
                Instant validationFrom,
                Instant validationTo,
                List<MarketCandle> validationCandles) {

            this.tuningFrom =
                    tuningFrom;

            this.tuningTo =
                    tuningTo;

            this.tuningCandles =
                    List.copyOf(
                            tuningCandles
                    );

            this.validationFrom =
                    validationFrom;

            this.validationTo =
                    validationTo;

            this.validationCandles =
                    List.copyOf(
                            validationCandles
                    );
        }

        @Override
        public List<MarketCandle> getHistoricalCandles(
                String symbol,
                int candleCount) {

            throw new UnsupportedOperationException(
                    "Fixed-count requests are not used"
            );
        }

        @Override
        public List<MarketCandle> getHistoricalCandles(
                String symbol,
                String timeframe,
                Instant from,
                Instant to) {

            assertEquals(
                    TEST_SYMBOL,
                    symbol
            );

            assertEquals(
                    "1d",
                    timeframe
            );

            if (from.equals(
                    tuningFrom
            ) && to.equals(
                    tuningTo
            )) {

                return tuningCandles;
            }

            if (from.equals(
                    validationFrom
            ) && to.equals(
                    validationTo
            )) {

                return validationCandles;
            }

            fail(
                    "Unexpected governed-validation market-data range: "
                            + from
                            + " -> "
                            + to
            );

            return List.of();
        }
    }

    /*
     * ============================================================
     * CONTROLLED CANDLE GENERATION
     * ============================================================
     */

    private static List<MarketCandle> dailyCandles(
            LocalDate firstDate,
            int count) {

        List<MarketCandle> candles =
                new ArrayList<>();

        for (int index = 0;
             index < count;
             index++) {

            BigDecimal price =
                    new BigDecimal(
                            "100.00"
                    ).add(
                            BigDecimal.valueOf(
                                    index
                            )
                    );

            candles.add(
                    new MarketCandle(
                            TEST_SYMBOL,
                            AssetType.STOCK,
                            "1d",
                            price,
                            price.add(
                                    BigDecimal.ONE
                            ),
                            price.subtract(
                                    BigDecimal.ONE
                            ),
                            price,
                            new BigDecimal(
                                    "100000"
                            ),
                            UsEquityTradingCalendar
                                    .startOfTradingDate(
                                            firstDate.plusDays(
                                                    index
                                            )
                                    )
                    )
            );
        }

        return List.copyOf(
                candles
        );
    }

    /*
     * ============================================================
     * CONTROLLED ALPACA RESPONSE
     * ============================================================
     */

    private static String barsResponse(
            Instant... timestamps) {

        StringJoiner bars =
                new StringJoiner(
                        ","
                );

        for (Instant timestamp :
                timestamps) {

            bars.add(
                    "{"
                            + "\"o\":100.00,"
                            + "\"h\":105.00,"
                            + "\"l\":98.00,"
                            + "\"c\":103.00,"
                            + "\"v\":100000,"
                            + "\"t\":\""
                            + timestamp
                            + "\""
                            + "}"
            );
        }

        return "{"
                + "\"bars\":["
                + bars
                + "],"
                + "\"next_page_token\":null"
                + "}";
    }

    /*
     * ============================================================
     * DATE HELPERS
     * ============================================================
     */

    private static Instant startOfDay(
            LocalDate date) {

        return UsEquityTradingCalendar
                .startOfTradingDate(
                        date
                );
    }

    private static Instant endExclusive(
            LocalDate date) {

        return UsEquityTradingCalendar
                .endExclusiveAfter(
                        date
                );
    }

    private record MockProvider(
            AlpacaMarketDataProvider provider,
            MockRestServiceServer server) {
    }
}