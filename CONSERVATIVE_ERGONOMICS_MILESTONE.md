# Conservative ergonomics milestone

## 1. Baseline and scope

Starting commit: `d04a885` on `codex/sdk-library-dogfood`.
The managed SDK, Unicode code-point strings and Sprig API-introspection commits
were already present and were preserved. The working tree was clean.

`./scripts/verify.sh` passed before the feature integration: **87 aggregate
suite/case gates, zero failures**, 26 independent grammar fixtures, 20 executed
documentation snippets, VitePress/link checks, and 17 editor tests plus VSIX
packaging. No current baseline failure was observed. An earlier shared-build
class-loading failure did not reproduce; this run does not establish its cause.

Host evidence: macOS/Darwin, OpenJDK 26.0.1; compiler and generated Java use the
existing Java 17 target. Linux, Windows and a JDK 17 runtime were not executed
locally in this milestone. The compiler version remains `0.3.0-alpha.1` in
`0.8-dev`; no release/tag/Marketplace publication was performed.

Three feature commits, in the requested order:

1. `Add trivia-preserving formatter`
2. `Add explicit module re-exports`
3. `Add match expressions`

An additional test/report commit records cross-feature hardening. `spec/`,
integer semantics, nullability, generic inference and effect contracts were not
redesigned. No requested-out-of-scope feature was added.

## 2. Formatter architecture

COMMENT and SPACE are retained on the ANTLR hidden channel. Both the compiler
layout adapter and independent grammar harness explicitly consume only semantic
(default-channel) tokens. Physical token columns still determine indentation;
TAB and invalid-character diagnostics remain unchanged. Comments and whitespace
never become fields on semantic AST nodes.

`SourceFormatter` consumes raw tokens and physical line structure, with a block
column stack and grouping depth. Inline comments stay on the same line;
standalone comments keep their order and map their original column to the
surrounding block stack. Literal token text is copied exactly, including Unicode
strings, numeric spelling and exponent signs.

Canonical style: four-space blocks, one extra continuation level, spaces around
infix/assignment operators and arrows, one space after commas and colons, no
spaces just inside delimiters, two spaces before inline comments, at most one
blank line, and a final newline for nonempty files. Unary signs remain unary.
There is no configuration, wrapping, declaration sorting or expression rewrite.

The source must parse first. After formatting, rule identities and terminal
sequence/text must match the original parse, ignoring spans. This is stricter
than merely checking that both files parse and establishes equivalent input to
the deterministic AST builder; it is not a claim of a formal semantic proof.

`fmt <file|directory>`, `fmt .`, `--check` and `--json` are implemented. Project
selection honors the manifest source directory. Build/cache directories are
excluded. Check mode writes nothing. The JSON diagnostic envelope adds
`checkedFiles`, `changedFiles` and `checkOnly`.

All selected files are validated before writes. Each changed regular file is
replaced atomically using a neighboring temporary file, with POSIX permissions
preserved; unchanged files retain their timestamp. Symlink replacement is
refused. Multiple replacements are not one filesystem transaction: an I/O
failure after an earlier successful replacement can leave a partially formatted
batch, but cannot overwrite a file with an incomplete formatted string.
`check`, `build` and `run` do not silently format source.

## 3. Declaration re-exports

```sprig
import "./internal.spr" as internal
export internal.Widget
```

Module order is imports, exports, ordinary declarations/statements.
Export is a contextual keyword, preserving pre-existing identifiers named
export. The AST builder validates placement with `SPR-MODULE-EXPORT-ORDER`; allowing placement
in the parser's recovery grammar does not imply semantic acceptance.

`NameResolver` resolves explicit names in dependency order before resolving
local signatures. Classes, enums, variants, functions and importable top-level
values share the original Symbol. Facades create neither wrapper declarations
nor copies of mutable values. Generator module-member references point to the
defining module, including assignments to mutable exported values.

Unknown aliases/members, Java targets, builtin pseudo-declarations, duplicate
names and local/alias collisions receive `SPR-MODULE-EXPORT`. Conflicts report
origin filenames and the existing related declaration span. General module
cycles already prevent reexport cycles (`SPR-NAME-IMPORT-CYCLE`). A 20-layer
chain terminates and executes correctly; this is not a graph performance benchmark.

Manifest exports continue controlling external module paths. A public facade
can expose its own internal declarations, while consumers cannot directly
import or query the unexported internal module. Resolver/cache behavior is
unchanged. `sprig api` and `--member` include the original signatures, with
`reexported: true` and `originModule` relative to the facade. A module that throws
at top level was inspected successfully without execution.

No wildcard, renaming, export-import shorthand, implicit export or visibility
modifier was introduced.

## 4. Expression-match semantics

`Expr.Match` is separate from statement match. It uses the existing resolved
case/binder plan (`Stmt.Match`/Branch) as shared internal representation; branch
bodies contain exactly one ExprStmt produced by the expression-only grammar.
Semantic resolution and match ownership/exhaustiveness/narrowing checks are
shared, with a branch-check callback for expression result typing.

