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

  Scenario: Calculate MACD histogram
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
    When I calculate the MACD histogram
    Then the MACD histogram should be 0.00

  Scenario: Calculate Bollinger Bands
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
    When I calculate Bollinger Bands with period 20 and standard deviation multiplier 2
    Then the Bollinger middle band should be 109.50
    And the Bollinger upper band should be 121.03
    And the Bollinger lower band should be 97.97

  Scenario: Calculate Average True Range
    Given the following market prices:
      | high   | low    | close  |
      | 102.00 | 99.00  | 101.00 |
      | 103.00 | 100.00 | 102.00 |
      | 104.00 | 101.00 | 103.00 |
      | 105.00 | 102.00 | 104.00 |
      | 106.00 | 103.00 | 105.00 |
      | 107.00 | 104.00 | 106.00 |
    When I calculate the Average True Range with period 5
    Then the Average True Range should be 3.00

  Scenario: Calculate Stochastic Oscillator percent K
    Given the following market prices:
      | high   | low    | close  |
      | 110.00 | 100.00 | 105.00 |
      | 112.00 | 101.00 | 108.00 |
      | 114.00 | 102.00 | 110.00 |
      | 116.00 | 103.00 | 113.00 |
      | 118.00 | 104.00 | 116.00 |
    When I calculate the Stochastic Oscillator percent K with period 5
    Then the Stochastic Oscillator percent K should be 88.89

  Scenario: Calculate Stochastic Oscillator percent D
    Given the following market prices:
      | high   | low    | close  |
      | 110.00 | 100.00 | 105.00 |
      | 112.00 | 101.00 | 108.00 |
      | 114.00 | 102.00 | 110.00 |
      | 116.00 | 103.00 | 113.00 |
      | 118.00 | 104.00 | 116.00 |
      | 120.00 | 105.00 | 117.00 |
      | 122.00 | 106.00 | 119.00 |
    When I calculate the Stochastic Oscillator percent D with K period 5 and D period 3
    Then the Stochastic Oscillator percent D should be 86.03

  Scenario: Calculate Average Directional Index
    Given the following market prices:
      | high   | low    | close  |
      | 100.00 | 95.00  | 98.00  |
      | 102.00 | 96.00  | 101.00 |
      | 104.00 | 98.00  | 103.00 |
      | 106.00 | 99.00  | 105.00 |
      | 108.00 | 101.00 | 107.00 |
      | 110.00 | 103.00 | 109.00 |
      | 112.00 | 105.00 | 111.00 |
      | 114.00 | 107.00 | 113.00 |
      | 116.00 | 109.00 | 115.00 |
      | 118.00 | 111.00 | 117.00 |
    When I calculate the Average Directional Index with period 5
    Then the Average Directional Index should be 100.00

  Scenario: Calculate Positive Directional Indicator
    Given the following market prices:
      | high   | low    | close  |
      | 100.00 | 95.00  | 98.00  |
      | 102.00 | 96.00  | 101.00 |
      | 104.00 | 98.00  | 103.00 |
      | 106.00 | 99.00  | 105.00 |
      | 108.00 | 101.00 | 107.00 |
      | 110.00 | 103.00 | 109.00 |
    When I calculate the Positive Directional Indicator with period 5
    Then the Positive Directional Indicator should be 30.30

  Scenario: Calculate Negative Directional Indicator
    Given the following market prices:
      | high   | low    | close  |
      | 110.00 | 105.00 | 107.00 |
      | 108.00 | 103.00 | 105.00 |
      | 106.00 | 101.00 | 103.00 |
      | 104.00 | 99.00  | 101.00 |
      | 102.00 | 97.00  | 99.00  |
      | 100.00 | 95.00  | 97.00  |
    When I calculate the Negative Directional Indicator with period 5
    Then the Negative Directional Indicator should be 40.00

  Scenario: Calculate Commodity Channel Index
    Given the following market prices:
      | high   | low    | close  |
      | 100.00 | 95.00  | 98.00  |
      | 102.00 | 96.00  | 101.00 |
      | 104.00 | 98.00  | 103.00 |
      | 106.00 | 99.00  | 105.00 |
      | 108.00 | 101.00 | 107.00 |
    When I calculate the Commodity Channel Index with period 5
    Then the Commodity Channel Index should be 110.47

  Scenario: Calculate Williams Percent R
    Given the following market prices:
      | high   | low    | close  |
      | 100.00 | 95.00  | 98.00  |
      | 102.00 | 96.00  | 101.00 |
      | 104.00 | 98.00  | 103.00 |
      | 106.00 | 99.00  | 105.00 |
      | 108.00 | 101.00 | 107.00 |
    When I calculate Williams Percent R with period 5
    Then Williams Percent R should be -7.69

  Scenario: Calculate Rate of Change
    Given the following closing prices:
      | price  |
      | 100.00 |
      | 102.00 |
      | 104.00 |
      | 106.00 |
      | 108.00 |
      | 110.00 |
    When I calculate the Rate of Change with period 5
    Then the Rate of Change should be 10.00

  Scenario: Calculate Money Flow Index
    Given the following market prices with volume:
      | high   | low    | close  | volume |
      | 100.00 | 95.00  | 98.00  | 1000   |
      | 102.00 | 96.00  | 101.00 | 1200   |
      | 104.00 | 98.00  | 103.00 | 1100   |
      | 103.00 | 97.00  | 99.00  | 1500   |
      | 105.00 | 99.00  | 104.00 | 1300   |
      | 107.00 | 101.00 | 106.00 | 1400   |
    When I calculate the Money Flow Index with period 5
    Then the Money Flow Index should be 77.38

  Scenario: Calculate On-Balance Volume
    Given the following closing prices with volume:
      | close  | volume |
      | 100.00 | 1000   |
      | 102.00 | 1200   |
      | 101.00 | 900    |
      | 104.00 | 1500   |
      | 103.00 | 1100   |
      | 106.00 | 1600   |
    When I calculate the On-Balance Volume
    Then the On-Balance Volume should be 2300

  Scenario: Calculate Chaikin Money Flow
    Given the following market prices with volume:
      | high   | low    | close  | volume |
      | 100.00 | 90.00  | 98.00  | 1000   |
      | 102.00 | 92.00  | 100.00 | 1200   |
      | 104.00 | 94.00  | 101.00 | 1100   |
      | 106.00 | 96.00  | 104.00 | 1500   |
      | 108.00 | 98.00  | 105.00 | 1300   |
    When I calculate the Chaikin Money Flow with period 5
    Then the Chaikin Money Flow should be 0.52

  Scenario: Calculate Accumulation Distribution Line
    Given the following market prices with volume:
      | high   | low    | close  | volume |
      | 100.00 | 90.00  | 98.00  | 1000   |
      | 102.00 | 92.00  | 100.00 | 1200   |
      | 104.00 | 94.00  | 101.00 | 1100   |
      | 106.00 | 96.00  | 104.00 | 1500   |
      | 108.00 | 98.00  | 105.00 | 1300   |
    When I calculate the Accumulation Distribution Line
    Then the Accumulation Distribution Line should be 3180.00

  Scenario: Calculate Volume Weighted Average Price
    Given the following market prices with volume:
      | high   | low    | close  | volume |
      | 100.00 | 90.00  | 98.00  | 1000   |
      | 102.00 | 92.00  | 100.00 | 1200   |
      | 104.00 | 94.00  | 101.00 | 1100   |
      | 106.00 | 96.00  | 104.00 | 1500   |
      | 108.00 | 98.00  | 105.00 | 1300   |
    When I calculate the Volume Weighted Average Price
    Then the Volume Weighted Average Price should be 100.16

  Scenario: Calculate Average Volume
    Given the following closing prices with volume:
      | close  | volume |
      | 100.00 | 1000   |
      | 102.00 | 1200   |
      | 101.00 | 900    |
      | 104.00 | 1500   |
      | 103.00 | 1100   |
      | 106.00 | 1600   |
    When I calculate the Average Volume with period 5
    Then the Average Volume should be 1260.00

  Scenario: Calculate Parabolic SAR
    Given the following market prices:
      | high   | low    | close  |
      | 100.00 | 95.00  | 98.00  |
      | 102.00 | 97.00  | 101.00 |
      | 104.00 | 99.00  | 103.00 |
      | 106.00 | 101.00 | 105.00 |
      | 108.00 | 103.00 | 107.00 |
      | 110.00 | 105.00 | 109.00 |
    When I calculate the Parabolic SAR with acceleration factor 0.02 and maximum acceleration 0.20
    Then the Parabolic SAR should be 96.96

  Scenario: Calculate Ichimoku Tenkan-sen
    Given the following market prices:
      | high   | low    | close  |
      | 100.00 | 95.00  | 98.00  |
      | 102.00 | 97.00  | 101.00 |
      | 104.00 | 99.00  | 103.00 |
      | 106.00 | 101.00 | 105.00 |
      | 108.00 | 103.00 | 107.00 |
      | 110.00 | 105.00 | 109.00 |
      | 112.00 | 107.00 | 111.00 |
      | 114.00 | 109.00 | 113.00 |
      | 116.00 | 111.00 | 115.00 |
    When I calculate the Ichimoku Tenkan-sen with period 9
    Then the Ichimoku Tenkan-sen should be 105.50

  Scenario: Calculate Ichimoku Kijun-sen
    Given the following market prices:
      | high   | low    | close  |
      | 100.00 | 95.00  | 98.00  |
      | 102.00 | 97.00  | 101.00 |
      | 104.00 | 99.00  | 103.00 |
      | 106.00 | 101.00 | 105.00 |
      | 108.00 | 103.00 | 107.00 |
      | 110.00 | 105.00 | 109.00 |
    When I calculate the Ichimoku Kijun-sen with period 6
    Then the Ichimoku Kijun-sen should be 102.50

  Scenario: Calculate Ichimoku Senkou Span A
    Given the following market prices:
      | high   | low    | close  |
      | 100.00 | 95.00  | 98.00  |
      | 102.00 | 97.00  | 101.00 |
      | 104.00 | 99.00  | 103.00 |
      | 106.00 | 101.00 | 105.00 |
      | 108.00 | 103.00 | 107.00 |
      | 110.00 | 105.00 | 109.00 |
      | 112.00 | 107.00 | 111.00 |
      | 114.00 | 109.00 | 113.00 |
      | 116.00 | 111.00 | 115.00 |
    When I calculate the Ichimoku Senkou Span A with Tenkan period 9 and Kijun period 6
    Then the Ichimoku Senkou Span A should be 107.00

  Scenario: Calculate Ichimoku Senkou Span B
    Given the following market prices:
      | high   | low    | close  |
      | 100.00 | 95.00  | 98.00  |
      | 102.00 | 97.00  | 101.00 |
      | 104.00 | 99.00  | 103.00 |
      | 106.00 | 101.00 | 105.00 |
      | 108.00 | 103.00 | 107.00 |
      | 110.00 | 105.00 | 109.00 |
      | 112.00 | 107.00 | 111.00 |
      | 114.00 | 109.00 | 113.00 |
      | 116.00 | 111.00 | 115.00 |
    When I calculate the Ichimoku Senkou Span B with period 9
    Then the Ichimoku Senkou Span B should be 105.50

  Scenario: Calculate Ichimoku Chikou Span
    Given the following market prices:
      | high   | low    | close  |
      | 100.00 | 95.00  | 98.00  |
      | 102.00 | 97.00  | 101.00 |
      | 104.00 | 99.00  | 103.00 |
      | 106.00 | 101.00 | 105.00 |
      | 108.00 | 103.00 | 107.00 |
      | 110.00 | 105.00 | 109.00 |
      | 112.00 | 107.00 | 111.00 |
      | 114.00 | 109.00 | 113.00 |
      | 116.00 | 111.00 | 115.00 |
    When I calculate the Ichimoku Chikou Span
    Then the Ichimoku Chikou Span should be 115.00