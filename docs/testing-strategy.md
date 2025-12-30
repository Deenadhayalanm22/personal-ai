# Testing Strategy

## Overview

This is a production-grade finance application. Testing strategy prioritizes **financial correctness** above all else.

---

## Test Types

### Unit Tests

**Purpose**: Fast feedback on business logic isolation

**Scope**: Single class or method

**Tools**: JUnit 5, Mockito

**When to Use**:
- Testing business logic without external dependencies
- Validating edge cases in algorithms
- Verifying error handling

**Example**: `LiabilityPaymentHandlerTest` validates payment processing logic

**Constraints**:
- Fast (milliseconds)
- No database
- No external services
- Mocks allowed

---

### Integration Tests

**Purpose**: Enforce financial correctness rules

**Scope**: Multiple components + real database

**Tools**: JUnit 5, Spring Boot Test, Testcontainers, PostgreSQL 16

**When to Use**:
- Testing financial workflows end-to-end
- Verifying database schema migrations
- Validating financial invariants
- Testing transaction boundaries

**Examples**:
- `MonthlySimulationIT` - Scenario-based testing
- `FuzzSimulationIT` - Property-based randomized testing
- `ValueContainerRepositoryIT` - Database connectivity

**Constraints**:
- Slower (seconds)
- Real PostgreSQL database via Testcontainers
- Real services and repositories
- **NO MOCKS** - mocks hide financial bugs

---

### Fuzz Tests

**Purpose**: Discover edge cases through randomization

**Scope**: Random financial scenarios

**Tools**: Seeded random, deterministic replay

**When to Use**:
- Stress-testing financial invariants
- Finding race conditions
- Discovering unexpected interactions

**Example**: `FuzzSimulationIT` runs 50+ random scenarios per execution

**Constraints**:
- Deterministic (seeded random)
- Reproducible (seed logged on failure)
- Must verify all financial invariants

---

## Why Testcontainers is Mandatory

### Rationale

1. **Real Database Behavior**: PostgreSQL has quirks (JSONB, transactions, locking) that H2/mocks don't replicate
2. **Migration Testing**: Flyway migrations must run against real PostgreSQL
3. **Isolation**: Each test class gets clean database state
4. **CI/CD Parity**: Local tests run same database as production

### Configuration

```java
static PostgreSQLContainer<?> postgres;

static {
    postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("integration_db")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);  // Enable container reuse
    postgres.start();
}
```

**Singleton Pattern**: One container shared across all test classes to prevent connection pool exhaustion

---

## Why Real Databases Are Used

### Financial Correctness Requires Real Database

**H2/HSQLDB/Derby are NOT acceptable for finance applications.**

Reasons:

1. **Transaction Semantics Differ**: PostgreSQL isolation levels behave differently from in-memory databases
2. **JSONB Differences**: PostgreSQL JSONB queries won't work on H2
3. **Constraint Enforcement**: Foreign keys, check constraints behave differently
4. **Concurrency**: In-memory databases don't replicate production concurrency issues
5. **Money Bugs Hide**: Floating point precision, decimal handling differs

**Example Bug Prevented**:
```sql
-- PostgreSQL: Enforces precision
amount NUMERIC(15,2)

-- H2: May allow silent truncation
-- Bug would only appear in production
```

---

## Why Mocks Are Forbidden in Integration Tests

### Mocks Hide Financial Bugs

**Integration tests must use real services and repositories.**

**Forbidden**:
```java
@Mock
private TransactionRepository transactionRepository;

@Mock
private ValueAdjustmentRepository adjustmentRepository;
```

**Required**:
```java
@Autowired
private TransactionRepository transactionRepository;

@Autowired
private ValueAdjustmentRepository adjustmentRepository;
```

### Why?

1. **Mocks bypass invariant checks**: Real repository validates constraints
2. **Mocks bypass transactions**: `@Transactional` behavior not tested
3. **Mocks bypass cascades**: Foreign key constraints not verified
4. **Mocks bypass database logic**: Triggers, defaults, computed columns skipped

**Real Bug Example**:
```java
// Mock test passes
when(repo.save(tx)).thenReturn(tx);

// Production fails: database constraint violation
// Real test catches this
```

---

## Testing Philosophy

### 1. Documents > Tests > Code

Financial rules are defined in natural language first in `/docs/FINANCIAL_RULES.md`:
- Section 1: Core Invariants
- Section 2: Container Behavior
- Section 3: Canonical Scenarios
- Section 4: Edge Cases
- Section 5: System Assumptions

**Integration tests enforce these documents.**

Production code must pass tests that enforce documents.

### 2. Fail Fast on Money Bugs

Integration tests run comprehensive invariant checks:

