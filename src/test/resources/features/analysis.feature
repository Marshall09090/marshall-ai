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