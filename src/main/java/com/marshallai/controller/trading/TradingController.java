package com.marshallai.controller.trading;

import com.marshallai.analysis.MarketAnalysisResult;
import com.marshallai.analysis.MarketAnalysisService;
import com.marshallai.market.SymbolMarketAnalysisService;
import com.marshallai.risk.EventRisk;
import com.marshallai.risk.EventRiskService;
import com.marshallai.risk.PositionSizeResult;
import com.marshallai.risk.PositionSizingService;
import com.marshallai.risk.TradeApprovalResult;
import com.marshallai.risk.TradeApprovalService;
import com.marshallai.signal.MarketSignal;
import com.marshallai.signal.SignalService;
import com.marshallai.signal.TechnicalSignal;
import com.marshallai.signal.TechnicalSignalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/trading")
public class TradingController {

    private static final int MINIMUM_CONFIDENCE_PERCENT = 80;

    private final MarketAnalysisService marketAnalysisService;
    private final TechnicalSignalService technicalSignalService;
    private final SymbolMarketAnalysisService symbolMarketAnalysisService;
    private final EventRiskService eventRiskService;
    private final SignalService signalService;
    private final PositionSizingService positionSizingService;
    private final TradeApprovalService tradeApprovalService;

    public TradingController(
            MarketAnalysisService marketAnalysisService,
            TechnicalSignalService technicalSignalService,
            SymbolMarketAnalysisService symbolMarketAnalysisService,
            EventRiskService eventRiskService,
            SignalService signalService,
            PositionSizingService positionSizingService,
            TradeApprovalService tradeApprovalService) {

        this.marketAnalysisService = marketAnalysisService;
        this.technicalSignalService = technicalSignalService;
        this.symbolMarketAnalysisService = symbolMarketAnalysisService;
        this.eventRiskService = eventRiskService;
        this.signalService = signalService;
        this.positionSizingService = positionSizingService;
        this.tradeApprovalService = tradeApprovalService;
    }

    // =========================================================
    // TRADING ENGINE STATUS
    // =========================================================

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getTradingStatus() {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "service",
                "MarshallAI Trading Engine"
        );

        response.put(
                "status",
                "READY"
        );

        response.put(
                "minimumConfidencePercent",
                MINIMUM_CONFIDENCE_PERCENT
        );

        response.put(
                "tradeApprovalEnabled",
                true
        );

        response.put(
                "symbolAnalysisEnabled",
                true
        );

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // MANUAL MARKET-DATA ANALYSIS
    //
    // This endpoint uses the OHLC price arrays supplied directly
    // in TradingAnalysisRequest.
    // =========================================================

