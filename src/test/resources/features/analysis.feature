Feature: MarshallAi market analysis engine

  Scenario: Analyze market prices and identify a bullish chart pattern
    Given the following market analysis closing prices:
      | price  |
      | 100.00 |
      | 110.00 |
      | 102.00 |
      | 109.80 |
      | 102.50 |
      | 110.10 |
      | 102.80 |
      | 112.00 |
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

  Scenario: Analyze market prices and identify a bearish breakdown
    Given the following market analysis closing prices:
      | price  |
      | 105.00 |
      | 104.00 |
      | 103.00 |
      | 103.50 |
      | 102.50 |
      | 102.00 |
      | 99.50  |
    When MarshallAi analyzes the market
    Then the market analysis should contain the "Bearish Breakdown" pattern
    And the market analysis direction should be "BEARISH"

  Scenario: Analyze market prices and identify a double top
    Given the following market analysis closing prices:
      | price  |
      | 100.00 |
      | 104.00 |
      | 108.00 |
      | 104.00 |
      | 108.20 |
      | 103.00 |
      | 99.00  |
    When MarshallAi analyzes the market
    Then the market analysis should contain the "Double Top" pattern
    And the market analysis direction should be "BEARISH"

  Scenario: Analyze market prices and identify a double bottom
    Given the following market analysis closing prices:
      | price  |
      | 110.00 |
      | 100.00 |
      | 106.00 |
      | 101.00 |
      | 105.00 |
      | 107.00 |
      | 109.00 |
    When MarshallAi analyzes the market
    Then the market analysis should contain the "Double Bottom" pattern
    And the market analysis direction should be "BULLISH"

  Scenario: Analyze market prices and identify a triple top
    Given the following market analysis closing prices:
      | price  |
      | 100.00 |
      | 110.00 |
      | 103.00 |
      | 109.50 |
      | 100.50 |
      | 110.20 |
      | 103.00 |
      | 98.00  |
    When MarshallAi analyzes the market
    Then the market analysis should contain the "Triple Top" pattern
    And the market analysis direction should be "BEARISH"

  Scenario: Analyze market prices and identify head and shoulders
    Given the following market analysis closing prices:
      | price  |
      | 100.00 |
      | 110.00 |
      | 103.00 |
      | 116.00 |
      | 102.50 |
      | 109.00 |
      | 100.00 |
    When MarshallAi analyzes the market
    Then the market analysis should contain the "Head and Shoulders" pattern
    And the market analysis direction should be "BEARISH"

  Scenario: Analyze market prices and identify inverse head and shoulders
    Given the following market analysis closing prices:
      | price  |
      | 120.00 |
      | 110.00 |
      | 117.00 |
      | 104.00 |
      | 117.50 |
      | 111.00 |
      | 120.00 |
    When MarshallAi analyzes the market
    Then the market analysis should contain the "Inverse Head and Shoulders" pattern
    And the market analysis direction should be "BULLISH"

  Scenario: Analyze market prices and identify an ascending triangle
    Given the following market analysis closing prices:
      | price  |
      | 100.00 |
      | 105.00 |
      | 101.50 |
      | 105.20 |
      | 102.50 |
      | 105.10 |
      | 103.80 |
      | 107.00 |
    When MarshallAi analyzes the market
    Then the market analysis should contain the "Ascending Triangle" pattern
    And the market analysis direction should be "BULLISH"

  Scenario: Analyze market prices and identify a descending triangle
    Given the following market analysis closing prices:
      | price  |
      | 110.00 |
      | 100.00 |
      | 108.00 |
      | 100.20 |
      | 106.00 |
      | 99.90  |
      | 104.00 |
      | 98.00  |
    When MarshallAi analyzes the market
    Then the market analysis should contain the "Descending Triangle" pattern
    And the market analysis direction should be "BEARISH"