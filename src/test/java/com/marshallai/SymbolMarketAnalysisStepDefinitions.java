package com.marshallai;

import com.marshallai.market.SymbolMarketAnalysisService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SymbolMarketAnalysisStepDefinitions {

    @Autowired
    private SymbolMarketAnalysisService symbolMarketAnalysisService;

    private SymbolMarketAnalysisService.SymbolMarketAnalysisResult result;

    private Exception capturedException;

    @Given("historical market data is available for symbol {string}")
    public void historicalMarketDataIsAvailableForSymbol(
            String symbol) {

        assertNotNull(symbol);
    }

    @When("MarshallAi analyzes symbol {string} using {int} candles")
    public void marshallAiAnalyzesSymbolUsingCandles(
            String symbol,
            int candleCount) {

        result =
                symbolMarketAnalysisService.analyze(
                        symbol,
                        candleCount
                );
    }

    @Then("the symbol analysis should contain {int} market candles")
    public void theSymbolAnalysisShouldContainMarketCandles(
            int expectedCount) {

        assertNotNull(
                result,
                "Symbol analysis result should not be null"
        );

        assertNotNull(
                result.candles(),
                "Market candles should not be null"
        );

        assertEquals(
                expectedCount,
                result.candles().size(),
                "Unexpected number of market candles"
        );
    }

    @Then("the symbol analysis result should not be null")
    public void theSymbolAnalysisResultShouldNotBeNull() {

        assertNotNull(
                result,
                "Symbol analysis result should not be null"
        );

        assertNotNull(
                result.marketAnalysis(),
                "Market analysis should not be null"
        );
    }

    @Then("the symbol technical signal should not be null")
    public void theSymbolTechnicalSignalShouldNotBeNull() {

        assertNotNull(
                result,
                "Symbol analysis result should not be null"
        );

        assertNotNull(
                result.technicalSignal(),
                "Technical signal should not be null"
        );
    }

    @Then("the symbol technical bullish weighted score should be {int}")
    public void theSymbolTechnicalBullishWeightedScoreShouldBe(
            int expectedScore) {

        assertNotNull(
                result,
                "Symbol analysis result should not be null"
        );

        assertNotNull(
                result.technicalSignal(),
                "Technical signal should not be null"
        );

        assertEquals(
                expectedScore,
                result.technicalSignal()
                        .getBullishWeightedScore(),
                "Unexpected bullish technical weighted score"
        );
    }

    @Then("the symbol technical bearish weighted score should be {int}")
    public void theSymbolTechnicalBearishWeightedScoreShouldBe(
            int expectedScore) {

        assertNotNull(
                result,
                "Symbol analysis result should not be null"
        );

        assertNotNull(
                result.technicalSignal(),
                "Technical signal should not be null"
        );

        assertEquals(
                expectedScore,
                result.technicalSignal()
                        .getBearishWeightedScore(),
                "Unexpected bearish technical weighted score"
        );
    }

    @Then("the symbol technical confidence score should be {int} percent")
    public void theSymbolTechnicalConfidenceScoreShouldBe(
            int expectedConfidence) {

        assertNotNull(
                result,
                "Symbol analysis result should not be null"
        );

        assertNotNull(
                result.technicalSignal(),
                "Technical signal should not be null"
        );

        assertEquals(
                expectedConfidence,
                result.technicalSignal()
                        .getConfidencePercent(),
                "Unexpected technical confidence score"
        );
    }

    @When("MarshallAi attempts to analyze a blank market symbol")
    public void marshallAiAttemptsToAnalyzeABlankMarketSymbol() {

        capturedException =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                symbolMarketAnalysisService.analyze(
                                        "",
                                        200
                                )
                );
    }

    @When("MarshallAi attempts to analyze symbol {string} using {int} candles")
    public void marshallAiAttemptsToAnalyzeSymbolUsingCandles(
            String symbol,
            int candleCount) {

        capturedException =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                symbolMarketAnalysisService.analyze(
                                        symbol,
                                        candleCount
                                )
                );
    }

    @Then("the symbol analysis should be rejected")
    public void theSymbolAnalysisShouldBeRejected() {

        assertNotNull(
                capturedException,
                "Expected symbol analysis to be rejected"
        );
    }

    @Then("the symbol analysis should be rejected as insufficient data")
    public void theSymbolAnalysisShouldBeRejectedAsInsufficientData() {

        assertNotNull(
                capturedException,
                "Expected insufficient historical market data to be rejected"
        );

        assertTrue(
                capturedException.getMessage() != null
                        && capturedException.getMessage()
                        .toLowerCase()
                        .contains("insufficient"),
                "Expected rejection reason to indicate insufficient market data"
        );
    }
}