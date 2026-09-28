# Managed SDK and Sprig Library Dogfooding Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax.

**Goal:** Provide a safely versioned Linux/macOS Sprig SDK install and upgrade path, and prove the installed SDK can develop useful Sprig web, SQLite migration, and CLI libraries without expanding language syntax.

**Architecture:** A POSIX bootstrap installer downloads release ZIP/checksum assets, verifies and smoke-tests into `~/.sprig/versions/<tag>`, then atomically replaces `~/.sprig/current`; a PATH wrapper targets `current`. The Java stage-0 `sprig upgrade` reuses the same versioned-install contract and refuses unmanaged/source installs. Web policy is split into importable Sprig modules while preserving `@web/app.spr`; migrations and CLI parsing are Sprig libraries over existing std/JDBC primitives, with only the narrowly justified JDBC script primitive in Java. A packaged SDK is installed into a temporary HOME and used by PATH-only integration tests.

**Tech Stack:** Java 17, POSIX shell, Python test harnesses, ANTLR 4.13.2, existing Sprig modules/files/JSON/JDBC support, SQLite JDBC already pinned by the sample.

**Spec:** User request pasted in attachment `5cf5b53e-b0f6-4258-a647-8d281e641223`.

## Global Constraints

- Do not add syntax or semantic features.
- Keep source checkout, unmanaged ZIP, and managed install distinguishable.
- Linux/macOS are supported; Windows remains experimental.
- SDK install and upgrade must preserve the working version on every failure.
- Never edit a user's shell startup file or a project's manifest/lock as part of upgrade.
- Release checksum verification is mandatory before extraction/use.
- Sprig owns web refactoring, migration policy and CLI policy; Java remains bootstrap/JVM mechanics.
- Tests use local fake release assets and temporary SDK roots; no test depends on GitHub availability.
- Preserve existing behavior and tests; do not weaken golden assertions.

---

## Baseline

- [x] Fetch latest `main`, create `codex/sdk-library-dogfood` from it, and confirm PR #31 is merged.
- [x] Run `./scripts/verify.sh`: build and all gates through 76/77 test cases passed; `check_projects.py` timed out only for the unavailable Maven Central DNS endpoint. The exact suite passed when `SPRIG_MAVEN_REPOSITORY` pointed to a local empty repository. Independent remaining gates passed: 27 grammar cases, 20 docs snippets + VitePress/192 links, and all 17 VS Code tests + VSIX packaging. The deterministic-fixture test repair is included in Task 7 verification work.
- [x] Record current commit `f7f0018`, `sprig-compiler 0.3.0-alpha.1`, language `0.8-dev`, latest published SDK `v0.3.0-alpha.1`, and `sprig capabilities --json` in the final report.
- [x] Read AGENTS, README, feature/limitations, Web+SQLite report, libraries, std modules, package/release scripts, CLI and module implementation.

## Task 1: Versioned managed SDK installer

**Files:** create `scripts/install-sprig.sh`; create `tests/installer/check_installer.py`; optionally add a tiny fixture HTTP server under `tests/installer/`; update `scripts/verify.py` and `docs/INSTALL.md` or the existing install document.

**Contract:** `$HOME/.sprig/versions/<tag>/`, atomic `$HOME/.sprig/current` symlink, `$HOME/.local/bin/sprig` wrapper. Each version directory has `sprig-install.json` with `installationKind=managed`, `version`, and `sourceReleaseTag`. Existing unrelated launcher files are never overwritten. The installer prints exactly `export PATH="$HOME/.local/bin:$PATH"` when that directory is absent from PATH and does not edit shell startup files.

- [x] Write local-fixture tests for a valid archive, bad checksum, missing JDK, wrong smoke-test version, repeated install, and preservation of the existing `current` target on failures.
- [x] Run `python3 tests/installer/check_installer.py` and confirm the new cases fail against absent installer behavior.
- [x] Implement installer parsing for `--version`, fixed official GitHub release URLs, JDK 17+ check, SHA-256 check, staging extraction, `sprig version` smoke test, version metadata, launcher ownership check and atomic pointer change.
- [x] Re-run the focused fixture tests; inspect both the filesystem layout and PATH wrapper invocation.
- [x] Document install, upgrade PATH behavior, ownership metadata and supported platforms.

## Task 2: `sprig upgrade` safety

**Files:** modify `compiler/src/main/java/sprig/compiler/cli/Main.java`, add `compiler/src/main/java/sprig/compiler/cli/ManagedSdkUpgrade.java` (or a focused package peer), update command catalog/help and add `tests/upgrade/check_upgrade.py`.

**Interface:** `sprig upgrade` installs the newest published SDK release; `sprig upgrade --check` reports current/latest and exits successfully when already current. No other upgrade options are accepted. The updater requires a valid managed marker and an SDK under `~/.sprig/versions`; source checkout and unmanaged archive receive distinct actionable diagnostics.

- [x] Add fixture tests for managed check/no-op, newer release, corrupt checksum, invalid ZIP, smoke failure, source checkout refusal, unmanaged ZIP refusal, unknown option, preservation of `sprig.lock`, and old-version retention.
- [x] Run focused tests to observe failures before implementation.
- [x] Implement HTTP release discovery using JDK APIs, strict asset/tag selection, checksum validation, temporary version directory, smoke test, and atomic `current` symlink swap. Leave old versions untouched.
- [x] Run `python3 tests/upgrade/check_upgrade.py`; verify all failed install paths leave the prior symlink and project dependency bytes unchanged.
- [x] Add CLI/help/capability/docs entries without mislabeling Windows as supported.

## Task 3: Modularize `sprig-web`

