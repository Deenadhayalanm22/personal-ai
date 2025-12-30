# Integration Testing Guide

## Overview

This project implements a comprehensive integration testing framework for financial operations using:
- **Testcontainers** with PostgreSQL 16
- **Flyway** database migrations
- **Deterministic financial simulations**
- **Property-based fuzz testing**
- **Financial invariant enforcement**

## Prerequisites

- Java 21 (enforced via Maven)
- Docker (for Testcontainers)
- Maven 3.6+

## Running Tests Locally

### Unit Tests Only
```bash
mvn clean test
```

### Integration Tests Only
```bash
mvn clean verify -Pintegration
```

### All Tests
```bash
mvn clean verify -Pintegration
```

## Fuzz Testing

### Regular Fuzz Runs (50 iterations)
```bash
mvn verify -Pintegration
```

### Heavy Fuzz Runs (100+ iterations)
```bash
mvn verify -Pintegration -Dfuzz.iterations=100
```

### Reproducing a Failed Fuzz Scenario
When a fuzz test fails, it will output a seed value. To reproduce:

```bash
mvn verify -Pintegration -Dtest=FuzzSimulationIT#testReproduceSeed -Dfuzz.seed=<SEED_VALUE>
```

Example:
```bash
mvn verify -Pintegration -Dtest=FuzzSimulationIT#testReproduceSeed -Dfuzz.seed=1042
```

## Financial Invariants

All integration tests enforce the following financial invariants after each simulation:

1. **No Orphan Adjustments**: Every value adjustment must reference a valid transaction
2. **Adjustment-Transaction Consistency**: All applied transactions must have corresponding adjustments
3. **Balance Integrity**: Container balances must match sum of adjustments from opening balance
4. **Money Conservation**: Total money across all containers must remain constant
5. **No Negative Balances**: Asset accounts (BANK_ACCOUNT, CASH) cannot have negative balances
6. **Capacity Limits**: Liability accounts (CREDIT_CARD, LOAN) must respect capacity limits
7. **Transaction Validity**: All transactions must have valid type, non-null amount, and non-negative amount
8. **Idempotency**: Re-running the same simulation must produce identical results

## Test Structure

### Base Class
- `IntegrationTestBase`: Sets up Testcontainers with PostgreSQL 16 and configures Spring context

### Simulation Framework
- `FinancialSimulationContext`: Holds simulated date and service references
- `FinancialSimulationRunner`: Fluent API for building financial scenarios
- `FinancialAssertions`: Comprehensive invariant checks

### Test Classes
- `ValueContainerRepositoryIT`: Basic database connectivity test
- `MonthlySimulationIT`: Deterministic monthly financial scenarios
- `FuzzSimulationIT`: Property-based randomized testing with reproducible seeds

## CI/CD

### Pull Request Checks
- Unit tests run separately for fast feedback
- Integration tests run with Testcontainers
- Both must pass for merge

### Nightly Fuzz Testing
- Runs at 2 AM UTC daily
- Executes 100 fuzz iterations
- Uploads failure reports as artifacts (30-day retention)
- Can be manually triggered via GitHub Actions UI

## Testcontainers Configuration

### Container Lifecycle (Singleton Pattern)

**REQUIRED**: All integration tests must use the singleton Testcontainers pattern to prevent connection pool exhaustion:

```java
public abstract class IntegrationTestBase {
    static PostgreSQLContainer<?> postgres;
    
    static {
        postgres = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("integration_db")
                .withUsername("test")
                .withPassword("test")
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(60))
                .withReuse(true);  // Enable container reuse
        postgres.start();
    }
}
```

**Key Points**:
- Uses **PostgreSQL 16** (pinned version for consistency)
- **Single shared container** across all test classes
- Container reuse enabled with `.withReuse(true)`
- No `@Testcontainers` or `@Container` annotations (manual lifecycle)
- Dynamic property configuration via `@DynamicPropertySource`

### Container Reuse Setup

To enable container reuse, create `~/.testcontainers.properties`:
```properties
testcontainers.reuse.enable=true
```

This prevents creating new containers for each test class, reducing test execution time and avoiding connection pool issues.

## HikariCP Connection Pool Configuration

### Required Settings

Integration tests use optimized HikariCP settings to prevent connection timeout issues during long-running fuzz tests:

```java
@DynamicPropertySource
static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
    // ... datasource URL, username, password
    
    // HikariCP configuration
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
    registry.add("spring.datasource.hikari.minimum-idle", () -> "5");
    registry.add("spring.datasource.hikari.connection-timeout", () -> "60000"); // 60s
    registry.add("spring.datasource.hikari.max-lifetime", () -> "1800000"); // 30 min
    registry.add("spring.datasource.hikari.idle-timeout", () -> "300000"); // 5 min
    registry.add("spring.datasource.hikari.leak-detection-threshold", () -> "30000"); // 30s
    registry.add("spring.datasource.hikari.validation-timeout", () -> "5000");
    registry.add("spring.datasource.hikari.connection-test-query", () -> "SELECT 1");
}
```

