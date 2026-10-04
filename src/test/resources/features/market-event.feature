Feature: MarshallAI market event ingestion

  Scenario: Market events are stored and ordered by scheduled time
    Given a "EARNINGS" market event named "Apple Earnings" with impact "HIGH" occurs in 60 minutes for ticker "AAPL"
    And a "ECONOMIC" market event named "Consumer Price Index" with impact "CRITICAL" occurs in 30 minutes
    When MarshallAI retrieves all market events
    Then the market event result should contain 2 events
    And the first market event should be named "Consumer Price Index"

  Scenario: Market events can be filtered by ticker
    Given a "EARNINGS" market event named "Apple Earnings" with impact "HIGH" occurs in 60 minutes for ticker "AAPL"
    And a "EARNINGS" market event named "Tesla Earnings" with impact "HIGH" occurs in 90 minutes for ticker "TSLA"
    When MarshallAI retrieves market events for ticker "AAPL"
    Then the market event result should contain 1 events
    And the first market event should be named "Apple Earnings"

  Scenario: MarshallAI identifies high impact events
    Given a "ECONOMIC" market event named "Consumer Price Index" with impact "CRITICAL" occurs in 30 minutes
    And a "NEWS" market event named "Minor Market Update" with impact "LOW" occurs in 45 minutes
    When MarshallAI retrieves high impact market events
    Then the market event result should contain 1 events
    And the first market event should be named "Consumer Price Index"

  Scenario: Global high impact event affects a ticker
    Given a "ECONOMIC" market event named "Federal Reserve Decision" with impact "CRITICAL" occurs in 20 minutes
    When MarshallAI checks ticker "AAPL" for high impact events in the next 60 minutes
    Then a high impact market event should be detected

  Scenario: Ticker specific event affects the matching ticker
    Given a "EARNINGS" market event named "Tesla Earnings" with impact "HIGH" occurs in 30 minutes for ticker "TSLA"
    When MarshallAI checks ticker "TSLA" for high impact events in the next 60 minutes
    Then a high impact market event should be detected

  Scenario: Ticker specific event does not affect another ticker
    Given a "EARNINGS" market event named "Tesla Earnings" with impact "HIGH" occurs in 30 minutes for ticker "TSLA"
    When MarshallAI checks ticker "AAPL" for high impact events in the next 60 minutes
    Then no high impact market event should be detected

  Scenario: Event outside the trading risk window does not block the ticker
    Given a "EARNINGS" market event named "Apple Earnings" with impact "HIGH" occurs in 180 minutes for ticker "AAPL"
    When MarshallAI checks ticker "AAPL" for high impact events in the next 60 minutes
    Then no high impact market event should be detected