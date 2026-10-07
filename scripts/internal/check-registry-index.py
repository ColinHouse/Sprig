#!/usr/bin/env python3
"""Validate a package registry index (registry/ by default) and, against a base
revision, the rules a pull request to it must keep.

Rules (issue #134):
  1. names: lowercase letters, digits and hyphens; std, sprig and the first-party
     names are reserved for the repository's own packages;
  2. releases are immutable: a published (name, version) keeps its rev and is
     never deleted; a broken release gets yanked = "true" with a reason;
  3. tags only in a strict registry: a branch release is rejected there;
  4. versions are SemVer; the newest is chosen by SemVer order;
  5. license: an SPDX identifier is required in a strict registry;
  6. owners: recorded at the first publish; a later change to an existing entry
     comes from an owner (--author) or needs maintainer approval (CODEOWNERS).

--base GITREF compares every changed entry with the version at that revision.
--fetch clones each new release at its tag, checks the tag still points at the
recorded rev, and runs sprig resolve/check (and sprig test when tests/ exists)
on the package with the SDK at --sprig.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NAME = re.compile(r"[a-z][a-z0-9-]*")
SEMVER = re.compile(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?")
REV = re.compile(r"[0-9a-f]{40}")
HANDLE = re.compile(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")
RESERVED = {"std", "sprig", "sprig-compiler", "sprig-runtime"}
SPDX_SHAPE = re.compile(r"[A-Za-z0-9.+-]+(?: (?:OR|AND|WITH) [A-Za-z0-9.+-]+)*")


class Failure(Exception):
    pass


def semver_key(version: str):
    match = SEMVER.fullmatch(version)
    numbers = tuple(int(match.group(i)) for i in (1, 2, 3))
    pre = match.group(4)
    if pre is None:
        return (numbers, 1, ())
    parts = tuple((0, int(part), "") if part.isdigit() else (1, 0, part) for part in pre.split("."))
    return (numbers, 0, parts)


def load_index(root: Path) -> dict:
    index_file = root / "registry.toml"
    data = tomllib.loads(index_file.read_text(encoding="utf-8")) if index_file.is_file() else {}
    registry = data.get("registry", {})
    strict = registry.get("strict", "false")
    if strict not in ("true", "false"):
        raise Failure(f"registry.toml: strict must be \"true\" or \"false\", not {strict!r}")
    return {"strict": strict == "true", "name": registry.get("name", root.name), "moved_to": registry.get("moved_to")}


def parse_entry(path: Path, strict: bool) -> dict:
    try:
        data = tomllib.loads(path.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError as e:
        raise Failure(f"{path.name}: not TOML: {e}")
    package = data.get("package")
    if not isinstance(package, dict):
        raise Failure(f"{path.name}: missing [package] table")
    name = package.get("name")
    if name != path.stem:
        raise Failure(f"{path.name}: [package] name must be \"{path.stem}\"")
    if not NAME.fullmatch(name) or name in RESERVED:
        raise Failure(f"{path.name}: name '{name}' must be lowercase letters, digits and hyphens; std and sprig are reserved")
    unknown = set(package) - {"name", "description", "git", "subdir", "license", "owners"}
    if unknown:
        raise Failure(f"{path.name}: unknown [package] keys {sorted(unknown)}")
    git = package.get("git")
    if not git or not isinstance(git, str):
        raise Failure(f"{path.name}: [package] git = URL is required")
    if "@" in git.split("://", 1)[-1].split("/", 1)[0]:
        raise Failure(f"{path.name}: git URL must not carry credentials")
    license_id = package.get("license")
    owners = package.get("owners", [])
    if not isinstance(owners, list) or any(not isinstance(o, str) or not HANDLE.fullmatch(o) for o in owners):
        raise Failure(f"{path.name}: owners must be a list of GitHub handles")
    if strict:
        if not license_id or not isinstance(license_id, str) or not SPDX_SHAPE.fullmatch(license_id):
            raise Failure(f"{path.name}: license = \"SPDX-ID\" is required in this registry")
        if not owners:
            raise Failure(f"{path.name}: owners = [\"github-handle\"] is required in this registry")
    releases = {}
    for item in data.get("release", []):
        unknown = set(item) - {"version", "tag", "branch", "rev", "yanked", "reason"}
        if unknown:
            raise Failure(f"{path.name}: unknown [[release]] keys {sorted(unknown)}")
        version = item.get("version")
        if not isinstance(version, str) or not SEMVER.fullmatch(version):
            raise Failure(f"{path.name}: release version {version!r} is not SemVer")
        if version in releases:
            raise Failure(f"{path.name}: version {version} is listed twice")
        tag, branch, rev = item.get("tag"), item.get("branch"), item.get("rev")
        if rev is not None and not (isinstance(rev, str) and REV.fullmatch(rev)):
            raise Failure(f"{path.name}: release {version} rev must be a full lowercase 40-character commit SHA")
        if tag and not branch:
            kind = "tag"
        elif branch and not tag and rev is None:
            kind = "branch"
        elif rev and not tag and not branch:
            kind = "rev"
        else:
            raise Failure(f"{path.name}: release {version} needs a tag (with an optional rev), a branch, or a rev")
        if strict and (kind != "tag" or rev is None):
            raise Failure(f"{path.name}: release {version} must name a tag and the commit rev it points at in this registry; "
                          "a branch is mutable")
        yanked = item.get("yanked", "false")
        if yanked not in ("true", "false"):
            raise Failure(f"{path.name}: release {version} yanked must be \"true\" or \"false\"")
        if yanked == "true" and not item.get("reason"):
            raise Failure(f"{path.name}: release {version} is yanked without a reason")
        releases[version] = {"kind": kind, "ref": tag or branch or rev, "rev": rev if kind != "rev" else rev,
                             "yanked": yanked == "true", "reason": item.get("reason")}
    return {"name": name, "git": git, "subdir": package.get("subdir"), "license": license_id, "owners": owners,
            "releases": releases}


def repo_of(root: Path) -> tuple[Path, str]:
    """The git work tree holding the index and the index's path inside it."""
    result = subprocess.run(["git", "-C", str(root), "rev-parse", "--show-toplevel"], capture_output=True, text=True)
    if result.returncode != 0:
        raise Failure(f"{root} is not inside a git work tree, so --base cannot compare it")
    top = Path(result.stdout.strip()).resolve()
    return top, root.resolve().relative_to(top).as_posix()


