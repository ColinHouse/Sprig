# Small practical standard layer

The SDK ships `std/*.spr` as ordinary modules. Import them by relative path;
for the bundled showcases that is `import "../../../../std/files.spr" as files`.
Copy the `std` directory beside a standalone project and adjust the import path.
There are no implicit imports or special standard-library syntax.

| Module | Public operations |
|---|---|
| `files` | `read_utf8`, `write_utf8`, `exists`, `is_file`, `is_directory`, `list`, `make_directory`, `join`, `normalize`, `file_name` |
| `process` | `arguments() -> List[String]`, bounds-checked `argument(Int)`, `environment(String) -> String?` |
| `text` | `lines`, literal `split`, `trim`, `starts_with`, `ends_with` |
| `time` | `epoch_millis() -> Int`, `utc_now() -> String` |
| `json` | `parse(String) -> Value`, `stringify(Value) -> String`, `quote(String)` |

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
