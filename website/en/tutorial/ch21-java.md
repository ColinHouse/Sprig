# 21. Calling Java

The first 20 chapters stayed inside Sprig's own world. This chapter opens a door: a Sprig program runs on the **JVM** (the Java Virtual Machine, the runtime Java programs run on), in the same place as Java programs. The whole Java ecosystem — time, compression, networking, databases — is yours to call.

In this chapter you will learn to:

- `import` a Java class and call its methods and constructors;
- use `sprig api` to see what a Java method looks like in Sprig;
- why every object a Java method returns needs a `null` check first;
- how Sprig's `Int` gets along with Java's `int`;
- pass a Sprig lambda and variadic arguments to Java;
- work with Java collections and arrays from Sprig;
- catch Java exceptions.

## 21.1 Your first Java class

**Why.** Java ships with an enormous standard library; `java.lang.Math`, for example, has a pile of math functions. Sprig does not reimplement them; it lets you call them.

The smallest example:

<<< @/snippets/book/ch21_first_java.spr

```text
9
4
```

Line by line:

- `import java.lang.Math as Math`: `java.lang.Math` is the class's **full name** (package path, dot, class name) and `as Math` is the short name used below. By convention the short name equals the class name; `as M` would work too, and then you write `M.max(...)`. The `as` part may be dropped when there is no clash, but this book always writes it.
- `Math.max(3, 9)`: a **static method** call. It needs no object; the syntax is just a function call with positional arguments.
- `Math.abs(-4)` likewise takes the absolute value.

Java method names are `camelCase` (lower-case first letter, later words capitalized), unlike Sprig's own `snake_case`. You do not have to memorize anything: copy the name from `sprig api`.

**Deliberate mistake: forgetting the import.**

<<< @/snippets/book/ch21_no_import.spr

```text
SPR-NAME-UNRESOLVED [NAME] main.spr:1:7: Unresolved name 'Math'
  hint: A Java class needs an import first: import java.lang.Math as Math.
```

At line 1, column 7 (where `Math` starts) the compiler says it does not know the name, and the hint spells out the missing line. Java classes never appear in the namespace on their own; even one in `java.lang` needs an import.

## 21.2 Looking up a signature with sprig api

**Why.** Java methods often have several overloads with different parameter types. Instead of guessing what `max` accepts, ask the compiler.

```text
$ sprig api java.lang.Math --member max
Java API: java.lang.Math
constructors:
staticMethods:
  public static double java.lang.Math.max(double,double) => max(Float, Float) -> Float
  public static float java.lang.Math.max(float,float) => max(Float32, Float32) -> Float32
  public static int java.lang.Math.max(int,int) => max(Int32, Int32) -> Int32
  public static long java.lang.Math.max(long,long) => max(Int, Int) -> Int
instanceMethods:
fields:
```

The left side of the arrow is the Java declaration, the right side the signature Sprig sees. `max` has four overloads and Sprig treats each separately: two `Int` arguments pick the `long` one, because `Int` is Sprig's 64-bit integer (chapters 3 and 17).

Now look at the question mark on a return type — the star of the next section:

```text
$ sprig api java.time.LocalDate --member of
Java API: java.time.LocalDate
constructors:
staticMethods:
  public static java.time.LocalDate java.time.LocalDate.of(int,int,int) => of(Int32, Int32, Int32) -> LocalDate?
  public static java.time.LocalDate java.time.LocalDate.of(int,java.time.Month,int) => of(Int32, Month, Int32) -> LocalDate?
instanceMethods:
fields:
```

The `?` after `LocalDate` means "could be `null`".

## 21.3 Every Java reference result may be null

**Why.** Java has `null` everywhere, and plenty of methods return it in special cases. Sprig records "may be null" in the type (chapter 13) and takes the most conservative stance toward Java: **every reference a Java method returns is treated as nullable**, so check before you use it.

The right way is to bind it and check it:

<<< @/snippets/book/ch21_null_check.spr

```text
2024
```

`LocalDate.of(2024, 2, 28)` never actually returns `null`, but Sprig refuses to guess. `date` has type `LocalDate?`; only inside the `if date != null:` branch is it a non-null `LocalDate`, and only there can `getYear()` be called.

Forgetting the check:

<<< @/snippets/book/ch13_nullable_java.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:4:12: Cannot access 'getYear' on a value that may be null (receiver type LocalDate?)
  hint: Bind the receiver to a let and check it with 'if value != null:' before using 'getYear'.
```

The error says the receiver of `getYear` is `LocalDate?` and may be `null`, and the hint gives the standard fix.

The rule is just as strict in the other direction: **a Java parameter does not accept a nullable value by default**.

<<< @/snippets/book/ch13_null_argument.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:5:11: Nullable value is not accepted by Java parameter 1 of ArrayList.add; Java parameters are treated as non-null (expected Object, actual String?)
  hint: Check for null first (if x != null), or handle the absent case in Sprig.
```

What these rules buy is concrete: Java's famous `NullPointerException` cannot be written in Sprig code. A few extra lines of checking stop a whole family of bugs at compile time.

Three kinds of result come without a question mark:

- primitive results: `names.size()` is `Int32`, `isEmpty()` is `Bool`;
- `toString()`, which Java's contract guarantees to return a `String`;
- methods and fields carrying nullability annotations (`@NotNull`, `@Nullable` and friends), which Sprig reads. See [appendix F](/en/tutorial/appendix-f-advanced-java) for the details.

## 21.4 Numbers: how Int becomes int

**Why.** Java APIs are full of 32-bit `int`, while Sprig's everyday `Int` is 64-bit. The rule is: **literals fit where they need to, variables narrow with a runtime check, and anything that may lose precision must be written out**.

<<< @/snippets/book_en/ch13_numbers.spr

```text
101
[ab, ab, ab, ab, ab]
4294967296
0.1
```

- `Integer.toBinaryString(n)` wants an `int`, and `n` is an `Int` variable. That narrowing happens automatically when the value fits in 32 bits, with a runtime range check.
- `Collections.nCopies[String](n, "ab")` uses `n` as the count; the generic return type argument `[String]` has to be written, because the compiler cannot infer it from a `String` argument.
- `Math.abs(big)` where `big` is `4294967296`, out of 32-bit range: Java has a `long` overload and Sprig prefers it, because `Int` is exactly `long` and needs no narrowing, so the number prints unchanged.
- The last line: `Float` never becomes `float` by itself. `ratio.toFloat32Lossy()` says explicitly "rounding is fine", producing Java's `float`.

If an `Int` variable really is out of 32-bit range and reaches an `int` parameter, the program stops at run time:

```sprig
import java.lang.Integer as Integer

let big: Int = 4294967296
print(Integer.toBinaryString(big))
```

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] main.spr:4:1: Numeric error: Int value outside Int32 range
  hint: Guard the checked arithmetic or use an explicit conversion; see `sprig help numerics`. Run with --stacktrace to see the JVM stack.
```

That is a feature: Java would silently truncate, Sprig errors rather than hand you a wrong answer.

**Deliberate mistake: passing a Float where a float is wanted.**

<<< @/snippets/book/ch13_float_to_float.spr

```text
SPR-JVM-MEMBER [JVM] main.spr:4:7: Java class Float has no method 'valueOf' matching 1 argument(s)
```

`Float.valueOf(float)` exists in Java, but none of its overloads accepts Sprig's `Float` (which is Java's `double`). The error says no `valueOf` has the right arity, because an argument of the wrong type does not count. `sprig check --json` lists every candidate and why it was rejected.

## 21.5 Passing functions to Java

**Why.** Java libraries often want a block of code to call later — a comparison rule for sorting, for instance. Java expresses that with interfaces (`Comparator`, `Runnable`, `Consumer` and so on), and Sprig lets you write its lambda (chapter 15) right there.

<<< @/snippets/book/ch13_callback.spr

```text
[Al, cy, bob]
AL
CY
BOB
```

- `names.sort(fn(a: String, b: String) => a.length() - b.length())`: `sort` wants a `Comparator` and the lambda does the comparing. `a.length()` is an `Int`; the lambda returns `Int`, and the compiler narrows it to the `int` the Java interface requires, checking the range at run time.
- **Lambda parameter types must be written** and must match the interface exactly. That differs from Sprig's own function values, whose types can be inferred; the mistake below shows what happens.
- `names.forEach(fn(n: String) => print(n.toUpperCase()))`: `forEach` wants a `Consumer`; the lambda takes one element per call and prints it upper-cased. An interface returning `void` accepts any result, so the `Unit` here is fine.

**Deliberate mistake: a lambda parameter without a type.**

<<< @/snippets/book/ch21_lambda_types.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:4:16: mismatched input ',' expecting ':' (unexpected ',')
```

