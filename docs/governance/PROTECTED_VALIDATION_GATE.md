# Protected Validation Readiness Gate

## Current status

Workload 5 functional and acceptance implementation is complete.

Tuning-data research is permitted.

The first real protected VALIDATION or HOLDOUT run remains blocked until
every OPEN item in this document is implemented and passing in the full
repository regression.

## Verified Workload 5 foundations

### Real PostgreSQL governance enforcement — CLOSED

Protected run governance integration tests use PostgreSQL through
Testcontainers and production Flyway migrations.

Relevant evidence includes:

- ProtectedRunGuardRepositoryTest
- exposure-state-machine.feature
- ProtectedRunExposureStepDefinitions

Covered behavior includes:

- atomic protected-target acquisition
- transaction rollback when the ledger insert fails
- guard/ledger consistency
- legal state-transition enforcement
- append-only ledger enforcement
- protected guard delete refusal
- governed PASS/FAIL persistence
- stale RUNNING_UNEXPOSED recovery
- deterministic EXPOSED recovery

### MarketDataProvider protection facade — CLOSED

MarketDataProvider is the public consumer interface.

ProtectedMarketDataProvider is the Spring @Primary implementation.

Real and simulated implementations are internal MarketDataProviderSource
delegates behind that facade.

Provider classification fails closed:

- REAL is the default provider type.
- SIMULATED must be declared explicitly.
- protected-date enforcement is derived from provider type.
- simulated-data exemption is derived from provider type rather than a
  caller-supplied flag.

Current wiring tests:

- springInjectsProtectedWrapperAsMarketDataProvider
- defaultProfileUsesSimulatedSourceBehindWrapper
- simulatedSourceCannotQualifyForGovernedVerdict

## Protected-run start gate

A real protected run must not acquire its guard unless all of these checks
have succeeded:

1. Durable PostgreSQL governance storage is active.
2. The governed-mode PostgreSQL advisory lock is held.
3. Guard and latest ledger state are consistent.
4. Ledger hash-chain integrity is valid.
5. The latest required external ledger anchor agrees with PostgreSQL.
6. A PostgreSQL backup completed successfully.
7. The backup exists and is non-empty.
8. The backup SHA-256 checksum was recorded.
9. The run's data-source type came from MarketDataProvider.providerType().
10. Governed PASS/FAIL is possible only when the recorded type is REAL.

## OPEN — Simulated governed-verdict prohibition

Existing provider-level policy:

- REAL allows governed verdicts.
- SIMULATED does not allow governed verdicts.
- MarketDataProviderType.requireGovernedVerdictAllowed() refuses SIMULATED.

Still required:

- record REAL or SIMULATED durably on protected-run acquisition
- derive it from the provider itself
- refuse SIMULATED in the governed verdict production API
- independently refuse SIMULATED governed verdicts in PostgreSQL
- integration-test application-layer refusal
- integration-test direct database refusal

This item blocks the first real protected validation run.

## OPEN — Explicit hash-chain tamper test

Existing implementation:

- every protected ledger event stores event_hash
- every event after the initial event stores previous_event_hash
- PostgreSQL validates previous_event_hash against the latest ledger hash

Still required:

- deliberately insert a ledger event containing a wrong previous_event_hash
- assert that PostgreSQL rejects the insert/transaction

This item blocks the first real protected validation run.

## OPEN — External ledger anchor

Required behavior:

After each successfully completed protected run, record outside PostgreSQL:

- run ID
- latest ledger event hash
- UTC anchor timestamp

The anchor must be written to a Git-tracked append-only governance file.

Before the next protected run starts:

- read the latest applicable committed anchor
- obtain the matching latest completed protected-run ledger hash
- compare the two
- refuse the protected run if they differ or required anchor evidence is absent

The external anchor must be written and committed after each completed
protected run.

This item blocks the first real protected validation run.

## OPEN — PostgreSQL backup preflight

Required behavior:

Before protected guard acquisition:

- run pg_dump
- require a successful exit status
- require the resulting backup file to exist
- require the backup file to be non-empty
- calculate SHA-256
- record backup path
- record backup checksum
- record backup completion timestamp

If any backup operation fails, protected guard acquisition must not occur.

This item blocks the first real protected validation run.

## Required execution order

Protected execution must follow this order:

    durable-store check
            |
    governed-mode advisory lock
            |
    guard / ledger integrity check
            |
    hash-chain integrity check
            |
    external-anchor verification
            |
    pg_dump backup
            |
    verify backup exists and is non-empty
            |
    calculate and record backup SHA-256
            |
    acquire protected guard
            |
    protected market-data access
            |
    persist EXPOSED
            |
    REAL-only governed PASS / FAIL
            |
    persist SPENT
            |
    write external ledger anchor
            |
    commit external anchor

The protected-run orchestration path must make acquireReservedTarget()
unreachable until all required preflight checks succeed.

## Final authorization gate

Before the first real protected validation run, record for each requirement:

- production file
- migration
- test class
- exact test method
- most recent passing regression

The final regression command is:

    ./gradlew test --rerun-tasks

A real protected validation or holdout run remains prohibited unless that
command succeeds with every gate test enabled.