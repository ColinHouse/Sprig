# Contributing to Sprig

**Want to contribute with Codex / Claude / ChatGPT?** You can help build a
programming language without being a compiler expert. Pick an
[agent-friendly issue](https://github.com/ColinHouse/Sprig/issues?q=is%3Aissue+is%3Aopen+label%3Aagent-friendly),
give your coding agent the issue and [`AGENTS.md`](AGENTS.md), then review its patch.
AI-assisted contributions are welcome. The submitter remains responsible for
understanding the change, tests, licensing and correctness.

## First contribution

1. Install **JDK 17+**, **Python 3.12+**, **Node.js 20+ / npm**, and Git.
2. Fork and clone the repository; create a focused branch.
3. Pick a scoped issue. `good first issue` means small and reviewed;
   `agent-friendly` means it has a mechanically testable contract.
4. Read the issue, `AGENTS.md`, and the nearest passing fixture. Ask the
   compiler about capabilities and APIs before guessing language behavior.
5. Make the patch and run the canonical contributor gate from the repository root:

   ```bash
   ./scripts/verify.sh
   ```

   On Windows (PowerShell or Command Prompt; no Bash required):

   ```powershell
   py -3 scripts/verify.py
   ```

   `python3 scripts/verify.py` is equivalent on Linux/macOS. This builds the
   compiler, runs all compiler/JVM regressions and the independent grammar
   harness, executes documentation snippets, and builds/checks the website.
   First use downloads pinned build libraries and npm dependencies.
6. Review the diff yourself. Keep regression evidence, remove unrelated
   edits, and explain which commands actually passed.
7. Open a PR using the template and disclose material AI assistance.

## Query → check → repair

After the build, use `bin/sprig` (`bin\sprig.cmd` on Windows):

```bash
./bin/sprig capabilities --json
./bin/sprig help generics --json
./bin/sprig api java.time.LocalDate --json
./bin/sprig check --json path/to/example.spr
./bin/sprig explain SPR-TYPE-NULLABLE --json
```

Grammar and compiler implementation are authoritative for current behavior.
[`docs/FEATURE_STATUS_IMPLEMENTED.md`](docs/FEATURE_STATUS_IMPLEMENTED.md)
records implementation status; `spec/` describes target semantics and may differ.
Report disagreements with a reproducer. Do not improvise a language feature.

## Focused checks and release checks

During an edit, use the subsystem commands in `AGENTS.md`; before opening a
PR, run `verify`. State separately whether evidence is parser acceptance,
static checking, generated Java compilation, or JVM runtime behavior.
Never change a golden output or weaken an assertion merely to remove a failure.

Release validation additionally packages the SDK, verifies checksums/legal
notices, extracts and exercises each showcase from the archive, and runs the
Linux/macOS × JDK17/26 hosted matrix; Windows preview runs separately and is non-blocking. Maintainers record those
results in the milestone validation record. A local contributor gate does
not establish release or platform validation.

## Scope and review

- `docs`, `tests-only`, `tooling`, `stdlib`, and `compiler` describe the area.
- `design-required` means syntax, type/effect/numeric/nullability/generic
  semantics need an explicit design decision before implementation. A motivating
  program belongs in a design issue; an unrelated PR must not add syntax.
- Fix correctness with a regression test and preserve existing oracles.
- Keep `spec/` intact unless the issue explicitly concerns the design kit.
- Edit root reference docs; `website/generated/` contains generated copies.
- Do not commit `build/`, `bin/`, downloaded JARs, `node_modules/`, caches,
  generated site output, personal paths, credentials or private trial logs.
- Justify new dependencies and update `LICENSE`, `NOTICE` and
  `THIRD_PARTY_NOTICES.md` for third-party material. Contributions use Apache-2.0.

See [`AI_DISCLOSURE.md`](AI_DISCLOSURE.md) for review responsibilities and
[`docs/contributing/TRIAL.md`](docs/contributing/TRIAL.md) for the concise
contributor evaluation protocol. Neither a model's confidence nor passing
compilation replaces review of user-visible behavior.

## Protected main

Main requires a PR, an up-to-date branch, resolved review conversations and
Linux/macOS × JDK17/26 plus Documentation site checks. Rules also apply to
administrators; force pushes and deletion are disabled. Required approval count
is currently zero for this small maintainer team; this does not replace patch
review or the submitter's AI-disclosure responsibility. Windows preview is not
a required status check. Submit semantic changes for design review explicitly.
