---
layout: home
hero:
  name: Sprig
  text: A small JVM language for people and AI
  tagline: Python-like syntax, static types, runs on the JVM. When something is wrong, the compiler tells you where, why, and how to fix it.
  image:
    src: /logo-round.png
    alt: Sprig
  actions:
    - theme: brand
      text: Start the tutorial
      link: /en/tutorial
    - theme: alt
      text: Install
      link: /en/guide/getting-started
    - theme: alt
      text: GitHub
      link: https://github.com/ColinHouse/Sprig
features:
  - title: Missed cases get caught
    details: Add a case to a type, and every match that forgets it fails to compile instead of failing at runtime.
  - title: Check for null first
    details: A value that can be null is written T?, and you check it before use. Objects returned by Java count too.
  - title: Errors you can act on
    details: Every error has a stable code, an exact location and a fix hint. Ask for JSON and hand it to your editor or AI assistant.
---

## A first look

<<< @/snippets/home/first_look.spr

Output:

```text
3.14159
7.0
MONDAY
```

Add a `Triangle` to `Shape` later and forget to update `area`, and `sprig check` points straight at it:

```text
SPR-MATCH-NONEXHAUSTIVE [FLOW] main.spr:9:12: Missing case: Shape.Triangle
  hint: Add 'case Shape.Triangle:' (there is no default case)
```

## What you can build

- Command-line tools and automation scripts
- JSON and text processing
- Small web services and SQLite apps (see the [examples](/en/examples))
- Programs that use existing Java libraries from Maven
- The game logic of a Minecraft mod (see [Fabric integration](/en/guide/fabric))

## Can I use it yet?

Sprig is young. The current release is the experimental v0.7.1-beta.1. Everything listed above works today, and each item has an example you can run. It doesn't have interfaces yet, generic type inference arrives in the next release, and it isn't meant for production use. The full list is in [known limitations](/en/reference/language/known-limitations).

## Up and running in five minutes

You need JDK 17 or newer. Once the [SDK is installed](/en/guide/getting-started):

```bash
sprig init hello
cd hello
sprig resolve
sprig run
```

When you see `Hello, Sprig!`, head to the [tutorial](/en/tutorial) and build a small expense tracker in about half an hour.

## Get involved

- Found a bug, or something feels awkward? [Open an issue](https://github.com/ColinHouse/Sprig/issues).
- Want to contribute code? Read the [contributing guide](/en/project/contributing), then pick an [open issue](https://github.com/ColinHouse/Sprig/issues?q=is%3Aissue+is%3Aopen).
- Code written with an AI assistant is welcome, as long as you've read it and tested it yourself.

[中文](/) · [Apache-2.0](https://github.com/ColinHouse/Sprig/blob/main/LICENSE) · [GitHub](https://github.com/ColinHouse/Sprig)
