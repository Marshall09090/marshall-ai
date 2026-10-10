ALTER TABLE protected_run_event_ledger
    ADD COLUMN governed_verdict VARCHAR(16);

ALTER TABLE protected_run_event_ledger
    ADD CONSTRAINT chk_protected_run_event_governed_verdict
        CHECK (
            (
                new_state = 'SPENT'
                    AND governed_verdict IS NOT NULL
                    AND governed_verdict IN ('PASS', 'FAIL')
                )
                OR
            (
                new_state <> 'SPENT'
                    AND governed_verdict IS NULL
                )
            );