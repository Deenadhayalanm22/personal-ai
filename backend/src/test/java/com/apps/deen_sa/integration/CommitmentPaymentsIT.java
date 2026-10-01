package com.apps.deen_sa.integration;
import com.apps.deen_sa.domain.*;
import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.repository.*;
import com.apps.deen_sa.service.*;
import com.apps.deen_sa.exception.WebApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=validate"})
@Transactional
class CommitmentPaymentsIT {
    @org.springframework.test.context.DynamicPropertySource
    static void database(org.springframework.test.context.DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getProperty("payment.test.url", "jdbc:postgresql://localhost:5433/test_db"));
        properties.add("spring.datasource.username", () -> System.getProperty("payment.test.username", "test_user"));
        properties.add("spring.datasource.password", () -> System.getProperty("payment.test.password", "test_password"));
    }
    @Autowired WebRecurringCommitmentService service;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired FinancialTransactionRepository transactions;
    @Autowired AppUserRepository users;
    @Autowired UserRecurringCommitmentRepository commitments;
    @Autowired FinancialTransactionCalendarService calendar;
    @Autowired FinancialTransactionListService list;
    @Autowired FinancialTransactionEditService editor;
    @Autowired FinancialActivityService activity;
    @Autowired MonthlyFinancialSnapshotService snapshots;
    @Autowired MonthlyPaymentOverviewService overview;
    @Autowired Clock clock;
    @Autowired TransactionDraftRepository drafts;
    @Autowired CommitmentSavingsPlanRepository savingPlans;
    @Autowired CommitmentSavingsEntryRepository savingEntries;
    private LocalDate today() { return LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))); }
    private AppUserEntity user() {
        var u = new AppUserEntity(); u.setChannel("WHATSAPP"); u.setExternalUserId(UUID.randomUUID().toString());
        u.setCurrency("INR"); u.setTimezone("Asia/Kolkata"); u.setLocale("en-IN"); return users.saveAndFlush(u);
    }
    private UserRecurringCommitmentEntity commitment(AppUserEntity u) {
        var c = new UserRecurringCommitmentEntity(); c.setUser(u); c.setLabel("Internet bill"); c.setPlanningAmount(new BigDecimal("1000.00"));
        c.setAmountMode(CommitmentAmountMode.FIXED); c.setEffectiveMonth(today().withDayOfMonth(1));
        c.setFirstExpectedDate(today()); c.setNextExpectedDate(today()); c.setDueDay(Math.min(today().getDayOfMonth(), 28));
        c.setCreatedAt(today().minusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant()); return commitments.saveAndFlush(c);
    }
    private WebRecurringCommitmentService.CompletionRequest request(Long transaction) {
        return new WebRecurringCommitmentService.CompletionRequest(new BigDecimal("900.00"), today(), today().plusMonths(1), null, today().withDayOfMonth(1), transaction);
    }
    private FinancialTransactionEntity expense(AppUserEntity u, String amount) {
        var d = new TransactionDraftEntity(); d.setUser(u); d.setInputType(InputType.TEXT); d.setSource(MessageSource.WHATSAPP);
        d.setSourceMessageId(UUID.randomUUID().toString()); d.setRawText("Recorded bill"); d.setStatus(TransactionDraftStatus.CONSUMED); drafts.saveAndFlush(d);
        var t = new FinancialTransactionEntity(); t.setUser(u); t.setAmount(new BigDecimal(amount)); t.setOccurredAt(today());
        t.setSourceDraft(d); return transactions.saveAndFlush(t);
    }
    @Test void paymentAndRetryAreOneExpenseAndUnpaidPlanFallsByTheEstimate() {
        var u = user(); var c = commitment(u); var month = YearMonth.from(today());
        assertThat(overview.forMonth(u, month).stillToPay()).isEqualByComparingTo("1000");
        var paid = service.complete(u, c.getId(), request(null));
        assertThat(paid.transactionId()).isNotNull();
        assertThat(service.complete(u, c.getId(), request(null)).transactionId()).isEqualTo(paid.transactionId());
        assertThat(calendar.calendar(u, month).totalSpend()).isEqualByComparingTo("900");
        assertThat(calendar.calendar(u, month).commitmentSpend()).isEqualByComparingTo("900");
        assertThat(overview.forMonth(u, month).stillToPay()).isEqualByComparingTo("0");
        assertThat(snapshots.preview(u, month).fullIntendedCommitment()).isEqualByComparingTo("1000");
        assertThat(snapshots.preview(u, month.plusMonths(1)).fullIntendedCommitment()).isEqualByComparingTo("1000");
        assertThat(activity.list(u, month).items()).hasSize(1).first().extracting(FinancialActivityService.ActivityItem::type).isEqualTo("COMMITMENT");
        assertThat(list.list(u, month, 20, null, null).items()).hasSize(1).first().extracting(FinancialTransactionListService.ExpenseItem::originalMessage).isEqualTo("Internet bill");
        assertThatThrownBy(() -> service.complete(u, c.getId(), new WebRecurringCommitmentService.CompletionRequest(new BigDecimal("950"), today(), today().plusMonths(1), null, today().withDayOfMonth(1), null))).isInstanceOf(WebApiException.class);
    }
    @Test void attachingAnOwnedExpenseDoesNotCreateAnotherAndRejectsForeignOrWrongAmounts() {
        var u = user(); var c = commitment(u); var t = expense(u, "900.00");
        assertThat(service.complete(u, c.getId(), request(t.getId())).transactionId()).isEqualTo(t.getId());
        assertThat(calendar.calendar(u, YearMonth.from(today())).transactionCount()).isEqualTo(1);
        var other = commitment(u); var foreign = expense(user(), "900.00");
        assertThatThrownBy(() -> service.complete(u, other.getId(), request(foreign.getId()))).isInstanceOf(WebApiException.class);
        var wrong = expense(u, "100");
        assertThatThrownBy(() -> service.complete(u, other.getId(), request(wrong.getId()))).isInstanceOf(WebApiException.class);
        assertThatThrownBy(() -> service.complete(u, other.getId(), request(t.getId()))).isInstanceOf(WebApiException.class);
    }
    @Test void extrasAreSpendingOnceAndCorrectionsUpdateHistoryWithoutChangingThePlan() {
        var u = user(); var c = commitment(u); var month = YearMonth.from(today()); var paid = service.complete(u, c.getId(), request(null));
        var extra = new WebRecurringCommitmentService.ExtraRequest(new BigDecimal("200"), "Additional support", "retry-key", null);
        service.addExtra(u, c.getId(), month.toString(), extra); service.addExtra(u, c.getId(), month.toString(), extra);
        assertThat(calendar.calendar(u, month).totalSpend()).isEqualByComparingTo("1100");
        assertThat(calendar.calendar(u, month).transactionCount()).isEqualTo(2);
        assertThat(activity.list(u, month).items()).hasSize(2);
        editor.edit(u, paid.transactionId(), new FinancialTransactionEditService.ExpenseUpdate(new BigDecimal("850"), today(), null, null, null, null));
        assertThat(service.history(u, c.getId()).history().getFirst().actualAmount()).isEqualByComparingTo("850");
        assertThat(calendar.calendar(u, month).totalSpend()).isEqualByComparingTo("1050");
        assertThatThrownBy(() -> editor.delete(u, paid.transactionId())).isInstanceOf(WebApiException.class);
        Long extraTransaction = service.history(u, c.getId()).history().getFirst().extras().getFirst().transactionId();
        editor.edit(u, extraTransaction, new FinancialTransactionEditService.ExpenseUpdate(new BigDecimal("250"), today(), null, null, null, null));
        assertThat(service.history(u, c.getId()).history().getFirst().extraAmount()).isEqualByComparingTo("250");
        service.delete(u, c.getId());
        assertThat(calendar.calendar(u, month).totalSpend()).isEqualByComparingTo("1100");
        assertThat(activity.list(u, month).items()).hasSize(2);
    }
    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentCompletionsCommitOnePayment() throws Exception {
        var u = user(); var c = commitment(u); var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var results = executor.invokeAll(java.util.List.of(
                    () -> service.complete(u, c.getId(), request(null)).transactionId(),
                    () -> service.complete(u, c.getId(), request(null)).transactionId()));
            assertThat(results.get(0).get()).isEqualTo(results.get(1).get());
            assertThat(calendar.calendar(u, YearMonth.from(today())).transactionCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                service.delete(u, c.getId());
                transactions.findStoryEvidence(u.getId(), today().withDayOfMonth(1), today().plusMonths(1).withDayOfMonth(1), null, null, null)
                        .forEach(transactions::delete);
                snapshots.refreshCurrent(u);
            });
        }
    }
    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void invalidAttachmentRollsBackOccurrenceAndReminder() {
        var u = user(); var c = commitment(u); var wrong = expense(u, "100");
        try {
            assertThatThrownBy(() -> service.complete(u, c.getId(), request(wrong.getId()))).isInstanceOf(WebApiException.class);
            assertThat(service.history(u, c.getId()).history()).isEmpty();
            assertThat(service.list(u).items().getFirst().nextExpectedDate()).isEqualTo(today());
            assertThat(calendar.calendar(u, YearMonth.from(today())).transactionCount()).isEqualTo(1);
        } finally {
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                service.delete(u, c.getId()); transactions.deleteById(wrong.getId()); snapshots.refreshCurrent(u);
            });
        }
    }
    @Test void correctingPaymentDateMovesSpendingButKeepsItsScheduledPlan() {
        var u = user(); var c = commitment(u); var month = YearMonth.from(today()); var paid = service.complete(u, c.getId(), request(null));
        editor.edit(u, paid.transactionId(), new FinancialTransactionEditService.ExpenseUpdate(new BigDecimal("850"), month.minusMonths(1).atEndOfMonth(), null, null, null, null));
        assertThat(calendar.calendar(u, month).totalSpend()).isEqualByComparingTo("0");
        assertThat(calendar.calendar(u, month.minusMonths(1)).totalSpend()).isEqualByComparingTo("850");
        assertThat(snapshots.preview(u, month).fullIntendedCommitment()).isEqualByComparingTo("1000");
        assertThat(overview.forMonth(u, month).stillToPay()).isEqualByComparingTo("0");
        assertThat(service.history(u, c.getId()).history().getFirst().completedAt()).isEqualTo(month.minusMonths(1).atEndOfMonth());
    }
    @Test void retryDoesNotAllocateSavingsTwiceOrCountSavingsAsAnotherExpense() {
        var u = user(); var c = commitment(u); c.setRecurrenceInterval(12); commitments.saveAndFlush(c);
        var plan = new CommitmentSavingsPlanEntity(); plan.setCommitment(c); plan.setTargetDate(today());
        plan.setTargetAmount(new BigDecimal("1000")); plan.setStartMonth(today().minusMonths(1).withDayOfMonth(1));
        plan.setMonthlyAmount(new BigDecimal("500")); plan.setFinalAmount(new BigDecimal("500")); plan.setCreatedAt(Instant.now(clock)); savingPlans.saveAndFlush(plan);
        var entry = new CommitmentSavingsEntryEntity(); entry.setPlan(plan); entry.setScheduledMonth(plan.getStartMonth());
        entry.setStatus("SAVED"); entry.setAmount(new BigDecimal("500")); entry.setRecordedAt(plan.getStartMonth()); savingEntries.saveAndFlush(entry);
        var payment = new WebRecurringCommitmentService.CompletionRequest(new BigDecimal("900"), today(), today().plusMonths(1), new BigDecimal("500"), today().withDayOfMonth(1), null);
        var paid = service.complete(u, c.getId(), payment); service.complete(u, c.getId(), payment);
        assertThat(savingPlans.findById(plan.getId()).orElseThrow().getUsedAmount()).isEqualByComparingTo("500");
        assertThat(calendar.calendar(u, YearMonth.from(today())).totalSpend()).isEqualByComparingTo("900");
        assertThat(calendar.calendar(u, YearMonth.from(today())).transactionCount()).isEqualTo(1);
        assertThatThrownBy(() -> editor.edit(u, paid.transactionId(), new FinancialTransactionEditService.ExpenseUpdate(new BigDecimal("400"), null, null, null, null, null)))
                .isInstanceOf(WebApiException.class);
    }
    @Test void payingFirstWeeklyOccurrenceDoesNotRemoveLaterWeeks() {
        var u = user(); var c = commitment(u); c.setRecurrenceUnit(CommitmentRecurrenceUnit.WEEK);
        c.setFirstExpectedDate(today().withDayOfMonth(1)); c.setNextExpectedDate(today().withDayOfMonth(1));
        c.setCreatedAt(today().withDayOfMonth(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant()); commitments.saveAndFlush(c);
        var month = YearMonth.from(today()); var initial = overview.forMonth(u, month).stillToPay();
        service.complete(u, c.getId(), new WebRecurringCommitmentService.CompletionRequest(new BigDecimal("900"), today(), today().withDayOfMonth(1).plusWeeks(1), null, today().withDayOfMonth(1), null));
        assertThat(overview.forMonth(u, month).stillToPay()).isEqualByComparingTo(initial.subtract(new BigDecimal("1000")));
    }
}
