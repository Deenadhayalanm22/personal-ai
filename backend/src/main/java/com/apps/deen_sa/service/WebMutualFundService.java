package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.*;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.InvestmentTransactionEntity;
import com.apps.deen_sa.entity.UserInvestmentEntity;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.InvestmentTransactionRepository;
import com.apps.deen_sa.repository.UserInvestmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.List;

/** FIN-EPIC-005 — Loans and mutual-fund planning. See docs/jira/personal-expense/FIN-EPIC-005-planning.md. */
@Service
public class WebMutualFundService {
    private static final String MFAPI = "MFAPI";
    private final UserInvestmentRepository investments;
    private final InvestmentTransactionRepository transactions;
    private final MfApiService mfApi;
    private final Clock clock;
    private final MonthlyFinancialSnapshotService snapshots;

    public WebMutualFundService(UserInvestmentRepository investments, InvestmentTransactionRepository transactions,
                                 MfApiService mfApi, Clock clock) {
        this(investments, transactions, mfApi, clock, null);
    }
    @Autowired
    public WebMutualFundService(UserInvestmentRepository investments, InvestmentTransactionRepository transactions,
                                 MfApiService mfApi, Clock clock, MonthlyFinancialSnapshotService snapshots) {
        this.investments = investments;
        this.transactions = transactions;
        this.mfApi = mfApi;
        this.clock = clock;
        this.snapshots = snapshots;
    }

