package com.marshallai.trading.execution;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class TradeEntryService {

    private final TradeDefinition tradeDefinition;

    public TradeEntryService() {
        this.tradeDefinition = TradeDefinition.v1();
    }

    public TradeEntryService(TradeDefinition tradeDefinition) {
        if (tradeDefinition == null) {
            throw new IllegalArgumentException(
                    "Trade definition cannot be null"
            );
        }

        this.tradeDefinition = tradeDefinition;
    }

    public BigDecimal calculateFrozenStopDistance(BigDecimal atr) {
        requirePositive(atr, "ATR");

        return atr.multiply(
                tradeDefinition.initialStopAtrMultiplier()
        );
    }

    public BigDecimal calculateEntryLimitPrice(
            BigDecimal signalClose,
            BigDecimal atr) {

        requirePositive(signalClose, "Signal close");
        requirePositive(atr, "ATR");

        return signalClose.add(
                atr.multiply(
                        tradeDefinition.entryLimitAtrMultiplier()
                )
        );
    }

    public BigDecimal calculateRiskAmount(
            BigDecimal portfolioEquity) {

        requirePositive(portfolioEquity, "Portfolio equity");

        return portfolioEquity.multiply(
                tradeDefinition.riskPercent()
        );
    }

    public int calculateRiskBasedQuantity(
            BigDecimal portfolioEquity,
            BigDecimal frozenStopDistance) {

        requirePositive(portfolioEquity, "Portfolio equity");
        requirePositive(
                frozenStopDistance,
                "Frozen stop distance"
        );

        BigDecimal riskAmount =
                calculateRiskAmount(portfolioEquity);

        return riskAmount
                .divide(
                        frozenStopDistance,
                        0,
                        RoundingMode.FLOOR
                )
                .intValue();
    }

    public int calculateExposureBasedQuantity(
            BigDecimal portfolioEquity,
            BigDecimal entryLimitPrice) {

        requirePositive(portfolioEquity, "Portfolio equity");
        requirePositive(
                entryLimitPrice,
                "Entry limit price"
        );

        BigDecimal maximumPositionValue =
                portfolioEquity.multiply(
                        tradeDefinition
                                .maximumPositionExposurePercent()
                );

        return maximumPositionValue
                .divide(
                        entryLimitPrice,
                        0,
                        RoundingMode.FLOOR
                )
                .intValue();
    }

    public int calculateFinalQuantity(
            BigDecimal portfolioEquity,
            BigDecimal frozenStopDistance,
            BigDecimal entryLimitPrice) {

        int riskBasedQuantity =
                calculateRiskBasedQuantity(
                        portfolioEquity,
                        frozenStopDistance
                );

        int exposureBasedQuantity =
                calculateExposureBasedQuantity(
                        portfolioEquity,
                        entryLimitPrice
                );

        return Math.min(
                riskBasedQuantity,
                exposureBasedQuantity
        );
    }

    public TradeEntryResult evaluateOpeningEntry(
            BigDecimal portfolioEquity,
            BigDecimal signalClose,
            BigDecimal atr,
            BigDecimal openingAuctionPrice) {

        requirePositive(portfolioEquity, "Portfolio equity");
        requirePositive(signalClose, "Signal close");
        requirePositive(atr, "ATR");
        requirePositive(
                openingAuctionPrice,
                "Opening auction price"
        );

        BigDecimal frozenStopDistance =
                calculateFrozenStopDistance(atr);

        BigDecimal entryLimitPrice =
                calculateEntryLimitPrice(
                        signalClose,
                        atr
                );

        BigDecimal riskAmount =
                calculateRiskAmount(portfolioEquity);

        int riskBasedQuantity =
                calculateRiskBasedQuantity(
                        portfolioEquity,
                        frozenStopDistance
                );

        int exposureBasedQuantity =
                calculateExposureBasedQuantity(
                        portfolioEquity,
                        entryLimitPrice
                );

        int quantity = Math.min(
                riskBasedQuantity,
                exposureBasedQuantity
        );

        BigDecimal openingGapAtr =
                openingAuctionPrice
                        .subtract(signalClose)
                        .divide(
                                atr,
                                8,
                                RoundingMode.HALF_UP
                        );

        /*
         * Quantity zero means the order must never be submitted.
         */
        if (quantity <= 0) {
            return TradeEntryResult.skipped(
                    0,
                    riskBasedQuantity,
                    exposureBasedQuantity,
                    riskAmount,
                    frozenStopDistance,
                    entryLimitPrice,
                    openingGapAtr
            );
        }

        /*
         * Trade Definition v1 uses an opening-only limit BUY.
         *
         * There is intentionally no gap-down filter.
         * A gap-down opening remains eligible.
         *
         * An opening above the limit does not fill.
         */
        if (openingAuctionPrice.compareTo(entryLimitPrice) > 0) {
            return TradeEntryResult.skipped(
                    quantity,
                    riskBasedQuantity,
                    exposureBasedQuantity,
                    riskAmount,
                    frozenStopDistance,
                    entryLimitPrice,
                    openingGapAtr
            );
        }

        BigDecimal fillPrice = openingAuctionPrice;

        BigDecimal initialStopPrice =
                fillPrice.subtract(
                        frozenStopDistance
                );

        BigDecimal initialRDenominator =
                frozenStopDistance.multiply(
                        BigDecimal.valueOf(quantity)
                );

        return TradeEntryResult.filled(
                quantity,
                riskBasedQuantity,
                exposureBasedQuantity,
                riskAmount,
                frozenStopDistance,
                entryLimitPrice,
                fillPrice,
                initialStopPrice,
                initialRDenominator,
                openingGapAtr
        );
    }

    public TradeDefinition getTradeDefinition() {
        return tradeDefinition;
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
}