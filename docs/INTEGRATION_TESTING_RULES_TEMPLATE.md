# Integration Testing Rules Template

## Purpose
This template provides a structured format for documenting new integration testing rules and patterns. Use this as a guide when adding new testing requirements to `INTEGRATION_TESTING.md`.

## When to Add New Rules

Add new rules to `INTEGRATION_TESTING.md` when:
- A new testing pattern is established (e.g., singleton pattern, transaction management)
- A common failure mode is identified and solved
- Best practices evolve based on real-world issues
- Infrastructure configuration changes (e.g., connection pool tuning)

## Rule Template Structure

```markdown
### [Rule Category Name]

**REQUIRED/RECOMMENDED**: [Clear statement of the rule]

[Code example showing correct implementation]

**Key Points**:
- Bullet 1: Brief explanation
- Bullet 2: Why it matters
- Bullet 3: When to apply

**Rationale**:
- Explain the problem this solves
- Reference specific issues if applicable
- Link to related patterns

**Anti-Pattern (DO NOT USE)**:
[Code example showing what NOT to do]
[Explanation of why this is problematic]
```

## Categories for New Rules

Place new rules in the appropriate section of `INTEGRATION_TESTING.md`:

1. **Prerequisites** - System requirements, dependencies
2. **Running Tests Locally** - Commands and configurations
3. **Testcontainers Configuration** - Container lifecycle and setup
4. **HikariCP Connection Pool Configuration** - Database connection management
5. **Financial Invariants** - Business logic assertions
6. **Test Structure** - Framework components
7. **Troubleshooting** - Common issues and solutions
8. **Best Practices** - Guidelines and recommendations

## Example: Adding a New Database Pattern

If you discover a new database testing pattern:

1. **Identify the category**: Likely "Test Structure" or "Best Practices"
2. **Write the rule**: Use the template above
3. **Add code examples**: Show both correct and incorrect usage
4. **Update relevant sections**: May affect "Troubleshooting" or other sections
5. **Test the documentation**: Ensure examples compile and run

## Documentation Governance

Per `ARCHITECTURE_GOVERNANCE.md`:
- **DO NOT** create new summary files (e.g., `INTEGRATION_TESTING_SUMMARY.md`)
- **DO** update `INTEGRATION_TESTING.md` directly
- **DO** keep rules concise and actionable
- **DO NOT** duplicate content across multiple files

## Checklist for Adding New Rules

- [ ] Rule solves a real problem (not hypothetical)
- [ ] Code examples are tested and working
- [ ] Anti-patterns are clearly marked
- [ ] Rationale explains "why", not just "what"
- [ ] Related sections are updated (Troubleshooting, Best Practices, etc.)
- [ ] No duplication with existing rules
- [ ] Language is clear and directive (REQUIRED, RECOMMENDED, DO NOT)
- [ ] Examples use actual code from the repository

## Prompt for AI Assistants

When instructing an AI to add integration testing rules:

```
Please update docs/INTEGRATION_TESTING.md with a new rule for [TOPIC].

Use the template in docs/INTEGRATION_TESTING_RULES_TEMPLATE.md as a guide.

Requirements:
1. Add to the [CATEGORY] section
2. Include working code examples from the repository
3. Show both correct pattern and anti-pattern
4. Explain the rationale (reference issue #XXX if applicable)
5. Update related sections (Troubleshooting, Best Practices)
6. Do not create new files - update existing canonical file only

The rule should address: [SPECIFIC PROBLEM STATEMENT]
```

## Examples of Well-Documented Rules

See `INTEGRATION_TESTING.md` for examples:
- **Testcontainers Singleton Pattern** (lines ~100-135)
- **HikariCP Configuration** (lines ~140-170)
- **Transaction Management for Cleanup** (lines ~172-202)

Each follows the structure:
1. Clear requirement statement
2. Working code example
3. Key points with rationale
4. Anti-pattern with explanation

## Maintenance

- **Review quarterly**: Ensure rules are still relevant
- **Archive obsolete rules**: Move to git history, don't keep in main doc
- **Consolidate similar rules**: Merge overlapping patterns
- **Keep it actionable**: Every rule should have a clear "do this, not that"

---

**Remember**: Integration testing rules exist to prevent real failures. Every rule should reference an actual problem that was solved.
