#!/usr/bin/env python3
"""Deterministic evaluation runner for the Sprig coding-agent task pack.

This runner scores a *submission* against mechanical acceptance scripts. It does
not run an LLM and does not claim model performance. Each task is worth one
point; a task passes only when its accept.py exits zero.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

HERE = Path(__file__).resolve().parent
TASKS = HERE / "tasks"
ROOT = HERE.parents[1]


def task_dirs(selected):
    if selected:
        return [TASKS / selected]
    return sorted(path for path in TASKS.iterdir() if (path / "task.json").is_file())


def evaluate(task, submission, launcher):
    temp = Path(tempfile.mkdtemp(prefix=f"sprig-eval-{task.name}-"))
    try:
        shutil.copytree(task / "initial", temp, dirs_exist_ok=True)
        if submission is not None:
            shutil.copytree(submission, temp, dirs_exist_ok=True)
        env = dict(os.environ, SPRIG=str(launcher), PYTHONUTF8="1")
        result = subprocess.run([sys.executable, str(task / "accept.py"), str(temp)],
                                cwd=task, env=env, text=True, capture_output=True, timeout=300)
        return result.returncode == 0, (result.stdout + result.stderr).strip()
    finally:
        shutil.rmtree(temp, ignore_errors=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--submission", type=Path,
                        help="directory overlaid on each task's initial files; omit to score initial state")
    parser.add_argument("--task", help="evaluate only this task id")
    parser.add_argument("--sdk", type=Path, default=ROOT, help="SDK root containing bin/sprig or, on Windows, bin/sprig.cmd")
    parser.add_argument("--expect-unsolved", action="store_true",
                        help="require every initial/unmodified task to fail (fixture self-check)")
    args = parser.parse_args()
    sdk = args.sdk.resolve()
    launcher = sdk / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
    if not launcher.is_file():
        raise SystemExit(f"SDK launcher not found: {launcher}")

    score = 0
    total = 0
    unsolved = 0
    for task in task_dirs(args.task):
        total += 1
        passed, detail = evaluate(task, args.submission, launcher)
        if args.expect_unsolved:
            unsolved += 1 if not passed else 0
            reason = "initial state unexpectedly passed" if passed else "initial state fails as designed"
            print(f"{'PASS' if not passed else 'FAIL'} {task.name}: {reason}")
        else:
            score += 1 if passed else 0
            print(f"{'PASS' if passed else 'FAIL'} {task.name}: {detail if not passed else 'accepted'}")
    if args.expect_unsolved:
        print(f"fixture self-check: {unsolved}/{total} initial states are correctly unsolved")
        return 0 if unsolved == total else 1
    print(f"score: {score}/{total} deterministic task checks passed")
    return 0 if score == total else 1


if __name__ == "__main__":
    raise SystemExit(main())
