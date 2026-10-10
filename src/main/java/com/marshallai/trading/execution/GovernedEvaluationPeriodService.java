package com.marshallai.trading.execution;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * Governs isolation between tuning, validation, and holdout
 * evaluation periods.
 *
 * Rules:
 *
 * 1. Every new governed period starts flat.
 * 2. A position cannot cross from one period into another.
 * 3. A surviving position is closed with PERIOD_END before
 *    the next period begins.
 * 4. A PERIOD_END trade remains attributed to the period
 *    where the trade was opened.
 * 5. Identical research inputs produce identical boundary
 *    outcomes.
 */
@Service
public class GovernedEvaluationPeriodService {

    private final GovernedPeriodBoundaryService
            boundaryService;

    public GovernedEvaluationPeriodService(
            GovernedPeriodBoundaryService boundaryService) {

        if (boundaryService == null) {
            throw new IllegalArgumentException(
                    "Governed period boundary service cannot be null"
            );
        }

        this.boundaryService =
                boundaryService;
    }

    /**
     * Starts a new governed evaluation period.
     *
     * A position from the previous period is never inherited.
     */
    public PeriodStart beginPeriod(
            EvaluationPeriod period,
            OpenTrade previousPeriodPosition) {

        requirePeriod(
                period
        );

        return new PeriodStart(
                period,
                0,
                previousPeriodPosition != null
        );
    }

    /**
     * Closes a surviving trade at the boundary of the period
     * in which that trade was opened.
     */
    public BoundaryClose closeAtPeriodEnd(
            OpenTrade openTrade,
            BigDecimal finalBarClose) {

        if (openTrade == null) {
            throw new IllegalArgumentException(
                    "Open trade cannot be null"
            );
        }

        requirePositive(
                finalBarClose,
                "Final bar close"
        );

        GovernedPeriodBoundaryService.PeriodEndLiquidation
                liquidation =
                boundaryService.liquidateAtPeriodEnd(
                        finalBarClose
                );

        BigDecimal profitLoss =
                liquidation.modeledFillPrice()
                        .subtract(
                                openTrade.entryPrice()
                        )
                        .multiply(
                                BigDecimal.valueOf(
                                        openTrade.quantity()
                                )
                        );

        return new BoundaryClose(
                openTrade.tradeId(),
                openTrade.openedPeriod(),
                openTrade.openedPeriod(),
                finalBarClose,
                liquidation.executionReferencePrice(),
                liquidation.modeledFillPrice(),
                liquidation.exitReason(),
                profitLoss
        );
    }

    /**
     * Performs the deterministic portion of a tuning research
     * boundary evaluation.
     *
     * The same:
     *
     * - tuning dataset fingerprint
     * - governed configuration hash
     * - period boundary dates
     * - open trade
     * - final close
     * - final-bar signal
     *
     * must produce the same value result.
     */
    public ResearchBoundaryResult evaluateResearchBoundary(
            ResearchBoundaryInput input) {

        if (input == null) {
            throw new IllegalArgumentException(
                    "Research boundary input cannot be null"
            );
        }

        GovernedPeriodBoundaryService.FinalBarEntryDecision
                entryDecision =
                boundaryService.evaluateFinalBarEntry(
                        input.finalBarBuySignal()
                );

        BoundaryClose boundaryClose =
                input.openTrade() == null
                        ? null
                        : closeAtPeriodEnd(
                        input.openTrade(),
                        input.finalBarClose()
                );

        String deterministicFingerprint =
                fingerprint(
                        canonicalResearchInput(
                                input
                        )
                );

        return new ResearchBoundaryResult(
                deterministicFingerprint,
                boundaryClose,
                entryDecision
        );
    }

    private static String canonicalResearchInput(
            ResearchBoundaryInput input) {

        return String.join(
                "|",
                input.tuningDatasetFingerprint(),
                input.governedConfigurationHash(),
                input.periodStart().toString(),
                input.periodEnd().toString(),
                canonicalOpenTrade(
                        input.openTrade()
                ),
                input.finalBarClose()
                        .stripTrailingZeros()
                        .toPlainString(),
                Boolean.toString(
                        input.finalBarBuySignal()
                )
        );
    }

    private static String canonicalOpenTrade(
            OpenTrade openTrade) {

        if (openTrade == null) {
            return "NO_OPEN_TRADE";
        }

        return String.join(
                ":",
                openTrade.tradeId(),
                openTrade.openedPeriod().name(),
                openTrade.entryPrice()
                        .stripTrailingZeros()
                        .toPlainString(),
                Integer.toString(
                        openTrade.quantity()
                )
        );
    }

