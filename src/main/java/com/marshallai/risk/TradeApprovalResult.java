package com.marshallai.risk;

import java.math.BigDecimal;

public class TradeApprovalResult {

    public enum Status {
        APPROVED,
        BLOCKED
    }

    private final Status status;
    private final String tradingDecision;
    private final int confidencePercent;
    private final EventRisk.Level eventRiskLevel;
    private final String reason;
    private final PositionSizeResult positionSize;

    public TradeApprovalResult(
            Status status,
            String tradingDecision,
            int confidencePercent,
            EventRisk.Level eventRiskLevel,
            String reason,
            PositionSizeResult positionSize) {

        if (status == null) {
            throw new IllegalArgumentException(
                    "Trade approval status cannot be null"
            );
        }

        if (tradingDecision == null
                || tradingDecision.isBlank()) {

            throw new IllegalArgumentException(
                    "Trading decision cannot be blank"
            );
        }

        if (confidencePercent < 0
                || confidencePercent > 100) {

            throw new IllegalArgumentException(
                    "Confidence percent must be between 0 and 100"
            );
        }

        if (eventRiskLevel == null) {
            throw new IllegalArgumentException(
                    "Event risk level cannot be null"
            );
        }

        if (reason == null
                || reason.isBlank()) {

            throw new IllegalArgumentException(
                    "Trade approval reason cannot be blank"
            );
        }

        if (status == Status.APPROVED
                && positionSize == null) {

            throw new IllegalArgumentException(
                    "Approved trade must have a position size"
            );
        }

        this.status = status;
        this.tradingDecision =
                tradingDecision.trim().toUpperCase();
        this.confidencePercent =
                confidencePercent;
        this.eventRiskLevel =
                eventRiskLevel;
        this.reason =
                reason.trim();
        this.positionSize =
                positionSize;
    }

    public Status getStatus() {
        return status;
    }

    public String getTradingDecision() {
        return tradingDecision;
    }

    public int getConfidencePercent() {
        return confidencePercent;
    }

    public EventRisk.Level getEventRiskLevel() {
        return eventRiskLevel;
    }

    public String getReason() {
        return reason;
    }

    public PositionSizeResult getPositionSize() {
        return positionSize;
    }

    public boolean isApproved() {
        return status == Status.APPROVED;
    }

    public boolean isBlocked() {
        return status == Status.BLOCKED;
    }

    // =========================================================
    // EXECUTABLE POSITION
    //
    // A blocked trade must never expose an executable position.
    // Position sizing may have been calculated earlier in the
    // analysis pipeline, but it is not executable unless final
    // trade approval has been granted.
    // =========================================================

    public int getQuantity() {

        if (!isApproved()
                || positionSize == null) {

            return 0;
        }

        return positionSize.getQuantity();
    }

    public BigDecimal getPositionValue() {

        if (!isApproved()
                || positionSize == null) {

            return BigDecimal.ZERO;
        }

        return positionSize.getPositionValue();
    }

    public BigDecimal getRiskAmount() {

        if (!isApproved()
                || positionSize == null) {

            return BigDecimal.ZERO;
        }

        return positionSize.getRiskAmount();
    }
}