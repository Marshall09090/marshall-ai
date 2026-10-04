package com.marshallai.risk;

import com.marshallai.signal.MarketSignal;
import org.springframework.stereotype.Service;

@Service
public class TradeApprovalService {

    private static final int MINIMUM_CONFIDENCE_PERCENT = 80;

    public TradeApprovalResult evaluate(
            MarketSignal marketSignal,
            EventRisk eventRisk,
            PositionSizeResult positionSize) {

        if (marketSignal == null) {
            throw new IllegalArgumentException(
                    "Market signal cannot be null"
            );
        }

        if (eventRisk == null) {
            throw new IllegalArgumentException(
                    "Event risk cannot be null"
            );
        }

        /*
         * -------------------------------------------------
         * 1. HOLD SIGNAL GATE
         * -------------------------------------------------
         */

        if (marketSignal.getDecision()
                == MarketSignal.Decision.HOLD) {

            return new TradeApprovalResult(
                    TradeApprovalResult.Status.BLOCKED,
                    marketSignal.getDecision().name(),
                    marketSignal.getConfidencePercent(),
                    eventRisk.getLevel(),
                    "Trading signal is HOLD",
                    positionSize
            );
        }

        /*
         * -------------------------------------------------
         * 2. CONFIDENCE GATE
         * -------------------------------------------------
         */

        if (marketSignal.getConfidencePercent()
                < MINIMUM_CONFIDENCE_PERCENT) {

            return new TradeApprovalResult(
                    TradeApprovalResult.Status.BLOCKED,
                    marketSignal.getDecision().name(),
                    marketSignal.getConfidencePercent(),
                    eventRisk.getLevel(),
                    "Signal confidence is below 80 percent",
                    positionSize
            );
        }

        /*
         * -------------------------------------------------
         * 3. EVENT RISK GATE
         * -------------------------------------------------
         */

        if (eventRisk.isTradeBlocked()) {

            return new TradeApprovalResult(
                    TradeApprovalResult.Status.BLOCKED,
                    marketSignal.getDecision().name(),
                    marketSignal.getConfidencePercent(),
                    eventRisk.getLevel(),
                    "Trading blocked by market event risk",
                    positionSize
            );
        }

        /*
         * -------------------------------------------------
         * 4. POSITION SIZE GATE
         * -------------------------------------------------
         */

        if (positionSize == null
                || positionSize.getQuantity() <= 0) {

            return new TradeApprovalResult(
                    TradeApprovalResult.Status.BLOCKED,
                    marketSignal.getDecision().name(),
                    marketSignal.getConfidencePercent(),
                    eventRisk.getLevel(),
                    "Position size is zero",
                    positionSize
            );
        }

        /*
         * -------------------------------------------------
         * 5. FINAL TRADE APPROVAL
         * -------------------------------------------------
         */

        return new TradeApprovalResult(
                TradeApprovalResult.Status.APPROVED,
                marketSignal.getDecision().name(),
                marketSignal.getConfidencePercent(),
                eventRisk.getLevel(),
                "Trade approved",
                positionSize
        );
    }
}