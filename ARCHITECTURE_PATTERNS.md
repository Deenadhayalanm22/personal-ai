# Architecture Patterns & Key Decisions

## Overview
This document outlines the architectural patterns, design decisions, and development practices used throughout the Personal AI Finance Application.

---

## 1. Architectural Patterns

### 1.1 Layered Architecture

**Structure**:
```
Presentation Layer (Controllers)
    ↓
Orchestration Layer (Orchestrators)
    ↓
Handler Layer (Intent Handlers)
    ↓
Service Layer (Business Logic)
    ↓
Repository Layer (Data Access)
    ↓
Database Layer (PostgreSQL)
```

**Benefits**:
- Clear separation of concerns
- Testability at each layer
- Independent scaling possibilities
- Easy to understand and maintain

**Implementation**:
- Each layer has single responsibility
- Dependencies flow downward only
- No circular dependencies
- DTOs for layer-to-layer communication

---

### 1.2 Strategy Pattern

**Used In**: ValueAdjustmentService

**Purpose**: Handle different adjustment logic for different container types

**Implementation**:
```java
interface ValueAdjustmentStrategy {
    void apply(ValueContainerEntity container, AdjustmentCommand cmd);
}

class CashStrategy implements ValueAdjustmentStrategy {
    // Simple debit/credit
}

class CreditStrategy implements ValueAdjustmentStrategy {
    // Credit limit checks, over-limit detection
}

class BankStrategy implements ValueAdjustmentStrategy {
    // Minimum balance checks
}

class InventoryStrategy implements ValueAdjustmentStrategy {
    // Quantity-based adjustments
}
```

**Resolver**:
```java
@Component
class ValueAdjustmentStrategyResolver {
    public ValueAdjustmentStrategy resolve(String containerType) {
        return strategies.get(containerType);
    }
}
```

**Benefits**:
- Avoids if/else chains
- Easy to add new container types
- Type-specific behavior encapsulated
- Open/Closed Principle compliance

---

### 1.3 Template Method Pattern

**Used In**: BaseLLMExtractor

**Purpose**: Standardize LLM interaction while allowing customization

**Implementation**:
```java
abstract class BaseLLMExtractor {
    // Template methods
    protected String callLLM(String system, String user) { ... }
    protected <T> T callAndParse(String system, String user, Class<T> type) { ... }
}

class IntentClassifier extends BaseLLMExtractor {
    public IntentResult classify(String text) {
        String prompt = promptLoader.load("llm/intent/classify.md");
        return callAndParse(prompt, text, IntentResult.class);
    }
}
```

**Benefits**:
- Consistent LLM interaction
- Centralized error handling
- Easy to swap LLM provider
- DRY principle

---

### 1.4 Repository Pattern

**Used In**: All data access

**Purpose**: Abstract database operations

**Implementation**:
```java
interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {
    @Query("SELECT COUNT(t) FROM TransactionEntity t WHERE ...")
    int countLoanEmis(Long loanId);
}
```

**Benefits**:
- Database agnostic (can swap PostgreSQL for MySQL)
- Testable with mocks
- Query optimization in one place
- Clear data access layer

---

### 1.5 Factory Pattern

**Used In**: AdjustmentCommandFactory

**Purpose**: Create adjustment commands from transactions

**Implementation**:
```java
@Component
class AdjustmentCommandFactory {
    public AdjustmentCommand forExpense(TransactionEntity tx) {
        return AdjustmentCommand.builder()
            .transactionId(tx.getId())
            .type(AdjustmentTypeEnum.DEBIT)
            .amount(tx.getAmount())
            .reason("EXPENSE")
            .build();
    }
    
    public AdjustmentCommand forIncome(TransactionEntity tx) { ... }
    public AdjustmentCommand forTransfer(TransactionEntity tx) { ... }
}
```

**Benefits**:
- Centralized command creation logic
- Consistent command structure
- Easy to add new command types

---

### 1.6 Chain of Responsibility (Implicit)

**Used In**: SpeechOrchestrator → Handler selection

**Purpose**: Route requests to appropriate handler

**Implementation**:
```java
@Service
class SpeechOrchestrator {
    private final Map<String, SpeechHandler> handlers;
    
    public SpeechResult process(String text, ConversationContext ctx) {
        // 1. Classify intent
        IntentResult intent = intentClassifier.classify(text);
        
        // 2. Find handler
        SpeechHandler handler = handlers.get(intent.intent());
        
        // 3. Delegate
        return handler.handleSpeech(text, ctx);
    }
}
```

