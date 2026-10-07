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
        for name in ("Task", "Scope", "Pool", "Channel", "Counter", "Lock", "Latch", "scope", "spawn", "spawn_on",
                     "parallel_map", "await_all", "channel"):
            assert '"name": "' + name + '"' in api or '"name":"' + name + '"' in api, name
        # Every task belongs to a scope: there is no unscoped spawn.
        assert '"name": "spawn"' in api or '"name":"spawn"' in api
        source = root / "unscoped.spr"
        source.write_text('import "@std/concurrent.spr" as concurrent\nlet t = concurrent.spawn(fn() => 1)\n',
                          encoding="utf-8")
        rejected = subprocess.run([str(SPRIG), "check", str(source)], capture_output=True, text=True, encoding="utf-8")
        assert rejected.returncode != 0 and "SPR-CALL-ARITY" in rejected.stdout + rejected.stderr, \
            rejected.stdout + rejected.stderr
    print("concurrent: example, contract (scopes, virtual threads, cancellation, I/O) and API passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
