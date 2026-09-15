package com.apps.deen_sa.integration;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.repository.AppUserRepository;
import com.apps.deen_sa.service.StockMarketDataAdapter;
import com.apps.deen_sa.service.WebAuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FIN-017 acceptance coverage for add-and-view stock holdings. */
@SpringBootTest(properties = {"openai.api-key=", "app.aggregation.scheduling-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestContainerInitializer.class)
class StockIntegrationIT {
    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository users;
    @MockBean private WebAuthenticationService authentication;
    @MockBean private StockMarketDataAdapter marketData;

    @Test
    void it_stock_001_addsAndViewsAStockUsingTheMarketDataAdapter() throws Exception {
        AppUserEntity user = new AppUserEntity();
        user.setChannel("WHATSAPP"); user.setExternalUserId("stock-owner"); user.setCreatedAt(Instant.now());
        user = users.saveAndFlush(user);
        var session = new jakarta.servlet.http.Cookie("WEB_SESSION", "stock-session");
        when(authentication.authenticate("stock-session")).thenReturn(user);
        when(marketData.search("itc")).thenReturn(List.of(new StockMarketDataAdapter.StockSearchResult("ITC.NS", "ITC Limited", "NSI", new BigDecimal("425.50"))));
        when(marketData.latestPrice("ITC.NS")).thenReturn(Optional.of(new BigDecimal("425.50")));

        mockMvc.perform(get("/api/web/stocks/search").cookie(session).param("q", "itc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].symbol").value("ITC.NS"))
                .andExpect(jsonPath("$[0].latestPrice").value(425.5));
        mockMvc.perform(post("/api/web/stocks").cookie(session).contentType(MediaType.APPLICATION_JSON).content("""
                {"symbol":"ITC.NS","name":"ITC Limited","exchange":"NSI","quantity":10,"totalInvestedAmount":4000}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.currentValue").value(4255)).andExpect(jsonPath("$.profitOrLoss").value(255));
        mockMvc.perform(get("/api/web/stocks").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stocks.length()").value(1))
                .andExpect(jsonPath("$.stocks[0].latestPrice").value(425.5));
        mockMvc.perform(post("/api/web/stocks").cookie(session).contentType(MediaType.APPLICATION_JSON).content("""
                {"symbol":"ITC.NS","name":"ITC Limited","quantity":10,"totalInvestedAmount":4000}
                """))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STOCK_EXISTS"));
    }

    @Test
    void it_stock_002_keepsRecordedHoldingWhenPriceIsUnavailable() throws Exception {
        AppUserEntity user = new AppUserEntity();
        user.setChannel("WHATSAPP"); user.setExternalUserId("stock-price-unavailable"); user.setCreatedAt(Instant.now());
        user = users.saveAndFlush(user);
        var session = new jakarta.servlet.http.Cookie("WEB_SESSION", "stock-price-session");
        when(authentication.authenticate("stock-price-session")).thenReturn(user);
        when(marketData.latestPrice(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/web/stocks").cookie(session).contentType(MediaType.APPLICATION_JSON).content("""
                {"symbol":"RELIANCE.NS","name":"Reliance Industries","quantity":2,"totalInvestedAmount":3000}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.invested").value(3000))
                .andExpect(jsonPath("$.currentValue").value(nullValue()));
    }
}
