package com.marshallai;

import com.marshallai.governance.exposure.ProtectedRunOperationalLogger;
import com.marshallai.governance.exposure.ProtectedRunState;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProtectedRunOperationalLoggerTest {

    @Test
    void runningUnexposedAllowsRunIdentifiersAndNonResultProgress() {

        List<String> messages =
                new ArrayList<>();

        ProtectedRunOperationalLogger logger =
                ProtectedRunOperationalLogger.forSink(
                        messages::add
                );

        UUID runId =
                UUID.randomUUID();

        logger.logProgress(
                runId,
                ProtectedRunState.RUNNING_UNEXPOSED,
                "Loading protected validation bars"
        );

        assertEquals(
                1,
                messages.size()
        );

        String message =
                messages.getFirst();

        assertTrue(
                message.contains(
                        runId.toString()
                )
        );

        assertTrue(
                message.contains(
                        "RUNNING_UNEXPOSED"
                )
        );

        assertTrue(
                message.contains(
                        "Loading protected validation bars"
                )
        );

        /*
         * Ordinary progress must not accidentally contain
         * result-bearing fields.
         */
        assertFalse(
                message.contains(
                        "trades="
                )
        );

        assertFalse(
                message.contains(
                        "pnl="
                )
        );

        assertFalse(
                message.contains(
                        "expectancy="
                )
        );

        assertFalse(
                message.contains(
                        "drawdown="
                )
        );

        assertFalse(
                message.contains(
                        "verdict="
                )
        );
    }

    @Test
    void runningUnexposedRejectsAllResultBearingInformation() {

        List<String> messages =
                new ArrayList<>();

        ProtectedRunOperationalLogger logger =
                ProtectedRunOperationalLogger.forSink(
                        messages::add
                );

        UUID runId =
                UUID.randomUUID();

        ProtectedRunOperationalLogger.ResultBearingInformation
                information =
                protectedResultInformation();

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                logger.logResultBearingInformation(
                                        runId,
                                        ProtectedRunState.RUNNING_UNEXPOSED,
                                        information
                                )
                );

        assertEquals(
                ProtectedRunOperationalLogger
                        .RESULT_BEARING_LOG_FORBIDDEN_BEFORE_EXPOSURE,
                exception.getMessage()
        );

        /*
         * The sink must receive absolutely nothing from the
         * rejected result-bearing call.
         */
        assertTrue(
                messages.isEmpty()
        );
    }

    @Test
    void exposedRunMayLogResultBearingInformation() {

        List<String> messages =
                new ArrayList<>();

        ProtectedRunOperationalLogger logger =
                ProtectedRunOperationalLogger.forSink(
                        messages::add
                );

        UUID runId =
                UUID.randomUUID();

        logger.logResultBearingInformation(
                runId,
                ProtectedRunState.EXPOSED,
                protectedResultInformation()
        );

        assertEquals(
                1,
                messages.size()
        );

        String message =
                messages.getFirst();

        assertTrue(
                message.contains(
                        runId.toString()
                )
        );

        assertTrue(
                message.contains(
                        "state=EXPOSED"
                )
        );

        assertTrue(
                message.contains(
                        "trades=BUY SPY -> SELL SPY"
                )
        );

        assertTrue(
                message.contains(
                        "pnl=125.50"
                )
        );

        assertTrue(
                message.contains(
                        "expectancy=0.42"
                )
        );

        assertTrue(
                message.contains(
                        "drawdown=0.08"
                )
        );

        assertTrue(
                message.contains(
                        "verdict=PASS"
                )
        );
    }

    @Test
    void spentRunMayLogResultBearingInformation() {

        List<String> messages =
                new ArrayList<>();

        ProtectedRunOperationalLogger logger =
                ProtectedRunOperationalLogger.forSink(
                        messages::add
                );

        logger.logResultBearingInformation(
                UUID.randomUUID(),
                ProtectedRunState.SPENT,
                protectedResultInformation()
        );

        assertEquals(
                1,
                messages.size()
        );

        assertTrue(
                messages.getFirst()
                        .contains(
                                "state=SPENT"
                        )
        );
    }

    private static
    ProtectedRunOperationalLogger.ResultBearingInformation
    protectedResultInformation() {

        return new ProtectedRunOperationalLogger
                .ResultBearingInformation(
                "BUY SPY -> SELL SPY",
                new BigDecimal(
                        "125.50"
                ),
                new BigDecimal(
                        "0.42"
                ),
                new BigDecimal(
                        "0.08"
                ),
                "PASS"
        );
    }
}