def git_show(repo: Path, base: str, path: str) -> str | None:
    result = subprocess.run(["git", "show", f"{base}:{path}"], cwd=repo, capture_output=True, text=True)
    return result.stdout if result.returncode == 0 else None


def check_against_base(root: Path, base: str, entries: dict, author: str | None, require_owner: bool, problems: list, notes: list):
    repo, relative = repo_of(root)
    prefix = f"{relative}/" if relative not in ("", ".") else ""
    listed = subprocess.run(["git", "ls-tree", "--name-only", base, f"{prefix}packages/"], cwd=repo,
                            capture_output=True, text=True)
    base_files = [line for line in listed.stdout.splitlines() if line.endswith(".toml")]
    for base_file in base_files:
        stem = Path(base_file).stem
        if stem not in entries:
            old_text = git_show(repo, base, base_file) or ""
            try:
                old_releases = tomllib.loads(old_text).get("release", [])
            except tomllib.TOMLDecodeError:
                old_releases = []
            # A branch release is mutable and was never a published version, so an
            # entry with only branch releases may be withdrawn; pinned releases stay.
            if old_releases and all(item.get("branch") and not item.get("tag") and not item.get("rev") for item in old_releases):
                notes.append(f"{stem}: removed (it listed only branch releases, never a pinned version)")
            else:
                problems.append(f"{stem}.toml was deleted; a published package stays listed (yank its releases instead)")
    for name, entry in entries.items():
        old_text = git_show(repo, base, f"{prefix}packages/{name}.toml")
        if old_text is None:
            notes.append(f"{name}: new package")
            continue
        with tempfile.NamedTemporaryFile("w", suffix=".toml", prefix=name + "-", delete=False, encoding="utf-8") as handle:
            handle.write(old_text)
            old_path = Path(handle.name)
        try:
            old_data = tomllib.loads(old_text)
        finally:
            old_path.unlink(missing_ok=True)
        old_releases = {item["version"]: item for item in old_data.get("release", []) if isinstance(item.get("version"), str)}
        new_text = (root / "packages" / f"{name}.toml").read_text(encoding="utf-8")
        if new_text == old_text:
            continue
        for version, item in old_releases.items():
            current = entry["releases"].get(version)
            if current is None:
                problems.append(f"{name} {version} was removed; a published release is never deleted, yank it instead")
                continue
            old_rev = item.get("rev") or (item.get("rev") if "rev" in item else None)
            old_ref = item.get("tag") or item.get("branch") or item.get("rev")
            if current["ref"] != old_ref or (old_rev and current["rev"] != old_rev):
                problems.append(f"{name} {version} changed its ref or rev; a published release is immutable")
            if item.get("yanked", "false") == "true" and not current["yanked"]:
                problems.append(f"{name} {version} was un-yanked; yanking is permanent")
        old_owners = old_data.get("package", {}).get("owners", [])
        if author is not None and old_owners and author not in old_owners:
            message = (f"{name}: changed by {author}, who is not an owner ({', '.join(old_owners)}); "
                       "this change needs maintainer approval")
            if require_owner:
                problems.append(message)
            else:
                notes.append("::warning::" + message)


