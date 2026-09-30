# Foreign JVM conformance (`conform`)

`conform C to J` declares a foreign JVM nominal contract between a Sprig-owned
class `C` and an imported Java interface `J`. It defines no methods and
performs no adaptation: the compiler verifies that the existing class methods
satisfy every supported abstract instance requirement of `J`, records the
relation as an explicit foreign assignability edge, and emits `C` with `J` in
its JVM interface list.

```sprig
import java.lang.Runnable as Runnable

class Task:
    func run() -> Unit:
        print("running")

conform Task to Runnable

let task = Task()
let runner: Runnable = task   # foreign conformance conversion
runner.run()
```

## What conform is not

It is a **declared foreign conformance conversion**, not general subtyping:

- `Task → Runnable` and `Task? → Runnable?` are legal; `Task? → Runnable` is
  rejected until narrowed;
- `List[Task] → List[Runnable]` is never inferred (generics stay invariant);
- there is no class inheritance, no Sprig `interface` declaration, no
  structural matching and no SAM conversion;
- `conform` does not change the class's Sprig API. `Task()` methods keep Sprig
  semantics; only the `J` view uses Java interop metadata.

Adaptation stays ordinary composition, never a conform DSL:

```sprig
class EndTickAdapter:
    let clock: Clock

    func onEndTick(server: Server) -> Unit:
        clock.tick(server)

conform EndTickAdapter to EndTick
```

## v1 rules

- The left class must be non-generic and declared in the same module.
- The target must be an imported **public**, non-generic, non-sealed Java
  interface. Annotations, abstract classes and ordinary classes are rejected.
- Write one declaration per relation: `conform Worker to Runnable` and
  `conform Worker to Closeable`, not a comma list.
- Witness methods match exactly by name, arity, JVM parameter shapes, return
  JVM shape and static/instance status. No parameter contravariance and no
  return covariance.
- Abstract methods that share a name (overloads) are rejected
  (`SPR-CONFORM-OVERLOAD`); Sprig classes do not overload.
- Default methods are not requirements; call them through the Java view. A
  default method declared in a subinterface satisfies the abstract method it
  overrides (the effective contract shadows it).
- Java interface inheritance is resolved to the effective contract: a
  subinterface declaration shadows the one it overrides, covariant returns
  collapse to the most-derived declaration, and the permitted checked
  exceptions come from the effective declaration (intersected across unrelated
  maximal declarations).
- Public concrete `java.lang.Object` methods (`equals`, `hashCode`,
  `toString`, ...) satisfy matching requirements, as they do for any Java
  class; a Sprig method that overrides one of them still gets the boundary
  guard.
- Generic methods erase to their erased shapes, which may be witnessed by the
  corresponding reference types; the type-parameter contract itself is not
  modeled. Boxed `Short`/`Byte`/`Character` parameters cannot be witnessed
  because the existing interop mapping turns those aliases into `Int32`/`String`.
- `requires T: X` is unchanged: foreign interfaces are not Sprig capabilities.
- Reference overloading (`pick(Object)` vs `pick(Runnable)`) keeps the existing
  JVM overload ranking: conformed classes behave like ordinary Java references
  and may report `SPR-JVM-AMBIGUOUS` where no exact class match exists.

## Foreign boundary

Java framework callers can pass `null` for reference parameters. A witness
method whose Sprig parameter is non-null guards the generated entry:

```java
java.util.Objects.requireNonNull(server, "foreign boundary: parameter 'server' must be non-null");
```

Checked effects are conservative in v1: a witness `throws` is accepted only
when every checked exception is permitted by the Java interface method
(`SPR-CONFORM-EFFECTS` otherwise). Sprig `Error` lowers to the unchecked
`SprigError` and is accepted.

## Explicit v1 restrictions confirmed by audit

Conformance is verified against the effective Java contract, not raw
reflection order. The following remain deliberate v1 boundaries:

- generic source classes and generic target interfaces (including generic
  ancestors);
- overloaded abstract methods;
- boxed `Short`/`Byte`/`Character` witness parameters;
- parameter contravariance and return covariance in the **Sprig witness**
  (covariance inside the Java interface hierarchy still resolves correctly);
- method renaming, adapters, SAM conversion, interfaces, inheritance and
  variance.

`conform` reserves the word `conform` (like `class` or `variant`); `to` stays a
contextual word and identifiers named `to` keep working.

## Diagnostics

`SPR-CONFORM-SOURCE`, `SPR-CONFORM-TARGET`, `SPR-CONFORM-MEMBER`,
`SPR-CONFORM-OVERLOAD` and `SPR-CONFORM-EFFECTS`. Every code has structured
guidance: run `sprig explain <code> --json` or `sprig help conform --json`.
