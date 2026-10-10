package com.marshallai;

import com.marshallai.portfolio.governance.GovernedPortfolioResultService;
import com.marshallai.portfolio.governance.PortfolioDrawdownService;
import com.marshallai.trading.execution.ExitReason;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GovernedPortfolioResultServiceTest {

    private GovernedPortfolioResultService service;

    @BeforeEach
    void setUp() {

        service =
                new GovernedPortfolioResultService(
                        new PortfolioDrawdownService()
                );
    }

    @Test
    void periodEndTradeRemainsInGovernedPortfolioResults() {

        GovernedPortfolioResultService.CompletedGovernedTrade
                periodEndTrade =
                new GovernedPortfolioResultService
                        .CompletedGovernedTrade(
                        "period-end-trade",
                        ExitReason.PERIOD_END,
                        new BigDecimal(
                                "-100.00"
                        ),
                        new BigDecimal(
                                "-0.50"
                        )
                );

        GovernedPortfolioResultService.GovernedPortfolioResults
                results =
                service.calculateResults(
                        new BigDecimal(
                                "1000.00"
                        ),
                        List.of(
                                periodEndTrade
                        )
                );

        assertTrue(
                results.includesTrade(
                        "period-end-trade"
                )
        );

        assertEquals(
                2,
                results.portfolioEquityCurve()
                        .size()
        );

        assertDecimalEquals(
                new BigDecimal(
                        "1000.00"
                ),
                results.portfolioEquityCurve()
                        .getFirst()
        );

        assertDecimalEquals(
                new BigDecimal(
                        "900.00"
                ),
                results.portfolioEquityCurve()
                        .getLast()
        );

        assertDecimalEquals(
                new BigDecimal(
                        "0.10000000"
                ),
                results.maximumDrawdown()
        );

        assertDecimalEquals(
                new BigDecimal(
                        "-0.50000000"
                ),
                results.primaryExpectancy()
        );

        assertEquals(
                1,
                results.periodEndTradeCount()
        );
    }

    @Test
    void expectancyIsReportedIncludingAndExcludingPeriodEndTrades() {

        GovernedPortfolioResultService.CompletedGovernedTrade
                ordinaryTrade =
                new GovernedPortfolioResultService
                        .CompletedGovernedTrade(
                        "ordinary-trade",
                        ExitReason.SELL,
                        new BigDecimal(
                                "100.00"
                        ),
                        new BigDecimal(
                                "1.00"
                        )
                );

        GovernedPortfolioResultService.CompletedGovernedTrade
                periodEndTrade =
                new GovernedPortfolioResultService
                        .CompletedGovernedTrade(
                        "period-end-trade",
                        ExitReason.PERIOD_END,
                        new BigDecimal(
                                "-50.00"
                        ),
                        new BigDecimal(
                                "-0.50"
                        )
                );

        GovernedPortfolioResultService.GovernedPortfolioResults
                results =
                service.calculateResults(
                        new BigDecimal(
                                "1000.00"
                        ),
                        List.of(
                                ordinaryTrade,
                                periodEndTrade
                        )
                );

        /*
         * Including PERIOD_END:
         *
         * (1.00R + -0.50R) / 2
         * = 0.25R
         */
        assertDecimalEquals(
                new BigDecimal(
                        "0.25000000"
                ),
                results.expectancyIncludingPeriodEnd()
        );

        /*
         * Excluding PERIOD_END:
         *
         * ordinary trade only
         * = 1.00R
         */
        assertDecimalEquals(
                new BigDecimal(
                        "1.00000000"
                ),
                results.expectancyExcludingPeriodEnd()
        );

        assertDecimalEquals(
                results.expectancyIncludingPeriodEnd(),
                results.primaryExpectancy()
        );

        assertEquals(
                1,
                results.periodEndTradeCount()
        );
    }

    @Test
    void largePeriodEndExpectancyDifferenceIsPreserved() {

        GovernedPortfolioResultService.PeriodBoundaryMetrics
                metrics =
                service.recordPeriodBoundaryMetrics(
                        new BigDecimal(
                                "0.40"
                        ),
                        new BigDecimal(
                                "0.80"
                        )
                );

        assertDecimalEquals(
                new BigDecimal(
                        "0.40"
                ),
                metrics.expectancyIncludingPeriodEnd()
        );

        assertDecimalEquals(
                new BigDecimal(
                        "0.80"
                ),
                metrics.expectancyExcludingPeriodEnd()
        );

        assertDecimalEquals(
                new BigDecimal(
                        "0.40"
                ),
                metrics.absoluteDifference()
        );

        assertFalse(
                metrics.averagedTogether()
        );

        /*
         * 0.60R would be the average of 0.40R and 0.80R.
         * That value must not replace either diagnostic.
         */
        assertNotEquals(
                0,
                new BigDecimal(
                        "0.60"
                ).compareTo(
                        metrics.expectancyIncludingPeriodEnd()
                )
        );

        assertNotEquals(
                0,
                new BigDecimal(
                        "0.60"
                ).compareTo(
                        metrics.expectancyExcludingPeriodEnd()
                )
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