The parser stops at the first comma: it was waiting for `name: type` and saw `,` instead. Write `: String` after both `a` and `b` and it compiles.

### Variadic arguments

A Java method may declare a variable number of trailing arguments, such as `String.format`'s `Object...`. Sprig writes them out directly:

<<< @/snippets/book/ch13_varargs.spr

```text
order-7
no arguments
notes.txt
```

In `String.format("%s-%d", "order", 7)` each trailing argument is checked against the element type and packed into an array by the compiler; `format("no arguments")` passes none, which is legal too. The same works for Java's `Paths.get("home", "ada", "notes.txt")`.

::: tip Coming from another language?
If you know Java: a Sprig lambda here is an instance of the Java functional interface, without the anonymous-class ceremony. Parameter types cannot be omitted, because the compiler does not infer this direction. An interface with more than three abstract parameters cannot be used.
:::

## 21.6 Collections and arrays

**Why.** Containers exchanged with Java libraries (`ArrayList`, `HashMap`) and Sprig's own `List` and `Map` are two different type families. Sprig's position: **conversions happen explicitly**, never as a hidden copy.

First, moving data between the two worlds, with the four functions in `@std/jvm`:

<<< @/snippets/book_en/ch13_collections.spr

```text
[ada, grace]
3
2
```

- `java_names` is a Java `ArrayList[String]`; `jvm.list_snapshot(java_names)` copies it now into an immutable Sprig `List`. Later changes on the Java side cannot be seen through the Sprig list.
- `jvm.list_copy(names)` goes the other way: a fresh Java `ArrayList`. `add`-ing to it does not touch the original Sprig list, so the last line still prints `2` for `names.size()`.

Assigning one family to the other directly does not work:

<<< @/snippets/book/ch13_collection_mismatch.spr

```text
SPR-TYPE-ASSIGN [TYPE] main.spr:4:37: Type mismatch in initializer (expected java.util.ArrayList[String], actual List[String])
```

`for` loops only accept Sprig's own collections too:

<<< @/snippets/book/ch13_for_java_list.spr

```text
SPR-TYPE-OPERAND [TYPE] main.spr:5:13: for requires a collection or String (expected List, MutableList, Map, MutableMap or String, actual java.util.ArrayList[String])
```

Either `list_snapshot` first, or hand a lambda to `forEach` as in 21.5.

### Arrays and bytes

A Java array is an **opaque value** in Sprig: you can hold it, check it for null, and pass it to another Java method, but you cannot build it, index it or loop over it in Sprig source. The common case is `byte[]`, and the runtime ships explicit helpers:

<<< @/snippets/book_en/ch13_bytes.spr

```text
3
dGVh
```

`HostBytes.utf8("tea")` encodes text as UTF-8 bytes and `HostBytes.length(bytes)` gives their count; `Base64.getEncoder()` is an ordinary Java call and `encodeToString` turns the bytes into Base64 text. `HostBytes` also has `utf8String(bytes)` (bytes back to text) and `hex(bytes)`.

## 21.7 Java exceptions

**Why.** Java methods throw exceptions, some of them **checked** (the compiler forces you to deal with them, such as `IOException`). Sprig treats that kind of possible failure as part of the signature: exactly like `throws Error` (chapter 14), the call site either catches it or declares it.

<<< @/snippets/book_en/ch13_exceptions.spr

```text
could not read: java.nio.file.NoSuchFileException: definitely-missing.txt
```

- `Files.readString` declares `IOException`, so `read_text` lists `throws IOException` and passes the responsibility to its caller.
- The top-level `try` calls it and `catch problem: IOException:` handles the failure. `NoSuchFileException` is a subclass of `IOException`, so one `catch` covers it.
- An exception object is an ordinary Java value: `problem.toString()` always returns a `String`, while `getMessage()` returns `String?`.
- **A Java exception is not a Sprig `Error`**: `catch problem: Error:` will not catch `NoSuchFileException`. Catch it by its Java class name.
- Unchecked exceptions (`ArithmeticException`, `IndexOutOfBoundsException`) need no declaration, but can be caught the same way.

