# Historical release validation — Sprig v0.7.0-beta.1

This records validation of the published `v0.7.0-beta.1` snapshot. The current
release validation authority is [`validation.md`](validation.md).
Published experimental Beta: compiler **0.7.0-beta.1**, language version
**0.8-dev**, JDK 17+, Apache-2.0. Linux and macOS are release-supported;
Windows is an experimental, non-blocking preview. This is not a production
stability or API compatibility promise.

## Published artifact

- [GitHub prerelease](https://github.com/ColinHouse/Sprig/releases/tag/v0.7.0-beta.1),
  published **2026-10-06 15:14:14 UTC**.
- Annotated tag `v0.7.0-beta.1` targets
  `9ebc25c28ef085c636e0d0964a277656a210b84e`, the merge commit of #108.
- ZIP: [`sprig-v0.7.0-beta.1-jdk.zip`](https://github.com/ColinHouse/Sprig/releases/download/v0.7.0-beta.1/sprig-v0.7.0-beta.1-jdk.zip),
  **6,175,936 bytes**.
- SHA-256: `c3b72de569af172b61d476591b2cf3e19e6b8b9989f4ae2b339e0b98f9fb1f92`;
  this matches the GitHub asset digest and the published
  [`.sha256` file](https://github.com/ColinHouse/Sprig/releases/download/v0.7.0-beta.1/sprig-v0.7.0-beta.1-jdk.zip.sha256).
- Release workflow [run 37482995059](https://github.com/ColinHouse/Sprig/actions/runs/37482995059)
  completed successfully. It built the exact tagged source, ran the tagged test
  suite, then downloaded and smoke-tested the same release ZIP on four Linux/
  macOS × JDK 17/26 jobs before publishing the prerelease.

## Verification evidence

| Evidence | Result |
|---|---|
| Release-preparation `./scripts/verify.sh` (macOS, OpenJDK 26.0.1) | Passed on the #108 branch: 136 gates/cases, 36 independent grammar cases, 50 executable documentation snippets, VitePress production build, Markdown link audit (171 files / 410 local targets), and VS Code tests (33 passed, 1 Windows-only skip) plus VSIX package. |
| Release-preparation archive and integration checks | `package-alpha.py --skip-build` followed by `check-sdk-archive.py` passed on the #108 branch, and `tests/fabric/check_template.py` reported "Fabric/Loom starter integration: passed". |
| Required CI | #108 passed every check, including the non-blocking Windows preview (11 checks). |
| Tagged-source preflight | Before the tag was pushed, a clean checkout of `v0.7.0-beta.1` reported `releaseStatus` `prerelease; v0.7.0-beta.1`; `package-alpha.py` with `SPRIG_PACKAGE_VERSION=v0.7.0-beta.1` accepted it, and `check-sdk-archive.py --archive` passed on the resulting ZIP. |
| Tagged-source release workflow | Build and full `scripts/test.py` completed successfully on the tag before packaging was handed to validation (package job 14:55:17–15:12:08 UTC). |
| Downloaded ZIP smoke matrix | Passed on Ubuntu and macOS with JDK 17 and JDK 26 (15:12–15:14 UTC); each job tested the actual release ZIP outside the source checkout. |
| Release notes | The GitHub release body is `docs/releases/v0.7.0-beta.1.md`. |
| Public asset checksum | Passed after downloading the published ZIP (6,175,936 bytes): `shasum -a 256 -c sprig-v0.7.0-beta.1-jdk.zip.sha256`. The value equals the GitHub asset digest. |
| Managed SDK upgrade | In an isolated `HOME`, `install-sprig.sh --version v0.6.0-beta.1` installed the previous Beta; `sprig upgrade --check` reported v0.7.0-beta.1 as available; `sprig upgrade` switched to it and retained the previous SDK directory; `sprig version` then reported `0.7.0-beta.1` with `releaseStatus` `prerelease; v0.7.0-beta.1`, and a second check reported it as the latest published SDK. The upgraded SDK printed the new newcomer hints (`elif`, `func main() -> Unit:`, `@std/process.spr`). |

#109 (suggested edits for mechanical rewrites and three more newcomer hints)
merged into `main` after the tag was pushed, so it is not part of this
release. The tag was created on the #108 merge commit and was not moved.

## Scope and limits

The Beta SDK packages the compiler and runtime, the `sprig lsp` language
server, the bundled `@std` modules, schema-5 dependency locks, the project test
runner, the Java wrapper generator, explicit Java interoperability including
functional interfaces and varargs, the first-party CLI, HTTP, JSON, SQLite and
Web packages, and the `dev.sprig` Gradle plugin with its Fabric/Loom starter,
as described in the release notes. Query an installed SDK with
`sprig capabilities --json` and inspect the asset contents for the exact
shipped surface.

Windows remains experimental and non-blocking; no Windows support claim is
made. Sprig has no central package registry and is not self-hosted. The VS Code
extension is built from `editors/vscode` and is not part of the SDK archive.
Java interop is intentionally bounded. Types and checked integer operations do
not prove numerical stability, algorithmic correctness, or production
readiness. See [`v0.7.0-beta.1.md`](v0.7.0-beta.1.md) and
[`known limitations`](../language/known-limitations.md).

The previous published Beta validation record is preserved at
[`validation-v0.6.0-beta.1.md`](validation-v0.6.0-beta.1.md); the versioned
release history remains in [`v0.6.0-beta.1.md`](v0.6.0-beta.1.md).
