Feature: MarshallAi system status
  As a user of MarshallAi
  I want to check whether the backend is online
  So that I know the service is available

  Scenario: MarshallAi is online
    Given the MarshallAi application is running
    When I request the system status
    Then the response status code should be 200
    And the response should contain the name "MarshallAi"
    And the response should contain the status "ONLINE"