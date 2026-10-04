package com.marshallai;

import com.marshallai.signal.TechnicalSignal;
import com.marshallai.signal.TechnicalSignalService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TechnicalSignalStepDefinitions {

    @Autowired
    private TechnicalSignalService technicalSignalService;

    private List<BigDecimal> highPrices;
    private List<BigDecimal> lowPrices;
    private List<BigDecimal> closingPrices;

    private TechnicalSignal technicalSignal;

    @Given("a strongly bullish technical market with {int} candles")
    public void aStronglyBullishTechnicalMarketWithCandles(
            int candleCount) {

        List<BigDecimal> closes =
                new ArrayList<>();

        for (int i = 0; i < candleCount; i++) {

            closes.add(
                    BigDecimal.valueOf(
                            100L + i
                    )
            );
        }

        buildCandles(closes);
    }

    @Given("a strongly bearish technical market with {int} candles")
    public void aStronglyBearishTechnicalMarketWithCandles(
            int candleCount) {

        List<BigDecimal> closes =
                new ArrayList<>();

        for (int i = 0; i < candleCount; i++) {

            closes.add(
                    BigDecimal.valueOf(
                            130L - i
                    )
            );
        }

        buildCandles(closes);
    }

    @Given("a mixed technical market")
    public void aMixedTechnicalMarket() {

        List<BigDecimal> closes =
                new ArrayList<>();

        for (int price = 100;
             price <= 125;
             price++) {

            closes.add(
                    BigDecimal.valueOf(price)
            );
        }

        closes.add(BigDecimal.valueOf(122));
        closes.add(BigDecimal.valueOf(119));
        closes.add(BigDecimal.valueOf(116));
        closes.add(BigDecimal.valueOf(113));

        buildCandles(closes);
    }

    @When("MarshallAi evaluates the technical signal")
    public void marshallAiEvaluatesTheTechnicalSignal() {

        technicalSignal =
                technicalSignalService.evaluate(
                        highPrices,
                        lowPrices,
                        closingPrices
                );
    }

    @Then("the technical bullish weighted score should be {int}")
    public void theTechnicalBullishWeightedScoreShouldBe(
            int expectedScore) {

        assertNotNull(
                technicalSignal,
                "Technical signal should not be null"
        );

        assertEquals(
                expectedScore,
                technicalSignal.getBullishWeightedScore(),
                "Unexpected technical bullish weighted score"
        );
    }

    @Then("the technical bearish weighted score should be {int}")
    public void theTechnicalBearishWeightedScoreShouldBe(
            int expectedScore) {

        assertNotNull(
                technicalSignal,
                "Technical signal should not be null"
        );

        assertEquals(
                expectedScore,
                technicalSignal.getBearishWeightedScore(),
                "Unexpected technical bearish weighted score"
        );
    }

    @Then("the technical confidence score should be {int} percent")
    public void theTechnicalConfidenceScoreShouldBe(
            int expectedConfidence) {

        assertNotNull(
                technicalSignal,
                "Technical signal should not be null"
        );

        assertEquals(
                expectedConfidence,
                technicalSignal.getConfidencePercent(),
                "Unexpected technical confidence score"
        );
    }

    private void buildCandles(
            List<BigDecimal> closes) {

        closingPrices =
                new ArrayList<>(closes);

        highPrices =
                new ArrayList<>();

        lowPrices =
                new ArrayList<>();

        for (BigDecimal close : closes) {

            highPrices.add(
                    close.add(
                            BigDecimal.ONE
                    )
            );

            lowPrices.add(
                    close.subtract(
                            BigDecimal.ONE
                    )
            );
        }
    }
}