# Finalized Money Stories product architecture

The app is now organized around four primary destinations:

- **Home** — expense-first summary, recent activity, story previews, and a quiet optional entry point for the wider money picture.
- **Transactions** — the complete expense workspace: calendar, date-based activity, editing, deletion, missing-expense capture, and AI-specific expense cleanup.
- **Stories** — one evidence-backed story feed across every enabled financial area. The current integration is spending stories; future domains are clearly reserved through source labels and filters.
- **You** — privacy controls and the settings that determine which domains may contribute to stories.

## Data interaction rules

Expense capture is special: it is AI-assisted and therefore has review, edit, missing-expense, and normalization tools. Normalization is intentionally named **Expense cleanup** in the UI and is never shown as a generic money feature.

Investments, emergency funds, goals, and budgets are opt-in modules. They will use structured manual forms and their own normal edit/delete controls. They will not use AI cleanup or normalization. Until their APIs are introduced, the application exposes the module architecture and privacy copy without pretending that data is persisted.

The reference visuals are `full-flow-01-expense-workspace.png`, `full-flow-02-optional-money-modules.png`, and `full-flow-03-stories-and-control.png` in this folder.
