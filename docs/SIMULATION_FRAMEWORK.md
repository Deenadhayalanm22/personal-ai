# Financial Simulation Framework

## Purpose

This framework enables **deterministic, repeatable financial simulations** for integration testing. It is designed to:

1. Test complex financial scenarios without manual setup
2. Enforce financial correctness through automated invariants
3. Enable property-based fuzz testing with reproducible seeds
4. Provide time-controlled simulation for multi-day/month scenarios

## Architecture

```
┌─────────────────────────────────────┐
│  FinancialSimulationRunner          │  Fluent API for building scenarios
│  (Test-only)                        │
└──────────────┬──────────────────────┘
               │
               v
┌─────────────────────────────────────┐
│  FinancialSimulationContext         │  Holds simulated date + services
│  (Test-only)                        │
└──────────────┬──────────────────────┘
               │
               v
┌─────────────────────────────────────┐
│  Real Production Services           │  AccountSetupHandler
│  (No Mocks!)                        │  ExpenseHandler
│                                     │  LiabilityPaymentHandler
│                                     │  StateContainerService
└─────────────────────────────────────┘
```

## Key Principles

### 1. Test-Only Code
- All simulation classes live in `src/test/java`
- Never imported by production code
- No risk of simulation logic leaking into production

### 2. Real Services, No Mocks
- Uses actual Spring beans (handlers, services, repositories)
- Exercises real business logic and database operations
- Catches integration bugs that unit tests miss

### 3. Deterministic Time Control
- `FinancialSimulationContext` holds current simulation date
- Tests can advance time explicitly: `ctx.setCurrentDate()`, `ctx.nextDay()`
- Enables testing time-sensitive logic (monthly statements, due dates)

### 4. Idempotency Enforcement
- Same input → same output (guaranteed)
- Re-running identical simulation must produce identical database state
- Detected by snapshot comparison before/after reruns

### 5. Reproducible Randomness
- Fuzz testing uses **seeded Random instances**
- Failed tests log seed for exact reproduction
- No flaky tests due to random behavior

## Components

### FinancialSimulationContext

**Location**: `src/test/java/com/apps/deen_sa/simulation/FinancialSimulationContext.java`

**Responsibility**: 
- Holds references to production handlers/services
- Manages simulated current date
- Provides time advancement methods

**Example**:
```java
FinancialSimulationContext ctx = new FinancialSimulationContext(
    accountSetupHandler,
    expenseHandler,
    liabilityPaymentHandler,
    valueContainerService
);

ctx.setCurrentDate(LocalDate.of(2024, 1, 1));
ctx.nextDay(); // Advances to 2024-01-02
```

### FinancialSimulationRunner

**Location**: `src/test/java/com/apps/deen_sa/simulation/FinancialSimulationRunner.java`

**Responsibility**:
- Fluent API for building multi-step scenarios
- Records actions for deferred execution
- Provides replay log for debugging

**Example**:
```java
FinancialSimulationRunner.simulate(ctx)
    .day(1).setupContainer("BANK_ACCOUNT", "Checking", 100000)
    .day(5).expense("Groceries", 2500, "BANK_ACCOUNT")
    .day(15).payCreditCard(10000, "My Card")
    .run();
```

**Key Methods**:
- `.day(N)`: Set day-of-month for next action
- `.setupContainer(type, name, value)`: Create account/card
- `.expense(desc, amount, source)`: Record expense
- `.payCreditCard(amount, targetName)`: Pay credit card
- `.run()`: Execute all queued actions

### RandomFinancialScenarioGenerator

**Location**: `src/test/java/com/apps/deen_sa/simulation/fuzz/RandomFinancialScenarioGenerator.java`

**Responsibility**:
- Generate reproducible random financial scenarios
- Seed-based randomness for exact reproduction
- Smart amount selection based on container capacities

**Example**:
```java
RandomFinancialScenarioGenerator gen = 
    new RandomFinancialScenarioGenerator(seed, daysInMonth, containersByType);

List<ScenarioAction> actions = gen.generate(30); // 30 random actions
```

**Action Types**:
- `EXPENSE`: Random expense (75% probability)
- `PAY_CREDIT_CARD`: Credit card payment (25% probability)

**Smart Randomness**:
- Expenses avoid exceeding container limits
- Payment amounts based on outstanding balance
- Action days sorted chronologically for determinism

