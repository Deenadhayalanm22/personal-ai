# Service Layer Documentation

> **Note**: Following the domain-first refactoring, services are now organized by domain:
> - **Finance Domain**: `com.apps.deen_sa.finance.*` (expense, loan, query, account)
> - **Conversation Domain**: `com.apps.deen_sa.conversation`
> - See [ARCHITECTURE.md](ARCHITECTURE.md) and [REFACTORING_SUMMARY.md](REFACTORING_SUMMARY.md) for complete details.

## Overview
The service layer contains business logic, orchestrating interactions between repositories, LLM services, and other components.

---

## 1. ExpenseSummaryService

**Purpose**: Provide dashboard-style expense summaries and analytics

### Responsibilities
- Calculate daily expense totals
- Calculate monthly expense totals
- Aggregate expenses by category
- Retrieve recent transactions

### Key Methods

#### `getDashboardSummary(): ExpenseSummaryDto`
Returns a comprehensive dashboard summary containing:
- **todayTotal**: Total expenses for current day
- **monthTotal**: Total expenses for current month (from 1st to today)
- **categoryTotals**: Map of category → total amount
- **recentTransactions**: Top 10 most recent expenses

### Implementation Details
```java
// Date calculations
LocalDate today = LocalDate.now();
LocalDate monthStart = today.withDayOfMonth(1);

// Aggregations via repository
BigDecimal todayTotal = repo.sumAmountByDate(today);
BigDecimal monthTotal = repo.sumAmountBetweenDates(monthStart, today);
Map<String, BigDecimal> categoryTotals = repo.sumAmountGroupByCategory(monthStart, today);
List<ExpenseItemDto> recent = repo.findTop10ByOrderBySpentAtDesc();
```

### Dependencies
- `ExpenseRepository`: Data access for expense queries

### Use Cases
- Main dashboard display
- Quick financial overview
- Category spending analysis

---

## 2. ExpenseAnalyticsService

**Purpose**: Advanced expense analytics and insights (implementation not shown in scanned files)

### Expected Responsibilities
- Trend analysis
- Spending patterns
- Budget tracking
- Anomaly detection

---

## 3. LoanAnalysisService

**Purpose**: Handle loan-related queries and calculations

### Responsibilities
- Calculate EMI remaining
- Analyze loan status
- Handle multi-loan scenarios
- Generate explanations via LLM

### Key Methods

#### `handleEmiRemaining(String userText, ConversationContext ctx): SpeechResult`
Handles queries like "How many EMIs are left?"

**Flow**:
1. **Retrieve Active Loans**: Query StateContainerRepository for active loans
2. **No Loans**: Return info message
3. **Multiple Loans**: Ask user to clarify which loan
   - Store loan IDs in context metadata
   - Set context to waiting for loan selection
   - Return follow-up question
4. **Single Loan or Resolved**: Call `computeAndRespond()`

#### `computeAndRespond(StateContainerEntity loan): SpeechResult`
**Computation Logic** (NO LLM here - pure calculation):

1. **Count Paid EMIs**: Query transaction repository
   ```java
   int emiPaid = transactionRepo.countLoanEmis(loan.getId());
   ```

2. **Extract Loan Details** from `loan.details` JSONB:
   - `emiAmount` (BigDecimal)
   - `tenureMonths` (Integer)
   - `endDate` (String)

3. **Calculate EMIs Left**:
   ```java
   if (tenureMonths != null) {
       emiLeft = Math.max(tenureMonths - emiPaid, 0);
   } else {
       emiLeft = loan.currentValue.divide(emiAmount).intValue();
   }
   ```

4. **Build Summary Map**:
   ```java
   Map<String, Object> summary = {
       "loanName": loan.getName(),
       "emiPaid": emiPaid,
       "emiLeft": emiLeft,
       "emiAmount": emiAmount,
       "outstanding": loan.getCurrentValue(),
       "endDate": details.get("endDate")
   };
   ```

