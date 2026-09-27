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
use their installed SDK directly. `sprig resolve` records `stdlib-version` and
`stdlib-sha256` in schema-3 locks; project consumers fail on mismatch, including
older locks missing these fields, and require explicit re-resolution.
The digest is SHA256 of each sorted module filename, NUL, exact UTF-8 file bytes,
NUL concatenated in filename order. LF/CRLF bytes are intentionally distinct.
The std version follows the compiler/SDK release during Alpha; no independent
stdlib compatibility promise or invisible upgrade is made.

| Module | Public operations |
|---|---|
| `files` | `read_utf8`, `write_utf8`, `exists`, `is_file`, `is_directory`, `list`, `make_directory`, `join`, `normalize`, `file_name` |
| `process` | `arguments() -> List[String]`, bounds-checked `argument(Int)`, `environment(String) -> String?` |
| `text` | `lines`, literal `split`, `trim`, `starts_with`, `ends_with` |
| `time` | `epoch_millis() -> Int`, `utc_now() -> String` |
| `json` | `parse(String) -> Value`, `stringify(Value) -> String`, `quote(String)`, `find_member(Value, String) -> Lookup` |

File text always uses UTF-8. Writes replace existing file content, create a file,
and require an existing parent; `make_directory` creates missing parents.
Directory listing produces a sorted snapshot of absolute normalized paths in
`List[String]`. `is_file`, `is_directory` and `exists` do not follow symbolic
links. Joining/normalizing paths is lexical and does not constrain paths to a
sandbox. `file_name` requires a path with a filename component. IO failures
preserve `java.io.IOException`; catch or declare that type. Null guards and
argument bounds report Sprig `Error`; OS invalid-path failures remain JVM errors.
The host is unrestricted local IO under the invoking user's permissions.

`process.arguments()` contains only arguments after `sprig run --`; the generated
JVM main copies its argument array before module initialization. An unset
environment value is `null`, distinct from an empty value. Time is wall-clock UTC,
not a monotonic duration clock. Text indexes follow Sprig's UTF-16 String contract.
`lines` accepts CRLF/LF and retains the final empty segment; `split` is literal,
retains empty segments, and rejects an empty separator. `trim` removes ASCII
space/tab/CR/LF, without claiming full Unicode whitespace handling.

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
