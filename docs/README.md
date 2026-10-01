# Sprig documentation map

Use the directory that matches the question. These pages describe the current
implementation; the compiler, runtime, grammar and executable tests are the
behavioral authority.

| Find | Read |
|---|---|
| Language, types, numbers, limitations | [`language/`](language/) |
| Java/JVM calls, host boundaries, wrappers | [`jvm/`](jvm/) |
| Projects, dependencies, installation, standard library | [`projects/`](projects/) |
| CLI tools, diagnostics, formatting, testing | [`tooling/`](tooling/) |
| Contribution policy and project provenance | [`contributing/`](contributing/) |
| Release notes and verified archive record | [`releases/`](releases/) |
| Historical audits and the retired v0.7 design kit | [`history/`](history/README.md) |

`website/` contains onboarding and presentation material. Its generated English
reference pages are copied from these canonical documents; edit the source here.
The current release validation is [`releases/validation.md`](releases/validation.md);
versioned release notes and older validation records preserve historical
evidence. The history directory is evidence about earlier states, not a current
language contract.

For the shortest current feature summary, see
[`language/feature-status.md`](language/feature-status.md). For commands and
test requirements, see [`tooling/testing.md`](tooling/testing.md). Repository
contributor rules remain in the root [`AGENTS.md`](../AGENTS.md) and
[`CONTRIBUTING.md`](../CONTRIBUTING.md).
