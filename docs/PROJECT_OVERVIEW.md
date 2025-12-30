# Personal AI Finance Application - Project Overview

## Project Description
A Spring Boot-based financial management application with AI/LLM integration for intelligent expense tracking, account management, and financial analytics. The system processes natural language inputs through WhatsApp to record expenses, manage accounts, and provide financial insights.

## Technology Stack
- **Framework**: Spring Boot 3.2.11
- **Language**: Java 21
- **Database**: PostgreSQL with JPA/Hibernate
- **LLM Integration**: OpenAI API (GPT-4.1 Mini)
- **Build Tool**: Maven
- **Key Libraries**:
  - Spring Web & WebFlux
  - Spring Data JPA
  - Lombok
  - Jackson (JSON processing)
  - OpenAI Java SDK (v4.6.1)

## High-Level Architecture

### Architecture Layers
```
┌─────────────────────────────────────────────────────────┐
│              Controllers Layer                           │
│  (WhatsApp, Speech, ExpenseSummary, Health)             │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│           Orchestration Layer                            │
│  (SpeechOrchestrator, ConversationContext)              │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│              Handler Layer                               │
│  (ExpenseHandler, QueryHandler, AccountSetupHandler)    │
└─────────────────────────────────────────────────────────┘
                         ↓
┌───────────────────┬──────────────────┬──────────────────┐
│   LLM Layer       │   Service Layer  │  Evaluator Layer │
│  (Classifiers,    │  (Business Logic)│  (Completeness   │
│   Extractors)     │                  │   Evaluation)    │
└───────────────────┴──────────────────┴──────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│         Repository Layer (JPA Repositories)              │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│              PostgreSQL Database                         │
└─────────────────────────────────────────────────────────┘
```

## Project Structure

> **Note**: The project has been refactored to follow Domain-Driven Design (DDD) principles.
> See [ARCHITECTURE.md](ARCHITECTURE.md) and [REFACTORING_SUMMARY.md](REFACTORING_SUMMARY.md) for complete details.

```
src/main/java/com/apps/deen_sa/
├── PersonalAiApplication.java          # Main application entry point
│
├── core/                                # 🏛️ SHARED KERNEL (no domain dependencies)
│   ├── state/                          # State management concepts
│   │   ├── StateChangeEntity.java
│   │   ├── StateChangeRepository.java
│   │   ├── StateChangeTypeEnum.java
│   │   ├── StateContainerEntity.java
│   │   ├── StateContainerRepository.java
│   │   └── CompletenessLevelEnum.java
│   └── mutation/                       # State mutation concepts
│       ├── StateMutationEntity.java
│       ├── StateMutationRepository.java
│       └── MutationTypeEnum.java
│
├── conversation/                        # 💬 CONVERSATION DOMAIN
│   ├── SpeechOrchestrator.java        # Main conversation orchestrator
│   ├── ConversationContext.java       # Conversation state management
│   ├── SpeechHandler.java             # Handler interface
│   ├── SpeechResult.java              # Result wrapper
│   ├── SpeechStatus.java              # Status enum
│   ├── SpeechController.java          # REST endpoint
│   ├── WhatsAppWebhookController.java # WhatsApp webhook
│   ├── WhatsAppMessageProcessor.java  # WhatsApp message handling
│   └── WhatsAppReplySender.java       # WhatsApp response sender
│
├── finance/                             # 💰 FINANCE DOMAIN
│   ├── expense/                        # Expense management subdomain
│   │   ├── ExpenseHandler.java        # Main expense handler
│   │   ├── ExpenseCompletenessEvaluator.java
│   │   ├── ExpenseSummaryService.java
│   │   ├── ExpenseAnalyticsService.java
│   │   ├── ExpenseMerger.java
│   │   ├── ExpenseDtoToEntityMapper.java
│   │   ├── ExpenseValidator.java
│   │   ├── ExpenseEntity.java
│   │   ├── ExpenseRepository.java
│   │   ├── ExpenseSummaryController.java
│   │   ├── ExpenseTaxonomyRegistry.java
│   │   ├── TagMasterEntity.java
│   │   ├── TagMasterRepository.java
│   │   └── TagNormalizationService.java
│   │
│   ├── loan/                           # Loan analysis subdomain
│   │   └── LoanAnalysisService.java
│   │
│   ├── payment/                        # Liability payment subdomain
│   │   └── LiabilityPaymentHandler.java
│   │
│   ├── query/                          # Query & analytics subdomain
│   │   ├── QueryHandler.java
│   │   ├── ExpenseQueryBuilder.java
│   │   ├── TimeRangeResolver.java
│   │   └── QueryContextFormatter.java
│   │
│   └── account/                        # Account/container management
│       ├── ValueContainerService.java
│       ├── ValueAdjustmentService.java
│       ├── AccountSetupHandler.java
│       ├── AccountSetupValidator.java
│       ├── ValueContainerCache.java
│       ├── InMemoryValueContainerCache.java
│       └── strategy/                   # State mutation strategies
│           ├── ValueAdjustmentStrategy.java
│           ├── ValueAdjustmentStrategyResolver.java
│           ├── AdjustmentCommandFactory.java (creates StateMutationCommands)
│           ├── CreditSettlementStrategy.java
│           ├── CashLikeStrategy.java
│           ├── CreditCardStrategy.java
│           └── LoanStrategy.java
│
├── food/                                # 🥘 FOOD DOMAIN (reserved for future)
│   ├── recipe/                         # (empty)
│   ├── inventory/                      # (empty)
│   ├── grocery/                        # (empty)
│   └── planner/                        # (empty)
│
├── llm/                                 # 🤖 LLM INTEGRATION
│   ├── BaseLLMExtractor.java
│   ├── PromptLoader.java
│   └── impl/
│       ├── IntentClassifier.java
│       ├── ExpenseClassifier.java
│       ├── QueryClassifier.java
│       ├── AccountSetupClassifier.java
│       ├── LiabilityPaymentClassifier.java
│       ├── TagSemanticMatcher.java
│       ├── LoanQueryExplainer.java
│       └── ExpenseSummaryExplainer.java
│
├── common/                              # 🔧 COMMON UTILITIES
│   └── exception/
│       └── LLMParsingException.java
│
├── dto/                                 # Data Transfer Objects (cross-cutting)
│   ├── ExpenseDto.java
│   ├── AccountSetupDto.java
│   ├── QueryResult.java
│   └── [18 more DTOs]
│
├── config/                              # Infrastructure configuration
│   ├── AsyncConfig.java
│   ├── ConversationConfig.java
│   ├── CorsConfig.java
│   ├── HttpClientConfig.java
│   └── LLMConfig.java
│
├── controller/                          # Infrastructure controllers
│   └── HealthController.java
│
└── schduler/                            # Background jobs
    └── LoadTestData.java

src/main/resources/
├── llm/                                 # LLM Prompts
│   ├── common/
│   │   └── global_rules.md
│   ├── expense/
│   │   ├── extract.md
│   │   ├── rules.md
│   │   ├── category_types.md
│   │   ├── followup_question.md
│   │   └── followup_refinement.md
│   ├── intent/
│   │   ├── classify.md
│   │   └── schema.json
│   ├── payment/
│   │   ├── extract.md
│   │   └── schema.json
│   └── query/
│       ├── classify.md
│       └── schema.json
├── application.yaml                     # Application configuration
├── expense-taxonomy.yml                 # Expense categories
├── subcategory-contracts.yaml          # Subcategory rules
└── test-prompts.yml                    # Test prompts
```

