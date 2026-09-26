#!/usr/bin/env python3
"""Deterministic recording rehearsal: real queries, deliberate error, repair/run.

This is an automated replay, not a claim that a fresh agent authored the patch.
The original project and fixture tree are never modified.
"""
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, default=ROOT, help="Built repo or extracted SDK root")
    parser.add_argument("--pause", action="store_true", help="Press Enter between recorded steps")
    args = parser.parse_args()
    sdk = args.sdk.resolve()
    cli = sdk / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
    if not cli.is_file():
        raise SystemExit("Build first or point --sdk at an extracted archive.")

    def command(label, arguments, cwd, expected=0):
        print(f"\n## {label}\n$ sprig {' '.join(arguments)}", flush=True)
        if args.pause:
            input("Press Enter to execute...")
        result = subprocess.run([str(cli), *arguments], cwd=cwd,
                                capture_output=True, text=True, encoding="utf-8")
        print(result.stdout, end="", flush=True)
        if result.stderr:
            print(result.stderr, end="", flush=True)
        if result.returncode != expected:
            raise AssertionError(f"{label}: exit {result.returncode}, expected {expected}")
        return result

    with tempfile.TemporaryDirectory(prefix="sprig-recording-") as temp:
        stage = Path(temp)
        # Preserve repository-relative std imports, with only this project's tree copied.
        project = stage / "examples/showcases/repository_audit"
        shutil.copytree(sdk / "examples/showcases/repository_audit", project)
        shutil.copytree(sdk / "std", stage / "std")
        for stale in project.glob("sprig.lock"):
            stale.unlink()  # disposable copy only; resolve binds paths for this copy
        command("Ask what is implemented", ["capabilities", "--json"], stage)
        command("Ask for generic syntax", ["help", "generics", "--json"], stage)
        print("\nProject files:", flush=True)
        for file in sorted(project.rglob("*.spr")):
            print(file.relative_to(stage), flush=True)
        print((project / "sprig.toml").read_text(encoding="utf-8"), flush=True)
        command("Resolve the actual project", ["resolve", "--json"], project)
        command("Check the real tool", ["check", "--json"], project)
        source = project / "src/metrics.spr"
        good = source.read_text(encoding="utf-8")
        old, bad = 'var lines: Int = 0', 'var lines: Int = "zero"'
        if good.count(old) != 1:
            raise AssertionError("Demo mutation anchor changed; update script explicitly.")
        print(f"\nDeliberate rehearsal edit: {old} → {bad}", flush=True)
        source.write_text(good.replace(old, bad), encoding="utf-8")
        failed = command("Inspect structured type error", ["check", "--json"], project, expected=1)
        diagnostics = json.loads(failed.stdout)["diagnostics"]
        if not any(d["code"] == "SPR-TYPE-ASSIGN" and d.get("expectedType") == "Int"
                   and d.get("actualType") == "String" for d in diagnostics):
            raise AssertionError("Expected a type diagnostic for the deliberate String→Int error.")
        source.write_text(good, encoding="utf-8")
        command("Repair and recheck", ["check", "--json"], project)
        command("Run on real input", ["run", "--", "fixtures/tree", "report.json"], project)
        report = project / "report.json"
        data = json.loads(report.read_text(encoding="utf-8"))
        print("\nGenerated report:", json.dumps(data, ensure_ascii=False, sort_keys=True), flush=True)
    print("\nContribute: pick an agent-friendly issue, read AGENTS.md, run verify, review, open a PR.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
