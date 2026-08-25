package com.apps.deen_sa.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TaxonomyCandidateDto {
    private String category;
    private String subcategory;
    private String itemConcept;
    private BigDecimal confidence;
}
