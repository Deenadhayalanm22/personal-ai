package com.apps.deen_sa.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ExpenseItemDto {
    private Long id;
    private BigDecimal amount;
    private String category;
    private String subcategory;
    private String merchantName;
    private String spentAt; // YYYY-MM-DD
}
