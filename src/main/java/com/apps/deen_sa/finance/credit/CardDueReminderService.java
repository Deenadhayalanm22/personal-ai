package com.apps.deen_sa.finance.credit;

import com.apps.deen_sa.finance.legacy.state.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class CardDueReminderService {
    private final StateContainerService containers;
    public CardDueReminderService(StateContainerService containers) { this.containers = containers; }

    public String reminders(Long userId, String timezone) {
        ZoneId zone = zone(timezone); LocalDate today = LocalDate.now(zone);
        List<String> reminders = containers.getActiveContainers(userId).stream()
                .filter(card -> "CREDIT_CARD".equals(card.getContainerType()))
                .filter(card -> card.getCurrentValue() != null && card.getCurrentValue().signum() > 0)
                .map(card -> reminder(card, today)).sorted().toList();
        return reminders.isEmpty() ? "No credit-card payment is currently due from the recorded outstanding balances."
                : "Card payment reminders:\n" + String.join("\n", reminders);
    }

    private String reminder(StateContainerEntity card, LocalDate today) {
        Integer dueDay = dueDay(card);
        if (dueDay == null) return "• " + card.getName() + ": ₹" + money(card.getCurrentValue()) + " outstanding; due day is not recorded.";
        LocalDate due = date(today.getYear(), today.getMonthValue(), dueDay);
        if (due.isBefore(today)) { YearMonth next = YearMonth.from(today).plusMonths(1); due = date(next.getYear(), next.getMonthValue(), dueDay); }
        long days = ChronoUnit.DAYS.between(today, due);
        return "• " + card.getName() + ": ₹" + money(card.getCurrentValue()) + " due on " + due
                + " (in " + days + (days == 1 ? " day)." : " days).");
    }
    private LocalDate date(int year, int month, int requestedDay) {
        YearMonth value = YearMonth.of(year, month); return value.atDay(Math.min(requestedDay, value.lengthOfMonth()));
    }
    private Integer dueDay(StateContainerEntity card) {
        Object value = card.getDetails() == null ? null : card.getDetails().get("dueDay");
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? null : Integer.valueOf(value.toString()); } catch (NumberFormatException ignored) { return null; }
    }
    private ZoneId zone(String value) { try { return ZoneId.of(value); } catch (Exception ignored) { return ZoneId.of("Asia/Kolkata"); } }
    private String money(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
}
