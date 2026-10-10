package com.marshallai;

import com.marshallai.market.calendar.UsEquityTradingCalendar;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class UsEquityTradingCalendarTest {

    @Test
    void mondayExclusiveEndMapsToPreviousFriday() {

        LocalDate exclusiveMonday =
                LocalDate.of(
                        2026,
                        10,
                        5
                );

        assertEquals(
                LocalDate.of(
                        2026,
                        10,
                        2
                ),
                UsEquityTradingCalendar
                        .lastTradingDateBefore(
                                exclusiveMonday
                        )
        );
    }

    @Test
    void dayAfterIndependenceHolidayMapsToPreviousTradingDay() {

        /*
         * July 4, 2026 is Saturday.
         *
         * The market holiday is therefore observed
         * Friday July 3.
         *
         * Monday July 6 is the next trading day, so an
         * exclusive boundary of July 6 must map back to
         * Thursday July 2.
         */
        LocalDate exclusiveMonday =
                LocalDate.of(
                        2026,
                        7,
                        6
                );

        assertEquals(
                LocalDate.of(
                        2026,
                        7,
                        2
                ),
                UsEquityTradingCalendar
                        .lastTradingDateBefore(
                                exclusiveMonday
                        )
        );
    }

    @Test
    void newYorkMidnightUsesFourUtcDuringDaylightSavingTime() {

        Instant start =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                LocalDate.of(
                                        2026,
                                        7,
                                        6
                                )
                        );

        assertEquals(
                Instant.parse(
                        "2026-07-06T04:00:00Z"
                ),
                start
        );
    }

    @Test
    void newYorkMidnightUsesFiveUtcDuringStandardTime() {

        Instant start =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                LocalDate.of(
                                        2026,
                                        1,
                                        5
                                )
                        );

        assertEquals(
                Instant.parse(
                        "2026-01-05T05:00:00Z"
                ),
                start
        );
    }

    @Test
    void instantIsConvertedToNewYorkTradingDate() {

        Instant dailyBarTimestamp =
                Instant.parse(
                        "2026-07-06T04:00:00Z"
                );

        assertEquals(
                LocalDate.of(
                        2026,
                        7,
                        6
                ),
                UsEquityTradingCalendar
                        .tradingDate(
                                dailyBarTimestamp
                        )
        );
    }

    @Test
    void exclusiveEndAfterInclusiveDateUsesNewYorkMidnight() {

        Instant endExclusive =
                UsEquityTradingCalendar
                        .endExclusiveAfter(
                                LocalDate.of(
                                        2026,
                                        7,
                                        2
                                )
                        );

        assertEquals(
                Instant.parse(
                        "2026-07-03T04:00:00Z"
                ),
                endExclusive
        );
    }
}