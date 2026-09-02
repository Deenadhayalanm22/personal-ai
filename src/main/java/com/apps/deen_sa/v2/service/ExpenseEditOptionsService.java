package com.apps.deen_sa.v2.service;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.finance.expense.ExpenseTaxonomyRegistry;
import com.apps.deen_sa.v2.domain.UserReferenceEntityType;
import com.apps.deen_sa.v2.repository.UserReferenceEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseEditOptionsService {
    private final ExpenseTaxonomyRegistry taxonomy;
    private final UserReferenceEntityRepository references;

    @Transactional(readOnly = true)
    public ExpenseEditOptions options(AppUserEntity user) {
        List<CategoryOption> categories = taxonomy.categories().stream()
                .map(category -> new CategoryOption(
                        category,
                        List.copyOf(taxonomy.subcategoriesFor(category))))
                .toList();
        List<MerchantOption> merchants = references
                .findByUserIdAndEntityTypeAndActiveTrueOrderByCanonicalNameAsc(
                        user.getId(), UserReferenceEntityType.MERCHANT)
                .stream()
                .map(reference -> new MerchantOption(
                        reference.getId(), reference.getCanonicalName()))
                .toList();
        return new ExpenseEditOptions(categories, merchants);
    }

    public record ExpenseEditOptions(
            List<CategoryOption> categories,
            List<MerchantOption> merchants
    ) {
    }

    public record CategoryOption(String name, List<String> subcategories) {
    }

    public record MerchantOption(Long id, String name) {
    }
}
