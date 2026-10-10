package com.marshallai;

import com.marshallai.portfolio.governance.PortfolioExecutionCostService;
import com.marshallai.trading.execution.ExitReason;
import com.marshallai.trading.execution.GovernedEvaluationPeriodService;
import com.marshallai.trading.execution.GovernedPeriodBoundaryService;
import com.marshallai.trading.execution.TradeStopService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class GovernedEvaluationPeriodServiceTest {

    private GovernedEvaluationPeriodService service;

    @BeforeEach
    void setUp() {

        GovernedPeriodBoundaryService boundaryService =
                new GovernedPeriodBoundaryService(
                        new TradeStopService(),
                        new PortfolioExecutionCostService()
                );

        service =
                new GovernedEvaluationPeriodService(
                        boundaryService
                );
    }

    @Test
    void newGovernedPeriodStartsFlatAndRejectsInheritedPosition() {

        GovernedEvaluationPeriodService.OpenTrade
                previousTrade =
                trade(
                        "previous-trade",
                        GovernedEvaluationPeriodService
                                .EvaluationPeriod.TUNING,
                        "90.00"
                );

        GovernedEvaluationPeriodService.PeriodStart
                validationStart =
                service.beginPeriod(
                        GovernedEvaluationPeriodService
                                .EvaluationPeriod.VALIDATION,
                        previousTrade
                );

        assertTrue(
                validationStart.startsFlat()
        );

        assertEquals(
                0,
                validationStart.openPositionCount()
        );

        assertTrue(
                validationStart.previousPositionRejected()
        );
    }

    @Test
    void tuningTradeIsClosedBeforeValidationStarts() {

        GovernedEvaluationPeriodService.OpenTrade tuningTrade =
                trade(
                        "tuning-trade",
                        GovernedEvaluationPeriodService
                                .EvaluationPeriod.TUNING,
                        "90.00"
                );

        GovernedEvaluationPeriodService.BoundaryClose close =
                service.closeAtPeriodEnd(
                        tuningTrade,
                        new BigDecimal(
                                "100.00"
                        )
                );

        assertEquals(
                ExitReason.PERIOD_END,
                close.exitReason()
        );

        assertEquals(
                GovernedEvaluationPeriodService
                        .EvaluationPeriod.TUNING,
                close.attributedPeriod()
        );

        GovernedEvaluationPeriodService.PeriodStart validation =
                service.beginPeriod(
                        GovernedEvaluationPeriodService
                                .EvaluationPeriod.VALIDATION,
                        tuningTrade
                );

        assertTrue(
                validation.startsFlat()
        );

        assertTrue(
                validation.previousPositionRejected()
        );
    }

    @Test
    void validationTradeIsClosedBeforeHoldoutStarts() {

        GovernedEvaluationPeriodService.OpenTrade validationTrade =
                trade(
                        "validation-trade",
                        GovernedEvaluationPeriodService
                                .EvaluationPeriod.VALIDATION,
                        "95.00"
                );

        GovernedEvaluationPeriodService.BoundaryClose close =
                service.closeAtPeriodEnd(
                        validationTrade,
                        new BigDecimal(
                                "100.00"
                        )
                );

        assertEquals(
                ExitReason.PERIOD_END,
                close.exitReason()
        );

        assertEquals(
                GovernedEvaluationPeriodService
                        .EvaluationPeriod.VALIDATION,
                close.attributedPeriod()
        );

        GovernedEvaluationPeriodService.PeriodStart holdout =
                service.beginPeriod(
                        GovernedEvaluationPeriodService
                                .EvaluationPeriod.HOLDOUT,
                        validationTrade
                );

        assertTrue(
                holdout.startsFlat()
        );

        assertTrue(
                holdout.previousPositionRejected()
        );
    }

    @Test
    void periodEndProfitLossBelongsToOpeningPeriodOnly() {

        GovernedEvaluationPeriodService.OpenTrade validationTrade =
                trade(
                        "validation-attribution",
                        GovernedEvaluationPeriodService
                                .EvaluationPeriod.VALIDATION,
                        "90.00"
                );

        GovernedEvaluationPeriodService.BoundaryClose close =
                service.closeAtPeriodEnd(
                        validationTrade,
                        new BigDecimal(
                                "100.00"
                        )
                );

        assertEquals(
                GovernedEvaluationPeriodService
                        .EvaluationPeriod.VALIDATION,
                close.openedPeriod()
        );

        assertEquals(
                GovernedEvaluationPeriodService
                        .EvaluationPeriod.VALIDATION,
                close.attributedPeriod()
        );

        assertNotEquals(
                GovernedEvaluationPeriodService
                        .EvaluationPeriod.HOLDOUT,
                close.attributedPeriod()
        );

        /*
         * 100.00 final close receives 2 bps adverse SELL
         * auction slippage:
         *
         * fill = 99.98
         * entry = 90.00
         * quantity = 1
         * P/L = 9.98
         */
        assertDecimalEquals(
                new BigDecimal(
                        "9.98"
                ),
                close.profitLoss()
        );
    }

    @Test
    void repeatedTuningResearchInputsProduceIdenticalBoundaryResults() {

        GovernedEvaluationPeriodService.OpenTrade tuningTrade =
                trade(
                        "deterministic-tuning-trade",
                        GovernedEvaluationPeriodService
                                .EvaluationPeriod.TUNING,
                        "90.00"
                );

        GovernedEvaluationPeriodService.ResearchBoundaryInput input =
                new GovernedEvaluationPeriodService
                        .ResearchBoundaryInput(
                        "tuning-dataset-sha256-v1",
                        "governed-configuration-sha256-v1",
                        LocalDate.of(
                                2025,
                                1,
                                1
                        ),
                        LocalDate.of(
                                2025,
                                6,
                                30
                        ),
                        tuningTrade,
                        new BigDecimal(
                                "100.00"
                        ),
                        true
                );

        GovernedEvaluationPeriodService.ResearchBoundaryResult first =
                service.evaluateResearchBoundary(
                        input
                );

        GovernedEvaluationPeriodService.ResearchBoundaryResult second =
                service.evaluateResearchBoundary(
                        input
                );

        assertEquals(
                first.deterministicFingerprint(),
                second.deterministicFingerprint()
        );

        assertEquals(
                first.boundaryClose(),
                second.boundaryClose()
        );

        assertEquals(
                first.finalBarEntryDecision(),
                second.finalBarEntryDecision()
        );

        assertEquals(
                ExitReason.PERIOD_END,
                first.boundaryClose()
                        .exitReason()
        );

        assertTrue(
                first.finalBarEntryDecision()
                        .skipped()
        );

        assertEquals(
                GovernedPeriodBoundaryService
                        .SKIPPED_PERIOD_BOUNDARY,
                first.finalBarEntryDecision()
                        .outcomeReason()
        );
    }

    private static GovernedEvaluationPeriodService.OpenTrade trade(
            String tradeId,
            GovernedEvaluationPeriodService.EvaluationPeriod period,
            String entryPrice) {

        return new GovernedEvaluationPeriodService.OpenTrade(
                tradeId,
                period,
                new BigDecimal(
                        entryPrice
                ),
                1
        );
    }

    private static void assertDecimalEquals(
            BigDecimal expected,
            BigDecimal actual) {

        assertNotNull(
                actual
        );

        assertEquals(
                0,
                expected.compareTo(
                        actual
                ),
                "Expected "
                        + expected
                        + " but was "
                        + actual
        );
    }
}