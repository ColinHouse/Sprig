# Release status

**Published SDK:** [v0.3.0-alpha.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.3.0-alpha.1), compiler `0.3.0-alpha.1`, language
`0.8-dev`, JDK17+, Apache-2.0. Experimental Alpha, not production-ready.

Linux/macOS × JDK17/26 passed source and the same actual tagged SDK ZIP gates.
Windows is an experimental, non-blocking preview. The SDK includes Maven
Resolver, locked project classpaths, explicit `@std` IO/JSON modules and three
showcases. `build --emit-java-only` exposes checked generated Java before javac.

See the [single validation record](https://github.com/ColinHouse/Sprig/blob/main/docs/RELEASE_VALIDATION.md) for source SHA, archive SHA256,
commands, actual CI links and limits. Untagged source builds report development,
based on the published release; clean matching tagged builds report prerelease.
No JDK is bundled. Formatter, Java SAM/arrays/general generic adapters, LSP/IDE
and self-hosting remain future work. Types do not prove numerical stability.
