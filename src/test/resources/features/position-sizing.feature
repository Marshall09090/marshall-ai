Feature: MarshallAI position sizing and trade risk management

  Scenario: Size a trade using one percent account risk
    Given a trading account balance of 500 dollars
    And the maximum trade risk is 1 percent
    And the planned entry price is 50 dollars
    And the planned stop loss price is 49 dollars
    When MarshallAI calculates the position size
    Then the maximum dollar risk should be 5 dollars
    And the risk per unit should be 1 dollars
    And the position quantity should be 5
    And the position value should be 250 dollars

  Scenario: Wider stop loss reduces the position size
    Given a trading account balance of 500 dollars
    And the maximum trade risk is 1 percent
    And the planned entry price is 50 dollars
    And the planned stop loss price is 47.50 dollars
    When MarshallAI calculates the position size
    Then the maximum dollar risk should be 5 dollars
    And the risk per unit should be 2.50 dollars
    And the position quantity should be 2
    And the position value should be 100 dollars

  Scenario: Available capital limits the position size
    Given a trading account balance of 500 dollars
    And the maximum trade risk is 10 percent
    And the planned entry price is 200 dollars
    And the planned stop loss price is 199 dollars
    When MarshallAI calculates the position size
    Then the maximum dollar risk should be 50 dollars
    And the risk per unit should be 1 dollars
    And the position quantity should be 2
    And the position value should be 400 dollars