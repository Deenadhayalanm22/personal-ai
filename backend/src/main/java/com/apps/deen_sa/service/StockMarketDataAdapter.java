package com.apps.deen_sa.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Market-data boundary for listed equities. Portfolio services depend on this
 * interface so Yahoo Finance can be replaced without changing persisted stock
 * holdings or HTTP endpoints.
 */
public interface StockMarketDataAdapter {
    List<StockSearchResult> search(String query);
    Optional<BigDecimal> latestPrice(String symbol);

    record StockSearchResult(String symbol, String name, String exchange, BigDecimal latestPrice) { }
}
