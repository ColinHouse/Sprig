# Release validation — Sprig v0.8.0-beta.1

This is the validation authority for the published `v0.8.0-beta.1` snapshot.
Published experimental Beta: compiler **0.8.0-beta.1**, language version
**0.8-dev**, separate JDK **21+**, Apache-2.0. Linux and macOS are
release-supported; Windows remains an experimental, non-blocking preview.
This is not a production-stability or frozen-language promise.

## Published artifact

- [GitHub prerelease](https://github.com/ColinHouse/Sprig/releases/tag/v0.8.0-beta.1),
  published **2026-10-08 16:25:30 UTC**.
- Tag `v0.8.0-beta.1` targets `13526327975e8ec8adf02a1cdf35419d235f1dd0`,
  the merge commit of #230. The published tag has not been moved.
- ZIP: [`sprig-v0.8.0-beta.1-jdk.zip`](https://github.com/ColinHouse/Sprig/releases/download/v0.8.0-beta.1/sprig-v0.8.0-beta.1-jdk.zip),
  **6,925,087 bytes**.
- SHA-256: `9c926aa10c7ec915deb76f5782be11137988a29a68d379bc194a11f8e3c08bab`.
  A fresh download matched both the GitHub asset digest and the published
  [`.sha256` file](https://github.com/ColinHouse/Sprig/releases/download/v0.8.0-beta.1/sprig-v0.8.0-beta.1-jdk.zip.sha256).
- Release workflow [run 37806498884](https://github.com/ColinHouse/Sprig/actions/runs/37806498884)
  completed successfully on the tagged commit.

## Verification evidence

| Evidence | Result |
|---|---|
| Tagged-source packaging | The release workflow's `package` job completed successfully. See the workflow run for its individual build, test and archive steps. |
| Downloaded ZIP smoke matrix | All four release jobs passed: Ubuntu and macOS, each with JDK 21 and JDK 26. These jobs test the downloaded SDK, not only the source checkout. |
| Publication | The release job passed; the public prerelease contains the ZIP and its checksum asset. |
| Local onboarding, 2026-10-09 | A fresh official ZIP was downloaded, its SHA-256 verified, and extracted in a temporary directory on macOS with JDK 26.0.1. `sprig version` printed `sprig-compiler 0.8.0-beta.1`; `sprig doctor --json` reported `jdkMinimum: 21` and `javacAvailable: true`. `sprig init hello`, `sprig resolve` and `sprig run` each exited 0; the program printed `Hello, Sprig!`. |
| Installed identity | The same extracted SDK's `sprig capabilities --json` reported compiler `0.8.0-beta.1`, language `0.8-dev` and `releaseStatus` `prerelease; v0.8.0-beta.1`. |

The local check used the manually extracted ZIP, not the managed installer or
an upgrade from an older SDK. It did not run Windows or manually exercise every
editor command. Those results are not inferred from the onboarding smoke check.

## Scope and limits

The SDK includes the tutorial's 0.8 syntax and standard modules, Java
interoperability, the language server, project/dependency tools, Gradle/JVM
integration and the Fabric starter. See the [release notes](v0.8.0-beta.1.md)
for additions, incompatible changes and library limits; query an installed SDK
with `sprig capabilities --json` for its actual surface.

The [VS Code extension](https://marketplace.visualstudio.com/items?itemName=ColinHouse.sprig-language)
is published separately as `ColinHouse.sprig-language`; its current version is
**0.3.1**, independent of the SDK and language versions. The Marketplace listing
was checked on 2026-10-09. The SDK ZIP does not install the extension or a JDK.

Windows remains experimental. Java interoperability is bounded, the compiler
is not self-hosted, and type checking does not prove numerical stability or
application correctness. See [known limitations](../language/known-limitations.md).

## Previous releases

The preceding published SDK's evidence is preserved unchanged in
[validation-v0.7.1-beta.1.md](validation-v0.7.1-beta.1.md); its release notes
are [v0.7.1-beta.1.md](v0.7.1-beta.1.md). Earlier evidence remains in
[validation-v0.7.0-beta.1.md](validation-v0.7.0-beta.1.md).
