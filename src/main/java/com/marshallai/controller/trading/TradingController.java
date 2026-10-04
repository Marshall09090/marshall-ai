package com.marshallai.controller.trading;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/trading")
public class TradingController {

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
}