# sprig-csv

A small text-only CSV API backed by Apache Commons CSV 1.14.1. Available by
path from this source checkout; not yet published in an SDK or registry release.

```toml
[[dependency]]
name = "csv"
path = "../../libraries/sprig-csv"
```

Run `sprig resolve` once to resolve Commons CSV and its transitive JVM
libraries. The generated lock pins their hashes; subsequent runs can use
`--offline` after the dependencies have been cached.

```sprig
import "@csv/csv.spr" as csv

let format = csv.Format(delimiter=",", header=true)
let table = csv.parse("name,note\r\n世界,\"tea, cake\"\r\n", format)
print(table.cell(0, "note"))
let output = csv.encode(table, format)
```

The complete [runnable example](../../examples/csv/README.md) creates a table,
writes UTF-8 CSV, reads it back and accesses cells by header without Java imports.

## API

| Value or function | Contract |
|---|---|
| `Format(delimiter=",", header=true)` | Delimiter is exactly one Unicode code point except quote, CR, LF or NUL. Tabs and semicolons work. `header` explicitly controls reading/writing the header record. |
| `Table(headers, rows)` | `headers: List[String]`, `rows: List[List[String]]`. Values remain text; numbers, dates, booleans and `null` text are never inferred. |
| `table.cell(row, column)` | Zero-based data row and exact case-sensitive header name. Unknown names and invalid row indices throw `CsvError`. |
| `parse(source, format)` | Materializes a CSV string into a table. |
| `encode(table, format)` | Returns a CSV string with CRLF record terminators, including the last record. |
| `read(path, format)` | Reads the entire file as strict UTF-8 and parses it. |
| `write(path, table, format)` | Validates and encodes before replacing the UTF-8 file. |

All operations that can fail declare `throws Error`. `CsvError` conforms to
`Error`; it has `message: String` and `cause: java.lang.Throwable?`. Catch it as
`csv.CsvError` when the original Java exception or nested CSV error is needed.
The cause is an explicit API field, not Java's `Throwable.getCause()` chain.
Validation failures have no cause; read/write wrappers retain their inner error.
Parse failures include the attempted record and current physical line plus the
upstream parser explanation. Header and width failures identify the column or
record; file failures also identify the path. No partial table is returned.

## CSV behavior

- With `header=true`, the first record supplies the headers and is excluded
  from `rows`. Missing headers (including an empty file), names empty after
  ASCII whitespace trimming, and duplicate exact names are errors. Names are
  otherwise preserved, including surrounding spaces; `Name` and `name` differ.
- Each data record must have exactly as many cells as the header. Missing and
  extra cells are errors, rather than silently padded or discarded.
- With `header=false`, every record is data and `headers` is empty. Ragged
  records are allowed. Encoding omits `table.headers` in this mode.
- A blank line is one record with one empty cell. Blank lines are retained;
  consequently they fail the width check under a multi-column header. An
  empty input without headers has zero records. A final line terminator does
  not create an extra blank record. A zero-cell row cannot be encoded; use
  `[""]` for a record containing an empty cell.
- Double quotes enclose cells containing delimiters or newlines; doubled
  quotes represent a literal quote. LF, CRLF and CR separate input records.
  Embedded newlines and unquoted whitespace are preserved. Commons CSV also
  accepts a quote inside an unquoted cell as literal text, and whitespace
  between a closing quote and delimiter/record ending. Unterminated quoted
  cells and other trailing characters after a closing quote are errors.
- One leading U+FEFF (UTF-8 BOM) is removed on input. Output has no BOM.
  Invalid UTF-8 file input is an error. Quotes, comments and null markers have
  no additional configurable dialect rules; backslash does not escape quotes.

## Memory, resources and limits

This API is synchronous and **materializes the whole document**. Parsing keeps
input text, Commons CSV records and Sprig rows in memory; encoding builds the
whole output string. There is no streaming interface, configured size limit or
truncation. Use a streaming JVM API directly for files that do not fit in the
available heap; do not use this adapter as an unbounded network ingestion API.

The package closes its parser/printer and file resources before returning or
throwing. Returned tables hold no reader or file handle. `write` follows the
same file policy as `@std/files.atomic_write_utf8`: it follows a destination
symlink, writes a temporary sibling, then replaces the target, using an atomic
rename where supported with a non-atomic fallback and no fsync guarantee.
Validation never touches the destination. An I/O failure is reported, and the
underlying helper cleans up temporary files. Existing parent directories are
required.

Commons CSV, Commons IO and Commons Codec are Apache-2.0 dependencies resolved
from Maven; no third-party source or JAR is vendored. See
[third-party notices](../../THIRD_PARTY_NOTICES.md).

Run the package contract from the repository root after building:

```sh
python3 tests/csv/check_csv.py
# After its first successful resolution:
python3 tests/csv/check_csv.py --offline
```
