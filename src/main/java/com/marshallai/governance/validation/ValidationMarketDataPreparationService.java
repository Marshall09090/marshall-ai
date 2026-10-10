package com.marshallai.governance.validation;

import com.marshallai.governance.exposure.ProtectedTuningPeriodRepository;
import com.marshallai.market.MarketCandle;
import com.marshallai.market.MarketDataProvider;
import com.marshallai.market.calendar.UsEquityTradingCalendar;
import com.marshallai.market.protection.ProtectedMarketDataAccessContext;
import com.marshallai.market.protection.ProtectedMarketDataAuthorization;
import com.marshallai.market.protection.ProtectedMarketDataBoundary;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class ValidationMarketDataPreparationService {

    private final MarketDataProvider marketDataProvider;

    private final ProtectedMarketDataBoundary
            protectedMarketDataBoundary;

    private final ProtectedTuningPeriodRepository
            tuningPeriodRepository;

    public ValidationMarketDataPreparationService(
            MarketDataProvider marketDataProvider,
            ProtectedMarketDataBoundary protectedMarketDataBoundary,
            ProtectedTuningPeriodRepository tuningPeriodRepository) {

        if (marketDataProvider == null) {
            throw new IllegalArgumentException(
                    "MarketDataProvider cannot be null"
            );
        }

        if (protectedMarketDataBoundary == null) {
            throw new IllegalArgumentException(
                    "ProtectedMarketDataBoundary cannot be null"
            );
        }

        if (tuningPeriodRepository == null) {
            throw new IllegalArgumentException(
                    "ProtectedTuningPeriodRepository cannot be null"
            );
        }

        this.marketDataProvider =
                marketDataProvider;

        this.protectedMarketDataBoundary =
                protectedMarketDataBoundary;

        this.tuningPeriodRepository =
                tuningPeriodRepository;
    }

    /**
     * Prepares the market-data dataset used by governed validation.
     *
     * The resulting dataset contains:
     *
     * 1. Exactly warmUpBarCount candles from the registered
     *    tuning period, marked WARM_UP_ONLY.
     *
     * 2. Validation-period candles marked EVALUATION.
     *
     * Warm-up candles may participate in indicator calculations,
     * but they are not eligible to produce validation trades.
     *
     * The original validation evaluation period is never widened.
     *
     * All date boundaries are defined using the US-equity
     * America/New_York trading calendar.
     */
    public PreparedValidationDataset prepare(
            String strategyVersion,
            String symbol,
            String timeframe,
            int warmUpBarCount,
            LocalDate validationStart,
            LocalDate validationEnd,
            ProtectedMarketDataAuthorization authorization) {

        requireText(
                strategyVersion,
                "Strategy version"
        );

        requireText(
                symbol,
                "Market symbol"
        );

        requireText(
                timeframe,
                "Timeframe"
        );

        if (warmUpBarCount <= 0) {
            throw new IllegalArgumentException(
                    "Warm-up bar count must be greater than zero"
            );
        }

        if (validationStart == null) {
            throw new IllegalArgumentException(
                    "Validation start cannot be null"
            );
        }

        if (validationEnd == null) {
            throw new IllegalArgumentException(
                    "Validation end cannot be null"
            );
        }

        if (validationStart.isAfter(validationEnd)) {
            throw new IllegalArgumentException(
                    "Validation start cannot be after validation end"
            );
        }

        if (authorization == null) {
            throw new IllegalArgumentException(
                    "Protected market-data authorization cannot be null"
            );
        }

        ProtectedTuningPeriodRepository.TuningPeriodSnapshot
                tuningPeriod =
                tuningPeriodRepository.requireTuningPeriod(
                        strategyVersion
                );

        Instant tuningFrom =
                startOfDay(
                        tuningPeriod.periodStart()
                );

        Instant tuningToExclusive =
                endExclusive(
                        tuningPeriod.periodEnd()
                );

        Instant validationFrom =
                startOfDay(
                        validationStart
                );

        Instant validationToExclusive =
                endExclusive(
                        validationEnd
                );

        List<MarketCandle> tuningCandles;

        List<MarketCandle> validationCandles;

        /*
         * One governed authorization owns both preparation reads.
         *
         * The warm-up boundary separately proves that tuning
         * history is legal for this validation run.
         *
         * The real market-data provider then performs its normal
         * protected-date authorization for validation data.
         */
        try (ProtectedMarketDataAccessContext.AuthorizationScope
                     scope =
                     ProtectedMarketDataAccessContext.authorize(
                             authorization
                     )) {

            protectedMarketDataBoundary
                    .authorizeWarmUpMarketDataRequest(
                            tuningFrom,
                            tuningToExclusive
                    );

            tuningCandles =
                    marketDataProvider.getHistoricalCandles(
                            symbol,
                            timeframe,
                            tuningFrom,
                            tuningToExclusive
                    );

            validationCandles =
                    marketDataProvider.getHistoricalCandles(
                            symbol,
                            timeframe,
                            validationFrom,
                            validationToExclusive
                    );
        }

        List<MarketCandle> normalizedTuning =
                requireCandlesInsideRange(
                        tuningCandles,
                        tuningFrom,
                        tuningToExclusive,
                        "tuning"
                );

        List<MarketCandle> normalizedValidation =
                requireCandlesInsideRange(
                        validationCandles,
                        validationFrom,
                        validationToExclusive,
                        "validation"
                );

        if (normalizedTuning.size()
                < warmUpBarCount) {

            throw new IllegalStateException(
                    "Insufficient tuning-period candles for validation "
                            + "warm-up. Required "
                            + warmUpBarCount
                            + " but received "
                            + normalizedTuning.size()
            );
        }

        /*
         * Use the newest N tuning-period candles.
         *
         * These are the candles immediately preceding validation
         * when the registered tuning period ends at the validation
         * boundary.
         */
        List<MarketCandle> selectedWarmUpCandles =
                List.copyOf(
                        normalizedTuning.subList(
                                normalizedTuning.size()
                                        - warmUpBarCount,
                                normalizedTuning.size()
                        )
                );

        List<PreparedValidationCandle> prepared =
                new ArrayList<>(
                        selectedWarmUpCandles.size()
                                + normalizedValidation.size()
                );

        for (MarketCandle candle :
                selectedWarmUpCandles) {

            prepared.add(
                    new PreparedValidationCandle(
                            candle,
                            CandleUsage.WARM_UP_ONLY
                    )
            );
        }

        for (MarketCandle candle :
                normalizedValidation) {

            prepared.add(
                    new PreparedValidationCandle(
                            candle,
                            CandleUsage.EVALUATION
                    )
            );
        }

        prepared.sort(
                Comparator.comparing(
                        preparedCandle ->
                                preparedCandle
                                        .candle()
                                        .timestamp()
                )
        );

        return new PreparedValidationDataset(
                strategyVersion.trim(),
                symbol.trim(),
                timeframe.trim(),
                validationStart,
                validationEnd,
                warmUpBarCount,
                prepared
        );
    }

    /**
     * Fails closed when a provider returns candles outside
     * the exact range that was requested.
     *
     * This also protects validation preparation when a future
     * provider implementation behaves differently from Alpaca.
     */
    private List<MarketCandle> requireCandlesInsideRange(
            List<MarketCandle> candles,
            Instant from,
            Instant toExclusive,
            String rangeName) {

        if (candles == null) {
            throw new IllegalStateException(
                    "Market-data provider returned null "
                            + rangeName
                            + " candles"
            );
        }

        List<MarketCandle> normalized =
                new ArrayList<>(
                        candles.size()
                );

        for (MarketCandle candle : candles) {

            if (candle == null) {
                throw new IllegalStateException(
                        "Market-data provider returned a null "
                                + rangeName
                                + " candle"
                );
            }

            Instant timestamp =
                    candle.timestamp();

            if (timestamp == null) {
                throw new IllegalStateException(
                        "Market-data provider returned a "
                                + rangeName
                                + " candle without a timestamp"
                );
            }

            if (timestamp.isBefore(from)
                    || !timestamp.isBefore(
                    toExclusive
            )) {

                throw new IllegalStateException(
                        "Market-data provider returned a "
                                + rangeName
                                + " candle outside the requested range: "
                                + timestamp
                );
            }

            normalized.add(
                    candle
            );
        }

        normalized.sort(
                Comparator.comparing(
                        MarketCandle::timestamp
                )
        );

        return List.copyOf(
                normalized
        );
    }

    private static Instant startOfDay(
            LocalDate date) {

        return UsEquityTradingCalendar
                .startOfTradingDate(
                        date
                );
    }

    private static Instant endExclusive(
            LocalDate date) {

        return UsEquityTradingCalendar
                .endExclusiveAfter(
                        date
                );
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

    /**
     * Classification applied by validation preparation.
     *
     * Raw MarketCandle objects deliberately do not carry
     * governance metadata.
     */
    public enum CandleUsage {

        WARM_UP_ONLY,

        EVALUATION
    }

    /**
     * Market candle plus its governed validation purpose.
     */
    public record PreparedValidationCandle(
            MarketCandle candle,
            CandleUsage usage) {

        public PreparedValidationCandle {

            Objects.requireNonNull(
                    candle,
                    "Market candle cannot be null"
            );

            Objects.requireNonNull(
                    usage,
                    "Candle usage cannot be null"
            );
        }

        public boolean isWarmUpOnly() {

            return usage
                    == CandleUsage.WARM_UP_ONLY;
        }

        public boolean isEvaluation() {

            return usage
                    == CandleUsage.EVALUATION;
        }
    }

    /**
     * Immutable governed validation dataset.
     *
     * Indicator calculations may consume allCandles().
     *
     * Trade or verdict logic must use evaluationCandles()
     * or isSignalEligibleForValidation().
     */
    public record PreparedValidationDataset(
            String strategyVersion,
            String symbol,
            String timeframe,
            LocalDate validationStart,
            LocalDate validationEnd,
            int warmUpBarCount,
            List<PreparedValidationCandle> candles) {

        public PreparedValidationDataset {

            Objects.requireNonNull(
                    strategyVersion,
                    "Strategy version cannot be null"
            );

            Objects.requireNonNull(
                    symbol,
                    "Symbol cannot be null"
            );

            Objects.requireNonNull(
                    timeframe,
                    "Timeframe cannot be null"
            );

            Objects.requireNonNull(
                    validationStart,
                    "Validation start cannot be null"
            );

            Objects.requireNonNull(
                    validationEnd,
                    "Validation end cannot be null"
            );

            Objects.requireNonNull(
                    candles,
                    "Prepared candles cannot be null"
            );

            if (warmUpBarCount <= 0) {
                throw new IllegalArgumentException(
                        "Warm-up bar count must be greater than zero"
                );
            }

            candles =
                    List.copyOf(
                            candles
                    );

            long actualWarmUpCount =
                    candles.stream()
                            .filter(
                                    PreparedValidationCandle::isWarmUpOnly
                            )
                            .count();

            if (actualWarmUpCount
                    != warmUpBarCount) {

                throw new IllegalArgumentException(
                        "Prepared dataset must contain exactly "
                                + warmUpBarCount
                                + " warm-up candles but contained "
                                + actualWarmUpCount
                );
            }
        }

        /**
         * All candles used for indicator continuity.
         *
         * Warm-up candles are included here.
         */
        public List<MarketCandle> allCandles() {

            return candles.stream()
                    .map(
                            PreparedValidationCandle::candle
                    )
                    .toList();
        }

        /**
         * Exactly the tuning-period candles classified
         * WARM_UP_ONLY.
         */
        public List<MarketCandle> warmUpCandles() {

            return candles.stream()
                    .filter(
                            PreparedValidationCandle::isWarmUpOnly
                    )
                    .map(
                            PreparedValidationCandle::candle
                    )
                    .toList();
        }

        /**
         * Only candles allowed to participate in governed
         * validation evaluation and trade eligibility.
         */
        public List<MarketCandle> evaluationCandles() {

            return candles.stream()
                    .filter(
                            PreparedValidationCandle::isEvaluation
                    )
                    .map(
                            PreparedValidationCandle::candle
                    )
                    .toList();
        }

        /**
         * Central validation signal gate.
         *
         * A signal tied to a WARM_UP_ONLY candle is never
         * eligible for validation.
         *
         * A signal must correspond to an EVALUATION candle
         * in the prepared dataset.
         */
        public boolean isSignalEligibleForValidation(
                Instant signalTimestamp) {

            if (signalTimestamp == null) {
                return false;
            }

            return candles.stream()
                    .anyMatch(
                            preparedCandle ->
                                    preparedCandle.isEvaluation()
                                            && preparedCandle
                                            .candle()
                                            .timestamp()
                                            .equals(
                                                    signalTimestamp
                                            )
                    );
        }
    }
}