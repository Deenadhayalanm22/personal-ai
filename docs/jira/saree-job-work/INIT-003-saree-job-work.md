# INIT-003 — Saree Job-Work Extension

| Field | Value |
|---|---|
| Issue type | Initiative |
| Status | To Do |
| Depends on | INIT-001 extension contract, units, conversions, reconciliation, and audit |
| Outcome | Let a small weaving/job-work owner control thread issued to workers, sarees received, weekly wages, and exceptions through WhatsApp |

## Scenario and validated assumptions

The owner issues raw thread to each employee, receives completed sarees, and pays weekly based on accepted saree count. Initial business defaults supplied are:

| Rule | Initial value | Treatment |
|---|---:|---|
| Thread issued per employee/batch | 1,000 metres | Configurable; record actual issue |
| Nominal saree length | 6.25 metres | Planning standard, not proof of actual consumption |
| Nominal finished weight | 170 grams | Configurable quality reference/tolerance |
| Theoretical yield | 160 sarees | `1000 / 6.25`; planning figure only |
| Expected weekly output | 20 minimum, 30 practical maximum | Alert threshold, not automatic wage deduction |
| Manufacturing wage/cost | ₹100 per accepted saree | Effective-dated rate; weekly total is accepted count × rate |

### Critical clarification

The numbers imply length-based material consumption, but a woven saree normally involves thread consumption that may not equal finished saree length. The system must therefore treat 160 as a theoretical planning yield until the owner confirms what “1,000 metres of raw thread” measures, whether warp/weft or wastage is included, and how actual consumption is counted. It must record actual issue, return, scrap, and accepted output rather than automatically accusing a worker of shortage.

## Primary workflow

```text
Create employee → issue thread batch → record partial/weekly saree surrender
→ inspect/accept/reject output → calculate payable accepted pieces
→ pay/part-pay → close or carry forward batch → review stock, productivity, and variance
```

The end-to-end MVP behavior is illustrated in the [target conversation](MVP-target-conversation.md).

## Initiative acceptance criteria

1. The owner can complete employee, material issue, surrender, wage, payment, and query workflows using simple WhatsApp voice/text without a form.
2. Every metre/piece/gram/rupee is stored with its unit; incompatible units are never added together.
3. One issue batch can receive several partial surrenders across weeks and remains traceable by employee and date.
4. Wages derive only from accepted saree count and the effective rate unless an authorized adjustment is recorded.
5. Expected yield/productivity are shown as planning/exception indicators, not as facts or automatic penalties.
6. Retried messages never duplicate material movements, production, wages, or payments.

## Epics

- [SAREE-EPIC-001 — People, materials, and configurable standards](SAREE-EPIC-001-setup.md)
- [SAREE-EPIC-002 — Material issue, production, and surrender](SAREE-EPIC-002-production.md)
- [SAREE-EPIC-003 — Weekly wages and settlement](SAREE-EPIC-003-wages.md)
- [SAREE-EPIC-004 — Operational insights and exception control](SAREE-EPIC-004-insights.md)
