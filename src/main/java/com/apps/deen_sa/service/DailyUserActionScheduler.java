package com.apps.deen_sa.service;

import org.springframework.stereotype.Component;

/** Evaluates all daily user-action candidates. Add new evaluators here as modules are introduced. */
@Component
public class DailyUserActionScheduler {
    private final LoanClosureReminderService loanClosureReminders;

    public DailyUserActionScheduler(LoanClosureReminderService loanClosureReminders) {
        this.loanClosureReminders = loanClosureReminders;
    }

    public void evaluateActions() {
        loanClosureReminders.createDueReminders();
    }
}
