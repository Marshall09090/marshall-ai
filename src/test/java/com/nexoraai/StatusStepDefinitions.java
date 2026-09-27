package com.nexoraai;

import com.nexoraai.controller.StatusController;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class StatusStepDefinitions {

    @Autowired
    private StatusController statusController;

    private Map<String, String> response;

    @When("I request the system status")
    public void requestSystemStatus() {
        response = statusController.getStatus();
    }

    @Then("the response status code should be {int}")
    public void responseStatusCodeShouldBe(Integer statusCode) {
        assertEquals(200, statusCode);
    }

    @Then("the response should contain the name {string}")
    public void responseShouldContainName(String name) {
        assertNotNull(response);
        assertEquals(name, response.get("name"));
    }

    @Then("the response should contain the status {string}")
    public void responseShouldContainStatus(String status) {
        assertNotNull(response);
        assertEquals(status, response.get("status"));
    }
}