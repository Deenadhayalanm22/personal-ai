# Liability Payment Implementation Summary

## Overview
Implemented credit card and loan payment functionality following the existing transaction, container, and strategy architecture. Payments are correctly modeled as TRANSFER transactions and do NOT appear in expense reports.

## Changes Made

### 1. Core Transaction Types
**File**: `core/transaction/TransactionTypeEnum.java`
- Added `TRANSFER` enum value for liability payments

### 2. Intent Classification
**File**: `llm/intent/classify.md`
- Added `LIABILITY_PAYMENT` intent
- Distinguished between expense payments (goods/services) and liability payments (credit cards/loans)
- Examples: "Paid 25,000 to credit card", "Paid EMI of 15,000"

### 3. Payment Data Model
**File**: `dto/LiabilityPaymentDto.java`
- DTO for liability payment extraction
- Fields: amount, sourceAccount, targetLiability, targetName, paymentType, paymentDate

### 4. LLM Extraction
**Files**:
- `llm/impl/LiabilityPaymentClassifier.java` - Extracts payment details from natural language
- `llm/payment/extract.md` - LLM prompt for payment extraction
- `llm/payment/schema.json` - Expected JSON schema

### 5. Adjustment Factory Extension
**File**: `finance/account/strategy/AdjustmentCommandFactory.java`
- Added `forTransferDebit()` - Creates DEBIT adjustment for source container
- Added `forTransferCredit()` - Creates CREDIT adjustment for target container

### 6. Loan Strategy
**File**: `finance/account/strategy/LoanStrategy.java`
- New strategy for LOAN containers
- Implements `CreditSettlementStrategy` interface
- `applyPayment()` method reduces loan outstanding when payment is made

### 7. Payment Handler
**File**: `finance/payment/LiabilityPaymentHandler.java`
- Implements `SpeechHandler` interface for "LIABILITY_PAYMENT" intent
- Resolves source container (bank account/wallet)
- Resolves target liability (credit card/loan)
- Creates TRANSFER transaction
- Applies financial impact:
  - Debits source container (bank account)
  - Credits target container (reduces outstanding via CreditSettlementStrategy)
- Ensures idempotency via `financiallyApplied` flag

## Architecture Compliance

### ✅ Requirements Met
- [x] NO new tables or schema changes
- [x] NO new entities
- [x] Reuses existing ValueAdjustmentService
- [x] Reuses existing strategies (CreditCardStrategy, new LoanStrategy)
- [x] Extended AdjustmentCommandFactory (not duplicated)
- [x] Follows domain-first package structure (finance.payment)
- [x] TRANSFER transactions excluded from expense reports

### Transaction Flow

```
User: "Paid 25,000 to credit card"
  ↓
IntentClassifier → LIABILITY_PAYMENT
  ↓
LiabilityPaymentHandler.handleSpeech()
  ↓
LiabilityPaymentClassifier.extractPayment() → LiabilityPaymentDto
  ↓
Resolve sourceContainer (BANK_ACCOUNT)
  ↓
Resolve targetContainer (CREDIT_CARD)
  ↓
Create TransactionEntity:
  - transactionType = TRANSFER
  - amount = 25000
  - sourceContainerId = <bank id>
  - targetContainerId = <credit card id>
  - details.reason = "CREDIT_CARD_PAYMENT"
  - financiallyApplied = false
  ↓
Save Transaction
  ↓
Apply Financial Impact:
  1. DEBIT bank (valueAdjustmentService.apply)
  2. CREDIT credit card (CreditCardStrategy.applyPayment - reduces outstanding)
  ↓
Set financiallyApplied = true
  ↓
Return SpeechResult.saved()
```

## Expense Analytics Safety

All expense analytics queries in `TransactionRepository` explicitly filter:
```sql
WHERE t.transaction_type = 'EXPENSE'
```

Therefore:
- TRANSFER transactions do NOT appear in expense summaries
- ExpenseSummaryService (uses legacy ExpenseEntity) - unaffected
- ExpenseAnalyticsService (uses TransactionRepository) - only counts EXPENSE type

## Supported Use Cases

### Credit Card Payment
```
User: "Paid 25,000 to credit card"
→ Creates TRANSFER transaction
→ Debits bank account
→ Credits (reduces) credit card outstanding
```

### Loan EMI Payment
```
User: "Paid EMI of 15,000"
→ Creates TRANSFER transaction
→ Debits bank account
→ Credits (reduces) loan outstanding
```

### Bank to Loan Transfer
```
User: "Transferred 40k from bank to loan"
→ Creates TRANSFER transaction
→ Debits bank account
→ Credits (reduces) loan outstanding
```

## Idempotency

The `financiallyApplied` flag ensures:
- Same transaction is never applied twice
- Re-running same request won't double-debit/credit

## Container Types

### Supported Source Containers
- `BANK_ACCOUNT` (default if not specified)
- `WALLET`
- `CASH`

### Supported Target Liabilities
- `CREDIT_CARD` (uses CreditCardStrategy)
- `LOAN` (uses new LoanStrategy)

Both implement `CreditSettlementStrategy.applyPayment()` to reduce outstanding amounts.

## Testing Checklist

- [ ] Bank → Credit Card payment reduces credit card outstanding
- [ ] Bank → Loan payment reduces loan outstanding
- [ ] Source bank account is debited correctly
- [ ] Idempotency: re-running same request doesn't double-apply
- [ ] TRANSFER transactions don't appear in expense reports
- [ ] ValueAdjustmentEntity audit trail is created correctly
- [ ] Follow-up questions work if details are missing

## Notes

1. User ID is currently hardcoded as `1L` - needs to be replaced with actual authentication context
2. Container resolution defaults to first matching container - could be enhanced with priority ordering
3. Follow-up handling is minimal - can be extended if needed
4. Payment date defaults to current timestamp if not specified
