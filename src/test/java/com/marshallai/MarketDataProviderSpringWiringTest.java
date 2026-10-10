package com.marshallai;

import com.marshallai.market.MarketDataProvider;
import com.marshallai.market.MarketDataProviderSource;
import com.marshallai.market.MarketDataProviderType;
import com.marshallai.market.ProtectedMarketDataProvider;
import com.marshallai.market.SimulatedMarketDataProvider;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MarketDataProviderSpringWiringTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MarketDataProvider marketDataProvider;

    @Autowired
    private MarketDataProviderSource marketDataProviderSource;

    @Test
    void springInjectsProtectedWrapperAsMarketDataProvider() {

        assertInstanceOf(
                ProtectedMarketDataProvider.class,
                marketDataProvider
        );

        assertSame(
                marketDataProvider,
                applicationContext.getBean(
                        MarketDataProvider.class
                )
        );
    }

    @Test
    void defaultProfileUsesSimulatedSourceBehindWrapper() {

        assertInstanceOf(
                SimulatedMarketDataProvider.class,
                marketDataProviderSource
        );

        assertEquals(
                MarketDataProviderType.SIMULATED,
                marketDataProviderSource.providerType()
        );

        assertEquals(
                MarketDataProviderType.SIMULATED,
                marketDataProvider.providerType()
        );
    }

    @Test
    void simulatedSourceCannotQualifyForGovernedVerdict() {

        assertFalse(
                marketDataProvider.providerType()
                        .allowsGovernedVerdict()
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                marketDataProvider
                                        .providerType()
                                        .requireGovernedVerdictAllowed()
                );

        assertEquals(
                "SIMULATED_PROVIDER_CANNOT_EMIT_GOVERNED_VERDICT",
                exception.getMessage()
        );
    }
}