package com.apps.deen_sa.mapper;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.entity.ExpenseEntity;

import java.time.OffsetDateTime;

public class ExpenseDtoToEntityMapper {

    private ExpenseDtoToEntityMapper() {} // prevent instantiation

    public static ExpenseEntity toEntity(ExpenseDto dto) {

        ExpenseEntity entity = new ExpenseEntity();

        entity.setAmount(dto.getAmount());
        entity.setCategory(dto.getCategory());
        entity.setSubcategory(dto.getSubcategory());
        entity.setMerchant(dto.getMerchant());
        entity.setPaymentMethod(dto.getPaymentMethod());

        if (dto.getSpentAt() != null) {
            entity.setSpentAt(OffsetDateTime.parse(dto.getSpentAt() + "T00:00:00Z"));
        } else {
            entity.setSpentAt(OffsetDateTime.now());
        }

        entity.setRawText(dto.getRawText());
        entity.setDetails(dto.getDetails());
        entity.setEmbedding(dto.getEmbedding());
        entity.setCreatedAt(OffsetDateTime.now());

        return entity;
    }
}
