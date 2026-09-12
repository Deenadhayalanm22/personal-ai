package com.apps.deen_sa.web;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.service.ExpenseTaxonomyRegistry;
import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.entity.UserReferenceEntity;
import com.apps.deen_sa.repository.UserReferenceEntityRepository;
import com.apps.deen_sa.service.ExpenseEditOptionsService;
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
        UserReferenceEntity account = new UserReferenceEntity();
        account.setId(8L);
        account.setCanonicalName("HDFC Salary Account");
        when(taxonomy.categories()).thenReturn(new LinkedHashSet<>(List.of("Food & Dining")));
        when(taxonomy.subcategoriesFor("Food & Dining"))
                .thenReturn(new LinkedHashSet<>(List.of("Restaurant & Cafe", "Food Delivery")));
        when(references.findByUserIdAndEntityTypeAndActiveTrueOrderByCanonicalNameAsc(
                42L, UserReferenceEntityType.MERCHANT))
                .thenReturn(List.of(merchant));
        when(references.findByUserIdAndEntityTypeAndActiveTrueOrderByCanonicalNameAsc(
                42L, UserReferenceEntityType.ACCOUNT))
                .thenReturn(List.of(account));

        var result = new ExpenseEditOptionsService(taxonomy, references).options(user);

        assertThat(result.categories()).containsExactly(
                new ExpenseEditOptionsService.CategoryOption(
                        "Food & Dining", List.of("Restaurant & Cafe", "Food Delivery")));
        assertThat(result.merchants()).containsExactly(
                new ExpenseEditOptionsService.MerchantOption(7L, "Nandana Palace"));
        assertThat(result.accounts()).containsExactly(
                new ExpenseEditOptionsService.AccountOption(8L, "HDFC Salary Account"));
    }
}
