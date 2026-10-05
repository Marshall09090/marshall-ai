package com.marshallai.trading.execution;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class TradeStopService {

    private final TradeDefinition tradeDefinition;

    public TradeStopService() {
        this.tradeDefinition = TradeDefinition.v1();
    }

    public TradeStopService(TradeDefinition tradeDefinition) {
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

        requirePositive(fillPrice, "Fill price");
        requirePositive(
                frozenStopDistance,
                "Frozen stop distance"
        );

        return fillPrice.subtract(frozenStopDistance);
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
                        tradeDefinition.trailingStopAtrMultiplier()
                )
        );
    }

    public BigDecimal selectNextActiveStop(
            BigDecimal initialStop,
            BigDecimal previousActiveStop,
            BigDecimal candidateTrailingStop) {

        requireValue(initialStop, "Initial stop");
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

    public StopEvaluation evaluateStop(
            BigDecimal activeStop,
            BigDecimal barOpen,
            BigDecimal barLow) {

        requireValue(activeStop, "Active stop");
        requirePositive(barOpen, "Bar open");
        requirePositive(barLow, "Bar low");

        /*
         * If the market gaps through the stop, the fill occurs
         * at the opening price. Loss may therefore exceed 1R.
         */
        if (barOpen.compareTo(activeStop) <= 0) {
            return new StopEvaluation(
                    true,
                    barOpen,
                    ExitReason.STOP
            );
        }

        /*
         * Otherwise an intraday touch fills at the active stop.
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

    public record StopEvaluation(
            boolean triggered,
            BigDecimal exitPrice,
            ExitReason exitReason
    ) {

        public StopEvaluation {
            if (triggered) {
                if (exitPrice == null) {
                    throw new IllegalArgumentException(
                            "Triggered stop requires an exit price"
                    );
                }

                if (exitReason != ExitReason.STOP) {
                    throw new IllegalArgumentException(
                            "Triggered stop must use STOP exit reason"
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
}