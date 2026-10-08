# Appendix F. Java interop in depth

Chapter 21 covered everyday Java calls: imports, `sprig api`, null checks, numbers, lambdas, collections, exceptions. This appendix lays out the details of the JVM boundary in one place, as a reference. Every rule comes from the compiler itself (`sprig help jvm`, `sprig api`), and every command below was really run.

In this chapter you will learn to:

- what `sprig api` tells you beyond signatures;
- how nullability annotations change the `?` on a result type;
- generic methods: when type arguments are inferred and when you must write them;
- both directions of `conform`: implementing a Java interface, and extending a Java class with `conform ... as parent`;
- the limits of functional interfaces, and why a function value that throws `Error` cannot cross;
- checked exceptions in functions, in top-level statements and in `conform`;
- `--classpath` and `--classpath-file`;
- a JVM type mapping quick reference.

## F.1 sprig api: metadata beyond signatures

**Why.** Java has many overloads and annotations; guessing from the surface does not work. `sprig api` reads the same metadata the compiler uses, and is tested with it.

`--member` narrows to one member. For a `final` class or an interface, `protectedMethods` is empty; for a class that can be extended, it lists the protected methods and who gets to use them:

```text
$ sprig api java.util.Random --member next
Java API: java.util.Random
constructors:
staticMethods:
instanceMethods:
fields:
protectedMethods (in a class declared with 'conform C to Random(...) as NAME': override them, call them as NAME.m(...)):
  protected int java.util.Random.next(int) => next(Int32) -> Int32
```

A `protected` method cannot be called on a value; only a class using the parent view can reach it (F.5).

With `--json` each member also carries these fields (the ones used later are explained where they come up):

- `nullableResult`: whether the result may be `null`;
- `interopLevel`: the interop grade (`direct`, `concrete-generic`, `opaque-array`, `adaptable`, `sprig-callable`, `java-callable`, `erased-generic`, `unsupported`);
- `interopReasonCodes`: machine-readable reasons such as `explicit-type-arguments-required`, `wildcard-bounds`, `generic-array-unsupported`;
- `usableFromSprig` and `unusableReason`: whether the member can be called, and why not.

`sprig api .` inspects a whole project; `sprig api @std/lists.spr` inspects a Sprig module. `api` only reads signatures and never runs your program.

## F.2 Nullability annotations

**Why.** The default rule (chapter 21) is "Java reference results are nullable, Java parameters are non-null". When a library states something more precise with annotations, Sprig follows the annotations.

The rules from `sprig help jvm`:

- results are nullable by default, except `toString()` and results annotated `NotNull`/`NonNull`/`Nonnull`, or with `NullMarked`, `NonNullApi`, `MethodsReturnNonnullByDefault` on the class or package;
- a parameter accepts a `T?` or `null` only when annotated `Nullable` or `CheckForNull`;
- annotations kept only in the class file (CLASS retention, such as `org.jetbrains.annotations`) are read too; annotations are matched by **simple name**, so every library's spelling counts.

Verify it with your own class. Three files in a directory:

```java
// src/fixture/NonNull.java
package fixture;

public @interface NonNull {}
```

```java
// src/fixture/Nullable.java
package fixture;

public @interface Nullable {}
```

```java
// src/fixture/Library.java
package fixture;

public class Library {
    @NonNull
    public String label() { return "ok"; }

    @Nullable
    public String nick() { return null; }

    public String plain() { return "p"; }
}
```

Compile, then read the signatures through `--classpath` (F.8 explains it):

```text
$ javac -d out/classes src/fixture/*.java
$ sprig api fixture.Library --classpath out/classes --member label
Java API: fixture.Library
constructors:
staticMethods:
instanceMethods:
  public java.lang.String fixture.Library.label() => label() -> String
fields:
$ sprig api fixture.Library --classpath out/classes --member nick
Java API: fixture.Library
constructors:
staticMethods:
instanceMethods:
  public java.lang.String fixture.Library.nick() => nick() -> String?
fields:
```

