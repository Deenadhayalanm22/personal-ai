# Edge Cases to Always Support

## Duplicate Events
- Same transaction processed twice
- Same payment received twice

## Ordering Issues
- Payment before expense
- Expense after statement close

## Partial Failures
- Adjustment succeeds but transaction save fails
- Retry after crash