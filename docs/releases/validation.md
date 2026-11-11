# Release validation — Sprig v0.9.0-beta.1

This is the validation authority for the published `v0.9.0-beta.1` snapshot.
Published experimental Beta: compiler **0.9.0-beta.1**, language version
**0.8-dev**, separate JDK **21+**, Apache-2.0. Linux and macOS are
release-supported; Windows remains an experimental, non-blocking preview.
This is not a production-stability or frozen-language promise. It is a new
MINOR under the [release and versioning policy](release-policy.md): Java code
that names a generated Sprig class has to add the project's package.

## Published artifact

- [GitHub prerelease](https://github.com/ColinHouse/Sprig/releases/tag/v0.9.0-beta.1),
  published **2026-11-11 11:06:14 UTC**.
- Tag `v0.9.0-beta.1` targets `3778d50e3ed280f2ed2c51f658b53e379b9d26ce`,
  the merge commit of #241. The published tag has not been moved.
- ZIP: [`sprig-v0.9.0-beta.1-jdk.zip`](https://github.com/ColinHouse/Sprig/releases/download/v0.9.0-beta.1/sprig-v0.9.0-beta.1-jdk.zip),
  **6,963,247 bytes**.
- SHA-256: `303e98ba062d50a5b3a97b28697905249234c359e3445e97dad645cb7098e114`.
  A fresh download matched both the GitHub asset digest and the published
  [`.sha256` file](https://github.com/ColinHouse/Sprig/releases/download/v0.9.0-beta.1/sprig-v0.9.0-beta.1-jdk.zip.sha256).
- Release workflow [run 43006374385](https://github.com/ColinHouse/Sprig/actions/runs/43006374385)
  completed successfully on the tagged commit.

## Verification evidence

| Evidence | Result |
|---|---|
| Before the tag | The preparation commit passed `./scripts/verify.sh`, the SDK archive check and the Loom build of the Fabric starter (#241). In a clean checkout of the merge commit, the build reported `releaseStatus` `prerelease; v0.9.0-beta.1`, and `SPRIG_PACKAGE_VERSION=v0.9.0-beta.1 python3 scripts/package-alpha.py` followed by `python3 scripts/check-sdk-archive.py --archive` passed. |
| Tagged-source packaging | The release workflow's `package` job completed successfully. See the workflow run for its individual build, test and archive steps. |
| Downloaded ZIP smoke matrix | All four release jobs passed: Ubuntu and macOS, each with JDK 21 and JDK 26. These jobs test the downloaded SDK, not only the source checkout. |
| Publication | The release job passed; the public prerelease contains the ZIP and its checksum asset. |
| Local onboarding, 2026-11-11 | A fresh official ZIP was downloaded, its SHA-256 verified, and extracted in a temporary directory on macOS with JDK 26.0.1. `sprig version` printed `sprig-compiler 0.9.0-beta.1`; `sprig doctor --json` reported `jdkMinimum: 21` and `javacAvailable: true`. `sprig init hello`, `sprig resolve` and `sprig run` each exited 0; the program printed `Hello, Sprig!`. |
| Installed identity | The same extracted SDK's `sprig capabilities --json` reported compiler `0.9.0-beta.1`, language `0.8-dev` and `releaseStatus` `prerelease; v0.9.0-beta.1`. |
| Managed upgrade, 2026-11-11 | In an isolated home directory on the same machine, `install-sprig.sh --version v0.8.0-beta.1` installed the previous release. `sprig upgrade --check` reported v0.9.0-beta.1; `sprig upgrade` switched to it and kept v0.8.0-beta.1; `sprig version` then printed `sprig-compiler 0.9.0-beta.1`. |

The local checks did not run Windows or manually exercise every editor
command. Those results are not inferred from them.

## Scope and limits

The SDK includes the tutorial's 0.8 syntax and standard modules, Java
interoperability, the language server, project/dependency tools, Gradle/JVM
integration and the Fabric starter. See the [release notes](v0.9.0-beta.1.md)
for additions, the incompatible change and library limits; query an installed
SDK with `sprig capabilities --json` for its actual surface.

Two Sprig mods in one Minecraft game were checked before the release, on the
commit that introduced the per-project package (#240): a Minecraft 26.3 client
started with the Fabric starter and the waypoint example installed together.
That check needs Minecraft and is not part of the release workflow.

The [VS Code extension](https://marketplace.visualstudio.com/items?itemName=ColinHouse.sprig-language)
is published separately as `ColinHouse.sprig-language`; its version is
**0.3.1**, independent of the SDK and language versions, and this release
did not change it. The SDK ZIP does not install the extension or a JDK.

Windows remains experimental. Java interoperability is bounded, the compiler
is not self-hosted, and type checking does not prove numerical stability or
application correctness. See [known limitations](../language/known-limitations.md).

## Previous releases

The preceding published SDK's evidence is preserved unchanged in
[validation-v0.8.0-beta.1.md](validation-v0.8.0-beta.1.md); its release notes
are [v0.8.0-beta.1.md](v0.8.0-beta.1.md). Earlier evidence remains in
[validation-v0.7.1-beta.1.md](validation-v0.7.1-beta.1.md) and
[validation-v0.7.0-beta.1.md](validation-v0.7.0-beta.1.md).
