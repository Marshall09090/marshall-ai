Feature: MarshallAI persistence and versioning

  MarshallAI must maintain an immutable and auditable history
  of runs, configurations, market data, and trading decisions.

  Scenario: A persisted run cannot be modified
    Given a MarshallAI run has been persisted
    When an attempt is made to modify the persisted run
    Then the modification should be rejected
    And the original persisted run should remain unchanged

  Scenario: Changing a governed setting produces a new configuration version
    Given a MarshallAI configuration has been fingerprinted
    When a governed configuration setting is changed
    Then the configuration fingerprint should be different

  Scenario: A rejected decision is persisted with its reason
    Given a MarshallAI run is active
    And a trading decision is rejected by a safety gate
    When the decision is persisted
    Then the rejected decision should exist in the decision ledger
    And the rejection gate should be recorded
    And the rejection reason should be recorded

  Scenario: A governed run without registered criteria is refused
    Given no acceptance criteria are registered for a governed validation run
    When MarshallAI attempts to start the run
    Then the run should be refused

  Scenario: A persisted run records the market data fingerprint
    Given historical candles were used by a MarshallAI run
    When the run is persisted
    Then the market data fingerprint should be recorded
    And the market data fetch timestamp should be recorded
