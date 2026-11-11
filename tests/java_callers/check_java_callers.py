#!/usr/bin/env python3
"""Java code that calls generated code directly finds the module initialized.

A module's top-level statements, among them the initializers of its top-level
`let` and `var` bindings, run in the generated `$init()`, which the generated
`main` starts. A Java host does not go through `main`: a Fabric entry point
calls `$M_main.fn$register()`, an ordinary Java class writes
`new $Counter().current()`. Such a call used to find the top-level bindings of
that module, and of the modules it imports, at their Java defaults (null, 0,
false) and fail far from the cause. This suite checks the contract that
replaces it:

- the first function called from Java, or the first object with methods made
  there, runs the module's top-level statements first: the imports' before its
  own, and each module's once, however the module is entered afterwards;
- code the statements call is not sent back to initialize, and a binding used
  before its own initializer ran is still reported as that;
- a failing statement fails the call that started it: an unchecked failure as
  it is, a checked Java exception as the cause of an IllegalStateException
  that names the module; the statements are not run again;
- the check is only in modules that have something to initialize, and not in
  the constructor of a class without methods;
- the first calls of several threads run the statements once, and no call
  waits for statements still running: they may start tasks that call the
  module's own functions and wait for those.

Each program is driven in two shapes: as `sprig build` compiles it, and as the
tree `build --emit-java-only --with-runtime` writes for a build tool (what the
Gradle plugin compiles), built here by javac alone.

Evidence level: javac and JVM runtime, outside the checkout.
"""
from __future__ import annotations

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

TIMEOUT = 120

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


def java(classpath: list[Path], main: str, *args: object, cwd: Path) -> subprocess.CompletedProcess[str]:
    command = [JAVA, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
               "-cp", os.pathsep.join(map(str, classpath)), main, *map(str, args)]
    try:
        return subprocess.run(command, cwd=cwd, text=True, encoding="utf-8", capture_output=True, timeout=TIMEOUT)
    except subprocess.TimeoutExpired:
        # A driver that waits for an initialization that waits for it never ends by itself.
        return subprocess.CompletedProcess(command, -1, "", f"{main} did not end within {TIMEOUT} seconds")


def make_project(home: Path, name: str, files: dict[str, str]) -> Path:
    project = home / name
    (project / "src").mkdir(parents=True)
    (project / "sprig.toml").write_text(
        f'[project]\nname = "{name}"\nversion = "0.1.0"\nlanguage = "0.8"\n', encoding="utf-8")
    for relative, text in files.items():
        (project / relative).write_text(text, encoding="utf-8")
    resolved = sprig("resolve", "--offline", cwd=project)
    if resolved.returncode != 0:
        raise RuntimeError(f"resolve failed for {name}: {resolved.stdout}{resolved.stderr}")
    return project


def build_shapes(project: Path) -> dict[str, Path]:
    """The project's classes as `sprig build` compiles them, and as a build tool does."""
    built = sprig("build", "--json", cwd=project)
    if built.returncode != 0:
        raise RuntimeError(f"build failed in {project.name}: {built.stdout}{built.stderr}")
    emitted = sprig("build", "--emit-java-only", "--with-runtime", "-d", "own", "--json", cwd=project)
    try:
        data = json.loads(emitted.stdout)
    except ValueError:
        data = {}
    if emitted.returncode != 0 or "runtimeSources" not in data:
        raise RuntimeError(f"build --emit-java-only --with-runtime failed in {project.name}: "
                           f"{emitted.stdout}{emitted.stderr}")
    compiled = javac([Path(path) for path in data["javaSources"] + data["runtimeSources"]], project / "own/classes")
    if compiled.returncode != 0:
        raise RuntimeError(f"javac failed on the emitted tree of {project.name}: {compiled.stdout}{compiled.stderr}")
    return {"sprig build": project / "sprig-build/classes", "its own runtime": project / "own/classes"}


