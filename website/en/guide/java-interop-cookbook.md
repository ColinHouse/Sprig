# Java interop cookbook

These are small boundary repairs for code that already calls Java. Start with
[the beginner Java chapter](/en/tutorial/ch21-java) for imports and basic calls;
use the [complete interop contract](/en/reference/jvm/interop) for the rules.
All programs below are compiled and either run against their output oracle or
checked against their expected diagnostic by the documentation gate.

First inspect the actual classpath: `sprig api java.util.ArrayList --member sort --json`.
For third-party classes, use the same resolved project or `--classpath` as your
program. Read `sprigParameterTypes`, `sprigReturnType`, `checkedExceptions`,
`interopReasonCodes` and the overload candidates; do not guess a Java signature.

## Nullable results {#nullable}

A `HashMap.get` may return no value. This is rejected with `SPR-TYPE-NULLABLE`:

<<< @/snippets/interop/nullable_bad.spr

Bind the result to a `let`, then handle both outcomes explicitly:

<<< @/snippets/interop/nullable.spr

Output: `missing`. Only immutable bindings narrow; copying a `var` into a `let`
and checking that copy is useful when the source is mutable. Java reference
results are nullable unless the supported annotations or `toString()` contract
say otherwise. Do not hide missing required data behind a default merely to
silence the checker. For optional data, `@std/nulls.spr` provides `or_else`.

## Exceptions: propagation and adaptation {#exceptions}

A named function must catch or declare a checked Java exception. This is
`SPR-FLOW-THROWS`:

<<< @/snippets/interop/exceptions_bad.spr

Propagate the imported exception when the caller needs its Java type. At a
Sprig function-value boundary, only `throws Error` is supported: catch the Java
exception inside a named function and translate it deliberately.

<<< @/snippets/interop/exceptions.spr

Output is `https://example.com`, then `invalid URI: ::bad`.
This example deliberately creates a domain message: `Error(message)` does not
retain the original Java exception as its cause. Preserve the Java exception by
propagating it if callers need that identity; use a library boundary that preserves
causes when one is available. A caught Java exception's `message` is `String?`;
its `toString()` is a non-null diagnostic description. Avoid broad catches that
turn errors into successful empty values.

## Collections and mutability {#collections}

Sprig collections and Java collections are distinct. This direct call fails
with `SPR-JVM-MEMBER`, even though the elements are strings:

<<< @/snippets/interop/collections_bad.spr

Copy to a Java collection explicitly, call Java, then take a detached Sprig snapshot:

<<< @/snippets/interop/collections.spr

Output is `[b, a]`, `[a, b]`, then `3`. `list_copy` creates a mutable independent
Java `ArrayList`; `list_snapshot` returns an immutable Sprig `List`. Later Java
mutation does not modify either the original or the snapshot. Map equivalents
are `map_copy` and `map_snapshot`. Snapshot functions reject null elements,
keys and values with `Error`. They do not create live views or deep-copy objects.

## Callbacks and SAM interfaces {#callbacks}

A Java functional-interface parameter can accept a Sprig `fn` with exact parameter
types. Java cannot express Sprig's `throws Error` contract, so this callback fails
with `SPR-TYPE-CALLABLE-THROWS`:

<<< @/snippets/interop/callbacks_bad.spr

Handle the error in a named function before passing the callback. For a
`Comparator[String]`, explicitly typed parameters and `compareTo` match its
`Int32` result without guessing a subtraction-based comparison:

<<< @/snippets/interop/callbacks.spr

Output is `[a, bb]`, then `failed`. The thread is joined before the program exits.
Choose the failure policy for the real application; logging here is just the
example's policy. For stateful framework callbacks or several abstract methods,
read [`conform`](/en/reference/jvm/conformance). General multi-method interfaces
are not SAMs; the compiler does not synthesize their implementations from a lambda.

## Overloaded methods and constructors {#overloads}

A floating-point capacity cannot select a `StringBuilder` constructor. This is
`SPR-JVM-MEMBER`:

<<< @/snippets/interop/overloads_bad.spr

Inspect before repairing:

```sh
sprig api java.lang.StringBuilder --member '<init>' --json
sprig api java.lang.Math --member max --json
```

Give the argument its intended type instead of rounding a value just to satisfy an overload:

<<< @/snippets/interop/overloads.spr

Output is `rows=3`, then `3`. A typed `Int32` chooses the `int` shape here; `Int`
usually prefers an exact `long` overload when one exists. Constructors and Java
methods use positional arguments. Explicit Java generic method arguments such
as `Collections.emptyList[String]()` settle a type parameter, not an arbitrary
overload tie. `SPR-JVM-AMBIGUOUS` requires examining all equally applicable
signatures; the compiler's cookbook hint does not pick one on your behalf.

## Building a large string {#string-building}

Repeated `result += fragment` in a loop copies the already accumulated prefix.
For similarly sized fragments, the total copied text can grow quadratically.
A short expression like `"count=" + count` remains convenient; an incremental
serializer should use a builder, or join parts it already holds:

<<< @/snippets/interop/string_building.spr

Both lines print `oak,birch,松`. Call `toString()` once after appending. `text.join`
from `@std/text.spr` works on string parts; retaining all parts costs memory.
Java `StringBuilder.length()` counts UTF-16 units, unlike Sprig String's code-point
length. The example only tests emptiness, for which that difference is harmless.
Java append formatting may differ for Sprig objects: explicitly convert a value
to its Sprig `toString()` text before appending when preserving that presentation
matters. No automatic loop rewrite or language semantic change is implied.
See [the reproducible comparison](/en/reference/jvm/string-building) for the
bounded benchmark and its limitations.
