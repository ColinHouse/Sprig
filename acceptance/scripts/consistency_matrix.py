#!/usr/bin/env python3
"""Independent check/build/run consistency matrix + stale-output test.

For each input the script records what each CLI phase does and verifies that
front-end guarantees are not bypassed by build/run. Written by the acceptance
reviewer.
"""
import json
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / "sprig"
CASES = ROOT / "acceptance" / "cases"
OUT = ROOT / "acceptance" / "results"
WORK = ROOT / "build" / "acceptance_matrix"

MATRIX = [
    # name, file, check_exit, build_exit, run_exit, run_code
    ("legal-hello", ROOT / "examples/hello.spr", 0, 0, 0, None),
    ("type-error-null", CASES / "p1_java_null_builder.spr", 1, 1, 1, None),
    ("default-effect", CASES / "p1_default_two_effects.spr", 1, 1, 1, None),
    ("unit-field", CASES / "p1_unit_field.spr", 1, 1, 1, None),
    ("zero-arg-lambda", CASES / "p2_lambda_zero_arg.spr", 0, 0, 0, None),
    ("runtime-failure-default", CASES / "p1_default_runtime_top.spr", 0, 0, 1, "SPR-RUNTIME-ERROR"),
    ("java-interop", ROOT / "tests/runtime/15_jvm_interop.spr", 0, 0, 0, None),
    ("numeric-overflow-runtime", CASES / "num_overflow_runtime.spr", 0, 0, 1, "SPR-RUNTIME-EXCEPTION"),
    ("numeric-static-int-division", CASES / "num_static_int_division.spr", 1, 1, 1, None),
]


def codes_of(text):
    try:
        return [d.get("code") for d in json.loads(text).get("diagnostics", [])]
    except json.JSONDecodeError:
        return None


def classes_present(directory):
    return any(directory.rglob("*.class")) if directory.exists() else False


def main():
    shutil.rmtree(WORK, ignore_errors=True)
    WORK.mkdir(parents=True, exist_ok=True)
    records = []
    for name, source, check_exit, build_exit, run_exit, run_code in MATRIX:
        build_dir = WORK / name
        check = subprocess.run([SPRIG, "check", "--json", source],
                               capture_output=True, text=True)
        build = subprocess.run([SPRIG, "build", "--json", source, "-d", build_dir],
                               capture_output=True, text=True)
        run = subprocess.run([SPRIG, "run", "--json", source],
                             capture_output=True, text=True)
        record = {
            "name": name,
            "check_exit": check.returncode,
            "build_exit": build.returncode,
            "run_exit": run.returncode,
            "check_codes": codes_of(check.stdout),
            "build_codes": codes_of(build.stdout),
            "run_codes": codes_of(run.stdout),
            "classes_after_build": classes_present(build_dir / "classes"),
            "problems": [],
        }
        if check.returncode != check_exit:
            record["problems"].append(f"check exit {check.returncode} != {check_exit}")
        if build.returncode != build_exit:
            record["problems"].append(f"build exit {build.returncode} != {build_exit}")
        if run.returncode != run_exit:
            record["problems"].append(f"run exit {run.returncode} != {run_exit}")
        if run_code is not None and run_code not in (record["run_codes"] or []):
            record["problems"].append(f"run codes {record['run_codes']} missing {run_code}")
        # A rejected program must not leave executable classes behind.
        if check.returncode != 0 and record["classes_after_build"]:
            record["problems"].append("classes emitted for front-end-rejected program")
        # Build must never succeed when check fails.
        if check.returncode != 0 and build.returncode == 0:
            record["problems"].append("build succeeded although check failed")
        records.append(record)

    # Stale-output test: pre-populate a destination with an unrelated generated
    # file, then build a different program into it.
    stale_dir = WORK / "stale"
    stale_java = stale_dir / "java" / "sprig" / "user"
    stale_classes = stale_dir / "classes" / "sprig" / "user"
    stale_java.mkdir(parents=True, exist_ok=True)
    stale_classes.mkdir(parents=True, exist_ok=True)
    (stale_java / "$Stale.java").write_text("public class $Stale {}\n", encoding="utf-8")
    (stale_classes / "$Stale.class").write_bytes(b"stale")
    build = subprocess.run([SPRIG, "build", "--json", ROOT / "examples/hello.spr",
                            "-d", stale_dir], capture_output=True, text=True)
    stale_files = list(stale_dir.rglob("*Stale*"))
    stale_record = {
        "name": "stale-output-cleanup",
        "build_exit": build.returncode,
        "stale_files_remaining": [str(p.relative_to(stale_dir)) for p in stale_files],
        "problems": [],
    }
    if build.returncode != 0:
        stale_record["problems"].append("build failed on fresh destination")
    if stale_files:
        stale_record["problems"].append("stale generated files survived a rebuild")
    records.append(stale_record)

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "consistency_matrix.json").write_text(json.dumps(records, indent=2), encoding="utf-8")
    ok = all(not r["problems"] for r in records)
    for r in records:
        marker = "pass" if not r["problems"] else "FAIL"
        print(f"{marker} {r['name']}: "
              + (f"check={r['check_exit']} build={r['build_exit']} run={r['run_exit']} "
                 f"classes={r['classes_after_build']}" if "check_exit" in r
                 else f"build={r['build_exit']} stale={r['stale_files_remaining']}"))
        for problem in r["problems"]:
            print(f"      {problem}")
    print(f"consistency matrix: {sum(1 for r in records if not r['problems'])}/{len(records)} passed")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
