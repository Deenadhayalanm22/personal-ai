package com.apps.deen_sa.web;

import com.apps.deen_sa.controller.WebFinanceController;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebFinanceControllerTest {

    @Test
    void exposesReferenceMergeContract() throws Exception {
        WebAuthenticationService authentication = mock(WebAuthenticationService.class);
        WebReferenceMergeService referenceMerges = mock(WebReferenceMergeService.class);
        AppUserEntity user = new AppUserEntity(); user.setId(42L);
        when(authentication.authenticate("session-token")).thenReturn(user);
        var mergeRequest = new WebReferenceMergeService.MergeRequest(
                com.apps.deen_sa.domain.UserReferenceEntityType.ACCOUNT,
                java.util.List.of(101L, 214L), "HDFC Bank");
        when(referenceMerges.merge(user, mergeRequest)).thenReturn(new WebReferenceMergeService.MergeResponse(
                "merge_01J", new WebReferenceMergeService.CanonicalReference(101L, "HDFC Bank",
                        com.apps.deen_sa.domain.UserReferenceEntityType.ACCOUNT,
                        java.util.List.of("HDFC Bank account", "HDFC UPI")),
                java.util.List.of(214L), 15, "COMPLETED"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WebFinanceController(new WebManager(
                authentication, mock(WebLoginRequestService.class), mock(WebExpenseTaxonomyService.class),
                mock(WebUserReferencePreferenceService.class), referenceMerges,
                mock(MonthlyFinancialTransactionService.class), mock(FinancialTransactionListService.class),
                mock(FinancialTransactionCalendarService.class), mock(ExpenseEditOptionsService.class),
                mock(FinancialTransactionEditService.class), mock(PendingActionContextService.class)),
                false, "Lax")).build();
        var cookie = new jakarta.servlet.http.Cookie("WEB_SESSION", "session-token");

        mvc.perform(post("/api/web/reference-preferences/merge").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"entityType":"ACCOUNT","referenceIds":[101,214],"canonicalName":"HDFC Bank"}
                                """).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mergeId").value("merge_01J"))
                .andExpect(jsonPath("$.canonicalReference.name").value("HDFC Bank"))
                .andExpect(jsonPath("$.mergedReferenceIds[0]").value(214))
                .andExpect(jsonPath("$.updatedTransactionCount").value(15));
    }

    @Test
    void authenticatesSessionAndReturnsAllReferenceEntityTypes() throws Exception {
        WebAuthenticationService authentication = mock(WebAuthenticationService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WebFinanceController(new WebManager(
                authentication,
                mock(WebLoginRequestService.class),
                mock(WebExpenseTaxonomyService.class),
                mock(WebUserReferencePreferenceService.class),
                mock(WebReferenceMergeService.class),
                mock(com.apps.deen_sa.service.MonthlyFinancialTransactionService.class),
                mock(com.apps.deen_sa.service.FinancialTransactionListService.class),
                mock(com.apps.deen_sa.service.FinancialTransactionCalendarService.class),
                mock(com.apps.deen_sa.service.ExpenseEditOptionsService.class),
                mock(com.apps.deen_sa.service.FinancialTransactionEditService.class),
                mock(PendingActionContextService.class)),
                false,
                "Lax")).build();

        mvc.perform(get("/api/web/reference-entity-types")
                        .cookie(new jakarta.servlet.http.Cookie(
                                "WEB_SESSION", "session-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityTypes.length()").value(3))
                .andExpect(jsonPath("$.entityTypes[0]").value("MERCHANT"))
                .andExpect(jsonPath("$.entityTypes[1]").value("BENEFICIARY"))
                .andExpect(jsonPath("$.entityTypes[2]").value("ACCOUNT"));

        verify(authentication).authenticate("session-token");
    }

    @Test
    void authenticatesSessionAndCreatesReferencePreference() throws Exception {
        WebAuthenticationService authentication = mock(WebAuthenticationService.class);
        WebUserReferencePreferenceService preferences =
                mock(WebUserReferencePreferenceService.class);
        AppUserEntity user =
                new AppUserEntity();
        user.setId(42L);
        var request = new WebUserReferencePreferenceService.UserReferencePreferenceRequest(
                com.apps.deen_sa.domain.UserReferenceEntityType.MERCHANT,
                "Amazon", "AMZN, Amazon India");
        when(authentication.authenticate("session-token")).thenReturn(user);
        when(preferences.create(user, request)).thenReturn(
                new WebUserReferencePreferenceService.UserReferencePreferenceResponse(
                        7L, com.apps.deen_sa.domain.UserReferenceEntityType.MERCHANT,
                        "Amazon", java.util.List.of(
                                new WebUserReferencePreferenceService.AliasResponse(9L, "AMZN"),
                                new WebUserReferencePreferenceService.AliasResponse(10L, "Amazon India")), 0));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WebFinanceController(new WebManager(
                authentication,
                mock(WebLoginRequestService.class),
                mock(WebExpenseTaxonomyService.class),
                preferences,
                mock(WebReferenceMergeService.class),
                mock(com.apps.deen_sa.service.MonthlyFinancialTransactionService.class),
                mock(com.apps.deen_sa.service.FinancialTransactionListService.class),
                mock(com.apps.deen_sa.service.FinancialTransactionCalendarService.class),
                mock(com.apps.deen_sa.service.ExpenseEditOptionsService.class),
                mock(com.apps.deen_sa.service.FinancialTransactionEditService.class),
                mock(PendingActionContextService.class)),
                false,
                "Lax")).build();

        mvc.perform(post("/api/web/reference-preferences")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entityType": "MERCHANT",
                                  "primaryReference": "Amazon",
                                  "alias": "AMZN, Amazon India"
                                }
                                """)
                        .cookie(new jakarta.servlet.http.Cookie(
                                "WEB_SESSION", "session-token")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceId").value(7))
                .andExpect(jsonPath("$.entityType").value("MERCHANT"))
                .andExpect(jsonPath("$.primaryReference").value("Amazon"))
                .andExpect(jsonPath("$.aliases.length()").value(2))
                .andExpect(jsonPath("$.aliases[0].aliasId").value(9))
                .andExpect(jsonPath("$.aliases[0].alias").value("AMZN"))
                .andExpect(jsonPath("$.aliases[1].aliasId").value(10))
                .andExpect(jsonPath("$.aliases[1].alias").value("Amazon India"));

        verify(preferences).create(user, request);
    }

    @Test
    void authenticatesSessionAndListsReferencePreferences() throws Exception {
        WebAuthenticationService authentication = mock(WebAuthenticationService.class);
        WebUserReferencePreferenceService preferences =
                mock(WebUserReferencePreferenceService.class);
        AppUserEntity user =
                new AppUserEntity();
        user.setId(42L);
        when(authentication.authenticate("session-token")).thenReturn(user);
        when(preferences.list(user)).thenReturn(
                new WebUserReferencePreferenceService.UserReferencePreferenceListResponse(
                        java.util.List.of(
                                new WebUserReferencePreferenceService.UserReferencePreferenceResponse(
                                        7L,
                                        com.apps.deen_sa.domain.UserReferenceEntityType.ACCOUNT,
                                        "HDFC Salary Account",
                                        java.util.List.of(
                                                new WebUserReferencePreferenceService.AliasResponse(
                                                        9L, "Salary Account")), 9))));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WebFinanceController(new WebManager(
                authentication,
                mock(WebLoginRequestService.class),
                mock(WebExpenseTaxonomyService.class),
                preferences,
                mock(WebReferenceMergeService.class),
                mock(com.apps.deen_sa.service.MonthlyFinancialTransactionService.class),
                mock(com.apps.deen_sa.service.FinancialTransactionListService.class),
                mock(com.apps.deen_sa.service.FinancialTransactionCalendarService.class),
                mock(com.apps.deen_sa.service.ExpenseEditOptionsService.class),
                mock(com.apps.deen_sa.service.FinancialTransactionEditService.class),
                mock(PendingActionContextService.class)),
                false,
                "Lax")).build();

        mvc.perform(get("/api/web/reference-preferences")
                        .cookie(new jakarta.servlet.http.Cookie(
                                "WEB_SESSION", "session-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.references.length()").value(1))
                .andExpect(jsonPath("$.references[0].referenceId").value(7))
                .andExpect(jsonPath("$.references[0].entityType").value("ACCOUNT"))
                .andExpect(jsonPath("$.references[0].primaryReference")
                        .value("HDFC Salary Account"))
                .andExpect(jsonPath("$.references[0].aliases[0].aliasId").value(9))
                .andExpect(jsonPath("$.references[0].aliases[0].alias")
                        .value("Salary Account"))
                .andExpect(jsonPath("$.references[0].transactionCount").value(9));

        verify(preferences).list(user);
    }
}
