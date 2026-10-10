package com.marshallai;

import com.marshallai.portfolio.governance.PortfolioExecutionCostService;
import com.marshallai.trading.execution.ExitReason;
import com.marshallai.trading.execution.GovernedPeriodBoundaryService;
import com.marshallai.trading.execution.TradeStopService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class GovernedPeriodBoundaryServiceTest {

    private GovernedPeriodBoundaryService service;

    @BeforeEach
    void setUp() {

        service =
                new GovernedPeriodBoundaryService(
                        new TradeStopService(),
                        new PortfolioExecutionCostService()
                );
    }

    @Test
    void finalBarBuySignalCannotCreateNewEntry() {

        GovernedPeriodBoundaryService.FinalBarEntryDecision decision =
                service.evaluateFinalBarEntry(
                        true
                );

        assertFalse(
                decision.orderCreated()
        );

        assertTrue(
                decision.skipped()
        );

        assertEquals(
                GovernedPeriodBoundaryService.SKIPPED_PERIOD_BOUNDARY,
                decision.outcomeReason()
        );
    }

    @Test
    void periodEndLiquidationUsesFinalBarCloseAsExecutionReference() {

        GovernedPeriodBoundaryService.PeriodEndLiquidation liquidation =
                service.liquidateAtPeriodEnd(
                        new BigDecimal(
                                "100.00"
                        )
                );

        assertEquals(
                0,
                liquidation.executionReferencePrice()
                        .compareTo(
                                new BigDecimal(
                                        "100.00"
                                )
                        )
        );

        assertEquals(
                ExitReason.PERIOD_END,
                liquidation.exitReason()
        );
    }

    @Test
    void periodEndLiquidationReceivesTwoBasisPointsAdverseAuctionSlippage() {

        GovernedPeriodBoundaryService.PeriodEndLiquidation liquidation =
                service.liquidateAtPeriodEnd(
                        new BigDecimal(
                                "100.00"
                        )
                );

        /*
         * Governance v1 auction slippage:
         *
         * 2 bps = 0.0002
         *
         * SELL fill:
         * 100.00 * (1 - 0.0002)
         * = 99.98
         */
        assertEquals(
                0,
                liquidation.modeledFillPrice()
                        .compareTo(
                                new BigDecimal(
                                        "99.98"
                                )
                        )
        );

        assertEquals(
                ExitReason.PERIOD_END,
                liquidation.exitReason()
        );
    }

    @Test
    void finalBarStopExecutesBeforePeriodEndLiquidation() {

        GovernedPeriodBoundaryService.FinalBarExitDecision decision =
                service.evaluateFinalBarExit(
                        new BigDecimal(
                                "90.00"
                        ),
                        new BigDecimal(
                                "100.00"
                        ),
                        new BigDecimal(
                                "89.00"
                        ),
                        new BigDecimal(
                                "95.00"
                        )
                );

        assertTrue(
                decision.stopTriggered()
        );

        assertFalse(
                decision.periodEndLiquidation()
        );

        assertEquals(
                ExitReason.STOP,
                decision.exitReason()
        );

        assertEquals(
                1,
                decision.exitCount()
        );

        /*
         * Low penetrated the stop during the bar, so the
         * execution reference remains the active stop.
         */
        assertEquals(
                0,
                decision.executionReferencePrice()
                        .compareTo(
                                new BigDecimal(
                                        "90.00"
                                )
                        )
        );

        /*
         * Portfolio Governance v1 applies 10 bps adverse
         * stop slippage:
         *
         * 90.00 * 0.999 = 89.91
         */
        assertEquals(
                0,
                decision.modeledFillPrice()
                        .compareTo(
                                new BigDecimal(
                                        "89.91"
                                )
                        )
        );
    }

    @Test
    void survivingFinalBarPositionReceivesExactlyOnePeriodEndExit() {

        GovernedPeriodBoundaryService.FinalBarExitDecision decision =
                service.evaluateFinalBarExit(
                        new BigDecimal(
                                "90.00"
                        ),
                        new BigDecimal(
                                "100.00"
                        ),
                        new BigDecimal(
                                "95.00"
                        ),
                        new BigDecimal(
                                "98.00"
                        )
                );

        assertFalse(
                decision.stopTriggered()
        );

        assertTrue(
                decision.periodEndLiquidation()
        );

        assertEquals(
                ExitReason.PERIOD_END,
                decision.exitReason()
        );

        assertEquals(
                1,
                decision.exitCount()
        );

        /*
         * The final-period close is the execution reference.
         */
        assertEquals(
                0,
                decision.executionReferencePrice()
                        .compareTo(
                                new BigDecimal(
                                        "98.00"
                                )
                        )
        );

        /*
         * 98.00 with 2 bps adverse SELL auction slippage:
         *
         * 98.00 * 0.9998 = 97.9804
         */
        assertEquals(
                0,
                decision.modeledFillPrice()
                        .compareTo(
                                new BigDecimal(
                                        "97.9804"
                                )
                        )
        );
    }

    @Test
    void noSignalOnFinalBarCreatesNoOrderAndNoSkipReason() {

        GovernedPeriodBoundaryService.FinalBarEntryDecision decision =
                service.evaluateFinalBarEntry(
                        false
                );

        assertFalse(
                decision.orderCreated()
        );

        assertFalse(
                decision.skipped()
        );

        assertNull(
                decision.outcomeReason()
        );
    }
}