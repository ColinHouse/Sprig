#!/usr/bin/env python3
"""The gate runner shared by scripts/test.py and scripts/test-affected.py.

Gates come from tests/test-map.json: suite scripts and two builtin sets of
per-file checks ("syntax", "goldens"). Each gate, and each file of a builtin
gate, is one job. Jobs of the parallel group run in a thread pool over
subprocesses; the serial group runs alone afterwards, one job at a time, in
the listed order. Every job's output is buffered and printed as one block when
it finishes, the final record keeps the map's order, and the summary line and
exit status are those of the historical test.py.
"""
from concurrent.futures import ThreadPoolExecutor
import fnmatch
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import threading
import time

ROOT = Path(__file__).resolve().parents[2]
MAP_PATH = ROOT / "tests" / "test-map.json"
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
DEFAULT_JOBS_CAP = 8


def load_map():
    return json.loads(MAP_PATH.read_text(encoding="utf-8"))


def default_jobs():
    """CPU count capped at 8; SPRIG_TEST_JOBS overrides the default."""
    override = os.environ.get("SPRIG_TEST_JOBS", "").strip()
    if override:
        try:
            return max(1, int(override))
        except ValueError:
            print(f"SPRIG_TEST_JOBS={override!r} is not a number; using the CPU count", file=sys.stderr)
    return max(1, min(os.cpu_count() or 1, DEFAULT_JOBS_CAP))


def full_gates(test_map):
    """The gates of the full test.py run, in order."""
    return [name for name, spec in test_map["gates"].items() if spec.get("full", True)]


def glob_to_regex(pattern):
    """A path glob with '**' (any directories) and '*' (within one segment)."""
    out = ""
    i = 0
    while i < len(pattern):
        c = pattern[i]
        if pattern.startswith("**/", i):
            out += "(?:.*/)?"
            i += 3
        elif pattern.startswith("**", i):
            out += ".*"
            i += 2
        elif c == "*":
            out += "[^/]*"
            i += 1
        elif c == "?":
            out += "[^/]"
            i += 1
        else:
            out += re.escape(c)
            i += 1
    return re.compile("^" + out + "$")


def sprig(*args, cwd=ROOT):
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=cwd, capture_output=True,
                          text=True, encoding="utf-8")


# ----------------------------------------------------------------------
# Jobs
# ----------------------------------------------------------------------

class Job:
    def __init__(self, gate, name, run, quiet=False):
        self.gate = gate          # the gate this job belongs to
        self.name = name          # the record name (unchanged from the historical runner)
        self.run = run            # () -> (ok: bool, output: str)
        self.quiet = quiet        # a per-file check: print only on failure
        self.ok = None
        self.output = ""
        self.seconds = 0.0


def syntax_jobs(files=None):
    jobs = []
    for category in ("positive", "negative"):
        for source in sorted((ROOT / "tests/syntax" / category).glob("*.spr")):
            if files is not None and source not in files:
                continue

            def run(source=source, category=category):
                proc = sprig("check", "--syntax-only", source)
                output = proc.stdout + proc.stderr
                ok = proc.returncode == 0 if category == "positive" else (
                    proc.returncode != 0 and ("SPR-LEX-" in output or "SPR-SYNTAX-" in output))
                return ok, output
            jobs.append(Job("syntax", f"syntax-{category} {source.name}", run, quiet=True))
    return jobs


def golden_jobs(files=None):
    jobs = []
    for category in ("runtime", "visitor"):
        for source in sorted((ROOT / "tests" / category).glob("*.spr")):
            golden = source.with_suffix(".out")
            if not golden.is_file():
                continue
            if files is not None and source not in files:
                continue

            def run(source=source, golden=golden):
                proc = sprig("run", source)
                # test.sh command substitution ignored trailing newlines in stdout/stderr.
                output = proc.stdout + proc.stderr
                ok = proc.returncode == 0 and output.rstrip("\n") == golden.read_text(encoding="utf-8").rstrip("\n")
                return ok, output
            jobs.append(Job("goldens", f"{category} {source.name}", run, quiet=True))
    if files is None:
        def exhaustiveness():
            proc = sprig("check", ROOT / "tests/visitor/nonexhaustive.spr")
            return "SPR-MATCH-NONEXHAUSTIVE" in proc.stdout + proc.stderr, proc.stdout + proc.stderr

        def explain():
            proc = sprig("explain", "SPR-MATCH-NONEXHAUSTIVE")
            return proc.returncode == 0, proc.stdout + proc.stderr

        def envelope():
            proc = sprig("check", "--json", ROOT / "tests/semantics/missing_case.spr")
            try:
                json.loads(proc.stdout)
                return True, ""
            except ValueError as error:
                return False, str(error) + "\n" + proc.stdout + proc.stderr
        jobs.append(Job("goldens", "visitor exhaustiveness", exhaustiveness, quiet=True))
        jobs.append(Job("goldens", "explain", explain, quiet=True))
        jobs.append(Job("goldens", "check JSON envelope", envelope, quiet=True))
    return jobs


