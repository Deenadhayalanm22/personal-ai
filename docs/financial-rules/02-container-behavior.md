# Value Container Behavior Rules

## Cash Container
- Debit reduces currentValue
- Credit increases currentValue
- currentValue must never be negative

## Credit Card Container
- Debit increases outstanding balance
- Payment reduces outstanding balance
- Outstanding must not exceed credit limit unless explicitly allowed
- Over-limit flag must reflect actual state