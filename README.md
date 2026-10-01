# Sprig

**Sprig does not try to make coding agents smarter. It tries to give them less
to guess.**

Sprig is an experimental, statically typed JVM language for people building
tools, automation and application code. Agent-friendly should also mean
review-friendly: inspectable source, explicit signatures, compiler queries and
structured diagnostics help people verify what changed.

The latest published SDK is [v0.5.0-beta.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.5.0-beta.1); it requires a separate JDK 17+. Linux and macOS are release-supported. Windows is an experimental preview. Sprig uses a Java stage-0 compiler and is not self-hosted. Check the [release status](docs/releases/validation.md) and installed `sprig capabilities --json` for the exact implemented feature set.

## Start here

- [Beginner tutorial](https://colinhouse.github.io/Sprig/tutorial)
- [中文教程](https://colinhouse.github.io/Sprig/tutorial)
- [Try the local Task Tracker](examples/task-tracker/README.md)
- [Browse application examples](examples/README.md) and [first-party libraries](libraries/README.md)
- [Gradle/JVM and Fabric integration](website/guide/gradle.md) with a runnable SDK starter
- [Language, JVM and tooling references](docs/README.md)

Install the published SDK, then create a project:

```sh
sprig init hello
cd hello
sprig resolve
sprig run
```

## Build from source

JDK 17+, Python 3.12+, Node.js 20+/npm and Git are needed for the full
developer gate. The first build downloads pinned ANTLR and Maven Resolver
tools.

```sh
git clone https://github.com/ColinHouse/Sprig.git
cd Sprig
python3 scripts/build.py
./scripts/verify.sh
```

See [contributor instructions](CONTRIBUTING.md) and [AI assistance disclosure](docs/contributing/ai-disclosure.md). Contributions are welcome; the author remains responsible for reviewing generated changes, tests, licensing and correctness.

Apache-2.0 · [Releases](https://github.com/ColinHouse/Sprig/releases) · [中文网站](https://colinhouse.github.io/Sprig/)
