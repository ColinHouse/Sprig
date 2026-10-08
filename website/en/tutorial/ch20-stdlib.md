# 20. The standard library at work

A program does more than arithmetic on its own data: it reads configuration, saves results, parses the JSON somebody sent, measures the gap between two dates, finds parts of a text that match a pattern. You do not write that yourself — the **standard library** shipped with the compiler has it.

The standard library is a set of modules whose names start with `@std/`, imported like any other: `import "@std/files.spr" as files`. You have already used some of them: `@std/text` (chapter 4), `@std/lists` (chapter 8), `@std/sets` (chapter 9), `@std/process` and `@std/random` (chapter 10), `@std/nulls` (chapter 13), `@std/math` (chapter 17), `@std/test` (chapter 19).

This chapter fills in four groups that real programs cannot do without: files, JSON, dates and time, and regular expressions.

In this chapter you will learn:

- `@std/files`: reading, writing, atomic replacement, listing directories, temporary files; every failure throws an `Error`;
- `@std/json` and `@std/json_codec`: parsing and producing JSON, reading fields by name and type, with paths in the error messages;
- `@std/dates` and `@std/time`: a date is ISO text, a timestamp is milliseconds;
- `@std/regex`: finding, replacing, whole-text matching, capture groups and splitting.

## 20.1 Files: @std/files

Why you need it: variables and lists live in memory and disappear when the program ends. To keep a result, or to read somebody else's data, you write and read files.

The smallest example does the whole round trip on a temporary file:

<<< @/snippets/book_en/ch20_files.spr

```text
true
3
first
second
false
```

Line by line:

- `files.temp_file()` creates a new empty file in the operating system's temporary directory and returns its absolute path. It is **not** removed automatically; call `remove_file` when you are done.
- `files.write_utf8(path, "first draft\n")` writes text as UTF-8, replacing the content in place when the file exists — so halfway through, another program can read a half-written file.
- `files.atomic_write_utf8(path, "first\nsecond\n")` writes too, but through a temporary sibling renamed over the target. A reader sees either the old content or the new content, never half of each. Use it when "another program may read this at any moment" matters.
- `files.exists(path)` prints `true`: the file is there.
- `files.read_lines(path)` reads it back line by line and returns a `List[String]` without the line endings. `"first\nsecond\n"` becomes `["first", "second", ""]`: the final newline leaves an empty line, so `.size()` is `3`.
- `files.read_utf8(path).trim()` reads the whole file as one string; `trim()` drops the trailing newline and prints `first` then `second`.
- `files.remove_file(path)` deletes the file; asking `exists` once more gives `false`.

Failure is normal for file operations — the path is missing, the permission is denied, the disk is full. The operations that can fail (reading, writing, deleting, creating, listing) all say `throws Error` in their signatures and throw an `Error` whose message is `cannot ACTION FILE: REASON`:

<<< @/snippets/book_en/ch20_files_error.spr

```text
cannot read no-such-file.txt: no such file
```

Catch it with the `try`/`catch` of chapter 14; `problem.message` is that line. Reading a missing file does not hand you an empty string or a `null` — "cannot read it" must be handled explicitly.

Other useful functions: `is_file` / `is_directory` say what a path is; `make_directory` creates a directory (and missing parents); `copy_file` and `move` copy and move (neither overwrites); `file_name` / `parent` / `join` / `normalize` / `absolute` work on path text; `list` and `walk` list directories; `temp_file` makes a temporary file. Note that `files.parent(...)` may have no parent and returns `String?`.

### Listing a directory

To see what a directory contains, use `list`; to recurse through a whole tree, use `walk`:

<<< @/snippets/book_en/ch20_files_list.spr

```text
2
a.txt
notes.txt
0
```

Line by line:

- `files.temp_file()` first gives us a temporary file; `files.parent(anchor)` takes the directory it sits in. `parent` has type `String?`, so `if parent == null: throw` narrows `parent` to `String` for what follows (chapter 13).
- `files.join(parent, "sprig-book-" + files.file_name(anchor))` pastes directory and name together with path rules. The directory name carries the temporary file's name, so every run gets a fresh one. `files.remove_file(anchor)` deletes the little file we only used for its name.
- `files.make_directory(dir)` creates the directory, and then we write `a.txt` and `notes.txt` into it.
- `files.list(dir)` returns a **sorted** snapshot whose entries are absolute paths. Printing absolute paths would expose directory names from your machine, so we print only the last segment with `file_name`: `a.txt` and `notes.txt`.
- `files.walk(dir)` lists the absolute paths of every **file** below the directory, recursively. This directory has two files, so it is those two lines again; `walk` does not list the directory itself.
- At the end we delete both files and count `list` again: `0`.

