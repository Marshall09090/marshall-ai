Feature: Symbol driven trading analysis API

  The MarshallAI trading API should be able to analyze
  a market symbol using historical candle data and run
  the result through the complete trading decision pipeline.

  Scenario: Analyze a strongly bullish symbol
    Given the symbol trading API has strongly bullish market data for "TSLA"
    And the symbol trading request has normal event risk
    And the symbol trading request uses valid position sizing values
    When the symbol trading analysis endpoint is executed
    Then the symbol trading response should be returned successfully
    And the symbol trading response symbol should be "TSLA"
    And the symbol trading technical confidence should be 100 percent
    And the symbol trading decision should be "BUY"
    And the symbol trading confidence should be at least 80 percent
    And the symbol trade should be approved

  Scenario: Reject a trade when confidence is below the required threshold
    Given the symbol trading API has mixed market data for "TSLA"
    And the symbol trading request has normal event risk
    And the symbol trading request uses valid position sizing values
    When the symbol trading analysis endpoint is executed
    Then the symbol trading response should be returned successfully
    And the symbol trading decision should be "HOLD"
    And the symbol trade should not be approved

  Scenario: Block a high confidence trade because of event risk
    Given the symbol trading API has strongly bullish market data for "TSLA"
    And the symbol trading request has high event risk
    And the symbol trading request uses valid position sizing values
    When the symbol trading analysis endpoint is executed
    Then the symbol trading response should be returned successfully
    And the symbol trading event risk should block trading
    And the symbol trade should not be approved