    @Transactional
    public MutualFundResponse create(AppUserEntity user, MutualFundCreateRequest request) {
        if (request == null) throw invalid("Mutual fund details are required");
        if (request.existingHolding() == null) throw invalid("An opening holding is required");
        UserInvestmentEntity investment = new UserInvestmentEntity();
        investment.setUser(user);
        investment.setAssetType(InvestmentAssetType.MUTUAL_FUND);
        investment.setProvider(MFAPI);
        investment.setExternalInstrumentId(requiredText(request.schemeCode(), "schemeCode"));
        investment.setDisplayNameSnapshot(requiredText(request.schemeName(), "schemeName"));
        investment.setIsinSnapshot(optionalText(request.isin()));
        applySipIfPresent(investment, request.monthlySipAmount(), request.sipDay(), request.startMonth());
        try {
            investment = investments.save(investment);
        } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
            throw new WebApiException(HttpStatus.CONFLICT, "INVESTMENT_EXISTS", "This mutual fund is already tracked");
        }
        createOpeningBalance(investment, request.existingHolding());
        createInitialSipOccurrenceIfNeeded(investment);
        MutualFundResponse response = response(investment);
        if (snapshots != null) snapshots.refreshCurrent(user); // FIN-018: refresh the monthly financial snapshot atomically with its investment source.
        return response;
    }

    /** Adds a monthly SIP to an already tracked lump-sum-only fund. */
    @Transactional
    public MutualFundResponse createSip(AppUserEntity user, Long investmentId, SipPlanRequest request) {
        UserInvestmentEntity investment = owned(user, investmentId);
        if (request == null) throw invalid("SIP details are required");
        if (investment.getSipStatus() == InvestmentSipStatus.ACTIVE) throw invalid("A SIP is already configured for this mutual fund");
        applySip(investment, request.amount(), request.day(), request.startMonth());
        investments.save(investment);
        createInitialSipOccurrenceIfNeeded(investment);
        if (snapshots != null) snapshots.refreshCurrent(user);
        return response(investment);
    }

    @Transactional
    public MutualFundResponse updatePlan(AppUserEntity user, Long id, PlanUpdateRequest request) {
        UserInvestmentEntity investment = owned(user, id);
        if (request == null || (request.frequency() == null && request.currentNav() == null)) throw invalid("Provide frequency or current NAV");
        if (request.currentNav() != null) investment.setCurrentNavOverride(positive(request.currentNav(), "currentNav", 6));
        if (request.frequency() != null) {
            if (!request.frequency().equals("MONTHLY") && !request.frequency().equals("QUARTERLY")) throw invalid("frequency must be MONTHLY or QUARTERLY");
            if (investment.getSipStatus() != InvestmentSipStatus.ACTIVE) throw invalid("Set up a SIP before changing frequency");
            YearMonth effective = request.effectiveMonth();
            if (effective == null || !effective.isAfter(YearMonth.now(clock))) throw invalid("effectiveMonth must be a future month");
            investment.setSipFrequency(request.frequency());
            investment.setSipAnchorMonth(effective.minusMonths(1).atDay(1));
        }
        investments.save(investment);
        if (snapshots != null) snapshots.refreshCurrent(user);
        return summary(investment);
    }

    @Transactional
    public TransactionResponse skipSip(AppUserEntity user, Long id, YearMonth month) {
        UserInvestmentEntity investment = owned(user, id);
        InvestmentTransactionEntity tx = transactions.findByInvestmentIdAndTransactionKindAndScheduledMonth(id, InvestmentTransactionKind.SIP, month.atDay(1))
                .orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "SIP_OCCURRENCE_NOT_FOUND", "SIP occurrence not found"));
        if (tx.getStatus() != InvestmentTransactionStatus.DUE || LocalDate.now(clock).isBefore(month.atDay(investment.getSipDay()))) throw invalid("Only a due SIP can be skipped");
        tx.setStatus(InvestmentTransactionStatus.SKIPPED);
        tx.setTransactionDate(LocalDate.now(clock));
        TransactionResponse response = TransactionResponse.from(transactions.save(tx));
        if (snapshots != null) snapshots.refreshCurrent(user);
        return response;
    }

    @Transactional
    public TransactionResponse addLumpSum(AppUserEntity user, Long investmentId, LumpSumRequest request) {
        UserInvestmentEntity investment = owned(user, investmentId);
        if (request == null) throw invalid("Lump sum details are required");
        InvestmentTransactionEntity tx = confirmed(investment, InvestmentTransactionKind.LUMPSUM,
                request.amount(), request.transactionDate(), request.nav(), request.units(), request.calculationSource());
        TransactionResponse response = TransactionResponse.from(transactions.save(tx));
        if (snapshots != null) snapshots.refreshCurrent(user); // FIN-018: refresh the monthly financial snapshot atomically with its investment source.
        return response;
    }

    @Transactional
    public TransactionResponse confirmSip(AppUserEntity user, Long investmentId, YearMonth month, LumpSumRequest request) {
        UserInvestmentEntity investment = owned(user, investmentId);
        if (month == null || request == null) throw invalid("SIP month and confirmation details are required");
        InvestmentTransactionEntity tx = transactions.findByInvestmentIdAndTransactionKindAndScheduledMonth(
                        investmentId, InvestmentTransactionKind.SIP, month.atDay(1))
                .orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "SIP_OCCURRENCE_NOT_FOUND", "SIP occurrence not found"));
        if (tx.getStatus() != InvestmentTransactionStatus.DUE || LocalDate.now(clock).isBefore(month.atDay(investment.getSipDay()))) {
            throw invalid("This SIP occurrence can no longer be confirmed");
        }
        InvestmentTransactionEntity confirmed = confirmed(investment, InvestmentTransactionKind.SIP, request.amount(),
                request.transactionDate(), request.nav(), request.units(), request.calculationSource());
        tx.setStatus(confirmed.getStatus()); tx.setAmount(confirmed.getAmount()); tx.setTransactionDate(confirmed.getTransactionDate());
        tx.setUnitPrice(confirmed.getUnitPrice()); tx.setUnits(confirmed.getUnits()); tx.setCalculationSource(confirmed.getCalculationSource());
        TransactionResponse response = TransactionResponse.from(transactions.save(tx));
        if (snapshots != null) snapshots.refreshCurrent(user); // FIN-018: confirmation changes current planning evidence.
        return response;
    }

    /** A confirmation is an allocation fact, and may be corrected from the Mutual Funds section. */
    @Transactional
    public TransactionResponse updateSip(AppUserEntity user, Long investmentId, YearMonth month, LumpSumRequest request) {
        UserInvestmentEntity investment = owned(user, investmentId);
        if (month == null || request == null) throw invalid("SIP month and allocation details are required");
        InvestmentTransactionEntity tx = transactions.findByInvestmentIdAndTransactionKindAndScheduledMonth(
                        investmentId, InvestmentTransactionKind.SIP, month.atDay(1))
                .orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "SIP_OCCURRENCE_NOT_FOUND", "SIP occurrence not found"));
        if (tx.getStatus() != InvestmentTransactionStatus.CONFIRMED) throw invalid("Confirm this SIP before editing its allocation");
        InvestmentTransactionEntity corrected = confirmed(investment, InvestmentTransactionKind.SIP, request.amount(),
                request.transactionDate(), request.nav(), request.units(), request.calculationSource());
        tx.setAmount(corrected.getAmount()); tx.setTransactionDate(corrected.getTransactionDate());
        tx.setUnitPrice(corrected.getUnitPrice()); tx.setUnits(corrected.getUnits()); tx.setCalculationSource(corrected.getCalculationSource());
        tx.setUpdatedAt(Instant.now());
        TransactionResponse response = TransactionResponse.from(transactions.save(tx));
        if (snapshots != null) snapshots.refreshCurrent(user);
        return response;
    }

    /** Correct a recorded opening holding, lump sum, or confirmed SIP without changing the SIP plan. */
    @Transactional
    public TransactionResponse updateTransaction(AppUserEntity user, Long investmentId, Long transactionId, LumpSumRequest request) {
        UserInvestmentEntity investment = owned(user, investmentId);
        if (transactionId == null || request == null) throw invalid("Transaction and corrected details are required");
        InvestmentTransactionEntity tx = transactions.findById(transactionId)
                .filter(candidate -> candidate.getInvestment().getId().equals(investment.getId()))
                .orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "INVESTMENT_TRANSACTION_NOT_FOUND", "Investment transaction not found"));
        if (tx.getStatus() != InvestmentTransactionStatus.CONFIRMED) throw invalid("Only confirmed investments can be corrected");
        InvestmentTransactionEntity corrected = confirmed(investment, tx.getTransactionKind(), request.amount(),
                request.transactionDate(), request.nav(), request.units(), request.calculationSource());
        tx.setAmount(corrected.getAmount()); tx.setTransactionDate(corrected.getTransactionDate());
        tx.setUnitPrice(corrected.getUnitPrice()); tx.setUnits(corrected.getUnits()); tx.setCalculationSource(corrected.getCalculationSource());
        tx.setUpdatedAt(Instant.now());
        TransactionResponse response = TransactionResponse.from(transactions.save(tx));
        if (snapshots != null) snapshots.refreshCurrent(user);
        return response;
    }

    @Transactional
    public void createCurrentSipOccurrences() {
        investments.findByAssetTypeAndSipStatus(InvestmentAssetType.MUTUAL_FUND, InvestmentSipStatus.ACTIVE).forEach(investment -> {
            ensureCurrentSipOccurrence(investment);
        });
    }

    @Transactional
    public MutualFundListResponse list(AppUserEntity user) {
        List<UserInvestmentEntity> funds = investments.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(i -> i.getAssetType() == InvestmentAssetType.MUTUAL_FUND).toList();
        funds.forEach(this::ensureCurrentSipOccurrence);
        return new MutualFundListResponse(funds.stream().map(this::summary).toList());
    }

    @Transactional(readOnly = true)
    public MutualFundDetailResponse detail(AppUserEntity user, Long investmentId) {
        UserInvestmentEntity investment = owned(user, investmentId);
        Holding holding = holding(investment);
        BigDecimal latestNav = investment.getCurrentNavOverride() != null ? investment.getCurrentNavOverride() : mfApi.latestNav(investment.getExternalInstrumentId()).orElse(null);
        BigDecimal currentValue = latestNav == null ? null : holding.units().multiply(latestNav).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitOrLoss = currentValue == null ? null : currentValue.subtract(holding.invested()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitOrLossPercent = profitOrLoss == null || holding.invested().signum() == 0 ? null
                : profitOrLoss.multiply(BigDecimal.valueOf(100)).divide(holding.invested(), 2, RoundingMode.HALF_UP);
        return new MutualFundDetailResponse(investment.getId(), investment.getDisplayNameSnapshot(), holding.invested(),
                currentValue, profitOrLoss, profitOrLossPercent, holding.averagePurchaseCost(), latestNav, holding.units(),
                transactions.findByInvestmentIdOrderByCreatedAtAsc(investment.getId()).stream()
                        .filter(tx -> tx.getStatus() == InvestmentTransactionStatus.CONFIRMED || tx.getStatus() == InvestmentTransactionStatus.SKIPPED)
                        .map(InvestmentHistoryItem::from).toList());
    }

    @Transactional
    public void delete(AppUserEntity user, Long investmentId) {
        UserInvestmentEntity investment = owned(user, investmentId);
        transactions.deleteByInvestmentId(investment.getId());
        investments.delete(investment);
        if (snapshots != null) snapshots.refreshCurrent(user);
    }

    private void createOpeningBalance(UserInvestmentEntity investment, ExistingHoldingRequest opening) {
        BigDecimal amount = amount(opening.totalInvestedAmount(), "totalInvestedAmount");
        BigDecimal units = positive(opening.currentUnits(), "currentUnits", 6);
        InvestmentTransactionEntity tx = confirmed(investment, InvestmentTransactionKind.OPENING_BALANCE, amount,
                LocalDate.now(clock), amount.divide(units, 6, RoundingMode.HALF_UP), units, InvestmentCalculationSource.USER_ENTERED);
        transactions.save(tx);
    }

    private void createInitialSipOccurrenceIfNeeded(UserInvestmentEntity investment) {
        if (investment.getSipStatus() == null) return;
        YearMonth current = YearMonth.now(clock);
        YearMonth start = YearMonth.from(investment.getSipStartMonth());
        createSipOccurrence(investment, start.isAfter(current) ? start : current,
                start.isAfter(current) ? InvestmentTransactionStatus.SCHEDULED : statusFor(investment, current));
    }

    /** A SIP is upcoming until its configured day, even when the monthly source is already planned. */
    private void ensureCurrentSipOccurrence(UserInvestmentEntity investment) {
        if (investment.getSipStatus() != InvestmentSipStatus.ACTIVE) return;
        YearMonth current = YearMonth.now(clock);
        if (!hasSipIn(investment, current)) return;
        var existing = transactions.findByInvestmentIdAndTransactionKindAndScheduledMonth(investment.getId(), InvestmentTransactionKind.SIP, current.atDay(1));
        if (existing.isEmpty()) {
            createSipOccurrence(investment, current, statusFor(investment, current));
        } else if (existing.get().getStatus() == InvestmentTransactionStatus.SCHEDULED && statusFor(investment, current) == InvestmentTransactionStatus.DUE) {
            existing.get().setStatus(InvestmentTransactionStatus.DUE);
            transactions.save(existing.get());
        }
    }

    private boolean hasSipIn(UserInvestmentEntity investment, YearMonth month) {
        YearMonth start = YearMonth.from(investment.getSipStartMonth());
        if (month.isBefore(start)) return false;
        YearMonth anchor = investment.getSipAnchorMonth() == null ? start : YearMonth.from(investment.getSipAnchorMonth());
        if (month.isBefore(anchor)) return true;
        return !"QUARTERLY".equals(investment.getSipFrequency()) || java.time.temporal.ChronoUnit.MONTHS.between(anchor, month) % 3 == 0;
    }

    private InvestmentTransactionStatus statusFor(UserInvestmentEntity investment, YearMonth month) {
        LocalDate today = LocalDate.now(clock);
        return YearMonth.from(today).equals(month) && today.getDayOfMonth() < investment.getSipDay()
                ? InvestmentTransactionStatus.SCHEDULED : InvestmentTransactionStatus.DUE;
    }

    private void createSipOccurrence(UserInvestmentEntity investment, YearMonth month, InvestmentTransactionStatus status) {
        if (transactions.findByInvestmentIdAndTransactionKindAndScheduledMonth(investment.getId(), InvestmentTransactionKind.SIP, month.atDay(1)).isPresent()) return;
        InvestmentTransactionEntity occurrence = new InvestmentTransactionEntity();
        occurrence.setInvestment(investment);
        occurrence.setTransactionKind(InvestmentTransactionKind.SIP);
        occurrence.setScheduledMonth(month.atDay(1));
        occurrence.setStatus(status);
        occurrence.setAmount(investment.getSipAmount());
        transactions.save(occurrence);
    }

    private void applySip(UserInvestmentEntity investment, BigDecimal amount, Integer day, YearMonth startMonth) {
        boolean any = amount != null || day != null || startMonth != null;
        if (!any) return;
        investment.setSipAmount(amount(amount, "monthlySipAmount"));
        if (day == null || day < 1 || day > 28) throw invalid("sipDay must be between 1 and 28");
        if (startMonth == null) throw invalid("startMonth is required when a SIP is configured");
        investment.setSipDay(day);
        // A plan added during a month is an upcoming commitment, not a retroactive allocation.
        // Its first confirmable SIP is therefore the following month.
        YearMonth requested = startMonth;
        YearMonth current = YearMonth.now(clock);
        investment.setSipStartMonth((!requested.isAfter(current) ? current.plusMonths(1) : requested).atDay(1));
        investment.setSipStatus(InvestmentSipStatus.ACTIVE);
        investment.setSipFrequency("MONTHLY");
        investment.setSipAnchorMonth(investment.getSipStartMonth());
    }

    private void applySipIfPresent(UserInvestmentEntity investment, BigDecimal amount, Integer day, YearMonth startMonth) {
        if (amount == null && day == null && startMonth == null) return;
        applySip(investment, amount, day, startMonth);
    }

    private InvestmentTransactionEntity confirmed(UserInvestmentEntity investment, InvestmentTransactionKind kind,
                                                   BigDecimal rawAmount, LocalDate date, BigDecimal rawNav,
                                                   BigDecimal rawUnits, InvestmentCalculationSource source) {
        BigDecimal amount = amount(rawAmount, "amount");
        if (date == null) throw invalid("transactionDate is required");
        if (source == null) throw invalid("calculationSource is required");
        BigDecimal nav = rawNav == null ? null : positive(rawNav, "nav", 6);
        BigDecimal units = rawUnits == null ? null : positive(rawUnits, "units", 6);
        if (units == null && nav == null) throw invalid("Provide nav or units");
        if (units == null) units = amount.divide(nav, 6, RoundingMode.HALF_UP);
        if (nav == null) nav = amount.divide(units, 6, RoundingMode.HALF_UP);
        InvestmentTransactionEntity tx = new InvestmentTransactionEntity();
        tx.setInvestment(investment); tx.setTransactionKind(kind); tx.setStatus(InvestmentTransactionStatus.CONFIRMED);
        tx.setAmount(amount); tx.setTransactionDate(date); tx.setUnitPrice(nav); tx.setUnits(units); tx.setCalculationSource(source);
        return tx;
    }

    private MutualFundResponse response(UserInvestmentEntity investment) {
        return summary(investment);
    }

    private MutualFundResponse summary(UserInvestmentEntity investment) {
        Holding holding = holding(investment);
        BigDecimal latestNav = investment.getCurrentNavOverride() != null ? investment.getCurrentNavOverride() : mfApi.latestNav(investment.getExternalInstrumentId()).orElse(null);
        BigDecimal currentValue = latestNav == null ? null : holding.units().multiply(latestNav).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitOrLoss = currentValue == null ? null : currentValue.subtract(holding.invested()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitOrLossPercent = profitOrLoss == null || holding.invested().signum() == 0 ? null
                : profitOrLoss.multiply(BigDecimal.valueOf(100)).divide(holding.invested(), 2, RoundingMode.HALF_UP);
        ActiveSip activeSip = investment.getSipStatus() == InvestmentSipStatus.ACTIVE
                ? new ActiveSip(investment.getSipAmount(), investment.getSipDay(), YearMonth.from(investment.getSipStartMonth()), investment.getSipFrequency(), nextSipDate(investment)) : null;
        SipOccurrence currentSip = activeSip == null ? null : transactions.findByInvestmentIdAndTransactionKindAndScheduledMonth(
                        investment.getId(), InvestmentTransactionKind.SIP, YearMonth.now(clock).atDay(1))
                .map(SipOccurrence::from).orElse(null);
        return new MutualFundResponse(investment.getId(), investment.getExternalInstrumentId(), investment.getDisplayNameSnapshot(),
                holding.invested(), currentValue, profitOrLoss, profitOrLossPercent, latestNav, activeSip, currentSip);
    }

    private LocalDate nextSipDate(UserInvestmentEntity investment) {
        YearMonth now = YearMonth.now(clock);
        for (int offset = 0; offset < 36; offset++) {
            YearMonth month = now.plusMonths(offset);
            if (!hasSipIn(investment, month)) continue;
            if (offset == 0) {
                var current = transactions.findByInvestmentIdAndTransactionKindAndScheduledMonth(investment.getId(), InvestmentTransactionKind.SIP, month.atDay(1));
                if (current.isPresent() && (current.get().getStatus() == InvestmentTransactionStatus.CONFIRMED || current.get().getStatus() == InvestmentTransactionStatus.SKIPPED)) continue;
            }
            return month.atDay(investment.getSipDay());
        }
        return null;
    }

    private Holding holding(UserInvestmentEntity investment) {
        List<InvestmentTransactionEntity> rows = transactions.findByInvestmentIdOrderByCreatedAtAsc(investment.getId());
        BigDecimal units = rows.stream().filter(r -> r.getStatus() == InvestmentTransactionStatus.CONFIRMED)
                .map(InvestmentTransactionEntity::getUnits).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal invested = rows.stream().filter(r -> r.getStatus() == InvestmentTransactionStatus.CONFIRMED)
                .map(InvestmentTransactionEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Holding(units, invested, units.signum() == 0 ? null : invested.divide(units, 6, RoundingMode.HALF_UP));
    }

    private UserInvestmentEntity owned(AppUserEntity user, Long id) {
        return investments.findByIdAndUserId(id, user.getId()).filter(i -> i.getAssetType() == InvestmentAssetType.MUTUAL_FUND)
                .orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "INVESTMENT_NOT_FOUND", "Mutual fund investment not found"));
    }
    private String requiredText(String value, String field) { String v = optionalText(value); if (v == null) throw invalid(field + " is required"); return v; }
    private String optionalText(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private BigDecimal amount(BigDecimal value, String field) { return positive(value, field, 2); }
    private BigDecimal positive(BigDecimal value, String field, int scale) { if (value == null || value.signum() <= 0) throw invalid(field + " must be greater than zero"); return value.setScale(scale, RoundingMode.HALF_UP); }
    private WebApiException invalid(String message) { return new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_MUTUAL_FUND", message); }

    public record MutualFundCreateRequest(String schemeCode, String schemeName, String isin, BigDecimal monthlySipAmount,
                                          Integer sipDay, YearMonth startMonth,
                                          ExistingHoldingRequest existingHolding) { }
    public record SipPlanRequest(BigDecimal amount, Integer day, YearMonth startMonth) { }
    public record ExistingHoldingRequest(BigDecimal currentUnits, BigDecimal totalInvestedAmount) { }
    public record LumpSumRequest(BigDecimal amount, LocalDate transactionDate, BigDecimal nav, BigDecimal units,
                                 InvestmentCalculationSource calculationSource) { }
    public record TransactionResponse(Long id, InvestmentTransactionKind transactionKind, InvestmentTransactionStatus status,
                                      YearMonth scheduledMonth, LocalDate transactionDate, BigDecimal amount,
                                      BigDecimal unitPrice, BigDecimal units, InvestmentCalculationSource calculationSource) {
        static TransactionResponse from(InvestmentTransactionEntity t) { return new TransactionResponse(t.getId(), t.getTransactionKind(), t.getStatus(),
                t.getScheduledMonth() == null ? null : YearMonth.from(t.getScheduledMonth()), t.getTransactionDate(), t.getAmount(), t.getUnitPrice(), t.getUnits(), t.getCalculationSource()); }
    }
    public record MutualFundResponse(Long id, String schemeCode, String schemeName, BigDecimal invested,
                                     BigDecimal currentValue, BigDecimal profitOrLoss, BigDecimal profitOrLossPercent,
                                     BigDecimal latestNav, ActiveSip activeSip, SipOccurrence currentSip) { }
    public record ActiveSip(BigDecimal amount, Integer day, YearMonth startMonth, String frequency, LocalDate nextDueDate) { }
    public record PlanUpdateRequest(String frequency, YearMonth effectiveMonth, BigDecimal currentNav) { }
    public record SipOccurrence(String scheduledMonth, String status, BigDecimal amount, LocalDate transactionDate,
                                BigDecimal nav, BigDecimal units, String calculationSource) {
        static SipOccurrence from(InvestmentTransactionEntity value) {
            return new SipOccurrence(value.getScheduledMonth().toString().substring(0, 7), value.getStatus().name(),
                    value.getAmount(), value.getTransactionDate(), value.getUnitPrice(), value.getUnits(),
                    value.getCalculationSource() == null ? null : value.getCalculationSource().name());
        }
    }
    public record MutualFundDetailResponse(Long id, String schemeName, BigDecimal invested, BigDecimal currentValue,
                                           BigDecimal profitOrLoss, BigDecimal profitOrLossPercent,
                                           BigDecimal averageNav, BigDecimal currentNav, BigDecimal units,
                                           List<InvestmentHistoryItem> history) { }
    public record InvestmentHistoryItem(Long id, InvestmentTransactionKind kind, YearMonth scheduledMonth, LocalDate transactionDate,
                                        BigDecimal amount, BigDecimal nav, BigDecimal units, InvestmentCalculationSource calculationSource, InvestmentTransactionStatus status, LocalDate dueDate) {
        static InvestmentHistoryItem from(InvestmentTransactionEntity tx) {
            return new InvestmentHistoryItem(tx.getId(), tx.getTransactionKind(),
                    tx.getScheduledMonth() == null ? null : YearMonth.from(tx.getScheduledMonth()),
                    tx.getTransactionDate(), tx.getAmount(), tx.getUnitPrice(), tx.getUnits(), tx.getCalculationSource(), tx.getStatus(),
                    tx.getScheduledMonth() == null ? null : tx.getScheduledMonth().withDayOfMonth(tx.getInvestment().getSipDay()));
        }
    }
    public record MutualFundListResponse(List<MutualFundResponse> mutualFunds) { }
    private record Holding(BigDecimal units, BigDecimal invested, BigDecimal averagePurchaseCost) { }
}
