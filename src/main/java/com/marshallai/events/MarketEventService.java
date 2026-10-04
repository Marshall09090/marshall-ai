package com.marshallai.events;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MarketEventService {

    private final List<MarketEvent> marketEvents =
            new ArrayList<>();

    // =========================================================
    // EVENT INGESTION
    // =========================================================

    public void addEvent(MarketEvent marketEvent) {

        if (marketEvent == null) {
            throw new IllegalArgumentException(
                    "Market event cannot be null"
            );
        }

        marketEvents.add(marketEvent);
    }

    public void addEvents(
            List<MarketEvent> events) {

        if (events == null) {
            throw new IllegalArgumentException(
                    "Market events cannot be null"
            );
        }

        for (MarketEvent event : events) {
            addEvent(event);
        }
    }

    // =========================================================
    // EVENT RETRIEVAL
    // =========================================================

    public List<MarketEvent> getAllEvents() {

        return marketEvents
                .stream()
                .sorted(
                        Comparator.comparing(
                                MarketEvent::getScheduledTime
                        )
                )
                .toList();
    }

    public List<MarketEvent> getUpcomingEvents(
            LocalDateTime currentTime) {

        validateTime(currentTime);

        return marketEvents
                .stream()
                .filter(event ->
                        !event
                                .getScheduledTime()
                                .isBefore(currentTime)
                )
                .sorted(
                        Comparator.comparing(
                                MarketEvent::getScheduledTime
                        )
                )
                .toList();
    }

    // =========================================================
    // TIME-WINDOW FILTERING
    // =========================================================

    public List<MarketEvent> getEventsBetween(
            LocalDateTime startTime,
            LocalDateTime endTime) {

        validateTime(startTime);
        validateTime(endTime);

        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException(
                    "End time cannot be before start time"
            );
        }

        return marketEvents
                .stream()
                .filter(event -> {

                    LocalDateTime eventTime =
                            event.getScheduledTime();

                    boolean afterOrEqualStart =
                            !eventTime.isBefore(startTime);

                    boolean beforeOrEqualEnd =
                            !eventTime.isAfter(endTime);

                    return afterOrEqualStart
                            && beforeOrEqualEnd;
                })
                .sorted(
                        Comparator.comparing(
                                MarketEvent::getScheduledTime
                        )
                )
                .toList();
    }

    // =========================================================
    // TICKER FILTERING
    // =========================================================

    public List<MarketEvent> getEventsForTicker(
            String ticker) {

        if (ticker == null
                || ticker.isBlank()) {

            throw new IllegalArgumentException(
                    "Ticker cannot be null or blank"
            );
        }

        String normalizedTicker =
                ticker.trim();

        return marketEvents
                .stream()
                .filter(event ->
                        event.isTickerSpecific()
                                && event
                                .getTicker()
                                .equalsIgnoreCase(
                                        normalizedTicker
                                )
                )
                .sorted(
                        Comparator.comparing(
                                MarketEvent::getScheduledTime
                        )
                )
                .toList();
    }

    // =========================================================
    // HIGH-IMPACT EVENT FILTERING
    // =========================================================

    public List<MarketEvent> getHighImpactEvents() {

        return marketEvents
                .stream()
                .filter(
                        MarketEvent::isHighImpact
                )
                .sorted(
                        Comparator.comparing(
                                MarketEvent::getScheduledTime
                        )
                )
                .toList();
    }

    public List<MarketEvent> getHighImpactEventsBetween(
            LocalDateTime startTime,
            LocalDateTime endTime) {

        return getEventsBetween(
                startTime,
                endTime
        )
                .stream()
                .filter(
                        MarketEvent::isHighImpact
                )
                .toList();
    }

    // =========================================================
    // TRADE-RISK EVENT LOOKUP
    // =========================================================

    public boolean hasHighImpactEventForTicker(
            String ticker,
            LocalDateTime startTime,
            LocalDateTime endTime) {

        if (ticker == null
                || ticker.isBlank()) {

            throw new IllegalArgumentException(
                    "Ticker cannot be null or blank"
            );
        }

        String normalizedTicker =
                ticker.trim();

        return getHighImpactEventsBetween(
                startTime,
                endTime
        )
                .stream()
                .anyMatch(event ->

                        !event.isTickerSpecific()

                                || event
                                .getTicker()
                                .equalsIgnoreCase(
                                        normalizedTicker
                                )
                );
    }

    // =========================================================
    // MANAGEMENT
    // =========================================================

    public int getEventCount() {
        return marketEvents.size();
    }

    public void clearEvents() {
        marketEvents.clear();
    }

    // =========================================================
    // VALIDATION
    // =========================================================

    private void validateTime(
            LocalDateTime time) {

        if (time == null) {
            throw new IllegalArgumentException(
                    "Time cannot be null"
            );
        }
    }
}