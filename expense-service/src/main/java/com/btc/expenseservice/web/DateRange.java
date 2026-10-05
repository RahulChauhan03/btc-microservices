package com.btc.expenseservice.web;

import com.btc.expenseservice.exception.InvalidRequestException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** A validated, bounded reporting period; defaults to the last 12 calendar months up to today. */
public record DateRange(LocalDate from, LocalDate to) {

    public static final long MAX_DAYS = 731;
    public static final DateRange ALL_TIME = new DateRange(LocalDate.of(1900, 1, 1), LocalDate.of(9999, 12, 31));

    public static DateRange resolve(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusMonths(11).withDayOfMonth(1) : from;
        if (start.isAfter(end)) {
            throw new InvalidRequestException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
            throw new InvalidRequestException("The date range can be at most two years");
        }
        return new DateRange(start, end);
    }
}
