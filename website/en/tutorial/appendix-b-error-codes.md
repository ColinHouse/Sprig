# Appendix B. Error codes and where to read about them

Every diagnostic from the compiler carries a fixed error code that starts with `SPR-`. A code's meaning never changes: new behavior gets a new code. This appendix lists the compiler's current 102 codes by category, one line each; the codes and the count change as the compiler does, so `sprig codes` always has the latest list.

## B.1 How to read an error code

The first line of an error has four parts:

```text
SPR-NUM-DIVISION [TYPE] main.spr:2:7: Integer / would truncate; use a.divTrunc(b), or convert both operands explicitly (expected explicit division, actual Int / Int)
```

- `SPR-NUM-DIVISION`: the error code, stable;
- `[TYPE]`: the phase that failed (lexing `LEX`, syntax `SYNTAX`, names `NAME`, types `TYPE`, flow `FLOW`, runtime `RUNTIME`, ...);
- `main.spr:2:7`: file, line and column (counting from 1);
- then the message; a `hint:` line, when there is one, is the suggestion editors use for quick fixes too.

For the full explanation, give the code to `sprig explain`:

```text
$ sprig explain SPR-NUM-DIVISION
SPR-NUM-DIVISION: Integer / is rejected; use divTrunc for deliberate truncation or explicit Float/Decimal arithmetic.
Why it matters: Integer / silently truncates in many languages; Sprig requires the intent to be explicit.
Common causes:
  - Integer division was written with / instead of divTrunc.
Safe fixes:
  - Use value.divTrunc(divisor) only when truncation is intended.
  - Convert to Float or Decimal when fractional arithmetic is intended.
Good:
  let q = (7).divTrunc(2)
Bad:
  let q = 7 / 2
```

`sprig explain <CODE> --json` gives the same information, plus the related help topic (`relatedHelp`, which you can pass straight to `sprig help`) and the `repair` suggestion; that form is meant for editors and AI assistants. `sprig check file.spr --json` prints every diagnostic from one check as structured JSON.

The tables below group the codes by prefix. Each line says only when the code appears; use `sprig explain` for how to fix it and why it is designed that way.

### B.2 Tooling and the command line

| Code | When it appears |
|---|---|
| `SPR-API-MEMBER` | The requested `--member` does not exist on the inspected Sprig declaration. |
| `SPR-API-TARGET` | `sprig api` cannot resolve the Sprig module or project to inspect. |
| `SPR-BUNDLE-JDEPS` | jdeps cannot analyze the bundled JARs to find the Java modules the program needs. |
| `SPR-BUNDLE-LAYOUT` | The Java installation or the classpath has a layout the bundle cannot use: no linkable runtime, a jlink failure, or an old bundle that cannot be removed. |
| `SPR-BUNDLE-TOOLS` | `build --bundle` needs jdeps and jlink from a full JDK, but the Java installation in use is a JRE. |
| `SPR-CLI-OPTION` | A command line is missing a required argument, or has an unknown option. |
| `SPR-WRAP-CHECK` | The generated Java wrapper source from `sprig wrap` failed Sprig checking or formatting. |

### B.3 Lexing and syntax

| Code | When it appears |
|---|---|
| `SPR-LEX-CHAR` | The input contains a character the Sprig lexer does not know. |
| `SPR-LEX-INDENT-FIRST` | The first code line of a file must start at column 1. |
| `SPR-LEX-INDENT-INCONSISTENT` | A dedent must return to a previous indentation level. |
| `SPR-LEX-STRING` | A string literal is unterminated or contains an invalid escape. |
| `SPR-LEX-TAB` | Tabs are not allowed; indentation uses spaces only. |
| `SPR-LEX-UNCLOSED` | A `(`, `[` or `{` was opened and never closed. |
| `SPR-LEX-UNMATCHED` | A closing bracket has no matching opener. |
| `SPR-SYNTAX-ERROR` | The token sequence does not match the Sprig grammar. |

