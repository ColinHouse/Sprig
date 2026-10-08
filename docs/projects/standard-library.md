# Small practical standard layer

The SDK supplies a reserved bundled package. Use the existing explicit package
import syntax from any directory, without a manifest dependency or a copied std:

```sprig
import "@std/files.spr" as files
import "@std/json.spr" as json
```

`@std` belongs to the installed SDK; dependency aliases cannot override it.
Only the bundled flat `.spr` modules are exported; traversal and symlinks are
rejected. There are no implicit imports or new grammar forms. Standalone files
use their installed SDK directly. `@std` is part of that SDK and is not a
manifest dependency or an independently pinned project-lock entry. Schema-5
locks contain neither `stdlib-version` nor `stdlib-sha256`; changing installed
std bytes alone does not stale a project lock. Consumers do check the recorded
compiler version, so moving to a different compiler version requires explicit
`sprig resolve`. There is no independent stdlib version selection or
compatibility promise. Release ZIP checksums and archive smoke tests validate
the published SDK distribution; a project lock does not attest the installed
SDK's exact bytes. See the
[published release validation record](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/validation.md).

`sprig api @std/text.spr --json` lists what a bundled module declares, with
signatures and the comment above each declaration (`doc`). It works from any
directory, with or without a project.

| Module | Public operations |
|---|---|
| `files` | `read_utf8`, `read_lines`, `write_utf8`, `walk`, `exists`, `is_file`, `is_directory`, `list`, `make_directory`, `join`, `normalize`, `file_name`, `parent`, `absolute`, `copy_file`, `move`, `remove_file`, `atomic_write_utf8`, `temp_file` |
| `process` | `arguments() -> List[String]`, bounds-checked `argument(Int)`, `environment(String) -> String?`; `exit(Int)`, `print_error(String)`; `run(List[String]) -> ProcessResult throws Error`; `read_line() -> String?`, `read_lines() -> List[String]`, `read_all() -> String` |
| `text` | `join`, `lines`, literal `split`, `trim`, `starts_with`, `ends_with`, `strip_prefix`, `strip_suffix`, `pad_left`, `pad_right`, `is_ascii_digit`, `is_ascii_letter`, `escape_html`; `fixed(Float, Int) -> String` |
| `math` | `abs`, `min`, `max`, `sign`; `clamp`, `floor_div` and `isqrt` declare checked `Error` for invalid arguments |
| `lists` | `sorted`, `sort_by`, `group_by` returning `List[Group[K, T]]`, `fold`, `find`, `any`, `all`, `count`, `sum`, `sum_by`, `max`, `min`, `max_by`, `min_by`; `first`, `last`, `take`, `drop`, `reversed`, `distinct`, `index_of`, `enumerate` returning `List[Indexed[T]]`, `zip` returning `List[Pair[A, B]]` |
| `sets` | `Set[T]` with `add`, `has`, `remove`, `size`, `to_list`; `of`, `union`, `intersection`, `difference` |
| `random` | `seeded(Int)`/`fresh()` giving a `Random` with `next_int(bound)`, `next_float`, `next_bool`; `shuffled`, `choice`, `uuid` |
| `regex` | `matches`, `find -> String?`, `find_all`, `find_groups -> List[String?]?`, `find_all_groups`, `replace_all`, `split`, all `throws Error` for an invalid pattern |
| `dates` | ISO dates as text: `today_utc`, `parse`, `is_valid`, `plus_days`, `days_between`, `day_of_week`, `year`, `month`, `day` |
| `nulls` | `or_else[T](T?, T) -> T`, `require[T](T?, String) -> T throws Error` |
| `time` | `epoch_millis() -> Int`, `utc_now() -> String`, `format_utc(Int) -> String`, `parse_utc(String) -> Int`; `sleep(Int)`, `monotonic_nanos() -> Int` |
| `json_codec` | typed field access over `json`: `root`, `root_array`, `required_*`, `optional_*`, `field`, `reject_unknown_fields`; builders `object`, `member`, `array`, `text`, `int`, `bool` |
| `json` | `parse(String) -> Value`, `stringify(Value) -> String`, `quote(String)`, `find_member(Value, String) -> Lookup` |
| `test` | `check(name, body)`, `check_error[T](name, body)`, `finish()`; `equal_int`, `equal_bool`, `equal_text`, `equal[T]`; `temp_dir() -> String throws Error`; `run_process` is `process.run` under its earlier name |

