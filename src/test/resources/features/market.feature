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