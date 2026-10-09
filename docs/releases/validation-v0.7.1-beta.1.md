# Release validation — Sprig v0.7.1-beta.1

This is the validation authority for the published `v0.7.1-beta.1` snapshot.
Published experimental Beta: compiler **0.7.1-beta.1**, language version
**0.8-dev**, JDK 17+, Apache-2.0. Linux and macOS are release-supported;
Windows is an experimental, non-blocking preview. This is not a production
stability or API compatibility promise. It is the first release under the
[release and versioning policy](release-policy.md), as a PATCH release.

## Published artifact

- [GitHub prerelease](https://github.com/ColinHouse/Sprig/releases/tag/v0.7.1-beta.1),
  published **2026-10-07 03:52:22 UTC**.
- Annotated tag `v0.7.1-beta.1` targets
  `b7fe68eb1cc98cb018f1d07e472d69d73acf4328`, the merge commit of #119.
- ZIP: [`sprig-v0.7.1-beta.1-jdk.zip`](https://github.com/ColinHouse/Sprig/releases/download/v0.7.1-beta.1/sprig-v0.7.1-beta.1-jdk.zip),
  **6,189,534 bytes**.
- SHA-256: `be8b43520750870f4fdf080b119ac5fe3159e6a1adf8f0d8abd5e61bc334b81f`;
  this matches the GitHub asset digest and the published
  [`.sha256` file](https://github.com/ColinHouse/Sprig/releases/download/v0.7.1-beta.1/sprig-v0.7.1-beta.1-jdk.zip.sha256).
- Release workflow [run 37564884616](https://github.com/ColinHouse/Sprig/actions/runs/37564884616)
  completed successfully. It built the exact tagged source, ran the tagged test
  suite, then downloaded and smoke-tested the same release ZIP on four Linux/
  macOS × JDK 17/26 jobs before publishing the prerelease.

## Verification evidence

| Evidence | Result |
|---|---|
| Release-preparation archive and integration checks | On the #119 branch, `package-alpha.py --skip-build` followed by `check-sdk-archive.py` passed (offline help, api, doctor, check, run, probe, init, resolve and project commands and the showcases; 53 Markdown files, 59 local links), and `tests/fabric/check_template.py` reported "Fabric/Loom starter integration: passed". |
| Full contributor gate | Not rerun locally on #119. An independent review ran the full `./scripts/verify.sh` on the head of #112, which carries the release's main changes, with every stage green; its findings were fixed in #116. The required CI matrix ran the full gate on #119. |
| Required CI | #119 passed every check, including the non-blocking Windows preview (11 checks). |
| Tagged-source preflight | Before the tag was pushed, a clean checkout of `b7fe68e` reported `releaseStatus` `prerelease; v0.7.1-beta.1`; `package-alpha.py` with `SPRIG_PACKAGE_VERSION=v0.7.1-beta.1` accepted it, and `check-sdk-archive.py --archive` passed on the resulting ZIP. |
| Tagged-source release workflow | Build and full `scripts/test.py` completed successfully on the tag before packaging was handed to validation (package job 03:03:57–03:21:54 UTC). |
| Downloaded ZIP smoke matrix | Passed on Ubuntu and macOS with JDK 17 and JDK 26 (03:28–03:45 UTC); each job tested the actual release ZIP outside the source checkout. |
| Release notes | The GitHub release body is `docs/releases/v0.7.1-beta.1.md` as of the tag. |
| Public asset checksum | Passed after downloading the published ZIP (6,189,534 bytes): `shasum -a 256 -c sprig-v0.7.1-beta.1-jdk.zip.sha256`. The value equals the GitHub asset digest. |
| Managed SDK upgrade | In an isolated `HOME`, `install-sprig.sh --version v0.7.0-beta.1` installed the previous Beta; `sprig upgrade --check` reported v0.7.1-beta.1 as available; `sprig upgrade` switched to it and retained the previous SDK directory; `sprig capabilities --json` then reported compiler `0.7.1-beta.1`, language `0.8-dev` and `releaseStatus` `prerelease; v0.7.1-beta.1`, and a second check reported it as the latest published SDK. The upgraded SDK rejected a nullable join with `SPR-TYPE-NULLABLE`, printed an `Error`'s message from `toString()`, returned `1` from `"b".compareTo("a")` and ran a 3,000,000-iteration `for` over `range`. |

#118 (quick fixes in `sprig lsp`) merged into `main` 34 seconds after #119.
The tag was created on the #119 merge commit, so #118 is not part of this
release, and the tag was not moved.

## Scope and limits

The Beta SDK packages the compiler and runtime, the `sprig lsp` language
server, the bundled `@std` modules, schema-5 dependency locks, the project test
runner, the Java wrapper generator, explicit Java interoperability including
functional interfaces, varargs, `Int` arguments for `int` parameters and
wildcard bounds, the first-party CLI, HTTP, JSON, SQLite and Web packages, and
the `dev.sprig` Gradle plugin with its Fabric/Loom starter, as described in the
release notes. Query an installed SDK with `sprig capabilities --json` and
inspect the asset contents for the exact shipped surface.

Windows remains experimental and non-blocking; no Windows support claim is
made. Sprig has no central package registry and is not self-hosted. The VS Code
extension is built from `editors/vscode` and is not part of the SDK archive.
Java interop is intentionally bounded. Types and checked integer operations do
not prove numerical stability, algorithmic correctness, or production
readiness. See [`v0.7.1-beta.1.md`](v0.7.1-beta.1.md) and
[`known limitations`](../language/known-limitations.md).

The previous published Beta validation record is preserved at
[`validation-v0.7.0-beta.1.md`](validation-v0.7.0-beta.1.md); the versioned
release history remains in [`v0.7.0-beta.1.md`](v0.7.0-beta.1.md).
