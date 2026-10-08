# 11. Classes and objects

In this chapter you will learn:

- how to pack related values into one **object**: a class's fields;
- how to write **methods** for an object, including methods that change its fields;
- when a one-line class is enough and when you need the full form;
- what `==` compares when you use it on two objects of the same class;
- two common errors: leaving out a required field, and naming a parameter after a field.

## 11.1 Why classes

Programs often have a few values that belong to the same thing. A point has an x and a y; an expense has an item, an amount and a note. Written as separate variables, the fact that they belong together lives only in your head:

```sprig
let x = 3
let y = 4
```

You can pass the wrong one or forget one and the compiler sees nothing wrong. A class puts that fact into the code: it is a **blueprint** that says which fields this object is made of. Something created from a class is called an **object** (or an instance).

<<< @/snippets/book/ch11_point.spr

```text
Point(x=3, y=4)
7
```

- `class Point:` defines a class; class names start with an uppercase letter. The fields come next: `let x: Int` and `let y: Int`. A field is declared like a binding and every field has a type.
- Since chapter 9 you have seen classes used in place of tuples: the one-line class `class Point(x: Int, y: Int)` is the same class as the block form above, just without the line breaks. A one-line class can only declare `let` fields.
- `Point(x=3, y=4)` creates an object. **Construction writes the field names**, which is called named arguments; you can reorder them, and nobody reading the code has to remember the field order.
- `let start = ...` binds the object to a name.
- Printing an object lists all its fields: `Point(x=3, y=4)`. That is handy when debugging.
- `start.x` reads a field (dot notation). `start.x + start.y` is 3 + 4.

Construction cannot rename fields, and leaving out a required field stops the compiler:

::: details Deliberate mistake: leaving out a required field
<<< @/snippets/book/ch06_missing_field.spr

```text
SPR-CALL-MISSING-FIELD [TYPE] main.spr:5:14: Missing required field 'cents:Int'
```

Reading this error: the code is `SPR-CALL-MISSING-FIELD`, the position is line 5, column 14 of `main.spr` (at `Expense(`), and the message says the required field `cents:Int` is missing. `sprig explain SPR-CALL-MISSING-FIELD` gives the full explanation. If a field has a default value (like `note` in the `Expense` class in 11.2), you may leave it out.
:::

## 11.2 Methods: functions attached to the object

A class can also hold functions. Such a function is called a **method**. A method uses its object's fields directly, without writing `this` or `self`.

<<< @/snippets/book/ch06_classes.spr

```text
Expense(item=Coffee, cents=1250, note=morning)
Coffee: 1250 (morning)
false
true
```

- The fields `item` and `cents` are `let`, so they cannot change after construction; `note` is `var`, so it can.
- `func is_big() -> Bool:` and `func describe() -> String:` are methods. They are indented inside the class and written like the plain functions from chapter 7, with one implicit extra: the current object.
- Inside a method, `cents` means the field of that object, just like `item` and `note`.
- `let suffix = if note == "": ...` is the if expression from chapter 5; both branches give `suffix` a string.
- `coffee.note = "morning"` assigns to a `var` field; the object is on the left of the dot.
- `coffee.describe()` calls the method; the parentheses are empty because `describe` takes no arguments.
- The first line of output is the object's current fields (note that `note` was already changed to `morning` before the `print`); the second is the string `describe()` builds: `Coffee: 1250 (morning)`.
- `is_big()` asks whether the amount is over 10000. `coffee` is 1250, so `false`; `rent` is 120000, so `true`.

One detail: **method arguments are positional**, like plain functions. Construction writes field names (`Expense(item=..., cents=...)`) — only object construction and variant cases do that. Calling a method writes `coffee.describe()` or `c.add(4)`, never `c.add(amount=4)`; the latter gives `SPR-CALL-POSITIONAL-REQUIRED`.

There are no custom constructors, no static members and no visibility modifiers: everything in a class is public. The tool for hiding internals is the module (chapter 18).

## 11.3 Objects that change: methods that write fields

When a field is `var`, a method can change it. The change stays in the object, so the object's state changes as the program runs.

<<< @/snippets/book/ch11_counter.spr

```text
0
1
5
```

Follow `counter.count` step by step:

