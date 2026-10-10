package com.marshallai.trading.execution;

import com.marshallai.portfolio.governance.PortfolioExecutionCostService;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Governs execution behavior at the final bar of a governed
 * tuning, validation, or holdout evaluation period.
 *
 * Rules:
 *
 * 1. A final-period bar cannot create a new entry.
 * 2. An active stop is evaluated before PERIOD_END liquidation.
 * 3. If the stop triggers, no PERIOD_END exit is created.
 * 4. If the stop does not trigger, exactly one PERIOD_END exit
 *    is created.
 * 5. PERIOD_END uses the final bar close as its execution
 *    reference.
 * 6. PERIOD_END receives adverse SELL auction slippage.
 * 7. No next-period opening price is accepted by this API.
 */
@Service
public class GovernedPeriodBoundaryService {

    public static final String SKIPPED_PERIOD_BOUNDARY =
            "SKIPPED_PERIOD_BOUNDARY";

    private final TradeStopService tradeStopService;

    private final PortfolioExecutionCostService
            executionCostService;

    public GovernedPeriodBoundaryService(
            TradeStopService tradeStopService,
            PortfolioExecutionCostService executionCostService) {

        if (tradeStopService == null) {
            throw new IllegalArgumentException(
                    "Trade stop service cannot be null"
            );
        }

        if (executionCostService == null) {
            throw new IllegalArgumentException(
                    "Portfolio execution-cost service cannot be null"
            );
        }

        this.tradeStopService =
                tradeStopService;

        this.executionCostService =
                executionCostService;
    }

    /**
     * The final bar of a governed period may not originate
     * a new position.
     */
    public FinalBarEntryDecision evaluateFinalBarEntry(
            boolean buySignalPresent) {

        if (!buySignalPresent) {
            return FinalBarEntryDecision.noSignal();
        }

        return FinalBarEntryDecision.skippedAtBoundary();
    }

    /**
     * Force-closes a position that survives through the final
     * governed bar.
     *
     * The final bar close is the execution reference.
     */
    public PeriodEndLiquidation liquidateAtPeriodEnd(
            BigDecimal finalBarClose) {

        requirePositive(
                finalBarClose,
                "Final bar close"
        );

        BigDecimal modeledFillPrice =
                executionCostService
                        .applySellAuctionSlippage(
                                finalBarClose
                        );

        return new PeriodEndLiquidation(
                finalBarClose,
                modeledFillPrice,
                ExitReason.PERIOD_END
        );
    }

    /**
     * Applies final-bar exit priority.
     *
     * STOP is evaluated first because it was already active
     * during the final trading bar.
     *
     * If STOP triggers, it wins and there is no second
     * PERIOD_END exit.
     *
     * If STOP does not trigger, the surviving position is
     * liquidated once using the final close.
     */
    public FinalBarExitDecision evaluateFinalBarExit(
            BigDecimal activeStop,
            BigDecimal finalBarOpen,
            BigDecimal finalBarLow,
            BigDecimal finalBarClose) {

        requirePositive(
                activeStop,
                "Active stop"
        );

        requirePositive(
                finalBarOpen,
                "Final bar open"
        );

        requirePositive(
                finalBarLow,
                "Final bar low"
        );

        requirePositive(
                finalBarClose,
                "Final bar close"
        );

        TradeStopService.StopEvaluation stopEvaluation =
                tradeStopService.evaluateStop(
                        activeStop,
                        finalBarOpen,
                        finalBarLow
                );

        if (stopEvaluation.triggered()) {

            BigDecimal stopExecutionReference =
                    stopEvaluation.exitPrice();

            BigDecimal modeledStopFill =
                    executionCostService
                            .applySellStopSlippage(
                                    stopExecutionReference
                            );

            return FinalBarExitDecision.stop(
                    stopExecutionReference,
                    modeledStopFill
            );
        }

        PeriodEndLiquidation liquidation =
                liquidateAtPeriodEnd(
                        finalBarClose
                );

        return FinalBarExitDecision.periodEnd(
                liquidation.executionReferencePrice(),
                liquidation.modeledFillPrice()
        );
    }

