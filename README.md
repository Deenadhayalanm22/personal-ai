# Personal AI Finance Application

A Spring Boot-based financial management application with AI/LLM integration for intelligent expense tracking, account management, and financial analytics. The system processes natural language inputs through WhatsApp to record expenses, manage accounts, and provide financial insights.

## 📚 Documentation

This project includes comprehensive documentation to help you understand the architecture, design decisions, and implementation details without needing to read through all the Java source files.

### Documentation Files

| File | Description | What You'll Learn |
|------|-------------|-------------------|
| **[PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md)** | High-level architecture and project structure | Technology stack, system architecture, core workflows, API endpoints |
| **[ARCHITECTURE.md](docs/ARCHITECTURE.md)** | New domain-first package structure | Domain-driven design, shared kernel, package organization |
| **[INTEGRATION_TESTING.md](docs/INTEGRATION_TESTING.md)** | Testing philosophy, strategy, and infrastructure | Unit vs integration vs fuzz tests, why real databases, why no mocks, Testcontainers setup, HikariCP config |
| **[FINANCIAL_RULES.md](docs/FINANCIAL_RULES.md)** | Financial rules & invariants (authoritative) | Core invariants, container behavior, scenarios, edge cases |
| **[FINANCIAL_RULES_TEST_COVERAGE.md](docs/FINANCIAL_RULES_TEST_COVERAGE.md)** | Financial rules coverage analysis | Rule-by-rule mapping to tests, compliance verification |
| **[REFACTORING_SUMMARY.md](docs/REFACTORING_SUMMARY.md)** | Complete refactoring history | All package moves, class relocations, import updates |
| **[ENTITIES.md](docs/ENTITIES.md)** | Database schema and entity relationships | Data models, entity fields, relationships, design rationale |
| **[SERVICES.md](docs/SERVICES.md)** | Service layer logic and responsibilities | Business logic, service interactions, transaction management |
| **[LLM_INTEGRATION.md](docs/LLM_INTEGRATION.md)** | AI/LLM components and prompt engineering | OpenAI integration, classifiers, extractors, prompt structure |
| **[ARCHITECTURE_PATTERNS.md](docs/ARCHITECTURE_PATTERNS.md)** | Design patterns and architectural decisions | Strategy, Factory, Template patterns, key trade-offs |
| **[QUICK_REFERENCE.md](docs/QUICK_REFERENCE.md)** | Quick lookup guide for common tasks | Code patterns, setup, troubleshooting, SQL queries |

## 🚀 Quick Start

### Prerequisites
- Java 21
- PostgreSQL database
- Maven 3.6+
- OpenAI API key

### Setup
1. Clone the repository
2. Set environment variables:
   ```bash
   export DB_HOST=localhost
   export DB_PORT=5432
   export DB_NAME=personal_ai
   export DB_USER=your_user
   export DB_PASSWORD=your_password
   export OPENAI_API_KEY=sk-your-key
   ```
3. Build and run:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

### Test the API
```bash
# Health check
curl http://localhost:8080/health

# Record an expense via speech
curl -X POST http://localhost:8080/api/speech \
  -H "Content-Type: application/json" \
  -d '{"text": "Spent 500 on groceries at BigBasket"}'
```

## 🎯 Key Features

- **Natural Language Processing**: Use conversational language to record expenses
- **Multi-turn Conversations**: System asks follow-up questions for missing details
- **Intelligent Classification**: AI-powered categorization and intent detection
- **Value Containers**: Unified model for accounts, credit cards, loans, inventory
- **Financial Impact Tracking**: Automatic balance updates with full audit trail
- **Progressive Enrichment**: Save incomplete data and enrich over time
- **WhatsApp Integration**: Record expenses directly from WhatsApp messages

## 🏗️ Architecture Highlights

- **Domain-First Design**: Organized by business domains (core, finance, conversation) rather than technical layers
- **Shared Kernel**: Core concepts (transaction, value) isolated and reused across domains
- **LLM-First Approach**: Natural language understanding via OpenAI GPT-4.1 Mini
- **Strategy Pattern**: Different financial adjustment strategies for different container types
- **Event Sourcing Ready**: Audit trail via StateMutationEntity
- **Multi-tenant Ready**: Built-in userId/businessId support

### Package Structure
```
com.apps.deen_sa
├── core (Shared Kernel)
│   ├── transaction
│   └── value
├── conversation (Speech & WhatsApp)
├── finance
│   ├── expense
│   ├── loan
│   ├── query
│   └── account (+ strategy)
├── llm (AI Integration)
└── common (Utilities)
```

See [ARCHITECTURE.md](docs/ARCHITECTURE.md) for complete package details.

## 📖 Use Case: Recording an Expense

```
User: "Spent 500 on groceries"
  → System classifies intent as EXPENSE
  → Extracts: amount=500, category=groceries
  → Saves transaction (MINIMAL completeness)
  → Asks: "Which account did you pay from?"

User: "Cash"
  → Updates transaction with sourceAccount=cash
  → Resolves cash container
  → Applies financial impact (debits cash balance)
  → Creates audit record
  → Confirms: "Recorded ₹500 expense from Cash"
```

## 💰 Financial Correctness & Testing Philosophy

