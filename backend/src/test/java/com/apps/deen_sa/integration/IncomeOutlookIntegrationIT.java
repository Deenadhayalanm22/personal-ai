package com.apps.deen_sa.integration;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.repository.AppUserRepository;
import com.apps.deen_sa.repository.UserIncomeProfileRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

/** FIN-019 acceptance coverage for the voluntary salary-only income outlook. */
@SpringBootTest(properties = {"openai.api-key=", "app.aggregation.scheduling-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestContainerInitializer.class)
class IncomeOutlookIntegrationIT {
    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository users;
    @Autowired private UserIncomeProfileRepository profiles;
    @MockBean private WebAuthenticationService authentication;

    @Test
    void it_income_outlook_001_savesARangeWithoutAnExactSalaryAndKeepsItPrivateToTheUser() throws Exception {
        AppUserEntity owner = createUser("income-owner");
        AppUserEntity other = createUser("income-other");
        var ownerCookie = session("income-owner-session", owner);
        var otherCookie = session("income-other-session", other);

        mockMvc.perform(put("/api/web/income-outlook/salary").cookie(ownerCookie)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"salaryVisibility":"RANGE","salaryRange":"FROM_50000_TO_100000","salaryFrequency":"MONTHLY"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salaryVisibility").value("RANGE"))
                .andExpect(jsonPath("$.salaryRange").value("FROM_50000_TO_100000"))
                .andExpect(jsonPath("$.exactMonthlySalary").doesNotExist());

        assertThat(profiles.findById(owner.getId())).isPresent()
                .get().extracting(profile -> profile.getExactMonthlySalary()).isNull();
        mockMvc.perform(get("/api/web/income-outlook").cookie(ownerCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.salary.salaryRange").value("FROM_50000_TO_100000"));
        mockMvc.perform(get("/api/web/income-outlook").cookie(otherCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.salary").doesNotExist());
    }

    @Test
    void it_income_outlook_002_allowsExactSalaryOnlyWhenTheUserChoosesItAndRejectsAnInvalidRequest() throws Exception {
        AppUserEntity user = createUser("income-exact");
        var cookie = session("income-exact-session", user);

        mockMvc.perform(put("/api/web/income-outlook/salary").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"salaryVisibility":"EXACT","exactMonthlySalary":82500.499,"salaryFrequency":"MONTHLY"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.exactMonthlySalary").value(82500.5))
                .andExpect(jsonPath("$.salaryRange").doesNotExist());
        assertThat(profiles.findById(user.getId()).orElseThrow().getExactMonthlySalary()).isEqualByComparingTo("82500.50");

        mockMvc.perform(put("/api/web/income-outlook/salary").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"salaryVisibility":"EXACT","exactMonthlySalary":0,"salaryFrequency":"MONTHLY"}
                                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INCOME_OUTLOOK"));
    }

    private jakarta.servlet.http.Cookie session(String token, AppUserEntity user) {
        when(authentication.authenticate(token)).thenReturn(user);
        return new jakarta.servlet.http.Cookie("WEB_SESSION", token);
    }
    private AppUserEntity createUser(String externalUserId) {
        AppUserEntity user = new AppUserEntity();
        user.setChannel("WHATSAPP"); user.setExternalUserId(externalUserId); user.setCreatedAt(Instant.now());
        return users.saveAndFlush(user);
    }
}
