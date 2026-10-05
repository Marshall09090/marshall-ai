package com.marshallai;

import com.marshallai.portfolio.governance.PortfolioBenchmarkService;
import com.marshallai.portfolio.governance.PortfolioCandidateService;
import com.marshallai.portfolio.governance.PortfolioCapitalService;
import com.marshallai.portfolio.governance.PortfolioDrawdownService;
import com.marshallai.portfolio.governance.PortfolioExecutionCostService;
import com.marshallai.portfolio.governance.PortfolioGovernanceDefinition;
import com.marshallai.portfolio.governance.PortfolioSignalOutcomeService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PortfolioGovernanceStepDefinitions {

    private final PortfolioGovernanceDefinition definition =
            PortfolioGovernanceDefinition.v1();

    private final PortfolioCapitalService capitalService =
            new PortfolioCapitalService(definition);

    private final PortfolioCandidateService candidateService =
            new PortfolioCandidateService();

    private final PortfolioExecutionCostService executionCostService =
            new PortfolioExecutionCostService(definition);

    private final PortfolioBenchmarkService benchmarkService =
            new PortfolioBenchmarkService(definition);

    private final PortfolioDrawdownService drawdownService =
            new PortfolioDrawdownService(definition);

    private final PortfolioSignalOutcomeService signalOutcomeService =
            new PortfolioSignalOutcomeService(definition);

    private BigDecimal governedStartingCapital;

    private BigDecimal portfolioEquity;
    private BigDecimal availableCash;
    private BigDecimal reservedCash = BigDecimal.ZERO;
    private BigDecimal availableUnreservedCash;

    private int pendingBuyQuantity;
    private BigDecimal pendingBuyLimitPrice;

    private BigDecimal existingPositionExposure =
            BigDecimal.ZERO;

    private BigDecimal pendingReservedExposure =
            BigDecimal.ZERO;

    private BigDecimal totalCommittedExposure =
            BigDecimal.ZERO;

    private BigDecimal proposedExposure =
            BigDecimal.ZERO;

    private PortfolioCapitalService.CapitalDecision capitalDecision;

    private PortfolioCapitalService.PositionExposureDecision
            positionExposureDecision;

    private final List<PortfolioCandidateService.Candidate>
            candidates = new ArrayList<>();

    private List<PortfolioCandidateService.Candidate>
            orderedCandidates = new ArrayList<>();

    private final Set<String> heldSymbols =
            new HashSet<>();

    private final Set<String> pendingBuySymbols =
            new HashSet<>();

    private PortfolioCandidateService.BuyDecision
            buyDecision;

    private boolean capitalUnavailable;
    private String portfolioOutcome;
    private boolean signalClassifiedAsFailed;
    private boolean standaloneSignalOutcomeAvailable;

    private boolean scheduledExit;
    private String preparedExitOrderType;
    private String preparedExitTimeInForce;

    private BigDecimal auctionReferencePrice;
    private BigDecimal modeledAuctionFillPrice;

    private BigDecimal stopExecutionReferencePrice;
    private BigDecimal modeledStopFillPrice;

    private BigDecimal grossProceeds;

    private PortfolioExecutionCostService.TransactionCostResult
            transactionCostResult;

    private boolean fixedUniverse;
    private boolean fixedEvaluationPeriod;
    private BigDecimal benchmarkStartingCapital;
    private boolean priceOnlyBenchmark;

    private PortfolioBenchmarkService.BenchmarkMetadata
            benchmarkMetadata;

    private boolean multipleSymbolsHeld;
    private BigDecimal calculatedMaximumDrawdown;

    /*
     * Edge-case state.
     */
    private BigDecimal buyLimitPrice;

    private PortfolioExecutionCostService.LimitAuctionFillResult
            limitAuctionFillResult;

    private BigDecimal preAuctionAvailableCapital;
    private BigDecimal expectedSameOpenExitProceeds =
            BigDecimal.ZERO;
    private boolean sameOpenExitScheduled;

    private PortfolioCapitalService.SameOpenCapitalDecision
            sameOpenCapitalDecision;

    private boolean passedEntryExposureCheck;
    private BigDecimal appreciatedPositionExposure;

    private PortfolioCapitalService.PostEntryExposureDecision
            postEntryExposureDecision;

    private BigDecimal gapThroughActiveStopPrice;
    private BigDecimal gapThroughOpeningPrice;

    private PortfolioExecutionCostService.StopGapFillResult
            stopGapFillResult;

    private PortfolioSignalOutcomeService.SignalOutcomeRecord
            signalOutcomeRecord;

    private PortfolioBenchmarkService.BuyAndHoldBenchmarkPlan
            benchmarkPlan;

    private BigDecimal benchmarkInitialModeledFill;

    @Given("portfolio governance version {string} is active")
    public void portfolioGovernanceVersionIsActive(
            String expectedVersion) {

        assertEquals(
                expectedVersion,
                definition.version()
        );
    }

    @Given("starting capital is {double}")
    public void startingCapitalIs(double amount) {

        assertDecimal(
                decimal(amount),
                definition.startingCapital()
        );
    }

    @Given("leverage is disabled")
    public void leverageIsDisabled() {

        assertFalse(
                definition.leverageEnabled()
        );

        assertFalse(
                capitalService.leverageEnabled()
        );
    }

    @Given("maximum total exposure is {int} percent of equity")
    public void maximumTotalExposureIsPercentOfEquity(
            int percent) {

        assertDecimal(
                BigDecimal.valueOf(percent)
                        .movePointLeft(2),
                definition.maximumTotalExposurePercent()
        );
    }

    @Given("maximum position exposure is {int} percent of equity")
    public void maximumPositionExposureIsPercentOfEquity(
            int percent) {

        assertDecimal(
                BigDecimal.valueOf(percent)
                        .movePointLeft(2),
                definition.maximumPositionExposurePercent()
        );
    }

    @Given("auction-fill slippage is {int} basis points")
    public void auctionFillSlippageIsBasisPoints(
            int basisPoints) {

        assertEquals(
                basisPoints,
                definition.auctionSlippageBasisPoints()
        );
    }

    @Given("stop-fill slippage is {int} basis points")
    public void stopFillSlippageIsBasisPoints(
            int basisPoints) {

        assertEquals(
                basisPoints,
                definition.stopSlippageBasisPoints()
        );
    }

    @Given("broker stock commission is {double}")
    public void brokerStockCommissionIs(
            double amount) {

        assertDecimal(
                decimal(amount),
                definition.brokerStockCommission()
        );
    }

    @When("a governed portfolio is created")
    public void aGovernedPortfolioIsCreated() {

        governedStartingCapital =
                definition.startingCapital();
    }

    @Then("portfolio starting capital should be {double}")
    public void portfolioStartingCapitalShouldBe(
            double expected) {

        assertDecimal(
                decimal(expected),
                governedStartingCapital
        );
    }

    @Then("the starting capital should be immutable")
    public void theStartingCapitalShouldBeImmutable() {

        BigDecimal original =
                definition.startingCapital();

        assertDecimal(
                original,
                governedStartingCapital
        );

        assertDecimal(
                original,
                definition.startingCapital()
        );
    }

    @Given("portfolio equity is {double}")
    public void portfolioEquityIs(double amount) {

        portfolioEquity =
                decimal(amount);
    }

    @Given("available cash is {double}")
    public void availableCashIs(double amount) {

        availableCash =
                decimal(amount);

        availableUnreservedCash =
                availableCash;
    }

    @Given("a pending BUY order has quantity {int}")
    public void aPendingBuyOrderHasQuantity(
            int quantity) {

        pendingBuyQuantity =
                quantity;
    }

    @Given("its limit price is {double}")
    public void itsLimitPriceIs(double price) {

        pendingBuyLimitPrice =
                decimal(price);
    }

    @When("the pending order is accepted")
    public void thePendingOrderIsAccepted() {

        reservedCash =
                capitalService.calculateReservedCash(
                        pendingBuyQuantity,
                        pendingBuyLimitPrice
                );

        availableUnreservedCash =
                capitalService
                        .calculateAvailableUnreservedCash(
                                availableCash,
                                reservedCash
                        );
    }

    @Then("reserved cash should be {double}")
    public void reservedCashShouldBe(
            double expected) {

        assertDecimal(
                decimal(expected),
                reservedCash
        );
    }

    @Then("available unreserved cash should be {double}")
    public void availableUnreservedCashShouldBe(
            double expected) {

        assertDecimal(
                decimal(expected),
                availableUnreservedCash
        );
    }

    @Given("a pending BUY order reserves {double}")
    public void aPendingBuyOrderReserves(
            double amount) {

        reservedCash =
                decimal(amount);

        availableUnreservedCash =
                capitalService
                        .calculateAvailableUnreservedCash(
                                availableCash,
                                reservedCash
                        );
    }

    @When("the pending order is cancelled")
    public void thePendingOrderIsCancelled() {

        reservedCash =
                BigDecimal.ZERO;

        availableUnreservedCash =
                capitalService
                        .calculateAvailableUnreservedCash(
                                availableCash,
                                reservedCash
                        );
    }

    @Given("existing position exposure is {double}")
    public void existingPositionExposureIs(
            double amount) {

        existingPositionExposure =
                decimal(amount);
    }

    @Given("pending orders reserve {double}")
    public void pendingOrdersReserve(
            double amount) {

        pendingReservedExposure =
                decimal(amount);
    }

    @When("another BUY requiring {double} of exposure is evaluated")
    public void anotherBuyRequiringExposureIsEvaluated(
            double amount) {

        proposedExposure =
                decimal(amount);

        capitalDecision =
                capitalService.evaluateAdditionalBuy(
                        portfolioEquity,
                        existingPositionExposure,
                        pendingReservedExposure,
                        proposedExposure
                );

        totalCommittedExposure =
                capitalDecision.totalCommittedExposure();
    }

    @Then("the BUY should be rejected for a capital constraint")
    public void theBuyShouldBeRejectedForACapitalConstraint() {

        if (sameOpenCapitalDecision != null) {

            assertFalse(
                    sameOpenCapitalDecision.approved()
            );

            assertEquals(
                    definition.capitalConstraintOutcome(),
                    sameOpenCapitalDecision.outcome()
            );

            return;
        }

        assertNotNull(
                capitalDecision
        );

        assertFalse(
                capitalDecision.approved()
        );

        assertEquals(
                definition.capitalConstraintOutcome(),
                capitalDecision.outcome()
        );
    }

    @Then("total committed exposure should not exceed {double}")
    public void totalCommittedExposureShouldNotExceed(
            double maximum) {

        assertNotNull(
                totalCommittedExposure
        );

        assertTrue(
                totalCommittedExposure.compareTo(
                        decimal(maximum)
                ) <= 0
        );
    }

    @Given("total committed exposure is {double}")
    public void totalCommittedExposureIs(
            double amount) {

        existingPositionExposure =
                decimal(amount);

        pendingReservedExposure =
                BigDecimal.ZERO;

        totalCommittedExposure =
                existingPositionExposure;
    }

    @Then("borrowed capital should remain {double}")
    public void borrowedCapitalShouldRemain(
            double expected) {

        assertFalse(
                capitalService.leverageEnabled()
        );

        assertDecimal(
                decimal(expected),
                capitalService.borrowedCapital()
        );
    }

    @When("a proposed position has exposure of {double}")
    public void aProposedPositionHasExposureOf(
            double amount) {

        proposedExposure =
                decimal(amount);

        positionExposureDecision =
                capitalService
                        .evaluatePositionExposure(
                                portfolioEquity,
                                proposedExposure
                        );
    }

    @Then("the proposed position should be rejected")
    public void theProposedPositionShouldBeRejected() {

        assertNotNull(
                positionExposureDecision
        );

        assertFalse(
                positionExposureDecision.approved()
        );
    }

    @Then("the rejection reason should be {string}")
    public void theRejectionReasonShouldBe(
            String expected) {

        assertNotNull(
                positionExposureDecision
        );

        assertEquals(
                expected,
                positionExposureDecision
                        .rejectionReason()
        );
    }

    @Given("the following same-day BUY candidates:")
    public void theFollowingSameDayBuyCandidates(
            DataTable table) {

        candidates.clear();

        List<Map<String, String>> rows =
                table.asMaps(
                        String.class,
                        String.class
                );

        for (Map<String, String> row : rows) {

            candidates.add(
                    new PortfolioCandidateService.Candidate(
                            row.get("symbol"),
                            Integer.parseInt(
                                    row.get("confidence")
                            )
                    )
            );
        }
    }

    @When("portfolio candidates are ordered")
    public void portfolioCandidatesAreOrdered() {

        orderedCandidates =
                candidateService.orderCandidates(
                        candidates
                );
    }

    @Then("the candidate order should be:")
    public void theCandidateOrderShouldBe(
            DataTable table) {

        List<String> expectedSymbols =
                table.asMaps(
                                String.class,
                                String.class
                        )
                        .stream()
                        .map(
                                row -> row.get("symbol")
                        )
                        .toList();

        List<String> actualSymbols =
                orderedCandidates.stream()
                        .map(
                                PortfolioCandidateService
                                        .Candidate::symbol
                        )
                        .toList();

        assertEquals(
                expectedSymbols,
                actualSymbols
        );
    }

    @Given("symbol {string} is already held")
    public void symbolIsAlreadyHeld(
            String symbol) {

        heldSymbols.add(
                symbol
        );
    }

    @Given("symbol {string} already has a pending BUY order")
    public void symbolAlreadyHasAPendingBuyOrder(
            String symbol) {

        pendingBuySymbols.add(
                symbol
        );
    }

    @When("a new BUY signal is received for {string}")
    public void aNewBuySignalIsReceivedFor(
            String symbol) {

        buyDecision =
                candidateService.evaluateBuySignal(
                        symbol,
                        heldSymbols,
                        pendingBuySymbols
                );
    }

    @Then("no new BUY order should be created")
    public void noNewBuyOrderShouldBeCreated() {

        assertNotNull(
                buyDecision
        );

        assertFalse(
                buyDecision.createOrder()
        );
    }

    @Then("the duplicate BUY should be recorded as ignored")
    public void theDuplicateBuyShouldBeRecordedAsIgnored() {

        assertNotNull(
                buyDecision
        );

        assertTrue(
                buyDecision.duplicateIgnored()
        );

        assertEquals(
                "DUPLICATE_BUY_IGNORED",
                buyDecision.outcome()
        );
    }

    @Given("symbol {string} was previously held")
    public void symbolWasPreviouslyHeld(
            String symbol) {

        heldSymbols.add(
                symbol
        );
    }

    @Given("the {string} position has exited")
    public void thePositionHasExited(
            String symbol) {

        heldSymbols.remove(
                symbol
        );
    }

    @Given("no BUY order for {string} is pending")
    public void noBuyOrderForIsPending(
            String symbol) {

        pendingBuySymbols.remove(
                symbol
        );
    }

    @Then("a new BUY order may be created")
    public void aNewBuyOrderMayBeCreated() {

        assertNotNull(
                buyDecision
        );

        assertTrue(
                buyDecision.createOrder()
        );

        assertTrue(
                buyDecision.reentryAllowed()
        );
    }

    @Given("a valid BUY signal cannot be accepted because capital is unavailable")
    public void aValidBuySignalCannotBeAcceptedBecauseCapitalIsUnavailable() {

        capitalUnavailable = true;

        signalClassifiedAsFailed = false;

        standaloneSignalOutcomeAvailable = true;
    }

    @When("the portfolio rejects the executable entry")
    public void thePortfolioRejectsTheExecutableEntry() {

        assertTrue(
                capitalUnavailable
        );

        portfolioOutcome =
                definition.capitalConstraintOutcome();
    }

    @Then("the portfolio outcome should be {string}")
    public void thePortfolioOutcomeShouldBe(
            String expected) {

        assertEquals(
                expected,
                portfolioOutcome
        );
    }

    @Then("the signal should not be classified as a failed signal")
    public void theSignalShouldNotBeClassifiedAsAFailedSignal() {

        assertFalse(
                signalClassifiedAsFailed
        );
    }

    @Then("the standalone signal outcome should remain available for research")
    public void theStandaloneSignalOutcomeShouldRemainAvailableForResearch() {

        assertTrue(
                standaloneSignalOutcomeAvailable
        );
    }

    @Given("a long position has a scheduled exit")
    public void aLongPositionHasAScheduledExit() {

        scheduledExit = true;
    }

    @When("the exit order is prepared")
    public void theExitOrderIsPrepared() {

        assertTrue(
                scheduledExit
        );

        preparedExitOrderType =
                executionCostService
                        .scheduledExitOrderType();

        preparedExitTimeInForce =
                executionCostService
                        .scheduledExitTimeInForce();
    }

    @Then("the order type should be {string}")
    public void theOrderTypeShouldBe(
            String expected) {

        assertEquals(
                expected,
                preparedExitOrderType
        );
    }

    @Then("the time in force should be {string}")
    public void theTimeInForceShouldBe(
            String expected) {

        assertEquals(
                expected,
                preparedExitTimeInForce
        );
    }

    @Given("an auction reference price of {double}")
    public void anAuctionReferencePriceOf(
            double price) {

        auctionReferencePrice =
                decimal(price);
    }

    @When("BUY auction slippage is applied")
    public void buyAuctionSlippageIsApplied() {

        modeledAuctionFillPrice =
                executionCostService
                        .applyBuyAuctionSlippage(
                                auctionReferencePrice
                        );
    }

    @Then("the modeled BUY fill price should be {double}")
    public void theModeledBuyFillPriceShouldBe(
            double expected) {

        BigDecimal actual;

        if (limitAuctionFillResult != null) {
            actual =
                    limitAuctionFillResult
                            .modeledFillPrice();
        } else {
            actual =
                    modeledAuctionFillPrice;
        }

        assertDecimal(
                decimal(expected),
                actual
        );
    }

    @Given("a stop execution reference price of {double}")
    public void aStopExecutionReferencePriceOf(
            double price) {

        stopExecutionReferencePrice =
                decimal(price);
    }

    @When("SELL stop slippage is applied")
    public void sellStopSlippageIsApplied() {

        modeledStopFillPrice =
                executionCostService
                        .applySellStopSlippage(
                                stopExecutionReferencePrice
                        );
    }

    @Then("the modeled SELL fill price should be {double}")
    public void theModeledSellFillPriceShouldBe(
            double expected) {

        BigDecimal actual;

        if (stopGapFillResult != null) {
            actual =
                    stopGapFillResult
                            .modeledFillPrice();
        } else {
            actual =
                    modeledStopFillPrice;
        }

        assertDecimal(
                decimal(expected),
                actual
        );
    }

    @Given("a stock trade has gross proceeds of {double}")
    public void aStockTradeHasGrossProceedsOf(
            double amount) {

        grossProceeds =
                decimal(amount);
    }

    @When("transaction costs are calculated")
    public void transactionCostsAreCalculated() {

        transactionCostResult =
                executionCostService
                        .calculateTransactionCosts(
                                grossProceeds
                        );
    }

    @Then("broker commission should be {double}")
    public void brokerCommissionShouldBe(
            double expected) {

        assertNotNull(
                transactionCostResult
        );

        assertDecimal(
                decimal(expected),
                transactionCostResult
                        .brokerCommission()
        );
    }

    @Then("regulatory fees should be calculated separately")
    public void regulatoryFeesShouldBeCalculatedSeparately() {

        assertNotNull(
                transactionCostResult
        );

        assertTrue(
                transactionCostResult
                        .regulatoryFeesSeparate()
        );
    }

    @Then("the regulatory fee model version should be recorded")
    public void theRegulatoryFeeModelVersionShouldBeRecorded() {

        assertNotNull(
                transactionCostResult
        );

        assertEquals(
                definition.regulatoryFeeModelVersion(),
                transactionCostResult
                        .regulatoryFeeModelVersion()
        );
    }

    @Given("a governed strategy run has a fixed universe")
    public void aGovernedStrategyRunHasAFixedUniverse() {

        fixedUniverse = true;
    }

    @Given("a fixed evaluation period")
    public void aFixedEvaluationPeriod() {

        fixedEvaluationPeriod = true;
    }

    @Given("starting capital of {double}")
    public void startingCapitalOf(
            double amount) {

        benchmarkStartingCapital =
                decimal(amount);
    }

    @When("the buy-and-hold benchmark is created")
    public void theBuyAndHoldBenchmarkIsCreated() {

        benchmarkMetadata =
                benchmarkService
                        .createBenchmarkMetadata(
                                fixedUniverse,
                                fixedEvaluationPeriod,
                                benchmarkStartingCapital,
                                false
                        );
    }

    @Then("the benchmark should use the same universe")
    public void theBenchmarkShouldUseTheSameUniverse() {

        assertNotNull(
                benchmarkMetadata
        );

        assertTrue(
                benchmarkMetadata.sameUniverse()
        );
    }

    @Then("the benchmark should use the same evaluation period")
    public void theBenchmarkShouldUseTheSameEvaluationPeriod() {

        assertNotNull(
                benchmarkMetadata
        );

        assertTrue(
                benchmarkMetadata
                        .sameEvaluationPeriod()
        );
    }

    @Then("the benchmark should use starting capital of {double}")
    public void theBenchmarkShouldUseStartingCapitalOf(
            double expected) {

        assertNotNull(
                benchmarkMetadata
        );

        assertDecimal(
                decimal(expected),
                benchmarkMetadata
                        .startingCapital()
        );
    }

    @Given("the benchmark uses price-only market data")
    public void theBenchmarkUsesPriceOnlyMarketData() {

        priceOnlyBenchmark = true;
    }

    @When("benchmark metadata is recorded")
    public void benchmarkMetadataIsRecorded() {

        benchmarkMetadata =
                benchmarkService
                        .createBenchmarkMetadata(
                                true,
                                true,
                                definition.startingCapital(),
                                priceOnlyBenchmark
                        );
    }

    @Then("the limitation {string} should be recorded")
    public void theLimitationShouldBeRecorded(
            String limitation) {

        assertNotNull(
                benchmarkMetadata
        );

        assertTrue(
                benchmarkService.hasLimitation(
                        benchmarkMetadata,
                        limitation
                )
        );
    }

    @Given("multiple symbols are held in the governed portfolio")
    public void multipleSymbolsAreHeldInTheGovernedPortfolio() {

        multipleSymbolsHeld = true;
    }

    @When("maximum drawdown is calculated")
    public void maximumDrawdownIsCalculated() {

        assertTrue(
                multipleSymbolsHeld
        );

        List<BigDecimal> combinedEquityCurve =
                List.of(
                        new BigDecimal("100000.00"),
                        new BigDecimal("105000.00"),
                        new BigDecimal("101000.00"),
                        new BigDecimal("99000.00"),
                        new BigDecimal("110000.00")
                );

        calculatedMaximumDrawdown =
                drawdownService
                        .calculateMaximumDrawdown(
                                combinedEquityCurve
                        );
    }

    @Then("drawdown should be measured from the combined portfolio equity curve")
    public void drawdownShouldBeMeasuredFromTheCombinedPortfolioEquityCurve() {

        assertNotNull(
                calculatedMaximumDrawdown
        );

        assertTrue(
                drawdownService
                        .usesCombinedPortfolioEquityCurve()
        );
    }

    @Then("drawdown should not be calculated independently per symbol")
    public void drawdownShouldNotBeCalculatedIndependentlyPerSymbol() {

        assertFalse(
                drawdownService
                        .usesPerSymbolDrawdown()
        );
    }

    /*
     * ---------------------------------------------------------
     * Portfolio Governance v1 edge cases
     * ---------------------------------------------------------
     */

    @Given("a BUY limit price of {double}")
    public void aBuyLimitPriceOf(
            double price) {

        buyLimitPrice =
                decimal(price);

        /*
         * One-share reservation is enough for this scenario
         * to prove that modeled execution cannot exceed the
         * actual limit-price reservation.
         */
        reservedCash =
                capitalService.calculateReservedCash(
                        1,
                        buyLimitPrice
                );
    }

    @When("BUY auction slippage is applied to the limit order")
    public void buyAuctionSlippageIsAppliedToTheLimitOrder() {

        limitAuctionFillResult =
                executionCostService
                        .evaluateBuyLimitAuctionFill(
                                auctionReferencePrice,
                                buyLimitPrice
                        );

        assertTrue(
                limitAuctionFillResult.filled()
        );
    }

    @Then("reserved cash should not exceed the limit-price reservation")
    public void reservedCashShouldNotExceedTheLimitPriceReservation() {

        assertNotNull(
                limitAuctionFillResult
        );

        assertTrue(
                limitAuctionFillResult
                        .modeledFillPrice()
                        .compareTo(
                                buyLimitPrice
                        ) <= 0
        );

        assertDecimal(
                buyLimitPrice,
                reservedCash
        );
    }

    @Given("capital available before the opening auction is {double}")
    public void capitalAvailableBeforeTheOpeningAuctionIs(
            double amount) {

        preAuctionAvailableCapital =
                decimal(amount);
    }

    @Given("a position is scheduled to exit at the same opening auction")
    public void aPositionIsScheduledToExitAtTheSameOpeningAuction() {

        sameOpenExitScheduled = true;
    }

    @Given("expected same-open exit proceeds are {double}")
    public void expectedSameOpenExitProceedsAre(
            double amount) {

        expectedSameOpenExitProceeds =
                decimal(amount);
    }

    @When("a new opening BUY requiring {double} is evaluated")
    public void aNewOpeningBuyRequiringIsEvaluated(
            double requiredCapital) {

        assertTrue(
                sameOpenExitScheduled
        );

        sameOpenCapitalDecision =
                capitalService
                        .evaluateOpeningBuyUsingPreAuctionCapital(
                                preAuctionAvailableCapital,
                                decimal(requiredCapital),
                                expectedSameOpenExitProceeds
                        );
    }

    @Then("the exit proceeds should not be available to the new BUY")
    public void theExitProceedsShouldNotBeAvailableToTheNewBuy() {

        assertNotNull(
                sameOpenCapitalDecision
        );

        assertFalse(
                sameOpenCapitalDecision
                        .exitProceedsAvailableToNewBuy()
        );

        assertDecimal(
                preAuctionAvailableCapital,
                sameOpenCapitalDecision
                        .usablePreAuctionCapital()
        );

        assertDecimal(
                expectedSameOpenExitProceeds,
                sameOpenCapitalDecision
                        .excludedSameOpenExitProceeds()
        );
    }

    @Then("the governed account type should be {string}")
    public void theGovernedAccountTypeShouldBe(
            String expected) {

        assertEquals(
                expected,
                capitalService.accountType()
        );
    }

    @Then("strategy leverage should remain disabled")
    public void strategyLeverageShouldRemainDisabled() {

        assertFalse(
                capitalService.leverageEnabled()
        );

        assertDecimal(
                BigDecimal.ZERO,
                capitalService.borrowedCapital()
        );
    }

    @Given("a position satisfied the five percent exposure limit at entry")
    public void aPositionSatisfiedTheFivePercentExposureLimitAtEntry() {

        portfolioEquity =
                definition.startingCapital();

        passedEntryExposureCheck =
                true;

        BigDecimal maximumEntryExposure =
                capitalService
                        .calculateMaximumPositionExposure(
                                portfolioEquity
                        );

        PortfolioCapitalService.PositionExposureDecision
                entryDecision =
                capitalService
                        .evaluatePositionExposure(
                                portfolioEquity,
                                maximumEntryExposure
                        );

        assertTrue(
                entryDecision.approved()
        );
    }

    @Given("the position appreciates above five percent of portfolio equity")
    public void thePositionAppreciatesAboveFivePercentOfPortfolioEquity() {

        BigDecimal maximumEntryExposure =
                capitalService
                        .calculateMaximumPositionExposure(
                                portfolioEquity
                        );

        appreciatedPositionExposure =
                maximumEntryExposure.multiply(
                        new BigDecimal("1.20")
                );

        assertTrue(
                appreciatedPositionExposure.compareTo(
                        maximumEntryExposure
                ) > 0
        );
    }

    @When("portfolio exposure is marked to market")
    public void portfolioExposureIsMarkedToMarket() {

        postEntryExposureDecision =
                capitalService
                        .evaluatePostEntryAppreciation(
                                portfolioEquity,
                                appreciatedPositionExposure,
                                passedEntryExposureCheck
                        );
    }

    @Then("no automatic trim should be scheduled")
    public void noAutomaticTrimShouldBeScheduled() {

        assertNotNull(
                postEntryExposureDecision
        );

        assertTrue(
                postEntryExposureDecision
                        .currentlyAboveEntryLimit()
        );

        assertTrue(
                postEntryExposureDecision
                        .exposureCapAppliesAtEntryOnly()
        );

        assertFalse(
                postEntryExposureDecision
                        .automaticTrimScheduled()
        );

        assertFalse(
                capitalService
                        .shouldAutoTrimForAppreciation(
                                portfolioEquity,
                                appreciatedPositionExposure
                        )
        );
    }

    @Given("the portfolio gap-through active stop price is {double}")
    public void thePortfolioGapThroughActiveStopPriceIs(
            double price) {

        gapThroughActiveStopPrice =
                decimal(price);
    }

    @Given("the portfolio gap-through opening price is {double}")
    public void thePortfolioGapThroughOpeningPriceIs(
            double price) {

        gapThroughOpeningPrice =
                decimal(price);
    }

    @When("SELL stop slippage is applied to the gap-through execution")
    public void sellStopSlippageIsAppliedToTheGapThroughExecution() {

        stopGapFillResult =
                executionCostService
                        .evaluateGapThroughStop(
                                gapThroughActiveStopPrice,
                                gapThroughOpeningPrice
                        );
    }

    @Then("the stop execution reference price should be {double}")
    public void theStopExecutionReferencePriceShouldBe(
            double expected) {

        assertNotNull(
                stopGapFillResult
        );

        assertDecimal(
                decimal(expected),
                stopGapFillResult
                        .executionReferencePrice()
        );
    }

    @Given("a valid BUY signal is skipped for a capital constraint")
    public void aValidBuySignalIsSkippedForACapitalConstraint() {

        capitalUnavailable = true;

        portfolioOutcome =
                definition.capitalConstraintOutcome();
    }

    @When("research outcomes are recorded")
    public void researchOutcomesAreRecorded() {

        assertTrue(
                capitalUnavailable
        );

        signalOutcomeRecord =
                signalOutcomeService
                        .recordCapitalConstrainedSignal(
                                "TSLA",
                                75
                        );
    }

    @Then("the execution outcome should be {string}")
    public void theExecutionOutcomeShouldBe(
            String expected) {

        assertNotNull(
                signalOutcomeRecord
        );

        assertEquals(
                expected,
                signalOutcomeRecord
                        .executionOutcome()
        );

        assertFalse(
                signalOutcomeRecord
                        .executableTradeCreated()
        );
    }

    @Then("a standalone trade outcome should still be recorded")
    public void aStandaloneTradeOutcomeShouldStillBeRecorded() {

        assertNotNull(
                signalOutcomeRecord
        );

        assertTrue(
                signalOutcomeRecord
                        .standaloneTradeOutcomeRecorded()
        );

        assertEquals(
                1,
                signalOutcomeService.size()
        );
    }

    @Then("the signal should remain eligible for confidence-band analysis")
    public void theSignalShouldRemainEligibleForConfidenceBandAnalysis() {

        assertNotNull(
                signalOutcomeRecord
        );

        assertTrue(
                signalOutcomeRecord
                        .confidenceBandEligible()
        );
    }

    @Given("an equal-weight buy-and-hold benchmark is created")
    public void anEqualWeightBuyAndHoldBenchmarkIsCreated() {

        benchmarkPlan =
                benchmarkService
                        .createEqualWeightBuyAndHoldBenchmark();

        assertTrue(
                benchmarkPlan
                        .equalWeightInitialAllocation()
        );
    }

    @When("the benchmark enters its initial positions")
    public void theBenchmarkEntersItsInitialPositions() {

        benchmarkInitialModeledFill =
                benchmarkService
                        .applyInitialPurchaseSlippage(
                                new BigDecimal("100.00")
                        );
    }

    @Then("initial purchases should use {int} basis points of BUY auction slippage")
    public void initialPurchasesShouldUseBasisPointsOfBuyAuctionSlippage(
            int expectedBasisPoints) {

        assertNotNull(
                benchmarkPlan
        );

        assertEquals(
                expectedBasisPoints,
                benchmarkPlan
                        .initialBuyAuctionSlippageBasisPoints()
        );

        assertEquals(
                expectedBasisPoints,
                benchmarkService
                        .initialBuyAuctionSlippageBasisPoints()
        );

        /*
         * At a 100.00 auction reference price,
         * the frozen 2 bps benchmark assumption
         * produces a 100.02 modeled initial fill.
         */
        assertDecimal(
                new BigDecimal("100.02"),
                benchmarkInitialModeledFill
        );
    }

    @Then("the benchmark should not rebalance after initial purchase")
    public void theBenchmarkShouldNotRebalanceAfterInitialPurchase() {

        assertNotNull(
                benchmarkPlan
        );

        assertFalse(
                benchmarkPlan
                        .rebalanceAfterInitialPurchase()
        );

        assertFalse(
                benchmarkService
                        .rebalancesAfterInitialPurchase()
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