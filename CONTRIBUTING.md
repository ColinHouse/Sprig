# Contributing

Sprig has two related parts: the v0.7 language design kit in `spec/` and the
working Java stage-0 compiler in `compiler/`. Read the root
[`README.md`](README.md), [`docs/FEATURE_STATUS_IMPLEMENTED.md`](docs/FEATURE_STATUS_IMPLEMENTED.md),
[`docs/NUMERIC_SEMANTICS.md`](docs/NUMERIC_SEMANTICS.md), and the closest
runnable example before changing compiler behavior. The design kit describes
some semantics that are still provisional; preserve disagreements as explicit
issues instead of guessing.

## Local setup

Requirements: JDK 26 or newer (the tested runtime), Python 3.12 or newer, and
`curl` for the first ANTLR download. From the repository root:

```bash
./scripts/build.sh
./scripts/test.sh
ANTLR_JAR="$PWD/tools/antlr-4.13.2-complete.jar" ./tools/test-grammar.sh
./scripts/check-docs.sh   # documented snippets + VitePress build
```

The full test script includes grammar positives/negatives, semantic
diagnostics, Java/JVM end-to-end programs, numeric boundaries,
compiler-correctness cases, parser recovery fuzzing, and independent acceptance
matrices. Do not update a golden result just to make a failing test pass: first
establish whether the source, expectation, or implementation is wrong.

## Changes and pull requests

- Keep changes focused and include a regression case for correctness fixes.
- For language features, update the grammar only when syntax needs to change;
  also update checking, Java generation/runtime behavior, diagnostics, tests,
  and documentation as applicable.
- Keep `spec/` source documents intact unless a change explicitly concerns the
  design kit. Record compiler-specific semantics under `docs/`.
- User-visible documentation lives in `website/`; its reference and project
  pages are generated from the root documents during the build. Edit the root
  document, never the generated copy.
- Report the exact commands run and distinguish parser-only checks from static
  checking and JVM execution.
- Do not commit generated `build/`, `bin/`, downloaded tool JARs,
  `website/node_modules/`, `website/.vitepress/dist/`, local paths,
  credentials, or private logs.

## AI-assisted contributions

AI-assisted work is welcome and must pass the same gates as any other change.
Disclose significant AI assistance (tool and scope), state what you verified
yourself, and never let an agent weaken a test or bypass a failing gate. The
full policy is in [`AI_DISCLOSURE.md`](AI_DISCLOSURE.md).

## License

There is no project license yet. Until the project owner chooses one, do not
publish source or binary artifacts as an open-source release. See
[`LICENSE_STATUS.md`](LICENSE_STATUS.md).
