package com.marshallai.portfolio.governance;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Service
public class PortfolioBenchmarkService {

    private final PortfolioGovernanceDefinition definition;
    private final PortfolioExecutionCostService executionCostService;

    public PortfolioBenchmarkService() {
        this.definition =
                PortfolioGovernanceDefinition.v1();

        this.executionCostService =
                new PortfolioExecutionCostService(
                        this.definition
                );
    }

    public PortfolioBenchmarkService(
            PortfolioGovernanceDefinition definition) {

        if (definition == null) {
            throw new IllegalArgumentException(
                    "Portfolio governance definition cannot be null"
            );
        }

        this.definition =
                definition;

        this.executionCostService =
                new PortfolioExecutionCostService(
                        definition
                );
    }

    /**
     * A governed buy-and-hold benchmark must use the same:
     *
     * - symbol universe
     * - evaluation period
     * - starting capital
     *
     * as the governed strategy run.
     */
    public BenchmarkMetadata createBenchmarkMetadata(
            boolean sameUniverse,
            boolean sameEvaluationPeriod,
            BigDecimal startingCapital,
            boolean priceOnlyMarketData) {

        requirePositive(
                startingCapital,
                "Benchmark starting capital"
        );

        Set<String> limitations =
                new HashSet<>();

        /*
         * Price-only bars exclude dividends.
         *
         * This limitation must be recorded explicitly because
         * buy-and-hold normally benefits from dividend income.
         */
        if (priceOnlyMarketData) {
            limitations.add(
                    definition.benchmarkDividendLimitation()
            );
        }

        return new BenchmarkMetadata(
                sameUniverse,
                sameEvaluationPeriod,
                startingCapital,
                Set.copyOf(limitations)
        );
    }

    /**
     * Creates the frozen Portfolio Governance v1 benchmark.
     *
     * Rules:
     *
     * 1. Equal-weight the governed universe at initial purchase.
     * 2. Initial BUYs use the same auction-slippage assumption
     *    as the strategy.
     * 3. No rebalancing occurs after the initial purchase.
     */
    public BuyAndHoldBenchmarkPlan
    createEqualWeightBuyAndHoldBenchmark() {

        return new BuyAndHoldBenchmarkPlan(
                true,
                definition.auctionSlippageBasisPoints(),
                false
        );
    }

    /**
     * Applies the exact same BUY-auction slippage model used
     * by the governed strategy to the benchmark's first-day
     * purchases.
     */
    public BigDecimal applyInitialPurchaseSlippage(
            BigDecimal auctionReferencePrice) {

        return executionCostService
                .applyBuyAuctionSlippage(
                        auctionReferencePrice
                );
    }

    public boolean usesEqualWeightInitialAllocation() {
        return true;
    }

    public int initialBuyAuctionSlippageBasisPoints() {
        return definition.auctionSlippageBasisPoints();
    }

    public boolean rebalancesAfterInitialPurchase() {
        return false;
    }

    public boolean hasLimitation(
            BenchmarkMetadata metadata,
            String limitation) {

        if (metadata == null) {
            throw new IllegalArgumentException(
                    "Benchmark metadata cannot be null"
            );
        }

        if (limitation == null ||
                limitation.isBlank()) {
            throw new IllegalArgumentException(
                    "Benchmark limitation cannot be blank"
            );
        }

        return metadata.limitations()
                .contains(
                        limitation
                );
    }

    public PortfolioGovernanceDefinition getDefinition() {
        return definition;
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

    public record BenchmarkMetadata(
            boolean sameUniverse,
            boolean sameEvaluationPeriod,
            BigDecimal startingCapital,
            Set<String> limitations
    ) {

        public BenchmarkMetadata {
            if (startingCapital == null ||
                    startingCapital.signum() <= 0) {
                throw new IllegalArgumentException(
                        "Benchmark starting capital must be greater than zero"
                );
            }

            limitations =
                    limitations == null
                            ? Set.of()
                            : Set.copyOf(
                            limitations
                    );
        }
    }

    public record BuyAndHoldBenchmarkPlan(
            boolean equalWeightInitialAllocation,
            int initialBuyAuctionSlippageBasisPoints,
            boolean rebalanceAfterInitialPurchase
    ) {

        public BuyAndHoldBenchmarkPlan {

            if (!equalWeightInitialAllocation) {
                throw new IllegalArgumentException(
                        "Portfolio Governance v1 benchmark must use equal-weight initial allocation"
                );
            }

            if (initialBuyAuctionSlippageBasisPoints < 0) {
                throw new IllegalArgumentException(
                        "Benchmark initial BUY slippage cannot be negative"
                );
            }

            if (rebalanceAfterInitialPurchase) {
                throw new IllegalArgumentException(
                        "Portfolio Governance v1 benchmark must not rebalance after initial purchase"
                );
            }
        }
    }
}