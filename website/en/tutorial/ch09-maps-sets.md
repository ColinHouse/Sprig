# 9. Maps and sets

In this chapter you will learn:

- maps: storing and fetching values by key;
- reading a missing key gives `null`, and what the `?` in a type means;
- the counting recipe with a map;
- the run-time trap in `m[k] += 1`;
- how to walk keys and values;
- the set type from `@std/sets.spr`.

## 9.1 Why you need maps

A list finds elements by index: 0, 1, 2 and so on. To find something "by name", you would have to remember which position holds which name. A map pairs a key directly with a value:

<<< @/snippets/book/ch09_map_basics.spr

```text
{Ada: 90, Bob: 72}
2
90
{Ada: 95, Bob: 72, Cyd: 85}
[Ada, Bob, Cyd]
[95, 72, 85]
true
false
72
{Ada: 95, Cyd: 85}
null
false
```

Line by line:

- `{"Ada": 90, "Bob": 72}` is a map literal written as `key: value`, separated by commas. With no annotation it is a `MutableMap[String, Int]`; the annotation here is for show.
- `scores["Ada"]` reads the value by key, giving `90`.
- `scores["Cyd"] = 85` adds a new key; `scores["Ada"] = 95` overwrites the value of an existing key.
- The print format is `{Ada: 95, Bob: 72, Cyd: 85}`. **A map keeps insertion order**: `Ada` was there first and stays first after the overwrite, while the new `Cyd` goes at the end.
- `scores.keys()` is the list of keys `[Ada, Bob, Cyd]`; `scores.values()` gives the values in the same order, `[95, 72, 85]`.
- `scores.containsKey("Bob")` asks whether the key exists; `"Zoe" in scores` is another spelling.
- `scores.remove("Bob")` deletes the key and returns **the old value**, `72`; removing it again gives `null` because the key is gone. After the first removal the map is `{Ada: 95, Cyd: 85}`.
- `scores.isEmpty()` is `false`; two keys are left.

The two spellings for reading and writing: `scores[k]` is `scores.get(k)`, and `scores[k] = v` is `scores.set(k, v)`.

| Method | What it does |
|---|---|
| `size()` / `isEmpty()` | number of keys / whether it is empty |
| `get(k)` / `m[k]` | read by key (the result may be null, see 9.2) |
| `set(k, v)` / `m[k] = v` | write or overwrite |
| `containsKey(k)` / `k in m` | is the key there |
| `keys()` / `values()` | list of keys / list of values |
| `remove(k)` | remove the key, return the old value or `null` |
| `clear()` | empty the map |

An empty map literal `{}` needs a type just like an empty list: `let counts: MutableMap[String, Int] = {}`.

## 9.2 Reads can come up empty

Here is the big difference from lists: nothing guarantees that a key exists when you read it. The result is therefore a **nullable type** `Int?`, which reads as "maybe an `Int`":

<<< @/snippets/book/ch09_map_null.spr

```text
null
coffee: none
tea: 12
```

- The static type of `stock["coffee"]` is `Int?`. The key does not exist, so the value is `null`, and `print` shows `null`.
- `if coffee == null:` runs, printing `coffee: none`.
- `stock["tea"]` exists with value 12; inside `if tea != null:` the variable `tea` is treated as a real `Int`, printing `tea: 12`.

"After `!= null` it can be used as a plain value" is called narrowing; chapter 13 covers nullable values properly. For now remember two rules: a map read gives `Int?`, and `if x != null:` comes before arithmetic.

### Deliberate mistake: doing arithmetic on a value that may be null

<<< @/snippets/book/ch09_map_null_arith.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:3:7: Operator '+' cannot use a value that may be null (expected non-null operands, actual Int? and Int)
  hint: coffee may be null (Int?): check it first with 'if coffee != null:', and inside that block it is Int, or give a fallback with or_else from @std/nulls.spr.
```

The code `SPR-TYPE-NULLABLE` says "a nullable value cannot be used directly". How to read the rest:

- `actual Int? and Int`: the left operand is `Int?` (possibly missing) and the right is `Int`.
- The `hint` gives two roads: test with `if coffee != null:` first, or give a fallback with `or_else` (a tool in `@std/nulls.spr`, covered in chapter 13).

The fix is:

```sprig
if coffee != null:
    print(coffee + 1)
```

## 9.3 The counting recipe

The classic map job is counting: given a stream of values, count how often each one appears. Counting one value with a list exercise is fine; counting all of them wants a map:

<<< @/snippets/book/ch09_map_count.spr

```text
{tea: 3, fig: 1, pear: 1}
3
```

The loop body is always the same three steps:

1. `let old = counts[word]` reads the current count (type `Int?`).
2. `old == null` means this is the first sighting, so write `1`.
3. Otherwise write back `old + 1`.

Follow the table:

| Step | `word` | `old` | `counts` |
|---|---|---|---|
| start | — | — | `{}` |
| 1 | tea | null | `{tea: 1}` |
| 2 | fig | null | `{tea: 1, fig: 1}` |
| 3 | tea | 1 | `{tea: 2, fig: 1}` |
| 4 | pear | null | `{tea: 2, fig: 1, pear: 1}` |
| 5 | tea | 2 | `{tea: 3, fig: 1, pear: 1}` |

When the loop ends, `counts` is `{tea: 3, fig: 1, pear: 1}` and `counts["tea"]` is `3`.

## 9.4 The run-time trap in `+=`

Looking at "read, test, write back", you might want the shorter form:

```sprig
counts[word] += 1
```

That only counts while the key **already exists**. With a missing key the line stops as a run-time error:

<<< @/snippets/book/ch09_map_add_trap.spr

Save the program as `main.spr` and run it:

```bash
sprig run main.spr
```

```text
2
SPR-RUNTIME-ERROR [RUNTIME] main.spr:4:1: Uncaught Error: compound assignment requires an existing map key
  hint: Catch it with try/catch or declare throws in the calling function. Run with --stacktrace to see the JVM stack.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

