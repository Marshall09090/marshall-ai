Feature: MarshallAI position sizing and trade risk management

  Scenario: Size a trade using one percent account risk
    Given a trading account balance of 500 dollars
    And the maximum trade risk is 1 percent
    And the planned entry price is 50 dollars
    And the planned stop loss price is 49 dollars
    When MarshallAI calculates the position size
    Then the maximum dollar risk should be 5 dollars
    And the risk per unit should be 1 dollars
    And the position quantity should be 0
    And the position value should be 0 dollars

  Scenario: Wider stop loss reduces the position size
    Given a trading account balance of 500 dollars
    And the maximum trade risk is 1 percent
    And the planned entry price is 50 dollars
    And the planned stop loss price is 47.50 dollars
    When MarshallAI calculates the position size
    Then the maximum dollar risk should be 5 dollars
    And the risk per unit should be 2.50 dollars
    And the position quantity should be 0
    And the position value should be 0 dollars

  Scenario: Maximum account exposure limits the position size
    Given a trading account balance of 100000 dollars
    And the maximum trade risk is 1 percent
    And the planned entry price is 450 dollars
    And the planned stop loss price is 440 dollars
    When MarshallAI calculates the position size
    Then the maximum dollar risk should be 1000 dollars
    And the risk per unit should be 10 dollars
    And the position quantity should be 11
    And the position value should be 4950 dollars