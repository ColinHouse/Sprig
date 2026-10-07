#!/usr/bin/env python3
"""JVM behavior of @std/concurrent: scopes on virtual threads, tasks, pools, channels, counters, locks, latches.

The contract program prints one line per rule; its golden output is next to it. Timing rules print a
Bool (ten thousand sleeping tasks finish together; cancelled tasks end at once), so the golden stays
deterministic while the real time is checked."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")


def command(cwd, *args):
    result = subprocess.run([str(SPRIG), *map(str, args)], cwd=cwd, capture_output=True,
                            text=True, encoding="utf-8", timeout=300)
    assert result.returncode == 0, result.stdout + result.stderr
    return result.stdout


def user_types(root):
    """A task's result may be any Sprig type (#145).

    Task[T] holds a HostTask[T]; using it with a variant, enum, class, contract or a
    class from another module once failed inside std/concurrent.spr with "has no JVM
    representation as a Java type argument". A Java type argument written as a concrete
    Sprig class is still rejected, since Java member calls on it do not know Sprig subtyping.
    """
    (root / "shapes.spr").write_text("class Point:\n    let x: Int\n    let y: Int\n", encoding="utf-8")
    program = root / "results.spr"
    program.write_text(
        'import java.util.ArrayList as ArrayList\nimport "@std/concurrent.spr" as concurrent\n'
        'import "./shapes.spr" as shapes\n\n'
        "variant Outcome:\n    Ok(value: Int)\n    Failed(problem: String)\n"
        "enum Level:\n    Low\n    High\n"
        "class Box:\n    let label: String\n"
        "class Sink:\n    func write(line: String) -> Int\n"
        "class Counter:\n    var count: Int = 0\n    func write(line: String) -> Int:\n"
        "        count += 1\n        return count\n"
        "conform Counter to Sink\n"
        "generic T:\n    class Holder:\n        let items: ArrayList[T]\n"
        "generic T:\n    func holder() -> Holder[T]:\n        return Holder[T](items=ArrayList[T]())\n\n"
        "func work() -> Outcome:\n    return Outcome.Ok(value=1)\n"
        "func outcome(s: concurrent.Scope) -> Outcome throws Error:\n"
        "    return concurrent.spawn(s, fn() => work()).await()\n"
        "func level(s: concurrent.Scope) -> Level throws Error:\n"
        "    return concurrent.spawn(s, fn() => Level.High).await()\n"
        "func box(s: concurrent.Scope) -> Box throws Error:\n"
        '    return concurrent.spawn(s, fn() => Box(label="b")).await()\n'
        "func sink(s: concurrent.Scope) -> Sink throws Error:\n"
        "    let made: Sink = Counter()\n    return concurrent.spawn(s, fn() => made).await()\n"
        "func point(s: concurrent.Scope) -> shapes.Point throws Error:\n"
        "    return concurrent.spawn(s, fn() => shapes.Point(x=1, y=2)).await()\n\n"
        "print(concurrent.scope(outcome))\nprint(concurrent.scope(level))\nprint(concurrent.scope(box).label)\n"
        'print(concurrent.scope(sink).write("x"))\nprint(concurrent.scope(point))\n'
        "print(concurrent.parallel_map([1, 2], fn(n: Int) => Outcome.Ok(value=n * 10)))\n"
        "let held = holder[Outcome]()\nprint(held.items.size())\n", encoding="utf-8")
    lines = command(root, "run", program).splitlines()
    assert lines == ["Ok(value=1)", "High", "b", "1", "Point(x=1, y=2)", "[Ok(value=10), Ok(value=20)]", "0"], lines
    written = root / "written.spr"
    written.write_text("import java.util.ArrayList as ArrayList\nvariant Outcome:\n    Ok(value: Int)\n"
                       "    Failed(problem: String)\nlet xs = ArrayList[Outcome]()\nprint(xs.size())\n",
                       encoding="utf-8")
    rejected = subprocess.run([str(SPRIG), "check", str(written)], capture_output=True, text=True, encoding="utf-8")
    output = rejected.stdout + rejected.stderr
    assert rejected.returncode != 0 and "has no JVM representation as a Java type argument" in output \
        and "written.spr:5:" in output, output


def main():
    with tempfile.TemporaryDirectory(prefix="sprig-concurrent-") as directory:
        root = Path(directory)
        project = root / "parallel_words"
        shutil.copytree(ROOT / "examples/parallel_words", project,
                        ignore=shutil.ignore_patterns("sprig.lock", "sprig-build"))
        command(project, "resolve")
        example = command(project, "run").splitlines()
        assert example == ["[9, 5, 11, 5]", "[9, 5, 11, 5]", "total 30", "reports 4", "finished 4"], example
        lines = command(ROOT, "run", ROOT / "tests/concurrent/contract.spr").splitlines()
        expected = (ROOT / "tests/concurrent/contract.out").read_text(encoding="utf-8").splitlines()
        assert lines == expected, "\n".join(lines)
        api = command(ROOT, "api", "@std/concurrent.spr", "--json")
        for name in ("Task", "Job", "Scope", "Pool", "Channel", "Counter", "Lock", "Latch", "scope", "scope_run",
                     "spawn", "run", "spawn_on", "parallel_map", "await_all", "channel"):
            assert '"name": "' + name + '"' in api or '"name":"' + name + '"' in api, name
        # Every task belongs to a scope: there is no unscoped spawn.
        assert '"name": "spawn"' in api or '"name":"spawn"' in api
        source = root / "unscoped.spr"
        source.write_text('import "@std/concurrent.spr" as concurrent\nlet t = concurrent.spawn(fn() => 1)\n',
                          encoding="utf-8")
        rejected = subprocess.run([str(SPRIG), "check", str(source)], capture_output=True, text=True, encoding="utf-8")
        assert rejected.returncode != 0 and "SPR-CALL-ARITY" in rejected.stdout + rejected.stderr, \
            rejected.stdout + rejected.stderr
        user_types(root)
    print("concurrent: example, contract (scopes, virtual threads, cancellation, I/O), user result types "
          "and API passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
