#!/usr/bin/env python3
"""v0.8 project discovery, sprig.toml and default-entry behavior."""
import json
from pathlib import Path
import shutil
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
              and (work / "sprig.toml").is_file() and (work / "src/main.spr").is_file(),
              init.stderr)
        manifest_text = (work / "sprig.toml").read_text()
        check("init-manifest", 'name = "app"' in manifest_text
              and 'language = "0.8"' in manifest_text)

        again = run(work, "init")
        check("init-refuses-overwrite", again.returncode == 2
              and "Refusing to overwrite" in again.stderr)

        project = run(work, "project", "--json")
        data = json.loads(project.stdout)["project"]
        check("project-json", project.returncode == 0 and data["name"] == "app"
              and data["source"] == "src" and data["entry"] == "src/main.spr"
              and data["lockStatus"] == "absent"
              and data["dependencyResolution"] == "not-needed", project.stdout + project.stderr)

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
        (work / "src" / "server.spr").write_text('print("server bin")\n')
        server = run(work, "run", "--bin", "server")
        check("bin-selection", server.returncode == 0
              and server.stdout == "server bin\n", server.stdout + server.stderr)
        unknown = run(work, "run", "--bin", "missing")
        check("unknown-bin", unknown.returncode == 1
              and "SPR-PROJECT-ENTRY" in unknown.stdout + unknown.stderr)

        (work / "sprig.toml").write_text('[project]\nname = "app"\n\n[[dependency]]\n'
                                         'name = "math"\npath = "../math"\n')
        deps = run(work, "deps", "--json")
        deps_data = json.loads(deps.stdout)
        check("deps-unresolved-honest", deps.returncode == 2
              and deps_data["sprigDependencies"][0]["resolved"] is False
              and any(d["code"] == "SPR-PROJECT-UNSUPPORTED"
                      for d in deps_data["diagnostics"]), deps.stdout + deps.stderr)
        deps_text = run(work, "deps")
        check("deps-text", deps_text.returncode == 2 and "unresolved" in deps_text.stdout)

        empty = Path(temp) / "empty"
        empty.mkdir()
        (empty / "sprig.toml").write_text('[project]\nname = "empty"\n')
        empty_deps = run(empty, "deps", "--json")
        check("deps-empty", empty_deps.returncode == 0
              and json.loads(empty_deps.stdout)["sprigDependencies"] == [])

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

    print(f"project model: {CHECKS} checks passed")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except AssertionError as error:
        print("FAIL:", error)
        sys.exit(1)