### B.4 Names, modules and exports

| Code | When it appears |
|---|---|
| `SPR-MODULE-EXPORT` | The target of an `export` is unknown, not public, or collides with a visible name. |
| `SPR-MODULE-EXPORT-ORDER` | The order must be imports, exports, then ordinary declarations and statements. |
| `SPR-NAME-DUPLICATE` | Two declarations share one name in one namespace. |
| `SPR-NAME-DUPLICATE-MEMBER` | A class or variant declares the same member twice. |
| `SPR-NAME-FIELD-SHADOW` | A parameter or local cannot share the name of a field of the current class. |
| `SPR-NAME-FORWARD-REFERENCE` | Top-level code uses a top-level binding that only runs below it. |
| `SPR-NAME-IMPORT` | An imported file or class cannot be resolved. |
| `SPR-NAME-IMPORT-CYCLE` | Sprig modules import each other in a cycle. |
| `SPR-NAME-LET-ASSIGN` | A `let` binding or `let` field cannot be reassigned. |
| `SPR-NAME-MODULE` | A module import or alias is wrong. |
| `SPR-NAME-NOT-A-TYPE` | A value was used where a type is required. |
| `SPR-NAME-NOT-A-VALUE` | A type or module name was used as a value. |
| `SPR-NAME-UNRESOLVED` | A name has no declaration in the current scope chain. |

### B.5 Calls and construction

| Code | When it appears |
|---|---|
| `SPR-CALL-ARITY` | The number of arguments is wrong. |
| `SPR-CALL-DUPLICATE-FIELD` | The same named field was given twice. |
| `SPR-CALL-MISSING-FIELD` | A required field was not given. |
| `SPR-CALL-NAMED-REQUIRED` | Sprig class and variant constructors require named arguments. |
| `SPR-CALL-POSITIONAL-REQUIRED` | Ordinary functions and JVM calls require positional arguments. |
| `SPR-CALL-UNKNOWN-FIELD` | A named argument matches no field. |

### B.6 Types and generics

| Code | When it appears |
|---|---|
| `SPR-GENERIC-CONSTRAINT` | A `requires` clause names an unknown capability, a contract or class instead of a capability, is in the wrong place, or a type argument is not `Comparable` where the callee requires it. |
| `SPR-TYPE-ASSIGN` | The assigned value does not match the target type. |
| `SPR-TYPE-CALLABLE-THROWS` | A function value's `throws` clause does not fit where it is used: only `fn(...) -> R throws Error` exists, and it is not accepted as a type without `throws`. |
| `SPR-TYPE-CAPTURE` | A lambda captures a `var` local; copy it into a `let` first. |
| `SPR-TYPE-CONDITION` | A condition must be `Bool`; Sprig has no truthiness. |
| `SPR-TYPE-FUNCTION-ARITY` | Function types and lambdas support zero to three explicitly typed parameters. |
| `SPR-TYPE-GENERIC-ARGS-REQUIRED` | A generic call or constructor needs written `[Type]` arguments: the arguments do not say what a type parameter is, or two of them disagree. |
| `SPR-TYPE-GENERIC-ARITY` | A generic declaration was used with the wrong number of type arguments; supply every parameter in declaration order. |
| `SPR-TYPE-GENERIC-NULLABLE` | This type parameter is used with `?` in the declaration, so its argument must be non-nullable. |
| `SPR-TYPE-INFER` | The type cannot be inferred without an annotation. |
| `SPR-TYPE-MISMATCH` | The expected and actual types are not compatible. |
| `SPR-TYPE-NOT-CALLABLE` | The callee is not callable, or a method name was used as a value. |
| `SPR-TYPE-NULL` | `null` is only assignable to an explicit nullable type, `T?`. |
| `SPR-TYPE-NULLABLE` | A value that may be `null` was used where a non-null value is required; check for `null` first. |
| `SPR-TYPE-OPERAND` | The operator or method is not defined for this operand type. |
| `SPR-TYPE-RETURN` | The returned value does not match the declared result type. |
| `SPR-TYPE-UNIT` | `Unit` is only valid as a function or method result; it cannot be a field, parameter, collection element or ordinary value. |