def drive(name: str, project: Path, shapes: dict[str, Path], driver: str, body: str, expected: str) -> None:
    """Runs `body` as the main method of a Java class against each shape of the project."""
    source = project / "drivers" / f"{driver}.java"
    source.parent.mkdir(exist_ok=True)
    source.write_text(f"public final class {driver} {{\n"
                      "    public static void main(String[] args) throws Exception {\n"
                      f"{body}"
                      "    }\n"
                      "}\n", encoding="utf-8")
    for shape, classes in shapes.items():
        driver_classes = project / "drivers" / (driver + " " + shape)
        compiled = javac([source], driver_classes, [classes])
        ran = java([driver_classes, classes], driver, cwd=project)
        verify(f"{name} ({shape})", compiled.returncode == 0 and ran.returncode == 0 and ran.stdout == expected,
               compiled.stdout + compiled.stderr + ran.stdout + ran.stderr)


STORE = """let names: MutableList[String] = []

func remember(name: String) -> Unit:
    names.append(name)

func seen() -> Int:
    return names.size()
"""

LIMITS = """let limit = 8
let label = "at most"
"""

# No statement of its own: everything it reads is another module's binding.
CALLERS_MAIN = """import "store.spr" as store
import "limits.spr" as limits

func describe() -> String:
    return limits.label + " " + limits.limit + ", now " + store.names.size()
"""


def check_reproduction(home: Path) -> None:
    project = make_project(home, "callers", {"src/main.spr": CALLERS_MAIN, "src/store.spr": STORE,
                                             "src/limits.spr": LIMITS})
    shapes = build_shapes(project)
    drive("a function of a module with a top-level binding", project, shapes, "Reproduction",
          '        sprig.user.callers.$M_store.fn$remember("x");\n'
          "        System.out.println(sprig.user.callers.$M_store.fn$seen());\n",
          "1\n")
    drive("a function that reads the bindings of the modules it imports", project, shapes, "Imports",
          "        System.out.println(sprig.user.callers.$M_main.fn$describe());\n",
          "at most 8, now 0\n")


BASE = """print("base starts")
let origin = "base"

func name() -> String:
    return origin
"""

MIDDLE = """import "base.spr" as base

print("middle starts after " + base.name())
let chain = base.origin + " > middle"

func chain_name() -> String:
    return chain
"""

ORDER_MAIN = """import "middle.spr" as middle
import "base.spr" as base

print("main starts after " + middle.chain_name())
var calls = 0

func call() -> String:
    calls += 1
    return "call " + calls + " sees " + middle.chain + " and " + base.name()
"""

STARTED = "base starts\nmiddle starts after base\nmain starts after base > middle\n"


def check_order(home: Path) -> None:
    project = make_project(home, "order", {"src/main.spr": ORDER_MAIN, "src/middle.spr": MIDDLE,
                                           "src/base.spr": BASE})
    shapes = build_shapes(project)
    drive("the statements run before the first call, imports first, and once", project, shapes, "Once",
          '        System.out.println("java starts");\n'
          "        System.out.println(sprig.user.order.$M_main.fn$call());\n"
          "        System.out.println(sprig.user.order.$M_main.fn$call());\n"
          "        System.out.println(sprig.user.order.$M_base.fn$name());\n"
          "        sprig.user.order.$M_main.$init();\n"
          "        sprig.user.order.$M_main.sprigMain();\n"
          "        System.out.println(sprig.user.order.$M_main.fn$call());\n",
          "java starts\n" + STARTED
          + "call 1 sees base > middle and base\ncall 2 sees base > middle and base\nbase\n"
          "call 3 sees base > middle and base\n")
    drive("a module entered through an import first is not started again", project, shapes, "ImportFirst",
          "        System.out.println(sprig.user.order.$M_base.fn$name());\n"
          "        System.out.println(sprig.user.order.$M_main.fn$call());\n",
          "base starts\nbase\nmiddle starts after base\nmain starts after base > middle\n"
          "call 1 sees base > middle and base\n")
    drive("an explicit start is still one start", project, shapes, "Explicit",
          "        sprig.user.order.$M_main.sprigMain();\n"
          "        System.out.println(sprig.user.order.$M_main.fn$call());\n",
          STARTED + "call 1 sees base > middle and base\n")


