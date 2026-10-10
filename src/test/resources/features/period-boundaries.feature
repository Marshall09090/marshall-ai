Feature: Governed evaluation period boundaries

  MarshallAI must keep tuning, validation, and holdout periods isolated
  so that positions, entries, and results cannot bleed across protected
  evaluation boundaries.

  Scenario: A governed evaluation period starts with no open positions
    Given a governed evaluation period is about to start
    And a position remained open from the previous period
    When the new evaluation period begins
    Then the new period should start with no open positions
    And no position from the previous period should be inherited

  Scenario: Validation uses 200 tuning bars only for indicator warm-up
    Given the validation period requires 200 warm-up bars
    And exactly 200 completed bars are available immediately before validation
    And those bars belong to the tuning period
    When the validation dataset is prepared
    Then exactly 200 warm-up bars should precede the validation evaluation period
    And the warm-up bars should not count as validation bars
    And no trade from the warm-up bars should be included in validation results

  Scenario: The final bar of a period cannot generate a new entry
    Given the final bar of a governed evaluation period produces a BUY signal
    When entry eligibility is evaluated for that final bar
    Then no new entry order should be created
    And the signal should be recorded as "SKIPPED_PERIOD_BOUNDARY"

  Scenario: An open trade is force-closed at the end of the period
    Given a long position remains open on the final bar of a governed period
    And the final bar closes at 100.00
    When the governed period ends
    Then the trade should be force-closed using the final bar close as the execution reference
    And the period-boundary exit reason should be "PERIOD_END"

  Scenario: Period-end liquidation receives standard adverse auction slippage
    Given a long position remains open on the final bar of a governed period
    And the final bar closes at 100.00
    And auction-fill slippage is 2 basis points
    When the period-end liquidation is modeled
    Then the period-end execution reference price should be 100.00
    And the modeled period-end SELL fill should be 99.98
    And the period-boundary exit reason should be "PERIOD_END"

  Scenario: Period-end trades remain in governed portfolio results
    Given a trade exits with reason "PERIOD_END"
    When governed portfolio results are calculated
    Then the trade should be included in the portfolio equity curve
    And the trade should be included in maximum drawdown
    And the trade should be included in primary expectancy

  Scenario: Expectancy is also reported without period-end trades
    Given completed governed trades include ordinary exits and "PERIOD_END" exits
    When expectancy metrics are calculated
    Then expectancy including period-end trades should be reported
    And expectancy excluding period-end trades should be reported
    And period-end trade count should be reported

  Scenario: A large period-end expectancy difference is preserved as a diagnostic
    Given expectancy including period-end trades is 0.40 R
    And expectancy excluding period-end trades is 0.80 R
    When period-boundary metrics are recorded
    Then both expectancy values should be preserved
    And the difference should not be hidden by averaging them

  Scenario: A tuning trade cannot continue into validation
    Given a position remains open at the end of the tuning period
    When the tuning period ends
    Then the position should be force-closed with reason "PERIOD_END"
    And validation should begin with no open position
    And the tuning trade should not continue into validation

  Scenario: A validation trade cannot continue into holdout
    Given a position remains open at the end of the validation period
    When the validation period ends
    Then the position should be force-closed with reason "PERIOD_END"
    And holdout should begin with no open position
    And the validation trade should not continue into holdout

  Scenario: A period-end exit is counted in the period where the trade was opened
    Given a trade was opened during validation
    And the trade remains open through the final validation bar
    When the validation period ends
    Then the "PERIOD_END" exit should belong to validation
    And its profit or loss should be included in validation results
    And no part of the trade should be attributed to holdout

  Scenario: No next-period opening price is used for period-end liquidation
    Given a position remains open on the final bar of validation
    And the final validation close is 100.00
    And the first holdout open is 105.00
    When the validation position is force-closed
    Then the execution reference should be the final validation close of 100.00
    And the first holdout open should not affect the validation exit

  Scenario: Period-end liquidation occurs after final-bar stop evaluation
    Given a long position has an active stop of 90.00 on the final bar
    And the final bar opens at 100.00
    And the final bar low is 89.00
    And the final bar closes at 95.00
    When final-bar exit priority is evaluated
    Then the stop should execute before period-end liquidation
    And the period-boundary exit reason should be "STOP"
    And no second "PERIOD_END" exit should be created

  Scenario: A surviving final-bar position receives exactly one period-end exit
    Given a long position has an active stop of 90.00 on the final bar
    And the final bar opens at 100.00
    And the final bar low is 95.00
    And the final bar closes at 98.00
    When final-bar exit priority is evaluated
    Then the stop should not trigger
    And exactly one period-end exit should be created
    And the period-boundary exit reason should be "PERIOD_END"

  Scenario: Period boundaries are deterministic across repeated research runs
    Given the same tuning dataset
    And the same governed configuration
    And the same period boundary dates
    When the tuning research run is repeated
    Then the same trades should be closed at the same period boundary
    And the same final-bar entry signals should be skipped
