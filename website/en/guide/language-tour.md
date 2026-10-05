# Language quick reference

This page walks through Sprig's syntax from top to bottom. It's meant for people who already know another programming language and want to get going quickly. If Sprig is your first contact with the language, do the [tutorial](/en/tutorial) first and come back here to look things up.

Every piece of code on this page is a real file under `website/snippets/` in the repository, and the docs check compiles and runs each one. To find out whether a feature is implemented, the [feature status](/en/reference/language/feature-status) page has the final word.

## Indentation and comments

Blocks are defined by indentation, as in Python:

- Indent with spaces only; a tab is rejected with `SPR-LEX-TAB`. Keep the indentation within a block consistent; how many spaces you use is up to you.
- The first line of code in a file starts in column 1.
- Inside parentheses, brackets and braces you can break lines freely, so long calls and literals can span several lines.
- `#` starts a comment.

## Variables: let and var

<<< @/snippets/variables.spr

- A `let` binding can't be changed after it's set; a `var` can.
- Local variables can leave out the type, and Sprig infers it from the right-hand side. Class fields always need a type.
- Conditions must be `Bool`. Neither `0` nor an empty string counts as false, and `if count:` is rejected with `SPR-TYPE-CONDITION`.

## Functions

<<< @/snippets/functions.spr

- Parameters and return values always have types. A function that returns nothing says `-> Unit`.
- You call functions with positional arguments. Creating an object is different: you name the fields (see Classes below).
- A function that can fail says `throws` in its signature (see Errors).

## Classes

<<< @/snippets/classes.spr

- Fields are declared with `let` (fixed once the object exists) or `var` (can change), optionally with a default.
- You create objects with field names: `Hero(name="Ada", health=80)`. Missing, misspelled or repeated fields are compile errors.
- Inside a method, a field's bare name refers to the current object's field; there's no prefix.
- Parameters and local variables can't have the same name as a field.

## enum, variant and match

<<< @/snippets/variants.spr

- An `enum` value carries no data, for example `Mode.Fast`.
- Each case of a `variant` can carry its own fields, which are immutable. `Expr.Add`, for example, carries `left` and `right`.
- `case Expr.Add as node:` binds the matched value to `node` so you can read its fields.
- A `match` must handle every case. There's no `default`, no wildcard branch and no fallthrough. Missing, repeated and impossible branches are all compile errors.
- `match` works as a statement, where a branch can hold several lines, and as an expression (as in `return match ...`), where each branch is a single expression.

Exhaustive matching pays off when code changes. Add a case to a variant, and every `match` that doesn't handle it fails to compile, so none slip through. `tests/visitor/ast_visitor.spr` in the repository, a small AST interpreter written in Sprig and run by the test suite, relies on exactly that.

## Collections

<<< @/snippets/collections.spr

- `List[T]` and `Map[K, V]` are read-only; to change a collection, use `MutableList[T]` or `MutableMap[K, V]`. Modifying a read-only collection is rejected with `SPR-COLLECTION-IMMUTABLE`.
- `toMutableList()`, `toList()`, `toMutableMap()` and `toMap()` copy the collection (the outer layer only), so the original stays as it was.
- A list declared with `let` and no type is a `MutableList`, which you can't pass where a `List` is expected. For a read-only list, write `let xs: List[Int] = [1, 2]`. A literal written directly as an argument is fine.
- Looking up a key in a `Map` gives you a nullable value: `null` when the key isn't there.
- Indexing, `in`, `get`, `set`, `append`, `sort` and the lambda-taking methods `map`, `filter` and `forEach` all work.
- Floating-point numbers can't be `Map` keys, because `NaN` and signed zero don't behave consistently under equality and hashing.

To sort by a field, group or total things up, use `@std/lists` from the standard library:

<<< @/snippets/guide/lists_group.spr

```text
Coffee
Lunch
Taxi
food: 3050
transport: 3600
```

- `sort_by` is a stable sort, and its key has to be orderable (see `Comparable` in [generics](/en/guide/generics)).
- `group_by` forms groups in the order their keys first appear and keeps the original order inside each group; keys are compared with `==`.
- `fold` combines the elements from left to right into one result, which covers sums and counts.

## Nullable values

<<< @/snippets/nullable.spr