OBJECTS = """import java.util.TimerTask as TimerTask

print("objects start")
let step = 5
var made = 0

class Counter:
    var count: Int = 0

    func next() -> Int:
        count += step
        return count

class Tick:
    let label: String

    func run() -> Unit:
        made += 1
        print(label + " " + made + " of step " + step)

conform Tick to TimerTask()
"""


def check_constructors(home: Path) -> None:
    project = make_project(home, "objects", {"src/main.spr": OBJECTS})
    shapes = build_shapes(project)
    drive("an object made with new", project, shapes, "Made",
          "        sprig.user.objects.$Counter counter = new sprig.user.objects.$Counter(0L);\n"
          '        System.out.println(counter.next() + " " + counter.next());\n'
          '        new sprig.user.objects.$Tick("tick").run();\n',
          "objects start\n5 10\ntick 1 of step 5\n")
    drive("an object of a class that extends a Java class", project, shapes, "Subclass",
          '        java.util.TimerTask task = new sprig.user.objects.$Tick("first");\n'
          "        task.run();\n",
          "objects start\nfirst 1 of step 5\n")


RECURSION = """print("starting")

func sum_to(n: Int) -> Int:
    if n == 0:
        return 0
    return n + sum_to(n - 1)

let total = sum_to(4)

func total_value() -> Int:
    return total
"""

# `first` is computed by a function that reads `late`, declared below it.
ORDERING = """print("starting")

func early() -> Int:
    return late + 1

func first_value() -> Int:
    return first

let first = early()
let late = 41
"""

ATTEMPTS = ("        for (int attempt = 1; attempt <= 2; attempt++) {\n"
            "            try {\n"
            "                System.out.println(CALL);\n"
            "            } catch (RuntimeException failure) {\n"
            '                System.out.println(attempt + ": " + failure.getClass().getSimpleName() + ": "\n'
            "                        + failure.getMessage());\n"
            "            }\n"
            "        }\n")


def check_reentry(home: Path) -> None:
    project = make_project(home, "recursion", {"src/main.spr": RECURSION})
    drive("functions the statements call do not start them again", project, build_shapes(project), "Recursion",
          "        System.out.println(sprig.user.recursion.$M_main.fn$total_value());\n"
          "        System.out.println(sprig.user.recursion.$M_main.fn$total_value());\n",
          "starting\n10\n10\n")
    project = make_project(home, "ordering", {"src/main.spr": ORDERING})
    drive("a binding used before its initializer is still reported as that", project, build_shapes(project),
          "Ordering", ATTEMPTS.replace("CALL", "sprig.user.ordering.$M_main.fn$first_value()"),
          "starting\n"
          "1: SprigInitializationError: Top-level 'late' was used before its initializer ran\n"
          "2: SprigInitializationError: Top-level 'first' was used before its initializer ran\n")


FAILING = """print("before")

func fail() -> Int throws Error:
    throw Error("no config")

let value = fail()

func read() -> Int:
    return value
"""

# A checked Java exception, which a top-level statement may leave uncaught.
CHECKED = """import java.net.URI as URI

print("before")
let address = URI("::bad uri")

func host() -> String?:
    return address.getHost()
"""


