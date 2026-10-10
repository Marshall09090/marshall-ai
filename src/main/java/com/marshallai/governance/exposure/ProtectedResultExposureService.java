package com.marshallai.governance.exposure;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Fail-closed publication boundary for protected-run results.
 *
 * Protected results may be computed internally while the run is
 * RUNNING_UNEXPOSED, but they are never returned to the caller
 * until EXPOSED has been durably persisted.
 *
 * If EXPOSED persistence fails, the locally computed result is
 * discarded and the protected run is durably aborted.
 *
 * This class deliberately does not log result-bearing content.
 */
@Service
public class ProtectedResultExposureService {

    private final ProtectedRunGuardRepository repository;

    public ProtectedResultExposureService(
            ProtectedRunGuardRepository repository) {

        if (repository == null) {

            throw new IllegalArgumentException(
                    "Protected-run guard repository cannot be null"
            );
        }

        this.repository =
                repository;
    }

    /**
     * Computes a protected result internally and releases it only
     * after the EXPOSED state is durably committed.
     */
    public <T> ResultExposure<T> computeAndExpose(
            long guardId,
            UUID runId,
            UUID instanceId,
            String configurationHash,
            String marketDataFingerprint,
            Supplier<T> protectedComputation,
            Instant exposureTimestamp,
            Instant abortTimestamp) {

        if (protectedComputation == null) {

            throw new IllegalArgumentException(
                    "Protected computation cannot be null"
            );
        }

        T computedResult =
                protectedComputation.get();

        if (computedResult == null) {

            throw new IllegalStateException(
                    "Protected computation returned no result"
            );
        }

        ProtectedRunGuardRepository.ExposureResult
                exposureResult;

        try {

            exposureResult =
                    repository.persistExposed(
                            guardId,
                            runId,
                            instanceId,
                            configurationHash,
                            marketDataFingerprint,
                            exposureTimestamp
                    );

        } catch (RuntimeException exposureFailure) {

            /*
             * The computed result intentionally never leaves this
             * method on this path.
             */
            computedResult =
                    null;

            ProtectedRunGuardRepository.AbortResult
                    abortResult =
                    repository.abortUnexposedRun(
                            guardId,
                            runId,
                            instanceId,
                            configurationHash,
                            abortTimestamp
                    );

            if (!abortResult.aborted()) {

                IllegalStateException abortFailure =
                        new IllegalStateException(
                                "EXPOSED persistence failed and "
                                        + "protected run could not be aborted: "
                                        + abortResult.refusalReason()
                        );

                abortFailure.addSuppressed(
                        exposureFailure
                );

                throw abortFailure;
            }

            return ResultExposure.abortedAfterFailure(
                    exposureFailure
                            .getClass()
                            .getSimpleName()
            );
        }

        if (!exposureResult.exposed()) {

            /*
             * A refused EXPOSED transition also fails closed:
             * no result-bearing object is returned.
             */
            computedResult =
                    null;

            return ResultExposure.refused(
                    exposureResult.refusalReason()
            );
        }

        /*
         * This is the first point where the caller is allowed to
         * receive the protected result.
         *
         * persistExposed(...) has already committed EXPOSED and
         * its matching ledger event before returning here.
         */
        return ResultExposure.released(
                computedResult
        );
    }

    public record ResultExposure<T>(
            boolean resultsVisible,
            boolean resultsDiscarded,
            boolean runAborted,
            T result,
            String refusalReason) {

        public static <T> ResultExposure<T> released(
                T result) {

            return new ResultExposure<>(
                    true,
                    false,
                    false,
                    result,
                    null
            );
        }

        public static <T> ResultExposure<T> refused(
                String refusalReason) {

            return new ResultExposure<>(
                    false,
                    true,
                    false,
                    null,
                    refusalReason
            );
        }

        public static <T> ResultExposure<T>
        abortedAfterFailure(
                String failureType) {

            return new ResultExposure<>(
                    false,
                    true,
                    true,
                    null,
                    failureType
            );
        }
    }
}