    @PostMapping("/analyze")
    public ResponseEntity<TradingAnalysisResponse> analyzeTradingOpportunity(
            @RequestBody TradingAnalysisRequest request) {

        validateRequest(request);

        MarketAnalysisResult marketAnalysisResult =
                marketAnalysisService.analyze(
                        request.closingPrices()
                );

        TechnicalSignal technicalSignal =
                technicalSignalService.evaluate(
                        request.highPrices(),
                        request.lowPrices(),
                        request.closingPrices()
                );

        TradingAnalysisResponse response =
                completeTradingAnalysis(
                        request,
                        normalizeSymbol(request.symbol()),
                        marketAnalysisResult,
                        technicalSignal
                );

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // SYMBOL-DRIVEN MARKET ANALYSIS
    //
    // IMPORTANT:
    //
    // This endpoint intentionally does NOT use highPrices,
    // lowPrices or closingPrices from TradingAnalysisRequest.
    //
    // Historical candles are obtained through:
    //
    // symbol
    //   -> SymbolMarketAnalysisService
    //   -> MarketDataProvider
    //   -> historical candles
    //   -> chart analysis
    //   -> technical analysis
    //
    // This keeps the symbol-driven API independent from manually
    // supplied market-price arrays.
    // =========================================================

    @PostMapping("/analyze-symbol")
    public ResponseEntity<TradingAnalysisResponse> analyzeSymbol(
            @RequestBody TradingAnalysisRequest request) {

        validateSymbolRequest(request);

        SymbolMarketAnalysisService.SymbolMarketAnalysisResult
                symbolAnalysis =
                symbolMarketAnalysisService.analyze(
                        request.symbol()
                );

        TradingAnalysisResponse response =
                completeTradingAnalysis(
                        request,
                        symbolAnalysis.symbol(),
                        symbolAnalysis.marketAnalysis(),
                        symbolAnalysis.technicalSignal()
                );

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // SHARED TRADING PIPELINE
    // =========================================================

    private TradingAnalysisResponse completeTradingAnalysis(
            TradingAnalysisRequest request,
            String symbol,
            MarketAnalysisResult marketAnalysisResult,
            TechnicalSignal technicalSignal) {

        // -----------------------------------------------------
        // 1. EVENT RISK
        // -----------------------------------------------------

        EventRisk eventRisk =
                eventRiskService.evaluate(
                        request.eventType(),
                        request.eventDescription(),
                        request.minutesUntilEvent(),
                        request.highImpactEvent()
                );

        // -----------------------------------------------------
        // 2. COMBINED SIGNAL
        //
        // Pattern evidence
        // + technical evidence
        // + 80% confidence threshold
        // + event-risk gate
        // -----------------------------------------------------

        MarketSignal marketSignal =
                signalService.evaluate(
                        marketAnalysisResult,
                        technicalSignal,
                        eventRisk
                );

        // -----------------------------------------------------
        // 3. POSITION SIZING
        // -----------------------------------------------------

        PositionSizeResult positionSize =
                positionSizingService.calculate(
                        request.accountBalance(),
                        request.riskPercent(),
                        request.entryPrice(),
                        request.stopLossPrice()
                );

        // -----------------------------------------------------
        // 4. FINAL TRADE APPROVAL
        // -----------------------------------------------------

        TradeApprovalResult tradeApproval =
                tradeApprovalService.evaluate(
                        marketSignal,
                        eventRisk,
                        positionSize
                );

        // -----------------------------------------------------
        // 5. RESPONSE
        // -----------------------------------------------------

        return new TradingAnalysisResponse(

                symbol,

                marketAnalysisResult
                        .getDetectedPatterns(),

                marketAnalysisResult
                        .getDirection()
                        .name(),

                technicalSignal
                        .getBullishWeightedScore(),

                technicalSignal
                        .getBearishWeightedScore(),

                technicalSignal
                        .getConfidencePercent(),

                marketSignal
                        .getDecision()
                        .name(),

                marketSignal
                        .getConfidencePercent(),

                marketSignal
                        .getBullishWeightedScore(),

                marketSignal
                        .getBearishWeightedScore(),

                eventRisk
                        .getLevel()
                        .name(),

                eventRisk
                        .isTradeBlocked(),

                tradeApproval
                        .getStatus()
                        .name(),

                tradeApproval
                        .getReason(),

                tradeApproval
                        .getQuantity(),

                tradeApproval
                        .getPositionValue(),

                tradeApproval
                        .getRiskAmount()
        );
    }

    // =========================================================
    // REQUEST VALIDATION
    // =========================================================

    private void validateRequest(
            TradingAnalysisRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Trading analysis request cannot be null"
            );
        }

        validateSymbol(request.symbol());

        if (request.highPrices() == null
                || request.lowPrices() == null
                || request.closingPrices() == null) {

            throw new IllegalArgumentException(
                    "Manual trading analysis requires market price data"
            );
        }

        if (request.highPrices().isEmpty()
                || request.lowPrices().isEmpty()
                || request.closingPrices().isEmpty()) {

            throw new IllegalArgumentException(
                    "Market price data cannot be empty"
            );
        }

        if (request.highPrices().size()
                != request.lowPrices().size()
                || request.highPrices().size()
                != request.closingPrices().size()) {

            throw new IllegalArgumentException(
                    "High, low and closing price lists must have equal sizes"
            );
        }
    }

    // =========================================================
    // SYMBOL REQUEST VALIDATION
    // =========================================================

    private void validateSymbolRequest(
            TradingAnalysisRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Trading analysis request cannot be null"
            );
        }

        validateSymbol(request.symbol());
    }

    // =========================================================
    // SYMBOL VALIDATION
    // =========================================================

    private void validateSymbol(
            String symbol) {

        if (symbol == null
                || symbol.isBlank()) {

            throw new IllegalArgumentException(
                    "Trading symbol cannot be blank"
            );
        }
    }

    // =========================================================
    // SYMBOL NORMALIZATION
    // =========================================================

    private String normalizeSymbol(
            String symbol) {

        return symbol
                .trim()
                .toUpperCase();
    }
}