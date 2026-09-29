#!/usr/bin/env python3
"""Regenerate every Git-tracked sprig.lock with the canonical resolver.

Tracked locks are generated artifacts. After a Git conflict take either
complete side for the lockfile, run this script, and commit the result:
`sprig resolve` output is the only authority, never ours/theirs.
"""
from pathlib import Path
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
LAUNCHER = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")


def tracked_locks():
    result = subprocess.run(["git", "ls-files", "--", "*sprig.lock"], cwd=ROOT,
                            text=True, capture_output=True)
    if result.returncode != 0:
        print("error: cannot list tracked lockfiles with git:", file=sys.stderr)
        print(result.stderr, end="", file=sys.stderr)
        return None
    return sorted(line for line in result.stdout.splitlines() if line.strip())


def main():
    if not LAUNCHER.is_file():
        print(f"error: {LAUNCHER.relative_to(ROOT)} not found; build first:", file=sys.stderr)
        print("  python3 scripts/build.py", file=sys.stderr)
        return 2
    locks = tracked_locks()
    if locks is None:
        return 2
    refreshed = []
    for lock in locks:
        project = ROOT / Path(lock).parent
        if not (project / "sprig.toml").is_file():
            print(f"error: {lock} has no sprig.toml next to it", file=sys.stderr)
            return 1
        result = subprocess.run([str(LAUNCHER), "resolve", "--offline"], cwd=project,
                                text=True, capture_output=True)
        if result.returncode != 0:
            print(f"error: `sprig resolve --offline` failed for {lock}", file=sys.stderr)
            print(result.stdout, end="", file=sys.stderr)
            print(result.stderr, end="", file=sys.stderr)
            print("hint: this script never goes online; populate the Maven/Git cache "
                  "with a normal `sprig resolve` first.", file=sys.stderr)
            return 1
        refreshed.append(lock)
        print(f"refreshed {lock}")
    print(f"{len(refreshed)} tracked lockfiles refreshed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
