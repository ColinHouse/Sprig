#!/usr/bin/env python3
"""`sprig build --bundle`: self-contained bundles that run without a JDK.

Five programs are bundled (a hello program, a CLI with spaced and UTF-8
arguments, a project with a Maven dependency, a SQLite program whose JDBC
driver carries a native library, and a sprig-web server). Every launcher runs
with JAVA_HOME unset and no `java` on PATH; exit codes, stdout, stderr and the
working directory are checked. The archive form is unpacked and run too.
Windows runs the `.cmd` launcher in the experimental lane only."""
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
WINDOWS = os.name == "nt"
FAILURES = []
COUNT = 0


def check(name, ok, detail=""):
    global COUNT
    COUNT += 1
    print(("pass " if ok else "FAIL ") + name)
    if not ok:
        print("     " + str(detail)[:1500])
        FAILURES.append(f"{name}: {detail}")


def sprig(*args, cwd):
    env = dict(os.environ)
    env.pop("JAVA_TOOL_OPTIONS", None)
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=cwd, env=env, text=True,
                          encoding="utf-8", capture_output=True, timeout=900)


def bare_environment(work, home):
    """No JAVA_HOME, and a PATH that holds the shell's helpers but no java."""
    tools = work / "no-java-tools"
    if not tools.exists():
        tools.mkdir()
        if not WINDOWS:
            for name in ("sh", "dirname", "mkdir"):
                found = shutil.which(name)
                assert found, name
                os.symlink(found, tools / name)
    env = {"PATH": str(tools), "HOME": str(home), "LC_ALL": "C.UTF-8", "LANG": "C.UTF-8"}
    if WINDOWS:
        # cmd.exe runs the .cmd launcher: CreateProcess finds it through ComSpec, and
        # the system directory (which holds no java.exe) supplies the shell's helpers.
        system_root = os.environ.get("SystemRoot", r"C:\Windows")
        env["PATH"] = system_root + r"\System32"
        env["SystemRoot"] = env["windir"] = system_root
        env["ComSpec"] = os.environ.get("ComSpec", system_root + r"\System32\cmd.exe")
        env["PATHEXT"] = os.environ.get("PATHEXT", ".COM;.EXE;.BAT;.CMD")
        env["LOCALAPPDATA"] = str(home)
        env["TEMP"] = env["TMP"] = str(work)
    return env


def jdk_tool(name):
    """jlink or jdeps: on PATH, or in the bin/ of the JDK that runs `java`."""
    found = shutil.which(name)
    if found:
        return found
    java = shutil.which("java")
    if java:
        home = subprocess.run([java, "-XshowSettings:properties", "-version"], capture_output=True, text=True)
        for line in (home.stderr + home.stdout).splitlines():
            if line.strip().startswith("java.home ="):
                candidate = Path(line.split("=", 1)[1].strip()) / "bin" / (name + (".exe" if WINDOWS else ""))
                if candidate.is_file():
                    return str(candidate)
    return None


def launcher_of(bundle_dir, name):
    return bundle_dir / "bin" / (name + ".cmd" if WINDOWS else name)


def run_launcher(launcher, args, env, cwd, timeout=120):
    assert not shutil.which("java", path=env["PATH"]), "java must not be reachable from the test PATH"
    # The launcher path and the arguments go straight to CreateProcess, as a user or a
    # calling program passes them; on Windows the system runs the .cmd through ComSpec.
    # Wrapping them in an explicit `cmd /c` with each argument quoted would trigger
    # cmd.exe's rule that strips the first and the last quote of the command line.
    return subprocess.run([str(launcher), *args], cwd=cwd, env=env, capture_output=True, timeout=timeout)


def decoded(result):
    """The streams as text; Windows line separators are normalized to LF."""
    return (text_of(result.stdout), text_of(result.stderr))


def text_of(data):
    text = data.decode("utf-8", "replace")
    return text.replace("\r\n", "\n") if WINDOWS else text


def stop(process):
    """Ends a launched program: on Windows the .cmd launcher's child java.exe too."""
    if WINDOWS:
        subprocess.run(["taskkill", "/F", "/T", "/PID", str(process.pid)], capture_output=True)
    process.kill()
    process.wait(timeout=20)


