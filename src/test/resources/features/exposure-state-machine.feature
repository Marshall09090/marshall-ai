Feature: Protected run exposure state machine

  MarshallAI must prevent protected validation and holdout data
  from being exposed more than once for the same governed target.

  Protected runs against real market data require durable state,
  atomic acquisition, auditable transitions, and fail-closed
  result exposure.

  Scenario: Real protected run requires a durable store
    Given a protected validation run uses real market data
    And only in-memory governance storage is active
    When the protected run is requested
    Then the protected run should be refused
    And the refusal reason should be "DURABLE_STORE_REQUIRED"

  Scenario: Every application start receives a fresh instance ID
    Given an application instance has started
    And its instance ID has been recorded
    When the application is restarted
    Then the new instance ID should differ from the previous instance ID

  Scenario: Governed mode permits only one active application instance
    Given one application instance holds the governed-mode advisory lock
    When another application instance requests governed mode
    Then the second instance should be refused
    And the refusal reason should be "GOVERNED_INSTANCE_ALREADY_ACTIVE"

  Scenario: A reserved protected target can be acquired atomically
    Given a protected target is in state "RESERVED"
    And the guard state matches the latest ledger state
    When the protected target is atomically acquired
    Then the protected target state should be "RUNNING_UNEXPOSED"
    And the acquiring instance ID should be recorded
    And a matching "RUNNING_UNEXPOSED" ledger event should exist

  Scenario: Two concurrent requests cannot acquire the same protected target
    Given a protected target is in state "RESERVED"
    When two validation requests attempt to acquire it concurrently
    Then exactly one request should acquire the target
    And the other request should be refused
    And protected market data should be read by only the successful request

  Scenario: Guard transition and ledger event commit together
    Given a protected target is in state "RESERVED"
    When the ledger event cannot be persisted during acquisition
    Then the guard should remain in state "RESERVED"
    And no "RUNNING_UNEXPOSED" transition should be committed

  Scenario: A stale unexposed run is recovered after restart
    Given a protected target is in state "RUNNING_UNEXPOSED"
    And the run belongs to an earlier application instance
    And no protected results were exposed
    When startup recovery runs
    Then an "ABORTED_RUN" ledger event should be recorded
    And the protected target should return to state "RESERVED"

  Scenario: Exposed state already consumes the protected attempt
    Given a protected target is in state "EXPOSED"
    When another validation attempt is requested
    Then the new attempt should be refused
    And the protected target should remain in state "EXPOSED"

  Scenario: Spent state permanently blocks another protected attempt
    Given a protected target is in state "SPENT"
    When another validation attempt is requested
    Then the new attempt should be refused
    And the protected target should remain in state "SPENT"

  Scenario: Guard and ledger disagreement refuses protected execution
    Given the guard state is "RESERVED"
    And the latest ledger state is "EXPOSED"
    When a protected run is requested
    Then the protected run should be refused
    And the refusal reason should be "GOVERNANCE_INTEGRITY_MISMATCH"
    And protected market data should not be read

  Scenario: EXPOSED must be durable before results become visible
    Given a protected run is in state "RUNNING_UNEXPOSED"
    And protected results have been computed internally
    When the system prepares to expose result-bearing information
    Then state "EXPOSED" should be persisted first
    And only after that should protected results become visible

  Scenario: Failed EXPOSED persistence destroys computed results
    Given a protected run is in state "RUNNING_UNEXPOSED"
    And protected results have been computed internally
    When persisting state "EXPOSED" fails
    Then the computed protected results should be discarded
    And no result-bearing information should be logged
    And no protected results should be returned
    And the run should be recorded as aborted

  Scenario: Result-bearing logs are forbidden before exposure
    Given a protected run is in state "RUNNING_UNEXPOSED"
    When operational progress is logged
    Then run identifiers and non-result progress may be logged
    But trades should not be logged
    And profit and loss should not be logged
    And expectancy should not be logged
    And drawdown should not be logged
    And governed PASS or FAIL should not be logged

  Scenario: Exposed incomplete run can be recomputed only identically
    Given a protected run is in state "EXPOSED"
    And its verdict was not persisted
    And the recovery uses the same run ID
    And the recovery uses the same configuration hash
    And the recovery uses the same market-data fingerprint
    When deterministic recovery is requested
    Then recovery should be allowed for the existing run
    And no new protected attempt should be created

  Scenario Outline: Exposed incomplete recovery refuses changed inputs
    Given a protected run is in state "EXPOSED"
    And its verdict was not persisted
    And the recovery run ID match is <runIdMatch>
    And the recovery configuration hash match is <configurationMatch>
    And the recovery market-data fingerprint match is <dataMatch>
    When deterministic recovery is requested
    Then recovery should be refused

    Examples:
      | runIdMatch | configurationMatch | dataMatch |
      | false      | true               | true      |
      | true       | false              | true      |
      | true       | true               | false     |

  Scenario: Completed protected run becomes spent
    Given a protected run is in state "EXPOSED"
    And its governed verdict is ready
    When the verdict is durably persisted
    Then the protected target state should become "SPENT"
    And a matching "SPENT" ledger event should exist

  Scenario: Illegal guard transitions are refused
    Given a protected target is in state "SPENT"
    When a transition to "RESERVED" is attempted
    Then the transition should be refused
    And the protected target should remain in state "SPENT"

  Scenario: Protected guard rows cannot be deleted
    Given a protected target guard exists
    When deletion of the guard is attempted
    Then the deletion should be refused