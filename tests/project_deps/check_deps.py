#!/usr/bin/env python3
"""v0.8 dependency resolver: local packages, exports, lockfile, Git SHA lock, offline."""
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / "sprig"
CHECKS = 0
GIT = shutil.which("git")


def check(name, ok, detail=""):
    global CHECKS
    if not ok:
        raise AssertionError(f"{name} {detail}")
    CHECKS += 1
    print("pass", name)


def run(cwd, *args):
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=cwd, text=True,
                          capture_output=True)


def git(cwd, *args):
    return subprocess.run(["git", *map(str, args)], cwd=cwd, text=True,
                          capture_output=True)


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def project(root, name, exports=(), dependencies=()):
    lines = ["[project]", f'name = "{name}"', 'version = "0.1.0"', 'language = "0.8"']
    if exports:
        rendered = ", ".join(f'"{item}"' for item in exports)
        lines.append(f"exports = [{rendered}]")
    for alias, path in dependencies:
        lines += ["", "[[dependency]]", f'name = "{alias}"', f'path = "{path}"']
    write(root / "sprig.toml", "\n".join(lines) + "\n")


def main():
    with tempfile.TemporaryDirectory(prefix="sprig-deps-") as temp:
        base = Path(temp)

        # ---------------------------------------------------------- local
        project(base / "lib", "sprig-linear", exports=["lib.spr"])
        write(base / "lib/src/lib.spr",
              "func double(value: Int) -> Int:\n    return value * 2\n")
        write(base / "lib/src/internal.spr",
              "func hidden() -> Int:\n    return 7\n")
        project(base / "app", "app", dependencies=[("math", "../lib")])
        write(base / "app/src/main.spr",
              'import "@math/lib.spr" as lib\nprint(lib.double(21))\n')

        app = base / "app"
        resolve = run(app, "resolve")
        check("local-resolve", resolve.returncode == 0, resolve.stdout + resolve.stderr)
        lock_text = (app / "sprig.lock").read_text()
        check("local-lock-fields", 'kind = "local"' in lock_text
              and 'project-name = "sprig-linear"' in lock_text
              and "portable = false" in lock_text, lock_text)
        run(app, "resolve")
        check("lock-deterministic", lock_text == (app / "sprig.lock").read_text())
        executed = run(app, "run")
        check("at-import-runs", executed.returncode == 0 and executed.stdout == "42\n",
              executed.stdout + executed.stderr)

        write(base / "app/src/main.spr",
              'import "@math/internal.spr" as hidden\nprint(hidden.hidden())\n')
        exports = run(app, "check")
        check("exports-enforced", exports.returncode == 1
              and "SPR-PROJECT-NOT-EXPORTED" in exports.stdout + exports.stderr,
              exports.stdout + exports.stderr)

        write(base / "app/src/main.spr",
              'import "@math/../lib.spr" as escape\nprint(1)\n')
        traversal = run(app, "check")
        check("traversal-blocked", traversal.returncode == 1
              and "SPR-DEP-NOT-FOUND" in traversal.stdout + traversal.stderr,
              traversal.stdout + traversal.stderr)

        write(base / "app/sprig.toml",
              '[project]\nname = "app"\nversion = "0.1.0"\nlanguage = "0.8"\n\n'
              '[[dependency]]\nname = "math"\npath = "../lib"\n\n'
              '[[dependency]]\nname = "math"\npath = "../lib"\n')
        duplicate = run(app, "resolve")
        check("duplicate-alias", duplicate.returncode == 1
              and "Duplicate dependency name" in duplicate.stdout + duplicate.stderr,
              duplicate.stdout + duplicate.stderr)

        # ---------------------------------------------------------- cycle
        project(base / "cycle/a", "a", exports=["a.spr"], dependencies=[("b", "../b")])
        write(base / "cycle/a/src/a.spr", "func fa() -> Int:\n    return 1\n")
        project(base / "cycle/b", "b", exports=["b.spr"], dependencies=[("a", "../a")])
        write(base / "cycle/b/src/b.spr", "func fb() -> Int:\n    return 2\n")
        project(base / "cycle/app", "cycle-app", dependencies=[("a", "../a")])
        write(base / "cycle/app/src/main.spr", "print(1)\n")
        cycle = run(base / "cycle/app", "resolve")
        check("cycle-detected", cycle.returncode == 1
              and "SPR-DEP-CYCLE" in cycle.stdout + cycle.stderr,
              cycle.stdout + cycle.stderr)

        # ---------------------------------------------------- transitive
        project(base / "trans/b", "pkg-b", exports=["b.spr"])
        write(base / "trans/b/src/b.spr", "func value() -> Int:\n    return 2\n")
        project(base / "trans/a", "pkg-a", exports=["a.spr"], dependencies=[("b", "../b")])
        write(base / "trans/a/src/a.spr",
              'import "@b/b.spr" as b\nfunc from_b() -> Int:\n    return b.value() + 1\n')
        project(base / "trans/app", "trans-app", dependencies=[("a", "../a")])
        write(base / "trans/app/src/main.spr",
              'import "@a/a.spr" as a\nprint(a.from_b())\n')
        trans_app = base / "trans/app"
        check("transitive-resolve", run(trans_app, "resolve").returncode == 0)
        trans_run = run(trans_app, "run")
        check("transitive-runs", trans_run.returncode == 0 and trans_run.stdout == "3\n",
              trans_run.stdout + trans_run.stderr)
        write(trans_app / "src/main.spr",
              'import "@b/b.spr" as b\nprint(b.value())\n')
        leaked = run(trans_app, "check")
        check("alias-scope-is-package-local", leaked.returncode == 1
              and "SPR-DEP-NOT-FOUND" in leaked.stdout + leaked.stderr,
              leaked.stdout + leaked.stderr)

        # ------------------------------------------------- dependency stale
        project(base / "stale", "stale-app", dependencies=[("dep", "../stale-dep")])
        project(base / "stale-dep", "stale-dep", exports=["d.spr"])
        write(base / "stale-dep/src/d.spr", "func v() -> Int:\n    return 1\n")
        write(base / "stale/src/main.spr",
              'import "@dep/d.spr" as d\nprint(d.v())\n')
        stale = base / "stale"
        check("stale-resolve", run(stale, "resolve").returncode == 0)
        write(base / "stale-dep/sprig.toml",
              '[project]\nname = "stale-dep"\nversion = "0.1.0"\nlanguage = "0.8"\n'
              '# changed\n')
        stale_run = run(stale, "check")
        check("dependency-manifest-stale", stale_run.returncode == 1
              and "SPR-PROJECT-LOCK-STALE" in stale_run.stdout + stale_run.stderr,
              stale_run.stdout + stale_run.stderr)

        # ---------------------------------------------------------- git
        if GIT is None:
            print("skip git dependency checks (git not available)")
        else:
            remote = base / "remote-server"
            work = base / "remote-work"
            work.mkdir()
            git(work, "init", "-q", "-b", "main", ".")
            write(work / "sprig.toml",
                  '[project]\nname = "gitlib"\nversion = "0.1.0"\nlanguage = "0.8"\n'
                  'exports = ["lib.spr"]\n')
            write(work / "src/lib.spr", "func value() -> Int:\n    return 1\n")
            git(work, "add", "-A")
            git(work, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "-m", "A")
            rev_a = git(work, "rev-parse", "HEAD").stdout.strip()
            remote.mkdir()
            git(remote, "init", "-q", "--bare", ".")
            git(work, "remote", "add", "origin", str(remote))
            git(work, "push", "-q", "origin", "main")

            project(base / "git-app", "git-app")
            write(base / "git-app/sprig.toml",
                  '[project]\nname = "git-app"\nversion = "0.1.0"\nlanguage = "0.8"\n\n'
                  '[[dependency]]\nname = "remote"\n'
                  f'git = "file://{remote}"\nbranch = "main"\n')
            write(base / "git-app/src/main.spr",
                  'import "@remote/lib.spr" as lib\nprint(lib.value())\n')
            git_app = base / "git-app"

            git_resolve = run(git_app, "resolve")
            check("git-resolve", git_resolve.returncode == 0
                  and f'revision = "{rev_a}"' in (git_app / "sprig.lock").read_text(),
                  git_resolve.stdout + git_resolve.stderr)
            git_run = run(git_app, "run")
            check("git-locked-run", git_run.returncode == 0 and git_run.stdout == "1\n",
                  git_run.stdout + git_run.stderr)

            write(work / "src/lib.spr", "func value() -> Int:\n    return 2\n")
            git(work, "add", "-A")
            git(work, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "-m", "B")
            git(work, "push", "-q", "origin", "main")
            rev_b = git(work, "rev-parse", "HEAD").stdout.strip()
            after_move = run(git_app, "run")
            check("git-branch-move-ignored", after_move.returncode == 0
                  and after_move.stdout == "1\n", after_move.stdout + after_move.stderr)
            offline = run(git_app, "run", "--offline")
            check("git-offline-from-cache", offline.returncode == 0
                  and offline.stdout == "1\n", offline.stdout + offline.stderr)
            re_resolve = run(git_app, "resolve")
            check("git-resolve-updates", re_resolve.returncode == 0
                  and f'revision = "{rev_b}"' in (git_app / "sprig.lock").read_text(),
                  re_resolve.stdout + re_resolve.stderr)
            updated = run(git_app, "run", "--offline")
            check("git-updated-locked-run", updated.returncode == 0
                  and updated.stdout == "2\n", updated.stdout + updated.stderr)

            empty_cache_home = base / "empty-home"
            empty_cache_home.mkdir()
            env = dict(PATH=subprocess.os.environ.get("PATH", ""),
                       HOME=str(empty_cache_home))
            current = subprocess.run([str(SPRIG), "resolve", "--offline", "--json"],
                                     cwd=git_app, text=True, capture_output=True, env=env)
            check("git-offline-current-lock-no-network", current.returncode == 0
                  and json.loads(current.stdout)["alreadyResolved"],
                  current.stdout + current.stderr)
            (git_app / "sprig.lock").unlink()
            fresh = subprocess.run([str(SPRIG), "resolve", "--offline"], cwd=git_app,
                                   text=True, capture_output=True, env=env)
            check("git-offline-missing-cache", fresh.returncode == 1
                  and "SPR-DEP-OFFLINE" in fresh.stdout + fresh.stderr,
                  fresh.stdout + fresh.stderr)

    print(f"dependency resolver: {CHECKS} checks passed")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except AssertionError as error:
        print("FAIL:", error)
        sys.exit(1)