**Files:** add `libraries/sprig-web/src/request_helpers.spr`, `response_helpers.spr`, and a small OpenAPI helper module only where dependencies remain acyclic; modify `app.spr`, library README/API metadata, and `tests/web/` if added assertions are needed.

- [x] Add module-level regressions through the existing web HTTP suite for query decoding, malformed JSON, OpenAPI output and route dispatch.
- [x] Run the focused web suite before refactoring and keep behavior as baseline.
- [x] Move low-level request parsing, response JSON construction and schema/OpenAPI assembly helpers behind internal local-module imports. Keep all public models and route-registration methods reachable from `web` (`@web/app.spr`) with unchanged signatures. Do not add re-export syntax or cyclic imports.
- [x] Run `python3 tests/web/check_web.py` and `python3 tests/sqlite/check_sqlite.py`.
- [x] Record any remaining source concentration and module-system pressure rather than adding language features.

## Task 4: Sprig-owned SQLite migrations

**Files:** create `libraries/sprig-sqlite/src/migrations.spr`, create `examples/sqlite_migrations/{sprig.toml,README.md,src/main.spr,migrations/*.sql}`, add tests under `tests/sqlite/`, and only if proven necessary extend `runtime/src/main/java/sprig/runtime/sqlite/Batch.java` and its Sprig adapter with atomic trusted-script execution.

**Interface:** `sqlite.Migrations(database=database, directory=path)` with `.apply()`. Migration filenames are unique ordered `NNN_name.sql`; UTF-8 contents are read through `@std/files`; applied version filenames are stored in `sprig_schema_migrations`; pending migration script and ledger insert commit in the same transaction. Re-running does no work. Applied migration files are immutable by documented convention; automatic content-drift hashing is outside this first migration layer.

- [x] Add real temporary-database integration tests for lexicographic order, first apply, restart/idempotence, migration SQL failure rollback and retry, malformed filenames, and SQL-injection-shaped filenames/content.
- [x] Run focused tests and identify whether existing `Database.batch` can run multi-statement scripts atomically. If it cannot, prove that with a failing fixture before adding one narrow host primitive.
- [x] Implement migration table, deterministic filename validation, UTF-8 reads, transactions and error propagation in Sprig; keep Java limited to the proven script execution primitive if required.
- [x] Run the SQLite migration integration and existing ledger suite, then document how to author immutable migrations.

## Task 5: Sprig CLI library and useful app

**Files:** create `libraries/sprig-cli/{sprig.toml,README.md,src/cli.spr}`; create `examples/json_select/{sprig.toml,README.md,src/main.spr,src/command.spr,src/transform.spr}`; add `tests/cli_library/`.

**Interface:** option specifications have canonical long names and optional one-character short names, with explicit flag/value kinds. Parse `std.process.arguments()` into typed `Map[String, OptionValue]` and positional `List[String]`; support `--name value`, `--name=value`, `-x`, and `--`; duplicate options are errors. `json-select --input FILE --key NAME [--output FILE]` parses an object, selects a present JSON member including JSON null, serializes it, and writes stdout or a file. Help is deterministic.

- [x] Add tests first for flags, separate/equals values, short options, positionals, delimiter, duplicates, unknown options, missing values, usage rendering, and end-to-end JSON selection/file output.
- [x] Verify each focused test fails for the intended missing behavior.
- [x] Implement parser/help in Sprig using current collections, variants, std process/files/json; implement the multi-file JSON utility.
- [x] Run CLI-library checks via current compiler and package resolution; document option rules and usage.

## Task 6: Installed SDK dogfood acceptance

**Files:** create `tests/dogfood/check_installed_sdk.py`; modify `scripts/verify.py` only as needed; update `docs/AGENT_GUIDE.md`, SDK package allowlist/docs list in `scripts/package-alpha.py`, examples index and final engineering report.

- [x] Build an SDK ZIP, install it under an isolated HOME from the local release fixture, and create a PATH containing only the managed launcher plus required JDK paths.
- [x] Assert `sprig version`, `sprig capabilities --json`, `sprig check` and `sprig run` use the installed SDK. The test harness may invoke Python to provision fixtures, but must not call repository `bin/sprig`, compiler classes, or build classpaths for dogfood project commands.
- [x] Run Web/ledger HTTP checks, SQLite migration restart/failure checks, and the JSON CLI utility through PATH-only SDK invocations.
- [x] Test `sprig upgrade --check` against same/newest local fake release, then test successful upgrade and all failure preservation paths.
- [x] Add final agent pressure evidence with pattern, count, workaround and classification. Keep ordinary verbosity separate from candidate language issues.

## Task 7: Documentation and final gates

**Files:** `docs/INSTALL.md`, `docs/KNOWN_LIMITATIONS.md`, `docs/DEPENDENCIES.md`, `docs/STANDARD_LIBRARY.md`, library/example READMEs, `docs/milestones/SDK_DOGFOOD_ENGINEERING_REPORT.md`, `docs/milestones/SDK_DOGFOOD_PRESSURE.md`, website install/toolchain/library pages and navigation, test drivers.

- [x] Document managed layout, ownership, checksum/smoke/swap/retention contract; clearly identify supported Linux/macOS and experimental Windows.
- [x] Document Web module split, migration API, CLI API and agent-discoverable commands/help.
- [x] Record baseline and final compiler/language/published versions, exact commands and results, Java vs Sprig source boundary, agent friction, unresolved pressure and failed/non-tested cases.
- [x] Run `./scripts/verify.sh`, package the SDK, and run installed SDK acceptance from a clean temporary prefix.
- [x] Run `git diff --check`; ensure no original design kit changes, generated outputs, local SDK databases, secrets or user-created duplicate files enter the commit.