This is a **production-grade finance application**. Financial correctness is non-negotiable.

### Rule Hierarchy

```
Documents > Tests > Code
```

**Financial rules are defined in natural language** in `/docs/FINANCIAL_RULES.md`:
- Section 1: Core Invariants - Fundamental financial laws (idempotency, no duplicate application)
- Section 2: Container Behavior - Account/container behavior rules
- Section 3: Canonical Scenarios - Standard transaction scenarios
- Section 4: Edge Cases - Duplicates, ordering, partial failures
- Section 5: System Assumptions - Determinism, database as truth

**Integration tests enforce these rules.** Production code must pass tests that enforce documented rules.

### Testing Strategy

**Unit Tests**: Fast feedback on business logic (mocks allowed)
```bash
mvn clean test
```

**Integration Tests**: Enforce financial correctness (NO mocks, real PostgreSQL via Testcontainers)
```bash
mvn clean verify -Pintegration
```

**Fuzz Tests**: Discover edge cases via randomized scenarios (50+ iterations, deterministic seeds)
```bash
mvn verify -Pintegration -Dfuzz.iterations=100
```

### Financial Invariants

Every integration test verifies **8 financial invariants**:

1. **No Orphan Adjustments** - Every adjustment references valid transaction
2. **Adjustment-Transaction Consistency** - Applied transactions have adjustments
3. **Balance Integrity** - Balance = opening + credits - debits
4. **Money Conservation** - Total money across containers constant
5. **No Negative Balances** - Assets (CASH, BANK_ACCOUNT) ≥ 0
6. **Capacity Limits** - Liabilities respect capacity limits
7. **Transaction Validity** - Valid type, non-null amount, non-negative
8. **Idempotency** - Rerunning simulation produces identical results

### Why Real Databases?

**Testcontainers + PostgreSQL 16 is mandatory for integration tests.**

- ❌ **NO H2/HSQLDB/Derby** - In-memory databases hide financial bugs
- ❌ **NO Mocks in Integration Tests** - Mocks bypass invariant checks
- ✅ **Real PostgreSQL** - Same behavior as production
- ✅ **Real Services** - Test actual transaction boundaries
- ✅ **Real Constraints** - Foreign keys, triggers, precision

### Deterministic Simulations

All financial scenarios are reproducible:

```bash
# Test fails with seed 1042
mvn verify -Pintegration -Dtest=FuzzSimulationIT#testReproduceSeed -Dfuzz.seed=1042
```

### CI Enforcement

**GitHub Actions blocks PR merges on rule violations.**

Pull request validation:
```bash
mvn clean verify -Pintegration -Dfuzz.iterations=50
```

Nightly comprehensive testing:
```bash
mvn verify -Pintegration -Dfuzz.iterations=100
```

### Documentation

- **[INTEGRATION_TESTING.md](docs/INTEGRATION_TESTING.md)** - Complete testing philosophy and integration test guide
- **[FINANCIAL_RULES.md](docs/FINANCIAL_RULES.md)** - Financial rules & how to add/update them
- **[FINANCIAL_RULES_TEST_COVERAGE.md](docs/FINANCIAL_RULES_TEST_COVERAGE.md)** - Coverage analysis

**Financial correctness protects against regressions. Tests are our contract.**

---

## 🧪 Testing (General)

```bash
# Run unit tests only
mvn clean test

# Run integration tests (requires Docker)
mvn clean verify -Pintegration

# Run specific test
mvn test -Dtest=ExpenseHandlerTest

# Run with coverage
mvn clean test jacoco:report
```

## 📊 Database Schema

The application uses PostgreSQL with the following key tables:
- `transaction_rec`: Core transaction records
- `value_container`: Accounts, loans, credit cards, inventory
- `value_adjustments`: Audit trail for all balance changes
- `expense`: Legacy expense model (for backward compatibility)
- `tag_master`: Canonical tag repository

See [ENTITIES.md](docs/ENTITIES.md) for detailed schema documentation.

## 🤖 LLM Integration

The application uses OpenAI GPT-4.1 Mini for:
- **Intent Classification**: Determine user's goal (expense, query, account setup, etc.)
- **Expense Extraction**: Parse natural language into structured data
- **Query Understanding**: Convert questions into database queries
- **Natural Language Generation**: Explain results in conversational language

See [LLM_INTEGRATION.md](docs/LLM_INTEGRATION.md) for prompt engineering details.

## 🔧 Configuration

Main configuration in `src/main/resources/application.yaml`:
- Database connection
- OpenAI API settings
- Server configuration
- JPA/Hibernate settings

## 📝 Contributing

When adding new features:
1. Follow existing patterns (see [ARCHITECTURE_PATTERNS.md](docs/ARCHITECTURE_PATTERNS.md))
2. Keep business logic separate from LLM calls
3. Ensure financial operations are idempotent
4. Add tests for new functionality
5. Update documentation

## 📄 License

[Add license information]

## 👥 Contact

[Add contact information]

---

**💡 Tip**: Start with [PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md) for a high-level understanding, then check [ARCHITECTURE.md](docs/ARCHITECTURE.md) for the new package structure, and dive into specific documentation files based on your needs.