package com.apps.deen_sa.credits;

import com.apps.deen_sa.controller.AiCreditController;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.exception.WebApiExceptionHandler;
import com.apps.deen_sa.service.WebAuthenticationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiCreditControllerTest {
    final WebAuthenticationService auth=mock(WebAuthenticationService.class);
    final CreditStore store=mock(CreditStore.class);
    final org.springframework.test.web.servlet.MockMvc mvc=MockMvcBuilders.standaloneSetup(new AiCreditController(auth,store)).setControllerAdvice(new WebApiExceptionHandler()).build();
    @Test void unauthenticatedAndNonAdminCannotReadOrGrantOtherUsersCredits() throws Exception {
        when(auth.authenticate(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(get("/api/web/ai-credits")).andExpect(status().isUnauthorized());
        var user=new AppUserEntity(); user.setId(7L); when(auth.authenticate("user")).thenReturn(user);
        mvc.perform(get("/api/web/ai-credits/admin/users").cookie(new Cookie("WEB_SESSION","user"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/web/ai-credits/admin/users/99/grants").cookie(new Cookie("WEB_SESSION","user")).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}")).andExpect(status().isForbidden());
        verifyNoInteractions(store);
    }
    @Test void balanceUsesServerProfileAndAdminGrantsAreAttributed() throws Exception {
        var user=new AppUserEntity(); user.setId(7L); user.setRole("SUPER_ADMIN"); user.setPortalEnabled(true); user.setChannel("WHATSAPP");
        when(auth.authenticate("admin")).thenReturn(user);
        mvc.perform(get("/api/web/ai-credits?userId=99").cookie(new Cookie("WEB_SESSION","admin"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(store).balance(7L);
        UUID id=UUID.randomUUID();
        mvc.perform(post("/api/web/ai-credits/admin/users/99/grants").cookie(new Cookie("WEB_SESSION","admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":100,\"requestId\":\""+id+"\",\"note\":\"Welcome\"}")).andExpect(status().isOk());
        verify(store).grant(eq(99L),eq(id),eq(new BigDecimal("100")),eq(7L),eq("Welcome"));
        user.setPortalEnabled(false);
        user.setChannel("WEB_DEMO");
        mvc.perform(get("/api/web/ai-credits/admin/users").cookie(new Cookie("WEB_SESSION","admin"))).andExpect(status().isOk());
        mvc.perform(get("/api/web/ai-credits/permissions").cookie(new Cookie("WEB_SESSION","admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.admin").value(true));
        user.setRole("USER");
        mvc.perform(get("/api/web/ai-credits/permissions").cookie(new Cookie("WEB_SESSION","admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.admin").value(false));
        mvc.perform(get("/api/web/ai-credits/admin/users").cookie(new Cookie("WEB_SESSION","admin"))).andExpect(status().isForbidden());
    }
}
