# Personal AI Finance Application

Personal AI is a Java 21/Spring Boot conversational finance assistant. It accepts natural-language messages through WhatsApp or REST, uses an LLM for interpretation, and keeps financial calculation, authorization, persistence, and reporting deterministic in PostgreSQL.

## Product documentation

The maintained product backlog and requirements live in **[docs/jira/README.md](docs/jira/README.md)**. It uses one stable hierarchy: Initiative → Epic → Story → Sub-task. Each story records status, priority, dependencies, plain-English requirements, and testable acceptance criteria.

## 🚀 Quick Start

### Prerequisites
- Java 21
- PostgreSQL database
- Maven 3.6+
- OpenAI API key

### Setup
1. Clone the repository
2. Provide PostgreSQL configuration (currently in `application.yaml` or standard Spring datasource overrides) and the integration credentials you use:
   ```bash
   export SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/personal_ai'
   export SPRING_DATASOURCE_USERNAME='your_user'
   export SPRING_DATASOURCE_PASSWORD='your_password'
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

# Process a natural-language message
curl -X POST http://localhost:8080/api/v1/process \
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
- **Mutation Audit Trail**: Balance changes are represented by `StateMutationEntity`
- **User-Scoped Data Model**: User identifiers exist; production authorization remains tracked work

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

The implementation is organized by conversation, core state/mutation, finance, and LLM domains.

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

## 💰 Financial correctness

Financial correctness is a product requirement. The application is still in development and must satisfy the production-readiness stories before handling real financial data.

### Testing strategy

**Unit Tests**: Fast feedback on business logic (mocks allowed)
```bash
mvn clean test
```

**Integration Tests**: Run tests named `*IT` through the Maven integration profile
```bash
mvn clean verify -Pintegration
```

Financial writes must be deterministic, auditable, and idempotent. An unknown balance remains unknown (`null`), never zero. See EPIC-002 in the Jira documentation for the full product contract and acceptance criteria.

---

## 🧪 Testing (General)

```bash
# Run unit tests only
mvn clean test

# Run integration tests (requires Docker)
mvn clean verify -Pintegration

# Run a specific test
mvn test -Dtest=ExpenseCompletenessEvaluatorTest
```

## 📊 Database schema

Flyway migrations under `src/main/resources/db/migration/` define the durable PostgreSQL schema. Do not infer production safety from Hibernate's current development-time schema-update setting; replacing it with migration validation is tracked in STORY-033.

## 🤖 LLM Integration

The application uses OpenAI GPT-4.1 Mini for:
- **Intent Classification**: Determine user's goal (expense, query, account setup, etc.)
- **Expense Extraction**: Parse natural language into structured data
- **Query Understanding**: Convert questions into database queries
- **Natural Language Generation**: Explain results in conversational language

## 🔧 Configuration

Main configuration in `src/main/resources/application.yaml`:
- Database connection
- OpenAI API settings
- Server configuration
- JPA/Hibernate settings

## 📝 Contributing

When adding new features:
1. Link the change to a story in `docs/jira/` and keep its acceptance criteria current
2. Keep business logic separate from LLM calls
3. Ensure financial operations are idempotent
4. Add tests for new functionality
5. Update documentation

## 📄 License

[Add license information]

## 👥 Contact

[Add contact information]

Start with the [Jira product documentation](docs/jira/README.md), then open the epic for the product area you are changing.
