package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.CommitmentAmountMode;
import com.apps.deen_sa.domain.RecurringCommitmentStatus;
import com.apps.deen_sa.domain.CommitmentRecurrenceUnit;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.FinancialTransactionEntity;
import com.apps.deen_sa.entity.UserRecurringCommitmentEntity;
import com.apps.deen_sa.entity.RecurringCommitmentOccurrenceEntity;
import com.apps.deen_sa.entity.RecurringCommitmentExtraEntity;
import com.apps.deen_sa.domain.RecurringCommitmentOccurrenceStatus;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.FinancialTransactionRepository;
import com.apps.deen_sa.repository.UserRecurringCommitmentRepository;
import com.apps.deen_sa.repository.RecurringCommitmentOccurrenceRepository;
import com.apps.deen_sa.repository.RecurringCommitmentExtraRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Comparator;

/** FIN-020 — explicit recurring commitments and opt-in recent-bill projections. */
@Service
public class WebRecurringCommitmentService {
    private final UserRecurringCommitmentRepository commitments;
    private final FinancialTransactionRepository transactions;
    private final MonthlyFinancialSnapshotService snapshots;
    private final RecurringCommitmentOccurrenceRepository occurrences;
    private final RecurringCommitmentExtraRepository extras;
    private final java.time.Clock clock;
    @org.springframework.beans.factory.annotation.Autowired private CommitmentSavingsService savings;
    public WebRecurringCommitmentService(UserRecurringCommitmentRepository commitments, FinancialTransactionRepository transactions,
                                         MonthlyFinancialSnapshotService snapshots, RecurringCommitmentOccurrenceRepository occurrences,
                                         RecurringCommitmentExtraRepository extras, java.time.Clock clock) {
        this.commitments = commitments; this.transactions = transactions; this.snapshots = snapshots; this.occurrences = occurrences; this.extras = extras; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public CommitmentListResponse list(AppUserEntity user) { return new CommitmentListResponse(commitments.findAllOwned(user.getId()).stream().map(value -> response(value, user)).toList()); }
    @Transactional(readOnly = true)
    public CommitmentHistoryResponse history(AppUserEntity user, Long id) {
        UserRecurringCommitmentEntity commitment = owned(user, id);
        return new CommitmentHistoryResponse(commitment.getId(), commitment.getLabel(), commitment.getPlanningAmount(),
                occurrences.findByCommitmentIdOrderByScheduledMonthDesc(commitment.getId()).stream()
                        .map(item -> new OccurrenceResponse(item.getScheduledMonth().toString().substring(0, 7),
                                item.getScheduledMonth(), item.getStatus().name(), item.getCompletedAt(), item.getActualAmount(), item.getExtraAmount(),
                                extras.findByOccurrenceIdOrderByCreatedAtAsc(item.getId()).stream().map(extra -> new ExtraResponse(extra.getAmount(), extra.getReason())).toList(), item.getSavingsUsed())).toList());
    }
    @Transactional
    public CommitmentResponse create(AppUserEntity user, CommitmentRequest request) {
        UserRecurringCommitmentEntity value = new UserRecurringCommitmentEntity(); value.setUser(user); apply(user, value, request, true);
        value.setCreatedAt(java.time.Instant.now(clock));
        commitments.saveAndFlush(value); snapshots.refreshCurrent(user); return response(value, user);
    }
    @Transactional
    public CommitmentResponse update(AppUserEntity user, Long id, CommitmentRequest request) {
        UserRecurringCommitmentEntity value = owned(user, id); apply(user, value, request, false); commitments.saveAndFlush(value); snapshots.refreshCurrent(user); return response(value, user);
    }
    @Transactional
    public void delete(AppUserEntity user, Long id) {
        UserRecurringCommitmentEntity value = owned(user, id);
        transactions.findByRecurringCommitmentId(value.getId()).forEach(transaction -> {
            transaction.setRecurringCommitment(null);
            transaction.setCommitmentMatchStatus(com.apps.deen_sa.domain.CommitmentMatchStatus.NOT_LINKED);
        });
        value.setLinkedTransactions(List.of());
        occurrences.findByCommitmentIdOrderByScheduledMonthDesc(value.getId()).forEach(occurrence -> extras.deleteAll(extras.findByOccurrenceIdOrderByCreatedAtAsc(occurrence.getId())));
        occurrences.deleteByCommitmentId(value.getId());
        commitments.delete(value);
        snapshots.refreshCurrent(user);
    }
    @Transactional
    public OccurrenceResponse markDone(AppUserEntity user, Long id, String month) {
        UserRecurringCommitmentEntity commitment = owned(user, id);
        if (RecurringCommitmentSchedule.dated(commitment)) throw invalid("Choose a scheduled payment");
        YearMonth scheduled = parseMonth(month);
        LocalDate today = LocalDate.now(clock.withZone(java.time.ZoneId.of(user.getTimezone())));
        LocalDate dueDate = commitment.getDueDay() == null ? null : scheduled.atDay(Math.min(commitment.getDueDay(), scheduled.lengthOfMonth()));
        if (commitment.getStatus() != RecurringCommitmentStatus.ACTIVE || scheduled.atDay(1).isBefore(commitment.getEffectiveMonth())
                || dueDate == null || !scheduled.equals(YearMonth.from(today)) || today.isBefore(dueDate) || createdAfterDueDate(user, commitment, scheduled, dueDate)) throw invalid("Commitment is not due this month");
        RecurringCommitmentOccurrenceEntity occurrence = occurrences.findByCommitmentIdAndScheduledMonth(commitment.getId(), scheduled.atDay(1)).orElseGet(RecurringCommitmentOccurrenceEntity::new);
        occurrence.setCommitment(commitment); occurrence.setScheduledMonth(scheduled.atDay(1)); occurrence.setStatus(RecurringCommitmentOccurrenceStatus.COMPLETED);
        occurrence.setCompletedAt(today); occurrence.setUpdatedAt(java.time.Instant.now(clock)); occurrences.saveAndFlush(occurrence);
        return occurrenceResponse(user, commitment, scheduled, today);
    }
    /** Records a real payment independently from the estimate and lets the user reset the next reminder. */
    @Transactional
    public OccurrenceResponse complete(AppUserEntity user, Long id, CompletionRequest request) {
        UserRecurringCommitmentEntity commitment = owned(user, id);
        if (request == null || request.actualAmount() == null || request.actualAmount().signum() <= 0 || request.completedAt() == null || request.nextExpectedDate() == null)
            throw invalid("Actual amount, completion date, and next expected date are required");
        if (commitment.getStatus() != RecurringCommitmentStatus.ACTIVE) throw invalid("Commitment is not active");
        LocalDate completed = request.completedAt();
        LocalDate scheduledDate = RecurringCommitmentSchedule.dated(commitment) ? request.occurrenceDate() : completed.withDayOfMonth(1);
        if (RecurringCommitmentSchedule.dated(commitment) && (scheduledDate == null || completed.isBefore(scheduledDate)
                || !RecurringCommitmentSchedule.dates(commitment, YearMonth.from(scheduledDate), java.time.ZoneId.of(user.getTimezone())).contains(scheduledDate)
                || !request.nextExpectedDate().isAfter(scheduledDate))) throw invalid("Choose a scheduled payment and a later next date");
        RecurringCommitmentOccurrenceEntity occurrence = occurrences.findByCommitmentIdAndScheduledMonth(commitment.getId(), scheduledDate).orElseGet(RecurringCommitmentOccurrenceEntity::new);
        if (occurrence.getStatus() == RecurringCommitmentOccurrenceStatus.SKIPPED || occurrence.getStatus() == RecurringCommitmentOccurrenceStatus.COMPLETED)
            throw invalid("This month already has an outcome");
        if (request.savingsUsed() != null) savings.allocate(user, id, commitment.getNextExpectedDate(), request.savingsUsed(), request.actualAmount());
        occurrence.setCommitment(commitment); occurrence.setScheduledMonth(scheduledDate); occurrence.setStatus(RecurringCommitmentOccurrenceStatus.COMPLETED);
        occurrence.setSavingsUsed(request.savingsUsed());
        occurrence.setCompletedAt(completed); occurrence.setActualAmount(request.actualAmount().setScale(2, RoundingMode.HALF_UP)); occurrence.setUpdatedAt(java.time.Instant.now(clock)); occurrences.saveAndFlush(occurrence);
        commitment.setNextExpectedDate(request.nextExpectedDate()); commitments.saveAndFlush(commitment); snapshots.refreshCurrent(user);
        LocalDate today = LocalDate.now(clock.withZone(java.time.ZoneId.of(user.getTimezone())));
        return RecurringCommitmentSchedule.dated(commitment)
                ? datedOccurrences(user, commitment, YearMonth.from(scheduledDate), today).stream()
                    .filter(item -> scheduledDate.equals(item.dueDate())).findFirst().orElseThrow()
                : occurrenceResponse(user, commitment, YearMonth.from(completed), today);
    }
    @Transactional
    public OccurrenceResponse skip(AppUserEntity user, Long id, String month) {
        UserRecurringCommitmentEntity commitment = owned(user, id);
        YearMonth scheduled = parseMonth(month.substring(0, 7));
        LocalDate today = LocalDate.now(clock.withZone(java.time.ZoneId.of(user.getTimezone())));
        LocalDate scheduledDate = RecurringCommitmentSchedule.dated(commitment) ? LocalDate.parse(month) : scheduled.atDay(1);
        if (commitment.getStatus() != RecurringCommitmentStatus.ACTIVE || !scheduled.equals(YearMonth.from(today))
                || !(RecurringCommitmentSchedule.dated(commitment)
                    ? datedOccurrences(user, commitment, scheduled, today).stream().anyMatch(item -> scheduledDate.equals(item.dueDate()) && "DUE".equals(item.status()))
                    : "DUE".equals(occurrenceResponse(user, commitment, scheduled, today).status())))
            throw invalid("Only a due current-month occurrence can be skipped");
        RecurringCommitmentOccurrenceEntity occurrence = occurrences.findByCommitmentIdAndScheduledMonth(id, scheduledDate).orElseGet(RecurringCommitmentOccurrenceEntity::new);
        if (occurrence.getStatus() == RecurringCommitmentOccurrenceStatus.COMPLETED) throw invalid("A paid occurrence cannot be skipped");
        occurrence.setCommitment(commitment); occurrence.setScheduledMonth(scheduledDate); occurrence.setStatus(RecurringCommitmentOccurrenceStatus.SKIPPED);
        occurrence.setUpdatedAt(java.time.Instant.now(clock)); occurrences.saveAndFlush(occurrence);
        if (RecurringCommitmentSchedule.dated(commitment) && scheduledDate.equals(commitment.getNextExpectedDate())) {
            commitment.setNextExpectedDate(scheduledDate.plusDays(RecurringCommitmentSchedule.intervalDays(commitment))); commitments.saveAndFlush(commitment);
        }
        snapshots.refreshCurrent(user);
        return RecurringCommitmentSchedule.dated(commitment)
                ? datedOccurrences(user, commitment, scheduled, today).stream()
                    .filter(item -> scheduledDate.equals(item.dueDate())).findFirst().orElseThrow()
                : occurrenceResponse(user, commitment, scheduled, today);
    }
    @Transactional
    public OccurrenceResponse addExtra(AppUserEntity user, Long id, String month, ExtraRequest request) {
        UserRecurringCommitmentEntity commitment = owned(user, id);
        YearMonth scheduled = parseMonth(month.substring(0, 7));
        LocalDate scheduledDate = RecurringCommitmentSchedule.dated(commitment) ? LocalDate.parse(month) : scheduled.atDay(1);
        if (request == null || request.amount() == null || request.amount().signum() <= 0 || request.reason() == null || request.reason().isBlank() || request.reason().trim().length() > 200) throw invalid("Positive extra amount and a reason of 1 to 200 characters are required");
        RecurringCommitmentOccurrenceEntity occurrence = occurrences.findByCommitmentIdAndScheduledMonth(id, scheduledDate).orElseThrow(() -> invalid("Record a payment before adding extra"));
        if (occurrence.getStatus() != RecurringCommitmentOccurrenceStatus.COMPLETED) throw invalid("Record a payment before adding extra");
        occurrence.setExtraAmount((occurrence.getExtraAmount() == null ? BigDecimal.ZERO : occurrence.getExtraAmount()).add(request.amount()).setScale(2, RoundingMode.HALF_UP));
        occurrence.setUpdatedAt(java.time.Instant.now(clock)); occurrences.saveAndFlush(occurrence);
        RecurringCommitmentExtraEntity extra = new RecurringCommitmentExtraEntity();
        extra.setOccurrence(occurrence); extra.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP)); extra.setReason(request.reason().trim()); extra.setCreatedAt(java.time.Instant.now(clock)); extras.saveAndFlush(extra);
        return RecurringCommitmentSchedule.dated(commitment)
                ? datedOccurrences(user, commitment, scheduled, LocalDate.now(clock.withZone(java.time.ZoneId.of(user.getTimezone())))).stream()
                    .filter(item -> scheduledDate.equals(item.dueDate())).findFirst().orElseThrow()
                : occurrenceResponse(user, commitment, scheduled, LocalDate.now(clock.withZone(java.time.ZoneId.of(user.getTimezone()))));
    }
    @Transactional(readOnly = true)
    public CommitmentReviewResponse review(AppUserEntity user, String month) {
        YearMonth scope = month == null ? YearMonth.now() : parseMonth(month);
        return new CommitmentReviewResponse(transactions.findCommitmentCandidates(user.getId(), scope.atDay(1), scope.plusMonths(1).atDay(1)).stream()
                .map(tx -> new CandidateResponse(tx.getId(), tx.getAmount(), tx.getOccurredAt(), tx.getCategory(), tx.getSubcategory(),
                        commitments.findAllOwned(user.getId()).stream().filter(c -> c.getStatus() == RecurringCommitmentStatus.ACTIVE
                                && java.util.Objects.equals(c.getCategory(), tx.getCategory()) && java.util.Objects.equals(c.getSubcategory(), tx.getSubcategory()))
                                .map(this::response).toList())).toList());
    }
    @Transactional
    public void resolve(AppUserEntity user, Long transactionId, ResolveRequest request) {
        FinancialTransactionEntity transaction = transactions.findOwnedVisibleById(transactionId, user.getId()).orElseThrow(() -> invalid("Transaction is unavailable"));
        if (request == null || request.commitmentId() == null) { transaction.setRecurringCommitment(null); transaction.setCommitmentMatchStatus(com.apps.deen_sa.domain.CommitmentMatchStatus.NOT_LINKED); }
        else { UserRecurringCommitmentEntity commitment = owned(user, request.commitmentId()); transaction.setRecurringCommitment(commitment); transaction.setCommitmentMatchStatus(com.apps.deen_sa.domain.CommitmentMatchStatus.MATCHED); }
        transactions.saveAndFlush(transaction); snapshots.refreshCurrent(user);
    }
    private void apply(AppUserEntity user, UserRecurringCommitmentEntity value, CommitmentRequest r, boolean creating) {
        if (r == null) throw invalid("Commitment details are required");
        if (r.label() != null) { if (r.label().isBlank() || r.label().length() > 120) throw invalid("Label must be 1 to 120 characters"); value.setLabel(r.label().trim()); }
        else if (creating) throw invalid("Label is required");
        if (r.amountMode() != null) value.setAmountMode(parseMode(r.amountMode())); else if (creating) throw invalid("Amount mode is required");
        if (r.planningAmount() != null) { if (r.planningAmount().signum() <= 0) throw invalid("Planning amount must be greater than zero"); value.setPlanningAmount(r.planningAmount().setScale(2, RoundingMode.HALF_UP)); }
        else if (creating) throw invalid("Planning amount is required");
        if (r.dueDay() != null) { if (r.dueDay() < 1 || r.dueDay() > 28) throw invalid("Due day must be between 1 and 28"); value.setDueDay(r.dueDay()); }
        if (r.recurrenceUnit() != null) value.setRecurrenceUnit(parseUnit(r.recurrenceUnit())); else if (creating) value.setRecurrenceUnit(CommitmentRecurrenceUnit.MONTH);
        if (r.recurrenceInterval() != null) { if (r.recurrenceInterval() < 1 || r.recurrenceInterval() > 365) throw invalid("Recurrence interval must be between 1 and 365"); value.setRecurrenceInterval(r.recurrenceInterval()); }
        if (r.flexibleSchedule() != null) value.setFlexibleSchedule(r.flexibleSchedule());
        if (r.nextExpectedDate() != null) {
            if (creating || !r.nextExpectedDate().equals(value.getNextExpectedDate())) value.setFirstExpectedDate(r.nextExpectedDate());
            value.setNextExpectedDate(r.nextExpectedDate());
        }
        if (r.effectiveMonth() != null) value.setEffectiveMonth(parseMonth(r.effectiveMonth()).atDay(1)); else if (creating) value.setEffectiveMonth(YearMonth.now().atDay(1));
        if (creating && value.getNextExpectedDate() == null) value.setNextExpectedDate(value.getEffectiveMonth().withDayOfMonth(value.getDueDay() == null ? 1 : value.getDueDay()));
        if (creating && value.getFirstExpectedDate() == null) value.setFirstExpectedDate(value.getNextExpectedDate());
        if (r.status() != null) value.setStatus(parseStatus(r.status()));
        if (r.category() != null) value.setCategory(r.category()); if (r.subcategory() != null) value.setSubcategory(r.subcategory());
        if (r.transactionIds() != null) value.setLinkedTransactions(r.transactionIds().stream().distinct().map(id -> transactions.findOwnedVisibleById(id, user.getId()).orElseThrow(() -> invalid("Transaction is unavailable"))).toList());
        if (r.sourceTransactionId() != null && r.transactionIds() == null) value.setLinkedTransactions(List.of(transactions.findOwnedVisibleById(r.sourceTransactionId(), user.getId()).orElseThrow(() -> invalid("Transaction is unavailable"))));
        if (creating && value.getLinkedTransactions().size() == 1) {
            FinancialTransactionEntity tx = value.getLinkedTransactions().getFirst();
            if (r.category() == null) value.setCategory(tx.getCategory()); if (r.subcategory() == null) value.setSubcategory(tx.getSubcategory()); if (value.getLabel() == null) value.setLabel(tx.getMerchant() == null ? tx.getCategory() : tx.getMerchant().getCanonicalName());
            value.setMerchant(tx.getMerchant());
        }
        if (value.getAmountMode() == CommitmentAmountMode.RECENT_BILL_ESTIMATE) {
            if (value.getLinkedTransactions().size() < 2) throw invalid("Link at least two payments for a recent-bill estimate");
            value.setPlanningAmount(recentBillEstimate(value.getLinkedTransactions()));
        }
        value.setUpdatedAt(java.time.Instant.now());
    }
    private UserRecurringCommitmentEntity owned(AppUserEntity u, Long id) { return commitments.findOwned(id, u.getId()).orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "COMMITMENT_NOT_FOUND", "Commitment not found")); }
    private CommitmentAmountMode parseMode(String v) { try { return CommitmentAmountMode.valueOf(v); } catch (Exception e) { throw invalid("Invalid amount mode"); } }
    private CommitmentRecurrenceUnit parseUnit(String v) { try { return CommitmentRecurrenceUnit.valueOf(v); } catch (Exception e) { throw invalid("Invalid recurrence unit"); } }
    private RecurringCommitmentStatus parseStatus(String v) { try { return RecurringCommitmentStatus.valueOf(v); } catch (Exception e) { throw invalid("Invalid commitment status"); } }
    private YearMonth parseMonth(String v) { try { return YearMonth.parse(v); } catch (Exception e) { throw invalid("Effective month must be YYYY-MM"); } }
    private WebApiException invalid(String m) { return new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_COMMITMENT", m); }
    /** Median is deliberately robust to one unusually high utility bill. */
    private BigDecimal recentBillEstimate(List<FinancialTransactionEntity> linked) {
        List<BigDecimal> amounts = linked.stream().map(FinancialTransactionEntity::getAmount).sorted().toList();
        int middle = amounts.size() / 2;
        BigDecimal estimate = amounts.size() % 2 == 1 ? amounts.get(middle) : amounts.get(middle - 1).add(amounts.get(middle)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        return estimate.setScale(2, RoundingMode.HALF_UP);
    }
    private CommitmentResponse response(UserRecurringCommitmentEntity c) {
        return new CommitmentResponse(c.getId(), c.getLabel(), c.getAmountMode().name(), c.getPlanningAmount(), c.getDueDay(), c.getEffectiveMonth().toString().substring(0, 7), c.getStatus().name(), c.getCategory(), c.getSubcategory(), c.getLinkedTransactions().stream().map(FinancialTransactionEntity::getId).toList(), c.getRecurrenceUnit().name(), c.getRecurrenceInterval(), c.isFlexibleSchedule(), c.getNextExpectedDate(), null, List.of());
    }
    private CommitmentResponse response(UserRecurringCommitmentEntity c, AppUserEntity user) {
        LocalDate today = LocalDate.now(clock.withZone(java.time.ZoneId.of(user.getTimezone())));
        List<OccurrenceResponse> thisMonth = RecurringCommitmentSchedule.dated(c) ? datedOccurrences(user, c, YearMonth.from(today), today) : List.of(occurrenceResponse(user, c, YearMonth.from(today), today));
        OccurrenceResponse current = thisMonth.stream().filter(item -> "DUE".equals(item.status())).findFirst()
                .or(() -> thisMonth.stream().filter(item -> "UPCOMING".equals(item.status())).findFirst())
                .orElse(thisMonth.isEmpty() ? null : thisMonth.getLast());
        return new CommitmentResponse(c.getId(), c.getLabel(), c.getAmountMode().name(), c.getPlanningAmount(), c.getDueDay(), c.getEffectiveMonth().toString().substring(0, 7), c.getStatus().name(), c.getCategory(), c.getSubcategory(), c.getLinkedTransactions().stream().map(FinancialTransactionEntity::getId).toList(), c.getRecurrenceUnit().name(), c.getRecurrenceInterval(), c.isFlexibleSchedule(), c.getNextExpectedDate(), current, thisMonth);
    }
    private List<OccurrenceResponse> datedOccurrences(AppUserEntity user, UserRecurringCommitmentEntity commitment, YearMonth month, LocalDate today) {
        var recorded = occurrences.findByCommitmentIdAndScheduledMonthBetweenOrderByScheduledMonthAsc(commitment.getId(), month.atDay(1), month.atEndOfMonth());
        return java.util.stream.Stream.concat(RecurringCommitmentSchedule.dates(commitment, month, java.time.ZoneId.of(user.getTimezone())).stream(),
                recorded.stream().map(RecurringCommitmentOccurrenceEntity::getScheduledMonth)).distinct().sorted().map(date -> {
            var saved = occurrences.findByCommitmentIdAndScheduledMonth(commitment.getId(), date).orElse(null);
            String status = saved != null ? saved.getStatus().name() : today.isBefore(date) ? "UPCOMING" : "DUE";
            return new OccurrenceResponse(month.toString(), date, status, saved == null ? null : saved.getCompletedAt(),
                    saved == null ? null : saved.getActualAmount(), saved == null ? null : saved.getExtraAmount(),
                    saved == null ? List.of() : extras.findByOccurrenceIdOrderByCreatedAtAsc(saved.getId()).stream()
                            .map(extra -> new ExtraResponse(extra.getAmount(), extra.getReason())).toList(), saved == null ? null : saved.getSavingsUsed());
        }).toList();
    }
    private OccurrenceResponse occurrenceResponse(AppUserEntity user, UserRecurringCommitmentEntity c, YearMonth month, LocalDate today) {
        LocalDate dueDate = c.getNextExpectedDate() != null && YearMonth.from(c.getNextExpectedDate()).equals(month) ? c.getNextExpectedDate() : null;
        var saved = occurrences.findByCommitmentIdAndScheduledMonth(c.getId(), month.atDay(1)).orElse(null);
        String status = saved != null && saved.getStatus() == RecurringCommitmentOccurrenceStatus.COMPLETED ? "COMPLETED"
                : saved != null && saved.getStatus() == RecurringCommitmentOccurrenceStatus.SKIPPED ? "SKIPPED"
                : dueDate != null && !today.isBefore(dueDate) && !createdAfterDueDate(user, c, month, dueDate) ? "DUE" : "UPCOMING";
        return new OccurrenceResponse(month.toString(), dueDate, status, saved == null ? null : saved.getCompletedAt(), saved == null ? null : saved.getActualAmount(), saved == null ? null : saved.getExtraAmount(),
                saved == null ? List.of() : extras.findByOccurrenceIdOrderByCreatedAtAsc(saved.getId()).stream().map(extra -> new ExtraResponse(extra.getAmount(), extra.getReason())).toList(), saved == null ? null : saved.getSavingsUsed());
    }
    private boolean createdAfterDueDate(AppUserEntity user, UserRecurringCommitmentEntity commitment, YearMonth scheduled, LocalDate dueDate) {
        java.time.ZoneId userZone = java.time.ZoneId.of(user.getTimezone());
        return YearMonth.from(commitment.getCreatedAt().atZone(userZone)).equals(scheduled)
                && commitment.getCreatedAt().atZone(userZone).toLocalDate().isAfter(dueDate);
    }
    public record CommitmentRequest(String label, String amountMode, BigDecimal planningAmount, Integer dueDay, String effectiveMonth, String status, String category, String subcategory, Long sourceTransactionId, List<Long> transactionIds, String recurrenceUnit, Integer recurrenceInterval, Boolean flexibleSchedule, LocalDate nextExpectedDate) { }
    public record CompletionRequest(BigDecimal actualAmount, LocalDate completedAt, LocalDate nextExpectedDate, BigDecimal savingsUsed, LocalDate occurrenceDate) { }
    public record ExtraRequest(BigDecimal amount, String reason) { }
    public record ExtraResponse(BigDecimal amount, String reason) { }
    public record CommitmentResponse(Long id, String label, String amountMode, BigDecimal planningAmount, Integer dueDay, String effectiveMonth, String status, String category, String subcategory, List<Long> transactionIds, String recurrenceUnit, Integer recurrenceInterval, boolean flexibleSchedule, LocalDate nextExpectedDate, OccurrenceResponse currentOccurrence, List<OccurrenceResponse> currentOccurrences) {
        public CommitmentResponse(Long id, String label, String amountMode, BigDecimal planningAmount, Integer dueDay, String effectiveMonth, String status, String category, String subcategory, List<Long> transactionIds) { this(id, label, amountMode, planningAmount, dueDay, effectiveMonth, status, category, subcategory, transactionIds, "MONTH", 1, false, null, null, List.of()); }
    }
    public record CommitmentListResponse(List<CommitmentResponse> items) { }
    public record CommitmentHistoryResponse(Long id, String label, BigDecimal planningAmount, List<OccurrenceResponse> history) { }
    public record CandidateResponse(Long transactionId, BigDecimal amount, LocalDate transactionDate, String category, String subcategory, List<CommitmentResponse> choices) { }
    public record CommitmentReviewResponse(List<CandidateResponse> items) { }
    public record ResolveRequest(Long commitmentId) { }
    public record OccurrenceResponse(String month, LocalDate dueDate, String status, LocalDate completedAt, BigDecimal actualAmount, BigDecimal extraAmount, List<ExtraResponse> extras, BigDecimal savingsUsed) { }
}
