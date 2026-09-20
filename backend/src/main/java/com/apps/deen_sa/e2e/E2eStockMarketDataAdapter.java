package com.apps.deen_sa.e2e;

import com.apps.deen_sa.service.StockMarketDataAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Stable listed-equity fixture data for the browser-only e2e profile. */
@Service
@Profile("e2e")
public class E2eStockMarketDataAdapter implements StockMarketDataAdapter {
    private static final List<StockSearchResult> STOCKS = List.of(
            new StockSearchResult("ITC.NS", "ITC Limited", "NSI", new BigDecimal("425.50")),
            new StockSearchResult("RELIANCE.NS", "Reliance Industries", "NSI", new BigDecimal("1500.00")));

    @Override
    public List<StockSearchResult> search(String query) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return STOCKS.stream().filter(stock -> stock.symbol().toLowerCase(Locale.ROOT).contains(normalized)
                || stock.name().toLowerCase(Locale.ROOT).contains(normalized)).toList();
    }

    @Override
    public Optional<BigDecimal> latestPrice(String symbol) {
        return STOCKS.stream().filter(stock -> stock.symbol().equalsIgnoreCase(symbol))
                .map(StockSearchResult::latestPrice).findFirst();
    }
}
