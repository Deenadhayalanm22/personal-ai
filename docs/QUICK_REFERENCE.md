# Quick Reference Guide

> **Note**: The project has been refactored to follow Domain-Driven Design (DDD).
> See [ARCHITECTURE.md](ARCHITECTURE.md) and [REFACTORING_SUMMARY.md](REFACTORING_SUMMARY.md) for the new package structure.

## Overview
This is a quick reference for understanding and working with the Personal AI Finance Application. For detailed documentation, see the other .md files.

---

## Project Files Navigation

| Document | Purpose |
|----------|---------|
| `PROJECT_OVERVIEW.md` | High-level architecture, technology stack, workflows |
| `ENTITIES.md` | Database schema, entity relationships, data models |
| `SERVICES.md` | Business logic, service layer documentation |
| `LLM_INTEGRATION.md` | AI/LLM integration, prompts, classifiers |
| `ARCHITECTURE_PATTERNS.md` | Design patterns, key decisions, best practices |
| `QUICK_REFERENCE.md` | This file - quick lookup guide |

---

## Common Use Cases

### 1. Adding a New Intent Type

**Files to Modify**:
1. Create prompt: `src/main/resources/llm/intent/classify.md`
2. Update IntentClassifier output schema
3. Create handler: `com.apps.deen_sa.handler/NewIntentHandler.java`
4. Implement `SpeechHandler` interface

**Example**:
```java
@Service
public class InvestmentHandler implements SpeechHandler {
    @Override
    public String intentType() { return "INVESTMENT"; }
    
    @Override
    public SpeechResult handleSpeech(String text, ConversationContext ctx) {
        // Handle investment recording
    }
    
    @Override
    public SpeechResult handleFollowup(String answer, ConversationContext ctx) {
        // Handle follow-up questions
    }
}
```

---

### 2. Adding a New Container Type

**Files to Modify**:
1. Update `StateContainerEntity` (no schema change needed - uses containerType string)
2. Create new strategy: `com.apps.deen_sa.strategy.impl/NewContainerStrategy.java`
3. Register strategy in `StateMutationStrategyResolver`

**Example**:
```java
@Component
public class WalletStrategy implements StateMutationStrategy {
    @Override
    public void apply(StateContainerEntity container, StateMutationCommand cmd) {
        if (cmd.getType() == DEBIT) {
            container.setCurrentValue(
                container.getCurrentValue().subtract(cmd.getAmount())
            );
        }
        // Add wallet-specific logic
    }
}
```

---

### 3. Adding a New LLM Classifier

**Steps**:
1. Create prompt files in `src/main/resources/llm/your_task/`
2. Create DTO for response
3. Extend `BaseLLMExtractor`
4. Use `PromptLoader` to load prompts
5. Call `callAndParse()` with response DTO class

**Example**:
```java
@Component
public class BudgetClassifier extends BaseLLMExtractor {
    
    private final PromptLoader promptLoader;
    
    public BudgetClassifier(OpenAIClient client, PromptLoader promptLoader) {
        super(client);
        this.promptLoader = promptLoader;
    }
    
    public BudgetDto extractBudget(String userText) {
        String systemPrompt = promptLoader.load("llm/budget/extract.md");
        String userPrompt = String.format("Extract budget from: %s", userText);
        return callAndParse(systemPrompt, userPrompt, BudgetDto.class);
    }
}
```

---

### 4. Querying Transactions

**Common Patterns**:

```java
// By user and date range
List<StateChangeEntity> txs = repo.findByUserIdAndTimestampBetween(
    userId, 
    startTime, 
    endTime
);

// By category
List<StateChangeEntity> txs = repo.findByUserIdAndCategory(
    userId, 
    "food"
);

// Aggregate sum
BigDecimal total = repo.sumAmountByUserIdAndTimestampBetween(
    userId, 
    startTime, 
    endTime
);

// Count
Long count = repo.countByUserIdAndCategory(userId, "transport");
```

---

### 5. Creating a New API Endpoint

**Example**:
```java
@RestController
@RequestMapping("/api/budget")
public class BudgetController {
    
    private final BudgetService budgetService;
    
    @GetMapping("/summary")
    public ResponseEntity<BudgetSummaryDto> getSummary(
        @RequestParam Long userId
    ) {
        BudgetSummaryDto summary = budgetService.getSummary(userId);
        return ResponseEntity.ok(summary);
    }
    
    @PostMapping("/create")
    public ResponseEntity<BudgetEntity> createBudget(
        @RequestBody BudgetDto dto
    ) {
        BudgetEntity budget = budgetService.create(dto);
        return ResponseEntity.ok(budget);
    }
}
```

---

## Key Code Patterns

### Pattern 1: Service with Repository

