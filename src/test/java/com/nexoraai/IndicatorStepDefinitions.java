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
            int slowPeriod
    ) {

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
}