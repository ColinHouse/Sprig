# sprig-web 0.1 development library

A synchronous localhost HTTP library implemented in Sprig. JDK HTTP transport
and UTF-8 decoding live behind a small Java boundary. This is a source checkout
library, not a published registry package or a production framework.

## Install and run

Declare a local dependency and explicitly resolve it:

```toml
[[dependency]]
name = "web"
path = "../../libraries/sprig-web"
```

```sh
sprig resolve
sprig run
```

Locks for local dependencies contain machine paths; resolve again after relocation.
`app.run(port)` binds **127.0.0.1**; `app.run_on(host, port)` binds another
address, such as `"0.0.0.0"` for every interface of a container (no TLS: put a
reverse proxy in front). `0` asks the OS for an ephemeral port. `app.port()` reports the actual port. `app.stop()` stops
it and is idempotent; a stopped App can start again. The live server keeps the
JVM running. Use Ctrl+C in the terminal to end it.

## Canonical source API

```sprig
import "@web/app.spr" as web

let app = web.App(title="Example", cors_origin="http://localhost:5173")

func greet(req: web.Request) -> web.Response:
    let name = req.path_param("name")
    if name != null:
        return web.text("Hello, " + name, 200).with_header("X-Example", "Sprig")
    return web.text("Missing name", 400)

# Malformed JSON makes req.json() throw; escaping the handler, it becomes 400.
func echo(req: web.Request) -> web.Response throws Error:
    return web.json_response(req.json(), 200)

app.get("/hello/{name}", fn(req: web.Request) => greet(req))
app.post("/echo", fn(req: web.Request) => echo(req))
app.run(8080)
```

Functions use positional calls. Classes/variants use named constructors. Response
helpers are module functions: `web.text(body, status)` and
`web.json_response(value, status)`. Ordinary functions do not have default arguments.

| Area | API / contract |
|---|---|
| App | `App(title=..., version="1.0.0", cors_origin="")`; fields shown with defaults are optional constructor fields |
| Routes | `get`, `post`, `put`, `patch`, `delete(path, handler)`, `route(Route(...))`; registration can throw `Error` |
| Handler | `fn(Request) -> Response throws Error`; synchronous; catch an `Error` to choose the response yourself, or declare `throws Error` and let the server answer it (see Errors) |
| Matching | Registration order wins, exact segments or one `{name}` per segment; decoded segment matching preserves encoded slashes inside a parameter; trailing slash is significant |
| Request | Read-only `method`, decoded `path`, UTF-8 `body`; `path_param(name)`, `query(name)`, `header(name)` return `String?` |
| Query | UTF-8 form decoding (`+` is space); first occurrence wins; absent is null, present empty is `""`; embedded `=` preserved |
| Header | Case-insensitive request lookup via JDK; response `Header(name=..., value=...)` list or `with_header(name,value)` |
| JSON | `req.json() -> json.Value throws Error`; `web.json_response(value,status) -> Response throws Error` uses existing closed `@std/json` data and exact number lexemes |
| Errors | Malformed URI/UTF-8 becomes 400, and so does the `BadRequest` that `req.json()` throws for a malformed body when it escapes the handler; missing route 404; any other `Error` or unchecked exception that escapes a handler becomes a generic 500 without exception details |
| CORS | Explicit `cors_origin` adds allow-origin, GET/POST/PUT/PATCH/DELETE/OPTIONS, Content-Type and Vary headers; OPTIONS 204; no credential mode |
| Response | `Response(status=..., body=..., content_type="text/plain; charset=utf-8", headers=[])`; status 200..599; transport owns framing; 204/304 omit body |

`json_response` turns a value that stringify rejects into an `Error` ("cannot write
the response as JSON: ..."), which becomes a controlled 500 when it escapes the
handler. Metadata describes schemas; it does **not** validate request bodies. The
application still inspects closed JSON variants and validates its own domain input.
A handler catches the errors it answers deliberately, such as a database constraint
as 400. A checked Java exception never crosses a function value, so a named helper
catches it.

## Explicit OpenAPI and Swagger UI

```sprig
let payload = web.Schema(kind=web.SchemaType.OBJECT, fields=[
    web.FieldSchema(name="name", schema=web.Schema(kind=web.SchemaType.STRING))
])
app.route(web.Route(
    method=web.Method.POST,
    path="/api/accounts",
    handler=fn(req: web.Request) => create_account(req),
    summary="Create account",
    request_schema=payload,
    response_schema=payload,
    response_status=201
))
```

`SchemaType` is STRING, INTEGER (int64), BOOLEAN, OBJECT or ARRAY. ARRAY requires
`items=Schema(...)`; OBJECT uses `fields=List[FieldSchema]`, whose `required` field
defaults true. `QueryParameter(name=..., schema=..., required=false)` values in
`Route.query_parameters` describe query inputs; `{name}` segments automatically
produce required string path metadata. `Route.summary` defaults empty;
request/response schemas default null and `response_status` defaults 200.

Registration rejects duplicate query parameter names and duplicate object-schema
field names. Equivalent templated paths must use the same parameter names across
methods: GET `/items/{id}` and POST `/items/{id}` share one OpenAPI path; a separate
`/items/{name}` registration is rejected. The same path can still have distinct
GET, POST and DELETE operations.

`app.openapi()` returns an ordinary `json.Value`. GET `/openapi.json` renders
OpenAPI 3.0.3; GET `/docs` serves Swagger UI HTML configured for that document.
The two paths are reserved. Swagger UI uses pinned `swagger-ui-dist@5.17.14` CDN
assets and **requires network access** to render the interactive UI. API routes
and OpenAPI tests run locally without fetching those assets. No annotation,
reflection or automatic class serialization is involved.

## Evidence and examples

- `examples/mini_web/src/host_probe.spr`: raw typed Fn1/JDK host experiment.
- `examples/mini_web/src/main.spr`: Sprig routing, JSON, parameters and OpenAPI.
- `examples/ledger`: SQLite application with explicit SQL and schema metadata.
- `python3 tests/web/check_web.py`: real HTTP on ephemeral ports, cleanup, lifecycle,
  negative registration checks and structural OpenAPI assertions.
- `policy.json`: behavior and routing policy index (not a signature list).
- Resolved signatures are compiler-owned: `sprig api @web/app.spr --json`
  (and `sprig api . --json` inside a depending project) after `sprig resolve`.

The first host processes requests serially. Authentication, sessions, middleware,
async, multipart, production limits and public interface binding are outside this
small experiment.

## Stable facade

New clients may import `@web/web.spr`: explicit declaration reexports expose
App, Request, Response, routing/schema types and response constructors. Their
types and initialization remain those of app.spr. Existing `@web/app.spr`
imports remain supported. `sprig api @web/web.spr --json` shows signatures and
origin metadata without executing server code.