`label` with `@NonNull` is `String` (no `?`), `nick` with `@Nullable` is `String?`, and the unannotated `plain` is `String?`. So `label()` needs no null check:

```sprig
import fixture.Library as Library

let lib = Library()
print(lib.label().toUpperCase())
let nick = lib.nick()
if nick == null:
    print("no nick")
```

```text
$ sprig run use_library.spr --classpath out/classes
OK
no nick
```

Class-level defaults (`NullMarked` and friends) must be **runtime-visible** annotations (RUNTIME retention). After giving the `Marked` class a RUNTIME `@NullMarked`, unannotated results become non-null too:

```java
// src/fixture/NullMarked.java
package fixture;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface NullMarked {}
```

```text
$ sprig api fixture.Marked --classpath out/classes --member label
Java API: fixture.Marked
constructors:
staticMethods:
instanceMethods:
  public java.lang.String fixture.Marked.label() => label() -> String
fields:
```

A member annotated `@Nullable` is unaffected by the default and stays `String?`; and a default **never makes a parameter nullable**. Only an explicitly `Nullable` parameter accepts `null`—Sprig never hands Java a `null` it did not ask for.

There is one more detail for member-level `@NonNull` / `@Nullable`: the annotation class does not even have to be on the classpath, because the class file carries the name. Copy `Library.class` alone into a directory and query again: the result is unchanged.

::: tip Coming from another language?
Minecraft's official mappings (Mojang mappings) put JSpecify `@NullMarked` on packages; Fabric Loader uses `org.jetbrains.annotations`. So when calling mod APIs, unannotated method results are usually non-null, and only `@Nullable` members need a check.
:::

## F.3 Generic methods: inferred or written

**Why.** Java static methods often carry their own type parameters (`<T> ...`). Sprig's rule is: **infer when the arguments fix every type parameter exactly; otherwise write `method[Type](...)`**.

The rule from `sprig help jvm`: type parameters are inferred when "the arguments fix every one of them exactly"; a lambda, `null`, a Sprig collection and a raw Java value provide no information, and the result type never participates. What an argument provides is its exact type: a `T` formal taking `String` gives `T = String`, a `List<T>` formal taking `ArrayList[String]` gives `T = String`, and the same `T` appearing twice must get the same type.

Here is one call that infers, and one that must be written:

<<< @/snippets/book_en/appendix_f_generic.spr

```text
[ada, grace]
0
```

Line by line:

- The signature of `Collections.sort(names)` has the recursive bound `T extends Comparable<? super T>`. `names` is `ArrayList[String]`, so `T = String` comes from the argument and the bound is checked at the call (`String` implements `Comparable[String]`); the sort happens in place.
- `Collections.emptyList[String]()` has no argument that can fix `T`, so `[String]` is written after the method name. The result is still nullable by the Java reference rule, `List[String]?`, so it is null-checked before `size()`.
- Mind the position: `[String]` follows the method name and is a **type argument**, not a Sprig collection index.

Inference has exact edges: a `List<T>` formal accepts only a **concrete** Java generic argument (`ArrayList[String]` works), never a raw `ArrayList()`; a lambda and `null` say nothing. A call that can neither infer nor write its arguments fails like this:

<<< @/snippets/book_en/appendix_f_generic_missing.spr

```text
SPR-JVM-MEMBER [JVM] main.spr:4:12: Java class Collectors has no method 'toList' matching 0 argument(s)
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

The message says there is no `toList` overload matching zero arguments—its type parameter has no argument to come from. `sprig check --json` lists each candidate's unresolved type under `data.candidates`, and `repair.kind` is `inspect-api-and-make-arguments-explicit`. The fix is `Collectors.toList[Int]()`.

Generic arguments are invariant between each other: `ArrayList[String]` is accepted as `List[String]` (the subtype projects to the interface), but `ArrayList[Int32]` is not a `List[String]`, and a raw `ArrayList()` never "becomes" any concrete type.

Wildcards keep their bounds. Sprig has no wildcard syntax of its own, but Java results can carry one, for example:

```text
$ sprig api java.lang.Object --member getClass
Java API: java.lang.Object
constructors:
staticMethods:
instanceMethods:
  public final native java.lang.Class<?> java.lang.Object.getClass() => getClass() -> java.lang.Class[?]?
