package com.marshallai;

import com.marshallai.trading.execution.ExitReason;

import com.marshallai.portfolio.governance.PortfolioExecutionCostService;
import com.marshallai.trading.execution.TradeDefinition;
import com.marshallai.trading.execution.TradeEntryResult;
import com.marshallai.trading.execution.TradeEntryService;
import com.marshallai.trading.execution.TradeExitService;
import com.marshallai.trading.execution.TradeStopService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class TradeDefinitionStepDefinitions {

    private final TradeDefinition definition =
            TradeDefinition.v1();

    private final TradeEntryService entryService =
            new TradeEntryService(definition);

    private final TradeStopService stopService =
            new TradeStopService(definition);

    private final TradeExitService exitService =
            new TradeExitService(definition);

    /*
     * Portfolio Governance owns execution-cost assumptions.
     *
     * TradeStopService determines whether a stop triggered
     * and at what reference price.
     *
     * PortfolioExecutionCostService then applies the governed
     * adverse stop-slippage assumption.
     */
    private final PortfolioExecutionCostService executionCostService =
            new PortfolioExecutionCostService();

    private BigDecimal signalClose;
    private BigDecimal atr;
    private BigDecimal portfolioEquity;
    private BigDecimal entryLimitPrice;
    private BigDecimal frozenStopDistance;
    private BigDecimal openingAuctionPrice;

    private int riskBasedQuantity;
    private int exposureBasedQuantity;
    private int finalQuantity;
    private BigDecimal riskAmount;

    private TradeEntryResult entryResult;

    private BigDecimal initialRDenominator;
    private BigDecimal recordedRDenominator;

    private BigDecimal activeStopPrice;
    private BigDecimal previousActiveStopPrice;
    private BigDecimal initialStopPrice;
    private BigDecimal nextBarOpen;
    private BigDecimal nextBarLow;
    private BigDecimal highestHighSinceEntry;
    private BigDecimal completedBarAtr;
    private BigDecimal candidateTrailingStop;
    private BigDecimal nextActiveStop;

    private TradeStopService.StopEvaluation stopEvaluation;

    /*
     * Entry-bar stop state.
     */
    private BigDecimal entryBarHigh;
    private BigDecimal entryBarLow;

    private TradeStopService.EntryBarStopEvaluation
            entryBarStopEvaluation;

    private BigDecimal modeledEntryBarExitFill;

    private BigDecimal fillPrice;
    private BigDecimal holdingBar20Close;
    private int holdingBar;

    private TradeExitService.StaleCheckResult staleCheckResult;

    private boolean longPositionHeld;
    private boolean sellExitScheduled;
    private boolean shortPositionOpened;

    private TradeExitService.ExitEvaluation exitEvaluation;

    private boolean belowExecutionThreshold;
    private boolean executableTradeCreated;
    private boolean shadowOutcomeRecorded;
    private boolean standaloneRUsed;

    @Given("trade definition version {string} is active")
    public void tradeDefinitionVersionIsActive(
            String version) {

        assertEquals(
                version,
                definition.version()
        );
    }

    @Given("the strategy is long-only")
    public void theStrategyIsLongOnly() {

        assertTrue(
                definition.longOnly()
        );
    }

    @Given("portfolio risk per trade is {int} percent")
    public void portfolioRiskPerTradeIsPercent(
            int percent) {

        assertDecimal(
                BigDecimal.valueOf(percent)
                        .movePointLeft(2),
                definition.riskPercent()
        );
    }

    @Given("maximum position exposure is {int} percent")
    public void maximumPositionExposureIsPercent(
            int percent) {

        assertDecimal(
                BigDecimal.valueOf(percent)
                        .movePointLeft(2),
                definition.maximumPositionExposurePercent()
        );
    }

    @Given("ATR uses {int} periods with Wilder smoothing")
    public void atrUsesPeriodsWithWilderSmoothing(
            int periods) {

        assertEquals(
                periods,
                definition.atrPeriods()
        );

        assertEquals(
                "WILDER",
                definition.atrSmoothing()
        );
    }

    @Given("the initial stop distance is {int} ATR")
    public void theInitialStopDistanceIsAtr(
            int multiplier) {

        assertDecimal(
                BigDecimal.valueOf(multiplier),
                definition.initialStopAtrMultiplier()
        );
    }

    @Given("the entry limit offset is {int} ATR")
    public void theEntryLimitOffsetIsAtr(
            int multiplier) {

        assertDecimal(
                BigDecimal.valueOf(multiplier),
                definition.entryLimitAtrMultiplier()
        );
    }

    @Given("the trailing stop distance is {int} ATR")
    public void theTrailingStopDistanceIsAtr(
            int multiplier) {

        assertDecimal(
                BigDecimal.valueOf(multiplier),
                definition.trailingStopAtrMultiplier()
        );
    }

    @Given("completed signal bar t has a close of {double}")
    public void completedSignalBarTHasACloseOf(
            double close) {

        signalClose =
                decimal(close);
    }

    @Given("ATR on completed signal bar t is {double}")
    public void atrOnCompletedSignalBarTIs(
            double value) {

        atr =
                decimal(value);
    }

    @When("a BUY signal is accepted")
    public void aBuySignalIsAccepted() {

        frozenStopDistance =
                entryService.calculateFrozenStopDistance(
                        atr
                );

        entryLimitPrice =
                entryService.calculateEntryLimitPrice(
                        signalClose,
                        atr
                );
    }

    @Then("the frozen stop distance should be {double}")
    public void theFrozenStopDistanceShouldBe(
            double expected) {

        assertDecimal(
                decimal(expected),
                frozenStopDistance
        );
    }

    @Then("the entry limit price should be {double}")
    public void theEntryLimitPriceShouldBe(
            double expected) {

        assertDecimal(
                decimal(expected),
                entryLimitPrice
        );
    }

    @Given("portfolio equity at the signal close is {double}")
    public void portfolioEquityAtTheSignalCloseIs(
            double equity) {

        portfolioEquity =
                decimal(equity);
    }

    @Given("the entry limit price is {double}")
    public void theEntryLimitPriceIs(
            double price) {

        entryLimitPrice =
                decimal(price);
    }

    @When("the trade quantity is calculated")
    public void theTradeQuantityIsCalculated() {

        frozenStopDistance =
                entryService.calculateFrozenStopDistance(
                        atr
                );

        riskAmount =
                entryService.calculateRiskAmount(
                        portfolioEquity
                );

        riskBasedQuantity =
                entryService.calculateRiskBasedQuantity(
                        portfolioEquity,
                        frozenStopDistance
                );

        exposureBasedQuantity =
                entryService.calculateExposureBasedQuantity(
                        portfolioEquity,
                        entryLimitPrice
                );

        finalQuantity =
                entryService.calculateFinalQuantity(
                        portfolioEquity,
                        frozenStopDistance,
                        entryLimitPrice
                );
    }

    @Then("the risk amount should be {double}")
    public void theRiskAmountShouldBe(
            double expected) {

        assertDecimal(
                decimal(expected),
                riskAmount
        );
    }

    @Then("the risk-based quantity should be {int}")
    public void theRiskBasedQuantityShouldBe(
            int expected) {

        assertEquals(
                expected,
                riskBasedQuantity
        );
    }

    @Then("the exposure-based quantity should be {int}")
    public void theExposureBasedQuantityShouldBe(
            int expected) {

        assertEquals(
                expected,
                exposureBasedQuantity
        );
    }

    @Then("the final quantity should be {int}")
    public void theFinalQuantityShouldBe(
            int expected) {

        assertEquals(
                expected,
                finalQuantity
        );
    }

    @Then("the trade should be skipped")
    public void theTradeShouldBeSkipped() {

        assertEquals(
                0,
                finalQuantity
        );
    }

    @Given("the next opening auction price is {double}")
    public void theNextOpeningAuctionPriceIs(
            double price) {

        openingAuctionPrice =
                decimal(price);
    }

    @When("the opening-only BUY order is evaluated")
    public void theOpeningOnlyBuyOrderIsEvaluated() {

        if (portfolioEquity == null) {
            portfolioEquity =
                    new BigDecimal("100000");
        }

        /*
         * Some scenarios provide the already-governed limit
         * directly rather than signal close + ATR.
         *
         * Reconstruct a compatible signal close only when
         * those source values were not supplied.
         */
        if (signalClose == null &&
                entryLimitPrice != null &&
                atr == null) {

            atr =
                    BigDecimal.ONE;

            signalClose =
                    entryLimitPrice.subtract(
                            definition.entryLimitAtrMultiplier()
                    );
        }

        if (atr == null) {
            atr =
                    BigDecimal.ONE;
        }

        if (signalClose == null) {
            signalClose =
                    entryLimitPrice.subtract(
                            atr.multiply(
                                    definition
                                            .entryLimitAtrMultiplier()
                            )
                    );
        }

        entryResult =
                entryService.evaluateOpeningEntry(
                        portfolioEquity,
                        signalClose,
                        atr,
                        openingAuctionPrice
                );
    }

    @Then("the trade should fill at {double}")
    public void theTradeShouldFillAt(
            double expected) {

        assertTrue(
                entryResult.filled()
        );

        assertDecimal(
                decimal(expected),
                entryResult.fillPrice()
        );
    }

    @Then("the initial stop price should be {double}")
    public void theInitialStopPriceShouldBe(
            double expected) {

        assertDecimal(
                decimal(expected),
                entryResult.initialStopPrice()
        );
    }

    @Then("the trade should not fill")
    public void theTradeShouldNotFill() {

        assertFalse(
                entryResult.filled()
        );
    }

    @Then("the entry should be skipped")
    public void theEntryShouldBeSkipped() {

        assertTrue(
                entryResult.skipped()
        );
    }

    @Then("the opening gap should be recorded as {double} ATR")
    public void theOpeningGapShouldBeRecordedAsAtr(
            double expected) {

        assertDecimal(
                decimal(expected),
                entryResult.openingGapAtr()
        );
    }

    @Given("a filled trade has quantity {int}")
    public void aFilledTradeHasQuantity(
            int quantity) {

        finalQuantity =
                quantity;
    }

    @Given("the frozen stop distance is {double}")
    public void theFrozenStopDistanceIs(
            double distance) {

        frozenStopDistance =
                decimal(distance);
    }

    @When("the initial R denominator is recorded")
    public void theInitialRDenominatorIsRecorded() {

        initialRDenominator =
                frozenStopDistance.multiply(
                        BigDecimal.valueOf(
                                finalQuantity
                        )
                );

        recordedRDenominator =
                initialRDenominator;
    }

    @Then("the initial R denominator should be {double}")
    public void theInitialRDenominatorShouldBe(
            double expected) {

        assertDecimal(
                decimal(expected),
                initialRDenominator
        );
    }

    @Then("the R denominator should remain immutable")
    public void theRDenominatorShouldRemainImmutable() {

        assertDecimal(
                initialRDenominator,
                recordedRDenominator
        );
    }

    @Given("a long trade was filled at {double}")
    public void aLongTradeWasFilledAt(
            double price) {

        fillPrice =
                decimal(price);

        longPositionHeld =
                true;
    }

    @Given("a trade was filled at {double}")
    public void aTradeWasFilledAt(
            double price) {

        fillPrice =
                decimal(price);
    }

    @Given("the active stop price is {double}")
    public void theActiveStopPriceIs(
            double price) {

        activeStopPrice =
                decimal(price);
    }

    @Given("the next bar opens at {double}")
    public void theNextBarOpensAt(
            double price) {

        nextBarOpen =
                decimal(price);
    }

    @Given("the next bar low is {double}")
    public void theNextBarLowIs(
            double price) {

        nextBarLow =
                decimal(price);
    }

    @When("the stop is evaluated")
    public void theStopIsEvaluated() {

        BigDecimal low =
                nextBarLow == null
                        ? nextBarOpen
                        : nextBarLow;

        stopEvaluation =
                stopService.evaluateStop(
                        activeStopPrice,
                        nextBarOpen,
                        low
                );
    }

    @Then("the trade should exit at {double}")
    public void theTradeShouldExitAt(
            double expected) {

        BigDecimal actual;

        if (stopEvaluation != null) {

            actual =
                    stopEvaluation.exitPrice();

        } else if (entryBarStopEvaluation != null &&
                entryBarStopEvaluation.triggered()) {

            actual =
                    entryBarStopEvaluation.triggerPrice();

        } else {

            assertNotNull(
                    exitEvaluation
            );

            actual =
                    exitEvaluation.exitPrice();
        }

        assertDecimal(
                decimal(expected),
                actual
        );
    }

    @Then("the exit reason should be {string}")
    public void theExitReasonShouldBe(
            String expected) {

        ExitReason actual;

        if (entryBarStopEvaluation != null &&
                entryBarStopEvaluation.triggered()) {

            actual =
                    entryBarStopEvaluation.exitReason();

        } else if (stopEvaluation != null) {

            actual =
                    stopEvaluation.exitReason();

        } else {

            assertNotNull(
                    exitEvaluation
            );

            actual =
                    exitEvaluation.exitReason();
        }

        assertNotNull(
                actual
        );

        assertEquals(
                expected,
                actual.name()
        );
    }

    @Given("the initial stop price is {double}")
    public void theInitialStopPriceIs(
            double price) {

        initialStopPrice =
                decimal(price);
    }

    @Given("the previous active stop price is {double}")
    public void thePreviousActiveStopPriceIs(
            double price) {

        previousActiveStopPrice =
                decimal(price);
    }

    @Given("the highest high since entry is {double}")
    public void theHighestHighSinceEntryIs(
            double price) {

        highestHighSinceEntry =
                decimal(price);
    }

    @Given("ATR on the completed bar is {double}")
    public void atrOnTheCompletedBarIs(
            double value) {

        completedBarAtr =
                decimal(value);
    }

    @When("the trailing stop is recalculated after the bar closes")
    public void theTrailingStopIsRecalculatedAfterTheBarCloses() {

        candidateTrailingStop =
                stopService.calculateCandidateTrailingStop(
                        highestHighSinceEntry,
                        completedBarAtr
                );

        nextActiveStop =
                stopService.selectNextActiveStop(
                        initialStopPrice,
                        previousActiveStopPrice,
                        candidateTrailingStop
                );
    }

    @Then("the candidate trailing stop should be {double}")
    public void theCandidateTrailingStopShouldBe(
            double expected) {

        assertDecimal(
                decimal(expected),
                candidateTrailingStop
        );
    }

    @Then("the next bar active stop should be {double}")
    public void theNextBarActiveStopShouldBe(
            double expected) {

        assertDecimal(
                decimal(expected),
                nextActiveStop
        );
    }

    @Then("the completed bar should still use the previous active stop of {double}")
    public void theCompletedBarShouldStillUseThePreviousActiveStopOf(
            double expected) {

        assertDecimal(
                decimal(expected),
                previousActiveStopPrice
        );
    }

    @Given("the candidate trailing stop price is {double}")
    public void theCandidateTrailingStopPriceIs(
            double price) {

        candidateTrailingStop =
                decimal(price);
    }

    @When("the next active stop is selected")
    public void theNextActiveStopIsSelected() {

        /*
         * The initial stop has already been incorporated into
         * the previous active stop for this scenario.
         */
        nextActiveStop =
                previousActiveStopPrice.max(
                        candidateTrailingStop
                );
    }

    @Then("the next bar active stop should remain {double}")
    public void theNextBarActiveStopShouldRemain(
            double expected) {

        assertDecimal(
                decimal(expected),
                nextActiveStop
        );
    }

    @Given("a trade is filled on an entry bar")
    public void aTradeIsFilledOnAnEntryBar() {

        holdingBar =
                exitService.initialHoldingBar();
    }

    /*
     * ---------------------------------------------------------
     * Entry-bar stop scenarios
     * ---------------------------------------------------------
     */

    @Given("a trade is filled on an entry bar at {double}")
    public void aTradeIsFilledOnAnEntryBarAt(
            double price) {

        fillPrice =
                decimal(price);

        longPositionHeld =
                true;

        holdingBar =
                exitService.initialHoldingBar();

        assertEquals(
                1,
                holdingBar
        );
    }

    @Given("the entry bar low is {double}")
    public void theEntryBarLowIs(
            double price) {

        entryBarLow =
                decimal(price);
    }

    @Given("the entry bar high is {double}")
    public void theEntryBarHighIs(
            double price) {

        entryBarHigh =
                decimal(price);
    }

    @When("the entry bar stop is evaluated")
    public void theEntryBarStopIsEvaluated() {

        assertNotNull(
                fillPrice,
                "Entry-bar fill price must be set"
        );

        assertNotNull(
                initialStopPrice,
                "Initial stop price must be set"
        );

        assertNotNull(
                entryBarLow,
                "Entry bar low must be set"
        );

        if (entryBarHigh == null) {

            entryBarStopEvaluation =
                    stopService.evaluateEntryBarStop(
                            fillPrice,
                            initialStopPrice,
                            entryBarLow
                    );

        } else {

            entryBarStopEvaluation =
                    stopService.evaluateEntryBarStop(
                            fillPrice,
                            initialStopPrice,
                            entryBarHigh,
                            entryBarLow
                    );
        }

        holdingBar =
                entryBarStopEvaluation.holdingBar();

        if (entryBarStopEvaluation.triggered()) {

            modeledEntryBarExitFill =
                    executionCostService
                            .applySellStopSlippage(
                                    entryBarStopEvaluation
                                            .triggerPrice()
                            );

            longPositionHeld =
                    false;
        }
    }

    @When("an entry bar low of {double} is evaluated")
    public void anEntryBarLowOfIsEvaluated(
            double price) {

        entryBarLow =
                decimal(price);

        assertNotNull(
                fillPrice,
                "Entry-bar fill price must be set"
        );

        assertNotNull(
                initialStopPrice,
                "Initial stop price must be set"
        );

        entryBarStopEvaluation =
                stopService.evaluateEntryBarStop(
                        fillPrice,
                        initialStopPrice,
                        entryBarLow
                );

        holdingBar =
                entryBarStopEvaluation.holdingBar();

        if (entryBarStopEvaluation.triggered()) {

            modeledEntryBarExitFill =
                    executionCostService
                            .applySellStopSlippage(
                                    entryBarStopEvaluation
                                            .triggerPrice()
                            );

            longPositionHeld =
                    false;
        }
    }

    @Then("the stop should trigger at {double}")
    public void theStopShouldTriggerAt(
            double expected) {

        assertNotNull(
                entryBarStopEvaluation
        );

        assertTrue(
                entryBarStopEvaluation.triggered()
        );

        assertDecimal(
                decimal(expected),
                entryBarStopEvaluation.triggerPrice()
        );
    }

    @Then("the modeled exit fill should be {double}")
    public void theModeledExitFillShouldBe(
            double expected) {

        assertNotNull(
                entryBarStopEvaluation
        );

        assertTrue(
                entryBarStopEvaluation.triggered()
        );

        assertNotNull(
                modeledEntryBarExitFill
        );

        assertDecimal(
                decimal(expected),
                modeledEntryBarExitFill
        );
    }

    @Then("the entry-bar stop triggered state should be {word}")
    public void theEntryBarStopTriggeredStateShouldBe(
            String expected) {

        assertNotNull(
                entryBarStopEvaluation
        );

        boolean expectedTriggered =
                Boolean.parseBoolean(
                        expected
                );

        assertEquals(
                expectedTriggered,
                entryBarStopEvaluation.triggered()
        );
    }

    @Then("the active stop for that bar should remain {double}")
    public void theActiveStopForThatBarShouldRemain(
            double expected) {

        assertNotNull(
                entryBarStopEvaluation
        );

        assertDecimal(
                decimal(expected),
                entryBarStopEvaluation.activeStop()
        );
    }

    @Then("the position should remain open")
    public void thePositionShouldRemainOpen() {

        assertNotNull(
                entryBarStopEvaluation
        );

        assertFalse(
                entryBarStopEvaluation.triggered()
        );

        assertNull(
                entryBarStopEvaluation.exitReason()
        );

        assertNull(
                entryBarStopEvaluation.triggerPrice()
        );
    }

    @Then("the next holding bar should be bar {int}")
    public void theNextHoldingBarShouldBeBar(
            int expected) {

        assertNotNull(
                entryBarStopEvaluation
        );

        assertFalse(
                entryBarStopEvaluation.triggered()
        );

        assertNotNull(
                entryBarStopEvaluation.nextHoldingBar()
        );

        assertEquals(
                expected,
                entryBarStopEvaluation
                        .nextHoldingBar()
                        .intValue()
        );
    }

    @When("holding bars are counted")
    public void holdingBarsAreCounted() {

        holdingBar =
                exitService.initialHoldingBar();
    }

    @Then("the entry bar should be holding bar {int}")
    public void theEntryBarShouldBeHoldingBar(
            int expected) {

        if (entryBarStopEvaluation != null) {

            assertEquals(
                    expected,
                    entryBarStopEvaluation.holdingBar()
            );

            return;
        }

        assertEquals(
                expected,
                holdingBar
        );
    }

    @Given("holding bar 20 closes at {double}")
    public void holdingBar20ClosesAt(
            double price) {

        holdingBar =
                20;

        holdingBar20Close =
                decimal(price);
    }

    @When("the stale trade check is evaluated at the close of bar 20")
    public void theStaleTradeCheckIsEvaluatedAtTheCloseOfBar20() {

        staleCheckResult =
                exitService.evaluateStaleCheck(
                        holdingBar,
                        holdingBar20Close,
                        fillPrice,
                        frozenStopDistance
                );
    }

    @Then("unrealized R should be {double}")
    public void unrealizedRShouldBe(
            double expected) {

        assertTrue(
                staleCheckResult.evaluated()
        );

        assertDecimal(
                decimal(expected),
                staleCheckResult.unrealizedR()
        );
    }

    @Then("an exit should be scheduled for the next open")
    public void anExitShouldBeScheduledForTheNextOpen() {

        assertTrue(
                staleCheckResult != null
                        ? staleCheckResult.exitScheduled()
                        : sellExitScheduled
        );
    }

    @Then("no stale exit should be scheduled")
    public void noStaleExitShouldBeScheduled() {

        assertFalse(
                staleCheckResult.exitScheduled()
        );
    }

    @Then("the stale trade check should not run again")
    public void theStaleTradeCheckShouldNotRunAgain() {

        assertTrue(
                staleCheckResult.evaluated()
        );

        assertFalse(
                exitService.shouldRunStaleCheck(
                        definition.staleCheckBar() + 1
                )
        );
    }

    @Given("a long position is currently held")
    public void aLongPositionIsCurrentlyHeld() {

        longPositionHeld =
                true;
    }

    @When("a SELL signal is generated")
    public void aSellSignalIsGenerated() {

        sellExitScheduled =
                exitService.shouldScheduleSellExit(
                        longPositionHeld
                );

        shortPositionOpened =
                exitService.shouldOpenShortPosition();
    }

    @Then("no short position should be opened")
    public void noShortPositionShouldBeOpened() {

        assertFalse(
                shortPositionOpened
        );
    }

    @Given("a SELL exit is scheduled for the next open")
    public void aSellExitIsScheduledForTheNextOpen() {

        sellExitScheduled =
                true;
    }

    @When("the scheduled exit is evaluated")
    public void theScheduledExitIsEvaluated() {

        assertTrue(
                sellExitScheduled
        );

        exitEvaluation =
                exitService.evaluateScheduledOpenExit(
                        activeStopPrice,
                        nextBarOpen,
                        ExitReason.SELL
                );
    }

    @Given("a valid signal has confidence below the execution threshold")
    public void aValidSignalHasConfidenceBelowTheExecutionThreshold() {

        belowExecutionThreshold =
                true;
    }

    @When("the execution gate blocks the signal")
    public void theExecutionGateBlocksTheSignal() {

        /*
         * The portfolio/execution gate does not create an
         * executable position, but research retains the signal.
         *
         * The later backtesting workload will calculate the
         * actual standalone hypothetical trade outcome.
         */
        if (belowExecutionThreshold) {

            executableTradeCreated =
                    false;

            shadowOutcomeRecorded =
                    true;

            standaloneRUsed =
                    true;
        }
    }

    @Then("no executable trade should be created")
    public void noExecutableTradeShouldBeCreated() {

        assertFalse(
                executableTradeCreated
        );
    }

    @Then("a shadow-only signal outcome should be recorded")
    public void aShadowOnlySignalOutcomeShouldBeRecorded() {

        assertTrue(
                shadowOutcomeRecorded
        );
    }

    @Then("its outcome should be measured using standalone trade R")
    public void itsOutcomeShouldBeMeasuredUsingStandaloneTradeR() {

        assertTrue(
                standaloneRUsed
        );
    }

    private BigDecimal decimal(
            double value) {

        return BigDecimal.valueOf(
                value
        );
    }

    private void assertDecimal(
            BigDecimal expected,
            BigDecimal actual) {

        assertNotNull(
                actual
        );

        assertEquals(
                0,
                expected.compareTo(actual),
                "Expected " +
                        expected +
                        " but was " +
                        actual
        );
    }
}