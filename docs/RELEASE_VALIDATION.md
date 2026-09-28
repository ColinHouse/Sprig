# Release validation — Sprig v0.4.0-alpha.1

This is the validation authority for the published `v0.4.0-alpha.1` snapshot.
Published experimental Alpha: compiler **0.4.0-alpha.1**, JDK17+, Apache-2.0.
Supported: **Linux/macOS**. Windows is **experimental and non-blocking**
(issue #33). A newer release replaces this record.

## Published artifact and evidence

- [Release](https://github.com/ColinHouse/Sprig/releases/tag/v0.4.0-alpha.1):
  annotated tag `v0.4.0-alpha.1`, target commit
  `b6077e0f72129618d64ccde8d028fd6531f0f606`, published 2026-09-28 as a
  prerelease.
- ZIP: `sprig-v0.4.0-alpha.1-jdk.zip`, 5,755,077 bytes.
- ZIP SHA256: `28faf79002bb6d7f532e77481e1cee5d4f29ddfc8574b0adb803c3cfe77250c8`
  (published `.sha256` asset; matches GitHub's asset digest).
- Exact main CI: [run 36442388309](https://github.com/ColinHouse/Sprig/actions/runs/36442388309)
  — Linux/macOS × JDK17/26 and Documentation site passed.
- [Release workflow 36442388300](https://github.com/ColinHouse/Sprig/actions/runs/36442388300):
  clean tagged build, full `scripts/test.py` on the tag, `SPRIG_PACKAGE_VERSION`
  packaging, four downloaded-SDK jobs (Linux/macOS × JDK17/26) smoke-testing the
  same ZIP, then prerelease publication. All jobs passed.

### Downloaded-release audit (2026-09-28)

- Assets re-downloaded from the release (not the local build directory);
  `shasum -a 256 -c` passed and the digest equals GitHub's asset digest.
- The tagged `tools/check-sdk-archive.py --archive <downloaded.zip>` passed:
  spaced extraction, checksum, standalone bundled `@std` run, Java-only output,
  `hello`/probe/`init`/`resolve`/`api`/`doctor`, and all three showcase projects.
- `BUILD_INFO.txt` records package `v0.4.0-alpha.1`, source revision `b6077e0`,
  clean working tree, and a JDK 17 build.
- Fresh-extraction smoke passed outside the repository: `version`, `doctor
  --json`, `capabilities --json` (`releaseStatus=prerelease; v0.4.0-alpha.1`),
  `help agents --json`, `api java.time.LocalDate --json`, `check`/`run`/`fmt
  --check`/`explain` on `examples/hello.spr` (`Hello, Ada!`).

## Verification performed before tagging

Base: `main` at `7471a37` plus the pre-release correctness fixes merged as
`b6077e0` (indexed-assignment key/index checking, nullable scalar equality,
left-to-right `in`, missing-key compound assignment). No syntax, type or
generic redesign; no runtime application behavior changed beyond the documented
fixes.

| Actual command | Result |
|---|---|
| `./scripts/verify.sh` | Build, `scripts/test.py` **106 gates/cases, 0 failed**, grammar **31** cases, documentation snippets **20/20**, VitePress production site and editor TextMate/CLI/JVM/VSIX passed |
| `python3 tools/check-tooling-consistency.py` | Version, language, JDK, license, commands, topics, codes and release status consistent |
| Adversarial regressions (in `scripts/test.py`) | 46 semantic attacks; 36 type-name checks; 16 newline attacks; 8 JVM bridge checks; 24 seeded variants/96 exact comparisons; 12 hostile installer archives; fresh managed SDK end-to-end — all passed |
| `python3 scripts/package-alpha.py --skip-build` and `python3 tools/check-sdk-archive.py` | Passed locally for `sprig-v0.4.0-alpha.1-jdk.zip` before the tag |

## Corrections landed with this release

`b6077e0` fixed ten adversarial defects plus two contract clarifications found
by the final Alpha audit; the full list is in
[`docs/releases/RELEASE_NOTES-v0.4.0-alpha.1.md`](releases/RELEASE_NOTES-v0.4.0-alpha.1.md)
and [`docs/milestones/ADVERSARIAL_CORRECTNESS_AUDIT.md`](milestones/ADVERSARIAL_CORRECTNESS_AUDIT.md).

## Limits

Publishing/registry/authentication, Maven plugins, non-JAR runtime artifacts,
full LSP/IDE services, generic inference/variance, interfaces/traits and
self-hosting remain absent. Java generic/array/varargs/SAM adapters are limited.
Windows preview jobs are non-blocking; their pre-existing failures are tracked
in issue #33 and no Windows correctness pass is claimed. Cache locks are
cooperative and local locks contain absolute paths. Milestone reports and
`docs/milestones/plans/` are history; `docs/README.md` defines the trust order.
Types do not prove algorithmic correctness or numerical stability.
