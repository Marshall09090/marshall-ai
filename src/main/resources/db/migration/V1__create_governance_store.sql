/*
 * MarshallAI
 * Workload 5 - Durable Dataset & Exposure Governance
 *
 * V1 creates:
 *
 * 1. protected_run_guard
 *    - narrowly mutable coordination record
 *    - one reservation per strategy + protected period
 *    - legal state transitions enforced by PostgreSQL
 *    - deletion forbidden
 *
 * 2. protected_run_event_ledger
 *    - append-only audit ledger
 *    - SHA-256 hash-chain fields
 *    - previous ledger state/hash continuity enforced
 *
 * 3. Deferred consistency checks
 *    - a guard state change cannot commit without its
 *      corresponding ledger event
 *    - a ledger state event cannot commit without the
 *      guard reaching the same state
 *
 * The application must still execute each guard transition
 * and matching ledger insert inside one transaction.
 */


/* ============================================================
 * PROTECTED RUN GUARD
 * ============================================================
 */

CREATE TABLE protected_run_guard
(
    id BIGSERIAL PRIMARY KEY,

    strategy_version VARCHAR(128) NOT NULL,

    protected_period_id VARCHAR(128) NOT NULL,

    protected_period_type VARCHAR(32) NOT NULL,

    period_start DATE NOT NULL,

    period_end DATE NOT NULL,

    state VARCHAR(32) NOT NULL DEFAULT 'RESERVED',

    /*
     * Assigned when RESERVED -> RUNNING_UNEXPOSED succeeds.
     */
    run_id UUID,

    /*
     * A fresh UUID generated for every application startup.
     * It identifies which process acquired the run.
     */
    instance_id UUID,

    /*
     * Frozen governed configuration used by this run.
     */
    configuration_hash VARCHAR(128),

    /*
     * Frozen fingerprint of the market data actually used.
     * May remain NULL until exposure is persisted.
     */
    market_data_fingerprint VARCHAR(128),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_protected_run_target
        UNIQUE (
                strategy_version,
                protected_period_id
            ),

    CONSTRAINT ck_protected_period_type
        CHECK (
            protected_period_type IN (
                                      'VALIDATION',
                                      'HOLDOUT'
                )
            ),

    CONSTRAINT ck_protected_run_state
        CHECK (
            state IN (
                      'RESERVED',
                      'RUNNING_UNEXPOSED',
                      'EXPOSED',
                      'SPENT'
                )
            ),

    CONSTRAINT ck_protected_period_dates
        CHECK (
            period_start <= period_end
            )
);


/* ============================================================
 * APPEND-ONLY EVENT LEDGER
 * ============================================================
 */

CREATE TABLE protected_run_event_ledger
(
    sequence_number BIGSERIAL PRIMARY KEY,

    event_id UUID NOT NULL UNIQUE,

    guard_id BIGINT NOT NULL,

    run_id UUID,

    event_type VARCHAR(64) NOT NULL,

    previous_state VARCHAR(32),

    new_state VARCHAR(32) NOT NULL,

    instance_id UUID,

    configuration_hash VARCHAR(128),

    market_data_fingerprint VARCHAR(128),

    /*
     * Hash of the immediately preceding ledger event.
     *
     * NULL only for the first RESERVED event for a target.
     */
    previous_event_hash CHAR(64),

    /*
     * SHA-256 hex digest calculated from the canonical
     * contents of this event plus previous_event_hash.
     */
    event_hash CHAR(64) NOT NULL UNIQUE,

    event_timestamp TIMESTAMPTZ NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_protected_run_event_guard
        FOREIGN KEY (guard_id)
            REFERENCES protected_run_guard (id),

    CONSTRAINT ck_ledger_previous_state
        CHECK (
            previous_state IS NULL
                OR previous_state IN (
                                      'RESERVED',
                                      'RUNNING_UNEXPOSED',
                                      'EXPOSED',
                                      'SPENT'
                )
            ),

    CONSTRAINT ck_ledger_new_state
        CHECK (
            new_state IN (
                          'RESERVED',
                          'RUNNING_UNEXPOSED',
                          'EXPOSED',
                          'SPENT'
                )
            ),

    CONSTRAINT ck_previous_event_hash_format
        CHECK (
            previous_event_hash IS NULL
                OR previous_event_hash ~ '^[0-9a-fA-F]{64}$'
),

    CONSTRAINT ck_event_hash_format
        CHECK (
            event_hash ~ '^[0-9a-fA-F]{64}$'
        )
);


CREATE INDEX idx_protected_run_event_guard_sequence
    ON protected_run_event_ledger (
                                   guard_id,
                                   sequence_number DESC
        );


CREATE INDEX idx_protected_run_event_run_id
    ON protected_run_event_ledger (
                                   run_id
        );


