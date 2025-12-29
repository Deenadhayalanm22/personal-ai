# Entity Layer Documentation

> **Note**: Following the domain-first refactoring, entities are now organized by domain:
> - **Core (Shared Kernel)**: `com.apps.deen_sa.core.transaction` and `com.apps.deen_sa.core.value`
> - **Finance Domain**: `com.apps.deen_sa.finance.expense`
> - See [ARCHITECTURE.md](ARCHITECTURE.md) and [REFACTORING_SUMMARY.md](REFACTORING_SUMMARY.md) for complete details.

## Overview
The entity layer represents the domain model and database schema using JPA annotations.

## Entity Relationships

```
┌─────────────────────┐
│  TransactionEntity  │◄────────┐
│  (transaction_rec)  │         │
└─────────────────────┘         │
         │                      │
         │ sourceContainerId    │ transactionId
         │ targetContainerId    │
         ↓                      │
┌─────────────────────┐         │
│ ValueContainerEntity│         │
│  (value_container)  │         │
└─────────────────────┘         │
         ↑                      │
         │ containerId          │
         │                      │
┌─────────────────────┐         │
│ValueAdjustmentEntity│─────────┘
│ (value_adjustments) │
└─────────────────────┘

┌─────────────────────┐
│   ExpenseEntity     │  (Legacy model)
│     (expense)       │
└─────────────────────┘

┌─────────────────────┐
│  TagMasterEntity    │  (Canonical tags)
│    (tag_master)     │
└─────────────────────┘
```

---

## 1. TransactionEntity

**Table**: `transaction_rec`  
**Purpose**: Core transaction recording with flexible design supporting multiple transaction types.

### Key Fields

#### Identity & Tenancy
- `id` (Long, PK, Auto-generated): Unique transaction identifier
- `userId` (String, NOT NULL): User/owner of the transaction
- `businessId` (String, nullable): For business transactions

#### Transaction Type
- `transactionType` (TransactionTypeEnum, NOT NULL): Type of transaction
  - Values defined in `utils/TransactionTypeEnum.java`
  - Examples: EXPENSE, INCOME, TRANSFER, INVESTMENT

#### Monetary Fields
- `amount` (BigDecimal, precision=15, scale=2, NOT NULL): Transaction amount
- `quantity` (BigDecimal, precision=15, scale=4): For quantity-based transactions
- `unit` (String, length=20): Unit of measurement (pcs, kg, litre, N/A)

#### Classification
- `category` (String, length=100): High-level category
- `subcategory` (String, length=100): Detailed classification
- `mainEntity` (String, length=150): Merchant/vendor/employee/client

#### Temporal
- `timestamp` (Instant, NOT NULL, field=tx_time): When transaction occurred
- `createdAt` (Instant, NOT NULL, immutable, @CreatedDate): Record creation
- `updatedAt` (Instant, NOT NULL, @UpdateTimestamp): Last update

#### Raw Data & Metadata
- `rawText` (TEXT): Original user input
- `details` (Map<String, Object>, JSONB): Flexible metadata storage
- `tags` (List<String>, JSONB): Transaction tags

#### Container Links
- `sourceContainerId` (Long): Where money/value came from
- `targetContainerId` (Long): Where money/value went to

#### State Management
- `completenessLevel` (CompletenessLevelEnum, NOT NULL): Data quality indicator
  - MINIMAL: Basic info only
  - OPERATIONAL: Has category/merchant
  - FINANCIAL: Complete with container mapping
- `financiallyApplied` (boolean, default=false): Whether financial impact has been applied
- `needsEnrichment` (boolean, default=false): Requires additional data

### Design Notes
- Uses `@EntityListeners(AuditingEntityListener.class)` for automatic timestamp management
- JSONB columns provide schema flexibility
- Completeness tracking enables progressive enhancement
- `financiallyApplied` flag prevents duplicate financial impacts
- Multi-tenant ready with userId and businessId

---

## 2. ValueContainerEntity

**Table**: `value_container`  
**Purpose**: Universal container for all value-holding entities (accounts, loans, inventory, receivables, etc.)

### Key Fields

