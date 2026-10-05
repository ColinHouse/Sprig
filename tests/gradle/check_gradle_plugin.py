#!/usr/bin/env python3
"""Functional acceptance for dev.sprig in an ordinary Java Gradle project."""
from __future__ import annotations

import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = Path(__file__).resolve().parent / "fixtures" / "java"
GRADLE_VERSION = "9.7.1"


def gradle_command() -> list[str]:
    configured = os.environ.get("SPRIG_GRADLE")
    if configured:
        return [configured]
    found = shutil.which("gradle")
    if found:
        return [found]
    script = "gradlew.bat" if os.name == "nt" else "gradlew"
    template_wrapper = ROOT / "libraries" / "sprig-fabric" / "template" / script
    if template_wrapper.is_file():
        return [str(template_wrapper)]
    wrapper_cache = Path.home() / ".gradle" / "wrapper" / "dists" / f"gradle-{GRADLE_VERSION}-bin"
    launcher = "gradle.bat" if os.name == "nt" else "gradle"
    for candidate in sorted(wrapper_cache.glob(f"*/gradle-{GRADLE_VERSION}/bin/{launcher}")):
        if candidate.is_file():
            return [str(candidate)]
    raise RuntimeError(
        f"Gradle {GRADLE_VERSION} is required; run the packaged wrapper or set SPRIG_GRADLE"
    )


