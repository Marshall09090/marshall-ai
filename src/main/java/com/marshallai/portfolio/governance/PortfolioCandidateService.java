package com.marshallai.portfolio.governance;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class PortfolioCandidateService {

    /**
     * Portfolio Governance v1:
     *
     * Same-day BUY candidates are processed in this order:
     *
     * 1. Highest confidence first
     * 2. Alphabetical symbol order for confidence ties
     *
     * This ordering must be deterministic so that identical
     * market data and configuration produce identical portfolios.
     */
    public List<Candidate> orderCandidates(
            List<Candidate> candidates) {

        if (candidates == null) {
            throw new IllegalArgumentException(
                    "Candidate list cannot be null"
            );
        }

        return candidates.stream()
                .sorted(
                        Comparator
                                .comparingInt(
                                        Candidate::confidence
                                )
                                .reversed()
                                .thenComparing(
                                        Candidate::symbol
                                )
                )
                .toList();
    }

    /**
     * A BUY is ignored while the symbol is already held
     * or while another BUY order for the symbol is pending.
     *
     * Once the position has exited and no BUY remains pending,
     * the next new BUY signal is allowed to create a new order.
     */
    public BuyDecision evaluateBuySignal(
            String symbol,
            Set<String> heldSymbols,
            Set<String> pendingBuySymbols) {

        String normalizedSymbol =
                normalizeSymbol(symbol);

        boolean alreadyHeld =
                containsSymbol(
                        heldSymbols,
                        normalizedSymbol
                );

        boolean alreadyPending =
                containsSymbol(
                        pendingBuySymbols,
                        normalizedSymbol
                );

        if (alreadyHeld || alreadyPending) {
            return new BuyDecision(
                    false,
                    true,
                    false,
                    "DUPLICATE_BUY_IGNORED"
            );
        }

        return new BuyDecision(
                true,
                false,
                true,
                "BUY_ALLOWED"
        );
    }

    private boolean containsSymbol(
            Set<String> symbols,
            String targetSymbol) {

        if (symbols == null ||
                symbols.isEmpty()) {
            return false;
        }

        return symbols.stream()
                .filter(symbol -> symbol != null)
                .map(String::trim)
                .map(String::toUpperCase)
                .anyMatch(
                        targetSymbol::equals
                );
    }

    private String normalizeSymbol(
            String symbol) {

        if (symbol == null ||
                symbol.isBlank()) {
            throw new IllegalArgumentException(
                    "Symbol cannot be blank"
            );
        }

        return symbol.trim().toUpperCase();
    }

    public record Candidate(
            String symbol,
            int confidence
    ) {

        public Candidate {
            if (symbol == null ||
                    symbol.isBlank()) {
                throw new IllegalArgumentException(
                        "Candidate symbol cannot be blank"
                );
            }

            if (confidence < 0 ||
                    confidence > 100) {
                throw new IllegalArgumentException(
                        "Candidate confidence must be between 0 and 100"
                );
            }

            symbol =
                    symbol.trim().toUpperCase();
        }
    }

    public record BuyDecision(
            boolean createOrder,
            boolean duplicateIgnored,
            boolean reentryAllowed,
            String outcome
    ) {

        public BuyDecision {
            if (outcome == null ||
                    outcome.isBlank()) {
                throw new IllegalArgumentException(
                        "BUY decision outcome cannot be blank"
                );
            }

            if (duplicateIgnored &&
                    createOrder) {
                throw new IllegalArgumentException(
                        "An ignored duplicate BUY cannot create an order"
                );
            }
        }
    }
}