```java
@Service
@RequiredArgsConstructor
public class MyService {
    
    private final MyRepository repo;
    
    @Transactional(readOnly = true)
    public MyEntity findById(Long id) {
        return repo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Not found: " + id));
    }
    
    @Transactional
    public MyEntity save(MyDto dto) {
        MyEntity entity = mapper.toEntity(dto);
        return repo.save(entity);
    }
}
```

---

### Pattern 2: LLM Extraction

```java
@Component
public class MyExtractor extends BaseLLMExtractor {
    
    private final PromptLoader promptLoader;
    
    public MyDto extract(String userText) {
        String prompt = promptLoader.combine(
            "llm/common/global_rules.md",
            "llm/my_task/extract.md"
        );
        
        String userPrompt = String.format(prompt, userText);
        
        return callAndParse(
            "llm/my_task/system.md",
            userPrompt,
            MyDto.class
        );
    }
}
```

---

### Pattern 3: Handler with Follow-up

```java
@Service
@RequiredArgsConstructor
public class MyHandler implements SpeechHandler {
    
    @Override
    public SpeechResult handleSpeech(String text, ConversationContext ctx) {
        MyDto dto = extractor.extract(text);
        
        if (dto.isIncomplete()) {
            ctx.setActiveIntent(intentType());
            ctx.setWaitingForField("missingField");
            ctx.setPartialObject(dto);
            
            return SpeechResult.followup(
                "What is the missing field?",
                List.of("missingField"),
                dto
            );
        }
        
        MyEntity saved = service.save(dto);
        ctx.reset();
        return SpeechResult.saved(saved);
    }
    
    @Override
    public SpeechResult handleFollowup(String answer, ConversationContext ctx) {
        MyDto partial = (MyDto) ctx.getPartialObject();
        MyDto updated = extractor.extractField(partial, answer);
        
        merger.merge(partial, updated);
        
        MyEntity saved = service.save(partial);
        ctx.reset();
        return SpeechResult.saved(saved);
    }
}
```

---

## Database Schema Quick Reference

### Key Tables

```sql
-- State Changes (Transactions)
state_change (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(30) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    source_container_id BIGINT,
    target_container_id BIGINT,
    details JSONB,
    tags JSONB,
    completeness_level VARCHAR(20) NOT NULL,
    financially_applied BOOLEAN DEFAULT FALSE,
    needs_enrichment BOOLEAN DEFAULT FALSE
);

-- State Containers
state_container (
    id BIGSERIAL PRIMARY KEY,
    owner_type VARCHAR(30) NOT NULL,
    owner_id BIGINT NOT NULL,
    container_type VARCHAR(30) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_value DECIMAL(19,4),
    available_value DECIMAL(19,4),
    capacity_limit DECIMAL(19,4),
    details JSONB,
    over_limit BOOLEAN DEFAULT FALSE
);

-- State Mutations (Audit Trail)
state_mutation (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT,
    container_id BIGINT,
    adjustment_type VARCHAR(10), -- DEBIT/CREDIT
    amount DECIMAL(19,4),
    reason VARCHAR(50),
    occurred_at TIMESTAMP,
    created_at TIMESTAMP
);

-- Legacy Expenses
expense (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    category VARCHAR(50) NOT NULL,
    merchant_name VARCHAR(200),
    spent_at TIMESTAMP NOT NULL,
    raw_text TEXT,
    details JSONB,
    tags TEXT[]
);
```

---

## Environment Setup

### Required Environment Variables

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=personal_ai
DB_USER=your_user
DB_PASSWORD=your_password

# OpenAI
OPENAI_API_KEY=sk-...

# Application
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=dev
```

### application.yaml Structure

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

openai:
  api-key: ${OPENAI_API_KEY}

server:
  port: ${SERVER_PORT:8080}
```

---

## Build & Run Commands

```bash
# Build
mvn clean install

# Run
mvn spring-boot:run

# Run with profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Run tests
mvn test

# Run specific test
mvn test -Dtest=ExpenseHandlerTest

# Package
mvn package

# Run packaged JAR
java -jar target/personal-ai-0.0.1-SNAPSHOT.jar
```

---

## Testing Endpoints

### Using cURL

```bash
# Health check
curl http://localhost:8080/health

# Expense summary
curl http://localhost:8080/api/expense/summary

# Speech input
curl -X POST http://localhost:8080/api/speech \
  -H "Content-Type: application/json" \
  -d '{"text": "Spent 500 on groceries"}'

# WhatsApp webhook verification
curl "http://localhost:8080/whatsapp/webhook?hub.mode=subscribe&hub.challenge=test&hub.verify_token=your_token"
```

### Using Postman

**POST /api/speech**
```json
{
  "text": "Spent 500 on groceries at BigBasket"
}
```

