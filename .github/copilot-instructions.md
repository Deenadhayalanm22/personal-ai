# GitHub Copilot Instructions

## Repository Governance Rules

This repository enforces strict governance rules to prevent documentation sprawl and maintain code quality.

### File Creation Rules

**CRITICAL: The repository has a hard limit of 25 documentation files.**

- ❌ **DO NOT create new files** unless explicitly approved
- ✅ **DO extend or consolidate existing files** instead
- ✅ **DO ask permission** if a new file seems absolutely necessary
- ⚠️ **Current documentation file count must stay ≤ 25**

### Financial Rules Documentation

**CRITICAL: Financial rules must live ONLY in `docs/FINANCIAL_RULES.md`**

- ❌ **DO NOT create** separate files for financial rules
- ❌ **DO NOT duplicate** financial rule definitions across files
- ✅ **DO consolidate** all financial rules into `FINANCIAL_RULES.md`
- ✅ **DO reference** `FINANCIAL_RULES.md` from other documents

### Documentation Sprawl Prevention

Documentation sprawl is considered **technical debt**.

- ❌ **DO NOT create** multiple small documentation files
- ❌ **DO NOT split** documentation unnecessarily
- ✅ **DO consolidate** related documentation into existing files
- ✅ **DO use sections** within existing files for organization

### Default Behavior for AI Tools

When working on this repository:

1. **Prefer modifying existing files** over creating new ones
2. **Check if relevant documentation exists** before creating new files
3. **Consolidate into existing files** when adding documentation
4. **Stop and explain** if creating a new file seems necessary:
   - What is the file for?
   - Why can't it be added to an existing file?
   - What existing file would need to be deleted to stay under the 25-file limit?

### Examples

#### ❌ WRONG: Creating New Documentation File

```
Creating new file: docs/payment-rules.md
```

**Why wrong**: Financial rules belong in `docs/FINANCIAL_RULES.md`

#### ✅ CORRECT: Extending Existing File

```
Adding payment rules section to docs/FINANCIAL_RULES.md
```

**Why correct**: Consolidates financial rules in the authoritative location

#### ❌ WRONG: Splitting Documentation

```
Creating docs/integration-testing-part-2.md because 
docs/INTEGRATION_TESTING.md is getting long
```

**Why wrong**: Creates documentation sprawl, exceeds file limit

#### ✅ CORRECT: Organizing Within File

```
Adding new section "Advanced Integration Testing" 
to docs/INTEGRATION_TESTING.md
```

**Why correct**: Keeps related content together, respects file limit

## Enforcement

These rules are enforced by:

1. **This file** - Instructs GitHub Copilot on repository conventions
2. **docs/GOVERNANCE.md** - Documents the governance policy
3. **Code review** - Human reviewers must reject PRs that violate these rules
4. **CI checks** (if implemented) - Automated validation of file counts

## When to Override

Override these rules only when:

1. **Explicitly approved** by repository maintainers
2. **Documented exception** is added to `docs/GOVERNANCE.md`
3. **Compensating file deletion** brings total back under limit

## Questions?

If you're unsure whether to create a new file:

1. **Stop and ask** the repository maintainer
2. **Propose consolidation** into existing files first
3. **Explain trade-offs** between new file vs. extending existing file

---

**Remember**: These rules exist to prevent documentation sprawl and maintain repository quality. They are not negotiable without explicit approval.
