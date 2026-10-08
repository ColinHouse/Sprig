# 8. Lists

In this chapter you will learn:

- why you need a container that holds many values;
- list literals, and reading and writing by index;
- the everyday methods: `append`, `size`, `insert`, `remove`, `removeAt`, `clear` and friends;
- what happens when an index is out of bounds;
- why an empty list literal needs a type;
- how `List` and `MutableList` relate: read-only views, snapshots and copies;
- how two lists compare and how to sort one;
- the part of `@std/lists.spr` that needs no function values.

## 8.1 Why you need lists

One student's score fits in one variable:

```sprig
let score_ada = 90
let score_bob = 72
```

What about forty students? You do not want forty variable names. A list holds a run of values of the same type under one name:

<<< @/snippets/book/ch08_list_basics.spr

```text
[90, 72, 85]
3
90
85
175
[60, 72, 85, 100]
```

Reading the program line by line:

- `let scores = [90, 72, 85]`: `[...]` is a list literal with comma-separated elements. With no annotation it is a `MutableList[Int]`; the element type `Int` is inferred from the contents.
- `print(scores)` prints `[90, 72, 85]`. Brackets and commas are the print format; string elements print without quotes.
- `scores.size()` is the number of elements. `size` is a method, so it takes a dot and parentheses.
- `scores[0]` is the first element: indexes start at 0, so `scores[2]` is the third element, `85`.
- `scores[0] + scores[2]` adds two elements as ordinary `Int` values, giving `175`.
- `scores[0] = 60` overwrites the first element.
- `scores.append(100)` adds one at the end. `append` has no return value, so `print(scores.append(100))` is not allowed.

Reading and writing each have two spellings: `scores[0]` is `scores.get(0)`, and `scores[0] = 60` is `scores.set(0, 60)`.

## 8.2 A set of everyday methods

Besides adding and fetching, you need to ask "is it in there", "where", and to delete elements:

<<< @/snippets/book/ch08_list_methods.spr

```text
true
true
1
-1
false
[Ada, Eve, Bob, Cyd]
true
[Eve, Bob, Cyd]
Eve
[Bob, Cyd]
Bob
2
[]
true
```

Line by line:

- `names.contains("Bob")` asks whether some element equals `"Bob"`; `"Cyd" in names` is another spelling.
- `names.indexOf("Bob")` is its position, counted from 0; `names.indexOf("Zoe")` finds nothing and gives `-1`.
- `names.isEmpty()` asks whether the list is empty; it still has elements, so `false`.
- `names.insert(1, "Eve")` inserts at index 1 and pushes `"Bob"` and `"Cyd"` one place to the right.
- `names.remove("Ada")` removes the first element **equal to** `"Ada"` and returns a `Bool`: whether it removed something. The result is `[Eve, Bob, Cyd]`.
- `names.removeAt(0)` removes **by index** and returns the removed element, `"Eve"`; the list becomes `[Bob, Cyd]`.
- `names.get(0)` is `names[0]`, giving `"Bob"`; `names.size()` is 2.
- `names.clear()` empties the list, which prints as `[]`; only now is `isEmpty()` `true`.

A table of the common methods (all on the mutable list `MutableList`):

| Method | What it does |
|---|---|
| `size()` / `isEmpty()` | number of elements / whether it is empty |
| `get(i)` / `xs[i]` | read index `i` |
| `set(i, x)` / `xs[i] = x` | write index `i` |
| `append(x)` | add at the end |
| `insert(i, x)` | insert at index `i` |
| `remove(x)` | remove the first equal value, return a `Bool` |
| `removeAt(i)` | remove by index, return the removed element |
| `contains(x)` / `x in xs` | does it hold the value |
| `indexOf(x)` | first position, or `-1` |
| `clear()` | empty the list |
| `sort()` | sort in place, ascending |

## 8.3 Walking a list with a loop

Real programs rarely handle a single element; they go through the whole list. The next program accumulates a total and builds a transformed list at the same time. Follow it with the trace table:

<<< @/snippets/book/ch08_list_loop.spr

```text
8
[8, 2, 6]
0: 4
1: 1
2: 3
```

The first loop takes each element `n` and does two things: adds `n` to `total`, and appends `n * 2` to `doubled`. Follow the values:

| Step | `n` | `total` | `doubled` |
|---|---|---|---|
| start | — | 0 | `[]` |
| 1 | 4 | 4 | `[8]` |
| 2 | 1 | 5 | `[8, 2]` |
| 3 | 3 | 8 | `[8, 2, 6]` |

The output `8` and `[8, 2, 6]` is `total` and `doubled` when the loop ends.

