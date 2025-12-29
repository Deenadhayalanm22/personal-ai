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

- Uses **PostgreSQL 16** (pinned version for consistency)
- Isolated database per test class
- Automatic cleanup after tests
- Dynamic property configuration via `@DynamicPropertySource`

## Troubleshooting

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

1. **Always run integration tests before committing** financial logic changes
2. **Use descriptive scenario names** in simulations
3. **Add new invariants** if you discover edge cases
4. **Never mock repositories** in integration tests - use real services
5. **Keep fuzz seeds reproducible** for debugging
6. **Document financial assumptions** in test comments

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