def check_failures(home: Path) -> None:
    project = make_project(home, "failing", {"src/main.spr": FAILING})
    drive("an unchecked failure reaches the first call as it is, and nothing runs twice", project,
          build_shapes(project), "Failing", ATTEMPTS.replace("CALL", "sprig.user.failing.$M_main.fn$read()"),
          "before\n"
          "1: SprigError: no config\n"
          "2: SprigInitializationError: Top-level 'value' was used before its initializer ran\n")
    project = make_project(home, "checked", {"src/main.spr": CHECKED})
    drive("a checked exception arrives as the cause of an unchecked one", project, build_shapes(project), "Checked",
          "        try {\n"
          "            System.out.println(sprig.user.checked.$M_main.fn$host());\n"
          "        } catch (RuntimeException failure) {\n"
          '            System.out.println(failure.getClass().getName() + ": " + failure.getMessage());\n'
          '            System.out.println("cause: " + failure.getCause());\n'
          "        }\n",
          "before\n"
          "java.lang.IllegalStateException: Sprig module 'main' failed to initialize: "
          "java.net.URISyntaxException: Expected scheme name at index 0: ::bad uri\n"
          "cause: java.net.URISyntaxException: Expected scheme name at index 0: ::bad uri\n")


PURE = """class Plain:
    let size: Int

class Named:
    let name: String

    func shout() -> String:
        return name + "!"

func twice(value: Int) -> Int:
    return value * 2

func thrice(value: Int) -> Int:
    return value * 3
"""

STATEFUL = """let limit = 3

class Size:
    let width: Int

class Box:
    let width: Int

    func fits() -> Bool:
        return width <= limit

func within(value: Int) -> Bool:
    return value <= limit

func over(value: Int) -> Bool:
    return value > limit
"""

# No statement of its own, but what it imports has one.
USER = """import "stateful.spr" as stateful

class Shelf:
    let width: Int

    func holds(box: stateful.Box) -> Bool:
        return box.width <= width

func narrow(value: Int) -> Bool:
    return stateful.within(value)
"""

SHAPES_MAIN = """import "pure.spr" as pure
import "user.spr" as user

func run() -> Bool:
    return user.narrow(pure.twice(1))
"""

CHECK = "$ensureInit();"


def check_cost(home: Path) -> None:
    """Where the entry check is: this is what a call costs, so it is pinned as text."""
    project = make_project(home, "shapes", {"src/main.spr": SHAPES_MAIN, "src/pure.spr": PURE,
                                            "src/stateful.spr": STATEFUL, "src/user.spr": USER})
    emitted = sprig("build", "--emit-java-only", "-d", "out", "--json", cwd=project)
    package = project / "out/java/sprig/user/shapes"

    def checks(name: str) -> int:
        path = package / name
        return path.read_text(encoding="utf-8").count(CHECK) if path.is_file() else -1

    verify("the program is emitted", emitted.returncode == 0 and (package / "$M_pure.java").is_file(),
           emitted.stdout + emitted.stderr)
    for name in ("$M_pure.java", "$Plain.java", "$Named.java"):
        verify(f"nothing to initialize, no check: {name}", checks(name) == 0, checks(name))
    verify("one check in each function of a module with a statement", checks("$M_stateful.java") == 2,
           checks("$M_stateful.java"))
    verify("one check in the constructor of its class with a method", checks("$Box.java") == 1, checks("$Box.java"))
    verify("no check in the constructor of its class without methods", checks("$Size.java") == 0,
           checks("$Size.java"))
    for name in ("$M_user.java", "$Shelf.java", "$M_main.java"):
        verify(f"a module whose imports have a statement is checked as well: {name}", checks(name) == 1,
               checks(name))


# The statement waits for tasks that call word_count, a function of the module that
# is still being initialized. An entry check that waited for the statements to end
# would keep those tasks waiting, and the statement with them: the driver would hang.
TASKS = """import "@std/concurrent.spr" as concurrent

func word_count(line: String) -> Int:
    return line.split(" ").size()

func count_all(s: concurrent.Scope) -> List[Int] throws Error:
    let tasks: MutableList[concurrent.Task[Int]] = []
    for line in ["the quick brown fox", "a lazy dog"]:
        tasks.append(concurrent.spawn(s, fn() => word_count(line)))
    return concurrent.await_all(tasks)

let counts = concurrent.scope(count_all)

func total() -> Int:
    var sum = 0
    for count in counts:
        sum += count
    return sum
"""


