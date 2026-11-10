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
An existing lock/cache never silently downloads missing artifacts. A lock records
a relative local dependency relative to the project, so the checkout can move; an
absolute path is recorded as written and needs `sprig resolve` after a move.

## API contract

| API | Contract |
| --- | --- |
| `open(path) -> Database` | Create/open an ordinary file-backed SQLite DB; existing parent required |
| `Database.execute(sql, List[Parameter]) -> Int` | One prepared statement; return affected row count |
| `Database.query(sql, List[Parameter]) -> Rows` | Prepared query with detached snapshot; `INSERT ... RETURNING` is supported |
| `transaction[T](Database, fn(Transaction) -> T throws Error) -> T` | Scoped read-check-write transaction; commit and return the callback result |
| `transaction_run(Database, fn(Transaction) -> Unit throws Error)` | The same transaction for a callback that returns nothing |
| `Transaction.execute(sql, List[Parameter]) -> Int` | One prepared update on the scope connection |
| `Transaction.query(sql, List[Parameter]) -> Rows` | Detached query snapshot on the same scope connection |
| `Database.batch(List[Statement]) -> Int` | One transaction; commit all commands or rollback on failure |
| `Statement(sql=..., parameters=...)` | Visible SQL (one statement) and explicit typed parameters |
| `Parameter.Integer(value=Int)` | Exact signed 64-bit INTEGER |
| `Parameter.Text(value=String)` | TEXT; quotes, Unicode and SQL-looking input remain data |
| `Parameter.Boolean(value=Bool)` | INTEGER 0/1 |
| `Parameter.Null` | SQL NULL, distinct from empty text and zero |
| `Rows.size()` | Detached row count |
| `Rows.is_null(row, column)` | Explicit nullable-result test; `throws Error` for an unknown column or row |
| `Rows.integer(row, column)` | Require non-null SQLite INTEGER; no Float/String coercion |
| `Rows.text(row, column)` | Require non-null SQLite TEXT |
| `Rows.boolean(row, column)` | Require integer 0/1 |

Indices are zero-based; column labels are case-insensitive and must be unique.
Unknown columns, invalid row indices, wrong kinds, NULL access through a non-null
accessor, or incorrect parameter count fail explicitly. Every `Rows` accessor
except `size()` declares `throws Error`. Each query is limited to
10,000 snapshot rows; whole result sets are loaded in memory. There is no cursor API.
BLOB and Decimal bindings are deliberately absent. INTEGER money uses minor units.

Each SQL string passed to `execute`, `query` or a batch `Statement` holds one
statement. A trailing `;`, empty statements and comments after it are fine, and
a semicolon inside quotes, a comment or a trigger body does not count. A trigger
body ends where SQLite's own `sqlite3_complete` ends it: at an `END` that is the
first token after one of its semicolons, so the `END` of a `CASE` expression or
a column named `end` inside the body does not end it. A second
statement is an `Error`, and the call changes nothing (a batch rolls back), where
JDBC would run the first statement and silently ignore the rest. Put a
multi-statement script in a migration file instead.

Each standalone database operation creates a connection, enables foreign keys and a 5-second busy
timeout, binds parameters, and closes statements/results/connections with Java
resource scopes. Rows retain no JDBC resources. Queries use an explicit transaction, so a failed
`INSERT ... RETURNING` snapshot (duplicate labels, unsupported types or size
limit) rolls back its write before returning an error. Batch parameters are snapshotted
when added; a failed batch rolls back even when the failure is adapter validation.
SQL transaction-control statements (`BEGIN`, `COMMIT`, `ROLLBACK`, `SAVEPOINT`,
`RELEASE`, `END`) are rejected; the library owns transaction lifetime. Separate
`Database` calls do not join a scoped transaction. SQL structure/identifiers remain trusted application code;
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

## Scoped read-check-write transactions

`sqlite.transaction(database, body)` calls `body` synchronously with a
`sqlite.Transaction`. Use that value's `query` and `execute` methods for every
operation in the transaction. They use one connection and see earlier writes
before commit. The existing `Parameter`, one-statement SQL validation and detached
`Rows` contracts apply. On success, all writes commit and the callback result is
returned; a returned `Rows` remains usable after the scope ends. Type arguments
can be inferred from the callback result or written as `transaction[Int](...)`.
Nullable results are supported. A callback that returns nothing goes to
`sqlite.transaction_run(database, body)`, which has the same connection, commit,
rollback and error rules; `Unit` is not a value, so `transaction` cannot take it.

An escaping callback `Error` is rethrown unchanged after rollback. SQL failures
are `Error` values with the JDBC exception retained as their cause. A failed
transaction SQL operation marks the scope failed: even if the callback catches
that error, the transaction rolls back and throws it when the callback returns.
Statements and result sets close after each operation, and the connection closes
on success and failure. Rollback/close failures are suppressed on the primary
error (or its JDBC cause); they do not replace it. If connection close fails after
a successful commit, an `Error` is reported but the committed writes remain.

The `Transaction` is valid only inside its callback, on that callback's thread.
Keeping it and calling it after return or failure throws `Error`. Nested scoped
transactions on the same thread are rejected, including through another
`Database`; savepoints are unsupported. Helpers should accept the current
`Transaction` instead. Inside the callback, a call on the database itself
(`Database.query`, `execute`, `batch`, or `open` on the same file) throws `Error`
at once: `This database has a transaction running on this thread; call query
and execute on the Transaction passed to the callback`. Such a call would use a
second connection, which does not see the transaction's writes and whose own
write would wait for the transaction's lock until the 5-second busy timeout.
The transaction itself goes on, and a database in another file works as usual.
This is a synchronous API with detached results, not streaming cursors.

Run the [transfer example](../../examples/sqlite/src/transfer.spr), which reads a
balance, checks available funds, updates two accounts and reads its own write:

```sh
cd examples/sqlite
../../bin/sprig resolve
../../bin/sprig run src/transfer.spr --offline -- transfer.sqlite
```

The first run prints `checking balance=750`; subsequent successful runs transfer
another 250 minor units. Insufficient funds throw `Error` and leave both balances
unchanged. Application code imports no JDBC classes and closes no resources.

## Experimental migrations

Import `@sqlite/migrations.spr` and call
`Migrations(database=database, directory="migrations").apply()`. Files must be
named `NNN_description.sql`; they are applied in sorted filename order and
recorded by name in `sprig_schema_migrations`. Keep applied files immutable.
The three-digit sequence prefix must be unique, and an applied file must not
sort after a pending one. Before running anything, `apply()` checks every
directory entry's name and sequence number, checks the order against the
ledger and reads every pending file; any problem there is an `Error` and no
migration runs. Each multi-statement SQL script and its ledger row then
execute in the same batch transaction. A failing script rolls both back,
the migrations before it stay applied, and `apply()` can be run again after
fixing the unapplied migration. The migration directory is trusted project
code, and this version does not hash applied files to detect edits. See
[`examples/sqlite_migrations`](../../examples/sqlite_migrations/README.md).