#### Identity & Ownership
- `id` (Long, PK, Auto-generated)
- `ownerType` (String, length=30, NOT NULL): Owner category
  - Values: USER, BUSINESS, EMPLOYEE, CUSTOMER, VENDOR
- `ownerId` (Long, NOT NULL): Reference to owner entity

#### Classification
- `containerType` (String, length=30, NOT NULL): Type of container
  - Values: CASH, BANK, CREDIT, INVENTORY, PAYABLE, RECEIVABLE
- `name` (String, NOT NULL): Human-readable name
- `status` (String, length=20, NOT NULL): Container status
  - Values: ACTIVE, CLOSED, SUSPENDED

#### Quantitative State
- `currency` (String, length=10): Currency code (INR, USD) or null for non-monetary
- `currentValue` (BigDecimal, precision=19, scale=4): Current balance/quantity
- `availableValue` (BigDecimal, precision=19, scale=4): Available for use
- `unit` (String, length=20): Unit of measurement (INR, kg, pcs, litres)

#### Limits & Constraints
- `capacityLimit` (BigDecimal, precision=19, scale=4): Maximum capacity (e.g., credit limit)
- `minThreshold` (BigDecimal, precision=19, scale=4): Minimum threshold alerts
- `priorityOrder` (Integer): Priority for automatic selection

#### Lifecycle
- `openedAt` (Instant): When container was opened
- `closedAt` (Instant): When container was closed
- `lastActivityAt` (Instant): Last transaction timestamp

#### External References
- `externalRefType` (String, length=30): External system type
  - Values: BANK, WALLET, INTERNAL, SUPPLIER
- `externalRefId` (String): Masked/safe external reference

#### Over-Limit Tracking
- `overLimit` (Boolean, default=false): Whether usage exceeds capacity
- `overLimitAmount` (BigDecimal, precision=19, scale=4): Amount exceeded

#### Metadata & Audit
- `details` (Map<String, Object>, JSONB): Flexible extensions (e.g., emiAmount, tenureMonths for loans)
- `createdAt` (Instant, @CreationTimestamp)
- `updatedAt` (Instant, @UpdateTimestamp)

### Use Cases

#### Bank Account
```
containerType: BANK
ownerType: USER
currentValue: 50000.00
availableValue: 50000.00
unit: INR
currency: INR
```

#### Credit Card
```
containerType: CREDIT
capacityLimit: 100000.00
currentValue: 25000.00 (used amount)
availableValue: 75000.00
overLimit: false
```

#### Loan
```
containerType: CREDIT (or custom LOAN)
currentValue: 320000.00 (outstanding)
details: {
  "emiAmount": 15000,
  "tenureMonths": 24,
  "endDate": "2027-12-31"
}
```

#### Inventory Item
```
containerType: INVENTORY
currentValue: 150.00
unit: kg
currency: null
```

### Design Notes
- Unified model for diverse financial instruments
- Supports both monetary and quantity-based containers
- Over-limit detection for credit instruments
- Flexible details field for container-specific attributes
- Priority ordering for automatic payment source selection

---

## 3. ValueAdjustmentEntity

**Table**: `value_adjustments`  
**Purpose**: Audit trail for all changes to value containers

### Key Fields
- `id` (Long, PK, Auto-generated)
- `transactionId` (Long): Link to the transaction that caused this adjustment
- `containerId` (Long): Which container was adjusted
- `adjustmentType` (AdjustmentTypeEnum): DEBIT or CREDIT
- `amount` (BigDecimal): Adjustment amount
- `reason` (String): Why adjustment occurred (EXPENSE, REVERSAL, EDIT)
- `occurredAt` (Instant): When adjustment occurred
- `createdAt` (Instant): When record was created

### Design Notes
- Immutable audit log
- Every container change creates an adjustment record
- Enables reconstruction of container history
- Supports reversal and correction workflows

---

## 4. ExpenseEntity

**Table**: `expense`  
**Purpose**: Legacy/simplified expense tracking model

### Key Fields

#### Identity & References
- `id` (Long, PK, Auto-generated)
- `userId` (Long, NOT NULL): User who made the expense
- `accountId` (Long): Associated account

