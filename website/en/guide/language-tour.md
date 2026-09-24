# Language Tour

This tour covers the language as the stage-0 compiler actually implements it.
Every snippet on this page is a real file under `website/snippets/` in the
repository and is executed by `tools/verify-doc-snippets.py` during
documentation checks.

The normative documents are the
[language spec (v0.7 design)](/en/reference/LANGUAGE_SPEC) and the
[implemented feature status](/en/reference/FEATURE_STATUS_IMPLEMENTED). Where the
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
cases have immutable named fields. `match` is a **statement**: each case names
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

## Lambdas

<<< @/snippets/lambdas.spr

Lambdas are expressions: `fn(x: Int) => expression`. Arities 0 through 3 are
supported, bodies are single expressions, and a lambda cannot declare
`throws`. A lambda that captures a `var` local is rejected
(`SPR-TYPE-CAPTURE`); copy it into a `let` binding first.

## Modules

<<< @/snippets/modules/main.spr

<<< @/snippets/modules/math_module.spr

`import "./file.spr" as alias` imports another Sprig file. Imports must appear
before any declaration or statement. The imported module initializes once;
import cycles are rejected with `SPR-NAME-IMPORT-CYCLE`. Importing Java
classes uses the same syntax with a qualified class name:
`import java.time.LocalDate as LocalDate`.

## What is not in the language

User-defined generics, inheritance and interfaces, `match` expressions,
`%=`, tuples/destructuring, arrays, varargs, string interpolation and function
types in source are **not implemented**. See
[Known limitations](/en/reference/KNOWN_LIMITATIONS) for the full list, and the
[stage-1 roadmap](/en/reference/STAGE1_ROADMAP) for what comes next.
