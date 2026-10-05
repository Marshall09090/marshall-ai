package com.marshallai.trading.execution;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class TradeExitService {

    private final TradeDefinition tradeDefinition;

    public TradeExitService() {
        this.tradeDefinition = TradeDefinition.v1();
    }

    public TradeExitService(TradeDefinition tradeDefinition) {
        if (tradeDefinition == null) {
            throw new IllegalArgumentException(
                    "Trade definition cannot be null"
            );
        }

        this.tradeDefinition = tradeDefinition;
    }

    /**
     * Entry bar is holding bar 1.
     */
    public int initialHoldingBar() {
        return 1;
    }

    /**
     * The stale-trade rule is evaluated once only,
     * at the close of the configured stale-check bar.
     */
    public boolean shouldRunStaleCheck(int holdingBar) {
        return holdingBar == tradeDefinition.staleCheckBar();
    }

    /**
     * Unrealized R for the stale check:
     *
     * (close - fill) / frozen stop distance
     *
     * This is evaluated before exit costs.
     */
    public BigDecimal calculateUnrealizedR(
            BigDecimal closePrice,
            BigDecimal fillPrice,
            BigDecimal frozenStopDistance) {

        requirePositive(closePrice, "Close price");
        requirePositive(fillPrice, "Fill price");
        requirePositive(
                frozenStopDistance,
                "Frozen stop distance"
        );

        return closePrice
                .subtract(fillPrice)
                .divide(
                        frozenStopDistance,
                        8,
                        RoundingMode.HALF_UP
                );
    }

    public StaleCheckResult evaluateStaleCheck(
            int holdingBar,
            BigDecimal closePrice,
            BigDecimal fillPrice,
            BigDecimal frozenStopDistance) {

        if (!shouldRunStaleCheck(holdingBar)) {
            return StaleCheckResult.notEvaluated();
        }

        BigDecimal unrealizedR =
                calculateUnrealizedR(
                        closePrice,
                        fillPrice,
                        frozenStopDistance
                );

        boolean scheduleExit =
                unrealizedR.compareTo(
                        tradeDefinition.staleMinimumR()
                ) < 0;

        return new StaleCheckResult(
                true,
                unrealizedR,
                scheduleExit
        );
    }

    /**
     * Trade Definition v1 is long-only.
     *
     * A SELL signal can close an existing long,
     * but it cannot establish a short position.
     */
    public boolean shouldScheduleSellExit(
            boolean longPositionHeld) {

        return tradeDefinition.longOnly()
                && longPositionHeld;
    }

    public boolean shouldOpenShortPosition() {
        return false;
    }

    /**
     * Resolve a scheduled market-on-open exit.
     *
     * STOP has attribution priority. If the opening
     * price is at or below the already-active stop,
     * the position exits at that same opening price
     * and the reason is recorded as STOP.
     *
     * Otherwise the scheduled reason is retained.
     */
    public ExitEvaluation evaluateScheduledOpenExit(
            BigDecimal activeStop,
            BigDecimal openingPrice,
            ExitReason scheduledReason) {

        requireValue(activeStop, "Active stop");
        requirePositive(openingPrice, "Opening price");

        if (scheduledReason == null) {
            throw new IllegalArgumentException(
                    "Scheduled exit reason cannot be null"
            );
        }

        if (scheduledReason == ExitReason.STOP) {
            throw new IllegalArgumentException(
                    "STOP is not a scheduled exit reason"
            );
        }

        if (openingPrice.compareTo(activeStop) <= 0) {
            return new ExitEvaluation(
                    true,
                    openingPrice,
                    ExitReason.STOP
            );
        }

        return new ExitEvaluation(
                true,
                openingPrice,
                scheduledReason
        );
    }

    public TradeDefinition getTradeDefinition() {
        return tradeDefinition;
    }

    private static void requirePositive(
            BigDecimal value,
            String fieldName) {

        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be greater than zero"
            );
        }
    }

    private static void requireValue(
            BigDecimal value,
            String fieldName) {

        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be null"
            );
        }
    }

    public record StaleCheckResult(
            boolean evaluated,
            BigDecimal unrealizedR,
            boolean exitScheduled
    ) {

        public static StaleCheckResult notEvaluated() {
            return new StaleCheckResult(
                    false,
                    null,
                    false
            );
        }
    }

    public record ExitEvaluation(
            boolean exited,
            BigDecimal exitPrice,
            ExitReason exitReason
    ) {

        public ExitEvaluation {
            if (exited) {
                if (exitPrice == null ||
                        exitPrice.signum() <= 0) {
                    throw new IllegalArgumentException(
                            "Exited trade requires a positive exit price"
                    );
                }

                if (exitReason == null) {
                    throw new IllegalArgumentException(
                            "Exited trade requires an exit reason"
                    );
                }
            }
        }
    }
}