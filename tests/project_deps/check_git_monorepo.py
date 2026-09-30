#!/usr/bin/env python3
"""Git monorepo ref/subdir lock behavior using only temporary local repositories."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
CHECKS = 0


def check(name, ok, detail=""):
    global CHECKS
    if not ok:
        raise AssertionError(f"{name}: {detail}")
    CHECKS += 1
    print("pass", name)


def git(cwd, *args):
    env = dict(os.environ,
               GIT_AUTHOR_NAME="Sprig fixture",
               GIT_AUTHOR_EMAIL="sprig-fixture@example.invalid",
               GIT_COMMITTER_NAME="Sprig fixture",
               GIT_COMMITTER_EMAIL="sprig-fixture@example.invalid")
    return subprocess.run(["git", *map(str, args)], cwd=cwd,
                          env=env, text=True, encoding="utf-8", capture_output=True)


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def manifest(name, exports, dependencies=()):
    result = ["[project]", f'name = "{name}"', 'version = "0.1.0"',
              'language = "0.8"', 'source = "src"',
              "exports = [" + ", ".join(f'"{item}"' for item in exports) + "]"]
    for dependency in dependencies:
        result.extend(["", "[[dependency]]"])
        result.extend(f'{key} = "{value}"' for key, value in dependency.items())
    return "\n".join(result) + "\n"


def dependency(name, url, ref, subdir=None):
    result = {"name": name, "git": url, **ref}
    if subdir is not None:
        result["subdir"] = subdir
    return result


def make_app(root, name, dependencies, source):
    root.mkdir(parents=True, exist_ok=True)
    write(root / "sprig.toml", manifest(name, (), dependencies))
    write(root / "src/main.spr", source)


def main():
    with tempfile.TemporaryDirectory(prefix="sprig-git-monorepo-") as temp:
        base = Path(temp)
        home = base / "home"
        home.mkdir()
        env = dict(os.environ, HOME=str(home), JAVA_TOOL_OPTIONS="-Duser.home=" + str(home))

        def sprig(cwd, *args):
            return subprocess.run([str(SPRIG), *map(str, args)], cwd=cwd, env=env,
                                  text=True, encoding="utf-8", capture_output=True)

        work = base / "repo-work"
        work.mkdir()
        init = git(work, "init", "-q", "-b", "main", ".")
        assert init.returncode == 0, init.stderr
        write(work / "sprig.toml", manifest("monorepo-root", ["root.spr"]))
        write(work / "src/root.spr", "func root_value() -> Int:\n    return 7\n")
        write(work / "packages/a/sprig.toml", manifest("package-a", ["a.spr"], [
            {"name": "b", "path": "../nested/b"},
        ]))
        write(work / "packages/a/src/a.spr",
              'import "@b/b.spr" as b\nfunc value() -> Int:\n    return b.value()\n')
        write(work / "packages/nested/b/sprig.toml", manifest("package-b", ["b.spr"]))
        write(work / "packages/nested/b/src/b.spr", "func value() -> Int:\n    return 1\n")
        write(work / "packages/other/sprig.toml", manifest("package-other", ["other.spr"]))
        write(work / "packages/other/src/other.spr", "func value() -> Int:\n    return 2\n")
        shutil.copytree(ROOT / "libraries/sprig-json-codec", work / "libraries/sprig-json-codec")
        write(work / "packages/marker.txt", "not a package")
        git(work, "add", "-A")
        committed = git(work, "-c", "user.name=fixture", "-c", "user.email=fixture@example.invalid",
                        "commit", "-q", "-m", "monorepo fixture")
        assert committed.returncode == 0, committed.stderr
        revision = git(work, "rev-parse", "HEAD").stdout.strip()
        tagged = git(work, "tag", "-a", "v1.0", "-m", "annotated fixture")
        assert tagged.returncode == 0, tagged.stderr
        lightweight = git(work, "tag", "vlight")
        assert lightweight.returncode == 0, lightweight.stderr
        remote = base / "repo.git"
        remote.mkdir()
        assert git(remote, "init", "--bare", "-q", ".").returncode == 0
        assert git(work, "remote", "add", "origin", str(remote)).returncode == 0
        pushed = git(work, "push", "-q", "origin", "main", "--tags")
        assert pushed.returncode == 0, pushed.stderr
        url = remote.as_uri()

        source = '''import "@a/a.spr" as a
import "@other/other.spr" as other
import "@btop/b.spr" as btop
import "@std/json.spr" as json
import "@json-codec/codec.spr" as codec
let data = codec.root(json.parse("{\\"name\\":\\"json-codec\\"}"))
print(codec.required_string(data, "name"))
print(a.value() + other.value() + btop.value())
'''
        branch_app = base / "branch-app"
        make_app(branch_app, "branch-consumer", [
            dependency("a", url, {"branch": "main"}, "packages/./a"),
            dependency("other", url, {"branch": "main"}, "packages/other"),
            dependency("btop", url, {"branch": "main"}, "packages/nested/b"),
            dependency("json-codec", url, {"tag": "v1.0"}, "libraries/sprig-json-codec"),
        ], source)
        resolved = sprig(branch_app, "resolve", "--json")
        check("branch-subdir-resolve", resolved.returncode == 0,
              resolved.stdout + resolved.stderr)
        lock = (branch_app / "sprig.lock").read_text(encoding="utf-8")
        check("same-repo-multiple-subdirs-locked", lock.count(f'revision = "{revision}"') == 4
              and 'subdir = "packages/a"' in lock
              and 'subdir = "packages/other"' in lock
              and 'subdir = "packages/nested/b"' in lock
              and 'subdir = "libraries/sprig-json-codec"' in lock,
              lock)
        check("transitive-package-edge-identity", 'id = "root/@a/@b"' in lock
              and 'id = "root/@btop"' in lock, lock)
        resolve_json = json.loads(resolved.stdout)
        git_rows = [row for row in resolve_json["sprig"] if row["kind"] == "git"]
        check("resolve-json-subdir", sorted(row["subdir"] for row in git_rows)
              == ["libraries/sprig-json-codec", "packages/a", "packages/nested/b", "packages/other"],
              json.dumps(git_rows, sort_keys=True))
        result = sprig(branch_app, "run")
        check("branch-subdir-run-through-transitive-and-codec", result.returncode == 0
              and result.stdout == "json-codec\n4\n", result.stdout + result.stderr)
        first_lock = lock
        repeat_resolve = sprig(branch_app, "resolve")
        check("subdir-lock-deterministic", repeat_resolve.returncode == 0
              and (branch_app / "sprig.lock").read_text(encoding="utf-8") == first_lock,
              repeat_resolve.stdout + repeat_resolve.stderr)
        offline = sprig(branch_app, "run", "--offline")
        check("subdir-offline-run", offline.returncode == 0 and offline.stdout == "json-codec\n4\n",
              offline.stdout + offline.stderr)
        moved = base / "moved-branch-app"
        shutil.copytree(branch_app, moved)
        relocated = sprig(moved, "run", "--offline")
        check("subdir-consumer-relocation", relocated.returncode == 0
              and relocated.stdout == "json-codec\n4\n",
              relocated.stdout + relocated.stderr)

        # A manifest change from one package root to another must stale the exact lock edge.
        manifest_path = branch_app / "sprig.toml"
        original_manifest = manifest_path.read_bytes()
        manifest_path.write_bytes(original_manifest.replace(b'packages/./a', b'packages/other', 1))
        stale_subdir = sprig(branch_app, "check")
        stale_output = stale_subdir.stdout + stale_subdir.stderr
        check("lock-rejects-changed-package-subdir", stale_subdir.returncode != 0
              and "SPR-PROJECT-LOCK-STALE" in stale_output, stale_output)
        manifest_path.write_bytes(original_manifest)

        for tag in ("v1.0", "vlight"):
            tag_app = base / ("tag-app-" + tag)
            make_app(tag_app, "tag-consumer", [
                dependency("lib", url, {"tag": tag}, "packages/a"),
            ], 'import "@lib/a.spr" as a\nprint(a.value())\n')
            tag_resolve = sprig(tag_app, "resolve")
            tag_lock = (tag_app / "sprig.lock").read_text(encoding="utf-8") if (tag_app / "sprig.lock").exists() else ""
            check("tag-subdir-resolve-" + tag, tag_resolve.returncode == 0
                  and f'requested = "tag:{tag}"' in tag_lock
                  and f'revision = "{revision}"' in tag_lock
                  and 'subdir = "packages/a"' in tag_lock,
                  tag_resolve.stdout + tag_resolve.stderr + tag_lock)
            tag_run = sprig(tag_app, "run", "--offline")
            check("tag-subdir-offline-run-" + tag,
                  tag_run.returncode == 0 and tag_run.stdout == "1\n",
                  tag_run.stdout + tag_run.stderr)

        rev_app = base / "rev-app"
        make_app(rev_app, "rev-consumer", [
            dependency("lib", url, {"rev": revision}, "packages/other"),
        ], 'import "@lib/other.spr" as other\nprint(other.value())\n')
        rev_resolve = sprig(rev_app, "resolve")
        rev_lock = (rev_app / "sprig.lock").read_text(encoding="utf-8") if (rev_app / "sprig.lock").exists() else ""
        check("full-rev-subdir", rev_resolve.returncode == 0
              and f'requested = "rev:{revision}"' in rev_lock
              and f'revision = "{revision}"' in rev_lock
              and 'subdir = "packages/other"' in rev_lock,
              rev_resolve.stdout + rev_resolve.stderr + rev_lock)
        rev_run = sprig(rev_app, "run", "--offline")
        check("full-rev-offline-run", rev_run.returncode == 0 and rev_run.stdout == "2\n",
              rev_run.stdout + rev_run.stderr)

        root_app = base / "root-app"
        make_app(root_app, "root-consumer", [dependency("rootlib", url, {"tag": "vlight"}, ".")],
                 'import "@rootlib/root.spr" as rootlib\nprint(rootlib.root_value())\n')
        root_result = sprig(root_app, "resolve")
        root_lock = (root_app / "sprig.lock").read_text(encoding="utf-8") if (root_app / "sprig.lock").exists() else ""
        check("dot-subdir-root-compatibility", root_result.returncode == 0
              and 'subdir = "' not in root_lock, root_result.stdout + root_result.stderr + root_lock)

        invalids = [
            ("traversal", 'subdir = "packages/../a"', ".."),
            ("absolute", 'subdir = "/packages/a"', "absolute"),
            ("empty", 'subdir = ""', "blank"),
        ]
        for name, field, hint in invalids:
            invalid_app = base / ("invalid-" + name)
            write(invalid_app / "sprig.toml",
                  '[project]\nname = "invalid"\nversion = "0.1.0"\nlanguage = "0.8"\n\n'
                  '[[dependency]]\nname = "lib"\ngit = "' + url + '"\nbranch = "main"\n' + field + "\n")
            invalid_result = sprig(invalid_app, "resolve")
            message = invalid_result.stdout + invalid_result.stderr
            check("reject-subdir-" + name, invalid_result.returncode == 1 and hint in message,
                  message)

        for name, subdir, expected in [
            ("missing", "packages/missing", "no sprig.toml"),
            ("file", "packages/marker.txt", "directory"),
        ]:
            invalid_app = base / ("invalid-target-" + name)
            make_app(invalid_app, "invalid-target", [
                dependency("lib", url, {"branch": "main"}, subdir),
            ], "print(1)\n")
            invalid_result = sprig(invalid_app, "resolve")
            message = invalid_result.stdout + invalid_result.stderr
            check("reject-subdir-target-" + name,
                  invalid_result.returncode == 1 and expected in message,
                  message)

        refs_app = base / "invalid-refs"
        write(refs_app / "sprig.toml",
              '[project]\nname = "invalid-refs"\nversion = "0.1.0"\nlanguage = "0.8"\n\n'
              '[[dependency]]\nname = "lib"\ngit = "' + url + '"\nbranch = "main"\ntag = "v1.0"\n')
        refs_result = sprig(refs_app, "resolve")
        check("reject-multiple-git-ref-intents", refs_result.returncode == 1
              and "mutually exclusive" in refs_result.stdout + refs_result.stderr,
              refs_result.stdout + refs_result.stderr)

        short_app = base / "invalid-short-rev"
        make_app(short_app, "short-rev", [dependency("lib", url, {"rev": revision[:12]})], "print(1)\n")
        short_result = sprig(short_app, "resolve")
        check("reject-short-revision", short_result.returncode == 1
              and "40-character" in short_result.stdout + short_result.stderr,
              short_result.stdout + short_result.stderr)

        path_subdir_app = base / "invalid-path-subdir"
        write(path_subdir_app / "sprig.toml",
              '[project]\nname = "invalid-path-subdir"\nversion = "0.1.0"\n'
              'language = "0.8"\n\n[[dependency]]\nname = "lib"\npath = "../lib"\n'
              'subdir = "nested"\n')
        path_subdir = sprig(path_subdir_app, "resolve")
        check("reject-subdir-on-local-dependency", path_subdir.returncode == 1
              and "only valid for a git dependency" in path_subdir.stdout + path_subdir.stderr,
              path_subdir.stdout + path_subdir.stderr)

        if os.name != "nt":
            outside = base / "outside"
            write(outside / "sprig.toml", manifest("escape", ["escape.spr"]))
            write(outside / "src/escape.spr", "func escape() -> Int:\n    return 9\n")
            try:
                (work / "packages/escape").symlink_to(outside, target_is_directory=True)
                git(work, "add", "-A")
                git(work, "-c", "user.name=fixture", "-c", "user.email=fixture@example.invalid",
                    "commit", "-q", "-m", "symlink escape fixture")
                git(work, "push", "-q", "origin", "main")
                escape_app = base / "escape-app"
                make_app(escape_app, "escape-consumer", [
                    dependency("escape", url, {"branch": "main"}, "packages/escape"),
                ], 'import "@escape/escape.spr" as escape\nprint(escape.escape())\n')
                escape_result = sprig(escape_app, "resolve")
                check("reject-symlink-subdir-escape", escape_result.returncode == 1
                      and "symbolic-link" in escape_result.stdout.lower() + escape_result.stderr.lower(),
                      escape_result.stdout + escape_result.stderr)
            except OSError as failure:
                print("skip symlink fixture:", failure)

    print(f"git monorepo dependencies: {CHECKS} checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
