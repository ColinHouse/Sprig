#!/usr/bin/env python3
"""Run only the gates a change affects.

    python3 scripts/test-affected.py [--base REF] [--list] [--jobs N]

Changed files are `git diff --name-only <base>...HEAD` (base defaults to the
merge base with origin/main) plus staged, unstaged and untracked files. Paths
map to gates through tests/test-map.json; a changed test or feature file that
tests/agent_tooling/capability-test-map.json names selects that feature's
suites; a changed suite script selects itself. A path no rule covers, or a
change to the runner, the build or the map itself, runs the full test.py. A
smoke set (the build plus a handful of goldens) always runs. The selected gates
run through the same parallel runner as test.py.
"""
import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent / "internal"))
import gate_runner  # noqa: E402

ROOT = gate_runner.ROOT
CAPABILITY_MAP = ROOT / "tests" / "agent_tooling" / "capability-test-map.json"


def git(*args):
    result = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, encoding="utf-8")
    return result.returncode, result.stdout


def merge_base(base):
    if base:
        code, out = git("merge-base", base, "HEAD")
        if code == 0:
            return out.strip()
        raise SystemExit(f"cannot resolve --base {base}")
    for candidate in ("origin/main", "main"):
        code, out = git("merge-base", candidate, "HEAD")
        if code == 0:
            return out.strip()
    return None


def changed_files(base):
    """Committed changes since the base, plus staged, unstaged and untracked files."""
    files = set()
    merge = merge_base(base)
    if merge:
        files.update(git("diff", "--name-only", merge + "...HEAD")[1].split("\n"))
        files.update(git("diff", "--name-only", merge)[1].split("\n"))
    files.update(git("diff", "--name-only", "--cached")[1].split("\n"))
    files.update(git("diff", "--name-only")[1].split("\n"))
    files.update(git("ls-files", "--others", "--exclude-standard")[1].split("\n"))
    return sorted(f for f in files if f.strip()), merge


def select(changed, test_map, capability_map):
    """The gates for the changed paths, or None when everything must run.

    Returns (gates, reasons, full_reason).
    """
    gates = set()
    reasons = {}
    rules = [(entry["paths"], [gate_runner.glob_to_regex(p) for p in entry["paths"]], entry["gates"])
             for entry in test_map["map"]]
    full_triggers = set(test_map.get("full_on_change", []))
    known_gates = set(test_map["gates"])
    fixture_to_features = {}
    for feature, fixtures in capability_map.items():
        for fixture in fixtures:
            fixture_to_features.setdefault(fixture, []).append(feature)
    for path in changed:
        selected = []
        if path in full_triggers:
            return None, reasons, f"{path} changes the runner, the build or the map"
        if path in known_gates:
            selected.append(path)
        for patterns, regexes, rule_gates in rules:
            if any(regex.match(path) for regex in regexes):
                for gate in rule_gates:
                    if gate == "self":
                        if path in known_gates:
                            selected.append(path)
                        else:
                            # A helper module next to the suites of a test directory
                            # selects every suite of that directory.
                            directory = str(Path(path).parent).replace("\\", "/") + "/"
                            selected.extend(g for g in known_gates if g.startswith(directory))
                    else:
                        selected.append(gate)
        if path in fixture_to_features:
            for feature in fixture_to_features[path]:
                for fixture in capability_map[feature]:
                    if fixture in known_gates:
                        selected.append(fixture)
                    elif fixture.endswith(".spr"):
                        if fixture.startswith(("tests/runtime/", "tests/visitor/")):
                            selected.append("goldens")
                        elif fixture.startswith("tests/semantics/"):
                            selected.append("scripts/check_cases.py")
                        elif fixture.startswith("tests/syntax/"):
                            selected.append("syntax")
        if not selected:
            return None, reasons, f"{path} matches no rule"
        for gate in selected:
            reasons.setdefault(gate, []).append(path)
        gates.update(selected)
    return gates, reasons, None


def smoke_jobs(test_map):
    smoke = test_map["smoke"]
    files = {ROOT / f for f in smoke["goldens"]}
    return gate_runner.golden_jobs(files)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", help="the ref to diff against (default: merge base with origin/main)")
    parser.add_argument("--list", action="store_true", help="print the selected gates and exit")
    parser.add_argument("--jobs", type=int, default=None, help="parallel jobs (default as test.py)")
    args = parser.parse_args(argv)
    jobs = args.jobs if args.jobs is not None else gate_runner.default_jobs()
    test_map = gate_runner.load_map()
    capability_map = json.loads(CAPABILITY_MAP.read_text(encoding="utf-8"))
    changed, merge = changed_files(args.base)
    gates, reasons, full_reason = select(changed, test_map, capability_map)
    print(f"changed files: {len(changed)}" + (f" (since {merge[:10]})" if merge else " (no base: working tree only)"))
    for path in changed:
        print("  " + path)
    if gates is None:
        print(f"full run: {full_reason}")
        selected = gate_runner.full_gates(test_map)
    else:
        selected = [name for name in test_map["gates"] if name in gates]
        print(f"selected gates: {len(selected)}" if selected else "selected gates: none (smoke only)")
        for gate in selected:
            print(f"  {gate}  <- {', '.join(sorted(set(reasons[gate]))[:3])}" + (" ..." if len(set(reasons[gate])) > 3 else ""))
    print("smoke: " + test_map["smoke"]["build"] + " + " + ", ".join(Path(f).name for f in test_map["smoke"]["goldens"]))
    if args.list:
        return 0
    build = subprocess.run([sys.executable, str(ROOT / test_map["smoke"]["build"])], cwd=ROOT)
    if build.returncode:
        print("build failed", file=sys.stderr)
        return build.returncode
    parallel, serial = gate_runner.jobs_for(selected, test_map)
    smoke = smoke_jobs(test_map) if gates is not None else []
    seen = {job.name for job in parallel}
    parallel = [job for job in smoke if job.name not in seen] + parallel
    return gate_runner.run_jobs(parallel, serial, jobs, label="test-affected.py")


if __name__ == "__main__":
    raise SystemExit(main())
