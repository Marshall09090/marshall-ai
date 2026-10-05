package com.marshallai;

import com.marshallai.market.AlpacaMarketDataProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlpacaMarketDataProviderTest {

    @Test
    void fixedCountAnalysisShouldUseDailyCandles() throws Exception {

        Field timeframeField =
                AlpacaMarketDataProvider.class
                        .getDeclaredField("DEFAULT_ANALYSIS_TIMEFRAME");

        timeframeField.setAccessible(true);

        String timeframe =
                (String) timeframeField.get(null);

        assertEquals(
                "1d",
                timeframe,
                "Fixed-count market analysis must use daily candles"
        );
    }

    @Test
    void historicalRequestsShouldUseSplitAdjustedPrices() throws Exception {

        Field adjustmentField =
                AlpacaMarketDataProvider.class
                        .getDeclaredField("BAR_ADJUSTMENT");

        adjustmentField.setAccessible(true);

        String adjustment =
                (String) adjustmentField.get(null);

        assertEquals(
                "split",
                adjustment,
                "Historical Alpaca bars must use split-adjusted prices"
        );
    }

    @Test
    void fixedCountAnalysisShouldUseEnoughCalendarHistoryForDailyBars()
            throws Exception {

        Field lookbackField =
                AlpacaMarketDataProvider.class
                        .getDeclaredField("MINIMUM_LOOKBACK_DAYS");

        lookbackField.setAccessible(true);

        long minimumLookbackDays =
                lookbackField.getLong(null);

        assertEquals(
                400L,
                minimumLookbackDays,
                "Daily analysis should request enough history for 200+ trading bars"
        );
    }
}