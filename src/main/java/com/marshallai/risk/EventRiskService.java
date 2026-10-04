package com.marshallai.risk;

import org.springframework.stereotype.Service;

@Service
public class EventRiskService {

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
}