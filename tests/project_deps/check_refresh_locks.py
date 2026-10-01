#!/usr/bin/env python3
"""Generated-lock policy: refresh-locks discovers tracked locks, is idempotent.

Tracked sprig.lock files are canonical resolver output. This suite proves the
repair tool touches exactly those files, works from any caller directory, is
byte-for-byte stable on a second run, and never scans untracked lockfiles.
"""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "refresh-locks.py"
passed = 0
failed = []


def verify(name, ok, detail=""):
    global passed
    if ok:
        passed += 1
    else:
        failed.append(name)
        print(f"FAIL {name}: {detail[:900]}")


def tracked_locks():
    result = subprocess.run(["git", "ls-files", "--", "*sprig.lock"], cwd=ROOT,
                            text=True, capture_output=True)
    return sorted(line for line in result.stdout.splitlines() if line.strip())


def refresh(cwd):
    return subprocess.run([sys.executable, str(SCRIPT)], cwd=cwd, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=600)


def main():
    locks = tracked_locks()
    verify("tracked-locks-discovered", bool(locks), "git ls-files found no locks")
    for lock in locks:
        verify(f"tracked-lock-has-manifest {lock}",
               (ROOT / lock).parent.joinpath("sprig.toml").is_file())

    before = {lock: (ROOT / lock).read_bytes() for lock in locks}
    with tempfile.TemporaryDirectory(prefix="sprig-refresh-") as temp:
        # A caller cwd outside the repository must not matter.
        first = refresh(temp)
        lines = [line for line in first.stdout.splitlines() if line.startswith("refreshed ")]
        verify("refresh-succeeds-off-cwd", first.returncode == 0,
               f"exit={first.returncode} {first.stdout}{first.stderr}")
        verify("refresh-reports-exactly-tracked",
               [line[len("refreshed "):] for line in lines] == locks,
               f"reported={[line[len('refreshed '):] for line in lines]}")
        verify("refresh-summary", first.stdout.rstrip().endswith(
            f"{len(locks)} tracked lockfiles refreshed"), first.stdout)

        after = {lock: (ROOT / lock).read_bytes() for lock in locks}
        verify("refresh-canonical-output", after == before,
               "tracked locks differ from canonical resolve output; commit the refresh")
        verify("all-locks-use-schema-5", all(b"lock-version = 5\n" in content
                                              for content in after.values()))
        verify("locks-do-not-carry-sdk-std-identity",
               all(b"stdlib-version" not in content and b"stdlib-sha256" not in content
                   for content in after.values()))

        second = refresh(temp)
        again = {lock: (ROOT / lock).read_bytes() for lock in locks}
        verify("refresh-idempotent", second.returncode == 0 and again == after,
               f"exit={second.returncode} {second.stdout}{second.stderr}")

        # Untracked lockfiles are never scanned or touched.
        probe_dir = ROOT / "tests" / "project_deps" / ".refresh-lock-probe"
        probe_lock = probe_dir / "sprig.lock"
        try:
            probe_dir.mkdir(parents=True, exist_ok=True)
            (probe_dir / "sprig.toml").write_text(
                '[project]\nname = "probe"\nversion = "0.1.0"\nlanguage = "0.8"\n',
                encoding="utf-8")
            probe_lock.write_text("PROBE\n", encoding="utf-8")
            third = refresh(temp)
            verify("untracked-lock-ignored",
                   third.returncode == 0
                   and probe_lock.read_text(encoding="utf-8") == "PROBE\n"
                   and ".refresh-lock-probe" not in third.stdout + third.stderr,
                   f"exit={third.returncode} {third.stdout}{third.stderr}")
        finally:
            shutil.rmtree(probe_dir, ignore_errors=True)

        # A checkout without a built launcher fails clearly, before any resolve.
        fake = Path(temp) / "fake-root"
        (fake / "scripts").mkdir(parents=True)
        shutil.copy2(SCRIPT, fake / "scripts" / "refresh-locks.py")
        missing = subprocess.run([sys.executable, str(fake / "scripts" / "refresh-locks.py")],
                                 cwd=fake, text=True,
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        verify("missing-launcher-fails-clearly",
               missing.returncode != 0 and "scripts/build.py" in missing.stderr,
               f"exit={missing.returncode} {missing.stdout}{missing.stderr}")

    print(f"refresh locks: {passed} checks passed, {len(failed)} failed")
    for name in failed:
        print(f"failed: {name}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
