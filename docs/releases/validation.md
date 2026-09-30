# Release validation — Sprig v0.5.0-beta.1

This is the validation authority for the published `v0.5.0-beta.1` snapshot.
Published experimental Beta: compiler **0.5.0-beta.1**, language version
**0.8-dev**, JDK 17+, Apache-2.0. Linux and macOS are release-supported;
Windows is an experimental, non-blocking preview. This is not a production
stability or API compatibility promise.

## Published artifact

- [GitHub prerelease](https://github.com/ColinHouse/Sprig/releases/tag/v0.5.0-beta.1),
  published **2026-09-30 22:53:09 UTC**.
- Annotated tag `v0.5.0-beta.1` targets
  `8df389ffdeee155e53a61d3abe7e78a4348ce2ff`.
- ZIP: [`sprig-v0.5.0-beta.1-jdk.zip`](https://github.com/ColinHouse/Sprig/releases/download/v0.5.0-beta.1/sprig-v0.5.0-beta.1-jdk.zip),
  **5,922,189 bytes**.
- SHA-256: `ac295e4aff3b54e0f12a0f8ae0c2e8b1c5d7baaee331d01e4c137b4113079c93`;
  this matches the GitHub asset digest and the published
  [`.sha256` file](https://github.com/ColinHouse/Sprig/releases/download/v0.5.0-beta.1/sprig-v0.5.0-beta.1-jdk.zip.sha256).
- Release workflow [run 36785724807](https://github.com/ColinHouse/Sprig/actions/runs/36785724807)
  completed successfully. It built the exact tagged source, ran the tagged test
  suite, then downloaded and smoke-tested the same release ZIP on four Linux/
  macOS × JDK 17/26 jobs before publishing the prerelease.

## Verification evidence

| Evidence | Result |
|---|---|
| Release-preparation `./scripts/verify.sh` | Passed: 117 gates/cases, 33 independent grammar cases, 22 executable documentation snippets, VitePress production build, Markdown link audit (150 files / 333 local targets), and VS Code tests/package. |
| Required CI for release-preparation PR #64 | Passed on Linux/macOS × JDK 17/26, documentation, and editor checks. Windows preview remains non-blocking and is not claimed as supported. |
| Tagged-source release workflow | Build and full `scripts/test.py` completed successfully before publishing. |
| Downloaded ZIP smoke matrix | Passed on Ubuntu and macOS with JDK 17 and JDK 26; each job tested the actual release ZIP outside the source checkout. |
| Public asset checksum | Passed after downloading the published ZIP: `shasum -a 256 -c sprig-v0.5.0-beta.1-jdk.zip.sha256`. |
| Managed SDK upgrade | A managed `0.4.0-alpha.1` installation found the Beta with `sprig upgrade --check`, upgraded with `sprig upgrade`, then reported compiler `0.5.0-beta.1`; the previous SDK directory remained available. A subsequent check reported the Beta as current. |

The release workflow first failed once because the JSON codec test left a
generated lockfile in the tagged checkout before packaging. PR #65 fixed the
test cleanup and moved archive packaging ahead of the test run while retaining
publication gates after the tests. The tag was not moved: workflow run
36785724807 built the original Beta tag using the corrected workflow from the
merged main branch. The successful run above is the publication authority.

## Scope and limits

The Beta SDK packages the project test runner, Java wrapper generator,
schema-4 dependency locks, selected explicit Java interoperability and the
first-party CLI, HTTP, JSON, SQLite and Web packages described in the release
notes. Query an installed SDK with `sprig capabilities --json` and inspect the
asset contents for the exact shipped surface.

Windows remains experimental and non-blocking; no Windows support claim is
made. Sprig has no central package registry or language server and is not
self-hosted. Java interop is intentionally bounded. Types and checked integer
operations do not prove numerical stability, algorithmic correctness, or
production readiness. See [`v0.5.0-beta.1.md`](v0.5.0-beta.1.md) and
[`known limitations`](../language/known-limitations.md).

The previous published Alpha validation record is preserved at
[`validation-v0.4.0-alpha.1.md`](validation-v0.4.0-alpha.1.md); the versioned
release history remains in [`v0.4.0-alpha.1.md`](v0.4.0-alpha.1.md).
