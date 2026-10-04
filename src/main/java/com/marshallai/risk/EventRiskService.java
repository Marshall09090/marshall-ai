package com.marshallai.risk;

import com.marshallai.events.MarketEvent;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class EventRiskService {

    // =========================================================
    // EXISTING EVENT-RISK EVALUATION
    // =========================================================

    public EventRisk evaluate(
            String eventType,
            String description,
            int minutesUntilEvent,
            boolean highImpact) {

        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException(
                    "Event type cannot be blank"
            );
        }

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "Event description cannot be blank"
            );
        }

        if (minutesUntilEvent < 0) {
            throw new IllegalArgumentException(
                    "Minutes until event cannot be negative"
            );
        }

        EventRisk.Level level;

        if (!highImpact) {

            level = EventRisk.Level.LOW;

        } else if (minutesUntilEvent <= 15) {

            level = EventRisk.Level.CRITICAL;

        } else if (minutesUntilEvent <= 60) {

            level = EventRisk.Level.HIGH;

        } else if (minutesUntilEvent <= 240) {

            level = EventRisk.Level.MEDIUM;

        } else {

            level = EventRisk.Level.LOW;
        }

        return new EventRisk(
                level,
                eventType,
                description
        );
    }

    // =========================================================
    // MARKET EVENT INTEGRATION
    // Converts MarketEvent directly into EventRisk.
    // =========================================================

    public EventRisk evaluate(
            MarketEvent marketEvent,
            LocalDateTime evaluationTime) {

        if (marketEvent == null) {
            throw new IllegalArgumentException(
                    "Market event cannot be null"
            );
        }

        if (evaluationTime == null) {
            throw new IllegalArgumentException(
                    "Evaluation time cannot be null"
            );
        }

        LocalDateTime scheduledTime =
                marketEvent.getScheduledTime();

        if (scheduledTime == null) {
            throw new IllegalArgumentException(
                    "Market event scheduled time cannot be null"
            );
        }

        long minutesUntilEvent =
                Duration.between(
                        evaluationTime,
                        scheduledTime
                ).toMinutes();

        if (minutesUntilEvent < 0) {
            throw new IllegalArgumentException(
                    "Market event cannot be in the past"
            );
        }

        if (minutesUntilEvent > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Market event is too far in the future"
            );
        }

        return evaluate(
                marketEvent.getSource(),
                marketEvent.getTitle(),
                (int) minutesUntilEvent,
                marketEvent.isHighImpact()
        );
    }

    // =========================================================
    // CONVENIENCE EVALUATION
    // Used later by the live trading pipeline.
    // =========================================================

    public EventRisk evaluate(
            MarketEvent marketEvent) {

        return evaluate(
                marketEvent,
                LocalDateTime.now()
        );
    }
}