The directory itself remains — `@std/files` removes files, not directories, and this one lives in the operating system's temporary area, which the system cleans up.

### Deliberate mistake: forgetting throws

`read_lines` throws, and a function that calls it must admit that in its signature, or the compiler refuses:

<<< @/snippets/book_en/ch20_files_throws.spr

```text
SPR-FLOW-THROWS [FLOW] main.spr:5:12: Call may throw Error; declare 'throws Error' or handle it with try/catch
```

`SPR-FLOW-THROWS`: line 5, column 12 calls something that may throw, while `first_line` neither declares `throws Error` nor wraps the call in `try`. There are two fixes: add `throws Error` to `first_line`'s signature (its callers then handle or declare it in turn), or catch it at the call site with `try`/`catch`.

::: tip Coming from another language?

- There is no file handle to open and close: these functions read or write a whole file at once, and a path is just a `String`.
- Failure is neither a return value (C's `-1`, Go's `err`) nor an exception that quietly travels: it throws an `Error` that you must handle or declare.
- The path functions are pure text: `join` and `normalize` never touch the disk or resolve symbolic links.

:::

## 20.2 JSON: @std/json and @std/json_codec

JSON is the most common text format for programs to exchange data. The standard library splits it in two: `@std/json` handles "JSON text ↔ JSON value", and `@std/json_codec` handles "read a field out of a JSON value by name and type".

<<< @/snippets/book_en/ch20_json.spr

```text
sprig
12
[small, typed]
null
{"name":"sprig","stars":12,"tags":["small","typed"]}
```

Line by line:

- `json.parse(doc)` turns the text into a `json.Value`. Every kind of JSON has a case: `Null`, `Boolean`, `Number`, `Text`, `Array`, `Object`. A number keeps its **exact original text** (`Number(text: String)`) instead of becoming a `Float` that may lose precision; a duplicate key in an object is rejected rather than letting the last one win.
- `codec.root(...)` requires the document itself to be an object and returns a reader carrying a **path**, whose root is `$`. For an array document use `codec.root_array`; element paths are `$[0]`, `$[1]`, and so on.
- `codec.required_string(root, "name")` reads `name` and requires a string: it prints `sprig`. `required_int(root, "stars")` reads the integer and prints `12`.
- `codec.required_string_array(root, "tags")` reads an array of strings; a printed list looks like `[small, typed]`.
- `codec.optional_string(root, "license")` reads a field that may be absent: absent (or JSON `null`) gives the `null` of `String?`, printed here as `null`.
- `json.stringify(json.parse(doc))` writes the value back as **compact** JSON with no extra spaces; `stringify` also rejects duplicate keys in a hand-built object.

When a field is missing, `null`, or of the wrong kind, `required_*` throws an `Error` whose message carries the field's path in the document:

<<< @/snippets/book_en/ch20_json_error.spr

```text
$.stars: expected integer, found string
```

`$.stars` is the path; `expected integer, found string` names both sides. Notice that "integer" is stricter than "number": JSON's `12.0` and `1e3` are numbers but not integers, and `required_int` rejects them. Use `required_number_text` (the original text) or `required_decimal` (an exact decimal) for those.

The field readers come in pairs: `required_*` needs the field present and of the right kind; `optional_*` allows absence (its result is `T?`); `as_*` is for values you already hold, such as array elements. To reject unexpected fields — handy when reading configuration — use `reject_unknown_fields(root, ["name", "stars"])`.

### Deliberate mistake: an optional result is not an ordinary value

`optional_int` returns `null` when the field is missing, so its type is `Int?` and it cannot be assigned to `Int` directly:

