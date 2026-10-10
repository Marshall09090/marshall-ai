package com.marshallai.market.protection;

public class ProtectedMarketDataAccessException
        extends RuntimeException {

    private final String refusalReason;

    public ProtectedMarketDataAccessException(
            String refusalReason) {

        super(refusalReason);

        if (refusalReason == null
                || refusalReason.isBlank()) {

            throw new IllegalArgumentException(
                    "Refusal reason cannot be blank"
            );
        }

        this.refusalReason = refusalReason;
    }

    public String getRefusalReason() {
        return refusalReason;
    }
}