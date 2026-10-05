package com.marshallai.portfolio.governance;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PortfolioExecutionCostService {

    private static final BigDecimal BASIS_POINT_DIVISOR =
            new BigDecimal("10000");

    private final PortfolioGovernanceDefinition definition;

    public PortfolioExecutionCostService() {
        this.definition = PortfolioGovernanceDefinition.v1();
    }

    public PortfolioExecutionCostService(
            PortfolioGovernanceDefinition definition) {

        if (definition == null) {
            throw new IllegalArgumentException(
                    "Portfolio governance definition cannot be null"
            );
        }

        this.definition = definition;
    }

    /**
     * Applies the governed BUY auction-slippage assumption.
     *
     * This method is appropriate for an unconstrained modeled
     * auction purchase such as the benchmark's initial purchase.
     */
    public BigDecimal applyBuyAuctionSlippage(
            BigDecimal referencePrice) {

        requirePositive(
                referencePrice,
                "Auction reference price"
        );

        BigDecimal slippageRate =
                basisPointsToRate(
                        definition.auctionSlippageBasisPoints()
                );

        return referencePrice.multiply(
                BigDecimal.ONE.add(slippageRate)
        );
    }

    /**
     * Evaluates a BUY limit-on-open fill.
     *
     * Rules:
     *
     * 1. If the auction price is above the limit, the order does not fill.
     * 2. If it is eligible to fill, adverse BUY slippage is modeled.
     * 3. The modeled fill can never exceed the BUY limit price.
     *
     * A real BUY limit order cannot execute above its limit.
     */
    public LimitAuctionFillResult evaluateBuyLimitAuctionFill(
            BigDecimal auctionPrice,
            BigDecimal limitPrice) {

        requirePositive(
                auctionPrice,
                "Auction price"
        );

        requirePositive(
                limitPrice,
                "Limit price"
        );

        if (auctionPrice.compareTo(limitPrice) > 0) {
            return LimitAuctionFillResult.notFilled(
                    auctionPrice,
                    limitPrice
            );
        }

        BigDecimal rawSlippageAdjustedPrice =
                applyBuyAuctionSlippage(
                        auctionPrice
                );

        BigDecimal modeledFillPrice =
                rawSlippageAdjustedPrice.min(
                        limitPrice
                );

        return LimitAuctionFillResult.filled(
                auctionPrice,
                limitPrice,
                rawSlippageAdjustedPrice,
                modeledFillPrice
        );
    }

    /**
     * SELL auction fills receive adverse auction slippage.
     */
    public BigDecimal applySellAuctionSlippage(
            BigDecimal referencePrice) {

        requirePositive(
                referencePrice,
                "Auction reference price"
        );

        BigDecimal slippageRate =
                basisPointsToRate(
                        definition.auctionSlippageBasisPoints()
                );

        return referencePrice.multiply(
                BigDecimal.ONE.subtract(slippageRate)
        );
    }

    /**
     * Applies adverse stop slippage.
     *
     * For a normal stop touch, the stop execution reference
     * price is the active stop.
     *
     * For a gap through the stop, the execution reference
     * price is the actual opening price. The same 10 bps
     * adverse slippage assumption is then applied on top
     * of that opening price.
     */
    public BigDecimal applySellStopSlippage(
            BigDecimal referencePrice) {

        requirePositive(
                referencePrice,
                "Stop execution reference price"
        );

        BigDecimal slippageRate =
                basisPointsToRate(
                        definition.stopSlippageBasisPoints()
                );

        return referencePrice.multiply(
                BigDecimal.ONE.subtract(slippageRate)
        );
    }

    /**
     * Explicit helper for a gap-through stop.
     *
     * Example:
     * active stop = 90.00
     * open        = 85.00
     *
     * reference   = 85.00
     * modeled fill with 10 bps adverse slippage = 84.915
     */
    public StopGapFillResult evaluateGapThroughStop(
            BigDecimal activeStopPrice,
            BigDecimal openingPrice) {

        requirePositive(
                activeStopPrice,
                "Active stop price"
        );

        requirePositive(
                openingPrice,
                "Opening price"
        );

        if (openingPrice.compareTo(activeStopPrice) > 0) {
            throw new IllegalArgumentException(
                    "Opening price must be at or below the active stop for a gap-through stop"
            );
        }

        BigDecimal modeledFillPrice =
                applySellStopSlippage(
                        openingPrice
                );

        return new StopGapFillResult(
                activeStopPrice,
                openingPrice,
                openingPrice,
                modeledFillPrice
        );
    }

    public TransactionCostResult calculateTransactionCosts(
            BigDecimal grossProceeds) {

        requireNonNegative(
                grossProceeds,
                "Gross proceeds"
        );

        /*
         * Portfolio Governance v1 freezes broker stock
         * commission at zero.
         *
         * Regulatory fees remain separate because their
         * schedules can change independently from strategy
         * behavior and must remain versioned.
         */
        return new TransactionCostResult(
                definition.brokerStockCommission(),
                true,
                definition.regulatoryFeeModelVersion()
        );
    }

    public String scheduledExitOrderType() {
        return definition.scheduledExitOrderType();
    }

    public String scheduledExitTimeInForce() {
        return definition.scheduledExitTimeInForce();
    }

    public PortfolioGovernanceDefinition getDefinition() {
        return definition;
    }

    private BigDecimal basisPointsToRate(
            int basisPoints) {

        if (basisPoints < 0) {
            throw new IllegalArgumentException(
                    "Basis points cannot be negative"
            );
        }

        return BigDecimal.valueOf(basisPoints)
                .divide(
                        BASIS_POINT_DIVISOR,
                        8,
                        RoundingMode.HALF_UP
                );
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

    public record LimitAuctionFillResult(
            boolean filled,
            BigDecimal auctionPrice,
            BigDecimal limitPrice,
            BigDecimal rawSlippageAdjustedPrice,
            BigDecimal modeledFillPrice
    ) {

        public LimitAuctionFillResult {
            if (auctionPrice == null ||
                    auctionPrice.signum() <= 0) {
                throw new IllegalArgumentException(
                        "Auction price must be greater than zero"
                );
            }

            if (limitPrice == null ||
                    limitPrice.signum() <= 0) {
                throw new IllegalArgumentException(
                        "Limit price must be greater than zero"
                );
            }

            if (filled) {
                if (rawSlippageAdjustedPrice == null ||
                        modeledFillPrice == null) {
                    throw new IllegalArgumentException(
                            "Filled limit auction order requires modeled prices"
                    );
                }

                if (modeledFillPrice.compareTo(limitPrice) > 0) {
                    throw new IllegalArgumentException(
                            "BUY limit fill cannot exceed the limit price"
                    );
                }
            }
        }

        public static LimitAuctionFillResult filled(
                BigDecimal auctionPrice,
                BigDecimal limitPrice,
                BigDecimal rawSlippageAdjustedPrice,
                BigDecimal modeledFillPrice) {

            return new LimitAuctionFillResult(
                    true,
                    auctionPrice,
                    limitPrice,
                    rawSlippageAdjustedPrice,
                    modeledFillPrice
            );
        }

        public static LimitAuctionFillResult notFilled(
                BigDecimal auctionPrice,
                BigDecimal limitPrice) {

            return new LimitAuctionFillResult(
                    false,
                    auctionPrice,
                    limitPrice,
                    null,
                    null
            );
        }
    }

    public record StopGapFillResult(
            BigDecimal activeStopPrice,
            BigDecimal openingPrice,
            BigDecimal executionReferencePrice,
            BigDecimal modeledFillPrice
    ) {

        public StopGapFillResult {
            requirePositive(
                    activeStopPrice,
                    "Active stop price"
            );

            requirePositive(
                    openingPrice,
                    "Opening price"
            );

            requirePositive(
                    executionReferencePrice,
                    "Execution reference price"
            );

            requirePositive(
                    modeledFillPrice,
                    "Modeled fill price"
            );
        }
    }

    public record TransactionCostResult(
            BigDecimal brokerCommission,
            boolean regulatoryFeesSeparate,
            String regulatoryFeeModelVersion
    ) {

        public TransactionCostResult {
            if (brokerCommission == null ||
                    brokerCommission.signum() < 0) {
                throw new IllegalArgumentException(
                        "Broker commission cannot be negative"
                );
            }

            if (regulatoryFeeModelVersion == null ||
                    regulatoryFeeModelVersion.isBlank()) {
                throw new IllegalArgumentException(
                        "Regulatory fee model version cannot be blank"
                );
            }
        }
    }
}