#!/usr/bin/env python3
"""Run the first-party Fabric starter as an independent Loom project."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import signal
import shutil
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[2]
TEMPLATE = ROOT / "libraries" / "sprig-fabric" / "template"


def run(argv: list[str], *, cwd: Path, env: dict[str, str]) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(argv, cwd=cwd, env=env, text=True, capture_output=True)
    if result.returncode:
        print("command:", " ".join(argv), file=sys.stderr)
        print(result.stdout, end="", file=sys.stderr)
        print(result.stderr, end="", file=sys.stderr)
    return result


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, default=Path(os.environ.get("SPRIG_HOME", ROOT)),
                        help="Sprig SDK root containing bin/sprig and libraries/sprig-gradle")
    parser.add_argument("--run-client", action="store_true", help="also launch the Fabric client smoke test")
    args = parser.parse_args()
    sdk = args.sdk.resolve()
    launcher = sdk / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
    require(TEMPLATE.is_dir(), f"Fabric starter template is missing: {TEMPLATE}")
    require(launcher.is_file(), f"Sprig launcher is missing from selected SDK: {launcher}")
    build_script = (TEMPLATE / "build.gradle").read_text(encoding="utf-8")
    forbidden = ("compileSprigBridge", "sprigClientCompileClasspath", "sprigClasspath",
                 "sprigGenerate", "tasks.register(", "sourceSets.client.java.srcDir",
                 "prepareClientClasspathDirectories", "commandLine(", "dependsOn(")
    require(not any(token in build_script for token in forbidden),
            "the starter must not duplicate the Sprig/Loom task graph")
    require(len([line for line in build_script.splitlines() if line.strip()]) <= 45,
            "the starter build script should stay a small host configuration")

    env = os.environ.copy()
    env["SPRIG_HOME"] = str(sdk)
    with tempfile.TemporaryDirectory(prefix="sprig fabric test ") as temp:
        project = Path(temp) / "independent Fabric project with spaces"
        shutil.copytree(TEMPLATE, project)
        lock = project / "sprig.lock"
        require(lock.is_file(), "template must carry a generated lock for its declared dependencies")
        lock_bytes = lock.read_bytes()

        check = run([str(project / "gradlew"), "--no-daemon", "clean", "check"],
                    cwd=project, env=env)
        require(check.returncode == 0, "Fabric clean check must compile and execute Sprig tests")
        output = check.stdout + check.stderr
        require("sprigTest" in output and '"passed":1' in output,
                "Gradle check must execute the template's Sprig test")
        require("compileSprigBridge" in output, "plugin must own the Java bridge compilation")
        require("sprigGenerate" in output, "plugin must own Sprig source generation")
        require(lock.read_bytes() == lock_bytes, "check must not rewrite the explicit Sprig lock")

        info = run([str(project / "gradlew"), "--no-daemon", "sprigInfo"],
                   cwd=project, env=env)
        require(info.returncode == 0 and "Target source set: client" in info.stdout,
                "the template must target Loom's split client source set")

        build = run([str(project / "gradlew"), "--no-daemon", "clean", "build"],
                    cwd=project, env=env)
        require(build.returncode == 0, "Fabric clean build must remap and package the mod")
        jars = sorted((project / "build" / "libs").glob("*.jar"))
        require(jars, "Fabric build must produce a mod jar")
        jar = run(["jar", "tf", str(jars[-1])], cwd=project, env=env)
        require(jar.returncode == 0, "the produced Fabric jar must be readable")
        for entry in ("dev/sprig/fabric/SprigCounterClient.class",
                      "dev/sprig/fabric/CounterActions.class",
                      "sprig/user/$M_main.class", "sprig/runtime/SprigRuntime.class",
                      "fabric.mod.json"):
            require(entry in jar.stdout,
                    f"the packaged mod jar must contain {entry}; contents were:\n{jar.stdout}")
            require(jar.stdout.splitlines().count(entry) == 1,
                    f"the packaged mod jar must contain exactly one {entry}")
        generated = project / "build/generated/sprig/client/java/sprig/user/$M_main.java"
        require(generated.is_file(), "generated Sprig Java must stay inspectable under build/")
        require(not any((project / "src").rglob("$M_main.java")),
                "generated code must not be written into source trees")

        if args.run_client:
            capture = project.parent / "runClient-smoke.log"
            capture.parent.mkdir(parents=True, exist_ok=True)
            command = [str(project / "gradlew"), "--no-daemon", "clean", "runClient"]
            with capture.open("w", encoding="utf-8") as log:
                process = subprocess.Popen(command, cwd=project, env=env,
                                           stdout=log, stderr=subprocess.STDOUT,
                                           start_new_session=(os.name != "nt"))
                deadline = time.monotonic() + 120
                latest = project / "run" / "logs" / "latest.log"
                while time.monotonic() < deadline and process.poll() is None:
                    captured = capture.read_text(encoding="utf-8", errors="replace")
                    game_log = latest.read_text(encoding="utf-8", errors="replace") if latest.is_file() else ""
                    if "Sprig Fabric counter initialized:" in captured + game_log:
                        break
                    time.sleep(1)
                if process.poll() is None:
                    if os.name == "nt":
                        process.terminate()
                    else:
                        try:
                            os.killpg(process.pid, signal.SIGTERM)
                        except ProcessLookupError:
                            pass
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        if os.name == "nt":
                            process.kill()
                        else:
                            try:
                                os.killpg(process.pid, signal.SIGKILL)
                            except ProcessLookupError:
                                pass
                        process.wait(timeout=10)
            captured = capture.read_text(encoding="utf-8", errors="replace")
            game_log = latest.read_text(encoding="utf-8", errors="replace") if latest.is_file() else ""
            startup = captured + game_log
            require("Sprig Fabric counter initialized:" in startup,
                    "clean runClient did not log that Fabric loaded the Sprig-backed client; "
                    f"output was:\n{startup[-6000:]}")
            require("NoClassDefFoundError" not in startup and "Could not find or load main class" not in startup,
                    f"runClient reported a classpath failure:\n{startup[-6000:]}")

    print("Fabric/Loom starter integration: passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"FAIL Fabric/Loom starter integration: {error}", file=sys.stderr)
        raise SystemExit(1)
