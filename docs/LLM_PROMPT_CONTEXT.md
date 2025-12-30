# LLM Prompt - Project Context Summary

## Purpose
This file serves as a comprehensive context summary for LLM tools to understand the Personal AI Finance Application. Use this as input when you need LLM insights about the project without uploading the actual Java source files.

---

## Project Identity

**Name**: Personal AI Finance Application  
**Type**: Spring Boot Backend Application  
**Language**: Java 21  
**Database**: PostgreSQL  
**AI Integration**: OpenAI GPT-4.1 Mini  
**Primary Interface**: WhatsApp + REST API  

---

## Core Concept

A financial management system that uses natural language processing to allow users to record expenses, manage accounts, and query financial data through conversational interfaces (primarily WhatsApp). The system intelligently extracts structured data from casual speech like "Spent 500 on groceries at BigBasket".

---

## Architecture Summary

### Layers (Top to Bottom)
1. **Controllers**: WhatsApp webhook, Speech API, Expense Summary API
2. **Orchestrators**: Route user input to appropriate handlers based on intent
3. **Handlers**: Intent-specific processing (Expense, Query, AccountSetup)
4. **Services**: Business logic (Expense summaries, Loan analysis, Tag normalization, Value adjustments)
5. **LLM Layer**: AI classifiers and extractors (Intent, Expense, Query, AccountSetup)
6. **Repositories**: JPA data access
7. **Database**: PostgreSQL with JSONB support

### Key Patterns
- **Strategy Pattern**: Different adjustment strategies for different container types
- **Template Method**: BaseLLMExtractor for standardized LLM interaction
- **State Pattern**: ConversationContext for multi-turn conversations
- **Factory Pattern**: AdjustmentCommandFactory (creates StateMutationCommands) for creating commands
- **Repository Pattern**: JPA repositories for data access

---

## Data Model (5 Core Entities)

### 1. StateChangeEntity (transaction_rec)
**Purpose**: Universal transaction recording  
**Key Fields**:
- amount, quantity, unit
- transactionType (EXPENSE, INCOME, TRANSFER, INVESTMENT)
- category, subcategory, mainEntity (merchant/vendor)
- sourceContainerId, targetContainerId (links to ValueContainer)
- completenessLevel (MINIMAL, OPERATIONAL, FINANCIAL)
- financiallyApplied (boolean flag to prevent duplicate impacts)
- needsEnrichment (boolean flag for incomplete data)
- details (JSONB for flexible metadata)
- tags (JSONB array)

### 2. StateContainerEntity (value_container)
**Purpose**: Universal container for all value-holding entities  
**Container Types**: CASH, BANK, CREDIT, INVENTORY, PAYABLE, RECEIVABLE  
**Key Fields**:
- ownerType, ownerId (USER, BUSINESS, etc.)
- containerType, name, status
- currentValue, availableValue (for tracking balances)
- capacityLimit (for credit limits)
- overLimit, overLimitAmount (credit overflow tracking)
- details (JSONB for type-specific data like EMI details)

### 3. StateMutationEntity (value_adjustments)
**Purpose**: Audit trail for all container balance changes  
**Key Fields**:
- transactionId (which transaction caused this)
- containerId (which container was affected)
- adjustmentType (DEBIT or CREDIT)
- amount, reason, occurredAt

### 4. ExpenseEntity (expense)
**Purpose**: Legacy simplified expense model  
**Key Fields**:
- amount, currency, category, subcategory
- merchantName, paymentMethod
- spentAt, recordedAt
- rawText, details (JSONB), tags (array)
- isValid, validationReason
- source (voice, manual, import, llm)

### 5. TagMasterEntity (tag_master)
**Purpose**: Canonical tag repository for normalization  
**Key Fields**: canonicalTag, status

---

## Key Services

### 1. ExpenseSummaryService
- Calculate daily/monthly totals
- Aggregate by category
- Retrieve recent transactions

### 2. LoanAnalysisService
- Calculate EMI remaining (pure Java calculation)
- Handle multi-loan scenarios
- Use LLM only for natural language explanation

### 3. TagNormalizationService
- Semantic matching of user tags to canonical tags
- Prevent tag proliferation

### 4. ValueContainerService
- CRUD for containers
- Get active containers for user

### 5. ValueAdjustmentService
- Apply financial adjustments using Strategy pattern
- Create audit trail
- Update container balances atomically

---

## LLM Integration Components

### BaseLLMExtractor
- Template for all LLM services
- Provides `callLLM()` and `callAndParse()` methods
- Uses GPT-4.1 Mini with temperature=0.1 (deterministic)

### Classifiers & Extractors

#### IntentClassifier
**Input**: User text  
**Output**: Intent (EXPENSE, QUERY, INCOME, INVESTMENT, TRANSFER, ACCOUNT_SETUP, UNKNOWN) + confidence  
**Critical Rule**: "I have a loan" = ACCOUNT_SETUP (state), "Paid EMI" = EXPENSE (action)

