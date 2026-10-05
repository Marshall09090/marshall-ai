Feature: MarshallAi technical indicator confirmation engine

  Scenario: Strong bullish technical confirmation
    Given a strongly bullish technical market with 30 candles
    When MarshallAi evaluates the technical signal
    Then the technical bullish weighted score should be 6
    And the technical bearish weighted score should be 0
    And the technical confidence score should be 100 percent

  Scenario: Strong bearish technical confirmation
    Given a strongly bearish technical market with 30 candles
    When MarshallAi evaluates the technical signal
    Then the technical bullish weighted score should be 0
    And the technical bearish weighted score should be 6
    And the technical confidence score should be 100 percent

  Scenario: Conflicting technical indicators reduce confidence
    Given a mixed technical market
    When MarshallAi evaluates the technical signal
    Then the technical bullish weighted score should be 2
    And the technical bearish weighted score should be 3
    And the technical confidence score should be 60 percent

  Scenario: No directional technical evidence produces zero confidence
    Given a technical market with no directional evidence
    When MarshallAi evaluates the technical signal
    Then the technical bullish weighted score should be 0
    And the technical bearish weighted score should be 0
    And the technical confidence score should be 0 percent