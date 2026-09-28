# Documentation map

This page defines which document to trust. Read it before following any other
document, and prefer the most specific current source over summaries.

## Implemented behavior — canonical order

1. `grammar/`, `compiler/`, `runtime/` — the implementation itself.
2. `docs/` contract pages below — implemented behavior in prose, verified by
   `scripts/test.py` and the release gates.
3. Query the checkout directly for the fastest current answer:
   `bin/sprig capabilities --json`, `bin/sprig help <topic> --json`,
   `bin/sprig api <Java.Class> --json`, `bin/sprig explain <SPR-CODE> --json`.

`spec/` is the older design kit. It records target semantics and design
discussions; it is **not** a guarantee of implemented behavior. When `spec/`
and `docs/` disagree, `docs/` and the compiler win. `spec/README-DESIGN-KIT.md`
describes the kit's own scope.

## Current contract pages (`docs/`)

| Area | Pages |
|---|---|
| Language status and orientation | `FEATURE_STATUS_IMPLEMENTED.md`, `QUICK_REFERENCE.md`, `KNOWN_LIMITATIONS.md` |
| Syntax features | `FORMATTER.md`, `MATCH_EXPRESSIONS.md`, `MODULE_REEXPORTS.md` |
| Type and value contracts | `GENERICS.md`, `NUMERIC_SEMANTICS.md`, `NUMERIC_DESIGN_DECISIONS.md`, `JVM_INTEROP.md` |
| Projects and dependencies | `PROJECTS.md`, `DEPENDENCIES.md`, `STANDARD_LIBRARY.md`, `HOST_SERVICES.md` |
| Tooling and operations | `DIAGNOSTIC_CODES.md`, `INSTALL.md`, `SHOWCASES.md` |
| Future work | `STAGE1_ROADMAP.md` |

## Release history (`docs/releases/`, `docs/RELEASE_VALIDATION.md`)

`docs/releases/` keeps one note per published version and is historical.
`RELEASE_VALIDATION.md` is the validation record for the most recent published
release; a newer release replaces it. Neither is a contract page: use the
current `docs/` pages and `capabilities --json` for what the checkout does.

## Engineering history (`docs/milestones/`)

Milestone reports and pressure reports are evidence of what was built, tried and
measured at a point in time. `docs/milestones/plans/` holds the completed
working plans that produced them. They may describe intermediate states, failed
approaches or counts from an older commit; do not treat them as current
behavior. Start with the report, not the plan.

## Process documents

- Repository contribution rules and agent workflow: `AGENTS.md` (repo root),
  `CONTRIBUTING.md`, `docs/contributing/`.
- SDK usage for coding agents: `AGENT_GUIDE.md` (repo root, also shipped in the
  SDK).
- AI provenance: `AI_DISCLOSURE.md`.

## Website

`website/` is the presentation layer. Its English reference pages are generated
from `docs/` and `spec/` by `website/scripts/sync-reference.mjs`; edit the
canonical documents, never the generated copies. Guides under `website/en/` are
hand-written for onboarding and may simplify contracts.
