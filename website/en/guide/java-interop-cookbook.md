# Java interop cookbook

Five things come up again and again where your code meets Java: a result that may be `null`, a checked exception, a collection, a callback, and an overloaded method. Each recipe shows the program the compiler rejects, what it says, and the version that works. The docs check runs every program on this page and compares what it prints with the block under it.

If you haven't called Java from Sprig yet, start with the [Java chapter of the book](/en/tutorial/ch21-java). The rules in full are in the [JVM interop reference](/en/reference/jvm/interop).

One habit helps with all five: look the signature up instead of guessing it.

```sh
sprig api java.util.ArrayList --member sort
```

This prints each overload as Java declares it and as Sprig sees it. Add `--json` for the machine-readable form (`sprigParameterTypes`, `sprigReturnType`, `checkedExceptions`, `interopReasonCodes`). For a library outside the JDK, run it in the resolved project or pass `--classpath`.

## A result that may be null {#nullable}

`HashMap.get` returns nothing when the key is missing, so Sprig types its result `String?` and won't let you use it as a `String`:

<<< @/snippets/interop/nullable_bad.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:4:12: Cannot access 'length' on a value that may be null (receiver type String?)
  hint: Bind the receiver to a let and check it with 'if value != null:' before using 'length'.
```

Keep the result in a `let` and say what happens in both cases:

<<< @/snippets/interop/nullable.spr

```text
missing
```

- Only a `let` narrows. If the value lives in a `var`, copy it into a `let` and check the copy.
- A Java reference result is nullable unless the library annotates it as non-null. `toString()` is always a `String`.
- When the value really is optional, `or_else` from `@std/nulls.spr` gives it a fallback. Don't add a default just to make the error go away: when the data is required, a missing value is a failure you should report.

## A checked exception {#exceptions}

`URI(text)` declares `URISyntaxException`. A named function has to catch it or declare it:

<<< @/snippets/interop/exceptions_bad.spr

```text
SPR-FLOW-THROWS [FLOW] main.spr:4:12: Call may throw URISyntaxException; declare 'throws URISyntaxException' or handle it with try/catch
```

The next program does both. `parse_uri` declares the Java exception and passes it on, so a caller can still catch it by its type. `parse_for_callback` catches it and throws an `Error` with a message of its own:

<<< @/snippets/interop/exceptions.spr

```text
https://example.com
invalid URI: ::bad
```

- Translate to `Error` when the function will be passed around as a value. A function type can end with `throws Error` and nothing else, so a checked Java exception never crosses one.
- `Error(message)` keeps the message and not the Java exception behind it. If callers need the original, declare it and let it propagate.
- A caught Java exception's `message` is a `String?`, because `getMessage()` may return `null`. `problem.toString()` is always a `String`.

## A collection {#collections}

A Sprig list isn't a `java.util.List`, even when both hold strings:

<<< @/snippets/interop/collections_bad.spr

```text
SPR-JVM-MEMBER [JVM] main.spr:2:1: Java class Collections has no method 'sort' matching 1 argument(s)
  hint: Run sprig api java.util.Collections --member sort to see the signatures this call can match.
```

Copy the list into a Java one with `@std/jvm.spr`, let Java work on the copy, then take a Sprig snapshot of the result:

<<< @/snippets/interop/collections.spr

```text
[b, a]
[a, b]
3
```

- `list_copy` gives you a separate, mutable `ArrayList`. Sorting it leaves `original` as it was, which is the first line.
- `list_snapshot` gives you a read-only Sprig `List` of what the Java list holds at that moment. The `add("c")` afterwards makes `copy` three long and doesn't touch `snapshot`.
- Maps work the same way with `map_copy` and `map_snapshot`.
- A snapshot isn't a live view, and it doesn't copy the objects inside. A `null` in the Java collection makes it throw `Error`.

## A callback {#callbacks}

A Java parameter whose type is a functional interface (`Runnable`, `Comparator`, `Consumer` and so on) takes a Sprig `fn` value. It can't take one that may throw `Error`, because Java has no way to see that:

<<< @/snippets/interop/callbacks_bad.spr

```text
SPR-TYPE-CALLABLE-THROWS [TYPE] main.spr:4:21: A function value that may throw Error cannot be passed to Java parameter 1 of Thread; Java cannot see the throws clause (expected fn() -> Unit, actual fn() -> Unit throws Error)
  hint: Handle the error inside a named function and pass a lambda that calls it.
```

Decide in a named function what a failure means, and pass a lambda that calls it:

<<< @/snippets/interop/callbacks.spr

```text
[a, bb]
failed
```

- `run_callback` prints the message. Your program might log it, count it or keep it for later; what matters is that you made the decision and wrote it down.
- For a comparator, write the parameter types and return `a.compareTo(b)`. Its result is the `Int32` that `Comparator` expects.
- An interface with several abstract methods, or a callback that keeps state, is a class with `conform`. See [conformance](/en/reference/jvm/conformance).

## An overloaded method or constructor {#overloads}

`StringBuilder` has four constructors, and none of them takes a `Float`:

<<< @/snippets/interop/overloads_bad.spr

```text
SPR-JVM-MEMBER [JVM] main.spr:2:15: No constructor of StringBuilder matches 1 argument(s)
  hint: Run sprig api java.lang.StringBuilder --member '<init>' to see the signatures this call can match.
```

Run the command from the hint, pick the overload you mean, and give the argument that type:

```sh
sprig api java.lang.StringBuilder --member '<init>'
sprig api java.lang.Math --member max
```

<<< @/snippets/interop/overloads.spr

```text
rows=3
3
```

- An `Int32` value selects the `int` overload. A plain `Int` prefers an exact `long` overload when there is one; otherwise it narrows to `int`, with a range check at run time.
- Java calls take positional arguments only.
- Type arguments in brackets, as in `Collections.emptyList[String]()`, settle a generic method's type parameter. They don't choose between overloads.
- `SPR-JVM-AMBIGUOUS` means two overloads fit equally well. Look at the candidates the diagnostic lists and change an argument's type until one fits; the compiler won't pick for you.
