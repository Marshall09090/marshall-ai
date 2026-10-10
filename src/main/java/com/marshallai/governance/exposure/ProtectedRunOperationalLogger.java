package com.marshallai.governance.exposure;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Logging boundary for protected governed runs.
 *
 * RUNNING_UNEXPOSED runs may emit identifiers and ordinary
 * operational progress only.
 *
 * Result-bearing information is forbidden until the protected
 * run has durably reached EXPOSED.
 */
@Service
public class ProtectedRunOperationalLogger {

    public static final String
            RESULT_BEARING_LOG_FORBIDDEN_BEFORE_EXPOSURE =
            "RESULT_BEARING_LOG_FORBIDDEN_BEFORE_EXPOSURE";

    private static final Logger LOGGER =
            Logger.getLogger(
                    ProtectedRunOperationalLogger.class.getName()
            );

    private final Consumer<String> sink;

    /**
     * Production constructor.
     */
    public ProtectedRunOperationalLogger() {

        this(
                LOGGER::info
        );
    }

    private ProtectedRunOperationalLogger(
            Consumer<String> sink) {

        if (sink == null) {

            throw new IllegalArgumentException(
                    "Log sink cannot be null"
            );
        }

        this.sink =
                sink;
    }

    /**
     * Test/adapter factory that lets callers provide a controlled
     * sink while exercising the exact production policy.
     */
    public static ProtectedRunOperationalLogger forSink(
            Consumer<String> sink) {

        return new ProtectedRunOperationalLogger(
                sink
        );
    }

    /**
     * Ordinary progress is permitted before exposure.
     *
     * This method deliberately accepts no trades, P/L,
     * expectancy, drawdown, or verdict fields.
     */
    public void logProgress(
            UUID runId,
            ProtectedRunState state,
            String progress) {

        requireRunId(
                runId
        );

        requireState(
                state
        );

        requireText(
                progress,
                "Progress"
        );

        sink.accept(
                "runId="
                        + runId
                        + " state="
                        + state
                        + " progress="
                        + progress
        );
    }

    /**
     * Result-bearing information may be logged only after the
     * protected run is durably EXPOSED.
     *
     * SPENT is also permitted because it necessarily follows
     * EXPOSED.
     */
    public void logResultBearingInformation(
            UUID runId,
            ProtectedRunState state,
            ResultBearingInformation information) {

        requireRunId(
                runId
        );

        requireState(
                state
        );

        if (information == null) {

            throw new IllegalArgumentException(
                    "Result-bearing information cannot be null"
            );
        }

        if (state != ProtectedRunState.EXPOSED
                && state != ProtectedRunState.SPENT) {

            throw new IllegalStateException(
                    RESULT_BEARING_LOG_FORBIDDEN_BEFORE_EXPOSURE
            );
        }

        sink.accept(
                "runId="
                        + runId
                        + " state="
                        + state
                        + " trades="
                        + information.trades()
                        + " pnl="
                        + information.profitAndLoss()
                        + " expectancy="
                        + information.expectancy()
                        + " drawdown="
                        + information.drawdown()
                        + " verdict="
                        + information.governedVerdict()
        );
    }

    private static void requireRunId(
            UUID runId) {

        if (runId == null) {

            throw new IllegalArgumentException(
                    "Run ID cannot be null"
            );
        }
    }

    private static void requireState(
            ProtectedRunState state) {

        if (state == null) {

            throw new IllegalArgumentException(
                    "Protected-run state cannot be null"
            );
        }
    }

    private static void requireText(
            String value,
            String fieldName) {

        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    fieldName
                            + " cannot be blank"
            );
        }
    }

    public record ResultBearingInformation(
            String trades,
            BigDecimal profitAndLoss,
            BigDecimal expectancy,
            BigDecimal drawdown,
            String governedVerdict) {

        public ResultBearingInformation {

            requireText(
                    trades,
                    "Trades"
            );

            if (profitAndLoss == null) {

                throw new IllegalArgumentException(
                        "Profit and loss cannot be null"
                );
            }

            if (expectancy == null) {

                throw new IllegalArgumentException(
                        "Expectancy cannot be null"
                );
            }

            if (drawdown == null) {

                throw new IllegalArgumentException(
                        "Drawdown cannot be null"
                );
            }

            requireText(
                    governedVerdict,
                    "Governed verdict"
            );
        }
    }
}