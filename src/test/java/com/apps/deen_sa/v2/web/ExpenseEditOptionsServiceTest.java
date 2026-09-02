package com.apps.deen_sa.v2.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.finance.expense.ExpenseTaxonomyRegistry;
import com.apps.deen_sa.v2.domain.UserReferenceEntityType;
import com.apps.deen_sa.v2.entity.UserReferenceEntity;
import com.apps.deen_sa.v2.repository.UserReferenceEntityRepository;
import com.apps.deen_sa.v2.service.ExpenseEditOptionsService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExpenseEditOptionsServiceTest {
    @Test
    void returnsTaxonomyHierarchyAndUsersActiveMerchants() {
        ExpenseTaxonomyRegistry taxonomy = mock(ExpenseTaxonomyRegistry.class);
        UserReferenceEntityRepository references = mock(UserReferenceEntityRepository.class);
        AppUserEntity user = new AppUserEntity();
        user.setId(42L);
        UserReferenceEntity merchant = new UserReferenceEntity();
        merchant.setId(7L);
        merchant.setCanonicalName("Nandana Palace");
        when(taxonomy.categories()).thenReturn(new LinkedHashSet<>(List.of("Food & Dining")));
        when(taxonomy.subcategoriesFor("Food & Dining"))
                .thenReturn(new LinkedHashSet<>(List.of("Restaurant & Cafe", "Food Delivery")));
        when(references.findByUserIdAndEntityTypeAndActiveTrueOrderByCanonicalNameAsc(
                42L, UserReferenceEntityType.MERCHANT))
                .thenReturn(List.of(merchant));

        var result = new ExpenseEditOptionsService(taxonomy, references).options(user);

        assertThat(result.categories()).containsExactly(
                new ExpenseEditOptionsService.CategoryOption(
                        "Food & Dining", List.of("Restaurant & Cafe", "Food Delivery")));
        assertThat(result.merchants()).containsExactly(
                new ExpenseEditOptionsService.MerchantOption(7L, "Nandana Palace"));
    }
}
