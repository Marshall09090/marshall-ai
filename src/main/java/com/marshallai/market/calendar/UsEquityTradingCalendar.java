package com.marshallai.market.calendar;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

public final class UsEquityTradingCalendar {

    public static final ZoneId MARKET_ZONE =
            ZoneId.of("America/New_York");

    private UsEquityTradingCalendar() {
    }

    /**
     * Converts an instant to its US-equity trading date.
     *
     * Trading dates are defined in America/New_York,
     * never by the UTC calendar date.
     */
    public static LocalDate tradingDate(
            Instant instant) {

        if (instant == null) {
            throw new IllegalArgumentException(
                    "Instant cannot be null"
            );
        }

        return instant
                .atZone(MARKET_ZONE)
                .toLocalDate();
    }

    /**
     * Start of a trading calendar date in New York.
     *
     * Depending on daylight saving time this becomes
     * either 04:00Z or 05:00Z.
     */
    public static Instant startOfTradingDate(
            LocalDate tradingDate) {

        requireDate(
                tradingDate,
                "Trading date"
        );

        return tradingDate
                .atStartOfDay(MARKET_ZONE)
                .toInstant();
    }

    /**
     * Exclusive boundary immediately after an inclusive
     * trading-period end date.
     */
    public static Instant endExclusiveAfter(
            LocalDate inclusiveTradingDate) {

        requireDate(
                inclusiveTradingDate,
                "Inclusive trading date"
        );

        return inclusiveTradingDate
                .plusDays(1)
                .atStartOfDay(MARKET_ZONE)
                .toInstant();
    }

    /**
     * Returns the final valid US-equity trading date strictly
     * before the supplied exclusive calendar date.
     *
     * Example:
     *
     * exclusive Monday -> previous Friday
     *
     * If the immediately preceding weekday is an exchange
     * holiday, the search continues backward.
     */
    public static LocalDate lastTradingDateBefore(
            LocalDate exclusiveDate) {

        requireDate(
                exclusiveDate,
                "Exclusive date"
        );

        LocalDate candidate =
                exclusiveDate.minusDays(1);

        while (!isTradingDay(candidate)) {
            candidate =
                    candidate.minusDays(1);
        }

        return candidate;
    }

    /**
     * Convenience conversion for an internal [from, to)
     * daily-bar request whose exclusive boundary is represented
     * as an Instant.
     */
    public static LocalDate lastTradingDateBefore(
            Instant exclusiveTo) {

        if (exclusiveTo == null) {
            throw new IllegalArgumentException(
                    "Exclusive end cannot be null"
            );
        }

        return lastTradingDateBefore(
                tradingDate(exclusiveTo)
        );
    }

    public static boolean isTradingDay(
            LocalDate date) {

        requireDate(
                date,
                "Trading date"
        );

        DayOfWeek dayOfWeek =
                date.getDayOfWeek();

        if (dayOfWeek == DayOfWeek.SATURDAY
                || dayOfWeek == DayOfWeek.SUNDAY) {

            return false;
        }

        return !isStandardMarketHoliday(
                date
        );
    }

