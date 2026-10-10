package com.marshallai.portfolio.governance;

import com.marshallai.trading.execution.ExitReason;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Produces governed portfolio results from completed trades.
 *
 * Period-boundary rules:
 *
 * 1. PERIOD_END trades remain part of governed portfolio results.
 * 2. PERIOD_END profit/loss contributes to the equity curve.
 * 3. PERIOD_END profit/loss contributes to maximum drawdown.
 * 4. Primary expectancy includes PERIOD_END trades.
 * 5. A diagnostic expectancy excluding PERIOD_END trades is also
 *    reported separately.
 * 6. The number of PERIOD_END trades is retained explicitly.
 * 7. Including/excluding expectancy values are never averaged
 *    together or otherwise hidden.
 */
@Service
public class GovernedPortfolioResultService {

    private static final int METRIC_SCALE = 8;

    private final PortfolioDrawdownService
            drawdownService;

    public GovernedPortfolioResultService(
            PortfolioDrawdownService drawdownService) {

        if (drawdownService == null) {
            throw new IllegalArgumentException(
                    "Portfolio drawdown service cannot be null"
            );
        }

        this.drawdownService =
                drawdownService;
    }

    public GovernedPortfolioResults calculateResults(
            BigDecimal startingEquity,
            List<CompletedGovernedTrade> completedTrades) {

        requirePositive(
                startingEquity,
                "Starting equity"
        );

        requireTrades(
                completedTrades
        );

        List<BigDecimal> equityCurve =
                new ArrayList<>();

        List<String> includedTradeIds =
                new ArrayList<>();

        equityCurve.add(
                startingEquity
        );

        BigDecimal runningEquity =
                startingEquity;

        BigDecimal totalR =
                BigDecimal.ZERO;

        BigDecimal nonPeriodEndR =
                BigDecimal.ZERO;

        int nonPeriodEndTradeCount =
                0;

        int periodEndTradeCount =
                0;

        for (CompletedGovernedTrade trade :
                completedTrades) {

            runningEquity =
                    runningEquity.add(
                            trade.profitLoss()
                    );

            if (runningEquity.signum() < 0) {
                throw new IllegalArgumentException(
                        "Portfolio equity cannot become negative"
                );
            }

            equityCurve.add(
                    runningEquity
            );

            includedTradeIds.add(
                    trade.tradeId()
            );

            totalR =
                    totalR.add(
                            trade.rMultiple()
                    );

            if (trade.exitReason()
                    == ExitReason.PERIOD_END) {

                periodEndTradeCount++;

            } else {

                nonPeriodEndR =
                        nonPeriodEndR.add(
                                trade.rMultiple()
                        );

                nonPeriodEndTradeCount++;
            }
        }

        BigDecimal expectancyIncludingPeriodEnd =
                average(
                        totalR,
                        completedTrades.size()
                );

        BigDecimal expectancyExcludingPeriodEnd =
                nonPeriodEndTradeCount == 0
                        ? BigDecimal.ZERO.setScale(
                        METRIC_SCALE
                )
                        : average(
                        nonPeriodEndR,
                        nonPeriodEndTradeCount
                );

        BigDecimal maximumDrawdown =
                drawdownService
                        .calculateMaximumDrawdown(
                                equityCurve
                        );

        return new GovernedPortfolioResults(
                List.copyOf(
                        completedTrades
                ),
                List.copyOf(
                        includedTradeIds
                ),
                List.copyOf(
                        equityCurve
                ),
                maximumDrawdown,
                expectancyIncludingPeriodEnd,
                expectancyExcludingPeriodEnd,
                expectancyIncludingPeriodEnd,
                periodEndTradeCount
        );
    }

    /**
     * Records the two expectancy views without combining them.
     *
     * The difference remains visible as a diagnostic because a
     * large difference can show that period-boundary liquidation
     * materially affects strategy outcomes.
     */
    public PeriodBoundaryMetrics recordPeriodBoundaryMetrics(
            BigDecimal expectancyIncludingPeriodEnd,
            BigDecimal expectancyExcludingPeriodEnd) {

        requireMetric(
                expectancyIncludingPeriodEnd,
                "Expectancy including PERIOD_END"
        );

        requireMetric(
                expectancyExcludingPeriodEnd,
                "Expectancy excluding PERIOD_END"
        );

        BigDecimal absoluteDifference =
                expectancyExcludingPeriodEnd
                        .subtract(
                                expectancyIncludingPeriodEnd
                        )
                        .abs();

        return new PeriodBoundaryMetrics(
                expectancyIncludingPeriodEnd,
                expectancyExcludingPeriodEnd,
                absoluteDifference,
                false
        );
    }

