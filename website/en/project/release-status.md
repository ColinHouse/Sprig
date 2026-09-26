# Release status

**Source candidate:** compiler `v0.3.0-alpha.1`, language `v0.8-dev`, JDK 17+,
Apache-2.0. **Current public SDK:**
[v0.2.0-alpha.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.2.0-alpha.1).

The v0.3 source milestone adds Apache Maven Resolver, one locked project JVM
classpath, Unix/Windows launchers, a small typed IO/JSON layer, three tooling
showcases and the contributor workflow. This page distinguishes source capability
from a published archive. It is experimental Alpha, not production-ready.

CI defines Linux/macOS/Windows × JDK17/26. A release workflow builds one exact
clean tagged ZIP and all six jobs smoke-test that same archive before publication.
Definitions alone are not evidence: consult the
[sole validation record](https://github.com/ColinHouse/Sprig/blob/main/docs/RELEASE_VALIDATION.md)
for exact commits, executed gates and remaining blockers. No JDK is bundled.

Publishing/registry, inference, variance, interfaces/traits, LSP/IDE and self-hosting
remain absent. The stage-1 frontend is a probe. Type safety does not certify
numerical stability. See [limitations](/en/reference/KNOWN_LIMITATIONS).
