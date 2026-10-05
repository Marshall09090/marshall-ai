Feature: Portfolio governance

  MarshallAI must manage portfolio capital deterministically
  so that backtesting, paper trading, and live execution
  use the same portfolio-level safety rules.

  Background:
    Given portfolio governance version "portfolio-governance-v1" is active
    And starting capital is 100000.00
    And leverage is disabled
    And maximum total exposure is 100 percent of equity
    And maximum position exposure is 5 percent of equity
    And auction-fill slippage is 2 basis points
    And stop-fill slippage is 10 basis points
    And broker stock commission is 0.00

  Scenario: Starting capital is fixed in the governed snapshot
    When a governed portfolio is created
    Then portfolio starting capital should be 100000.00
    And the starting capital should be immutable

  Scenario: A pending order reserves cash at its limit price
    Given portfolio equity is 100000.00
    And available cash is 100000.00
    And a pending BUY order has quantity 40
    And its limit price is 125.00
    When the pending order is accepted
    Then reserved cash should be 5000.00
    And available unreserved cash should be 95000.00

  Scenario: A cancelled pending order releases reserved cash
    Given available cash is 100000.00
    And a pending BUY order reserves 5000.00
    When the pending order is cancelled
    Then reserved cash should be 0.00
    And available unreserved cash should be 100000.00

  Scenario: Filled and pending exposure cannot exceed portfolio equity
    Given portfolio equity is 100000.00
    And existing position exposure is 95000.00
    And pending orders reserve 5000.00
    When another BUY requiring 1000.00 of exposure is evaluated
    Then the BUY should be rejected for a capital constraint
    And total committed exposure should not exceed 100000.00

  Scenario: Portfolio does not use leverage
    Given portfolio equity is 100000.00
    And total committed exposure is 100000.00
    When another BUY requiring 1.00 of exposure is evaluated
    Then the BUY should be rejected for a capital constraint
    And borrowed capital should remain 0.00

  Scenario: Position exposure cannot exceed five percent of equity
    Given portfolio equity is 100000.00
    When a proposed position has exposure of 5001.00
    Then the proposed position should be rejected
    And the rejection reason should be "POSITION_EXPOSURE_LIMIT"

  Scenario: Same-day BUY candidates are ordered by confidence first
    Given the following same-day BUY candidates:
      | symbol | confidence |
      | TSLA   | 88         |
      | AAPL   | 92         |
      | MSFT   | 90         |
    When portfolio candidates are ordered
    Then the candidate order should be:
      | symbol |
      | AAPL   |
      | MSFT   |
      | TSLA   |

  Scenario: Equal-confidence BUY candidates are ordered alphabetically
    Given the following same-day BUY candidates:
      | symbol | confidence |
      | TSLA   | 92         |
      | MSFT   | 92         |
      | AAPL   | 92         |
    When portfolio candidates are ordered
    Then the candidate order should be:
      | symbol |
      | AAPL   |
      | MSFT   |
      | TSLA   |

  Scenario: Duplicate BUY is ignored while the symbol is held
    Given symbol "TSLA" is already held
    When a new BUY signal is received for "TSLA"
    Then no new BUY order should be created
    And the duplicate BUY should be recorded as ignored

  Scenario: Duplicate BUY is ignored while a BUY order is pending
    Given symbol "TSLA" already has a pending BUY order
    When a new BUY signal is received for "TSLA"
    Then no new BUY order should be created
    And the duplicate BUY should be recorded as ignored

  Scenario: Re-entry is allowed after a position exits
    Given symbol "TSLA" was previously held
    And the "TSLA" position has exited
    And no BUY order for "TSLA" is pending
    When a new BUY signal is received for "TSLA"
    Then a new BUY order may be created

  Scenario: Capital-constrained signal remains a valid research signal
    Given a valid BUY signal cannot be accepted because capital is unavailable
    When the portfolio rejects the executable entry
    Then the portfolio outcome should be "SKIPPED_CAPITAL_CONSTRAINT"
    And the signal should not be classified as a failed signal
    And the standalone signal outcome should remain available for research

  Scenario: Scheduled exits use market-on-open execution
    Given a long position has a scheduled exit
    When the exit order is prepared
    Then the order type should be "MARKET"
    And the time in force should be "OPG"

  Scenario: Auction fills apply two basis points of slippage
    Given an auction reference price of 100.00
    When BUY auction slippage is applied
    Then the modeled BUY fill price should be 100.02

  Scenario: Stop fills apply ten basis points of slippage
    Given a stop execution reference price of 100.00
    When SELL stop slippage is applied
    Then the modeled SELL fill price should be 99.90

  Scenario: Broker commission is zero but regulatory fees remain separate
    Given a stock trade has gross proceeds of 10000.00
    When transaction costs are calculated
    Then broker commission should be 0.00
    And regulatory fees should be calculated separately
    And the regulatory fee model version should be recorded

  Scenario: Benchmark uses the same governed universe period and capital
    Given a governed strategy run has a fixed universe
    And a fixed evaluation period
    And starting capital of 100000.00
    When the buy-and-hold benchmark is created
    Then the benchmark should use the same universe
    And the benchmark should use the same evaluation period
    And the benchmark should use starting capital of 100000.00

  Scenario: Missing dividend data is recorded as a benchmark limitation
    Given the benchmark uses price-only market data
    When benchmark metadata is recorded
    Then the limitation "BENCHMARK_EXCLUDES_DIVIDENDS" should be recorded

  Scenario: Portfolio drawdown is measured on the combined portfolio
    Given multiple symbols are held in the governed portfolio
    When maximum drawdown is calculated
    Then drawdown should be measured from the combined portfolio equity curve
    And drawdown should not be calculated independently per symbol

  Scenario: BUY auction slippage can never exceed the entry limit
    Given a BUY limit price of 100.00
    And an auction reference price of 100.00
    When BUY auction slippage is applied to the limit order
    Then the modeled BUY fill price should be 100.00
    And reserved cash should not exceed the limit-price reservation

  Scenario: Same-open exits cannot fund new opening entries
    Given capital available before the opening auction is 5000.00
    And a position is scheduled to exit at the same opening auction
    And expected same-open exit proceeds are 10000.00
    When a new opening BUY requiring 6000.00 is evaluated
    Then the exit proceeds should not be available to the new BUY
    And the BUY should be rejected for a capital constraint
    And the governed account type should be "REG_T_MARGIN"
    And strategy leverage should remain disabled

  Scenario: Appreciation above five percent does not force a trim
    Given a position satisfied the five percent exposure limit at entry
    And the position appreciates above five percent of portfolio equity
    When portfolio exposure is marked to market
    Then no automatic trim should be scheduled

  Scenario: A gap-through stop receives stop slippage after the gap
    Given the portfolio gap-through active stop price is 90.00
    And the portfolio gap-through opening price is 85.00
    When SELL stop slippage is applied to the gap-through execution
    Then the stop execution reference price should be 85.00
    And the modeled SELL fill price should be 84.915

  Scenario: A capital-constrained signal remains in the signal-outcome ledger
    Given a valid BUY signal is skipped for a capital constraint
    When research outcomes are recorded
    Then the execution outcome should be "SKIPPED_CAPITAL_CONSTRAINT"
    And a standalone trade outcome should still be recorded
    And the signal should remain eligible for confidence-band analysis

  Scenario: Buy-and-hold benchmark pays initial auction slippage and is not rebalanced
    Given an equal-weight buy-and-hold benchmark is created
    When the benchmark enters its initial positions
    Then initial purchases should use 2 basis points of BUY auction slippage
    And the benchmark should not rebalance after initial purchase