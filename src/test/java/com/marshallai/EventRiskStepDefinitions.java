package com.marshallai;

import com.marshallai.risk.EventRisk;
import com.marshallai.risk.EventRiskService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventRiskStepDefinitions {

    @Autowired
    private EventRiskService eventRiskService;

    private String eventType;
    private String description;
    private int minutesUntilEvent;
    private boolean highImpact;

    private EventRisk eventRisk;

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

    @Then("the event risk level should be {string}")
    public void theEventRiskLevelShouldBe(
            String expectedLevel) {

        assertEquals(
                EventRisk.Level.valueOf(expectedLevel),
                eventRisk.getLevel()
        );
    }

    @Then("new trading should be blocked")
    public void newTradingShouldBeBlocked() {

        assertEquals(
                true,
                eventRisk.isTradeBlocked()
        );
    }

    @Then("new trading should be allowed")
    public void newTradingShouldBeAllowed() {

        assertEquals(
                false,
                eventRisk.isTradeBlocked()
        );
    }
}