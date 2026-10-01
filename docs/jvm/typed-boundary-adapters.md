# Typed Java boundary adapter generation: deferred

## Decision

This integration milestone does not generate Java-to-Sprig or Sprig-to-Java
boundary adapters. The Fabric starter keeps a small, ordinary Java bridge and
uses Sprig `conform` for the application-owned implementation.

## Existing tools

- `sprig api` inspects Java members and reports which signatures the compiler
  can use, including the reason a signature is unsupported.
- `sprig wrap` generates editable Sprig source for supported Java members. It
  is a Java-to-Sprig wrapper generator, not a generator for Java classes that
  adapt Sprig implementations to arbitrary host APIs.
- `conform` checks a bounded foreign Java-interface contract for an existing
  Sprig class. It does not synthesize methods or change the class ABI.

These tools answer useful inspection, import and conformance questions, but
they do not remove the repeated Gradle bridge compilation and classpath wiring
observed in the Quest Board experiment. `sprig-gradle` addresses that build
mechanics directly while leaving the host boundary visible and reviewable.

## Why generation is deferred

The Quest Board has one concrete bridge shape. That is enough to justify
first-party Gradle integration, but not enough evidence to define a general
adapter API. A generator would need independently useful fixtures for multiple
real boundary shapes, explicit rejection rules for unsupported signatures,
ordinary reviewable generated source, no runtime reflection, and a stable
visible ABI. No new language semantics are authorized by this milestone.

Reconsider only after at least two independent host integrations show the same
adapter boilerplate remains after using `sprig-gradle`. Start with a small
design proposal that compares handwritten bridge source with existing
`api`/`wrap`/`conform` contracts and identifies signatures that can be safely
generated. Keep unsupported overloads, wildcards, callbacks, nullability and
exception behavior explicit; never make a generator silently broaden the JVM
interop contract.
