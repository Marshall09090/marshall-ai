package com.marshallai;

import com.marshallai.portfolio.governance.PortfolioExecutionCostService;
import com.marshallai.portfolio.governance.GovernedPortfolioResultService;
import com.marshallai.portfolio.governance.PortfolioDrawdownService;
import com.marshallai.trading.execution.ExitReason;
import com.marshallai.trading.execution.GovernedEvaluationPeriodService;
import com.marshallai.trading.execution.GovernedPeriodBoundaryService;
import com.marshallai.trading.execution.TradeStopService;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GovernedPeriodBoundaryStepDefinitions {

    private GovernedPeriodBoundaryService
            periodBoundaryService;

    private GovernedEvaluationPeriodService
            evaluationPeriodService;

    private GovernedPortfolioResultService
            governedPortfolioResultService;

    private List<GovernedPortfolioResultService.CompletedGovernedTrade>
            governedCompletedTrades;

    private GovernedPortfolioResultService.GovernedPortfolioResults
            governedPortfolioResults;

    private BigDecimal diagnosticExpectancyIncludingPeriodEnd;

    private BigDecimal diagnosticExpectancyExcludingPeriodEnd;

    private GovernedPortfolioResultService.PeriodBoundaryMetrics
            periodBoundaryMetrics;

    private GovernedEvaluationPeriodService.OpenTrade
            lifecycleOpenTrade;

    private GovernedEvaluationPeriodService.PeriodStart
            lifecyclePeriodStart;

    private GovernedEvaluationPeriodService.BoundaryClose
            lifecycleBoundaryClose;

    private BigDecimal validationAttributedProfitLoss;

    private String repeatedTuningDatasetFingerprint;

    private String repeatedGovernedConfigurationHash;

    private LocalDate repeatedPeriodStart;

    private LocalDate repeatedPeriodEnd;

    private GovernedEvaluationPeriodService.ResearchBoundaryResult
            firstResearchBoundaryResult;

    private GovernedEvaluationPeriodService.ResearchBoundaryResult
            secondResearchBoundaryResult;

    private boolean longPositionOpen;

    private boolean finalBarBuySignal;

    private BigDecimal finalBarOpen;

    private BigDecimal finalBarLow;

    private BigDecimal finalBarClose;

    private BigDecimal activeStop;

    private BigDecimal finalValidationClose;

    private BigDecimal firstHoldoutOpen;

    private GovernedPeriodBoundaryService.FinalBarEntryDecision
            finalBarEntryDecision;

    private GovernedPeriodBoundaryService.PeriodEndLiquidation
            periodEndLiquidation;

    private GovernedPeriodBoundaryService.FinalBarExitDecision
            finalBarExitDecision;

    private ExitReason lastExitReason;

    @Before
    public void setUpPeriodBoundaryScenario() {

        periodBoundaryService =
                new GovernedPeriodBoundaryService(
                        new TradeStopService(),
                        new PortfolioExecutionCostService()
                );

        evaluationPeriodService =
                new GovernedEvaluationPeriodService(
                        periodBoundaryService
                );

        governedPortfolioResultService =
                new GovernedPortfolioResultService(
                        new PortfolioDrawdownService()
                );

        longPositionOpen = false;
        finalBarBuySignal = false;

        finalBarOpen = null;
        finalBarLow = null;
        finalBarClose = null;
        activeStop = null;

        finalValidationClose = null;
        firstHoldoutOpen = null;

        finalBarEntryDecision = null;
        periodEndLiquidation = null;
        finalBarExitDecision = null;

        lastExitReason = null;

        lifecycleOpenTrade = null;
        lifecyclePeriodStart = null;
        lifecycleBoundaryClose = null;

        validationAttributedProfitLoss = null;

        repeatedTuningDatasetFingerprint = null;
        repeatedGovernedConfigurationHash = null;
        repeatedPeriodStart = null;
        repeatedPeriodEnd = null;

        firstResearchBoundaryResult = null;
        secondResearchBoundaryResult = null;

        governedCompletedTrades = null;
        governedPortfolioResults = null;

        diagnosticExpectancyIncludingPeriodEnd = null;
        diagnosticExpectancyExcludingPeriodEnd = null;
        periodBoundaryMetrics = null;
    }

    /*
     * ============================================================
     * FINAL BAR CANNOT CREATE A NEW ENTRY
     * ============================================================
     */

    @Given(
            "the final bar of a governed evaluation period "
                    + "produces a BUY signal"
    )
    public void finalBarProducesBuySignal() {

        finalBarBuySignal =
                true;
    }

    @When(
            "entry eligibility is evaluated for that final bar"
    )
    public void entryEligibilityIsEvaluatedForFinalBar() {

        finalBarEntryDecision =
                periodBoundaryService
                        .evaluateFinalBarEntry(
                                finalBarBuySignal
                        );
    }

    @Then("no new entry order should be created")
    public void noNewEntryOrderShouldBeCreated() {

        assertNotNull(
                finalBarEntryDecision
        );

        assertFalse(
                finalBarEntryDecision.orderCreated()
        );
    }

    @Then("the signal should be recorded as {string}")
    public void signalShouldBeRecordedAs(
            String expectedReason) {

        assertNotNull(
                finalBarEntryDecision
        );

        assertTrue(
                finalBarEntryDecision.skipped()
        );

        assertEquals(
                expectedReason,
                finalBarEntryDecision.outcomeReason()
        );

        assertEquals(
                GovernedPeriodBoundaryService
                        .SKIPPED_PERIOD_BOUNDARY,
                finalBarEntryDecision.outcomeReason()
        );
    }

    /*
     * ============================================================
     * PERIOD-END FORCE CLOSE
     * ============================================================
     */

    @Given(
            "a long position remains open on the final bar "
                    + "of a governed period"
    )
    public void longPositionRemainsOpenOnFinalBar() {

        longPositionOpen =
                true;
    }

    @Given("the final bar closes at {double}")
    public void finalBarClosesAt(
            Double price) {

        finalBarClose =
                decimal(
                        price
                );
    }

    @When("the governed period ends")
    public void governedPeriodEnds() {

        assertTrue(
                longPositionOpen
        );

        assertNotNull(
                finalBarClose
        );

        periodEndLiquidation =
                periodBoundaryService
                        .liquidateAtPeriodEnd(
                                finalBarClose
                        );

        lastExitReason =
                periodEndLiquidation
                        .exitReason();

        longPositionOpen =
                false;
    }

    @Then(
            "the trade should be force-closed using the final bar "
                    + "close as the execution reference"
    )
    public void tradeShouldBeForceClosedUsingFinalClose() {

        assertFalse(
                longPositionOpen
        );

        assertNotNull(
                periodEndLiquidation
        );

        assertNotNull(
                finalBarClose
        );

        assertDecimalEquals(
                finalBarClose,
                periodEndLiquidation
                        .executionReferencePrice()
        );
    }

    /*
     * ============================================================
     * PERIOD-END AUCTION SLIPPAGE
     * ============================================================
     */


    @When("the period-end liquidation is modeled")
    public void periodEndLiquidationIsModeled() {

        assertTrue(
                longPositionOpen
        );

        assertNotNull(
                finalBarClose
        );

        periodEndLiquidation =
                periodBoundaryService
                        .liquidateAtPeriodEnd(
                                finalBarClose
                        );

        lastExitReason =
                periodEndLiquidation
                        .exitReason();
    }

    @Then(
            "the period-end execution reference price "
                    + "should be {double}"
    )
    public void periodEndExecutionReferencePriceShouldBe(
            Double expectedPrice) {

        assertNotNull(
                periodEndLiquidation
        );

        assertDecimalEquals(
                decimal(
                        expectedPrice
                ),
                periodEndLiquidation
                        .executionReferencePrice()
        );
    }

    @Then(
            "the modeled period-end SELL fill should be {double}"
    )
    public void modeledPeriodEndSellFillShouldBe(
            Double expectedPrice) {

        assertNotNull(
                periodEndLiquidation
        );

        assertDecimalEquals(
                decimal(
                        expectedPrice
                ),
                periodEndLiquidation
                        .modeledFillPrice()
        );
    }

    /*
     * ============================================================
     * FINAL-BAR STOP PRIORITY
     * ============================================================
     */

    @Given(
            "a long position has an active stop of {double} "
                    + "on the final bar"
    )
    public void longPositionHasActiveStopOnFinalBar(
            Double stopPrice) {

        longPositionOpen =
                true;

        activeStop =
                decimal(
                        stopPrice
                );
    }

    @Given("the final bar opens at {double}")
    public void finalBarOpensAt(
            Double price) {

        finalBarOpen =
                decimal(
                        price
                );
    }

    @Given("the final bar low is {double}")
    public void finalBarLowIs(
            Double price) {

        finalBarLow =
                decimal(
                        price
                );
    }

    @When("final-bar exit priority is evaluated")
    public void finalBarExitPriorityIsEvaluated() {

        assertTrue(
                longPositionOpen
        );

        assertNotNull(
                activeStop
        );

        assertNotNull(
                finalBarOpen
        );

        assertNotNull(
                finalBarLow
        );

        assertNotNull(
                finalBarClose
        );

        finalBarExitDecision =
                periodBoundaryService
                        .evaluateFinalBarExit(
                                activeStop,
                                finalBarOpen,
                                finalBarLow,
                                finalBarClose
                        );

        lastExitReason =
                finalBarExitDecision
                        .exitReason();

        longPositionOpen =
                false;
    }

    @Then(
            "the stop should execute before period-end liquidation"
    )
    public void stopShouldExecuteBeforePeriodEndLiquidation() {

        assertNotNull(
                finalBarExitDecision
        );

        assertTrue(
                finalBarExitDecision
                        .stopTriggered()
        );

        assertFalse(
                finalBarExitDecision
                        .periodEndLiquidation()
        );

        assertEquals(
                ExitReason.STOP,
                finalBarExitDecision
                        .exitReason()
        );

        assertEquals(
                1,
                finalBarExitDecision
                        .exitCount()
        );
    }

    @Then("no second {string} exit should be created")
    public void noSecondExitShouldBeCreated(
            String forbiddenReason) {

        assertNotNull(
                finalBarExitDecision
        );

        assertEquals(
                "PERIOD_END",
                forbiddenReason
        );

        assertTrue(
                finalBarExitDecision
                        .stopTriggered()
        );

        assertFalse(
                finalBarExitDecision
                        .periodEndLiquidation()
        );

        assertEquals(
                1,
                finalBarExitDecision
                        .exitCount()
        );

        assertNotEquals(
                ExitReason.valueOf(
                        forbiddenReason
                ),
                finalBarExitDecision
                        .exitReason()
        );
    }

    /*
     * ============================================================
     * SURVIVING FINAL-BAR POSITION
     * ============================================================
     */

    @Then("the stop should not trigger")
    public void stopShouldNotTrigger() {

        assertNotNull(
                finalBarExitDecision
        );

        assertFalse(
                finalBarExitDecision
                        .stopTriggered()
        );

        assertTrue(
                finalBarExitDecision
                        .periodEndLiquidation()
        );
    }

    @Then("exactly one period-end exit should be created")
    public void exactlyOnePeriodEndExitShouldBeCreated() {

        assertNotNull(
                finalBarExitDecision
        );

        assertTrue(
                finalBarExitDecision
                        .periodEndLiquidation()
        );

        assertFalse(
                finalBarExitDecision
                        .stopTriggered()
        );

        assertEquals(
                1,
                finalBarExitDecision
                        .exitCount()
        );

        assertEquals(
                ExitReason.PERIOD_END,
                finalBarExitDecision
                        .exitReason()
        );
    }

    /*
     * ============================================================
     * SHARED EXIT ASSERTION
     * ============================================================
     */

    @Then("the period-boundary exit reason should be {string}")
    public void exitReasonShouldBe(
            String expectedReason) {

        assertNotNull(
                lastExitReason
        );

        assertEquals(
                ExitReason.valueOf(
                        expectedReason
                ),
                lastExitReason
        );
    }

    /*
     * ============================================================
     * NO NEXT-PERIOD PRICE LEAKAGE
     * ============================================================
     */

    @Given(
            "a position remains open on the final bar "
                    + "of validation"
    )
    public void positionRemainsOpenOnFinalBarOfValidation() {

        longPositionOpen =
                true;
    }

    @Given("the final validation close is {double}")
    public void finalValidationCloseIs(
            Double price) {

        finalValidationClose =
                decimal(
                        price
                );
    }

    @Given("the first holdout open is {double}")
    public void firstHoldoutOpenIs(
            Double price) {

        firstHoldoutOpen =
                decimal(
                        price
                );
    }

    @When("the validation position is force-closed")
    public void validationPositionIsForceClosed() {

        assertTrue(
                longPositionOpen
        );

        assertNotNull(
                finalValidationClose
        );

        assertNotNull(
                firstHoldoutOpen
        );

        /*
         * Deliberately pass only the final validation close.
         *
         * The production boundary API has no argument for the
         * next-period opening price, preventing price leakage.
         */
        periodEndLiquidation =
                periodBoundaryService
                        .liquidateAtPeriodEnd(
                                finalValidationClose
                        );

        lastExitReason =
                periodEndLiquidation
                        .exitReason();

        longPositionOpen =
                false;
    }

    @Then(
            "the execution reference should be the final "
                    + "validation close of {double}"
    )
    public void executionReferenceShouldBeFinalValidationClose(
            Double expectedPrice) {

        assertNotNull(
                periodEndLiquidation
        );

        assertDecimalEquals(
                decimal(
                        expectedPrice
                ),
                periodEndLiquidation
                        .executionReferencePrice()
        );

        assertDecimalEquals(
                finalValidationClose,
                periodEndLiquidation
                        .executionReferencePrice()
        );
    }

    @Then(
            "the first holdout open should not affect "
                    + "the validation exit"
    )
    public void firstHoldoutOpenShouldNotAffectValidationExit() {

        assertNotNull(
                periodEndLiquidation
        );

        assertNotNull(
                firstHoldoutOpen
        );

        assertNotNull(
                finalValidationClose
        );

        assertDecimalEquals(
                finalValidationClose,
                periodEndLiquidation
                        .executionReferencePrice()
        );

        assertNotEquals(
                0,
                firstHoldoutOpen.compareTo(
                        periodEndLiquidation
                                .executionReferencePrice()
                )
        );

        assertEquals(
                ExitReason.PERIOD_END,
                periodEndLiquidation
                        .exitReason()
        );
    }

    /*
     * ============================================================
     * GOVERNED PERIOD LIFECYCLE
     * ============================================================
     */

    @Given("a governed evaluation period is about to start")
    public void governedEvaluationPeriodIsAboutToStart() {

        lifecyclePeriodStart = null;
    }

    @Given("a position remained open from the previous period")
    public void positionRemainedOpenFromPreviousPeriod() {

        lifecycleOpenTrade =
                new GovernedEvaluationPeriodService.OpenTrade(
                        "previous-period-trade",
                        GovernedEvaluationPeriodService
                                .EvaluationPeriod.TUNING,
                        new BigDecimal(
                                "90.00"
                        ),
                        1
                );
    }

    @When("the new evaluation period begins")
    public void newEvaluationPeriodBegins() {

        assertNotNull(
                lifecycleOpenTrade
        );

        lifecyclePeriodStart =
                evaluationPeriodService.beginPeriod(
                        GovernedEvaluationPeriodService
                                .EvaluationPeriod.VALIDATION,
                        lifecycleOpenTrade
                );
    }

    @Then(
            "the new period should start with no open positions"
    )
    public void newPeriodShouldStartWithNoOpenPositions() {

        assertNotNull(
                lifecyclePeriodStart
        );

        assertTrue(
                lifecyclePeriodStart.startsFlat()
        );

        assertEquals(
                0,
                lifecyclePeriodStart
                        .openPositionCount()
        );
    }

    @Then(
            "no position from the previous period "
                    + "should be inherited"
    )
    public void noPreviousPositionShouldBeInherited() {

        assertNotNull(
                lifecyclePeriodStart
        );

        assertTrue(
                lifecyclePeriodStart
                        .previousPositionRejected()
        );

        assertEquals(
                0,
                lifecyclePeriodStart
                        .openPositionCount()
        );
    }

    /*
     * ============================================================
     * TUNING -> VALIDATION ISOLATION
     * ============================================================
     */

    @Given(
            "a position remains open at the end "
                    + "of the tuning period"
    )
    public void positionRemainsOpenAtEndOfTuningPeriod() {

        lifecycleOpenTrade =
                new GovernedEvaluationPeriodService.OpenTrade(
                        "tuning-boundary-trade",
                        GovernedEvaluationPeriodService
                                .EvaluationPeriod.TUNING,
                        new BigDecimal(
                                "90.00"
                        ),
                        1
                );

        finalBarClose =
                new BigDecimal(
                        "100.00"
                );
    }

    @When("the tuning period ends")
    public void tuningPeriodEnds() {

        assertNotNull(
                lifecycleOpenTrade
        );

        assertNotNull(
                finalBarClose
        );

        lifecycleBoundaryClose =
                evaluationPeriodService.closeAtPeriodEnd(
                        lifecycleOpenTrade,
                        finalBarClose
                );

        lifecyclePeriodStart =
                evaluationPeriodService.beginPeriod(
                        GovernedEvaluationPeriodService
                                .EvaluationPeriod.VALIDATION,
                        lifecycleOpenTrade
                );

        lastExitReason =
                lifecycleBoundaryClose
                        .exitReason();
    }

    @Then(
            "the position should be force-closed "
                    + "with reason {string}"
    )
    public void positionShouldBeForceClosedWithReason(
            String expectedReason) {

        assertNotNull(
                lifecycleBoundaryClose
        );

        assertEquals(
                ExitReason.valueOf(
                        expectedReason
                ),
                lifecycleBoundaryClose
                        .exitReason()
        );
    }

    @Then("validation should begin with no open position")
    public void validationShouldBeginWithNoOpenPosition() {

        assertNotNull(
                lifecyclePeriodStart
        );

        assertEquals(
                GovernedEvaluationPeriodService
                        .EvaluationPeriod.VALIDATION,
                lifecyclePeriodStart.period()
        );

        assertTrue(
                lifecyclePeriodStart.startsFlat()
        );

        assertEquals(
                0,
                lifecyclePeriodStart
                        .openPositionCount()
        );
    }

    @Then(
            "the tuning trade should not continue "
                    + "into validation"
    )
    public void tuningTradeShouldNotContinueIntoValidation() {

        assertNotNull(
                lifecycleBoundaryClose
        );

        assertNotNull(
                lifecyclePeriodStart
        );

        assertEquals(
                GovernedEvaluationPeriodService
                        .EvaluationPeriod.TUNING,
                lifecycleBoundaryClose
                        .openedPeriod()
        );

        assertEquals(
                GovernedEvaluationPeriodService
                        .EvaluationPeriod.TUNING,
                lifecycleBoundaryClose
                        .attributedPeriod()
        );

        assertEquals(
                ExitReason.PERIOD_END,
                lifecycleBoundaryClose
                        .exitReason()
        );

        assertTrue(
                lifecyclePeriodStart.startsFlat()
        );
    }

    /*
     * ============================================================
     * VALIDATION -> HOLDOUT ISOLATION
     * ============================================================
     */

    @Given(
            "a position remains open at the end "
                    + "of the validation period"
    )
    public void positionRemainsOpenAtEndOfValidationPeriod() {

        lifecycleOpenTrade =
                new GovernedEvaluationPeriodService.OpenTrade(
                        "validation-boundary-trade",
                        GovernedEvaluationPeriodService
                                .EvaluationPeriod.VALIDATION,
                        new BigDecimal(
                                "90.00"
                        ),
                        1
                );

        finalBarClose =
                new BigDecimal(
                        "100.00"
                );
    }

    @When("the validation period ends")
    public void validationPeriodEnds() {

        assertNotNull(
                lifecycleOpenTrade
        );

        assertNotNull(
                finalBarClose
        );

        lifecycleBoundaryClose =
                evaluationPeriodService.closeAtPeriodEnd(
                        lifecycleOpenTrade,
                        finalBarClose
                );

        validationAttributedProfitLoss =
                lifecycleBoundaryClose
                        .profitLoss();

        lifecyclePeriodStart =
                evaluationPeriodService.beginPeriod(
                        GovernedEvaluationPeriodService
                                .EvaluationPeriod.HOLDOUT,
                        lifecycleOpenTrade
                );

        lastExitReason =
                lifecycleBoundaryClose
                        .exitReason();
    }

    @Then("holdout should begin with no open position")
    public void holdoutShouldBeginWithNoOpenPosition() {

        assertNotNull(
                lifecyclePeriodStart
        );

        assertEquals(
                GovernedEvaluationPeriodService
                        .EvaluationPeriod.HOLDOUT,
                lifecyclePeriodStart.period()
        );

        assertTrue(
                lifecyclePeriodStart.startsFlat()
        );

        assertEquals(
                0,
                lifecyclePeriodStart
                        .openPositionCount()
        );
    }

    @Then(
            "the validation trade should not continue "
                    + "into holdout"
    )
    public void validationTradeShouldNotContinueIntoHoldout() {

        assertNotNull(
                lifecycleBoundaryClose
        );

        assertNotNull(
                lifecyclePeriodStart
        );

        assertEquals(
                GovernedEvaluationPeriodService
                        .EvaluationPeriod.VALIDATION,
                lifecycleBoundaryClose
                        .openedPeriod()
        );

        assertEquals(
                GovernedEvaluationPeriodService
                        .EvaluationPeriod.VALIDATION,
                lifecycleBoundaryClose
                        .attributedPeriod()
        );

        assertEquals(
                ExitReason.PERIOD_END,
                lifecycleBoundaryClose
                        .exitReason()
        );

        assertTrue(
                lifecyclePeriodStart.startsFlat()
        );
    }

    /*
     * ============================================================
     * PERIOD OWNERSHIP / ATTRIBUTION
     * ============================================================
     */

    @Given("a trade was opened during validation")
    public void tradeWasOpenedDuringValidation() {

        lifecycleOpenTrade =
                new GovernedEvaluationPeriodService.OpenTrade(
                        "validation-attribution-trade",
                        GovernedEvaluationPeriodService
                                .EvaluationPeriod.VALIDATION,
                        new BigDecimal(
                                "90.00"
                        ),
                        1
                );

        finalBarClose =
                new BigDecimal(
                        "100.00"
                );
    }

    @Given(
            "the trade remains open through "
                    + "the final validation bar"
    )
    public void tradeRemainsOpenThroughFinalValidationBar() {

        assertNotNull(
                lifecycleOpenTrade
        );

        assertEquals(
                GovernedEvaluationPeriodService
                        .EvaluationPeriod.VALIDATION,
                lifecycleOpenTrade
                        .openedPeriod()
        );
    }

    @Then(
            "the {string} exit should belong to validation"
    )
    public void exitShouldBelongToValidation(
            String expectedReason) {

        assertNotNull(
                lifecycleBoundaryClose
        );

        assertEquals(
                ExitReason.valueOf(
                        expectedReason
                ),
                lifecycleBoundaryClose
                        .exitReason()
        );

        assertEquals(
                GovernedEvaluationPeriodService
                        .EvaluationPeriod.VALIDATION,
                lifecycleBoundaryClose
                        .attributedPeriod()
        );
    }

    @Then(
            "its profit or loss should be included "
                    + "in validation results"
    )
    public void profitLossShouldBeIncludedInValidationResults() {

        assertNotNull(
                lifecycleBoundaryClose
        );

        assertNotNull(
                validationAttributedProfitLoss
        );

        assertEquals(
                GovernedEvaluationPeriodService
                        .EvaluationPeriod.VALIDATION,
                lifecycleBoundaryClose
                        .attributedPeriod()
        );

        assertDecimalEquals(
                lifecycleBoundaryClose
                        .profitLoss(),
                validationAttributedProfitLoss
        );
    }

    @Then(
            "no part of the trade should be attributed "
                    + "to holdout"
    )
    public void noPartOfTradeShouldBeAttributedToHoldout() {

        assertNotNull(
                lifecycleBoundaryClose
        );

        assertNotEquals(
                GovernedEvaluationPeriodService
                        .EvaluationPeriod.HOLDOUT,
                lifecycleBoundaryClose
                        .attributedPeriod()
        );

        assertEquals(
                GovernedEvaluationPeriodService
                        .EvaluationPeriod.VALIDATION,
                lifecycleBoundaryClose
                        .attributedPeriod()
        );
    }

    /*
     * ============================================================
     * DETERMINISTIC REPEATED RESEARCH
     * ============================================================
     */

    @Given("the same tuning dataset")
    public void sameTuningDataset() {

        repeatedTuningDatasetFingerprint =
                "tuning-dataset-sha256-v1";
    }

    @Given("the same governed configuration")
    public void sameGovernedConfiguration() {

        repeatedGovernedConfigurationHash =
                "governed-configuration-sha256-v1";
    }

    @Given("the same period boundary dates")
    public void samePeriodBoundaryDates() {

        repeatedPeriodStart =
                LocalDate.of(
                        2025,
                        1,
                        1
                );

        repeatedPeriodEnd =
                LocalDate.of(
                        2025,
                        6,
                        30
                );
    }

    @When("the tuning research run is repeated")
    public void tuningResearchRunIsRepeated() {

        assertNotNull(
                repeatedTuningDatasetFingerprint
        );

        assertNotNull(
                repeatedGovernedConfigurationHash
        );

        assertNotNull(
                repeatedPeriodStart
        );

        assertNotNull(
                repeatedPeriodEnd
        );

        GovernedEvaluationPeriodService.OpenTrade
                tuningTrade =
                new GovernedEvaluationPeriodService.OpenTrade(
                        "deterministic-tuning-trade",
                        GovernedEvaluationPeriodService
                                .EvaluationPeriod.TUNING,
                        new BigDecimal(
                                "90.00"
                        ),
                        1
                );

        GovernedEvaluationPeriodService.ResearchBoundaryInput
                input =
                new GovernedEvaluationPeriodService
                        .ResearchBoundaryInput(
                        repeatedTuningDatasetFingerprint,
                        repeatedGovernedConfigurationHash,
                        repeatedPeriodStart,
                        repeatedPeriodEnd,
                        tuningTrade,
                        new BigDecimal(
                                "100.00"
                        ),
                        true
                );

        firstResearchBoundaryResult =
                evaluationPeriodService
                        .evaluateResearchBoundary(
                                input
                        );

        secondResearchBoundaryResult =
                evaluationPeriodService
                        .evaluateResearchBoundary(
                                input
                        );
    }

    @Then(
            "the same trades should be closed "
                    + "at the same period boundary"
    )
    public void sameTradesShouldBeClosedAtSameBoundary() {

        assertNotNull(
                firstResearchBoundaryResult
        );

        assertNotNull(
                secondResearchBoundaryResult
        );

        assertEquals(
                firstResearchBoundaryResult
                        .deterministicFingerprint(),
                secondResearchBoundaryResult
                        .deterministicFingerprint()
        );

        assertEquals(
                firstResearchBoundaryResult
                        .boundaryClose(),
                secondResearchBoundaryResult
                        .boundaryClose()
        );

        assertNotNull(
                firstResearchBoundaryResult
                        .boundaryClose()
        );

        assertEquals(
                ExitReason.PERIOD_END,
                firstResearchBoundaryResult
                        .boundaryClose()
                        .exitReason()
        );

        assertEquals(
                GovernedEvaluationPeriodService
                        .EvaluationPeriod.TUNING,
                firstResearchBoundaryResult
                        .boundaryClose()
                        .attributedPeriod()
        );
    }

    @Then(
            "the same final-bar entry signals "
                    + "should be skipped"
    )
    public void sameFinalBarSignalsShouldBeSkipped() {

        assertNotNull(
                firstResearchBoundaryResult
        );

        assertNotNull(
                secondResearchBoundaryResult
        );

        assertEquals(
                firstResearchBoundaryResult
                        .finalBarEntryDecision(),
                secondResearchBoundaryResult
                        .finalBarEntryDecision()
        );

        assertTrue(
                firstResearchBoundaryResult
                        .finalBarEntryDecision()
                        .skipped()
        );

        assertFalse(
                firstResearchBoundaryResult
                        .finalBarEntryDecision()
                        .orderCreated()
        );

        assertEquals(
                GovernedPeriodBoundaryService
                        .SKIPPED_PERIOD_BOUNDARY,
                firstResearchBoundaryResult
                        .finalBarEntryDecision()
                        .outcomeReason()
        );
    }

    /*
     * ============================================================
     * GOVERNED PORTFOLIO RESULT ACCOUNTING
     * ============================================================
     */

    @Given("a trade exits with reason {string}")
    public void tradeExitsWithReasonForGovernedResults(
            String exitReason) {

        governedCompletedTrades =
                List.of(
                        new GovernedPortfolioResultService
                                .CompletedGovernedTrade(
                                "period-end-result-trade",
                                ExitReason.valueOf(
                                        exitReason
                                ),
                                new BigDecimal(
                                        "-100.00"
                                ),
                                new BigDecimal(
                                        "-0.50"
                                )
                        )
                );
    }

    @When("governed portfolio results are calculated")
    public void governedPortfolioResultsAreCalculated() {

        assertNotNull(
                governedCompletedTrades
        );

        governedPortfolioResults =
                governedPortfolioResultService
                        .calculateResults(
                                new BigDecimal(
                                        "1000.00"
                                ),
                                governedCompletedTrades
                        );
    }

    @Then(
            "the trade should be included "
                    + "in the portfolio equity curve"
    )
    public void tradeShouldBeIncludedInPortfolioEquityCurve() {

        assertNotNull(
                governedPortfolioResults
        );

        assertTrue(
                governedPortfolioResults
                        .includesTrade(
                                "period-end-result-trade"
                        )
        );

        assertEquals(
                2,
                governedPortfolioResults
                        .portfolioEquityCurve()
                        .size()
        );

        assertDecimalEquals(
                new BigDecimal(
                        "1000.00"
                ),
                governedPortfolioResults
                        .portfolioEquityCurve()
                        .getFirst()
        );

        assertDecimalEquals(
                new BigDecimal(
                        "900.00"
                ),
                governedPortfolioResults
                        .portfolioEquityCurve()
                        .getLast()
        );
    }

    @Then(
            "the trade should be included "
                    + "in maximum drawdown"
    )
    public void tradeShouldBeIncludedInMaximumDrawdown() {

        assertNotNull(
                governedPortfolioResults
        );

        /*
         * 1000 -> 900 means the PERIOD_END loss produced
         * a governed maximum drawdown of 10%.
         */
        assertDecimalEquals(
                new BigDecimal(
                        "0.10000000"
                ),
                governedPortfolioResults
                        .maximumDrawdown()
        );
    }

    @Then(
            "the trade should be included "
                    + "in primary expectancy"
    )
    public void tradeShouldBeIncludedInPrimaryExpectancy() {

        assertNotNull(
                governedPortfolioResults
        );

        assertDecimalEquals(
                new BigDecimal(
                        "-0.50000000"
                ),
                governedPortfolioResults
                        .primaryExpectancy()
        );

        assertDecimalEquals(
                governedPortfolioResults
                        .expectancyIncludingPeriodEnd(),
                governedPortfolioResults
                        .primaryExpectancy()
        );

        assertEquals(
                1,
                governedPortfolioResults
                        .periodEndTradeCount()
        );
    }

    /*
     * ============================================================
     * EXPECTANCY INCLUDING / EXCLUDING PERIOD_END
     * ============================================================
     */

    @Given(
            "completed governed trades include ordinary exits "
                    + "and {string} exits"
    )
    public void completedGovernedTradesIncludeOrdinaryAndPeriodEnd(
            String periodEndReason) {

        governedCompletedTrades =
                List.of(
                        new GovernedPortfolioResultService
                                .CompletedGovernedTrade(
                                "ordinary-result-trade",
                                ExitReason.SELL,
                                new BigDecimal(
                                        "100.00"
                                ),
                                new BigDecimal(
                                        "1.00"
                                )
                        ),
                        new GovernedPortfolioResultService
                                .CompletedGovernedTrade(
                                "period-end-result-trade",
                                ExitReason.valueOf(
                                        periodEndReason
                                ),
                                new BigDecimal(
                                        "-50.00"
                                ),
                                new BigDecimal(
                                        "-0.50"
                                )
                        )
                );
    }

    @When("expectancy metrics are calculated")
    public void expectancyMetricsAreCalculated() {

        assertNotNull(
                governedCompletedTrades
        );

        governedPortfolioResults =
                governedPortfolioResultService
                        .calculateResults(
                                new BigDecimal(
                                        "1000.00"
                                ),
                                governedCompletedTrades
                        );
    }

    @Then(
            "expectancy including period-end trades "
                    + "should be reported"
    )
    public void expectancyIncludingPeriodEndShouldBeReported() {

        assertNotNull(
                governedPortfolioResults
        );

        /*
         * (1.00R + -0.50R) / 2 = 0.25R
         */
        assertDecimalEquals(
                new BigDecimal(
                        "0.25000000"
                ),
                governedPortfolioResults
                        .expectancyIncludingPeriodEnd()
        );

        assertDecimalEquals(
                governedPortfolioResults
                        .expectancyIncludingPeriodEnd(),
                governedPortfolioResults
                        .primaryExpectancy()
        );
    }

    @Then(
            "expectancy excluding period-end trades "
                    + "should be reported"
    )
    public void expectancyExcludingPeriodEndShouldBeReported() {

        assertNotNull(
                governedPortfolioResults
        );

        /*
         * Ordinary trade only = 1.00R.
         */
        assertDecimalEquals(
                new BigDecimal(
                        "1.00000000"
                ),
                governedPortfolioResults
                        .expectancyExcludingPeriodEnd()
        );
    }

    @Then("period-end trade count should be reported")
    public void periodEndTradeCountShouldBeReported() {

        assertNotNull(
                governedPortfolioResults
        );

        assertEquals(
                1,
                governedPortfolioResults
                        .periodEndTradeCount()
        );
    }

    /*
     * ============================================================
     * PERIOD-BOUNDARY EXPECTANCY DIAGNOSTIC
     * ============================================================
     */

    @Given(
            "expectancy including period-end trades "
                    + "is {double} R"
    )
    public void expectancyIncludingPeriodEndIs(
            Double expectancy) {

        diagnosticExpectancyIncludingPeriodEnd =
                decimal(
                        expectancy
                );
    }

    @Given(
            "expectancy excluding period-end trades "
                    + "is {double} R"
    )
    public void expectancyExcludingPeriodEndIs(
            Double expectancy) {

        diagnosticExpectancyExcludingPeriodEnd =
                decimal(
                        expectancy
                );
    }

    @When("period-boundary metrics are recorded")
    public void periodBoundaryMetricsAreRecorded() {

        assertNotNull(
                diagnosticExpectancyIncludingPeriodEnd
        );

        assertNotNull(
                diagnosticExpectancyExcludingPeriodEnd
        );

        periodBoundaryMetrics =
                governedPortfolioResultService
                        .recordPeriodBoundaryMetrics(
                                diagnosticExpectancyIncludingPeriodEnd,
                                diagnosticExpectancyExcludingPeriodEnd
                        );
    }

    @Then("both expectancy values should be preserved")
    public void bothExpectancyValuesShouldBePreserved() {

        assertNotNull(
                periodBoundaryMetrics
        );

        assertDecimalEquals(
                diagnosticExpectancyIncludingPeriodEnd,
                periodBoundaryMetrics
                        .expectancyIncludingPeriodEnd()
        );

        assertDecimalEquals(
                diagnosticExpectancyExcludingPeriodEnd,
                periodBoundaryMetrics
                        .expectancyExcludingPeriodEnd()
        );

        assertDecimalEquals(
                new BigDecimal(
                        "0.40"
                ),
                periodBoundaryMetrics
                        .absoluteDifference()
        );
    }

    @Then(
            "the difference should not be hidden "
                    + "by averaging them"
    )
    public void differenceShouldNotBeHiddenByAveraging() {

        assertNotNull(
                periodBoundaryMetrics
        );

        assertFalse(
                periodBoundaryMetrics
                        .averagedTogether()
        );

        BigDecimal hiddenAverage =
                new BigDecimal(
                        "0.60"
                );

        assertNotEquals(
                0,
                hiddenAverage.compareTo(
                        periodBoundaryMetrics
                                .expectancyIncludingPeriodEnd()
                )
        );

        assertNotEquals(
                0,
                hiddenAverage.compareTo(
                        periodBoundaryMetrics
                                .expectancyExcludingPeriodEnd()
                )
        );
    }

    /*
     * ============================================================
     * HELPERS
     * ============================================================
     */

    private static BigDecimal decimal(
            Double value) {

        assertNotNull(
                value
        );

        return BigDecimal.valueOf(
                value
        );
    }

    private static void assertDecimalEquals(
            BigDecimal expected,
            BigDecimal actual) {

        assertNotNull(
                expected
        );

        assertNotNull(
                actual
        );

        assertEquals(
                0,
                expected.compareTo(
                        actual
                ),
                "Expected "
                        + expected
                        + " but was "
                        + actual
        );
    }
}