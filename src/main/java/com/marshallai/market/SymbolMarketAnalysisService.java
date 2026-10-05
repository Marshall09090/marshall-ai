package com.marshallai.market;

import com.marshallai.analysis.MarketAnalysisResult;
import com.marshallai.analysis.MarketAnalysisService;
import com.marshallai.signal.TechnicalSignal;
import com.marshallai.signal.TechnicalSignalService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class SymbolMarketAnalysisService {

    /*
     * MarshallAI requires enough completed historical candles
     * for technical-analysis warm-up before producing a signal.
     */
    private static final int MINIMUM_CANDLE_COUNT = 200;

    /*
     * Symbol analysis uses the same safe minimum by default.
     */
    private static final int DEFAULT_CANDLE_COUNT = MINIMUM_CANDLE_COUNT;

    private final MarketDataProvider marketDataProvider;
    private final MarketAnalysisService marketAnalysisService;
    private final TechnicalSignalService technicalSignalService;

    public SymbolMarketAnalysisService(
            MarketDataProvider marketDataProvider,
            MarketAnalysisService marketAnalysisService,
            TechnicalSignalService technicalSignalService) {

        this.marketDataProvider = marketDataProvider;
        this.marketAnalysisService = marketAnalysisService;
        this.technicalSignalService = technicalSignalService;
    }

    public SymbolMarketAnalysisResult analyze(String symbol) {

        return analyze(
                symbol,
                DEFAULT_CANDLE_COUNT
        );
    }

    public SymbolMarketAnalysisResult analyze(
            String symbol,
            int candleCount) {

        validateRequest(
                symbol,
                candleCount
        );

        List<MarketCandle> candles =
                marketDataProvider.getHistoricalCandles(
                        symbol,
                        candleCount
                );

        validateReturnedCandles(
                symbol,
                candles
        );

        List<BigDecimal> highPrices =
                candles.stream()
                        .map(MarketCandle::high)
                        .toList();

        List<BigDecimal> lowPrices =
                candles.stream()
                        .map(MarketCandle::low)
                        .toList();

        List<BigDecimal> closingPrices =
                candles.stream()
                        .map(MarketCandle::close)
                        .toList();

        MarketAnalysisResult marketAnalysis =
                marketAnalysisService.analyze(
                        closingPrices
                );

        TechnicalSignal technicalSignal =
                technicalSignalService.evaluate(
                        highPrices,
                        lowPrices,
                        closingPrices
                );

        return new SymbolMarketAnalysisResult(
                symbol.trim().toUpperCase(),
                candles,
                marketAnalysis,
                technicalSignal
        );
    }

    private void validateRequest(
            String symbol,
            int candleCount) {

        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException(
                    "Market symbol cannot be blank"
            );
        }

        if (candleCount <= 0) {
            throw new IllegalArgumentException(
                    "Candle count must be greater than zero"
            );
        }

        if (candleCount < MINIMUM_CANDLE_COUNT) {
            throw new IllegalArgumentException(
                    "Insufficient historical market data requested. "
                            + "At least "
                            + MINIMUM_CANDLE_COUNT
                            + " candles are required"
            );
        }
    }

    private void validateReturnedCandles(
            String symbol,
            List<MarketCandle> candles) {

        if (candles == null || candles.isEmpty()) {
            throw new IllegalStateException(
                    "No market candles were returned for symbol: "
                            + symbol
            );
        }

        if (candles.size() < MINIMUM_CANDLE_COUNT) {
            throw new IllegalStateException(
                    "Insufficient historical market data returned for symbol "
                            + symbol.trim().toUpperCase()
                            + ". Required at least "
                            + MINIMUM_CANDLE_COUNT
                            + " candles but received "
                            + candles.size()
            );
        }
    }

    public record SymbolMarketAnalysisResult(
            String symbol,
            List<MarketCandle> candles,
            MarketAnalysisResult marketAnalysis,
            TechnicalSignal technicalSignal) {
    }
}