# sprig-sqlite: explicit SQL, typed values

This experimental source package depends on exact
`org.xerial:sqlite-jdbc:3.46.1.0`, resolved by Sprig. No Maven CLI, ORM, plugins,
JDBC classes or connection lifetime management are required in application code.
The driver is an application dependency, not bundled into every compiler invocation.

```toml
[[dependency]]
name = "sqlite"
path = "../../libraries/sprig-sqlite"
```

```sprig
import "@sqlite/sqlite.spr" as sqlite

func demo() -> Unit throws Error:
    let db = sqlite.open("notes.sqlite")
    db.execute("CREATE TABLE IF NOT EXISTS notes (body TEXT NOT NULL)", [])
    db.execute("INSERT INTO notes(body) VALUES (?)", [sqlite.Parameter.Text(value="user text")])
    let rows = db.query("SELECT body FROM notes ORDER BY rowid", [])
    if rows.size() > 0:
        print(rows.text(0, "body"))

demo()
```

Run `sprig resolve` once, then `sprig check --offline` / `sprig run --offline`.
An existing lock/cache never silently downloads missing artifacts. Local package
locks record canonical paths and must be re-resolved after relocating a checkout.

## API contract

| API | Contract |
| --- | --- |
| `open(path) -> Database` | Create/open an ordinary file-backed SQLite DB; existing parent required |
| `Database.execute(sql, List[Parameter]) -> Int` | One prepared statement; return affected row count |
| `Database.query(sql, List[Parameter]) -> Rows` | Prepared query with detached snapshot; `INSERT ... RETURNING` is supported |
| `Database.batch(List[Statement]) -> Int` | One transaction; commit all commands or rollback on failure |
| `Statement(sql=..., parameters=...)` | Visible SQL and explicit typed parameters |
| `Parameter.Integer(value=Int)` | Exact signed 64-bit INTEGER |
| `Parameter.Text(value=String)` | TEXT; quotes, Unicode and SQL-looking input remain data |
| `Parameter.Boolean(value=Bool)` | INTEGER 0/1 |
| `Parameter.Null` | SQL NULL, distinct from empty text and zero |
| `Rows.size()` | Detached row count |
| `Rows.is_null(row, column)` | Explicit nullable-result test |
| `Rows.integer(row, column)` | Require non-null SQLite INTEGER; no Float/String coercion |
| `Rows.text(row, column)` | Require non-null SQLite TEXT |
| `Rows.boolean(row, column)` | Require integer 0/1 |

Indices are zero-based; column labels are case-insensitive and must be unique.
Unknown columns, invalid row indices, wrong kinds, NULL access through a non-null
accessor, or incorrect parameter count fail explicitly. Each query is limited to
10,000 snapshot rows; whole result sets are loaded in memory. There is no cursor API.
BLOB and Decimal bindings are deliberately absent. INTEGER money uses minor units.

Each operation creates a connection, enables foreign keys and a 5-second busy
timeout, binds parameters, and closes statements/results/connections with Java
resource scopes. Rows retain no JDBC resources. Queries use an explicit transaction, so a failed
`INSERT ... RETURNING` snapshot (duplicate labels, unsupported types or size
limit) rolls back its write before returning an error. Batch parameters are snapshotted
when added; a failed batch rolls back even when the failure is adapter validation.
SQL transaction-control statements (`BEGIN`, `COMMIT`, `ROLLBACK`, `SAVEPOINT`,
`RELEASE`, `END`) are rejected; batch owns transaction lifetime. No transaction can
span separate calls. SQL structure/identifiers remain trusted application code;
only data uses parameters. This is not a SQL sandbox.

Errors preserve the JDBC cause as recoverable `Error` (`SqliteError`); the host
`constraint()` query distinguishes SQLite constraint violations from internal
failures. HTTP applications choose their public error response without leaking
SQL/cause details. SQLite numeric operations retain SQLite semantics; integer SUM
overflow fails and is never converted to Float by the adapter.

Ordinary file paths only: no `:memory:`, SQLite URI mode or remote database. Paths
are normalized to absolute form. The driver extracts its native library into the
OS temporary directory; this needs normal local filesystem/native-load permission.
JDK 26 prints a native-access warning unless the user explicitly enables native
access (for example `JAVA_TOOL_OPTIONS=--enable-native-access=ALL-UNNAMED`).
SLF4J may report its absent logger binding; the demo does not add a logging stack.

See [SQLite example](../../examples/sqlite/README.md) and
[ledger backend](../../examples/ledger/README.md). Verify with
`python3 tests/sqlite/check_sqlite.py` (network only in explicit `sprig resolve`),
or `--offline` after cache warm-up.
