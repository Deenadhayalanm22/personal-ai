package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.CommitmentRecurrenceUnit;
import com.apps.deen_sa.entity.UserRecurringCommitmentEntity;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Dates in a month for a weekly commitment, anchored to its next expected payment. */
final class RecurringCommitmentSchedule {
    private RecurringCommitmentSchedule() { }

    static boolean weekly(UserRecurringCommitmentEntity commitment) {
        return commitment.getRecurrenceUnit() == CommitmentRecurrenceUnit.WEEK;
    }

    static List<LocalDate> dates(UserRecurringCommitmentEntity commitment, YearMonth month, ZoneId zone) {
        if (commitment.getNextExpectedDate() == null) return List.of();
        if (!weekly(commitment)) return YearMonth.from(commitment.getNextExpectedDate()).equals(month)
                ? List.of(commitment.getNextExpectedDate()) : List.of();
        int days = 7 * Math.max(1, commitment.getRecurrenceInterval());
        LocalDate firstAllowed = commitment.getEffectiveMonth();
        if (commitment.getCreatedAt() != null) {
            LocalDate created = commitment.getCreatedAt().atZone(zone).toLocalDate();
            if (created.isAfter(firstAllowed)) firstAllowed = created;
        }
        LocalDate date = commitment.getNextExpectedDate();
        while (!date.minusDays(days).isBefore(month.atDay(1)) && !date.minusDays(days).isBefore(firstAllowed)) date = date.minusDays(days);
        while (date.isBefore(month.atDay(1))) date = date.plusDays(days);
        List<LocalDate> dates = new ArrayList<>();
        for (; !date.isAfter(month.atEndOfMonth()); date = date.plusDays(days)) {
            if (!date.isBefore(firstAllowed)) dates.add(date);
        }
        return dates;
    }
}
