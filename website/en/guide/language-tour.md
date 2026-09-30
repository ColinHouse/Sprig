# Language Tour

This tour covers the language as the stage-0 compiler actually implements it.
Every snippet on this page is a real file under `website/snippets/` in the
repository and is executed by `scripts/internal/verify-doc-snippets.py` during
documentation checks.

The normative documents are the
[language spec (design contract)](https://github.com/ColinHouse/Sprig/blob/main/docs/history/design-kit/LANGUAGE_SPEC.md) and the
[implemented feature status](/en/reference/language/feature-status). Where the
design kit proposes more than the compiler does, this page follows the
compiler.

## Layout and comments

Blocks are indentation-based. The first code line starts at column 1, one
indent level is any consistent number of spaces, and tabs are rejected with
`SPR-LEX-TAB`. Newlines inside `()`, `[]` and `{}` are ignored, so calls and
literals may span lines. `#` starts a comment.

## Bindings

<<< @/snippets/variables.spr

`let` binds once and cannot be reassigned; `var` can. Local bindings infer
their type from the initializer, while class fields always need an explicit
type. There is no implicit truthiness: conditions must be `Bool`.

## Functions

<<< @/snippets/functions.spr

Every named function and method declares parameter types and a return type,
including `-> Unit`. Calls use positional arguments. A function that can fail
adds `throws ErrorType` (see [errors](#errors) below).

## Classes

<<< @/snippets/classes.spr

A class declares `let` (immutable) and `var` (mutable) fields with optional
defaults. Construction is always named: `Hero(name="Ada", health=80)`.
Missing, unknown or duplicate fields are compile errors. Methods access the
current instance's fields without a prefix; parameters and locals may not
shadow a field.

## Enums, variants and match

<<< @/snippets/variants.spr

`enum` cases carry no payload. `variant` declares a sealed sum type whose
cases have immutable named fields. Statement `match` allows multi-statement suites; expression `match` produces a
value with exactly one expression per branch. Each case names
one enum or variant case, optionally binding the payload with `as node`.
Missing, duplicate, wrong-type and unreachable branches are compile errors;
there is no `default` or wildcard, and no fallthrough. Because the match is
exhaustive by construction, adding a case to a variant forces every visitor to
handle it — the property the compiler itself relies on in
`tests/visitor/ast_visitor.spr`, a multi-visitor AST interpreter written in
Sprig and run by the test suite.

## Collections

<<< @/snippets/collections.spr

`List[T]` and `Map[K,V]` are read-only; `MutableList[T]` and `MutableMap[K,V]`
are mutable. `toMutableList()`, `toList()`, `toMutableMap()` and `toMap()`
produce new outer collections. Mutation through an immutable type is rejected
with `SPR-COLLECTION-IMMUTABLE`. Indexing, `in`, `get`, `set`, `append`,
`sort` and the higher-order `map`/`filter`/`forEach` methods are implemented.
Floating-point map keys are rejected because IEEE equality and hashing
disagree for `NaN` and signed zero.

## Nullability

<<< @/snippets/nullable.spr

`null` is only assignable to `T?`. A value narrows to non-null inside a proven
`!= null` branch; using a possibly-null value where non-null is required is
`SPR-TYPE-NULLABLE`. Mutable fields are not narrowed across calls. Java
reference results are conservatively nullable (see
[JVM interoperability](/en/guide/jvm-interop)).

## Errors

<<< @/snippets/errors.spr

A function declares the error types it can raise with `throws`. Callers must
either handle them with `try`/`catch` (plus optional `finally`) or declare the
same effect; `SPR-FLOW-THROWS` is reported otherwise. `Error` values expose a
`message` field. Java checked exceptions can be caught as the imported Java
exception class.

## Explicit generics

<<< @/snippets/generics.spr

User-defined classes, variants and functions support `generic T:` or
`generic K, V:` blocks. Every use writes explicit type arguments, such as
`Box[Int](value=42)`; parameters are invariant and there is no inference.
Equality on a parameter requires `requires T: Equatable`. See the
[generics guide](/en/guide/generics) for the implemented contract.

## Lambdas

<<< @/snippets/lambdas.spr

Lambdas are expressions: `fn(x: Int) => expression`. Arities 0 through 3 are
supported, bodies are single expressions, and a lambda cannot declare
`throws`. A lambda that captures a `var` local is rejected
(`SPR-TYPE-CAPTURE`); copy it into a `let` binding first.

## JSON object lookup

<<< @/snippets/json_lookup.spr

`json.find_member` distinguishes `Missing`, `Found(value: json.Value)` and
`NotObject`. Present JSON null, false, zero and empty strings remain found
values. Duplicate object keys still raise `Error`; the object member order is
preserved. The [standard-layer contract](https://github.com/ColinHouse/Sprig/blob/main/docs/projects/standard-library.md)
explains parsing, lookup and serialization boundaries.

## Function types

A function type spells parameter types and result types:

<<< @/snippets/function_types.spr

`fn(Int) -> Int` is a **type**; `fn(x: Int) => x + 1` is a **value expression**.
Arity is 0–3. Parameters and results are invariant: there is no function
subtyping, implicit conversion or untyped fallback. Use parentheses for outer
nullability, `(fn(Int) -> Int)?`; `fn(Int) -> Int?` has a nullable result.
Ordinary null narrowing applies before invoking a nullable function value.
Function types may annotate locals, fields, parameters and results, or occur in
explicit generic arguments. Function types cannot declare `throws`; checked
errors must be handled inside a non-throwing callable.

The JVM bridge accepts corresponding Sprig-owned `sprig.runtime.Fn0`–`Fn3`
formal signatures with supported concrete type arguments. It does not convert
functions to arbitrary Java `Function`, `Consumer`, `Runnable` or interfaces.
Ask `sprig api <Class> --json` about the actual formal signature.

## Modules

<<< @/snippets/modules/main.spr

<<< @/snippets/modules/math_module.spr

`import "./file.spr" as alias` imports another Sprig file. Imports must appear
before any declaration or statement. The imported module initializes once;
import cycles are rejected with `SPR-NAME-IMPORT-CYCLE`. Importing Java
classes uses the same syntax with a qualified class name:
`import java.time.LocalDate as LocalDate`.

## What is not in the language

Generic inference, variance, inheritance and interfaces, `%=`,
tuples/destructuring and string interpolation are **not implemented**. Source
array syntax, varargs and wildcard shapes are outside the JVM interop profile
(arrays still cross as opaque foreign values; see
[JVM interoperability](/en/guide/jvm-interop)). See
[Known limitations](/en/reference/language/known-limitations) for the full list, and the
[stage-1 roadmap](/en/reference/language/stage1-roadmap) for what comes next.

## Conservative ergonomics

Use [explicit declaration facades](/en/reference/language/module-reexports),
[value-producing matches](/en/reference/language/match-expressions) and
[canonical comment-preserving formatting](/en/reference/tooling/formatter).
These add no wildcard exports, block expressions or formatter configuration.
