package com.marshallai;

import com.marshallai.events.MarketEvent;
import com.marshallai.risk.EventRisk;
import com.marshallai.risk.EventRiskService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class EventRiskStepDefinitions {

    @Autowired
    private EventRiskService eventRiskService;

    private String eventType;
    private String description;
    private int minutesUntilEvent;
    private boolean highImpact;

    private EventRisk eventRisk;

    private MarketEvent marketEvent;
    private LocalDateTime evaluationTime;

    // =========================================================
    // EXISTING EVENT-RISK STEPS
    // =========================================================

    @Given("a {string} event named {string}")
    public void anEventNamed(
            String eventType,
            String description) {

        this.eventType = eventType;
        this.description = description;
    }

    @Given("the event occurs in {int} minutes")
    public void theEventOccursInMinutes(
            int minutesUntilEvent) {

        this.minutesUntilEvent =
                minutesUntilEvent;
    }

    @Given("the event is high impact")
    public void theEventIsHighImpact() {

        highImpact = true;
    }

    @Given("the event is low impact")
    public void theEventIsLowImpact() {

        highImpact = false;
    }

    @When("MarshallAI evaluates the event risk")
    public void marshallAIEvaluatesTheEventRisk() {

        eventRisk =
                eventRiskService.evaluate(
                        eventType,
                        description,
                        minutesUntilEvent,
                        highImpact
                );
    }

    // =========================================================
    // MARKET EVENT INTEGRATION STEPS
    // =========================================================

    @Given(
            "an ingested {string} market event named {string} " +
                    "with impact {string} occurs in {int} minutes"
    )
    public void anIngestedMarketEventOccursInMinutes(
            String eventType,
            String title,
            String impact,
            int minutesUntilEvent) {

        evaluationTime =
                LocalDateTime.of(
                        2026,
                        1,
                        15,
                        10,
                        0
                );

        MarketEvent.Impact marketImpact =
                MarketEvent.Impact.valueOf(
                        impact
                );

        marketEvent =
                new MarketEvent(
                        eventType,
                        marketImpact,
                        title,
                        evaluationTime.plusMinutes(
                                minutesUntilEvent
                        ),
                        "",
                        "BDD"
                );
    }

    @When(
            "MarshallAI automatically evaluates " +
                    "the ingested market event"
    )
    public void marshallAIAutomaticallyEvaluatesTheIngestedMarketEvent() {

        assertNotNull(
                marketEvent,
                "Market event should not be null"
        );

        assertNotNull(
                evaluationTime,
                "Evaluation time should not be null"
        );

        eventRisk =
                eventRiskService.evaluate(
                        marketEvent,
                        evaluationTime
                );
    }

    // =========================================================
    // SHARED ASSERTIONS
    // =========================================================

    @Then("the event risk level should be {string}")
    public void theEventRiskLevelShouldBe(
            String expectedLevel) {

        assertNotNull(
                eventRisk,
                "Event risk should not be null"
        );

        assertEquals(
                EventRisk.Level.valueOf(
                        expectedLevel
                ),
                eventRisk.getLevel()
        );
    }

    @Then("new trading should be blocked")
    public void newTradingShouldBeBlocked() {

        assertNotNull(
                eventRisk,
                "Event risk should not be null"
        );

        assertEquals(
                true,
                eventRisk.isTradeBlocked()
        );
    }

    @Then("new trading should be allowed")
    public void newTradingShouldBeAllowed() {

        assertNotNull(
                eventRisk,
                "Event risk should not be null"
        );

        assertEquals(
                false,
                eventRisk.isTradeBlocked()
        );
    }
}