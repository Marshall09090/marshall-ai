# Workload 02 — Persistence & Strategy/Configuration Versioning

## Status

**IN PROGRESS 🚧**

This workload establishes MarshallAI's persistent, immutable audit trail.

The persistence layer is designed to make analysis, backtesting, paper trading, and future live-trading activity reproducible and auditable.

Workload 02 must be completed before the backtesting engine is built so that backtest results are traceable from the first run.

---

## 1. Objective

MarshallAI must be able to answer:

> Exactly which code, strategy, configuration, market data, evidence, risk rules, execution assumptions, and acceptance criteria produced this decision or result?

Persistence is therefore not limited to storing backtest results.

MarshallAI will maintain an append-only decision ledger covering:

- Ad hoc analysis
- Backtests
- Paper trading
- Future live trading

The primary hierarchy is:

```text
Run
 ↓
Decisions
 ↓
Future Orders
 ↓
Future Fills
```

Orders and fills are not implemented in this workload, but the persistence architecture must allow them to be added later without redesigning the run and decision model.

---

## 2. Core Architectural Principles

Workload 02 follows these rules:

1. Historical records are append-only.
2. Persisted runs cannot be rewritten to reflect newer logic.
3. Persisted decisions cannot be rewritten.
4. Rejected decisions are persisted.
5. Configuration identity is derived from configuration content.
6. Market datasets are fingerprinted.
7. Governed validation runs require preregistered acceptance criteria.
8. PASS or FAIL is calculated by MarshallAI.
9. PASS or FAIL is never manually entered.
10. Build identity includes the Git commit hash.
11. The persistence model is multi-asset.
12. Secrets and credentials are never persisted in snapshots.

---

## 3. Persistence Architecture

The target architecture is:

```text
MarshallAI
    ↓
Run Service
    ↓
Run Validation
    ↓
Configuration Snapshot + Hash
    ↓
Criteria Snapshot
    ↓
Market Data Fingerprint
    ↓
Persistent Run
    ↓
Decision Evaluation
    ↓
Persistent Decision Ledger
    ↓
Future:
Order Intent
    ↓
Order
    ↓
Fill
```

Persistence must not change the behavior of the trading-analysis engine.

It records what happened and the context under which it happened.

---

## 4. Run Types

MarshallAI will use one run model across the platform.

Initial run types:

```text
AD_HOC_ANALYSIS
BACKTEST
PAPER
LIVE
```

### AD_HOC_ANALYSIS

Used for individual analysis requests such as:

```text
/api/trading/analyze-symbol
```

This allows the current trading-analysis pipeline to exercise persistence before the backtesting engine exists.

Ad hoc analysis does not require backtest acceptance criteria.

### BACKTEST

Represents a historical strategy evaluation.

Governed validation backtests require preregistered acceptance criteria.

### PAPER

Represents paper-trading operation.

Paper validation criteria will eventually be preregistered before paper results are used for a paper-to-live decision.

### LIVE

Reserved for future live trading.

Creating a LIVE run type does not enable live trading.

Live order execution remains outside the scope of this workload.

---

## 5. Run Record

A persisted run should contain sufficient information to reconstruct the context in which it occurred.

The run model will support fields such as:

```text
Run ID
Run Type
Created Timestamp
Started Timestamp
Completed Timestamp

Application Version
Git Commit Hash

Strategy Version
Configuration Version / Hash
Configuration Snapshot

Criteria Version
Criteria Registration Timestamp
Criteria Snapshot

Asset Type
Symbol / Currency Pair
Timeframe

Market Data Provider
Market Data Feed
Adjustment Method
Data Start
Data End
Data Fetch Timestamp
Market Data Fingerprint

Execution Model
Fee Model
Slippage Model

Run Status
Computed PASS / FAIL
Failure Criteria
```

Additional metrics will be added as later workloads introduce backtesting and paper trading.

---

## 6. Decision Ledger

Runs alone are insufficient for auditability.

Every material trading decision inside a run must be capable of being persisted.

This includes:

- BUY
- SELL
- HOLD
- Approved decisions
- Blocked decisions
- Event-risk rejections
- Confidence-threshold rejections
- Position-sizing failures
- Future runtime-safety rejections

A decision record should support:

