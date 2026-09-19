package com.apps.deen_sa.integration;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.repository.AppUserRepository;
import com.apps.deen_sa.repository.InvestmentTransactionRepository;
import com.apps.deen_sa.repository.UserInvestmentRepository;
import com.apps.deen_sa.service.WebAuthenticationService;
import com.apps.deen_sa.service.MfApiService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Keeps HTTP and persistence mechanics out of the investment scenario contract. */
final class MutualFundScenarioDriver {
    private final MockMvc mockMvc;
    private final AppUserRepository users;
    private final UserInvestmentRepository investments;
    private final InvestmentTransactionRepository transactions;
    private final WebAuthenticationService authentication;
    private final MfApiService mfApi;
    private AppUserEntity owner;
    private jakarta.servlet.http.Cookie session;
    private Long investmentId;

    MutualFundScenarioDriver(MockMvc mockMvc, AppUserRepository users, UserInvestmentRepository investments,
                             InvestmentTransactionRepository transactions, WebAuthenticationService authentication) {
        this(mockMvc, users, investments, transactions, authentication, null);
    }

    MutualFundScenarioDriver(MockMvc mockMvc, AppUserRepository users, UserInvestmentRepository investments,
                             InvestmentTransactionRepository transactions, WebAuthenticationService authentication, MfApiService mfApi) {
        this.mockMvc = mockMvc; this.users = users; this.investments = investments;
        this.transactions = transactions; this.authentication = authentication; this.mfApi = mfApi;
    }

    void startUser() {
        owner = new AppUserEntity();
        owner.setChannel("WHATSAPP"); owner.setExternalUserId("mutual-fund-owner"); owner.setCreatedAt(Instant.now());
        owner = users.saveAndFlush(owner);
        when(authentication.authenticate("mutual-fund-session")).thenReturn(owner);
        when(mfApi.latestNav(anyString())).thenReturn(Optional.of(new BigDecimal("200.00")));
        session = new jakarta.servlet.http.Cookie("WEB_SESSION", "mutual-fund-session");
    }

    void addFundWithMonthlySipAndOpeningHolding() throws Exception {
        mockMvc.perform(post("/api/web/mutual-funds").cookie(session).contentType(MediaType.APPLICATION_JSON).content("""
                {"schemeCode":"122639","schemeName":"Parag Parikh Flexi Cap Fund - Direct Plan - Growth",
                 "isin":"INF879O01027","monthlySipAmount":25000,"sipDay":5,"startMonth":"2020-09",
                 "existingHolding":{"currentUnits":128.456,"totalInvestedAmount":10000}}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schemeCode").value("122639"))
                .andExpect(jsonPath("$.schemeName").value("Parag Parikh Flexi Cap Fund - Direct Plan - Growth"));
        investmentId = investments.findByUserIdOrderByCreatedAtDesc(owner.getId()).getFirst().getId();
    }

    void assertOpeningHoldingAndDueSip() throws Exception {
        assertThat(transactions.findByInvestmentIdOrderByCreatedAtAsc(investmentId)).hasSize(2);
        portfolio().andExpect(jsonPath("$.mutualFunds[0].invested").value(10000))
                .andExpect(jsonPath("$.mutualFunds[0].currentValue").value(25691.2))
                .andExpect(jsonPath("$.mutualFunds[0].profitOrLoss").value(15691.2))
                .andExpect(jsonPath("$.mutualFunds[0].activeSip.amount").value(25000));
    }

    void recordNavEstimatedLumpSum() throws Exception {
        mockMvc.perform(post("/api/web/mutual-funds/{id}/lump-sums", investmentId).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"amount":50000,"transactionDate":"2026-09-07","nav":87.25,
                                 "calculationSource":"NAV_ESTIMATED"}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.transactionKind").value("LUMPSUM"))
                .andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.units").value(573.065903));
    }

    void recordStatementVerifiedLumpSum() throws Exception {
        mockMvc.perform(post("/api/web/mutual-funds/{id}/lump-sums", investmentId).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"amount":10000,"transactionDate":"2026-09-10","units":112.99435,
                                 "calculationSource":"STATEMENT_VERIFIED"}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.unitPrice").value(88.5))
                .andExpect(jsonPath("$.calculationSource").value("STATEMENT_VERIFIED"));
    }

    void assertCombinedHoldingIsAccurate() throws Exception {
        portfolio().andExpect(jsonPath("$.mutualFunds.length()").value(1))
                .andExpect(jsonPath("$.mutualFunds[0].invested").value(70000))
                .andExpect(jsonPath("$.mutualFunds[0].currentValue").value(162903.25))
                .andExpect(jsonPath("$.mutualFunds[0].profitOrLoss").value(92903.25));
    }

    void assertFundCardShowsReturnsAndHoldingMetrics() throws Exception {
        mockMvc.perform(get("/api/web/mutual-funds/{id}", investmentId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemeName").value("Parag Parikh Flexi Cap Fund - Direct Plan - Growth"))
                .andExpect(jsonPath("$.invested").value(70000))
                .andExpect(jsonPath("$.currentValue").value(162903.25))
                .andExpect(jsonPath("$.profitOrLoss").value(92903.25))
                .andExpect(jsonPath("$.profitOrLossPercent").value(132.72))
                .andExpect(jsonPath("$.averageNav").value(85.940581))
                .andExpect(jsonPath("$.currentNav").value(200))
                .andExpect(jsonPath("$.units").value(814.516253));
    }

    void deleteFund() throws Exception {
        mockMvc.perform(delete("/api/web/mutual-funds/{id}", investmentId).cookie(session))
                .andExpect(status().isNoContent());
    }

    void assertFundAndTransactionsAreDeleted() throws Exception {
        assertThat(investments.findById(investmentId)).isEmpty();
        assertThat(transactions.findByInvestmentIdOrderByCreatedAtAsc(investmentId)).isEmpty();
        portfolio().andExpect(jsonPath("$.mutualFunds").isEmpty());
    }

    private org.springframework.test.web.servlet.ResultActions portfolio() throws Exception {
        return mockMvc.perform(get("/api/web/mutual-funds").cookie(session)).andExpect(status().isOk());
    }
}
