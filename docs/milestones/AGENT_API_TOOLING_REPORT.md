# Agent API introspection and tooling: engineering report

Baseline for this milestone: commit `5d5cea4` (after the managed-SDK and
String-code-point milestones); compiler `0.3.0-alpha.1`; language `0.8-dev`;
latest published SDK `v0.3.0-alpha.1`. No language syntax was added.

## A. Agent query surface

Stable, offline-capable queries (all JSON unless noted):

| Query | Purpose |
|---|---|
| `sprig capabilities --json` | commands, types, feature booleans, `featureGuidance`, `stringSemantics` |
| `sprig help <topic> --json` | syntax/rules/examples for 18 topics incl. `strings`, `agents`, `upgrade` |
| `sprig api <Java.Class> [--member N] --json` | JVM reflection metadata (unchanged) |
| `sprig api <file.spr> [--member Type.member] --json` | resolved Sprig module API |
| `sprig api @pkg/module.spr [--member Type.member] --json` | project- and export-resolved Sprig module API |
| `sprig api . --json` | project inventory: source modules + exported dependency modules |
| `sprig project --json`, `sprig deps --json` | manifest/lock/dependency state (unchanged) |
| `sprig check/build/run --json` | diagnostics with optional `relatedHelp`/`repair` |
| `sprig explain <CODE> --json` | meaning, why it matters, confused-with, causes, fixes, examples, repair, related help |
| `sprig upgrade --check` | managed SDK status (unchanged) |

New stable codes: `SPR-API-TARGET`, `SPR-API-MEMBER`; both documented in
`docs/DIAGNOSTIC_CODES.md` and available through `explain`.

## B. Sprig module introspection

`@web/app.spr` (excerpt; `sprig api @web/app.spr --json` from `examples/ledger`):

```json
{
  "kind": "class", "name": "FieldSchema",
  "fields": [
    {"name": "name", "type": "String", "mutable": false, "required": true},
    {"name": "schema", "type": "Schema", "mutable": false, "required": true}
  ],
  "methods": []
}
```

`@sqlite/sqlite.spr` (excerpt):

```json
{
  "kind": "variant", "name": "Parameter",
  "cases": [
    {"name": "Integer", "fields": [{"name": "value", "type": "Int", "mutable": false, "required": true}]},
    {"name": "Text", "fields": [{"name": "value", "type": "String", "mutable": false, "required": true}]}
  ]
}
```

Function where applicable:

```json
{"kind": "function", "name": "apply", "parameters": [], "result": "Int", "throws": ["Error", "IOException"]}
```

Project inventory (`sprig api . --json` in `examples/ledger`) reports
`handlers.spr`, `main.spr`, `store.spr`, `values.spr` as `origin: "source"`,
and `@web/app.spr`, `@sqlite/sqlite.spr`, `@sqlite/migrations.spr` as
`origin: "dependency"` with `exported: true`. Unexported helper modules are
absent. Stale locks, missing modules, missing aliases and unexported modules
are refused with their existing stable codes (`SPR-PROJECT-LOCK-STALE`,
`SPR-DEP-NOT-FOUND`, `SPR-PROJECT-NOT-EXPORTED`). Import cycles are refused
with `SPR-NAME-IMPORT-CYCLE`. Nothing executes application code.

## C. Repair metadata

The stable diagnostic schema is unchanged; two optional fields were added.

Before (schema, and still for unrelated codes):

```json
{"code": "SPR-TYPE-NULLABLE", "phase": "TYPE", "severity": "error",
 "message": "Cannot access 'length' on a value that may be null", "related": [], "suggestedEdits": []}
```

After:

```json
{"code": "SPR-TYPE-NULLABLE", "phase": "TYPE",
 "relatedHelp": "nullability",
 "repair": {"kind": "narrow-before-use", "machineApplicable": false}}

{"code": "SPR-NUM-DIVISION", "phase": "TYPE",
 "expectedType": "explicit division", "actualType": "Int / Int",
 "relatedHelp": "numerics",
 "repair": {"kind": "use-divTrunc-only-if-truncation-is-intended", "machineApplicable": false}}

{"code": "SPR-COLLECTION-IMMUTABLE", "phase": "TYPE",
 "relatedHelp": "collections",
 "repair": {"kind": "convert-with-toMutableList-or-toMutableMap", "machineApplicable": false}}
```

Every current hint is `machineApplicable: false` because none of these fixes is
semantics-preserving without a human/agent decision. `explain` returns the same
`repair` object plus `whyMatters`, `confusedWith`, causes, fixes and both
examples; for example `SPR-COLLECTION-IMMUTABLE` reports that Java and Python
list mutation are the common confusions and that `toMutableList()` is the
canonical fix.

`capabilities --json` now includes `featureGuidance`, for example:

```json
"inheritance": {"supported": false, "alternatives": ["composition", "narrow Java host adapter"], "helpTopic": "classes"}
```

covering inheritance, interfaces, arbitrary Java SAM, generic inference,
match expressions, wildcard match, arrays, varargs, annotations, decorators,
macros, block lambdas, named function references, reflection-derived schemas,
async, Comparable, variance, operator overloading, pipelines, string
interpolation, `Char`, tuples, destructuring and the absent package registry.

## D. Dogfooding

New peripheral tools are written in Sprig, not Java/Python:

