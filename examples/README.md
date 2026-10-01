# Examples

This directory contains programs with an independent purpose, not a syntax
gallery. Begin with the [executable bilingual tutorial](../website/tutorial.md).
Tutorial snippets live under `website/snippets/`; regression fixtures live in
`tests/`.

Run `sprig resolve` in each project before checking or running it. Libraries
used by examples are in [`libraries/`](../libraries/).

## Start here

- [task-tracker](task-tracker/README.md) — local JSON CLI; no network service
  or external package dependency.
- [json_select](json_select/README.md) — multi-file JSON CLI using
  `sprig-cli`.
- [config_summary](config_summary/README.md) — fixture-driven UTF-8 JSON
  summary with deterministic output and malformed-input evidence.
- [agent_tools](agent_tools/README.md) — compiler API and diagnostic tools
  written in Sprig.

## JVM applications

- [application_foundation](application_foundation/README.md) — HTTP, JSON,
  time and files with first-party libraries.
- [sqlite](sqlite/README.md) — pinned Maven JDBC driver and prepared SQL.
- [sqlite_migrations](sqlite_migrations/README.md) — ordered transactional
  migrations with rollback and retry.
- [ledger](ledger/README.md) — reduced accounts/categories/transactions HTTP
  backend with integer minor units and restart persistence.
- [mini_web](mini_web/README.md) — typed routes, JSON, OpenAPI and Swagger UI.
- [test_runner](test_runner/README.md) — runtime, table, temporary-file,
  subprocess and expected-diagnostic tests using `sprig test`.

## Compiler and ecosystem cases

- [repository_audit](showcases/repository_audit/README.md) — multi-module tree
  walker that counts source/text files and writes JSON.
- [maven_slug](showcases/maven_slug/README.md) — resolves a real Java library,
  queries its API and reuses the locked cache offline.
- [source_analyzer](showcases/source_analyzer/README.md) — Sprig frontend
  tooling with variants and exhaustive traversal.
- [stage1_frontend_probe](stage1_frontend_probe/frontend.spr) — a bounded
  source-level probe for bootstrap planning, not a self-hosted compiler.

[Fabric/Loom integration](../website/guide/fabric.md) has a runnable starter in
[`libraries/sprig-fabric`](../libraries/sprig-fabric/README.md), backed by the
generic [`sprig-gradle` plugin](../libraries/sprig-gradle/README.md). Each
project README lists commands and known limits; examples demonstrate only the
APIs exercised by their own tests.