`test` is intended for ordinary programs run through `sprig test`. Its
temporary directory helper requires the runner-provided environment. See
[testing](../tooling/testing.md) for isolation, timeout and failure behavior.

```sprig
import "@std/test.spr" as test

test.check("adds", fn() => test.equal_int(add(1, 2), 3, "sum"))
test.check("keeps order", fn() => test.equal(sorted([2, 1]), [1, 2], "sorted"))
test.check_error("rejects text", fn() => parse("x"))
test.finish()
```

- `check(name, body)` runs the body now and prints `ok name`, or
  `FAIL name: message` when the body throws `Error`; the program goes on
  to the next check. The body is a lambda, so it may call the `equal_*`
  checks and any function that throws `Error`.
- `check_error[T](name, body)` passes when the body throws `Error`. The
  body may return a value; `T` is the type of the lambda's result.
- `finish()` prints `N passed, M failed` and ends the program with status 1
  when a check failed, which `sprig test` reports as a failed program.
- `equal[T](actual, expected, what)` compares any value that supports `==`,
  lists included, and shows both values the way `print` shows them.

`process.run(command)` runs an argv vector directly (no shell), with
standard input closed and standard output and error captured as UTF-8,
and returns a `ProcessResult` with `exit_code`, `stdout` and `stderr`. A
nonzero status is an ordinary value; a missing executable or a child that
runs longer than 30 seconds is an `Error`. `test.run_process` is the same
function under its earlier name.

`equal_int(actual, expected, what)`, `equal_bool` and `equal_text` do nothing
when the two values are equal. Otherwise they throw an `Error` whose message
names both values, such as `total: expected 4, got 5`; the built-in `assert`
reports only that an assertion failed. `what` says which value was checked.
`equal_text` shows both texts quoted and escapes backslashes, double quotes,
line feeds (`\n`), carriage returns (`\r`) and tabs (`\t`), so a difference in
line endings or surrounding spaces is visible.
They are ordinary functions that declare `throws Error`.

File text always uses UTF-8. `write_utf8` replaces existing file content,
creates a file, and requires an existing parent; `make_directory` creates missing
parents. `list(path)` produces a sorted snapshot of the directory entries as
absolute normalized paths, not names relative to `path`. `is_file`, `is_directory`
and `exists` do not follow symbolic links. `join(base, child)` uses lexical path
resolution: an absolute `child` replaces `base`, so joining a directory with a
listed entry does not make that entry relative. Joining and normalizing do not
constrain paths to a sandbox. `file_name` requires a path with a filename
component. `parent` first normalizes lexically, preserves relative paths, and
returns `null` for a leaf with no parent or a filesystem root. `absolute`
returns an absolute normalized path without checking existence or resolving
symbolic links, so its value is based on the process working directory.

`copy_file` and `move` require an existing regular-file source and reject
symbolic links, directories and existing destinations; neither silently
overwrites. `remove_file` deletes one regular file and rejects directories and
symbolic links. `atomic_write_utf8` writes and closes a temporary sibling before
replacing the target. It requests an atomic same-filesystem replacement; if the
provider reports atomic moves are unsupported, it falls back to a regular
replacement move, which is not crash-atomic. Temporary files are removed after
success or failure. No API here promises fsync or crash durability. Like
`write_utf8`, `atomic_write_utf8` follows a symbolic link at the path: the
temporary file is a sibling of the file the link leads to, that file is
replaced and the link stays. A file that `write_utf8` may not write, such as a
read-only one, makes `atomic_write_utf8` fail the same way (`cannot write
out/report.txt: access denied`) before it writes anything, although the rename
alone would need only the directory's permission. A replaced file keeps its
POSIX permissions where the filesystem supports them, and a new file gets the
permissions `write_utf8` would create it with (`rw-rw-rw-` less the process
umask). Either way the file then belongs to the user who wrote it. `temp_file`
creates an empty file in the operating system temporary directory; callers can
remove it with `remove_file`.

