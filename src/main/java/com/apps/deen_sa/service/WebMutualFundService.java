package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.*;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.InvestmentTransactionEntity;
import com.apps.deen_sa.entity.UserInvestmentEntity;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.InvestmentTransactionRepository;
import com.apps.deen_sa.repository.UserInvestmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.List;

@Service
public class WebMutualFundService {
    private static final String MFAPI = "MFAPI";
    private final UserInvestmentRepository investments;
    private final InvestmentTransactionRepository transactions;
    private final MfApiService mfApi;
    private final Clock clock;

    public WebMutualFundService(UserInvestmentRepository investments, InvestmentTransactionRepository transactions,
                                 MfApiService mfApi, Clock clock) {
        this.investments = investments;
        this.transactions = transactions;
        this.mfApi = mfApi;
        this.clock = clock;
    }

    @Transactional
    public MutualFundResponse create(AppUserEntity user, MutualFundCreateRequest request) {
        if (request == null) throw invalid("Mutual fund details are required");
        UserInvestmentEntity investment = new UserInvestmentEntity();
        investment.setUser(user);
        investment.setAssetType(InvestmentAssetType.MUTUAL_FUND);
        investment.setProvider(MFAPI);
        investment.setExternalInstrumentId(requiredText(request.schemeCode(), "schemeCode"));
        investment.setDisplayNameSnapshot(requiredText(request.schemeName(), "schemeName"));
        investment.setIsinSnapshot(optionalText(request.isin()));
        applySip(investment, request.monthlySipAmount(), request.sipDay(), request.startMonth());
        try {
            investment = investments.save(investment);
        } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
            throw new WebApiException(HttpStatus.CONFLICT, "INVESTMENT_EXISTS", "This mutual fund is already tracked");
        }
        if (request.existingHolding() != null) createOpeningBalance(investment, request.existingHolding());
        createInitialSipOccurrenceIfNeeded(investment);
        return response(investment);
    }

    @Transactional
    public TransactionResponse addLumpSum(AppUserEntity user, Long investmentId, LumpSumRequest request) {
        UserInvestmentEntity investment = owned(user, investmentId);
        if (request == null) throw invalid("Lump sum details are required");
        InvestmentTransactionEntity tx = confirmed(investment, InvestmentTransactionKind.LUMPSUM,
                request.amount(), request.transactionDate(), request.nav(), request.units(), request.calculationSource());
        return TransactionResponse.from(transactions.save(tx));
    }

    @Transactional
    public TransactionResponse confirmSip(AppUserEntity user, Long investmentId, YearMonth month, LumpSumRequest request) {
        UserInvestmentEntity investment = owned(user, investmentId);
        if (month == null || request == null) throw invalid("SIP month and confirmation details are required");
        InvestmentTransactionEntity tx = transactions.findByInvestmentIdAndTransactionKindAndScheduledMonth(
                        investmentId, InvestmentTransactionKind.SIP, month.atDay(1))
                .orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "SIP_OCCURRENCE_NOT_FOUND", "SIP occurrence not found"));
        if (tx.getStatus() == InvestmentTransactionStatus.CONFIRMED || tx.getStatus() == InvestmentTransactionStatus.SKIPPED) {
            throw invalid("This SIP occurrence can no longer be confirmed");
        }
        InvestmentTransactionEntity confirmed = confirmed(investment, InvestmentTransactionKind.SIP, request.amount(),
                request.transactionDate(), request.nav(), request.units(), request.calculationSource());
        tx.setStatus(confirmed.getStatus()); tx.setAmount(confirmed.getAmount()); tx.setTransactionDate(confirmed.getTransactionDate());
        tx.setUnitPrice(confirmed.getUnitPrice()); tx.setUnits(confirmed.getUnits()); tx.setCalculationSource(confirmed.getCalculationSource());
        return TransactionResponse.from(transactions.save(tx));
    }

    @Transactional
    public void createCurrentSipOccurrences() {
        YearMonth current = YearMonth.now(clock);
        investments.findByAssetTypeAndSipStatus(InvestmentAssetType.MUTUAL_FUND, InvestmentSipStatus.ACTIVE).forEach(investment -> {
            if (!YearMonth.from(investment.getSipStartMonth()).isAfter(current)) createSipOccurrence(investment, current, InvestmentTransactionStatus.DUE);
        });
    }

    @Transactional(readOnly = true)
    public MutualFundListResponse list(AppUserEntity user) {
        return new MutualFundListResponse(investments.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(i -> i.getAssetType() == InvestmentAssetType.MUTUAL_FUND).map(this::summary).toList());
    }

    @Transactional(readOnly = true)
    public MutualFundDetailResponse detail(AppUserEntity user, Long investmentId) {
        UserInvestmentEntity investment = owned(user, investmentId);
        Holding holding = holding(investment);
        BigDecimal latestNav = mfApi.latestNav(investment.getExternalInstrumentId()).orElse(null);
        BigDecimal currentValue = latestNav == null ? null : holding.units().multiply(latestNav).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitOrLoss = currentValue == null ? null : currentValue.subtract(holding.invested()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitOrLossPercent = profitOrLoss == null || holding.invested().signum() == 0 ? null
                : profitOrLoss.multiply(BigDecimal.valueOf(100)).divide(holding.invested(), 2, RoundingMode.HALF_UP);
        return new MutualFundDetailResponse(investment.getId(), investment.getDisplayNameSnapshot(), holding.invested(),
                currentValue, profitOrLoss, profitOrLossPercent, holding.averagePurchaseCost(), latestNav, holding.units());
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
                start.isAfter(current) ? InvestmentTransactionStatus.SCHEDULED : InvestmentTransactionStatus.DUE);
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
        investment.setSipStartMonth(startMonth.atDay(1));
        investment.setSipStatus(InvestmentSipStatus.ACTIVE);
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
        BigDecimal latestNav = mfApi.latestNav(investment.getExternalInstrumentId()).orElse(null);
        BigDecimal currentValue = latestNav == null ? null : holding.units().multiply(latestNav).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitOrLoss = currentValue == null ? null : currentValue.subtract(holding.invested()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitOrLossPercent = profitOrLoss == null || holding.invested().signum() == 0 ? null
                : profitOrLoss.multiply(BigDecimal.valueOf(100)).divide(holding.invested(), 2, RoundingMode.HALF_UP);
        ActiveSip activeSip = investment.getSipStatus() == InvestmentSipStatus.ACTIVE
                ? new ActiveSip(investment.getSipAmount(), investment.getSipDay(), YearMonth.from(investment.getSipStartMonth())) : null;
        return new MutualFundResponse(investment.getId(), investment.getExternalInstrumentId(), investment.getDisplayNameSnapshot(),
                holding.invested(), currentValue, profitOrLoss, profitOrLossPercent, latestNav, activeSip);
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
                                     BigDecimal latestNav, ActiveSip activeSip) { }
    public record ActiveSip(BigDecimal amount, Integer day, YearMonth startMonth) { }
    public record MutualFundDetailResponse(Long id, String schemeName, BigDecimal invested, BigDecimal currentValue,
                                           BigDecimal profitOrLoss, BigDecimal profitOrLossPercent,
                                           BigDecimal averageNav, BigDecimal currentNav, BigDecimal units) { }
    public record MutualFundListResponse(List<MutualFundResponse> mutualFunds) { }
    private record Holding(BigDecimal units, BigDecimal invested, BigDecimal averagePurchaseCost) { }
}
