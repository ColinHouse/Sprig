#!/usr/bin/env python3
"""A project's generated classes live in its own Java package (#223).

Every Sprig program used to compile into the one package sprig.user and to
carry the runtime as sprig.runtime, so two Sprig-built jars in one class
loader (two Fabric mods in one game) shadowed each other's classes. This
suite checks the contract that replaces it:

- a project compiles into sprig.user.<project>, a single file into sprig.user;
- `build --emit-java-only --with-runtime` also writes the runtime's sources,
  as sprig.user.<project>.runtime, and the generated code uses that copy, so
  a build tool compiles one tree that shares no class name with another
  project's;
- two projects with the same module and class names run side by side in one
  JVM, in either classpath order;
- a program built with its own runtime prints what `sprig run` prints.

Evidence level: javac and JVM runtime, outside the checkout.
"""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
CLI = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
JAVAC = shutil.which("javac")
JAVA = shutil.which("java")

failures: list[str] = []
passed = 0


def verify(name: str, condition: bool, detail: object = "") -> None:
    global passed
    if condition:
        passed += 1
    else:
        failures.append(f"{name}: {detail}"[:4000])
        print(f"FAIL {name}: {detail}"[:4000], file=sys.stderr)


def sprig(*args: object, cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run([str(CLI), *map(str, args)], cwd=cwd, text=True, encoding="utf-8",
                          capture_output=True)


def envelope(result: subprocess.CompletedProcess[str]) -> dict:
    try:
        return json.loads(result.stdout)
    except ValueError:
        return {}


def make_project(home: Path, name: str, files: dict[str, str], directory: str | None = None) -> Path:
    project = home / (directory or name)
    (project / "src").mkdir(parents=True)
    (project / "sprig.toml").write_text(
        f'[project]\nname = "{name}"\nversion = "0.1.0"\nlanguage = "0.8"\n', encoding="utf-8")
    for relative, text in files.items():
        path = project / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    resolved = sprig("resolve", "--offline", cwd=project)
    if resolved.returncode != 0:
        raise RuntimeError(f"resolve failed for {name}: {resolved.stdout}{resolved.stderr}")
    return project


def javac(sources: list[Path], classes: Path, classpath: list[Path] = ()) -> subprocess.CompletedProcess[str]:
    classes.mkdir(parents=True, exist_ok=True)
    # A list file keeps the command short; javac reads quoted names, with \ as the escape.
    listing = classes.parent / (classes.name + ".sources")
    listing.write_text("\n".join('"' + str(path).replace("\\", "\\\\") + '"' for path in sources),
                       encoding="utf-8")
    command = [JAVAC, "-encoding", "UTF-8", "-nowarn", "-d", str(classes)]
    if classpath:
        command += ["-cp", os.pathsep.join(map(str, classpath))]
    return subprocess.run([*command, "@" + str(listing)], text=True, encoding="utf-8", capture_output=True)


def java(classpath: list[Path], main: str, cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run([JAVA, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                           "-cp", os.pathsep.join(map(str, classpath)), main],
                          cwd=cwd, text=True, encoding="utf-8", capture_output=True)


def class_files(classes: Path) -> list[str]:
    return sorted(path.relative_to(classes).as_posix() for path in classes.rglob("*.class"))


STORE = """let names: MutableList[String] = []

func remember(name: String) -> Unit:
    names.append(name)

func seen() -> Int:
    return names.size()
"""


def main_module(label: str, step: int) -> str:
    return f"""import "store.spr" as store

class Counter:
    var count: Int = 0

    func bump() -> Int:
        count += {step}
        return count

func describe() -> String:
    store.remember("{label}")
    let counter = Counter()
    counter.bump()
    return "{label} counts " + counter.bump() + ", remembers " + store.seen()

print(describe())
"""


def check_package_names(home: Path) -> None:
    # The project's name, with every character a Java package name cannot hold replaced.
    names = {
        "ordinary-project": "sprig.user.ordinary_project",
        "Demo2": "sprig.user.Demo2",
        "2048": "sprig.user._2048",
        "new": "sprig.user.new_",
        "my mod.v2": "sprig.user.my_mod_v2",
        "a$b": "sprig.user.a_b",
        "记账": "sprig.user.记账",
    }
    for index, (name, package) in enumerate(names.items()):
        project = make_project(home, name, {"src/main.spr": 'print("named")\n'}, f"named{index}")
        described = envelope(sprig("project", "--json", cwd=project))
        verify(f"project --json names the package of {name!r}",
               described.get("project", {}).get("generatedPackage") == package, described.get("project"))
        built = envelope(sprig("build", "--emit-java-only", "-d", "out", "--json", cwd=project))
        verify(f"{name!r} compiles into {package}",
               built.get("mainClass") == package + ".$M_main" and built.get("generatedPackage") == package
               and built.get("runtimePackage") == "sprig.runtime"
               and (project / "out/java" / package.replace(".", "/") / "$M_main.java").is_file(), built)
        if name.isascii():
            # A JVM takes its main class from the command line, which not every
            # platform's default code page can carry for a name like the last one.
            ran = sprig("run", cwd=project)
            verify(f"{name!r} runs", ran.returncode == 0 and ran.stdout.strip() == "named", ran.stdout + ran.stderr)
    text = sprig("project", cwd=make_project(home, "plain-text", {"src/main.spr": 'print("x")\n'}))
    verify("project prints the package", "generated package: sprig.user.plain_text" in text.stdout, text.stdout)


def check_single_file(home: Path) -> None:
    # No project, no name: a file outside a project keeps the base package.
    directory = home / "single file"
    directory.mkdir()
    (directory / "hello.spr").write_text('print("hello")\n', encoding="utf-8")
    built = envelope(sprig("build", "hello.spr", "--emit-java-only", "-d", "out", "--json", cwd=directory))
    verify("single file package", built.get("mainClass") == "sprig.user.$M_hello"
           and built.get("generatedPackage") == "sprig.user" and built.get("runtimePackage") == "sprig.runtime"
           and "runtimeSources" not in built, built)
    with_runtime = envelope(sprig("build", "hello.spr", "--emit-java-only", "--with-runtime", "-d", "own",
                                  "--json", cwd=directory))
    verify("single file with its runtime", with_runtime.get("runtimePackage") == "sprig.user.runtime"
           and (directory / "own/java/sprig/user/runtime/SprigRuntime.java").is_file(), with_runtime)


def check_options(home: Path) -> None:
    project = make_project(home, "options", {"src/main.spr": 'print("x")\n'})
    for command in (("build", "--with-runtime"), ("run", "--with-runtime"), ("check", "--with-runtime")):
        rejected = sprig(*command, "--json", cwd=project)
        verify(f"{' '.join(command)} is rejected", rejected.returncode == 2
               and "--with-runtime is only valid with build --emit-java-only" in rejected.stdout,
               rejected.stdout + rejected.stderr)
    features = envelope(sprig("capabilities", "--json", cwd=project)).get("features", {})
    verify("capabilities report the option", features.get("emitJavaWithRuntime") is True, features)


def check_cli_project(home: Path) -> Path:
    """The CLI's own commands in a project: build, run, test and a mapped failure."""
    project = make_project(home, "alpha", {
        "src/main.spr": main_module("alpha", 1), "src/store.spr": STORE,
        "tests/store_test.spr": 'import "../src/store.spr" as store\nstore.remember("x")\nassert(store.seen() == 1)\n',
    })
    built = envelope(sprig("build", "--json", cwd=project))
    classes = class_files(project / "sprig-build/classes")
    verify("build puts the program in the project's package", built.get("mainClass") == "sprig.user.alpha.$M_main"
           and {"sprig/user/alpha/$M_main.class", "sprig/user/alpha/$M_store.class",
                "sprig/user/alpha/$Counter.class", "sprig/runtime/SprigRuntime.class"} <= set(classes)
           and not [name for name in classes if name.startswith("sprig/user/") and name.count("/") == 2],
           (built, classes[:8]))
    ran = java([project / "sprig-build/classes"], "sprig.user.alpha.$M_main", project)
    verify("built classes run", ran.returncode == 0 and ran.stdout.strip() == "alpha counts 2, remembers 1",
           ran.stdout + ran.stderr)
    tested = envelope(sprig("test", "--json", cwd=project))
    verify("sprig test in a project", tested.get("summary") == {"total": 1, "passed": 1, "failed": 0}, tested)

    failing = make_project(home, "fails", {"src/main.spr":
                           "func add_one(value: Int) -> Int:\n    return value + 1\n\nprint(add_one(9223372036854775807))\n"})
    reported = envelope(sprig("run", "--json", cwd=failing))
    diagnostic = (reported.get("diagnostics") or [{}])[0]
    verify("a failure in a project maps to its Sprig line", diagnostic.get("code") == "SPR-RUNTIME-EXCEPTION"
           and (diagnostic.get("range") or {}).get("start", {}).get("line") == 1
           and diagnostic.get("uri", "").endswith("/src/main.spr"), diagnostic)
    stack = sprig("run", "--stacktrace", cwd=failing)
    verify("--stacktrace names the project's classes",
           "at sprig.user.fails.$M_main.fn$add_one" in stack.stdout + stack.stderr
           and "at sprig.runtime.NumericOps.add" in stack.stdout + stack.stderr, stack.stdout + stack.stderr)
    return failing


def emit_with_runtime(project: Path, out: Path, *source: object) -> tuple[dict, list[Path], list[Path]]:
    result = sprig("build", *source, "--emit-java-only", "--with-runtime", "-d", out, "--json", cwd=project)
    data = envelope(result)
    if result.returncode != 0 or "runtimeSources" not in data:
        raise RuntimeError(f"build --emit-java-only --with-runtime failed in {project.name}: "
                           f"{result.stdout}{result.stderr}")
    return data, [Path(path) for path in data["javaSources"]], [Path(path) for path in data["runtimeSources"]]


def check_two_projects(home: Path, failing: Path) -> None:
    """The issue's program: the same module and class names twice, in one class loader."""
    built = {}
    for label, step in (("alpha", 1), ("beta", 10)):
        project = home / label if label == "alpha" else make_project(
            home, label, {"src/main.spr": main_module(label, step), "src/store.spr": STORE})
        data, program, runtime = emit_with_runtime(project, project / "own")
        package = f"sprig.user.{label}"
        verify(f"{label}: packages", data.get("generatedPackage") == package
               and data.get("runtimePackage") == package + ".runtime"
               and data.get("mainClass") == package + ".$M_main" and data.get("javacInvoked") is False, data)
        java_root = project / "own/java"
        emitted = sorted(path.relative_to(java_root).as_posix() for path in java_root.rglob("*.java"))
        listed = sorted(path.relative_to(java_root).as_posix() for path in program + runtime)
        verify(f"{label}: every written file is listed, all under the project's package",
               emitted == listed and all(name.startswith(f"sprig/user/{label}/") for name in emitted)
               and all(path.relative_to(java_root).as_posix().startswith(f"sprig/user/{label}/runtime/")
                       for path in runtime)
               and not any("/runtime/" in path.relative_to(java_root).as_posix() for path in program),
               emitted[:6])
        shipped = sorted(path.relative_to(ROOT / "runtime/src/main/java/sprig/runtime").as_posix()
                         for path in (ROOT / "runtime/src/main/java/sprig/runtime").rglob("*.java"))
        verify(f"{label}: the whole runtime is written",
               sorted(path.relative_to(java_root / f"sprig/user/{label}/runtime").as_posix()
                      for path in runtime) == shipped, len(runtime))
        # Compiled alone: nothing on the classpath, so the tree must be self-contained.
        classes = project / "own/classes"
        compiled = javac(program + runtime, classes)
        verify(f"{label}: javac compiles the tree on its own", compiled.returncode == 0,
               compiled.stdout + compiled.stderr)
        names = class_files(classes)
        verify(f"{label}: every class is in the project's package",
               names and all(name.startswith(f"sprig/user/{label}/") for name in names), names[:6])
        leaks = [name for name in names if b"sprig/runtime/" in (classes / name).read_bytes()]
        verify(f"{label}: no class refers to sprig.runtime", not leaks, leaks[:6])
        built[label] = (classes, names)

    shared = set(built["alpha"][1]) & set(built["beta"][1])
    verify("the two projects share no class name", not shared, sorted(shared)[:6])

    driver = home / "driver"
    driver.mkdir()
    (driver / "Driver.java").write_text(
        "public final class Driver {\n"
        "    public static void main(String[] args) throws Exception {\n"
        "        // Each module's top-level statements, then its functions again: the\n"
        "        // two store modules keep their own lists.\n"
        "        sprig.user.alpha.$M_main.sprigMain();\n"
        "        sprig.user.beta.$M_main.sprigMain();\n"
        "        System.out.println(sprig.user.alpha.$M_main.fn$describe());\n"
        "        System.out.println(sprig.user.beta.$M_main.fn$describe());\n"
        "    }\n"
        "}\n", encoding="utf-8")
    both = [built["alpha"][0], built["beta"][0]]
    compiled = javac([driver / "Driver.java"], driver / "classes", both)
    verify("Java names both projects' classes", compiled.returncode == 0, compiled.stdout + compiled.stderr)
    expected = ("alpha counts 2, remembers 1\nbeta counts 20, remembers 1\n"
                "alpha counts 2, remembers 2\nbeta counts 20, remembers 2\n")
    for order in (both, both[::-1]):
        ran = java([driver / "classes", *order], "Driver", driver)
        verify(f"both run in one JVM ({order[0].parent.parent.name} first)",
               ran.returncode == 0 and ran.stdout == expected, ran.stdout + ran.stderr)

    # An uncaught failure names the project's own runtime class, and the frames it
    # reports are the generated code's: the runtime's own frames are not among them.
    data, program, runtime = emit_with_runtime(failing, failing / "own")
    compiled = javac(program + runtime, failing / "own/classes")
    crashed = java([failing / "own/classes"], data["mainClass"], failing)
    lines = crashed.stderr.splitlines()
    frames = [line for line in lines if line.startswith("sprig-runtime-frame:")]
    verify("a failure is reported from the project's runtime", compiled.returncode == 0 and crashed.returncode == 1
           and "sprig-runtime-failure: sprig.user.fails.runtime.SprigNumericError: Int addition overflow" in lines
           and frames and all(line.split()[-1].startswith("$M_main.java:") for line in frames),
           crashed.stdout + crashed.stderr + compiled.stderr)


def check_std_modules(home: Path) -> None:
    # Every bundled module, so each runtime class the standard library names is one the copy has.
    modules = sorted(path.name for path in (ROOT / "std").glob("*.spr"))
    imports = "".join(f'import "@std/{name}" as m{index}\n' for index, name in enumerate(modules))
    project = make_project(home, "std-user", {"src/main.spr": imports + 'print("std")\n'})
    _, program, runtime = emit_with_runtime(project, project / "own")
    compiled = javac(program + runtime, project / "own/classes")
    verify(f"all {len(modules)} @std modules compile against the project's runtime",
           compiled.returncode == 0, compiled.stdout + compiled.stderr)
    ran = java([project / "own/classes"], "sprig.user.std_user.$M_main", project)
    verify("and run", ran.returncode == 0 and ran.stdout.strip() == "std", ran.stdout + ran.stderr)


def check_corpus(home: Path) -> None:
    """Each golden program of tests/runtime, built with its own runtime, prints its golden output."""
    project = make_project(home, "corpus", {"src/main.spr": 'print("corpus")\n'})
    programs = []
    for source in sorted((ROOT / "tests/runtime").glob("*.spr")):
        shutil.copy(source, project / "src" / source.name)
        if source.with_suffix(".out").is_file():
            programs.append(source)
    # One project, so one runtime package: its copy is compiled once and shared.
    _, _, runtime = emit_with_runtime(project, project / "own")
    runtime_classes = project / "runtime-classes"
    compiled = javac(runtime, runtime_classes)
    verify("the runtime copy compiles alone", compiled.returncode == 0, compiled.stdout + compiled.stderr)
    if compiled.returncode != 0:
        return

    def one(source: Path) -> tuple[str, bool, str]:
        out = project / "out" / source.stem
        try:
            data, generated, _ = emit_with_runtime(project, out, Path("src") / source.name)
        except RuntimeError as error:
            return source.name, False, str(error)
        built = javac(generated, out / "classes", [runtime_classes])
        if built.returncode != 0:
            return source.name, False, built.stdout + built.stderr
        ran = java([out / "classes", runtime_classes], data["mainClass"], ROOT)
        golden = source.with_suffix(".out").read_text(encoding="utf-8")
        actual = ran.stdout + ran.stderr
        return source.name, ran.returncode == 0 and actual.rstrip("\n") == golden.rstrip("\n"), actual

    with ThreadPoolExecutor(max_workers=min(4, os.cpu_count() or 1)) as pool:
        for name, ok, detail in pool.map(one, programs):
            verify(f"golden {name} with the project's runtime", ok, detail)
    verify("the corpus has programs", bool(programs), "no tests/runtime/*.spr with a golden .out")


def main() -> int:
    if not CLI.is_file() or JAVAC is None or JAVA is None:
        print("FAIL project package: bin/sprig, javac and java are required", file=sys.stderr)
        return 1
    with tempfile.TemporaryDirectory(prefix="sprig project package ") as directory:
        home = Path(directory).resolve()
        check_package_names(home)
        check_single_file(home)
        check_options(home)
        failing = check_cli_project(home)
        check_two_projects(home, failing)
        check_std_modules(home)
        check_corpus(home)
    if failures:
        print(f"project package: {passed} passed, {len(failures)} failed", file=sys.stderr)
        return 1
    print(f"project package: {passed} passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"FAIL project package: {error}", file=sys.stderr)
        raise SystemExit(1)