A failed file operation is a Sprig `Error` whose message names the operation,
the path as the caller wrote it and the reason, for example
`cannot read data/notes.txt: no such file`, `cannot write out/report.txt: the
parent directory does not exist` or `cannot read data/input.bin: not valid
UTF-8`. The reason does not depend on the JDK's wording or the operating system.
Because the functions throw only `Error`, they can be called inside a lambda,
for example in `paths.map(fn(path: String) => files.read_utf8(path))`. A program
that must tell IO failures apart by Java type calls `java.nio.file.Files`
directly and catches `java.io.IOException`. Null guards and argument bounds
report Sprig `Error`; OS invalid-path failures remain JVM errors. The host is unrestricted local IO under the invoking user's
permissions.

`process.arguments()` contains only arguments after `sprig run --`; the generated
JVM main copies its argument array before module initialization. An unset
environment value is `null`, distinct from an empty value. Time is wall-clock UTC,
not a monotonic duration clock. Sprig has no `Char` type: a textual element is a
non-null `String`, and `length`, indexing, `charAt`, `codeAt`, `substring`,
`indexOf`, iteration and empty-separator `String.split` use Unicode code points,
not UTF-16 code units and not grapheme clusters. For example `"A😀東".length()`
is 3 and `"A😀東".indexOf("東")` is 2, while `"e" + String.fromCode(769)` has
length 2. Java `char` interop remains a single UTF-16 code unit at the JVM
boundary. `lines` accepts CRLF/LF and retains the final empty segment; `split` is
literal, retains empty segments, and rejects an empty separator. `trim` removes
ASCII space/tab/CR/LF, without claiming full Unicode whitespace handling.

`text.join(values, separator)` returns `""` for an empty list, the item itself
for a one-item list, and exactly one separator between neighboring items. An
empty separator concatenates items; a multi-character or Unicode separator is
preserved literally. Example:

```sprig
import "@std/text.spr" as text
print(text.join(["Sprig", "JVM", "✓"], " · "))
```

`text.pad_left(value, width, fill)` and `text.pad_right` extend a value to
`width` code points. The fill repeats as often as needed and its last
repetition is cut to fit, so `pad_left("abc", 10, "xy")` is `xyxyxyxabc`. A
value that is already long enough, or an empty fill, comes back unchanged;
neither function throws. `text.is_ascii_digit(value)` is true for a non-empty
value made only of `0`-`9`, and `text.is_ascii_letter(value)` for one made only
of `A`-`Z` and `a`-`z`. Both are false for an empty string and for every
non-ASCII digit or letter. Example:

```sprig
import "@std/text.spr" as text
print(text.pad_left("7", 3, "0"))      # 007
print(text.is_ascii_digit("2024"))     # true
```

`text.escape_html(value)` replaces the five characters HTML treats specially:
`&` by `&amp;`, `<` by `&lt;`, `>` by `&gt;`, `"` by `&quot;` and `'` by
`&#39;`. Everything else, non-ASCII text included, is unchanged. An entity
already in the value is escaped again (`&amp;` becomes `&amp;amp;`), so escape
a piece of text once, where it goes into the page. The result is safe between
tags and inside a quoted attribute value; a URL, a script or a style needs its
own checks. Example:

```sprig
import "@std/text.spr" as text
print("<h1>" + text.escape_html("Tom & Jerry's <best>") + "</h1>")
# <h1>Tom &amp; Jerry&#39;s &lt;best&gt;</h1>
```

`math` covers exact 64-bit integers only; there are no Float or Decimal
overloads. `abs`, `min`, `max` and `sign` are total functions. `abs` of the
minimum `Int` overflows and raises the same checked numeric failure as any
other overflowing operation. `clamp(value, low, high)` rejects inverted bounds,
`floor_div(value, divisor)` rejects a zero divisor and floors toward negative
infinity for either divisor sign, and `isqrt(value)` rejects negative inputs.
The three rejections are checked `Error`s, not silent zero results. Example:

```sprig
import "@std/math.spr" as math
print(math.floor_div(0 - 9, 8))  # -2, unlike / which truncates
print(math.isqrt(1000000))       # 1000
```

`time.format_utc(epoch_millis)` returns the canonical UTC representation from
`java.time.Instant`, such as `1970-01-01T00:00:00Z`. `time.parse_utc(text)`
requires an ISO-8601 instant with an explicit `Z` or numeric offset, normalizes
offsets to UTC, and returns epoch milliseconds. Sub-millisecond precision is
discarded toward the earlier millisecond. Invalid calendar dates, absent
offsets, malformed values, and values outside the `Int` epoch-millisecond range
raise Sprig `Error`. Neither operation consults the machine's local timezone;
no timezone database or locale parsing is provided.

## Sorting, grouping and searching lists

`lists` works on ordinary `List[T]` values; there is no separate dataset type.
Every function is eager, returns a new list and leaves its input unchanged. The
`[T, K]` in the signatures below names the type parameters; a call usually
leaves them out, because its arguments determine them.

- `sort_by[T, K](items, key)` requires `K: Comparable`. It is stable, and keys
  use the order of `MutableList.sort()`, so Float keys put `-0.0` before `0.0`
  and NaN last.
- `group_by[T, K](items, key)` requires `K: Equatable` and returns
  `List[Group[K, T]]`, where `Group` has `key: K` and `items: List[T]`. Groups
  appear in first-seen key order and keep input order inside each group. Keys
  match with Sprig `==`, never with Java equality or hashing: a NaN key never
  joins another group, `-0.0` joins `0.0`, and a group keeps the first key it
  saw. Matching scans the keys found so far, so grouping is quadratic in the
  number of distinct keys.
- `fold[T, A](items, initial, step)` combines items from left to right.
- `sorted[T](items)` requires `T: Comparable` and returns the items in
  ascending order. It is stable and uses the order of `MutableList.sort()`.
- `find[T](items, accept)` returns the first item the test accepts, in list
  order, or `null` when none does. The result has type `T?`.
- `any[T](items, accept)` and `all[T](items, accept)` stop at the first item
  that decides the answer. For an empty list `any` is false and `all` is true.
- `count[T](items, accept)` is the number of items the test accepts.
- `sum(values)` adds a `List[Int]`, and `sum_by[T](items, amount)` adds one
  `Int` per item. Both use checked arithmetic, so an overflow fails like any
  other `Int` overflow, and both return 0 for an empty list. Other numeric
  types use `fold`.
- `max[T](items)` and `min[T](items)` require `T: Comparable` and return the
  largest or smallest item, or `null` for an empty list, so the result has
  type `T?`. They use the order of `sorted`: for `Float`, NaN is the largest
  value and `-0.0` is below `0.0`. Of items that compare equal, such as the
  `Decimal` values 2.50 and 2.5, the first wins.
- `max_by[T, K](items, key)` and `min_by[T, K](items, key)` require
  `K: Comparable` and return the item with the largest or smallest key, or
  `null` for an empty list. Keys use the order of `sort_by`, `key` runs once
  per item, and of items with equal keys the first wins.

Every helper that takes a callable is `rethrows`: its parameter is written
`fn(T) -> K throws Error`, and a call throws exactly what the lambda you pass
throws. A lambda that only reads fields makes an ordinary call, as above. A
lambda that calls a function declaring `throws Error`, such as a parser,
makes the helper call throw `Error`, so that call needs `throws Error` on the
enclosing function or a `try`/`catch`:

```sprig
func cents(text: String) -> Int throws Error:
    let value = text.toIntOrNull()
    if value != null:
        return value
    throw Error("not a number: " + text)

func total(prices: List[String]) -> Int throws Error:
    return lists.sum_by(prices, fn(p: String) => cents(p))
```