```text
Decision ID
Run ID
Decision Timestamp

Symbol / Pair
Asset Type

Detected Patterns

Technical Bullish Score
Technical Bearish Score
Technical Confidence

Combined Bullish Score
Combined Bearish Score
Combined Confidence

Event Risk Level
Event Trade Blocked

Requested / Candidate Decision
Final Decision

Trade Approval Status
Rejection Gate
Rejection Reason

Calculated Quantity
Position Value
Risk Amount
```

A rejected decision is not discarded.

It is part of the audit history.

Example:

```text
Candidate Decision:
BUY

Confidence:
79%

Required Confidence:
80%

Final Decision:
HOLD

Approval:
BLOCKED

Rejection Gate:
SIGNAL_CONFIDENCE

Reason:
Minimum trading confidence not satisfied
```

---

## 7. Append-Only Historical Records

Persisted historical records are immutable.

The application must not modify an existing run because:

- strategy logic changed
- configuration changed
- thresholds changed
- market data changed
- a bug was fixed
- newer analysis produced a different result

Instead:

```text
Old Run
    remains unchanged

New Logic / Configuration
    ↓
New Run
```

This preserves historical truth.

Deletion and mutation of audit records must not be part of normal application behavior.

---

## 8. Strategy Versioning

Strategy identity must be recorded independently from application/build identity.

Example:

```text
Strategy:
marshall-core

Strategy Version:
1
```

Later strategy changes create another strategy version rather than modifying historical runs.

Strategy versioning allows MarshallAI to distinguish changes to trading logic from changes to infrastructure or application code.

---

## 9. Configuration Snapshots

A configuration version must represent its actual contents.

A run stores an immutable configuration snapshot containing governed settings such as:

```text
Indicator periods
RSI thresholds
MACD parameters
EMA parameters
ADX parameters
Signal weights
Minimum confidence threshold
Risk percentage
Maximum position exposure
Event-risk settings
Execution assumptions
```

The exact schema will evolve as MarshallAI gains capabilities.

Sensitive credentials must never be included.

---

## 10. Configuration Fingerprinting

Configuration identity will be content-based.

MarshallAI will:

1. Build a canonical configuration representation.
2. Serialize it deterministically.
3. Calculate a SHA-256 hash.
4. Use that hash as the configuration fingerprint/version identity.

Conceptually:

```text
Canonical Configuration
        ↓
Deterministic Serialization
        ↓
SHA-256
        ↓
Configuration Fingerprint
```

Required behavior:

```text
Same configuration
    ↓
Same fingerprint
```

and:

```text
Change one governed setting
    ↓
Different fingerprint
```

This prevents two materially different configurations from accidentally sharing the same configuration identity.

---

## 11. Market Data Fingerprinting

A historical request alone does not prove which candles a run actually used.

Adjusted historical data can change when providers apply later corporate-action information or corrections.

MarshallAI must therefore fingerprint the actual candle dataset used by the run.

The market-data audit context should include:

```text
Provider
Feed
Symbol
Asset Type
Timeframe
Adjustment
Requested Start
Requested End
Fetch Timestamp
Candle Count
Candle Fingerprint
```

The fingerprint should be calculated deterministically from the canonical ordered candle data.

Conceptually:

```text
Oldest → Newest Candles
        ↓
Canonical Candle Representation
        ↓
SHA-256
        ↓
Dataset Fingerprint
```

This allows MarshallAI to determine whether two runs actually observed identical market data.

---

## 12. Build Identity

Application version alone is insufficient for exact source-code traceability.

Runs will record:

```text
Application Version
Git Commit Hash
```

The Git commit hash identifies the source-code checkpoint responsible for the run.

This will later allow a result to be associated with:

```text
Code
+
Strategy
+
Configuration
+
Criteria
+
Market Data
```

---

## 13. Acceptance Criteria Registration

Governed validation must not be judged using thresholds invented after the results are known.

Acceptance criteria must therefore be registered before a governed validation run begins.

Conceptually:

```text
Criteria Registered
        ↓
Criteria Version Created
        ↓
Run Starts
        ↓
Results Generated
        ↓
Criteria Evaluator
        ↓
PASS / FAIL
```

The run stores an immutable snapshot of the criteria it was evaluated against.

---

## 14. Criteria Enforcement

For governed validation runs:

```text
No valid preregistered criteria
        ↓
RUN REFUSED
```

The system must not silently start the validation and attach criteria afterward.