### Domain Dependencies

```
core ← finance, conversation, llm     (Shared kernel used by all)
common ← all domains                   (Common utilities)
finance → core, common, llm, conversation
conversation → core, common, llm
llm → core, common
```

**Eliminated Packages** (moved to domains):
- ❌ `entity/` → Distributed to `core` and domain packages
- ❌ `repo/` → Distributed to `core` and domain packages
- ❌ `service/` → Moved to `finance.*`
- ❌ `handler/` → Moved to `conversation` and `finance.*`
- ❌ `orchestrator/` → Renamed to `conversation`
- ❌ `resolver/` → Moved to `finance.query` and `finance.account.strategy`
- ❌ `evaluator/`, `mapper/`, `validator/` → Moved to respective domains
- ❌ `strategy/` → Moved to `finance.account.strategy`
- ❌ `utils/` → Enums moved to `core`, utilities to domains
- ❌ `cache/` → Moved to `finance.account`
- ❌ `formatter/`, `registry/` → Moved to `finance.expense`
- ❌ `whatsApp/` → Moved to `conversation`
- ❌ `exception/` → Moved to `common.exception`

## Core Workflows

### 1. Expense Recording Flow
```
User Input (Voice/Text) 
  → WhatsApp/Speech Controller
  → SpeechOrchestrator
  → IntentClassifier (LLM)
  → ExpenseHandler
  → ExpenseClassifier (LLM) - Extract fields
  → ExpenseCompletenessEvaluator
  → Save StateChangeEntity
  → Apply Financial Impact (if container available)
  → Return SpeechResult
```

### 1a. Liability Payment Flow (Credit Cards & Loans)
```
User Input: "Paid 25,000 to credit card"
  → WhatsApp/Speech Controller
  → SpeechOrchestrator
  → IntentClassifier (LLM) → LIABILITY_PAYMENT
  → LiabilityPaymentHandler
  → LiabilityPaymentClassifier (LLM) - Extract payment details
  → Resolve Source Container (Bank Account)
  → Resolve Target Container (Credit Card/Loan)
  → Create StateChangeEntity (type=TRANSFER)
  → Apply Financial Impact:
      • DEBIT source (bank account reduces)
      • CREDIT target (liability outstanding reduces)
  → Create StateMutationEntity audit trail
  → Mark StateChange as financiallyApplied=true
  → Return SpeechResult
```

