Feature: Nexora technical indicators

  Scenario: Calculate a simple moving average
    Given the following closing prices:
      | price  |
      | 100.00 |
      | 102.00 |
      | 104.00 |
      | 106.00 |
      | 108.00 |
    When I calculate a simple moving average with period 5
    Then the simple moving average should be 104.00

  Scenario: Calculate an exponential moving average
    Given the following closing prices:
      | price  |
      | 100.00 |
      | 102.00 |
      | 104.00 |
      | 106.00 |
      | 108.00 |
    When I calculate an exponential moving average with period 3
    Then the exponential moving average should be 106.00

  Scenario: Calculate relative strength index
    Given the following closing prices:
      | price |
      | 44.34 |
      | 44.09 |
      | 44.15 |
      | 43.61 |
      | 44.33 |
      | 44.83 |
      | 45.10 |
      | 45.42 |
      | 45.84 |
      | 46.08 |
      | 45.89 |
      | 46.03 |
      | 45.61 |
      | 46.28 |
      | 46.28 |
    When I calculate the relative strength index with period 14
    Then the relative strength index should be 70.46

  Scenario: Calculate MACD line
    Given the following closing prices:
      | price  |
      | 100.00 |
      | 101.00 |
      | 102.00 |
      | 103.00 |
      | 104.00 |
      | 105.00 |
      | 106.00 |
      | 107.00 |
      | 108.00 |
      | 109.00 |
      | 110.00 |
      | 111.00 |
      | 112.00 |
      | 113.00 |
      | 114.00 |
      | 115.00 |
      | 116.00 |
      | 117.00 |
      | 118.00 |
      | 119.00 |
      | 120.00 |
      | 121.00 |
      | 122.00 |
      | 123.00 |
      | 124.00 |
      | 125.00 |
      | 126.00 |
      | 127.00 |
      | 128.00 |
      | 129.00 |
    When I calculate the MACD line with fast period 12 and slow period 26
    Then the MACD line should be 7.00

  Scenario: Calculate MACD signal line
    Given the following closing prices:
      | price  |
      | 100.00 |
      | 101.00 |
      | 102.00 |
      | 103.00 |
      | 104.00 |
      | 105.00 |
      | 106.00 |
      | 107.00 |
      | 108.00 |
      | 109.00 |
      | 110.00 |
      | 111.00 |
      | 112.00 |
      | 113.00 |
      | 114.00 |
      | 115.00 |
      | 116.00 |
      | 117.00 |
      | 118.00 |
      | 119.00 |
      | 120.00 |
      | 121.00 |
      | 122.00 |
      | 123.00 |
      | 124.00 |
      | 125.00 |
      | 126.00 |
      | 127.00 |
      | 128.00 |
      | 129.00 |
      | 130.00 |
      | 131.00 |
      | 132.00 |
      | 133.00 |
      | 134.00 |
    When I calculate the MACD signal line
    Then the MACD signal line should be 7.00