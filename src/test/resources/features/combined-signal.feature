Feature: Combined trading signal with event-risk protection

  The MarshallAI trading engine combines chart-pattern evidence
  and technical-indicator evidence while applying an event-risk
  safety gate before allowing a BUY or SELL decision.

  Scenario: Critical event risk overrides a strong BUY signal
    Given the combined analysis detected the following patterns:
      | pattern          |
      | Bullish Breakout |
      | Cup and Handle   |
    And the technical engine produced bullish score 6 and bearish score 0
    And the combined signal has event risk level "CRITICAL"
    When MarshallAI evaluates the risk-gated combined trading signal
    Then the combined bullish weighted score should be 12
    And the combined bearish weighted score should be 0
    And the combined confidence score should be 100 percent
    And the combined trading decision should be "HOLD"

  Scenario: Critical event risk overrides a strong SELL signal
    Given the combined analysis detected the following patterns:
      | pattern           |
      | Bearish Breakdown |
      | Double Top        |
    And the technical engine produced bullish score 0 and bearish score 6
    And the combined signal has event risk level "CRITICAL"
    When MarshallAI evaluates the risk-gated combined trading signal
    Then the combined bullish weighted score should be 0
    And the combined bearish weighted score should be 12
    And the combined confidence score should be 100 percent
    And the combined trading decision should be "HOLD"

  Scenario: Medium event risk allows a strong BUY signal
    Given the combined analysis detected the following patterns:
      | pattern          |
      | Bullish Breakout |
      | Cup and Handle   |
    And the technical engine produced bullish score 6 and bearish score 0
    And the combined signal has event risk level "MEDIUM"
    When MarshallAI evaluates the risk-gated combined trading signal
    Then the combined bullish weighted score should be 12
    And the combined bearish weighted score should be 0
    And the combined confidence score should be 100 percent
    And the combined trading decision should be "BUY"