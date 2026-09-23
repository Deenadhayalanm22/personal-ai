package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.InvestmentAssetType;
import com.apps.deen_sa.domain.InvestmentCalculationSource;
import com.apps.deen_sa.domain.InvestmentTransactionKind;
import com.apps.deen_sa.domain.InvestmentTransactionStatus;
import com.apps.deen_sa.domain.InvestmentSipStatus;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** FIN-EPIC-005 — Listed-stock add and view flow. See docs/jira/personal-expense/FIN-EPIC-005-planning.md. */
@Service
public class WebStockService {
    private static final String YAHOO_FINANCE = "YAHOO_FINANCE";
    private final UserInvestmentRepository investments;
    private final InvestmentTransactionRepository transactions;
    private final StockMarketDataAdapter marketData;
    private final Clock clock;
    private final MonthlyFinancialSnapshotService snapshots;

    public WebStockService(UserInvestmentRepository investments, InvestmentTransactionRepository transactions,
                           StockMarketDataAdapter marketData, Clock clock) {
        this(investments, transactions, marketData, clock, null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public WebStockService(UserInvestmentRepository investments, InvestmentTransactionRepository transactions,
                           StockMarketDataAdapter marketData, Clock clock, MonthlyFinancialSnapshotService snapshots) {
        this.investments = investments; this.transactions = transactions; this.marketData = marketData; this.clock = clock; this.snapshots = snapshots;
    }

    @Transactional
    public StockResponse create(AppUserEntity user, StockCreateRequest request) {
        if (request == null) throw invalid("Stock details are required");
        BigDecimal quantity = positive(request.quantity(), "quantity", 6);
        BigDecimal invested = positive(request.totalInvestedAmount(), "totalInvestedAmount", 2);
        UserInvestmentEntity investment = new UserInvestmentEntity();
        investment.setUser(user); investment.setAssetType(InvestmentAssetType.STOCK); investment.setProvider(YAHOO_FINANCE);
        investment.setExternalInstrumentId(required(request.symbol(), "symbol").toUpperCase());
        investment.setDisplayNameSnapshot(required(request.name(), "name"));
        investment.setExchange(optional(request.exchange()));
        try { investment = investments.save(investment); }
        catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
            throw new WebApiException(HttpStatus.CONFLICT, "STOCK_EXISTS", "This stock is already tracked");
        }
        InvestmentTransactionEntity opening = new InvestmentTransactionEntity();
        opening.setInvestment(investment); opening.setTransactionKind(InvestmentTransactionKind.OPENING_BALANCE);
        opening.setStatus(InvestmentTransactionStatus.CONFIRMED); opening.setTransactionDate(LocalDate.now(clock));
        opening.setAmount(invested); opening.setUnits(quantity);
        opening.setUnitPrice(invested.divide(quantity, 6, RoundingMode.HALF_UP));
        opening.setCalculationSource(InvestmentCalculationSource.USER_ENTERED);
        transactions.save(opening);
        return summary(investment);
    }

    // Listing stocks materializes and promotes the current monthly-plan occurrence.
    @Transactional
    public StockListResponse list(AppUserEntity user) {
        List<UserInvestmentEntity> stocks = investments.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(i -> i.getAssetType() == InvestmentAssetType.STOCK).toList();
        stocks.forEach(this::ensureCurrentMonthlyPlanOccurrence);
        return new StockListResponse(stocks.stream().map(this::summary).toList());
    }

    @Transactional(readOnly = true)
    public StockDetailResponse detail(AppUserEntity user, Long investmentId) {
        UserInvestmentEntity investment = owned(user, investmentId);
        StockResponse summary = summary(investment);
        List<StockHistoryEntry> history = transactions.findByInvestmentIdOrderByCreatedAtAsc(investmentId).stream()
                .filter(tx -> tx.getStatus() == InvestmentTransactionStatus.CONFIRMED || tx.getStatus() == InvestmentTransactionStatus.SKIPPED)
                .map(tx -> new StockHistoryEntry(tx.getId(), tx.getTransactionKind().name(), tx.getStatus().name(), tx.getScheduledMonth(), tx.getTransactionDate(), tx.getAmount(), tx.getUnitPrice(), tx.getUnits()))
                .toList();
        return new StockDetailResponse(summary, history);
    }

    @Transactional
    public StockResponse createMonthlyPlan(AppUserEntity user, Long investmentId, MonthlyPlanRequest request) {
        UserInvestmentEntity investment = owned(user, investmentId);
        if (request == null) throw invalid("Monthly plan details are required");
        investment.setSipAmount(positive(request.amount(), "amount", 2));
        if (request.day() == null || request.day() < 1 || request.day() > 28) throw invalid("day must be between 1 and 28");
        if (request.startMonth() == null) throw invalid("startMonth is required");
        investment.setSipDay(request.day()); investment.setSipStartMonth(request.startMonth().atDay(1));
        investment.setSipStatus(InvestmentSipStatus.ACTIVE); investments.save(investment);
        ensureCurrentMonthlyPlanOccurrence(investment);
        if (snapshots != null) snapshots.refreshCurrent(user);
        return summary(investment);
    }

    @Transactional
    public MonthlyPlanOccurrenceResponse confirmMonthlyPlan(AppUserEntity user, Long investmentId, YearMonth month, MonthlyPlanConfirmation request) {
        UserInvestmentEntity investment = owned(user, investmentId);
        if (month == null || request == null) throw invalid("Monthly plan confirmation details are required");
        InvestmentTransactionEntity tx = transactions.findByInvestmentIdAndTransactionKindAndScheduledMonth(investmentId, InvestmentTransactionKind.SIP, month.atDay(1))
                .orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "STOCK_MONTHLY_PLAN_OCCURRENCE_NOT_FOUND", "Monthly plan occurrence not found"));
        if (tx.getStatus() != InvestmentTransactionStatus.DUE) throw invalid("This monthly plan occurrence can no longer be confirmed");
        BigDecimal amount = positive(request.amount(), "amount", 2);
        if (request.transactionDate() != null && request.transactionDate().isBefore(tx.getScheduledMonth().withDayOfMonth(investment.getSipDay()))) throw invalid("Payment date must be on or after the due date");
        BigDecimal price = positive(request.executionPrice(), "executionPrice", 6);
        BigDecimal units = request.units() == null ? amount.divide(price, 6, RoundingMode.HALF_UP) : positive(request.units(), "units", 6);
        tx.setStatus(InvestmentTransactionStatus.CONFIRMED); tx.setAmount(amount); tx.setTransactionDate(request.transactionDate() == null ? LocalDate.now(clock) : request.transactionDate());
        tx.setUnitPrice(price); tx.setUnits(units); tx.setCalculationSource(InvestmentCalculationSource.USER_ENTERED); transactions.save(tx);
        if (snapshots != null) snapshots.refreshCurrent(user);
        return MonthlyPlanOccurrenceResponse.from(tx);
    }

    @Transactional
    public MonthlyPlanOccurrenceResponse skipMonthlyPlan(AppUserEntity user, Long investmentId, YearMonth month) {
        UserInvestmentEntity investment = owned(user, investmentId);
        InvestmentTransactionEntity tx = transactions.findByInvestmentIdAndTransactionKindAndScheduledMonth(investmentId, InvestmentTransactionKind.SIP, month.atDay(1))
                .orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "STOCK_MONTHLY_PLAN_OCCURRENCE_NOT_FOUND", "Monthly plan occurrence not found"));
        if (tx.getStatus() != InvestmentTransactionStatus.DUE) throw invalid("Only a due monthly plan can be skipped");
        tx.setStatus(InvestmentTransactionStatus.SKIPPED); tx.setTransactionDate(LocalDate.now(clock)); transactions.save(tx);
        if (snapshots != null) snapshots.refreshCurrent(user);
        return MonthlyPlanOccurrenceResponse.from(tx);
    }

    @Transactional
    public StockDetailResponse addPurchase(AppUserEntity user, Long investmentId, StockPurchaseRequest request) {
        UserInvestmentEntity investment = owned(user, investmentId);
        if (request == null || request.transactionDate() == null) throw invalid("Purchase date is required");
        BigDecimal amount = positive(request.amount(), "amount", 2);
        BigDecimal units = positive(request.units(), "units", 6);
        InvestmentTransactionEntity tx = new InvestmentTransactionEntity();
        tx.setInvestment(investment); tx.setTransactionKind(InvestmentTransactionKind.BUY);
        tx.setStatus(InvestmentTransactionStatus.CONFIRMED); tx.setTransactionDate(request.transactionDate());
        tx.setAmount(amount); tx.setUnits(units); tx.setUnitPrice(amount.divide(units, 6, RoundingMode.HALF_UP));
        tx.setCalculationSource(InvestmentCalculationSource.USER_ENTERED); transactions.save(tx);
        return detail(user, investmentId);
    }

    @Transactional
    public StockDetailResponse updateTransaction(AppUserEntity user, Long investmentId, Long transactionId, StockPurchaseRequest request) {
        owned(user, investmentId);
        if (request == null || request.transactionDate() == null) throw invalid("Transaction date is required");
        InvestmentTransactionEntity tx = transactions.findByInvestmentIdOrderByCreatedAtAsc(investmentId).stream()
                .filter(item -> item.getId().equals(transactionId) && item.getStatus() == InvestmentTransactionStatus.CONFIRMED)
                .findFirst().orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "STOCK_TRANSACTION_NOT_FOUND", "Stock transaction not found"));
        BigDecimal amount = positive(request.amount(), "amount", 2);
        BigDecimal units = positive(request.units(), "units", 6);
        tx.setAmount(amount); tx.setUnits(units); tx.setTransactionDate(request.transactionDate());
        tx.setUnitPrice(amount.divide(units, 6, RoundingMode.HALF_UP)); transactions.save(tx);
        return detail(user, investmentId);
    }

    @Transactional
    public void delete(AppUserEntity user, Long investmentId) {
        UserInvestmentEntity investment = investments.findByIdAndUserId(investmentId, user.getId())
                .filter(candidate -> candidate.getAssetType() == InvestmentAssetType.STOCK)
                .orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "STOCK_NOT_FOUND", "Stock holding not found"));
        transactions.deleteByInvestmentId(investment.getId());
        investments.delete(investment);
        if (snapshots != null) snapshots.refreshCurrent(user);
    }

    private StockResponse summary(UserInvestmentEntity investment) {
        List<InvestmentTransactionEntity> rows = transactions.findByInvestmentIdOrderByCreatedAtAsc(investment.getId());
        BigDecimal quantity = rows.stream().filter(t -> t.getStatus() == InvestmentTransactionStatus.CONFIRMED)
                .map(InvestmentTransactionEntity::getUnits).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal invested = rows.stream().filter(t -> t.getStatus() == InvestmentTransactionStatus.CONFIRMED)
                .map(InvestmentTransactionEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal price = marketData.latestPrice(investment.getExternalInstrumentId()).orElse(null);
        BigDecimal currentValue = price == null ? null : quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pnl = currentValue == null ? null : currentValue.subtract(invested).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pnlPercent = pnl == null || invested.signum() == 0 ? null : pnl.multiply(BigDecimal.valueOf(100)).divide(invested, 2, RoundingMode.HALF_UP);
        return new StockResponse(investment.getId(), investment.getExternalInstrumentId(), investment.getDisplayNameSnapshot(), investment.getExchange(), quantity, invested, price, currentValue, pnl, pnlPercent,
                monthlyPlan(investment), currentMonthlyPlanOccurrence(investment));
    }
    private UserInvestmentEntity owned(AppUserEntity user, Long investmentId) { return investments.findByIdAndUserId(investmentId, user.getId()).filter(candidate -> candidate.getAssetType() == InvestmentAssetType.STOCK).orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "STOCK_NOT_FOUND", "Stock holding not found")); }
    private void ensureCurrentMonthlyPlanOccurrence(UserInvestmentEntity investment) {
        if (investment.getSipStatus() != InvestmentSipStatus.ACTIVE || investment.getSipStartMonth() == null) return;
        YearMonth current = YearMonth.now(clock); if (current.isBefore(YearMonth.from(investment.getSipStartMonth()))) return;
        var existing = transactions.findByInvestmentIdAndTransactionKindAndScheduledMonth(investment.getId(), InvestmentTransactionKind.SIP, current.atDay(1));
        InvestmentTransactionStatus status = LocalDate.now(clock).getDayOfMonth() < investment.getSipDay() ? InvestmentTransactionStatus.SCHEDULED : InvestmentTransactionStatus.DUE;
        if (existing.isEmpty()) { InvestmentTransactionEntity tx = new InvestmentTransactionEntity(); tx.setInvestment(investment); tx.setTransactionKind(InvestmentTransactionKind.SIP); tx.setScheduledMonth(current.atDay(1)); tx.setStatus(status); tx.setAmount(investment.getSipAmount()); transactions.save(tx); }
        else if (existing.get().getStatus() == InvestmentTransactionStatus.SCHEDULED && status == InvestmentTransactionStatus.DUE) { existing.get().setStatus(status); transactions.save(existing.get()); }
    }
    private MonthlyPlanResponse monthlyPlan(UserInvestmentEntity investment) { return investment.getSipStatus() != InvestmentSipStatus.ACTIVE ? null : new MonthlyPlanResponse(investment.getSipAmount(), investment.getSipDay(), YearMonth.from(investment.getSipStartMonth()), investment.getSipStatus().name()); }
    private MonthlyPlanOccurrenceResponse currentMonthlyPlanOccurrence(UserInvestmentEntity investment) { return transactions.findByInvestmentIdAndTransactionKindAndScheduledMonth(investment.getId(), InvestmentTransactionKind.SIP, YearMonth.now(clock).atDay(1)).map(MonthlyPlanOccurrenceResponse::from).orElse(null); }
    private String required(String value, String field) { String text = optional(value); if (text == null) throw invalid(field + " is required"); return text; }
    private String optional(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private BigDecimal positive(BigDecimal value, String field, int scale) { if (value == null || value.signum() <= 0) throw invalid(field + " must be greater than zero"); return value.setScale(scale, RoundingMode.HALF_UP); }
    private WebApiException invalid(String message) { return new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_STOCK", message); }

    public record StockCreateRequest(String symbol, String name, String exchange, BigDecimal quantity, BigDecimal totalInvestedAmount) { }
    public record StockResponse(Long id, String symbol, String name, String exchange, BigDecimal quantity, BigDecimal invested,
                                BigDecimal latestPrice, BigDecimal currentValue, BigDecimal profitOrLoss, BigDecimal profitOrLossPercent,
                                MonthlyPlanResponse activeMonthlyPlan, MonthlyPlanOccurrenceResponse currentMonthlyPlan) { }
    public record StockListResponse(List<StockResponse> stocks) { }
    public record StockDetailResponse(StockResponse stock, List<StockHistoryEntry> history) { }
    public record StockHistoryEntry(Long id, String kind, String status, LocalDate scheduledMonth, LocalDate transactionDate, BigDecimal amount, BigDecimal executionPrice, BigDecimal units) { }
    public record StockPurchaseRequest(BigDecimal amount, LocalDate transactionDate, BigDecimal units) { }
    public record MonthlyPlanRequest(BigDecimal amount, Integer day, YearMonth startMonth) { }
    public record MonthlyPlanConfirmation(BigDecimal amount, LocalDate transactionDate, BigDecimal executionPrice, BigDecimal units) { }
    public record MonthlyPlanResponse(BigDecimal amount, Integer day, YearMonth startMonth, String status) { }
    public record MonthlyPlanOccurrenceResponse(YearMonth month, String status, BigDecimal amount, LocalDate transactionDate, BigDecimal executionPrice, BigDecimal units) { static MonthlyPlanOccurrenceResponse from(InvestmentTransactionEntity tx) { return new MonthlyPlanOccurrenceResponse(YearMonth.from(tx.getScheduledMonth()), tx.getStatus().name(), tx.getAmount(), tx.getTransactionDate(), tx.getUnitPrice(), tx.getUnits()); } }
}
