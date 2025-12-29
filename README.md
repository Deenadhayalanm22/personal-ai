# Personal AI Finance Application

A Spring Boot-based financial management application with AI/LLM integration for intelligent expense tracking, account management, and financial analytics. The system processes natural language inputs through WhatsApp to record expenses, manage accounts, and provide financial insights.

## 📚 Documentation

This project includes comprehensive documentation to help you understand the architecture, design decisions, and implementation details without needing to read through all the Java source files.

### Documentation Files

| File | Description | What You'll Learn |
|------|-------------|-------------------|
| **[PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)** | High-level architecture and project structure | Technology stack, system architecture, core workflows, API endpoints |
| **[ENTITIES.md](ENTITIES.md)** | Database schema and entity relationships | Data models, entity fields, relationships, design rationale |
| **[SERVICES.md](SERVICES.md)** | Service layer logic and responsibilities | Business logic, service interactions, transaction management |
| **[LLM_INTEGRATION.md](LLM_INTEGRATION.md)** | AI/LLM components and prompt engineering | OpenAI integration, classifiers, extractors, prompt structure |
| **[ARCHITECTURE_PATTERNS.md](ARCHITECTURE_PATTERNS.md)** | Design patterns and architectural decisions | Strategy, Factory, Template patterns, key trade-offs |
| **[QUICK_REFERENCE.md](QUICK_REFERENCE.md)** | Quick lookup guide for common tasks | Code patterns, setup, troubleshooting, SQL queries |

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

- **Layered Architecture**: Clear separation between controllers, handlers, services, and repositories
- **LLM-First Approach**: Natural language understanding via OpenAI GPT-4.1 Mini
- **Strategy Pattern**: Different financial adjustment strategies for different container types
- **Event Sourcing Ready**: Audit trail via ValueAdjustmentEntity
- **Multi-tenant Ready**: Built-in userId/businessId support

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

## 🧪 Testing

```bash
# Run all tests
mvn test

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

See [ENTITIES.md](ENTITIES.md) for detailed schema documentation.

## 🤖 LLM Integration

The application uses OpenAI GPT-4.1 Mini for:
- **Intent Classification**: Determine user's goal (expense, query, account setup, etc.)
- **Expense Extraction**: Parse natural language into structured data
- **Query Understanding**: Convert questions into database queries
- **Natural Language Generation**: Explain results in conversational language

See [LLM_INTEGRATION.md](LLM_INTEGRATION.md) for prompt engineering details.

## 🔧 Configuration

Main configuration in `src/main/resources/application.yaml`:
- Database connection
- OpenAI API settings
- Server configuration
- JPA/Hibernate settings

## 📝 Contributing

When adding new features:
1. Follow existing patterns (see [ARCHITECTURE_PATTERNS.md](ARCHITECTURE_PATTERNS.md))
2. Keep business logic separate from LLM calls
3. Ensure financial operations are idempotent
4. Add tests for new functionality
5. Update documentation

## 📄 License

[Add license information]

## 👥 Contact

[Add contact information]

---

**💡 Tip**: Start with [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md) for a high-level understanding, then dive into specific documentation files based on your needs.