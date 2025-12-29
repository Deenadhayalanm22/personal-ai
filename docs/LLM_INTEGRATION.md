# LLM Integration Documentation

> **Note**: LLM integration layer remains mostly unchanged in the refactoring.
> Package: `com.apps.deen_sa.llm` - See [ARCHITECTURE.md](ARCHITECTURE.md) for details.

## Overview
The application uses OpenAI's GPT-4.1 Mini for natural language understanding and generation. LLM components are separated into classifiers, extractors, and explainers, each with specific, focused responsibilities.

**Package**: `com.apps.deen_sa.llm`  
**Prompts**: `src/main/resources/llm/`

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│               BaseLLMExtractor                      │
│  (OpenAI client, JSON parsing, common methods)     │
└─────────────────────────────────────────────────────┘
                         ↑
                         │ extends
         ┌───────────────┴───────────────┬──────────────┬──────────────┐
         │                               │              │              │
┌────────────────┐              ┌────────────────┐ ┌──────────┐ ┌──────────┐
│ Classifiers    │              │  Extractors    │ │Explainers│ │ Matchers │
├────────────────┤              ├────────────────┤ ├──────────┤ ├──────────┤
│ Intent         │              │ Expense        │ │ Loan     │ │ Tag      │
│ Query          │              │ AccountSetup   │ │ Summary  │ │ Semantic │
│                │              │                │ │          │ │          │
└────────────────┘              └────────────────┘ └──────────┘ └──────────┘
```

---

## Base Class: BaseLLMExtractor

**Location**: `com.apps.deen_sa.llm.BaseLLMExtractor.java`

### Purpose
Provides common LLM interaction functionality for all LLM services.

### Key Components

#### OpenAI Client
```java
protected final OpenAIClient client;
```
- Injected via constructor
- Configured with API key from application.yaml

#### JSON Mapper
```java
protected final ObjectMapper mapper;
```
- Jackson ObjectMapper configured for:
  - JavaTimeModule (handle Instant, LocalDate, etc.)
  - Disable WRITE_DATES_AS_TIMESTAMPS (ISO-8601 format)

### Core Methods

#### `callLLM(String systemPrompt, String userPrompt): String`
**Purpose**: Make raw LLM call and return JSON string

**Configuration**:
- Model: GPT-4.1 Mini (`ChatModel.GPT_4_1_MINI`)
- Temperature: 0.1 (very deterministic, minimal creativity)
- Messages: System + User message pattern

**Implementation**:
```java
ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
    .model(ChatModel.GPT_4_1_MINI)
    .addSystemMessage(systemPrompt)
    .addUserMessage(userPrompt)
    .temperature(0.1)
    .build();