- `counts["tea"] += 1` works: the key exists, and the program prints `2`.
- `counts["fig"] += 1` fails: `+=` has to read the old value and add, but `counts["fig"]` is `null`, which cannot be added. The hint says it plainly: `compound assignment requires an existing map key`.

So count with the recipe from 9.3: when you do not know whether the key is there, read it into `old` and test for `null`.

## 9.5 Walking keys and values

<<< @/snippets/book/ch09_map_iterate.spr

```text
tea
milk
rice
12
3
20
tea=12
milk=3
rice=20
```

- `for key in stock:` walks a map directly and yields the **keys**.
- `stock.values()` is the list of values and can be looped over.
- `stock.keys()` is the list of keys; inside the loop `stock[key]` is still `Int?`, so the `if value != null:` test stays.
- In `key + "=" + value`, `value` has been narrowed to an `Int`, and a string plus an `Int` joins with `+`.

## 9.6 Sets: asking only "is it there"

If all you care about is which values showed up, not their order or count, use a set. Sprig's sets live in `@std/sets.spr`:

<<< @/snippets/book/ch09_sets.spr

```text
2
true
true
true
true
[apple, fig]
[apple, fig, kiwi]
[fig]
[apple]
```

- `sets.of(["apple", "pear", "apple"])` builds a set from a list: the repeated `apple` is kept once, members sit in **first-seen order**, and `size()` is 2.
- `fruits.has("apple")` asks whether a value is a member.
- `fruits.add("fig")` adds a member and returns a `Bool`: `true` when it was new, `false` when it was already there.
- `fruits.remove("pear")` removes a member and returns a `Bool`: `true` if it was there, `false` on a second try.
- `fruits.to_list()` gives the members in insertion order, `[apple, fig]`.
- `union`, `intersection` and `difference` return new sets and leave their inputs alone.

An empty set writes its element type: `sets.of[String]([])`.

::: tip Coming from another language?
Python's `set` has no order guarantee and its iteration order can vary; Sprig's set keeps first-insertion order. Java's `HashSet`/`HashMap` also make no order promise, while Sprig's `Map` and `Set` print in insertion order, so output is reproducible.
:::

## 9.7 Packing two things at once?

A list holds one kind of element, and a map pairs one key with one value. To bundle "name, score and hometown" into one thing you need your own class; chapter 11 shows how to define one in a single line. Until then, a map with field names as keys works.

## Summary

- `{key: value}` is a map literal, a `MutableMap` by default; maps keep insertion order.
- A map read gives a nullable `V?`; using it directly is `SPR-TYPE-NULLABLE`, so test `if x != null:` first.
- Count with "read, test for `null`, write back"; `m[k] += 1` needs the key to exist or it is the run-time `SPR-RUNTIME-ERROR`.
- `for key in m:` walks the keys; `m.keys()` and `m.values()` give lists.
- `@std/sets.spr` provides sets: `of`, `add`, `has`, `remove`, `size`, `to_list`, `union`, `intersection`, `difference`.

## Exercises

**Exercise 1 (warm-up)** Given `{"tea": 12, "milk": 3, "rice": 20}`, add up `values()` and print the total.

Hint: `for value in stock.values():`, then `total += value`.

::: details Answer
<<< @/snippets/book/ch09_ex1_answer.spr

```text
35
```
:::

**Exercise 2** Count how often each letter appears in `["b", "a", "b", "c", "b"]`; print the whole map and the count for `"b"`.

Hint: use the counting recipe from 9.3.

::: details Answer
<<< @/snippets/book/ch09_ex2_answer.spr

```text
{b: 3, a: 1, c: 1}
3
```
:::

**Exercise 3** Add two maps together: `{"a": 1, "b": 2}` and `{"b": 3, "c": 4}` become `{"a": 1, "b": 5, "c": 4}` — a key on both sides gets the sum.

Hint: walk `right.keys()`; `left[key]` is `Int?`, so narrow it first and then write either `other` or `old + other`.

::: details Answer
<<< @/snippets/book/ch09_ex3_answer.spr

```text
{a: 1, b: 5, c: 4}
```
:::

**Exercise 4** Use `@std/sets` on `["a", "b", "a", "c"]` and print the number of distinct values and the list.

Hint: `sets.of(values)`, then `size()` and `to_list()`.

::: details Answer
<<< @/snippets/book/ch09_ex4_answer.spr

```text
3
[a, b, c]
```
:::

The next chapter puts this and everything before it together into a small playable program: [Project: guess the number](/en/tutorial/ch10-project-guess).