    private static BigDecimal average(
            BigDecimal total,
            int count) {

        if (count <= 0) {
            throw new IllegalArgumentException(
                    "Average count must be greater than zero"
            );
        }

        return total.divide(
                BigDecimal.valueOf(
                        count
                ),
                METRIC_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private static void requireTrades(
            List<CompletedGovernedTrade> trades) {

        if (trades == null
                || trades.isEmpty()) {

            throw new IllegalArgumentException(
                    "Completed governed trades cannot be empty"
            );
        }

        if (trades.stream()
                .anyMatch(
                        trade -> trade == null
                )) {

            throw new IllegalArgumentException(
                    "Completed governed trade cannot be null"
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

    private static void requireMetric(
            BigDecimal value,
            String fieldName) {

        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName
                            + " cannot be null"
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

    public record CompletedGovernedTrade(
            String tradeId,
            ExitReason exitReason,
            BigDecimal profitLoss,
            BigDecimal rMultiple) {

        public CompletedGovernedTrade {

            requireText(
                    tradeId,
                    "Trade ID"
            );

            if (exitReason == null) {
                throw new IllegalArgumentException(
                        "Exit reason cannot be null"
                );
            }

            requireMetric(
                    profitLoss,
                    "Profit or loss"
            );

            requireMetric(
                    rMultiple,
                    "R multiple"
            );
        }
    }

    public record GovernedPortfolioResults(
            List<CompletedGovernedTrade> completedTrades,
            List<String> includedTradeIds,
            List<BigDecimal> portfolioEquityCurve,
            BigDecimal maximumDrawdown,
            BigDecimal expectancyIncludingPeriodEnd,
            BigDecimal expectancyExcludingPeriodEnd,
            BigDecimal primaryExpectancy,
            int periodEndTradeCount) {

        public GovernedPortfolioResults {

            if (completedTrades == null
                    || completedTrades.isEmpty()) {

                throw new IllegalArgumentException(
                        "Completed trades cannot be empty"
                );
            }

            if (includedTradeIds == null
                    || includedTradeIds.size()
                    != completedTrades.size()) {

                throw new IllegalArgumentException(
                        "Every completed trade must be included"
                );
            }

            if (portfolioEquityCurve == null
                    || portfolioEquityCurve.size()
                    != completedTrades.size() + 1) {

                throw new IllegalArgumentException(
                        "Equity curve must contain the starting "
                                + "equity plus one point per trade"
                );
            }

            requireMetric(
                    maximumDrawdown,
                    "Maximum drawdown"
            );

            requireMetric(
                    expectancyIncludingPeriodEnd,
                    "Expectancy including PERIOD_END"
            );

            requireMetric(
                    expectancyExcludingPeriodEnd,
                    "Expectancy excluding PERIOD_END"
            );

            requireMetric(
                    primaryExpectancy,
                    "Primary expectancy"
            );

            if (primaryExpectancy.compareTo(
                    expectancyIncludingPeriodEnd
            ) != 0) {

                throw new IllegalArgumentException(
                        "Primary expectancy must include "
                                + "PERIOD_END trades"
                );
            }

            if (periodEndTradeCount < 0) {
                throw new IllegalArgumentException(
                        "PERIOD_END trade count cannot be negative"
                );
            }
        }

        public boolean includesTrade(
                String tradeId) {

            return includedTradeIds.contains(
                    tradeId
            );
        }
    }

    public record PeriodBoundaryMetrics(
            BigDecimal expectancyIncludingPeriodEnd,
            BigDecimal expectancyExcludingPeriodEnd,
            BigDecimal absoluteDifference,
            boolean averagedTogether) {

        public PeriodBoundaryMetrics {

            requireMetric(
                    expectancyIncludingPeriodEnd,
                    "Expectancy including PERIOD_END"
            );

            requireMetric(
                    expectancyExcludingPeriodEnd,
                    "Expectancy excluding PERIOD_END"
            );

            requireMetric(
                    absoluteDifference,
                    "Expectancy difference"
            );

            if (absoluteDifference.signum() < 0) {
                throw new IllegalArgumentException(
                        "Expectancy difference cannot be negative"
                );
            }

            if (averagedTogether) {
                throw new IllegalArgumentException(
                        "Period-boundary expectancy diagnostics "
                                + "must never be averaged together"
                );
            }
        }
    }
}