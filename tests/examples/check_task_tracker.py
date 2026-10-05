#!/usr/bin/env python3
"""Exercise the documented task tracker through its installed CLI surface."""
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
PROJECT = ROOT / "examples" / "task-tracker"
SPRIG = Path(os.environ.get("SPRIG_BIN", ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")))
CHECKS = 0


def check(name, condition, detail=""):
    global CHECKS
    CHECKS += 1
    if not condition:
        raise AssertionError(f"{name}: {detail}")


def run(*args, env):
    return subprocess.run([str(SPRIG), *args], cwd=ACTIVE_PROJECT, env=env,
                          capture_output=True, text=True, encoding="utf-8")


ACTIVE_PROJECT = PROJECT


def main():
    tutorial_source = ROOT / "website" / "snippets" / "tutorial" / "task_tracker.spr"
    example_source = PROJECT / "src" / "main.spr"
    check("tutorial and project use the same source",
          tutorial_source.read_bytes() == example_source.read_bytes(),
          "copy the verified example source to its tutorial snippet")
    with tempfile.TemporaryDirectory(prefix="sprig-task-tracker-") as temp:
        global ACTIVE_PROJECT
        temp_root = Path(temp)
        ACTIVE_PROJECT = temp_root / "task-tracker"
        initialized = subprocess.run([str(SPRIG), "init", ACTIVE_PROJECT.name],
                                     cwd=temp_root, capture_output=True, text=True,
                                     encoding="utf-8")
        check("init project", initialized.returncode == 0,
              initialized.stdout + initialized.stderr)
        target_source = ACTIVE_PROJECT / "src" / "main.spr"
        target_source.write_bytes(example_source.read_bytes())
        resolved = run("resolve", env=os.environ.copy())
        check("resolve initialized project", resolved.returncode == 0,
              resolved.stdout + resolved.stderr)

        store = temp_root / "tasks.json"
        env = os.environ.copy()
        env["SPRIG_TASKS_FILE"] = str(store)
        checked = run("check", "--offline", "--json", env=env)
        check("static check", checked.returncode == 0, checked.stdout + checked.stderr)

        added1 = run("run", "--offline", "--", "add", "Read the tutorial", env=env)
        added2 = run("run", "--offline", "--", "add", "Build a tool", env=env)
        check("add commands", added1.returncode == 0 and added2.returncode == 0,
              added1.stdout + added1.stderr + added2.stdout + added2.stderr)

        listed = run("run", "--offline", "--", "list", env=env)
        check("list output", listed.returncode == 0 and listed.stdout ==
              "1 [ ] Read the tutorial\n2 [ ] Build a tool\n", listed.stdout + listed.stderr)

        done = run("run", "--offline", "--", "done", "1", env=env)
        listed_done = run("run", "--offline", "--", "list", env=env)
        check("done command", done.returncode == 0 and listed_done.returncode == 0
              and listed_done.stdout ==
              "1 [x] Read the tutorial\n2 [ ] Build a tool\n",
              done.stdout + done.stderr + listed_done.stdout + listed_done.stderr)

        document = json.loads(store.read_text(encoding="utf-8"))
        check("persisted JSON", document == [
            {"id": 1, "title": "Read the tutorial", "done": True},
            {"id": 2, "title": "Build a tool", "done": False},
        ], repr(document))

        store.write_text("{broken", encoding="utf-8")
        malformed = run("run", "--offline", "--", "list", env=env)
        check("malformed JSON is rejected", malformed.returncode != 0,
              malformed.stdout + malformed.stderr)

    print(f"task tracker: {CHECKS} checks passed")


if __name__ == "__main__":
    main()
