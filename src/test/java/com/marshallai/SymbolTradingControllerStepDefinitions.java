package com.marshallai;

import com.marshallai.controller.trading.TradingAnalysisRequest;
import com.marshallai.controller.trading.TradingAnalysisResponse;
import com.marshallai.controller.trading.TradingController;
import com.marshallai.market.SimulatedMarketDataProvider;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SymbolTradingControllerStepDefinitions {

    @Autowired
    private TradingController tradingController;

    @Autowired
    private SimulatedMarketDataProvider simulatedMarketDataProvider;

    private String symbol;

    private String eventType;
    private String eventDescription;
    private int minutesUntilEvent;
    private boolean highImpactEvent;

    private BigDecimal accountBalance;
    private BigDecimal riskPercent;
    private BigDecimal entryPrice;
    private BigDecimal stopLossPrice;

    private ResponseEntity<TradingAnalysisResponse> responseEntity;
    private TradingAnalysisResponse response;

    // =========================================================
    // MARKET DATA SETUP
    // =========================================================

    @Given("the symbol trading API has strongly bullish market data for {string}")
    public void theSymbolTradingApiHasStronglyBullishMarketDataFor(
            String symbol) {

        this.symbol = symbol;

        simulatedMarketDataProvider
                .useStronglyBullishMarket();
    }

    @Given("the symbol trading API has mixed market data for {string}")
    public void theSymbolTradingApiHasMixedMarketDataFor(
            String symbol) {

        this.symbol = symbol;

        simulatedMarketDataProvider
                .useMixedMarket();
    }

    // =========================================================
    // EVENT RISK SETUP
    // =========================================================

    @Given("the symbol trading request has normal event risk")
    public void theSymbolTradingRequestHasNormalEventRisk() {

        eventType = "NONE";
        eventDescription =
                "No major market event";

        minutesUntilEvent = 1440;
        highImpactEvent = false;
    }

    @Given("the symbol trading request has high event risk")
    public void theSymbolTradingRequestHasHighEventRisk() {

        eventType = "FOMC";

        eventDescription =
                "High impact monetary policy announcement";

        minutesUntilEvent = 15;
        highImpactEvent = true;
    }

    // =========================================================
    // POSITION SIZING SETUP
    // =========================================================

    @Given("the symbol trading request uses valid position sizing values")
    public void theSymbolTradingRequestUsesValidPositionSizingValues() {

        accountBalance =
                new BigDecimal("10000.00");

        riskPercent =
                new BigDecimal("1.00");

        entryPrice =
                new BigDecimal("100.00");

        stopLossPrice =
                new BigDecimal("95.00");
    }

    // =========================================================
    // EXECUTE SYMBOL TRADING API
    // =========================================================

    @When("the symbol trading analysis endpoint is executed")
    public void theSymbolTradingAnalysisEndpointIsExecuted() {

        assertNotNull(
                symbol,
                "Symbol must be configured before analysis"
        );

        /*
         * The analyze-symbol endpoint gets the actual market
         * candles from SimulatedMarketDataProvider.
         *
         * These request price lists are therefore only placeholders
         * required by the TradingAnalysisRequest record.
         */

        List<BigDecimal> placeholderPrices =
                placeholderPrices();

        TradingAnalysisRequest request =
                new TradingAnalysisRequest(
                        symbol,
                        buildHighPrices(
                                placeholderPrices
                        ),
                        buildLowPrices(
                                placeholderPrices
                        ),
                        placeholderPrices,
                        eventType,
                        eventDescription,
                        minutesUntilEvent,
                        highImpactEvent,
                        accountBalance,
                        riskPercent,
                        entryPrice,
                        stopLossPrice
                );

        responseEntity =
                tradingController.analyzeSymbol(
                        request
                );

        response =
                responseEntity.getBody();
    }

    // =========================================================
    // RESPONSE VALIDATION
    // =========================================================

    @Then("the symbol trading response should be returned successfully")
    public void theSymbolTradingResponseShouldBeReturnedSuccessfully() {

        assertNotNull(
                responseEntity,
                "Expected a response entity"
        );

        assertTrue(
                responseEntity
                        .getStatusCode()
                        .is2xxSuccessful(),
                "Expected successful HTTP response"
        );

        assertNotNull(
                response,
                "Expected trading analysis response body"
        );
    }

    @Then("the symbol trading response symbol should be {string}")
    public void theSymbolTradingResponseSymbolShouldBe(
            String expectedSymbol) {

        assertNotNull(response);

        assertEquals(
                expectedSymbol,
                response.symbol()
        );
    }

    @Then("the symbol trading technical confidence should be {int} percent")
    public void theSymbolTradingTechnicalConfidenceShouldBePercent(
            int expectedConfidence) {

        assertNotNull(response);

        assertEquals(
                expectedConfidence,
                response.technicalConfidencePercent()
        );
    }

    // =========================================================
    // TRADING DECISION
    // =========================================================

    @Then("the symbol trading decision should be {string}")
    public void theSymbolTradingDecisionShouldBe(
            String expectedDecision) {

        assertNotNull(response);

        System.out.println(
                "DEBUG SYMBOL SIGNAL: decision="
                        + response.tradingDecision()
                        + ", confidence="
                        + response.confidencePercent()
                        + ", bullish="
                        + response.bullishWeightedScore()
                        + ", bearish="
                        + response.bearishWeightedScore()
                        + ", patterns="
                        + response.detectedPatterns()
                        + ", technicalBullish="
                        + response.technicalBullishScore()
                        + ", technicalBearish="
                        + response.technicalBearishScore()
                        + ", technicalConfidence="
                        + response.technicalConfidencePercent()
        );

        assertEquals(
                expectedDecision,
                response.tradingDecision()
        );
    }

    @Then("the symbol trading confidence should be at least {int} percent")
    public void theSymbolTradingConfidenceShouldBeAtLeastPercent(
            int minimumConfidence) {

        assertNotNull(response);

        assertTrue(
                response.confidencePercent()
                        >= minimumConfidence,
                "Expected confidence to be at least "
                        + minimumConfidence
                        + "% but was "
                        + response.confidencePercent()
                        + "%"
        );
    }

    // =========================================================
    // TRADE APPROVAL
    // =========================================================

    @Then("the symbol trade should be approved")
    public void theSymbolTradeShouldBeApproved() {

        assertNotNull(response);

        assertEquals(
                "APPROVED",
                response.tradeApprovalStatus()
        );

        assertFalse(
                response.eventTradeBlocked(),
                "Approved trade should not be blocked by event risk"
        );
    }

    @Then("the symbol trade should not be approved")
    public void theSymbolTradeShouldNotBeApproved() {

        assertNotNull(response);

        assertFalse(
                "APPROVED".equals(
                        response.tradeApprovalStatus()
                ),
                "Expected trade not to be approved"
        );
    }

    // =========================================================
    // EVENT RISK
    // =========================================================

    @Then("the symbol trading event risk should block trading")
    public void theSymbolTradingEventRiskShouldBlockTrading() {

        assertNotNull(response);

        assertTrue(
                response.eventTradeBlocked(),
                "Expected event risk to block trading"
        );
    }

    // =========================================================
    // PLACEHOLDER REQUEST PRICES
    // =========================================================

    private List<BigDecimal> placeholderPrices() {

        List<BigDecimal> prices =
                new ArrayList<>();

        for (int i = 0;
             i < 30;
             i++) {

            prices.add(
                    BigDecimal.valueOf(
                            100L + i
                    )
            );
        }

        return prices;
    }

    // =========================================================
    // HIGH PRICES
    // =========================================================

    private List<BigDecimal> buildHighPrices(
            List<BigDecimal> closingPrices) {

        List<BigDecimal> highPrices =
                new ArrayList<>();

        for (BigDecimal close :
                closingPrices) {

            highPrices.add(
                    close.add(
                            BigDecimal.ONE
                    )
            );
        }

        return highPrices;
    }

    // =========================================================
    // LOW PRICES
    // =========================================================

    private List<BigDecimal> buildLowPrices(
            List<BigDecimal> closingPrices) {

        List<BigDecimal> lowPrices =
                new ArrayList<>();

        for (BigDecimal close :
                closingPrices) {

            lowPrices.add(
                    close.subtract(
                            BigDecimal.ONE
                    )
            );
        }

        return lowPrices;
    }
}