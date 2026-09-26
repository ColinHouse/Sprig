# v0.8 generics — implementation and validation report

**Scope of this round:** user-defined single-parameter generics end to end
(grammar → AST → types → checker → codegen → CLI metadata → tests → docs).
The `sprig.toml` project system, lockfile, dependency resolution and stage-1
integration described in the v0.8 plan are **not implemented**; see section 5.
No release was created.

**Tree:** branch `v0.8-generics-projects` from `e354969` (alpha.2).
**Compiler metadata:** `0.2.0-alpha.1`, language `0.8-dev`.

## 1. Implemented generic rules

| Rule | Where | Evidence |
|---|---|---|
| `generic T:` wraps one class/variant/enum/function; exactly one parameter | `grammar/SprigParser.g4` (`genericDefinition`), `AstBuilder.buildGeneric` | `tests/syntax/positive/08_generics.spr`; negative `15_generic_multi_parameter.spr` |
| `T` is block-scoped and never leaks | `NameResolver.typeParamsOf` + per-decl `typeParamTypes`; body annotations resolved with the active map | `tests/semantics/generic_scope_leak.spr` → `SPR-NAME-UNRESOLVED` |
| Explicit application only: `Box[Int](...)`, `identity[Int](x)`, `Option[Int].Some(value=1)`, `Option[Int].None` | grammar `subscript` + `TypeChecker.checkGenericCall` | `tests/runtime/19_generics.spr` |
| No inference: `identity(42)` / `Box(value=1)` | `requireExplicitTypeArguments`, class-ctor guard | `tests/semantics/generic_inference.spr` → `SPR-TYPE-GENERIC-ARGS-REQUIRED` |
| Strict arity, including bare `Box` in a type position and arguments on non-generic types | `TypeRefResolver.resolveArguments` / `instantiateUserType` | `generic_wrong_arity.spr`, `p2_generic_*` acceptance cases → `SPR-TYPE-GENERIC-ARITY` |
| Invariance, no subtyping/variance | `ClassType`/`VariantType` equality includes arguments | design; no variance syntax exists |
| `T` has no operators/equality/methods | `TypeChecker.checkBinary` guard | `generic_operator.spr` → `SPR-TYPE-OPERAND` |
| Practical Strict nullability: `Box[String?]` legal, rejected when the declaration applies `?` to `T` | `TypeRefResolver.declaresNullableParameter` | `generic_nullable_arg.spr` → `SPR-TYPE-GENERIC-NULLABLE` |
| Generic variants use expanded payloads; `match` stays exhaustive on instantiations | grammar `variantCase`, `sameMatchOwner` compares variant declarations | `tests/runtime/19_generics.spr`, `tests/semantics/missing_case.spr` unchanged |
| Indexing preserved: `values[index]` is an index; `foo[T](...)` is a generic use | grammar `subscriptContent`, `Expr.Subscript` resolved by symbol kind | all v0.7 runtime tests, `07_nullable`, collections |
| Internal parameter lists (not a single hardcoded field) | `Decl.typeParams` is a `List<String>`; arity checks iterate it | code, future multi-parameter readiness |

`requires T: Comparable|Equatable` is parsed, placement- and name-checked, but
capability implication is **not implemented**: every clause reports
`SPR-GENERIC-CONSTRAINT` rather than silently doing nothing.

## 2. JVM lowering

Generics are erased and boxed:

- a type parameter is `java.lang.Object` in generated Java;
- generic classes/variants are emitted as raw classes;
- any type still containing a type parameter (for example `MutableList[T]`) is
  erased to its raw runtime class (`SprigMutableList`), so `SprigList<Object>`
  can never leak into a `SprigList<String>` position;
- call arguments in erased slots are boxed (`Long.valueOf`, ...), results are
  cast and unboxed;
- `instanceof` remains raw, so `match` lowering and exhaustiveness are intact.

Example generated Java for `Box[Int]`:

```java
public final class $Box { public final java.lang.Object value; ... }
$init: box = new sprig.user.$Box(java.lang.Long.valueOf(42L));
       print(((java.lang.Long) box.value).longValue());
```

