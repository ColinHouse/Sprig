#!/usr/bin/env python3
"""Full test.sh contract, with native Windows launcher and Python process calls."""
from pathlib import Path
import json
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")


def main():
    passed, failures = 0, []

    def record(name, ok, details=""):
        nonlocal passed
        if ok:
            passed += 1
        else:
            failures.append(name)
            print(f"FAIL: {name}\n{details}", flush=True)

    def sprig(*args):
        return subprocess.run([str(SPRIG), *map(str, args)], cwd=ROOT,
                              capture_output=True, text=True, encoding="utf-8")

    if not SPRIG.is_file() or sprig("version").returncode:
        print("Build first: python3 scripts/build.py", file=sys.stderr)
        return 2
    for category in ("positive", "negative"):
        for source in sorted((ROOT / "tests/syntax" / category).glob("*.spr")):
            proc = sprig("check", "--syntax-only", source)
            output = proc.stdout + proc.stderr
            ok = proc.returncode == 0 if category == "positive" else (
                proc.returncode != 0 and ("SPR-LEX-" in output or "SPR-SYNTAX-" in output))
            record(f"syntax-{category} {source.name}", ok, output)
    for category in ("runtime", "visitor"):
        for source in sorted((ROOT / "tests" / category).glob("*.spr")):
            golden = source.with_suffix(".out")
            if not golden.is_file():
                continue
            proc = sprig("run", source)
            # test.sh command substitution ignores trailing newlines in stdout/stderr.
            output = proc.stdout + proc.stderr
            record(f"{category} {source.name}", proc.returncode == 0 and
                   output.rstrip("\n") == golden.read_text(encoding="utf-8").rstrip("\n"), output)
    proc = sprig("check", ROOT / "tests/visitor/nonexhaustive.spr")
    record("visitor exhaustiveness", "SPR-MATCH-NONEXHAUSTIVE" in proc.stdout + proc.stderr)
    record("explain", sprig("explain", "SPR-MATCH-NONEXHAUSTIVE").returncode == 0)
    try:
        json.loads(sprig("check", "--json", ROOT / "tests/semantics/missing_case.spr").stdout)
        record("check JSON envelope", True)
    except ValueError as error:
        record("check JSON envelope", False, str(error))
    suites = ["tests/formatter/check_formatter.py", "tests/reexports/check_reexports.py", "tests/match_expression/check_match_expression.py", "tests/if_expression/check_if_expression.py", "scripts/check_cases.py", "tests/numeric/check_numeric.py",
              "tests/runtime/check_strings.py", "tests/runtime/check_runtime_diagnostics.py",
              "tests/jvm_interop/check_interop.py",
              "tests/gradle/check_gradle_plugin.py",
              "tests/wrap/check_wrap.py",
              "tests/correctness/check_correctness.py", "tests/recovery/check_recovery.py",
              "tests/acceptance/scripts/run_acceptance.py", "tests/acceptance/scripts/json_matrix.py",
              "tests/acceptance/scripts/consistency_matrix.py", "tests/agent_tooling/check_tooling.py",
              "tests/agent_tooling/check_diagnostics.py", "tests/agent_tooling/check_newcomer_hints.py", "tests/lsp/check_lsp.py", "tests/docs/check_doc_roles.py",
              "tests/agent_tooling/check_sprig_api.py", "tests/agent_tooling/check_agent_tools.py",
              "tests/agent_eval/check_task_pack.py", "tests/examples/check_task_tracker.py",
              "tests/cli_contract/check_cli_contract.py", "tests/launcher/check_windows_arguments.py", "tests/test_runner/check_test_runner.py", "tests/http/check_http.py", "tests/bootstrap/check_probe.py",
              "tests/project/check_project.py", "tests/project_deps/check_deps.py", "tests/project_deps/check_git_monorepo.py", "tests/project_deps/check_git_lock.py", "tests/project_deps/check_add_remove.py",
              "tests/project_deps/check_cleanup.py", "tests/registry/check_registry.py", "tests/adversarial/v08/check_generics.py",
              "tests/capabilities/check_comparable.py",
              "tests/adversarial/v08/check_projects.py"]
    suites += ["tests/adversarial/regressions/check_semantics.py",
               "tests/adversarial/regressions/check_type_names.py",
               "tests/adversarial/regressions/check_java_identifiers.py",
               "tests/adversarial/regressions/check_jvm_limits.py",
               "tests/adversarial/regressions/check_layout_lines.py",
               "tests/adversarial/regressions/check_jvm_bridges.py",
               "tests/adversarial/regressions/check_properties.py",
               "tests/adversarial/regressions/check_install_failures.py",
               "tests/adversarial/regressions/check_sdk_composition.py"]
    suites += ["tests/application_foundation/check_composition.py", "tests/conform/check_conform.py",
               "tests/callables/check_callables.py", "tests/launcher/check_launcher_jit.py",
               "tests/installer/check_installer.py", "tests/upgrade/check_upgrade.py",
               "tests/cli_library/check_cli_library.py", "tests/json_codec/check_json_codec.py", "tests/concurrent/check_concurrent.py", "tests/web/check_web.py", "tests/sqlite/check_sqlite.py", "tests/sqlite/check_migrations.py", "tests/dogfood/check_installed_sdk.py", "tests/maven/check_resolver.py", "scripts/test-stdlib.py",
               "scripts/test-showcases.py", "tests/project_deps/check_refresh_locks.py",
               "tests/release_hardening/check_hardening.py", "tests/bundle/check_bundle.py"]
    for suite in suites:
        print(f"== {suite} ==", flush=True)
        command = [sys.executable, str(ROOT / suite)]
        if suite == "scripts/check_cases.py":
            command.append(str(ROOT))
        record(suite, subprocess.run(command, cwd=ROOT).returncode == 0)
    print(f"summary: {passed} gates/cases passed, {len(failures)} failed")
    for name in failures:
        print(f"failed: {name}")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
