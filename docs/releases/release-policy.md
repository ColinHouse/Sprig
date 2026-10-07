# Release and versioning policy

Sprig carries two version numbers: the SDK version of a build and the
language version that build accepts. This page defines both, when each one
changes, and the steps of a release. It applies from v0.7.1-beta.1 on.

| | SDK version | Language version |
|---|---|---|
| Example | `0.7.1-beta.1` | `0.8-dev` |
| Names | one build of the compiler, runtime, standard library and tools | the language: syntax, type rules and what a program does when it runs |
| Shown in | `sprig version`, `compilerVersion` in `sprig capabilities --json`, the tag `v0.7.1-beta.1` and the archive name | `languageVersion` in `sprig capabilities --json`; a project declares `language = "0.8"` in `sprig.toml` |
| Changes | with every release | only as described below |

## SDK version

The SDK version is `MAJOR.MINOR.PATCH-STAGE.N`, a SemVer 2.0 pre-release.
`STAGE` is `alpha`, `beta` or `rc`; a stable release has no suffix. Before 1.0,
the first number stays 0 and the other two follow Cargo's reading of SemVer,
where MINOR is the incompatible number:

- **MINOR** (0.7 → 0.8) for a release with an incompatible change: a correct
  program written for the previous release is rejected or does something
  else, other than through a soundness or behavior fix (below). A release that
  brings a large set of new features may take a new MINOR as well.
- **PATCH** (0.7.0 → 0.7.1) for a release whose changes are all compatible:
  bug fixes, speed, diagnostics, help and documentation, soundness and behavior
  fixes, and additions that leave existing programs alone, such as a new
  method. Upgrading within one MINOR line does not require changing code,
  except code that such a fix rejects or that relied on the old behavior.
- **N** starts at 1 and counts prereleases of the same `MAJOR.MINOR.PATCH`
  and stage. A new PATCH or MINOR resets it.

The stage only moves forward, from `alpha` to `beta` to `rc` to stable, and
each step is an owner decision. After 1.0, MAJOR replaces MINOR as the number
for incompatible changes, as SemVer defines.

## Language version

The language version is `MAJOR.MINOR`, followed by `-dev` while that version
is still open.

- **Open (`0.8-dev`).** The 0.8 language is still being built. A release may
  add to it or change it; every change that can reject or change an existing
  program is listed in the release notes' upgrade section.
- **Frozen (`0.8`).** The release that declares a language version stable drops
  `-dev`. From then on, that version changes only through soundness and
  behavior fixes, and new features go into the next version (`0.9-dev`).
- **Next version.** After a version is frozen, a change to its syntax or
  meaning starts the next language version, and projects move to it by
  declaring the new `language` in `sprig.toml`.

Writing down behavior that already existed, such as the rule that `==` on two
class objects compares identity, does not change the language version.

## Soundness and behavior fixes

A soundness fix makes the compiler reject a program whose accepted behavior
contradicted the documented contract. Joining a `T?` value into a `String`, for
example, printed `null` in a language whose rule is that absence is checked
before a value is used. A behavior fix keeps a program compiling but changes
what it does, where the old behavior contradicted the documented contract:
`toString()` on an `Error` returned `sprig.runtime.SprigError: message`, while
the documented rule is that a value's text is what `print` shows.

Both are bug fixes: they may ship in a PATCH release and do not change the
language version. The release notes list each one in the upgrade section: a
soundness fix with its diagnostic code and the rewrite, a behavior fix with what
changes and how to find the code it affects, since no diagnostic reports it.

## Release steps

A tag, a release and any public claim about one need a verified release build
and the owner's decision to publish ([`AGENTS.md`](https://github.com/ColinHouse/Sprig/blob/main/AGENTS.md)).
A published tag is never moved; a broken release is replaced by a new version.

1. **Choose the version** with the rules above.
2. **Preparation PR** (`release/vX`), merged with required CI green:
   - `compilerVersion` in `catalog.properties`, with `releaseStatus` set to
     `development; based on vX; latest published vPREVIOUS`;
   - the version in documentation headings, the `libraries/` READMEs,
     `tests/agent_tooling/check_tooling.py` and `website/package.json` (with its
     lock file);
   - release notes in `docs/releases/vX.md`: highlights, the upgrade section and
     known limits. The release workflow publishes this file as the release body;
   - tracked locks regenerated with `python3 scripts/refresh-locks.py`;
   - `./scripts/verify.sh`, `python3 scripts/package-alpha.py --skip-build`
     followed by `python3 scripts/check-sdk-archive.py`, and
     `python3 tests/fabric/check_template.py`, all passing.
3. **Tag preflight.** In a clean checkout of the merge commit, a build reports
   `releaseStatus` `prerelease; vX`, and `SPRIG_PACKAGE_VERSION=vX python3
   scripts/package-alpha.py` followed by `python3 scripts/check-sdk-archive.py
   --archive` passes.
4. **Tag.** `git tag -a vX -m "Sprig vX"` on that commit, then push the tag.
   `.github/workflows/release.yml` builds and tests the tagged source, packages
   it, smoke-tests the downloaded archive on Linux and macOS with JDK 21 and 26,
   and publishes the prerelease.
5. **Check the published release.** The downloaded archive's SHA-256 matches
   the `.sha256` file and GitHub's digest, and `sprig upgrade` from the previous
   release works in an isolated home directory.
6. **Post-release PR.** [`validation.md`](validation.md) records the published
   release (the previous record moves to `validation-vPREVIOUS.md`), and the
   README, the catalog's `releaseStatus` and the site's release pages name the
   new latest release, in Chinese and English.