/* ============================================================
 * GUARD STATE TRANSITION ENFORCEMENT
 * ============================================================
 */

CREATE OR REPLACE FUNCTION enforce_protected_guard_transition()
RETURNS TRIGGER
LANGUAGE plpgsql
AS
$$
BEGIN

    /*
     * Every new protected target starts RESERVED.
     */
    IF TG_OP = 'INSERT' THEN

        IF NEW.state <> 'RESERVED' THEN
            RAISE EXCEPTION
                'Protected run guard must be created in RESERVED state';
END IF;

RETURN NEW;
END IF;


    /*
     * V1 has no heartbeat/lease updates.
     *
     * Every guard UPDATE must therefore represent an actual
     * legal state transition.
     */
    IF NEW.state = OLD.state THEN
        RAISE EXCEPTION
            'Protected run guard cannot be updated without a state transition';
END IF;


    /*
     * Legal v1 transitions:
     *
     * RESERVED
     *   -> RUNNING_UNEXPOSED
     *
     * RUNNING_UNEXPOSED
     *   -> EXPOSED
     *
     * RUNNING_UNEXPOSED
     *   -> RESERVED
     *      crash recovery before exposure only
     *
     * EXPOSED
     *   -> SPENT
     */

    IF OLD.state = 'RESERVED'
       AND NEW.state = 'RUNNING_UNEXPOSED' THEN

        IF NEW.run_id IS NULL THEN
            RAISE EXCEPTION
                'RUNNING_UNEXPOSED requires a run_id';
END IF;

        IF NEW.instance_id IS NULL THEN
            RAISE EXCEPTION
                'RUNNING_UNEXPOSED requires an instance_id';
END IF;

        IF NEW.configuration_hash IS NULL
           OR BTRIM(NEW.configuration_hash) = '' THEN
            RAISE EXCEPTION
                'RUNNING_UNEXPOSED requires a configuration_hash';
END IF;

        NEW.updated_at = CURRENT_TIMESTAMP;

RETURN NEW;
END IF;


    IF OLD.state = 'RUNNING_UNEXPOSED'
       AND NEW.state = 'EXPOSED' THEN

        IF NEW.run_id IS NULL THEN
            RAISE EXCEPTION
                'EXPOSED requires a run_id';
END IF;

        IF NEW.instance_id IS NULL THEN
            RAISE EXCEPTION
                'EXPOSED requires an instance_id';
END IF;

        IF NEW.configuration_hash IS NULL
           OR BTRIM(NEW.configuration_hash) = '' THEN
            RAISE EXCEPTION
                'EXPOSED requires a configuration_hash';
END IF;

        IF NEW.market_data_fingerprint IS NULL
           OR BTRIM(NEW.market_data_fingerprint) = '' THEN
            RAISE EXCEPTION
                'EXPOSED requires a market_data_fingerprint';
END IF;

        NEW.updated_at = CURRENT_TIMESTAMP;

RETURN NEW;
END IF;


    IF OLD.state = 'RUNNING_UNEXPOSED'
       AND NEW.state = 'RESERVED' THEN

        /*
         * Startup crash recovery.
         *
         * Ownership and run-specific values are cleared when
         * the abandoned unexposed attempt is returned to the
         * reservation pool.
         */
        NEW.run_id = NULL;
        NEW.instance_id = NULL;
        NEW.configuration_hash = NULL;
        NEW.market_data_fingerprint = NULL;
        NEW.updated_at = CURRENT_TIMESTAMP;

RETURN NEW;
END IF;


    IF OLD.state = 'EXPOSED'
       AND NEW.state = 'SPENT' THEN

        NEW.updated_at = CURRENT_TIMESTAMP;

RETURN NEW;
END IF;


    RAISE EXCEPTION
        'Illegal protected run state transition: % -> %',
        OLD.state,
        NEW.state;

END;
$$;


CREATE TRIGGER trg_protected_guard_transition
    BEFORE INSERT OR UPDATE
                         ON protected_run_guard
                         FOR EACH ROW
                         EXECUTE FUNCTION enforce_protected_guard_transition();


/* ============================================================
 * GUARD DELETE PROTECTION
 * ============================================================
 */

CREATE OR REPLACE FUNCTION prevent_protected_guard_delete()
RETURNS TRIGGER
LANGUAGE plpgsql
AS
$$
BEGIN

    RAISE EXCEPTION
        'Protected run guard rows cannot be deleted';

END;
$$;


CREATE TRIGGER trg_prevent_protected_guard_delete
    BEFORE DELETE
    ON protected_run_guard
    FOR EACH ROW
    EXECUTE FUNCTION prevent_protected_guard_delete();


/* ============================================================
 * LEDGER APPEND-ONLY PROTECTION
 * ============================================================
 */