ChatCompletion completion = client.chat().completions().create(params);
return completion.choices().getFirst().message().content().orElseThrow();
```

#### `callAndParse<T>(String systemPrompt, String userPrompt, Class<T> responseType): T`
**Purpose**: Call LLM and parse JSON response into Java object

**Flow**:
1. Call `callLLM()` to get JSON string
2. Parse JSON using ObjectMapper
3. Return typed object

**Error Handling**:
```java
try {
    return mapper.readValue(json, responseType);
} catch (Exception e) {
    throw new RuntimeException(
        "LLM returned invalid JSON for " + responseType.getSimpleName()
            + ". Raw response: " + json,
        e
    );
}
```

---

## Prompt Management: PromptLoader

**Location**: `com.apps.deen_sa.llm.PromptLoader.java`

### Purpose
Load and cache prompt templates from classpath resources.

### Key Features

#### Caching
```java
private final Map<String, String> cache = new ConcurrentHashMap<>();
```
- Prompts loaded once and cached
- Thread-safe concurrent access

#### Methods

##### `load(String path): String`
Load a single prompt file
```java
String prompt = promptLoader.load("llm/intent/classify.md");
```

##### `combine(String... paths): String`
Combine multiple prompts with double newline separator
```java
String fullPrompt = promptLoader.combine(
    "llm/common/global_rules.md",
    "llm/expense/rules.md",
    "llm/expense/extract.md"
);
```

### Implementation
```java
private String readFromClasspath(String path) {
    try (InputStream is = getClass()
            .getClassLoader()
            .getResourceAsStream(path)) {
        
        if (is == null) {
            throw new IllegalArgumentException(
                "Prompt file not found on classpath: " + path
            );
        }
        
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
        throw new UncheckedIOException(
            "Failed to load prompt file: " + path, e
        );
    }
}
```

---

## LLM Service Implementations

### 1. IntentClassifier

**Location**: `com.apps.deen_sa.llm.impl.IntentClassifier.java`

**Purpose**: Classify user message into one of predefined intents

**Intents**:
- EXPENSE: Spending money
- QUERY: Asking about transactions
- INCOME: Money coming in
- INVESTMENT: Investment activities
- TRANSFER: Moving money between accounts
- ACCOUNT_SETUP: Declaring/creating financial containers
- UNKNOWN: Unclear intent

**Method**:
```java
public IntentResult classify(String userText)
```

**Prompt Structure**:
```
System: llm/intent/classify.md
User: User's raw text
Response: JSON {intent, confidence}
```

**Key Classification Rules** (from prompt):
- "I have a loan" → ACCOUNT_SETUP (describing state)
- "Paid EMI" → EXPENSE (action verb)
- EMI mentioned WITHOUT payment verb → ACCOUNT_SETUP
- EMI mentioned WITH payment verb → EXPENSE

**Output**:
```java
record IntentResult(String intent, double confidence)
```

---

### 2. ExpenseClassifier

**Location**: `com.apps.deen_sa.llm.impl.ExpenseClassifier.java`

**Purpose**: Extract expense details from user text and handle follow-ups

**Methods**:

#### `extractExpense(String userText): ExpenseDto`
Extract all possible expense fields from initial input

**Prompts Used**:
- `llm/common/global_rules.md`
- `llm/expense/rules.md`
- `llm/expense/category_types.md`
- `llm/expense/extract.md` (with user text injected)

**Output**: ExpenseDto with fields:
- amount (BigDecimal)
- category (String)
- subcategory (String)
- merchantName (String)
- tags (List<String>)
- timestamp (Instant)
- sourceAccount (String)
- quantity (BigDecimal)
- unit (String)
- details (Map<String, Object>)
- rawText (String)

#### `generateFollowupQuestionForExpense(String field, ExpenseDto partial): String`
Generate natural follow-up question for missing field

**Prompt**: `llm/expense/followup_question.md`

**Examples**:
- Missing category: "What category is this expense for?"
- Missing merchant: "Where did you make this purchase?"
- Missing source: "Which account did you pay from?"

#### `extractFieldFromFollowup(ExpenseDto partial, String field, String answer): ExpenseDto`
Extract specific field from user's follow-up answer

**Prompt**: `llm/expense/followup_refinement.md`

**Context Provided**:
- Current partial data (JSON)
- Field being asked for
- User's answer

**Output**: ExpenseDto with only the newly extracted field populated

---

### 3. QueryClassifier

**Location**: `com.apps.deen_sa.llm.impl.QueryClassifier.java`

**Purpose**: Parse user queries about past transactions

**Method**:
```java
public QueryResult classifyQuery(String userText)
```

**Prompt**: `llm/query/classify.md`

**Output**: QueryResult containing:
- queryType (String): SPEND_SUMMARY, TRANSACTION_LIST, LOAN_EMI, etc.
- timeRange (TimeRange): FROM, TO dates
- filters (Map<String, Object>): category, merchant, tags, etc.
- aggregation (String): SUM, COUNT, AVG
- sortBy (String)
- limit (Integer)

**Example Queries**:
- "How much did I spend last month?"
  - queryType: SPEND_SUMMARY
  - timeRange: {from: "2024-11-01", to: "2024-11-30"}
  - aggregation: SUM

- "Show me all food expenses this week"
  - queryType: TRANSACTION_LIST
  - filters: {category: "food"}
  - timeRange: {from: "2024-12-23", to: "2024-12-29"}

---

### 4. AccountSetupClassifier

**Location**: `com.apps.deen_sa.llm.impl.AccountSetupClassifier.java`

**Purpose**: Extract account/container setup information

**Method**:
```java
public AccountSetupDto extractAccountSetup(String userText)
```

**Output**: AccountSetupDto containing:
- containerType (String): CASH, BANK, CREDIT, LOAN
- name (String): User-friendly name
- currentValue (BigDecimal): Current balance/outstanding
- currency (String)
- capacityLimit (BigDecimal): Credit limit for credit cards
- details (Map<String, Object>): EMI details, tenure, etc.

**Example Inputs**:
- "I have a personal loan of 5 lakhs, EMI is 15k, ends in Dec 2027"
  - containerType: LOAN (or CREDIT)
  - currentValue: 500000
  - details: {emiAmount: 15000, endDate: "2027-12-31"}

- "Create a credit card with 50k limit"
  - containerType: CREDIT
  - capacityLimit: 50000

---

### 5. TagSemanticMatcher

**Location**: `com.apps.deen_sa.llm.impl.TagSemanticMatcher.java`

**Purpose**: Match user tags to canonical tags using semantic similarity

**Method**:
```java
public TagMatchResult findBestMatch(String userTag, List<String> canonicalTags)
```

**Output**: TagMatchResult containing:
- bestMatch (String): Most similar canonical tag
- confidence (double): 0.0 to 1.0

**Use Case**:
```java
// User enters: "groc"
// Canonical tags: ["groceries", "food", "transport"]
// Returns: {bestMatch: "groceries", confidence: 0.9}
```

**Implementation**:
- Sends user tag and canonical options to LLM
- LLM performs semantic matching
- Returns best match with confidence

---

### 6. LoanQueryExplainer

**Location**: `com.apps.deen_sa.llm.impl.LoanQueryExplainer.java`

**Purpose**: Generate natural language explanations for loan calculations

**Method**:
```java
public String explainEmiRemaining(Map<String, Object> summary)
```

**Input** (from LoanAnalysisService):
```java
{
    "loanName": "HDFC Personal Loan",
    "emiPaid": 8,
    "emiLeft": 16,
    "emiAmount": 15000,
    "outstanding": 320000,
    "endDate": "2027-12-31"
}
```

**Output** (Natural Language):
```
"You have 16 EMIs left for your HDFC Personal Loan. 
You've already paid 8 EMIs of ₹15,000 each. 
Your outstanding amount is ₹3,20,000, 
and the loan will be fully paid by December 2027."
```

**Design Note**:
- Calculation done in LoanAnalysisService (pure Java)
- LLM only converts data to natural language
- Ensures accuracy and testability

---

### 7. ExpenseSummaryExplainer

**Location**: `com.apps.deen_sa.llm.impl.ExpenseSummaryExplainer.java`

**Purpose**: Generate natural language summaries of expense data

**Method**:
```java
public String explainSummary(ExpenseSummaryDto summary)
```

**Input**:
```java
{
    "todayTotal": 1250.00,
    "monthTotal": 45000.00,
    "categoryTotals": {
        "food": 15000,
        "transport": 8000,
        "utilities": 5000
    },
    "recentTransactions": [...]
}
```

**Output**:
Natural language summary suitable for voice or text display.

---

## Prompt Structure

### Location
`src/main/resources/llm/`

### Directory Layout
```
llm/
├── common/
│   └── global_rules.md         # Universal rules for all LLM calls
├── expense/
│   ├── extract.md              # Extract expense from user text
│   ├── rules.md                # Expense-specific rules
│   ├── category_types.md       # Valid categories and subcategories
│   ├── followup_question.md    # Generate follow-up questions
│   └── followup_refinement.md  # Extract field from follow-up answer
├── intent/
│   ├── classify.md             # Intent classification prompt
│   └── schema.json             # Response schema
└── query/
    ├── classify.md             # Query parsing prompt
    └── schema.json             # Response schema
