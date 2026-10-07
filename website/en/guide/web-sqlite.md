# Web and SQLite

The SDK includes two libraries, `libraries/sprig-web` for HTTP services and `libraries/sprig-sqlite` for SQLite databases. It also includes example projects that use them: `examples/mini_web`, `examples/sqlite`, `examples/sqlite_migrations` and `examples/ledger`.

These are experimental examples that show how far Sprig can go. They aren't frameworks you can put straight into production.

## Run the ledger backend

`examples/ledger` is a complete bookkeeping backend: an HTTP API, SQLite storage and generated API documentation. From the root of a built Sprig repository, run:

```bash
cd examples/ledger
../../bin/sprig resolve
../../bin/sprig check --offline
../../bin/sprig run --offline -- ledger.sqlite 8080
```

When you see `LEDGER_READY 8080`, the server is up. Open `http://localhost:8080/docs` to try the API in Swagger UI; `/openapi.json` is the machine-readable description. The Swagger page loads its assets from a CDN, so it needs a network connection. The backend itself only listens on your machine and runs fully offline once dependencies are resolved and cached. Press Ctrl+C to stop it.

In VS Code, run long-running programs like this one with **Sprig: Run in Terminal**.

| Method | Path | What it does |
|---|---|---|
| GET, POST | `/api/accounts` | Lists or creates accounts |
| GET, POST | `/api/categories` | Lists or creates categories |
| GET, POST | `/api/transactions` | Lists or creates transactions |
| DELETE | `/api/transactions/{id}` | Deletes a transaction: 204 if deleted, 404 if it doesn't exist |
| GET | `/api/statistics/monthly?month=2026-09` | Number of transactions and totals for a month |