- A type that might have no value is written `T?`. Only `T?` accepts `null`.
- Check before you use it. Inside `if x != null:`, `x` has a value. In versions newer than v0.5.0-beta.1, `if x != null and x.length() > 3:` works too.
- Returning early works as well: after `if x == null: return ...`, the rest of the code treats `x` as present.
- Using a possibly-null value where a value is required is rejected with `SPR-TYPE-NULLABLE`.
- A check on a `var` field stops counting once a function is called in between, because the call might have changed the field.
- Objects returned by Java methods are always treated as possibly `null`; see [JVM interop](/en/guide/jvm-interop).

## Errors

<<< @/snippets/errors.spr

- A function lists the errors it can throw with `throws` in its signature.
- The caller has two options: handle the error with `try` / `catch` (optionally with `finally`), or add `throws` to its own signature. Doing neither is rejected with `SPR-FLOW-THROWS`.
- An `Error` has a `message` field.
- Java checked exceptions are caught the same way: name the imported Java exception class after `catch`.

## Generics

<<< @/snippets/generics.spr

Your own classes, variants and functions can go inside a `generic T:` (or `generic K, V:`) block. Every use spells out the type arguments, as in `Box[Int](value=42)`. Sprig doesn't infer type arguments, and generics have no variance. To compare values of a type parameter with `==`, start the function with `requires T: Equatable`; to order them with `<`, use `requires T: Comparable`. The [generics guide](/en/guide/generics) has the details.

## Lambdas

<<< @/snippets/lambdas.spr

- A lambda is an expression: `fn(x: Int) => x * 2`.
- It takes 0 to 3 parameters, its body is a single expression, and it can't declare `throws`.
- A lambda can't capture a `var` local; that's rejected with `SPR-TYPE-CAPTURE`. Copy the value into a `let` first.

## Function types

<<< @/snippets/function_types.spr

- `fn(Int) -> Int` is a **type**; `fn(x: Int) => x + 1` is a **value**.
- Function types take 0 to 3 parameters. Parameter and result types must match exactly: there's no function subtyping and no automatic conversion.
- To make the whole function nullable, add parentheses: `(fn(Int) -> Int)?`. `fn(Int) -> Int?` means the result is nullable. Check a nullable function value before calling it, like any nullable value.
- Function types can be used for variables, fields, parameters and results, and as explicit generic arguments.
- A function type can't declare `throws`, so checked errors have to be handled inside the function.

When you pass a function value to Java, the Java parameter has to be one of Sprig's own `sprig.runtime.Fn0` to `Fn3` types. Sprig doesn't convert functions to Java's `Function`, `Consumer`, `Runnable` or any other interface. If you're not sure, `sprig api <Class> --json` shows the actual signature.

## Looking up JSON fields

<<< @/snippets/json_lookup.spr

`json.find_member` has three outcomes: `Missing` (no such key), `Found` (the value is in `value`) and `NotObject` (you didn't look inside an object). A key whose value is `null`, `false`, `0` or an empty string is still `Found`, so it never gets confused with a missing key. Duplicate keys in an object throw an `Error`, and members keep their order. The [standard library notes](https://github.com/ColinHouse/Sprig/blob/main/docs/projects/standard-library.md) cover parsing, lookup and serialization in full.

## Modules

<<< @/snippets/modules/main.spr

<<< @/snippets/modules/math_module.spr

- `import "./file.spr" as alias` imports another Sprig file.
- Imports go at the top of the file, before any declaration or statement.
- Each module is initialized once. Two modules importing each other (an import cycle) is rejected with `SPR-NAME-IMPORT-CYCLE`.
- Java classes are imported the same way, with the full class name: `import java.time.LocalDate as LocalDate`.
- The standard library starts with `@std`, as in `import "@std/json.spr" as json`.

## A few smaller features

- `sprig fmt` formats code in one standard style. It keeps your comments and has no options. See [formatter](/en/reference/tooling/formatter).
- A module can re-export an imported declaration with `export alias.Symbol`, but that can't be used to get around a dependency's exports. There's no `export *`. See [re-exports](/en/reference/language/module-reexports).
- An expression `match` allows one expression per branch; for several lines, use a statement `match`. See [match expressions](/en/reference/language/match-expressions).

## Not in the language yet

None of these exist yet:

- generic inference and variance
- inheritance and interfaces
- `%=`
- tuples and destructuring
- string interpolation: `"${name}"` is just text, and you join strings with `+`

When working with Java, Sprig has no array syntax and doesn't support varargs or wildcard types. Java arrays themselves can still be received and passed along as they are; see [JVM interop](/en/guide/jvm-interop).

The full list is in [known limitations](/en/reference/language/known-limitations), and what comes next is in the [roadmap](/en/reference/language/stage1-roadmap).
