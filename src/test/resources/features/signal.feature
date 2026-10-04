Feature: MarshallAi weighted trading signal decision engine

  Scenario: Issue a BUY decision exactly at the 80 percent threshold
    Given the market analysis detected the following patterns:
      | pattern            |
      | Bullish Breakout   |
      | Bull Flag          |
      | Ascending Triangle |
      | Ascending Channel  |
      | Bearish Rectangle  |
    When MarshallAi evaluates the trading signal
    Then the bullish signal count should be 4
    And the bearish signal count should be 1
    And the bullish weighted score should be 8
    And the bearish weighted score should be 2
    And the confidence score should be 80 percent
    And the trading decision should be "BUY"

  Scenario: Issue a SELL decision exactly at the 80 percent threshold
    Given the market analysis detected the following patterns:
      | pattern             |
      | Bearish Breakdown   |
      | Bear Flag           |
      | Descending Triangle |
      | Descending Channel  |
      | Bullish Rectangle   |
    When MarshallAi evaluates the trading signal
    Then the bullish signal count should be 1
    And the bearish signal count should be 4
    And the bullish weighted score should be 2
    And the bearish weighted score should be 8
    And the confidence score should be 80 percent
    And the trading decision should be "SELL"

  Scenario: Strong bullish patterns produce high weighted confidence
    Given the market analysis detected the following patterns:
      | pattern            |
      | Bullish Breakout   |
      | Cup and Handle     |
      | Descending Channel |
    When MarshallAi evaluates the trading signal
    Then the bullish signal count should be 2
    And the bearish signal count should be 1
    And the bullish weighted score should be 6
    And the bearish weighted score should be 1
    And the confidence score should be 86 percent
    And the trading decision should be "BUY"

  Scenario: HOLD when weighted confidence is below 80 percent
    Given the market analysis detected the following patterns:
      | pattern          |
      | Bullish Breakout |
      | Cup and Handle   |
      | Bear Flag        |
    When MarshallAi evaluates the trading signal
    Then the bullish signal count should be 2
    And the bearish signal count should be 1
    And the bullish weighted score should be 6
    And the bearish weighted score should be 2
    And the confidence score should be 75 percent
    And the trading decision should be "HOLD"

  Scenario: HOLD when no directional confirmation exists
    Given the market analysis detected the following patterns:
      | pattern              |
      | Symmetrical Triangle |
    When MarshallAi evaluates the trading signal
    Then the bullish signal count should be 0
    And the bearish signal count should be 0
    And the bullish weighted score should be 0
    And the bearish weighted score should be 0
    And the confidence score should be 0 percent
    And the trading decision should be "HOLD"