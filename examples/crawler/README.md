# crawler: concurrent crawler over local HTML pages

Starts at `pages/index.html`, follows local `href="..."` links round by round
(every page discovered in a round is fetched in its own task), and reports
word counts per page sorted by name, so the output is deterministic no matter
how the threads interleave. Uses `@std/concurrent` (`scope`, `spawn`,
`await_all`, `channel` for progress events, `counter` for the total, `lock`
around the shared lists), `@std/regex`, `@std/files`, `@std/lists`, `@std/text`.

```sh
sprig resolve
sprig check
sprig run                        # pages/ from index.html
sprig run -- pages about.html    # other root or start page
sprig test                       # parse_test (links, words), crawl_test (rounds, determinism)
```

A broken link is reported as `FAILED name: reason` rather than failing the
crawl; `pages/orphan.html` is never reached and never counted. Modules:
`src/crawl.spr` (everything), `src/main.spr` (arguments).

Note: a `Task[T]` result cannot be a user-defined class/variant/enum in this
compiler version, so `fetch` returns a one-element `List[Fetched]`.
