package com.apps.deen_sa.mapper;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.entity.TransactionEntity;
import com.apps.deen_sa.utils.TransactionTypeEnum;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

public class ExpenseDtoToEntityMapper {

    private ExpenseDtoToEntityMapper() {
        // utility class, no instantiation
    }

    public static TransactionEntity toEntity(
            ExpenseDto dto,
            Long userId
    ) {

        TransactionEntity entity = new TransactionEntity();

        // ----------------------------
        // IDENTITY
        // ----------------------------
        entity.setUserId(userId.toString());
        entity.setTransactionType(TransactionTypeEnum.EXPENSE);

        // ----------------------------
        // CORE FINANCIAL DATA
        // ----------------------------
        entity.setAmount(dto.getAmount());
        entity.setCategory(dto.getCategory());
        entity.setSubcategory(dto.getSubcategory());

        // Merchant / payee
        entity.setMainEntity(dto.getMerchantName());

        // ----------------------------
        // DATE HANDLING
        // ----------------------------
        if (dto.getTransactionDate() != null) {
            entity.setTimestamp(
                    dto.getTransactionDate()
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
            );
        } else {
            // Should not happen if validator is correct
            entity.setTimestamp(Instant.now());
        }

        // ----------------------------
        // RAW TEXT
        // ----------------------------
        entity.setRawText(dto.getRawText());

        // ----------------------------
        // TAGS
        // ----------------------------
        entity.setTags(dto.getTags());

        // ----------------------------
        // DETAILS (MERGE SAFELY)
        // ----------------------------
        Map<String, Object> details = new HashMap<>();

        if (dto.getDetails() != null) {
            details.putAll(dto.getDetails());
        }

        entity.setDetails(details);

        return entity;
    }

    public static void updateEntity(TransactionEntity entity, ExpenseDto dto) {

        // ----------------------------
        // CORE FINANCIAL DATA
        // ----------------------------

        if (dto.getAmount() != null) {
            entity.setAmount(dto.getAmount());
        }

        if (dto.getCategory() != null) {
            entity.setCategory(dto.getCategory());
        }

        if (dto.getSubcategory() != null) {
            entity.setSubcategory(dto.getSubcategory());
        }

        if (dto.getMerchantName() != null) {
            entity.setMainEntity(dto.getMerchantName());
        }

        // ----------------------------
        // DATE HANDLING
        // ----------------------------

        if (dto.getTransactionDate() != null) {
            entity.setTimestamp(
                    dto.getTransactionDate()
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
            );
        }

        // ----------------------------
        // RAW TEXT (append, don’t overwrite history)
        // ----------------------------

        if (dto.getRawText() != null) {
            entity.setRawText(dto.getRawText());
        }

        // ----------------------------
        // TAGS (replace only if present)
        // ----------------------------

        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            entity.setTags(dto.getTags());
        }

        // ----------------------------
        // DETAILS (deep merge, never wipe)
        // ----------------------------

        if (dto.getDetails() != null && !dto.getDetails().isEmpty()) {
            Map<String, Object> existing =
                    entity.getDetails() != null
                            ? new HashMap<>(entity.getDetails())
                            : new HashMap<>();

            existing.putAll(dto.getDetails());
            entity.setDetails(existing);
        }

        // ----------------------------
        // NEVER TOUCH THESE HERE
        // ----------------------------
        // entity.setUserId(...)
        // entity.setTransactionType(...)
        // entity.setCompletenessLevel(...)
        // entity.setFinanciallyApplied(...)
        // entity.setSourceContainerId(...)
    }
}
