package com.apps.deen_sa.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AccountSetupDto {
    private boolean valid;
    private String reason; // if valid = false

    // Ownership
    private String ownerType;     // USER | BUSINESS | EMPLOYEE
    private Long ownerId;

    // Classification
    private String containerType; // BANK | CREDIT | WALLET | CASH
    private String name;          // Salary Account, HDFC Credit Card

    // Financial state
    private String currency;      // INR
    private BigDecimal currentValue;
    private BigDecimal availableValue;

    // Limits
    private BigDecimal capacityLimit; // credit limit, wallet max
    private BigDecimal minThreshold;  // optional

    // Lifecycle / metadata
    private String externalRefType; // BANK | WALLET
    private String externalRefId;   // masked number

    private Map<String, Object> details;

    private String rawText;

    private List<String> missingFields;
}
