package com.marshallai.events;

import java.time.LocalDateTime;

public class MarketEvent {

    public enum Impact {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    private final String eventType;
    private final Impact impact;
    private final String title;
    private final LocalDateTime scheduledTime;
    private final String ticker;
    private final String source;

    public MarketEvent(
            String eventType,
            Impact impact,
            String title,
            LocalDateTime scheduledTime,
            String ticker,
            String source) {

        this.eventType = requireText(
                eventType,
                "Event type cannot be null or blank"
        );

        if (impact == null) {
            throw new IllegalArgumentException(
                    "Event impact cannot be null"
            );
        }

        this.impact = impact;

        this.title = requireText(
                title,
                "Event title cannot be null or blank"
        );

        if (scheduledTime == null) {
            throw new IllegalArgumentException(
                    "Scheduled time cannot be null"
            );
        }

        this.scheduledTime = scheduledTime;

        this.ticker =
                ticker == null
                        ? ""
                        : ticker.trim();

        this.source =
                source == null
                        ? ""
                        : source.trim();
    }

    public String getEventType() {
        return eventType;
    }

    public Impact getImpact() {
        return impact;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getScheduledTime() {
        return scheduledTime;
    }

    public String getTicker() {
        return ticker;
    }

    public String getSource() {
        return source;
    }

    public boolean isTickerSpecific() {
        return !ticker.isBlank();
    }

    public boolean isHighImpact() {
        return impact == Impact.HIGH
                || impact == Impact.CRITICAL;
    }

    private String requireText(
            String value,
            String errorMessage) {

        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    errorMessage
            );
        }

        return value.trim();
    }
}