def suite_job(gate, spec):
    command = [sys.executable, str(ROOT / gate)]
    for arg in spec.get("args", []):
        command.append(arg.replace("{root}", str(ROOT)))

    def run():
        proc = subprocess.run(command, cwd=ROOT, capture_output=True, text=True,
                              encoding="utf-8", errors="replace")
        return proc.returncode == 0, proc.stdout + proc.stderr
    return Job(gate, gate, run)


def jobs_for(gates, test_map, builtin_files=None):
    """Jobs for the named gates in map order, split into (parallel, serial)."""
    specs = test_map["gates"]
    ordered = [name for name in specs if name in set(gates)]
    missing = [name for name in gates if name not in specs]
    if missing:
        raise SystemExit("unknown gates: " + ", ".join(missing))
    parallel, serial = [], []
    for gate in ordered:
        spec = specs[gate]
        if spec.get("builtin"):
            files = None if builtin_files is None else builtin_files.get(gate)
            jobs = syntax_jobs(files) if gate == "syntax" else golden_jobs(files)
        else:
            jobs = [suite_job(gate, spec)]
        (serial if spec.get("serial") else parallel).extend(jobs)
    return parallel, serial


# ----------------------------------------------------------------------
# Running
# ----------------------------------------------------------------------

def run_jobs(parallel, serial, jobs, label="test"):
    """Runs the jobs, prints them as they finish, and the record in map order.

    Returns the exit status: 0 when every job passed.
    """
    lock = threading.Lock()
    started = time.monotonic()

    def execute(job):
        begin = time.monotonic()
        try:
            ok, output = job.run()
        except Exception as error:  # a crashed check is a failed check, not a crashed runner
            ok, output = False, f"{type(error).__name__}: {error}"
        job.ok, job.output, job.seconds = ok, output, time.monotonic() - begin
        with lock:
            report(job)

    def report(job):
        if job.quiet:
            if not job.ok:
                print(f"FAIL: {job.name} ({job.seconds:.1f}s)\n{job.output}", flush=True)
            return
        status = "ok" if job.ok else "FAIL"
        print(f"== {job.name} == {status} ({job.seconds:.1f}s)", flush=True)
        if job.output.strip():
            print(job.output.rstrip("\n"), flush=True)

    print(f"{label}: {len(parallel)} jobs in parallel ({jobs} at a time), then {len(serial)} serial", flush=True)
    if jobs <= 1:
        for job in parallel:
            execute(job)
    else:
        with ThreadPoolExecutor(max_workers=jobs) as pool:
            list(pool.map(execute, parallel))
    if serial:
        print(f"-- serial group ({len(serial)} jobs, one at a time) --", flush=True)
        for job in serial:
            execute(job)

    everything = parallel + serial
    passed = sum(1 for job in everything if job.ok)
    failures = [job.name for job in everything if not job.ok]
    print(f"summary: {passed} gates/cases passed, {len(failures)} failed")
    for name in failures:
        print(f"failed: {name}")
    slowest = sorted((job for job in everything if not job.quiet or not job.ok), key=lambda j: -j.seconds)[:10]
    if slowest:
        print("slowest:")
        for job in slowest:
            print(f"  {job.seconds:7.1f}s  {job.name}")
    quiet = [job for job in everything if job.quiet]
    if quiet:
        print(f"  {sum(job.seconds for job in quiet):7.1f}s  per-file checks in total ({len(quiet)} files)")
    print(f"wall time: {time.monotonic() - started:.1f}s with --jobs {jobs}")
    return 1 if failures else 0


def ensure_built():
    if not SPRIG.is_file() or sprig("version").returncode:
        print("Build first: python3 scripts/build.py", file=sys.stderr)
        return False
    return True
