# 6. Repeating: loops

The temperature program at the end of the last chapter copied the same `if` three times just to change one number. Repetition is what computers are best at: give them a rule and let them turn the crank. This chapter has two kinds of crank: `while` and `for`.

In this chapter you will learn:

- to repeat while a condition holds, with `while`;
- to follow a loop with a variable trace table;
- to repeat a counted number of times with `for` and `range`;
- to walk through the pieces of a string or a list with `for`;
- to leave a loop early with `break`, skip to the next round with `continue`, spin with `while true`, and leave a branch empty with `pass`;
- to combine all of it into FizzBuzz.

## 6.1 while: repeat while the condition holds

<<< @/snippets/book/ch06_while.spr

```text
3
2
1
liftoff
```

`while condition:` looks like `if`, except that it does not look only once:

1. Check `count > 0`. `count` is 3, true, so run the indented block.
2. The block prints `3`, then subtracts 1 from `count`, which becomes 2.
3. Go back to step 1 and check again. This prints `2` and `1`; `count` becomes 0.
4. The next check is `0 > 0`, false, so the loop is left.
5. `print("liftoff")` is not indented, so it is outside the loop and runs after it.

**The loop stops because the block contains a line that eventually makes the condition false** — here `count -= 1`. Without it, `count` would stay 3 and the program would print forever and never stop; press `Ctrl+C` in the terminal to force it to end.

## 6.2 A variable trace table

Loops are hard to read by eye, and the steadiest way is to draw a table and follow it step by step. This program adds 1 through 5:

<<< @/snippets/book/ch06_while_sum.spr

```text
15
```

The trace, one row per round:

| Round | `n` when checked | `n <= 5` | `total` after adding | `n` after adding |
|---|---|---|---|---|
| before the loop | 1 | — | 0 | 1 |
| 1 | 1 | true | 1 | 2 |
| 2 | 2 | true | 3 | 3 |
| 3 | 3 | true | 6 | 4 |
| 4 | 4 | true | 10 | 5 |
| 5 | 5 | true | 15 | 6 |
| after | 6 | false | 15 | 6 |

`total` ends at 15: 1+2+3+4+5. When a loop of your own gets stuck, take a sheet of paper and write down "the values in the condition, the values in the key variables" for each round, just like this.

## 6.3 Deliberate mistake: a while condition that is not a Bool

<<< @/snippets/book/ch06_while_nonbool.spr

```text
SPR-TYPE-CONDITION [TYPE] main.spr:2:7: Condition must be Bool (no truthiness) (expected Bool, actual Int)
```

Chapter 3 said it: Sprig has no "non-zero is true". Here `n` is an `Int`, so the compiler asks for a real comparison: `while n > 0:`. The same rule applies to `if`.

## 6.4 for and range: repeating a count

When you know what to count and where to stop, `for` is less work:

<<< @/snippets/book/ch06_range.spr

```text
0
1
2
--
2
3
4
5
--
0
3
6
9
--
5
3
1
```

`for variable in a sequence of values:` takes those values one at a time and runs the block once for each. `range` produces integers:

- `range(3)`: starts at 0 and stops before 3, giving 0, 1, 2. The stop value is **excluded**.
- `range(2, 6)`: from 2 up to but not including 6, giving 2, 3, 4, 5.
- `range(0, 10, 3)`: the third argument is the step; it adds 3 each time, giving 0, 3, 6, 9.
- `range(5, 0, -2)`: the step may be negative; counting down from 5 gives 5, 3, 1.

`range` takes different numbers of arguments: one means "from 0 up to this number", two mean "start, stop", and three add the step.

## 6.5 Deliberate mistake: the loop variable cannot change

The loop variable is replaced by the next value each round, so it behaves like a `let`:

<<< @/snippets/book/ch06_for_assign.spr

```text
SPR-NAME-LET-ASSIGN [TYPE] main.spr:2:5: Cannot assign to immutable binding 'n'; declare it with var
```

When you want a value that is computed from the loop variable and changes each round, declare a `var` inside the block:

<<< @/snippets/book/ch06_for_var.spr

```text
1
3
5
```

`n` takes 0, 1 and 2; `doubled` is declared fresh each round and comes out as `1`, `3` and `5`. The loop variable does not live outside the loop either. A `print(n)` after the loop cannot find `n`, and the compiler reports `SPR-NAME-UNRESOLVED`.

## 6.6 Walking through strings and lists

`for` is not only for counting; the "sequence of values" on its right can be anything that can be taken one piece at a time. A string, met in chapter 4, is a sequence of characters:

<<< @/snippets/book/ch06_for_string.spr

```text
S
p
r
i
g
```

Each round `ch` receives one character, as a `String` holding exactly one character (counted in code points, as chapter 4 said), and it is immutable inside the loop just like the counting variable.

Square brackets wrap a list literal, which chapter 8 covers in full; `for` treats it the same way as a string:

<<< @/snippets/book/ch06_for_list.spr

```text
tea
milk
rice
```

## 6.7 break and continue

- `continue`: abandon this round and start the next one.
- `break`: stop the whole loop; no later round runs.

<<< @/snippets/book/ch06_break_continue.spr

```text
1
3
5
7
```

Round by round:

