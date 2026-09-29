# sprig-http

Small synchronous HTTP/HTTPS client for ordinary Sprig applications. It uses
the JDK `java.net.http.HttpClient`; the Sprig API keeps requests, headers and
responses in ordinary typed values.

```sprig
import "@http/http.spr" as http

let headers = [http.Header(name="Accept", value="application/json")]
let response = http.get("https://example.com/data", headers, 5000)
if response.status >= 200 and response.status < 300:
    print(response.body)
```

Declare the package as a local dependency while developing from this checkout:

```toml
[[dependency]]
name = "http"
path = "../libraries/sprig-http"
```

Run `sprig resolve`, then `sprig build`, `sprig run` or `sprig test` as usual.
Requests block the calling thread. `get` and `post_text` are small conveniences;
`send(Request(...))` supports other method names and an optional UTF-8 text body.

## Contract

- HTTP and HTTPS URLs must be absolute and have a host. Invalid URI/method/header,
  network, timeout, invalid UTF-8 response and interruption failures raise
  Sprig `Error`. Interruption restores the JVM thread interrupt flag.
- The positive `timeout_ms` is a request-level timeout. Redirects are disabled;
  there are no retries.
- Non-2xx status codes are ordinary `Response` values. Callers choose how to
  handle them.
- Request and response bodies are UTF-8 text. Binary and streaming bodies are
  outside this API. JSON is not decoded automatically; use `@std/json` and a
  JSON codec explicitly.
- Request headers are passed to the JDK in caller order; the JDK controls their
  wire casing. Response header names are lowercase; rows are sorted by name and
  repeated values become repeated `Header` rows in the order supplied by the JDK.
- The API does not provide async calls, WebSockets, multipart, cookies,
  authentication, retry/backoff, proxy configuration or client certificates.

The package integration test uses a local loopback server and never contacts the
public internet.
