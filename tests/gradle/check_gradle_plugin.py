#!/usr/bin/env python3
"""Functional acceptance for dev.sprig in an ordinary Java Gradle project."""
from __future__ import annotations

import os
from pathlib import Path
import shlex
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
        # Main.java makes the Counter with `new` and runs no generated main; 42 is a
        # top-level binding of main.spr, which the constructor initializes first.
        require("42" in output, "Java must call the generated Sprig implementation")
        require('"passed":1' in output, "Gradle check must execute the Sprig test")
        require(f"Compiler home: {sdk_home}" in output,
                "SPRIG_HOME must select the compiler from that SDK before PATH fallback")
        for field in ("Compiler version:", "Compiler home:", "Runtime source:",
                      "Target source set: main", "Generated Java:", "Bridge sources:",
                      "Generated package: sprig.user.ordinary_project",
                      "Test directory:", "Lock status:", "Compile classpath entries:"):
            require(field in output, f"sprigInfo must report {field}")
        # The project's classes and its copy of the runtime, in the project's package.
        package_directory = project / "build/generated/sprig/main/java/sprig/user/ordinary_project"
        generated = package_directory / "$Counter.java"
        require(generated.is_file(), f"generated Java is inspectable at {generated}")
        require((package_directory / "runtime/SprigRuntime.java").is_file(),
                "the project's runtime copy is generated next to its classes")
        require(not list((project / "src").rglob("$Counter.java")),
                "generation must not write Java into source directories")

        jar = project / "build/libs/ordinary-project.jar"
        require(jar.is_file(), "Gradle build must produce the ordinary Java jar")
        jar_contents = run(["jar", "tf", str(jar)], cwd=project, env=env)
        require(jar_contents.returncode == 0, "jar inspection must succeed")
        entries = jar_contents.stdout.splitlines()
        for entry in ("sprig/user/ordinary_project/$Counter.class",
                      "sprig/user/ordinary_project/runtime/SprigRuntime.class",
                      "dev/sprig/fixture/Meter.class"):
            require(entries.count(entry) == 1, f"application jar must contain exactly one {entry}")
        # Nothing of Sprig's sits outside the project's package: another Sprig-built
        # jar on the same classpath has no class name in common with this one.
        outside = [entry for entry in entries if entry.startswith("sprig/") and entry.endswith(".class")
                   and not entry.startswith("sprig/user/ordinary_project/")]
        require(not outside, f"every Sprig class must be in the project's package, found {outside[:5]}")

        # Two Sprig-built jars in one JVM (#223): a second project with the same
        # module and class names, built by the same plugin, next to the first.
        second = Path(temp) / "second project"
        shutil.copytree(FIXTURE, second)
        for relative, old, new in (("sprig.toml", 'name = "ordinary-project"', 'name = "second-project"'),
                                   ("src/main.spr", "let answer = 42", "let answer = 7"),
                                   ("src/main/java/demo/Main.java", "sprig.user.ordinary_project",
                                    "sprig.user.second_project")):
            path = second / relative
            text = path.read_text(encoding="utf-8")
            require(old in text, f"fixture {relative} must contain {old!r}")
            path.write_text(text.replace(old, new), encoding="utf-8")
        require(run([str(launcher), "resolve", "--offline"], cwd=second, env=env).returncode == 0,
                "the second project must resolve")
        require(run([*gradle, "--offline", "--no-daemon", "jar"], cwd=second, env=env).returncode == 0,
                "the second project must build its jar")
        second_jar = second / "build/libs/ordinary-project.jar"
        probe = Path(temp) / "probe"
        probe.mkdir()
        (probe / "Probe.java").write_text(
            "public final class Probe {\n"
            "    public static void main(String[] args) {\n"
            "        System.out.println(new sprig.user.ordinary_project.$Counter().current()\n"
            '                + " " + new sprig.user.second_project.$Counter().current());\n'
            "    }\n"
            "}\n", encoding="utf-8")
        compiled = run(["javac", "-cp", os.pathsep.join([str(jar), str(second_jar)]), "-d", str(probe),
                        str(probe / "Probe.java")], cwd=probe, env=env)
        require(compiled.returncode == 0, "Java must be able to name both projects' classes")
        for order in ([jar, second_jar], [second_jar, jar]):
            together = run(["java", "-cp", os.pathsep.join(map(str, [probe, *order])), "Probe"],
                           cwd=probe, env=env)
            require(together.returncode == 0 and together.stdout.strip() == "42 7",
                    "two Sprig-built jars on one classpath must each run their own classes, in either order; "
                    f"{order[0].parent.parent.parent.name} first printed {together.stdout!r} {together.stderr!r}")

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
        # directory holds both, and Windows must pick sprig.cmd. The fixture's
        # settings read SPRIG_HOME, so name the plugin build directly here.
        settings = project / "settings.gradle"
        fixture_settings = settings.read_text(encoding="utf-8")
        settings.write_text("pluginManagement {\n    includeBuild('"
                            + (sdk_home / "libraries/sprig-gradle").as_posix()
                            + "')\n}\n\nrootProject.name = \"ordinary-project\"\n", encoding="utf-8")
        path_env = {key: value for key, value in env.items() if key.upper() != "SPRIG_HOME"}
        path_env["PATH"] = str(sdk_home / "bin") + os.pathsep + env.get("PATH", "")
        via_path = run([*gradle, "--offline", "--no-daemon", "sprigInfo"], cwd=project, env=path_env)
        settings.write_text(fixture_settings, encoding="utf-8")
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

        if os.name != "nt":
            # A compiler without the option the plugin generates with, as v0.8.0-beta.1
            # is: the build stops while it is configured and says what is missing.
            older = Path(temp) / "older-sprig"
            older.write_text(
                "#!/bin/sh\n"
                f'if [ "$1" = capabilities ]; then {shlex.quote(str(launcher))} "$@"'
                " | sed 's/\"emitJavaWithRuntime\": *true/\"emitJavaWithRuntime\": false/'; exit; fi\n"
                f'exec {shlex.quote(str(launcher))} "$@"\n', encoding="utf-8")
            older.chmod(0o755)
            older_env = env.copy()
            older_env["SPRIG_EXECUTABLE"] = str(older)
            unsupported = run([*gradle, "--offline", "--no-daemon", "sprigInfo"],
                              cwd=project, env=older_env, report_failure=False)
            unsupported_output = unsupported.stdout + unsupported.stderr
            require(unsupported.returncode != 0
                    and "must provide build --emit-java-only --with-runtime" in unsupported_output,
                    "a compiler without --with-runtime must be refused with guidance\n" + unsupported_output)

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
