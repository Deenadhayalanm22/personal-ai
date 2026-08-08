package com.apps.deen_sa.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Getter
@Setter
public class AssetBuyDto {
    private boolean valid;
    private String reason; // if valid = false

    // Asset identification
    private String assetIdentifier; // ITC, SBI Bluechip, Gold
    private BigDecimal quantity;     // 100 shares, 50 units, 20 grams
    private String unit;             // shares, units, grams
    
    // Buy transaction details
    private BigDecimal pricePerUnit; // 380, 520, 6200
    private LocalDate tradeDate;     // defaults to today if not provided
    
    // Source of funds
    private String sourceAccount;    // BANK_ACCOUNT, CASH, etc.
    
    private Map<String, Object> details;
    private String rawText;
}