    private static String fingerprint(
            String value) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            return HexFormat.of()
                    .formatHex(
                            digest.digest(
                                    value.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            )
                    );

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to fingerprint research boundary input",
                    exception
            );
        }
    }

    private static void requirePeriod(
            EvaluationPeriod period) {

        if (period == null) {
            throw new IllegalArgumentException(
                    "Evaluation period cannot be null"
            );
        }
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

    private static void requireText(
            String value,
            String fieldName) {

        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    fieldName
                            + " cannot be blank"
            );
        }
    }

    public enum EvaluationPeriod {

        TUNING,

        VALIDATION,

        HOLDOUT
    }

    public record OpenTrade(
            String tradeId,
            EvaluationPeriod openedPeriod,
            BigDecimal entryPrice,
            int quantity) {

        public OpenTrade {

            requireText(
                    tradeId,
                    "Trade ID"
            );

            requirePeriod(
                    openedPeriod
            );

            requirePositive(
                    entryPrice,
                    "Entry price"
            );

            if (quantity <= 0) {
                throw new IllegalArgumentException(
                        "Trade quantity must be greater than zero"
                );
            }
        }
    }

    public record PeriodStart(
            EvaluationPeriod period,
            int openPositionCount,
            boolean previousPositionRejected) {

        public PeriodStart {

            requirePeriod(
                    period
            );

            if (openPositionCount != 0) {
                throw new IllegalArgumentException(
                        "A governed period must start with zero "
                                + "open positions"
                );
            }
        }

        public boolean startsFlat() {

            return openPositionCount == 0;
        }
    }

    public record BoundaryClose(
            String tradeId,
            EvaluationPeriod openedPeriod,
            EvaluationPeriod attributedPeriod,
            BigDecimal finalBarClose,
            BigDecimal executionReferencePrice,
            BigDecimal modeledFillPrice,
            ExitReason exitReason,
            BigDecimal profitLoss) {

        public BoundaryClose {

            requireText(
                    tradeId,
                    "Trade ID"
            );

            requirePeriod(
                    openedPeriod
            );

            requirePeriod(
                    attributedPeriod
            );

            requirePositive(
                    finalBarClose,
                    "Final bar close"
            );

            requirePositive(
                    executionReferencePrice,
                    "Execution reference price"
            );

            requirePositive(
                    modeledFillPrice,
                    "Modeled fill price"
            );

            if (exitReason != ExitReason.PERIOD_END) {
                throw new IllegalArgumentException(
                        "Boundary close must use PERIOD_END"
                );
            }

            if (profitLoss == null) {
                throw new IllegalArgumentException(
                        "Profit or loss cannot be null"
                );
            }

            if (openedPeriod != attributedPeriod) {
                throw new IllegalArgumentException(
                        "Boundary trade must remain attributed "
                                + "to its opening period"
                );
            }
        }
    }

    public record ResearchBoundaryInput(
            String tuningDatasetFingerprint,
            String governedConfigurationHash,
            LocalDate periodStart,
            LocalDate periodEnd,
            OpenTrade openTrade,
            BigDecimal finalBarClose,
            boolean finalBarBuySignal) {

        public ResearchBoundaryInput {

            requireText(
                    tuningDatasetFingerprint,
                    "Tuning dataset fingerprint"
            );

            requireText(
                    governedConfigurationHash,
                    "Governed configuration hash"
            );

            if (periodStart == null) {
                throw new IllegalArgumentException(
                        "Period start cannot be null"
                );
            }

            if (periodEnd == null) {
                throw new IllegalArgumentException(
                        "Period end cannot be null"
                );
            }

            if (periodStart.isAfter(
                    periodEnd
            )) {
                throw new IllegalArgumentException(
                        "Period start cannot be after period end"
                );
            }

            requirePositive(
                    finalBarClose,
                    "Final bar close"
            );

            if (openTrade != null
                    && openTrade.openedPeriod()
                    != EvaluationPeriod.TUNING) {

                throw new IllegalArgumentException(
                        "Tuning research boundary trade must "
                                + "belong to TUNING"
                );
            }
        }
    }

    public record ResearchBoundaryResult(
            String deterministicFingerprint,
            BoundaryClose boundaryClose,
            GovernedPeriodBoundaryService.FinalBarEntryDecision
            finalBarEntryDecision) {

        public ResearchBoundaryResult {

            requireText(
                    deterministicFingerprint,
                    "Deterministic fingerprint"
            );

            if (finalBarEntryDecision == null) {
                throw new IllegalArgumentException(
                        "Final-bar entry decision cannot be null"
                );
            }
        }
    }
}