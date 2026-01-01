# Domain-First Package Architecture

## Package Structure

```
com.apps.deen_sa
├── core (Shared Kernel - No domain dependencies)
│   ├── state
│   │   ├── StateChangeEntity
│   │   ├── StateChangeRepository
│   │   ├── StateChangeTypeEnum
│   │   ├── StateContainerEntity
│   │   ├── StateContainerRepository
│   │   ├── StateContainerService
│   │   ├── CompletenessLevelEnum
│   │   └── cache
│   │       ├── StateContainerCache
│   │       └── InMemoryStateContainerCache
│   └── mutation
│       ├── StateMutationEntity
│       ├── StateMutationRepository
│       ├── StateMutationService
│       ├── MutationTypeEnum
│       └── strategy
│           ├── StateMutationStrategy (interface - SPI contract)
│           └── StateMutationStrategyResolver (generic resolver)
│
├── conversation (Conversational Orchestration)
│   ├── SpeechOrchestrator
│   ├── ConversationContext
│   ├── SpeechHandler
│   ├── SpeechResult
│   ├── SpeechStatus
│   ├── SpeechController
│   ├── WhatsAppWebhookController
│   ├── WhatsAppMessageProcessor
│   └── WhatsAppReplySender
│
├── finance (Finance Domain)
│   ├── expense
│   │   ├── ExpenseHandler
│   │   ├── ExpenseCompletenessEvaluator
│   │   ├── ExpenseSummaryService
│   │   ├── ExpenseAnalyticsService
│   │   ├── ExpenseMerger
│   │   ├── ExpenseDtoToEntityMapper
│   │   ├── ExpenseValidator
│   │   ├── ExpenseEntity
│   │   ├── ExpenseRepository
│   │   ├── ExpenseSummaryController
│   │   ├── ExpenseTaxonomyRegistry
│   │   ├── TagMasterEntity
│   │   ├── TagMasterRepository
│   │   └── TagNormalizationService
│   │
│   ├── loan
│   │   └── LoanAnalysisService
│   │
│   ├── payment
│   │   └── LiabilityPaymentHandler
│   │
│   ├── query
│   │   ├── QueryHandler
│   │   ├── ExpenseQueryBuilder
│   │   ├── TimeRangeResolver
│   │   └── QueryContextFormatter
│   │
│   └── account
│       ├── AccountSetupHandler
│       ├── AccountSetupValidator
│       └── strategy
│           ├── AdjustmentCommandFactory (finance-specific)
│           ├── CreditSettlementStrategy (finance-specific interface)
│           ├── CashLikeStrategy (implements core.mutation.strategy.StateMutationStrategy)
│           ├── CreditCardStrategy (implements core.mutation.strategy.StateMutationStrategy)
│           └── LoanStrategy (implements core.mutation.strategy.StateMutationStrategy)
│
├── food (Reserved for Future)
│   ├── recipe (empty)
│   ├── inventory (empty)
│   ├── grocery (empty)
│   └── planner (empty)
│
├── llm (LLM Integration - Unchanged)
│   └── impl
│       ├── AccountSetupClassifier
│       ├── ExpenseClassifier
│       ├── TagSemanticMatcher
│       ├── LoanQueryExplainer
│       ├── IntentClassifier
│       ├── QueryClassifier
│       └── ExpenseSummaryExplainer
│
├── common (Utilities Only)
│   └── exception
│       └── LLMParsingException
│
├── dto (Cross-cutting DTOs)
│   └── [All DTOs remain here]
│
├── config (Infrastructure)
│   └── [All config classes]
│
├── controller (Infrastructure only)
│   └── HealthController
│
└── schduler (Infrastructure)
    └── LoadTestData
```

## Dependency Rules

### ✅ Allowed
- finance → core
- finance → common
- finance → llm
- finance → conversation
- conversation → core
- conversation → common
- conversation → llm
- llm → core
- llm → common

### ❌ Forbidden
- core → finance
- core → conversation
- core → food
- core → llm
- common → any domain

## Key Principles

1. **Domain-First**: Business logic grouped by domain (finance, conversation), not technical layer
2. **Shared Kernel**: Core business concepts isolated and reused
3. **Clean Boundaries**: Each domain has well-defined responsibilities
4. **Strategy Co-location**: Design patterns live with their domain context
5. **Future-Ready**: Food domain prepared for expansion
6. **No Circular Dependencies**: Clean, acyclic dependency graph
