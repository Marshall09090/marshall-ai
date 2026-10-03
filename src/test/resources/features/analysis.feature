Feature: MarshallAi market analysis engine

  Scenario: Analyze market prices and identify a bullish chart pattern
    Given the following market analysis closing prices:
      | price  |
      | 100.00 |
      | 110.00 |
      | 102.00 |
      | 109.50 |
      | 101.50 |
      | 110.20 |
      | 102.20 |
      | 112.50 |
    When MarshallAi analyzes the market
    Then the market analysis should contain the "Bullish Rectangle" pattern
    And the market analysis direction should be "BULLISH"

  Scenario: Analyze market prices and identify a bearish rectangle
    Given the following market analysis closing prices:
      | price  |
      | 112.00 |
      | 102.00 |
      | 110.00 |
      | 102.50 |
      | 109.50 |
      | 101.80 |
      | 109.00 |
      | 99.00  |
    When MarshallAi analyzes the market
    Then the market analysis should contain the "Bearish Rectangle" pattern
    And the market analysis direction should be "BEARISH"

  Scenario: Analyze market prices and identify an ascending channel
    Given the following market analysis closing prices:
      | price  |
      | 100.00 |
      | 105.00 |
      | 102.00 |
      | 107.00 |
      | 104.00 |
      | 109.00 |
      | 106.00 |
      | 111.00 |
      | 108.00 |
      | 113.00 |
    When MarshallAi analyzes the market
    Then the market analysis should contain the "Ascending Channel" pattern
    And the market analysis direction should be "BULLISH"

  Scenario: Analyze market prices and identify a descending channel
    Given the following market analysis closing prices:
      | price  |
      | 113.00 |
      | 108.00 |
      | 111.00 |
      | 106.00 |
      | 109.00 |
      | 104.00 |
      | 107.00 |
      | 102.00 |
      | 105.00 |
      | 100.00 |
    When MarshallAi analyzes the market
    Then the market analysis should contain the "Descending Channel" pattern
    And the market analysis direction should be "BEARISH"

  Scenario: Analyze market prices and identify a bullish pennant
    Given the following market analysis closing prices:
      | price  |
      | 100.00 |
      | 104.00 |
      | 109.00 |
      | 114.00 |
      | 112.00 |
      | 113.50 |
      | 112.50 |
      | 113.20 |
      | 116.00 |
    When MarshallAi analyzes the market
    Then the market analysis should contain the "Bullish Pennant" pattern
    And the market analysis direction should be "BULLISH"

  Scenario: Analyze market prices and identify a bearish pennant
    Given the following market analysis closing prices:
      | price  |
      | 120.00 |
      | 116.00 |
      | 111.00 |
      | 106.00 |
      | 108.00 |
      | 106.50 |
      | 107.50 |
      | 106.80 |
      | 104.00 |
    When MarshallAi analyzes the market
    Then the market analysis should contain the "Bearish Pennant" pattern
    And the market analysis direction should be "BEARISH"

  Scenario: Analyze market prices and identify a bull flag
    Given the following market analysis closing prices:
      | price  |
      | 100.00 |
      | 104.00 |
      | 108.00 |
      | 112.00 |
      | 110.00 |
      | 109.00 |
      | 108.50 |
      | 111.00 |
      | 114.00 |
    When MarshallAi analyzes the market
    Then the market analysis should contain the "Bull Flag" pattern
    And the market analysis direction should be "BULLISH"

  Scenario: Analyze market prices and identify a bear flag
    Given the following market analysis closing prices:
      | price  |
      | 120.00 |
      | 115.00 |
      | 110.00 |
      | 112.00 |
      | 113.00 |
      | 111.00 |
      | 109.00 |
    When MarshallAi analyzes the market
    Then the market analysis should contain the "Bear Flag" pattern
    And the market analysis direction should be "BEARISH"

  Scenario: Analyze market prices and identify a bullish breakout
    Given the following market analysis closing prices:
      | price  |
      | 100.00 |
      | 102.00 |
      | 101.00 |
      | 104.00 |
    When MarshallAi analyzes the market
    Then the market analysis should contain the "Bullish Breakout" pattern
    And the market analysis direction should be "BULLISH"