```sprig
import "@std/lists.spr" as lists

for group in lists.group_by(orders, fn(o: Order) => o.category):
    let total = lists.sum_by(group.items, fn(o: Order) => o.cents)
    print(group.key + " " + total)

let large = lists.find(orders, fn(o: Order) => o.cents > 400)
if large != null:
    print(large.item)

let priciest = lists.max_by(orders, fn(o: Order) => o.cents)
if priciest != null:
    print(priciest.item)
```

An empty list has no largest item, so `max` pairs with `nulls.or_else` when
a default makes sense:

```sprig
import "@std/lists.spr" as lists
import "@std/nulls.spr" as nulls

func next_id(ids: List[Int]) -> Int:
    return nulls.or_else(lists.max(ids), 0) + 1
```

## Command-line programs: input, errors and exit status

`process` gives a program the three things a command-line tool needs besides
its arguments.

- `exit(status)` ends the program with an exit status from 0 to 255, after
  flushing what was printed. 0 means success. A status outside that range is a
  bug in the caller and stops the program with `SPR-RUNTIME-EXCEPTION`. The
  checker does not know that `exit` never returns, so a function that must
  return a value still needs a `return` or a `throw` after it.
- `print_error(text)` writes one line to standard error, so messages for the
  person running the tool stay out of the program's output.
- `read_line()` returns the next line of standard input without its line
  ending, or `null` at the end of input. `read_lines()` returns every
  remaining line as a list. `read_all()` returns everything that is left,
  line endings included. A line ends with LF, CRLF or CR, and the last line
  needs no ending. All three declare `throws Error`.

Piped or redirected input is read as UTF-8, and bytes that are not UTF-8 are
an `Error`, not a replacement character. Input typed at a terminal uses the
terminal's encoding, such as GBK on a Chinese Windows console, when both
standard input and standard output are the terminal.

```sprig
import "@std/process.spr" as process

func total(lines: List[String]) -> Int throws Error:
    var sum = 0
    for line in lines:
        let amount = line.toIntOrNull()
        if amount == null:
            throw Error("not a number: " + line)
        sum += amount
    return sum

try:
    print(total(process.read_lines()))
catch problem: Error:
    process.print_error(problem.message)
    process.exit(1)
```

`sprig run` passes its own standard input to the program and forwards the
program's exit status. It shows what the program wrote to standard error after
the program ends. In text mode, a deliberate nonzero exit adds no compiler
error message; `--json` reports `SPR-PROGRAM-EXIT` and its
`data.programExitCode`. In `sprig test`, program output appears before the one
line reporting its exit status. With `--json`, and in `sprig test`, the program
gets an empty standard input, and `sprig run --json` carries what the program
wrote to standard error in `programErrorOutput`, next to `programOutput`.

## Nullable values

`nulls` covers the two ways a nullable read usually ends. Both take the
value's type from their arguments: `nulls.or_else(counts[word], 0)` is an `Int`
when `counts` holds `Int` values.

- `or_else[T](value, fallback)` returns the value, or the fallback when the
  value is `null`. The fallback is an ordinary argument, so it is evaluated
  even when it is not used.
- `require[T](value, message)` returns the value, or throws `Error(message)`
  when it is `null`. An uncaught error is reported at the `throw` inside
  `nulls.spr`, so the message should say which value was missing.

The type argument must not be nullable itself: a written `or_else[String?]` is
rejected with `SPR-TYPE-GENERIC-NULLABLE`, and an inferred one never is, because
a `String?` value makes `T` a `String`. Reading a `Map` or a `MutableMap` gives a
nullable value, so `or_else` also supplies the starting value when counting:

```sprig
import "@std/nulls.spr" as nulls
import "@std/process.spr" as process

let path = nulls.or_else(process.environment("TASKS_FILE"), "tasks.json")
let counts: MutableMap[String, Int] = {}
for word in ["tea", "rice", "tea"]:
    counts[word] = nulls.or_else(counts[word], 0) + 1
print(counts)  # {tea: 2, rice: 1}
```

## JSON is an ordinary recursive Sprig data model

