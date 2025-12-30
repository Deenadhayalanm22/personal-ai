# Repository Governance Rules

## Purpose
This document establishes **THE SINGLE SOURCE OF TRUTH** for repository governance. All contributors MUST follow these rules.

---

## File Creation Rules
- The repository has a hard limit of 25 documentation files.
- No new files may be added unless explicitly approved.
- Existing files must be extended or consolidated instead.

## Documentation Rules
- Financial rules must live ONLY in `FINANCIAL_RULES.md`.
- No duplicate rule definitions across files.
- Documentation sprawl is considered technical debt.

## Copilot / AI Rules
- AI tools must not create new files by default.
- AI tools must prefer modifying existing files.
- If a new file seems necessary, AI must stop and explain why.

---

## Core Principle: One Topic, One File

Documentation sprawl leads to:
- Context duplication and confusion
- Maintenance burden
- Tooling limits being exceeded
- Inconsistent information across files

**Therefore**: Each architectural topic maps to exactly ONE canonical file.

## Canonical Documentation Map

| Topic | Canonical File | Purpose |
|-------|---------------|---------|
| Package structure & architecture | `ARCHITECTURE.md` | Domain-first architecture, package dependencies |
| Project overview & tech stack | `PROJECT_OVERVIEW.md` | High-level system description, workflows |
| Entities & database schema | `ENTITIES.md` | Data model, relationships, lifecycle |
| Refactoring history | `REFACTORING_SUMMARY.md` | Package moves, class renames |
| LLM behavior & prompts | `LLM_PROMPT_CONTEXT.md` | LLM integration patterns |
| LLM technical details | `LLM_INTEGRATION.md` | Implementation details, code examples |
| Integration testing | `INTEGRATION_TESTING.md` | Testing framework, patterns, rules |
| Financial rules | `FINANCIAL_RULES.md` | Authoritative financial rules |
| Financial test coverage | `FINANCIAL_RULES_TEST_COVERAGE.md` | Rule-to-test mapping |
| Service layer | `SERVICES.md` | Business logic, service responsibilities |
| Design patterns | `ARCHITECTURE_PATTERNS.md` | Patterns used in the codebase |
| Quick reference | `QUICK_REFERENCE.md` | Common tasks, troubleshooting |
| Simulation framework | `SIMULATION_FRAMEWORK.md` | Testing simulation infrastructure |

## Prohibited Documentation Patterns

The following are **STRICTLY FORBIDDEN**:

### ❌ DO NOT Create
- `*_SUMMARY.md` files (summaries belong in canonical docs)
- `*_UPDATED.md` files (update the canonical doc in place)
- `*_V2.md` or versioned docs (use git history for versions)
- `*_IMPLEMENTATION.md` for features (merge into appropriate canonical doc)
- `*_FINAL.md` files (there is no "final", only current)
- `*_COMPLIANCE.md` or `*_AUDIT.md` files (historical audits don't belong in main docs)
- `*_TEMPLATE.md` files (meta-documentation about how to write docs)
- Duplicate architecture or overview files

### ❌ DO NOT Duplicate
- Architecture explanations across multiple files
- Package structure diagrams
- Entity relationship descriptions
- Workflow descriptions

## Required Documentation Actions

### When Adding a Feature
1. Update the relevant canonical file(s) in place
2. Add new section under appropriate heading
3. Do NOT create a separate implementation file

### When Refactoring
1. Update `ARCHITECTURE.md` if package structure changes
2. Update `REFACTORING_SUMMARY.md` with what moved where
3. Update `PROJECT_OVERVIEW.md` if workflows change
4. Do NOT create a separate refactoring summary

### When Fixing Documentation Issues
1. Fix errors in the canonical file
2. Remove duplicate content
3. Consolidate related information
4. Delete redundant files

## Merge, Never Multiply

When content exists in multiple places:

1. **Identify** the canonical file for that topic
2. **Extract** unique, valuable information from duplicates
3. **Merge** into the canonical file under appropriate heading
4. **Delete** the duplicate file
5. **Verify** no information was lost

## Enforcement

This governance exists due to:
- **Tool limits**: LLMs have context windows; sprawl exceeds them
- **Maintenance burden**: Multiple files = multiple places to update
- **Inconsistency risk**: Duplicate docs drift out of sync

**All pull requests that violate these rules will be rejected.**

## Emergency Override

Only repository maintainers may create new top-level documentation files, and only when:
1. A new major domain is added (e.g., `food/` becomes active)
2. A fundamentally new architectural pattern is introduced
3. Existing canonical files become too large (>1000 lines)

Even then, the new file must be added to the canonical map above.

## Governance History

- **2025-12-29**: Initial governance established due to documentation sprawl
- **2025-12-30**: Consolidated ARCHITECTURE_GOVERNANCE.md into GOVERNANCE.md, deleted 6 files
- This file is the authority; update this file when governance changes

---

**Remember**: Documentation is code. Treat it with the same discipline.