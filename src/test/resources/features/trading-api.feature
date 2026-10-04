Feature: MarshallAI trading REST API

  Scenario: Trading engine reports ready status
    When MarshallAI requests the trading API status
    Then the trading API response status should be 200
    And the trading engine status should be "READY"
    And the trading API minimum confidence should be 80 percent
    And trade approval should be enabled