### B.7 Control flow and runtime

| Code | When it appears |
|---|---|
| `SPR-FLOW-BREAK` | `break` is only valid inside a loop. |
| `SPR-FLOW-CATCH-NEVER-THROWN` | A `catch` names a checked Java exception that nothing in the `try` block can throw. |
| `SPR-FLOW-CONTINUE` | `continue` is only valid inside a loop. |
| `SPR-FLOW-MISSING-RETURN` | A non-`Unit` function must return on every path. |
| `SPR-FLOW-RETHROWS` | `rethrows` needs a parameter whose function type declares `throws Error`, and the function may throw nothing of its own. |
| `SPR-FLOW-THROWS` | A recoverable error must be declared with `throws` or caught on the spot. |
| `SPR-FLOW-THROWS-UNUSED` | A function declares a checked Java exception that its body can never throw. |
| `SPR-FLOW-UNREACHABLE` | A statement follows a statement that always exits. |
| `SPR-PROGRAM-EXIT` | The Sprig program exited with a non-zero status. |
| `SPR-RUNTIME-ERROR` | An uncaught Sprig `Error` value at runtime; the message is the content of the `Error`. |
| `SPR-RUNTIME-EXCEPTION` | An uncaught JVM exception at runtime, wrapped with a Sprig source position; `--stacktrace` shows the full JVM stack. |

### B.8 Numbers

| Code | When it appears |
|---|---|
| `SPR-NUM-CONVERSION` | An implicit numeric conversion could lose precision or range; use an explicit method. |
| `SPR-NUM-DIVISION` | Integer `/` is rejected; for deliberate truncation use `divTrunc`, and for `Decimal` use `divide`. |
| `SPR-NUM-MIXED` | A binary operation mixes integer and floating-point values, or binary floating-point and decimal. |
| `SPR-NUM-RANGE` | A numeric literal is outside the target range, or underflows to zero. |

### B.9 Classes, contracts and collections

| Code | When it appears |
|---|---|
| `SPR-CLASS-ABSTRACT` | A contract class (a class whose methods have no bodies) declares fields, mixes methods with and without bodies, is generic, or is constructed. |
| `SPR-COLLECTION-IMMUTABLE` | The `List`/`Map` is read-only; convert with `toMutableList()`/`toMutableMap()` first. |
| `SPR-CONFORM-EFFECTS` | A witness method declares checked exceptions the Java interface method does not allow. |
| `SPR-CONFORM-MEMBER` | A class method does not exactly match the Java method it witnesses or overrides, or a required abstract method is missing. |
| `SPR-CONFORM-OVERLOAD` | The Java interface requires overloaded abstract methods, which Sprig classes cannot represent. |
| `SPR-CONFORM-PARENT` | A parent view (`as NAME`) only calls inherited methods; it is not a value, has no fields, and cannot call abstract methods. |
| `SPR-CONFORM-SOURCE` | The left side of `conform` must be a non-generic Sprig class with method bodies declared in this module; there is no retroactive conformance, and a contract cannot conform. |
| `SPR-CONFORM-TARGET` | The target of `conform` must be an imported public, non-generic, non-sealed Java interface; with parentheses it is a public, non-final, non-generic Java class (the built-in `Error` is one of these). |

### B.10 Enums, variants and match

