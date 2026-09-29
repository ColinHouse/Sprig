#!/usr/bin/env python3
"""Runtime failure UX: stable codes, wrapped messages, source ranges, stack opt-in."""
import json
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
passed = 0
failed = []


def call(*args):
    return subprocess.run([str(SPRIG), *map(str, args)], text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60)


def verify(name, ok, detail=""):
    global passed
    if ok:
        passed += 1
    else:
        failed.append(name)
        print(f"FAIL {name}: {detail[:800]}")


CASES = {
    "overflow": (
        "let big: Int = 9223372036854775807\nprint(big + 1)\n",
        "SPR-RUNTIME-EXCEPTION", "Numeric error: Int addition overflow", "checked-arithmetic", 2),
    "string-index": (
        'print("abc"[10])\n',
        "SPR-RUNTIME-EXCEPTION", "String index 10 is out of bounds; the string has 3 code points",
        "string-bounds", 1),
    "string-slice": (
        'print("abc".substring(1, 9))\n',
        "SPR-RUNTIME-EXCEPTION", "String slice [1, 9) is out of bounds; the string has 3 code points",
        "string-bounds", 1),
    "list-index": (
        "let xs: List[Int] = [1, 2]\nprint(xs[5])\n",
        "SPR-RUNTIME-EXCEPTION", "List index 5 is out of bounds; size is 2", "list-bounds", 2),
    "uncaught-error": (
        'func fail() -> Int throws Error:\n    throw Error("bad input")\nprint(fail())\n',
        "SPR-RUNTIME-ERROR", "Uncaught Error: bad input", "sprig-error", 2),
    "program-exit": (
        "import java.lang.System\nSystem.exit(3)\n",
        "SPR-PROGRAM-EXIT", "Program exited with status 3", None, None),
}

