package com.apps.deen_sa.service;

import com.apps.deen_sa.exception.WebApiException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Yahoo Finance implementation of the replaceable stock market-data adapter. */
@Service
public class YahooFinanceStockMarketDataAdapter implements StockMarketDataAdapter {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private final RestTemplate http;

    public YahooFinanceStockMarketDataAdapter(RestTemplate http) { this.http = http; }

    @Override
    public List<StockSearchResult> search(String query) {
        if (query == null || query.trim().length() < 2) {
            throw new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_STOCK_SEARCH", "q must contain at least 2 characters");
        }
        try {
            YahooSearchResponse response = http.exchange(
                    "https://query1.finance.yahoo.com/v1/finance/search?q={query}&quotesCount=5",
                    HttpMethod.GET, new HttpEntity<>(headers()), YahooSearchResponse.class, query.trim()).getBody();
            if (response == null || response.quotes() == null) return List.of();
            return Arrays.stream(response.quotes())
                    .filter(q -> q.symbol() != null && q.quoteType() != null && q.quoteType().equalsIgnoreCase("EQUITY"))
                    .map(q -> new StockSearchResult(q.symbol(), q.longname() != null ? q.longname() : q.shortname(), q.exchange()))
                    .toList();
        } catch (RuntimeException failure) {
            throw new WebApiException(HttpStatus.BAD_GATEWAY, "STOCK_LOOKUP_FAILED", "Unable to search stock symbols");
        }
    }

    @Override
    public Optional<BigDecimal> latestPrice(String symbol) {
        try {
            YahooChartResponse response = http.exchange(
                    "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}", HttpMethod.GET,
                    new HttpEntity<>(headers()), YahooChartResponse.class, symbol).getBody();
            if (response == null || response.chart() == null || response.chart().result() == null || response.chart().result().length == 0) return Optional.empty();
            BigDecimal price = response.chart().result()[0].meta() == null ? null : response.chart().result()[0].meta().regularMarketPrice();
            return Optional.ofNullable(price);
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    private HttpHeaders headers() { HttpHeaders headers = new HttpHeaders(); headers.set(HttpHeaders.USER_AGENT, USER_AGENT); return headers; }
    private record YahooSearchResponse(YahooQuote[] quotes) { }
    private record YahooQuote(String symbol, String shortname, String longname, String exchange, String quoteType) { }
    private record YahooChartResponse(YahooChart chart) { }
    private record YahooChart(YahooChartResult[] result) { }
    private record YahooChartResult(YahooMeta meta) { }
    private record YahooMeta(BigDecimal regularMarketPrice) { }
}
