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

```
src/main/java/com/apps/deen_sa/
├── PersonalAiApplication.java          # Main application entry point
├── cache/                               # Caching implementations
├── config/                              # Spring configuration classes
├── controller/                          # REST endpoints
│   ├── ExpenseSummaryController.java
│   ├── HealthController.java
│   ├── SpeechController.java
│   └── WhatsAppWebhookController.java
├── dto/                                 # Data Transfer Objects
│   ├── ExpenseDto.java
│   ├── AccountSetupDto.java
│   ├── QueryResult.java
│   └── [18 more DTOs]
├── entity/                              # JPA Entities (Domain Models)
│   ├── ExpenseEntity.java
│   ├── TransactionEntity.java
│   ├── ValueContainerEntity.java
│   ├── ValueAdjustmentEntity.java
│   └── TagMasterEntity.java
├── evaluator/                           # Business rule evaluators
│   └── ExpenseCompletenessEvaluator.java
├── exception/                           # Custom exceptions
├── formatter/                           # Data formatters
├── handler/                             # Intent-specific handlers
│   ├── ExpenseHandler.java
│   ├── QueryHandler.java
│   └── AccountSetupHandler.java
├── llm/                                 # LLM integration
│   ├── BaseLLMExtractor.java
│   ├── PromptLoader.java
│   └── impl/                            # LLM service implementations
│       ├── IntentClassifier.java
│       ├── ExpenseClassifier.java
│       ├── QueryClassifier.java
│       ├── AccountSetupClassifier.java
│       ├── TagSemanticMatcher.java
│       ├── LoanQueryExplainer.java
│       └── ExpenseSummaryExplainer.java
├── mapper/                              # DTO ↔ Entity mappers
├── orchestrator/                        # Conversation orchestration
│   ├── SpeechOrchestrator.java
│   ├── SpeechHandler.java
│   ├── ConversationContext.java
│   └── SpeechResult.java
├── registry/                            # Component registries
├── repo/                                # JPA Repositories
│   ├── ExpenseRepository.java
│   ├── TransactionRepository.java
│   ├── ValueContainerRepo.java
│   ├── ValueAdjustmentRepository.java
│   └── TagMasterRepository.java
├── resolver/                            # Resolvers for queries and adjustments
│   ├── TimeRangeResolver.java
│   ├── ExpenseQueryBuilder.java
│   ├── ValueAdjustmentStrategyResolver.java
│   └── AdjustmentCommandFactory.java
├── scheduler/                           # Background jobs
├── service/                             # Business logic services
│   ├── ExpenseSummaryService.java
│   ├── ExpenseAnalyticsService.java
│   ├── LoanAnalysisService.java
│   ├── TagNormalizationService.java
│   ├── ValueContainerService.java
│   └── ValueAdjustmentService.java
├── strategy/                            # Strategy pattern implementations
│   └── impl/                            # Concrete strategies
├── utils/                               # Utilities and enums
│   ├── AdjustmentTypeEnum.java
│   ├── TransactionTypeEnum.java
│   ├── CompletenessLevelEnum.java
│   └── ExpenseMerger.java
├── validator/                           # Validation logic
└── whatsApp/                            # WhatsApp integration
    └── WhatsAppMessageProcessor.java

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
│   └── query/
│       ├── classify.md
│       └── schema.json
├── application.yaml                     # Application configuration
├── expense-taxonomy.yml                 # Expense categories
├── subcategory-contracts.yaml          # Subcategory rules
└── test-prompts.yml                    # Test prompts
```

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
  → Save TransactionEntity
  → Apply Financial Impact (if container available)
  → Return SpeechResult
```

### 2. Intent Classification
User inputs are classified into:
- **EXPENSE**: Spending money or making payments
- **QUERY**: Asking about past transactions
- **INCOME**: Money coming in
- **INVESTMENT**: Investment activities
- **TRANSFER**: Moving money between accounts
- **ACCOUNT_SETUP**: Creating/declaring financial containers
- **UNKNOWN**: Unclear intent

### 3. Completeness Levels
Transactions are evaluated for completeness:
- **MINIMAL**: Basic info (amount) - saved but needs enrichment
- **OPERATIONAL**: Has category/merchant - saved, container mapping attempted
- **FINANCIAL**: Complete with source account - full financial impact applied

### 4. Financial Impact Application
```
Transaction Created
  → Resolve Source Container (Bank/Credit/Cash)
  → Create AdjustmentCommand
  → Apply ValueAdjustmentStrategy
  → Update Container Balance
  → Create ValueAdjustmentEntity (audit trail)
  → Mark Transaction as financiallyApplied=true
```

## Key Design Decisions

### 1. Multi-Entity Data Model
- **TransactionEntity**: New comprehensive transaction model
- **ExpenseEntity**: Legacy/simple expense model
- **ValueContainerEntity**: Universal container for all value-holding entities (accounts, loans, inventory)
- **ValueAdjustmentEntity**: Audit trail for all container adjustments

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
