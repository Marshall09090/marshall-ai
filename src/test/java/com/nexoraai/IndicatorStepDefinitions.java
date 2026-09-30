package com.nexoraai;

import com.nexoraai.indicator.IndicatorService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class IndicatorStepDefinitions {

    @Autowired
    private IndicatorService indicatorService;

    private List<BigDecimal> closingPrices;

    private BigDecimal smaResult;
    private BigDecimal emaResult;
    private BigDecimal rsiResult;
    private BigDecimal macdResult;
    private BigDecimal macdSignalResult;
    private BigDecimal macdHistogramResult;

    private BigDecimal bollingerMiddleBandResult;
    private BigDecimal bollingerUpperBandResult;
    private BigDecimal bollingerLowerBandResult;

    private List<BigDecimal> highPrices;
    private List<BigDecimal> lowPrices;
    private BigDecimal atrResult;

    private BigDecimal stochasticPercentKResult;
    private BigDecimal stochasticPercentDResult;

    private BigDecimal adxResult;
    private BigDecimal positiveDiResult;
    private BigDecimal negativeDiResult;

    private BigDecimal cciResult;
    private BigDecimal williamsPercentRResult;
    private BigDecimal rocResult;

    private List<BigDecimal> volumeValues;
    private BigDecimal mfiResult;
    private BigDecimal obvResult;
    private BigDecimal cmfResult;

    @Given("the following closing prices:")
    public void theFollowingClosingPrices(DataTable dataTable) {

        closingPrices = dataTable
                .asMaps(String.class, String.class)
                .stream()
                .map(row -> new BigDecimal(row.get("price")))
                .toList();
    }

    // =========================
    // SMA
    // =========================

    @When("I calculate a simple moving average with period {int}")
    public void calculateSimpleMovingAverage(int period) {

        smaResult = indicatorService.calculateSma(
                closingPrices,
                period
        );
    }

    @Then("the simple moving average should be {double}")
    public void simpleMovingAverageShouldBe(double expectedValue) {

        assertNotNull(smaResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = smaResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "SMA value does not match expected result"
        );
    }

    // =========================
    // EMA
    // =========================

    @When("I calculate an exponential moving average with period {int}")
    public void calculateExponentialMovingAverage(int period) {

        emaResult = indicatorService.calculateEma(
                closingPrices,
                period
        );
    }

    @Then("the exponential moving average should be {double}")
    public void exponentialMovingAverageShouldBe(double expectedValue) {

        assertNotNull(emaResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = emaResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "EMA value does not match expected result"
        );
    }

    // =========================
    // RSI
    // =========================

    @When("I calculate the relative strength index with period {int}")
    public void calculateRelativeStrengthIndex(int period) {

        rsiResult = indicatorService.calculateRsi(
                closingPrices,
                period
        );
    }

    @Then("the relative strength index should be {double}")
    public void relativeStrengthIndexShouldBe(double expectedValue) {

        assertNotNull(rsiResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = rsiResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "RSI value does not match expected result"
        );
    }

    // =========================
    // MACD LINE
    // =========================

    @When("I calculate the MACD line with fast period {int} and slow period {int}")
    public void calculateMacdLine(
            int fastPeriod,
            int slowPeriod) {

        macdResult = indicatorService.calculateMacd(
                closingPrices,
                fastPeriod,
                slowPeriod
        );
    }

    @Then("the MACD line should be {double}")
    public void macdLineShouldBe(double expectedValue) {

        assertNotNull(macdResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = macdResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "MACD value does not match expected result"
        );
    }

    // =========================
    // MACD SIGNAL LINE
    // =========================

    @When("I calculate the MACD signal line")
    public void calculateMacdSignalLine() {

        macdSignalResult = indicatorService.calculateMacdSignalLine(
                closingPrices,
                12,
                26,
                9
        );
    }

    @Then("the MACD signal line should be {double}")
    public void macdSignalLineShouldBe(double expectedValue) {

        assertNotNull(macdSignalResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = macdSignalResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "MACD signal line does not match expected result"
        );
    }

    // =========================
    // MACD HISTOGRAM
    // =========================

    @When("I calculate the MACD histogram")
    public void calculateMacdHistogram() {

        macdHistogramResult = indicatorService.calculateMacdHistogram(
                closingPrices,
                12,
                26,
                9
        );
    }

    @Then("the MACD histogram should be {double}")
    public void macdHistogramShouldBe(double expectedValue) {

        assertNotNull(macdHistogramResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = macdHistogramResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "MACD histogram does not match expected result"
        );
    }

    // =========================
    // BOLLINGER BANDS
    // =========================

    @When("I calculate Bollinger Bands with period {int} and standard deviation multiplier {double}")
    public void calculateBollingerBands(
            int period,
            double standardDeviationMultiplier) {

        List<BigDecimal> bands = indicatorService.calculateBollingerBands(
                closingPrices,
                period,
                BigDecimal.valueOf(standardDeviationMultiplier)
        );

        assertNotNull(bands);

        bollingerMiddleBandResult = bands.get(0);
        bollingerUpperBandResult = bands.get(1);
        bollingerLowerBandResult = bands.get(2);
    }

    @Then("the Bollinger middle band should be {double}")
    public void bollingerMiddleBandShouldBe(double expectedValue) {

        assertNotNull(bollingerMiddleBandResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = bollingerMiddleBandResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "Bollinger middle band does not match expected result"
        );
    }

    @Then("the Bollinger upper band should be {double}")
    public void bollingerUpperBandShouldBe(double expectedValue) {

        assertNotNull(bollingerUpperBandResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = bollingerUpperBandResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "Bollinger upper band does not match expected result"
        );
    }

    @Then("the Bollinger lower band should be {double}")
    public void bollingerLowerBandShouldBe(double expectedValue) {

        assertNotNull(bollingerLowerBandResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = bollingerLowerBandResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "Bollinger lower band does not match expected result"
        );
    }

    // =========================
    // MARKET PRICE DATA
    // =========================

    @Given("the following market prices:")
    public void theFollowingMarketPrices(DataTable dataTable) {

        List<java.util.Map<String, String>> rows =
                dataTable.asMaps(String.class, String.class);

        highPrices = rows.stream()
                .map(row -> new BigDecimal(row.get("high")))
                .toList();

        lowPrices = rows.stream()
                .map(row -> new BigDecimal(row.get("low")))
                .toList();

        closingPrices = rows.stream()
                .map(row -> new BigDecimal(row.get("close")))
                .toList();
    }

    // =========================
    // AVERAGE TRUE RANGE (ATR)
    // =========================

    @When("I calculate the Average True Range with period {int}")
    public void calculateAverageTrueRange(int period) {

        atrResult = indicatorService.calculateAtr(
                highPrices,
                lowPrices,
                closingPrices,
                period
        );
    }

    @Then("the Average True Range should be {double}")
    public void averageTrueRangeShouldBe(double expectedValue) {

        assertNotNull(atrResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = atrResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "ATR value does not match expected result"
        );
    }

    // =========================
    // STOCHASTIC OSCILLATOR %K
    // =========================

    @When("I calculate the Stochastic Oscillator percent K with period {int}")
    public void calculateStochasticOscillatorPercentK(int period) {

        stochasticPercentKResult =
                indicatorService.calculateStochasticPercentK(
                        highPrices,
                        lowPrices,
                        closingPrices,
                        period
                );
    }

    @Then("the Stochastic Oscillator percent K should be {double}")
    public void stochasticOscillatorPercentKShouldBe(double expectedValue) {

        assertNotNull(stochasticPercentKResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = stochasticPercentKResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "Stochastic Oscillator percent K does not match expected result"
        );
    }

    // =========================
    // STOCHASTIC OSCILLATOR %D
    // =========================

    @When("I calculate the Stochastic Oscillator percent D with K period {int} and D period {int}")
    public void calculateStochasticOscillatorPercentD(
            int kPeriod,
            int dPeriod) {

        stochasticPercentDResult =
                indicatorService.calculateStochasticPercentD(
                        highPrices,
                        lowPrices,
                        closingPrices,
                        kPeriod,
                        dPeriod
                );
    }

    @Then("the Stochastic Oscillator percent D should be {double}")
    public void stochasticOscillatorPercentDShouldBe(double expectedValue) {

        assertNotNull(stochasticPercentDResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = stochasticPercentDResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "Stochastic Oscillator percent D does not match expected result"
        );
    }

    // =========================
    // AVERAGE DIRECTIONAL INDEX (ADX)
    // =========================

    @When("I calculate the Average Directional Index with period {int}")
    public void calculateAverageDirectionalIndex(int period) {

        adxResult = indicatorService.calculateAdx(
                highPrices,
                lowPrices,
                closingPrices,
                period
        );
    }

    @Then("the Average Directional Index should be {double}")
    public void averageDirectionalIndexShouldBe(double expectedValue) {

        assertNotNull(adxResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = adxResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "ADX value does not match expected result"
        );
    }

    // =========================
    // POSITIVE DIRECTIONAL INDICATOR (+DI)
    // =========================

    @When("I calculate the Positive Directional Indicator with period {int}")
    public void calculatePositiveDirectionalIndicator(int period) {

        positiveDiResult =
                indicatorService.calculatePositiveDi(
                        highPrices,
                        lowPrices,
                        closingPrices,
                        period
                );
    }

    @Then("the Positive Directional Indicator should be {double}")
    public void positiveDirectionalIndicatorShouldBe(double expectedValue) {

        assertNotNull(positiveDiResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = positiveDiResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "Positive Directional Indicator value does not match expected result"
        );
    }

    // =========================
    // NEGATIVE DIRECTIONAL INDICATOR (-DI)
    // =========================

    @When("I calculate the Negative Directional Indicator with period {int}")
    public void calculateNegativeDirectionalIndicator(int period) {

        negativeDiResult =
                indicatorService.calculateNegativeDi(
                        highPrices,
                        lowPrices,
                        closingPrices,
                        period
                );
    }

    @Then("the Negative Directional Indicator should be {double}")
    public void negativeDirectionalIndicatorShouldBe(double expectedValue) {

        assertNotNull(negativeDiResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = negativeDiResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "Negative Directional Indicator value does not match expected result"
        );
    }

    // =========================
    // COMMODITY CHANNEL INDEX (CCI)
    // =========================

    @When("I calculate the Commodity Channel Index with period {int}")
    public void calculateCommodityChannelIndex(int period) {

        cciResult =
                indicatorService.calculateCci(
                        highPrices,
                        lowPrices,
                        closingPrices,
                        period
                );
    }

    @Then("the Commodity Channel Index should be {double}")
    public void commodityChannelIndexShouldBe(double expectedValue) {

        assertNotNull(cciResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = cciResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "CCI value does not match expected result"
        );
    }

    // =========================
    // WILLIAMS PERCENT R
    // =========================

    @When("I calculate Williams Percent R with period {int}")
    public void calculateWilliamsPercentR(int period) {

        williamsPercentRResult =
                indicatorService.calculateWilliamsPercentR(
                        highPrices,
                        lowPrices,
                        closingPrices,
                        period
                );
    }

    @Then("Williams Percent R should be {double}")
    public void williamsPercentRShouldBe(double expectedValue) {

        assertNotNull(williamsPercentRResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = williamsPercentRResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "Williams Percent R value does not match expected result"
        );
    }

    // =========================
    // RATE OF CHANGE (ROC)
    // =========================

    @When("I calculate the Rate of Change with period {int}")
    public void calculateRateOfChange(int period) {

        rocResult = indicatorService.calculateRoc(
                closingPrices,
                period
        );
    }

    @Then("the Rate of Change should be {double}")
    public void rateOfChangeShouldBe(double expectedValue) {

        assertNotNull(rocResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = rocResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "ROC value does not match expected result"
        );
    }

    // =========================
    // MONEY FLOW INDEX (MFI)
    // =========================

    @Given("the following market prices with volume:")
    public void theFollowingMarketPricesWithVolume(DataTable dataTable) {

        List<java.util.Map<String, String>> rows =
                dataTable.asMaps(String.class, String.class);

        highPrices = rows.stream()
                .map(row -> new BigDecimal(row.get("high")))
                .toList();

        lowPrices = rows.stream()
                .map(row -> new BigDecimal(row.get("low")))
                .toList();

        closingPrices = rows.stream()
                .map(row -> new BigDecimal(row.get("close")))
                .toList();

        volumeValues = rows.stream()
                .map(row -> new BigDecimal(row.get("volume")))
                .toList();
    }

    @When("I calculate the Money Flow Index with period {int}")
    public void calculateMoneyFlowIndex(int period) {

        mfiResult = indicatorService.calculateMfi(
                highPrices,
                lowPrices,
                closingPrices,
                volumeValues,
                period
        );
    }

    @Then("the Money Flow Index should be {double}")
    public void moneyFlowIndexShouldBe(double expectedValue) {

        assertNotNull(mfiResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = mfiResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "MFI value does not match expected result"
        );
    }

    // =========================
    // ON-BALANCE VOLUME (OBV)
    // =========================

    @Given("the following closing prices with volume:")
    public void theFollowingClosingPricesWithVolume(DataTable dataTable) {

        List<java.util.Map<String, String>> rows =
                dataTable.asMaps(String.class, String.class);

        closingPrices = rows.stream()
                .map(row -> new BigDecimal(row.get("close")))
                .toList();

        volumeValues = rows.stream()
                .map(row -> new BigDecimal(row.get("volume")))
                .toList();
    }

    @When("I calculate the On-Balance Volume")
    public void calculateOnBalanceVolume() {

        obvResult = indicatorService.calculateObv(
                closingPrices,
                volumeValues
        );
    }

    @Then("the On-Balance Volume should be {double}")
    public void onBalanceVolumeShouldBe(double expectedValue) {

        assertNotNull(obvResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = obvResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "OBV value does not match expected result"
        );
    }

    // =========================
    // CHAIKIN MONEY FLOW (CMF)
    // =========================

    @When("I calculate the Chaikin Money Flow with period {int}")
    public void calculateChaikinMoneyFlow(int period) {

        cmfResult = indicatorService.calculateCmf(
                highPrices,
                lowPrices,
                closingPrices,
                volumeValues,
                period
        );
    }

    @Then("the Chaikin Money Flow should be {double}")
    public void chaikinMoneyFlowShouldBe(double expectedValue) {

        assertNotNull(cmfResult);

        BigDecimal expected = BigDecimal.valueOf(expectedValue)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actual = cmfResult
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                0,
                expected.compareTo(actual),
                "CMF value does not match expected result"
        );
    }
}