- `let doubled: MutableList[Int] = []` starts an empty list for the result. An empty literal must write its element type; section 8.5 shows what happens when it does not.
- `total += n` is shorthand for `total = total + n`. `total` must be declared `var` to change.
- The second loop uses `range(0, numbers.size())` to produce the indexes `0, 1, 2`; `i` is an `Int`.
- `i.toString()` turns an `Int` into a `String` so it can be concatenated with another string. When you join a string and a number, convert the number yourself.

## 8.4 What an out-of-bounds index does

An index must be between `0` and `size() - 1`. Going past that is not a compile error: the compiler does not know how long the list will be at run time. It is a run-time error.

Save the program in `ch08_oob.spr` as `main.spr` and run it:

```bash
sprig run main.spr
```

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] main.spr:2:1: List index 5 is out of bounds; size is 3
  hint: Check the list length before indexing. Run with --stacktrace to see the JVM stack.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

The program stops here and exits with a nonzero status; later statements do not run. Read the line: the code `SPR-RUNTIME-EXCEPTION`, the position `main.spr:2:1`, "index 5 is out of bounds; size is 3", then a `hint` telling you to check the length before indexing. This is a bug in your program; the fix is to keep the index in range, not to "catch" the error.

::: tip Coming from another language?
Many languages surface an `IndexOutOfBoundsException`/`IndexError` here. Sprig turns it into a diagnostic with an error code and a hint, and the program still stops. Indexes start at 0 and the end of a range is exclusive (`range(0, xs.size())` covers every index), as in Python and Java arrays.
:::

## 8.5 An empty list literal needs a type (deliberate mistake)

This does not pass the checker:

<<< @/snippets/book/ch08_empty_list.spr

```text
SPR-TYPE-INFER [TYPE] main.spr:1:10: Cannot infer the type of an empty list literal; add a type annotation
```

There is not a single element in `[]` to suggest the element type, so the compiler asks you to write it:

```sprig
let a: MutableList[Int] = []   # will be appended to and set
let b: List[String] = []       # read-only
```

Once the type is written, whatever you put in later is checked; a wrong element type is a type error, not a run-time surprise.

## 8.6 List and MutableList

Sprig puts "can change" and "cannot change" in the types: `MutableList[T]` is mutable, `List[T]` is read-only. Many functions only need to read, so they write `List` in the signature and promise the caller "I will not change your list". A `MutableList` can be passed where a `List` is expected: it is the same data, seen read-only.

<<< @/snippets/book/ch05_readonly.spr

```text
6
10
4
5
```

Line by line:

- `func total(xs: List[Int])` walks the list and adds it up. The caller's `numbers` is a `MutableList`; it goes straight in, with no copy.
- The first two lines, 6 and 10: after `numbers.append(4)`, `total(numbers)` goes from 6 to 10, so the function sees the caller's list.
- `let snapshot = numbers.toList()` takes a read-only snapshot; after `numbers.append(5)` the snapshot still has 4 elements while the list has 5.

The copying behaviour of `toList()` and `toMutableList()` deserves its own program:

<<< @/snippets/book/ch08_list_views.spr

```text
[90, 72, 85]
[90, 72, 85]
[90, 72, 85, 60]
[1, 2]
[1, 2, 3]
[90, 72, 85, 60, 100]
[90, 72, 85, 60, 100]
```

Match the seven lines to the code:

- `let view: List[Int] = grades` is a read-only **view** of the same data. After `grades.append(85)`, `view` has 85 too (line 1).
- `toList()` copies a read-only snapshot. After `grades.append(60)`, the snapshot still has three elements (line 2) and the list has four (line 3).
- `let frozen: List[Int] = [1, 2]` is a read-only list; `frozen.toMutableList()` copies out a mutable `copy`. Changing `copy` leaves `frozen` alone (lines 4 and 5).
- `grades.toMutableList()`: `grades` is already mutable, so there is no copy: it returns **the same** list (both lines 6 and 7 gained the 100). `toMutableList()` is only a copy on a read-only list.

::: tip Coming from another language?
`List` is not Java's `Collections.unmodifiableList` wrapper, and it does not freeze the data: it is the same collection seen read-only, and the elements themselves (if they are mutable collections) are not protected. For an independent copy, take a `toList()` snapshot or a `toMutableList()` copy.
:::

### Deliberate mistake: changing a read-only list

<<< @/snippets/book/ch05_immutable.spr

```text
SPR-COLLECTION-IMMUTABLE [TYPE] main.spr:2:1: Cannot call mutating method 'append' on an immutable collection; take an explicit snapshot with toMutableList()
```

