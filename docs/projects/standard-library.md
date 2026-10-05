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
signatures. It works from any directory, with or without a project.

| Module | Public operations |
|---|---|
| `files` | `read_utf8`, `write_utf8`, `exists`, `is_file`, `is_directory`, `list`, `make_directory`, `join`, `normalize`, `file_name`, `parent`, `absolute`, `copy_file`, `move`, `remove_file`, `atomic_write_utf8`, `temp_file` |
| `process` | `arguments() -> List[String]`, bounds-checked `argument(Int)`, `environment(String) -> String?` |
| `text` | `join`, `lines`, literal `split`, `trim`, `starts_with`, `ends_with` |
| `math` | `abs`, `min`, `max`, `sign`; `clamp`, `floor_div` and `isqrt` declare checked `Error` for invalid arguments |
| `lists` | `sort_by`, `group_by` returning `List[Group[K, T]]`, `fold` |
| `time` | `epoch_millis() -> Int`, `utc_now() -> String`, `format_utc(Int) -> String`, `parse_utc(String) -> Int` |
| `json_codec` | typed field access over `json`: `root`, `root_array`, `required_*`, `optional_*`, `field`, `reject_unknown_fields`; builders `object`, `member`, `array`, `text`, `int`, `bool` |
| `json` | `parse(String) -> Value`, `stringify(Value) -> String`, `quote(String)`, `find_member(Value, String) -> Lookup` |
| `test` | `temp_dir() -> String throws Error`, `run_process(List[String]) -> ProcessResult throws Error` (argv, UTF-8 stdout/stderr, exit code) |

`test` is intended for ordinary programs run through `sprig test`. Its
temporary directory helper requires the runner-provided environment. The
process helper remains available to a standalone program, but it neither
invokes a shell nor turns a nonzero child status into an exception. See
[testing](../tooling/testing.md) for isolation, timeout and failure behavior.

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

## Sorting and grouping lists

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
  Aggregates are folds: a sum of `Int` uses checked arithmetic, and an overflow
  fails like any other `Int` overflow.

```sprig
import "@std/lists.spr" as lists

for group in lists.group_by[Order, String](orders, fn(o: Order) => o.category):
    let total = lists.fold[Order, Int](group.items, 0, fn(sum: Int, o: Order) => sum + o.cents)
    print(group.key + " " + total.toString())
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
  `required_decimal` parses it as `Decimal`.
- `reject_unknown_fields(reader, allowed)` throws for a field that is not in
  the list, and for a duplicate field in a manually built object.
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
