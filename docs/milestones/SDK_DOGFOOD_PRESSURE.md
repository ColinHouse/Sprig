# Managed SDK dogfood: agent pressure log

Pressure observed while implementing the managed SDK, `sprig-web` modules,
SQLite migrations and the CLI library. Classifications are one of:
library/API design, missing std helper, JVM adapter pressure, tooling/install
pressure, possible language pressure, ordinary verbosity — no change needed.

## Possible language pressure

### 1. Forwarding lambdas to adapt a method to a source function value

```sprig
server = HostServer(port, fn(raw: HostRequest) => dispatch(raw))
```

- Frequency: 1 in `sprig-web`; the pattern recurs in every web application
  (`fn(req: Request) => handler(req)`) and in tests.
- Workaround: write an expression lambda whose body immediately calls an
  existing named function/method.
- Safe and understandable: yes; the lambda has one explicit typed parameter
  and forwards to a checked method. It is repetitive, not error-prone.
- Non-goal for this milestone: named function references / block lambdas.

### 2. Explicit OpenAPI and JSON construction

```sprig
jsonlib.Value.Object(members=[jsonlib.Member(key="name", value=jsonlib.Value.Text(value=name)), ...])
```

- Frequency: 35 `jsonlib.Member(` constructions in `sprig-web`; the largest
  single source expression in `operation_json`.
- Workaround: nested constructor calls with explicit variant cases; schemas
  are explicit typed metadata.
- Safe and understandable: yes, but visually dense and easy to mis-nest.
  No tuples/destructuring/interpolation were added (all non-goals).

### 3. One-character text handling without a Char type

```sprig
if name.charAt(0) < "0" or name.charAt(0) > "9": ...
```

- Frequency: 9 `charAt` uses in `migrations.spr` and `cli.spr` filename/name
  validation.
- Workaround: treat `charAt` results as one-code-point `String` values and
  use String comparison. Safe and readable.
- This is the motivating evidence for the separate, explicitly approved
  String code-point semantics correction; no `Char` type is introduced.

## Library/API design

### 4. No static methods

`web.text`, `web.json_response`, `sqlite.open`, `cli.parse`, `cli.usage` are
module-qualified free functions rather than static methods.
- Frequency: every package entry point.
- Workaround: ordinary functions; constructors supply field defaults.
- Safe and understandable: yes. Recorded, not changed.

### 5. No module re-export

`sprig-web` keeps its public surface in `app.spr` and imports helper modules
internally (`request_helpers`, `routing_helpers`, `openapi_helpers`).
- Frequency: 3 internal modules; callers still use one import.
- Workaround: public declarations stay in the imported entry module.
- Safe and understandable: yes; helper modules are not exported to users.

### 6. Errors cannot propagate through function-typed callbacks

`fn(Request) -> Response` has no throws clause, so `App.dispatch` catches
`BadRequest`, `Error` and `RuntimeException` and maps them to responses.
- Frequency: one dispatch boundary; documented in the HTTP pressure record.
- Workaround: explicit typed catch at the host boundary.
- Safe and understandable: yes; no effect system was added.

## Missing std helper

### 7. Build a Mutable collection, then copy with `toList()`/`toMap()`

- Frequency: 13 `toList()`/`toMap()` calls and 22 `MutableList`/`MutableMap`
  occurrences across the new Sprig code.
- Workaround: append in a loop and convert once at the boundary, matching the
  existing std collection model.
- Safe and understandable: yes; ordinary verbosity, not a language blocker.

### 8. String building by concatenation

- Frequency: 36 `+` concatenations in the new library/example sources.
- Workaround: `+` with explicit separators, e.g.
  `"invalid migration filename: " + name + " (expected NNN_name.sql)"`.
- Safe and understandable: yes. Interpolation is a non-goal.

## JVM adapter pressure

### 9. Trusted multi-statement SQL scripts

JDBC `PreparedStatement` executes one statement; a migration file may contain
several statements plus trigger bodies with internal semicolons.
- Frequency: every migration apply (one primitive serves all).
- Workaround: narrow runtime primitive `Batch.addScript` +
  `Database.splitScript` inside the existing batch transaction; migration
  policy (order, ledger, idempotence) stays in Sprig.
- Safe and understandable: yes; the primitive is documented as trusted SQL
  and the splitter has dedicated quoted-semicolon/trigger tests.

### 10. Nullable host results

`required(raw.method())`, `required(raw.body())`, `required(raw.rawQuery())`
guard Java reference results.
- Frequency: 9 `required(` calls in `sprig-web`.
- Workaround: explicit local helper that raises `BadRequest`/`Error`.
- Safe and understandable: yes; nullability remains explicit.

## Tooling/install pressure

### 11. Bootstrap cannot be written in Sprig

Downloading a release over HTTPS, checking a digest, extracting a ZIP, and
atomically swapping a symlink require host mechanics (JDK HTTP/ZIP/NIO or
curl/unzip/shasum); `sprig upgrade` must also run from a possibly stale
installed SDK and must not replace JARs in the running process.
- Frequency: installer + upgrader, one-time and per-upgrade.
- Workaround: `scripts/install-sprig.sh` and
  `compiler/.../cli/ManagedSdkUpgrade.java` at the documented
  bootstrap/tooling boundary; all policy decisions and messages are
  deterministic and tested with local fixtures.
- Safe and understandable: yes; explicitly allowed Java boundary.

### 12. Handwritten library API metadata

`libraries/sprig-web/api.json` duplicates signatures that the compiler could
derive.
- Frequency: one file per library; drift risk is real.
- Workaround: keep behavior documentation in README and treat signatures as
  duplicated metadata for now.
- Safe and understandable: risky long-term; ecosystem-level `sprig api`
  introspection is the next milestone, not part of this one.
