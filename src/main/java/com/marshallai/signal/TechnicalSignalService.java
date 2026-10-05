package com.marshallai.signal;

import com.marshallai.indicator.IndicatorService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TechnicalSignalService {

    private static final int RSI_PERIOD = 14;
    private static final int EMA_PERIOD = 20;

    private static final int MACD_FAST_PERIOD = 12;
    private static final int MACD_SLOW_PERIOD = 26;

    private static final int ADX_PERIOD = 14;

    private static final BigDecimal RSI_BULLISH_LEVEL =
            BigDecimal.valueOf(55);

    private static final BigDecimal RSI_BEARISH_LEVEL =
            BigDecimal.valueOf(45);

    private static final BigDecimal ADX_TREND_THRESHOLD =
            BigDecimal.valueOf(25);

    private final IndicatorService indicatorService;

    public TechnicalSignalService(
            IndicatorService indicatorService) {

        this.indicatorService = indicatorService;
    }

    public TechnicalSignal evaluate(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            List<BigDecimal> closingPrices) {

        validatePrices(
                highPrices,
                lowPrices,
                closingPrices
        );

        int bullishWeightedScore = 0;
        int bearishWeightedScore = 0;

        // =========================
        // RSI
        // =========================

        BigDecimal rsi =
                indicatorService.calculateRsi(
                        closingPrices,
                        RSI_PERIOD
                );

        if (rsi.compareTo(RSI_BULLISH_LEVEL) >= 0) {

            bullishWeightedScore += 1;

        } else if (rsi.compareTo(RSI_BEARISH_LEVEL) <= 0) {

            bearishWeightedScore += 1;
        }

        // =========================
        // MACD
        // =========================

        BigDecimal macd =
                indicatorService.calculateMacd(
                        closingPrices,
                        MACD_FAST_PERIOD,
                        MACD_SLOW_PERIOD
                );

        if (macd.compareTo(BigDecimal.ZERO) > 0) {

            bullishWeightedScore += 2;

        } else if (macd.compareTo(BigDecimal.ZERO) < 0) {

            bearishWeightedScore += 2;
        }

        // =========================
        // EMA TREND
        // =========================

        BigDecimal ema =
                indicatorService.calculateEma(
                        closingPrices,
                        EMA_PERIOD
                );

        BigDecimal latestClose =
                closingPrices.get(
                        closingPrices.size() - 1
                );

        if (latestClose.compareTo(ema) > 0) {

            bullishWeightedScore += 1;

        } else if (latestClose.compareTo(ema) < 0) {

            bearishWeightedScore += 1;
        }

        // =========================
        // ADX + DIRECTIONAL INDEX
        // =========================

        BigDecimal adx =
                indicatorService.calculateAdx(
                        highPrices,
                        lowPrices,
                        closingPrices,
                        ADX_PERIOD
                );

        if (adx.compareTo(ADX_TREND_THRESHOLD) >= 0) {

            BigDecimal positiveDi =
                    indicatorService.calculatePositiveDi(
                            highPrices,
                            lowPrices,
                            closingPrices,
                            ADX_PERIOD
                    );

            BigDecimal negativeDi =
                    indicatorService.calculateNegativeDi(
                            highPrices,
                            lowPrices,
                            closingPrices,
                            ADX_PERIOD
                    );

            if (positiveDi.compareTo(negativeDi) > 0) {

                bullishWeightedScore += 2;

            } else if (negativeDi.compareTo(positiveDi) > 0) {

                bearishWeightedScore += 2;
            }
        }

        // =========================
        // TECHNICAL CONFIDENCE
        // =========================

        int totalWeightedScore =
                bullishWeightedScore
                        + bearishWeightedScore;

        /*
         * No directional evidence means no technical confidence.
         *
         * This explicit guard prevents a 0 bullish / 0 bearish
         * technical result from ever being represented as 100%
         * confidence.
         */
        if (totalWeightedScore == 0) {

            return new TechnicalSignal(
                    0,
                    0,
                    0
            );
        }

        int dominantWeightedScore =
                Math.max(
                        bullishWeightedScore,
                        bearishWeightedScore
                );

        int confidencePercent =
                (int) Math.round(
                        dominantWeightedScore
                                * 100.0
                                / totalWeightedScore
                );

        return new TechnicalSignal(
                bullishWeightedScore,
                bearishWeightedScore,
                confidencePercent
        );
    }

    private void validatePrices(
            List<BigDecimal> highPrices,
            List<BigDecimal> lowPrices,
            List<BigDecimal> closingPrices) {

        if (highPrices == null
                || lowPrices == null
                || closingPrices == null) {

            throw new IllegalArgumentException(
                    "Market prices cannot be null"
            );
        }

        if (highPrices.size() != lowPrices.size()
                || highPrices.size() != closingPrices.size()) {

            throw new IllegalArgumentException(
                    "High, low and closing prices must have equal sizes"
            );
        }

        if (closingPrices.size() < MACD_SLOW_PERIOD + 1) {

            throw new IllegalArgumentException(
                    "At least 27 market prices are required"
            );
        }
    }
}