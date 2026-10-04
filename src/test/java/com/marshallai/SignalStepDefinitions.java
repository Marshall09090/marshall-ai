package com.marshallai;

import com.marshallai.analysis.MarketAnalysisResult;
import com.marshallai.signal.MarketSignal;
import com.marshallai.signal.SignalService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SignalStepDefinitions {

    @Autowired
    private SignalService signalService;

    private List<String> detectedPatterns;

    private MarketSignal marketSignal;

    @Given("the market analysis detected the following patterns:")
    public void theMarketAnalysisDetectedTheFollowingPatterns(
            DataTable dataTable) {

        detectedPatterns =
                dataTable
                        .asMaps(String.class, String.class)
                        .stream()
                        .map(row -> row.get("pattern"))
                        .toList();
    }

    @When("MarshallAi evaluates the trading signal")
    public void marshallAiEvaluatesTheTradingSignal() {

        MarketAnalysisResult marketAnalysisResult =
                new MarketAnalysisResult(
                        detectedPatterns,
                        MarketAnalysisResult.Direction.NEUTRAL
                );

        marketSignal =
                signalService.evaluate(
                        marketAnalysisResult
                );
    }

    @Then("the trading decision should be {string}")
    public void theTradingDecisionShouldBe(
            String expectedDecision) {

        assertNotNull(
                marketSignal,
                "Market signal should not be null"
        );

        assertEquals(
                expectedDecision,
                marketSignal.getDecision().name(),
                "Unexpected trading decision"
        );
    }

    @Then("the confidence score should be {int} percent")
    public void theConfidenceScoreShouldBe(
            int expectedConfidencePercent) {

        assertNotNull(
                marketSignal,
                "Market signal should not be null"
        );

        assertEquals(
                expectedConfidencePercent,
                marketSignal.getConfidencePercent(),
                "Unexpected confidence score"
        );
    }

    @Then("the bullish signal count should be {int}")
    public void theBullishSignalCountShouldBe(
            int expectedCount) {

        assertEquals(
                expectedCount,
                marketSignal.getBullishSignalCount(),
                "Unexpected bullish signal count"
        );
    }

    @Then("the bearish signal count should be {int}")
    public void theBearishSignalCountShouldBe(
            int expectedCount) {

        assertEquals(
                expectedCount,
                marketSignal.getBearishSignalCount(),
                "Unexpected bearish signal count"
        );
    }

    @Then("the bullish weighted score should be {int}")
    public void theBullishWeightedScoreShouldBe(
            int expectedScore) {

        assertEquals(
                expectedScore,
                marketSignal.getBullishWeightedScore(),
                "Unexpected bullish weighted score"
        );
    }

    @Then("the bearish weighted score should be {int}")
    public void theBearishWeightedScoreShouldBe(
            int expectedScore) {

        assertEquals(
                expectedScore,
                marketSignal.getBearishWeightedScore(),
                "Unexpected bearish weighted score"
        );
    }
}