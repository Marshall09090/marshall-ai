Feature: MarshallAi trading signal decision engine

  Scenario: Issue a BUY decision at the 80 percent confidence threshold
    Given the market analysis detected the following patterns:
      | pattern            |
      | Bullish Rectangle  |
      | Bull Flag          |
      | Ascending Triangle |
      | Cup and Handle     |
      | Bearish Rectangle  |
    When MarshallAi evaluates the trading signal
    Then the bullish signal count should be 4
    And the bearish signal count should be 1
    And the confidence score should be 80 percent
    And the trading decision should be "BUY"

  Scenario: Issue a SELL decision at the 80 percent confidence threshold
    Given the market analysis detected the following patterns:
      | pattern             |
      | Bearish Rectangle   |
      | Bear Flag           |
      | Descending Triangle |
      | Rounding Top        |
      | Bullish Rectangle   |
    When MarshallAi evaluates the trading signal
    Then the bullish signal count should be 1
    And the bearish signal count should be 4
    And the confidence score should be 80 percent
    And the trading decision should be "SELL"

  Scenario: HOLD when confidence is below 80 percent
    Given the market analysis detected the following patterns:
      | pattern            |
      | Bullish Rectangle  |
      | Bull Flag          |
      | Cup and Handle     |
      | Bearish Rectangle  |
      | Bear Flag          |
    When MarshallAi evaluates the trading signal
    Then the bullish signal count should be 3
    And the bearish signal count should be 2
    And the confidence score should be 60 percent
    And the trading decision should be "HOLD"

  Scenario: HOLD when no directional confirmation exists
    Given the market analysis detected the following patterns:
      | pattern               |
      | Symmetrical Triangle  |
    When MarshallAi evaluates the trading signal
    Then the bullish signal count should be 0
    And the bearish signal count should be 0
    And the confidence score should be 0 percent
    And the trading decision should be "HOLD"