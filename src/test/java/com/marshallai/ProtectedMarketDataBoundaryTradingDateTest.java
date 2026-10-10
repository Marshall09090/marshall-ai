package com.marshallai;

import com.marshallai.governance.exposure.ProtectedPeriodType;
import com.marshallai.governance.exposure.ProtectedRunGuardRepository;
import com.marshallai.market.calendar.UsEquityTradingCalendar;
import com.marshallai.market.protection.ProtectedMarketDataAccessException;
import com.marshallai.market.protection.ProtectedMarketDataBoundary;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtectedMarketDataBoundaryTradingDateTest {

    private static PostgreSQLContainer postgres;

    private static ProtectedRunGuardRepository
            guardRepository;

    private static ProtectedMarketDataBoundary
            boundary;

    @BeforeAll
    static void initializeDatabase() {

        postgres =
                new PostgreSQLContainer(
                        "postgres:16-alpine"
                );

        postgres.start();

        Flyway.configure()
                .dataSource(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword()
                )
                .locations(
                        "classpath:db/migration"
                )
                .load()
                .migrate();

        DriverManagerDataSource dataSource =
                new DriverManagerDataSource();

        dataSource.setDriverClassName(
                "org.postgresql.Driver"
        );

        dataSource.setUrl(
                postgres.getJdbcUrl()
        );

        dataSource.setUsername(
                postgres.getUsername()
        );

        dataSource.setPassword(
                postgres.getPassword()
        );

        JdbcTemplate jdbcTemplate =
                new JdbcTemplate(
                        dataSource
                );

        DataSourceTransactionManager transactionManager =
                new DataSourceTransactionManager(
                        dataSource
                );

        guardRepository =
                new ProtectedRunGuardRepository(
                        jdbcTemplate,
                        transactionManager
                );

        boundary =
                ProtectedMarketDataBoundary
                        .forJdbcTemplate(
                                jdbcTemplate
                        );
    }

    @AfterAll
    static void stopDatabase() {

        if (postgres != null) {

            postgres.stop();
        }
    }

    @Test
    void requestEndingAtFirstProtectedTradingDateDoesNotEnterProtection() {

        LocalDate protectedStart =
                LocalDate.of(
                        2042,
                        7,
                        1
                );

        LocalDate protectedEnd =
                LocalDate.of(
                        2042,
                        7,
                        31
                );

        reserveValidationPeriod(
                protectedStart,
                protectedEnd
        );

        Instant from =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                LocalDate.of(
                                        2042,
                                        6,
                                        30
                                )
                        );

        /*
         * Exclusive end is exactly the beginning of
         * the first protected New York trading date.
         *
         * Therefore July 1 is NOT part of this request.
         */
        Instant to =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                protectedStart
                        );

        assertDoesNotThrow(
                () ->
                        boundary.authorizeRealMarketDataRequest(
                                from,
                                to
                        )
        );
    }

    @Test
    void firstProtectedTradingDateIsProtected() {

        LocalDate protectedStart =
                LocalDate.of(
                        2043,
                        7,
                        1
                );

        LocalDate protectedEnd =
                LocalDate.of(
                        2043,
                        7,
                        31
                );

        reserveValidationPeriod(
                protectedStart,
                protectedEnd
        );

        Instant from =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                protectedStart
                        );

        Instant to =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                protectedStart.plusDays(
                                        1
                                )
                        );

        ProtectedMarketDataAccessException exception =
                assertThrows(
                        ProtectedMarketDataAccessException.class,
                        () ->
                                boundary.authorizeRealMarketDataRequest(
                                        from,
                                        to
                                )
                );

        assertEquals(
                ProtectedMarketDataBoundary
                        .PROTECTED_VALIDATION_PERIOD,
                exception.getRefusalReason()
        );
    }

    @Test
    void lastProtectedTradingDateIsProtected() {

        LocalDate protectedStart =
                LocalDate.of(
                        2044,
                        7,
                        1
                );

        LocalDate protectedEnd =
                LocalDate.of(
                        2044,
                        7,
                        29
                );

        reserveValidationPeriod(
                protectedStart,
                protectedEnd
        );

        Instant from =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                protectedEnd
                        );

        /*
         * The protected end date is inclusive.
         *
         * The request ends exclusively at the following
         * New York calendar date, so protectedEnd is still
         * part of the request.
         */
        Instant to =
                UsEquityTradingCalendar
                        .endExclusiveAfter(
                                protectedEnd
                        );

        ProtectedMarketDataAccessException exception =
                assertThrows(
                        ProtectedMarketDataAccessException.class,
                        () ->
                                boundary.authorizeRealMarketDataRequest(
                                        from,
                                        to
                                )
                );

        assertEquals(
                ProtectedMarketDataBoundary
                        .PROTECTED_VALIDATION_PERIOD,
                exception.getRefusalReason()
        );
    }

    @Test
    void requestBeginningAfterProtectedEndDoesNotOverlapProtection() {

        LocalDate protectedStart =
                LocalDate.of(
                        2045,
                        7,
                        3
                );

        LocalDate protectedEnd =
                LocalDate.of(
                        2045,
                        7,
                        31
                );

        reserveValidationPeriod(
                protectedStart,
                protectedEnd
        );

        LocalDate firstDateAfterProtection =
                protectedEnd.plusDays(
                        1
                );

        Instant from =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                firstDateAfterProtection
                        );

        Instant to =
                UsEquityTradingCalendar
                        .startOfTradingDate(
                                firstDateAfterProtection
                                        .plusDays(
                                                3
                                        )
                        );

        assertDoesNotThrow(
                () ->
                        boundary.authorizeRealMarketDataRequest(
                                from,
                                to
                        )
        );
    }

    private static void reserveValidationPeriod(
            LocalDate start,
            LocalDate end) {

        guardRepository.createReservation(
                "trading-date-boundary-"
                        + UUID.randomUUID(),
                "validation-"
                        + UUID.randomUUID(),
                ProtectedPeriodType.VALIDATION,
                start,
                end,
                Instant.parse(
                        "2026-10-08T12:00:00Z"
                )
        );
    }
}