```

### Prompt Example: Intent Classification

**File**: `llm/intent/classify.md`

**Content**:
```markdown
You are a financial intent classifier.

Your task is to classify the user's message into EXACTLY ONE of the following intents:
- EXPENSE
- QUERY
- INCOME
- INVESTMENT
- TRANSFER
- ACCOUNT_SETUP
- UNKNOWN

IMPORTANT DEFINITIONS (READ CAREFULLY)
----------------------------------------

ACCOUNT_SETUP:
Use this intent when the user is DECLARING, RECORDING, or DESCRIBING
an existing or new financial account, obligation, or value container.

Examples:
- "I have a personal loan of 5 lakhs"
- "EMI is 15k and ends in Dec 2027"
- "Add a credit card with 50k limit"

EXPENSE:
Use this intent ONLY when the user is SPENDING money or MAKING A PAYMENT.

Payment verbs include: paid, pay, spent, debited, deducted, transferred...

Examples:
- "Paid my loan EMI today"
- "Spent 500 on groceries"

CRITICAL RULES (NON-NEGOTIABLE)
1. If the user is DESCRIBING EXISTENCE or STATE → ACCOUNT_SETUP
2. If the user is PERFORMING AN ACTION → EXPENSE / INCOME / TRANSFER
3. EMI mentioned WITHOUT a payment verb → ACCOUNT_SETUP
4. EMI mentioned WITH a payment verb → EXPENSE
5. NEVER output anything except raw JSON

