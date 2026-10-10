package com.marshallai.market.protection;

import com.marshallai.governance.exposure.ProtectedPeriodType;
import com.marshallai.governance.exposure.ProtectedRunState;
import com.marshallai.market.calendar.UsEquityTradingCalendar;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class ProtectedMarketDataBoundary {

    public static final String DURABLE_STORE_REQUIRED =
            "DURABLE_STORE_REQUIRED";

    public static final String PROTECTED_VALIDATION_PERIOD =
            "PROTECTED_VALIDATION_PERIOD";

    public static final String PROTECTED_HOLDOUT_PERIOD =
            "PROTECTED_HOLDOUT_PERIOD";

    public static final String HOLDOUT_AUTHORIZATION_REQUIRED =
            "HOLDOUT_AUTHORIZATION_REQUIRED";

    public static final String GOVERNANCE_INTEGRITY_MISMATCH =
            "GOVERNANCE_INTEGRITY_MISMATCH";

    public static final String AUTHORIZED_RANGE_EXCEEDED =
            "AUTHORIZED_RANGE_EXCEEDED";

    public static final String WARM_UP_OUTSIDE_TUNING_PERIOD =
            "WARM_UP_OUTSIDE_TUNING_PERIOD";

    private final Supplier<JdbcTemplate> jdbcTemplateSupplier;

    @Autowired
    public ProtectedMarketDataBoundary(
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {

        if (jdbcTemplateProvider == null) {
            throw new IllegalArgumentException(
                    "JdbcTemplate provider cannot be null"
            );
        }

        this.jdbcTemplateSupplier =
                jdbcTemplateProvider::getIfAvailable;
    }

    private ProtectedMarketDataBoundary(
            Supplier<JdbcTemplate> jdbcTemplateSupplier) {

        if (jdbcTemplateSupplier == null) {
            throw new IllegalArgumentException(
                    "JdbcTemplate supplier cannot be null"
            );
        }

        this.jdbcTemplateSupplier =
                jdbcTemplateSupplier;
    }

    public static ProtectedMarketDataBoundary forJdbcTemplate(
            JdbcTemplate jdbcTemplate) {

        if (jdbcTemplate == null) {
            throw new IllegalArgumentException(
                    "JdbcTemplate cannot be null"
            );
        }

        return new ProtectedMarketDataBoundary(
                () -> jdbcTemplate
        );
    }

    public static ProtectedMarketDataBoundary
    failClosedWithoutDurableStore() {

        return new ProtectedMarketDataBoundary(
                () -> null
        );
    }

    /**
     * Authorizes a real market-data request before external
     * market data is retrieved.
     *
     * Validation and holdout periods remain protected from
     * ungoverned callers.
     *
     * A governed run must own the protected target.
     *
     * Holdout access additionally requires a separate,
     * durable explicit holdout authorization.
     *
     * The requested range uses an exclusive end timestamp,
     * matching MarshallAI's historical-data range model.
     *
     * Protected periods are evaluated by US-equity trading
     * date in America/New_York.
     */
    public void authorizeRealMarketDataRequest(
            Instant from,
            Instant to) {

        requireRange(
                from,
                to
        );

        JdbcTemplate jdbcTemplate =
                requireJdbcTemplate();

        LocalDate requestedStart =
                tradingDate(
                        from
                );

        LocalDate requestedEnd =
                lastRequestedTradingDate(
                        from,
                        to
                );

        List<ProtectedPeriod> protectedPeriods =
                findOverlappingProtectedPeriods(
                        jdbcTemplate,
                        requestedStart,
                        requestedEnd
                );

        /*
         * Ordinary tuning-period and other unprotected
         * historical reads do not require a governed run.
         */
        if (protectedPeriods.isEmpty()) {
            return;
        }

        ProtectedMarketDataAuthorization authorization =
                ProtectedMarketDataAccessContext
                        .currentAuthorization();

        /*
         * Ungoverned callers cannot read protected periods.
         */
        if (authorization == null) {

            throw new ProtectedMarketDataAccessException(
                    refusalReasonFor(
                            protectedPeriods.getFirst()
                    )
            );
        }

        /*
         * Resolve the actual acquired target using durable
         * PostgreSQL governance state.
         *
         * A thread-local authorization by itself does not
         * grant access.
         */
        ProtectedPeriod authorizedPeriod =
                requireAuthorizedPeriod(
                        jdbcTemplate,
                        authorization
                );

        /*
         * One governed authorization cannot unlock another
         * protected target.
         */
        for (ProtectedPeriod protectedPeriod :
                protectedPeriods) {

            if (protectedPeriod.guardId()
                    != authorizedPeriod.guardId()) {

                if (protectedPeriod.periodType()
                        == ProtectedPeriodType.HOLDOUT) {

                    throw new ProtectedMarketDataAccessException(
                            HOLDOUT_AUTHORIZATION_REQUIRED
                    );
                }

                throw new ProtectedMarketDataAccessException(
                        PROTECTED_VALIDATION_PERIOD
                );
            }
        }

        /*
         * The full request must remain inside the acquired
         * protected evaluation period.
         */
        if (requestedStart.isBefore(
                authorizedPeriod.periodStart())
                || requestedEnd.isAfter(
                authorizedPeriod.periodEnd())) {

            throw new ProtectedMarketDataAccessException(
                    AUTHORIZED_RANGE_EXCEEDED
            );
        }

        /*
         * Validation authorization is based on successful
         * protected-target acquisition and ownership.
         */
        if (authorizedPeriod.periodType()
                == ProtectedPeriodType.VALIDATION) {

            return;
        }

        /*
         * Holdout is more restrictive.
         *
         * Acquisition alone is not sufficient.
         *
         * A separate persisted authorization must identify
         * the exact holdout target.
         */
        if (authorizedPeriod.periodType()
                == ProtectedPeriodType.HOLDOUT) {

            requireExplicitHoldoutAuthorization(
                    jdbcTemplate,
                    authorizedPeriod
            );

            return;
        }

        throw new ProtectedMarketDataAccessException(
                GOVERNANCE_INTEGRITY_MISMATCH
        );
    }

    /**
     * Authorizes the historical range specifically requested
     * for validation warm-up calculations.
     *
     * This method does not mark candles or permit trading.
     * The caller must label retrieved warm-up candles as
     * WARM_UP_ONLY and exclude them from trade eligibility.
     *
     * The entire requested warm-up range must:
     *
     * 1. Have an acquired validation-run authorization.
     * 2. Belong to the same strategy's registered tuning period.
     * 3. Finish before the validation evaluation period.
     * 4. Avoid every protected validation/holdout period.
     *
     * It intentionally does not permit arbitrary historical
     * expansion of the normal validation request.
     */
    public void authorizeWarmUpMarketDataRequest(
            Instant from,
            Instant to) {

        requireRange(
                from,
                to
        );

        JdbcTemplate jdbcTemplate =
                requireJdbcTemplate();

        ProtectedMarketDataAuthorization authorization =
                ProtectedMarketDataAccessContext
                        .currentAuthorization();

        if (authorization == null) {

            throw new ProtectedMarketDataAccessException(
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }

        ProtectedPeriod validationPeriod =
                requireAuthorizedPeriod(
                        jdbcTemplate,
                        authorization
                );

        if (validationPeriod.periodType()
                != ProtectedPeriodType.VALIDATION) {

            throw new ProtectedMarketDataAccessException(
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }

        TuningPeriod tuningPeriod =
                requireRegisteredTuningPeriod(
                        jdbcTemplate,
                        validationPeriod.strategyVersion()
                );

        Instant tuningStart =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                tuningPeriod.periodStart()
                        );

        Instant tuningEndExclusive =
                UsEquityTradingCalendar
                        .endExclusiveAfter(
                                tuningPeriod.periodEnd()
                        );

        Instant validationStart =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                validationPeriod.periodStart()
                        );

        /*
         * Tuning registration must not overlap the validation
         * evaluation period.
         */
        if (!tuningPeriod.periodEnd().isBefore(
                validationPeriod.periodStart())) {

            refuseWarmUpRange();
        }

        /*
         * Warm-up requests use [from, to).
         *
         * A zero-length request does not constitute valid
         * warm-up history.
         */
        if (!to.isAfter(
                from
        )
                || from.isBefore(
                tuningStart
        )
                || to.isAfter(
                tuningEndExclusive
        )
                || to.isAfter(
                validationStart
        )) {

            refuseWarmUpRange();
        }

        /*
         * Even a registered tuning range cannot become a
         * bypass for some other protected target.
         */
        List<ProtectedPeriod> overlappingProtectedPeriods =
                findOverlappingProtectedPeriods(
                        jdbcTemplate,
                        tradingDate(
                                from
                        ),
                        lastRequestedTradingDate(
                                from,
                                to
                        )
                );

        if (!overlappingProtectedPeriods.isEmpty()) {

            refuseWarmUpRange();
        }
    }

    private JdbcTemplate requireJdbcTemplate() {

        JdbcTemplate jdbcTemplate =
                jdbcTemplateSupplier.get();

        if (jdbcTemplate == null) {

            throw new ProtectedMarketDataAccessException(
                    DURABLE_STORE_REQUIRED
            );
        }

        return jdbcTemplate;
    }

    private List<ProtectedPeriod>
    findOverlappingProtectedPeriods(
            JdbcTemplate jdbcTemplate,
            LocalDate requestedStart,
            LocalDate requestedEnd) {

        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    strategy_version,
                    protected_period_id,
                    protected_period_type,
                    period_start,
                    period_end,
                    state,
                    run_id,
                    instance_id,
                    configuration_hash
                FROM protected_run_guard
                WHERE period_start <= ?
                  AND period_end >= ?
                  AND protected_period_type IN (
                      'VALIDATION',
                      'HOLDOUT'
                  )
                ORDER BY period_start, id
                """,
                this::mapProtectedPeriod,
                requestedEnd,
                requestedStart
        );
    }

    private ProtectedPeriod requireAuthorizedPeriod(
            JdbcTemplate jdbcTemplate,
            ProtectedMarketDataAuthorization authorization) {

        List<ProtectedPeriod> matching =
                jdbcTemplate.query(
                        """
                        SELECT
                            id,
                            strategy_version,
                            protected_period_id,
                            protected_period_type,
                            period_start,
                            period_end,
                            state,
                            run_id,
                            instance_id,
                            configuration_hash
                        FROM protected_run_guard
                        WHERE id = ?
                        """,
                        this::mapProtectedPeriod,
                        authorization.guardId()
                );

        if (matching.size() != 1) {

            throw new ProtectedMarketDataAccessException(
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }

        ProtectedPeriod protectedPeriod =
                matching.getFirst();

        /*
         * Only an acquired, not-yet-exposed run may open
         * protected market data.
         */
        if (protectedPeriod.state()
                != ProtectedRunState.RUNNING_UNEXPOSED) {

            throw new ProtectedMarketDataAccessException(
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }

        if (!authorization.runId().equals(
                protectedPeriod.runId())) {

            throw new ProtectedMarketDataAccessException(
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }

        if (!authorization.instanceId().equals(
                protectedPeriod.instanceId())) {

            throw new ProtectedMarketDataAccessException(
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }

        if (!authorization.configurationHash().equals(
                protectedPeriod.configurationHash())) {

            throw new ProtectedMarketDataAccessException(
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }

        /*
         * Confirm that the acquired guard still agrees with
         * its most recent immutable ledger event.
         */
        requireLedgerAgreement(
                jdbcTemplate,
                protectedPeriod
        );

        return protectedPeriod;
    }

    private void requireLedgerAgreement(
            JdbcTemplate jdbcTemplate,
            ProtectedPeriod protectedPeriod) {

        List<LedgerOwnership> ledgerEvents =
                jdbcTemplate.query(
                        """
                        SELECT
                            new_state,
                            run_id,
                            instance_id,
                            configuration_hash
                        FROM protected_run_event_ledger
                        WHERE guard_id = ?
                        ORDER BY sequence_number DESC
                        LIMIT 1
                        """,
                        (resultSet, rowNumber) ->
                                new LedgerOwnership(
                                        ProtectedRunState.valueOf(
                                                resultSet.getString(
                                                        "new_state"
                                                )
                                        ),
                                        resultSet.getObject(
                                                "run_id",
                                                UUID.class
                                        ),
                                        resultSet.getObject(
                                                "instance_id",
                                                UUID.class
                                        ),
                                        resultSet.getString(
                                                "configuration_hash"
                                        )
                                ),
                        protectedPeriod.guardId()
                );

        if (ledgerEvents.size() != 1) {

            throw new ProtectedMarketDataAccessException(
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }

        LedgerOwnership ledger =
                ledgerEvents.getFirst();

        if (ledger.state()
                != protectedPeriod.state()
                || !protectedPeriod.runId().equals(
                ledger.runId())
                || !protectedPeriod.instanceId().equals(
                ledger.instanceId())
                || !protectedPeriod.configurationHash().equals(
                ledger.configurationHash())) {

            throw new ProtectedMarketDataAccessException(
                    GOVERNANCE_INTEGRITY_MISMATCH
            );
        }
    }

    /**
     * A matching explicit holdout authorization must exist
     * in the separate durable authorization table.
     */
    private void requireExplicitHoldoutAuthorization(
            JdbcTemplate jdbcTemplate,
            ProtectedPeriod protectedPeriod) {

        Integer authorizationCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM protected_holdout_authorization
                        WHERE guard_id = ?
                          AND strategy_version = ?
                          AND protected_period_id = ?
                          AND authorization_timestamp IS NOT NULL
                        """,
                        Integer.class,
                        protectedPeriod.guardId(),
                        protectedPeriod.strategyVersion(),
                        protectedPeriod.protectedPeriodId()
                );

        if (authorizationCount == null
                || authorizationCount != 1) {

            throw new ProtectedMarketDataAccessException(
                    HOLDOUT_AUTHORIZATION_REQUIRED
            );
        }
    }

    private TuningPeriod requireRegisteredTuningPeriod(
            JdbcTemplate jdbcTemplate,
            String strategyVersion) {

        List<TuningPeriod> tuningPeriods =
                jdbcTemplate.query(
                        """
                        SELECT
                            period_start,
                            period_end
                        FROM protected_tuning_period
                        WHERE strategy_version = ?
                        """,
                        (resultSet, rowNumber) ->
                                new TuningPeriod(
                                        resultSet.getObject(
                                                "period_start",
                                                LocalDate.class
                                        ),
                                        resultSet.getObject(
                                                "period_end",
                                                LocalDate.class
                                        )
                                ),
                        strategyVersion
                );

        if (tuningPeriods.size() != 1) {

            throw new ProtectedMarketDataAccessException(
                    WARM_UP_OUTSIDE_TUNING_PERIOD
            );
        }

        return tuningPeriods.getFirst();
    }

    private ProtectedPeriod mapProtectedPeriod(
            ResultSet resultSet,
            int rowNumber) throws SQLException {

        return new ProtectedPeriod(
                resultSet.getLong(
                        "id"
                ),
                resultSet.getString(
                        "strategy_version"
                ),
                resultSet.getString(
                        "protected_period_id"
                ),
                ProtectedPeriodType.valueOf(
                        resultSet.getString(
                                "protected_period_type"
                        )
                ),
                resultSet.getObject(
                        "period_start",
                        LocalDate.class
                ),
                resultSet.getObject(
                        "period_end",
                        LocalDate.class
                ),
                ProtectedRunState.valueOf(
                        resultSet.getString(
                                "state"
                        )
                ),
                resultSet.getObject(
                        "run_id",
                        UUID.class
                ),
                resultSet.getObject(
                        "instance_id",
                        UUID.class
                ),
                resultSet.getString(
                        "configuration_hash"
                )
        );
    }

    private String refusalReasonFor(
            ProtectedPeriod protectedPeriod) {

        return switch (
                protectedPeriod.periodType()
                ) {

            case VALIDATION ->
                    PROTECTED_VALIDATION_PERIOD;

            case HOLDOUT ->
                    PROTECTED_HOLDOUT_PERIOD;
        };
    }

    private LocalDate tradingDate(
            Instant instant) {

        return UsEquityTradingCalendar
                .tradingDate(
                        instant
                );
    }

    private LocalDate lastRequestedTradingDate(
            Instant from,
            Instant to) {

        /*
         * Protected periods are defined by US-equity trading
         * dates in America/New_York.
         *
         * MarshallAI market-data ranges use:
         *
         *     [from, to)
         *
         * When the exclusive end is exactly at the start of a
         * New York calendar date, that date is not part of the
         * request. The last requested trading date is therefore
         * the previous valid market day.
         *
         * If the exclusive end falls during a trading day, that
         * trading date is part of the request.
         *
         * If the exclusive end falls on a weekend or market
         * holiday, the last requested trading date is the most
         * recent valid market day before it.
         */
        if (!to.isAfter(
                from
        )) {

            return tradingDate(
                    to
            );
        }

        LocalDate toTradingDate =
                tradingDate(
                        to
                );

        Instant startOfToTradingDate =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                toTradingDate
                        );

        if (to.equals(
                startOfToTradingDate
        )
                || !UsEquityTradingCalendar
                .isTradingDay(
                        toTradingDate
                )) {

            return UsEquityTradingCalendar
                    .lastTradingDateBefore(
                            toTradingDate
                    );
        }

        return toTradingDate;
    }

    private void refuseWarmUpRange() {

        throw new ProtectedMarketDataAccessException(
                WARM_UP_OUTSIDE_TUNING_PERIOD
        );
    }

    private void requireRange(
            Instant from,
            Instant to) {

        if (from == null) {

            throw new IllegalArgumentException(
                    "Market-data start cannot be null"
            );
        }

        if (to == null) {

            throw new IllegalArgumentException(
                    "Market-data end cannot be null"
            );
        }

        if (to.isBefore(
                from
        )) {

            throw new IllegalArgumentException(
                    "Market-data end cannot be before start"
            );
        }
    }

    private record ProtectedPeriod(
            long guardId,
            String strategyVersion,
            String protectedPeriodId,
            ProtectedPeriodType periodType,
            LocalDate periodStart,
            LocalDate periodEnd,
            ProtectedRunState state,
            UUID runId,
            UUID instanceId,
            String configurationHash) {
    }

    private record LedgerOwnership(
            ProtectedRunState state,
            UUID runId,
            UUID instanceId,
            String configurationHash) {
    }

    private record TuningPeriod(
            LocalDate periodStart,
            LocalDate periodEnd) {
    }
}