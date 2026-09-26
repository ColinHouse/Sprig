#!/usr/bin/env python3
"""v0.8 project model: discovery, sprig.toml, default entry, lock requirement."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / "sprig"
CHECKS = 0


def check(name, ok, detail=""):
    global CHECKS
    if not ok:
        raise AssertionError(f"{name} {detail}")
    CHECKS += 1
    print("pass", name)


def run(cwd, *args):
    return subprocess.run([str(SPRIG), *args], cwd=cwd, text=True, capture_output=True)


def main():
    with tempfile.TemporaryDirectory(prefix="sprig-project-") as temp:
        work = Path(temp) / "app"
        work.mkdir()

        init = run(work, "init")
        check("init-creates", init.returncode == 0
              and (work / "sprig.toml").is_file() and (work / "src/main.spr").is_file()
              and "sprig resolve" in init.stdout, init.stdout + init.stderr)
        manifest_text = (work / "sprig.toml").read_text()
        check("init-manifest", 'name = "app"' in manifest_text
              and 'language = "0.8"' in manifest_text)

        again = run(work, "init")
        check("init-refuses-overwrite", again.returncode == 2
              and "Refusing to overwrite" in again.stderr)

        locked = run(work, "run")
        check("lock-required-before-resolve", locked.returncode == 1
              and "SPR-PROJECT-LOCK-MISSING" in locked.stdout + locked.stderr,
              locked.stdout + locked.stderr)

        resolve = run(work, "resolve")
        check("resolve-creates-lock", resolve.returncode == 0
              and (work / "sprig.lock").is_file(), resolve.stdout + resolve.stderr)
        first = (work / "sprig.lock").read_text()
        run(work, "resolve")
        check("lock-deterministic", first == (work / "sprig.lock").read_text())

        project = run(work, "project", "--json")
        data = json.loads(project.stdout)["project"]
        check("project-json", project.returncode == 0 and data["name"] == "app"
              and data["source"] == "src" and data["entry"] == "src/main.spr"
              and data["lockStatus"] == "current", project.stdout + project.stderr)

        default_run = run(work, "run")
        check("project-run-default-entry", default_run.returncode == 0
              and default_run.stdout == "Hello, Sprig!\n", default_run.stdout + default_run.stderr)

        (work / "other.spr").write_text('print("explicit")\n')
        explicit = run(work, "run", "other.spr")
        check("explicit-file-wins", explicit.returncode == 0
              and explicit.stdout == "explicit\n", explicit.stdout + explicit.stderr)

        nested = work / "nested" / "deep"
        nested.mkdir(parents=True)
        nested_run = run(nested, "run")
        check("nested-discovery", nested_run.returncode == 0
              and nested_run.stdout == "Hello, Sprig!\n", nested_run.stdout + nested_run.stderr)

        (work / "sprig.toml").write_text(
            '[project]\nname = "app"\nversion = "0.1.0"\nlanguage = "0.8"\n\n'
            '[[bin]]\nname = "server"\nentry = "src/server.spr"\n')
        stale = run(work, "run")
        check("stale-lock-rejected", stale.returncode == 1
              and "SPR-PROJECT-LOCK-STALE" in stale.stdout + stale.stderr,
              stale.stdout + stale.stderr)
        (work / "src" / "server.spr").write_text('print("server bin")\n')
        run(work, "resolve")
        server = run(work, "run", "--bin", "server")
        check("bin-selection", server.returncode == 0
              and server.stdout == "server bin\n", server.stdout + server.stderr)
        unknown = run(work, "run", "--bin", "missing")
        check("unknown-bin", unknown.returncode == 1
              and "SPR-PROJECT-ENTRY" in unknown.stdout + unknown.stderr)

        deps = run(work, "deps", "--json")
        deps_data = json.loads(deps.stdout)
        check("deps-empty-resolved", deps.returncode == 0
              and deps_data["sprigDependencies"] == [], deps.stdout + deps.stderr)

        (work / "sprig.toml").write_text('[project]\nname = "app"\n\n[[jvm]]\n'
                                         'group = "g"\nartifact = "a"\nversion = "1.0"\n')
        jvm = run(work, "resolve")
        check("jvm-dependency-blocked-clearly", jvm.returncode == 1
              and "SPR-DEP-MAVEN" in jvm.stdout + jvm.stderr, jvm.stdout + jvm.stderr)

        (work / "sprig.toml").write_text('[project]\nname = \n')
        broken = run(work, "project")
        check("malformed-manifest", broken.returncode == 1
              and "SPR-PROJECT-MANIFEST" in broken.stderr + broken.stdout,
              broken.stdout + broken.stderr)

        outside = Path(temp) / "outside"
        outside.mkdir()
        missing = run(outside, "project")
        check("project-outside", missing.returncode == 2
              and "No sprig.toml" in missing.stderr, missing.stdout + missing.stderr)

        single = outside / "single.spr"
        single.write_text('print("single")\n')
        single_run = run(outside, "run", str(single))
        check("single-file-no-lock", single_run.returncode == 0
              and single_run.stdout == "single\n", single_run.stdout + single_run.stderr)

    print(f"project model: {CHECKS} checks passed")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except AssertionError as error:
        print("FAIL:", error)
        sys.exit(1)
