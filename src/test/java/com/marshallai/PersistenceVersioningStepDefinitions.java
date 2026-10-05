package com.marshallai;

import com.marshallai.persistence.config.ConfigurationFingerprintService;
import com.marshallai.persistence.decision.DecisionLedger;
import com.marshallai.persistence.decision.DecisionRecord;
import com.marshallai.persistence.run.RunLedger;
import com.marshallai.persistence.run.RunRecord;
import com.marshallai.persistence.run.RunService;
import com.marshallai.persistence.run.RunType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PersistenceVersioningStepDefinitions {

    @Autowired
    private RunService runService;

    @Autowired
    private RunLedger runLedger;

    @Autowired
    private DecisionLedger decisionLedger;

    @Autowired
    private ConfigurationFingerprintService configurationFingerprintService;

    private RunRecord persistedRun;
    private UUID runId;

    private String originalConfigurationVersion;
    private String changedConfigurationVersion;

    private DecisionRecord persistedDecision;

    private RuntimeException modificationException;
    private RuntimeException runStartException;

    @Given("a MarshallAI run has been persisted")
    public void aMarshallAiRunHasBeenPersisted() {

        persistedRun = createAdHocRun(
                "append-only-test-market-data"
        );

        runId = persistedRun.runId();

        assertNotNull(runId);
    }

    @When("an attempt is made to modify the persisted run")
    public void anAttemptIsMadeToModifyThePersistedRun() {

        /*
         * RunLedger is intentionally append-only.
         *
         * There is no update method. To prove that an existing run
         * cannot be replaced, attempt to append another RunRecord
         * using the same run ID but changed configuration.
         */

        RunRecord attemptedReplacement =
                new RunRecord(
                        persistedRun.runId(),
                        persistedRun.runType(),
                        persistedRun.strategyVersion(),
                        "attempted-modified-configuration-version",
                        persistedRun.buildVersion(),
                        persistedRun.criteriaVersion(),
                        persistedRun.symbol(),
                        persistedRun.timeframe(),
                        persistedRun.dataSource(),
                        persistedRun.dataFeed(),
                        persistedRun.adjustmentMethod(),
                        persistedRun.marketDataFingerprint(),
                        persistedRun.marketDataFetchedAt(),
                        Map.of(
                                "minimumCandleCount", 200,
                                "maximumPositionExposurePercent", 10
                        ),
                        persistedRun.criteriaSnapshot(),
                        Instant.now()
                );

        modificationException =
                assertThrows(
                        RuntimeException.class,
                        () -> runLedger.append(
                                attemptedReplacement
                        )
                );
    }

    @Then("the modification should be rejected")
    public void theModificationShouldBeRejected() {

        assertNotNull(modificationException);
    }

    @Then("the original persisted run should remain unchanged")
    public void theOriginalPersistedRunShouldRemainUnchanged() {

        RunRecord reloadedRun =
                runLedger.requireById(runId);

        assertEquals(
                persistedRun.configurationVersion(),
                reloadedRun.configurationVersion()
        );

        assertEquals(
                persistedRun.configurationSnapshot(),
                reloadedRun.configurationSnapshot()
        );

        assertEquals(
                persistedRun.createdAt(),
                reloadedRun.createdAt()
        );
    }

    @Given("a MarshallAI configuration has been fingerprinted")
    public void aMarshallAiConfigurationHasBeenFingerprinted() {

        originalConfigurationVersion =
                configurationFingerprintService.fingerprint(
                        Map.of(
                                "minimumCandleCount", 200,
                                "maximumPositionExposurePercent", 5,
                                "minimumTradeConfidencePercent", 80
                        )
                );

        assertNotNull(originalConfigurationVersion);
        assertFalse(originalConfigurationVersion.isBlank());
    }

    @When("a governed configuration setting is changed")
    public void aGovernedConfigurationSettingIsChanged() {

        changedConfigurationVersion =
                configurationFingerprintService.fingerprint(
                        Map.of(
                                "minimumCandleCount", 200,
                                "maximumPositionExposurePercent", 10,
                                "minimumTradeConfidencePercent", 80
                        )
                );
    }

    @Then("the configuration fingerprint should be different")
    public void theConfigurationFingerprintShouldBeDifferent() {

        assertNotEquals(
                originalConfigurationVersion,
                changedConfigurationVersion
        );
    }

    @Given("a MarshallAI run is active")
    public void aMarshallAiRunIsActive() {

        persistedRun = createAdHocRun(
                "decision-test-market-data"
        );

        runId = persistedRun.runId();

        assertNotNull(runId);
    }

    @Given("a trading decision is rejected by a safety gate")
    public void aTradingDecisionIsRejectedByASafetyGate() {

        assertNotNull(runId);
    }

    @When("the decision is persisted")
    public void theDecisionIsPersisted() {

        persistedDecision =
                decisionLedger.append(
                        new DecisionRecord(
                                UUID.randomUUID(),
                                runId,
                                "TSLA",
                                "HOLD",
                                0,
                                0,
                                0,
                                false,
                                "TRADE_APPROVAL",
                                "Trading signal is HOLD",
                                Map.of(
                                        "technicalBullishScore", 0,
                                        "technicalBearishScore", 0,
                                        "combinedConfidencePercent", 0
                                ),
                                Instant.now()
                        )
                );
    }

    @Then("the rejected decision should exist in the decision ledger")
    public void theRejectedDecisionShouldExistInTheDecisionLedger() {

        assertNotNull(persistedDecision);
        assertNotNull(persistedDecision.decisionId());

        List<DecisionRecord> decisions =
                decisionLedger.findByRunId(runId);

        assertTrue(
                decisions.stream()
                        .anyMatch(
                                decision ->
                                        decision.decisionId()
                                                .equals(
                                                        persistedDecision.decisionId()
                                                )
                        )
        );
    }

    @Then("the rejection gate should be recorded")
    public void theRejectionGateShouldBeRecorded() {

        assertEquals(
                "TRADE_APPROVAL",
                persistedDecision.rejectionGate()
        );
    }

    @Then("the rejection reason should be recorded")
    public void theRejectionReasonShouldBeRecorded() {

        assertEquals(
                "Trading signal is HOLD",
                persistedDecision.rejectionReason()
        );
    }

    @Given("no acceptance criteria are registered for a governed validation run")
    public void noAcceptanceCriteriaAreRegisteredForAGovernedValidationRun() {

        /*
         * No criteria are registered for the version used by the
         * governed run below. The run must therefore be refused.
         */
    }

    @When("MarshallAI attempts to start the run")
    public void marshallAiAttemptsToStartTheRun() {

        runStartException =
                assertThrows(
                        RuntimeException.class,
                        () -> runService.startGovernedRun(
                                RunType.BACKTEST,
                                "strategy-v1",
                                "test-build",
                                "unregistered-criteria-v1",
                                "TSLA",
                                "1D",
                                "alpaca",
                                "iex",
                                "split",
                                "test-market-data-fingerprint",
                                Instant.now(),
                                Map.of(
                                        "minimumCandleCount", 200
                                )
                        )
                );
    }

    @Then("the run should be refused")
    public void theRunShouldBeRefused() {

        assertNotNull(runStartException);
    }

    @Given("historical candles were used by a MarshallAI run")
    public void historicalCandlesWereUsedByAMarshallAiRun() {

        /*
         * Workload 01 owns historical candle retrieval.
         *
         * Workload 02 verifies that the identity of the data used by
         * a run is persisted through its fingerprint and fetch time.
         */
    }

    @When("the run is persisted")
    public void theRunIsPersisted() {

        persistedRun =
                createAdHocRun(
                        "sha256:test-candle-fingerprint"
                );
    }

    @Then("the market data fingerprint should be recorded")
    public void theMarketDataFingerprintShouldBeRecorded() {

        assertNotNull(
                persistedRun.marketDataFingerprint()
        );

        assertFalse(
                persistedRun.marketDataFingerprint()
                        .isBlank()
        );
    }

    @Then("the market data fetch timestamp should be recorded")
    public void theMarketDataFetchTimestampShouldBeRecorded() {

        assertNotNull(
                persistedRun.marketDataFetchedAt()
        );
    }

    private RunRecord createAdHocRun(
            String marketDataFingerprint) {

        return runService.startAdHocRun(
                "strategy-v1",
                "test-build",
                "TSLA",
                "1D",
                "alpaca",
                "iex",
                "split",
                marketDataFingerprint,
                Instant.now(),
                Map.of(
                        "minimumCandleCount", 200,
                        "maximumPositionExposurePercent", 5
                )
        );
    }
}