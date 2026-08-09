# Saree job-work MVP — target conversation

This is the target WhatsApp text/voice experience for the INIT-003 MVP happy path. It covers batch assignment, saree surrender, wages, and payment while using the core audit and idempotency contract. Owner thread inventory and production inspection are outside this MVP.

## Preconditions

- Employee: **Selvi**, with no open batch
- Wage rule: **₹100 per surrendered saree**, effective for this work
- Tenant date, timezone, and week boundary are configured
- The owner is authorized to assign a batch, approve wages, and record payments

The wage rule already exists; it is not recreated during this conversation.

## Target owner conversation

The assistant uses the owner's language in production. English is shown here as the canonical content; reviewed Tamil and romanized-Tamil templates should carry the same facts.

> **Owner:** Give Selvi 1,000 metres of thread today.
>
> **Assistant:** Done. Assigned **1,000 m of thread** to **Selvi** today in batch **SW-101**. The batch is open.
>
> **Owner:** Selvi surrendered 24 sarees.
>
> **Assistant:** Recorded **24 sarees** for **SW-101**. Selvi earned **₹2,400** at **₹100 each**. Approve this wage?
>
> **Owner:** Yes.
>
> **Assistant:** Approved **₹2,400** for Selvi. Has it been paid by cash or bank?
>
> **Owner:** Paid ₹2,400 cash today.
>
> **Assistant:** Payment recorded: **₹2,400 cash** to **Selvi** today. Earned **₹2,400**, paid **₹2,400**, balance **₹0**. Batch **SW-101** is still open.

For this MVP, all surrendered sarees are accepted automatically; there is no inspection follow-up or pending-inspection state. The assistant asks about payment method because the wage-payment story requires it. It does not ask the owner to choose a batch because Selvi has exactly one unambiguous open batch after assignment.

## Deterministic effects by turn

| Owner action | Committed event | Effect | Owner-visible reference |
|---|---|---|---|
| Assign 1,000 m | `saree:material-issued:v1` | Create open batch SW-101 assigned to Selvi with 1,000 m; do not calculate owner stock | SW-101 |
| Surrender 24 | `saree:production-surrendered:v1` | Record and automatically accept 24 pieces; earn `24 × ₹100 = ₹2,400` | SW-101 surrender record |
| Approve wage | `saree:wage-statement-approved:v1` | Freeze a versioned statement for `24 × ₹100 = ₹2,400` | Wage statement reference |
| Record payment | `saree:wage-paid:v1` | Apply ₹2,400 cash to the approved statement; outstanding wage becomes ₹0 | Payment reference |

Surrender does not close SW-101. Batch closure is a separate owner-confirmed action because more production may still be reported.

## Resulting state

| State | Expected value |
|---|---:|
| Selvi open batches | 1 (SW-101) |
| Thread assigned to SW-101 | 1,000 m |
| Surrendered and automatically accepted | 24 pieces |
| Earned wage | ₹2,400 |
| Approved wage | ₹2,400 |
| Paid wage | ₹2,400 |
| Outstanding wage | ₹0 |

The MVP does not calculate the owner's remaining thread stock, actual thread consumption, finished-stock inventory, or yield.

## Traceability and retry contract

Each committed event retains the owner, effective and recorded times, source message/audio evidence reference, normalized facts and units, extension/schema version, rule version, causation links, and an idempotency key. Batch, surrender, wage, and payment projections link back to those immutable events.

Retry safety is based on the inbound channel delivery identity plus the pending-action context, not on matching message text. If WhatsApp delivers the same assignment, surrender, approval, or payment message again, the existing event is returned and no record or monetary movement is applied twice. The reply is short and explicit, for example:

> **Assistant:** Already recorded — payment **₹2,400 cash** to **Selvi**. Balance **₹0**.

A genuinely new later message with the same words can still represent a new real-world event because it has a different delivery identity. Concurrent retries must resolve to the same committed event through the tenant-scoped unique idempotency key.

## MVP guardrails

- If Selvi has multiple matching open batches, ask which batch before recording the surrender.
- If the owner tries to pay more than the approved balance, ask for confirmation and do not silently create an advance.
- A low-confidence transcription of employee, assigned quantity, unit, surrendered count, or payment amount requires a read-back before committing the affected event.
- A correction or undo appends an auditable amendment/reversal; it never edits or deletes the original record.

## Acceptance scenario

**Given** Selvi has no open batch and the effective wage rate is ₹100 per surrendered saree  
**When** the owner assigns 1,000 m to Selvi, records her surrender of 24 sarees, approves the resulting wage, and records a ₹2,400 payment  
**Then** Selvi has one open batch with 1,000 m assigned, 24 sarees are recorded and automatically accepted, earned wage is ₹2,400, paid wage is ₹2,400, outstanding wage is ₹0, and replaying any delivered turn produces no duplicate event or movement.
