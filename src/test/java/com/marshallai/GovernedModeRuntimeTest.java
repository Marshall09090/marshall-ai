package com.marshallai;

import com.marshallai.governance.exposure.ApplicationInstanceIdentity;
import com.marshallai.governance.exposure.GovernedModeAdvisoryLock;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GovernedModeRuntimeTest {

    private static PostgreSQLContainer postgres;

    private static DataSource dataSource;

    @BeforeAll
    static void startPostgres() {

        postgres =
                new PostgreSQLContainer(
                        "postgres:16-alpine"
                );

        postgres.start();

        DriverManagerDataSource configured =
                new DriverManagerDataSource();

        configured.setDriverClassName(
                "org.postgresql.Driver"
        );

        configured.setUrl(
                postgres.getJdbcUrl()
        );

        configured.setUsername(
                postgres.getUsername()
        );

        configured.setPassword(
                postgres.getPassword()
        );

        dataSource =
                configured;
    }

    @AfterAll
    static void stopPostgres() {

        if (postgres != null) {

            postgres.stop();
        }
    }

    @Test
    void everyApplicationStartupReceivesFreshInstanceId() {

        ApplicationInstanceIdentity first =
                new ApplicationInstanceIdentity();

        ApplicationInstanceIdentity second =
                new ApplicationInstanceIdentity();

        UUID firstId =
                first.instanceId();

        UUID secondId =
                second.instanceId();

        assertNotNull(
                firstId
        );

        assertNotNull(
                secondId
        );

        assertNotEquals(
                firstId,
                secondId
        );

        /*
         * Identity is stable throughout the same application
         * instance.
         */
        assertEquals(
                firstId,
                first.instanceId()
        );

        assertEquals(
                secondId,
                second.instanceId()
        );
    }

    @Test
    void onlyOneApplicationInstanceMayHoldGovernedModeLock() {

        GovernedModeAdvisoryLock first =
                new GovernedModeAdvisoryLock(
                        dataSource
                );

        GovernedModeAdvisoryLock second =
                new GovernedModeAdvisoryLock(
                        dataSource
                );

        try {

            GovernedModeAdvisoryLock.LockResult
                    firstResult =
                    first.acquire();

            assertTrue(
                    firstResult.acquired()
            );

            assertTrue(
                    first.isHeld()
            );

            GovernedModeAdvisoryLock.LockResult
                    secondResult =
                    second.acquire();

            assertFalse(
                    secondResult.acquired()
            );

            assertEquals(
                    GovernedModeAdvisoryLock
                            .GOVERNED_INSTANCE_ALREADY_ACTIVE,
                    secondResult.refusalReason()
            );

            assertFalse(
                    second.isHeld()
            );

        } finally {

            if (first.isHeld()) {

                first.release();
            }

            if (second.isHeld()) {

                second.release();
            }
        }
    }

    @Test
    void governedModeMayBeAcquiredAfterPreviousInstanceReleasesLock() {

        GovernedModeAdvisoryLock first =
                new GovernedModeAdvisoryLock(
                        dataSource
                );

        GovernedModeAdvisoryLock second =
                new GovernedModeAdvisoryLock(
                        dataSource
                );

        assertTrue(
                first.acquire()
                        .acquired()
        );

        first.release();

        try {

            GovernedModeAdvisoryLock.LockResult result =
                    second.acquire();

            assertTrue(
                    result.acquired()
            );

            assertTrue(
                    second.isHeld()
            );

        } finally {

            if (second.isHeld()) {

                second.release();
            }
        }
    }
}