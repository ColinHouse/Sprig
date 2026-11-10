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
import zipfile

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
    parser.add_argument("--with-example", action="append", default=[], metavar="NAME",
                        help="with --run-client: also install the mod of examples/NAME in that client, "
                             "to see two Sprig mods load in one game (for example fabric_waypoints)")
    args = parser.parse_args()
    require(args.run_client or not args.with_example, "--with-example needs --run-client")
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

        check = run([str(project / ("gradlew.bat" if os.name == "nt" else "gradlew")), "--no-daemon", "clean", "check"],
                    cwd=project, env=env)
        require(check.returncode == 0, "Fabric clean check must compile and execute Sprig tests")
        output = check.stdout + check.stderr
        require("sprigTest" in output and '"passed":1' in output,
                "Gradle check must execute the template's Sprig test")
        require("compileSprigBridge" in output, "plugin must own the Java bridge compilation")
        require("sprigGenerate" in output, "plugin must own Sprig source generation")
        require(lock.read_bytes() == lock_bytes, "check must not rewrite the explicit Sprig lock")

        info = run([str(project / ("gradlew.bat" if os.name == "nt" else "gradlew")), "--no-daemon", "sprigInfo"],
                   cwd=project, env=env)
        require(info.returncode == 0 and "Target source set: main" in info.stdout,
                "the template must target Loom's common main source set, visible to both entry points")

        build = run([str(project / ("gradlew.bat" if os.name == "nt" else "gradlew")), "--no-daemon", "clean", "build"],
                    cwd=project, env=env)
        require(build.returncode == 0, "Fabric clean build must remap and package the mod")
        jars = sorted((project / "build" / "libs").glob("*.jar"))
        require(jars, "Fabric build must produce a mod jar")
        jar = run(["jar", "tf", str(jars[-1])], cwd=project, env=env)
        require(jar.returncode == 0, "the produced Fabric jar must be readable")
        entries = jar.stdout.splitlines()
        for entry in ("dev/sprig/fabric/SprigCounterClient.class",
                      "dev/sprig/fabric/CounterActions.class",
                      "sprig/user/sprig_fabric_counter/$M_main.class",
                      "sprig/user/sprig_fabric_counter/runtime/SprigRuntime.class",
                      "fabric.mod.json"):
            require(entries.count(entry) == 1,
                    f"the packaged mod jar must contain exactly one {entry}; contents were:\n{jar.stdout}")
        # Fabric loads every mod in one class loader: what Sprig adds to this jar
        # must all be in the project's own package, so a second Sprig mod has no
        # class name in common with it.
        outside = [entry for entry in entries if entry.startswith("sprig/") and entry.endswith(".class")
                   and not entry.startswith("sprig/user/sprig_fabric_counter/")]
        require(not outside, f"every Sprig class of the mod must be in its project's package, found {outside[:5]}")
        package_directory = project / "build/generated/sprig/main/java/sprig/user/sprig_fabric_counter"
        generated = package_directory / "$M_main.java"
        require(generated.is_file(), "generated Sprig Java must stay inspectable under build/")
        wand = package_directory / "$Wand.java"
        require(wand.is_file() and "extends net.minecraft.world.item.Item" in wand.read_text(encoding="utf-8")
                and "$Wand.super.use(" in wand.read_text(encoding="utf-8"),
                "the Sprig wand must compile to an Item subclass that calls the inherited use")
        require(not any((project / "src").rglob("$M_main.java")),
                "generated code must not be written into source trees")

        if args.run_client:
            # Other Sprig mods for the same game: Fabric loads them and the starter
            # in one class loader, which is where shared class names would collide.
            installed = []
            for name in args.with_example:
                example = Path(temp) / f"example {name}"
                shutil.copytree(ROOT / "examples" / name, example,
                                ignore=shutil.ignore_patterns(".gradle", "build", "run"))
                built = run([str(example / ("gradlew.bat" if os.name == "nt" else "gradlew")), "--no-daemon",
                             "clean", "build"], cwd=example, env=env)
                require(built.returncode == 0, f"example {name} must build its mod jar")
                mods = [path for path in (example / "build" / "libs").glob("*.jar")
                        if not path.name.endswith("-sources.jar")]
                require(len(mods) == 1, f"example {name}: expected one mod jar, found {[m.name for m in mods]}")
                with zipfile.ZipFile(mods[0]) as mod:
                    installed.append(json.loads(mod.read("fabric.mod.json").decode("utf-8"))["id"])
                (project / "run" / "mods").mkdir(parents=True, exist_ok=True)
                shutil.copy(mods[0], project / "run" / "mods" / mods[0].name)
            capture = project.parent / "runClient-smoke.log"
            capture.parent.mkdir(parents=True, exist_ok=True)
            command = [str(project / ("gradlew.bat" if os.name == "nt" else "gradlew")), "--no-daemon", "clean", "runClient"]
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
            # Main entry points run before client ones, so the starter's client line
            # above also says that every installed mod's main entry point returned.
            for mod_id in ["sprig_counter", *installed]:
                require(f"- {mod_id} " in startup,
                        f"Fabric did not list the mod {mod_id} as loaded:\n{startup[-6000:]}")
            if installed:
                print(f"client started with {', '.join(['sprig_counter', *installed])} loaded together")

    print("Fabric/Loom starter integration: passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"FAIL Fabric/Loom starter integration: {error}", file=sys.stderr)
        raise SystemExit(1)
