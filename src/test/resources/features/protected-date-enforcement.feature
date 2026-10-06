Feature: Protected date enforcement at the market-data boundary

  MarshallAI must protect validation and holdout market data
  at the market-data layer so that controllers, services,
  ad hoc analysis, and future code paths cannot bypass
  protected-period governance.

  Simulated test data is exempt from protected-date enforcement.

  Scenario: Ad hoc analysis cannot read validation-period market data
    Given a validation period is protected
    And an ad hoc analysis uses real market data
    When the analysis requests bars overlapping the validation period
    Then the market-data boundary should refuse the request
    And the refusal reason should be "PROTECTED_VALIDATION_PERIOD"
    And no protected validation bars should be returned

  Scenario: Ad hoc analysis cannot read holdout-period market data
    Given a holdout period is protected
    And an ad hoc analysis uses real market data
    When the analysis requests bars overlapping the holdout period
    Then the market-data boundary should refuse the request
    And the refusal reason should be "PROTECTED_HOLDOUT_PERIOD"
    And no protected holdout bars should be returned

  Scenario: Protection is enforced below the controller layer
    Given a validation period is protected
    And a service calls the real market-data provider directly
    When the service requests protected validation bars
    Then the market-data boundary should refuse the request
    And protected validation bars should not be returned

  Scenario: Governed validation may read its authorized validation period
    Given a validation period is protected
    And a governed validation run has acquired that protected target
    When the governed run requests its authorized validation bars
    Then the market-data boundary should allow the request
    And only the authorized protected period should be returned

  Scenario: Validation warm-up may read tuning-period history
    Given a governed validation run requires 200 warm-up bars
    And those warm-up bars are inside the tuning period
    When validation market data is prepared
    Then the 200 tuning-period warm-up bars should be allowed
    And the warm-up bars should be marked as "WARM_UP_ONLY"
    And the validation evaluation period should remain unchanged

  Scenario: Warm-up bars cannot create validation trades
    Given validation includes tuning-period warm-up bars
    When signals are calculated across the prepared dataset
    Then signals from warm-up-only bars should not create validation trades
    And only signals inside the validation evaluation period should be eligible for validation

  Scenario: Warm-up access cannot extend beyond the tuning period
    Given a governed validation run requires 200 warm-up bars
    And the requested warm-up range reaches outside the registered tuning period
    When validation market data is prepared
    Then the market-data boundary should refuse the request
    And the refusal reason should be "WARM_UP_OUTSIDE_TUNING_PERIOD"

  Scenario: Holdout access requires a separate explicit authorization
    Given a validation run is authorized
    And a holdout period is protected
    When the validation run requests holdout market data
    Then the market-data boundary should refuse the request
    And the refusal reason should be "HOLDOUT_AUTHORIZATION_REQUIRED"

  Scenario: Explicit holdout authorization is recorded separately
    Given a holdout period is protected
    And the holdout has not been authorized
    When an explicit holdout authorization is granted
    Then a separate holdout authorization record should be created
    And the authorization should identify the strategy version
    And the authorization should identify the protected holdout period
    And the authorization should have its own authorization timestamp

  Scenario: Authorized holdout run may read only its holdout period
    Given a holdout period is protected
    And an explicit holdout authorization exists
    And the governed holdout run has acquired that protected target
    When the holdout run requests its authorized market data
    Then the market-data boundary should allow the request
    And only the authorized holdout period should be returned

  Scenario: Simulated provider is exempt from protected-date enforcement
    Given validation and holdout periods are protected
    And the simulated market-data provider is active
    When simulated bars overlap protected dates
    Then the simulated market-data request should be allowed
    And no protected real-market-data exposure should be recorded

  Scenario: Real market data cannot bypass protection through another entry point
    Given a validation period is protected
    And real market data is requested outside the governed run path
    When any market-data entry point requests overlapping validation bars
    Then the request should be refused before bars are returned
    And the refusal reason should be "PROTECTED_VALIDATION_PERIOD"

  Scenario: Planned live capital sensitivity uses tuning data only
    Given the governed research baseline capital is 100000.00
    And planned controlled-live capital is 10000.00
    And the tuning period is available
    When capital sensitivity is evaluated
    Then only tuning-period market data should be used
    And protected validation data should not be exposed
    And protected holdout data should not be exposed
    And zero-quantity skip rate should be recorded
    And the run should be recorded as "RESEARCH"
    And no governed PASS or FAIL verdict should be produced

  Scenario: Capital sensitivity cannot be run against protected validation data
    Given planned controlled-live capital is 10000.00
    And the validation period is protected
    When capital sensitivity requests validation-period market data
    Then the market-data boundary should refuse the request
    And the refusal reason should be "PROTECTED_VALIDATION_PERIOD"
    And no governed verdict should be produced