```java
FinancialAssertions.assertNoOrphanAdjustments();
FinancialAssertions.assertAdjustmentsMatchTransactions();
FinancialAssertions.assertContainerBalance();
FinancialAssertions.assertTotalMoneyConserved();
FinancialAssertions.assertNoNegativeBalances();
FinancialAssertions.assertCapacityLimitsRespected();
FinancialAssertions.assertAllTransactionsHaveValidStatus();
```

**Every simulation verifies all 8 invariants.**

### 3. Deterministic Simulations

All financial tests are reproducible:

```bash
# Failed fuzz test outputs seed
mvn verify -Pintegration -Dtest=FuzzSimulationIT#testReproduceSeed -Dfuzz.seed=1042
```

### 4. Real Database, Real Services

Integration tests use:
- ✅ Real PostgreSQL via Testcontainers
- ✅ Real Spring beans
- ✅ Real JPA repositories
- ✅ Real transaction management
- ✅ Real database constraints

Integration tests reject:
- ❌ H2/HSQLDB/Derby
- ❌ Mocked repositories
- ❌ Mocked services
- ❌ In-memory substitutes

---

## Test Execution

### Local Development

```bash
# Unit tests only (fast)
mvn clean test

# Integration tests (requires Docker)
mvn clean verify -Pintegration

# Integration tests with more fuzz iterations
mvn verify -Pintegration -Dfuzz.iterations=100
```

### CI/CD Pipeline

```bash
# Pull request validation
mvn clean verify -Pintegration -Dfuzz.iterations=50

# Nightly comprehensive testing
mvn verify -Pintegration -Dfuzz.iterations=100
```

**CI blocks merges on test failures.**

---

## Adding New Tests

### When to Add Unit Tests

✅ Business logic without external dependencies  
✅ Validation rules  
✅ Utility functions  
✅ Error handling

### When to Add Integration Tests

✅ New financial workflows  
✅ New transaction types  
✅ New container behaviors  
✅ Changes to money calculations  
✅ Database schema changes

**If code touches money, add integration test.**

### When to Update Fuzz Tests

✅ New transaction types added  
✅ New container types added  
✅ New financial operations

---

## Test Infrastructure

### Singleton Testcontainers

**REQUIRED**: All integration tests must use singleton pattern

```java
public abstract class IntegrationTestBase {
    static PostgreSQLContainer<?> postgres;
    
    static {
        postgres = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("integration_db")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
        postgres.start();
    }
}
```

**DO NOT** use `@Testcontainers` or `@Container` annotations (causes multiple containers)

### HikariCP Configuration

Integration tests require optimized connection pool:

```java
registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
registry.add("spring.datasource.hikari.minimum-idle", () -> "5");
registry.add("spring.datasource.hikari.connection-timeout", () -> "60000");
registry.add("spring.datasource.hikari.leak-detection-threshold", () -> "30000");
```

### Transaction Management for Cleanup

**REQUIRED**: Use `TransactionTemplate` for test data cleanup

```java
@Autowired
private TransactionTemplate transactionTemplate;

private void cleanupTestData() {
    transactionTemplate.execute(status -> {
        valueAdjustmentRepository.deleteAll();
        transactionRepository.deleteAll();
        valueContainerRepo.deleteAll();
        return null;
    });
}
```

**Without `TransactionTemplate`, connections leak during long-running fuzz tests.**

---

## Common Mistakes

### ❌ Using H2 for Integration Tests

**Wrong**:
```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

**Right**:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

### ❌ Mocking Repositories

**Wrong**:
```java
@Mock
private TransactionRepository transactionRepository;
```

**Right**:
```java
@Autowired
private TransactionRepository transactionRepository;
```

### ❌ Multiple Testcontainers Instances

**Wrong**:
```java
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
```

**Right**:
```java
static PostgreSQLContainer<?> postgres;

static {
    postgres = new PostgreSQLContainer<>("postgres:16").withReuse(true);
    postgres.start();
}
```

---

## Financial Correctness Checklist

Before committing code that touches money:

- [ ] Integration test added
- [ ] Test uses real database (Testcontainers)
- [ ] Test uses real services (no mocks)
- [ ] All 8 financial invariants verified
- [ ] Test is deterministic (reproducible)
- [ ] Documented which financial rule test enforces

---

## Summary

**Financial correctness is non-negotiable.**

- Unit tests: Fast feedback, business logic
- Integration tests: Enforce financial rules, real database
- Fuzz tests: Discover edge cases, randomization
- Testcontainers: Mandatory for integration tests
- Real databases: Required, no in-memory substitutes
- No mocks: Integration tests use real services
- Documents > Tests > Code: Rules defined in natural language

**CI blocks merges on rule violations.**
