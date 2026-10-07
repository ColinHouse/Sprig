# Sprig diagnostic codes (stable)

Generated from `bin/sprig codes`. Codes never change meaning;
new behavior gets a new code. See also `sprig explain <code>`.

| Code | Meaning |
|---|---|
| SPR-CLI-OPTION | A CLI command is missing a required argument or has an unknown option. |
| SPR-API-TARGET | The requested Sprig module or project API target cannot be resolved. |
| SPR-API-MEMBER | The requested `--member` does not exist on the inspected Sprig declaration. |
| SPR-CALL-ARITY | Wrong number of arguments. |
| SPR-CALL-DUPLICATE-FIELD | The same named field was provided twice. |
| SPR-CALL-MISSING-FIELD | A required field was not provided. |
| SPR-CALL-NAMED-REQUIRED | Sprig class and variant constructors require named arguments. |
| SPR-CALL-POSITIONAL-REQUIRED | Functions and JVM calls require positional arguments. |
| SPR-CALL-UNKNOWN-FIELD | Named argument does not match any field. |
| SPR-COLLECTION-IMMUTABLE | List/Map are read-only; convert with toMutableList()/toMutableMap(). |
| SPR-FLOW-BREAK | break is only valid inside a loop. |
| SPR-FLOW-CATCH-NEVER-THROWN | A catch names a checked Java exception that nothing in its try block can throw. |
| SPR-FLOW-CONTINUE | continue is only valid inside a loop. |
| SPR-FLOW-MISSING-RETURN | A non-Unit function must return on every path. |
| SPR-FLOW-RETHROWS | rethrows needs a parameter whose function type declares throws Error, and the function may throw nothing of its own. |
| SPR-FLOW-THROWS | A recoverable error must be declared with throws or caught. |
| SPR-FLOW-THROWS-UNUSED | A function declares a checked Java exception that its body can never throw. |
| SPR-FLOW-UNREACHABLE | Statement follows a statement that always exits. |
| SPR-CONFORM-EFFECTS | A witness method declares checked exceptions the Java interface method does not permit. |
| SPR-CLASS-ABSTRACT | A contract class (a class whose methods have no body) declares fields, mixes methods with and without a body, is generic, or is constructed; a contract is implemented by classes that conform to it. |
| SPR-CONFORM-MEMBER | A class method does not exactly match the Java method it witnesses or overrides, or a required abstract method is missing. |
| SPR-CONFORM-OVERLOAD | The Java interface requires overloaded abstract methods, which Sprig classes cannot represent. |
| SPR-CONFORM-PARENT | The parent view of a class that extends a Java class only calls inherited methods; it is not a value, has no fields and cannot reach abstract methods. |
| SPR-CONFORM-SOURCE | The conform source must be a non-generic Sprig class with method bodies, declared in this module; there is no retroactive conformance and a contract never conforms. |
| SPR-CONFORM-TARGET | The conform target must be an imported public, non-generic, non-sealed Java interface, or with parentheses a public, non-final, non-generic Java class (or the built-in `Error`, which makes an error class) whose constructor the named fields select. |
| SPR-JVM-AMBIGUOUS | The Java overload is ambiguous for these argument types. |
| SPR-JVM-CLASS | The imported Java class could not be loaded, or it lives in the unnamed package. |
| SPR-JVM-CLASSPATH | A `--classpath` entry is empty, missing, or not a JAR/directory. |
| SPR-JVM-COMPILE | The generated Java source did not compile; may be a compiler bug. |
| SPR-JVM-INTERNAL | Internal compiler or tooling failure. |
| SPR-JVM-MEMBER | No Java method/constructor/field matches this call. |
| SPR-LEX-CHAR | The input contains a character outside the Sprig lexer. |
| SPR-LEX-INDENT-FIRST | The first code line of a file must start at column 1. |
| SPR-LEX-INDENT-INCONSISTENT | A dedent must return to a previous indentation level. |
| SPR-LEX-STRING | A string literal is unterminated or contains an invalid escape. |
| SPR-LEX-TAB | Tabs are forbidden; Sprig indentation uses spaces only. |
| SPR-LEX-UNCLOSED | A '(' '[' or '{' was opened and never closed. |
| SPR-LEX-UNMATCHED | A closing delimiter has no matching opener. |
| SPR-MATCH-DUPLICATE | A match branch repeats a case. |
| SPR-MATCH-ENUM-BINDER | Payloadless enum cases cannot bind 'as name'. |
| SPR-MATCH-INFERENCE | An expression match needs an explicit result type because its branches establish no non-null type. |
| SPR-MATCH-NONEXHAUSTIVE | Every enum/variant case must have a match branch; there is no default. |
| SPR-MATCH-RESULT | An expression-match branch has an incompatible result type. |
| SPR-MATCH-SCRUTINEE | match requires a non-nullable enum or variant value. |
| SPR-MATCH-UNKNOWN-CASE | The case name does not exist on the matched type. |
| SPR-MATCH-WRONG-TYPE | A match branch belongs to a different enum/variant. |
| SPR-MODULE-EXPORT | A declaration reexport names an unknown or illegal target, or conflicts with a visible name. |
| SPR-MODULE-EXPORT-ORDER | Imports, declaration reexports and ordinary definitions are out of order. |
| SPR-NAME-DUPLICATE | Two declarations share one name in one namespace. |
| SPR-NAME-DUPLICATE-MEMBER | A class/variant declares the same member twice. |
| SPR-NAME-FIELD-SHADOW | A parameter/local cannot shadow a current-class field. |
| SPR-NAME-IMPORT | An imported file or class cannot be resolved. |
| SPR-NAME-IMPORT-CYCLE | Sprig modules form an import cycle. |
| SPR-NAME-LET-ASSIGN | let bindings and let fields cannot be reassigned. |
| SPR-NAME-FORWARD-REFERENCE | Top-level code uses a top-level binding before its declaration runs. |
| SPR-NAME-MODULE | Module import/alias problem. |
| SPR-NAME-NOT-A-TYPE | A value name was used where a type is required. |
| SPR-NAME-NOT-A-VALUE | A type or module name was used as a value. |
| SPR-NAME-UNRESOLVED | A name has no declaration in the current scope chain. |
| SPR-NUM-RANGE | A numeric literal is outside its target range or underflows to zero. |
| SPR-NUM-CONVERSION | An implicit numeric conversion risks precision or range loss. |
| SPR-NUM-DIVISION | Integer/BigInt `/` would truncate, or Decimal `/` lacks a rounding policy. |
| SPR-NUM-MIXED | A numeric operator cannot implicitly mix these numeric families. |
| SPR-RUNTIME-ERROR | Uncaught Sprig Error value at runtime; the wrapped message, `origin` data and source range are reported. |
| SPR-WRAP-CHECK | Generated Java-to-Sprig wrapper source failed Sprig checking or formatting. |
| SPR-RUNTIME-EXCEPTION | Uncaught JVM exception at runtime, wrapped to a Sprig-level message with a source range; `run --stacktrace` restores the raw JVM stack. |
| SPR-PROGRAM-EXIT | The Sprig program exited with a non-zero process status. |
| SPR-SYNTAX-ERROR | The token sequence does not match the Sprig grammar. |
| SPR-TYPE-ASSIGN | Assignment value does not match the target type. |
| SPR-TYPE-FUNCTION-ARITY | Function types and lambdas support zero to three explicitly typed parameters. |
| SPR-TYPE-CALLABLE-THROWS | A function value's throws clause does not fit where it is used; only fn(...) -> R throws Error exists, and it is not accepted where a function type without throws is expected. |
| SPR-TYPE-CAPTURE | A lambda captures a var local; copy it into a let binding first. |
| SPR-TYPE-CONDITION | Conditions must be Bool; Sprig has no truthiness. |
| SPR-TYPE-INFER | The type cannot be inferred without an annotation. |
| SPR-TYPE-MISMATCH | Expected and actual types are not compatible. |
| SPR-TYPE-NOT-CALLABLE | The callee is not callable (or a method name was used as a value). |
| SPR-TYPE-NULL | null is only assignable to an explicit nullable type T?. |
| SPR-TYPE-NULLABLE | A possibly-null value is used where non-null is required, including a `T?` joined into a String; check for null first. |
| SPR-TYPE-OPERAND | Operator or method is not defined for this operand type. |
| SPR-TYPE-RETURN | Returned value does not match the declared return type. |
| SPR-TYPE-UNIT | Unit is only a function/method result; it cannot be a field, parameter, collection element, or ordinary value. |
| SPR-TYPE-GENERIC-ARITY | A generic declaration was used with the wrong number of type arguments (supply every parameter in declaration order). |
| SPR-TYPE-GENERIC-ARGS-REQUIRED | A generic call or constructor needs written `[Type]` arguments: its arguments do not say what a type parameter is, or two of them disagree. |
| SPR-TYPE-GENERIC-NULLABLE | This type parameter is used with `?` in the declaration, so its argument must be non-nullable. |
| SPR-GENERIC-CONSTRAINT | A `requires` clause names an unknown capability, or a contract or class in place of one (a contract is a type, never a bound), or is misplaced, or a type argument is not `Comparable` where the callee requires it. |
| SPR-PROJECT-MANIFEST | `sprig.toml` is missing, malformed, or lacks a required field. |
| SPR-PROJECT-ENTRY | The project entry point is missing or the named `--bin` is unknown. |
| SPR-PROJECT-UNSUPPORTED | The project or a dependency needs project features or a language version this compiler does not support. |
| SPR-PROJECT-LOCK-MISSING | This project has no `sprig.lock`; run `sprig resolve`. |
| SPR-PROJECT-LOCK-STALE | Compiler identity, `sprig.toml` or a dependency manifest differs from `sprig.lock`; run `sprig resolve`. |
| SPR-PROJECT-LOCK-SCHEMA | The lockfile schema version is not supported. |
| SPR-PROJECT-NOT-EXPORTED | A module imported from a dependency is not in its `exports`. |
| SPR-DEP-CYCLE | Sprig project dependencies form a cycle. |
| SPR-DEP-NOT-FOUND | A declared Sprig dependency or module cannot be found. |
| SPR-DEP-GIT | A Git dependency operation failed. Cache-lock contention waits up to five seconds, then asks the user to retry; it never steals a lock. |
| SPR-DEP-OFFLINE | A required dependency resource is missing from the cache in offline mode. |
| SPR-DEP-REGISTRY | A package registry could not be read, or does not list the requested package or version. `sprig search` shows what a registry lists. |
| SPR-DEP-CHECKSUM | Locked dependency bytes do not match SHA-256; corrupted cache is rejected. |
| SPR-DEP-MAVEN | A JVM (Maven) dependency operation failed. |