#### ExpenseClassifier
**Methods**:
- `extractExpense(String)`: Extract all expense fields from user text
- `generateFollowupQuestionForExpense()`: Generate missing field questions
- `extractFieldFromFollowup()`: Extract specific field from follow-up answer

#### QueryClassifier
**Input**: Natural language query  
**Output**: QueryResult (queryType, timeRange, filters, aggregation, sortBy)  
**Example**: "How much did I spend on food last month?" → SPEND_SUMMARY query with category=food, timeRange=last_month

#### AccountSetupClassifier
**Input**: Account declaration  
**Output**: AccountSetupDto (containerType, name, currentValue, capacityLimit, details)  
**Example**: "I have a 5 lakh loan, EMI 15k" → LOAN/CREDIT container with details

#### TagSemanticMatcher
**Input**: User tag + list of canonical tags  
**Output**: Best matching canonical tag + confidence

#### LoanQueryExplainer & ExpenseSummaryExplainer
**Purpose**: Convert structured data to natural language  
**Design**: Calculation in service (Java), explanation via LLM

---

## Key Workflows

### Expense Recording Flow
```
1. User: "Spent 500 on groceries"
2. WhatsApp/Speech Controller receives text
3. SpeechOrchestrator classifies intent → EXPENSE
4. ExpenseHandler processes:
   a. ExpenseClassifier extracts: amount=500, category=groceries
   b. ExpenseCompletenessEvaluator → MINIMAL level
   c. Save StateChangeEntity (needsEnrichment=true, financiallyApplied=false)
   d. Ask follow-up: "Which account did you pay from?"
5. User: "Cash"
6. ExpenseHandler handleFollowup:
   a. Extract sourceAccount=cash
   b. Update transaction
   c. Resolve cash container
   d. Apply financial impact (debit cash)
   e. Mark financiallyApplied=true
   f. Create StateMutationEntity (audit trail)
7. Response: "Recorded ₹500 groceries expense from Cash"
```

### Query Flow
```
1. User: "How much did I spend on food last month?"
2. Intent → QUERY
3. QueryClassifier extracts:
   - queryType: SPEND_SUMMARY
   - timeRange: {from: last_month_start, to: last_month_end}
   - filters: {category: "food"}
4. QueryHandler builds database query
5. Execute aggregation
6. ExpenseSummaryExplainer converts to natural language
7. Response: "You spent ₹12,450 on food last month"
```

### Account Setup Flow
```
1. User: "I have a personal loan of 5 lakhs, EMI is 15k"
2. Intent → ACCOUNT_SETUP
3. AccountSetupClassifier extracts:
   - containerType: LOAN (or CREDIT)
   - currentValue: 500000
   - details: {emiAmount: 15000}
4. AccountSetupHandler creates StateContainerEntity
5. Response: "Recorded your loan of ₹5,00,000"
```

---

## Design Decisions & Rationale

### 1. Separation: Calculation vs Explanation
**Rule**: Java does calculations (deterministic, testable), LLM does explanation (natural language)  
**Why**: LLMs are non-deterministic; business logic must be precise

