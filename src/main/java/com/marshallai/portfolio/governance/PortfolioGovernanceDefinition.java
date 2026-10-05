package com.marshallai.portfolio.governance;

import java.math.BigDecimal;

/**
 * Immutable governed configuration for MarshallAI Portfolio Governance v1.
 *
 * These values must remain fixed for a governed strategy/configuration
 * version so that backtesting, paper trading, and later live execution
 * use the same portfolio-level rules.
 */
public record PortfolioGovernanceDefinition(
        String version,
        BigDecimal startingCapital,
        boolean leverageEnabled,
        BigDecimal maximumTotalExposurePercent,
        BigDecimal maximumPositionExposurePercent,
        int auctionSlippageBasisPoints,
        int stopSlippageBasisPoints,
        BigDecimal brokerStockCommission,
        String regulatoryFeeModelVersion
) {

    public PortfolioGovernanceDefinition {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException(
                    "Portfolio governance version cannot be blank"
            );
        }

        requirePositive(
                startingCapital,
                "Starting capital"
        );

        requirePercent(
                maximumTotalExposurePercent,
                "Maximum total exposure percent"
        );

        requirePercent(
                maximumPositionExposurePercent,
                "Maximum position exposure percent"
        );

        if (maximumPositionExposurePercent.compareTo(
                maximumTotalExposurePercent
        ) > 0) {
            throw new IllegalArgumentException(
                    "Maximum position exposure cannot exceed maximum total exposure"
            );
        }

        if (auctionSlippageBasisPoints < 0) {
            throw new IllegalArgumentException(
                    "Auction slippage basis points cannot be negative"
            );
        }

        if (stopSlippageBasisPoints < 0) {
            throw new IllegalArgumentException(
                    "Stop slippage basis points cannot be negative"
            );
        }

        if (brokerStockCommission == null ||
                brokerStockCommission.signum() < 0) {
            throw new IllegalArgumentException(
                    "Broker stock commission cannot be negative"
            );
        }

        if (regulatoryFeeModelVersion == null ||
                regulatoryFeeModelVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "Regulatory fee model version cannot be blank"
            );
        }
    }

    public static PortfolioGovernanceDefinition v1() {
        return new PortfolioGovernanceDefinition(
                "portfolio-governance-v1",
                new BigDecimal("100000.00"),
                false,
                new BigDecimal("1.00"),
                new BigDecimal("0.05"),
                2,
                10,
                new BigDecimal("0.00"),
                "regulatory-fees-v1"
        );
    }

    public String scheduledExitOrderType() {
        return "MARKET";
    }

    public String scheduledExitTimeInForce() {
        return "OPG";
    }

    public String capitalConstraintOutcome() {
        return "SKIPPED_CAPITAL_CONSTRAINT";
    }

    public String benchmarkDividendLimitation() {
        return "BENCHMARK_EXCLUDES_DIVIDENDS";
    }

    public boolean useCombinedPortfolioDrawdown() {
        return true;
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

    private static void requirePercent(
            BigDecimal value,
            String fieldName) {

        if (value == null ||
                value.signum() <= 0 ||
                value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be greater than zero and no more than 100 percent"
            );
        }
    }
}