package com.marshallai;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GovernanceStoreMigrationTest {

    @Test
    void flywayMigrationCreatesGovernanceStoreInRealPostgresql()
            throws Exception {

        try (PostgreSQLContainer postgres =
                     new PostgreSQLContainer("postgres:16-alpine")) {

            postgres.start();

            Flyway flyway =
                    Flyway.configure()
                            .dataSource(
                                    postgres.getJdbcUrl(),
                                    postgres.getUsername(),
                                    postgres.getPassword()
                            )
                            .locations(
                                    "classpath:db/migration"
                            )
                            .load();

            var migrationResult =
                    flyway.migrate();

            /*
             * A fresh governance database currently consists of:
             *
             * V1 - initial protected-run governance store
             * V2 - governed verdict support
             * V3 - protected market-data authorization support
             * V4 - protected-run market-data source provenance
             */
            assertEquals(
                    4,
                    migrationResult.migrationsExecuted,
                    "Exactly four governance migrations should execute"
            );

            try (Connection connection =
                         DriverManager.getConnection(
                                 postgres.getJdbcUrl(),
                                 postgres.getUsername(),
                                 postgres.getPassword()
                         )) {

                // =====================================================
                // V1 TABLES
                // =====================================================

                assertTrue(
                        tableExists(
                                connection,
                                "protected_run_guard"
                        ),
                        "protected_run_guard should exist"
                );

                assertTrue(
                        tableExists(
                                connection,
                                "protected_run_event_ledger"
                        ),
                        "protected_run_event_ledger should exist"
                );

                // =====================================================
                // V1 DATABASE GOVERNANCE TRIGGERS
                // =====================================================

                assertTrue(
                        triggerExists(
                                connection,
                                "trg_protected_guard_transition"
                        ),
                        "Guard transition trigger should exist"
                );

                assertTrue(
                        triggerExists(
                                connection,
                                "trg_prevent_protected_guard_delete"
                        ),
                        "Guard delete-protection trigger should exist"
                );

                assertTrue(
                        triggerExists(
                                connection,
                                "trg_prevent_protected_ledger_mutation"
                        ),
                        "Ledger append-only trigger should exist"
                );

                assertTrue(
                        triggerExists(
                                connection,
                                "trg_validate_protected_ledger_chain"
                        ),
                        "Ledger-chain trigger should exist"
                );

                assertTrue(
                        triggerExists(
                                connection,
                                "trg_guard_matches_ledger"
                        ),
                        "Guard-to-ledger consistency trigger should exist"
                );

                assertTrue(
                        triggerExists(
                                connection,
                                "trg_ledger_matches_guard"
                        ),
                        "Ledger-to-guard consistency trigger should exist"
                );

                // =====================================================
                // V2 GOVERNED VERDICT CONTRACT
                // =====================================================

                assertTrue(
                        columnExists(
                                connection,
                                "protected_run_event_ledger",
                                "governed_verdict"
                        ),
                        "V2 should add governed_verdict to protected_run_event_ledger"
                );

                assertTrue(
                        constraintExists(
                                connection,
                                "protected_run_event_ledger",
                                "chk_protected_run_event_governed_verdict"
                        ),
                        "V2 governed-verdict constraint should exist"
                );

                // =====================================================
                // V3 PROTECTED MARKET-DATA AUTHORIZATION TABLES
                // =====================================================

                assertTrue(
                        tableExists(
                                connection,
                                "protected_tuning_period"
                        ),
                        "V3 should create protected_tuning_period"
                );

                assertTrue(
                        tableExists(
                                connection,
                                "protected_holdout_authorization"
                        ),
                        "V3 should create protected_holdout_authorization"
                );

                // =====================================================
                // V3 TUNING-PERIOD CONTRACT
                // =====================================================

                assertTrue(
                        columnExists(
                                connection,
                                "protected_tuning_period",
                                "strategy_version"
                        ),
                        "protected_tuning_period should contain strategy_version"
                );

                assertTrue(
                        columnExists(
                                connection,
                                "protected_tuning_period",
                                "tuning_period_id"
                        ),
                        "protected_tuning_period should contain tuning_period_id"
                );

                assertTrue(
                        columnExists(
                                connection,
                                "protected_tuning_period",
                                "period_start"
                        ),
                        "protected_tuning_period should contain period_start"
                );

                assertTrue(
                        columnExists(
                                connection,
                                "protected_tuning_period",
                                "period_end"
                        ),
                        "protected_tuning_period should contain period_end"
                );

                assertTrue(
                        constraintExists(
                                connection,
                                "protected_tuning_period",
                                "ck_protected_tuning_period_dates"
                        ),
                        "Tuning-period date constraint should exist"
                );

                assertTrue(
                        constraintExists(
                                connection,
                                "protected_tuning_period",
                                "uq_protected_tuning_period_strategy"
                        ),
                        "Strategy tuning-period uniqueness constraint should exist"
                );

                assertTrue(
                        constraintExists(
                                connection,
                                "protected_tuning_period",
                                "uq_protected_tuning_period_identity"
                        ),
                        "Tuning-period identity uniqueness constraint should exist"
                );

                // =====================================================
                // V3 HOLDOUT AUTHORIZATION CONTRACT
                // =====================================================

                assertTrue(
                        columnExists(
                                connection,
                                "protected_holdout_authorization",
                                "guard_id"
                        ),
                        "protected_holdout_authorization should contain guard_id"
                );

                assertTrue(
                        columnExists(
                                connection,
                                "protected_holdout_authorization",
                                "strategy_version"
                        ),
                        "protected_holdout_authorization should contain strategy_version"
                );

                assertTrue(
                        columnExists(
                                connection,
                                "protected_holdout_authorization",
                                "protected_period_id"
                        ),
                        "protected_holdout_authorization should contain protected_period_id"
                );

                assertTrue(
                        columnExists(
                                connection,
                                "protected_holdout_authorization",
                                "authorization_timestamp"
                        ),
                        "protected_holdout_authorization should contain authorization_timestamp"
                );

                assertTrue(
                        constraintExists(
                                connection,
                                "protected_holdout_authorization",
                                "fk_protected_holdout_authorization_guard"
                        ),
                        "Holdout authorization should reference protected_run_guard"
                );

                assertTrue(
                        constraintExists(
                                connection,
                                "protected_holdout_authorization",
                                "uq_protected_holdout_authorization_guard"
                        ),
                        "Each protected guard should have at most one holdout authorization"
                );

                assertTrue(
                        constraintExists(
                                connection,
                                "protected_holdout_authorization",
                                "uq_protected_holdout_authorization_target"
                        ),
                        "Each protected holdout target should have at most one authorization"
                );

                // =====================================================
                // V4 PROTECTED-RUN DATA-SOURCE PROVENANCE
                // =====================================================

                assertTrue(
                        columnExists(
                                connection,
                                "protected_run_guard",
                                "data_source_type"
                        ),
                        "V4 should add data_source_type to protected_run_guard"
                );

                assertTrue(
                        columnExists(
                                connection,
                                "protected_run_event_ledger",
                                "data_source_type"
                        ),
                        "V4 should add data_source_type to protected_run_event_ledger"
                );

                // =====================================================
                // V4 GUARD DATA-SOURCE CONTRACT
                // =====================================================

                assertTrue(
                        constraintExists(
                                connection,
                                "protected_run_guard",
                                "ck_protected_run_guard_data_source_type"
                        ),
                        "Guard data-source domain constraint should exist"
                );

                assertTrue(
                        constraintExists(
                                connection,
                                "protected_run_guard",
                                "ck_protected_run_guard_source_by_state"
                        ),
                        "Guard state/source relationship constraint should exist"
                );

                assertTrue(
                        triggerExists(
                                connection,
                                "trg_validate_protected_guard_data_source_type"
                        ),
                        "Guard data-source lifecycle trigger should exist"
                );

                // =====================================================
                // V4 LEDGER DATA-SOURCE CONTRACT
                // =====================================================

                assertTrue(
                        constraintExists(
                                connection,
                                "protected_run_event_ledger",
                                "ck_protected_run_event_data_source_type"
                        ),
                        "Ledger data-source provenance constraint should exist"
                );

                assertTrue(
                        constraintExists(
                                connection,
                                "protected_run_event_ledger",
                                "ck_governed_verdict_requires_real_data"
                        ),
                        "Governed verdicts should require REAL market data"
                );

                assertTrue(
                        triggerExists(
                                connection,
                                "trg_validate_protected_run_data_source_type"
                        ),
                        "Ledger data-source continuity trigger should exist"
                );
            }
        }
    }

    private boolean tableExists(
            Connection connection,
            String tableName)
            throws Exception {

        String sql =
                """
                SELECT to_regclass(?) IS NOT NULL
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    "public." + tableName
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                assertTrue(
                        resultSet.next()
                );

                return resultSet.getBoolean(1);
            }
        }
    }

    private boolean triggerExists(
            Connection connection,
            String triggerName)
            throws Exception {

        String sql =
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM pg_trigger
                    WHERE tgname = ?
                      AND NOT tgisinternal
                )
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    triggerName
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                assertTrue(
                        resultSet.next()
                );

                return resultSet.getBoolean(1);
            }
        }
    }

    private boolean columnExists(
            Connection connection,
            String tableName,
            String columnName)
            throws Exception {

        String sql =
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = ?
                      AND column_name = ?
                )
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    tableName
            );

            statement.setString(
                    2,
                    columnName
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                assertTrue(
                        resultSet.next()
                );

                return resultSet.getBoolean(1);
            }
        }
    }

    private boolean constraintExists(
            Connection connection,
            String tableName,
            String constraintName)
            throws Exception {

        String sql =
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.table_constraints
                    WHERE table_schema = 'public'
                      AND table_name = ?
                      AND constraint_name = ?
                )
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    tableName
            );

            statement.setString(
                    2,
                    constraintName
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                assertTrue(
                        resultSet.next()
                );

                return resultSet.getBoolean(1);
            }
        }
    }
}