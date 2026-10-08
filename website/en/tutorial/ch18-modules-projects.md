# 18. Modules, projects and dependencies

Every program in the first seventeen chapters fits in one file. Real programs grow past that, and they use code other people wrote. This chapter covers three things:

- **Modules**: split code across `.spr` files that import each other;
- **Projects**: pin one entry point, a set of sources and a dependency list in `sprig.toml`;
- **Dependencies**: use Sprig packages that live in a local directory, a Git repository or a package registry.

In this chapter you will learn:

- one `.spr` file is one module; after `import "./math_module.spr" as math`, you reach its top-level functions, classes and `let` bindings as `math.name`;
- a module's top-level statements run once, the first time it is imported, and two modules that import each other are rejected by the compiler;
- `export` turns a module into a facade that re-exports only the names you choose;
- a project is `sprig.toml` + `src/` + `tests/` + a committed `sprig.lock`; without a resolved lock, `sprig run` refuses to run;
- `sprig add` declares a dependency, `@alias/module.spr` imports it, and only the modules in the dependency's `exports` can be imported.

## 18.1 One file is one module

Two things get mixed together in a program: helper functions (area, formatting, parsing) and the main flow that uses them. Keep the helpers in their own file and the main file stays short enough to read at a glance, while the helpers can be reused by another program. That unit — one file — is a module.

The smallest example: one module and one program that uses it. Both files go in the same directory.

`math_module.spr`:

<<< @/snippets/book_en/ch18_import/math_module.spr

`main.spr`:

<<< @/snippets/book_en/ch18_import/main.spr

```text
49
3.14159
```

Line by line:

- `import "./math_module.spr" as math` imports `math_module.spr` from the **same directory** and gives it the local alias `math`. A path starting with `./` means "relative to this file".
- `print(math.square(7))` reaches a function in the module through the alias, a dot and the name. `square(7)` returns `49`.
- `print(math.pi_approx)` reads a top-level `let` binding the same way. The `math.` part is required; writing `pi_approx` alone is not something the compiler knows.
- The other two lines, `49` and `3.14159`, are simply the return value and the binding printed out.

A module has no `private`: every top-level function, class and `let` binding in an imported file is visible. To hide something, put it in another file that nobody imports.

### Top-level statements run once

A module's top-level statements run the first time it is imported. If two modules depend on the same module, it still initializes only once. Three files:

`d.spr`:

<<< @/snippets/book_en/ch18_once/d.spr

`b.spr`:

<<< @/snippets/book_en/ch18_once/b.spr

`main.spr`:

<<< @/snippets/book_en/ch18_once/main.spr

```text
d initialized
b done
main done
```

`main.spr` imports `d` directly and also gets it through `b`. The compiler handles `b` first; the first import of `d` runs the top-level statements of `d.spr`, printing `d initialized`, and then `b.spr` prints its own `b done`. Back in `main.spr`, importing `d` again does nothing because the module is already initialized, and the last line is `main done`. This rule guarantees that module setup code, such as reading a configuration file once, does not repeat.

### Import cycles are rejected

If `main.spr` imports `other.spr` and `other.spr` imports `main.spr`, there is no sensible order in which to initialize them. The compiler does not guess; it reports an error:

`other.spr`:

<<< @/snippets/book_en/ch18_cycle/other.spr

`main.spr`:

<<< @/snippets/book_en/ch18_cycle/main.spr

```text
SPR-NAME-IMPORT-CYCLE [NAME] main.spr: Circular module import: main.spr -> other.spr -> main.spr
```

Read the line: `SPR-NAME-IMPORT-CYCLE` is the error code, `main.spr` says where the problem is reported, and after the colon `main.spr -> other.spr -> main.spr` draws the import chain that loops back on itself. The fix is to break the cycle, usually by moving the code both files need into a third module.

### Deliberate mistake: forgetting the alias

Importing under an alias and then using the bare name is the most common beginner error:

<<< @/snippets/book_en/ch18_alias/main.spr

```text
SPR-NAME-UNRESOLVED [NAME] main.spr:4:7: Unresolved name 'square'
```

`SPR-NAME-UNRESOLVED` means "I cannot find this name". `main.spr:4:7` points at line 4, column 7, the start of `square`. The compiler will not guess that you meant `math.square`; change `square(7)` to `math.square(7)` and it compiles.

::: tip Coming from another language?

- Python's `from math import square` copies a name into the current file; Sprig has no such form, only `import ... as ...`, and the alias is never optional. There is no `import *` either.
- Java ties package names to directory structure; a Sprig module is the file itself, the import path is relative, and you choose the alias.

:::

## 18.2 export: a facade for others

A module may have many top-level names but only a few meant for outsiders. Sprig does not hide the rest; instead you write a **facade module** that re-exports just the names you want to publish. Everyone else imports the facade and sees only those names.

`math_module.spr`:

<<< @/snippets/book_en/ch18_facade/math_module.spr

