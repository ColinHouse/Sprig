# Foreign JVM conformance (`conform`)

`conform C to J` declares a foreign JVM nominal contract between a Sprig-owned
class `C` and an imported Java interface `J`. It defines no methods and
performs no adaptation: the compiler verifies that the existing class methods
satisfy every supported abstract instance requirement of `J`, records the
relation as an explicit foreign assignability edge, and emits `C` with `J` in
its JVM interface list. With parentheses, `conform C to J(field, ...)` makes
the generated class of `C` extend the Java class `J` instead; see
[Extending a Java class](#extending-a-java-class).

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
- there is no inheritance between Sprig classes (a Java superclass is
  declared with the class form below), no structural matching and no SAM
  conversion; a Sprig-side contract is a class without method bodies, see
  [Contract classes](#contract-classes);
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

## Contract classes

A class whose methods all end at the line break, with no body and no fields,
is a **contract**:

```sprig
class Sink:
    func write(line: String) -> Unit
    func flush() -> Int

class Console:
    var written: Int = 0

    func write(line: String) -> Unit:
        written += 1
        print(line)

    func flush() -> Int:
        return written
conform Console to Sink

func log_all(lines: List[String], sink: Sink) -> Int:
    for line in lines:
        sink.write(line)
    return sink.flush()

print(log_all(["a", "b"], Console()))
```

- `conform C to Contract` (or `conform C to alias.Contract` for a contract
  declared in an imported Sprig module) requires `C` to have every method of
  the contract with the same parameter types and result type, the same
  `rethrows`, and a `throws` clause that is a subset of the contract's
  (`SPR-CONFORM-MEMBER`, `SPR-CONFORM-EFFECTS`). Nothing is adapted or
  renamed.
- A `C` value then goes where the contract type is expected (`Sink`,
  `Sink?`, a `List[Sink]` literal), and is used through the contract's
  methods only. There is no conversion back (`let c: Console = sink` is
  rejected) and generics stay invariant (`List[Console]` is not `List[Sink]`).
- A contract cannot be constructed, declare fields, or mix methods with and
  without a body (`SPR-CLASS-ABSTRACT`); a contract cannot itself conform, to
  another contract, a Java interface or a Java class alike
  (`SPR-CONFORM-SOURCE`): each implementing class declares both conformances.
  A function outside a class never omits its body (`SPR-SYNTAX-ERROR`).
- One `conform` per relation; a class may conform to several contracts and
  Java interfaces. `conform` is written in the module that declares the class,
  so there is no retroactive conformance for an imported or dependency class
  (`SPR-CONFORM-SOURCE`; wrap it in a local class that forwards), and
  conformance is always declared: a class with the right methods is not a
  `Sink` until it says so (`SPR-TYPE-MISMATCH` as an argument, with a hint
  that names the missing `conform`; `SPR-TYPE-ASSIGN` in a `let` initializer).
- Three rules hold for the 0.8 language. They are decisions, not gaps, and
  each rejection names the alternative:
  - **A contract is never generic.** `Repository[T]` inside a `generic` block
    is `SPR-CLASS-ABSTRACT`, and a generic class never conforms
    (`SPR-CONFORM-SOURCE`). Declare one contract per element type, or keep a
    generic class that holds its single operation as a `fn` field.
  - **A contract is a type, never a bound.** `requires T: Sink` is
    `SPR-GENERIC-CONSTRAINT`: take `Sink` as the parameter type
    (`func drain(sink: Sink)`). Capabilities stay the closed set `Equatable`
    and `Comparable`; a contract has no associated types and never conforms
    to or extends another contract.
  - **A contract has no default methods.** Mixing methods with and without a
    body is `SPR-CLASS-ABSTRACT`: write the shared behavior as a module
    function that takes the contract, `func log_all(sink: Sink, lines:
    List[String]) -> Unit`.
- There is no downcast from a contract to a class and no type test on class
  or contract values: `match` is for enums and variants. The decision guide
  is one line: **a closed set of types is a variant; an open set is a
  contract.**
- Generated Java: the contract is a `public interface` with one abstract
  method per Sprig method (`void` for `Unit`, checked Java exceptions in the
  `throws` clause); the conforming class `implements` it. `==` on contract
  values is identity, like any class value; `print` shows the object's own
  class.

This is the open counterpart of `variant`: a `variant` lists every case in
one place and `match` must cover them all; a contract lists the methods and
any module may add a conforming class. Neither is inheritance: a contract has
no state and no default bodies.

## v1 rules

- The left class must be non-generic and declared in the same module.
- Without parentheses the target must be an imported **public**,
  non-generic, non-sealed Java interface; annotations are rejected. With
  parentheses it must be a public, non-final, non-generic, non-sealed Java
  class, abstract or concrete (see below); a class named without parentheses,
  or an interface named with them, is rejected with the form to write.
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

## Extending a Java class

```sprig
import conformfixture.ClassBase as ClassBase   # abstract: describe(); concrete: greet(String)

class Item:
    let name: String
    var uses: Int = 0

    func describe() -> String:
        uses += 1
        return "item " + name + " " + uses

    func greet(who: String) -> String:
        let inherited = parent.greet(who)   # the inherited ClassBase.greet
        if inherited == null:
            return "[none]"
        return "[" + inherited + "]"

conform Item to ClassBase(name) as parent

let item = Item(name="wand")
let base: ClassBase = item                  # the class extends ClassBase
print(item.id())                            # an inherited public method
```

`conform C to J(field, ...) as NAME` makes the generated Java class of `C`
extend `J`. The object is one object: a Java framework that holds the `J` sees
the same instance Sprig mutates, so there is no delegate and no identity split.
Sprig itself still has no inheritance: a Sprig class cannot extend a Sprig
class, and `C`'s own API is unchanged.

- **Constructor.** The names in parentheses are fields of `C`, passed in that
  order to the one public or protected constructor of `J` whose parameter JVM
  shapes match the fields' shapes exactly (`conform C to J()` selects the
  no-argument constructor). Expressions and literals are not allowed, because
  `super(...)` runs before any field of `C` exists; the generated constructor
  passes its own parameters. No matching constructor is `SPR-CONFORM-TARGET`
  with the available shapes.
- **Abstract methods** of `J` and its ancestors (including interface methods no
  class in the chain implements) need a method of `C` with the exact Java
  signature, as interface witnesses do (`SPR-CONFORM-MEMBER` when missing).
- **Overrides are matched by shape.** A method of `C` whose name is the name of
  a public or protected instance method of the chain must match one of that
  name's shapes exactly; it then overrides that method and the generated Java
  carries `@Override`. A matching name with none of the shapes is rejected,
  because Java would silently add an overload and the hook would never run.
  Overriding a `final` method, or hiding a `static` one with the same
  signature, is `SPR-CONFORM-MEMBER`. Checked exceptions follow the interface
  rule (`SPR-CONFORM-EFFECTS`). Methods whose names appear nowhere in the chain
  are ordinary Sprig methods.
- **The parent view.** `as NAME` declares a class-scope name, visible in every
  method of `C` (also inside lambdas there), that calls the inherited
  implementation: `NAME.m(args)` is Java's `super.m(args)`, generated as
  `C.super.m(args)`, for the public and protected methods of the chain
  (`sprig api J` lists the protected ones under `protectedMethods`; the
  language server completes both after `NAME.`). `NAME` is not a value, has no fields, never names a static
  method and cannot call an abstract method; each of those is
  `SPR-CONFORM-PARENT`, as is an alias on an interface conform or one that
  shares a name with a field or method of `C`. The result of a parent call is
  mapped like any Java result, so a reference result is nullable. The alias is
  optional; without it an override replaces the inherited behaviour.
- **Inherited members.** On a value of `C`, members that `C` does not declare
  resolve through the Java view of `J` (`item.id()`, `plain.count = 7`), with
  the ordinary interop mapping. Inside the class body there is no `self` and
  no bare-name access to inherited members; the parent view is the explicit
  way. A field of `C` shadows an inherited member of the same name on `C`
  receivers, as in Java.
- **Assignability.** `C → J`, `C → K` for every superclass `K` of `J` and
  `C → I` for every interface the chain implements are declared conversions,
  like `C → Interface` today; collections stay invariant.
- **Generated Java.** `public final class C extends J implements ...`, the
  constructor's `super(...)` first, `@Override` on witnesses and overrides, the
  entry guards of the foreign boundary, and no generated `toString`: the
  inherited one (or a Sprig override of it) is used.
- **Not in v1.** Protected methods of `J` can be overridden and called through
  the parent view, but not on values of the class (Java's own rule across
  packages); protected fields are not reachable; generic superclasses, a second
  Java superclass, super calls into other classes than `J`'s chain, and
  constructor expressions are rejected. Loom's remapping covers the generated Java like any Java source of
  a mod, because it is compiled in the same source set.

## Error classes

The one class target that needs no import is `Error`. A class with a
`message: String` field that conforms to `Error(message)` is an **error
class**: its generated class extends `sprig.runtime.SprigError`, so it is an
error type everywhere Sprig asks for one.

```sprig
class NotFound:
    let message: String
    let sku: String
conform NotFound to Error(message)

func load(sku: String) -> Int throws NotFound:
    throw NotFound(message="no item " + sku, sku=sku)

try:
    print(load("ZZ"))
catch problem: NotFound:
    print(problem.sku)
```

- `throws NotFound` declares it; a caller that declares or catches `Error`
  covers it, since the class is assignable to `Error`.
- `catch problem: NotFound` narrows to the class, so its fields are readable;
  a catch of the class after a catch of `Error` is unreachable
  (`SPR-FLOW-THROWS`).
- `print(problem)` and `"failed: " + problem` show the message, as for an
  `Error`, and `problem.message` is the field.
- A lambda that throws an error class has the function type `... throws Error`;
  function types never name a narrower error.
- The other `conform ... (fields)` rules apply: one superclass, the named
  fields in the superclass constructor's order (`message`, or `message` and a
  Java `Throwable` cause), no generic class.

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
- method renaming, adapters, SAM conversion, Sprig interfaces, inheritance
  between Sprig classes and variance;
- for class targets: protected members on values of the class (the parent
  view may call protected methods), generic superclasses, constructor
  expressions and calling the parent view as a value.

`conform` reserves the word `conform` (like `class` or `variant`); `to` stays a
contextual word and identifiers named `to` keep working.

## Diagnostics

`SPR-CONFORM-SOURCE`, `SPR-CONFORM-TARGET`, `SPR-CONFORM-MEMBER`,
`SPR-CONFORM-OVERLOAD`, `SPR-CONFORM-EFFECTS` and `SPR-CONFORM-PARENT`. Every code has structured
guidance: run `sprig explain <code> --json` or `sprig help conform --json`.