Amounts are integers in the smallest currency unit (cents, for example) and can be negative. The [ledger README](https://github.com/ColinHouse/Sprig/blob/main/examples/ledger/README.md) lists the request fields, storage and validation rules.

## Handlers are ordinary Sprig functions

A route handler has the type `fn(web.Request) -> web.Response throws Error`, with one rule: an answer the client should see, whether it's a 404 or a 400 that says which field is wrong, is a `Response` you return, and an error you don't handle escapes for the server to answer. These excerpts are from `examples/mini_web/src/main.spr`:

```sprig
import "@web/web.spr" as web

let app = web.App(title="Mini Web", cors_origin="http://localhost:5173")

func root(req: web.Request) -> web.Response:
    return web.text("Hello, Sprig!", 200).with_header("X-Sprig", "mini-web")

func hello(req: web.Request) -> web.Response:
    let name = req.path_param("name")
    let greeting = req.query("greeting")
    if name != null:
        if greeting != null:
            return web.text(greeting + ", " + name + "!", 200)
        return web.text("Hello, " + name + "!", 200)
    return web.text("Missing name", 400)

func echo(req: web.Request) -> web.Response throws Error:
    return web.json_response(req.json(), 200)

app.get("/", fn(req: web.Request) => root(req))
```

- Register handlers with `app.get`, `app.post`, `app.put`, `app.patch` and `app.delete`. `app.run(port)` listens on 127.0.0.1 only; to be reachable from other machines or outside a container, use `app.run_on("0.0.0.0", port)` (no TLS: put a reverse proxy in front). To document the route as well, pass a `web.Route` to `app.route`, with a summary and the shapes of the parameters, request and response. There are no decorators and no Java annotations.
- Build responses with module functions: `web.text(body, status)` and `web.json_response(value, status)`. There's no static `Response.text` form.
- `req.path_param`, `req.query` and `req.header` return `String?`, so "no such parameter" and "an empty parameter" stay distinct.
- `req.body` is UTF-8 text, and `req.json()` parses the body into a JSON value. To find a field in it, use `json.find_member`, which tells apart a missing key, a JSON `null` value and a value that isn't an object; see the [language quick reference](/en/guide/language-tour).
- If the body isn't valid JSON, `req.json()` throws an `Error`, and `web.json_response` throws one for a value it can't write as JSON. When the client needs no more than "bad request", declare `throws Error` like `echo` above and let it escape; when you want to say what's wrong, return a 400 `Response` yourself. Don't catch an error only to return a generic 400 or 500: the server does that.
- Malformed requests get a 400 (so does a body `req.json()` can't parse, when the handler lets that error escape), unknown routes a 404, and any other error that escapes a handler a controlled 500.

## API documentation

The OpenAPI document comes from `Schema`, `FieldSchema` and `QueryParameter` values that you write out explicitly; the library never reflects over arbitrary classes. These descriptions are documentation only, so handlers still validate the values they receive. Duplicate query parameters and conflicting path templates are rejected when you register a route.

The [`libraries/sprig-web` README](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-web/README.md) describes the behavior in full. After `sprig resolve`, `sprig api @web/app.spr --json` lists the signature of everything in the library.

## SQL stays in plain sight

The SQLite library gets a pinned `org.xerial:sqlite-jdbc:3.46.1.0` from Maven, recorded in the lock file.

- SQL statements are ordinary strings that you write, and they count as trusted application code. Values from users always go in through prepared-statement parameters, which come in four kinds: `Integer`, `Text`, `Boolean` and `Null`. This is not a SQL sandbox.
- Each SQL string holds one statement. A trailing semicolon or comment is fine; a second statement is an error instead of being silently skipped, so a multi-statement script belongs in a migration file.
- A query returns a typed snapshot of the results, already disconnected from the database. Connections, statements and result sets are closed when they're done. A single query can return at most 10,000 rows (more is an error), and the whole result is held in memory.
- `Database.batch` runs a list of statements in one transaction; if any of them fails, all of them are rolled back. When a query (including `INSERT ... RETURNING`) fails, anything it wrote is rolled back too.
- You can't write transaction statements such as `BEGIN` or `COMMIT` yourself; `batch` manages transactions.
- There's no ORM, no automatic conversion from REAL to Int, and no BLOB or Decimal parameters. Only ordinary database files are supported, not `:memory:` databases.

### Database migrations (experimental)

Import `@sqlite/migrations.spr` and call `Migrations(database=database, directory="migrations").apply()`:

- Migration files are named `NNN_description.sql`, and the three-digit numbers must be unique.
- Files run in filename order, and the names of applied files are recorded in the `sprig_schema_migrations` table. Before running anything, `apply()` checks the whole directory against that table: if a name doesn't fit the pattern, two files share a number, or an applied file sorts after one that hasn't been applied, it stops and nothing runs.
- Each file's SQL and its record are committed in the same transaction. If something fails, both are rolled back, and you can run again after fixing the file.
- Don't edit a file that has already been applied: file contents aren't checksummed yet, so the change would go unnoticed. The migrations directory counts as trusted project code.

See [`examples/sqlite_migrations`](https://github.com/ColinHouse/Sprig/tree/main/examples/sqlite_migrations) for a complete example, and the [`libraries/sprig-sqlite` README](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-sqlite/README.md) for the full API.

## Limitations

This is a synchronous, single-user example. It has no authentication, sessions, connection pooling or async handling, and it isn't designed for public deployment. Request bodies and query results are held in memory in full. Type checking makes sure the types are right, but it can't make your business rules or arithmetic correct.

## How it's tested

The repository's tests start a real local HTTP server, work with temporary SQLite files, check that data survives a restart and verify the structure of the OpenAPI document:

```bash
python3 tests/callables/check_callables.py
python3 tests/web/check_web.py
python3 tests/sqlite/check_sqlite.py
./scripts/verify.sh
```

The development record is in `docs/history/milestones/WEB_SQLITE_ENGINEERING_REPORT.md`. For a command-line tool made of several files, with option parsing, see [`examples/json_select`](https://github.com/ColinHouse/Sprig/tree/main/examples/json_select).
