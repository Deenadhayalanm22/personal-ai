# Documentation Editing Rules (Authoritative)

## Purpose
This document establishes **THE SINGLE SOURCE OF TRUTH** for documentation governance in this repository. All contributors MUST follow these rules when updating or creating documentation.

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
| Integration testing | `INTEGRATION_TESTING.md` | Testing framework, patterns, rules |
| Integration testing rules template | `INTEGRATION_TESTING_RULES_TEMPLATE.md` | Template for adding new testing rules |

## Prohibited Documentation Patterns

The following are **STRICTLY FORBIDDEN**:

### ❌ DO NOT Create
- `*_SUMMARY.md` files (summaries belong in canonical docs)
- `*_UPDATED.md` files (update the canonical doc in place)
- `*_V2.md` or versioned docs (use git history for versions)
- `*_IMPLEMENTATION.md` for features (merge into appropriate canonical doc)
- `*_FINAL.md` files (there is no "final", only current)
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
- This file is the authority; update this file when governance changes

---

**Remember**: Documentation is code. Treat it with the same discipline.
