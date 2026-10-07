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
        # cmd.exe and the launcher need the system directory, which holds no java.exe.
        env["PATH"] = os.environ.get("SystemRoot", r"C:\Windows") + r"\System32"
        env["SystemRoot"] = os.environ.get("SystemRoot", r"C:\Windows")
        env["LOCALAPPDATA"] = str(home)
        env["TEMP"] = env["TMP"] = str(work)
    return env


def launcher_of(bundle_dir, name):
    return bundle_dir / "bin" / (name + ".cmd" if WINDOWS else name)


def run_launcher(launcher, args, env, cwd, timeout=120):
    assert not shutil.which("java", path=env["PATH"]), "java must not be reachable from the test PATH"
    return subprocess.run([str(launcher), *args], cwd=cwd, env=env, capture_output=True,
                          timeout=timeout)


def decoded(result):
    return result.stdout.decode("utf-8", "replace"), result.stderr.decode("utf-8", "replace")


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
    with tempfile.TemporaryDirectory(prefix="sprig bundle ") as temporary:
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
        text = sprig("build", "hello.spr", "--bundle", "-d", "dist", cwd=hello)
        check("text-report-names-platform", text.returncode == 0 and "runs only on" in text.stdout
              and "Bundle:" in text.stdout, text.stdout + text.stderr)
        result = run_launcher(launcher_of(hello_bundle, "hello"), [], env, elsewhere)
        out, err = decoded(result)
        check("hello-runs-without-java", result.returncode == 0 and out == "hello, 世界\n" and err == "",
              f"exit={result.returncode} out={out!r} err={err!r}")
        archive_cache = home / ".cache" / "sprig" / "bundles"
        if not WINDOWS:
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
        arguments = ["two words", "你好 世界", "--flag=ü", ""]
        result = run_launcher(launcher_of(cli_bundle, "args"), arguments, env, elsewhere)
        out, err = decoded(result)
        expected = "count 4\n" + "".join(f"[{a}]\n" for a in arguments) + f"cwd {elsewhere.resolve()}\n"
        check("cli-arguments-cwd-exit", result.returncode == 3 and out == expected and err == "warned 4\n",
              f"exit={result.returncode} out={out!r} err={err!r} expected={expected!r}")
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
        database = elsewhere / "notes 世界.sqlite"
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
            server.kill()
            server.wait(timeout=20)
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
            check("archive-records-unix-modes", "hello/bin/hello" in executables
                  and "hello/runtime/bin/java" in executables
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
        jlink = shutil.which("jlink")
        jdeps = shutil.which("jdeps")
        if jlink and jdeps and not WINDOWS:
            compiler_jars = [str(ROOT / "build/sprig-compiler.jar"), *map(str, (ROOT / "build/deps").glob("*.jar")),
                             *map(str, (ROOT / "build/deps/resolver").glob("*.jar"))]
            tool_env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
            modules = subprocess.run([jdeps, "--print-module-deps", "--ignore-missing-deps", "--multi-release", "21",
                                      *compiler_jars], env=tool_env, text=True, capture_output=True, timeout=600)
            module_list = modules.stdout.strip().splitlines()[-1] if modules.returncode == 0 and modules.stdout.strip() else "java.base"
            # Two images: a JRE without the tools (SPR-BUNDLE-TOOLS), and one that carries
            # jdeps and jlink but, like every jlink image, no jmods/ (SPR-BUNDLE-LAYOUT).
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
            print("skip jre-refused-with-tools-code: jlink/jdeps not on PATH")

    print(f"bundle: {COUNT - len(FAILURES)} passed, {len(FAILURES)} failed")
    for failure in FAILURES:
        print("  " + failure)
    return 1 if FAILURES else 0


if __name__ == "__main__":
    sys.exit(main())
