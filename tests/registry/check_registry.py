#!/usr/bin/env python3
"""Package registries: search, add from an index, publish into one, and the failure codes."""
import json
import os
import shutil
import sys
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
        rev_120 = subprocess.run(["git", "-C", str(repo), "rev-parse", "v1.2.0^{commit}"], capture_output=True, text=True,
                                 env=GIT_ENV).stdout.strip()
        check("publish-records-the-commit", f'rev = "{rev_120}"' in entry and body(published)["package"]["releases"][-1]["rev"] == rev_120, entry)
        again = sprig(repo, "publish", "--registry", str(registry), "--tag", "v1.2.0", "--version", "1.2.0", "--json", env=env)
        check("publish-same-version-rejected", again.returncode == 1 and body(again)["diagnostics"][0]["code"] == "SPR-DEP-REGISTRY"
              and "already published" in body(again)["diagnostics"][0]["message"]
              and (registry / "packages" / "mathlib.toml").read_text(encoding="utf-8").count('version = "1.2.0"') == 1, again.stdout)
        latest = sprig(app, "search", "mathlib", "--json", env=env)
        check("search-sees-published", body(latest)["packages"][0]["latest"] == "1.2.0", latest.stdout)

        # Versions order by SemVer, not by list position; a pre-release sorts before its release.
        (registry / "packages" / "mathlib.toml").write_text(
            (registry / "packages" / "mathlib.toml").read_text(encoding="utf-8")
            + '\n[[release]]\nversion = "1.10.0-beta.1"\ntag = "v1.1.0"\n\n[[release]]\nversion = "1.2.1"\ntag = "v1.2.0"\n',
            encoding="utf-8")
        ordered = sprig(app, "search", "mathlib", "--json", env=env)
        check("latest-by-semver", body(ordered)["packages"][0]["latest"] == "1.10.0-beta.1", ordered.stdout)
        bad_version = base / "badreg"
        (bad_version / "packages").mkdir(parents=True)
        (bad_version / "packages" / "odd.toml").write_text(f'[package]\nname = "odd"\ngit = "{url}"\n\n[[release]]\nversion = "1.0"\ntag = "v1.0.0"\n',
                                                           encoding="utf-8")
        (app / "sprig.toml").write_text('[project]\nname = "app"\n\n[[registry]]\nname = "local"\npath = "../registry"\n\n'
                                        '[[registry]]\nname = "bad"\npath = "../badreg"\n', encoding="utf-8")
        not_semver = sprig(app, "search", "--registry", "bad", "--json", env=env)
        check("non-semver-version-rejected", not_semver.returncode == 1 and "SemVer" in body(not_semver)["diagnostics"][0]["message"],
              not_semver.stdout)
        (bad_version / "packages" / "odd.toml").unlink()
        (bad_version / "packages" / "Bad_Name.toml").write_text(f'[package]\nname = "Bad_Name"\ngit = "{url}"\n', encoding="utf-8")
        bad_name = sprig(app, "search", "--registry", "bad", "--json", env=env)
        check("package-name-rule", bad_name.returncode == 1 and "lowercase" in body(bad_name)["diagnostics"][0]["hint"], bad_name.stdout)
        (bad_version / "packages" / "Bad_Name.toml").unlink()
        (app / "sprig.toml").write_text('[project]\nname = "app"\n\n[[registry]]\nname = "local"\npath = "../registry"\n', encoding="utf-8")

        # Yanking: never chosen for a new dependency, refused by version, still resolvable from a lock.
        (app / "src" / "main.spr").write_text('import "@mathlib/math.spr" as math\nprint(math.thrice(14))\n', encoding="utf-8")
        pinned_latest = sprig(app, "add", "mathlib", "--version", "1.2.1", "--json", env=env)
        check("add-before-yank", pinned_latest.returncode == 0, pinned_latest.stdout + pinned_latest.stderr)
        yanked = sprig(repo, "publish", "--registry", str(registry), "--yank", "1.2.1", "--reason", "built from the wrong commit", "--json", env=env)
        entry = (registry / "packages" / "mathlib.toml").read_text(encoding="utf-8")
        check("publish-yank", yanked.returncode == 0 and body(yanked)["yanked"] is True and 'yanked = "true"' in entry
              and 'reason = "built from the wrong commit"' in entry, yanked.stdout + yanked.stderr + entry)
        still_runs = sprig(app, "run", "--offline", env=env)
        check("locked-yanked-release-still-resolves", still_runs.returncode == 0 and still_runs.stdout.strip() == "42",
              still_runs.stdout + still_runs.stderr)
        sprig(app, "remove", "mathlib", "--json", env=env)
        refused = sprig(app, "add", "mathlib", "--version", "1.2.1", "--json", env=env)
        check("add-yanked-version-refused", refused.returncode == 1 and "yanked" in body(refused)["diagnostics"][0]["message"], refused.stdout)
        skipped = sprig(app, "search", "mathlib", "--json", env=env)
        check("latest-skips-yanked", body(skipped)["packages"][0]["latest"] == "1.10.0-beta.1", skipped.stdout)
        no_yank_target = sprig(repo, "publish", "--registry", str(registry), "--yank", "9.9.9", "--reason", "x", "--json", env=env)
        check("yank-unknown-version", no_yank_target.returncode == 1, no_yank_target.stdout)

        # A moved tag is detected: the index recorded the commit v1.2.0 pointed at.
        git(repo, "tag", "-f", "v1.2.0", "v1.0.0")
        moved = sprig(app, "add", "mathlib", "--version", "1.2.0", "--json", env=env)
        check("moved-tag-refused", moved.returncode == 1 and "now points at" in body(moved)["diagnostics"][0]["message"], moved.stdout)
        git(repo, "tag", "-f", "v1.2.0", rev_120)
        restored = sprig(app, "add", "mathlib", "--version", "1.2.0", "--json", env=env)
        check("pinned-tag-accepted", restored.returncode == 0, restored.stdout + restored.stderr)
        sprig(app, "remove", "mathlib", "--json", env=env)

        # A strict registry (the default one's rules): tags pinned to a commit, license and owners.
        strict = base / "strict"
        (strict / "packages").mkdir(parents=True)
        (strict / "registry.toml").write_text('[registry]\nname = "strict"\nstrict = "true"\n', encoding="utf-8")
        (strict / "packages" / "mathlib.toml").write_text(
            f'[package]\nname = "mathlib"\ngit = "{url}"\n\n[[release]]\nversion = "1.0.0"\nbranch = "main"\n', encoding="utf-8")
        (app / "sprig.toml").write_text('[project]\nname = "app"\n\n[[registry]]\nname = "strict"\npath = "../strict"\n', encoding="utf-8")
        branch_in_strict = sprig(app, "search", "--json", env=env)
        check("strict-rejects-branch-release", branch_in_strict.returncode == 1
              and "license" in body(branch_in_strict)["diagnostics"][0]["message"] + body(branch_in_strict)["diagnostics"][0]["message"],
              branch_in_strict.stdout)
        (strict / "packages" / "mathlib.toml").unlink()
        unlicensed = sprig(repo, "publish", "--registry", str(strict), "--git", url, "--tag", "v1.0.0", "--version", "1.0.0", "--json", env=env)
        check("strict-publish-needs-license-and-owner", unlicensed.returncode == 1
              and body(unlicensed)["diagnostics"][0]["code"] == "SPR-DEP-REGISTRY", unlicensed.stdout)
        licensed = sprig(repo, "publish", "--registry", str(strict), "--git", url, "--tag", "v1.0.0", "--version", "1.0.0",
                         "--license", "Apache-2.0", "--owner", "octocat", "--json", env=env)
        strict_entry = (strict / "packages" / "mathlib.toml").read_text(encoding="utf-8")
        check("strict-publish-records-license-owner-rev", licensed.returncode == 0 and 'license = "Apache-2.0"' in strict_entry
              and 'owners = ["octocat"]' in strict_entry and 'rev = "' in strict_entry, licensed.stdout + licensed.stderr + strict_entry)
        branch_publish = sprig(repo, "publish", "--registry", str(strict), "--branch", "main", "--version", "1.0.1", "--json", env=env)
        check("strict-publish-rejects-branch", branch_publish.returncode == 1, branch_publish.stdout)

        # The index validator: valid index, then the immutability rules against a git base.
        index_repo = base / "index"
        shutil.copytree(strict, index_repo)
        subprocess.run(["git", "init", "--quiet", str(index_repo)], check=True, env=GIT_ENV)
        git(index_repo, "add", ".")
        git(index_repo, "commit", "--quiet", "-m", "index")
        validator = ROOT / "scripts" / "internal" / "check-registry-index.py"
        valid = subprocess.run([sys.executable, str(validator), "--root", str(index_repo), "--json"], capture_output=True, text=True)
        check("validator-accepts-valid-index", valid.returncode == 0 and body(valid)["problems"] == [], valid.stdout + valid.stderr)
        # Fetch the published release at its tag and run the SDK on it.
        fetched = subprocess.run([sys.executable, str(validator), "--root", str(index_repo), "--fetch", "--sprig", str(SPRIG), "--json"],
                                 capture_output=True, text=True, env=env)
        check("validator-fetches-and-checks", fetched.returncode == 0 and any("resolve/check passed" in n for n in body(fetched)["notes"]),
              fetched.stdout + fetched.stderr)
        # Mutating a published release is rejected against the base; yanking is accepted.
        text = (index_repo / "packages" / "mathlib.toml").read_text(encoding="utf-8")
        (index_repo / "packages" / "mathlib.toml").write_text(text.replace(f'rev = "', 'rev = "0000000000000000000000000000000000000000"\n#'), encoding="utf-8")
        git(index_repo, "add", ".")
        git(index_repo, "commit", "--quiet", "-m", "move")
        base_rev = subprocess.run(["git", "-C", str(index_repo), "rev-parse", "HEAD~1"], capture_output=True, text=True, env=GIT_ENV).stdout.strip()
        mutated = subprocess.run([sys.executable, str(validator), "--root", str(index_repo), "--base", base_rev, "--json"],
                                 capture_output=True, text=True, cwd=index_repo)
        check("validator-rejects-changed-rev", mutated.returncode == 1 and any("immutable" in p for p in body(mutated)["problems"]),
              mutated.stdout + mutated.stderr)
        git(index_repo, "reset", "--quiet", "--hard", "HEAD~1")
        (index_repo / "packages" / "mathlib.toml").write_text(text + 'yanked = "true"\nreason = "superseded"\n', encoding="utf-8")
        git(index_repo, "add", ".")
        git(index_repo, "commit", "--quiet", "-m", "yank")
        yank_ok = subprocess.run([sys.executable, str(validator), "--root", str(index_repo), "--base", "HEAD~1", "--author", "octocat", "--json"],
                                 capture_output=True, text=True, cwd=index_repo)
        check("validator-accepts-yank-by-owner", yank_ok.returncode == 0 and body(yank_ok)["problems"] == [], yank_ok.stdout + yank_ok.stderr)
        stranger = subprocess.run([sys.executable, str(validator), "--root", str(index_repo), "--base", "HEAD~1", "--author", "someone",
                                   "--require-owner", "--json"], capture_output=True, text=True, cwd=index_repo)
        check("validator-flags-non-owner", stranger.returncode == 1 and any("maintainer approval" in p for p in body(stranger)["problems"]),
              stranger.stdout + stranger.stderr)
        (app / "sprig.toml").write_text('[project]\nname = "app"\n\n[[registry]]\nname = "local"\npath = "../registry"\n', encoding="utf-8")
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
              and names == ["cli", "http", "json-codec", "sqlite", "web"], listing.stdout + listing.stderr)
    print(f"registry: {PASSED} checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
