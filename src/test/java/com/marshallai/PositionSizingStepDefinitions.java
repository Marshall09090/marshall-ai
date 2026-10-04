package com.marshallai;

import com.marshallai.risk.PositionSizeResult;
import com.marshallai.risk.PositionSizingService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PositionSizingStepDefinitions {

    @Autowired
    private PositionSizingService positionSizingService;

    private BigDecimal accountBalance;
    private BigDecimal riskPercent;
    private BigDecimal entryPrice;
    private BigDecimal stopLossPrice;

    private PositionSizeResult result;

    @Given("a trading account balance of {double} dollars")
    public void aTradingAccountBalanceOfDollars(
            double balance) {

        accountBalance =
                BigDecimal.valueOf(balance);
    }

    @Given("the maximum trade risk is {double} percent")
    public void theMaximumTradeRiskIsPercent(
            double risk) {

        riskPercent =
                BigDecimal.valueOf(risk);
    }

    @Given("the planned entry price is {double} dollars")
    public void thePlannedEntryPriceIsDollars(
            double price) {

        entryPrice =
                BigDecimal.valueOf(price);
    }

    @Given("the planned stop loss price is {double} dollars")
    public void thePlannedStopLossPriceIsDollars(
            double price) {

        stopLossPrice =
                BigDecimal.valueOf(price);
    }

    @When("MarshallAI calculates the position size")
    public void marshallAICalculatesThePositionSize() {

        result =
                positionSizingService.calculate(
                        accountBalance,
                        riskPercent,
                        entryPrice,
                        stopLossPrice
                );
    }

    @Then("the maximum dollar risk should be {double} dollars")
    public void theMaximumDollarRiskShouldBeDollars(
            double expectedRisk) {

        assertEquals(
                0,
                result.getRiskAmount().compareTo(
                        BigDecimal.valueOf(expectedRisk)
                )
        );
    }

    @Then("the risk per unit should be {double} dollars")
    public void theRiskPerUnitShouldBeDollars(
            double expectedRiskPerUnit) {

        assertEquals(
                0,
                result.getRiskPerUnit().compareTo(
                        BigDecimal.valueOf(expectedRiskPerUnit)
                )
        );
    }

    @Then("the position quantity should be {int}")
    public void thePositionQuantityShouldBe(
            int expectedQuantity) {

        assertEquals(
                expectedQuantity,
                result.getQuantity()
        );
    }

    @Then("the position value should be {double} dollars")
    public void thePositionValueShouldBeDollars(
            double expectedValue) {

        assertEquals(
                0,
                result.getPositionValue().compareTo(
                        BigDecimal.valueOf(expectedValue)
                )
        );
    }
}