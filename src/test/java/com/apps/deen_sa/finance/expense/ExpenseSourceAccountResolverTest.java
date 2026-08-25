package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.finance.legacy.state.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ExpenseSourceAccountResolverTest {
    @Test
    void resolvesGenericUpiToMostRecentlyUsedBankAccount() {
        StateContainerService containers = mock(StateContainerService.class);
        StateChangeRepository transactions = mock(StateChangeRepository.class);
        StateContainerEntity older = account(3L, "ICICI bank account", "BANK_ACCOUNT");
        StateContainerEntity recent = account(7L, "hdfc bank account", "BANK_ACCOUNT");
        when(containers.getActiveContainers(42L)).thenReturn(List.of(older, recent));
        when(transactions.findMostRecentlyUsedActiveSourceId("42", "BANK_ACCOUNT"))
                .thenReturn(Optional.of(7L));
        ExpenseDto expense = new ExpenseDto(); expense.setSourceAccount("UPI");

        StateContainerEntity resolved = new ExpenseSourceAccountResolver(containers, transactions)
                .resolve(expense, 42L);

        assertThat(resolved.getId()).isEqualTo(7L);
        assertThat(resolved.getName()).isEqualTo("hdfc bank account");
    }

    private StateContainerEntity account(Long id, String name, String type) {
        StateContainerEntity value = new StateContainerEntity();
        value.setId(id); value.setName(name); value.setContainerType(type); value.setStatus("ACTIVE");
        return value;
    }
}