fields:
```

A `Class[?]?` value can be received, null-checked and passed on to another Java method, but cannot be written in a Sprig declaration. A container with `? extends` is read-only: elements read through it are typed by the bound, and any write (`add`) is rejected.

<<< @/snippets/book/ch13_wildcards.spr

```text
[3, 1]
ArrayList
3
```

## F.4 Implementing a Java interface with conform

**Why.** Java frameworks ask for "an object that implements this interface": a thread wants `Runnable`, a callback wants `Consumer`. A Sprig class can declare that it satisfies a Java interface, with `conform`:

<<< @/snippets/book/ch13_conform_interface.spr

```text
hi ada
hi ada
```

Line by line:

- `class Greeter` has `func run() -> Unit`, matching the shape of the single abstract method of `Runnable`.
- `conform Greeter to Runnable` is a declaration: no methods, no adaptation. After checking the shapes, the compiler puts `Runnable` in the generated Java class's interface list.
- `let task: Runnable = Greeter(name="ada")` hands the Sprig object to the Java view; `Thread(task)` accepts it as usual.
- `conform` must be written in the module that declares the class: there is no retroactive or structural "the shape matches so it counts" conformance (chapter 16's contract classes also require an explicit `conform`).

The rules (`sprig help conform`):

- the target must be an imported **public, non-generic, non-sealed Java interface**;
- every abstract instance method needs a method of the class with the same name, the same JVM parameter shapes and the same JVM result shape; overloaded abstract methods are not supported;
- a class may conform to several interfaces, but each relation is written once;
- `Task → Runnable` and `Task? → Runnable?` hold; `Task? → Runnable` needs narrowing first; `List[Task] → List[Runnable]` never holds (generics are invariant).

## F.5 Extending a Java class: `conform ... to J(fields) as parent`

**Why.** Some Java frameworks require you to extend their class and override methods (a template method, say). Sprig classes have no inheritance, but `conform C to J(field, ...) as parent` makes the generated class `extends J`.

<<< @/snippets/book_en/ch13_conform_class.spr

```text
30
63
2
48
3
```

Line by line:

- `class Counting` has the fields `seed` and `calls` and defines `next(bits: Int32) -> Int32`.
- `conform Counting to Random(seed) as parent`: `seed` in the parentheses is a field of `Counting`, passed in order to the constructor of `Random` whose JVM shapes match (`Random(long)`); `as parent` gives the inherited implementation a name visible inside the class.
- `func next` overrides `Random`'s protected `next(int)` (call `sprig api java.util.Random --member next` to see it under `protectedMethods`). The body's `parent.next(bits)` is Java's `super.next(bits)`.
- The first two `rng.nextInt(100)` calls go through the overridden `next`, so `rng.calls` is 2. Handing `rng` to the parent view (`let plain: Random = rng`) and calling once more prints `48` and then `calls` reads 3: the `next` called inside `Random.nextInt` is still `Counting`'s override, so the parent view does not split the object's identity.

The rules (`sprig help conform` and `docs/jvm/conformance.md`):

- the target must be a public, non-final, non-generic, non-sealed Java class, abstract or concrete;
- the parentheses may hold only field names of `C`, never expressions: `super(...)` runs before any field exists; `conform C to J()` chooses the no-argument constructor;
- a `C` method whose name matches a public/protected method of the chain must match one of its shapes exactly, or Java would quietly add an overload and the hook would never run; overriding a `final` method or hiding a `static` one is `SPR-CONFORM-MEMBER`;
- `parent` is not a value, has no fields and cannot call abstract methods; protected methods are callable only through it, not on a typed value;
- on a value of `C`, undeclared methods still resolve through the Java view (`rng.nextInt(100)`);
- generic superclasses, a second Java superclass and constructor expressions are outside v1.

## F.6 Functional interfaces

**Why.** Java uses "an interface with exactly one abstract method" to mean "code to run later". A Sprig `fn` value goes directly into those positions, with its parameter types written out.

<<< @/snippets/book_en/appendix_f_functional.spr

```text
[ada, bob]
[ADA, BOB]
```

- `removeIf` takes a `Predicate`: `fn(name: String) => name.length() < 3` removes short names.
- `replaceAll` takes a `UnaryOperator`: it replaces elements in place, and a `Unit` result is fine.
- Both interfaces' type arguments are bound through the receiver (`ArrayList[String]`), so the lambda's parameter type is simply `String`.

The rules:

- one abstract method, no method-level type parameters, at most three parameters;
- parameter types must map exactly to the interface method (chapter 21's `SPR-SYNTAX-ERROR` was a missing type);
- a `void` result accepts any lambda result; an `int` result also accepts a lambda returning `Int` (with a runtime range check);
- a wildcard in the interface's type arguments reads as its bound, so a lambda implementing `Consumer<String>` satisfies `Consumer<? super String>`;
- the lambda's type must not carry `throws Error`: Java cannot see that. Passing one directly fails:

<<< @/snippets/book_en/appendix_f_callable_throws.spr

```text
SPR-TYPE-CALLABLE-THROWS [TYPE] main.spr:7:21: A function value that may throw Error cannot be passed to Java parameter 1 of Thread; Java cannot see the throws clause (expected fn() -> Unit, actual fn() -> Unit throws Error)
  hint: Handle the error inside a named function and pass a lambda that calls it.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

