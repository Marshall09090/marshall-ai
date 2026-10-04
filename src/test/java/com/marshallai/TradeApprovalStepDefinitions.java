package com.marshallai;

import com.marshallai.risk.EventRisk;
import com.marshallai.risk.PositionSizeResult;
import com.marshallai.risk.TradeApprovalResult;
import com.marshallai.risk.TradeApprovalService;
import com.marshallai.signal.MarketSignal;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TradeApprovalStepDefinitions {

    @Autowired
    private TradeApprovalService tradeApprovalService;

    private MarketSignal marketSignal;
    private EventRisk eventRisk;
    private PositionSizeResult positionSizeResult;
    private TradeApprovalResult tradeApprovalResult;

    @Given("the trade approval signal is {string} with {int} percent confidence")
    public void theTradeApprovalSignalIsWithPercentConfidence(
            String decision,
            int confidencePercent) {

        int bullishScore = 0;
        int bearishScore = 0;

        if ("BUY".equalsIgnoreCase(decision)) {
            bullishScore = 10;
        } else if ("SELL".equalsIgnoreCase(decision)) {
            bearishScore = 10;
        }

        marketSignal = new MarketSignal(
                MarketSignal.Decision.valueOf(
                        decision.toUpperCase()
                ),
                confidencePercent,
                bullishScore,
                bearishScore,
                bullishScore,
                bearishScore
        );
    }

    @Given("the trade approval event risk is {string}")
    public void theTradeApprovalEventRiskIs(
            String riskLevel) {

        eventRisk = new EventRisk(
                EventRisk.Level.valueOf(
                        riskLevel.toUpperCase()
                ),
                "TEST",
                "Trade approval test event"
        );
    }

    @Given("the trade approval position quantity is {int}")
    public void theTradeApprovalPositionQuantityIs(
            int quantity) {

        positionSizeResult = createPositionSize(
                quantity
        );
    }

    @When("MarshallAI evaluates final trade approval")
    public void marshallAIEvaluatesFinalTradeApproval() {

        tradeApprovalResult =
                tradeApprovalService.evaluate(
                        marketSignal,
                        eventRisk,
                        positionSizeResult
                );
    }

    @Then("the final trade status should be {string}")
    public void theFinalTradeStatusShouldBe(
            String expectedStatus) {

        assertNotNull(
                tradeApprovalResult,
                "Trade approval result should not be null"
        );

        assertEquals(
                TradeApprovalResult.Status.valueOf(
                        expectedStatus.toUpperCase()
                ),
                tradeApprovalResult.getStatus()
        );
    }

    @Then("the final trade decision should be {string}")
    public void theFinalTradeDecisionShouldBe(
            String expectedDecision) {

        assertNotNull(
                tradeApprovalResult,
                "Trade approval result should not be null"
        );

        assertEquals(
                expectedDecision,
                tradeApprovalResult.getTradingDecision()
        );
    }

    @Then("the final trade confidence should be {int} percent")
    public void theFinalTradeConfidenceShouldBe(
            int expectedConfidence) {

        assertNotNull(
                tradeApprovalResult,
                "Trade approval result should not be null"
        );

        assertEquals(
                expectedConfidence,
                tradeApprovalResult.getConfidencePercent()
        );
    }

    private PositionSizeResult createPositionSize(
            int quantity) {

        BigDecimal entryPrice =
                new BigDecimal("50.00");

        BigDecimal stopLossPrice =
                new BigDecimal("49.00");

        BigDecimal riskPerUnit =
                new BigDecimal("1.00");

        BigDecimal riskAmount =
                BigDecimal.valueOf(quantity);

        BigDecimal positionValue =
                entryPrice.multiply(
                        BigDecimal.valueOf(quantity)
                );

        return new PositionSizeResult(
                new BigDecimal("500.00"),
                new BigDecimal("1.00"),
                riskAmount,
                entryPrice,
                stopLossPrice,
                riskPerUnit,
                quantity,
                positionValue
        );
    }
}