The bug this round actually hit was exactly the erasure leak above
(`SprigMutableList<Object>` vs `SprigMutableList<String>`), found by the
stage-1 generic slice and fixed in `JavaGenerator.javaType`.

## 3. Constraints supported

None. `requires` syntax only. This is the largest gap versus the v0.8 plan and
is reported honestly in `capabilities --json`
(`genericConstraints: false`).

## 4. Explicitly unsupported

Multiple type parameters, inference, variance, capability implications,
generic constraints on JVM types, generic lambdas/function types in source,
and any registry/project feature.

## 5–8. Project system, lockfile, Git and Maven (not implemented)

`sprig.toml`, `sprig.lock`, `sprig init/resolve/deps/project`, local/Git/Maven
dependencies, offline mode and a dependency cache **do not exist in this
tree**. The v0.7 single-file and relative-import model is unchanged, and
counters report honestly in `capabilities --json`:

```
packageManifest=false  lockfile=false  localDependencies=false
gitDependencies=false  mavenDependencies=false  centralSprigRegistry=false
```

Why this round stopped there: the generics work above is a prerequisite for
the stage-1 code that the project system must resolve, and a half-written
manifest/resolver would create exactly the nondeterminism the plan forbids
(silent branch movement, stale locks, partial Maven graphs). Implementing
Maven correctly also means depending on Maven Resolver and distributing its
runtime, which is a separate engineering decision. Recommended next round, in
order: (1) `sprig.toml` parsing + project discovery + `sprig init` /
`project --json` without dependencies; (2) `sprig.lock` for local path deps;
(3) Git deps with `resolve` as the only mutator; (4) Maven Resolver behind the
same lockfile discipline.

## 9. What the stage-1 work actually exposed

The new Sprig-written generic container slice
(`tests/visitor/generic_stack.spr`) replaces per-type stack classes with one
`generic T: class Stack` used as `Stack[String]` and `Stack[Int]`, plus
`drain[T](Stack[T])`. It failed first for real reasons, all fixed:

1. local `let` type annotations inside generic bodies were resolved without
   the active type-parameter map (`Unknown type 'T'`);
2. `var` locals cannot be null-narrowed in Sprig, so the first `pop` loop had
   to be rewritten with an immutable binding — correct language behavior
   surfacing in generic code;
3. erased generic containers leaked `Object` parameters into substituted
   positions (codegen fix described above).

These are the kind of issues the plan wanted stage-1 to find, and they were
found before any project-system work.

## 10. Is a second type parameter needed yet?

Not yet in practice. The stage-1 slice needed `Stack[T]`; key/value shapes use
the builtin `Map[String, T]`. Real cases recorded for the future:
`Pair[A,B]`, `Result[T,E]`, reusable map-like structures. No blocker was hit,
so multi-parameter syntax stays out of the user surface while `Decl.typeParams`
already stores a list.

## 11. Evidence for interfaces/function types?

None collected. Generic containers, variants and explicit capability-free code
were sufficient for the slice. Function types in source and interfaces remain
unimplemented and are not required by the evidence so far.

## 12. Is v0.8 / 0.2.0-alpha.1 ready?

**No — the full v0.8 gate is not met.** Generics core: implemented and green.
Project system, lockfile and dependency resolution: not implemented.
Capability implications: not implemented.

What *is* verified on this tree:

| Gate | Result |
|---|---|
| v0.7 baseline at `e354969` | `scripts/test.sh` 103/103 |
| v0.8-dev tree | `scripts/test.sh` **114 passed, 0 failed** |
| numeric semantics | 66/66, unchanged |
| grammar harness | 21/21 |
| recovery, correctness, acceptance, JSON, consistency matrices | all green |
| agent tooling / CLI contract | 82 + 19 checks green |
| documentation gate | snippets 18/18 + VitePress build |
| `./.github` CI | not yet run for this branch |

No known silent miscompile remains in the implemented generics surface: every
`check`-accepted generic program in the corpus also passes `javac` and JVM
execution, verified by `tests/runtime/19_generics.spr` and the erased-container
regression in `tests/visitor/generic_stack.spr`.