### FinancialAssertions

**Location**: `src/test/java/com/apps/deen_sa/assertions/FinancialAssertions.java`

**Responsibility**:
- Enforce financial invariants after simulations
- Pure assertion library (reads-only, no mutations)

**Available Assertions**:
```java
// Referential integrity
assertNoOrphanAdjustments(adjRepo, txRepo);

// Transaction consistency
assertAdjustmentsMatchTransactions(txRepo, adjRepo);

// Balance integrity
assertContainerBalance(containerId, openingValue, adjRepo, containerRepo);

// Money conservation
assertTotalMoneyConserved(openingBalances, containerRepo);

// Business rules
assertNoNegativeBalances(containerRepo);
assertCapacityLimitsRespected(containerRepo);

// Idempotency
assertIdempotentOnRerun(snapshotBefore, containerRepo, adjRepo);
```

## Simulation Patterns

### Pattern 1: Deterministic Monthly Scenario

**Use Case**: Test specific sequences of transactions

```java
@Test
void monthlyBudgetScenario() {
    FinancialSimulationContext ctx = createContext();
    ctx.setCurrentDate(LocalDate.of(2024, 3, 1));
    
    FinancialSimulationRunner.simulate(ctx)
        // Setup phase
        .day(1).setupContainer("BANK_ACCOUNT", "Salary Account", 0)
        .day(1).setupContainer("CREDIT_CARD", "Primary Card", 0)
        
        // Income
        .day(1).expense("Salary Reversal", -80000, "BANK_ACCOUNT")
        
        // Daily expenses
        .day(3).expense("Groceries", 5000, "CREDIT_CARD")
        .day(7).expense("Fuel", 3000, "CREDIT_CARD")
        .day(15).expense("Utilities", 8000, "BANK_ACCOUNT")
        
        // Bill payment
        .day(25).payCreditCard(8000, "Primary Card")
        .run();
    
    // Verify all invariants
    assertAllInvariants();
}
```

### Pattern 2: Idempotency Test

**Use Case**: Ensure simulations are repeatable

```java
@Test
void simulationIsIdempotent() {
    // Run once
    runScenario();
    Map<Long, BigDecimal> snapshotBefore = captureBalances();
    long adjCountBefore = adjRepo.count();
    
    // Run again with identical inputs
    runScenario();
    Map<Long, BigDecimal> snapshotAfter = captureBalances();
    long adjCountAfter = adjRepo.count();
    
    // Verify nothing changed
    FinancialAssertions.assertIdempotentOnRerun(
        snapshotBefore, containerRepo, adjRepo
    );
    assertEquals(adjCountBefore, adjCountAfter);
}
```

### Pattern 3: Property-Based Fuzz Testing

**Use Case**: Test system under randomized but reproducible scenarios

```java
@Test
void fuzzFinancialOperations() {
    int iterations = Integer.parseInt(
        System.getProperty("fuzz.iterations", "50")
    );
    
    for (int i = 0; i < iterations; i++) {
        long seed = 1000L + i;
        
        try {
            runRandomScenario(seed);
            assertAllInvariants();
        } catch (AssertionError e) {
            System.err.println("FAILED AT SEED: " + seed);
            System.err.println("REPRODUCE: mvn test -Dfuzz.seed=" + seed);
            throw e;
        }
    }
}
```

## LLM Test Configuration

**Location**: `src/test/java/com/apps/deen_sa/simulation/LLMTestConfiguration.java`

**Purpose**: Replace LLM classifiers with deterministic parsers for tests

**Why?**
- Tests shouldn't depend on external LLM API
- Simulation needs to be fast and deterministic
- String-based protocol for test scenarios

**Protocol Examples**:
```
SIM:ACCOUNT;type=BANK_ACCOUNT;name=Checking;current=50000;date=2024-01-01
SIM:EXPENSE;amount=1200;desc=Groceries;source=CREDIT_CARD;date=2024-01-05
SIM:PAYMENT;amount=3000;target=CREDIT_CARD;targetName=MyCard;date=2024-01-15
```

## Financial Invariants Enforced

### 1. No Orphan Adjustments
Every `StateMutationEntity` must reference a valid `StateChangeEntity`.

**Why**: Prevents data corruption where adjustments exist without their parent transaction.

### 2. Adjustment-Transaction Consistency
Every applied transaction must have corresponding adjustments.

