#!/usr/bin/env python3
"""Sprig-written agent tools consume compiler JSON through the CLI library."""
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
TOOLS = ROOT / "examples" / "agent_tools"
passed = 0
failed = []


def run(project, *args):
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=project, text=True,
                          encoding="utf-8", capture_output=True, timeout=180)


def verify(name, ok, detail=""):
    global passed
    if ok:
        passed += 1
    else:
        failed.append(name)
        print(f"FAIL {name}: {detail[:900]}")


def snapshot(project, target, destination):
    proc = run(project, "api", target, "--json")
    assert proc.returncode == 0, (target, proc.stdout, proc.stderr)
    destination.write_text(proc.stdout, encoding="utf-8")
    return destination


resolved = run(TOOLS, "resolve", "--offline")
verify("tools resolve", resolved.returncode == 0, resolved.stdout + resolved.stderr)

with tempfile.TemporaryDirectory(prefix="sprig-agent-tools-") as temp:
    work = Path(temp)
    module_snapshot = snapshot(TOOLS, ROOT / "libraries/sprig-cli/src/cli.spr", work / "cli.json")

    report = run(TOOLS, "run", "--bin", "api-report", "--offline", "--", module_snapshot)
    verify("api-report module", report.returncode == 0 and "# API report:" in report.stdout
           and "function parse(" in report.stdout and "throws Error" in report.stdout,
           f"{report.stdout}{report.stderr}")

    output = work / "report.md"
    written = run(TOOLS, "run", "--bin", "api-report", "--offline", "--", module_snapshot, "-o", output)
    verify("api-report file output", written.returncode == 0 and output.is_file()
           and "# API report:" in output.read_text(encoding="utf-8"), written.stdout + written.stderr)

    usage = run(TOOLS, "run", "--bin", "api-report", "--offline", "--")
    verify("api-report usage", usage.returncode == 0 and "Usage: api-report" in usage.stdout, usage.stdout)

    ledger = ROOT / "examples/ledger"
    if not (ledger / "sprig.lock").is_file():
        run(ledger, "resolve", "--offline")
    if (ledger / "sprig.lock").is_file():
        project_snapshot = snapshot(ledger, ".", work / "project.json")
        project = json.loads(project_snapshot.read_text(encoding="utf-8"))
        if project.get("kind") == "sprig-project":
            inventory_report = run(TOOLS, "run", "--bin", "api-report", "--offline", "--", project_snapshot)
            verify("api-report project", inventory_report.returncode == 0
                   and "## @web/app.spr" in inventory_report.stdout,
                   f"{inventory_report.stdout}{inventory_report.stderr}")

    bad = work / "bad.spr"
    bad.write_text('let value: String? = null\nprint(value.length())\n', encoding="utf-8")
    checked = run(TOOLS, "check", bad, "--json")
    assert checked.returncode == 1, checked.stdout
    diagnostics = work / "diagnostics.json"
    diagnostics.write_text(checked.stdout, encoding="utf-8")
    summary = run(TOOLS, "run", "--bin", "diag-summary", "--offline", "--", diagnostics)
    verify("diag-summary groups",
           summary.returncode == 0 and "diagnostics: 1 (1 error, 0 warning)" in summary.stdout
           and "SPR-TYPE-NULLABLE: 1" in summary.stdout and "  TYPE: 1" in summary.stdout,
           f"{summary.stdout}{summary.stderr}")

    before = work / "before.spr"
    before.write_text('func greet(name: String) -> String:\n    return "hi " + name\n\n'
                      'enum Mode:\n    FAST\n    SLOW\n\nlet answer: Int = 41\n', encoding="utf-8")
    after = work / "after.spr"
    after.write_text('func greet(name: String, excited: Bool) -> String:\n    return "hi " + name\n\n'
                     'enum Mode:\n    FAST\n    SLOW\n\n'
                     'func shout(text: String) -> String:\n    return text.toUpperCase()\n\n'
                     'class Extra:\n    let name: String\n\nlet extra: String = "new"\n', encoding="utf-8")
    before_snapshot = snapshot(TOOLS, before, work / "before.json")
    after_snapshot = snapshot(TOOLS, after, work / "after.json")
    for path, label in ((before_snapshot, "fixture"), (after_snapshot, "fixture")):
        data = json.loads(path.read_text(encoding="utf-8"))
        data["module"] = label
        path.write_text(json.dumps(data), encoding="utf-8")
    diff = run(TOOLS, "run", "--bin", "api-diff", "--offline", "--", before_snapshot, after_snapshot)
    verify("api-diff detects changes",
           diff.returncode == 0 and "added=4" in diff.stdout and "removed=1" in diff.stdout
           and "changed=1" in diff.stdout, f"{diff.stdout}{diff.stderr}")
    same = run(TOOLS, "run", "--bin", "api-diff", "--offline", "--", before_snapshot, before_snapshot)
    verify("api-diff self is clean", same.returncode == 0 and "added=0 removed=0 changed=0" in same.stdout,
           f"{same.stdout}{same.stderr}")

print(f"Sprig-written agent tools: {passed} checks passed, {len(failed)} failed")
for name in failed:
    print(f"failed: {name}")
raise SystemExit(1 if failed else 0)