With expected type, every branch uses ordinary contextual checking and
assignability. Without context, the first successful non-null result establishes
the candidate; subsequent non-null results must be assignable to it. Explicit
null makes the inferred result nullable using existing `NullableType.of` rules.
All-null results require context (`SPR-MATCH-INFERENCE`). Incompatible ordinary
results receive `SPR-MATCH-RESULT` with expected/actual types; existing null and
numeric diagnostics may supply a more specific error. No union/common-supertype
inference or lossy numeric promotion was added. Unit-valued expression matches
are rejected; statement match supplies the side-effect-only form.

Binders are branch-local. Field-initializer restrictions are retained through
child branch/lambda scopes; explicit local binders may still shadow a field.
Each branch is checked normally, so recoverable throws
from any branch must be declared/caught even when a literal enum chooses another
branch at runtime. Mutable-capture checking traverses the scrutinee and branches,
including both parsed and semantically resolved subscript indices.

Lowering uses a Java 17 `switch (0)` expression with one default block, an
explicitly typed scrutinee temporary, existing enum/variant tests and conditional
`yield` results. It creates no closure and therefore does not capture mutable
locals or translate checked throws through a lambda. Sprig chooses result types
before Java generation; existing conversion/boxing helpers adapt each yield.
Erased generic payloads and return results are tested with Int, Int32, Bool,
String and nullable generic instantiations.

Direct initializer, return, class-field default, nested branch and expression
lambda positions work. Indentation blocks inside grouping delimiters are not
supported by the current layout adapter: bind the result before passing it to a
call/list/parenthesized operand. Branches are not arbitrary suites. Empty,
multi-statement and declaration branches receive syntax diagnostics; branch
recovery retains statement-match repair guidance where possible. Existing
statement match is selected explicitly when a statement starts with MATCH.

## 5. Executed tests

First integration gate: 94 aggregate suite/case gates passed, one failed.
The Agent tools test reused an ignored ledger lock created before web's manifest
changed. The compiler correctly reported `SPR-PROJECT-LOCK-STALE`. The fixture
now explicitly resolves offline before inspection; stale-lock enforcement and
API assertions remain intact. A separate compatibility regression was also
caught: initially reserving EXPORT broke old identifiers. A failing legacy
variable/parameter/field test drove the contextual-keyword repair.

Two further adversarial checks found and repaired scope gaps: expression-match
child scopes lost field-initializer restrictions, and the existing mutable
capture traversal omitted the newer Subscript node. Before repair the first
incorrectly passed `check`; the second passed `check` and failed at javac for a
non-final lambda capture. They now produce `SPR-NAME-UNRESOLVED` and
`SPR-TYPE-CAPTURE`, respectively. A positive field-default binder-shadowing case
guards against over-restricting legitimate locals. The intermediate second gate
was stopped after dependency tests because its early match suite predated these
new checks; it is not reported as a complete pass.

The next complete compiler/JVM test stage reported **95 aggregate gates/cases
passed, zero failed**, including both scope fixes. Its documentation stage then
stopped: four new diagnostic codes appeared in the code registry and prose but
had no rows in the mandatory code table. `tools/check-tooling-consistency.py`
first named `SPR-MATCH-INFERENCE`; a full registry/table comparison identified
`SPR-MATCH-RESULT`, `SPR-MODULE-EXPORT` and `SPR-MODULE-EXPORT-ORDER` too.
All four table rows were added without weakening the checker.

Final full verification after the table repair: `./scripts/verify.sh`
**passed (exit 0)**. The compiler/JVM stage reported **95 aggregate gates/cases,
zero failed**. The independent grammar harness accepted/rejected **31 fixtures**
(syntax evidence only). The documentation stage executed **20 snippets**,
checked tooling/release metadata, built VitePress, and verified **217 link
targets across 121 Markdown files**. The editor stage passed **18 tests** and
packaged `sprig-language-0.1.0.vsix`. These counts describe separate stages and
are not added together as a purported number of compiler tests.

Focused commands actually executed successfully:

```sh
python3 tests/formatter/check_formatter.py
python3 tests/reexports/check_reexports.py
python3 tests/match_expression/check_match_expression.py
python3 tests/web/check_web.py
python3 tools/test-grammar.py
python3 scripts/check-docs.py
VSCODE_EXECUTABLE_PATH='/Applications/Visual Studio Code.app/Contents/MacOS/Code' npm run test:host
```

The host command ran from `editors/vscode/` using the installed macOS Code
executable and an isolated temporary workspace/profile, without modifying the
user's installed extension. It exited 0: registration, diagnostic/error repair,
actual JVM Run, generated-Java-only view and save checking passed.
After the four diagnostic table rows were added, `./scripts/check-docs.sh`
passed again: 20 executed snippets, tooling consistency, VitePress production
build and 217 local link targets across 121 Markdown files.

Evidence is intentionally separated:

