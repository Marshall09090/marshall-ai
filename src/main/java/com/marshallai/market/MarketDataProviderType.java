package com.marshallai.market;

public enum MarketDataProviderType {

    REAL(
            true,
            true
    ),

    SIMULATED(
            false,
            false
    );

    private final boolean protectedDateEnforcementRequired;

    private final boolean governedVerdictAllowed;

    MarketDataProviderType(
            boolean protectedDateEnforcementRequired,
            boolean governedVerdictAllowed) {

        this.protectedDateEnforcementRequired =
                protectedDateEnforcementRequired;

        this.governedVerdictAllowed =
                governedVerdictAllowed;
    }

    public boolean requiresProtectedDateEnforcement() {

        return protectedDateEnforcementRequired;
    }

    public boolean allowsGovernedVerdict() {

        return governedVerdictAllowed;
    }

    public void requireGovernedVerdictAllowed() {

        if (!governedVerdictAllowed) {

            throw new IllegalStateException(
                    "SIMULATED_PROVIDER_CANNOT_EMIT_GOVERNED_VERDICT"
            );
        }
    }
}