<<< @/snippets/book_en/ch20_json_optional.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:6:18: Nullable value is not assignable to Int (initializer); check for null first (expected Int, actual Int?)
```

The fix is the one from chapter 13: check for `null` first, or use `nulls.or_else` / `nulls.require`. Do not switch to `required_int` just to make it compile — "may be absent" and "must be there" mean different things, and that is exactly how readers tell them apart.

::: tip Coming from another language?

- Numbers never pass through a `Double`: `Value.Number` keeps the original text and `required_decimal` hands you an exact `Decimal`. That is built for money.
- Reading a field is not "get an `Any` and cast": `required_int` guarantees an `Int` result at the type level, and throws otherwise.
- For JSON of arbitrary shape, `match` on the cases of `json.Value` (chapter 12); for a fixed shape, use the codec.
- To build JSON, compose with `codec.text` / `int` / `number` / `bool` / `member` / `object` / `array` and call `json.stringify`; never concatenate strings.

:::

## 20.3 Dates and time: @std/dates and @std/time

When something was created, how many days until it is due, which of two came first — that is dates. Sprig does not invent a date type: **a date is ISO text**, such as `2026-10-05`; a timestamp is an `Int` counting milliseconds since 1970-01-01T00:00:00Z, which can also be written as ISO-8601 text with an offset.

<<< @/snippets/book_en/ch20_dates.spr

```text
2026-10-05
2026-10-15
14
1
false
1970-01-01T00:00:00Z
1970-01-01T00:00:01.500Z
1791450930000
```

Line by line:

- `dates.parse("2026-10-05")` checks that the text is a real calendar date in ISO form and returns it; it prints `2026-10-05`.
- `dates.plus_days(start, 10)` is 10 days later: `2026-10-15`. A negative count goes back.
- `dates.days_between(start, "2026-10-19")` is the number of days from the first date to the second: `14`. An earlier second date gives a negative number.
- `dates.day_of_week(start)`: `1` is Monday through `7` for Sunday. 2026-10-05 is a Monday.
- `dates.is_valid("2026-02-30")`: February has no 30th, so `false`. `is_valid` only answers "is this a legal date"; it does not throw.
- `time.format_utc(0)`: millisecond `0` is `1970-01-01T00:00:00Z`; `1500` is one and a half seconds later, printed as `1970-01-01T00:00:01.500Z`.
- `time.parse_utc("2026-10-08T17:15:30+08:00")` turns text with an offset into the millisecond count `1791450930000`. `+08:00` is UTC+8, so that instant is 09:15:30 in UTC.

`@std/dates` also has `today_utc()` (today) and `year` / `month` / `day`. `@std/time` also has `utc_now()` (now), `epoch_millis()` (the current millisecond count), `sleep(milliseconds)` (pause), and `monotonic_nanos()` (only for measuring elapsed time, unaffected by clock adjustments).

`parse` does not guess: an impossible date throws at **run time** instead of quietly returning some other date.

<<< @/snippets/book_en/ch20_dates_error.spr

```text
not a date: 2026-02-30
```

### Deliberate mistake: days are not text

The second argument of `plus_days` is an `Int`. Writing `"3"` for three days is rejected outright:

<<< @/snippets/book_en/ch20_dates_type.spr

```text
SPR-TYPE-MISMATCH [TYPE] main.spr:4:37: Type mismatch in argument 2 of plus_days (expected Int, actual String)
```

`SPR-TYPE-MISMATCH` says which argument, what was expected and what was given. Sprig never turns text into a number on its own; to parse user input, use `"3".toIntOrNull()` from chapter 4.

::: tip Coming from another language?

- There is no date object: dates are `String`s and the `dates` functions are pure. ISO text sorts lexicographically in chronological order, so `"2026-10-05" < "2026-10-06"` is `true`.
- `dates` is a calendar day, `time` is an instant. There is no time-zone database, no duration type, no format-pattern string.
- For heavier date arithmetic (time zones, periods, formatting), call into Java's `java.time` from chapter 21 onward.

:::

## 20.4 Regular expressions: @std/regex

Texts often need "find the part that looks like a phone number", "replace every number with `#`", "check that the whole string has the right shape". A regular expression is a mini-language for describing such patterns.

<<< @/snippets/book_en/ch20_regex.spr

```text
[a1, b22, c333]
a# b# c#
true
[a, 1]
[a1, b22, c333]
true
```

Line by line:

