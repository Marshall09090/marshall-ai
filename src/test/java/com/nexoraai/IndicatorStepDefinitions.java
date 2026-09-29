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
}