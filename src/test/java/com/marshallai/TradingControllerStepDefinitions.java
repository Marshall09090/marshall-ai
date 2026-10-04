package com.marshallai;

import com.marshallai.controller.trading.TradingAnalysisRequest;
import com.marshallai.controller.trading.TradingAnalysisResponse;
import com.marshallai.controller.trading.TradingController;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TradingControllerStepDefinitions {

    @Autowired
    private TradingController tradingController;

    private ResponseEntity<Map<String, Object>> response;

    private TradingAnalysisRequest tradingAnalysisRequest;

    private ResponseEntity<TradingAnalysisResponse>
            tradingAnalysisResponse;

    /*
     * ============================================================
     * TRADING STATUS ENDPOINT
     * ============================================================
     */

    @When("MarshallAI requests the trading API status")
    public void marshallAIRequestsTheTradingApiStatus() {

        response =
                tradingController.getTradingStatus();
    }

    @When("the trading status endpoint is requested")
    public void theTradingStatusEndpointIsRequested() {

        response =
                tradingController.getTradingStatus();
    }

    @Then("the trading API response status should be {int}")
    public void theTradingApiResponseStatusShouldBe(
            int expectedStatus) {

        assertNotNull(response);

        assertEquals(
                expectedStatus,
                response.getStatusCode().value()
        );
    }

    @Then("the trading service should be {string}")
    public void theTradingServiceShouldBe(
            String expectedService) {

        assertNotNull(response);
        assertNotNull(response.getBody());

        assertEquals(
                expectedService,
                response.getBody().get("service")
        );
    }

    @Then("the trading engine status should be {string}")
    public void theTradingEngineStatusShouldBe(
            String expectedStatus) {

        assertNotNull(response);
        assertNotNull(response.getBody());

        assertEquals(
                expectedStatus,
                response.getBody().get("status")
        );
    }

    @Then("the trading API minimum confidence should be {int} percent")
    public void theTradingApiMinimumConfidenceShouldBe(
            int expectedConfidence) {

        assertNotNull(response);
        assertNotNull(response.getBody());

        Number actualConfidence =
                (Number) response.getBody().get(
                        "minimumConfidencePercent"
                );

        assertNotNull(actualConfidence);

        assertEquals(
                expectedConfidence,
                actualConfidence.intValue()
        );
    }

    @Then("the minimum trading confidence should be {int} percent")
    public void theMinimumTradingConfidenceShouldBe(
            int expectedConfidence) {

        assertNotNull(response);
        assertNotNull(response.getBody());

        Number actualConfidence =
                (Number) response.getBody().get(
                        "minimumConfidencePercent"
                );

        assertNotNull(actualConfidence);

        assertEquals(
                expectedConfidence,
                actualConfidence.intValue()
        );
    }

    @Then("trade approval should be enabled")
    public void tradeApprovalShouldBeEnabled() {

        assertNotNull(response);
        assertNotNull(response.getBody());

        assertEquals(
                Boolean.TRUE,
                response.getBody().get(
                        "tradeApprovalEnabled"
                )
        );
    }

    /*
     * ============================================================
     * COMPLETE TRADING ANALYSIS PIPELINE
     * ============================================================
     */

    @Given("a valid bullish trading analysis request")
    public void aValidBullishTradingAnalysisRequest() {

        /*
         * Thirty candles are supplied because the technical
         * indicator engine requires enough market history for
         * MACD and the other indicators.
         *
         * The price structure forms a bullish rounding-bottom
         * pattern followed by a breakout.
         */

        List<BigDecimal> closingPrices = List.of(
                new BigDecimal("120.0"),
                new BigDecimal("118.0"),
                new BigDecimal("116.0"),
                new BigDecimal("114.0"),
                new BigDecimal("112.0"),
                new BigDecimal("110.5"),
                new BigDecimal("109.0"),
                new BigDecimal("107.5"),
                new BigDecimal("106.0"),
                new BigDecimal("105.0"),
                new BigDecimal("104.0"),
                new BigDecimal("103.0"),
                new BigDecimal("102.0"),
                new BigDecimal("101.5"),
                new BigDecimal("101.0"),
                new BigDecimal("102.2"),
                new BigDecimal("103.4"),
                new BigDecimal("104.6"),
                new BigDecimal("105.8"),
                new BigDecimal("107.0"),
                new BigDecimal("108.2"),
                new BigDecimal("109.4"),
                new BigDecimal("110.6"),
                new BigDecimal("111.8"),
                new BigDecimal("113.0"),
                new BigDecimal("114.2"),
                new BigDecimal("115.4"),
                new BigDecimal("116.6"),
                new BigDecimal("117.8"),
                new BigDecimal("121.0")
        );

        List<BigDecimal> highPrices =
                closingPrices.stream()
                        .map(price ->
                                price.add(
                                        BigDecimal.ONE
                                )
                        )
                        .toList();

        List<BigDecimal> lowPrices =
                closingPrices.stream()
                        .map(price ->
                                price.subtract(
                                        BigDecimal.ONE
                                )
                        )
                        .toList();

        tradingAnalysisRequest =
                new TradingAnalysisRequest(
                        "TSLA",

                        highPrices,
                        lowPrices,
                        closingPrices,

                        "ECONOMIC",
                        "Low impact economic release",
                        10,
                        false,

                        new BigDecimal("100000.00"),
                        new BigDecimal("1.00"),
                        new BigDecimal("121.00"),
                        new BigDecimal("116.00")
                );
    }

    @When("the trading analysis endpoint is requested")
    public void theTradingAnalysisEndpointIsRequested() {

        assertNotNull(
                tradingAnalysisRequest,
                "Trading analysis request should not be null"
        );

        tradingAnalysisResponse =
                tradingController.analyzeTradingOpportunity(
                        tradingAnalysisRequest
                );
    }

    @Then("the trading analysis response should be successful")
    public void theTradingAnalysisResponseShouldBeSuccessful() {

        assertNotNull(tradingAnalysisResponse);

        assertEquals(
                200,
                tradingAnalysisResponse
                        .getStatusCode()
                        .value()
        );

        assertNotNull(
                tradingAnalysisResponse.getBody()
        );
    }

    @Then("the trading analysis symbol should be {string}")
    public void theTradingAnalysisSymbolShouldBe(
            String expectedSymbol) {

        TradingAnalysisResponse body =
                getTradingAnalysisBody();

        assertEquals(
                expectedSymbol,
                body.symbol()
        );
    }

    @Then("the trading analysis should contain detected patterns")
    public void theTradingAnalysisShouldContainDetectedPatterns() {

        TradingAnalysisResponse body =
                getTradingAnalysisBody();

        assertNotNull(
                body.detectedPatterns()
        );

        assertFalse(
                body.detectedPatterns().isEmpty(),
                "Expected at least one detected chart pattern"
        );
    }

    @Then("the trading analysis should contain a trading decision")
    public void theTradingAnalysisShouldContainATradingDecision() {

        TradingAnalysisResponse body =
                getTradingAnalysisBody();

        assertNotNull(
                body.tradingDecision()
        );

        assertFalse(
                body.tradingDecision().isBlank(),
                "Trading decision should not be blank"
        );
    }

    @Then("the trading analysis confidence should be between {int} and {int} percent")
    public void theTradingAnalysisConfidenceShouldBeBetweenAndPercent(
            int minimum,
            int maximum) {

        TradingAnalysisResponse body =
                getTradingAnalysisBody();

        int confidence =
                body.confidencePercent();

        assertTrue(
                confidence >= minimum,
                "Confidence was below the expected minimum"
        );

        assertTrue(
                confidence <= maximum,
                "Confidence was above the expected maximum"
        );
    }

    @Then("the trading analysis should contain an event risk level")
    public void theTradingAnalysisShouldContainAnEventRiskLevel() {

        TradingAnalysisResponse body =
                getTradingAnalysisBody();

        assertNotNull(
                body.eventRiskLevel()
        );

        assertFalse(
                body.eventRiskLevel().isBlank(),
                "Event risk level should not be blank"
        );
    }

    @Then("the trading analysis should contain a trade approval status")
    public void theTradingAnalysisShouldContainATradeApprovalStatus() {

        TradingAnalysisResponse body =
                getTradingAnalysisBody();

        assertNotNull(
                body.tradeApprovalStatus()
        );

        assertFalse(
                body.tradeApprovalStatus().isBlank(),
                "Trade approval status should not be blank"
        );
    }

    @Then("the trading analysis position quantity should not be negative")
    public void theTradingAnalysisPositionQuantityShouldNotBeNegative() {

        TradingAnalysisResponse body =
                getTradingAnalysisBody();

        assertTrue(
                body.quantity() >= 0,
                "Position quantity must not be negative"
        );
    }

    /*
     * ============================================================
     * TEST HELPER
     * ============================================================
     */

    private TradingAnalysisResponse getTradingAnalysisBody() {

        assertNotNull(
                tradingAnalysisResponse,
                "Trading analysis response should not be null"
        );

        TradingAnalysisResponse body =
                tradingAnalysisResponse.getBody();

        assertNotNull(
                body,
                "Trading analysis response body should not be null"
        );

        return body;
    }
}