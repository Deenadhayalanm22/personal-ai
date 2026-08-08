package com.apps.deen_sa.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class LiabilityPaymentDto {

    private boolean valid;
    private String reason; // if valid = false

    // Core payment data
    private BigDecimal amount;

    // Source account (where money comes from)
    private String sourceAccount;  // BANK_ACCOUNT, WALLET

    // Target liability (where payment goes to)
    private String targetLiability; // CREDIT_CARD, LOAN
    private String targetName;      // e.g., "HDFC Credit Card", "Personal Loan"

    // Payment context
    private LocalDate paymentDate;
    private String paymentType;     // CREDIT_CARD_PAYMENT, LOAN_PAYMENT, EMI

    // Original input
    private String rawText;
}
