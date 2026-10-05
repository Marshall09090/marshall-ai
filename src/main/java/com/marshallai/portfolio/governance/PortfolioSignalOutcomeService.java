package com.marshallai.portfolio.governance;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PortfolioSignalOutcomeService {

    private final PortfolioGovernanceDefinition definition;
    private final List<SignalOutcomeRecord> records =
            new ArrayList<>();

    public PortfolioSignalOutcomeService() {
        this.definition =
                PortfolioGovernanceDefinition.v1();
    }

    public PortfolioSignalOutcomeService(
            PortfolioGovernanceDefinition definition) {

        if (definition == null) {
            throw new IllegalArgumentException(
                    "Portfolio governance definition cannot be null"
            );
        }

        this.definition = definition;
    }

    /**
     * Records a valid BUY signal that could not become an
     * executable portfolio trade because capital was unavailable.
     *
     * The execution path is skipped, but the research path remains.
     * This prevents capital-ranking decisions from removing signals
     * from confidence-band analysis.
     */
    public SignalOutcomeRecord recordCapitalConstrainedSignal(
            String symbol,
            int confidence) {

        String normalizedSymbol =
                normalizeSymbol(symbol);

        validateConfidence(
                confidence
        );

        SignalOutcomeRecord record =
                new SignalOutcomeRecord(
                        normalizedSymbol,
                        confidence,
                        definition.capitalConstraintOutcome(),
                        false,
                        true,
                        true
                );

        records.add(
                record
        );

        return record;
    }

    public List<SignalOutcomeRecord> records() {
        return List.copyOf(records);
    }

    public int size() {
        return records.size();
    }

    public void clear() {
        records.clear();
    }

    public PortfolioGovernanceDefinition getDefinition() {
        return definition;
    }

    private static String normalizeSymbol(
            String symbol) {

        if (symbol == null ||
                symbol.isBlank()) {
            throw new IllegalArgumentException(
                    "Symbol cannot be blank"
            );
        }

        return symbol
                .trim()
                .toUpperCase();
    }

    private static void validateConfidence(
            int confidence) {

        if (confidence < 0 ||
                confidence > 100) {
            throw new IllegalArgumentException(
                    "Confidence must be between 0 and 100"
            );
        }
    }

    public record SignalOutcomeRecord(
            String symbol,
            int confidence,
            String executionOutcome,
            boolean executableTradeCreated,
            boolean standaloneTradeOutcomeRecorded,
            boolean confidenceBandEligible
    ) {

        public SignalOutcomeRecord {
            if (symbol == null ||
                    symbol.isBlank()) {
                throw new IllegalArgumentException(
                        "Signal-outcome symbol cannot be blank"
                );
            }

            validateConfidence(
                    confidence
            );

            if (executionOutcome == null ||
                    executionOutcome.isBlank()) {
                throw new IllegalArgumentException(
                        "Execution outcome cannot be blank"
                );
            }

            if (executableTradeCreated) {
                throw new IllegalArgumentException(
                        "A capital-constrained signal cannot create an executable trade"
                );
            }

            if (!standaloneTradeOutcomeRecorded) {
                throw new IllegalArgumentException(
                        "Capital-constrained signals must retain a standalone trade outcome"
                );
            }

            if (!confidenceBandEligible) {
                throw new IllegalArgumentException(
                        "Capital-constrained signals must remain eligible for confidence-band analysis"
                );
            }
        }
    }
}