    private static void requirePositive(
            BigDecimal value,
            String fieldName) {

        if (value == null
                || value.signum() <= 0) {

            throw new IllegalArgumentException(
                    fieldName
                            + " must be greater than zero"
            );
        }
    }

    public record FinalBarEntryDecision(
            boolean orderCreated,
            boolean skipped,
            String outcomeReason) {

        public FinalBarEntryDecision {

            if (orderCreated && skipped) {
                throw new IllegalArgumentException(
                        "A final-bar entry cannot be both "
                                + "created and skipped"
                );
            }

            if (skipped
                    && !SKIPPED_PERIOD_BOUNDARY.equals(
                    outcomeReason
            )) {

                throw new IllegalArgumentException(
                        "Skipped final-bar entry must use "
                                + SKIPPED_PERIOD_BOUNDARY
                );
            }

            if (!skipped
                    && outcomeReason != null) {

                throw new IllegalArgumentException(
                        "Non-skipped final-bar entry cannot "
                                + "have a skip reason"
                );
            }
        }

        public static FinalBarEntryDecision
        skippedAtBoundary() {

            return new FinalBarEntryDecision(
                    false,
                    true,
                    SKIPPED_PERIOD_BOUNDARY
            );
        }

        public static FinalBarEntryDecision noSignal() {

            return new FinalBarEntryDecision(
                    false,
                    false,
                    null
            );
        }
    }

    public record PeriodEndLiquidation(
            BigDecimal executionReferencePrice,
            BigDecimal modeledFillPrice,
            ExitReason exitReason) {

        public PeriodEndLiquidation {

            requirePositive(
                    executionReferencePrice,
                    "Period-end execution reference price"
            );

            requirePositive(
                    modeledFillPrice,
                    "Period-end modeled fill price"
            );

            if (exitReason != ExitReason.PERIOD_END) {
                throw new IllegalArgumentException(
                        "Period-end liquidation must use PERIOD_END"
                );
            }
        }
    }

    public record FinalBarExitDecision(
            boolean stopTriggered,
            boolean periodEndLiquidation,
            BigDecimal executionReferencePrice,
            BigDecimal modeledFillPrice,
            ExitReason exitReason,
            int exitCount) {

        public FinalBarExitDecision {

            if (stopTriggered == periodEndLiquidation) {
                throw new IllegalArgumentException(
                        "Exactly one final-bar exit path "
                                + "must be selected"
                );
            }

            requirePositive(
                    executionReferencePrice,
                    "Final-bar execution reference price"
            );

            requirePositive(
                    modeledFillPrice,
                    "Final-bar modeled fill price"
            );

            if (exitReason == null) {
                throw new IllegalArgumentException(
                        "Final-bar exit reason cannot be null"
                );
            }

            if (exitCount != 1) {
                throw new IllegalArgumentException(
                        "Final-bar evaluation must create "
                                + "exactly one exit"
                );
            }

            if (stopTriggered
                    && exitReason != ExitReason.STOP) {

                throw new IllegalArgumentException(
                        "Stop-triggered final-bar exit must use STOP"
                );
            }

            if (periodEndLiquidation
                    && exitReason != ExitReason.PERIOD_END) {

                throw new IllegalArgumentException(
                        "Boundary liquidation must use PERIOD_END"
                );
            }
        }

        public static FinalBarExitDecision stop(
                BigDecimal executionReferencePrice,
                BigDecimal modeledFillPrice) {

            return new FinalBarExitDecision(
                    true,
                    false,
                    executionReferencePrice,
                    modeledFillPrice,
                    ExitReason.STOP,
                    1
            );
        }

        public static FinalBarExitDecision periodEnd(
                BigDecimal executionReferencePrice,
                BigDecimal modeledFillPrice) {

            return new FinalBarExitDecision(
                    false,
                    true,
                    executionReferencePrice,
                    modeledFillPrice,
                    ExitReason.PERIOD_END,
                    1
            );
        }
    }
}