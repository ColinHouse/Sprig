# Sprig — publication readiness report

> **Update (owner decisions applied, 2026-09-25):** the project owner selected
> **Apache-2.0** (with its express patent grant) and confirmed the repository
> destination <https://github.com/ColinHouse/Sprig>. A `LICENSE`, `NOTICE`,
> README badges and icon, repository About metadata, a Pages deployment
> workflow and a tag-driven prerelease workflow were added, and the site now
> defaults to the `/Sprig/` base path. The original license/repository/asset
> blockers below are therefore resolved or explicitly owned by the project
> owner; the remaining technical caveats (Java 17 runtime not observed,
> documented limitations) still apply.

**Round date:** 2026-09-25
**Scope:** repository organization, documentation site, README/contribution
documents, license and asset review, and a clean-environment audit. This round
did not start stage-1 self-hosting, add language features, or create a tag,
GitHub Release or deployment.

**Bottom line:** the repository and documentation site are ready to show
publicly **after** the owner decides on a license, confirms the icon/mascot
rights, and confirms the repository destination. The compiler itself is an
alpha: it is not yet release-ready (Java 17 runtime and hosted CI remain
unverified, and known limitations are documented rather than fixed).

## 1. Repository after organization

Public history root commit: `f737b51` (initial public state), plus `8ca8809`
(website/acceptance READMEs) and the commit that adds this report and the
cleanup record. A local-only branch `pre-cleanup-snapshot` holds the old
`output/`-era tree for recovery; **it must not be pushed**.

```text
compiler/     Java stage-0 compiler (only compiler source tree)
runtime/      Java runtime for generated programs
grammar/      SprigLexer.g4, SprigParser.g4 + audit note
spec/         v0.7 design kit (docs only; no duplicate grammar/tests)
docs/         numeric contract, design decisions, codes, status, limits,
              stage-1 roadmap, draft release notes
examples/     five runnable examples
tests/        syntax, semantics, runtime, visitor, numeric, correctness,
              recovery (single executable corpus)
acceptance/   independent cases, scripts, generated results (gitignored)
scripts/      build.sh, test.sh, check-docs.sh, package-alpha.sh, check_cases.py
tools/        ANTLR jar (download-on-build), grammar harness, static checks,
              documentation snippet verifier
website/      VitePress site with package-lock.json
.github/      CI and prepared Pages deployment workflows
```

Generated `build/`, `bin/`, `dist/`, `sprig-build/`, `acceptance/results/`,
`website/node_modules/`, `website/generated/` and the VitePress output are
gitignored.

## 2. Grammar audit (`.g4`)

- `grammar/SprigLexer.g4` and `grammar/SprigParser.g4` are the only grammar
  files; the former `output/grammar/` copies were byte-identical duplicates and
  were removed.
- Every syntax form used by the compiler and by the full `.spr` corpus is
  accepted; all 14 syntax-negative fixtures are rejected as expected
  (`tools/test-grammar.sh`, 21/21).
- No grammar correction was required. The real defect found was in the
  separate reference harness: synthetic layout tokens lacked a `TokenSource`,
  which could make ANTLR recovery throw. It is fixed in
  `tools/grammar-harness/LayoutTokenSource.java` and documented in
  `grammar/README.md`.

## 3. Documentation site

- VitePress **1.6.4** (pinned, `package-lock.json` committed), built from
  `website/` and maintained in the same repository as the compiler.
- 23 content pages: home, getting started, language tour, JVM interop,
  tooling/JSON, examples, 12 reference pages and 5 project pages.
- Reference/project pages are generated from the authoritative root documents
  at build time (`website/scripts/sync-reference.mjs`); generated pages are
  gitignored, so nothing is duplicated in git. Dead links fail the build.
- The base path defaults to `/sprig/` for a GitHub Pages project site and is
  overridden with `DOCS_BASE` (the Pages workflow derives it from
  `actions/configure-pages`). Builds were verified with both `/sprig/` and `/`.
- Homepage, navigation, sidebar, code blocks, snippet includes, favicon,
  logo, OG image and mobile layout use the default VitePress theme with a
  small palette customization; the production build passes.
- Deployment workflow is prepared (`.github/workflows/docs.yml`) but has not
  run: no repository, Pages configuration or deployment is claimed.

## 4. Icon usage

`icon.png` (1254×1254, project root) is the only artwork. It is used, without
redesign or recoloring, to derive:

| Asset | Derivation |
|---|---|
| `website/public/logo.png` | 192×192 resize |
| `website/public/favicon-32.png`, `favicon-16.png` | 32/16 px resizes |
| `website/public/apple-touch-icon.png` | 180×180 resize |
| `website/public/og-image.png` | 1200×630 canvas with the original centered |

**Open item:** the owner must confirm the artwork's origin/rights, and in
particular the cat silhouette on the laptop and mug, which resembles GitHub's
Octocat mark. No claim of original or unrestricted licensing is made; see
`THIRD_PARTY_NOTICES.md`.

## 5. README, AGENTS, contribution and AI documents

- `README.md` rewritten for external readers: what Sprig is, alpha status,
  what works, build and first program, documentation pointers, repository
  layout, limitations, license and AI disclosure. No invented benchmarks or
  success rates. Includes a short Chinese summary whose facts mirror the
  English text.
- `AGENTS.md` added with source-of-truth map, required commands, language
  change checklist, testing rules and boundaries.
- `CONTRIBUTING.md` updated for the new layout and links to the AI policy.
- `AI_DISCLOSURE.md` created and linked once from README/CONTRIBUTING/site.
- `LICENSE_STATUS.md` expanded with component coverage and candidate license
  directions; no license chosen.
