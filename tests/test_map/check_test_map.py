#!/usr/bin/env python3
"""tests/test-map.json keeps the one inventory of gates honest.

Sample paths map to the expected gates; every gate test.py runs is reachable
from at least one mapping, so no suite is orphaned; every gate and file the map
names exists; the serial reasons are written down; the glob matcher behaves."""
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts" / "internal"))
sys.path.insert(0, str(ROOT / "scripts"))
import gate_runner  # noqa: E402
import importlib.util  # noqa: E402

spec = importlib.util.spec_from_file_location("test_affected", ROOT / "scripts" / "test-affected.py")
test_affected = importlib.util.module_from_spec(spec)
spec.loader.exec_module(test_affected)

FAILURES = []
COUNT = 0


def check(name, ok, detail=""):
    global COUNT
    COUNT += 1
    print(("pass " if ok else "FAIL ") + name)
    if not ok:
        FAILURES.append(f"{name}: {detail}")


def main():
    test_map = gate_runner.load_map()
    capability_map = json.loads((ROOT / "tests/agent_tooling/capability-test-map.json").read_text(encoding="utf-8"))
    gates = test_map["gates"]

    # Every gate and file the map names exists.
    for name, spec in gates.items():
        if spec.get("builtin"):
            continue
        check(f"gate-exists {name}", (ROOT / name).is_file())
    for entry in test_map["map"]:
        for gate in entry["gates"]:
            check(f"mapped-gate-known {gate}", gate == "self" or gate in gates)
    for path in test_map["smoke"]["goldens"]:
        check(f"smoke-golden-exists {Path(path).name}", (ROOT / path).is_file() and (ROOT / path).with_suffix(".out").is_file())
    check("smoke-build-exists", (ROOT / test_map["smoke"]["build"]).is_file())
    for path in test_map["full_on_change"]:
        check(f"full-trigger-exists {path}", (ROOT / path).is_file())
    for name, spec in gates.items():
        if spec.get("serial") is not None:
            check(f"serial-reason-written {name}", isinstance(spec["serial"], str) and len(spec["serial"]) > 20)

    # Every gate of the full run is reachable from at least one mapping (its own
    # path through the 'self' rules counts; builtin gates need a named rule).
    reachable = set()
    for entry in test_map["map"]:
        reachable.update(g for g in entry["gates"] if g != "self")
    self_rules = [gate_runner.glob_to_regex(p) for entry in test_map["map"] if "self" in entry["gates"] for p in entry["paths"]]
    for name in gate_runner.full_gates(test_map):
        ok = name in reachable or any(r.match(name) for r in self_rules)
        check(f"gate-reachable {name}", ok, "no mapping selects it")

    # The glob matcher.
    rx = gate_runner.glob_to_regex
    check("glob-double-star", rx("grammar/**").match("grammar/SprigParser.g4") is not None
          and rx("compiler/src/main/java/sprig/compiler/sem/**").match("compiler/src/main/java/sprig/compiler/sem/x/Y.java") is not None)
    check("glob-single-star-one-segment", rx("tests/runtime/*.spr").match("tests/runtime/01_arithmetic.spr") is not None
          and rx("tests/runtime/*.spr").match("tests/runtime/sub/x.spr") is None)
    check("glob-literal", rx("README.md").match("README.md") is not None and rx("README.md").match("docs/README.md") is None)

    # Sample paths map to the expected gates.
    def selection(paths):
        gates_found, _, full = test_affected.select(paths, test_map, capability_map)
        return (None if gates_found is None else set(gates_found)), full

    samples = [
        (["grammar/SprigParser.g4"], {"scripts/test-grammar.py", "syntax", "tests/recovery/check_recovery.py", "tests/formatter/check_formatter.py", "tests/lsp/check_lsp.py"}),
        (["compiler/src/main/java/sprig/compiler/front/LayoutTokenSource.java"], {"syntax", "tests/formatter/check_formatter.py", "tests/if_expression/check_if_expression.py"}),
        (["compiler/src/main/java/sprig/compiler/sem/TypeChecker.java"], {"scripts/check_cases.py", "goldens", "tests/conform/check_conform.py", "tests/numeric/check_numeric.py", "tests/agent_tooling/check_newcomer_hints.py"}),
        (["compiler/src/main/java/sprig/compiler/gen/JavaGenerator.java"], {"goldens", "tests/correctness/check_correctness.py", "tests/concurrent/check_concurrent.py", "tests/callables/check_callables.py"}),
        (["runtime/src/main/java/sprig/runtime/StringOps.java"], {"goldens", "tests/numeric/check_numeric.py", "tests/jvm_interop/check_interop.py"}),
        (["compiler/src/main/java/sprig/compiler/lsp/Server.java"], {"tests/lsp/check_lsp.py"}),
        (["compiler/src/main/resources/sprig/compiler/tooling/catalog.properties"], {"tests/agent_tooling/check_tooling.py", "scripts/internal/check-tooling-consistency.py", "tests/agent_tooling/check_newcomer_hints.py"}),
        (["compiler/src/main/java/sprig/compiler/diag/Codes.java"], {"tests/agent_tooling/check_diagnostics.py"}),
        (["std/lists.spr"], {"scripts/test-stdlib.py", "goldens"}),
        (["libraries/sprig-web/src/app.spr"], {"tests/web/check_web.py"}),
        (["libraries/sprig-sqlite/src/sqlite.spr"], {"tests/sqlite/check_sqlite.py", "tests/sqlite/check_migrations.py"}),
        (["docs/language/generics.md"], {"scripts/internal/verify-doc-snippets.py", "scripts/internal/check-doc-links.py"}),
        (["website/guide/tooling.md"], {"scripts/internal/verify-doc-snippets.py", "scripts/internal/check-doc-links.py"}),
        (["registry/packages/web.toml"], {"tests/registry/check_registry.py"}),
        (["tests/runtime/19_generics.spr"], {"goldens"}),
        (["tests/semantics/cases.json"], {"scripts/check_cases.py"}),
        (["tests/formatter/check_formatter.py"], {"tests/formatter/check_formatter.py"}),
        (["tests/web/http_support.py"], {"tests/web/check_web.py"}),
    ]
    for paths, expected in samples:
        found, full = selection(paths)
        check(f"sample {paths[0]}", found is not None and expected <= found,
              f"full={full} found={sorted(found) if found else found}")
    # A changed suite does not drag the whole run in, and a feature fixture selects its suites.
    found, _ = selection(["tests/formatter/check_formatter.py"])
    check("suite-selects-only-itself", found == {"tests/formatter/check_formatter.py"}, sorted(found or []))
    fixture = next(f for fixtures in capability_map.values() for f in fixtures if f.endswith(".spr"))
    found, _ = selection([fixture])
    check("capability-fixture-selects-feature-suites", found is not None and found, fixture)
    # Unknown paths and the runner itself run everything.
    for path in ["mystery/file.txt", "scripts/test.py", "tests/test-map.json", "scripts/build.py"]:
        found, full = selection([path])
        check(f"full-run {path}", found is None and bool(full), f"found={found}")
    # The map's order is the record order: gates appear once, serial ones are known.
    full = gate_runner.full_gates(test_map)
    check("no-duplicate-gates", len(full) == len(set(full)))
    check("historical-gates-present", {"scripts/check_cases.py", "tests/lsp/check_lsp.py", "tests/release_hardening/check_hardening.py",
                                       "tests/project_deps/check_refresh_locks.py", "syntax", "goldens"} <= set(full))
    # --list runs without touching the build and names the smoke set.
    listed = subprocess.run([sys.executable, str(ROOT / "scripts/test-affected.py"), "--list"], cwd=ROOT,
                            capture_output=True, text=True, encoding="utf-8")
    check("test-affected-list", listed.returncode == 0 and "smoke: scripts/build.py" in listed.stdout, listed.stdout[-400:] + listed.stderr[-400:])
    helped = subprocess.run([sys.executable, str(ROOT / "scripts/test.py"), "--help"], cwd=ROOT, capture_output=True, text=True)
    check("test-py-help-names-jobs", helped.returncode == 0 and "--jobs" in helped.stdout, helped.stdout)

    print(f"test map: {COUNT - len(FAILURES)} checks passed, {len(FAILURES)} failed")
    for failure in FAILURES:
        print("  " + failure)
    return 1 if FAILURES else 0


if __name__ == "__main__":
    sys.exit(main())