AD_HOC_ANALYSIS is exempt from backtest acceptance-criteria requirements.

This allows today's symbol-analysis requests to be persisted before the backtester exists.

The exact governance requirements for PAPER and LIVE runs will be expanded before those execution modes are enabled.

---

## 15. PASS / FAIL Evaluation

PASS or FAIL must be computed by MarshallAI.

It must never be supplied manually by the caller.

Future backtest criteria may include metrics such as:

```text
Minimum number of trades
Minimum expectancy
Maximum drawdown
Minimum return
Benchmark-relative performance
Maximum acceptable costs
Other preregistered requirements
```

Conceptually:

```text
Run Metrics
    +
Registered Criteria Snapshot
        ↓
CriteriaEvaluator
        ↓
PASS / FAIL
```

The evaluator should also record which criteria failed.

---

## 16. Multi-Asset Design

Persistence must support multiple asset classes from the beginning.

Examples:

```text
STOCK
FOREX
```

Future asset classes can be introduced without replacing the persistence architecture.

A Forex record may eventually include additional context such as:

```text
Currency Pair
Quote Currency
Account Currency
Pip Size
Lot Size
Leverage
Margin
Spread
Session
Rollover / Swap Assumptions
```

Forex-specific trading behavior is not implemented by this workload.

The persistence model simply avoids making stock-only assumptions that would prevent Forex expansion.

---

## 17. Database Technology

Workload 02 uses:

```text
PostgreSQL
Spring Data JPA
Flyway
JSON / JSONB
Testcontainers
Cucumber
```

### PostgreSQL

PostgreSQL is the persistence database.

### Flyway

All database schema changes must be version-controlled through Flyway migrations.

The application must not rely on automatic schema mutation as the long-term schema-management strategy.

### JSONB

Immutable snapshots such as configuration and criteria snapshots can use PostgreSQL JSONB where appropriate.

Structured relational columns should still be used for fields that require direct querying, constraints, relationships, or indexing.

### Testcontainers

Persistence integration and Cucumber tests will run against real PostgreSQL through Testcontainers.

An in-memory database such as H2 will not be used as the authoritative substitute for PostgreSQL persistence behavior.

This is particularly important for:

- JSONB
- PostgreSQL constraints
- PostgreSQL indexes
- Flyway migrations
- SQL behavior

---

## 18. Database Migration Policy

Database schema changes must be represented as migrations.

Initial structure will use:

```text
src/main/resources/db/migration/
```

with files such as:

```text
V1__create_strategy_and_configuration_tables.sql
V2__create_run_tables.sql
V3__create_decision_ledger.sql
```

Exact migration boundaries may change during implementation.

Already-applied migrations must not be silently rewritten in deployed environments.

New schema changes should create new migrations.

---

## 19. Security

Persistence must never contain:

```text
Alpaca API keys
Alpaca secret keys
Database passwords
Authentication tokens
Private credentials
Environment secrets
```

Configuration snapshots contain trading configuration, not secrets.

Secrets remain external to persisted strategy/run snapshots.

---

## 20. Initial Cucumber Acceptance Scenarios

Implementation begins with RED acceptance tests.

### Scenario 1 — Persisted runs are immutable

```gherkin
Scenario: A persisted run cannot be modified
  Given a MarshallAI run has been persisted
  When an attempt is made to modify the persisted run
  Then the modification should be rejected
  And the original persisted run should remain unchanged
```

### Scenario 2 — Configuration changes create new identity

```gherkin
Scenario: Changing a governed setting produces a new configuration version
  Given a MarshallAI configuration has been fingerprinted
  When a governed configuration setting is changed
  Then the configuration fingerprint should be different
```

### Scenario 3 — Rejected decisions are auditable

```gherkin
Scenario: A rejected decision is persisted with its reason
  Given a MarshallAI run is active
  And a trading decision is rejected by a safety gate
  When the decision is persisted
  Then the rejected decision should exist in the decision ledger
  And the rejection gate should be recorded
  And the rejection reason should be recorded
```

### Scenario 4 — Preregistered criteria are mandatory

```gherkin
Scenario: A governed run without registered criteria is refused
  Given no acceptance criteria are registered for a governed validation run
  When MarshallAI attempts to start the run
  Then the run should be refused
```

### Scenario 5 — Market data is fingerprinted

