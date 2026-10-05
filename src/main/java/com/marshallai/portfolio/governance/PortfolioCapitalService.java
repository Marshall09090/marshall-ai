package com.marshallai.portfolio.governance;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PortfolioCapitalService {

    private final PortfolioGovernanceDefinition definition;

    public PortfolioCapitalService() {
        this.definition = PortfolioGovernanceDefinition.v1();
    }

    public PortfolioCapitalService(
            PortfolioGovernanceDefinition definition) {

        if (definition == null) {
            throw new IllegalArgumentException(
                    "Portfolio governance definition cannot be null"
            );
        }

        this.definition = definition;
    }

    public BigDecimal calculateReservedCash(
            int quantity,
            BigDecimal limitPrice) {

        if (quantity < 0) {
            throw new IllegalArgumentException(
                    "Quantity cannot be negative"
            );
        }

        requirePositive(
                limitPrice,
                "Limit price"
        );

        /*
         * BUY capital is reserved at the actual limit price.
         *
         * Slippage may affect the modeled fill, but a real
         * BUY limit order cannot execute above its limit.
         */
        return limitPrice.multiply(
                BigDecimal.valueOf(quantity)
        );
    }

    public BigDecimal calculateAvailableUnreservedCash(
            BigDecimal availableCash,
            BigDecimal reservedCash) {

        requireNonNegative(
                availableCash,
                "Available cash"
        );

        requireNonNegative(
                reservedCash,
                "Reserved cash"
        );

        BigDecimal result =
                availableCash.subtract(
                        reservedCash
                );

        if (result.signum() < 0) {
            throw new IllegalStateException(
                    "Reserved cash cannot exceed available cash"
            );
        }

        return result;
    }

    public BigDecimal calculateMaximumTotalExposure(
            BigDecimal portfolioEquity) {

        requirePositive(
                portfolioEquity,
                "Portfolio equity"
        );

        return portfolioEquity.multiply(
                definition.maximumTotalExposurePercent()
        );
    }

    public BigDecimal calculateMaximumPositionExposure(
            BigDecimal portfolioEquity) {

        requirePositive(
                portfolioEquity,
                "Portfolio equity"
        );

        return portfolioEquity.multiply(
                definition.maximumPositionExposurePercent()
        );
    }

    public BigDecimal calculateTotalCommittedExposure(
            BigDecimal existingPositionExposure,
            BigDecimal pendingReservedExposure) {

        requireNonNegative(
                existingPositionExposure,
                "Existing position exposure"
        );

        requireNonNegative(
                pendingReservedExposure,
                "Pending reserved exposure"
        );

        return existingPositionExposure.add(
                pendingReservedExposure
        );
    }

    public CapitalDecision evaluateAdditionalBuy(
            BigDecimal portfolioEquity,
            BigDecimal existingPositionExposure,
            BigDecimal pendingReservedExposure,
            BigDecimal proposedExposure) {

        requirePositive(
                portfolioEquity,
                "Portfolio equity"
        );

        requireNonNegative(
                existingPositionExposure,
                "Existing position exposure"
        );

        requireNonNegative(
                pendingReservedExposure,
                "Pending reserved exposure"
        );

        requireNonNegative(
                proposedExposure,
                "Proposed exposure"
        );

        BigDecimal currentCommitted =
                calculateTotalCommittedExposure(
                        existingPositionExposure,
                        pendingReservedExposure
                );

        BigDecimal maximumAllowed =
                calculateMaximumTotalExposure(
                        portfolioEquity
                );

        BigDecimal proposedCommitted =
                currentCommitted.add(
                        proposedExposure
                );

        boolean approved =
                proposedCommitted.compareTo(
                        maximumAllowed
                ) <= 0;

        if (!approved) {
            return new CapitalDecision(
                    false,
                    currentCommitted,
                    maximumAllowed,
                    definition.capitalConstraintOutcome()
            );
        }

        return new CapitalDecision(
                true,
                proposedCommitted,
                maximumAllowed,
                null
        );
    }

    /**
     * Evaluates capital availability for orders participating
     * in the same opening auction.
     *
     * Portfolio Governance v1 does NOT allow proceeds from a
     * position exiting in the same opening auction to finance
     * a new opening BUY.
     *
     * All new opening entries must be fundable using capital
     * that was already available before that auction began.
     */
    public SameOpenCapitalDecision evaluateOpeningBuyUsingPreAuctionCapital(
            BigDecimal preAuctionAvailableCapital,
            BigDecimal requiredCapital,
            BigDecimal sameOpenExitProceeds) {

        requireNonNegative(
                preAuctionAvailableCapital,
                "Pre-auction available capital"
        );

        requireNonNegative(
                requiredCapital,
                "Required capital"
        );

        requireNonNegative(
                sameOpenExitProceeds,
                "Same-open exit proceeds"
        );

        /*
         * Intentionally do NOT add sameOpenExitProceeds
         * to usable capital.
         */
        BigDecimal usableCapital =
                preAuctionAvailableCapital;

        boolean approved =
                requiredCapital.compareTo(
                        usableCapital
                ) <= 0;

        return new SameOpenCapitalDecision(
                approved,
                usableCapital,
                sameOpenExitProceeds,
                false,
                approved
                        ? null
                        : definition.capitalConstraintOutcome()
        );
    }

    public PositionExposureDecision evaluatePositionExposure(
            BigDecimal portfolioEquity,
            BigDecimal proposedPositionExposure) {

        requirePositive(
                portfolioEquity,
                "Portfolio equity"
        );

        requireNonNegative(
                proposedPositionExposure,
                "Proposed position exposure"
        );

        BigDecimal maximumAllowed =
                calculateMaximumPositionExposure(
                        portfolioEquity
                );

        boolean approved =
                proposedPositionExposure.compareTo(
                        maximumAllowed
                ) <= 0;

        return new PositionExposureDecision(
                approved,
                maximumAllowed,
                approved
                        ? null
                        : "POSITION_EXPOSURE_LIMIT"
        );
    }

    /**
     * The 5% maximum position exposure rule is an ENTRY rule.
     *
     * Once a position has validly entered, market appreciation
     * may cause its current marked value to exceed 5% of
     * portfolio equity.
     *
     * Portfolio Governance v1 does not force a trim merely
     * because a winning position appreciates above that level.
     */
    public PostEntryExposureDecision evaluatePostEntryAppreciation(
            BigDecimal portfolioEquity,
            BigDecimal currentPositionExposure,
            boolean passedEntryExposureCheck) {

        requirePositive(
                portfolioEquity,
                "Portfolio equity"
        );

        requireNonNegative(
                currentPositionExposure,
                "Current position exposure"
        );

        if (!passedEntryExposureCheck) {
            throw new IllegalArgumentException(
                    "Post-entry appreciation evaluation requires a position that passed the entry exposure check"
            );
        }

        BigDecimal currentExposurePercent =
                currentPositionExposure.divide(
                        portfolioEquity,
                        8,
                        java.math.RoundingMode.HALF_UP
                );

        boolean currentlyAboveEntryLimit =
                currentExposurePercent.compareTo(
                        definition.maximumPositionExposurePercent()
                ) > 0;

        return new PostEntryExposureDecision(
                currentlyAboveEntryLimit,
                false,
                true,
                currentExposurePercent
        );
    }

    public boolean positionExposureCapAppliesAtEntryOnly() {
        return true;
    }

    public boolean shouldAutoTrimForAppreciation(
            BigDecimal portfolioEquity,
            BigDecimal currentPositionExposure) {

        requirePositive(
                portfolioEquity,
                "Portfolio equity"
        );

        requireNonNegative(
                currentPositionExposure,
                "Current position exposure"
        );

        /*
         * Appreciation above the original 5% entry cap
         * never causes an automatic trim in v1.
         */
        return false;
    }

    public boolean sameOpenExitProceedsAvailableForNewEntries() {
        return false;
    }

    public String accountType() {
        return "REG_T_MARGIN";
    }

    public boolean leverageEnabled() {
        return definition.leverageEnabled();
    }

    public BigDecimal borrowedCapital() {
        return BigDecimal.ZERO;
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

    public record CapitalDecision(
            boolean approved,
            BigDecimal totalCommittedExposure,
            BigDecimal maximumAllowedExposure,
            String outcome
    ) {
    }

    public record PositionExposureDecision(
            boolean approved,
            BigDecimal maximumAllowedExposure,
            String rejectionReason
    ) {
    }

    public record SameOpenCapitalDecision(
            boolean approved,
            BigDecimal usablePreAuctionCapital,
            BigDecimal excludedSameOpenExitProceeds,
            boolean exitProceedsAvailableToNewBuy,
            String outcome
    ) {

        public SameOpenCapitalDecision {
            requireNonNegative(
                    usablePreAuctionCapital,
                    "Usable pre-auction capital"
            );

            requireNonNegative(
                    excludedSameOpenExitProceeds,
                    "Excluded same-open exit proceeds"
            );

            if (exitProceedsAvailableToNewBuy) {
                throw new IllegalArgumentException(
                        "Same-open exit proceeds cannot fund new opening BUYs in Portfolio Governance v1"
                );
            }
        }
    }

    public record PostEntryExposureDecision(
            boolean currentlyAboveEntryLimit,
            boolean automaticTrimScheduled,
            boolean exposureCapAppliesAtEntryOnly,
            BigDecimal currentExposurePercent
    ) {

        public PostEntryExposureDecision {
            requireNonNegative(
                    currentExposurePercent,
                    "Current exposure percent"
            );

            if (automaticTrimScheduled) {
                throw new IllegalArgumentException(
                        "Portfolio Governance v1 does not automatically trim appreciating positions"
                );
            }

            if (!exposureCapAppliesAtEntryOnly) {
                throw new IllegalArgumentException(
                        "Portfolio Governance v1 applies the 5% position cap at entry only"
                );
            }
        }
    }
}