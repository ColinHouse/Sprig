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

| Module | Public operations |
|---|---|
| `files` | `read_utf8`, `write_utf8`, `exists`, `is_file`, `is_directory`, `list`, `make_directory`, `join`, `normalize`, `file_name`, `parent`, `absolute`, `copy_file`, `move`, `remove_file`, `atomic_write_utf8`, `temp_file` |
| `process` | `arguments() -> List[String]`, bounds-checked `argument(Int)`, `environment(String) -> String?` |
| `text` | `join`, `lines`, literal `split`, `trim`, `starts_with`, `ends_with`, `pad_left`, `pad_right`, `is_ascii_digit`, `is_ascii_letter` |
| `math` | `abs`, `min`, `max`, `sign`; `clamp`, `floor_div` and `isqrt` declare checked `Error` for invalid arguments |
| `lists` | `sorted`, `sort_by`, `group_by` returning `List[Group[K, T]]`, `fold`, `find`, `any`, `all`, `count`, `sum`, `sum_by` |
| `nulls` | `or_else[T](T?, T) -> T`, `require[T](T?, String) -> T throws Error` |
| `time` | `epoch_millis() -> Int`, `utc_now() -> String`, `format_utc(Int) -> String`, `parse_utc(String) -> Int` |
| `json` | `parse(String) -> Value`, `stringify(Value) -> String`, `quote(String)`, `find_member(Value, String) -> Lookup` |
| `test` | `temp_dir() -> String throws Error`, `run_process(List[String]) -> ProcessResult throws Error` (argv, UTF-8 stdout/stderr, exit code); `equal_int`, `equal_bool`, `equal_text` |

`test` is intended for ordinary programs run through `sprig test`. Its
temporary directory helper requires the runner-provided environment. The
process helper remains available to a standalone program, but it neither
invokes a shell nor turns a nonzero child status into an exception. See
[testing](../tooling/testing.md) for isolation, timeout and failure behavior.

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
parents. Directory listing produces a sorted snapshot of absolute normalized
paths in `List[String]`. `is_file`, `is_directory` and `exists` do not follow
symbolic links. Joining/normalizing paths is lexical and does not constrain paths
to a sandbox. `file_name` requires a path with a filename component. `parent`
first normalizes lexically, preserves relative paths, and returns `null` for a
leaf with no parent or a filesystem root. `absolute` returns an absolute
normalized path without checking existence or resolving symbolic links, so its
value is based on the process working directory.

`copy_file` and `move` require an existing regular-file source and reject
symbolic links, directories and existing destinations; neither silently
overwrites. `remove_file` deletes one regular file and rejects directories and
symbolic links. `atomic_write_utf8` writes and closes a temporary sibling before
replacing the target. It requests an atomic same-filesystem replacement; if the
provider reports atomic moves are unsupported, it falls back to a regular
replacement move, which is not crash-atomic. Temporary files are removed after
success or failure. No API here promises fsync or crash durability. `temp_file`
creates an empty file in the operating system temporary directory; callers can
remove it with `remove_file`.

IO failures preserve `java.io.IOException`; catch or declare that type. Null
guards and argument bounds report Sprig `Error`; OS invalid-path failures remain
JVM errors. The host is unrestricted local IO under the invoking user's
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
Every function is eager, returns a new list and leaves its input unchanged.

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

A lambda cannot call a function that declares `throws`, so a step that can
fail, such as parsing each item, still needs a `for` loop.

```sprig
import "@std/lists.spr" as lists

for group in lists.group_by[Order, String](orders, fn(o: Order) => o.category):
    let total = lists.sum_by[Order](group.items, fn(o: Order) => o.cents)
    print(group.key + " " + total.toString())

let large = lists.find[Order](orders, fn(o: Order) => o.cents > 400)
if large != null:
    print(large.item)
```

## Nullable values

`nulls` covers the two ways a nullable read usually ends. Like every generic
call, both name the value's type.

- `or_else[T](value, fallback)` returns the value, or the fallback when the
  value is `null`. The fallback is an ordinary argument, so it is evaluated
  even when it is not used.
- `require[T](value, message)` returns the value, or throws `Error(message)`
  when it is `null`. An uncaught error is reported at the `throw` inside
  `nulls.spr`, so the message should say which value was missing.

The type argument must not be nullable itself: `or_else[String?]` is rejected
with `SPR-TYPE-GENERIC-NULLABLE`. Reading a `Map` or a `MutableMap` gives a
nullable value, so `or_else` also supplies the starting value when counting:

```sprig
import "@std/nulls.spr" as nulls
import "@std/process.spr" as process

let path = nulls.or_else[String](process.environment("TASKS_FILE"), "tasks.json")
let counts: MutableMap[String, Int] = {}
for word in ["tea", "rice", "tea"]:
    counts[word] = nulls.or_else[Int](counts[word], 0) + 1
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

`HostFiles`, `HostSystem`, and `HostText` are explicit Java implementation
boundaries, queryable with `sprig api ... --json`. `files.list` explicitly copies
an indexed host snapshot into a typed Sprig list; it does not change the general
Java generic collection interop contract. The old frontend probe's `HostFiles`
methods remain available for compatibility.

Verify actual JVM behavior with `python3 scripts/test-stdlib.py` in a source clone.