## Optional structured fields

Diagnostic JSON keeps the stable fields (`code`, `phase`, `severity`, `uri`,
`range`, `message`, `related`, `suggestedEdits`) and may add optional fields:

| Field | Meaning |
|---|---|
| `expectedType` / `actualType` | Rendered types involved in a mismatch. |
| `hint` | One short human instruction. |
| `relatedHelp` | Help topic accepted by `sprig help <topic>`. |
| `repair` | `{ "kind": ..., "machineApplicable": bool }` repair strategy. |

`suggestedEdits` is always present. It is usually empty; when the hint names
one mechanical rewrite, the list holds it as
`{ "range": {start, end}, "newText": "...", "description": "..." }` with the
same zero-based positions as `range`, a zero-length range for an insertion,
and `newText` replacing exactly that range. Applying an edit moves the program
past the diagnostic; whether it is what the author meant (adding `throws Error`
to a function, say) is still theirs to judge. Edits exist today for a missing
`@std` import, an undeclared `throws`, positional constructor arguments and
`else if`. In an editor, `sprig lsp` offers the same edits as quick fixes; see
[language server](lsp.md#quick-fixes).

`machineApplicable` is true only for a correction that is semantics-preserving
and unambiguous. When it is false, treat `repair.kind` as a strategy, not a
patch: for example a nullable value needs an explicit narrow/handle decision,
which is business logic the compiler must not invent. `sprig explain <CODE>
--json` returns the same `repair` object plus causes, safe fixes and examples.

## Syntax errors

- `SPR-SYNTAX-ERROR` reports layout problems in source-level terms, such as an
  unexpected indentation or a missing indented block after a header
  (`else:`, `if ...:`, `func ...:`). Parser recovery does not expose
  synthetic `INDENT`, `DEDENT` or end-of-file tokens as user-facing wording
  (source text quoted in a message, such as an identifier `MAX_INDENT`, is
  never rewritten), and redundant end-of-file errors after a missing block are
  suppressed.

## Declaration facade errors

- `SPR-MODULE-EXPORT`: unknown/illegal target or duplicate/colliding visible name; includes origin names.
- `SPR-MODULE-EXPORT-ORDER`: imports, exports, declarations/statements must appear in that order.
- Existing `SPR-NAME-IMPORT-CYCLE` also rejects reexport chains containing a cycle.

## Function signatures

- `SPR-SYNTAX-ERROR` reports a `throws` clause without a declared result type
  with explicit guidance (`-> Unit throws Error`), a hint to add the result type
  and the `functions` help topic.

## Expression match results

- `SPR-MATCH-RESULT`: incompatible branch result; expected/actual types and match repair guidance.
- `SPR-MATCH-INFERENCE`: no non-null type can be inferred; annotate explicitly.
- Existing `SPR-MATCH-*`, null/numeric/capture/effect diagnostics also apply.
- `SPR-SYNTAX-ERROR` rejects empty/multi-statement/declaration branches, with statement-match guidance where the parser retains branch context.
- `SPR-TYPE-UNIT` rejects side-effect-only expression matches.

## If expression results

- `SPR-SYNTAX-ERROR` reports, each once and at the place to fix: a missing
  `else` ("An if expression needs an else branch"), a branch value on the
  header's line, a branch with several lines or a statement, an `if` expression
  inside parentheses, brackets or braces, an `if` expression used as an
  operand, and Python's `a if c else b` and C's `c ? a : b`, with the `if`
  expression as the hint. `else if` keeps its mechanical rewrite to `elif`.
  An `if` guard on a match case (`case X as c if cond:`) and an `if` filter on
  a `for` loop get a hint to test the condition inside the body. A grammar
  predicate's text is never a message: a statement that goes on past its end
  reads "Expected the end of the line".
- `SPR-TYPE-CONDITION`: a condition that is not `Bool`.
- `SPR-TYPE-MISMATCH` ("Type mismatch in if branch result") and the numeric
  conversion codes: a branch incompatible with the expected type or with the
  first non-null branch.
- `SPR-TYPE-INFER`: every branch is `null` and nothing gives the type.
- `SPR-TYPE-UNIT`: a branch without a value; use an `if` statement.
