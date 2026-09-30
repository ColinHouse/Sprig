# Configuration summary

This small command reads a committed UTF-8 JSON file, decodes it with
`sprig-json-codec`, matches the optional field state, validates application
rules, and prints a deterministic summary.

From this directory:

```sh
sprig resolve
sprig run -- fixtures/board.json
```

Expected output:

```text
schema=1
active=alpha
projects=2 (青空, Garden)
targets=3
```

To see a path-aware failure, make a copy of the fixture, change one target's
`amount` to `"three"`, then run the program with that file. The decoder reports
the JSON path and expected type; it does not coerce a string to an integer.

The dependency is a portable path to the bundled codec library, so the same
commands work in the repository and in the extracted SDK archive.
