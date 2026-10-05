Feature: Symbol market analysis

  MarshallAi should retrieve historical market candles for a symbol
  and use those candles to perform chart-pattern and technical analysis
  only when sufficient historical market data is available.

  Scenario: Analyze a bullish symbol using sufficient historical market candles
    Given historical market data is available for symbol "TSLA"
    When MarshallAi analyzes symbol "TSLA" using 200 candles
    Then the symbol analysis should contain 200 market candles
    And the symbol analysis result should not be null
    And the symbol technical signal should not be null
    And the symbol technical bullish weighted score should be 6
    And the symbol technical bearish weighted score should be 0
    And the symbol technical confidence score should be 100 percent

  Scenario: Reject insufficient historical market data
    Given historical market data is available for symbol "TSLA"
    When MarshallAi attempts to analyze symbol "TSLA" using 199 candles
    Then the symbol analysis should be rejected as insufficient data

  Scenario: Reject a blank symbol
    When MarshallAi attempts to analyze a blank market symbol
    Then the symbol analysis should be rejected

  Scenario: Reject an invalid candle count
    When MarshallAi attempts to analyze symbol "TSLA" using 0 candles
    Then the symbol analysis should be rejected