package com.apps.deen_sa.cache;

import com.apps.deen_sa.controller.WebFinanceController;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.service.WebAccountService;
import com.apps.deen_sa.service.WebAuthenticationService;
import com.apps.deen_sa.service.WebManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortalReadCacheAspectTest {
    final PortalReadCacheTest fixture = new PortalReadCacheTest();
    final WebAuthenticationService auth = mock(WebAuthenticationService.class);
    final WebManager manager = mock(WebManager.class);
    WebFinanceController controller;
    AppUserEntity user;

    @BeforeEach void setup() {
        fixture.setup();
        user = new AppUserEntity(); user.setId(1L); user.setTimezone("Asia/Kolkata"); user.setCurrency("INR"); user.setRole("USER");
        when(auth.authenticate("secret-token")).thenReturn(user);
        var factory = new AspectJProxyFactory(new WebFinanceController(manager, false, "Lax"));
        factory.addAspect(new PortalReadCacheAspect(fixture.cache, auth, fixture.mapper,
                Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC)));
        controller = factory.getProxy();
    }

    @Test void authenticatesEveryHitAndNeverStoresRawSessionToken() {
        var response = new WebAccountService.AccountList("INR", List.of());
        when(manager.accounts("secret-token")).thenReturn(response);
        assertEquals(response, controller.accounts("secret-token"));
        assertEquals(response, controller.accounts("secret-token"));
        verify(manager, times(1)).accounts("secret-token");
        verify(auth, times(2)).authenticate("secret-token");
        var keys = org.mockito.ArgumentCaptor.forClass(String.class);
        var values = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(fixture.store).put(anyString(), keys.capture(), values.capture(), any(java.time.Duration.class));
        assertFalse((keys.getValue() + values.getValue()).contains("secret-token"));
    }

    @Test void revokedSessionCannotReadExistingCache() {
        when(manager.accounts("secret-token")).thenReturn(new WebAccountService.AccountList("INR", List.of()));
        controller.accounts("secret-token");
        when(auth.authenticate("secret-token")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        assertThrows(ResponseStatusException.class, () -> controller.accounts("secret-token"));
        verify(manager, times(1)).accounts("secret-token");
    }

    @Test void switchingProfileAndDifferentSessionsCannotShareCache() {
        when(manager.accounts(anyString())).thenReturn(new WebAccountService.AccountList("INR", List.of()));
        controller.accounts("secret-token");
        user.setId(2L); controller.accounts("secret-token");
        when(auth.authenticate("other-token")).thenReturn(user);
        controller.accounts("other-token");
        verify(manager, times(2)).accounts("secret-token");
        verify(manager).accounts("other-token");
    }

    @Test void differentQueryArgumentsDoNotShareResponsesAndWritesInvalidate() {
        when(manager.searchStocks("secret-token", "A")).thenReturn(List.of());
        when(manager.searchStocks("secret-token", "B")).thenReturn(List.of());
        controller.searchStocks("secret-token", "A"); controller.searchStocks("secret-token", "B");
        controller.searchStocks("secret-token", "A");
        verify(manager, times(1)).searchStocks("secret-token", "A");
        fixture.cache.invalidate(); controller.searchStocks("secret-token", "A");
        verify(manager, times(2)).searchStocks("secret-token", "A");
    }

    @Test void authenticationEndpointsRemainUncached() {
        controller.session("secret-token"); controller.session("secret-token");
        verify(manager, times(2)).validateSession("secret-token");
        verifyNoInteractions(auth);
        verifyNoInteractions(fixture.store);
    }
}
