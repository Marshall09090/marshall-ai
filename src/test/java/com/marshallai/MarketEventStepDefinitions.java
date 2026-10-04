package com.marshallai;

import com.marshallai.events.MarketEvent;
import com.marshallai.events.MarketEventService;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MarketEventStepDefinitions {

    @Autowired
    private MarketEventService marketEventService;

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(
                    2026,
                    10,
                    3,
                    9,
                    30
            );

    private List<MarketEvent> resultEvents;

    private boolean highImpactEventDetected;

    @Before
    public void clearMarketEvents() {
        marketEventService.clearEvents();
        resultEvents = List.of();
        highImpactEventDetected = false;
    }

    @Given(
            "a {string} market event named {string} " +
                    "with impact {string} occurs in {int} minutes " +
                    "for ticker {string}"
    )
    public void aMarketEventOccursForTicker(
            String eventType,
            String title,
            String impact,
            int minutes,
            String ticker) {

        MarketEvent marketEvent =
                new MarketEvent(
                        eventType,
                        MarketEvent.Impact.valueOf(
                                impact.toUpperCase()
                        ),
                        title,
                        BASE_TIME.plusMinutes(minutes),
                        ticker,
                        "BDD"
                );

        marketEventService.addEvent(
                marketEvent
        );
    }

    @Given(
            "a {string} market event named {string} " +
                    "with impact {string} occurs in {int} minutes"
    )
    public void aGlobalMarketEventOccurs(
            String eventType,
            String title,
            String impact,
            int minutes) {

        MarketEvent marketEvent =
                new MarketEvent(
                        eventType,
                        MarketEvent.Impact.valueOf(
                                impact.toUpperCase()
                        ),
                        title,
                        BASE_TIME.plusMinutes(minutes),
                        "",
                        "BDD"
                );

        marketEventService.addEvent(
                marketEvent
        );
    }

    @When("MarshallAI retrieves all market events")
    public void marshallAIRetrievesAllMarketEvents() {

        resultEvents =
                marketEventService.getAllEvents();
    }

    @When(
            "MarshallAI retrieves upcoming market events " +
                    "from the base time"
    )
    public void marshallAIRetrievesUpcomingMarketEvents() {

        resultEvents =
                marketEventService.getUpcomingEvents(
                        BASE_TIME
                );
    }

    @When(
            "MarshallAI retrieves market events " +
                    "for ticker {string}"
    )
    public void marshallAIRetrievesEventsForTicker(
            String ticker) {

        resultEvents =
                marketEventService.getEventsForTicker(
                        ticker
                );
    }

    @When(
            "MarshallAI retrieves high impact market events"
    )
    public void marshallAIRetrievesHighImpactEvents() {

        resultEvents =
                marketEventService.getHighImpactEvents();
    }

    @When(
            "MarshallAI checks ticker {string} " +
                    "for high impact events in the next {int} minutes"
    )
    public void marshallAIChecksTickerForHighImpactEvents(
            String ticker,
            int minutes) {

        highImpactEventDetected =
                marketEventService
                        .hasHighImpactEventForTicker(
                                ticker,
                                BASE_TIME,
                                BASE_TIME.plusMinutes(
                                        minutes
                                )
                        );
    }

    @Then(
            "the market event result should contain {int} events"
    )
    public void theMarketEventResultShouldContainEvents(
            int expectedCount) {

        assertEquals(
                expectedCount,
                resultEvents.size(),
                "Unexpected number of market events"
        );
    }

    @Then(
            "the first market event should be named {string}"
    )
    public void theFirstMarketEventShouldBeNamed(
            String expectedTitle) {

        assertFalse(
                resultEvents.isEmpty(),
                "Expected at least one market event"
        );

        assertEquals(
                expectedTitle,
                resultEvents.get(0).getTitle(),
                "Unexpected first market event"
        );
    }

    @Then(
            "a high impact market event should be detected"
    )
    public void aHighImpactMarketEventShouldBeDetected() {

        assertTrue(
                highImpactEventDetected,
                "Expected a high impact event"
        );
    }

    @Then(
            "no high impact market event should be detected"
    )
    public void noHighImpactMarketEventShouldBeDetected() {

        assertFalse(
                highImpactEventDetected,
                "Did not expect a high impact event"
        );
    }
}