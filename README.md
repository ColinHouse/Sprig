# Sprig

**Sprig does not try to make coding agents smarter. It tries to give them less
to guess.**

Sprig is an experimental, statically typed JVM language for people building
tools, automation and application code. Agent-friendly should also mean
review-friendly: inspectable source, explicit signatures, compiler queries and
structured diagnostics help people verify what changed.

The latest published SDK is [v0.7.1-beta.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.7.1-beta.1); it requires a separate JDK 21+. Linux and macOS are release-supported. Windows is an experimental preview. Sprig uses a Java stage-0 compiler and is not self-hosted. Check the [release status](docs/releases/validation.md) and installed `sprig capabilities --json` for the exact implemented feature set.

## A first look

A closed `variant`, a `match` that must list every case, explicit function
signatures and a Java call whose result must be checked for null:

```sprig
import java.time.LocalDate as LocalDate

variant Shape:
    Circle(radius: Float)
    Rect(width: Float, height: Float)

func area(shape: Shape) -> Float:
    match shape:
        case Shape.Circle as circle:
            return 3.14159 * circle.radius * circle.radius
        case Shape.Rect as rect:
            return rect.width * rect.height

print(area(Shape.Circle(radius=1.0)))
print(area(Shape.Rect(width=2.0, height=3.5)))

# Java reference results are nullable until checked.
let date = LocalDate.parse("2026-10-05")
if date != null:
    print(date.getDayOfWeek())
```

`sprig run shapes.spr` prints `3.14159`, `7.0` and `MONDAY`. Without the null
check, `sprig check unchecked.spr` rejects the program:

```sprig
import java.time.LocalDate as LocalDate

let date = LocalDate.parse("2026-10-05")
print(date.getDayOfWeek())
```

```text
SPR-TYPE-NULLABLE [TYPE] unchecked.spr:4:12: Cannot access 'getDayOfWeek' on a value that may be null (receiver type LocalDate?)
  hint: Bind the receiver to a let and check it with 'if value != null:' before using 'getDayOfWeek'.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

## Start here

- [Beginner tutorial](https://colinhouse.github.io/Sprig/en/tutorial)
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

JDK 21+, Python 3.12+, Node.js 20+/npm and Git are needed for the full
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
