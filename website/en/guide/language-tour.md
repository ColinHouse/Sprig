# Language quick reference

This page walks through Sprig's syntax from top to bottom. It's meant for people who already know another programming language and want to get going quickly. If Sprig is your first contact with the language, do the [tutorial](/en/tutorial) first and come back here to look things up.

Every piece of code on this page is a real file under `website/snippets/` in the repository, and the docs check compiles and runs each one. To find out whether a feature is implemented, the [feature status](/en/reference/language/feature-status) page has the final word.

## Indentation and comments

Blocks are defined by indentation, as in Python:

- Indent with spaces only; a tab is rejected with `SPR-LEX-TAB`. Keep the indentation within a block consistent; how many spaces you use is up to you.
- The first line of code in a file starts in column 1.
- If a line is unexpectedly indented or an `else:` has no indented body, `SPR-SYNTAX-ERROR` describes the indentation problem without exposing internal `INDENT` or `DEDENT` markers.
- Inside parentheses, brackets and braces you can break lines freely, so long calls and literals can span several lines.
- `#` starts a comment.

## Variables: let and var

<<< @/snippets/variables.spr

- A `let` binding can't be changed after it's set; a `var` can.
- Local variables can leave out the type, and Sprig infers it from the right-hand side. Class fields always need a type.
- To put a number or any other value into text, use `+`: `"visits " + visits`. There is nothing to convert first. A value that may be `null` has to be checked first (see [Nullable values](#nullable-values)).
- Conditions must be `Bool`. Neither `0` nor an empty string counts as false, and `if count:` is rejected with `SPR-TYPE-CONDITION`.

## Functions

<<< @/snippets/functions.spr

- Parameters and return values always have types. A function that returns nothing says `-> Unit`.
- You call functions with positional arguments. Creating an object is different: you name the fields (see Classes below).
- A function that can fail says `throws` in its signature (see Errors).

## Classes

<<< @/snippets/classes.spr

- Fields are declared with `let` (fixed once the object exists) or `var` (can change), optionally with a default.
- A small class with only `let` fields and no methods fits on one line: `class Position(x: Int, y: Int)`. It is the same kind of class as the block form; `var` fields, defaults and methods need the block.
- A class whose methods have **no body** is a contract. Another class declares `conform Console to Sink` and must have every method of the contract with the same types; a `Console` then goes wherever a `Sink` is expected and is used through the contract's methods. A contract has no fields, no default bodies, cannot be constructed, and there is no conversion back. Example:

<<< @/snippets/contracts.spr
- You create objects with field names: `Hero(name="Ada", health=80)`. Missing, misspelled or repeated fields are compile errors.
- Inside a method, a field's bare name refers to the current object's field; there's no prefix.
- Parameters and local variables can't have the same name as a field.
- `==` on two objects asks whether they're the same object. Two `Point(x=1, y=2)` objects are not `==`, even though every field matches: change one and the other stays as it was. To compare contents, compare the fields you mean (`a.id == b.id`), or use a variant.

## enum, variant and match

<<< @/snippets/variants.spr

- An `enum` value carries no data, for example `Mode.Fast`.
- Each case of a `variant` can carry its own fields, which are immutable. `Expr.Add`, for example, carries `left` and `right`.
- `case Expr.Add as node:` binds the matched value to `node` so you can read its fields.
- Variant and enum values compare by value: `Shape.Circle(radius=1.0) == Shape.Circle(radius=1.0)` is `true`. Strings, numbers, lists and maps do too.
- A `match` must handle every case. There's no `default`, no wildcard branch and no fallthrough. Missing, repeated and impossible branches are all compile errors.
- `match` works as a statement, where a branch can hold several lines, and as an expression (as in `return match ...`), where each branch is a single expression.

Exhaustive matching pays off when code changes. Add a case to a variant, and every `match` that doesn't handle it fails to compile, so none slip through. `tests/visitor/ast_visitor.spr` in the repository, a small AST interpreter written in Sprig and run by the test suite, relies on exactly that.

## Choosing a value with if

<<< @/snippets/if_expressions.spr

- `if` produces a value wherever an expression `match` can: after `=`, `return` or `throw`, and as a lambda's body. Each branch is one expression on its own indented line, and `elif` and `else` line up with the line the `if` starts on.
- The `else` branch is required; leave it out and you get "An if expression needs an else branch", while the rest of the file is still checked and the editor keeps working until you add it. When there's no value to produce, write an ordinary `if` statement: an `if` at the start of a statement is always the `if` statement.
- All branches share one type: the type the position expects, as in `let ratio: Float = if ...`, or else the type of the first branch that isn't `null`. A `null` branch makes the result nullable.
- There's no `a if c else b` as in Python and no `c ? a : b` as in C; the compiler points you to the `if` expression instead.
- Line breaks are ignored inside parentheses, so an `if` expression can't go straight into a call. Bind it to a `let` first and pass the name. The details are in [if expressions](/en/reference/language/if-expressions).

## Collections

<<< @/snippets/collections.spr

- `List[T]` and `Map[K, V]` are read-only; to change a collection, use `MutableList[T]` or `MutableMap[K, V]`. Modifying a read-only collection is rejected with `SPR-COLLECTION-IMMUTABLE`.
- `toMutableList()`, `toList()`, `toMutableMap()` and `toMap()` copy the collection (the outer layer only), so the original stays as it was.
- A list declared with `let` and no type is a `MutableList`. It can be passed where a `List` is expected: the function sees the same list read-only, so a later `append` through the mutable name is visible to it, and `toList()` takes a snapshot when you need one. A `List` never becomes a `MutableList` without `toMutableList()`.
- Looking up a key in a `Map` gives you a nullable value: `null` when the key isn't there.
- Indexing, `in`, `get`, `set`, `append`, `sort` and the lambda-taking methods `map`, `filter` and `forEach` all work.
- Floating-point numbers can't be `Map` keys, because `NaN` and signed zero don't behave consistently under equality and hashing.

To sort by a field, group, total things up or search, use `@std/lists` from the standard library (new in v0.6.0-beta.1):

<<< @/snippets/guide/lists_group.spr

```text
Coffee
Lunch
Taxi
food: 3050
transport: 3600
first ride: Taxi
true
2
```

- `sort_by` is a stable sort, and its key has to be orderable (see `Comparable` in [generics](/en/guide/generics)). `sorted` sorts a list of orderable values such as `List[String]`.
- `group_by` forms groups in the order their keys first appear and keeps the original order inside each group; keys are compared with `==`.
- `sum_by` adds up one `Int` per element, and `sum` adds up a `List[Int]`.
- `find` gives you the first element that passes the test, or `null` when none does. `any`, `all` and `count` take the same kind of test.
- `fold` combines the elements from left to right into one result, for anything the functions above don't cover.
- `first`, `last`, `take`, `drop`, `reversed`, `distinct`, `index_of`, `enumerate` and `zip` cover the small list jobs; `@std/sets` adds `Set[T]` (`sets.of[String]([...])`, `has`, `add`, `union`); `@std/random`, `@std/regex` and `@std/dates` wrap one JDK facility each. The [standard library notes](https://github.com/ColinHouse/Sprig/blob/main/docs/projects/standard-library.md) list every function.

## Nullable values

<<< @/snippets/nullable.spr

- A type that might have no value is written `T?`. Only `T?` accepts `null`.
- Check before you use it. Inside `if x != null:`, `x` has a value. Since v0.6.0-beta.1, `if x != null and x.length() > 3:` works too.
- `elif` and `else` know the earlier conditions were false: after `if x == null:`, an `elif flag:` branch and the `else` both treat `x` as present, with no second check. Writing `x != null` again there is accepted; it is just redundant.
- Returning early works as well: after `if x == null: return ...`, the rest of the code treats `x` as present. An `if/elif` chain whose earlier branches all return works the same way.
- Using a possibly-null value where a value is required is rejected with `SPR-TYPE-NULLABLE`.
- A check on a `var` field stops counting once a function is called in between, because the call might have changed the field.
- Objects returned by Java methods are always treated as possibly `null`, except a `toString()` result; see [JVM interop](/en/guide/jvm-interop).

When all you want is a fallback value or an error, `@std/nulls` (new in v0.6.0-beta.1) saves the `if`:

<<< @/snippets/guide/nulls_fallback.spr

```text
12
0
{tea: 2, rice: 1}
8080
port must be a number: eighty
```

- `or_else` returns the value, or the fallback you give it when the value is `null`.
- `require` returns the value, or throws an `Error` with your message when the value is `null`.
- Neither needs a type in brackets: like any generic call, it takes the type from the value you pass. (That's newer than v0.7.1-beta.1, where you write `nulls.or_else[Int](...)`.)

## Errors

<<< @/snippets/errors.spr

- A function lists the errors it can throw with `throws` in its signature.
- The caller has two options: handle the error with `try` / `catch` (optionally with `finally`), or add `throws` to its own signature. Doing neither is rejected with `SPR-FLOW-THROWS`.
- An `Error` has a `message` field. `print(problem)`, `"failed: " + problem` and `problem.toString()` show that same message.
- Java checked exceptions are caught the same way: name the imported Java exception class after `catch`. Such an exception shows Java's text, class name first, and its `message` is a `String?`, because Java's `getMessage()` may return `null`.
- Your own error types are error classes: a class with a `message: String` field plus `conform NotFound to Error(message)`. Throw it, declare it with `throws NotFound`, and catch it by name to read its fields, or as `Error` to catch every kind at once. A catch of the class after a catch of `Error` is unreachable and reported.

<<< @/snippets/error_classes.spr

## Generics

<<< @/snippets/generics.spr

Your own classes, variants and functions can go inside a `generic T:` (or `generic K, V:`) block. A call works out the type arguments from its arguments, so `Box(value=42)` is a `Box[Int]` (newer than v0.7.1-beta.1, which needs `Box[Int](value=42)`). When the arguments can't say, as with an empty list, you write them yourself: `lists.first[String]([])`. Generics have no variance. To compare values of a type parameter with `==`, start the function with `requires T: Equatable`; to order them with `<`, use `requires T: Comparable`. The [generics guide](/en/guide/generics) has the details.

## Lambdas

<<< @/snippets/lambdas.spr

- A lambda is an expression: `fn(x: Int) => x * 2`.
- It takes 0 to 3 parameters and its body is a single expression. It may call a function that throws `Error`; its type then says so (see Function types below).
- A lambda can't capture a `var` local; that's rejected with `SPR-TYPE-CAPTURE`. Copy the value into a `let` first.
- A named function, module function or method without the call parentheses is a function value: `items.map(shout)`, `lists.sum`, `counter.bump`. Its type is the forwarding lambda's, throws clause included. A method reference evaluates its receiver once, when the value is created. A generic function writes its type arguments: `identity[Int]`. `print` is a value only where a `fn(T) -> Unit` is expected, such as `items.forEach(print)`. Java and built-in methods still take a lambda. Function values are not compared with `==`.

<<< @/snippets/function_references.spr

## Function types

<<< @/snippets/function_types.spr

- `fn(Int) -> Int` is a **type**; `fn(x: Int) => x + 1` is a **value**.
- Function types take 0 to 3 parameters. Parameter and result types must match exactly: there's no function subtyping and no automatic conversion.
- To make the whole function nullable, add parentheses: `(fn(Int) -> Int)?`. `fn(Int) -> Int?` means the result is nullable. Check a nullable function value before calling it, like any nullable value.
- Function types can be used for variables, fields, parameters and results, and as explicit generic arguments.
- A function type may end with `throws Error`, and nothing else: `fn(String) -> Int throws Error`. A lambda that calls a function throwing `Error` has that type, and calling such a value needs `throws Error` or `try`/`catch`, like any throwing call. A value without the clause is accepted where the clause is expected, never the other way round (`SPR-TYPE-CALLABLE-THROWS`). A checked Java exception never crosses a function value; handle it inside a named function.
- A function that only passes its callable's errors on is declared `rethrows` instead of `throws`: `func twice(step: fn(Int) -> Int throws Error, value: Int) -> Int rethrows:`. A call to it throws exactly what the lambda you pass throws, so a lambda that cannot fail makes an ordinary call. The `@std/lists` helpers work this way.

<<< @/snippets/callable_throws.spr

```text
[1, 2]
caught: not a number: x
18
21
caught: not a number: three
```

A function value can go where Java expects a functional interface with up to three parameters, such as `Comparator`, `Consumer` or `Runnable`, and where it expects one of Sprig's own `sprig.runtime.Fn0` to `Fn3` types; see [JVM interop](/en/guide/jvm-interop). If you're not sure, `sprig api <Class> --json` shows the actual signature.

## Looking up JSON fields

<<< @/snippets/json_lookup.spr

`json.find_member` has three outcomes: `Missing` (no such key), `Found` (the value is in `value`) and `NotObject` (you didn't look inside an object). A key whose value is `null`, `false`, `0` or an empty string is still `Found`, so it never gets confused with a missing key. Duplicate keys in an object throw an `Error`, and members keep their order. The [standard library notes](https://github.com/ColinHouse/Sprig/blob/main/docs/projects/standard-library.md) cover parsing, lookup and serialization in full.

To read whole records, `@std/json_codec` (new in v0.6.0-beta.1) takes a field by name and type, so you don't write a `match` for every field:

<<< @/snippets/json_fields.spr

```text
Read the tour {"id":1,"title":"Read the tour","done":true}
$[1].id: expected integer, found string
```

- `required_int`, `required_string` and `required_bool` return the field, or throw an `Error` when it is missing, `null` or another kind. Nothing is converted: the string `"2"` is not an integer.
- Every message starts with where the problem is, here element 1 of the list, field `id`.
- The `optional_` versions return `null` for a missing or `null` field.
- `root` reads a document that is an object, and `root_array` one that is a list.
- `object`, `member`, `int`, `text` and `bool` build the values to write back.

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

- inferring type arguments from the expected type, and variance
- inheritance (open polymorphism is a contract class, see [Classes](#classes))
- `%=`
- tuples and destructuring
- string interpolation: `"${name}"` is just text. Join with `+` instead: it accepts any value on either side, so `"count " + count` works without `toString()`, and the value appears as `print` would show it. `null`, a value that may be `null` (such as an `Int?`) and a `Unit` result are rejected. A chain evaluates from the left, so `1 + 2 + " items"` is `3 items`.

When working with Java, Sprig has no array syntax and no wildcard syntax of its own. Java arrays can still be received and passed along as they are, varargs methods take their trailing arguments, and Java wildcards keep their bounds; see [JVM interop](/en/guide/jvm-interop).

The full list is in [known limitations](/en/reference/language/known-limitations), and what comes next is in the [roadmap](/en/reference/language/stage1-roadmap).
