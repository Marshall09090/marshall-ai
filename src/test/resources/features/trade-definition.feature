Feature: Trade definition and execution contract

  MarshallAI must use a deterministic and versioned trade definition
  so that research, backtesting, paper trading, and later execution
  follow the same trade mechanics.

  Background:
    Given trade definition version "trade-definition-v1" is active
    And the strategy is long-only
    And portfolio risk per trade is 1 percent
    And maximum position exposure is 5 percent
    And ATR uses 14 periods with Wilder smoothing
    And the initial stop distance is 2 ATR
    And the entry limit offset is 1 ATR
    And the trailing stop distance is 3 ATR

  Scenario: Freeze the initial stop distance at signal time
    Given completed signal bar t has a close of 100.00
    And ATR on completed signal bar t is 5.00
    When a BUY signal is accepted
    Then the frozen stop distance should be 10.00
    And the entry limit price should be 105.00

  Scenario: Size a trade using frozen risk distance and limit-price exposure
    Given portfolio equity at the signal close is 100000.00
    And ATR on completed signal bar t is 5.00
    And the entry limit price is 105.00
    When the trade quantity is calculated
    Then the risk amount should be 1000.00
    And the risk-based quantity should be 100
    And the exposure-based quantity should be 47
    And the final quantity should be 47

  Scenario: Skip an entry when calculated quantity is zero
    Given portfolio equity at the signal close is 100.00
    And ATR on completed signal bar t is 50.00
    And the entry limit price is 150.00
    When the trade quantity is calculated
    Then the final quantity should be 0
    And the trade should be skipped

  Scenario: Fill an opening-only limit entry
    Given completed signal bar t has a close of 100.00
    And ATR on completed signal bar t is 5.00
    And the entry limit price is 105.00
    And the next opening auction price is 103.00
    When the opening-only BUY order is evaluated
    Then the trade should fill at 103.00
    And the initial stop price should be 93.00

  Scenario: Do not fill above the opening limit
    Given the entry limit price is 105.00
    And the next opening auction price is 106.00
    When the opening-only BUY order is evaluated
    Then the trade should not fill
    And the entry should be skipped

  Scenario: Allow a large gap down to fill
    Given completed signal bar t has a close of 100.00
    And ATR on completed signal bar t is 5.00
    And the entry limit price is 105.00
    And the next opening auction price is 90.00
    When the opening-only BUY order is evaluated
    Then the trade should fill at 90.00
    And the opening gap should be recorded as -2.00 ATR

  Scenario: Freeze the R denominator at entry
    Given a filled trade has quantity 47
    And the frozen stop distance is 10.00
    When the initial R denominator is recorded
    Then the initial R denominator should be 470.00
    And the R denominator should remain immutable

  Scenario: A gap through the active stop can lose more than one R
    Given a long trade was filled at 100.00
    And the active stop price is 90.00
    And the next bar opens at 85.00
    When the stop is evaluated
    Then the trade should exit at 85.00
    And the exit reason should be "STOP"

  Scenario: An intraday stop touch fills at the stop
    Given the active stop price is 90.00
    And the next bar opens at 95.00
    And the next bar low is 89.00
    When the stop is evaluated
    Then the trade should exit at 90.00
    And the exit reason should be "STOP"

  Scenario: Trailing stop uses completed-bar information only
    Given the initial stop price is 90.00
    And the previous active stop price is 90.00
    And the highest high since entry is 115.00
    And ATR on the completed bar is 5.00
    When the trailing stop is recalculated after the bar closes
    Then the candidate trailing stop should be 100.00
    And the next bar active stop should be 100.00
    And the completed bar should still use the previous active stop of 90.00

  Scenario: Trailing stop never moves down
    Given the previous active stop price is 100.00
    And the candidate trailing stop price is 98.00
    When the next active stop is selected
    Then the next bar active stop should remain 100.00

  Scenario: Entry bar counts as holding bar one
    Given a trade is filled on an entry bar
    When holding bars are counted
    Then the entry bar should be holding bar 1

  Scenario: Stale trade exits after the one-time bar 20 check
    Given a trade was filled at 100.00
    And the frozen stop distance is 10.00
    And holding bar 20 closes at 108.00
    When the stale trade check is evaluated at the close of bar 20
    Then unrealized R should be 0.80
    And an exit should be scheduled for the next open

  Scenario: Winning trade continues after the bar 20 check
    Given a trade was filled at 100.00
    And the frozen stop distance is 10.00
    And holding bar 20 closes at 112.00
    When the stale trade check is evaluated at the close of bar 20
    Then unrealized R should be 1.20
    And no stale exit should be scheduled
    And the stale trade check should not run again

  Scenario: SELL closes a long and never opens a short
    Given a long position is currently held
    When a SELL signal is generated
    Then an exit should be scheduled for the next open
    And no short position should be opened

  Scenario: Stop receives attribution priority over a scheduled exit
    Given the active stop price is 90.00
    And a SELL exit is scheduled for the next open
    And the next bar opens at 85.00
    When the scheduled exit is evaluated
    Then the trade should exit at 85.00
    And the exit reason should be "STOP"

  Scenario: Blocked signals remain available for shadow research
    Given a valid signal has confidence below the execution threshold
    When the execution gate blocks the signal
    Then no executable trade should be created
    And a shadow-only signal outcome should be recorded
    And its outcome should be measured using standalone trade R