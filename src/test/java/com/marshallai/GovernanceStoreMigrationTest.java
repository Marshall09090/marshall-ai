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

            assertEquals(
                    1,
                    migrationResult.migrationsExecuted,
                    "Exactly one governance migration should execute"
            );

            try (Connection connection =
                         DriverManager.getConnection(
                                 postgres.getJdbcUrl(),
                                 postgres.getUsername(),
                                 postgres.getPassword()
                         )) {

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
}