**Why**: Ensures transactions actually affect account balances.

**Rules**:
- `TRANSFER`: Adjustments net to zero
- `EXPENSE`: Adjustments equal `-amount`

### 3. Container Balance Integrity
Container current value must equal: `openingValue + credits - debits`

**Why**: Detects race conditions, double-applications, or lost updates.

### 4. Money Conservation
Total money across all containers must remain constant (unless external income/expense).

**Why**: Internal transfers shouldn't create or destroy money.

### 5. No Negative Asset Balances
`BANK_ACCOUNT` and `CASH` containers cannot go negative.

**Why**: Real bank accounts can't have negative balances.

**Note**: Credit cards and loans CAN be negative (they're liabilities).

### 6. Capacity Limits Respected
`CREDIT_CARD` and `LOAN` balances must not exceed `capacityLimit`.

**Why**: Credit limits are enforced by real financial institutions.

### 7. Idempotency
Re-running identical simulation produces identical database state.

**Why**: Ensures system is deterministic and predictable.

## Debugging Failed Simulations

### Step 1: Capture Seed
When a fuzz test fails:
```
====================================
FUZZ SIMULATION FAILURE
====================================
Seed: 1042
To reproduce, run:
  mvn verify -Pintegration -Dtest=FuzzSimulationIT#testReproduceSeed -Dfuzz.seed=1042
====================================
```

### Step 2: Reproduce Locally
```bash
mvn verify -Pintegration -Dtest=FuzzSimulationIT#testReproduceSeed -Dfuzz.seed=1042
```

### Step 3: Add Debugging
Edit `runOne()` in `FuzzSimulationIT`:
```java
// Add after scenario generation
System.out.println("Generated scenario:");
for (ScenarioAction action : scenario) {
    System.out.println(action);
}
```

### Step 4: Inspect Database
Add breakpoint after `runner.run()` and inspect:
```sql
SELECT * FROM state_container;
SELECT * FROM state_change;
SELECT * FROM state_mutation;
```

## Extending the Framework

### Adding a New Action Type

1. **Update `FinancialSimulationRunner`**:
```java
public FinancialSimulationRunner transfer(long amount, String from, String to) {
    LocalDate actionDate = this.currentDay;
    actions.add(() -> {
        String text = String.format(
            "SIM:TRANSFER;amount=%d;from=%s;to=%s;date=%s",
            amount, from, to, actionDate
        );
        ctx.transferHandler.handleSpeech(text, new ConversationContext());
    });
    replayLog.add(String.format("Day %s: TRANSFER %d from %s to %s", 
        actionDate.getDayOfMonth(), amount, from, to));
    return this;
}
```

2. **Update `RandomFinancialScenarioGenerator`**:
```java
// Add TRANSFER to ActionType enum
public enum ActionType { EXPENSE, PAY_CREDIT_CARD, TRANSFER }

// Update generate() method
if (kind < 50) {
    // expense
} else if (kind < 75) {
    // pay credit card
} else {
    // transfer
    actions.add(new ScenarioAction(day, ActionType.TRANSFER, ...));
}
```

3. **Update `LLMTestConfiguration`**:
```java
@Bean
@Primary
public TransferClassifier transferClassifier() {
    return new TransferClassifier(null) {
        @Override
        public TransferDto extractTransfer(String text) {
            // Parse SIM:TRANSFER protocol
        }
    };
}
```

### Adding a New Invariant

1. **Add to `FinancialAssertions`**:
```java
public static void assertNewInvariant(Params...) {
    // Assertion logic
    assertTrue(condition, "Meaningful error message");
}
```

2. **Call from tests**:
```java
FinancialAssertions.assertNewInvariant(...);
```

3. **Document in this guide**

## Performance Considerations

- **Unit tests**: Mock LLM, fast (< 1s each)
- **Integration tests**: Real DB via Testcontainers (~30-60s for 50 fuzz)
- **Nightly fuzz**: 100 iterations (~1-2 minutes)

**Optimization tips**:
- Use `@DirtiesContext` sparingly (slow)
- Share container across test class when possible
- Limit fuzz iterations for local development

## Security Note

⚠️ **Never use this simulation framework in production code!**

- LLM mocking configuration is test-only
- Simulated dates bypass real-time checks
- Missing production validations for speed

The framework is intentionally isolated to `src/test` to prevent accidental misuse.
