#!/usr/bin/env python3
"""Package registries: search, add from an index, publish into one, and the failure codes."""
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
GIT_ENV = dict(os.environ, GIT_CONFIG_GLOBAL="/dev/null", GIT_CONFIG_NOSYSTEM="1",
               GIT_AUTHOR_NAME="Sprig test", GIT_AUTHOR_EMAIL="test@example.invalid",
               GIT_COMMITTER_NAME="Sprig test", GIT_COMMITTER_EMAIL="test@example.invalid")
PASSED = 0


def check(name, ok, detail=""):
    global PASSED
    if not ok:
        raise AssertionError(f"{name}: {detail}")
    PASSED += 1
    print("pass " + name)


def sprig(cwd, *args, env=None):
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=cwd, env=env or os.environ,
                          capture_output=True, text=True, encoding="utf-8", timeout=300)


def git(repo, *args):
    subprocess.run(["git", "-C", str(repo), *args], check=True, env=GIT_ENV, capture_output=True)


def body(result):
    return json.loads(result.stdout)


def main():
    with tempfile.TemporaryDirectory(prefix="sprig-registry-") as directory:
        base = Path(directory)
        home = base / "home"
        home.mkdir()
        env = dict(os.environ, JAVA_TOOL_OPTIONS=f'-Duser.home="{home}"', SPRIG_JAVAC_CACHE="off")

        # A package repository with two tagged versions.
        repo = base / "mathlib"
        (repo / "src").mkdir(parents=True)
        (repo / "sprig.toml").write_text('[project]\nname = "mathlib"\nversion = "1.0.0"\nsource = "src"\nexports = ["math.spr"]\n',
                                         encoding="utf-8")
        (repo / "src" / "math.spr").write_text('func twice(n: Int) -> Int:\n    return n * 2\n', encoding="utf-8")
        subprocess.run(["git", "init", "--quiet", str(repo)], check=True, env=GIT_ENV)
        git(repo, "add", ".")
        git(repo, "commit", "--quiet", "-m", "v1")
        git(repo, "tag", "v1.0.0")
        (repo / "src" / "math.spr").write_text('func twice(n: Int) -> Int:\n    return n * 2\n\nfunc thrice(n: Int) -> Int:\n    return n * 3\n',
                                               encoding="utf-8")
        (repo / "sprig.toml").write_text('[project]\nname = "mathlib"\nversion = "1.1.0"\nsource = "src"\nexports = ["math.spr"]\n',
                                         encoding="utf-8")
        git(repo, "add", ".")
        git(repo, "commit", "--quiet", "-m", "v1.1")
        git(repo, "tag", "v1.1.0")
        url = repo.as_uri()

        # A local registry listing it, and an application declaring that registry.
        registry = base / "registry"
        (registry / "packages").mkdir(parents=True)
        (registry / "registry.toml").write_text('[registry]\nname = "local"\n', encoding="utf-8")
        (registry / "packages" / "mathlib.toml").write_text(
            f'[package]\nname = "mathlib"\ndescription = "Doubling and tripling"\ngit = "{url}"\n\n'
            '[[release]]\nversion = "1.0.0"\ntag = "v1.0.0"\n\n[[release]]\nversion = "1.1.0"\ntag = "v1.1.0"\n',
            encoding="utf-8")
        app = base / "app"
        (app / "src").mkdir(parents=True)
        (app / "sprig.toml").write_text('[project]\nname = "app"\n\n[[registry]]\nname = "local"\npath = "../registry"\n',
                                        encoding="utf-8")
        (app / "src" / "main.spr").write_text('import "@mathlib/math.spr" as math\nprint(math.twice(21))\n', encoding="utf-8")

        found = sprig(app, "search", "doubl", "--json", env=env)
        data = body(found)
        check("search-finds-package", found.returncode == 0 and [p["name"] for p in data["packages"]] == ["mathlib"]
              and data["packages"][0]["latest"] == "1.1.0" and data["registries"][0]["name"] == "local", found.stdout + found.stderr)
        text = sprig(app, "search", env=env)
        check("search-text", text.returncode == 0 and "mathlib  1.1.0" in text.stdout and "Doubling" in text.stdout, text.stdout + text.stderr)
        none = sprig(app, "search", "nothing-here", "--json", env=env)
        check("search-no-match", none.returncode == 0 and body(none)["packages"] == [], none.stdout)

        added = sprig(app, "add", "mathlib", "--json", env=env)
        data = body(added)
        manifest = (app / "sprig.toml").read_text(encoding="utf-8")
        check("add-from-registry", added.returncode == 0 and data["dependency"]["registry"] == "local"
              and data["dependency"]["version"] == "1.1.0" and data["dependency"]["intentKind"] == "tag"
              and 'tag = "v1.1.0"' in manifest and f'git = "{url}"' in manifest and (app / "sprig.lock").is_file(),
              added.stdout + added.stderr + manifest)
        ran = sprig(app, "run", "--offline", env=env)
        check("run-registry-dependency", ran.returncode == 0 and ran.stdout.strip() == "42", ran.stdout + ran.stderr)

        duplicate = sprig(app, "add", "mathlib", "--json", env=env)
        check("add-twice-rejected", duplicate.returncode != 0 and body(duplicate)["diagnostics"][0]["code"] == "SPR-PROJECT-MANIFEST",
              duplicate.stdout)
        removed = sprig(app, "remove", "mathlib", "--json", env=env)
        check("remove", removed.returncode == 0, removed.stdout + removed.stderr)
        pinned = sprig(app, "add", "mathlib", "--version", "1.0.0", "--json", env=env)
        manifest = (app / "sprig.toml").read_text(encoding="utf-8")
        check("add-pinned-version", pinned.returncode == 0 and 'tag = "v1.0.0"' in manifest, pinned.stdout + manifest)
        (app / "src" / "main.spr").write_text('import "@mathlib/math.spr" as math\nprint(math.thrice(1))\n', encoding="utf-8")
        old = sprig(app, "check", "--offline", "--json", env=env)
        check("pinned-version-is-the-old-one", old.returncode != 0, old.stdout)
        sprig(app, "remove", "mathlib", "--json", env=env)

        unknown = sprig(app, "add", "nothing", "--json", env=env)
        check("add-unknown-package", unknown.returncode == 1 and body(unknown)["diagnostics"][0]["code"] == "SPR-DEP-REGISTRY",
              unknown.stdout + unknown.stderr)
        missing_version = sprig(app, "add", "mathlib", "--version", "9.9.9", "--json", env=env)
        diagnostic = body(missing_version)["diagnostics"][0]
        check("add-unknown-version", missing_version.returncode == 1 and diagnostic["code"] == "SPR-DEP-REGISTRY"
              and "1.1.0" in diagnostic["message"], missing_version.stdout)
        wrong_registry = sprig(app, "add", "mathlib", "--registry", "elsewhere", "--json", env=env)
        check("add-unknown-registry", wrong_registry.returncode == 1
              and body(wrong_registry)["diagnostics"][0]["code"] == "SPR-DEP-REGISTRY", wrong_registry.stdout)
        mixed = sprig(app, "add", "mathlib", "--version", "1.0.0", "--git", url, "--json", env=env)
        check("version-needs-registry-lookup", mixed.returncode == 2
              and body(mixed)["diagnostics"][0]["code"] == "SPR-CLI-OPTION", mixed.stdout)

        # publish a third release from the package's own directory
        git(repo, "tag", "v1.2.0")
        published = sprig(repo, "publish", "--registry", str(registry), "--tag", "v1.2.0", "--version", "1.2.0", "--json", env=env)
        entry = (registry / "packages" / "mathlib.toml").read_text(encoding="utf-8")
        check("publish-appends-release", published.returncode == 0 and body(published)["package"]["latest"] == "1.2.0"
              and 'version = "1.2.0"' in entry and 'description = "Doubling and tripling"' in entry
              and f'git = "{url}"' in entry, published.stdout + published.stderr + entry)
        again = sprig(repo, "publish", "--registry", str(registry), "--tag", "v1.2.0", "--version", "1.2.0", "--json", env=env)
        check("publish-same-version-replaces", again.returncode == 0 and body(again)["replacedVersion"] is True
              and (registry / "packages" / "mathlib.toml").read_text(encoding="utf-8").count('version = "1.2.0"') == 1, again.stdout)
        latest = sprig(app, "search", "mathlib", "--json", env=env)
        check("search-sees-published", body(latest)["packages"][0]["latest"] == "1.2.0", latest.stdout)
        no_ref = sprig(repo, "publish", "--registry", str(registry), "--json", env=env)
        check("publish-needs-ref", no_ref.returncode == 2 and body(no_ref)["diagnostics"][0]["code"] == "SPR-CLI-OPTION", no_ref.stdout)
        fresh = base / "fresh"
        (fresh / "src").mkdir(parents=True)
        (fresh / "sprig.toml").write_text('[project]\nname = "fresh"\nversion = "0.1.0"\n', encoding="utf-8")
        first_time = sprig(fresh, "publish", "--registry", str(registry), "--tag", "v0.1.0", "--json", env=env)
        check("publish-first-time-needs-git", first_time.returncode == 2, first_time.stdout)

        # The default registry offline without a pin, from a directory with no project.
        plain = base / "plain"
        plain.mkdir()
        offline = sprig(plain, "search", "--offline", "--json", env=env)
        check("default-registry-offline-without-pin", offline.returncode == 1
              and body(offline)["diagnostics"][0]["code"] == "SPR-DEP-OFFLINE", offline.stdout + offline.stderr)

        # The repository's own registry parses and lists the first-party libraries.
        own = base / "own"
        own.mkdir()
        (own / "sprig.toml").write_text(f'[project]\nname = "own"\n\n[[registry]]\nname = "sprig"\npath = "{(ROOT / "registry").as_posix()}"\n',
                                        encoding="utf-8")
        listing = sprig(own, "search", "--json", env=env)
        names = sorted(p["name"] for p in body(listing)["packages"])
        check("repository-registry-lists-libraries", listing.returncode == 0
              and names == ["cli", "concurrent", "http", "json-codec", "sqlite", "web"], listing.stdout + listing.stderr)
    print(f"registry: {PASSED} checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
