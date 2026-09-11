package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.entity.FinancialTransactionEntity;
import com.apps.deen_sa.entity.UserReferenceEntity;
import com.apps.deen_sa.repository.FinancialTransactionRepository;
import com.apps.deen_sa.repository.UserReferenceEntityRepository;
import com.apps.deen_sa.exception.WebApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class FinancialTransactionEditService {
    private final FinancialTransactionRepository transactions;
    private final UserReferenceEntityRepository references;
    private final ExpenseTaxonomyRegistry taxonomy;
    private final MoneyStoryChangeService storyChanges;

    public FinancialTransactionEditService(FinancialTransactionRepository transactions,
                                           UserReferenceEntityRepository references,
                                           ExpenseTaxonomyRegistry taxonomy) {
        this(transactions, references, taxonomy, null);
    }
    @Autowired
    public FinancialTransactionEditService(FinancialTransactionRepository transactions,
                                           UserReferenceEntityRepository references,
                                           ExpenseTaxonomyRegistry taxonomy,
                                           MoneyStoryChangeService storyChanges) {
        this.transactions = transactions; this.references = references; this.taxonomy = taxonomy;
        this.storyChanges = storyChanges;
    }

    @Transactional
    public FinancialTransactionListService.ExpenseItem edit(
            AppUserEntity user,
            Long transactionId,
            ExpenseUpdate request
    ) {
        if (request == null || request.hasNoChanges()) {
            throw badRequest("Provide at least one value to update");
        }
        FinancialTransactionEntity transaction = transactions
                .findOwnedVisibleById(transactionId, user.getId())
                .orElseThrow(() -> new WebApiException(
                        HttpStatus.NOT_FOUND, "EXPENSE_NOT_FOUND",
                        "Expense not found or no longer active"));
        LocalDate previousDate = transaction.getOccurredAt();

        if (request.amount() != null) {
            if (request.amount().signum() <= 0) {
                throw badRequest("Amount must be greater than zero");
            }
            transaction.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        }
        if (request.transactionDate() != null) {
            transaction.setOccurredAt(request.transactionDate());
        }
        updateClassification(transaction, request.category(), request.subcategory());
        if (request.merchantId() != null) {
            UserReferenceEntity merchant = references
                    .findByIdAndUserIdAndEntityTypeAndActiveTrue(
                            request.merchantId(), user.getId(), UserReferenceEntityType.MERCHANT)
                    .orElseThrow(() -> new WebApiException(
                            HttpStatus.BAD_REQUEST, "INVALID_MERCHANT",
                            "Merchant is unavailable for this user"));
            transaction.setMerchant(merchant);
        }
        transaction.setUpdatedAt(Instant.now());
        FinancialTransactionEntity saved = transactions.saveAndFlush(transaction);
        if (storyChanges != null) {
            storyChanges.changed(user, previousDate);
            if (!previousDate.equals(saved.getOccurredAt())) storyChanges.changed(user, saved.getOccurredAt());
        }
        return FinancialTransactionListService.ExpenseItem.from(saved, user);
    }

    @Transactional
    public void delete(AppUserEntity user, Long transactionId) {
        FinancialTransactionEntity transaction = transactions
                .findOwnedVisibleById(transactionId, user.getId())
                .orElseThrow(() -> new WebApiException(
                        HttpStatus.NOT_FOUND, "EXPENSE_NOT_FOUND",
                        "Expense not found or no longer active"));
        Instant now = Instant.now();
        transaction.setDeletedAt(now);
        transaction.setUpdatedAt(now);
        transactions.saveAndFlush(transaction);
        if (storyChanges != null) storyChanges.changed(user, transaction.getOccurredAt());
    }

    private void updateClassification(
            FinancialTransactionEntity transaction,
            String requestedCategory,
            String requestedSubcategory
    ) {
        if (requestedCategory == null && requestedSubcategory == null) {
            return;
        }
        String category = requestedCategory == null
                ? transaction.getCategory()
                : taxonomy.canonicalLabel(requestedCategory)
                        .filter(taxonomy::isCategory)
                        .orElseThrow(() -> badRequest("Invalid category"));
        String subcategory = requestedSubcategory == null
                ? transaction.getSubcategory()
                : taxonomy.canonicalLabel(requestedSubcategory)
                        .filter(taxonomy::isSubcategory)
                        .orElseThrow(() -> badRequest("Invalid subcategory"));
        if (category == null || subcategory == null
                || !taxonomy.subcategoriesFor(category).contains(subcategory)) {
            throw badRequest("Subcategory does not belong to category");
        }
        transaction.setCategory(category);
        transaction.setSubcategory(subcategory);
        transaction.setSpendingNature(taxonomy.spendingNatureFor(category, subcategory)
                .orElseThrow(() -> badRequest("Spending nature is missing from the expense taxonomy")));
    }

    private WebApiException badRequest(String message) {
        return new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_EXPENSE_UPDATE", message);
    }

    public record ExpenseUpdate(
            BigDecimal amount,
            LocalDate transactionDate,
            String category,
            String subcategory,
            Long merchantId
    ) {
        boolean hasNoChanges() {
            return amount == null && transactionDate == null
                    && category == null && subcategory == null && merchantId == null;
        }
    }
}