def check_tasks(home: Path) -> None:
    project = make_project(home, "tasks", {"src/main.spr": TASKS})
    drive("statements may start tasks that call the module and wait for them", project, build_shapes(project),
          "Tasks", "        System.out.println(sprig.user.tasks.$M_main.fn$total());\n", "7\n")


THREADS = """print("init")

func ping() -> Int:
    return 1
"""

# Each round loads the program in a class loader of its own, so every round has a
# first call; the threads spin until all of them are released at once.
THREADS_DRIVER = """import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class Threads {
    private static volatile boolean go;

    public static void main(String[] args) throws Exception {
        int rounds = Integer.parseInt(args[0]);
        int threads = Integer.parseInt(args[1]);
        URL[] classes = {Path.of(args[2]).toUri().toURL()};
        int wrong = 0;
        for (int round = 0; round < rounds; round++) {
            try (URLClassLoader loader = new URLClassLoader(classes, ClassLoader.getPlatformClassLoader())) {
                Method ping = Class.forName("sprig.user.threads.$M_main", true, loader).getMethod("fn$ping");
                long[] results = new long[threads];
                Thread[] workers = new Thread[threads];
                go = false;
                for (int index = 0; index < threads; index++) {
                    int slot = index;
                    workers[index] = new Thread(() -> {
                        while (!go) {
                            Thread.onSpinWait();
                        }
                        try {
                            results[slot] = (Long) ping.invoke(null);
                        } catch (ReflectiveOperationException failure) {
                            results[slot] = -1;
                        }
                    });
                    workers[index].start();
                }
                Thread.sleep(1);
                go = true;
                for (Thread worker : workers) {
                    worker.join();
                }
                for (long result : results) {
                    if (result != 1) {
                        wrong++;
                    }
                }
            }
        }
        System.out.println("wrong results: " + wrong);
    }
}
"""

ROUNDS = 300
THREADS_PER_ROUND = 4


def check_threads(home: Path) -> None:
    project = make_project(home, "threads", {"src/main.spr": THREADS})
    shapes = build_shapes(project)
    source = project / "drivers" / "Threads.java"
    source.parent.mkdir()
    source.write_text(THREADS_DRIVER, encoding="utf-8")
    compiled = javac([source], project / "drivers/classes")
    for shape, classes in shapes.items():
        ran = java([project / "drivers/classes"], "Threads", ROUNDS, THREADS_PER_ROUND, classes, cwd=project)
        lines = ran.stdout.splitlines()
        verify(f"first calls from {THREADS_PER_ROUND} threads run the statements once ({shape})",
               compiled.returncode == 0 and ran.returncode == 0 and lines.count("init") == ROUNDS
               and lines[-1:] == ["wrong results: 0"],
               f"{lines.count('init')} starts in {ROUNDS} rounds; {lines[-1:]} {compiled.stderr}{ran.stderr}")


def main() -> int:
    if not CLI.is_file() or JAVAC is None or JAVA is None:
        print("FAIL java callers: bin/sprig, javac and java are required", file=sys.stderr)
        return 1
    with tempfile.TemporaryDirectory(prefix="sprig java callers ") as directory:
        home = Path(directory).resolve()
        check_reproduction(home)
        check_order(home)
        check_constructors(home)
        check_reentry(home)
        check_failures(home)
        check_cost(home)
        check_tasks(home)
        check_threads(home)
    if failures:
        print(f"java callers: {passed} passed, {len(failures)} failed", file=sys.stderr)
        return 1
    print(f"java callers: {passed} passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"FAIL java callers: {error}", file=sys.stderr)
        raise SystemExit(1)
