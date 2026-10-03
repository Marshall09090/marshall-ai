Feature: Nexora market analysis engine

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
    When Nexora analyzes the market
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
    When Nexora analyzes the market
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
    When Nexora analyzes the market
    Then the market analysis should contain the "Ascending Channel" pattern
    And the market analysis direction should be "BULLISH"