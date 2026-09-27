package com.nexoraai;

import com.nexoraai.market.AssetType;
import com.nexoraai.market.MarketDataService;
import com.nexoraai.market.MarketQuote;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import io.cucumber.java.en.Given;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MarketStepDefinitions {
    @Given("the Nexora AI application is running")
    public void theNexoraAiApplicationIsRunning() {
        // Application context is already started by Spring Boot
    }
    @Autowired
    private MarketDataService marketDataService;

    private MarketQuote marketQuote;

    @When("I request a market quote for {string} with asset type {string}")
    public void requestMarketQuote(String symbol, String assetType) {
        marketQuote = marketDataService.getQuote(
                symbol,
                AssetType.valueOf(assetType)
        );
    }

    @Then("the market quote symbol should be {string}")
    public void marketQuoteSymbolShouldBe(String expectedSymbol) {
        assertNotNull(marketQuote);
        assertEquals(expectedSymbol, marketQuote.symbol());
    }

    @Then("the market quote asset type should be {string}")
    public void marketQuoteAssetTypeShouldBe(String expectedAssetType) {
        assertNotNull(marketQuote);
        assertEquals(
                AssetType.valueOf(expectedAssetType),
                marketQuote.assetType()
        );
    }

    @Then("the market quote should contain a price")
    public void marketQuoteShouldContainAPrice() {
        assertNotNull(marketQuote);
        assertNotNull(marketQuote.price());
        assertTrue(marketQuote.price().signum() > 0);
    }
    @Then("the market quote price should be greater than zero")
    public void marketQuotePriceShouldBeGreaterThanZero() {
        assertNotNull(marketQuote);
        assertNotNull(marketQuote.price());
        assertTrue(marketQuote.price().signum() > 0);
    }

    @Then("the market quote should contain a timestamp")
    public void marketQuoteShouldContainATimestamp() {
        assertNotNull(marketQuote);
        assertNotNull(marketQuote.timestamp());
    }
}