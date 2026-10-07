# blog: static blog generator

Reads Markdown-ish posts from `posts/` (a `title:` line, a `date: YYYY-MM-DD`
line, a blank line, then paragraphs; `**bold**` and `` `code` `` inline) and
writes `site/<slug>.html` for every post plus `site/index.html` (newest
first) and `site/style.css`. Uses `@std/files`, `@std/text` and `@std/lists`.

```sh
sprig resolve
sprig check
sprig run                       # posts/ -> site/
sprig run -- other/posts out    # explicit directories
sprig test                      # tests/parse_test, render_test, end_to_end_test
```

A post without `title:`/`date:` or with a malformed date exits with status 2
and names the file; a missing posts directory exits with status 1.

Modules: `src/post.spr` (parsing, HTML rendering), `src/site.spr` (directory
walk and output), `src/main.spr` (arguments, exit codes).
