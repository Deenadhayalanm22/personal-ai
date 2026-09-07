package com.apps.deen_sa.web;

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
    void authenticatesSessionAndReturnsAllReferenceEntityTypes() throws Exception {
        WebAuthenticationService authentication = mock(WebAuthenticationService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WebFinanceController(
                authentication,
                mock(WebLoginRequestService.class),
                mock(WebExpenseTaxonomyService.class),
                new WebUserReferenceEntityTypeService(),
                mock(WebUserReferencePreferenceService.class),
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
        com.apps.deen_sa.conversation.AppUserEntity user =
                new com.apps.deen_sa.conversation.AppUserEntity();
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
                                new WebUserReferencePreferenceService.AliasResponse(10L, "Amazon India"))));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WebFinanceController(
                authentication,
                mock(WebLoginRequestService.class),
                mock(WebExpenseTaxonomyService.class),
                new WebUserReferenceEntityTypeService(),
                preferences,
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
}
