# v0.3 developer attraction — implementation contract

Goal: a stranger can build a useful JVM tool and contribute with compiler feedback.
Target compiler 0.3.0-alpha.1, language 0.8-dev. Current public release remains
0.2.0-alpha.1 until the new exact-commit release gates succeed.

## Architecture and decisions

Use Apache Maven Resolver plus its Maven model-provider libraries, not a POM parser.
Sprig resolve collects compile/runtime transitive dependencies with exact direct
versions, Maven conflict semantics, exclusions/optional/scope and effective parent/BOM
models. Reject snapshots/ranges/dynamic selected versions for reproducible locks.
Persist graph/coordinate identity, classifier/type, provenance and SHA-256 of JARs/POMs;
rebuild classpath from locked artifact order without graph re-resolution. Cache misses,
changed bytes, stale locks and unsupported configurations fail explicitly. Central is
the default; a local repository fixture supports deterministic offline tests. No plugins
or build hooks execute. Libraries are bundled with the SDK; Maven CLI is not required.
Retain scoped Sprig edge IDs and Git integrity; migrate schema explicitly if needed.

One project preparation path feeds check/build/run/api/doctor/deps. Explicit source
outside a project still bypasses that project's dependency graph. Preserve explicit
--classpath and define deterministic duplicate handling.

SDK shell and Windows cmd launchers use platform path separators and quoting.
Build/package portable Python drivers support CI and contributors without Bash on
Windows; release archives include resolver libraries and legal notices. Test Windows
paths/spaces/drives and fresh archive init/resolve/run, not merely compilation.

Small ordinary Sprig modules provide filesystem, arguments/environment, text/time,
and a recursive closed JSON variant. Java host services are explicit implementation
boundaries, not a replacement for a Sprig-facing data model. No Any, callbacks or new
syntax. All approximation/interop limitations stay documented.

Showcases: multi-module repository statistics/auditor CLI (~200-500+ Sprig lines),
small real Maven-library application with api/offline tests, and an AST/code-analysis
utility based on the frontend probe. Record measured friction; do not add syntax to fix it.

## Execution and ownership

- [x] Maven resolver, schema/classpath/CLI, build dependencies: parent agent.
- [x] Windows launcher, portable build/package, OS/JDK CI definitions and local archive smoke: platform agent. Hosted gates remain below.
- [x] Small standard/host APIs, JSON model and three showcases: applications agent.
- [x] Contributor guide, one-command verification, issue/PR templates, demo: contributor agent.
- [ ] Integrate modules and preserve all existing correctness oracles; targeted negative
      Maven fixtures plus full parser/semantic/Java/JVM/docs/archive gates.
- [ ] Seed 10-20 genuine issues with acceptance contracts and labels after actual gaps
      are known. Run fresh blind contribution trials using only public docs and issue.
- [ ] Rewrite landing/quickstart around tooling/application code and query/check/repair.
      Keep one validation authority and one concise design-pressure record.
- [ ] Commit/push reviewable changes; Linux/macOS/Windows JDK17/newer hosted matrix;
      exact clean main gates, annotated prerelease tag, workflow publication and downloaded
      SDK verification on all supported OSs. Do not publish early.

## Acceptance and evaluation

Baseline: main 64b13452471c35e92d7fc45cd6857bdc5fa9d632, public v0.2.0-alpha.1,
compiler 0.2.0-alpha.1, language 0.8-dev, schema2; prior main hosted CI green.
Fresh baseline and final tests must be run and recorded, not inferred.
Maven fixture tests cover parent/BOM/properties, exclusions, optional/scope/conflicts,
exact-version refusal, deterministic locks, tampered cache, missing cache offline,
concurrent cache use, path safety and every CLI classpath consumer. Real small Central
library adds distinct network+offline end-to-end evidence.
Archive gates run version/capabilities/check/run/init/resolve/project run and all three
showcases. Windows hosted evidence is mandatory. Contributor trials measure actual
iterations/failures/files/tool queries; never invent a human evaluation or fake an agent.
Existing semantic/Golden tests may change only when their former unsupported Maven
expectations are explicitly replaced by correct positive/negative Maven contracts.
No grammar/type/numeric/nullability/generic/effect redesign in this milestone.

Source for Maven model semantics: https://maven.apache.org/resolver/how-resolver-works.html
