package com.nexoraai;

import com.nexoraai.market.AssetType;
import com.nexoraai.market.MarketCandle;
import com.nexoraai.market.MarketDataService;
import com.nexoraai.market.MarketQuote;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MarketStepDefinitions {

    @Autowired
    private MarketDataService marketDataService;

    private MarketQuote marketQuote;
    private List<MarketCandle> historicalCandles;

    @Given("the Nexora AI application is running")
    public void theNexoraAiApplicationIsRunning() {
        // Application context is already started by Spring Boot.
        assertNotNull(marketDataService);
    }

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

    @When("I request historical market data for {string} with asset type {string} and timeframe {string}")
    public void requestHistoricalMarketData(
            String symbol,
            String assetType,
            String timeframe
    ) {
        historicalCandles = marketDataService.getHistoricalCandles(
                symbol,
                AssetType.valueOf(assetType),
                timeframe
        );
    }

    @Then("the historical market data should not be empty")
    public void historicalMarketDataShouldNotBeEmpty() {
        assertNotNull(historicalCandles);
        assertFalse(historicalCandles.isEmpty());
    }

    @Then("each candle should contain an open price")
    public void eachCandleShouldContainAnOpenPrice() {
        assertNotNull(historicalCandles);

        historicalCandles.forEach(candle -> {
            assertNotNull(candle.open());
            assertTrue(candle.open().signum() > 0);
        });
    }

    @Then("each candle should contain a high price")
    public void eachCandleShouldContainAHighPrice() {
        assertNotNull(historicalCandles);

        historicalCandles.forEach(candle -> {
            assertNotNull(candle.high());
            assertTrue(candle.high().signum() > 0);
        });
    }

    @Then("each candle should contain a low price")
    public void eachCandleShouldContainALowPrice() {
        assertNotNull(historicalCandles);

        historicalCandles.forEach(candle -> {
            assertNotNull(candle.low());
            assertTrue(candle.low().signum() > 0);
        });
    }

    @Then("each candle should contain a close price")
    public void eachCandleShouldContainAClosePrice() {
        assertNotNull(historicalCandles);

        historicalCandles.forEach(candle -> {
            assertNotNull(candle.close());
            assertTrue(candle.close().signum() > 0);
        });
    }

    @Then("each candle should contain volume")
    public void eachCandleShouldContainVolume() {
        assertNotNull(historicalCandles);

        historicalCandles.forEach(candle -> {
            assertNotNull(candle.volume());
            assertTrue(candle.volume().signum() > 0);
        });
    }

    @Then("each candle should contain a timestamp")
    public void eachCandleShouldContainATimestamp() {
        assertNotNull(historicalCandles);

        historicalCandles.forEach(candle ->
                assertNotNull(candle.timestamp())
        );
    }

    @Then("each candle high price should be greater than or equal to its low price")
    public void eachCandleHighShouldBeGreaterThanOrEqualToLow() {
        assertNotNull(historicalCandles);

        historicalCandles.forEach(candle ->
                assertTrue(
                        candle.high().compareTo(candle.low()) >= 0,
                        "Candle high price must be greater than or equal to low price"
                )
        );
    }
}