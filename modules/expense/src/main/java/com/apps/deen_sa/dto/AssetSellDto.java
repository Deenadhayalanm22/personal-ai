package com.apps.deen_sa.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Getter
@Setter
public class AssetSellDto {
    private boolean valid;
    private String reason; // if valid = false

    // Asset identification
    private String assetIdentifier; // ITC, SBI Bluechip, Gold
    private BigDecimal quantity;     // 15 shares, 5 units, 2 grams
    private String unit;             // shares, units, grams
    
    // Sell transaction details
    private BigDecimal pricePerUnit; // 400, 520, 6300
    private LocalDate tradeDate;     // defaults to today if not provided
    
    // Target for proceeds (where money goes)
    private String targetAccount;    // BANK_ACCOUNT, CASH, etc.
    
    private Map<String, Object> details;
    private String rawText;
}
