Feature: Nexora AI system status
  As a user of Nexora AI
  I want to check whether the backend is online
  So that I know the service is available

  Scenario: Nexora AI is online
    Given the Nexora AI application is running
    When I request the system status
    Then the response status code should be 200
    And the response should contain the name "Nexora AI"
    And the response should contain the status "ONLINE"