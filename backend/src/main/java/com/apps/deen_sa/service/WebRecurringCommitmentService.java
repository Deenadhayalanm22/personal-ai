package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.CommitmentAmountMode;
import com.apps.deen_sa.domain.RecurringCommitmentStatus;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.FinancialTransactionEntity;
import com.apps.deen_sa.entity.UserRecurringCommitmentEntity;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.FinancialTransactionRepository;
import com.apps.deen_sa.repository.UserRecurringCommitmentRepository;
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
    public WebRecurringCommitmentService(UserRecurringCommitmentRepository commitments, FinancialTransactionRepository transactions,
                                         MonthlyFinancialSnapshotService snapshots) {
        this.commitments = commitments; this.transactions = transactions; this.snapshots = snapshots;
    }
    @Transactional(readOnly = true)
    public CommitmentListResponse list(AppUserEntity user) { return new CommitmentListResponse(commitments.findAllOwned(user.getId()).stream().map(this::response).toList()); }
    @Transactional
    public CommitmentResponse create(AppUserEntity user, CommitmentRequest request) {
        UserRecurringCommitmentEntity value = new UserRecurringCommitmentEntity(); value.setUser(user); apply(user, value, request, true);
        commitments.saveAndFlush(value); snapshots.refreshCurrent(user); return response(value);
    }
    @Transactional
    public CommitmentResponse update(AppUserEntity user, Long id, CommitmentRequest request) {
        UserRecurringCommitmentEntity value = owned(user, id); apply(user, value, request, false); commitments.saveAndFlush(value); snapshots.refreshCurrent(user); return response(value);
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
        if (r.effectiveMonth() != null) value.setEffectiveMonth(parseMonth(r.effectiveMonth()).atDay(1)); else if (creating) value.setEffectiveMonth(YearMonth.now().atDay(1));
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
    private CommitmentResponse response(UserRecurringCommitmentEntity c) { return new CommitmentResponse(c.getId(), c.getLabel(), c.getAmountMode().name(), c.getPlanningAmount(), c.getDueDay(), c.getEffectiveMonth().toString().substring(0, 7), c.getStatus().name(), c.getCategory(), c.getSubcategory(), c.getLinkedTransactions().stream().map(FinancialTransactionEntity::getId).toList()); }
    public record CommitmentRequest(String label, String amountMode, BigDecimal planningAmount, Integer dueDay, String effectiveMonth, String status, String category, String subcategory, Long sourceTransactionId, List<Long> transactionIds) { }
    public record CommitmentResponse(Long id, String label, String amountMode, BigDecimal planningAmount, Integer dueDay, String effectiveMonth, String status, String category, String subcategory, List<Long> transactionIds) { }
    public record CommitmentListResponse(List<CommitmentResponse> items) { }
    public record CandidateResponse(Long transactionId, BigDecimal amount, LocalDate transactionDate, String category, String subcategory, List<CommitmentResponse> choices) { }
    public record CommitmentReviewResponse(List<CandidateResponse> items) { }
    public record ResolveRequest(Long commitmentId) { }
}