`json.Value` is a closed variant with `Null`, `Boolean(value: Bool)`,
`Number(text: String)`, `Text(value: String)`, `Array(values: List[Value])`,
and `Object(members: List[Member])`. `Member` has `key: String` and `value: Value`.
Objects preserve input/member order and reject duplicate keys. Numbers retain
validated exact JSON lexemes (`12.50`, `-2e3`) without lossy Float conversion;
applications choose any numeric conversion explicitly. No `Any` is involved.

### Explicit object lookup

`find_member(value: Value, key: String) -> Lookup throws Error` returns a closed
variant: `Missing`, `Found(value: Value)`, or `NotObject`. A present JSON null
is `Found(value=Value.Null)`, never `Missing`. False, zero and an empty string
also remain present values; there is no truthiness or value coercion. Key
comparison is exact String equality, including Unicode keys. Member order and
stored values are unchanged.

```sprig
import "@std/json.spr" as json

let document = json.parse("{\"name\":null}")
match json.find_member(document, "name"):
    case json.Lookup.Missing:
        print("missing")
    case json.Lookup.Found as member:
        print(json.stringify(member.value))  # prints null
    case json.Lookup.NotObject:
        print("expected an object")
```

Lookup validates every key in a manually constructed object before returning,
so duplicate keys after a matching member also raise `Error`. Parsing and
serialization retain their existing duplicate rejection. Nonobjects return
`NotObject`; this lookup does not validate unrelated nested values or number
lexemes. Parsing/serialization perform those validations. Lookup scans the
ordered members and returns the stored value without a dynamic escape hatch.

Parser/serializer logic, recursive traversal, collections and errors are Sprig.
The tiny `HostText` boundary only converts UTF-16 hex units and escapes control
characters. Parsing accepts JSON escapes and rejects trailing input, trailing
commas, invalid number syntax, invalid escapes and raw control characters.
Nesting above 128 is rejected during parsing and rendering. Manually constructed
`Number` lexemes and duplicate `Object` members are validated on serialization.
Unicode escape units use JVM UTF-16 representation; this API does not claim
Unicode scalar validation or canonical JSON normalization. It loads whole input
strings and is intended for small tooling/configuration data, not streaming data.

### Typed fields with `json_codec`

`match` has no wildcard branch, so reading one integer out of a `json.Value`
by hand lists all six cases. `@std/json_codec.spr` does that once: it checks
the JSON kind, never converts between kinds, and reports the place a problem
was found.

```sprig
import "@std/json.spr" as json
import "@std/json_codec.spr" as codec

class Task:
    let id: Int
    let title: String
    let done: Bool

func decode_task(row: codec.Reader) -> Task throws Error:
    return Task(
        id=codec.required_int(row, "id"),
        title=codec.required_string(row, "title"),
        done=codec.required_bool(row, "done")
    )

func encode_task(task: Task) -> json.Value:
    return codec.object([
        codec.member("id", codec.int(task.id)),
        codec.member("title", codec.text(task.title)),
        codec.member("done", codec.bool(task.done))
    ])
```

- A `Reader` is a value together with the path it was reached through, such
  as `$`, `$.projects[1]` or `$[0].id`. Every error message starts with that
  path: `$[0].id: expected integer, found string`.
- `root(value)` requires the document to be an object. `root_array(value)`
  requires an array and returns one `Reader` per element.
- `required_string`, `required_bool`, `required_int`, `required_decimal`,
  `required_number_text`, `required_array`, `required_string_array` and
  `required_object` throw `Error` when the field is missing, is JSON `null` or
  has another kind. The two failures read differently: `required field is
  missing` and `expected string, found null`.
- The matching `optional_*` functions return `null` for a missing field and for
  JSON `null`. `field(reader, name)` returns `Field.Missing`, `Field.Null` or
  `Field.Value(value)` when a program must tell those apart.
- No kind is converted: `"12"` is not an integer, and `12.5` or `1e3` is not an
  integer either. `required_int` also rejects integers outside the `Int` range.
  `required_number_text` keeps the exact JSON number text, and
  `required_decimal` parses it as `Decimal`. A number whose exponent is out
  of `Decimal`'s range, such as `1e9999999999`, is an `Error` with the path:
  `$.amount: expected decimal in range, found number 1e9999999999`.