def bundle(cwd, *args):
    result = sprig("build", *args, "--bundle", "--json", cwd=cwd)
    assert result.returncode == 0, result.stdout + result.stderr
    data = json.loads(result.stdout)
    assert data["exitCode"] == 0 and not data["diagnostics"], data
    bundle_dir = Path(data["bundle"])
    check("layout-" + bundle_dir.name,
          (bundle_dir / "lib" / (bundle_dir.name + ".jar")).is_file()
          and (bundle_dir / "lib" / "sprig-runtime.jar").is_file()
          and (bundle_dir / "runtime" / "legal").is_dir()
          and any((bundle_dir / "runtime" / "legal").iterdir())
          and (bundle_dir / "bin" / bundle_dir.name).is_file()
          and (bundle_dir / "bin" / (bundle_dir.name + ".cmd")).is_file()
          and "java.base" in data["modules"] and data["runtimeBytes"] > 10_000_000
          and data["platform"]["os"] and data["platform"]["arch"],
          json.dumps(data)[:600])
    return bundle_dir, data


def main():
    with tempfile.TemporaryDirectory(prefix="sprig bundle ", ignore_cleanup_errors=WINDOWS) as temporary:
        work = Path(temporary)
        home = work / "home with spaces"
        home.mkdir()
        env = bare_environment(work, home)
        elsewhere = work / "working dir"
        elsewhere.mkdir()

        # 1. hello
        hello = work / "hello"
        hello.mkdir()
        (hello / "hello.spr").write_text('print("hello, 世界")\n', encoding="utf-8")
        hello_bundle, hello_data = bundle(hello, "hello.spr", "-d", "dist")
        text = sprig("build", "hello.spr", "--bundle", "-d", "dist-text", cwd=hello)
        check("text-report-names-platform", text.returncode == 0 and "runs only on" in text.stdout
              and "Bundle:" in text.stdout, f"exit={text.returncode} out={text.stdout!r} err={text.stderr!r}")
        rebuilt = sprig("build", "hello.spr", "--bundle", "-d", "dist", "--json", cwd=hello)
        check("rebuild-replaces-previous-bundle", rebuilt.returncode == 0, rebuilt.stdout[-400:] + rebuilt.stderr[-300:])
        result = run_launcher(launcher_of(hello_bundle, "hello"), [], env, elsewhere)
        out, err = decoded(result)
        check("hello-runs-without-java", result.returncode == 0 and out == "hello, 世界\n" and err == "",
              f"exit={result.returncode} out={out!r} err={err!r}")
        archive_cache = home / ("sprig" if WINDOWS else ".cache/sprig") / "bundles"
        # -XX:+AutoCreateSharedArchive exists since JDK 19; the report says whether the
        # launchers use it, and the archive appears only then.
        feature = int(hello_data["javaVersion"].split(".")[0].split("-")[0])
        check("launcher-cds-matches-jdk", hello_data["launcherCdsArchive"] == (feature >= 19), hello_data["javaVersion"])
        launcher_text = launcher_of(hello_bundle, "hello").read_text(encoding="utf-8")
        check("launcher-flags-match-jdk", ("AutoCreateSharedArchive" in launcher_text) == hello_data["launcherCdsArchive"],
              launcher_text)
        if hello_data["launcherCdsArchive"]:
            check("cds-archive-created", any(archive_cache.rglob("app.jsa")), list(home.rglob("*")))
        again = run_launcher(launcher_of(hello_bundle, "hello"), [], env, elsewhere)
        check("hello-second-run", again.returncode == 0 and decoded(again)[0] == "hello, 世界\n", decoded(again))
        no_home = run_launcher(launcher_of(hello_bundle, "hello"), [],
                               {k: v for k, v in env.items() if k not in ("HOME", "LOCALAPPDATA")}, elsewhere)
        check("hello-without-home", no_home.returncode == 0 and decoded(no_home)[0] == "hello, 世界\n", decoded(no_home))

        # 2. a CLI: arguments with spaces and UTF-8, stderr, exit status, working directory
        cli = work / "cli"
        cli.mkdir()
        (cli / "args.spr").write_text('''import "@std/process.spr" as process
import "@std/files.spr" as files
let args = process.arguments()
print("count " + args.size())
for argument in args:
    print("[" + argument + "]")
print("cwd " + files.absolute("."))
process.print_error("warned " + args.size())
if args.size() > 2:
    process.exit(3)
''', encoding="utf-8")
        cli_bundle, _ = bundle(cli, "args.spr", "-d", "out")
        # java.exe reads its command line in the ANSI code page (a documented limit), so
        # the Windows lane keeps the arguments ASCII; Linux and macOS carry UTF-8.
        arguments = ["two words", "hello world", "--flag=x", ""] if WINDOWS else ["two words", "你好 世界", "--flag=ü", ""]
        result = run_launcher(launcher_of(cli_bundle, "args"), arguments, env, elsewhere)
        out, err = decoded(result)
        expected = "count 4\n" + "".join(f"[{a}]\n" for a in arguments)
        # The program prints its working directory as the system handed it over; on a
        # Windows runner TEMP is spelled with an 8.3 short name (C:\Users\RUNNER~1\...),
        # so the directory is compared, not the spelling.
        lines, _, cwd_line = out.rpartition("cwd ")
        reported = Path(cwd_line.rstrip("\n")) if cwd_line else None
        check("cli-arguments-cwd-exit", result.returncode == 3 and lines == expected and err == "warned 4\n"
              and reported is not None and reported.resolve() == elsewhere.resolve(),
              f"exit={result.returncode} out={out!r} err={err!r} expected={expected!r} cwd={elsewhere.resolve()}")
        result = run_launcher(launcher_of(cli_bundle, "args"), ["a"], env, elsewhere)
        out, err = decoded(result)
        check("cli-exit-zero", result.returncode == 0 and out.startswith("count 1\n[a]\n") and err == "warned 1\n",
              f"exit={result.returncode} out={out!r} err={err!r}")

        # 3. a project with a Maven dependency (commons-text)
        maven = work / "maven_slug"
        shutil.copytree(ROOT / "examples/showcases/maven_slug", maven, ignore=shutil.ignore_patterns("sprig.lock", "sprig-build"))
        resolved = sprig("resolve", cwd=maven)
        assert resolved.returncode == 0, resolved.stdout + resolved.stderr
        maven_bundle, maven_data = bundle(maven)
        jars = sorted(p.name for p in (maven_bundle / "lib").iterdir())
        check("maven-jars-named-by-coordinate", "commons-text-1.12.0.jar" in jars
              and any(j.startswith("commons-lang3-") for j in jars) and "maven-slug.jar" in jars, jars)
        result = run_launcher(launcher_of(maven_bundle, "maven-slug"), [], env, elsewhere)
        out, err = decoded(result)
        check("maven-program-runs", result.returncode == 0 and "<article" in out and "sprig" in out.lower(),
              f"exit={result.returncode} out={out!r} err={err!r}")

        # 4. SQLite: the JDBC driver's native library travels inside its JAR
        sqlite_root = work / "sqlite space"
        shutil.copytree(ROOT / "libraries/sprig-sqlite", sqlite_root / "libraries/sprig-sqlite",
                        ignore=shutil.ignore_patterns("sprig.lock", "sprig-build"))
        shutil.copytree(ROOT / "examples/sqlite", sqlite_root / "examples/sqlite",
                        ignore=shutil.ignore_patterns("sprig.lock", "*.sqlite", "sprig-build"))
        sqlite_project = sqlite_root / "examples/sqlite"
        resolved = sprig("resolve", cwd=sqlite_project)
        assert resolved.returncode == 0, resolved.stdout + resolved.stderr
        sqlite_bundle, sqlite_data = bundle(sqlite_project)
        check("sqlite-modules", "java.sql" in sqlite_data["modules"]
              and any(p.name.startswith("sqlite-jdbc-3.") for p in (sqlite_bundle / "lib").iterdir()),
              sqlite_data["modules"])
        database = elsewhere / ("notes.sqlite" if WINDOWS else "notes 世界.sqlite")
        first = run_launcher(launcher_of(sqlite_bundle, "sqlite-demo"), [str(database)], env, elsewhere)
        second = run_launcher(launcher_of(sqlite_bundle, "sqlite-demo"), [str(database)], env, elsewhere)
        # The JDBC driver's SLF4J binding warning goes to stderr; stdout is the program's alone.
        check("sqlite-persists-across-runs", first.returncode == 0 and second.returncode == 0
              and decoded(first)[0] == "persisted notes=1\n" and decoded(second)[0] == "persisted notes=2\n"
              and database.is_file(),
              f"{first.returncode} {decoded(first)} {second.returncode} {decoded(second)}")

        # 5. a sprig-web server answering a request
        web_root = work / "web"
        shutil.copytree(ROOT / "libraries/sprig-web", web_root / "libraries/sprig-web",
                        ignore=shutil.ignore_patterns("sprig.lock", "sprig-build"))
        shutil.copytree(ROOT / "examples/mini_web", web_root / "examples/mini_web",
                        ignore=shutil.ignore_patterns("sprig.lock", "sprig-build"))
        web_project = web_root / "examples/mini_web"
        resolved = sprig("resolve", cwd=web_project)
        assert resolved.returncode == 0, resolved.stdout + resolved.stderr
        web_bundle, web_data = bundle(web_project)
        check("web-modules", "jdk.httpserver" in web_data["modules"], web_data["modules"])
        server = subprocess.Popen([str(launcher_of(web_bundle, "mini-web"))], cwd=elsewhere, env=env,
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        try:
            port = None
            deadline = time.time() + 120
            while time.time() < deadline:
                line = server.stdout.readline().decode("utf-8", "replace")
                if not line:
                    break
                if line.startswith("PORT="):
                    port = int(line.strip().removeprefix("PORT="))
                    break
            check("web-server-reports-port", port is not None, "no PORT line")
            if port is not None:
                with urllib.request.urlopen(f"http://127.0.0.1:{port}/hello/%E4%BD%A0%E5%A5%BD", timeout=10) as response:
                    body = response.read().decode("utf-8")
                    check("web-server-answers", response.status == 200 and body == "Hello, 你好!", body)
        finally:
            stop(server)
            server.stdout.close()

        # --archive: a ZIP next to the bundle with Unix modes, runnable after unpacking
        archived = sprig("build", "hello.spr", "--bundle", "--archive", "-d", "dist", "--json", cwd=hello)
        assert archived.returncode == 0, archived.stdout + archived.stderr
        archive = Path(json.loads(archived.stdout)["archive"])
        check("archive-next-to-bundle", archive.is_file() and archive.parent == hello_bundle.parent
              and archive.name == "hello.zip", str(archive))
        with zipfile.ZipFile(archive) as zipped:
            names = zipped.namelist()
            executables = {info.filename for info in zipped.infolist()
                           if not info.filename.endswith("/") and (info.external_attr >> 16) & 0o111}
            java_binary = "hello/runtime/bin/java" + (".exe" if WINDOWS else "")
            check("archive-records-unix-modes", "hello/bin/hello" in executables
                  and java_binary in executables
                  and all(info.create_system == 3 for info in zipped.infolist())
                  and "hello/runtime/legal/java.base/LICENSE" in names,
                  sorted(executables)[:5])
            unpacked = work / "unpacked"
            zipped.extractall(unpacked)
            for info in zipped.infolist():
                mode = info.external_attr >> 16
                if mode and not info.filename.endswith("/"):
                    os.chmod(unpacked / info.filename, stat.S_IMODE(mode))
        result = run_launcher(launcher_of(unpacked / "hello", "hello"), [], env, elsewhere)
        check("unpacked-archive-runs", result.returncode == 0 and decoded(result)[0] == "hello, 世界\n", decoded(result))

        # Option contract and the stable codes
        wrong = sprig("run", "hello.spr", "--bundle", cwd=hello)
        check("bundle-only-with-build", wrong.returncode == 2 and "--bundle is only valid with build" in wrong.stderr + wrong.stdout,
              wrong.stdout + wrong.stderr)
        wrong = sprig("build", "hello.spr", "--archive", cwd=hello)
        check("archive-needs-bundle", wrong.returncode == 2 and "--archive is only valid with build --bundle" in wrong.stderr + wrong.stdout,
              wrong.stdout + wrong.stderr)
        for code in ("SPR-BUNDLE-TOOLS", "SPR-BUNDLE-JDEPS", "SPR-BUNDLE-LAYOUT"):
            explained = sprig("explain", code, "--json", cwd=hello)
            data = json.loads(explained.stdout)
            check("explain-" + code, explained.returncode == 0 and data["known"] is True
                  and data["documentationTopic"] == "build" and data["safeFixes"], explained.stdout[:300])
        capabilities = json.loads(sprig("capabilities", "--json", cwd=hello).stdout)
        check("capabilities-bundle-flag", capabilities["features"].get("bundle") is True, capabilities["features"])
        help_build = json.loads(sprig("help", "build", "--json", cwd=hello).stdout)
        check("help-build-topic", help_build["topic"] == "build" and any("--bundle" in line for line in help_build["syntax"])
              and any("jlink" in rule for rule in help_build["rules"]), help_build["syntax"])

        # A JRE (no jdeps, no jlink) is refused with the tools code: the compiler runs on a
        # jlink image of its own modules, built here from the running JDK.
        jlink = jdk_tool("jlink")
        jdeps = jdk_tool("jdeps")
        if jlink and jdeps:
            compiler_jars = [str(ROOT / "build/sprig-compiler.jar"), *map(str, (ROOT / "build/deps").glob("*.jar")),
                             *map(str, (ROOT / "build/deps/resolver").glob("*.jar"))]
            tool_env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
            modules = subprocess.run([jdeps, "--print-module-deps", "--ignore-missing-deps", "--multi-release", "21",
                                      *compiler_jars], env=tool_env, text=True, capture_output=True, timeout=600)
            module_list = modules.stdout.strip().splitlines()[-1] if modules.returncode == 0 and modules.stdout.strip() else "java.base"
            # Two images: a JRE without the tools (SPR-BUNDLE-TOOLS), and one that carries
            # jdeps and jlink but no packaged modules and no linkable runtime, so its jlink
            # cannot build another image (SPR-BUNDLE-LAYOUT).
            for label, extra, expected in (("jre-refused-with-tools-code", "", "SPR-BUNDLE-TOOLS"),
                                           ("no-jmods-refused-with-layout-code", ",jdk.jlink,jdk.jdeps", "SPR-BUNDLE-LAYOUT")):
                image = work / ("image " + expected)
                built = subprocess.run([jlink, "--add-modules", module_list + ",jdk.compiler" + extra, "--strip-debug",
                                        "--no-header-files", "--no-man-pages", "--output", str(image)],
                                       env=tool_env, text=True, capture_output=True, timeout=900)
                if built.returncode != 0:
                    print(f"skip {label}: jlink could not build the probe image: " + built.stderr[:200])
                    continue
                image_env = {**tool_env, "PATH": str(image / "bin") + os.pathsep + env["PATH"]}
                image_env.pop("JAVA_HOME", None)
                out_dir = "out-" + expected
                probe = subprocess.run([str(SPRIG), "build", "hello.spr", "--bundle", "-d", out_dir, "--json"], cwd=hello,
                                       env=image_env, text=True, encoding="utf-8", capture_output=True, timeout=900)
                codes = [d["code"] for d in json.loads(probe.stdout)["diagnostics"]] if probe.stdout.strip().startswith("{") else []
                check(label, probe.returncode == 1 and codes == [expected]
                      and not (hello / out_dir / "hello" / "runtime").exists(),
                      f"exit={probe.returncode} codes={codes} {probe.stdout[:300]} {probe.stderr[:300]}")
        else:
            print("skip jre-refused-with-tools-code: jlink/jdeps not found next to java")

    print(f"bundle: {COUNT - len(FAILURES)} passed, {len(FAILURES)} failed")
    for failure in FAILURES:
        print("  " + failure)
    return 1 if FAILURES else 0


if __name__ == "__main__":
    sys.exit(main())