5. **Ask LLM for Explanation** (ONLY for natural language response):
   ```java
   String explanation = llm.explainEmiRemaining(summary);
   ```

### Design Philosophy
- **Calculation**: Pure Java, no LLM
- **Explanation**: LLM converts data to natural language
- **Separation**: Business logic separate from presentation

### Dependencies
- `StateContainerRepository`: Loan data access
- `StateChangeRepository`: EMI payment history
- `LoanQueryExplainer`: Natural language generation

---

## 4. TagNormalizationService

**Purpose**: Normalize user-generated tags to canonical forms

### Responsibilities
- Prevent tag proliferation
- Match user tags to canonical tags
- Maintain consistent taxonomy

### Expected Functionality
- Semantic matching via LLM (TagSemanticMatcher)
- Fuzzy matching for typos
- Auto-suggest canonical tags
- Sync with TagMasterEntity

### Use Cases
```java
// User enters: ["groc", "food shopping", "veggies"]
List<String> normalized = tagNormalizationService.normalizeTags(userTags);
// Returns: ["groceries", "food", "vegetables"]
```

---

## 5. ValueContainerService

**Purpose**: Manage ValueContainer CRUD and queries

### Responsibilities
- Create new containers (accounts, loans, wallets)
- Retrieve active containers for a user
- Find containers by ID
- Update container metadata

### Key Methods (Inferred)

#### `getActiveContainers(Long userId): List<StateContainerEntity>`
Returns all active containers for a user
- Filters by `ownerType=USER`, `ownerId=userId`, `status=ACTIVE`

#### `findValueContainerById(Long id): StateContainerEntity`
Retrieves a specific container by ID

#### `createContainer(AccountSetupDto dto): StateContainerEntity`
Creates a new container from account setup data

### Use Cases
- Account setup workflow
- Payment source selection
- Container balance queries

---

## 6. ValueAdjustmentService

**Purpose**: Apply financial adjustments to value containers

### Responsibilities
- Execute balance changes
- Create audit trail (StateMutationEntity)
- Handle different container types via strategies
- Enforce business rules (over-limit detection)

### Key Methods

#### `apply(StateContainerEntity container, StateMutationCommand command): void`

**Flow**:
1. **Resolve Strategy**: Get appropriate strategy for container type
   ```java
   ValueAdjustmentStrategy strategy = strategyResolver.resolve(container.getContainerType());
   ```

2. **Execute Adjustment**: Apply strategy
   ```java
   strategy.apply(container, command);
   ```

3. **Update Container**: Save modified container
   ```java
   containerRepo.save(container);
   ```

4. **Create Audit Record**: Log adjustment
   ```java
   StateMutationEntity adjustment = new StateMutationEntity();
   adjustment.setTransactionId(command.getTransactionId());
   adjustment.setContainerId(container.getId());
   adjustment.setAdjustmentType(command.getType());
   adjustment.setAmount(command.getAmount());
   adjustment.setReason(command.getReason());
   adjustment.setOccurredAt(Instant.now());
   adjustmentRepo.save(adjustment);
   ```

### Strategy Pattern
Different strategies for different container types:
- **CashStrategy**: Simple debit/credit
- **BankStrategy**: Check minimum balance
- **CreditStrategy**: Check credit limit, calculate available, detect over-limit
- **InventoryStrategy**: Quantity-based adjustments

### Dependencies
- `StateContainerRepository`: Container persistence
- `ValueAdjustmentRepository`: Audit trail
- `ValueAdjustmentStrategyResolver`: Strategy selection

### Design Notes
- Idempotent if called with same transaction ID
- Atomic operations (transaction boundaries)
- Audit trail for compliance

---

## Service Interaction Patterns

### Pattern 1: Handler → Service → Repository
```
ExpenseHandler
  → ValueContainerService.getActiveContainers()
  → StateContainerRepository.findByOwnerIdAndStatus()
```

