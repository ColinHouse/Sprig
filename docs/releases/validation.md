# Release validation — Sprig v0.6.0-beta.1

This is the validation authority for the published `v0.6.0-beta.1` snapshot.
Published experimental Beta: compiler **0.6.0-beta.1**, language version
**0.8-dev**, JDK 17+, Apache-2.0. Linux and macOS are release-supported;
Windows is an experimental, non-blocking preview. This is not a production
stability or API compatibility promise.

## Published artifact

- [GitHub prerelease](https://github.com/ColinHouse/Sprig/releases/tag/v0.6.0-beta.1),
  published **2026-10-06 03:43:52 UTC**.
- Annotated tag `v0.6.0-beta.1` targets
  `d2ed998c4328330873e880b9198718ab1786b9bf`.
- ZIP: [`sprig-v0.6.0-beta.1-jdk.zip`](https://github.com/ColinHouse/Sprig/releases/download/v0.6.0-beta.1/sprig-v0.6.0-beta.1-jdk.zip),
  **6,126,199 bytes**.
- SHA-256: `b64e90cc644fa8a41202bcd59d0859d7a96d945031c09040625ea50115790990`;
  this is the GitHub asset digest; the release also publishes a
  [`.sha256` file](https://github.com/ColinHouse/Sprig/releases/download/v0.6.0-beta.1/sprig-v0.6.0-beta.1-jdk.zip.sha256).
- Release workflow [run 37408465542](https://github.com/ColinHouse/Sprig/actions/runs/37408465542)
  completed successfully. It built the exact tagged source, ran the tagged test
  suite, then downloaded and smoke-tested the same release ZIP on four Linux/
  macOS × JDK 17/26 jobs before publishing the prerelease.

## Verification evidence

| Evidence | Result |
|---|---|
| Release-preparation `./scripts/verify.py` (macOS, OpenJDK 26.0.1) | Passed on `main` with #91, #92 and #94 combined: 128 gates/cases, 33 independent grammar cases, 48 executable documentation snippets, VitePress production build, Markdown link audit (167 files / 396 local targets), and VS Code tests (33 passed, 1 Windows-only skip) plus VSIX package. |
| Release-preparation archive and integration checks | `package-alpha.py --skip-build` followed by `check-sdk-archive.py` passed on the #91 branch, and `tests/fabric/check_template.py` reported "Fabric/Loom starter integration: passed". |
| Required CI | #91 passed on Linux/macOS × JDK 17/26 and documentation after re-running jobs that GitHub's hosted runners never picked up. #94 passed every check, including the non-blocking Windows preview. |
| Tagged-source preflight | Before the tag was pushed, a clean checkout of `v0.6.0-beta.1` reported `releaseStatus` `prerelease; v0.6.0-beta.1`, and `package-alpha.py` with `SPRIG_PACKAGE_VERSION=v0.6.0-beta.1` accepted it. |
| Tagged-source release workflow | Build and full `scripts/test.py` completed successfully on the tag before packaging was handed to validation (package job 03:19:54–03:40:41 UTC). |
| Downloaded ZIP smoke matrix | Passed on Ubuntu and macOS with JDK 17 and JDK 26; each job tested the actual release ZIP outside the source checkout. |
| Release notes | The GitHub release body is `docs/releases/v0.6.0-beta.1.md`. The notes path fixed in #91 replaced the generated PR list that v0.5.0-beta.1 shipped with. |
| Public asset checksum | GitHub reports the ZIP asset digest `sha256:b64e90cc644fa8a41202bcd59d0859d7a96d945031c09040625ea50115790990`. A separate download-and-verify of the public asset was not run for this record. |
| Managed SDK upgrade | Not run for this record; the installer and upgrade paths are covered by `tests/installer` and `tests/upgrade` in the tagged test suite. |

#92 (`@std/process` exit status, standard error and standard input) merged
into `main` while the release preparation #91 was in CI, so the tag includes
it. #94 added it to the release notes, and the full local `verify.py` run above
covers the combined source. The tag was created on the #94 merge commit and
was not moved.

## Scope and limits

The Beta SDK packages the compiler and runtime, the `sprig lsp` language
server, the bundled `@std` modules, schema-5 dependency locks, the project test
runner, the Java wrapper generator, selected explicit Java interoperability,
the first-party CLI, HTTP, JSON, SQLite and Web packages, and the `dev.sprig`
Gradle plugin with its Fabric/Loom starter, as described in the release notes.
Query an installed SDK with `sprig capabilities --json` and inspect the asset
contents for the exact shipped surface.

Windows remains experimental and non-blocking; no Windows support claim is
made. Sprig has no central package registry and is not self-hosted. The VS Code
extension is built from `editors/vscode` and is not part of the SDK archive.
Java interop is intentionally bounded. Types and checked integer operations do
not prove numerical stability, algorithmic correctness, or production
readiness. See [`v0.6.0-beta.1.md`](v0.6.0-beta.1.md) and
[`known limitations`](../language/known-limitations.md).

The previous published Beta validation record is preserved at
[`validation-v0.5.0-beta.1.md`](validation-v0.5.0-beta.1.md); the versioned
release history remains in [`v0.5.0-beta.1.md`](v0.5.0-beta.1.md).