**Deliberate mistake: a checked exception neither declared nor handled.**

<<< @/snippets/book/ch21_checked.spr

```text
SPR-FLOW-THROWS [FLOW] main.spr:8:16: Call may throw IOException; declare 'throws IOException' or handle it with try/catch
  hint: Declare it on 'read_text' by changing its header to 'func read_text(name: String) -> String throws Error, IOException:', and its callers then handle or declare it too (top-level statements need neither), or handle it where it happens: 'try:' around the call, then 'catch problem: IOException:' with what to do instead. Sprig keeps recoverable errors explicit; there is no implicit propagation.
```

The hint is the answer key: either add `IOException` to the `throws` list or wrap the call in `try`/`catch` on the spot.

::: tip Coming from another language?
If you have met Java's `NullPointerException` and checked exceptions: Sprig turns both into facts in the type system — check nullability, declare or catch checked exceptions. It is a little more typing in exchange for far fewer runtime surprises.
:::

## Summary

- `import java.package.Class as Name` brings in a Java class; static methods, instance methods and constructors are called directly with positional arguments.
- Not sure about a signature? `sprig api Class --member method` shows what Sprig sees; a `?` on a result means it may be `null`.
- Java reference results are always nullable and must be checked; Java parameters are non-null by default.
- Literals and small `Int` values narrow to `int` with a runtime check; `Float` never becomes `float` on its own.
- Lambdas can be passed to Java functional interfaces; parameter types must be written.
- Java and Sprig collections never convert implicitly: use `@std/jvm`; use `HostBytes` for `byte[]`.
- Java checked exceptions work like `throws`; catch them by Java class name, not as Sprig `Error`.

## Exercises

**Exercise 1 (easy).** Use `java.lang.Math` to compute and print the square root of 2. Hint: the method is `sqrt`, and the argument is `2.0`.

::: details Answer
<<< @/snippets/book/ch21_ex_sqrt.spr

```text
1.4142135623730951
```

`sqrt` returns Java's primitive `double`, which maps to a non-null Sprig `Float`.
:::

**Exercise 2 (easy).** Use `java.util.HashMap` to map names to ages: Ada=36, Grace=45. Print the number of entries and Ada's age. Hint: `put(key, value)`, `size()` returns `Int32`, and `get` returns something nullable.

::: details Answer
<<< @/snippets/book/ch21_ex_hashmap.spr

```text
2
36
```

A Java generic class writes its arguments: `HashMap[String, Int]()`. `ages.get("Ada")` is `Int?`, so check `if ada != null:` first.
:::

**Exercise 3 (medium).** Use `java.lang.StringBuilder` to join `"Sprig"` and `"!"`, then print the result. Hint: you can ignore what `append` returns; `toString()` hands you the `String`.

::: details Answer
<<< @/snippets/book/ch21_ex_builder.spr

```text
Sprig!
```

The result of `append` can be ignored as a statement; `toString()` is guaranteed to return a non-null `String`, so it can go straight into `print`.
:::

**Exercise 4 (medium).** Call `Integer.parseInt("42")` and `Integer.parseInt("abc")`, catch the `NumberFormatException` from the second and print its message. Hint: unchecked exceptions need no declaration — just `catch` it.

::: details Answer
<<< @/snippets/book/ch21_ex_catch.spr

```text
42
bad number: java.lang.NumberFormatException: For input string: "abc"
```

`catch problem: NumberFormatException:` catches it by Java class name; `problem.toString()` always has a value and includes the class and the reason.
:::

**Exercise 5 (harder).** Build a Java `ArrayList[Int]` with 3, 1, 2 hit and sort it from largest to smallest with a lambda, then print it. Hint: a comparator returning `b - a` sorts descending.

::: details Answer
<<< @/snippets/book/ch21_ex_sort.spr

```text
[3, 2, 1]
```

The interface wants the comparator to return Java's `int`; the lambda returns `Int` and the compiler narrows it. `print` on a Java `ArrayList` uses Java's `toString`, giving `[3, 2, 1]`.
:::

Next chapter: [Concurrency](/en/tutorial/ch22-concurrency) — making a Sprig program do several things at once.
