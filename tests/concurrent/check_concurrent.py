#!/usr/bin/env python3
"""JVM behavior of the sprig-concurrent library: tasks, pools, channels, counters, locks, latches."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")


def command(project, *args):
    result = subprocess.run([str(SPRIG), *map(str, args)], cwd=project, capture_output=True,
                            text=True, encoding="utf-8", timeout=180)
    assert result.returncode == 0, result.stdout + result.stderr
    return result.stdout


def main():
    with tempfile.TemporaryDirectory(prefix="sprig-concurrent-") as directory:
        root = Path(directory)
        project = root / "examples/parallel_words"
        shutil.copytree(ROOT / "examples/parallel_words", project,
                        ignore=shutil.ignore_patterns("sprig.lock", "sprig-build"))
        shutil.copytree(ROOT / "libraries/sprig-concurrent", root / "libraries/sprig-concurrent",
                        ignore=shutil.ignore_patterns("sprig.lock", "sprig-build"))
        command(project, "resolve")
        example = command(project, "run").splitlines()
        assert example == ["[9, 5, 11, 5]", "[9, 5, 11, 5]", "total 30", "reports 4", "finished 4"], example
        shutil.copy(ROOT / "tests/concurrent/contract.spr", project / "src/contract.spr")
        lines = command(project, "run", "src/contract.spr").splitlines()
        expected = [
            "42", "true",
            "failed: true",
            "[1, 4, 9, 16]",
            "true", "[3, 1, 2]", "[3, 1, 2]", "pool is shut down; it accepts no more tasks",
            "10000", "10000", "true", "0",
            "1500", "3000",
            "[a, b, c]", "3", "true", "-1", "null", "true", "false", "1",
            "true", "0",
            "task did not finish within 50 ms", "true", "true",
        ]
        assert lines == expected, "\n".join(lines)
        api = command(project, "api", "@concurrent/concurrent.spr", "--json")
        for name in ("Task", "Pool", "Channel", "Counter", "Lock", "Latch", "spawn", "parallel_map", "channel"):
            assert '"name": "' + name + '"' in api or '"name":"' + name + '"' in api, name
    print("concurrent: example and contract passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