    private static boolean isStandardMarketHoliday(
            LocalDate date) {

        int year =
                date.getYear();

        if (date.equals(
                observedHoliday(
                        LocalDate.of(
                                year,
                                Month.JANUARY,
                                1
                        )
                )
        )) {
            return true;
        }

        /*
         * A Saturday New Year's Day can be observed on
         * December 31 of the previous calendar year.
         */
        if (date.equals(
                observedHoliday(
                        LocalDate.of(
                                year + 1,
                                Month.JANUARY,
                                1
                        )
                )
        )) {
            return true;
        }

        LocalDate martinLutherKingDay =
                LocalDate.of(
                                year,
                                Month.JANUARY,
                                1
                        )
                        .with(
                                TemporalAdjusters
                                        .dayOfWeekInMonth(
                                                3,
                                                DayOfWeek.MONDAY
                                        )
                        );

        if (date.equals(
                martinLutherKingDay
        )) {
            return true;
        }

        LocalDate presidentsDay =
                LocalDate.of(
                                year,
                                Month.FEBRUARY,
                                1
                        )
                        .with(
                                TemporalAdjusters
                                        .dayOfWeekInMonth(
                                                3,
                                                DayOfWeek.MONDAY
                                        )
                        );

        if (date.equals(
                presidentsDay
        )) {
            return true;
        }

        if (date.equals(
                easterSunday(year)
                        .minusDays(2)
        )) {
            return true;
        }

        LocalDate memorialDay =
                LocalDate.of(
                                year,
                                Month.MAY,
                                31
                        )
                        .with(
                                TemporalAdjusters
                                        .previousOrSame(
                                                DayOfWeek.MONDAY
                                        )
                        );

        if (date.equals(
                memorialDay
        )) {
            return true;
        }

        /*
         * NYSE began observing Juneteenth in 2022.
         */
        if (year >= 2022
                && date.equals(
                observedHoliday(
                        LocalDate.of(
                                year,
                                Month.JUNE,
                                19
                        )
                )
        )) {
            return true;
        }

        if (date.equals(
                observedHoliday(
                        LocalDate.of(
                                year,
                                Month.JULY,
                                4
                        )
                )
        )) {
            return true;
        }

        LocalDate laborDay =
                LocalDate.of(
                                year,
                                Month.SEPTEMBER,
                                1
                        )
                        .with(
                                TemporalAdjusters
                                        .firstInMonth(
                                                DayOfWeek.MONDAY
                                        )
                        );

        if (date.equals(
                laborDay
        )) {
            return true;
        }

        LocalDate thanksgiving =
                LocalDate.of(
                                year,
                                Month.NOVEMBER,
                                1
                        )
                        .with(
                                TemporalAdjusters
                                        .dayOfWeekInMonth(
                                                4,
                                                DayOfWeek.THURSDAY
                                        )
                        );

        if (date.equals(
                thanksgiving
        )) {
            return true;
        }

        return date.equals(
                observedHoliday(
                        LocalDate.of(
                                year,
                                Month.DECEMBER,
                                25
                        )
                )
        );
    }

    private static LocalDate observedHoliday(
            LocalDate holiday) {

        return switch (
                holiday.getDayOfWeek()
                ) {

            case SATURDAY ->
                    holiday.minusDays(1);

            case SUNDAY ->
                    holiday.plusDays(1);

            default ->
                    holiday;
        };
    }

    /**
     * Gregorian Easter calculation.
     *
     * Needed because Good Friday is an exchange holiday
     * even though it is not a fixed-date federal holiday.
     */
    private static LocalDate easterSunday(
            int year) {

        int a =
                year % 19;

        int b =
                year / 100;

        int c =
                year % 100;

        int d =
                b / 4;

        int e =
                b % 4;

        int f =
                (b + 8) / 25;

        int g =
                (b - f + 1) / 3;

        int h =
                (19 * a
                        + b
                        - d
                        - g
                        + 15)
                        % 30;

        int i =
                c / 4;

        int k =
                c % 4;

        int l =
                (32
                        + 2 * e
                        + 2 * i
                        - h
                        - k)
                        % 7;

        int m =
                (a
                        + 11 * h
                        + 22 * l)
                        / 451;

        int month =
                (h
                        + l
                        - 7 * m
                        + 114)
                        / 31;

        int day =
                ((h
                        + l
                        - 7 * m
                        + 114)
                        % 31)
                        + 1;

        return LocalDate.of(
                year,
                month,
                day
        );
    }

    private static void requireDate(
            LocalDate date,
            String fieldName) {

        if (date == null) {
            throw new IllegalArgumentException(
                    fieldName
                            + " cannot be null"
            );
        }
    }
}