# The Sprig Programming Language

Welcome. This book teaches Sprig from scratch: a small language that runs on the JVM, looks like Python, and — when you get something wrong — tells you which line is wrong, why, and how to fix it.

Every program in the book has really run, and the output under a program is what it printed. Every "Deliberate mistake" has really been rejected by the compiler, with the error text copied as it is. You do not need to have programmed before, and you do not need to know Java.

## What kind of language Sprig is

Start with one complete program:

<<< @/snippets/book/ch09_errors.spr

```text
42
failed: not a number: abc
done
```

Those dozen lines already show several of Sprig's core ideas:

- **Indentation is structure.** No braces, no semicolons, one thing per line.
- **Types on signatures, inference in locals.** Function parameters and results always name a type; `let value = ...` inside a function body does not.
- **Failure goes into the type.** `throws Error` is part of the signature, and a caller either handles the error or declares it as well. Nothing escapes silently.
- **So does absence.** A value that may be missing says so in its type, and you need a `== null` check before you can use it as an ordinary value.
- **No implicit conversions, no "nonzero is true", no fallback branch, and no inheritance.** You will meet each of these in the chapters.

This program uses functions, `Error`, and `try`/`catch`/`finally`. It is fine if none of that makes sense yet: Chapter 7 covers functions and Chapter 14 covers handling errors.

## Who this book is for

It is written for people who have never programmed, and for people coming from another language.

- If this is your first language: read in order. In each chapter, first look at the examples, then at the output, then do the exercises. The answers are folded away, so try before you peek.
- If you know Python, JavaScript or Java: the first chapters can go quickly, but Chapter 3 (numbers) and Chapter 13 (values that may be missing) cover where Sprig differs from other languages, so do not skip them. [Appendix D](/en/tutorial/appendix-d-from-other-languages) maps your old habits onto Sprig one by one, to consult as you read.

## How to read this book

There are 24 chapters in three parts, with appendices after them.

Each chapter starts with what you will learn and ends with a summary and exercises. The book's conventions:

- A `text` block directly under a code block is the result of running it.
- In a "Deliberate mistake" section the code does **not** compile, and the `text` block under it is the compiler's real error. The `SPR-...` at the start is the error code; `sprig explain` followed by that code gives the full explanation.
- Every complete program in the book is a real `.spr` file in the repository, and you can run it with `sprig run <file>`.
- The errors come from the compiler, not from a human. Another version may word them differently, but the codes do not change.

## Which compiler version you need

This book follows the 0.8 language, the compiler on the repository's `main` branch. `sprig capabilities` reports it as `language 0.8-dev`.

The published SDK is v0.7.1-beta.1, which is older than this book: if expressions, one-line classes, contract classes, error classes, function references, type-argument inference and `@std/concurrent` all arrived after v0.7.1-beta.1. Until 0.8 is released, [build from source](/en/tutorial/ch01-tools) and use the repository's `bin/sprig` (`bin\sprig.cmd` on Windows). Chapter 1 walks through the environment, the version check and your first program.

## Chapter map

### Part 1: Writing programs from scratch

| Chapter | What you will learn |
|---|---|
| [1. Getting set up](/en/tutorial/ch01-tools) | the terminal, the JDK, the compiler, an editor, and how to check the version |
| [2. Your first program, and reading errors](/en/tutorial/ch02-first-program) | `print`, comments, the four parts of an error, `sprig explain` |
| [3. Values, variables and arithmetic](/en/tutorial/ch03-values) | `let`/`var`, types, the rules for integers and floating-point values, `Bool` |
| [4. Text](/en/tutorial/ch04-text) | escapes, common string methods, code points, `@std/text` |
| [5. Making decisions: if](/en/tutorial/ch05-if) | conditions, `elif`, if expressions |
| [6. Repeating: loops](/en/tutorial/ch06-loops) | `while`, `for`, `range`, `break`/`continue` |
| [7. Functions](/en/tutorial/ch07-functions) | parameters, results, scope, recursion, naming |
| [8. Lists](/en/tutorial/ch08-lists) | `List` and `MutableList`, common methods, sorting |
| [9. Maps and sets](/en/tutorial/ch09-maps-sets) | `Map`, counting patterns, `@std/sets` |
| [10. Project: guess the number](/en/tutorial/ch10-project-guess) | reading input, command-line arguments, random numbers |

### Part 2: Describing the world with types

| Chapter | What you will learn |
|---|---|
| [11. Classes and objects](/en/tutorial/ch11-classes) | fields, methods, one-line classes, identity |
| [12. Enums, variants and match](/en/tutorial/ch12-enums-variants) | closed sets of types, exhaustive matching, recursive variants |
| [13. Values that may be missing](/en/tutorial/ch13-nullable) | `T?`, narrowing, `or_else` |
| [14. Handling errors](/en/tutorial/ch14-errors) | `throws`, error classes, rethrowing, `finally` |
| [15. Functions as values](/en/tutorial/ch15-functions-as-values) | lambdas, function references, capture, function types that throw |
| [16. Generics and contract classes](/en/tutorial/ch16-generics-contracts) | `generic`, `requires`, contract classes and `conform` |
| [17. More about numbers](/en/tutorial/ch17-numbers) | `Int32`/`Float32`, the conversion table, `Decimal`/`BigInt`, `@std/math` |

### Part 3: Building real programs

| Chapter | What you will learn |
|---|---|
| [18. Modules, projects and dependencies](/en/tutorial/ch18-modules-projects) | `import`, `sprig.toml`, lockfiles, dependencies |
| [19. Testing](/en/tutorial/ch19-testing) | `@std/test`, `sprig test`, compile-fail tests |
| [20. The standard library at work](/en/tutorial/ch20-stdlib) | files, JSON, date and time, regular expressions |
| [21. Calling Java](/en/tutorial/ch21-java) | importing Java classes, nullable results, collection adapters |
| [22. Concurrency](/en/tutorial/ch22-concurrency) | virtual threads, tasks, channels, locks |
| [23. Tools and AI assistants](/en/tutorial/ch23-tooling) | the language server, `fmt`, structured queries, writing with an assistant |
| [24. Project: an expense tracker](/en/tutorial/ch24-project-ledger) | the whole book in one complete program |

### Appendices

| Appendix | What is in it |
|---|---|
| [A. Operators and precedence](/en/tutorial/appendix-a-operators) | every operator, the precedence table, associativity |
| [B. Error codes and where to read about them](/en/tutorial/appendix-b-error-codes) | the stable `SPR-` codes, grouped |
| [C. Keywords and built-in functions](/en/tutorial/appendix-c-keywords) | keywords, built-in functions, built-in methods |
| [D. Coming from Python, JavaScript or Java](/en/tutorial/appendix-d-from-other-languages) | old habits mapped onto Sprig |
| [E. What Sprig leaves out](/en/tutorial/appendix-e-not-in-sprig) | deliberate omissions and what to write instead |
| [F. Java interop in depth](/en/tutorial/appendix-f-advanced-java) | wildcards, annotations, parent classes, `wrap` |

## What this book does not cover

It is not the language specification, and not a complete API list. For the syntax line by line there is the [language quick reference](/en/guide/language-tour); for the precise contracts of numbers, nullability and generics there is the [reference](/en/reference/language/feature-status). Fabric mods, Gradle integration, web and SQLite each have their own guide.

Ready? Start with [Chapter 1: Getting set up](/en/tutorial/ch01-tools). Then go in order and do not skip chapters: each one only uses what earlier chapters taught.
