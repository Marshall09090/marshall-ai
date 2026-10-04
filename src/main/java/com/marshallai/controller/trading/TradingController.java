package com.marshallai.controller.trading;

import com.marshallai.analysis.MarketAnalysisResult;
import com.marshallai.analysis.MarketAnalysisService;
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

    private final MarketAnalysisService marketAnalysisService;
    private final TechnicalSignalService technicalSignalService;
    private final EventRiskService eventRiskService;
    private final SignalService signalService;
    private final PositionSizingService positionSizingService;
    private final TradeApprovalService tradeApprovalService;

    public TradingController(
            MarketAnalysisService marketAnalysisService,
            TechnicalSignalService technicalSignalService,
            EventRiskService eventRiskService,
            SignalService signalService,
            PositionSizingService positionSizingService,
            TradeApprovalService tradeApprovalService) {

        this.marketAnalysisService = marketAnalysisService;
        this.technicalSignalService = technicalSignalService;
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
                80
        );

        response.put(
                "tradeApprovalEnabled",
                true
        );

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // COMPLETE TRADING ANALYSIS
    // =========================================================

    @PostMapping("/analyze")
    public ResponseEntity<TradingAnalysisResponse> analyzeTradingOpportunity(
            @RequestBody TradingAnalysisRequest request) {

        // -----------------------------------------------------
        // 1. CHART PATTERN ANALYSIS
        // -----------------------------------------------------

        MarketAnalysisResult marketAnalysisResult =
                marketAnalysisService.analyze(
                        request.closingPrices()
                );

        // -----------------------------------------------------
        // 2. TECHNICAL INDICATOR ANALYSIS
        // -----------------------------------------------------

        TechnicalSignal technicalSignal =
                technicalSignalService.evaluate(
                        request.highPrices(),
                        request.lowPrices(),
                        request.closingPrices()
                );

        // -----------------------------------------------------
        // 3. EVENT RISK ANALYSIS
        // -----------------------------------------------------

        EventRisk eventRisk =
                eventRiskService.evaluate(
                        request.eventType(),
                        request.eventDescription(),
                        request.minutesUntilEvent(),
                        request.highImpactEvent()
                );

        // -----------------------------------------------------
        // 4. COMBINED TRADING SIGNAL
        //
        // Chart patterns
        // + technical indicators
        // + event-risk gate
        // + 80% confidence requirement
        // -----------------------------------------------------

        MarketSignal marketSignal =
                signalService.evaluate(
                        marketAnalysisResult,
                        technicalSignal,
                        eventRisk
                );

        // -----------------------------------------------------
        // 5. POSITION SIZING
        // -----------------------------------------------------

        PositionSizeResult positionSize =
                positionSizingService.calculate(
                        request.accountBalance(),
                        request.riskPercent(),
                        request.entryPrice(),
                        request.stopLossPrice()
                );

        // -----------------------------------------------------
        // 6. FINAL TRADE APPROVAL
        // -----------------------------------------------------

        TradeApprovalResult tradeApproval =
                tradeApprovalService.evaluate(
                        marketSignal,
                        eventRisk,
                        positionSize
                );

        // -----------------------------------------------------
        // 7. API RESPONSE
        // -----------------------------------------------------

        TradingAnalysisResponse response =
                new TradingAnalysisResponse(

                        request.symbol(),

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

        return ResponseEntity.ok(response);
    }
}