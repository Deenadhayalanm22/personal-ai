package com.apps.deen_sa.resolver;

import com.apps.deen_sa.dto.TimeRange;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class TimeRangeResolver {

    public TimeRange resolve(String timePeriod) {

        LocalDate today = LocalDate.now();

        return switch (timePeriod) {

            case "TODAY" -> new TimeRange(
                    today.atStartOfDay(zone()).toInstant(),
                    Instant.now()
            );

            case "THIS_WEEK" -> {
                LocalDate start = today.with(DayOfWeek.MONDAY);
                yield new TimeRange(
                        start.atStartOfDay(zone()).toInstant(),
                        Instant.now()
                );
            }

            case "THIS_MONTH" -> {
                LocalDate start = today.withDayOfMonth(1);
                yield new TimeRange(
                        start.atStartOfDay(zone()).toInstant(),
                        Instant.now()
                );
            }

            case "THIS_YEAR" -> {
                LocalDate start = today.withDayOfYear(1);
                yield new TimeRange(
                        start.atStartOfDay(zone()).toInstant(),
                        Instant.now()
                );
            }

            default -> throw new IllegalArgumentException("Unsupported time period");
        };
    }

    private ZoneId zone() {
        return ZoneId.systemDefault();
    }
}
