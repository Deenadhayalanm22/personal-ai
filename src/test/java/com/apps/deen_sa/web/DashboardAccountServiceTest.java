package com.apps.deen_sa.web;

import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerService;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DashboardAccountServiceTest {
    private static final ZoneId TIMEZONE = ZoneId.of("Asia/Kolkata");
    private final StateContainerService containers = mock(StateContainerService.class);
    private final StateChangeRepository transactions = mock(StateChangeRepository.class);
    private final DashboardAccountService service = new DashboardAccountService(containers, transactions);

    @Test
    void mapsCashLikeAndCreditCardValuesForDisplay() {
        StateContainerEntity bank = container(1L, "HDFC Salary", "BANK_ACCOUNT", "42500");
        bank.setAvailableValue(new BigDecimal("42500"));
        StateContainerEntity card = container(2L, "HDFC Card", "CREDIT_CARD", "26000");
        card.setCapacityLimit(new BigDecimal("100000"));
        card.setLastActivityAt(Instant.parse("2026-08-25T08:00:00Z"));
        when(containers.getActiveContainers(42L)).thenReturn(List.of(bank, card));
        when(transactions.countActiveTransactionsByAccount(eq("42"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new Object[]{1L, 48L}, new Object[]{2L, 40L}));

        var result = service.activeAccounts(42L, TIMEZONE);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).primaryLabel()).isEqualTo("Available balance");
        assertThat(result.get(0).primaryValue()).isEqualByComparingTo("42500");
        assertThat(result.get(0).transactionCount()).isEqualTo(48);
        assertThat(result.get(1).primaryLabel()).isEqualTo("Available credit");
        assertThat(result.get(1).primaryValue()).isEqualByComparingTo("74000");
        assertThat(result.get(1).outstanding()).isEqualByComparingTo("26000");
        assertThat(result.get(1).creditLimit()).isEqualByComparingTo("100000");
        assertThat(result.get(1).transactionCount()).isEqualTo(40);
    }

    @Test
    void ordersAccountsByTransactionCountThenLatestActivity() {
        StateContainerEntity olderCard = container(2L, "HDFC Card", "CREDIT_CARD", "15000");
        olderCard.setLastActivityAt(Instant.parse("2026-08-20T08:00:00Z"));
        StateContainerEntity bank = container(1L, "HDFC Bank", "BANK_ACCOUNT", "48000");
        bank.setLastActivityAt(Instant.parse("2026-08-24T08:00:00Z"));
        StateContainerEntity newerWallet = container(3L, "Wallet", "WALLET", "500");
        newerWallet.setLastActivityAt(Instant.parse("2026-08-25T08:00:00Z"));
        when(containers.getActiveContainers(42L)).thenReturn(List.of(olderCard, bank, newerWallet));
        when(transactions.countActiveTransactionsByAccount(eq("42"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new Object[]{2L, 40L}, new Object[]{1L, 48L}, new Object[]{3L, 40L}));

        var result = service.activeAccounts(42L, TIMEZONE);

        assertThat(result).extracting(DashboardAccountService.DashboardAccount::name)
                .containsExactly("HDFC Bank", "Wallet", "HDFC Card");
    }

    @Test
    void excludesNonFinancialContainers() {
        when(containers.getActiveContainers(42L)).thenReturn(List.of(container(3L, "Fabric", "INVENTORY", "20")));
        when(transactions.countActiveTransactionsByAccount(eq("42"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        assertThat(service.activeAccounts(42L, TIMEZONE)).isEmpty();
    }

    private StateContainerEntity container(Long id, String name, String type, String current) {
        StateContainerEntity value = new StateContainerEntity();
        value.setId(id); value.setOwnerId(42L); value.setName(name); value.setContainerType(type);
        value.setStatus("ACTIVE"); value.setCurrency("INR"); value.setCurrentValue(new BigDecimal(current));
        return value;
    }
}