**Important**: Liability payments are TRANSFER transactions, NOT expenses. They do not appear in expense analytics.

### 2. Intent Classification
User inputs are classified into:
- **EXPENSE**: Spending money on goods/services
- **QUERY**: Asking about past transactions
- **INCOME**: Money coming in
- **INVESTMENT**: Investment activities
- **TRANSFER**: Moving money between accounts
- **LIABILITY_PAYMENT**: Paying credit cards or loans (NOT expenses)
- **ACCOUNT_SETUP**: Creating/declaring financial containers
- **UNKNOWN**: Unclear intent

### 3. Completeness Levels
Transactions are evaluated for completeness:
- **MINIMAL**: Basic info (amount) - saved but needs enrichment
- **OPERATIONAL**: Has category/merchant - saved, container mapping attempted
- **FINANCIAL**: Complete with source account - full financial impact applied

### 4. Financial Impact Application
```
StateChange Created
  → Resolve Source Container (Bank/Credit/Cash)
  → Create StateMutationCommand
  → Apply ValueAdjustmentStrategy
  → Update Container Balance
  → Create StateMutationEntity (audit trail)
  → Mark StateChange as financiallyApplied=true
```

## Key Design Decisions

### 1. Multi-Entity Data Model
- **StateChangeEntity**: Comprehensive state change model (core kernel)
- **ExpenseEntity**: Legacy/simple expense model (finance domain)
- **StateContainerEntity**: Universal container for all value-holding entities (core kernel)
- **StateMutationEntity**: Audit trail for all container mutations (core kernel)

### 2. LLM-First Approach
- Natural language processing for expense extraction
- Intent classification using OpenAI GPT-4.1 Mini
- Prompt engineering with structured templates
- Separation of prompt logic (resources/llm/) from code

### 3. Conversation State Management
- **ConversationContext**: Maintains state across multi-turn conversations
- Follow-up question handling for missing fields
- Incremental data enrichment

### 4. Strategy Pattern for Adjustments
- Different strategies for different container types
- Extensible for new financial instruments
- Supports complex scenarios (credit limits, overlimit handling)

### 5. Tag Normalization
- Semantic matching of user tags to canonical tags
- Prevents tag proliferation
- LLM-powered similarity matching

## Integration Points

### WhatsApp Integration
- Webhook receiver for WhatsApp messages
- Message processing and response formatting
- Async message handling

### OpenAI Integration
- Structured prompts loaded from resources
- JSON response parsing
- Temperature settings for consistency (0.1)
- Model: GPT-4.1 Mini

## Database Schema Highlights

### Value Container Model
- Supports multiple owner types (USER, BUSINESS, EMPLOYEE)
- Container types: CASH, BANK, CREDIT, INVENTORY, PAYABLE, RECEIVABLE
- Tracks current value, available value, capacity limits
- Over-limit detection and tracking

### Transaction Model
- Generic transaction recording
- Links to source and target containers
- Completeness tracking
- Enrichment flags (needsEnrichment, financiallyApplied)

### Flexible Metadata
- JSONB columns for extensibility (details field)
- Tag arrays for categorization
- Raw text preservation for audit

## Running the Application

### Prerequisites
- Java 21
- PostgreSQL database
- OpenAI API key

### Configuration
Set in `application.yaml`:
- Database connection
- OpenAI API credentials
- Server port and settings

### Build and Run
```bash
mvn clean install
mvn spring-boot:run
```

## API Endpoints

### Health Check
- `GET /health` - Application health status

### Expense Management
- `GET /api/expense/summary` - Get expense dashboard summary

### Speech Interface
- `POST /api/speech` - Process natural language input
- Request: `{"text": "Spent 500 on groceries"}`
- Response: Transaction result with follow-up questions if needed

### WhatsApp
- `POST /whatsapp/webhook` - WhatsApp message webhook
- `GET /whatsapp/webhook` - Webhook verification

## Future Extensibility

The architecture supports:
- Multiple LLM providers (via BaseLLMExtractor)
- New intent types (via SpeechHandler interface)
- Additional container types
- Custom adjustment strategies
- Enhanced analytics and reporting
- Multi-tenant support (userId/businessId already in place)

## Development Guidelines

1. **Prompts First**: Define LLM behavior via prompts in resources/llm/
2. **Minimal LLM Logic**: Keep business logic out of LLM calls
3. **Incremental Enrichment**: Support partial data and progressive enhancement
4. **Audit Everything**: Maintain full audit trail via adjustments
5. **Idempotency**: Prevent duplicate financial applications
