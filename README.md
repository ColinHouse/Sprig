# Sprig

<p align="center">
  <img src="website/public/logo-round.png" alt="Sprig icon" width="150">
</p>

**Sprig is a small, explicit, statically typed JVM language for tools,
automation and reliable application code.** Its indentation-based syntax,
sealed variants, exhaustive `match`, checked numerics and compiler query tools
are designed to make programs and compiler feedback easy to inspect. Sprig is
experimental Alpha, requires JDK 17+, and currently uses a Java stage-0
compiler; it is not self-hosted.

[简体中文 website](https://colinhouse.github.io/Sprig/) ·
[English website](https://colinhouse.github.io/Sprig/en/) ·
[Releases](https://github.com/ColinHouse/Sprig/releases) ·
[Contributing](CONTRIBUTING.md)

## Try Sprig

Install the current published [v0.4.0-alpha.1 SDK](https://github.com/ColinHouse/Sprig/releases/tag/v0.4.0-alpha.1)
(requires a separate JDK 17+), or build a source checkout:

```bash
git clone https://github.com/ColinHouse/Sprig.git
cd Sprig
python3 scripts/build.py
./bin/sprig version
```

Create and run a project with an installed SDK:

```bash
sprig init my-tool
cd my-tool
sprig resolve
sprig run
```

A source build fetches pinned ANTLR and Maven Resolver libraries. Maven CLI is
not required. Linux and macOS are release-supported; Windows is an experimental
preview.

## Compiler feedback

Use the compiler to query language and JVM API details instead of guessing:

```bash
sprig capabilities --json
sprig help generics --json
sprig api java.time.LocalDate --json
sprig check --json src/main.spr
sprig explain SPR-TYPE-NULLABLE --json
```

## Documentation

- [Documentation map](docs/README.md): current language, JVM, project and tooling references.
- [Feature status](docs/language/feature-status.md) and [known limitations](docs/language/known-limitations.md).
- [Getting started](website/guide/getting-started.md) and [examples gallery](examples/README.md).
- [First-party libraries](libraries/README.md).
- [Historical evidence](docs/history/README.md), including selected audits and the retired v0.7 design kit.
- [VS Code extension](editors/vscode/README.md): local preview with syntax highlighting and compiler actions.

## Contribute

Read [AGENTS.md](AGENTS.md) for repository rules and [CONTRIBUTING.md](CONTRIBUTING.md)
for setup and review expectations. The canonical local gate is:

```bash
./scripts/verify.sh
```

## License

Apache-2.0. See [NOTICE](NOTICE), [third-party notices](THIRD_PARTY_NOTICES.md)
and [license scope](docs/contributing/license-status.md).
