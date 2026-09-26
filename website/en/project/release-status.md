# Release status

**Current prerelease:** [v0.2.0-alpha.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.2.0-alpha.1)
**Language:** `v0.8-dev` · **JDK:** 17+ · **License:** Apache-2.0

The second public prerelease includes multi-parameter explicit generics,
Equatable, local/Git dependency resolution with schema-2 locks and offline
builds, Agent tooling and explicit JVM `--classpath`.

Assets: `sprig-v0.2.0-alpha.1-jdk.zip` and its `.sha256`. No JDK is bundled.
Verify the checksum before extracting; run version/capabilities/check/run.
The release workflow rebuilds, runs the full suite, checks tag/version equality
and smoke-tests the archive before publishing. CI covers JDK 17 and 26 on Linux;
local verification covers macOS Apple Silicon. Classes use `javac --release 17`.

The [release notes](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/RELEASE_NOTES-v0.2.0-alpha.1.md)
and [sole validation record](https://github.com/ColinHouse/Sprig/blob/main/docs/post-v0.7/V08_VALIDATION_REPORT.md)
record scope and evidence. Published alpha.1 notes remain in Git history and docs/releases.

This is experimental. Maven/project-aware Maven classpaths, publishing/registry,
Comparable, inference, variance, interfaces/traits and LSP/IDE are unsupported.
The stage-1 frontend is a probe, not a self-hosted compiler. Type safety does
not certify numerical stability. See [limitations](/en/reference/KNOWN_LIMITATIONS).
