Feature: MarshallAI combined trading signal engine

  Scenario: Bullish patterns and bullish technicals produce a BUY
    Given the combined analysis detected the following patterns:
      | pattern          |
      | Bullish Breakout |
      | Cup and Handle   |
    And the technical engine produced bullish score 6 and bearish score 0
    When MarshallAI evaluates the combined trading signal
    Then the combined bullish weighted score should be 12
    And the combined bearish weighted score should be 0
    And the combined confidence score should be 100 percent
    And the combined trading decision should be "BUY"

  Scenario: Bearish patterns and bearish technicals produce a SELL
    Given the combined analysis detected the following patterns:
      | pattern           |
      | Bearish Breakdown |
      | Double Top        |
    And the technical engine produced bullish score 0 and bearish score 6
    When MarshallAI evaluates the combined trading signal
    Then the combined bullish weighted score should be 0
    And the combined bearish weighted score should be 12
    And the combined confidence score should be 100 percent
    And the combined trading decision should be "SELL"

  Scenario: Conflicting evidence produces a HOLD
    Given the combined analysis detected the following patterns:
      | pattern          |
      | Bullish Breakout |
      | Cup and Handle   |
    And the technical engine produced bullish score 0 and bearish score 3
    When MarshallAI evaluates the combined trading signal
    Then the combined bullish weighted score should be 6
    And the combined bearish weighted score should be 3
    And the combined confidence score should be 67 percent
    And the combined trading decision should be "HOLD"

  Scenario: Combined evidence reaches exactly 80 percent confidence
    Given the combined analysis detected the following patterns:
      | pattern          |
      | Bullish Breakout |
      | Bull Flag        |
    And the technical engine produced bullish score 3 and bearish score 2
    When MarshallAI evaluates the combined trading signal
    Then the combined bullish weighted score should be 8
    And the combined bearish weighted score should be 2
    And the combined confidence score should be 80 percent
    And the combined trading decision should be "BUY"