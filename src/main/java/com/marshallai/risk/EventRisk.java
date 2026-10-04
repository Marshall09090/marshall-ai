package com.marshallai.risk;

import java.util.Objects;

public class EventRisk {

    public enum Level {
        NONE,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    private final Level level;
    private final String eventType;
    private final String description;
    private final boolean tradeBlocked;

    public EventRisk(
            Level level,
            String eventType,
            String description) {

        this.level =
                Objects.requireNonNull(
                        level,
                        "Event risk level cannot be null"
                );

        this.eventType =
                Objects.requireNonNull(
                        eventType,
                        "Event type cannot be null"
                );

        this.description =
                Objects.requireNonNull(
                        description,
                        "Event description cannot be null"
                );

        this.tradeBlocked =
                level == Level.HIGH
                        || level == Level.CRITICAL;
    }

    public Level getLevel() {
        return level;
    }

    public String getEventType() {
        return eventType;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTradeBlocked() {
        return tradeBlocked;
    }
}