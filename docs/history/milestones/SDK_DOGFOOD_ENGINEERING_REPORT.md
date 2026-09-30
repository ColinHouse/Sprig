# Managed SDK and library dogfood: engineering report

Baseline commit `f7f0018`; compiler `0.3.0-alpha.1`; language `0.8-dev`;
latest published SDK `v0.3.0-alpha.1`. `./scripts/verify.sh` passed after the
change (build, compiler/JVM suites, grammar, executed docs, editor).

## A. Managed installation contract

```text
~/.sprig/
  versions/
    v0.3.0-alpha.1/
      bin/sprig ...                    extracted release contents
      sprig-install.json               installation metadata
    .install.XXXXXX/                   staging, removed on success or failure
  current -> versions/v0.3.0-alpha.1   atomic symlink, the active SDK
~/.local/bin/sprig                     installer-owned PATH launcher
```

`scripts/install-sprig.sh` owns this layout; the Java `sprig upgrade` command
owns the same contract. `sprig-install.json` is written into each version
directory and records `installationKind: "managed"`, `version`,
`sourceReleaseTag` and `sdkArchiveSha256`. Ownership rules:

- Installation is user-scoped; no root and no Maven CLI are used.
- `current` is only ever a symlink under `~/.sprig/versions`; a real file or a
  link escaping that directory is refused, not replaced.
- `~/.local/bin/sprig` is created only when absent. A launcher without the
  `# Sprig managed SDK launcher` marker is never overwritten; the installer
  fails with a deterministic message instead.
- Shell startup files are never edited. If the launcher directory is not on
  `PATH`, the installer prints exactly
  `export PATH="$HOME/.local/bin:$PATH"`.
- A manually extracted SDK ZIP and a Git source checkout carry no marker and
  are not managed installs; the upgrader refuses both with distinct
  instructions (Git rebuild vs. run the installer).

## B. Upgrade safety

`sprig upgrade` and `sprig upgrade --check` are implemented in
`compiler/src/main/java/sprig/compiler/cli/ManagedSdkUpgrade.java` (Java, the
bootstrap/tooling boundary). `--check` only queries the release API and prints
either "already the latest" or "`vX` is installed; `vY` is available"; it
downloads nothing.

Upgrade sequence:

1. Validate this SDK: locate `~/.sprig`, reject `.git` checkouts, reject
   layouts that are neither `.../current` nor `.../versions/<tag>`, require
   managed metadata and a `current` symlink whose real target stays inside
   `versions`.
2. Resolve the newest published `v*` release tag from the GitHub release API.
   If the active tag is newest, this is a clean no-op.
3. Download the checksum asset first; it must be one `sha256` line naming the
   exact archive. Download the archive (256 MiB cap), verify SHA-256.
4. Extract into a staging directory under `versions/` with path traversal,
   duplicate-entry, absolute-path and 768 MiB expanded-size checks.
5. Smoke-test `bin/sprig version` with a 30 s timeout and require the reported
   version to equal the requested tag.
6. Publish the version directory only by atomic rename. If the directory
   already exists, it is reused only when its marker, source tag and recorded
   digest all match and its smoke test passes; otherwise upgrade fails.
7. Switch `current` by creating a temporary symlink and atomically replacing
   the existing one (`ATOMIC_MOVE`; `mv -fT`/`mv -fh` in the shell installer).

Any failure before step 7 leaves the previous `current` untouched; staging and
pointer temporaries are deleted in `finally`. Previously installed versions are
retained. Upgrade never reads or writes a project's `sprig.toml` or
`sprig.lock`, and it never replaces JARs of the currently executing process;
the switch is a pointer change instead. Checksum verification establishes
release/transport consistency, not signed provenance.

## C. Sprig-written surface