| `n` | What happens |
|---|---|
| 1 | odd; `1 > 7` is false; print `1` |
| 2 | even, `continue`, back to the top of the loop |
| 3 | print `3` |
| 4 | `continue` |
| 5 | print `5` |
| 6 | `continue` |
| 7 | print `7` |
| 8 | even, so `continue` runs before the `break` check is ever reached |
| 9 | odd; `9 > 7` is true, `break`, the loop ends |

Notice the order: round 8 is caught by `continue` before the `break` check. Do not make the two exits of a loop too tangled.

## 6.8 Deliberate mistake: break outside a loop

<<< @/snippets/book/ch06_break_outside.spr

```text
SPR-FLOW-BREAK [FLOW] main.spr:1:1: break outside a loop
```

`break` and `continue` may only live inside a loop body. The compiler performs flow analysis (the category is `FLOW`) and sees at once that this line has no loop to leave. A `continue` outside a loop reports `SPR-FLOW-CONTINUE`, by the same rule.

## 6.9 while true and pass

Some loops do not have a tidy condition to put at the top, such as "find the first number divisible by 7": let `while true:` spin, and `break` when the search succeeds.

`pass` is the statement that does nothing. A branch's block must contain at least one line, so when there is really nothing to do, `pass` fills the spot:

<<< @/snippets/book/ch06_while_true_pass.spr

```text
7
0
1
3
4
```

The first half: `answer` grows from 0 by 1 each round; at 7, `7 % 7 == 0` holds, so it breaks and prints `7`. With `while true`, always make sure a `break` is somewhere inside, or it really will spin forever.

The second half: `range(5)` yields 0 through 4. When `n == 2` there is nothing to do, but the block cannot be empty, so `pass` holds its place; the other values are printed by the `else` block, which is why 2 is missing from the output.

## 6.10 Putting it together: FizzBuzz

Combine `for`, `if`, `elif` and `%` into a classic: count from 1 to 15; print `Fizz` for multiples of 3, `Buzz` for multiples of 5, `FizzBuzz` for multiples of both, and the number itself otherwise.

<<< @/snippets/book/ch06_fizzbuzz.spr

```text
1
2
Fizz
4
Buzz
Fizz
7
8
Fizz
Buzz
11
Fizz
13
14
FizzBuzz
```

A number divisible by both 3 and 5 is also divisible by each of them, so `n % 15 == 0` **must come first**; otherwise 15 is caught by `n % 3 == 0` and printed as `Fizz`. Branches are checked from top to bottom and the first match wins — the rule from the last chapter.

## Summary

- `while condition:` checks once at the top of every round; the block must eventually make the condition false.
- Read a loop with a trace table: list the condition and the key variables for each round.
- A condition must be a `Bool`; there is no "non-zero is true".
- `for x in a sequence:` takes one value at a time; all three forms of `range` exclude the stop value, and the step may be negative.
- The loop variable behaves like a `let`, cannot be assigned, and does not exist after the loop.
- `continue` starts the next round, `break` ends the whole loop, and both may only appear inside a loop.
- `while true` pairs with `break`; `pass` is the empty statement.

## Exercises

### Exercise 1: a table of squares

Print the squares of 1 through 10 with `for` and `range`, one per line.

Hint: `for n in range(1, 11):` and print `n * n` in the block.

::: details Answer

<<< @/snippets/book/ch06_ex_squares.spr

```text
1
4
9
16
25
36
49
64
81
100
```

:::

### Exercise 2: factorial with while

Compute the factorial of 5 (5×4×3×2×1) with `while` and print the result.

Hint: start with `var n = 5` and `var result = 1`; each round do `result *= n`, then `n -= 1`, while `n > 1`.

::: details Answer

<<< @/snippets/book/ch06_ex_factorial.spr

```text
120
```

:::

### Exercise 3: countdown

Count from 5 down to 1 with `range`, one per line, then print `liftoff`.

Hint: the step is `-1`; the start is 5 and the stop is 0 (because the stop is excluded).

::: details Answer

<<< @/snippets/book/ch06_ex_countdown.spr

```text
5
4
3
2
1
liftoff
```

:::

### Exercise 4: counting vowels

How many vowels (`a`, `e`, `i`, `o`, `u`) are in `"banana"`? Print the count.

Hint: `var vowels = 0`, `for ch in "banana":`, and join all the vowels into one condition with `or`.

::: details Answer

<<< @/snippets/book/ch06_ex_vowels.spr

```text
3
```

:::

### Exercise 5: the first square above 200

Counting up from 1, which number's square is the first to exceed 200? Stop as soon as you find it with `break` and print the number.

Hint: keep the answer in `var found = -1`; in the loop, test `n * n > 200`, and when it holds store `n` in `found` and `break`.

::: details Answer

<<< @/snippets/book/ch06_ex_first_square.spr

```text
15
```

:::

::: tip Coming from another language?

- Python: `while`, `for ... in`, `range`, `break` and `continue` look and behave almost the same, and `range` excludes the stop value there too.
- Java / JavaScript / C: there is no `i++` or `++i` — write `i += 1`; there is no C-style `for (i = 0; i < n; i++)`, so counting uses `range`; there is no `do ... while`; and no `for(;;)`, so write `while true`.
- The loop variable is gone after the loop, unlike Python: here it is created fresh in each round.

:::

Next chapter: [Chapter 7: Functions](/en/tutorial/ch07-functions).