**Handlers**:
- ExpenseHandler (EXPENSE intent)
- QueryHandler (QUERY intent)
- AccountSetupHandler (ACCOUNT_SETUP intent)

**Benefits**:
- Intent-specific processing
- Easy to add new intents
- Decoupled from orchestrator

---

### 1.7 State Pattern (Conversation Management)

**Used In**: ConversationContext + Handlers

**Purpose**: Manage multi-turn conversations

**States**:
- **Initial**: No active conversation
- **Follow-up**: Waiting for missing field
- **Completed**: Conversation finished

**Implementation**:
```java
class ConversationContext {
    private String activeIntent;      // Current intent being processed
    private String waitingForField;   // Field waiting for user input
    private Object partialObject;     // Partially filled DTO
    private Long activeTransactionId; // Transaction being enriched
    private Map<String, Object> metadata; // Additional context
    
    public boolean isInFollowup() {
        return waitingForField != null;
    }
    
    public void reset() {
        activeIntent = null;
        waitingForField = null;
        partialObject = null;
        activeTransactionId = null;
    }
}
```

**Flow**:
```
User: "Spent 500"
  → Extract: amount=500
  → State: waiting for "category"
  → Response: "What category is this expense for?"

User: "Food"
  → Extract: category="food"
  → Merge into partial object
  → State: waiting for "merchant"
  → Response: "Where did you make this purchase?"

User: "Swiggy"
  → Extract: merchant="Swiggy"
  → Complete transaction
  → State: reset
```

---

## 2. Design Decisions

### 2.1 LLM Separation of Concerns

**Decision**: Separate calculation from explanation

**Rationale**:
- LLMs are non-deterministic
- Business logic must be testable
- Cost optimization

**Implementation**:
```java
// ❌ BAD: LLM does calculation
String response = llm.call("Calculate how many EMIs are left for this loan...");

// ✅ GOOD: Java does calculation, LLM explains
int emiLeft = calculateEmiLeft(loan);  // Pure Java
String explanation = llm.explain(emiLeft, loan);  // Natural language
```

**Example** (LoanAnalysisService):
```java
// Calculation: Pure Java (testable, deterministic)
int emiPaid = transactionRepo.countLoanEmis(loan.getId());
int emiLeft = tenureMonths - emiPaid;

// Explanation: LLM (natural language generation)
String response = llmExplainer.explainEmiRemaining(summary);
```

---

### 2.2 Progressive Data Enrichment

**Decision**: Save incomplete transactions and enrich incrementally