| Component | Sprig policy | Java/bootstrap boundary |
|---|---|---|
| `sprig-web` | 409 lines across `app.spr` (316), `openapi_helpers.spr` (42), `request_helpers.spr` (33), `routing_helpers.spr` (18): methods, schemas, routing, CORS, errors, OpenAPI, dispatch | Pre-existing `sprig.runtime.web` host: sockets, exchange lifetime, UTF-8 decode, byte writing |
| `sprig-sqlite` migrations | `migrations.spr` (54): ordering, ledger, idempotence, ordering-drift detection, filename validation, transactions | `Batch.addScript` + `Database.splitScript` (runtime, ~70 lines): JDBC cannot execute multi-statement scripts through `PreparedStatement`, and trusted script splitting needs SQL lexical scanning |
| `sprig-cli` | 155 lines: specs, duplicate/unknown/missing-value policies, usage | none |
| `json-select` | 54 lines across `main.spr`, `command.spr`, `transform.spr` | none |
| Managed install | none: bootstrap must work before any Sprig exists | `install-sprig.sh` (POSIX sh; curl/unzip/shasum) and `ManagedSdkUpgrade.java` (JDK HTTP client, ZIP, atomic move, process smoke test) |

The only new Sprig-facing Java primitive is trusted multi-statement batch
execution. `Database.batch` previously accepted one prepared statement per
command; a migration must execute an arbitrary trusted `.sql` script and its
ledger insert in one transaction. Splitting SQL text around quotes, comments,
trigger `BEGIN ... END` bodies and `CASE` requires byte-level lexical scanning
that the current Sprig string/collection APIs cannot express over JDBC
statements, so the narrow primitive was added at the documented JDBC boundary.
Migration policy itself (which files to apply, order, ledger, drift checks)
remains in Sprig.

## D. Agent experience

- `sprig capabilities --json` lists `upgrade`; `sprig help upgrade --json`
  gives syntax, rules and an example; the topic is in the packaged docs.
- `bin/sprig upgrade --check` outside a managed install fails with one exact
  instruction: source checkout → `git`/`scripts/build.sh`; unmanaged ZIP →
  `scripts/install-sprig.sh`.
- Metadata and layout checks produce deterministic messages rather than
  partially modifying an SDK.
- `AGENT_GUIDE.md` and `docs/INSTALL.md` are inside the release archive, so
  the installed SDK carries its own install/upgrade instructions.
- `tests/dogfood/check_installed_sdk.py` builds a local release fixture,
  installs it into an isolated `HOME`, and then runs `sprig version`,
  `sprig capabilities --json`, `sprig check`, `sprig run`, the ledger HTTP
  checks, SQLite migration restart/failure checks, `json-select`, and
  `sprig upgrade --check` using only the managed launcher on `PATH`. It does
  not call repository `bin/sprig`, compiler classes or build classpaths.

## E. Language pressure

Observed friction is recorded in
[`SDK_DOGFOOD_PRESSURE.md`](SDK_DOGFOOD_PRESSURE.md). No syntax or semantics
were added; the recurring items are expression-lambda forwarding, explicit
JSON/OpenAPI construction, string concatenation, list build/`toList()` copies,
nullable host guards, and the absence of static methods/re-exports. The
graduated semantics requested by the user (String code-point indexing and
iteration) are tracked as a separate correction milestone, not as part of this
one.

## Verification actually run

- `python3 tests/installer/check_installer.py` — checksum, ZIP-path safety,
  smoke, switch, retention, PATH and launcher ownership passed.
- `python3 tests/upgrade/check_upgrade.py` — latest check, no-op, verified
  switch, checksum/ZIP/smoke failure preservation, install-kind refusal and
  project immutability passed.
- `python3 tests/cli_library/check_cli_library.py` — parsing and usage passed.
- `python3 tests/sqlite/check_migrations.py` — order, idempotence, quoted
  semicolons/triggers, rollback/retry, filename validation passed.
- `python3 tests/web/check_web.py` — real HTTP, Unicode, CORS, OpenAPI passed.
- `python3 tests/dogfood/check_installed_sdk.py` — installed PATH-only
  acceptance passed.
- `./scripts/verify.sh` — passed (build, full compiler/JVM suites, grammar,
  executed docs, editor).
