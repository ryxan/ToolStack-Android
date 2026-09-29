# Archive — Original Planning Documents

These files (`prd.md`, `structure.md`, `techstack.md`, `design.md`) were written before
implementation began. They describe a planned feature set that diverged from what was
actually built.

**Do not treat these as the current state of the app.** Notable gaps:

- Wire Calculation, Ropes, Chains, and Components Selection were never implemented.
- Ratio Mix was built instead (not mentioned here).
- A multi-module Gradle layout (`app/`, `core/`, `feature/`, `data/`) was never adopted —
  the app is a single `:app` module.
- Billing / premium (Google Play Billing, server-side verification) was added — not mentioned here.
- The drag-to-reorder home screen and shortcut system were added — not mentioned here.

For the current architecture, see `.kiro/steering/project-conventions.md`.
