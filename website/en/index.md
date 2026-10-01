---
layout: home
hero:
  name: Sprig
  text: Less to guess. Easier to verify.
  tagline: Sprig does not try to make coding agents smarter. It tries to give them less to guess. For people and agents; agent-friendly should also mean review-friendly. Experimental Beta; JDK 17+.
  image:
    src: /logo-round.png
    alt: Sprig
  actions:
    - theme: brand
      text: Start the tutorial
      link: /en/tutorial
    - theme: alt
      text: Contribute with your agent
      link: /en/project/contributing
    - theme: alt
      text: GitHub
      link: https://github.com/ColinHouse/Sprig
features:
  - title: Programs people can read
    details: Explicit function types, clear bindings and small JVM programs make source easier to inspect and discuss.
  - title: Ask the compiler instead of guessing
    details: Capabilities, topic help, Java signatures and JSON diagnostics provide reproducible implementation evidence.
  - title: Keep changes reviewable
    details: Type checking, exhaustive match, checked numerics and locked dependencies expose concrete problems early; they do not prove an algorithm correct.
---

## What can I build?

Repository automation, data transforms, small JVM applications and compiler
utilities. The source milestone's [showcases](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases)
include a repository auditor, a real Maven-library application and a source
analyzer. Their READMEs include inputs, commands and explicit boundaries.

## Early dogfood: feedback, not a performance claim

The maintainer reports trying a real Sprig workflow with a lower-cost coding
model. This is early, anecdotal product dogfooding: there was no controlled
experiment, equivalent Java implementation, preregistered task set or
productivity metric. It cannot support claims that Sprig beats Java, improves
productivity by a measured amount or eliminates model errors. The narrower
observation is that compiler queries and structured diagnostics can provide
concrete evidence for repairs. We welcome external users to reproduce and
report their experience.

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

The published SDK is experimental [v0.5.0-beta.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.5.0-beta.1),
with compiler version `0.5.0-beta.1` and language version `0.8-dev`. Beta brings
project testing, wrapper generation, schema-4 dependency locks, explicit Java
generic and collection boundaries, and first-party CLI, HTTP, JSON, SQLite and
Web packages into the SDK. See [release status](/en/project/release-status),
the release assets and `sprig capabilities --json` from the installed SDK for
the exact feature set. The end-to-end wiring from a real Fabric/Loom dogfood is
in [Fabric / JVM framework integration](/en/guide/fabric).

Sprig is not self-hosted or production ready. Publishing/registry, LSP,
interfaces and generic inference remain future work. See
[known limitations](/en/reference/language/known-limitations).

## Help build Sprig

Want to contribute with Codex / Claude / ChatGPT? Pick an
[agent-friendly issue](https://github.com/ColinHouse/Sprig/issues?q=is%3Aissue+is%3Aopen+label%3Aagent-friendly),
read AGENTS.md, run the contributor gate, review the patch and open a PR.
[Contributing](/en/project/contributing) explains the short path; AI assistance
is welcome and submitters own review, tests and correctness.

Apache-2.0 · [中文](/) · [Language tour](/en/guide/language-tour) ·
[Tooling and JSON](/en/guide/tooling)
