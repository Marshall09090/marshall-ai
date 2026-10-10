package com.marshallai.governance.research;

import com.marshallai.governance.exposure.ProtectedTuningPeriodRepository;
import com.marshallai.market.MarketCandle;
import com.marshallai.market.MarketDataProvider;
import com.marshallai.market.calendar.UsEquityTradingCalendar;
import com.marshallai.persistence.run.RunRecord;
import com.marshallai.persistence.run.RunService;
import com.marshallai.persistence.run.RunType;
import com.marshallai.risk.PositionSizeResult;
import com.marshallai.risk.PositionSizingService;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CapitalSensitivityResearchService {

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    private static final int RATE_SCALE =
            8;

    private final MarketDataProvider marketDataProvider;

    private final ProtectedTuningPeriodRepository
            tuningPeriodRepository;

    private final PositionSizingService
            positionSizingService;

    private final RunService
            runService;

    public CapitalSensitivityResearchService(
            MarketDataProvider marketDataProvider,
            ProtectedTuningPeriodRepository tuningPeriodRepository,
            PositionSizingService positionSizingService,
            RunService runService) {

        if (marketDataProvider == null) {
            throw new IllegalArgumentException(
                    "MarketDataProvider cannot be null"
            );
        }

        if (tuningPeriodRepository == null) {
            throw new IllegalArgumentException(
                    "ProtectedTuningPeriodRepository cannot be null"
            );
        }

        if (positionSizingService == null) {
            throw new IllegalArgumentException(
                    "PositionSizingService cannot be null"
            );
        }

        if (runService == null) {
            throw new IllegalArgumentException(
                    "RunService cannot be null"
            );
        }

        this.marketDataProvider =
                marketDataProvider;

        this.tuningPeriodRepository =
                tuningPeriodRepository;

        this.positionSizingService =
                positionSizingService;

        this.runService =
                runService;
    }

    /**
     * Evaluates planned-live capital sensitivity strictly against
     * the registered tuning period.
     *
     * This is RESEARCH.
     *
     * It does not acquire validation or holdout targets and does
     * not produce a governed PASS/FAIL verdict.
     *
     * Registered market dates are interpreted using the
     * US-equity America/New_York trading calendar.
     */
    public CapitalSensitivityResult evaluate(
            String strategyVersion,
            String buildVersion,
            String symbol,
            String timeframe,
            BigDecimal baselineCapital,
            BigDecimal plannedCapital,
            BigDecimal riskPercent,
            BigDecimal stopLossPercent,
            String dataSource,
            String dataFeed,
            String adjustmentMethod) {

        requireText(
                strategyVersion,
                "Strategy version"
        );

        requireText(
                buildVersion,
                "Build version"
        );

        requireText(
                symbol,
                "Symbol"
        );

        requireText(
                timeframe,
                "Timeframe"
        );

        requirePositive(
                baselineCapital,
                "Baseline capital"
        );

        requirePositive(
                plannedCapital,
                "Planned capital"
        );

        requirePositive(
                riskPercent,
                "Risk percent"
        );

        requirePositive(
                stopLossPercent,
                "Stop-loss percent"
        );

        if (riskPercent.compareTo(
                ONE_HUNDRED
        ) > 0) {

            throw new IllegalArgumentException(
                    "Risk percent cannot exceed 100"
            );
        }

        if (stopLossPercent.compareTo(
                ONE_HUNDRED
        ) >= 0) {

            throw new IllegalArgumentException(
                    "Stop-loss percent must be less than 100"
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

        /*
         * This is the only market-data request made by the service.
         *
         * Validation and holdout dates are never supplied to the
         * provider.
         */
        List<MarketCandle> candles =
                marketDataProvider.getHistoricalCandles(
                        symbol,
                        timeframe,
                        tuningFrom,
                        tuningToExclusive
                );

        List<MarketCandle> tuningCandles =
                requireCandlesInsideTuningPeriod(
                        candles,
                        tuningFrom,
                        tuningToExclusive
                );

        if (tuningCandles.isEmpty()) {

            throw new IllegalStateException(
                    "Capital sensitivity requires tuning-period candles"
            );
        }

        int baselineZeroQuantityCount =
                0;

        int plannedZeroQuantityCount =
                0;

        List<CapitalSensitivityObservation> observations =
                new ArrayList<>();

        for (MarketCandle candle :
                tuningCandles) {

            BigDecimal entryPrice =
                    candle.close();

            requirePositive(
                    entryPrice,
                    "Candle close price"
            );

            BigDecimal stopLossPrice =
                    calculateStopLossPrice(
                            entryPrice,
                            stopLossPercent
                    );

            PositionSizeResult baselinePosition =
                    positionSizingService.calculate(
                            baselineCapital,
                            riskPercent,
                            entryPrice,
                            stopLossPrice
                    );

            PositionSizeResult plannedPosition =
                    positionSizingService.calculate(
                            plannedCapital,
                            riskPercent,
                            entryPrice,
                            stopLossPrice
                    );

            if (baselinePosition.getQuantity()
                    <= 0) {

                baselineZeroQuantityCount++;
            }

            if (plannedPosition.getQuantity()
                    <= 0) {

                plannedZeroQuantityCount++;
            }

            observations.add(
                    new CapitalSensitivityObservation(
                            candle.timestamp(),
                            entryPrice,
                            stopLossPrice,
                            baselinePosition.getQuantity(),
                            plannedPosition.getQuantity()
                    )
            );
        }

        BigDecimal zeroQuantitySkipRate =
                BigDecimal.valueOf(
                                plannedZeroQuantityCount
                        )
                        .divide(
                                BigDecimal.valueOf(
                                        tuningCandles.size()
                                ),
                                RATE_SCALE,
                                RoundingMode.HALF_UP
                        );

        String marketDataFingerprint =
                fingerprint(
                        tuningCandles
                );

        Map<String, Object> configurationSnapshot =
                new LinkedHashMap<>();

        configurationSnapshot.put(
                "researchType",
                "CAPITAL_SENSITIVITY"
        );

        configurationSnapshot.put(
                "baselineCapital",
                baselineCapital
        );

        configurationSnapshot.put(
                "plannedCapital",
                plannedCapital
        );

        configurationSnapshot.put(
                "riskPercent",
                riskPercent
        );

        configurationSnapshot.put(
                "stopLossPercent",
                stopLossPercent
        );

        configurationSnapshot.put(
                "tuningPeriodStart",
                tuningPeriod.periodStart()
                        .toString()
        );

        configurationSnapshot.put(
                "tuningPeriodEnd",
                tuningPeriod.periodEnd()
                        .toString()
        );

        configurationSnapshot.put(
                "marketDataCandleCount",
                tuningCandles.size()
        );

        configurationSnapshot.put(
                "baselineZeroQuantityCount",
                baselineZeroQuantityCount
        );

        configurationSnapshot.put(
                "plannedZeroQuantityCount",
                plannedZeroQuantityCount
        );

        configurationSnapshot.put(
                "zeroQuantitySkipRate",
                zeroQuantitySkipRate
        );

        RunRecord runRecord =
                runService.startResearchRun(
                        strategyVersion,
                        buildVersion,
                        symbol,
                        timeframe,
                        dataSource,
                        dataFeed,
                        adjustmentMethod,
                        marketDataFingerprint,
                        Instant.now(),
                        configurationSnapshot
                );

        if (runRecord.runType()
                != RunType.RESEARCH) {

            throw new IllegalStateException(
                    "Capital sensitivity must be recorded as RESEARCH"
            );
        }

        return new CapitalSensitivityResult(
                runRecord,
                tuningPeriod.periodStart(),
                tuningPeriod.periodEnd(),
                baselineCapital,
                plannedCapital,
                tuningCandles.size(),
                baselineZeroQuantityCount,
                plannedZeroQuantityCount,
                zeroQuantitySkipRate,
                observations
        );
    }

    private static BigDecimal calculateStopLossPrice(
            BigDecimal entryPrice,
            BigDecimal stopLossPercent) {

        BigDecimal remainingPercent =
                ONE_HUNDRED.subtract(
                        stopLossPercent
                );

        BigDecimal stopLossPrice =
                entryPrice
                        .multiply(
                                remainingPercent
                        )
                        .divide(
                                ONE_HUNDRED,
                                8,
                                RoundingMode.HALF_UP
                        );

        if (stopLossPrice.signum()
                <= 0) {

            throw new IllegalArgumentException(
                    "Calculated stop-loss price must be greater than zero"
            );
        }

        if (stopLossPrice.compareTo(
                entryPrice
        ) == 0) {

            throw new IllegalArgumentException(
                    "Calculated stop-loss price cannot equal entry price"
            );
        }

        return stopLossPrice;
    }

    private static List<MarketCandle>
    requireCandlesInsideTuningPeriod(
            List<MarketCandle> candles,
            Instant tuningFrom,
            Instant tuningToExclusive) {

        if (candles == null) {

            throw new IllegalStateException(
                    "Market-data provider returned null candles"
            );
        }

        List<MarketCandle> validated =
                new ArrayList<>(
                        candles.size()
                );

        for (MarketCandle candle :
                candles) {

            if (candle == null) {

                throw new IllegalStateException(
                        "Market-data provider returned a null candle"
                );
            }

            Instant timestamp =
                    candle.timestamp();

            if (timestamp == null) {

                throw new IllegalStateException(
                        "Market-data candle timestamp cannot be null"
                );
            }

            if (timestamp.isBefore(
                    tuningFrom
            ) || !timestamp.isBefore(
                    tuningToExclusive
            )) {

                throw new IllegalStateException(
                        "Capital sensitivity received market data "
                                + "outside the registered tuning period: "
                                + timestamp
                );
            }

            validated.add(
                    candle
            );
        }

        validated.sort(
                java.util.Comparator.comparing(
                        MarketCandle::timestamp
                )
        );

        return List.copyOf(
                validated
        );
    }

    private static String fingerprint(
            List<MarketCandle> candles) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            for (MarketCandle candle :
                    candles) {

                String canonical =
                        candle.symbol()
                                + "|"
                                + candle.assetType()
                                + "|"
                                + candle.timeframe()
                                + "|"
                                + candle.open()
                                + "|"
                                + candle.high()
                                + "|"
                                + candle.low()
                                + "|"
                                + candle.close()
                                + "|"
                                + candle.volume()
                                + "|"
                                + candle.timestamp()
                                + "\n";

                digest.update(
                        canonical.getBytes(
                                StandardCharsets.UTF_8
                        )
                );
            }

            return HexFormat.of()
                    .formatHex(
                            digest.digest()
                    );

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
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

    private static void requirePositive(
            BigDecimal value,
            String fieldName) {

        if (value == null
                || value.signum() <= 0) {

            throw new IllegalArgumentException(
                    fieldName
                            + " must be greater than zero"
            );
        }
    }

    public record CapitalSensitivityObservation(
            Instant timestamp,
            BigDecimal entryPrice,
            BigDecimal stopLossPrice,
            int baselineQuantity,
            int plannedQuantity) {

        public CapitalSensitivityObservation {

            if (timestamp == null) {
                throw new IllegalArgumentException(
                        "Observation timestamp cannot be null"
                );
            }

            if (entryPrice == null
                    || stopLossPrice == null) {

                throw new IllegalArgumentException(
                        "Observation prices cannot be null"
                );
            }

            if (baselineQuantity < 0
                    || plannedQuantity < 0) {

                throw new IllegalArgumentException(
                        "Position quantities cannot be negative"
                );
            }
        }

        public boolean plannedQuantitySkipped() {

            return plannedQuantity == 0;
        }
    }

    public record CapitalSensitivityResult(
            RunRecord runRecord,
            LocalDate tuningPeriodStart,
            LocalDate tuningPeriodEnd,
            BigDecimal baselineCapital,
            BigDecimal plannedCapital,
            int marketDataCandleCount,
            int baselineZeroQuantityCount,
            int plannedZeroQuantityCount,
            BigDecimal zeroQuantitySkipRate,
            List<CapitalSensitivityObservation> observations) {

        public CapitalSensitivityResult {

            if (runRecord == null) {
                throw new IllegalArgumentException(
                        "Research run record cannot be null"
                );
            }

            if (runRecord.runType()
                    != RunType.RESEARCH) {

                throw new IllegalArgumentException(
                        "Capital sensitivity result must belong to a RESEARCH run"
                );
            }

            if (tuningPeriodStart == null
                    || tuningPeriodEnd == null) {

                throw new IllegalArgumentException(
                        "Tuning period cannot be null"
                );
            }

            if (baselineCapital == null
                    || plannedCapital == null) {

                throw new IllegalArgumentException(
                        "Capital values cannot be null"
                );
            }

            if (marketDataCandleCount <= 0) {

                throw new IllegalArgumentException(
                        "Market-data candle count must be greater than zero"
                );
            }

            if (baselineZeroQuantityCount < 0
                    || plannedZeroQuantityCount < 0) {

                throw new IllegalArgumentException(
                        "Zero-quantity counts cannot be negative"
                );
            }

            if (zeroQuantitySkipRate == null
                    || zeroQuantitySkipRate.signum() < 0
                    || zeroQuantitySkipRate.compareTo(
                    BigDecimal.ONE
            ) > 0) {

                throw new IllegalArgumentException(
                        "Zero-quantity skip rate must be between zero and one"
                );
            }

            observations =
                    observations == null
                            ? List.of()
                            : List.copyOf(
                            observations
                    );
        }

        /**
         * Research sensitivity never emits a governed verdict.
         */
        public String governedVerdict() {

            return null;
        }
    }
}