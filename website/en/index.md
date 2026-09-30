---
layout: home
hero:
  name: Sprig
  text: Predictable JVM tools, written together
  tagline: A small, explicit language for tooling, automation and reliable application code. Query the compiler, edit, check and repair. Experimental Alpha; JDK 17+.
  image:
    src: /logo-round.png
    alt: Sprig
  actions:
    - theme: brand
      text: Five-minute start
      link: /en/guide/getting-started
    - theme: alt
      text: Contribute with your agent
      link: /en/project/contributing
    - theme: alt
      text: GitHub
      link: https://github.com/ColinHouse/Sprig
features:
  - title: Readable tools and ASTs
    details: Indentation, explicit generics, sealed variants and exhaustive match for CLI utilities, configuration tools and source analyzers.
  - title: Compiler feedback you can query
    details: Capabilities, topic help, JVM signatures and stable JSON diagnostics give humans and coding agents a shared source of truth.
  - title: Explicit runtime boundaries
    details: Checked integers, exact Decimal/BigInt, conservative Java nullability and reproducible dependency locks make failures visible.
---

## What can I build?

Repository automation, data transforms, small JVM applications and compiler
utilities. The source milestone's [showcases](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases)
include a repository auditor, a real Maven-library application and a source
analyzer. Their READMEs include inputs, commands and explicit boundaries.

## Start with a project

[Install the SDK or build from source](/en/guide/getting-started), then:

```bash
sprig version
sprig init my-tool
cd my-tool
sprig resolve
sprig run
```

Output: `Hello, Sprig!`. Then query `sprig capabilities --json`, edit
`src/main.spr`, and use `sprig check --json` to guide repairs.

## Release and limitations

The published SDK is [v0.4.0-alpha.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.4.0-alpha.1).
v0.4 adds the canonical formatter, explicit
module re-exports, expression `match`, Unicode code-point string semantics,
`sprig api` module/project introspection, managed SDK upgrades and an adversarial
correctness pass.
The current source checkout (not yet in the v0.4.0-alpha.1 release assets) also
includes concrete Java generics, opaque arrays with the explicit
`@std/jvm.spr` collection adapters, the `sprig wrap` generator, the
`sprig test` project runner and schema-4 owner-relative portable local locks.
The end-to-end wiring from a real Fabric/Loom dogfood is in
[Fabric / JVM framework integration](/en/guide/fabric).

See [release status](/en/project/release-status), the release assets and the
checkout's capability output. Sprig is not self-hosted or production ready.
Publishing/registry, LSP, interfaces and generic inference remain future work.
See [known limitations](/en/reference/language/known-limitations).

## Help build Sprig

Want to contribute with Codex / Claude / ChatGPT? Pick an
[agent-friendly issue](https://github.com/ColinHouse/Sprig/issues?q=is%3Aissue+is%3Aopen+label%3Aagent-friendly),
read AGENTS.md, run the contributor gate, review the patch and open a PR.
[Contributing](/en/project/contributing) explains the short path; AI assistance
is welcome and submitters own review, tests and correctness.

Apache-2.0 · [中文](/) · [Language tour](/en/guide/language-tour) ·
[Tooling and JSON](/en/guide/tooling)