The fix is to handle the error on the Sprig side and pass a function value that cannot throw: the Java-side interface has only `void run()`, with nowhere to express "may fail".

## F.7 Checked exceptions

**Why.** Java puts "may fail" in the signature: checked exceptions such as `IOException` and `SQLException` must be handled. Sprig treats them as facts just like `throws Error`.

<<< @/snippets/book_en/ch13_exceptions.spr

```text
could not read: java.nio.file.NoSuchFileException: definitely-missing.txt
```

- `Files.readString` carries `throws IOException`, so `read_text` declares `throws IOException` and passes the responsibility to its caller.
- The top-level `try` / `catch problem: IOException:` catches the failure. `NoSuchFileException` is a subclass of `IOException`, so one `catch` handles it.
- The exception object is a Java value: `problem.toString()` is always non-null and formatted `ClassName: message`; `problem.message` is `String?`, because `getMessage()` may return `null`.
- **A Java exception is not a Sprig `Error`**: `catch problem: Error:` does not catch `NoSuchFileException`; conversely, Java code that turns a Sprig error into text sees `sprig.runtime.SprigError: message`.
- A `catch problem: IOException:` whose `try` block cannot throw it is `SPR-FLOW-CATCH-NEVER-THROWN`.

Inside a **named function** a checked exception is either caught with `try`/`catch` or declared in `throws`. Top-level statements are a provisional exception: `sprig check` does not demand handling, and at runtime an uncaught one aborts the program:

```text
$ sprig check toplevel.spr
$ sprig run toplevel.spr
SPR-RUNTIME-EXCEPTION [RUNTIME] toplevel.spr:6:5: NoSuchFileException: definitely-missing.txt
  hint: Inspect the failing operation and the values it received. Run with --stacktrace to see the JVM stack.
```

Unchecked exceptions (`ArithmeticException`, `NumberFormatException`) need no declaration but may be written in `throws`; either way callers are unaffected.

In `conform`, a witness method's `throws` may only be a **subset** of the checked exceptions the Java interface method permits, or it is `SPR-CONFORM-EFFECTS`; Sprig's `Error` maps to the unchecked `SprigError` and is always allowed.

## F.8 The classpath: `--classpath` and `--classpath-file`

**Why.** To use someone's JAR or classes you compiled with `javac` yourself, you have to tell the compiler where to look. Every command with `--classpath` (`api`, `check`, `build`, `run`, `lsp`) also has `--classpath-file`.

