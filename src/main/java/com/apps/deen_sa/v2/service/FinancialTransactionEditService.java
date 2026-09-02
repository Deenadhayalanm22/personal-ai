package com.apps.deen_sa.v2.service;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.finance.expense.ExpenseTaxonomyRegistry;
import com.apps.deen_sa.v2.domain.UserReferenceEntityType;
import com.apps.deen_sa.v2.entity.FinancialTransactionEntity;
import com.apps.deen_sa.v2.entity.UserReferenceEntity;
import com.apps.deen_sa.v2.repository.FinancialTransactionRepository;
import com.apps.deen_sa.v2.repository.UserReferenceEntityRepository;
import com.apps.deen_sa.web.WebApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinancialTransactionEditService {
    private final FinancialTransactionRepository transactions;
    private final UserReferenceEntityRepository references;
    private final ExpenseTaxonomyRegistry taxonomy;

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
        return item(saved, user);
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
    }

    private FinancialTransactionListService.ExpenseItem item(
            FinancialTransactionEntity transaction,
            AppUserEntity user
    ) {
        String merchant = transaction.getMerchant() == null
                ? null
                : transaction.getMerchant().getCanonicalName();
        return new FinancialTransactionListService.ExpenseItem(
                transaction.getId(),
                transaction.getSourceDraft().getRawText(),
                transaction.getAmount().setScale(2, RoundingMode.HALF_UP),
                user.getCurrency(),
                transaction.getOccurredAt()
                        .atStartOfDay(ZoneId.of(user.getTimezone())).toInstant(),
                transaction.getCategory(),
                transaction.getSubcategory(),
                merchant);
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
