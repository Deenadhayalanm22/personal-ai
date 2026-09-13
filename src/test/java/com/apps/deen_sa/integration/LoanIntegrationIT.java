package com.apps.deen_sa.integration;

import com.apps.deen_sa.domain.LoanType;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.UserLoanEntity;
import com.apps.deen_sa.repository.AppUserRepository;
import com.apps.deen_sa.repository.UserLoanRepository;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"openai.api-key=", "app.aggregation.scheduling-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestContainerInitializer.class)
class LoanIntegrationIT {
    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository users;
    @Autowired private UserLoanRepository loans;
    @MockBean private WebAuthenticationService authentication;

    @Test
    void it_loan_001_createsListsAndEditsOnlyTheAuthenticatedUsersLoan() throws Exception {
        AppUserEntity owner = createUser("loan-owner");
        AppUserEntity otherUser = createUser("loan-other-user");
        when(authentication.authenticate("loan-owner-session")).thenReturn(owner);
        when(authentication.authenticate("loan-other-session")).thenReturn(otherUser);
        var ownerCookie = new jakarta.servlet.http.Cookie("WEB_SESSION", "loan-owner-session");
        var otherCookie = new jakarta.servlet.http.Cookie("WEB_SESSION", "loan-other-session");

        mockMvc.perform(post("/api/web/loans")
                        .cookie(ownerCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loanName": "HDFC Home Loan",
                                  "loanType": "HOME",
                                  "lenderName": "HDFC Bank",
                                  "originalPrincipal": 5000000,
                                  "monthlyEmiAmount": 45000,
                                  "totalTenureMonths": 240,
                                  "firstEmiDueDate": "2025-04-05",
                                  "notes": "Primary residence"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loanName").value("HDFC Home Loan"))
                .andExpect(jsonPath("$.loanType").value("HOME"))
                .andExpect(jsonPath("$.monthlyEmiAmount").value(45000))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        UserLoanEntity loan = loans.findByUserIdOrderByCreatedAtDesc(owner.getId()).getFirst();
        assertThat(loan.getOriginalPrincipal()).isEqualByComparingTo("5000000.00");
        assertThat(loans.findByUserIdOrderByCreatedAtDesc(otherUser.getId())).isEmpty();

        mockMvc.perform(get("/api/web/loans").cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loans.length()").value(1))
                .andExpect(jsonPath("$.loans[0].id").value(loan.getId()))
                .andExpect(jsonPath("$.loans[0].firstEmiDueDate").value("2025-04-05"));

        mockMvc.perform(patch("/api/web/loans/{id}", loan.getId())
                        .cookie(ownerCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "monthlyEmiAmount": 47500.25,
                                  "totalTenureMonths": 228,
                                  "notes": "EMI revised"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyEmiAmount").value(47500.25))
                .andExpect(jsonPath("$.totalTenureMonths").value(228))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.notes").value("EMI revised"));

        UserLoanEntity edited = loans.findById(loan.getId()).orElseThrow();
        assertThat(edited.getMonthlyEmiAmount()).isEqualByComparingTo("47500.25");
        assertThat(edited.getTotalTenureMonths()).isEqualTo(228);
        assertThat(edited.getStatus()).isEqualTo(com.apps.deen_sa.domain.LoanStatus.ACTIVE);
        assertThat(edited.getLoanType()).isEqualTo(LoanType.HOME);

        mockMvc.perform(patch("/api/web/loans/{id}", loan.getId())
                        .cookie(otherCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"loanName\":\"Not allowed\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOAN_NOT_FOUND"));
    }

    private AppUserEntity createUser(String externalUserId) {
        AppUserEntity user = new AppUserEntity();
        user.setChannel("WHATSAPP");
        user.setExternalUserId(externalUserId);
        user.setCreatedAt(Instant.now());
        return users.saveAndFlush(user);
    }
}
