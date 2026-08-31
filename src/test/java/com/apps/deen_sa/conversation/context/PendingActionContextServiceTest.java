package com.apps.deen_sa.conversation.context;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.web.WebApiException;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PendingActionContextServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-31T14:15:00Z");

    @Test
    void createsThirtyMinuteContextAndReplacesPreviousActiveContext() {
        PendingActionContextRepository repository = mock(PendingActionContextRepository.class);
        PendingActionContextEntity previous = active("ctx_old", LocalDate.of(2026, 8, 10));
        when(repository.findActiveForUpdate(42L)).thenReturn(List.of(previous));
        PendingActionContextService service = service(repository);

        var response = service.create(42L, new PendingActionContextService.ContextRequest(
                PendingActionContextService.TYPE, "2026-08-14", "Asia/Kolkata"));

        assertThat(response.contextId()).startsWith("ctx_");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.date()).isEqualTo("2026-08-14");
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(response.whatsappUrl()).isEqualTo("https://wa.me/919999999999");
        assertThat(previous.getStatus()).isEqualTo(PendingActionContextStatus.REPLACED);
        verify(repository).save(any(PendingActionContextEntity.class));
    }

    @Test
    void rejectsFutureDateAndInvalidTimezone() {
        PendingActionContextService service = service(mock(PendingActionContextRepository.class));
        assertThatThrownBy(() -> service.create(42L, new PendingActionContextService.ContextRequest(
                PendingActionContextService.TYPE, "2026-09-01", "Asia/Kolkata")))
                .isInstanceOfSatisfying(WebApiException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_CONTEXT_DATE"));
        assertThatThrownBy(() -> service.create(42L, new PendingActionContextService.ContextRequest(
                PendingActionContextService.TYPE, "2026-08-14", "Not/AZone")))
                .isInstanceOfSatisfying(WebApiException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_TIMEZONE"));
    }

    @Test
    void attachesFallbackDateButLetsExplicitDateWin() {
        PendingActionContextRepository repository = mock(PendingActionContextRepository.class);
        when(repository.findFirstByUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                eq(42L), eq(PendingActionContextStatus.ACTIVE), any())).thenReturn(
                Optional.of(active("ctx_one", LocalDate.of(2026, 8, 14))));
        PendingActionContextService service = service(repository);

        ExpenseDto fallback = new ExpenseDto();
        service.attachToNextExpense(fallback, "spent 500 on fuel", 42L, "WHATSAPP");
        assertThat(fallback.getTransactionDate()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(fallback.isContextDateApplied()).isTrue();

        ExpenseDto explicit = new ExpenseDto();
        explicit.setTransactionDate(LocalDate.of(2026, 8, 20));
        service.attachToNextExpense(explicit, "on 20 August spent 500 on fuel", 42L, "WHATSAPP");
        assertThat(explicit.getTransactionDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(explicit.isContextDateApplied()).isFalse();
        assertThat(explicit.getPendingActionContextId()).isEqualTo("ctx_one");
    }

    @Test
    void consumesOnlyAnActiveUnexpiredOwnedContext() {
        PendingActionContextRepository repository = mock(PendingActionContextRepository.class);
        PendingActionContextEntity context = active("ctx_one", LocalDate.of(2026, 8, 14));
        when(repository.findOwnedForUpdate("ctx_one", 42L)).thenReturn(Optional.of(context));

        assertThat(service(repository).consumeIfActive(42L, "ctx_one")).isTrue();
        assertThat(context.getStatus()).isEqualTo(PendingActionContextStatus.CONSUMED);
        assertThat(context.getConsumedAt()).isEqualTo(NOW);
    }

    private PendingActionContextService service(PendingActionContextRepository repository) {
        return new PendingActionContextService(repository, Duration.ofMinutes(30), "+91 99999 99999",
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PendingActionContextEntity active(String id, LocalDate date) {
        PendingActionContextEntity context = new PendingActionContextEntity();
        context.setId(id); context.setUserId(42L); context.setContextType(PendingActionContextService.TYPE);
        context.setContextValue(date.toString()); context.setTimezone("Asia/Kolkata");
        context.setStatus(PendingActionContextStatus.ACTIVE); context.setCreatedAt(NOW.minusSeconds(60));
        context.setExpiresAt(NOW.plusSeconds(600));
        return context;
    }
}
