# Sprig v0.7 — language contract (proposed)

## General principles

Target: an independent small statically typed JVM language that initially translates typed Sprig IR to Java source (`javac` compiles that source). **Not** a Java syntax superset. The reference ANTLR4 frontend and Java layout adapter are not a compiler. Keep one canonical syntax for each operation rather than accumulating aliases. No pipeline operator. All named functions/methods declare parameter and return types, including `-> Unit`. Variables can use initializer-based local inference; class fields need explicit types. Expressions do not use implicit truthiness or chained comparisons.

## 1. Names and binding

`let` prohibits rebinding a variable or reassigning a `let` class field after construction; `var` permits it. Neither recursively freezes objects. Inside a method, unqualified current-instance fields resolve to that class's fields. A parameter/local cannot shadow any current-class field, and duplicate members are rejected. Use explicit receiver for *other* objects. No `self` keyword in this version; passing the current receiver as a value remains an explicit **open** language design question. Identifier precedence across module/import scopes must be deterministic and ambiguity must be diagnosed, not arbitrarily resolved.

## 2. Types and functions

Native types: `Int` (signed 64-bit), `Float` (64-bit binary floating), `Bool`, `String`, `Unit`, and nullable `T?`. Collections: `List[T]`, `MutableList[T]`, `Map[K,V]`, `MutableMap[K,V]` with distinct mutability types. No implicit conversions between a mutable list and immutable list: use explicit snapshot methods returning a new outer collection. Typed empty literal requires contextual type. Generic type application syntax is recognized but user-defined generic declarations/constraints are **not** specified. Basic numeric operator behavior, overflow and integer division require explicit specification before full runtime claims. A `Bool` is required by conditions, without truthiness/coercion. Anonymous expression lambdas are `fn(x: Int) => expr` with inferred expression result; named funcs require explicit results.

## 3. Object construction

Every Sprig class has one compiler-synthesized constructor. Invocation requires **only named fields**: `Hero(name="Ada")`, `Hero(name="Ada", health=20)`. Fields without defaults are required; defaults evaluate once **per new instance**, in declaration order. Unknown/duplicate/missing fields are compile errors. Ordinary functions and Java/JVM calls take positional arguments only. Syntax forbids mixing positional and named arguments in one call. The semantic checker determines whether a call is a class constructor, variant constructor, or ordinary/JVM method and enforces its call category. Any default expression referencing uninitialized fields or producing checked exceptions needs explicit rejection/handling; do not expose partially initialized objects.

## 4. `enum`, `variant`, and exhaustive `match` (NEW)

`enum TokenKind:` defines unique, immutable, **payloadless** cases such as `TokenKind.EOF`. `variant Expr:` defines a sealed, nominal sum type with unique cases and zero or more immutable named typed fields. A case constructor has the single canonical named style: `Expr.Literal(value=3)`; a payloadless case is the value `Expr.End` (not `Expr.End()`). `Expr.Binary(left=..., right=...)` builds a variant value. Nested recursive type references (such as `left: Expr`) are allowed; nullability remains explicit.

`match value:` is a **statement**, with one `case Qualified.Case:` or `case Qualified.Case as binder:` clause for each case of the matched enum/variant. The matched value evaluates exactly once. No default/wildcard and no fallthrough; the *semantic* checker rejects non-enum/non-variant scrutinee, duplicated cases, cases from a different type, non-exhaustive matches and bindings on payloadless enums. For variant payloads, `as binder` binds the concrete case object so `binder.left` has a static type. Binder scope is **only** its case suite. A branch may omit the binder when its payload is unused. This avoids nested pattern/destructuring syntax and keeps object fields named and explicit. Match exhaustiveness is semantic, never guaranteed by `.g4` alone. `match` expression is not supported; a function returning a value returns in each case or assigns a variable declared before the match.

## 5. Nullability, errors, and effects

`null` is only assignable to `T?`; an immutable local can be narrowed after a proven `x != null` check. Mutable fields are not silently narrowed across calls/assignments. Expected absence uses `T?`; anticipated recoverable errors use `-> T throws ErrorType` plus `try` / `catch error: ErrorType`, or explicit propagation via a function's declared errors. Runtime defects are unchecked. Mapping of Java checked and unchecked exceptions is **provisional**, must be documented before enabling interop. `throws` is currently a *design proposal*, not a proof that all Java exceptions are checked. No built-in duplicate `Result` error channel in v0.7.

## 6. Modules, JVM integration, and tools

Top-of-file, module-level imports: `import "./math.spr" as math` or `import java.time.LocalDate as Date`. No implicit import, module scanning or network download on `import`; versions and dependencies are declared in a project manifest and lockfile (formats not yet implemented). JVM classes and their accessible methods are resolved against a pinned JDK and dependency classpath, with deterministic overload checking and explicit interop conversion rules. Java `List` is not silently treated as immutable Sprig `List`. Language server, compiler and agent-query interfaces **should** share the same symbol/type/JVM API index; they are not implemented in this kit.

## 7. Bootstrap feasibility

The sum type + exhaustive match is primarily motivated by expressing token/AST variants and catching forgotten cases when a compiler's AST evolves. String/codepoint APIs, file IO, collection iteration/indexing, diagnostics, imports and primitive conversions must become usable library facilities before Sprig can bootstrap. An external JDK/JVM remains an acceptable host; first-stage compiler can be in Java, second stage in Sprig. A grammar file does NOT imply self-hosting.