`append`, `set`, `insert`, `remove`, `removeAt`, `clear` and `sort` only exist on `MutableList`. Using one on a `List` is rejected outright, and the message points at `toMutableList()` to get a mutable copy.

## 8.7 Comparing and sorting

Lists compare with `==` and sort with `sort()`:

<<< @/snippets/book/ch08_list_compare.spr

```text
true
false
true
[banana, fig, pear]
[72, 85, 90]
```

- `a == b` is `true`: both lists have the same length and equal elements at every position.
- `a == c` is `false`: same elements, different order, so they are not equal. Lists compare by contents, not by identity.
- `words.sort()` sorts the strings in place, ascending, giving `[banana, fig, pear]`. `sort()` returns nothing, so it stands on its own line and cannot go inside `print`. The same works for numbers.

## 8.8 @std/lists: the tools that need no function values

The built-in methods stay deliberately small; batch operations live in the standard module `@std/lists.spr`:

<<< @/snippets/book_en/ch08_std_lists.spr

```text
[banana, fig, fig, pear]
[pear, fig, banana, fig]
[fig, banana, fig, pear]
[pear, fig, banana]
1
6
[pear, fig]
[fig]
```

- `import "@std/lists.spr" as lists` brings the module in; call its functions as `lists.name`.
- `lists.sorted(words.toList())` returns a new sorted list, and `words` keeps its original order (line 2). The `toList()` call passes a read-only snapshot first: hand a mutable list straight to `lists.sorted` today and the implementation sorts the original too (the module comment says the input is left alone; this is a known mismatch). The snapshot keeps it predictable.
- `lists.reversed(words)` returns a new reversed list, and `lists.distinct(words)` keeps each value at its first occurrence.
- `lists.index_of(words, "fig")` is the first index of `"fig"`; `lists.sum([1, 2, 3])` adds numbers up.
- `lists.take(words, 2)` takes the first two, and `lists.drop(words, 3)` drops the first three.
- These are the functions that need no function value. `map`, `filter`, `sort_by` and `find` take one, so they wait for chapter 15.

## Summary

- `[1, 2, 3]` is a list literal, a `MutableList[Int]` by default; `let xs: List[Int] = [...]` is read-only.
- Indexes start at 0; going out of bounds is the run-time error `SPR-RUNTIME-EXCEPTION`, not a compile error.
- An empty literal `[]` must write its element type, or the compiler reports `SPR-TYPE-INFER`.
- A `MutableList` goes where a `List` is expected as the same data seen read-only; `toList()` is a read-only snapshot; `toMutableList()` copies a read-only list and returns a mutable list itself unchanged.
- Mutating methods only exist on `MutableList`; calling one on a `List` is `SPR-COLLECTION-IMMUTABLE`.
- `==` compares contents in order; `sort()` sorts in place, ascending.
- `@std/lists.spr` adds `sorted`, `reversed`, `distinct`, `index_of`, `sum`, `take`, `drop` and more; the functions that need function values wait for chapter 15.

## Exercises

**Exercise 1 (warm-up)** Add 10 to every element of `[3, 1, 4, 1, 5]`, collect the results in a new list, and print it.

Hint: start with `let out: MutableList[Int] = []`, loop with `for n in numbers:`, and `out.append(n + 10)` inside.

::: details Answer
<<< @/snippets/book/ch08_ex1_answer.spr

```text
[13, 11, 14, 11, 15]
```
:::

**Exercise 2** Write a function `largest(xs: List[Int]) -> Int` that returns the largest element. Assume the list is not empty.

Hint: start with `xs[0]` as the running maximum; replace it whenever the loop sees something larger.

::: details Answer
<<< @/snippets/book/ch08_ex2_answer.spr

```text
9
```
:::

**Exercise 3** Given the names `["Ada", "Bob", "Cyd"]`, print a numbered list: `1. Ada`, `2. Bob`, `3. Cyd`.

Hint: `for i in range(0, names.size())`; the number is `i + 1`; call `.toString()` before joining.

::: details Answer
<<< @/snippets/book/ch08_ex3_answer.spr

```text
1. Ada
2. Bob
3. Cyd
```
:::

**Exercise 4** Use `@std/lists` on `[3, 1, 3, 2, 1]`: print the de-duplicated list and the reversed list.

Hint: `import "@std/lists.spr" as lists`, then `lists.distinct(raw)` and `lists.reversed(raw)`.

::: details Answer
<<< @/snippets/book/ch08_ex4_answer.spr

```text
[3, 1, 2]
[1, 2, 3, 1, 3]
```
:::

The next chapter widens the single column of a list into lookups by key, plus a collection that forbids duplicates: [Maps and sets](/en/tutorial/ch09-maps-sets).