- `THIRD_PARTY_NOTICES.md` expanded: ANTLR BSD text, website toolchain and
  Inter font (OFL), icon provenance item.
- `docs/FEATURE_STATUS_IMPLEMENTED.md` no longer points at removed historical
  reports; `tests/README.md`, `spec/README-DESIGN-KIT.md` and
  `spec/AGENTS-DESIGN-KIT.md` were corrected for the merged layout.

## 6. License and third-party status

| Item | Status |
|---|---|
| Project license | **Not selected — blocking.** Owner must choose and add license text. |
| ANTLR 4.13.2 | BSD, reproduced; jar downloaded at build with pinned SHA-256, not committed. |
| Website dependencies | VitePress/Vue/Shiki/MiniSearch MIT; Inter font OFL-1.1; listed in notices. |
| Icon/mascot | Owner-supplied; provenance and third-party marks **unconfirmed — blocking**. |
| Fonts/images/data | No other third-party assets committed. |

## 7. Cleanup summary

Full details are in `PUBLICATION_CLEANUP_RECORD.md`. Counts of tracked files
removed or moved: 56 deletions, 3 renames, 14 modified, 48 added relative to
the pre-cleanup snapshot. Nothing was deleted before an external tarball
backup and a local Git snapshot commit existed.

- **Removed from the public tree:** `output/` (historical reports, duplicate
  grammar, duplicate ANTLR jar), `spec/tests/`, `spec/examples/` (byte-equal
  or strict subsets of `tests/`), stale design-kit status/worksheets,
  `acceptance/results/env.txt` from tracking.
- **Archived outside the repository:** `../Sprig-local-archive/` holds the
  pre-publication tarball, the historical `output/` reports and the old
  independent acceptance report.
- **Moved:** reference harness to `tools/grammar-harness/`; draft release
  notes to `docs/releases/`.
- **Merged/kept authoritative:** `tests/` owns the executable corpus;
  `docs/` owns implementation semantics; `spec/` owns the v0.7 design kit;
  `grammar/` owns syntax.

## 8. Clean-environment verification

Environment: macOS Apple Silicon, OpenJDK 26.0.1, Python 3.14.6, Node.js
24.16.0, ANTLR 4.13.2 (SHA-256 `eae2dfa1…`). The clean clone had no build
outputs, launcher or ANTLR jar; the build downloaded the tool and verified its
digest.

| Command | Result |
|---|---|
| `./scripts/build.sh` (clean clone) | Passed; ANTLR downloaded and checksum-verified |
| `./scripts/test.sh` (clean clone) | **100 passed, 0 failed** |
| `./tools/test-grammar.sh` | **21/21 passed** |
| `./scripts/check-docs.sh` | **17/17 documented snippets/examples passed; VitePress production build passed** |
| F1 incomplete expression (`check --json`) | `SPR-SYNTAX-ERROR`, exit 1 (no internal error) |
| F2 zero-argument lambda | `check`/`build`/`run` consistent; program runs |
| F3 `Int32` compound literal | `counter += 1` and `counter = counter + 1` both run and print `6`; checked arithmetic unchanged |
| F4 nullable → Java reference formal | `SPR-TYPE-NULLABLE`, no silent NPE path |
| `scripts/package-alpha.sh` | Archive built and SHA-256 emitted; extracted archive passed `version`, `check`, `run` |

Compiler evidence levels: parser acceptance (grammar harness), static
checking (semantics/correctness/recovery), `javac` success and JVM behavior
(runtime/examples/acceptance) were exercised separately.

## 9. Security and privacy review

- No credentials, tokens, private keys or personal absolute paths found in
  tracked files; the only absolute paths live in ignored build outputs and
  test logs.
- The public branch has a clean two-commit history containing only the
  reviewed tree. The local `pre-cleanup-snapshot` branch contains historical
  working documents and must not be pushed; if the repository is initialized
  from a fresh directory instead, keep the backup outside it.
- Raw acceptance logs and JSON are gitignored because they contain machine
  paths and environment details.

## 10. Remaining correctness issues and limitations

- `runtime/.../SprigList.java` still emits a `javac` unchecked-operations
  note; it does not fail the build and needs a focused generic-varargs audit.
- Uncaught runtime numeric errors carry the source file but not a precise
  arithmetic-expression span.
- Java 17 runtime execution is unverified (source is compiled with
  `--release 17`); the documented requirement is JDK 26 or newer.
- CI and the Pages workflow are configured but have never run on GitHub.
- Full Java generics/annotations, arrays/varargs, `short`/`byte` adapters,
  standard library, package manager, LSP and stage-1 self-hosting remain
  unimplemented. These are documented, not hidden.

## 11. Public-push / release blockers

1. **License** — owner must select and record a project license; no release
   may be published before that.
2. **Icon rights** — owner must confirm artwork provenance and the possible
   third-party mark before publishing the site or repo.
3. **Repository identity** — owner must confirm the public owner/name and the
   Pages address (project site vs user site vs custom domain).
4. **Hosted checks** — run CI on the accepted commit and configure Pages
   before claiming a deployment; do not create the tag/Release yet.
5. **Do not push the local backup branch** or the local archive directory.

**Status distinction:** *publicly presentable repository and website* — ready
pending items 1–3; *formally releasable compiler* — not yet, pending items
1–4 and the documented limitations.

## 12. Files added for this audit

- `PUBLICATION_READINESS_REPORT.md` (this file)
- `PUBLICATION_CLEANUP_RECORD.md`
