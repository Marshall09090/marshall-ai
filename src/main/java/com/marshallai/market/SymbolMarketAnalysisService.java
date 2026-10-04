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

    private static final int DEFAULT_CANDLE_COUNT = 30;

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

        return analyze(symbol, DEFAULT_CANDLE_COUNT);
    }

    public SymbolMarketAnalysisResult analyze(
            String symbol,
            int candleCount) {

        validateRequest(symbol, candleCount);

        List<MarketCandle> candles =
                marketDataProvider.getHistoricalCandles(
                        symbol,
                        candleCount
                );

        if (candles == null || candles.isEmpty()) {
            throw new IllegalStateException(
                    "No market candles were returned for symbol: "
                            + symbol
            );
        }

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
    }

    public record SymbolMarketAnalysisResult(
            String symbol,
            List<MarketCandle> candles,
            MarketAnalysisResult marketAnalysis,
            TechnicalSignal technicalSignal) {
    }
}