| statement | value of `counter.count` after it |
|---|---|
| `let counter = Counter()` | 0 (the field's default) |
| `counter.bump()` | 1 |
| `counter.add(4)` | 5 |

- `func bump() -> Unit:` takes no arguments and `count += 1` adds one to this object's `count`; `+=` is from chapter 3.
- `func add(amount: Int) -> Unit:` takes one argument, `amount`, and does `count += amount`.
- Both methods return `Unit`, which means "no value", exactly like plain functions.

::: tip Coming from another language?
- Construction writes no `new`: just `Counter()`. Fields and methods are read with a dot.
- There is no `this`/`self`: a method writes the field name directly. Spelling out the current object is an error (`SPR-NAME-UNRESOLVED`).
- Construction requires field names, like Python keyword arguments, except that in Sprig they are mandatory.
- Printing an object lists every field. In Python the default `__repr__` gives an address; Java's `toString` does too.
- There is no inheritance and no static members. Chapter 12's variants and chapter 16's contract classes cover "one of a fixed set" and "a shared set of methods" respectively.
:::

## 11.4 Deliberate mistake: a parameter named after a field

A method's fields and its parameters share one namespace. When a parameter takes a field's name, it hides the field:

<<< @/snippets/book/ch11_field_shadow.spr

```text
SPR-NAME-FIELD-SHADOW [NAME] main.spr:4:5: Parameter 'count' shadows field 'count' of class Counter
  Field declared here at 2:5
```

How to read it:

- the code is `SPR-NAME-FIELD-SHADOW`, at line 4, column 5 — the parameter `count`;
- the message says the parameter `count` shadows the field `count` of `Counter`;
- the second line is a related position: the field is declared at line 2, column 5. The compiler knows which field you mean, but not which one you want inside the body, so it refuses to guess.

The fix is to rename. A method's parameter may not share a field's name, which is different from the `this.count = count` pattern in many languages. Exercise 3 below fixes this class.

## 11.5 When are two objects equal

On class objects, `==` compares **identity**: whether they are the same object, not whether their fields match.

<<< @/snippets/book/ch06_identity.spr

```text
false
true
true
```

- `a` and `b` are built with the same fields, but they are two different objects, so `a == b` is `false`.
- `a == a` is of course `true`.
- To compare contents you write what you mean: `a.x == b.x and a.y == b.y` is `true`. With more fields you would write a method, such as `func same_as(other: Point) -> Bool`.
- When a value really is a **value** and not an **object** (a coordinate, an amount, a shape), chapter 12's variants are a better fit: a variant's `==` compares contents, and the compiler provides it.

## 11.6 When to use a class

Classes suit things with identity that change: a counter, a list being edited, a connection. The test is simple:

- Does the same content count as "the same thing"? If it changes and has its own identity — a class.
- Is equal content fully interchangeable, with no separate changes — chapter 12's variants.

A one-line class is for bundling a few values that will not change afterwards; when you need `var` fields, defaults or methods, write the block form. A class works as a field type, lives in lists and is passed as an argument, just like the value types.

Sprig classes have no inheritance, so "a base class plus subclasses" is written differently here: the fixed shapes go into a variant and the shared behavior becomes a plain function. Chapter 12 shows the full pattern.

## Summary

- A `class` holds fields and methods; fields have types, `let` fields cannot change, `var` fields can, and fields may have defaults.
- Construction writes field names: `Point(x=3, y=4)`. Printing an object lists all its fields.
- A method uses field names directly, with no `this`/`self`; method arguments are positional, and a parameter may not share a field's name.
- `==` compares identity; comparing contents is something you write, or a reason to use a variant.

## Exercises

**Exercise 1 (one-line class).** Define a one-line class `Book(title: String, pages: Int)`, create one object, print the whole object, then print its `title`.

::: details Answer
<<< @/snippets/book/ch11_ex1.spr

```text
Book(title=Sprig, pages=200)
Sprig
```
:::

**Exercise 2 (a changing field).** Write a `Reading` class with a `var read: Int = 0` field and a `read_pages(n: Int)` method that adds `n` to `read`. Read 10 pages, then 5 more, and print `read`.

::: details Answer
<<< @/snippets/book/ch11_ex2.spr

```text
15
```
:::

**Exercise 3 (fix the shadowing).** The `Counter` in 11.4 does not compile. Give the parameter a name that does not collide with the field, and make it print `3`.

::: details Answer
<<< @/snippets/book/ch11_ex3.spr

```text
3
```
:::

**Exercise 4 (identity).** Write a program that creates two `Point`s with the same fields (`a` and `b`), adds `let c = a`, and prints `a == b`, `a == c` and `a.x == b.x` in that order. Guess the output before you run it.

::: details Answer
<<< @/snippets/book/ch11_ex4.spr

```text
false
true
true
```

`a == b` is `false` (two different objects), `a == c` is `true` (`c` is the object `a`), and `a.x == b.x` is `true` (a field comparison).
:::

Next chapter: [Enums, variants and match](/en/tutorial/ch12-enums-variants).