- Formatter: seven focused source fixtures plus canonical CRLF/Unicode/EOF
  program and an exact import/declaration/comment-boundary fixture, three
  invalid-file checks, default/custom project selection,
  read-only check JSON, timestamp preservation, whole-batch parse-failure
  preservation, and check/run source immutability. JVM output/status/stderr
  before and after formatting match. Corpus: **349 valid roundtrips**, **27
  invalid inputs skipped**; parse structure, idempotence, comments and literal
  spellings compared in memory. No corpus-wide source rewrite.
  The last boundary fixture was added after the full gate's initial formatter
  phase; the complete focused formatter command was rerun successfully against
  the unchanged compiler, including that fixture and the corpus.
- Reexports: class/function/enum/variant/value facade, mutation of shared value,
  multiple defining modules, 20-layer chain, manifest-bound external package,
  API/member/origin discovery, no-execution query; **11 explicit negative
  source fixtures**, cycle detection and two package-boundary rejection checks.
- Match expressions: **three independent JVM programs**, plus a formatted
  rerun; side-effecting scrutinee executes once, selected branch only; generic
  and nullable payloads/results, nested/lambda/field/return contexts and Java
  nullable result. **19 explicit negative source fixtures** plus undeclared
  branch throws check. Statement-temporary and expression versions agree.
- Independent grammar after features: **31 parser fixtures**. Grammar evidence
  alone is not static checking or execution evidence.
- Real Mini Web HTTP/OpenAPI tests passed through the new facade and method
  name expression matches, including Unicode/query/headers/CORS and errors.
- Formatter-era docs gate passed: 20 executed snippets, VitePress build and
  207 link targets. The final gate below is authoritative for final docs.

Mutation sensitivity (temporary changes restored and rebuilt):

| Mutation | Detection |
|---|---|
| Omit comment output | Formatter's exact canonical/comment output assertion fails |
| Skip export name-collision guard | Different-origin duplicate is incorrectly accepted; negative test fails |
| Evaluate match scrutinee twice | Independent call counter differs; JVM output assertion fails |
| Remove branch result assignability check | Int/String mismatch is incorrectly accepted; static negative test fails |

All four were executed together with
`python3 tests/ergonomics/check_mutations.py`; all four tests detected their
mutation, then sources were restored and rebuilt. These runs preceded the two
final scope regressions above; the mutation sites and their guards were retained.
The optional script deliberately rebuilds and must not run concurrently with
normal verification. No mutation is committed.
No test golden was altered or existing assertion weakened.

## 6. Dogfood and concrete benefit

- `examples/json_select/src/command.spr`: explicitly formatted five OptionSpec
  declarations; only canonical spaces changed, no bulk migration.
- `libraries/sprig-web/src/web.spr`: a 13-declaration stable facade;
  Mini Web now imports `@web/web.spr` while existing app.spr consumers remain
  supported. Implementation helpers need not form the facade API.
- `method_name` and `operation_name` in app.spr now directly return an
  expression match. Each branch contains the result string, removing repeated
  return statements while retaining exhaustiveness and the original signatures.

Concrete public-facade usage in Mini Web:

```sprig
import "@web/web.spr" as web
let app = web.App(title="Mini Web", cors_origin="http://localhost:5173")
```

Concrete result mapping in the library, replacing statement branches each
containing `return "GET"`, `return "POST"`, or `return "DELETE"`:

```sprig
func method_name(method: Method) -> String:
    return match method:
        case Method.GET:
            "GET"
        case Method.POST:
            "POST"
        case Method.DELETE:
            "DELETE"
```

Tooling advertises `formatter`, `moduleReexports` and `matchExpression`;
unsupported syntax excludes expression matches but continues listing block
lambdas, wildcard match and unrelated missing capabilities. Contextual export
declarations are also highlighted in VS Code without marking ordinary export
identifiers. Contracts ship in SDK docs and website refs.

## 7. Deferred pressure

Named function references, block lambdas, tuple/destructuring, string
interpolation and generic inference remain deferred. Web callbacks still use
explicit forwarding lambdas; these features are not prerequisites for facades
or expression matches. Direct match blocks inside call arguments need a separate
layout/composition design; export renaming and visibility would change the API
model and were deliberately not added.

## 8. Remaining risks and limits

- Standalone comments have no semantic AST attachment. Original order/columns
  guide placement; unusual human intent cannot be proved. Corpus coverage is
  finite, and no aggressive wrapping/alignment is attempted.
- Module loading remains recursive and follows the existing graph machinery.
  Cycle rejection and a moderate chain are tested, not unbounded hostile depth.
- Erased generics still depend on existing compiler-controlled casts and boxing.
  Tested instances do not prove every host generic combination safe.
- Java switch lowering is checked by javac and JVM tests on this host. The
  existing CI platform/JDK matrix remains necessary for release acceptance.
- Whole-batch formatting is not a transactional filesystem operation, and
  concurrent external file edits during formatting are outside this first
  formatter's synchronization contract.

Significant implementation, test and documentation work was AI-assisted by
Codex. This report records commands executed by that same implementation agent;
it does not claim an independent human review, release publication, numerical
stability proof or general self-hosting completion.
