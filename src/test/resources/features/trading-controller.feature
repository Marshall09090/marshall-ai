Feature: MarshallAI trading analysis API

  Scenario: Trading engine status is available
    When the trading status endpoint is requested
    Then the trading service should be "MarshallAI Trading Engine"
    And the trading engine status should be "READY"
    And the minimum trading confidence should be 80 percent
    And trade approval should be enabled
  Scenario: MarshallAI performs a complete bullish trading analysis
    Given a valid bullish trading analysis request
    When the trading analysis endpoint is requested
    Then the trading analysis response should be successful
    And the trading analysis symbol should be "TSLA"
    And the trading analysis should contain detected patterns
    And the trading analysis should contain a trading decision
    And the trading analysis confidence should be between 0 and 100 percent
    And the trading analysis should contain an event risk level
    And the trading analysis should contain a trade approval status
    And the trading analysis position quantity should not be negative