```text
$ javac -d out/classes src/fixture/*.java
$ sprig check use_library.spr --classpath out/classes
$ sprig check use_library.spr --classpath-file classpath.txt
$ echo $?
0
```

`classpath.txt` holds one JAR or directory per line; blank lines and `#` comments are ignored:

```text
# one JAR or directory per line; blank lines and # comments are ignored
out/classes
```

This form is for build tools: the Windows command line has a length limit, and a long `--classpath` will not fit. Repeated `--classpath` entries work too, as does the platform path separator for several entries at once.

Lookup order is: JDK runtime classes → the project's locked dependency JARs (in recorded order) → explicitly passed entries; the first match wins. A missing entry is an error rather than a silent skip:

```text
$ sprig api fixture.Library --classpath nope.jar
SPR-JVM-CLASSPATH [JVM] Classpath entry does not exist: .../nope.jar
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

(The real output shows the absolute path.) Inside a project none of this is needed: `[[jvm]]` dependencies in `sprig.toml` are downloaded and locked by `sprig resolve`, and `api`/`check`/`build`/`run` use the locked JARs automatically (chapter 18).

## F.9 Turning a Java class into Sprig source: sprig wrap

**Why.** Copying method signatures out of an unfamiliar Java class by hand invites mistakes. `sprig wrap` reads the same classpath and generates the supported methods as ordinary, editable Sprig source:

```text
$ sprig wrap fixture.Library --classpath out/classes --out wrapped.spr
Wrapped fixture.Library -> .../wrapped.spr
  generated: 4, skipped: 0
```

The first lines of the generated `wrapped.spr`:

```sprig
# Generated by sprig wrap from fixture.Library.
# Ordinary Sprig source; safe to edit.
# Regeneration overwrites only with --force.

import fixture.Library as Host

class Library:
    let host: Host

    func label() -> String?:
        return host.label()
```

- unsupported methods are skipped with structured reasons (`varargs`, wildcards, generic arrays, ...), never guessed;
- `--force` is required to overwrite an existing file; the generated source is checked before it is written;
- it is conservative: `label` is annotated `@NonNull`, yet the wrapper still returns `String?`, and one extra null check is always safe;
- `--member` wraps a single method.

## F.10 JVM type mapping quick reference

| Java | Sprig | Notes |
|---|---|---|
| `long` | `Int` | 64-bit integer |
| `int`, `short`, `byte` | `Int32` | fits in 32 bits |
| `double` | `Float` | |
| `float` | `Float32` | `Float` never narrows automatically |
| `boolean` | `Bool` | |
| `char` | `String` | a string literal of one UTF-16 unit |
| boxed types (`Long`, `Integer`, ...) | nullable counterparts (`Int?`, `Int32?`, ...) | results are nullable by default |
| reference results | `T?` | except `toString()` and non-null annotations |
| arrays such as `byte[]` | opaque values | use the `HostBytes` helpers for `byte[]` |
| collections such as `List`/`Map` | no implicit conversion | copy explicitly with `@std/jvm` |

A few easy-to-forget points:

- an `Int` passed to an `int`/`Integer` formal narrows automatically with a runtime range check, and an exact `long` overload is still preferred; a literal that does not fit an `int` fails at check time (`SPR-JVM-MEMBER`);
- a `Float` never becomes a `float` on its own: write `toFloat32Lossy()` (rounding allowed) or `toFloat32Exact()`;
- arrays cannot be constructed, indexed or iterated; use `HostBytes.utf8`/`utf8String`/`length`/`hex` for `byte[]`;
- a generic class takes concrete arguments (`ArrayList[String]()`); a generic method infers when it can and is written otherwise (F.3);
- Java parameters are non-null by default; an `Int?` going to an `int` slot must be narrowed first (`if x != null` or `or_else`).

## Summary

- `sprig api --json` gives machine-readable `nullableResult`, `interopLevel`, `interopReasonCodes` and more; `protectedMethods` pairs with the parent view.
- Nullability annotations are matched by simple name: `NotNull`/`NonNull`/`Nonnull` make results non-null, `Nullable`/`CheckForNull` let parameters accept `null`; class-level defaults need runtime-visible annotations, and parameters never become nullable from a default.
- Generic methods infer when the arguments fix the type parameters exactly, otherwise write `method[Type](...)`; lambdas, `null` and raw values provide no information.
- `conform C to Interface` declares an implementation; `conform C to Class(fields) as parent` extends a Java class and uses the parent view to call the overridden `super` methods.
- `fn` values go to functional interfaces with written parameter types; those carrying `throws Error` cannot.
- Checked exceptions must be handled or declared in named functions; top-level statements are provisional; a `conform` witness may throw only a subset of what the interface allows.
- `--classpath` / `--classpath-file` share the lookup order with a project's locked dependencies; `sprig wrap` turns a Java class into editable Sprig source.

## Exercises

**Exercise 1 (easy).** Use `sprig api` on `java.lang.Integer.parseInt`. Which overload takes a `String`? Is the result nullable?

::: details Answer
```text
$ sprig api java.lang.Integer --member parseInt
Java API: java.lang.Integer
constructors:
staticMethods:
  public static int java.lang.Integer.parseInt(java.lang.CharSequence,int,int,int) throws java.lang.NumberFormatException => parseInt(CharSequence, Int32, Int32, Int32) -> Int32
  public static int java.lang.Integer.parseInt(java.lang.String) throws java.lang.NumberFormatException => parseInt(String) -> Int32
  public static int java.lang.Integer.parseInt(java.lang.String,int) throws java.lang.NumberFormatException => parseInt(String, Int32) -> Int32
