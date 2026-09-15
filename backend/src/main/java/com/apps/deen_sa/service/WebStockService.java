package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.InvestmentAssetType;
import com.apps.deen_sa.domain.InvestmentCalculationSource;
import com.apps.deen_sa.domain.InvestmentTransactionKind;
import com.apps.deen_sa.domain.InvestmentTransactionStatus;
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
import java.util.List;

/** FIN-EPIC-005 — Listed-stock add and view flow. See docs/jira/personal-expense/FIN-EPIC-005-planning.md. */
@Service
public class WebStockService {
    private static final String YAHOO_FINANCE = "YAHOO_FINANCE";
    private final UserInvestmentRepository investments;
    private final InvestmentTransactionRepository transactions;
    private final StockMarketDataAdapter marketData;
    private final Clock clock;

    public WebStockService(UserInvestmentRepository investments, InvestmentTransactionRepository transactions,
                           StockMarketDataAdapter marketData, Clock clock) {
        this.investments = investments; this.transactions = transactions; this.marketData = marketData; this.clock = clock;
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

    @Transactional(readOnly = true)
    public StockListResponse list(AppUserEntity user) {
        return new StockListResponse(investments.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(i -> i.getAssetType() == InvestmentAssetType.STOCK).map(this::summary).toList());
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
        return new StockResponse(investment.getId(), investment.getExternalInstrumentId(), investment.getDisplayNameSnapshot(), investment.getExchange(), quantity, invested, price, currentValue, pnl, pnlPercent);
    }
    private String required(String value, String field) { String text = optional(value); if (text == null) throw invalid(field + " is required"); return text; }
    private String optional(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private BigDecimal positive(BigDecimal value, String field, int scale) { if (value == null || value.signum() <= 0) throw invalid(field + " must be greater than zero"); return value.setScale(scale, RoundingMode.HALF_UP); }
    private WebApiException invalid(String message) { return new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_STOCK", message); }

    public record StockCreateRequest(String symbol, String name, String exchange, BigDecimal quantity, BigDecimal totalInvestedAmount) { }
    public record StockResponse(Long id, String symbol, String name, String exchange, BigDecimal quantity, BigDecimal invested,
                                BigDecimal latestPrice, BigDecimal currentValue, BigDecimal profitOrLoss, BigDecimal profitOrLossPercent) { }
    public record StockListResponse(List<StockResponse> stocks) { }
}
