package com.apps.deen_sa.service;

import org.springframework.stereotype.Component;

/** Evaluates all daily user-action candidates. Add new evaluators here as modules are introduced. */
/** FIN-EPIC-005 — Loans and mutual-fund planning. See docs/jira/personal-expense/FIN-EPIC-005-planning.md. */
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