```gherkin
Scenario: A persisted run records the market data fingerprint
  Given historical candles were used by a MarshallAI run
  When the run is persisted
  Then the market data fingerprint should be recorded
  And the market data fetch timestamp should be recorded
```

---

## 21. Additional Required Regression Coverage

Workload 02 should also verify:

```text
Identical configuration → identical fingerprint

Different configuration → different fingerprint

Identical ordered candle dataset → identical fingerprint

Changed candle dataset → different fingerprint

Rejected decision → persisted

Approved decision → persisted

Historical run → cannot be modified

Historical decision → cannot be modified

Criteria registered before run → accepted

Required criteria missing → run refused

PASS / FAIL → calculated by application

Secrets → absent from snapshots
```

---

## 22. Relationship to Workload 01

Workload 01 established the market-data and trading-safety baseline before persistence was introduced.

The baseline includes:

```text
Real Alpaca historical market data
IEX development feed
Completed daily candles
200-candle minimum
Split-adjusted prices
Oldest → newest ordering
In-progress daily candle exclusion
Zero-evidence confidence correction
HOLD non-executable protection
5% maximum position exposure
Cucumber regression coverage
Real TSLA end-to-end verification
```

This means the first persisted MarshallAI records are based on the stabilized analysis pipeline rather than the known defects discovered during Workload 01.

---

## 23. Relationship to Backtesting

The backtesting engine is intentionally built after this workload.

Therefore every future backtest can begin with:

```text
Run ID
Strategy Version
Configuration Fingerprint
Git Commit Hash
Criteria Version
Market Data Fingerprint
```

and finish with:

```text
Decision Ledger
Metrics
Computed PASS / FAIL
Failed Criteria
```

This prevents the project from accumulating untraceable backtest results.

---

## 24. Planned Implementation Sequence

Workload 02 will proceed in this order:

```text
1. Freeze persistence contract in this README

2. Create Cucumber acceptance scenarios

3. Add PostgreSQL persistence dependencies

4. Add Flyway

5. Add Testcontainers PostgreSQL

6. Configure test persistence

7. Create initial Flyway migrations

8. Implement run types

9. Implement strategy versioning

10. Implement canonical configuration snapshots

11. Implement SHA-256 configuration fingerprinting

12. Implement criteria registration

13. Implement governed-run validation

14. Implement market-data fingerprinting

15. Implement persistent run model

16. Implement append-only decision ledger

17. Persist rejected decisions and rejection reasons

18. Implement automatic criteria evaluation foundation

19. Connect AD_HOC_ANALYSIS to the existing analysis pipeline

20. Run persistence/Cucumber integration tests

21. Run complete MarshallAI regression suite

22. Update this README with final implementation details

23. Perform Git checkpoint
```

---

## 25. Definition of Done

Workload 02 is complete only when:

- PostgreSQL persistence is operational.
- Flyway owns schema migrations.
- Testcontainers runs persistence tests against PostgreSQL.
- Run types are implemented.
- Strategy versions are persisted.
- Configuration snapshots are immutable.
- Configuration fingerprints are deterministic.
- Market-data fingerprints are deterministic.
- Market-data fetch context is persisted.
- Runs are append-only.
- Decisions are append-only.
- Rejected decisions are persisted.
- Rejection gate and reason are persisted.
- Governed validation runs require preregistered criteria.
- Criteria snapshots are immutable.
- PASS / FAIL is application-computed.
- Git commit/build identity is captured.
- AD_HOC_ANALYSIS can use the persistence layer.
- Existing Workload 01 behavior remains green.
- New Cucumber scenarios pass.
- Full Gradle test suite passes.
- Documentation reflects the final implementation.
- Git checkpoint is committed and pushed.

---

## 26. Out of Scope

Workload 02 does not implement:

- Backtesting engine
- Strategy optimization
- Walk-forward validation
- Paper broker order submission
- Live broker order submission
- Order management
- Fill reconciliation
- Trade management
- Dashboard
- Runtime scheduler
- Forex execution
- Forex pip/lot sizing
- Economic-calendar integration
- Production database operations architecture

Those capabilities belong to later workloads.

---

## 27. Next Workload

After Workload 02 is complete, the roadmap proceeds to the preregistration of backtesting acceptance criteria and then the backtesting engine.

The persistence foundation created here ensures those future results are versioned, reproducible, attributable, and auditable from their first execution.