- `reject_unknown_fields(reader, allowed)` throws for a field that is not in
  the list, and for a duplicate key in a manually built object. Reading any
  field of such an object throws too, with the same message:
  `$: duplicate object key: a`.
- `object`, `member`, `array`, `string_array`, `text`, `int`, `bool` and
  `number` build values for `json.stringify`. They add no policy.

There is no automatic mapping from classes: no reflection, annotations or
generated code. Rules such as positive amounts or unique ids stay in the
application. The first-party `sprig-json-codec` package reexports this module,
so `import "@json-codec/codec.spr"` keeps working and names the same types.
`examples/task-tracker` is a complete program built on it.

`HostFiles`, `HostSystem`, and `HostText` are explicit Java implementation
boundaries, queryable with `sprig api ... --json`. `files.list` explicitly copies
an indexed host snapshot into a typed Sprig list; it does not change the general
Java generic collection interop contract. The old frontend probe's `HostFiles`
methods remain available for compatibility.

Verify actual JVM behavior with `python3 scripts/test-stdlib.py` in a source clone.

## Sets, random values, regular expressions and dates

These four modules wrap one JDK facility each behind a small Sprig surface,
with the failure cases turned into `Error`:

```sprig
import "@std/sets.spr" as sets
import "@std/random.spr" as random
import "@std/regex.spr" as regex
import "@std/dates.spr" as dates

let seen = sets.of(["pear", "apple", "pear"])
print(seen.to_list())                                    # [pear, apple]
print(seen.has("apple"))                                 # true
let dice = random.seeded(42)
print(dice.next_int(6) >= 0)                             # true
print(regex.find_all("\\d+", "order 66 of 99"))           # [66, 99]
print(regex.find_groups("(\\w+)@(\\w+)", "to ada@host"))  # [ada, host]
print(dates.plus_days("2026-10-06", 30))                 # 2026-11-05
```

- `sets.Set[T]` keeps members in insertion order and compares them the way map
  keys are compared, so a `Float` or `Float32` set is rejected as a `Float` map
  key is, at the call that makes it, also when generic code passes its own `T`
  on to `sets.of`; `add` and `remove` report whether anything changed.
  `union`, `intersection` and `difference` return new sets.
- `random.seeded(seed)` gives the same sequence on every run; `fresh()` does
  not. `next_int(bound)` checks the bound, `shuffled` returns a copy, `choice`
  needs a non-empty list, and `uuid()` is the 36-character text form. None of
  it is suitable for secrets.
- `regex` uses Java's pattern and replacement syntax (`$1` for a group). An
  invalid pattern is an `Error` with Java's message; `find` returns `null` for
  no match and `split` drops a trailing empty piece, as Java does.
  `find_groups` returns the capture groups 1 to n of the first match,
  numbered by their opening parentheses, or `null` when nothing matches. A
  group that took no part in the match, such as the unused side of
  `(a)|(b)`, is `null`, so the type is `List[String?]?`. `find_all_groups`
  returns those lists for every match, in order.
- `dates` has no date type: a date is ISO text such as `2026-10-06`, checked by
  every function. `day_of_week` is 1 for Monday through 7 for Sunday, and
  `days_between` is negative when the end comes first.

The list helpers `first`/`last` return `null` for an empty list, `take`/`drop`
reject a negative count, `distinct` keeps the first occurrence, `index_of`
returns -1 when absent, and `enumerate`/`zip` return small classes
(`Indexed[T]` with `index` and `value`, `Pair[A, B]` with `first` and `second`)
because Sprig has no tuples. `text.fixed(value, decimals)` formats a finite
`Float` with that many decimals, rounding half away from zero.
`files.read_lines` splits like `text.lines`, and `files.walk` lists every file
below a directory in sorted order without following symbolic links.
`time.sleep` pauses and `time.monotonic_nanos` measures elapsed time.
