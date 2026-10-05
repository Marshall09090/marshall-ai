package com.marshallai;

import com.marshallai.analysis.MarketAnalysisResult;
import com.marshallai.risk.EventRisk;
import com.marshallai.signal.MarketSignal;
import com.marshallai.signal.SignalService;
import com.marshallai.signal.TechnicalSignal;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CombinedSignalStepDefinitions {

    @Autowired
    private SignalService signalService;

    private List<String> detectedPatterns;

    private TechnicalSignal technicalSignal;

    private EventRisk eventRisk;

    private MarketSignal combinedSignal;

    @Given("the combined analysis detected the following patterns:")
    public void theCombinedAnalysisDetectedTheFollowingPatterns(
            DataTable dataTable) {

        detectedPatterns =
                dataTable
                        .asMaps(String.class, String.class)
                        .stream()
                        .map(row -> row.get("pattern"))
                        .toList();
    }

    @Given("the technical engine produced bullish score {int} and bearish score {int}")
    public void theTechnicalEngineProducedBullishScoreAndBearishScore(
            int bullishScore,
            int bearishScore) {

        int totalScore =
                bullishScore + bearishScore;

        int confidencePercent = 0;

        if (totalScore > 0) {

            int dominantScore =
                    Math.max(
                            bullishScore,
                            bearishScore
                    );

            confidencePercent =
                    (int) Math.round(
                            dominantScore
                                    * 100.0
                                    / totalScore
                    );
        }

        technicalSignal =
                new TechnicalSignal(
                        bullishScore,
                        bearishScore,
                        confidencePercent
                );
    }

    @Given("the combined signal has event risk level {string}")
    public void theCombinedSignalHasEventRiskLevel(
            String riskLevel) {

        eventRisk =
                new EventRisk(
                        EventRisk.Level.valueOf(riskLevel),
                        "TEST_EVENT",
                        "Combined signal event risk test"
                );
    }
    @Given("the combined analysis detected no patterns")
    public void theCombinedAnalysisDetectedNoPatterns() {

        detectedPatterns = List.of();
    }

    @When("MarshallAI evaluates the combined trading signal")
    public void marshallAIEvaluatesTheCombinedTradingSignal() {

        MarketAnalysisResult marketAnalysisResult =
                buildMarketAnalysisResult();

        combinedSignal =
                signalService.evaluate(
                        marketAnalysisResult,
                        technicalSignal
                );
    }

    @When("MarshallAI evaluates the risk-gated combined trading signal")
    public void marshallAIEvaluatesTheRiskGatedCombinedTradingSignal() {

        MarketAnalysisResult marketAnalysisResult =
                buildMarketAnalysisResult();

        combinedSignal =
                signalService.evaluate(
                        marketAnalysisResult,
                        technicalSignal,
                        eventRisk
                );
    }

    @Then("the combined bullish weighted score should be {int}")
    public void theCombinedBullishWeightedScoreShouldBe(
            int expectedScore) {

        assertNotNull(
                combinedSignal,
                "Combined signal should not be null"
        );

        assertEquals(
                expectedScore,
                combinedSignal.getBullishWeightedScore(),
                "Unexpected combined bullish weighted score"
        );
    }

    @Then("the combined bearish weighted score should be {int}")
    public void theCombinedBearishWeightedScoreShouldBe(
            int expectedScore) {

        assertNotNull(
                combinedSignal,
                "Combined signal should not be null"
        );

        assertEquals(
                expectedScore,
                combinedSignal.getBearishWeightedScore(),
                "Unexpected combined bearish weighted score"
        );
    }

    @Then("the combined confidence score should be {int} percent")
    public void theCombinedConfidenceScoreShouldBe(
            int expectedConfidence) {

        assertNotNull(
                combinedSignal,
                "Combined signal should not be null"
        );

        assertEquals(
                expectedConfidence,
                combinedSignal.getConfidencePercent(),
                "Unexpected combined confidence score"
        );
    }

    @Then("the combined trading decision should be {string}")
    public void theCombinedTradingDecisionShouldBe(
            String expectedDecision) {

        assertNotNull(
                combinedSignal,
                "Combined signal should not be null"
        );

        assertEquals(
                expectedDecision,
                combinedSignal.getDecision().name(),
                "Unexpected combined trading decision"
        );
    }

    private MarketAnalysisResult buildMarketAnalysisResult() {

        return new MarketAnalysisResult(
                detectedPatterns,
                MarketAnalysisResult.Direction.NEUTRAL
        );
    }
}