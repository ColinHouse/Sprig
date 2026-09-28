# Release validation — Sprig v0.4.0-alpha.1 (release candidate)

Release candidate: compiler **0.4.0-alpha.1**, JDK17+, Apache-2.0. The latest
**published** release remains `v0.3.0-alpha.1`; no v0.4 publication is claimed
until the annotated tag workflow completes. Supported: **Linux/macOS**. Windows
is **experimental and non-blocking** (issue #33). After publication, the tag
target, workflow run links, published ZIP SHA256 and downloaded-release audit
are recorded here.

## Publication workflow

- [Release](https://github.com/ColinHouse/Sprig/releases/tag/v0.4.0-alpha.1):
  annotated tag `v0.4.0-alpha.1`, built by the exact-tag
  [release workflow](../.github/workflows/release.yml).
- ZIP: `sprig-v0.4.0-alpha.1-jdk.zip` with its `.sha256` asset; the SHA256
  published next to the asset is authoritative.
- The release workflow builds and runs `scripts/test.py` on the clean tagged
  commit, packages with `SPRIG_PACKAGE_VERSION`, downloads the ZIP into four
  supported-platform jobs (Linux/macOS × JDK17/26) and smoke-tests it with
  `tools/check-sdk-archive.py --archive` before publishing the prerelease.
- Downloaded-release audit (append after publication): tag target SHA, run/job
  links, published ZIP SHA256, and the archive check run against the downloaded
  assets.

## Verification performed before tagging (2026-09-28)

Base: `main` at `2edfef7398b11094009248df8e427d3ba3c485c6` (repository hygiene
merged), plus this release's version, notes and documentation updates. Language
syntax, numeric, nullability, generic and effect contracts are unchanged.

| Actual command | Result |
|---|---|
| `./scripts/verify.sh` | Build, `scripts/test.py` **102 gates/cases, 0 failed**, grammar **31** cases, documentation snippets **20/20**, VitePress production site and editor TextMate/CLI/JVM/VSIX passed |
| `python3 scripts/package-alpha.py --skip-build` and `python3 tools/check-sdk-archive.py` | Spaced extraction, checksum, standalone `@std` run, Java-only output, hello/probe/init/resolve/api/doctor and all three showcases passed |
| `tools/check-doc-links.py` | 124 Markdown files, 217 local targets passed |
| `tools/check-tooling-consistency.py` | Version, language, JDK, license, commands, topics, codes and release status consistent |
| Adversarial regressions (wired into `scripts/test.py`) | `check_semantics` 35 attacks; `check_type_names` 36 checks with formatter identity; `check_layout_lines` 16 newline attacks; `check_jvm_bridges` 8 checks against an independent `javac`/`java` oracle; `check_properties` 24 seeded variants / 96 exact JVM comparisons / 24 missing-case rejections; `check_install_failures` 12 hostile SDK ZIPs preserving the previous SDK; `check_sdk_composition` fresh managed SDK end-to-end |
| Failure-injection evidence | Reverting only `JvmMetadata.java` leaves the bridge suite at 4/8 failures; the installation suite overwrites an external sentinel on the unfixed installer. Both are fixed in this release |
| `python3 scripts/test-grammar.py` | 31 syntax cases passed; parser acceptance is not runtime proof |

The correctness pass is documented in
[`docs/milestones/ADVERSARIAL_CORRECTNESS_AUDIT.md`](milestones/ADVERSARIAL_CORRECTNESS_AUDIT.md):
A01 inferred globals, A02 default effects, A06 generated type names, A07 `finally`
completion, A08 CR positions, A10 JVM bridge resolution, A11 unnamed-package
imports and A12 installer ZIP hardening. A03 (named-argument evaluation order)
and A09 (module read-before-initialization) remain open design decisions.

## Limits

Publishing/registry/authentication beyond GitHub releases, Maven plugins, non-JAR
runtime artifacts, full LSP/IDE services, generic inference/variance,
interfaces/traits and self-hosting remain absent. Java generic/array/varargs/SAM
adapters are limited. Windows source CI is non-blocking and its pre-existing
failures are tracked in issue #33; no Windows SDK correctness pass is claimed.
Cache locks are cooperative and local locks contain absolute paths. Milestone
reports and `docs/milestones/plans/` are history; `docs/README.md` defines the
trust order. Types do not prove algorithmic correctness or numerical stability.
