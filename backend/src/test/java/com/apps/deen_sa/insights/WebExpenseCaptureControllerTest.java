package com.apps.deen_sa.insights;

import com.apps.deen_sa.controller.WebExpenseCaptureController;
import com.apps.deen_sa.controller.ManualExpenseCaptureController;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.exception.WebApiExceptionHandler;
import com.apps.deen_sa.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WebExpenseCaptureControllerTest {
    final WebAuthenticationService auth=mock(WebAuthenticationService.class);
    final WebExpenseCaptureService capture=mock(WebExpenseCaptureService.class);
    final ExpenseConfirmationCommandHandler confirmation=mock(ExpenseConfirmationCommandHandler.class);
    final org.springframework.test.web.servlet.MockMvc mvc=MockMvcBuilders.standaloneSetup(new WebExpenseCaptureController(auth,capture,confirmation))
            .setControllerAdvice(new WebApiExceptionHandler()).build();
    @Test void manualPreparationAuthenticatesAndUsesActiveProfileWithoutAI() throws Exception {
        var manual=mock(ManualExpenseCaptureService.class);
        var manualMvc=MockMvcBuilders.standaloneSetup(new ManualExpenseCaptureController(auth,manual))
                .setControllerAdvice(new WebApiExceptionHandler()).build();
        when(auth.authenticate(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        manualMvc.perform(post("/api/web/expenses/manual").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(manual);
        var user=new AppUserEntity();user.setId(42L);when(auth.authenticate("demo")).thenReturn(user);
        when(manual.prepare(eq(user),any())).thenReturn(new WebExpenseCaptureService.Response("READY","Review",123L,null));
        manualMvc.perform(post("/api/web/expenses/manual").cookie(new Cookie("WEB_SESSION","demo"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-09-28\",\"amount\":450,\"category\":\"Food\",\"subcategory\":\"Dining\",\"requestId\":\"123e4567-e89b-12d3-a456-426614174000\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.extractionId").value(123));
        verify(manual).prepare(eq(user),any());verifyNoInteractions(capture,confirmation);
    }
    @Test void authenticationPrecedesPreparationAndDecisions() throws Exception {
        when(auth.authenticate(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(post("/api/web/expense-chat/capture").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        for (String action:java.util.List.of("confirm","cancel")) mvc.perform(post("/api/web/expense-chat/capture/123/"+action)).andExpect(status().isUnauthorized());
        verifyNoInteractions(capture,confirmation);
    }
    @Test void confirmationAndCancellationUseResolvedDemoProfile() throws Exception {
        var user=new AppUserEntity();user.setId(42L);when(auth.authenticate("demo")).thenReturn(user);
        when(confirmation.handleWeb(42L,123L,true)).thenReturn(LocalDate.parse("2026-09-28"));
        mvc.perform(post("/api/web/expense-chat/capture/123/confirm").cookie(new Cookie("WEB_SESSION","demo")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.date").value("2026-09-28"));
        mvc.perform(post("/api/web/expense-chat/capture/123/cancel").cookie(new Cookie("WEB_SESSION","demo")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(confirmation).handleWeb(42L,123L,false);
        when(capture.capture(eq(user),any())).thenReturn(new WebExpenseCaptureService.Response("NEEDS_DETAILS","Amount?",null,null));
        mvc.perform(post("/api/web/expense-chat/capture").cookie(new Cookie("WEB_SESSION","demo"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-09-28\",\"message\":\"Groceries\",\"turns\":[],\"requestId\":\"123e4567-e89b-12d3-a456-426614174000\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEEDS_DETAILS"));
        verify(capture).capture(eq(user),any());
    }
}