### Pattern 2: Service → LLM → Service
```
LoanAnalysisService
  → Calculate data (pure logic)
  → LoanQueryExplainer.explainEmiRemaining() (LLM)
  → Return natural language result
```

### Pattern 3: Service → Strategy → Repository
```
ValueAdjustmentService
  → Resolve strategy based on container type
  → Execute strategy
  → Save container + audit record
```

### Pattern 4: Service → Service Composition
```
ExpenseHandler
  → TagNormalizationService.normalizeTags()
  → ValueContainerService.findById()
  → ValueAdjustmentService.apply()
```

---

## Service Design Principles

### 1. Single Responsibility
Each service has a clear, focused purpose
- ExpenseSummaryService: Only summaries
- LoanAnalysisService: Only loan logic
- TagNormalizationService: Only tag handling

### 2. Separation of Concerns
- **Calculation**: Pure Java logic
- **Explanation**: LLM for natural language
- **Persistence**: Repository layer

### 3. Composability
Services are designed to be composed
```java
// Handler composes multiple services
ExpenseHandler {
    TagNormalizationService tagService;
    ValueContainerService containerService;
    ValueAdjustmentService adjustmentService;
    ExpenseCompletenessEvaluator evaluator;
}
```

### 4. Strategy for Variation
Use Strategy pattern for type-specific behavior
- ValueAdjustmentStrategy for different containers
- Avoids if/else chains
- Easy to extend

### 5. Stateless Services
Services don't hold conversation state
- State managed in ConversationContext
- Services are @Service singletons
- Thread-safe operations

---

## Transaction Management

Services use Spring's declarative transaction management:

```java
@Service
@Transactional // Default: read-write
public class ValueAdjustmentService {
    
    @Transactional(readOnly = true)
    public StateContainerEntity findById(Long id) {
        // Read-only for performance
    }
    
    @Transactional // Read-write for modifications
    public void apply(StateContainerEntity container, StateMutationCommand cmd) {
        // Atomic: both container update and audit record
    }
}
```

---

## Error Handling

Services throw runtime exceptions for business rule violations:

```java
if (tx.getSourceContainerId() == null) {
    throw new IllegalStateException(
        "Cannot apply financial impact without source container"
    );
}
```

These are caught by:
- Handler layer (converts to SpeechResult)
- Controller advice (converts to HTTP responses)

---

## Future Service Extensions

### Planned Services
- **BudgetService**: Budget tracking and alerts
- **ReportingService**: Generate financial reports
- **NotificationService**: Alerts and reminders
- **ReconciliationService**: Match transactions with bank statements
- **ForecastingService**: Predict future expenses

### Extension Points
- New LLM explainers for different query types
- Additional strategies for new container types
- Analytics algorithms for spending insights
- Integration services for external systems

---

## Service Testing Strategy

### Unit Tests
- Mock repositories and LLM services
- Test business logic in isolation
- Verify calculations

### Integration Tests
- Test service interactions
- Verify database operations
- Test strategy resolution

### Example Test Structure
```java
@SpringBootTest
class LoanAnalysisServiceTest {
    
    @MockBean
    private StateChangeRepository transactionRepo;
    
    @MockBean
    private StateContainerRepository containerRepo;
    
    @Autowired
    private LoanAnalysisService service;
    
    @Test
    void shouldCalculateEmiRemaining() {
        // Given: Mock loan and transaction data
        // When: Call handleEmiRemaining()
        // Then: Verify EMI calculation
    }
}
```

---

## Performance Considerations

### Caching Opportunities
- Tag normalization results
- Container lookups
- Category summaries

### Query Optimization
- Use repository projections for summaries
- Batch operations where possible
- Index critical query paths

### LLM Call Minimization
- Cache prompt templates
- Batch similar requests
- Use cheaper models for simple tasks