User message: "%s"
```

### Prompt Design Principles

1. **Clear Instructions**: Explicit task definition
2. **Examples**: Show expected behavior
3. **Rules**: Critical non-negotiable rules highlighted
4. **Schema**: JSON schema for structured responses
5. **Formatting**: User input injected via `String.format()`

---

## LLM Configuration

### Model Selection
```java
ChatModel.GPT_4_1_MINI
```
- Cost-effective
- Fast response times
- Sufficient for classification and extraction tasks

### Temperature Settings
```java
.temperature(0.1)
```
- Very deterministic (low temperature)
- Minimal creativity
- Consistent outputs for same inputs
- Critical for classification tasks

### Message Pattern
```java
.addSystemMessage(systemPrompt)  // Task definition, rules, examples
.addUserMessage(userPrompt)      // User's actual input
```

---

## Error Handling

### Invalid JSON Response
```java
try {
    return mapper.readValue(json, responseType);
} catch (Exception e) {
    throw new RuntimeException(
        "LLM returned invalid JSON for " + responseType.getSimpleName()
            + ". Raw response: " + json,
        e
    );
}
```

### Missing Content
```java
.orElseThrow(() -> new RuntimeException("LLM returned no content"))
```

### Prompt Not Found
```java
if (is == null) {
    throw new IllegalArgumentException(
        "Prompt file not found on classpath: " + path
    );
}
```

---

## Testing Strategy

### Unit Tests
- Mock OpenAIClient
- Test prompt assembly
- Verify JSON parsing

### Integration Tests
- Test against real OpenAI API (expensive)
- Use test API keys
- Validate end-to-end flow

### Prompt Testing
Use `test-prompts.yml` for regression testing:
```yaml
intent_tests:
  - input: "I have a loan of 5 lakhs"
    expected_intent: ACCOUNT_SETUP
  - input: "Paid EMI today"
    expected_intent: EXPENSE
```

---

## Cost Optimization

### Strategies
1. **Caching**: Cache prompt templates (PromptLoader)
2. **Smaller Model**: Use GPT-4.1 Mini instead of GPT-4
3. **Low Temperature**: Faster inference
4. **Minimal Prompts**: Concise instructions
5. **Batch Operations**: Group similar requests (future)

### Cost Tracking
- Log token usage per request
- Monitor via OpenAI dashboard
- Set budget alerts

---

## Security Considerations

### API Key Management
- Store in environment variables
- Never commit to source control
- Rotate periodically

### Input Sanitization
- User input is passed to LLM
- No code execution in prompts
- JSON-only responses (no markdown, HTML)

### Output Validation
- Always parse and validate LLM responses
- Use typed DTOs
- Handle malformed JSON gracefully

---

## Future Enhancements

### Multi-Provider Support
Abstract LLM provider:
```java
interface LLMProvider {
    String call(String system, String user);
}

class OpenAIProvider implements LLMProvider { ... }
class AnthropicProvider implements LLMProvider { ... }
```

### Prompt Versioning
- Version prompts (e.g., `classify_v2.md`)
- A/B testing different prompt versions
- Gradual rollout

### Fine-Tuning
- Create training data from production
- Fine-tune model for domain-specific tasks
- Reduce prompt size and cost

### Streaming Responses
For long explanations:
```java
Stream<String> streamExplanation(Map<String, Object> data)
```

### Embedding-Based Matching
For tag matching and semantic search:
- Pre-compute embeddings for canonical tags
- Use vector similarity instead of LLM calls
- Faster and cheaper
