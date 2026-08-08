package com.apps.deen_sa.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
public class AssetDto {
    private boolean valid;
    private String reason; // if valid = false

    // Asset identification
    private String assetIdentifier; // ITC, SBI Bluechip, Gold
    private BigDecimal quantity;     // 100 shares, 50 units, 20 grams
    private String unit;             // shares, units, grams

    // Optional enrichment fields
    private String broker;           // optional
    private BigDecimal investedAmount; // optional - "how much you invested"
    
    private Map<String, Object> details;
    private String rawText;
}
