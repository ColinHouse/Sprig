# Mini HTTP experiment

From this directory:

```sh
../../bin/sprig resolve
../../bin/sprig run
```

The server binds an ephemeral localhost port and prints `PORT=...`.
Use that port with GET `/`, GET `/hello/{name}?greeting=Hi`, POST `/echo`,
GET `/openapi.json`, GET `/docs`, and DELETE `/delete/{id}`. POST `/echo` expects
JSON, for example `{"message":"Hello"}`. `/fail` deliberately produces a controlled
500 using an invalid JSON response value. Ctrl+C ends the server.

Run the earlier raw host experiment with `../../bin/sprig run src/host_probe.spr`.
It handles GET `/`, GET `/hello/name` and POST `/echo` entirely in Sprig and uses
only the Java transport boundary. `/echo` in the raw probe returns UTF-8 body text.

The Swagger page uses pinned CDN assets and requires network access. API tests
run without fetching them. See `libraries/sprig-web/README.md` for the precise
source API and callback error-handling rules.
