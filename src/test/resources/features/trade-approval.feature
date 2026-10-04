Feature: MarshallAI final trade approval gate

  Scenario: Strong BUY signal with acceptable risk is approved
    Given the trade approval signal is "BUY" with 90 percent confidence
    And the trade approval event risk is "LOW"
    And the trade approval position quantity is 5
    When MarshallAI evaluates final trade approval
    Then the final trade status should be "APPROVED"
    And the final trade decision should be "BUY"
    And the final trade confidence should be 90 percent

  Scenario: Strong SELL signal with acceptable risk is approved
    Given the trade approval signal is "SELL" with 90 percent confidence
    And the trade approval event risk is "LOW"
    And the trade approval position quantity is 5
    When MarshallAI evaluates final trade approval
    Then the final trade status should be "APPROVED"
    And the final trade decision should be "SELL"
    And the final trade confidence should be 90 percent

  Scenario: Signal below the minimum confidence is blocked
    Given the trade approval signal is "BUY" with 79 percent confidence
    And the trade approval event risk is "LOW"
    And the trade approval position quantity is 5
    When MarshallAI evaluates final trade approval
    Then the final trade status should be "BLOCKED"
    And the final trade decision should be "BUY"
    And the final trade confidence should be 79 percent

  Scenario: HOLD signal is blocked
    Given the trade approval signal is "HOLD" with 100 percent confidence
    And the trade approval event risk is "LOW"
    And the trade approval position quantity is 5
    When MarshallAI evaluates final trade approval
    Then the final trade status should be "BLOCKED"
    And the final trade decision should be "HOLD"
    And the final trade confidence should be 100 percent

  Scenario: High event risk blocks an otherwise strong trade
    Given the trade approval signal is "BUY" with 95 percent confidence
    And the trade approval event risk is "HIGH"
    And the trade approval position quantity is 5
    When MarshallAI evaluates final trade approval
    Then the final trade status should be "BLOCKED"
    And the final trade decision should be "BUY"
    And the final trade confidence should be 95 percent

  Scenario: Critical event risk blocks an otherwise strong trade
    Given the trade approval signal is "SELL" with 95 percent confidence
    And the trade approval event risk is "CRITICAL"
    And the trade approval position quantity is 5
    When MarshallAI evaluates final trade approval
    Then the final trade status should be "BLOCKED"
    And the final trade decision should be "SELL"
    And the final trade confidence should be 95 percent

  Scenario: Zero position size blocks a trade
    Given the trade approval signal is "BUY" with 90 percent confidence
    And the trade approval event risk is "LOW"
    And the trade approval position quantity is 0
    When MarshallAI evaluates final trade approval
    Then the final trade status should be "BLOCKED"
    And the final trade decision should be "BUY"
    And the final trade confidence should be 90 percent