def fetch_and_check(entry: dict, version: str, release: dict, sprig: Path, problems: list, notes: list):
    with tempfile.TemporaryDirectory(prefix="sprig-registry-fetch-") as directory:
        clone = Path(directory) / "pkg"
        args = ["git", "clone", "--quiet", "--depth", "1"]
        if release["kind"] in ("tag", "branch"):
            args += ["--branch", release["ref"]]
        args += [entry["git"], str(clone)]
        if subprocess.run(args, capture_output=True, text=True).returncode != 0:
            if release["kind"] == "rev":
                subprocess.run(["git", "clone", "--quiet", entry["git"], str(clone)], check=True, capture_output=True)
                subprocess.run(["git", "-C", str(clone), "checkout", "--quiet", release["ref"]], check=True, capture_output=True)
            else:
                problems.append(f"{entry['name']} {version}: cannot clone {release['ref']} from {entry['git']}")
                return
        head = subprocess.run(["git", "-C", str(clone), "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()
        expected = release["rev"] if release["kind"] != "rev" else release["ref"]
        if expected and head != expected:
            problems.append(f"{entry['name']} {version}: {release['kind']} {release['ref']} points at {head}, the index records {expected}")
            return
        package_dir = clone / entry["subdir"] if entry.get("subdir") else clone
        if not (package_dir / "sprig.toml").is_file():
            problems.append(f"{entry['name']} {version}: no sprig.toml at {entry.get('subdir') or '.'}")
            return
        manifest = tomllib.loads((package_dir / "sprig.toml").read_text(encoding="utf-8")).get("project", {})
        source = manifest.get("source", "src")
        entry_file = manifest.get("entry", f"{source}/main.spr")
        # A library package has exported modules and often no entry: check each export.
        targets = [[]] if (package_dir / entry_file).is_file() else [[f"{source}/{export}"] for export in manifest.get("exports", [])]
        if not targets:
            problems.append(f"{entry['name']} {version}: the package has neither an entry nor exports to check")
            return
        commands = [["resolve"]] + [["check", *target] for target in targets]
        for command in commands:
            result = subprocess.run([str(sprig), *command, "--offline"] if command[0] == "check" else [str(sprig), *command],
                                    cwd=package_dir, capture_output=True, text=True)
            if result.returncode != 0:
                problems.append(f"{entry['name']} {version}: sprig {' '.join(command)} failed:\n{result.stdout}{result.stderr}")
                return
        if (package_dir / "tests").is_dir():
            result = subprocess.run([str(sprig), "test"], cwd=package_dir, capture_output=True, text=True)
            if result.returncode != 0:
                problems.append(f"{entry['name']} {version}: sprig test failed:\n{result.stdout}{result.stderr}")
                return
        notes.append(f"{entry['name']} {version}: fetched at {head[:12]}, resolve/check passed")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", default=str(ROOT / "registry"), help="index directory (default: registry/)")
    parser.add_argument("--base", help="git revision to diff against (pull request base)")
    parser.add_argument("--author", help="GitHub login of the change's author, for the owners rule")
    parser.add_argument("--require-owner", action="store_true", help="fail (not warn) when the author is not an owner")
    parser.add_argument("--fetch", action="store_true", help="clone new releases and run sprig resolve/check/test")
    parser.add_argument("--sprig", default=str(ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")))
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    root = Path(args.root)
    problems: list[str] = []
    notes: list[str] = []
    entries: dict = {}
    try:
        index = load_index(root)
        packages = root / "packages"
        if not packages.is_dir():
            raise Failure(f"{root}: no packages/ directory")
        for path in sorted(packages.glob("*")):
            if path.suffix != ".toml":
                problems.append(f"{path.name}: only packages/NAME.toml files belong in the index")
                continue
            try:
                entries[path.stem] = parse_entry(path, index["strict"])
            except Failure as e:
                problems.append(str(e))
    except Failure as e:
        problems.append(str(e))
    try:
        if args.base and not problems:
            check_against_base(root, args.base, entries, args.author, args.require_owner, problems, notes)
    except Failure as e:
        problems.append(str(e))
    if args.fetch and not problems:
        changed = set(entries)
        if args.base:
            repo, relative = repo_of(root)
            prefix = f"{relative}/" if relative not in ("", ".") else ""
            changed = set()
            for name, entry in entries.items():
                old_text = git_show(repo, args.base, f"{prefix}packages/{name}.toml")
                new_text = (root / "packages" / f"{name}.toml").read_text(encoding="utf-8")
                if old_text != new_text:
                    changed.add(name)
        for name in sorted(changed):
            entry = entries[name]
            old_versions = set()
            if args.base:
                old_text = git_show(repo, args.base, f"{prefix}packages/{name}.toml")
                if old_text:
                    old_versions = {item.get("version") for item in tomllib.loads(old_text).get("release", [])}
            for version, release in entry["releases"].items():
                if version in old_versions or release["yanked"]:
                    continue
                fetch_and_check(entry, version, release, Path(args.sprig), problems, notes)
    if args.json:
        print(json.dumps({"root": str(root), "packages": sorted(entries), "problems": problems, "notes": notes}, indent=2))
    else:
        for note in notes:
            print(note)
        for problem in problems:
            print("::error::" + problem if os.environ.get("GITHUB_ACTIONS") else "error: " + problem)
        if not problems:
            print(f"registry index: {len(entries)} package(s) valid" + (" (strict)" if entries and load_index(root)["strict"] else ""))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
