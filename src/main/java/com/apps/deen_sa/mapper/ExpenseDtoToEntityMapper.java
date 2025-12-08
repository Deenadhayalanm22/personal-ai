package com.apps.deen_sa.mapper;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.entity.ExpenseEntity;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public class ExpenseDtoToEntityMapper {

    private ExpenseDtoToEntityMapper() {} // prevent instantiation

    public static ExpenseEntity toEntity(ExpenseDto dto, Long userId) {

        // Backend default → always sanitize date
        OffsetDateTime spentAt = dto.getSpentAt() != null
                ? dto.getSpentAt().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime()
                : OffsetDateTime.now();

        return ExpenseEntity.builder()
                .userId(userId)                                // Hardcode 1 for now if single user
                .accountId(null)                               // Future enhancement
                .amount(dto.getAmount())
                .currency("INR")
                .category(dto.getCategory())
                .subcategory(dto.getSubcategory())
                .merchantName(dto.getMerchantName())
                .paymentMethod(dto.getPaymentMethod())
                .spentAt(spentAt)
                .recordedAt(OffsetDateTime.now())
                .rawText(dto.getRawText())
                .details(dto.getDetails())
                .tags(dto.getTags())
                .isValid(dto.isValid())
                .validationReason(dto.getReason())
                .source("voice")
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
