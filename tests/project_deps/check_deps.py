#!/usr/bin/env python3
"""v0.8 dependency resolver: local packages, exports, lockfile, Git SHA lock, offline."""
import os
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
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
              and "lock-version = 5" in lock_text
              and "portable = true" in lock_text
              and 'path = "../lib"' in lock_text, lock_text)
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

        # ------------------------------------- portable relocation (schema 5)
        plib = base / "portable/lib"
        project(plib, "portable-lib", exports=["lib.spr"])
        write(plib / "src/lib.spr", "func double(value: Int) -> Int:\n    return value * 2\n")
        papp = base / "portable/app"
        project(papp, "portable-app", dependencies=[("math", "../lib")])
        write(papp / "src/main.spr",
              'import "@math/lib.spr" as lib\nprint(lib.double(21))\n')
        portable_resolve = run(papp, "resolve")
        portable_lock = (papp / "sprig.lock").read_text()
        check("portable-lock-schema", portable_resolve.returncode == 0
              and "lock-version = 5" in portable_lock
              and "portable = true" in portable_lock
              and 'path = "../lib"' in portable_lock,
              portable_resolve.stdout + portable_resolve.stderr + portable_lock)

        for spelling in ("../../lib", "../../lib/./", "../../lib/../lib"):
            sibling = base / "spellings" / spelling.replace("/", "_sl_").replace(".", "d")
            project(sibling, "spelling-app", dependencies=[("math", spelling)])
            write(sibling / "src/main.spr",
                  'import "@math/lib.spr" as lib\nprint(lib.double(2))\n')
            spelling_result = run(sibling, "resolve")
            spelling_lock = (sibling / "sprig.lock").read_text()
            check("locator-normalized-" + spelling.replace("/", "_"),
                  spelling_result.returncode == 0 and 'path = "../../lib"' in spelling_lock
                  and 'path = "../../lib/./"' not in spelling_lock
                  and 'path = "../../lib/../lib"' not in spelling_lock,
                  spelling_result.stdout + spelling_result.stderr + spelling_lock)

        shutil.copytree(base / "portable", base / "portable-copy")
        copied = run(base / "portable-copy/app", "run", "--offline")
        check("direct-relocation-runs", copied.returncode == 0 and copied.stdout == "42\n",
              copied.stdout + copied.stderr)
        check("direct-relocation-lock-unchanged",
              (base / "portable-copy/app/sprig.lock").read_text() == portable_lock)

        deps_json = json.loads(run(papp, "deps", "--json").stdout)
        entry = next(item for item in deps_json["sprigDependencies"] if item["name"] == "math")
        check("deps-json-portable-locator", entry["portable"] is True
              and entry["path"] == "../lib", json.dumps(entry))
        check("deps-json-runtime-path-non-identity",
              "runtimeResolvedPath" in entry
              and "runtimeResolvedPath" not in portable_lock
              and Path(entry["runtimeResolvedPath"]).is_dir(),
              json.dumps(entry))

        (papp / "sprig.lock").write_text(
            portable_lock.replace("lock-version = 5", "lock-version = 4"))
        rejected = run(papp, "check")
        check("schema-4-rejected", rejected.returncode == 1
              and "SPR-PROJECT-LOCK-SCHEMA" in rejected.stdout + rejected.stderr,
              rejected.stdout + rejected.stderr)
        (papp / "sprig.lock").write_text(portable_lock)

        absent_target = base / "absent-guard"
        project(absent_target, "absent-app",
                dependencies=[("math", str((base / "portable/does-not-exist").resolve()))])
        write(absent_target / "src/main.spr", "print(1)\n")
        missing = run(absent_target, "resolve")
        check("absolute-missing-target", missing.returncode == 1
              and "SPR-DEP-NOT-FOUND" in missing.stdout + missing.stderr,
              missing.stdout + missing.stderr)

        project(base / "portable/abs-lib", "abs-lib", exports=["abs.spr"])
        write(base / "portable/abs-lib/src/abs.spr", "func value() -> Int:\n    return 5\n")
        abs_app = base / "portable/abs-app"
        project(abs_app, "abs-app",
                dependencies=[("abs", str((base / "portable/abs-lib").resolve()))])
        write(abs_app / "src/main.spr",
              'import "@abs/abs.spr" as abs\nprint(abs.value())\n')
        abs_resolve = run(abs_app, "resolve")
        abs_lock = (abs_app / "sprig.lock").read_text()
        check("absolute-declaration-non-portable", abs_resolve.returncode == 0
              and "portable = false" in abs_lock
              and f'path = "{(base / "portable/abs-lib").resolve()}"' in abs_lock,
              abs_resolve.stdout + abs_resolve.stderr + abs_lock)
        shutil.copytree(abs_app, base / "portable/abs-app-moved")
        moved_ok = run(base / "portable/abs-app-moved", "run", "--offline")
        check("absolute-declaration-relocates-declarer", moved_ok.returncode == 0
              and moved_ok.stdout == "5\n", moved_ok.stdout + moved_ok.stderr)
        shutil.move(str(base / "portable/abs-lib"), str(base / "portable/abs-lib-gone"))
        moved_missing = run(base / "portable/abs-app-moved", "check")
        check("absolute-declaration-target-move-stale", moved_missing.returncode == 1
              and ("SPR-DEP-NOT-FOUND" in moved_missing.stdout + moved_missing.stderr
                   or "SPR-PROJECT-LOCK-STALE" in moved_missing.stdout + moved_missing.stderr),
              moved_missing.stdout + moved_missing.stderr)
        shutil.move(str(base / "portable/abs-lib-gone"), str(base / "portable/abs-lib"))

        write(papp / "sprig.toml",
              '[project]\nname = "portable-app"\nversion = "0.1.0"\nlanguage = "0.8"\n\n'
              '[[dependency]]\nname = "math"\npath = "../lib/."\n')
        renamed = run(papp, "check")
        check("declaring-locator-change-stale", renamed.returncode == 1
              and "SPR-PROJECT-LOCK-STALE" in renamed.stdout + renamed.stderr,
              renamed.stdout + renamed.stderr)
        write(papp / "sprig.toml",
              '[project]\nname = "portable-app"\nversion = "0.1.0"\nlanguage = "0.8"\n\n'
              '[[dependency]]\nname = "math"\npath = "../lib"\n')

        project(base / "portable/lib-2", "portable-lib", exports=["lib.spr"])
        write(base / "portable/lib-2/src/lib.spr", "func double(value: Int) -> Int:\n    return value * 2\n")
        link_app = base / "portable/link-app"
        project(link_app, "link-app", dependencies=[("math", "../lib-link")])
        write(link_app / "src/main.spr",
              'import "@math/lib.spr" as lib\nprint(lib.double(4))\n')
        link = base / "portable/lib-link"
        if os.name == "nt":
            print("skip symlink alias relocation (windows)")
        else:
            link.symlink_to(base / "portable/lib")
            check("symlink-alias-resolve", run(link_app, "resolve").returncode == 0)
            link.unlink()
            link.symlink_to(base / "portable/lib-2")
            link_run = run(link_app, "run", "--offline")
            check("symlink-alias-retarget-accepted", link_run.returncode == 0
                  and link_run.stdout == "8\n", link_run.stdout + link_run.stderr)

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
        shutil.copytree(base / "trans", base / "trans-copy")
        trans_lock = (base / "trans/app/sprig.lock").read_text()
        trans_copy = run(base / "trans-copy/app", "run", "--offline")
        check("transitive-relocation-runs", trans_copy.returncode == 0
              and trans_copy.stdout == "3\n", trans_copy.stdout + trans_copy.stderr)
        check("transitive-relocation-edges", "root/@a" in trans_lock
              and "root/@a/@b" in trans_lock
              and (base / "trans-copy/app/sprig.lock").read_text() == trans_lock)
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
                  f'git = "{remote.as_uri()}"\nbranch = "main"\n')
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
            env = dict(os.environ, HOME=str(empty_cache_home),
                       JAVA_TOOL_OPTIONS=f'-Duser.home="{empty_cache_home}"')
            # Keep Git available for integrity verification, but make the origin
            # unavailable. Cached offline reuse must not query or fetch it.
            remote.rename(base / "remote-unavailable")
            current = run(git_app, "resolve", "--offline", "--json")
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