`facade.spr`:

<<< @/snippets/book_en/ch18_facade/facade.spr

`main.spr`:

<<< @/snippets/book_en/ch18_facade/main.spr

```text
25
```

- `facade.spr` first imports `math_module.spr` normally, then `export math.square` re-exports the imported `square`. `facade.spr` is now a module that provides `square`.
- `main.spr` neither knows nor cares where `square` comes from; it deals only with `facade`.
- A re-exported name keeps the identity of the original declaration, so the facade does not copy the function, it adds an outward entry point.
- `import` comes first, then `export`, then ordinary declarations. Each `export` names one symbol and there is no wildcard.

### Deliberate mistake: a name the facade did not re-export

The facade re-exports `square`, but the caller wants `pi_approx`:

<<< @/snippets/book_en/ch18_facade/limited.spr

```text
SPR-NAME-UNRESOLVED [NAME] limited.spr:4:7: Module 'facade' has no member 'pi_approx'
```

This time `SPR-NAME-UNRESOLVED` adds `Module 'facade' has no member 'pi_approx'`: the name is fine, the `facade` module just does not have it. Either add `export math.pi_approx` to the facade or import `math_module.spr` directly.

::: tip Coming from another language?

Java's `public` modifier opens a window on a member; Sprig is the other way round: everything is public by default, and you narrow the surface with an extra facade file that lists the usable names. That resembles a package's `__init__.py` or a barrel `index.js` re-exporting submodules.

:::

## 18.3 Projects: sprig.toml

A directory holding a set of modules, one entry point and a dependency list is a Sprig project. `sprig init` starts one:

```text
$ sprig init
Created /private/tmp/sprig-book/my-tool/sprig.toml
Created /private/tmp/sprig-book/my-tool/src/main.spr
Run `sprig resolve` to create sprig.lock.
```

(The paths in the output are absolute paths of the working directory; yours will differ.) The layout is:

```text
my-tool/
├── sprig.toml        # project configuration
├── sprig.lock        # the lockfile sprig resolve writes
└── src/
    └── main.spr      # the program entry point
```

`sprig.toml`:

```toml
[project]
name = "my-tool"
version = "0.1.0"
language = "0.8"
```

The generated `src/main.spr`:

```sprig
# my-tool entry point.

func main() -> Unit:
    print("Hello, Sprig!")

main()
```

Commands run inside a project walk upward to the nearest `sprig.toml` and default to `src/main.spr` as the entry. Resolve the dependencies, then run:

```text
$ sprig resolve
Resolved 0 Sprig dependency entries and 0 Maven artifacts/models into /private/tmp/sprig-book/my-tool/sprig.lock

$ sprig run
Hello, Sprig!
```

`sprig resolve` writes `sprig.lock`, which pins every dependency to an exact version or commit. Three habits to remember:

- **Commit `sprig.lock`.** Anyone with the same lock gets exactly the build you got;
- **Only `resolve`, `add` and `remove` write it.** `check`, `run` and `build` only read it;
- **When the manifest and the lock disagree, commands refuse to run.** Run `sprig resolve` again after editing `sprig.toml`.

### Deliberate mistake: running before resolve

Right after `init`, before any `resolve`:

```text
$ sprig run
SPR-PROJECT-LOCK-MISSING [CLI] Project 'my-tool' has no sprig.lock
  hint: Run `sprig resolve`.
```

`SPR-PROJECT-LOCK-MISSING` says the project has no lockfile, and the `hint` gives the next step. After a manual manifest edit with a lock that no longer matches, you get the related `SPR-PROJECT-LOCK-STALE`:

```text
$ sprig run
SPR-PROJECT-LOCK-STALE [CLI] sprig.toml and sprig.lock do not match
  hint: Run `sprig resolve`.
```

### One project, several entry points

A project can have more than one executable entry, declared with `[[bin]]`:

```toml
[project]
name = "tools"
version = "0.1.0"
language = "0.8"

[[bin]]
name = "hello"
entry = "src/hello.spr"

[[bin]]
name = "bye"
entry = "src/bye.spr"
```

With several `[[bin]]` tables, `sprig run` must pick one with `--bin name`:

```text
$ sprig run
SPR-PROJECT-ENTRY [CLI] sprig.toml: Multiple binaries require --bin or an explicit [project] entry
  hint: Name one: --bin hello, --bin bye. sprig check with no file checks every bin.

$ sprig run --bin hello
hello
```

`sprig project` prints the project's information and `sprig project --json` gives it to scripts or editors. The manifest accepts few keys: `name`, `version`, `language`, `source` (default `src`), `entry` (default `<source>/main.spr`), plus `[[bin]]`, the `[[dependency]]`, `[[jvm]]` and `[[registry]]` tables below. A misspelled key is rejected.

## 18.4 Dependencies

To use modules from another project, declare a dependency first. Say a colleague keeps math helpers in `../math`:

```text
$ sprig add math --path ../math
Added sprig-path dependency math and updated /private/tmp/sprig-book/dep/consumer/sprig.lock
```

`sprig add` edits `sprig.toml` and updates `sprig.lock` right away. The manifest gains:

```toml
[[dependency]]
name = "math"
path = "../math"
```

Then the source imports through `@` and the dependency name:

```sprig
import "@math/vector.spr" as vector
```

```text
$ sprig run
25
```

The dependency decides which of its modules can be imported through its own `sprig.toml`:

```toml
[project]
name = "math"
version = "0.1.0"
language = "0.8"
exports = ["vector.spr"]
```

Only the modules listed in `exports` can be imported. If your colleague adds a `hidden.spr` without listing it, importing it gives:

```text
SPR-PROJECT-NOT-EXPORTED [NAME] main.spr:1:1: Dependency 'math' does not export 'hidden.spr'
  hint: Only modules listed in the dependency's [project] exports are importable.
```

`@std/...` is the one exception: standard-library modules come with the compiler installation, need no dependency declaration and are not written into the lockfile.

Besides a local directory, `sprig add` understands Git and Maven sources:

```bash
sprig add math --path ../math                                     # a local directory
sprig add format --git https://example.com/format.git --tag v1.0  # a Git repository
sprig add --jvm org.apache.commons:commons-text:1.12.0            # a Java library from Maven
```

A Git dependency can point at a version with `--branch`, `--tag` or `--rev`, and `resolve` pins the result to an exact commit. Maven coordinates are for Java libraries and are covered in chapter 21. `check`, `run`, `resolve`, `add`, `remove` and `test` all accept `--offline`: verified caches only, and a missing piece reports `SPR-DEP-OFFLINE` instead of fetching silently.

### Finding packages in a registry

When you cannot remember a package's Git URL, ask the registry:

```text
$ sprig search json
json-codec  0.7.1-beta.1  https://github.com/ColinHouse/Sprig.git libraries/sprig-json-codec  Apache-2.0
    Path-aware JSON decoding and encoding over @std/json
1 package(s) in 1 registry(ies); add one with: sprig add NAME [--version V]
```

`sprig add json-codec` looks it up and writes an **ordinary Git dependency** into your manifest; `check` and `run` never consult the registry again. A registry is only an index with no central server: the default index is the `registry/` directory of the Sprig repository, and a team registers its own with `[[registry]]`. Publishing means committing an entry to that index; `sprig publish --registry DIR --tag v1.0.0` writes the current package into it (the default registry also requires `--license` and `--owner`), and the command then tells you to open the pull request.

## Summary

- One `.spr` file is one module. `import "./x.spr" as x`, then use `x.name`; top-level names are visible by default.
- A module initializes once; importing in a cycle is `SPR-NAME-IMPORT-CYCLE`.
- `export x.name` builds a facade; a name the facade did not re-export gives the caller `SPR-NAME-UNRESOLVED`.
- A project is `sprig.toml` + `src/` + `tests/`; `sprig resolve` writes the lockfile, which is committed; `check`/`run`/`build` reject a missing or stale lock.
- `sprig add` declares a dependency and `@alias/module.spr` imports it; only the dependency's `exports` are importable; `--offline` uses caches only.
- `@std/` ships with the compiler; it is not a dependency.

## Exercises

### Exercise 1: pull out a first module

Split this program into two files: `double.spr` holding the function `double`, and `main.spr` importing it and printing `double(21)`.

```sprig
func double(x: Int) -> Int:
    return x * 2

print(double(21))
```

Hint: after the import, `double` is reached through the alias.

::: details Answer

`double.spr`:

<<< @/snippets/book_en/ch18_ex1/double.spr

`main.spr`:

<<< @/snippets/book_en/ch18_ex1/main.spr

```text
42
```

:::

### Exercise 2: add a facade

Starting from exercise 1, add `facade.spr` that imports `double.spr` and re-exports only `double`; make `main.spr` import from the facade.

Hint: `export alias.name`.

::: details Answer

`double.spr`:

<<< @/snippets/book_en/ch18_ex2/double.spr

`facade.spr`:

<<< @/snippets/book_en/ch18_ex2/facade.spr

`main.spr`:

<<< @/snippets/book_en/ch18_ex2/main.spr

```text
8
```

:::

### Exercise 3: how many times does it print?

When one module is imported twice under two aliases, how many times do its top-level statements run? Guess first, then run the two files in the answer.

Hint: recall "run once" from 18.1.

::: details Answer

`base.spr`:

<<< @/snippets/book_en/ch18_ex3/base.spr

`main.spr`:

<<< @/snippets/book_en/ch18_ex3/main.spr

```text
base initialized
main done
```

The top-level statements of `base.spr` run once, even though `main.spr` imports it under both `first` and `second`. The second import only adds another alias; it does not re-initialize the module.

:::

Next chapter: [Testing](/en/tutorial/ch19-testing).
