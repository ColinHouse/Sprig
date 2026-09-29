# Application foundation example

This small consumer composes the synchronous `sprig-http` client,
`@std/json`, `sprig-json-codec`, `@std/time`, `@std/files` and `@std/text`.
The only Java code involved is the HTTP/OS boundary; the application flow is
ordinary Sprig.

The checked-in manifest uses portable local paths for development from this
repository checkout. A Git consumer can install the JSON codec from a published
repository tag and package subdirectory; no such tag is assumed by this
example. There is no central Sprig registry.

Run `sprig resolve`, then `sprig run -- http://127.0.0.1:<port>/message <output>`.
The local fixture `tests/application_foundation/check_composition.py` starts a
loopback server and verifies the written UTF-8 result without public network
access.
