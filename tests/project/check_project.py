#!/usr/bin/env python3
"""v0.8 project model: discovery, sprig.toml, default entry, lock requirement."""
import os
import json
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
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

        # "." and ".." name the directory they resolve to.
        dotted = Path(temp) / "dotted"
        dotted.mkdir()
        here = run(dotted, "init", ".")
        check("init-dot-uses-directory-name", here.returncode == 0
              and 'name = "dotted"' in (dotted / "sprig.toml").read_text()
              and "/./" not in here.stdout.replace("\\", "/"), here.stdout + here.stderr)
        parent = Path(temp) / "parent"
        (parent / "child").mkdir(parents=True)
        up = run(parent, "init", "child/..")
        check("init-dotdot-uses-directory-name", up.returncode == 0
              and 'name = "parent"' in (parent / "sprig.toml").read_text()
              and not (parent / "child" / "sprig.toml").exists(), up.stdout + up.stderr)

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
              and "SPR-PROJECT-ENTRY" in unknown.stdout + unknown.stderr
              and "Name one: --bin server." in unknown.stderr
              and "checks every bin" not in unknown.stderr, unknown.stdout + unknown.stderr)

        deps = run(work, "deps", "--json")
        deps_data = json.loads(deps.stdout)
        check("deps-empty-resolved", deps.returncode == 0
              and deps_data["sprigDependencies"] == [], deps.stdout + deps.stderr)

        (work / "sprig.toml").write_text('[project]\nname = "app"\n\n[[jvm]]\n'
                                         'group = "g"\nartifact = "a"\nversion = "1.0"\n')
        jvm = run(work, "resolve")
        check("jvm-dependency-blocked-clearly", jvm.returncode == 1
              and "SPR-DEP-MAVEN" in jvm.stdout + jvm.stderr, jvm.stdout + jvm.stderr)

        late_manifest = (
            '[project]\nname = "app"\nversion = "0.1.0"\n\n'
            '[[dependency]]\nname = "codec"\npath = "../codec"\n\n'
            '[[dependency]]\nname = "codec"\npath = "../other"\n')
        (work / "sprig.toml").write_text(late_manifest)
        duplicate = run(work, "project")
        check("semantic-manifest-error-text-line", duplicate.returncode == 1
              and "line 10" in duplicate.stderr + duplicate.stdout
              and "Duplicate dependency name 'codec'" in duplicate.stderr + duplicate.stdout,
              duplicate.stdout + duplicate.stderr)
        duplicate_json = run(work, "project", "--json")
        duplicate_data = json.loads(duplicate_json.stdout)
        diagnostic = duplicate_data["diagnostics"][0]
        check("semantic-manifest-error-json-range", duplicate_json.returncode == 1
              and diagnostic["code"] == "SPR-PROJECT-MANIFEST"
              and diagnostic["uri"].endswith("sprig.toml")
              and diagnostic["range"]["start"]["line"] == 9,
              duplicate_json.stdout + duplicate_json.stderr)

        missing_field_manifest = (
            '[project]\nname = "app"\n\n[[jvm]]\ngroup = "fixture"\nartifact = "client"\n')
        (work / "sprig.toml").write_text(missing_field_manifest)
        missing_field = run(work, "project", "--json")
        missing_field_data = json.loads(missing_field.stdout)
        missing_diagnostic = missing_field_data["diagnostics"][0]
        check("missing-manifest-field-points-at-table-header", missing_field.returncode == 1
              and missing_diagnostic["code"] == "SPR-PROJECT-MANIFEST"
              and missing_diagnostic["range"]["start"]["line"] == 3
              and "requires group, artifact and version" in missing_diagnostic["message"],
              missing_field.stdout + missing_field.stderr)

        duplicate_bins = (
            '[project]\nname = "app"\n\n[[bin]]\nname = "worker"\nentry = "src/one.spr"\n\n'
            '[[bin]]\nname = "worker"\nentry = "src/two.spr"\n')
        (work / "sprig.toml").write_text(duplicate_bins)
        duplicate_bin = run(work, "project", "--json")
        bin_diagnostic = json.loads(duplicate_bin.stdout)["diagnostics"][0]
        check("duplicate-bin-points-at-second-name", duplicate_bin.returncode == 1
              and bin_diagnostic["range"]["start"]["line"] == 8
              and "Duplicate bin name 'worker'" in bin_diagnostic["message"],
              duplicate_bin.stdout + duplicate_bin.stderr)

        conflicting_refs = (
            '[project]\nname = "app"\n\n[[dependency]]\nname = "dep"\n'
            'git = "https://example.invalid/dep.git"\nbranch = "main"\ntag = "v1"\n')
        (work / "sprig.toml").write_text(conflicting_refs)
        refs = run(work, "project", "--json")
        refs_diagnostic = json.loads(refs.stdout)["diagnostics"][0]
        check("conflicting-ref-points-at-later-field", refs.returncode == 1
              and refs_diagnostic["range"]["start"]["line"] == 7
              and "mutually exclusive" in refs_diagnostic["message"],
              refs.stdout + refs.stderr)

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

        check_bins(Path(temp) / "tools")

    print(f"project model: {CHECKS} checks passed")
    return 0