- `regex.find_all("[a-z][0-9]+", text)` finds "a lowercase letter followed by one or more digits" and returns every match in order: `[a1, b22, c333]`.
- `regex.replace_all("[0-9]+", text, "#")` replaces each run of digits with `#`, giving `a# b# c#`.
- `regex.matches(...)` asks whether the **whole** text matches the pattern, so it is `true`. Unlike `find_all`, it must match from beginning to end.
- `regex.find_groups("([a-z])([0-9]+)", text)` uses parentheses as **capture groups** and returns the groups of the first match: `[a, 1]`. When nothing matches it returns `null`, and a group that took no part is `null`. `find_all_groups` gives the groups of every match.
- `regex.split("\\s+", text)` splits on whitespace: `[a1, b22, c333]`. In the pattern, `\\s` is string escaping (chapter 4); the regex engine receives `\s`.
- `regex.find("z+", text)` looks for "one or more z"; there are none, so it returns `null`, and `== null` prints `true`.

### Deliberate mistake: find's result is not a String yet

<<< @/snippets/book_en/ch20_regex_null.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:4:21: Nullable value is not assignable to String (initializer); check for null first (expected String, actual String?)
```

`SPR-TYPE-NULLABLE` appears again: `find` may come back empty-handed, so its result is `String?`. Either check for `null` first, or use `find_all` — when it finds nothing it is an empty list, not `null`.

::: tip Coming from another language?

- The pattern syntax is Java's `java.util.regex`: `\d`, `\w`, `(?<name>...)` and the quantifiers are all the same.
- "No match" is neither an exception nor an empty string: it is `String?`, and the compiler makes you deal with it.
- Every call compiles the pattern again; think before putting one in a loop. To reuse a compiled pattern, use Java's `Pattern` (chapter 21).

:::

## Summary

- The standard library is imported as `@std/name.spr` and needs no dependency declaration.
- `@std/files`: `read_utf8` / `read_lines` to read, `write_utf8` to write, `atomic_write_utf8` to replace atomically, `list` / `walk` to list directories, `temp_file` for temporary files; failures throw `Error`, and functions that call them must declare `throws`.
- `@std/json` moves between text and values; `@std/json_codec`'s `required_*` / `optional_*` read fields by type with `$` paths in errors; `optional_*` results are `T?`.
- A date is ISO text and a timestamp is milliseconds; `dates.parse` / `plus_days` / `days_between` / `is_valid` / `day_of_week`, `time.format_utc` / `parse_utc` / `utc_now` / `sleep`.
- `@std/regex`: `find` (`String?`), `find_all`, `replace_all`, `matches` (whole text), `find_groups`, `split`.

## Exercises

### Exercise 1: find every number

From the text `"I have 3 cats, 12 dogs and 1 fish"`, find every number and print the list plus how many there are.

Hint: `[0-9]+` means "one or more digits"; `find_all` returns a `List[String]`, and a list has `.size()`.

::: details Answer

<<< @/snippets/book_en/ch20_ex1.spr

```text
[3, 12, 1]
3
```

:::

### Exercise 2: sum the amounts in a JSON array

This JSON is an array whose rows each have an `amount`. Add up all the `amount`s and print the total.

```text
[{"amount": 12}, {"amount": 30}, {"amount": 5}]
```

Hint: `codec.root_array(...)` gives one reader per element, and `codec.required_int(row, "amount")` reads the integer.

::: details Answer

<<< @/snippets/book_en/ch20_ex2.spr

```text
47
```

:::

### Exercise 3: date arithmetic

Starting from `"2026-10-08"`, print the date 30 days later and how many days remain until the end of the year (`"2026-12-31"`).

Hint: `plus_days(date, days)` and `days_between(start, end)` both take ISO text directly.

::: details Answer

<<< @/snippets/book_en/ch20_ex3.spr

```text
2026-11-07
84
```

:::

### Exercise 4: read back a file with a blank line

Write `"one\n\ntwo\n"` to a temporary file, read it back line by line, print the line count, and check whether the second line is blank. Delete the file at the end.

Hint: in `write_utf8`, `\n` is a newline; the elements from `read_lines` do not carry their line endings. Remember that the final newline produces an empty line too.

::: details Answer

<<< @/snippets/book_en/ch20_ex4.spr

```text
4
true
```

:::

Next chapter: [Calling Java](/en/tutorial/ch21-java).
