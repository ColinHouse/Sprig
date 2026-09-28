# Examples

This directory contains programs and projects with an independent purpose:
things you can build and run with Sprig. It is not a syntax gallery.

Executable tutorials and syntax snippets live under
[`website/snippets/`](../website/snippets/), where the documentation gate runs
each program and compares it with a checked `.out` oracle. Regression fixtures
live under `tests/`. See
[the classification rule](https://github.com/ColinHouse/Sprig/blob/main/CONTRIBUTING.md#where-code-examples-live)
for how to choose between them.

## Application projects

Run `sprig resolve` from each project before check/run; the package libraries
live in [`libraries/`](../libraries/).

- [mini_web](mini_web/README.md) — typed routes, JSON, explicit OpenAPI and
  Swagger UI.
- [sqlite](sqlite/README.md) — pinned Maven JDBC driver and persisted prepared
  SQL.
- [ledger](ledger/README.md) — reduced accounts/categories/transactions HTTP
  backend with integer minor units and restart persistence.
- [sqlite_migrations](sqlite_migrations/README.md) — ordered, idempotent SQLite
  migrations with transactional rollback and retry.
- [json_select](json_select/README.md) — a multi-file JSON CLI using the
  Sprig option-parsing library.

## Showcases

Project-oriented programs exercised by the release gates:

- [repository_audit](showcases/repository_audit/README.md) — multi-module tree
  walker that counts source/text files and writes JSON.
- [maven_slug](showcases/maven_slug/README.md) — resolves a real Java library,
  queries its API and reuses the locked cache offline.
- [source_analyzer](showcases/source_analyzer/README.md) — Sprig frontend
  tooling with variants and exhaustive traversal.

## Tooling and bootstrap

These have wider SDK and tooling dependencies and are intentionally managed
separately from the gallery above:

- [agent_tools](agent_tools/README.md) — Sprig-written compiler API and
  diagnostic tools.
- [stage1_frontend_probe](stage1_frontend_probe/frontend.spr) — the stage-1
  frontend subset probe.