### 2. Progressive Enrichment
**Rule**: Save incomplete data, enrich incrementally  
**Why**: Better UX (don't force all details), natural conversation flow  
**How**: CompletenessLevel enum (MINIMAL → OPERATIONAL → FINANCIAL)

### 3. Idempotent Financial Application
**Rule**: Check `financiallyApplied` flag before applying impact  
**Why**: Prevent duplicate balance changes if code runs twice  
**How**: Boolean flag + audit trail

### 4. Value Container Abstraction
**Rule**: Single entity for all value-holding instruments  
**Why**: Reduced complexity, common query patterns, easy extensibility  
**Trade-off**: Type-specific validation in app layer vs database constraints

### 5. JSONB for Flexibility
**Rule**: Use JSONB for extensible metadata  
**Why**: Schema evolution without migrations, type-specific fields  
**Example**: Loan containers store {emiAmount, tenureMonths} in details field

### 6. Prompts as Resources
**Rule**: Store prompts as .md files in resources/llm/  
**Why**: Version control, non-developers can edit, separation of code/instructions  
**Structure**: common/, expense/, intent/, query/ subdirectories

### 7. Low Temperature (0.1)
**Rule**: Use temperature=0.1 for all LLM calls  
**Why**: Deterministic, consistent, minimal hallucination, faster inference

---

## Prompt Engineering Structure

### Prompt Location: src/main/resources/llm/

```
llm/
├── common/global_rules.md (never hallucinate, JSON only, etc.)
├── expense/
│   ├── extract.md (extract expense from text)
│   ├── rules.md (expense-specific rules)
│   ├── category_types.md (valid categories)
│   ├── followup_question.md (generate follow-up)
│   └── followup_refinement.md (extract from follow-up)
├── intent/
│   ├── classify.md (classify user intent)
│   └── schema.json (response schema)
└── query/
    ├── classify.md (parse query)
    └── schema.json (response schema)
```

### Prompt Loading Pattern
```java
String prompt = promptLoader.combine(
    "llm/common/global_rules.md",
    "llm/expense/rules.md",
    "llm/expense/extract.md"
);
String formatted = String.format(prompt, userText);
return callAndParse(systemPrompt, formatted, ExpenseDto.class);
```

---

## Critical Business Rules

### Intent Classification
1. **Describing state** (I have, My loan is) → ACCOUNT_SETUP
2. **Performing action** (Paid, Spent, Bought) → EXPENSE/INCOME/TRANSFER
3. **EMI mentioned WITHOUT payment verb** → ACCOUNT_SETUP
4. **EMI mentioned WITH payment verb** → EXPENSE

### Completeness Levels
- **MINIMAL**: Amount only → Save but mark needsEnrichment=true
- **OPERATIONAL**: + category + merchant → Save, attempt container mapping
- **FINANCIAL**: + sourceAccount → Apply financial impact

### Financial Impact Rules
1. Check `financiallyApplied` flag first (idempotency)
2. Resolve source container
3. Use Strategy pattern based on container type
4. Update container balance
5. Create StateMutationEntity (audit)
6. Set `financiallyApplied=true`

---

## Tech Stack Summary

- **Framework**: Spring Boot 3.2.11
- **Language**: Java 21 (using records, pattern matching)
- **Build**: Maven
- **Database**: PostgreSQL (JSONB, ACID transactions)
- **ORM**: JPA/Hibernate
- **LLM**: OpenAI GPT-4.1 Mini (openai-java SDK v4.6.1)
- **Utilities**: Lombok (reduce boilerplate), Jackson (JSON)
- **Messaging**: WhatsApp webhook integration

---

## Key Files Structure

```
src/main/java/com/apps/deen_sa/
├── entity/ (5 entities)
├── repo/ (5 repositories)
├── service/ (6 services)
├── handler/ (3 handlers: Expense, Query, AccountSetup)
├── orchestrator/ (SpeechOrchestrator, ConversationContext)
├── llm/ (BaseLLMExtractor + 7 implementations)
├── controller/ (4 controllers)
├── dto/ (19 DTOs)
├── utils/ (3 enums + utilities)
├── strategy/ (ValueAdjustmentStrategy + implementations)
├── resolver/ (TimeRange, Query, Strategy resolvers)
├── evaluator/ (ExpenseCompletenessEvaluator)
└── mapper/ (DTO ↔ Entity mappers)

src/main/resources/
├── llm/ (8 prompt files)
├── application.yaml
├── expense-taxonomy.yml
└── subcategory-contracts.yaml
```

---

## Performance & Optimization

### Current Optimizations
- Prompt caching (PromptLoader with ConcurrentHashMap)
- GPT-4.1 Mini (cheaper than GPT-4)
- Low temperature (faster inference)
- Database indexes on user_id, timestamp, container_id

### Future Opportunities
- LLM result caching
- Batch LLM operations
- Embedding-based tag matching (cheaper than LLM calls)
- Query result caching
- Read replicas for analytics

---

## Common Troubleshooting

### Q: Transaction applied twice
**A**: Missing `financiallyApplied` check. Always check flag before applying.

### Q: LLM returns invalid JSON
**A**: Check prompt clarity, verify temperature=0.1, add explicit schema to prompt.

### Q: Container not found during expense
**A**: No active container matches. User must create container via ACCOUNT_SETUP first.

### Q: Tags not normalizing
**A**: No canonical tags in tag_master table. Insert canonical tags first.

---

## Use This Documentation For

1. **Understanding the codebase** without reading 85 Java files
2. **Onboarding new developers** with comprehensive context
3. **LLM prompting** for insights, optimization suggestions, debugging help
4. **Architectural decisions** when adding new features
5. **Code review** against documented patterns and principles
6. **Refactoring guidance** based on established design
7. **Testing strategy** understanding what and how to test
8. **Deployment planning** understanding system dependencies

---

## Example LLM Prompts You Can Use

**Prompt 1**: "Based on this project documentation, suggest 3 performance optimizations for the LLM integration layer"

**Prompt 2**: "How would I add a new intent type called 'BUDGET' to track budget goals? Walk me through the changes needed."

**Prompt 3**: "What are the potential security vulnerabilities in this architecture? Focus on the LLM integration and financial transaction handling."

**Prompt 4**: "Suggest a migration strategy to move all ExpenseEntity records to StateChangeEntity without downtime."

**Prompt 5**: "How can I add support for multi-currency transactions? What entities and services need modification?"

---

## Documentation Version

**Created**: 2024-12-29  
**Files**: 6 markdown documents + README  
**Total Lines**: ~3,211 lines  
**Coverage**: Complete project architecture, entities, services, LLM integration, patterns, and quick reference

---

## Next Steps for Development

1. Implement budget tracking feature
2. Add recurring transaction support
3. Build analytics dashboard
4. Implement multi-currency support
5. Add export/import functionality
6. Create mobile app interface
7. Implement real-time notifications
8. Add ML-based fraud detection

---

**END OF CONTEXT SUMMARY**