**Rationale**:
- User experience (don't force all details upfront)
- Natural conversation flow
- Capture data even if incomplete

**Implementation**:
```java
enum CompletenessLevelEnum {
    MINIMAL,      // amount only
    OPERATIONAL,  // + category, merchant
    FINANCIAL     // + source account (can apply financial impact)
}
```

**Flags**:
- `needsEnrichment`: True if missing critical fields
- `financiallyApplied`: True if balance impact applied

**Flow**:
```
Input: "Spent 500"
  → Save as MINIMAL (amount=500)
  → needsEnrichment=true
  → financiallyApplied=false
  → Ask follow-up for category

Input: "Food"
  → Update to OPERATIONAL (category=food)
  → needsEnrichment=true (still missing source)
  → Ask follow-up for source

Input: "Cash"
  → Update to FINANCIAL (sourceAccount=cash)
  → Apply financial impact
  → needsEnrichment=false
  → financiallyApplied=true
```

---

### 2.3 Idempotent Financial Application

**Decision**: Prevent duplicate balance adjustments

**Problem**: If handler runs twice, balance could be debited twice

**Solution**:
```java
if (!transaction.isFinanciallyApplied()) {
    applyFinancialImpact(transaction);
    transaction.setFinanciallyApplied(true);
    repo.save(transaction);
}
```

**Safeguards**:
- Database flag (`financially_applied`)
- Check before apply
- Audit trail (ValueAdjustmentEntity)

---

### 2.4 Value Container Abstraction

**Decision**: Single entity for all value-holding instruments

**Alternatives Considered**:
1. Separate entities (BankAccount, CreditCard, Loan, Inventory)
2. Inheritance hierarchy
3. Single polymorphic entity ✅ CHOSEN

**Rationale**:
- Reduced schema complexity
- Common query patterns
- Easy to add new types
- Flexible metadata (JSONB details field)

**Trade-offs**:
- Type-specific validation in application layer
- Some fields nullable (not all types need all fields)

**Supported Types**:
- CASH (physical cash)
- BANK (bank account)
- CREDIT (credit card, loan)
- INVENTORY (stock, goods)
- PAYABLE (accounts payable)
- RECEIVABLE (accounts receivable)

---

### 2.5 JSONB for Flexibility

**Decision**: Use PostgreSQL JSONB for extensible metadata

**Used In**:
- `TransactionEntity.details`
- `ValueContainerEntity.details`
- `ExpenseEntity.details`

**Benefits**:
- Schema evolution without migrations
- Type-specific fields (e.g., loan EMI details)
- Easy to add new attributes
- Indexed and queryable

**Example**:
```java
// Loan container details
{
    "emiAmount": 15000,
    "tenureMonths": 24,
    "endDate": "2027-12-31",
    "interestRate": 12.5
}

// Expense details
{
    "vehicleNumber": "KA01AB1234",
    "location": "Bangalore",
    "notes": "Business trip"
}
```

**Trade-offs**:
- Less type safety than dedicated columns
- Harder to enforce constraints
- Requires careful documentation

---

### 2.6 Dual Entity Models

**Decision**: Maintain both ExpenseEntity and TransactionEntity

**Rationale**:
- **ExpenseEntity**: Simple, legacy, UI-focused
- **TransactionEntity**: Comprehensive, new, business-focused

**Migration Strategy**:
- New features use TransactionEntity
- Legacy features use ExpenseEntity
- Gradual migration over time
- No breaking changes

**Benefits**:
- Backward compatibility
- Smooth transition
- Feature velocity (new features don't wait for migration)

---

### 2.7 Prompt Engineering Best Practices

**Decision**: Store prompts as markdown files, not hardcoded strings

**Benefits**:
- Version control for prompts
- Easy to edit and test
- Non-developers can improve prompts
- Clear separation: code vs. instructions

**Structure**:
```
src/main/resources/llm/
├── common/global_rules.md       # Shared across all LLM calls
├── expense/extract.md            # Expense-specific
└── intent/classify.md            # Intent-specific
```

**Loading**:
```java
String prompt = promptLoader.combine(
    "llm/common/global_rules.md",
    "llm/expense/rules.md",
    "llm/expense/extract.md"
);
```

---

### 2.8 Low Temperature for Consistency

**Decision**: Use temperature=0.1 for all LLM calls

**Rationale**:
- Deterministic outputs
- Consistent classification
- Minimal hallucination
- Faster inference

**Use Cases**:
- Intent classification (must be consistent)
- Expense extraction (accuracy critical)
- Query parsing (deterministic results)

**Exception**: Could use higher temperature for creative tasks (not in this app)

---

## 3. Development Practices

### 3.1 Lombok for Boilerplate Reduction

**Usage**:
- `@Getter`, `@Setter` for entities
- `@RequiredArgsConstructor` for services
- `@Builder` for DTOs
- `@NoArgsConstructor`, `@AllArgsConstructor` for JPA entities

**Benefits**:
- Less code to maintain
- Consistent getters/setters
- Builder pattern for complex objects

---

### 3.2 Record Classes for DTOs

**Usage**:
```java
record IntentResult(String intent, double confidence) {}
record QueryResult(String queryType, TimeRange timeRange, Map<String, Object> filters) {}
```

**Benefits**:
- Immutable by default
- Concise syntax
- Auto-generated equals/hashCode/toString
- Perfect for data transfer

---

### 3.3 Spring Dependency Injection

**Pattern**: Constructor injection (recommended)

```java
@Service
@RequiredArgsConstructor
public class ExpenseHandler implements SpeechHandler {
    private final ExpenseClassifier llm;
    private final TransactionRepository repo;
    private final TagNormalizationService tagService;
    // All injected via constructor
}
```

**Benefits**:
- Immutable dependencies
- Easy to test (pass mocks via constructor)
- Clear dependencies

---

### 3.4 Interface-Based Design

**Pattern**: Program to interfaces

```java
interface SpeechHandler {
    String intentType();
    SpeechResult handleSpeech(String text, ConversationContext ctx);
    SpeechResult handleFollowup(String answer, ConversationContext ctx);
}

// Implementations
class ExpenseHandler implements SpeechHandler { ... }
class QueryHandler implements SpeechHandler { ... }
```

**Benefits**:
- Polymorphism
- Easy to mock for testing
- Flexible implementations

---

### 3.5 Explicit Exception Handling

**Pattern**: Fail fast with descriptive messages

```java
if (tx.getSourceContainerId() == null) {
    throw new IllegalStateException(
        "Cannot apply financial impact without source container"
    );
}
```

**Benefits**:
- Clear error messages
- Easier debugging
- No silent failures

---

### 3.6 Immutable DTOs

**Pattern**: Use records or final fields

```java
@Builder
public record ExpenseDto(
    BigDecimal amount,
    String category,
    String merchantName,
    List<String> tags
) {}
```

**Benefits**:
- Thread-safe
- No accidental mutations
- Clear data flow

---

## 4. Key Architectural Decisions

### 4.1 WhatsApp as Primary Interface

**Decision**: Build for conversational interface first

**Implications**:
- Natural language processing critical
- Multi-turn conversation support
- Async message handling
- Simple text responses

---

### 4.2 PostgreSQL with JSONB

**Decision**: Relational + document features

**Benefits**:
- Strong consistency (ACID)
- Flexible schema (JSONB)
- Rich query capabilities
- Proven scalability

---

### 4.3 Spring Boot Framework

**Decision**: Use Spring Boot ecosystem

**Benefits**:
- Production-ready features (actuator, metrics)
- Auto-configuration
- Large ecosystem
- Easy deployment

---

### 4.4 OpenAI GPT-4.1 Mini

**Decision**: Use OpenAI for LLM tasks

**Rationale**:
- Mature API
- Good performance/cost ratio
- Reliable uptime
- Easy integration

**Alternative Considered**: Open-source models (higher hosting cost, lower API cost)

---

### 4.5 Multi-Tenancy Ready

**Decision**: Include userId/businessId from the start

**Implementation**:
```java
@Column(name = "user_id", nullable = false)
private String userId;

@Column(name = "business_id")
private String businessId;
```

**Benefits**:
- Future-proof
- Easy to add multi-tenant features
- Data isolation built-in

---

## 5. Performance Considerations

### 5.1 Caching Strategy

**Implemented**:
- Prompt caching (PromptLoader)
- LLM result caching (future)

**Opportunities**:
- Tag normalization results
- Container lookups
- Summary calculations

---

### 5.2 Database Indexing

**Critical Indexes**:
```sql
CREATE INDEX idx_transaction_user_time ON transaction_rec(user_id, timestamp);
CREATE INDEX idx_container_owner ON value_container(owner_id, container_type, status);
CREATE INDEX idx_adjustment_tx ON value_adjustments(transaction_id);
CREATE INDEX idx_adjustment_container ON value_adjustments(container_id, occurred_at);
```

---

### 5.3 LLM Call Optimization

**Strategies**:
- Use smallest viable model (GPT-4.1 Mini)
- Low temperature (faster inference)
- Concise prompts
- Batch operations (future)

---

## 6. Security Considerations

### 6.1 Input Validation

**Pattern**: Validate at API boundary

```java
@NotNull
@Positive
private BigDecimal amount;
```

---

### 6.2 API Key Management

**Pattern**: Environment variables

```yaml
openai:
  api-key: ${OPENAI_API_KEY}
```

Never commit secrets!

---

### 6.3 SQL Injection Prevention

**Pattern**: Parameterized queries (JPA/JPQL)

```java
@Query("SELECT t FROM TransactionEntity t WHERE t.userId = :userId")
List<TransactionEntity> findByUserId(@Param("userId") String userId);
```

---

## 7. Testing Strategy

### 7.1 Unit Tests
- Mock external dependencies
- Test business logic in isolation

### 7.2 Integration Tests
- Test database operations
- Test LLM integration
- Test handler workflows

### 7.3 Prompt Testing
Use `test-prompts.yml` for regression

---

## 8. Future Optimization Opportunities

1. **Event Sourcing**: For full audit trail and replay
2. **CQRS**: Separate read/write models for analytics
3. **Async Processing**: For bulk imports and reports
4. **Embedding Search**: For semantic tag/transaction search
5. **Graph Database**: For relationship analysis (spending patterns)
6. **Real-time Notifications**: Via WebSocket
7. **ML Models**: Custom models for classification (reduce LLM cost)

---

## 9. Lessons Learned

### What Works Well
- Layered architecture with clear boundaries
- LLM for NL understanding, Java for calculations
- Progressive enrichment
- JSONB for flexibility

### Areas for Improvement
- More comprehensive error handling
- Better caching strategy
- Performance monitoring
- Test coverage

### Key Takeaways
- Keep business logic out of LLM
- Design for conversation (not forms)
- Embrace incompleteness (progressive enhancement)
- Audit everything (ValueAdjustmentEntity)