CREATE OR REPLACE FUNCTION prevent_protected_ledger_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS
$$
BEGIN

    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION
            'Protected run event ledger is append-only; UPDATE is forbidden';
END IF;

    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'Protected run event ledger is append-only; DELETE is forbidden';
END IF;

    RAISE EXCEPTION
        'Unsupported protected ledger mutation';

END;
$$;


CREATE TRIGGER trg_prevent_protected_ledger_mutation
    BEFORE UPDATE OR DELETE
ON protected_run_event_ledger
FOR EACH ROW
EXECUTE FUNCTION prevent_protected_ledger_mutation();


/* ============================================================
 * HASH CHAIN + LEDGER CONTINUITY
 * ============================================================
 */

CREATE OR REPLACE FUNCTION validate_protected_ledger_chain()
RETURNS TRIGGER
LANGUAGE plpgsql
AS
$$
DECLARE

latest_state VARCHAR(32);
    latest_hash CHAR(64);
    latest_sequence BIGINT;

BEGIN

SELECT
    new_state,
    event_hash,
    sequence_number
INTO
    latest_state,
    latest_hash,
    latest_sequence
FROM protected_run_event_ledger
WHERE guard_id = NEW.guard_id
ORDER BY sequence_number DESC
    LIMIT 1;


/*
 * First ledger event for a target.
 */
IF latest_sequence IS NULL THEN

        IF NEW.previous_state IS NOT NULL THEN
            RAISE EXCEPTION
                'First protected ledger event must not have a previous state';
END IF;

        IF NEW.previous_event_hash IS NOT NULL THEN
            RAISE EXCEPTION
                'First protected ledger event must not have a previous event hash';
END IF;

        IF NEW.new_state <> 'RESERVED' THEN
            RAISE EXCEPTION
                'First protected ledger event must establish RESERVED state';
END IF;

RETURN NEW;
END IF;


    /*
     * Every later event must chain from the latest event.
     */
    IF NEW.previous_state IS DISTINCT FROM latest_state THEN
        RAISE EXCEPTION
            'Protected ledger previous state does not match latest ledger state';
END IF;


    IF NEW.previous_event_hash IS DISTINCT FROM latest_hash THEN
        RAISE EXCEPTION
            'Protected ledger previous hash does not match latest ledger hash';
END IF;


RETURN NEW;

END;
$$;


CREATE TRIGGER trg_validate_protected_ledger_chain
    BEFORE INSERT
    ON protected_run_event_ledger
    FOR EACH ROW
    EXECUTE FUNCTION validate_protected_ledger_chain();


/* ============================================================
 * GUARD / LEDGER CONSISTENCY
 *
 * Deferred until transaction commit so application code may:
 *
 *   1. update the guard
 *   2. append the matching ledger event
 *
 * inside either order within the same transaction.
 *
 * If only one side commits, the transaction fails.
 * ============================================================
 */

CREATE OR REPLACE FUNCTION verify_protected_guard_ledger_consistency()
RETURNS TRIGGER
LANGUAGE plpgsql
AS
$$
DECLARE

target_guard_id BIGINT;
    guard_state VARCHAR(32);
    ledger_state VARCHAR(32);

BEGIN

    IF TG_TABLE_NAME = 'protected_run_guard' THEN
        target_guard_id := NEW.id;
ELSE
        target_guard_id := NEW.guard_id;
END IF;


SELECT state
INTO guard_state
FROM protected_run_guard
WHERE id = target_guard_id;


SELECT new_state
INTO ledger_state
FROM protected_run_event_ledger
WHERE guard_id = target_guard_id
ORDER BY sequence_number DESC
    LIMIT 1;


IF guard_state IS NULL THEN
        RAISE EXCEPTION
            'Protected guard does not exist for ledger consistency check';
END IF;


    IF ledger_state IS NULL THEN
        RAISE EXCEPTION
            'Protected guard has no corresponding ledger state';
END IF;


    IF guard_state IS DISTINCT FROM ledger_state THEN
        RAISE EXCEPTION
            'Governance integrity mismatch: guard state % does not match latest ledger state %',
            guard_state,
            ledger_state;
END IF;


RETURN NULL;

END;
$$;


/*
 * Guard changes must match the ledger by COMMIT.
 */
CREATE CONSTRAINT TRIGGER trg_guard_matches_ledger
AFTER INSERT OR UPDATE
                           ON protected_run_guard
                           DEFERRABLE INITIALLY DEFERRED
                           FOR EACH ROW
                           EXECUTE FUNCTION verify_protected_guard_ledger_consistency();


/*
 * Ledger state events must match the guard by COMMIT.
 */
CREATE CONSTRAINT TRIGGER trg_ledger_matches_guard
AFTER INSERT
ON protected_run_event_ledger
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION verify_protected_guard_ledger_consistency();