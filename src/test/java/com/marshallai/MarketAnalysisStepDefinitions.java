package com.marshallai;

import com.marshallai.analysis.MarketAnalysisResult;
import com.marshallai.analysis.MarketAnalysisService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MarketAnalysisStepDefinitions {

    @Autowired
    private MarketAnalysisService marketAnalysisService;

    private List<BigDecimal> marketAnalysisClosingPrices;

    private MarketAnalysisResult marketAnalysisResult;

    @Given("the following market analysis closing prices:")
    public void theFollowingMarketAnalysisClosingPrices(DataTable dataTable) {

        marketAnalysisClosingPrices =
                dataTable
                        .asMaps(String.class, String.class)
                        .stream()
                        .map(row -> new BigDecimal(row.get("price")))
                        .toList();
    }

    @When("MarshallAi analyzes the market")
    public void marshallAiAnalyzesTheMarket() {

        marketAnalysisResult =
                marketAnalysisService.analyze(
                        marketAnalysisClosingPrices
                );
    }

    @Then("the market analysis should contain the {string} pattern")
    public void marketAnalysisShouldContainPattern(String patternName) {

        assertNotNull(
                marketAnalysisResult,
                "Market analysis result should not be null"
        );

        assertTrue(
                marketAnalysisResult
                        .getDetectedPatterns()
                        .contains(patternName),
                "Expected market analysis to contain pattern: " + patternName
        );
    }

    @Then("the market analysis direction should be {string}")
    public void marketAnalysisDirectionShouldBe(String expectedDirection) {

        assertNotNull(
                marketAnalysisResult,
                "Market analysis result should not be null"
        );

        assertEquals(
                expectedDirection,
                marketAnalysisResult
                        .getDirection()
                        .name(),
                "Unexpected market analysis direction"
        );
    }
}