Feature: Nexora market data

  As a Nexora user
  I want to retrieve market information
  So that Nexora can analyze financial markets

  Scenario: Request a stock market quote
    Given the Nexora AI application is running
    When I request a market quote for "AAPL" with asset type "STOCK"
    Then the market quote symbol should be "AAPL"
    And the market quote asset type should be "STOCK"
    And the market quote should contain a price
    And the market quote should contain a price
    And the market quote price should be greater than zero
    And the market quote should contain a timestamp

  Scenario: Request historical candle data for a stock
    Given the Nexora AI application is running
    When I request historical market data for "AAPL" with asset type "STOCK" and timeframe "1m"
    Then the historical market data should not be empty
    And each candle should contain an open price
    And each candle should contain a high price
    And each candle should contain a low price
    And each candle should contain a close price
    And each candle should contain volume
    And each candle should contain a timestamp
    And each candle high price should be greater than or equal to its low price