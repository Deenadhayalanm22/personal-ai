package com.apps.deen_sa.web;

import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DashboardAccountServiceTest {
    private final StateContainerService containers = mock(StateContainerService.class);
    private final DashboardAccountService service = new DashboardAccountService(containers);

    @Test
    void mapsCashLikeAndCreditCardValuesForDisplay() {
        StateContainerEntity bank = container(1L, "HDFC Salary", "BANK_ACCOUNT", "42500");
        bank.setAvailableValue(new BigDecimal("42500"));
        StateContainerEntity card = container(2L, "HDFC Card", "CREDIT_CARD", "26000");
        card.setCapacityLimit(new BigDecimal("100000"));
        card.setLastActivityAt(Instant.parse("2026-08-25T08:00:00Z"));
        when(containers.getActiveContainers(42L)).thenReturn(List.of(bank, card));

        var result = service.activeAccounts(42L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).primaryLabel()).isEqualTo("Available balance");
        assertThat(result.get(0).primaryValue()).isEqualByComparingTo("42500");
        assertThat(result.get(1).primaryLabel()).isEqualTo("Available credit");
        assertThat(result.get(1).primaryValue()).isEqualByComparingTo("74000");
        assertThat(result.get(1).outstanding()).isEqualByComparingTo("26000");
        assertThat(result.get(1).creditLimit()).isEqualByComparingTo("100000");
    }

    @Test
    void excludesNonFinancialContainers() {
        when(containers.getActiveContainers(42L)).thenReturn(List.of(container(3L, "Fabric", "INVENTORY", "20")));
        assertThat(service.activeAccounts(42L)).isEmpty();
    }

    private StateContainerEntity container(Long id, String name, String type, String current) {
        StateContainerEntity value = new StateContainerEntity();
        value.setId(id); value.setOwnerId(42L); value.setName(name); value.setContainerType(type);
        value.setStatus("ACTIVE"); value.setCurrency("INR"); value.setCurrentValue(new BigDecimal(current));
        return value;
    }
}
