# Sprig v0.8 — implementation and validation report

**Tree:** branch `v0.8-multiparam-projects` from `96cad8a` (merged v0.8
generics core, PR #7). **Compiler metadata:** `0.2.0-alpha.1`, language
`0.8-dev`. **No tag, release or published archive was created by this round.**

Baseline was frozen before any change: commit `96cad8a`, grammar SHA-256
`acff6111…` (lexer) / `ff620950…` (parser), `scripts/test.sh` 114/114, numeric
66/66. Current tree: **117/117 tests**, numeric 66/66, grammar harness 21/21,
docs gate 18/18 snippets + VitePress build, SDK archive offline smoke passed.

## 1. Are multi-parameter generics implemented?

Yes, end to end. `generic K, V:` declares any number of parameters (no
arbitrary limit); the grammar stores a `typeParameterList`, the AST keeps
`Decl.typeParams` as a list, and arity, substitution and nullable analysis are
per position. Duplicate names in one list report `SPR-NAME-DUPLICATE`; a block
wraps exactly one class, variant or function; using a parameter outside the
block is `SPR-NAME-UNRESOLVED`. Evidence:
`tests/runtime/19_generics.spr` (two-parameter class, variant and function,
nested `Entry[String, Box[Int]]`), `tests/visitor/generic_stack.spr`
(`Table[K,V]`), syntax positive `08_generics.spr`, syntax negatives
`15_generic_two_targets.spr` / `16_generic_empty_body.spr`, semantics
`generic_duplicate_parameter.spr`, `generic_wrong_arity.spr`.

## 2. How does generic codegen work?

Erasure with compiler-controlled boxing, never Java erasure semantics leaking
into Sprig:

- a type parameter becomes `java.lang.Object`; generic classes/variants are
  raw JVM classes;
- primitive arguments are boxed at erased slots (`Long.valueOf`, ...) and
  results are cast plus unboxed;
- any type still containing a parameter (for example `MutableList[T]`) is
  erased to its raw runtime class, so `SprigList<Object>` can never occupy a
  substituted `SprigList<String>` slot;
- `instanceof` stays raw, so match exhaustiveness is unaffected;
- receiver expressions are parenthesized before member access, so an unboxing
  cast cannot change the meaning of `a.value.inner`.

Two real bugs were found by the adversarial corpus and fixed this way: the
erasure leak above (`SprigMutableList<Object>` vs `<String>`) and the cast
precedence in nested access. Neither reached a release.

## 3. How does the nullable generic rule work?

Practical Strict, parameter by parameter. `Box[String?]` is legal when the
declaration stores `T` directly; if the declaration applies `?` to the
parameter (`let value: T?`) then `Box[String?]` reports
`SPR-TYPE-GENERIC-NULLABLE`, while `Box[String]` works. In a two-parameter
declaration each parameter is analysed separately, so
`K = String?, V = Int` is allowed for `key: K, value: V?` but `V = Int?` is
rejected. No `String??` is produced and no flattening happens.

## 4. How far do generic constraints go?

One capability is implemented: `requires X: Equatable` makes `==`/`!=` on that
parameter legal and generates value equality (the checker sets the existing
`valueEquality` lowering). `requires X: Comparable` is parsed, validated for
placement and parameter identity, and then rejected with
`SPR-GENERIC-CONSTRAINT` as not implemented. Unknown capability names report
the same code. There are no interfaces, traits, user-defined capabilities,
variance or wildcards. `capabilities --json` reports
`genericConstraints: true` with `multipleGenericParameters: true`; the
Comparable gap is stated in `help generics` and in the known-limitations file.

## 5. Is the project/package system deterministic?

The part that exists is deterministic by construction; the part that does not
exist cannot be judged.

Implemented: `sprig.toml` discovery (upward), required `[project] name`,
defaults `source = src`, `entry = src/main.spr`, `[[bin]]` named entries,
`exports`, `[[dependency]]` and `[[jvm]]` declarations, `sprig init` (never
overwrites), `sprig project --json`, `sprig deps --json`, and explicit-file
priority for `check/build/run`. Nothing mutates the manifest; no command
resolves or rewrites anything. Evidence: `tests/project/check_project.py`
(14 checks), including malformed TOML (`SPR-PROJECT-MANIFEST`), nested
discovery, `--bin`, explicit-file priority and project-less errors.

Not implemented: dependency resolution, `sprig.lock`, offline cache and
`@name/...` imports. `sprig deps` lists declared dependencies with
`resolved: false` and exits 2 with `SPR-PROJECT-UNSUPPORTED`. This is a
declared blocker, not a hidden behavior: `capabilities --json` keeps
`lockfile=false`, `localDependencies=false`, `gitDependencies=false`,
`mavenDependencies=false`, `centralSprigRegistry=false`.

## 6. Are Git branches locked to a SHA?

No. Git dependencies are declarable in the manifest and listed by
`sprig deps`, but there is no resolver, no lockfile and no fetch. No build ever
contacts a Git remote.

## 7. Do Maven dependencies share the check/build/run/api classpath?

No. JVM dependencies can be declared with exact `group`/`artifact`/`version`
(the parser rejects ranges, `latest` and `+`), but they are listed unresolved.
Today `--classpath` remains the only classpath mechanism, exactly as in
alpha.2.

## 8. Do clean/offline builds hold?

- Clean build: `scripts/build.sh` from a clean tree downloads the pinned ANTLR
  jar, verifies SHA-256, compiles and smoke-tests the launcher. Re-verified on
  this branch.
- Offline SDK: `scripts/package-alpha.sh` + `tools/check-sdk-archive.py`
  extracted the candidate archive and ran `version`, `capabilities`, `doctor`,
  `api`, every help-topic example, `check`, `run` and the stage-1 probe with no
  network. Passed.
- Project dependencies offline: not applicable, because resolution does not
  exist.

## 9. How many generic abstractions does stage-1 actually use?

The Sprig-written stage-1 slice `tests/visitor/generic_stack.spr` now uses:

| Abstraction | Shape | Replaces |
|---|---|---|
| `Stack[T]` | generic class | per-element-type stack classes in the probe's symbol table |
| `drain[T](Stack[T])` | generic function | per-element-type drain loops |
| `Table[K,V]` | two-parameter generic class + `requires K: Equatable` | parallel per-value-type lookup tables |

The 568-line `frontend.spr` probe itself still uses its concrete structures;
migrating it to these abstractions is recorded as remaining work rather than
claimed.

## 10. Are multiple parameters proven useful?

Yes, in the slice: `Table[K,V]` is a natural two-parameter abstraction (a
symbol environment maps keys to independently typed values), and
`Entry[String, Box[Int]]` exercises nesting. Single-parameter `Stack[T]` was
not enough for the lookup without duplicating the table per value type. No
three-parameter need appeared. `[K, V]` stays readable in tests, arity errors
are localized and `SPR-TYPE-GENERIC-ARITY` names the expected and actual
counts; agents have both `help generics` and structured diagnostics. Usage is
still small — three abstractions in one slice — so the honest conclusion is
"proven sufficient for the probe, not yet proven indispensable at scale".

## 11. Any silent miscompile?

None known in the tested surface. The adversarial corpus covers
`Box[Int|Int32|Float|Float32|Bool|String|String?|BigInt|Decimal]`,
`Entry[String, Int]`, `Entry[Int, String?]`, `Result[Int, String]` with
exhaustive match, nested generics and generic recursion through `drain`. Every
`check`-accepted program in the corpus also passes `javac` and JVM execution,
and the two erasure/precedence defects found during development were fixed and
covered by `tests/visitor/generic_stack.spr` and runtime 19.

## 12. Remaining release blockers

1. **Dependency system**: `sprig.lock`, local/Git/Maven resolution, offline
   cache and `@name/...` imports are absent; `capabilities` reports this.
2. **Capabilities**: only `Equatable`; `Comparable` and user-defined
   capabilities are not implemented.
3. **Stage-1 integration**: the frontend probe itself has not been migrated to
   the generic abstractions.
4. **Agent blind test** (v0.8 plan section 45) has not been run.
5. **Hosted CI** for this branch must pass before any merge; the archive
   published for `v0.2.0-alpha.1` does not exist (only a local candidate and
   clearly-marked draft notes).
6. `sprig api`/`doctor` do not consume project context beyond today's explicit
   `--classpath`.

Per the plan, publishing is left to an independent acceptance agent; this
round created no tag and no release.
