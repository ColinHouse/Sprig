# Maven text → HTML utility

Commons Text 1.12.0 supplies real capitalization and HTML entity escaping,
with Commons Lang resolved transitively. Sprig owns the CLI, ASCII slug and IO.
From this directory:

```sh
sprig resolve
sprig api org.apache.commons.text.StringEscapeUtils --json
sprig check
sprig run
sprig run -- 'hello <JVM> & sprig' article.html
sprig run --offline
```

Default output:

```html
<article id="sprig-the-jvm"><h1>Sprig &amp; The Jvm</h1></article>
```

The second argument writes UTF-8 HTML. Offline run requires the lock and cached
artifacts produced by resolve; it verifies hashes without graph re-resolution.
Inspect `sprig.lock` to see the transitive graph and provenance.
