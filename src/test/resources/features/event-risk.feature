Feature: MarshallAI event risk protection

  Scenario: Imminent FOMC decision creates critical risk
    Given a "FOMC" event named "Federal Reserve interest rate decision"
    And the event occurs in 10 minutes
    And the event is high impact
    When MarshallAI evaluates the event risk
    Then the event risk level should be "CRITICAL"
    And new trading should be blocked

  Scenario: Upcoming Fed Chair speech creates high risk
    Given a "FED_CHAIR" event named "Federal Reserve Chair speech"
    And the event occurs in 30 minutes
    And the event is high impact
    When MarshallAI evaluates the event risk
    Then the event risk level should be "HIGH"
    And new trading should be blocked

  Scenario: Earnings several hours away creates medium risk
    Given a "EARNINGS" event named "Company earnings report"
    And the event occurs in 180 minutes
    And the event is high impact
    When MarshallAI evaluates the event risk
    Then the event risk level should be "MEDIUM"
    And new trading should be allowed

  Scenario: Distant high impact event creates low risk
    Given a "CPI" event named "Consumer Price Index release"
    And the event occurs in 300 minutes
    And the event is high impact
    When MarshallAI evaluates the event risk
    Then the event risk level should be "LOW"
    And new trading should be allowed

  Scenario: Low impact event does not block trading
    Given a "ECONOMIC" event named "Low impact economic release"
    And the event occurs in 10 minutes
    And the event is low impact
    When MarshallAI evaluates the event risk
    Then the event risk level should be "LOW"
    And new trading should be allowed

  Scenario: Ingested critical market event automatically blocks trading
    Given an ingested "ECONOMIC" market event named "Federal Reserve Decision" with impact "CRITICAL" occurs in 10 minutes
    When MarshallAI automatically evaluates the ingested market event
    Then the event risk level should be "CRITICAL"
    And new trading should be blocked

  Scenario: Ingested high impact market event becomes high risk
    Given an ingested "EARNINGS" market event named "Apple Earnings" with impact "HIGH" occurs in 45 minutes
    When MarshallAI automatically evaluates the ingested market event
    Then the event risk level should be "HIGH"
    And new trading should be blocked

  Scenario: Ingested high impact market event becomes medium risk
    Given an ingested "ECONOMIC" market event named "Inflation Report" with impact "HIGH" occurs in 180 minutes
    When MarshallAI automatically evaluates the ingested market event
    Then the event risk level should be "MEDIUM"
    And new trading should be allowed

  Scenario: Distant high impact market event becomes low risk
    Given an ingested "ECONOMIC" market event named "Employment Report" with impact "HIGH" occurs in 300 minutes
    When MarshallAI automatically evaluates the ingested market event
    Then the event risk level should be "LOW"
    And new trading should be allowed

  Scenario: Nearby low impact market event remains low risk
    Given an ingested "NEWS" market event named "Minor Market Update" with impact "LOW" occurs in 10 minutes
    When MarshallAI automatically evaluates the ingested market event
    Then the event risk level should be "LOW"
    And new trading should be allowed