package com.apps.deen_sa.finance.query;

import com.apps.deen_sa.dto.TimeRange;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class TimeRangeResolver {

    public TimeRange resolve(String timePeriod) {

        String p = timePeriod.trim().toUpperCase();
        LocalDate today = LocalDate.now();
        Instant now = Instant.now();

        // ----------------------
        // TODAY
        // ----------------------
        if (p.equals("TODAY")) {
            return new TimeRange(
                    today.atStartOfDay(zone()).toInstant(),
                    now
            );
        }

        // ----------------------
        // THIS_* cases
        // ----------------------
        if (p.startsWith("THIS_")) {
            return resolveThisPeriod(p, today, now);
        }

        // ----------------------
        // LAST_* cases
        // ----------------------
        if (p.startsWith("LAST_")) {
            return resolveLastPeriod(p, today, now);
        }

        throw new IllegalArgumentException("Unsupported time period: " + timePeriod);
    }

    private TimeRange resolveThisPeriod(String p, LocalDate today, Instant now) {

        return switch (p) {

            case "THIS_WEEK" -> {
                LocalDate start = today.with(DayOfWeek.MONDAY);
                yield range(start, now);
            }

            case "THIS_MONTH" -> {
                LocalDate start = today.withDayOfMonth(1);
                yield range(start, now);
            }

            case "THIS_YEAR" -> {
                LocalDate start = today.withDayOfYear(1);
                yield range(start, now);
            }

            default -> throw new IllegalArgumentException("Unsupported time period: " + p);
        };
    }

    private TimeRange resolveLastPeriod(String p, LocalDate today, Instant now) {

        // LAST_MONTH, LAST_3_MONTHS, LAST_10_DAYS, etc.
        String[] parts = p.split("_");

        int amount = 1;
        String unit;

        if (parts.length == 2) {
            // LAST_MONTH
            unit = parts[1];
        } else if (parts.length == 3) {
            // LAST_3_MONTHS
            amount = Integer.parseInt(parts[1]);
            unit = parts[2];
        } else {
            throw new IllegalArgumentException("Invalid time period: " + p);
        }

        LocalDate start = switch (unit) {

            case "DAY", "DAYS" ->
                    today.minusDays(amount);

            case "WEEK", "WEEKS" ->
                    today.minusWeeks(amount);

            case "MONTH", "MONTHS" ->
                    today.minusMonths(amount);

            case "YEAR", "YEARS" ->
                    today.minusYears(amount);

            default ->
                    throw new IllegalArgumentException("Unsupported time unit: " + unit);
        };

        return range(start, now);
    }

    private TimeRange range(LocalDate start, Instant now) {
        return new TimeRange(
                start.atStartOfDay(zone()).toInstant(),
                now
        );
    }

    private ZoneId zone() {
        return ZoneId.systemDefault();
    }
}
