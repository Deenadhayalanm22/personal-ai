package com.apps.deen_sa.service;

import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Shared advance window for scheduled payment and skip decisions. */
@Component
public class PaymentActionWindow {
    @Value("${app.payments.advance-days:5}")
    private int advanceDays = 5;

    public boolean available(LocalDate dueDate, LocalDate today) {
        return dueDate != null && YearMonth.from(dueDate).equals(YearMonth.from(today))
                && !today.isBefore(dueDate.minusDays(Math.max(0, advanceDays)));
    }
}