| Tool | Source | Purpose |
|---|---|---|
| `api-report` | `examples/agent_tools/src/api_report.spr` + `meta.spr` | deterministic Markdown from saved `sprig api --json` |
| `diag-summary` | `diag_summary.spr` + `meta.spr` | group saved diagnostics by severity/phase/code |
| `api-diff` | `api_diff.spr` + `meta.spr` | simple textual public-signature diff of two snapshots |

`meta.spr` (200 lines) does explicit JSON walking and signature rendering using
only `@std/json`, and all three bins use the Sprig `@cli` option library.
`libraries/sprig-web/api.json` (handwritten signature duplication) was replaced
by `libraries/sprig-web/policy.json`, which carries only behavior policy
(routing, query, CORS, errors, OpenAPI, host, Swagger) and points at
`sprig api @web/app.spr --json` for authoritative signatures.

Remaining non-Sprig components and why:

- `SprigApi.java` and the `Main.java` wiring: extraction requires compiler
  AST/symbol/type information, which is exactly the allowed Java boundary.
- `tests/agent_eval/run_tasks.py` and `check_task_pack.py`: process
  orchestration, filesystem overlays and subprocess timeouts; Python is
  explicitly allowed by the milestone for the outer benchmark harness.
- `tests/agent_tooling/check_sprig_api.py` and `check_agent_tools.py`: test
  harnesses that invoke the CLI and assert JSON; they are test code, not
  product tooling.
- `examples/agent_tools` itself is Sprig, including its multi-file policy.

## E. Formatter

**No formatter shipped.** A formatter that preserves comments cannot be built
on the current pipeline, and the milestone forbids faking one.

Smallest concrete reproducer:

```sprig
class Thing:  # keep this comment
    let value:Int=1
```

The token stream available to any tool has already discarded `# keep this
comment` and the spacing: `grammar/SprigLexer.g4` declares
`COMMENT: '#' ~[\r\n]* -> skip;` and `SPACE: [ ]+ -> skip;`, and
`compiler/.../front/LayoutTokenSource.java` consumes that skipped stream to
synthesize `NEWLINE`/`INDENT`/`DEDENT`. The AST (`ast/`) records spans only, so
there is no trivia to re-emit; a canonical `sprig fmt` needs a
trivia-preserving lexer mode plus comment attachment before it can round-trip
existing source safely. This is recorded in `docs/KNOWN_LIMITATIONS.md` and is
the recommended prerequisite for a future formatting milestone.

## F. Agent benchmark readiness

`tests/agent_eval` is a deterministic starter pack; **no model has been run and
no model performance is claimed**.

| Task | Mechanical acceptance |
|---|---|
| 01_collections | exact stdout `total=3` / `positive=3`; no hardcoded answer, no Java |
| 02_nullability | compiles after narrowing; stdout `sprig` |
| 03_variants | exhaustive match added; stdout `12`/`9` |
| 04_generics | explicit `Box[Int]`; stdout `42` |
| 05_json_transform | `@std/json` + `@std/process`; stdout `items=3` for a fixture |
| 06_unsupported_recovery | composition instead of inheritance; exact stdout; no Java |
| 07_module_repair | multi-module project checks after import fix; stdout `16` |

`run_tasks.py` overlays a submission on each task's `initial/`, sets `SPRIG` to
the chosen SDK launcher, runs the hidden `accept.py`, and scores one point per
task. `tests/agent_eval/check_task_pack.py` proves every initial state fails
and every known-good solution passes (7/7 each), so the pack cannot silently
rot into a pre-solved or unsatisfiable state. The runner works against the
repository launcher or an installed SDK (`--sdk ~/.sprig/current`).

## G. Language pressure

Recurring patterns while writing `meta.spr` and the tools (recorded, not
fixed):

1. **Exhaustive match over closed JSON variants** — every `json.Value` match
   enumerates all six cases even when only `Text` matters; `json.Lookup` adds
   three more. Frequency: 6 matches in ~200 lines. Workaround: explicit
   fallback branches. Safe, but the dominant verbosity source.
2. **No map/filter over strings/keys** — `sort_strings` is a hand-written
   insertion sort and grouping uses `bump` helpers. Frequency: 3 helpers.
   Workaround safe and understandable; a future std helper, not syntax.
3. **Nullable plumbing** — `or_empty`/`or_zero` wrappers recur around
   `MutableMap.get`. Frequency: ~10 uses. Safe.
4. **String concatenation** — every rendered line is `+` concatenation
   (interpolation remains a non-goal).
5. **No tuples/destructuring** — `Surface` exists mainly to pair key/value,
   and diffing re-derives keys from strings.
6. **Module-level `generic` is reserved** — a local named `generic` fails to
   parse; trivially renamed. Ordinary, no language change needed.

None of these justified new syntax; no language change was made.

## Verification

- `python3 tests/agent_tooling/check_sprig_api.py` — 27/27 checks.
- `python3 tests/agent_tooling/check_agent_tools.py` — 8/8 checks.
- `python3 tests/agent_eval/check_task_pack.py` — 7 initial states fail and
  7 known solutions pass.
- `tests/dogfood/check_installed_sdk.py` — installed-SDK `version`,
  `capabilities` (`stringSemantics`, `featureGuidance`), `api` module/project,
  agent tools, ledger HTTP, migrations, json-select, `explain`,
  `help agents` and `upgrade --check`.
- `./scripts/verify.sh` — full contributor gate.
