# `conform` v1 adversarial audit

Audited PR: [#44](https://github.com/ColinHouse/Sprig/pull/44), base `main`
`33814c19648c9b9debd43d85a487c5a4776c913f`, head tested
`fda9d138f2b9bbd7207f90bf8ae5046b5eda1c0d` (the implementation commit is
unchanged from `d2364ee`; the second commit merges main into the branch).

Method: independent Java fixtures compiled with `javac`, Java behavior controls
run directly on the JVM, and adversarial Sprig probes executed against the
built head outside the repository. No existing test was trusted as evidence.
Pre-fix and post-fix results are recorded below.

## PROVEN CORRECTNESS BUGS (fixed)

### C1 — inherited contract flattened parent-first; legal Java contracts rejected

Reproducers: `ChildDefault extends Parent` overriding `void f()` with a
`default` method; `MixedDefaultC extends MixAbstract, MixDefault` overriding
with a default.

- Java control (javac + JVM): `final class Control implements ChildDefault {}`
  and `implements MixedDefaultC {}` are valid with no method.
- Head actual: `conform Example to ChildDefault` failed
  `SPR-CONFORM-MEMBER` ("has no method 'f'").
- Root cause: `ConformanceChecker.collect` walked parents first and used
  `putIfAbsent`, so an inherited abstract declaration was never removed by a
  more-derived default declaration.
- Repair: requirements are now the **effective contract**: a declaration in a
  strict subinterface shadows the declaration it overrides; when every maximal
  declaration is default, no witness is required.
- Regression: `child_default_contract`, `mixed_default_contract`,
  `mixed_abstract_override` in `tests/conform/check_conform.py`.

### C2 — covariant return redeclaration treated as an overload

Reproducer: `CovDerived extends CovBase` with `String value()` overriding
`Object value()`.

- Java control: `class Control implements CovDerived { public String value() }`
  compiles and runs.
- Head actual: `SPR-CONFORM-OVERLOAD` because requirement keys included the
  return descriptor, so parent and child declarations looked like overloads.
- Root cause: same flat enumeration; requirement identity included the return
  type.
- Repair: requirement keys are name + parameter descriptor; maximal
  declarations collapse covariance to the most-derived declaration, whose
  return shape is the verified one. A witness returning the parent shape
  (`Object value()`) is now correctly rejected `SPR-CONFORM-MEMBER`.
- Regression: `covariant_return` (positive), `covariant_parent_witness`
  (negative).

### C3 — child `throws` narrowing accepted until javac (under-rejection)

Reproducer: `ThrowNarrow extends ThrowBase` redeclaring `void f()` without
`throws IOException`.

- javac control: `class Bad implements ThrowNarrow { public void f() throws IOException {} }`
  fails with "overridden method does not throw IOException".
- Head actual: `check` accepted a Sprig witness declaring `throws IOException`
  and would have failed in javac.
- Root cause: parent-first `putIfAbsent` kept the parent declaration's
  exception set for an identical JVM descriptor.
- Repair: the effective declaration supplies the permitted checked exceptions;
  for unrelated maximal declarations the permitted set is the intersection.
  Narrowing now reports `SPR-CONFORM-EFFECTS` before javac.
- Regression: `throws_narrowed_child`, `throws_broader_than_declared`,
  `throws_within_declared`, plus the existing effect tests.

### C4 — public Object methods demanded explicit witnesses

Reproducer: an interface redeclaring `boolean equals(Object)`, `int hashCode()`,
`String toString()`.

- Java control: `final class Control implements ObjectIface {}` compiles; the
  class's inherited public Object methods satisfy the interface.
- Head actual: three `SPR-CONFORM-MEMBER` errors.
- Repair: a requirement matched by a public concrete method of
  `java.lang.Object` (same name, parameters and return shape) is satisfied
  without a Sprig witness. If the class declares a matching Sprig method, that
  method is still marked as a foreign boundary so Java null cannot leak into a
  non-null Sprig parameter.
- Regression: `object_methods_satisfied`.

### C5 — conformance edges registered after the inference prepass

Reproducer: `let handle = Support.runIt(Task())` as an unannotated top-level
binding with `conform Task to Runnable`.

- Head actual: `SPR-JVM-MEMBER` ("no method runIt matching 1 argument"),
  because the unannotated-global inference prepass ran before
  `ConformanceChecker`, so the foreign assignability edge did not exist yet.
- Repair: `ConformanceChecker.check(module)` now runs before the inference
  prepass, so every expression sees declared edges.
- Regression: `inferred_global_conformance` (positive, JVM output asserted).

## INTENTIONAL V1 LIMITATIONS (documented)

- **`conform` is a reserved lexer keyword.** `let conform = 1`,
  `func conform`, and `class conform` no longer parse (verified: syntax errors
  on head). This matches existing language keywords such as `class`/`variant`;
  `to` remains contextual and identifiers named `to` still work. Recorded as an
  intentional reservation for the Alpha, not an accidental regression.
- **Boxed `Short`/`Byte`/`Character` witness parameters** cannot be expressed:
  the existing interop mapping turns those aliases into `Int32`/`String`, so
  the JVM shapes cannot match (verified: `SPR-CONFORM-MEMBER` with
  `java.lang.Short -> int`). Documented in `JVM_CONFORMANCE.md`.
- **Generic methods witness by erasure.** `<T> void accept(T)` is satisfied by
  a Sprig method taking `Object`; the generated Java compiles and runs
  (verified with a Java control and a Sprig probe). The type-parameter contract
  itself is not modeled. Documented.
- **Sprig-witness covariance/contravariance** stays rejected; covariance inside
  the Java hierarchy resolves correctly (C2).

## NOT A CONFORM BUG

- **Reference overload specificity.** `Chooser.pick(Task())` with
  `pick(Object)`/`pick(Runnable)` reports `SPR-JVM-AMBIGUOUS`. The same failure
  occurs on base main for plain Java references
  (`Chooser2.pick(ArrayList())` with `pick(Object)`/`pick(java.util.List)`), so
  this is the existing JVM overload ranking, not something conform introduced.
  Conformed classes fail deterministically with a precise diagnostic instead of
  silently selecting the wrong overload. Not changed here.

## NO ISSUE (independently probed)

- **Shape matrix**: `long/int/double/float/boolean/String/void`, `Fn1` and
  `SprigList` raw shapes accepted; `char` vs `String`, arrays, varargs and a
  Sprig class against `Object` rejected with `SPR-CONFORM-MEMBER` (no
  structural typing).
- **Assignability**: `C → Child`, `C → Parent`, nullable variants accepted;
  `C? → J`, `List[C] → List[J]` rejected; field initializers and returns
  verified; `Semantics.isAssignable` and `JavaTypes.rawAssignable` agree on all
  probed positions.
- **Boundary guards**: each non-null reference parameter position is guarded
  ('a' and 'b'), the body never runs, the message is deterministic, a nullable
  witness receives Java null, primitives get no guard, and one method
  witnessing two interfaces is guarded once and works through both views.
- **State and phases**: a failed sibling conformance does not remove a valid
  edge, does not partially mark witnesses, and does not let expressions pass;
  module import order is resolved before use (two-module probe passed), and
  repeated checks are byte-stable.
- **Diagnostics**: `SPR-CONFORM-MEMBER` carries phase `TYPE`, a real source
  range, `expectedType`/`actualType`, a hint and `relatedHelp: conform` in JSON;
  every conform code has `explain --json` coverage.
- **Generation**: emitted Java (`--emit-java-only`) compiles with standalone
  `javac` against the fixture classes and runtime, and a pure Java caller can
  instantiate `sprig.user.$Impl` through the interface; a null argument hits
  the boundary `NullPointerException`. No adapters, synthesized methods,
  visibility, constructor or metadata changes were observed.

## Answers to the audit questions

1. Inherited flattening did **not** match Java override/default/throws
   semantics; C1–C3 fixed it by resolving the effective contract.
2. Yes, before the fix: C3 passed `check` and failed javac. Now rejected with
   `SPR-CONFORM-EFFECTS`.
3. Yes, before the fix: C1/C2/C4 rejected legal Java contracts. Now accepted.
4. Coherent after the fixes; overload ranking remains the pre-existing
   weak specificity for reference types (documented above).
5. Yes: guards run before the body, evaluate only the parameter, apply to every
   non-null reference position, and nullable/primitive params are unaffected.
6. No: validation completes before any `conformedInterfaces` or
   `foreignBoundary` mutation; failed siblings leave valid edges intact.
7. Intentional keyword reservation, consistent with the language; `to` stays
   contextual. Documented, not changed.
8. No unrelated behavior changed; only `ConformanceChecker`, its phase call
   site, and documentation were modified.

## Verification after repair

- `tests/conform/check_conform.py`: **59 checks passed** (38 before; 21 added).
- Independent audit probes: zero remaining findings.
- `./scripts/verify.sh`: **105 gates/cases, 0 failed**, grammar **33**,
  documentation snippets **20/20**, editor passed.
- `tools/check-tooling-consistency.py`; `check_tooling.py` **142**;
  `check_diagnostics.py` **90 codes / 90 explain payloads**;
  `check_sprig_api.py` **30**; `check-docs.py` passed.
- `package-alpha.py --skip-build` and `check-sdk-archive.py` passed.

PR #44 was merged as `d0a4358` before these repairs landed, so they are carried
by this follow-up branch against the merged main.