instanceMethods:
fields:
```

The overload taking a `String` is `parseInt(String) -> Int32`. `int` is primitive, so it maps to the non-null `Int32` (not `Int32?`).
:::

**Exercise 2 (easy).** Use `removeIf` and a lambda to remove every name starting with `"a"` from `["ada", "alan", "bob"]`, and print what remains.

::: details Answer
<<< @/snippets/book/appendix_f_ex_predicate.spr

```text
[bob]
```

`removeIf` takes a `Predicate` and the lambda returns `Bool`; `startsWith` is a Java `String` method.
:::

**Exercise 3 (medium).** Change the `Random` example from F.5 to override `nextInt(bound)`: count how often `nextInt` is called, and let each call go through the inherited implementation. Hint: the signature is `nextInt(bound: Int32) -> Int32`, matching the public method of `Random`.

::: details Answer
<<< @/snippets/book_en/appendix_f_ex_parent.spr

```text
30
63
2
```

`nextInt`'s shape matches the parent's, so it overrides; `parent.nextInt(bound)` calls the inherited implementation. After the two `print(rng.nextInt(100))` calls, `calls` is 2.
:::

**Exercise 4 (harder).** Put the fixture class directory from F.8 into `classpath.txt`, check `use_library.spr` with `--classpath-file`, then deliberately misspell the path once and see what is reported. Hint: blank lines and `#` comments are allowed in the file.

::: details Answer
```text
$ cat classpath.txt
# one JAR or directory per line; blank lines and # comments are ignored
out/classes

$ sprig check use_library.spr --classpath-file classpath.txt
$ echo $?
0
$ sed -i '' 's|out/classes|out/typo|' classpath.txt
$ sprig check use_library.spr --classpath-file classpath.txt
SPR-JVM-CLASSPATH [JVM] Classpath entry does not exist: .../out/typo
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

A passing `check` prints nothing and exits 0. A wrong path reports `SPR-JVM-CLASSPATH` immediately, with the resolved absolute path.
:::

Back to: [21. Calling Java](/en/tutorial/ch21-java), or put it to work in [chapter 24's project](/en/tutorial/ch24-project-ledger).

::: tip Coming from another language?
If you write Java: `conform` here is a constrained version of `implements` and `extends` in one line—zero adaptation and explicit declaration for interfaces, and constructor fields plus exact overrides for a superclass. The code `sprig wrap` generates is ordinary Sprig, a good starting point for calling an unfamiliar library.
:::