**Rationale**:
- **Larger pool size (20)**: Handles concurrent operations during fuzz tests
- **Higher minimum idle (5)**: Maintains ready connections
- **Longer connection timeout (60s)**: Prevents premature timeouts during heavy load
- **Shorter idle timeout (5min)**: Releases unused connections faster
- **Aggressive leak detection (30s)**: Identifies connection leaks early

### Transaction Management for Cleanup

**REQUIRED**: Use `TransactionTemplate` for test data cleanup to ensure proper connection release:

```java
@Autowired
private TransactionTemplate transactionTemplate;

private void cleanupTestData() {
    transactionTemplate.execute(status -> {
        // Delete in reverse order of dependencies
        valueAdjustmentRepository.deleteAll();
        transactionRepository.deleteAll();
        valueContainerRepo.deleteAll();
        // Spring handles transaction commit and connection release
        return null;
    });
}
```

**Why This Matters**:
- Without explicit transaction management, connections may leak during cleanup
- `TransactionTemplate` ensures proper commit/rollback and connection release
- Critical for long-running tests with many iterations (e.g., 50+ fuzz iterations)

**Anti-Pattern (DO NOT USE)**:
```java
// ❌ This can leak connections
private void cleanupTestData() {
    valueAdjustmentRepository.deleteAll();
    transactionRepository.deleteAll();
    valueContainerRepo.deleteAll();
}
```

## Troubleshooting

### Connection Pool Exhaustion
If you see "Connection is not available, request timed out after XXXms":

1. **Verify singleton pattern**: Ensure `IntegrationTestBase` uses manual container lifecycle (no `@Container`)
2. **Check transaction management**: Cleanup methods must use `TransactionTemplate`
3. **Enable container reuse**: Set `testcontainers.reuse.enable=true` in `~/.testcontainers.properties`
4. **Review pool settings**: Ensure HikariCP configuration matches recommended values above

### Docker Not Available
If you see "Could not find a valid Docker environment", ensure Docker is running:
```bash
docker info
```

### Java Version Mismatch
Ensure Java 21 is active:
```bash
java -version
```

Should output: `openjdk version "21.x.x"`

### Testcontainers Cleanup
If containers are left running:
```bash
docker ps -a | grep testcontainers | awk '{print $1}' | xargs docker rm -f
```

## Best Practices

### Architecture & Infrastructure
1. **Use singleton Testcontainers pattern** - Manual lifecycle with `.withReuse(true)`
2. **Always use TransactionTemplate** for test data cleanup in long-running tests
3. **Configure HikariCP properly** - Use recommended pool sizes and timeouts
4. **Never mock repositories** in integration tests - use real services

### Test Development
5. **Always run integration tests before committing** financial logic changes
6. **Use descriptive scenario names** in simulations
7. **Add new invariants** if you discover edge cases
8. **Keep fuzz seeds reproducible** for debugging
9. **Document financial assumptions** in test comments

### Connection Management
10. **Clean up test data after iterations** - Prevents database bloat and connection leaks
11. **Monitor connection pool metrics** - Watch for leak detection warnings
12. **Test with realistic iteration counts** - Run locally with 50+ fuzz iterations before PR

## Simulation Example

```java
@Test
void customFinancialScenario() {
    FinancialSimulationContext ctx = new FinancialSimulationContext(
        accountSetupHandler, expenseHandler, 
        liabilityPaymentHandler, valueContainerService
    );
    
    ctx.setCurrentDate(LocalDate.now().withDayOfMonth(1));
    
    FinancialSimulationRunner.simulate(ctx)
        .day(1).setupContainer("BANK_ACCOUNT", "Checking", 50000)
        .day(5).expense("Groceries", 2000, "BANK_ACCOUNT")
        .day(10).expense("Rent", 15000, "BANK_ACCOUNT")
        .run();
    
    // Assert all invariants
    FinancialAssertions.assertNoOrphanAdjustments(adjRepo, txRepo);
    FinancialAssertions.assertNoNegativeBalances(containerRepo);
    // ... more assertions
}
```

## Adding New Invariants

1. Add assertion method to `FinancialAssertions`
2. Call from all relevant test classes
3. Document the invariant in this guide
4. Add test cases that specifically validate the new invariant

## Performance

- Unit tests: ~5-10 seconds
- Integration tests (50 fuzz): ~30-60 seconds
- Nightly fuzz (100 iterations): ~1-2 minutes