with tempfile.TemporaryDirectory(prefix="sprig-runtime-diag-") as work:
    directory = Path(work)
    for name, (source, code, message, origin, line) in CASES.items():
        path = directory / f"{name}.spr"
        path.write_text(source, encoding="utf-8")

        text = call("run", path)
        combined = text.stdout + text.stderr
        ok = text.returncode != 0 and code in combined and message in combined
        if code != "SPR-PROGRAM-EXIT":
            ok = ok and "\tat " not in combined
            ok = ok and "sprig-runtime-failure" not in combined
            ok = ok and "sprig-runtime-frame" not in combined
        verify(f"text {name}", ok, f"exit={text.returncode} {combined!r}")

        envelope = call("run", path, "--json")
        try:
            diagnostic = json.loads(envelope.stdout)["diagnostics"][0]
        except (ValueError, KeyError, IndexError) as error:
            verify(f"json {name}", False, f"{error}: {envelope.stdout!r} {envelope.stderr!r}")
            continue
        details = f"exit={envelope.returncode} {diagnostic}"
        ok = envelope.returncode == text.returncode != 0 and diagnostic["code"] == code \
            and diagnostic["message"] == message
        if origin is not None:
            ok = ok and diagnostic.get("range") is not None
            ok = ok and diagnostic.get("data", {}).get("origin") == origin
            ok = ok and diagnostic["range"]["start"]["line"] == line - 1
        if name == "program-exit":
            ok = ok and diagnostic.get("data", {}).get("programExitCode") == 3
        verify(f"json {name}", ok, details)

        if code != "SPR-PROGRAM-EXIT":
            assert "Run with --stacktrace" in diagnostic["hint"], diagnostic["hint"]

    # Issue #22 acceptance: local and imported Int/Int32 failures must report the
    # actual .spr file and the failing statement line, with stable codes. The
    # padding keeps failures away from line 1 so the mapping cannot pass by luck.
    def local_source(type_name):
        literal = "9223372036854775807" if type_name == "Int" else "2147483647"
        lines = [f"# local {type_name} overflow on a later line", "",
                 f"let big: {type_name} = {literal}",
                 f"let one: {type_name} = 1",
                 "print(big + one)"]
        return "\n".join(lines) + "\n", len(lines) - 1

    def module_source(type_name):
        literal = "9223372036854775807" if type_name == "Int" else "2147483647"
        lines = [f"# imported {type_name} failure far from the module first line",
                 "", "", "", "", "",
                 f"func overflow() -> {type_name}:",
                 f"    let big: {type_name} = {literal}",
                 f"    let one: {type_name} = 1",
                 "    return big + one"]
        return "\n".join(lines) + "\n", len(lines) - 1

    for type_name in ("Int", "Int32"):
        message = f"Numeric error: {type_name} addition overflow"

        local = directory / f"local-{type_name.lower()}.spr"
        local_source_text, local_line = local_source(type_name)
        local.write_text(local_source_text, encoding="utf-8")
        result = call("run", local, "--json")
        try:
            diagnostic = json.loads(result.stdout)["diagnostics"][0]
        except (ValueError, KeyError, IndexError):
            diagnostic = None
        verify(f"numeric span local {type_name}",
               result.returncode == 1 and diagnostic is not None
               and local_line >= 4
               and diagnostic["code"] == "SPR-RUNTIME-EXCEPTION"
               and diagnostic["message"] == message
               and diagnostic.get("data", {}).get("origin") == "checked-arithmetic"
               and diagnostic.get("uri", "").endswith("/" + local.name)
               and diagnostic.get("range") is not None
               and diagnostic["range"]["start"]["line"] == local_line,
               f"exit={result.returncode} line={local_line} {diagnostic}")

        module = directory / f"numeric-{type_name.lower()}.spr"
        module_source_text, module_line = module_source(type_name)
        module.write_text(module_source_text, encoding="utf-8")
        caller = directory / f"imported-{type_name.lower()}.spr"
        caller.write_text(f'import "./{module.name}" as numeric\nprint("before")\n'
                          "print(numeric.overflow())\n", encoding="utf-8")
        result = call("run", caller, "--json")
        try:
            diagnostic = json.loads(result.stdout)["diagnostics"][0]
        except (ValueError, KeyError, IndexError):
            diagnostic = None
        verify(f"numeric span imported {type_name}",
               result.returncode == 1 and diagnostic is not None
               and module_line >= 4
               and diagnostic["code"] == "SPR-RUNTIME-EXCEPTION"
               and diagnostic["message"] == message
               and diagnostic.get("data", {}).get("origin") == "checked-arithmetic"
               and diagnostic.get("uri", "").endswith("/" + module.name)
               and not diagnostic.get("uri", "").endswith("/" + caller.name)
               and diagnostic.get("range") is not None
               and diagnostic["range"]["start"]["line"] == module_line,
               f"exit={result.returncode} line={module_line} {diagnostic}")

    # --stacktrace restores the raw JVM frames for debugging without losing the wrapper.
    overflow = directory / "overflow.spr"
    stack = call("run", overflow, "--stacktrace")
    combined = stack.stdout + stack.stderr
    verify("stacktrace frames", stack.returncode == 1 and "at sprig.user." in combined
           and "SPR-RUNTIME-EXCEPTION" in combined and "Numeric error:" in combined,
           f"exit={stack.returncode} {combined!r}")
    verify("stacktrace hides marker", "sprig-runtime-failure" not in combined
           and "sprig-runtime-frame" not in combined, combined)

    # JSON debugging keeps the same diagnostic and carries the captured stack as data.
    stack_json = call("run", overflow, "--json", "--stacktrace")
    try:
        stack_diagnostic = json.loads(stack_json.stdout)["diagnostics"][0]
    except (ValueError, KeyError, IndexError) as error:
        stack_diagnostic = {}
        verify("stacktrace json", False, f"{error}: {stack_json.stdout!r} {stack_json.stderr!r}")
    else:
        stack = stack_diagnostic.get("data", {}).get("jvmStack", "")
        verify("stacktrace json", stack_json.returncode == 1
               and stack_diagnostic.get("code") == "SPR-RUNTIME-EXCEPTION"
               and "at sprig.user." in stack and stack_diagnostic.get("range") is not None,
               f"exit={stack_json.returncode} {stack_diagnostic}")

    # The flag is run-only, like --keep.
    foreign = call("check", directory / "string-index.spr", "--stacktrace", "--json")
    try:
        foreign_code = json.loads(foreign.stdout)["diagnostics"][0]["code"]
    except (ValueError, KeyError, IndexError):
        foreign_code = None
    verify("stacktrace run-only", foreign.returncode == 2 and foreign_code == "SPR-CLI-OPTION",
           f"exit={foreign.returncode} {foreign.stdout}{foreign.stderr}")

    # A passing program keeps plain output and never mentions the transport marker.
    clean = directory / "clean.spr"
    clean.write_text('print("ok")\n', encoding="utf-8")
    run = call("run", clean)
    verify("success output", run.returncode == 0 and run.stdout == "ok\n"
           and "sprig-runtime-failure" not in run.stdout + run.stderr,
           f"exit={run.returncode} {run.stdout!r} {run.stderr!r}")

print(f"runtime diagnostics: {passed} checks passed, {len(failed)} failed")
for name in failed:
    print(f"failed: {name}")
raise SystemExit(1 if failed else 0)