def run(argv: list[str], *, cwd: Path, env: dict[str, str],
        report_failure: bool = True) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(argv, cwd=cwd, env=env, text=True, capture_output=True)
    if result.returncode != 0 and report_failure:
        print("command:", " ".join(argv), file=sys.stderr)
        print(result.stdout, end="", file=sys.stderr)
        print(result.stderr, end="", file=sys.stderr)
    return result


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    sdk_home = Path(os.environ.get("SPRIG_HOME", ROOT)).resolve()
    launcher = sdk_home / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
    if not launcher.is_file():
        raise RuntimeError(f"Sprig launcher not found: {launcher}")
    gradle = gradle_command()
    with tempfile.TemporaryDirectory(prefix="sprig gradle test ") as temp:
        project = Path(temp) / "ordinary project with spaces"
        shutil.copytree(FIXTURE, project)
        env = os.environ.copy()
        env["SPRIG_HOME"] = str(sdk_home)
        resolved = run([str(launcher), "resolve", "--offline"], cwd=project, env=env)
        require(resolved.returncode == 0, "the real CLI must create the no-dependency fixture lock")

        first = run([*gradle, "--offline", "--no-daemon", "clean", "check", "build", "run",
                     "sprigInfo"], cwd=project, env=env)
        require(first.returncode == 0, "ordinary Java project must pass Gradle check/build/run")
        output = first.stdout + first.stderr
        require("42" in output, "Java must call the generated Sprig implementation")
        require('"passed":1' in output, "Gradle check must execute the Sprig test")
        require(f"Compiler home: {sdk_home}" in output,
                "SPRIG_HOME must select the compiler from that SDK before PATH fallback")
        for field in ("Compiler version:", "Compiler home:", "Runtime source:",
                      "Target source set: main", "Generated Java:", "Bridge sources:",
                      "Test directory:", "Lock status:", "Compile classpath entries:"):
            require(field in output, f"sprigInfo must report {field}")
        generated = project / "build/generated/sprig/main/java/sprig/user/$Counter.java"
        require(generated.is_file(), f"generated Java is inspectable at {generated}")
        require(not (project / "src/main/java/sprig/user/$Counter.java").exists(),
                "generation must not write Java into source directories")

        jar = project / "build/libs/ordinary-project.jar"
        require(jar.is_file(), "Gradle build must produce the ordinary Java jar")
        jar_contents = run(["jar", "tf", str(jar)], cwd=project, env=env)
        require(jar_contents.returncode == 0, "jar inspection must succeed")
        for entry in ("sprig/user/$Counter.class", "sprig/runtime/SprigRuntime.class",
                      "dev/sprig/fixture/Meter.class"):
            require(entry in jar_contents.stdout, f"application jar must contain {entry}")
            require(jar_contents.stdout.splitlines().count(entry) == 1,
                    f"application jar must contain exactly one {entry}")

        bridge = project / "src/sprigBridge/java/dev/sprig/fixture/Meter.java"
        bridge_source = bridge.read_text(encoding="utf-8")
        bridge.write_text("package dev.sprig.fixture; public interface Meter { this is invalid; }\n",
                          encoding="utf-8")
        bad_bridge = run([*gradle, "--offline", "--no-daemon", "sprigCheck"],
                         cwd=project, env=env, report_failure=False)
        bad_bridge_output = bad_bridge.stdout + bad_bridge.stderr
        require(bad_bridge.returncode != 0 and "Meter.java" in bad_bridge_output,
                "bridge javac failures must identify the Java source\n" + bad_bridge_output)
        bridge.write_text(bridge_source, encoding="utf-8")

        java_source = project / "src/main/java/demo/Main.java"
        valid_java = java_source.read_text(encoding="utf-8")
        java_source.write_text("package demo; public final class Main { this is invalid; }\n",
                               encoding="utf-8")
        bad_java = run([*gradle, "--offline", "--no-daemon", "compileJava"],
                       cwd=project, env=env, report_failure=False)
        bad_java_output = bad_java.stdout + bad_java.stderr
        require(bad_java.returncode != 0 and "Main.java" in bad_java_output,
                "host javac failures must identify the Java source\n" + bad_java_output)
        java_source.write_text(valid_java, encoding="utf-8")

        second = run([*gradle, "--offline", "--no-daemon", "check"], cwd=project, env=env)
        require(second.returncode == 0, "unchanged Gradle check must remain successful")
        require("sprigGenerate UP-TO-DATE" in second.stdout,
                "unchanged Sprig generation must be Gradle up-to-date")

        # Without SPRIG_HOME the platform launcher is found on PATH; an SDK bin
        # directory holds both, and Windows must pick sprig.cmd.
        path_env = {key: value for key, value in env.items() if key.upper() != "SPRIG_HOME"}
        path_env["PATH"] = str(sdk_home / "bin") + os.pathsep + env.get("PATH", "")
        via_path = run([*gradle, "--offline", "--no-daemon", "sprigInfo"], cwd=project, env=path_env)
        require(via_path.returncode == 0 and f"Compiler home: {sdk_home}" in via_path.stdout,
                "sprigInfo must find the SDK launcher on PATH\n" + via_path.stdout + via_path.stderr)

        lock = project / "sprig.lock"
        lock.unlink()
        missing_lock = run([*gradle, "--offline", "--no-daemon", "check"],
                           cwd=project, env=env, report_failure=False)
        missing_output = missing_lock.stdout + missing_lock.stderr
        require(missing_lock.returncode != 0, "missing lock must fail the Gradle lifecycle")
        require("SPR-PROJECT-LOCK-MISSING" in missing_output and "sprig resolve" in missing_output,
                "Gradle must retain actionable missing-lock diagnostics\n" + missing_output)
        require(not lock.exists(), "check must never resolve or write the lock implicitly")
        resolved_by_task = run([*gradle, "--offline", "--no-daemon", "sprigResolve"],
                               cwd=project, env=env)
        require(resolved_by_task.returncode == 0 and lock.is_file(),
                "only explicit sprigResolve should write the current lock")

        main_source = project / "src/main.spr"
        valid_source = main_source.read_text(encoding="utf-8")
        main_source.write_text('let bad: Int = "wrong"\n', encoding="utf-8")
        invalid = run([*gradle, "--offline", "--no-daemon", "sprigCheck"],
                      cwd=project, env=env, report_failure=False)
        invalid_output = invalid.stdout + invalid.stderr
        require(invalid.returncode != 0 and "SPR-TYPE-ASSIGN" in invalid_output,
                "Sprig type errors must fail sprigCheck with their stable diagnostic code\n" + invalid_output)
        require("main.spr" in invalid_output and '"line": 0' in invalid_output,
                "Sprig diagnostics must preserve source path and line\n" + invalid_output)
        main_source.write_text(valid_source, encoding="utf-8")

        missing_cli_env = env.copy()
        missing_cli_env["SPRIG_EXECUTABLE"] = str(project / "missing sprig executable")
        missing_cli = run([*gradle, "--offline", "--no-daemon", "sprigInfo"],
                          cwd=project, env=missing_cli_env, report_failure=False)
        missing_cli_output = missing_cli.stdout + missing_cli.stderr
        require(missing_cli.returncode != 0 and "SPRIG_EXECUTABLE" in missing_cli_output,
                "missing explicit Sprig executable must produce setup guidance\n" + missing_cli_output)

        main_source.unlink()
        stale = run([*gradle, "--offline", "--no-daemon", "sprigGenerate"],
                    cwd=project, env=env, report_failure=False)
        stale_output = stale.stdout + stale.stderr
        require(stale.returncode != 0, "generation without a project entry must fail\n" + stale_output)
        require(not generated.exists(), "failed regeneration must remove stale generated Java")

    print("Gradle plugin ordinary Java integration: passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"FAIL Gradle plugin ordinary Java integration: {error}", file=sys.stderr)
        raise SystemExit(1)