def diagnostics(result):
    return json.loads(result.stdout)["diagnostics"]


def check_bins(tools):
    """A project of several [[bin]] tools and no [project] entry: check covers every
    bin, --bin selects one for check, build and run, and an explicit file wins."""
    (tools / "src").mkdir(parents=True)
    (tools / "sprig.toml").write_text(
        '[project]\nname = "tools"\n\n'
        '[[bin]]\nname = "server"\nentry = "src/server.spr"\n\n'
        '[[bin]]\nname = "worker"\nentry = "src/worker.spr"\n')
    (tools / "src/shared.spr").write_text('func greeting(name: String) -> String:\n    return "hello " + name\n')
    (tools / "src/server.spr").write_text('import "shared.spr" as shared\nprint(shared.greeting("server"))\n')
    (tools / "src/worker.spr").write_text('import "shared.spr" as shared\nprint(shared.greeting("worker"))\n')
    run(tools, "resolve", "--offline")

    every = run(tools, "check", "--json")
    data = json.loads(every.stdout)
    check("check-covers-every-bin", every.returncode == 0 and data["bins"] == ["server", "worker"]
          and data["diagnostics"] == [], every.stdout + every.stderr)

    # A mistake in the second bin is found by the plain check, not by --bin for the first.
    (tools / "src/worker.spr").write_text('let count: Int = "one"\nprint(count)\n')
    caught = run(tools, "check", "--json")
    caught_codes = [d["code"] for d in diagnostics(caught)]
    check("check-finds-error-in-any-bin", caught.returncode == 1 and caught_codes == ["SPR-TYPE-ASSIGN"]
          and diagnostics(caught)[0]["uri"].endswith("worker.spr"), caught.stdout)
    only_server = run(tools, "check", "--bin", "server")
    check("check-bin-selects-one", only_server.returncode == 0, only_server.stdout + only_server.stderr)
    only_worker = run(tools, "check", "--bin", "worker", "--json")
    check("check-bin-reports-its-errors", only_worker.returncode == 1
          and [d["code"] for d in diagnostics(only_worker)] == ["SPR-TYPE-ASSIGN"], only_worker.stdout)
    (tools / "src/worker.spr").write_text('import "shared.spr" as shared\nprint(shared.greeting("worker"))\n')

    # A module both bins import reports its problem once, not once per bin.
    (tools / "src/shared.spr").write_text('func greeting(name: String) -> String:\n    return 1\n')
    shared = run(tools, "check", "--json")
    check("shared-module-reported-once", shared.returncode == 1
          and [d["code"] for d in diagnostics(shared)] == ["SPR-TYPE-RETURN"], shared.stdout)
    (tools / "src/shared.spr").write_text('func greeting(name: String) -> String:\n    return "hello " + name\n')

    explicit = run(tools, "check", "src/worker.spr")
    check("explicit-file-inside-project-wins", explicit.returncode == 0, explicit.stdout + explicit.stderr)
    explicit_run = run(tools, "run", "src/worker.spr")
    check("explicit-file-runs-inside-multi-bin-project", explicit_run.returncode == 0
          and explicit_run.stdout == "hello worker\n", explicit_run.stdout + explicit_run.stderr)

    built = run(tools, "build", "--bin", "worker", "-d", str(tools / "out"), "--json")
    check("build-bin", built.returncode == 0 and (tools / "out" / "classes").is_dir(), built.stdout + built.stderr)
    worker = run(tools, "run", "--bin", "worker")
    check("run-bin", worker.returncode == 0 and worker.stdout == "hello worker\n", worker.stdout + worker.stderr)

    for command in ("build", "run"):
        ambiguous = run(tools, command, "--json")
        entry = diagnostics(ambiguous)[0]
        check(command + "-needs-a-bin", ambiguous.returncode == 1 and entry["code"] == "SPR-PROJECT-ENTRY"
              and "--bin server, --bin worker" in entry["hint"], ambiguous.stdout)
    unknown = run(tools, "check", "--bin", "missing", "--json")
    check("unknown-bin-lists-choices", unknown.returncode == 1
          and "--bin server, --bin worker" in diagnostics(unknown)[0]["hint"], unknown.stdout)
    both = run(tools, "check", "--bin", "worker", "src/server.spr")
    check("file-and-bin-rejected", both.returncode == 2 and "either a .spr file or --bin" in both.stderr,
          both.stdout + both.stderr)
    api = run(tools, "api", "--bin", "worker", ".")
    check("bin-only-for-check-build-run", api.returncode == 2
          and "--bin is only valid with check, build or run" in api.stderr, api.stdout + api.stderr)

    (tools / "src/worker.spr").unlink()
    gone = run(tools, "check", "--json")
    check("missing-bin-entry-named", gone.returncode == 1
          and diagnostics(gone)[0]["message"] == "Project entry does not exist: src/worker.spr", gone.stdout)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except AssertionError as error:
        print("FAIL:", error)
        sys.exit(1)
