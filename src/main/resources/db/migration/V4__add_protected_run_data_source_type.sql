/*
 * MarshallAI
 *
 * Protected-run data-source provenance.
 *
 * New protected runs must durably record whether their
 * market data source is REAL or SIMULATED.
 *
 * Governed PASS / FAIL is permitted only for REAL data.
 *
 * The application derives this value from
 * MarketDataProvider.providerType().
 *
 * Historical pre-V4 rows are not reclassified. Their source
 * provenance was not recorded when they were created, so this
 * migration deliberately does not pretend they were REAL.
 */


/* ============================================================
 * PROTECTED RUN GUARD
 * ============================================================
 */

ALTER TABLE protected_run_guard
    ADD COLUMN data_source_type VARCHAR(16);


/*
 * Basic domain constraint.
 *
 * NULL remains possible for:
 *
 * - RESERVED targets that have not been acquired
 * - historical pre-V4 records whose provenance was not stored
 */
ALTER TABLE protected_run_guard
    ADD CONSTRAINT ck_protected_run_guard_data_source_type
        CHECK (
            data_source_type IS NULL
                OR data_source_type IN (
                                        'REAL',
                                        'SIMULATED'
                )
            );


/*
 * New/updated rows must obey the state/source relationship.
 *
 * NOT VALID intentionally avoids making an unsupported claim
 * about historical pre-V4 rows.
 *
 * PostgreSQL still enforces a NOT VALID CHECK constraint for
 * new rows and rows subsequently updated after this migration.
 */
ALTER TABLE protected_run_guard
    ADD CONSTRAINT ck_protected_run_guard_source_by_state
        CHECK (
            (
                state = 'RESERVED'
                    AND data_source_type IS NULL
                )
                OR
            (
                state IN (
                          'RUNNING_UNEXPOSED',
                          'EXPOSED',
                          'SPENT'
                    )
                    AND data_source_type IN (
                                             'REAL',
                                             'SIMULATED'
                    )
                )
            )
    NOT VALID;


/* ============================================================
 * GUARD SOURCE-TYPE LIFECYCLE
 * ============================================================
 *
 * RESERVED -> RUNNING_UNEXPOSED
 *     establishes source provenance.
 *
 * RUNNING_UNEXPOSED -> EXPOSED -> SPENT
 *     must preserve exactly the same source type.
 *
 * RUNNING_UNEXPOSED -> RESERVED
 *     abort/recovery clears the mutable guard source type,
 *     while the immutable ABORTED_RUN ledger event preserves
 *     the abandoned run's provenance.
 */

CREATE OR REPLACE FUNCTION validate_protected_guard_data_source_type()
RETURNS TRIGGER
LANGUAGE plpgsql
AS
$$
BEGIN

    /*
     * A fresh reservation belongs to no run yet.
     */
    IF TG_OP = 'INSERT' THEN

        IF NEW.state = 'RESERVED'
           AND NEW.data_source_type IS NOT NULL THEN

            RAISE EXCEPTION
                'Reserved protected target must not have a data source type';

END IF;

RETURN NEW;

END IF;


    /*
     * Acquisition establishes provenance.
     */
    IF OLD.state = 'RESERVED'
       AND NEW.state = 'RUNNING_UNEXPOSED' THEN

        IF NEW.data_source_type IS NULL THEN

            RAISE EXCEPTION
                'Protected run acquisition requires a data source type';

END IF;

RETURN NEW;

END IF;


    /*
     * Abort/recovery removes ownership from the mutable guard.
     */
    IF OLD.state = 'RUNNING_UNEXPOSED'
       AND NEW.state = 'RESERVED' THEN

        IF NEW.data_source_type IS NOT NULL THEN

            RAISE EXCEPTION
                'Recovered protected reservation must clear data source type';

END IF;

RETURN NEW;

END IF;


    /*
     * All other legal run-state transitions must preserve
     * source provenance exactly.
     */
    IF NEW.data_source_type
       IS DISTINCT FROM OLD.data_source_type THEN

        RAISE EXCEPTION
            'Protected run data source type cannot change after acquisition';

END IF;


RETURN NEW;

END;
$$;


CREATE TRIGGER trg_validate_protected_guard_data_source_type
    BEFORE INSERT OR UPDATE
                         ON protected_run_guard
                         FOR EACH ROW
                         EXECUTE FUNCTION validate_protected_guard_data_source_type();


/* ============================================================
 * IMMUTABLE LEDGER PROVENANCE
 * ============================================================
 */

ALTER TABLE protected_run_event_ledger
    ADD COLUMN data_source_type VARCHAR(16);


/*
 * New run-bearing ledger events must contain provenance.
 *
 * Historical rows are intentionally left unchanged.
 */
ALTER TABLE protected_run_event_ledger
    ADD CONSTRAINT ck_protected_run_event_data_source_type
        CHECK (
            (
                run_id IS NULL
                    AND data_source_type IS NULL
                )
                OR
            (
                run_id IS NOT NULL
                    AND data_source_type IN (
                                             'REAL',
                                             'SIMULATED'
                    )
                )
            )
    NOT VALID;


/* ============================================================
 * GOVERNED VERDICT DATABASE ENFORCEMENT
 * ============================================================
 *
 * Even code that bypasses the Java verdict API cannot persist
 * PASS or FAIL for a SIMULATED protected run.
 *
 * NOT VALID preserves historical rows whose source provenance
 * predates this migration while still enforcing the rule for
 * every new ledger event.
 */

ALTER TABLE protected_run_event_ledger
    ADD CONSTRAINT ck_governed_verdict_requires_real_data
        CHECK (
            governed_verdict IS NULL
                OR data_source_type = 'REAL'
            )
    NOT VALID;


/* ============================================================
 * LEDGER SOURCE-TYPE CONTINUITY
 * ============================================================
 */

CREATE OR REPLACE FUNCTION validate_protected_run_data_source_type()
RETURNS TRIGGER
LANGUAGE plpgsql
AS
$$
DECLARE

latest_source_type VARCHAR(16);

BEGIN

    /*
     * The initial RESERVED event belongs to the protected
     * target, not to an acquired run.
     */
    IF NEW.run_id IS NULL THEN

        IF NEW.data_source_type IS NOT NULL THEN

            RAISE EXCEPTION
                'Ledger event without a run ID must not have a data source type';

END IF;

RETURN NEW;

END IF;


    /*
     * Every event belonging to an acquired run must record
     * source provenance.
     */
    IF NEW.data_source_type IS NULL THEN

        RAISE EXCEPTION
            'Protected run ledger event requires a data source type';

END IF;


    /*
     * Locate the latest source classification already recorded
     * for this exact run.
     */
SELECT data_source_type
INTO latest_source_type
FROM protected_run_event_ledger
WHERE guard_id = NEW.guard_id
  AND run_id = NEW.run_id
  AND data_source_type IS NOT NULL
ORDER BY sequence_number DESC
    LIMIT 1;


/*
 * The first run event establishes source provenance.
 *
 * Every later event for the same run must preserve it.
 */
IF latest_source_type IS NOT NULL
       AND NEW.data_source_type
           IS DISTINCT FROM latest_source_type THEN

        RAISE EXCEPTION
            'Protected run data source type changed within the same run';

END IF;


RETURN NEW;

END;
$$;


CREATE TRIGGER trg_validate_protected_run_data_source_type
    BEFORE INSERT
    ON protected_run_event_ledger
    FOR EACH ROW
    EXECUTE FUNCTION validate_protected_run_data_source_type();