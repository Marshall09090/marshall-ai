package com.marshallai.portfolio.governance;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PortfolioDrawdownService {

    private final PortfolioGovernanceDefinition definition;

    public PortfolioDrawdownService() {
        this.definition = PortfolioGovernanceDefinition.v1();
    }

    public PortfolioDrawdownService(
            PortfolioGovernanceDefinition definition) {

        if (definition == null) {
            throw new IllegalArgumentException(
                    "Portfolio governance definition cannot be null"
            );
        }

        this.definition = definition;
    }

    /**
     * Portfolio Governance v1 measures drawdown from one combined
     * chronological portfolio-equity curve across all symbols,
     * positions, pending capital and cash.
     *
     * It must never calculate the governed maximum drawdown
     * independently per symbol.
     */
    public BigDecimal calculateMaximumDrawdown(
            List<BigDecimal> portfolioEquityCurve) {

        if (portfolioEquityCurve == null ||
                portfolioEquityCurve.isEmpty()) {
            throw new IllegalArgumentException(
                    "Portfolio equity curve cannot be empty"
            );
        }

        BigDecimal firstValue =
                portfolioEquityCurve.getFirst();

        requirePositive(
                firstValue,
                "Initial portfolio equity"
        );

        BigDecimal peak =
                firstValue;

        BigDecimal maximumDrawdown =
                BigDecimal.ZERO;

        for (BigDecimal equity : portfolioEquityCurve) {
            requireNonNegative(
                    equity,
                    "Portfolio equity"
            );

            if (equity.compareTo(peak) > 0) {
                peak = equity;
            }

            BigDecimal drawdown =
                    peak.subtract(equity)
                            .divide(
                                    peak,
                                    8,
                                    RoundingMode.HALF_UP
                            );

            if (drawdown.compareTo(
                    maximumDrawdown
            ) > 0) {
                maximumDrawdown =
                        drawdown;
            }
        }

        return maximumDrawdown;
    }

    public boolean usesCombinedPortfolioEquityCurve() {
        return definition.useCombinedPortfolioDrawdown();
    }

    public boolean usesPerSymbolDrawdown() {
        return false;
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

    private static void requireNonNegative(
            BigDecimal value,
            String fieldName) {

        if (value == null ||
                value.signum() < 0) {
            throw new IllegalArgumentException(
                    fieldName +
                            " cannot be negative"
            );
        }
    }
}