#### Core Finance
- `amount` (BigDecimal, precision=15, scale=2, NOT NULL)
- `currency` (String, length=3, default="INR", NOT NULL)
- `category` (String, length=50, NOT NULL): Predefined categories (Transport, Food, Health)
- `subcategory` (String, length=100): Flexible user-generated
- `merchantName` (String, length=200): Where money was spent
- `paymentMethod` (String, length=50): How payment was made

#### Temporal
- `spentAt` (OffsetDateTime, NOT NULL): When expense occurred
- `recordedAt` (OffsetDateTime, default=now, NOT NULL): When recorded in system

#### Data & Metadata
- `rawText` (TEXT): Original input
- `details` (Map<String, Object>, JSONB): Vehicle, location, custom fields, notes
- `tags` (List<String>): User tags (stored in `expense_tags` table)

#### Validation
- `isValid` (Boolean, default=true): Whether expense is valid
- `validationReason` (TEXT): Why invalid

#### Source Tracking
- `source` (String, length=50): Origin of expense (voice, manual, import, llm)
- `sourceRef` (String, length=255): Reference to original source

#### Audit
- `createdAt` (OffsetDateTime, default=now, NOT NULL)
- `updatedAt` (OffsetDateTime)

### Design Notes
- Simpler model compared to TransactionEntity
- Uses `@ElementCollection` for tags (separate table)
- OffsetDateTime for timezone-aware timestamps
- JSONB for flexible metadata
- Validation fields for data quality

---

## 5. TagMasterEntity

**Table**: `tag_master`  
**Purpose**: Canonical tag repository for normalization

### Key Fields
- `id` (Long, PK, Auto-generated)
- `canonicalTag` (String): Standardized tag name
- `status` (String): Tag status (active, deprecated, etc.)

### Design Notes
- Minimal structure
- Used by TagNormalizationService
- Prevents tag proliferation
- Semantic matching via LLM

---

## Enum Definitions

### TransactionTypeEnum
Located in `utils/TransactionTypeEnum.java`
- Defines valid transaction types
- Examples: EXPENSE, INCOME, TRANSFER, INVESTMENT

### CompletenessLevelEnum
Located in `utils/CompletenessLevelEnum.java`
```java
MINIMAL      // Basic data only
OPERATIONAL  // Has category and merchant
FINANCIAL    // Complete with container mapping
```

### AdjustmentTypeEnum
Located in `utils/AdjustmentTypeEnum.java`
```java
DEBIT   // Decreases container value
CREDIT  // Increases container value
```

---

## Database Considerations

### JSON Columns
- Uses PostgreSQL JSONB for flexible metadata
- Indexed for performance where needed
- Allows schema evolution without migrations

### Precision
- Monetary amounts: DECIMAL(15, 2) - supports up to 999,999,999,999.99
- Quantities: DECIMAL(19, 4) - high precision for inventory

### Timestamps
- Uses `Instant` for UTC timestamps (no timezone issues)
- Uses `OffsetDateTime` in legacy ExpenseEntity (timezone-aware)

### Indexes
Recommended indexes (not shown in entity code):
- `transaction_rec(user_id, timestamp)`
- `value_container(owner_id, container_type, status)`
- `value_adjustments(transaction_id)`
- `value_adjustments(container_id, occurred_at)`

---

## Entity Lifecycle

### Transaction Flow
1. **Create**: TransactionEntity created from user input
2. **Evaluate**: Completeness level determined
3. **Save**: Entity persisted with appropriate flags
4. **Resolve**: Container mapping attempted (if data available)
5. **Apply**: Financial impact applied (creates ValueAdjustmentEntity)
6. **Update**: Mark as `financiallyApplied=true`
7. **Enrich**: Additional data added via follow-up (if `needsEnrichment=true`)

### Container Flow
1. **Setup**: ValueContainerEntity created via AccountSetupHandler
2. **Link**: Transactions link via sourceContainerId/targetContainerId
3. **Adjust**: ValueAdjustmentEntity records created on each transaction
4. **Update**: currentValue and availableValue updated
5. **Monitor**: overLimit checked and set if capacity exceeded

---

## Migration Path

The system supports dual models:
- **ExpenseEntity**: Simple, legacy, used by ExpenseSummaryService
- **TransactionEntity**: New, comprehensive, future-focused

This allows gradual migration while maintaining backward compatibility.