| Code | When it appears |
|---|---|
| `SPR-MATCH-DUPLICATE` | A `match` branch repeats a case. |
| `SPR-MATCH-ENUM-BINDER` | A payloadless enum case cannot bind `as name`. |
| `SPR-MATCH-INFERENCE` | An expression `match` cannot settle on a non-null result type without an explicit annotation. |
| `SPR-MATCH-NONEXHAUSTIVE` | Every enum/variant case needs a branch; there is no default. |
| `SPR-MATCH-RESULT` | An expression `match` branch's result type disagrees with the expected type (or the first non-null branch). |
| `SPR-MATCH-SCRUTINEE` | `match` only matches a non-nullable enum or variant value. |
| `SPR-MATCH-UNKNOWN-CASE` | The case name does not exist on the matched type. |
| `SPR-MATCH-WRONG-TYPE` | A branch belongs to a different enum/variant. |

### B.11 Java interop

| Code | When it appears |
|---|---|
| `SPR-JVM-AMBIGUOUS` | The Java overload is ambiguous for these argument types. |
| `SPR-JVM-CLASS` | The imported Java class cannot be loaded, or it lives in the unnamed package. |
| `SPR-JVM-CLASSPATH` | A `--classpath` entry is missing, empty, or not a JAR or directory. |
| `SPR-JVM-COMPILE` | The generated Java source did not compile; this may be a compiler bug. |
| `SPR-JVM-INTERNAL` | Internal compiler or tooling failure. |
| `SPR-JVM-MEMBER` | No Java method, constructor or field matches this call. |

### B.12 Projects and dependencies

| Code | When it appears |
|---|---|
| `SPR-DEP-CHECKSUM` | A locked dependency's bytes differ from its recorded SHA-256; do not use the corrupted cache entry. |
| `SPR-DEP-CYCLE` | Sprig project dependencies form a cycle. |
| `SPR-DEP-GIT` | A Git dependency operation failed: missing git, remote, ref or revision. |
| `SPR-DEP-MAVEN` | A JVM (Maven) dependency operation failed. |
| `SPR-DEP-NOT-FOUND` | A declared Sprig dependency or module cannot be found. |
| `SPR-DEP-OFFLINE` | A dependency resource that offline mode needs is not in the cache. |
| `SPR-DEP-REGISTRY` | A package registry cannot be read, or does not list the requested package or version. |
| `SPR-PROJECT-ENTRY` | The project entry point does not exist, or the name given to `--bin` is unknown. |
| `SPR-PROJECT-LOCK-MISSING` | The project has no `sprig.lock`; run `sprig resolve` first. |
| `SPR-PROJECT-LOCK-SCHEMA` | The lockfile schema version is not supported by this compiler. |
| `SPR-PROJECT-LOCK-STALE` | The compiler identity, `sprig.toml`, or a dependency manifest differs from the lock; run `resolve` again. |
| `SPR-PROJECT-MANIFEST` | `sprig.toml` is missing, malformed, or lacks a required field. |
| `SPR-PROJECT-NOT-EXPORTED` | A module imported from a dependency is not exported by that dependency. |
| `SPR-PROJECT-UNSUPPORTED` | The project or a dependency needs project features or a language version this compiler does not support. |

## Related chapters

- [Chapter 2: Your first program, and reading errors](/en/tutorial/ch02-first-program): the four parts of an error and `sprig explain`.
- [Chapter 7: Functions](/en/tutorial/ch07-functions): returns and calls behind the `SPR-FLOW-*` codes.
- [Chapter 12: Enums, variants and match](/en/tutorial/ch12-enums-variants): `SPR-MATCH-*`.
- [Chapter 13: Values that may be missing](/en/tutorial/ch13-nullable): `SPR-TYPE-NULL`, `SPR-TYPE-NULLABLE`.
- [Chapter 14: Handling errors](/en/tutorial/ch14-errors): `SPR-FLOW-THROWS`, `SPR-RUNTIME-ERROR`.
- [Chapter 18: Modules, projects and dependencies](/en/tutorial/ch18-modules-projects): `SPR-MODULE-*`, `SPR-PROJECT-*`, `SPR-DEP-*`.
- [Chapter 21: Calling Java](/en/tutorial/ch21-java): `SPR-JVM-*`.
