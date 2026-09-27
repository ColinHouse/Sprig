# HTTP experiment: measured language and JVM pressure

This record covers the bounded raw host experiment (phase 5), the ordinary Sprig
web package (phase 6), and explicit OpenAPI/Swagger metadata (phase 7). It describes
implemented source in this checkout, not a published framework.

## Evidence path

1. `examples/mini_web/src/host_probe.spr` passes a source
   `fn(HostRequest) -> HostResponse` to the Java host's concrete
   `Fn1<HostRequest,HostResponse>` constructor. It implements GET `/`, GET
   `/hello/<name>` and POST `/echo` in Sprig.
2. `libraries/sprig-web/src/app.spr` moves request interpretation, segment matching,
   ordered registration, responses, errors, CORS and OpenAPI into ordinary Sprig
   types/functions. Java still owns binding/exchanges/byte decoding/lifecycle.
3. `examples/mini_web/src/main.spr` exercises exact and parameter routes, query
   text, JSON body/response, explicit schemas and ordinary typed callbacks.
4. `python3 tests/web/check_web.py` runs real local HTTP using normal `sprig run`,
   ephemeral bound ports and readiness output; it always terminates the process
   and checks that the listening port is released. No Swagger CDN fetch is part
   of this offline HTTP test.

## Pressure and classification

| Observed pressure | Classification | Implemented resolution / evidence |
|---|---|---|
| A route must store a typed handler, and get/post/delete must accept one | language feature needed | Source `fn(Request) -> Response` appears in Route fields and App method parameters. No erased Any or untyped handler |
| Host constructor accepts a generic JVM callable formal | language feature needed | Concrete Sprig-owned Fn1 ABI; raw probe and App.run cross the bridge with HostRequest/HostResponse. Arbitrary Java SAM conversion remains outside this experiment |
| HTTP request/response bodies and JDK APIs use byte arrays | JVM adapter sufficient | Java reads bytes, validates UTF-8 and writes byte length; no Java array syntax was added |
| JDK headers and executor/server interfaces use Java collections/platform abstractions | JVM adapter sufficient | HostRequest exposes scalar strings; HostResponse receives one header at a time. Routing/metadata lists stay Sprig List/MutableList |
| Percent decoding needs byte operations and strict Unicode error detection | JVM adapter sufficient | HostCodec handles UTF-8 mechanics. Sprig splits raw path into segments before decoding, so `%2F` stays inside a parameter |
| Reflection regards Java String and host reference results as nullable | JVM adapter sufficient | Sprig guards host return values explicitly and preserves absence for header/path/query APIs. No general nullability relaxation |
| Socket binding and exchange IO throw checked IOException | JVM adapter sufficient | HostServer owns exchange cleanup and turns a bind failure into Error; application handlers do not manipulate exchanges |
| Source callable types have no throws clause; a lambda cannot call an unhandled throws Error function | library API problem | Request.json explicitly catches parser Error and raises BadRequest for400. json_response explicitly catches stringify Error and raises an unchecked internal failure for500. Application/database handlers catch their own recoverable errors before returning a response; compiler effects remain unchanged |
| A method named json shadows a module alias json inside Request | library API problem | Internal module alias is jsonlib; public Request.json remains ordinary method syntax |
| Conceptual Response.text(...) would require static Sprig methods | library API problem | Canonical helpers are `web.text(body,status)` and `web.json_response(value,status)`; no static-method syntax added |
| Ordinary functions have no parameter defaults | library API problem | Helpers take an explicit status; constructors supply ordinary field defaults for metadata |
| Long-lived programs were buffered by `sprig run` until exit | tooling problem | Normal text run streams child stdout and stdin; JSON run preserves its captured envelope. Test reads PORT while the child is live and verifies child cleanup on CLI termination |
| Automatic arbitrary-class schema/JSON reflection | not worth solving yet | Schema, FieldSchema and QueryParameter are explicit typed metadata. Handlers inspect the closed JSON variant and own domain validation |
| Concurrent application access, streaming bodies and a production server contract | not worth solving yet | First adapter executes serially on localhost with a minimal lifecycle. This experiment does not justify async syntax or a middleware/DI system |

## Implemented application policy

- GET, POST and DELETE registration; exact or `{name}` segment routes; first
  registered matching route wins. Trailing slashes remain meaningful.
- UTF-8 body text; path/query/header access. Query first occurrence wins, `+`
  decodes as space, absent stays null and a present empty value stays empty.
- Closed typed JSON; malformed JSON/URI/UTF-8400, missing route404 and generic
  controlled500. Responses carry explicit status, content type and headers.
- Configured local Vue origin CORS and OPTIONS204; credentials/authentication are
  outside this application.
- OpenAPI3.0.3 at `/openapi.json`, explicit request/response schema and status,
  inferred path parameter metadata and explicit query metadata. Schema metadata
  is validated at registration and does not automatically validate request bodies.
- `/docs` serves Swagger UI HTML with pinned CDN assets; rendering the interactive
  page requires network access. HTTP/OpenAPI tests validate local HTML/JSON
  structure and do not claim a browser CDN execution test.

## Verification actually run

`python3 tests/web/check_web.py` passed with these evidence layers:

- Source static checking and javac compilation via normal Sprig project run.
- Raw host callback on the JVM: GET root/name and UTF-8 POST echo.
- Real HTTP: Unicode path/query, encoded slash and embedded equals, duplicate and
  empty query values, custom headers, JSON values preserving null/false/zero,
  malformed JSON/URI/UTF-8, 400/404/500, CORS preflight and DELETE204.
- Lifecycle: separate App route state; duplicate/reserved/bad parameter and invalid
  schema rejection; ephemeral binding; idempotent stop and restart; CLI termination
  releases the persistent server port.
- OpenAPI registration regressions: duplicate query parameter names, equivalent
  templated paths using different names (including nested templates and different
  HTTP methods), and duplicate object-schema fields are rejected with Error.
  Distinct methods sharing the identical path remain valid.
- OpenAPI JSON parsed structurally: paths, methods, request/response object schema,
  required fields, path/query parameters, integer-array and optional-bool schema.
- Swagger HTML references the local OpenAPI route. Interactive CDN assets were
  not fetched by this local test.

The full contributor gate and integrated SQLite/ledger evidence are recorded in
the milestone engineering report separately.
