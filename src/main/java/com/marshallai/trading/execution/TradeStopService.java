package com.marshallai.trading.execution;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class TradeStopService {

    private final TradeDefinition tradeDefinition;

    public TradeStopService() {
        this.tradeDefinition = TradeDefinition.v1();
    }

    public TradeStopService(
            TradeDefinition tradeDefinition) {

        if (tradeDefinition == null) {
            throw new IllegalArgumentException(
                    "Trade definition cannot be null"
            );
        }

        this.tradeDefinition = tradeDefinition;
    }

    public BigDecimal calculateInitialStop(
            BigDecimal fillPrice,
            BigDecimal frozenStopDistance) {

        requirePositive(
                fillPrice,
                "Fill price"
        );

        requirePositive(
                frozenStopDistance,
                "Frozen stop distance"
        );

        BigDecimal initialStop =
                fillPrice.subtract(
                        frozenStopDistance
                );

        if (initialStop.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Initial stop must remain greater than zero"
            );
        }

        return initialStop;
    }

    public BigDecimal calculateCandidateTrailingStop(
            BigDecimal highestHighSinceEntry,
            BigDecimal completedBarAtr) {

        requirePositive(
                highestHighSinceEntry,
                "Highest high since entry"
        );

        requirePositive(
                completedBarAtr,
                "Completed bar ATR"
        );

        return highestHighSinceEntry.subtract(
                completedBarAtr.multiply(
                        tradeDefinition
                                .trailingStopAtrMultiplier()
                )
        );
    }

    public BigDecimal selectNextActiveStop(
            BigDecimal initialStop,
            BigDecimal previousActiveStop,
            BigDecimal candidateTrailingStop) {

        requireValue(
                initialStop,
                "Initial stop"
        );

        requireValue(
                previousActiveStop,
                "Previous active stop"
        );

        requireValue(
                candidateTrailingStop,
                "Candidate trailing stop"
        );

        return initialStop
                .max(previousActiveStop)
                .max(candidateTrailingStop);
    }

    /**
     * Evaluates the active stop for a completed trading bar.
     *
     * If the market opens through the stop, the execution
     * reference is the opening price.
     *
     * Otherwise, if the bar low touches or crosses the stop,
     * the execution reference is the active stop.
     *
     * Portfolio-level execution-cost modeling may subsequently
     * apply adverse stop slippage to this reference price.
     */
    public StopEvaluation evaluateStop(
            BigDecimal activeStop,
            BigDecimal barOpen,
            BigDecimal barLow) {

        requirePositive(
                activeStop,
                "Active stop"
        );

        requirePositive(
                barOpen,
                "Bar open"
        );

        requirePositive(
                barLow,
                "Bar low"
        );

        if (barLow.compareTo(barOpen) > 0) {
            throw new IllegalArgumentException(
                    "Bar low cannot exceed bar open"
            );
        }

        /*
         * Gap through the active stop.
         */
        if (barOpen.compareTo(activeStop) <= 0) {
            return new StopEvaluation(
                    true,
                    barOpen,
                    ExitReason.STOP
            );
        }

        /*
         * Intraday touch or penetration.
         *
         * The comparison is intentionally inclusive:
         * low == stop triggers the stop.
         */
        if (barLow.compareTo(activeStop) <= 0) {
            return new StopEvaluation(
                    true,
                    activeStop,
                    ExitReason.STOP
            );
        }

        return StopEvaluation.notTriggered();
    }

    /**
     * Entry-bar stop evaluation for a newly filled long trade.
     *
     * Frozen v1 rules:
     *
     * 1. The entry bar is holding bar 1.
     * 2. The initial stop is active immediately after the fill.
     * 3. low <= initial stop triggers the stop.
     * 4. The entry bar cannot raise its own trailing stop.
     * 5. If the trade survives, the next holding bar is bar 2.
     *
     * This method returns the stop trigger/reference price.
     * Adverse stop slippage is applied separately by portfolio
     * execution-cost modeling.
     */
    public EntryBarStopEvaluation evaluateEntryBarStop(
            BigDecimal fillPrice,
            BigDecimal initialStop,
            BigDecimal entryBarLow) {

        requirePositive(
                fillPrice,
                "Fill price"
        );

        requirePositive(
                initialStop,
                "Initial stop"
        );

        requirePositive(
                entryBarLow,
                "Entry bar low"
        );

        if (initialStop.compareTo(fillPrice) >= 0) {
            throw new IllegalArgumentException(
                    "Initial stop for a long trade must be below the fill price"
            );
        }

        boolean triggered =
                entryBarLow.compareTo(
                        initialStop
                ) <= 0;

        if (triggered) {
            return EntryBarStopEvaluation.triggered(
                    initialStop
            );
        }

        return EntryBarStopEvaluation.survived(
                initialStop
        );
    }

    /**
     * Entry-bar overload that accepts the bar high explicitly.
     *
     * The high is validated but deliberately does not alter
     * the active stop during the same bar.
     *
     * A trailing stop derived from this bar can only become
     * active on the following bar.
     */
    public EntryBarStopEvaluation evaluateEntryBarStop(
            BigDecimal fillPrice,
            BigDecimal initialStop,
            BigDecimal entryBarHigh,
            BigDecimal entryBarLow) {

        requirePositive(
                entryBarHigh,
                "Entry bar high"
        );

        requirePositive(
                entryBarLow,
                "Entry bar low"
        );

        if (entryBarHigh.compareTo(entryBarLow) < 0) {
            throw new IllegalArgumentException(
                    "Entry bar high cannot be below entry bar low"
            );
        }

        if (entryBarHigh.compareTo(fillPrice) < 0) {
            throw new IllegalArgumentException(
                    "Entry bar high cannot be below the opening fill price"
            );
        }

        /*
         * Intentionally ignore entryBarHigh when choosing the
         * active stop for bar 1.
         *
         * This prevents the bar's own high from raising the
         * trailing stop before the bar's low is evaluated.
         */
        return evaluateEntryBarStop(
                fillPrice,
                initialStop,
                entryBarLow
        );
    }

    public TradeDefinition getTradeDefinition() {
        return tradeDefinition;
    }

    private static void requirePositive(
            BigDecimal value,
            String fieldName) {

        if (value == null ||
                value.signum() <= 0) {
            throw new IllegalArgumentException(
                    fieldName +
                            " must be greater than zero"
            );
        }
    }

    private static void requireValue(
            BigDecimal value,
            String fieldName) {

        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName +
                            " cannot be null"
            );
        }
    }

    public record StopEvaluation(
            boolean triggered,
            BigDecimal exitPrice,
            ExitReason exitReason
    ) {

        public StopEvaluation {
            if (triggered) {
                requirePositive(
                        exitPrice,
                        "Triggered stop exit price"
                );

                if (exitReason != ExitReason.STOP) {
                    throw new IllegalArgumentException(
                            "Triggered stop must use STOP exit reason"
                    );
                }
            } else {
                if (exitPrice != null ||
                        exitReason != null) {
                    throw new IllegalArgumentException(
                            "Non-triggered stop cannot have an exit price or exit reason"
                    );
                }
            }
        }

        public static StopEvaluation notTriggered() {
            return new StopEvaluation(
                    false,
                    null,
                    null
            );
        }
    }

    public record EntryBarStopEvaluation(
            boolean triggered,
            BigDecimal activeStop,
            BigDecimal triggerPrice,
            ExitReason exitReason,
            int holdingBar,
            Integer nextHoldingBar
    ) {

        public EntryBarStopEvaluation {

            requirePositive(
                    activeStop,
                    "Entry-bar active stop"
            );

            if (holdingBar != 1) {
                throw new IllegalArgumentException(
                        "Entry bar must be holding bar 1"
                );
            }

            if (triggered) {

                requirePositive(
                        triggerPrice,
                        "Entry-bar stop trigger price"
                );

                if (exitReason != ExitReason.STOP) {
                    throw new IllegalArgumentException(
                            "Triggered entry-bar stop must use STOP exit reason"
                    );
                }

                if (nextHoldingBar != null) {
                    throw new IllegalArgumentException(
                            "Stopped entry-bar trade cannot advance to another holding bar"
                    );
                }

            } else {

                if (triggerPrice != null ||
                        exitReason != null) {
                    throw new IllegalArgumentException(
                            "Surviving entry bar cannot have a stop trigger price or exit reason"
                    );
                }

                if (nextHoldingBar == null ||
                        nextHoldingBar != 2) {
                    throw new IllegalArgumentException(
                            "A trade surviving the entry bar must advance to holding bar 2"
                    );
                }
            }
        }

        public static EntryBarStopEvaluation triggered(
                BigDecimal initialStop) {

            return new EntryBarStopEvaluation(
                    true,
                    initialStop,
                    initialStop,
                    ExitReason.STOP,
                    1,
                    null
            );
        }

        public static EntryBarStopEvaluation survived(
                BigDecimal initialStop) {

            return new EntryBarStopEvaluation(
                    false,
                    initialStop,
                    null,
                    null,
                    1,
                    2
            );
        }
    }
}