**Expected Response**:
```json
{
  "status": "FOLLOWUP",
  "message": "I recorded your expense. Which account did you pay from?",
  "missingFields": ["sourceAccount"],
  "partialData": {
    "amount": 500,
    "merchantName": "BigBasket",
    "category": "groceries"
  }
}
```

---

## Common Troubleshooting

### Issue: LLM returns invalid JSON

**Cause**: Prompt not clear or temperature too high

**Solution**:
1. Check prompt formatting
2. Verify temperature=0.1
3. Add explicit JSON schema to prompt
4. Test prompt in OpenAI Playground

---

### Issue: Transaction applied twice

**Cause**: Missing `financiallyApplied` check

**Solution**:
```java
if (!tx.isFinanciallyApplied()) {
    applyFinancialImpact(tx);
    tx.setFinanciallyApplied(true);
    repo.save(tx);
}
```

---

### Issue: Container not found during expense

**Cause**: No matching container for payment method

**Solution**:
1. Check container status (must be ACTIVE)
2. Verify container type matches
3. Create container via AccountSetupHandler first

---

### Issue: Tags not normalizing

**Cause**: No canonical tags in database

**Solution**:
```sql
INSERT INTO tag_master (canonical_tag, status) VALUES
('groceries', 'active'),
('food', 'active'),
('transport', 'active');
```

---

## Performance Tips

### Database
- Add indexes on frequently queried columns
- Use query projections for large result sets
- Batch operations where possible

### LLM
- Cache prompt templates (already done via PromptLoader)
- Use GPT-4.1 Mini (cheaper than GPT-4)
- Keep temperature low (0.1) for faster inference

### Application
- Enable Spring Boot caching
- Use async processing for bulk operations
- Monitor with Spring Actuator

---

## Code Quality Checklist

- [ ] Service methods have single responsibility
- [ ] LLM only for NL understanding/generation, not calculations
- [ ] Financial operations are idempotent
- [ ] All transactions create audit trail
- [ ] Input validation at API boundary
- [ ] Exceptions have descriptive messages
- [ ] Tests cover main scenarios
- [ ] Documentation updated

---

## Useful SQL Queries

### Find incomplete transactions
```sql
SELECT * FROM state_change 
WHERE needs_enrichment = true;
```

### Sum expenses by category (last 30 days)
```sql
SELECT category, SUM(amount) 
FROM state_change 
WHERE user_id = '1' 
  AND transaction_type = 'EXPENSE'
  AND timestamp > NOW() - INTERVAL '30 days'
GROUP BY category;
```

### Find over-limit containers
```sql
SELECT * FROM state_container 
WHERE over_limit = true 
  AND status = 'ACTIVE';
```

### Audit trail for container
```sql
SELECT * FROM state_mutation 
WHERE container_id = 123 
ORDER BY occurred_at DESC;
```

---

## Prompt Template Example

**File**: `src/main/resources/llm/expense/extract.md`

```markdown
Extract expense details from user input.

Fields to extract:
- amount (required)
- category
- subcategory
- merchantName
- timestamp
- sourceAccount
- tags

Rules:
- Return ONLY valid JSON
- Do not hallucinate
- If field is unclear, omit it

User input: "%s"
```

**Usage**:
```java
String prompt = promptLoader.load("llm/expense/extract.md");
String formatted = String.format(prompt, userText);
```

---

## Integration Checklist

### New LLM Service
- [ ] Create prompt files in resources/llm/
- [ ] Extend BaseLLMExtractor
- [ ] Define response DTO
- [ ] Inject PromptLoader
- [ ] Use callAndParse() method
- [ ] Handle invalid JSON responses
- [ ] Test with various inputs

### New Entity
- [ ] Define JPA entity with annotations
- [ ] Create repository interface
- [ ] Add indexes for query performance
- [ ] Create DTO for API layer
- [ ] Create mapper (DTO ↔ Entity)
- [ ] Write repository tests

### New API Endpoint
- [ ] Create controller method
- [ ] Define request/response DTOs
- [ ] Add validation annotations
- [ ] Handle exceptions
- [ ] Document endpoint
- [ ] Test with cURL/Postman

---

## Additional Resources

- **Spring Boot Docs**: https://spring.io/projects/spring-boot
- **JPA/Hibernate**: https://hibernate.org/orm/documentation/
- **OpenAI API**: https://platform.openai.com/docs
- **PostgreSQL JSONB**: https://www.postgresql.org/docs/current/datatype-json.html

---

## Contact & Contribution

For questions about this codebase, refer to the detailed documentation files:
- Architecture questions → `ARCHITECTURE_PATTERNS.md`
- Entity/schema questions → `ENTITIES.md`
- Service logic → `SERVICES.md`
- LLM integration → `LLM_INTEGRATION.md`
