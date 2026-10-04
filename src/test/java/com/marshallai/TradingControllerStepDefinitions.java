package com.marshallai;

import com.marshallai.controller.trading.TradingController;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TradingControllerStepDefinitions {

    @Autowired
    private TradingController tradingController;

    private ResponseEntity<Map<String, Object>> response;

    @When("MarshallAI requests the trading API status")
    public void marshallAIRequestsTheTradingApiStatus() {

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

        assertEquals(
                expectedConfidence,
                response.getBody().get(
                        "minimumConfidencePercent"
                )
        );
    }

    @Then("trade approval should be enabled")
    public void tradeApprovalShouldBeEnabled() {

        assertNotNull(response);
        assertNotNull(response.getBody());

        assertEquals(
                true,
                response.getBody().get(
                        "tradeApprovalEnabled"
                )
        );
    }
}