package com.marshallai.persistence.run;

import com.marshallai.persistence.config.ConfigurationFingerprintService;
import com.marshallai.persistence.criteria.CriteriaRecord;
import com.marshallai.persistence.criteria.CriteriaRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class RunService {

    private final RunLedger runLedger;
    private final CriteriaRegistry criteriaRegistry;
    private final ConfigurationFingerprintService
            configurationFingerprintService;

    public RunService(
            RunLedger runLedger,
            CriteriaRegistry criteriaRegistry,
            ConfigurationFingerprintService
                    configurationFingerprintService) {

        this.runLedger = runLedger;
        this.criteriaRegistry = criteriaRegistry;
        this.configurationFingerprintService =
                configurationFingerprintService;
    }

    public RunRecord startGovernedRun(
            RunType runType,
            String strategyVersion,
            String buildVersion,
            String criteriaVersion,
            String symbol,
            String timeframe,
            String dataSource,
            String dataFeed,
            String adjustmentMethod,
            String marketDataFingerprint,
            Instant marketDataFetchedAt,
            Map<String, Object> configurationSnapshot) {

        CriteriaRecord criteria =
                criteriaRegistry.requireRegistered(
                        criteriaVersion
                );

        String configurationVersion =
                configurationFingerprintService.fingerprint(
                        configurationSnapshot
                );

        RunRecord runRecord =
                new RunRecord(
                        UUID.randomUUID(),
                        runType,
                        strategyVersion,
                        configurationVersion,
                        buildVersion,
                        criteria.criteriaVersion(),
                        symbol,
                        timeframe,
                        dataSource,
                        dataFeed,
                        adjustmentMethod,
                        marketDataFingerprint,
                        marketDataFetchedAt,
                        configurationSnapshot,
                        criteria.criteriaSnapshot(),
                        Instant.now()
                );

        return runLedger.append(runRecord);
    }

    public RunRecord startAdHocRun(
            String strategyVersion,
            String buildVersion,
            String symbol,
            String timeframe,
            String dataSource,
            String dataFeed,
            String adjustmentMethod,
            String marketDataFingerprint,
            Instant marketDataFetchedAt,
            Map<String, Object> configurationSnapshot) {

        String configurationVersion =
                configurationFingerprintService.fingerprint(
                        configurationSnapshot
                );

        RunRecord runRecord =
                new RunRecord(
                        UUID.randomUUID(),
                        RunType.AD_HOC_ANALYSIS,
                        strategyVersion,
                        configurationVersion,
                        buildVersion,
                        null,
                        symbol,
                        timeframe,
                        dataSource,
                        dataFeed,
                        adjustmentMethod,
                        marketDataFingerprint,
                        marketDataFetchedAt,
                        configurationSnapshot,
                        Map.of(),
                        Instant.now()
                );

        return runLedger.append(runRecord);
    }
}