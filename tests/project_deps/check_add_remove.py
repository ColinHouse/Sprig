#!/usr/bin/env python3
"""Transactional dependency CLI integration checks."""
import json
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
CHECKS = 0


def check(name, ok, detail=""):
    global CHECKS
    if not ok:
        raise AssertionError(f"{name}: {detail}")
    CHECKS += 1
    print("pass", name)


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def sprig(cwd, *args, env=None):
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=cwd, env=env,
                          text=True, encoding="utf-8", capture_output=True)


def git(cwd, *args):
    return subprocess.run(["git", *map(str, args)], cwd=cwd, text=True,
                          encoding="utf-8", capture_output=True)


def main():
    with tempfile.TemporaryDirectory(prefix="sprig-add-remove-") as temp:
        root = Path(temp)
        home = root / "home"
        home.mkdir()
        env = dict(os.environ, HOME=str(home), JAVA_TOOL_OPTIONS="-Duser.home=" + str(home))
        project = root / "app"
        write(project / "sprig.toml",
              '# keep this project comment\n[project]\nname = "app"\nversion = "0.1.0"\n'
              'language = "0.8"\n# preserve this spacing\nsource = "src"\n')
        write(project / "src/main.spr", "print(1)\n")
        package = root / "local-lib"
        write(package / "sprig.toml",
              '[project]\nname = "local-lib"\nversion = "0.1.0"\nlanguage = "0.8"\n'
              'source = "src"\nexports = ["lib.spr"]\n')
        write(package / "src/lib.spr", "func value() -> Int:\n    return 3\n")
        workflow = root / "agent-app"
        initialized = sprig(root, "init", workflow, "--json", env=env)
        check("agent-flow-init", initialized.returncode == 0, initialized.stdout + initialized.stderr)
        write(workflow / "src/main.spr",
              'import "@lib/lib.spr" as lib\nprint(lib.value())\n')
        workflow_add = sprig(workflow, "add", "lib", "--path", "../local-lib", "--json", env=env)
        check("agent-flow-add", workflow_add.returncode == 0, workflow_add.stdout + workflow_add.stderr)
        workflow_deps = sprig(workflow, "deps", "--json", env=env)
        check("agent-flow-deps-json", workflow_deps.returncode == 0 and "lib" in workflow_deps.stdout,
              workflow_deps.stdout + workflow_deps.stderr)
        workflow_api = sprig(workflow, "api", "@lib/lib.spr", "--json", env=env)
        check("agent-flow-api", workflow_api.returncode == 0 and "value" in workflow_api.stdout,
              workflow_api.stdout + workflow_api.stderr)
        workflow_test = sprig(workflow, "test", "--json", env=env)
        check("agent-flow-test", workflow_test.returncode == 0, workflow_test.stdout + workflow_test.stderr)
        workflow_run = sprig(workflow, "run", "--json", env=env)
        check("agent-flow-run", workflow_run.returncode == 0
              and json.loads(workflow_run.stdout).get("programOutput") == "3\n",
              workflow_run.stdout + workflow_run.stderr)
        original = (project / "sprig.toml").read_bytes()
        added = sprig(project, "add", "lib", "--path", "../local-lib", "--json", env=env)
        try:
            payload = json.loads(added.stdout)
        except ValueError:
            payload = {}
        check("local-path-add", added.returncode == 0 and payload.get("command") == "add",
              added.stdout + added.stderr)
        check("add-json-report", payload.get("manifestChanged") is True
              and payload.get("lockChanged") is True
              and payload.get("dependency", {}).get("kind") == "sprig-path"
              and payload.get("dependency", {}).get("projectName") == "local-lib",
              added.stdout + added.stderr)
        manifest_after_add = (project / "sprig.toml").read_bytes()
        lock_after_add = (project / "sprig.lock").read_bytes() if (project / "sprig.lock").is_file() else b""
        check("add-preserves-manifest-comments", b"# keep this project comment" in manifest_after_add
              and b"# preserve this spacing" in manifest_after_add, manifest_after_add.decode())
        check("add-writes-lock", bool(lock_after_add), added.stdout + added.stderr)
        deps = sprig(project, "deps", "--json", env=env)
        check("deps-sees-added-package", deps.returncode == 0 and "lib" in deps.stdout,
              deps.stdout + deps.stderr)
        duplicate = sprig(project, "add", "lib", "--path", "../local-lib", "--json", env=env)
        check("duplicate-alias-rejected", duplicate.returncode != 0
              and (project / "sprig.toml").read_bytes() == manifest_after_add
              and (project / "sprig.lock").read_bytes() == lock_after_add,
              duplicate.stdout + duplicate.stderr)
        missing = sprig(project, "add", "missing", "--path", "../absent", "--json", env=env)
        check("missing-path-rollback", missing.returncode != 0
              and (project / "sprig.toml").read_bytes() == manifest_after_add
              and (project / "sprig.lock").read_bytes() == lock_after_add,
              missing.stdout + missing.stderr)

        # A local bare monorepo exercises CLI ref intent and selected package roots.
        repo = root / "git-work"
        repo.mkdir()
        initialized = git(repo, "init", "-q", "-b", "main", ".")
        assert initialized.returncode == 0, initialized.stderr
        write(repo / "packages/codec/sprig.toml",
              '[project]\nname = "codec"\nversion = "0.1.0"\nlanguage = "0.8"\n'
              'source = "src"\nexports = ["codec.spr"]\n')
        write(repo / "packages/codec/src/codec.spr", "func value() -> Int:\n    return 8\n")
        shutil.copytree(ROOT / "libraries/sprig-json-codec", repo / "libraries/sprig-json-codec")
        subprocess.run(["git", "add", "-A"], cwd=repo, check=True)
        committed = git(repo, "-c", "user.name=fixture", "-c", "user.email=fixture@example.invalid",
                        "commit", "-q", "-m", "codec")
        assert committed.returncode == 0, committed.stderr
        revision = git(repo, "rev-parse", "HEAD").stdout.strip()
        assert git(repo, "tag", "v1", "-m", "codec release").returncode == 0
        bare = root / "git-remote.git"
        bare.mkdir()
        assert git(bare, "init", "--bare", "-q", ".").returncode == 0
        assert git(repo, "remote", "add", "origin", bare.as_uri()).returncode == 0
        pushed = git(repo, "push", "-q", "origin", "main", "--tags")
        assert pushed.returncode == 0, pushed.stderr
        git_url = bare.as_uri()

        # Maven is resolved through a tiny file repository, without public network access.
        maven_repo = root / "maven-repository"
        coordinate = maven_repo / "fixture" / "tiny" / "1.0"
        coordinate.mkdir(parents=True)
        pom = ('<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>'
               '<groupId>fixture</groupId><artifactId>tiny</artifactId><version>1.0</version>'
               '<packaging>jar</packaging></project>')
        (coordinate / "tiny-1.0.pom").write_text(pom, encoding="utf-8")
        with zipfile.ZipFile(coordinate / "tiny-1.0.jar", "w") as archive:
            archive.writestr("fixture.txt", "tiny")
        for artifact in (coordinate / "tiny-1.0.pom", coordinate / "tiny-1.0.jar"):
            artifact.with_name(artifact.name + ".sha1").write_text(
                hashlib.sha1(artifact.read_bytes()).hexdigest(), encoding="ascii")
        maven_env = dict(env, SPRIG_MAVEN_REPOSITORY=maven_repo.as_uri(),
                         SPRIG_MAVEN_CACHE=str(root / "maven-cache"))

        git_workflow = root / "agent-git-app"
        git_init = sprig(root, "init", git_workflow, "--json", env=env)
        assert git_init.returncode == 0, (git_init.stdout, git_init.stderr)
        write(git_workflow / "src/main.spr",
              'import "@std/json.spr" as json\n'
              'import "@json-codec/codec.spr" as codec\n'
              'let root = codec.root(json.parse("{\\"name\\":\\"Sprig\\"}"))\n'
              'print(codec.required_string(root, "name"))\n')
        git_workflow_add = sprig(git_workflow, "add", "json-codec", "--git", git_url,
                                 "--tag", "v1", "--subdir", "libraries/sprig-json-codec",
                                 "--json", env=maven_env)
        check("agent-flow-add-json-codec-from-git-subdir", git_workflow_add.returncode == 0,
              git_workflow_add.stdout + git_workflow_add.stderr)
        git_workflow_jvm = sprig(git_workflow, "add", "--jvm", "fixture:tiny:1.0", "--json", env=maven_env)
        check("agent-flow-add-jvm", git_workflow_jvm.returncode == 0,
              git_workflow_jvm.stdout + git_workflow_jvm.stderr)
        git_workflow_deps = sprig(git_workflow, "deps", "--json", env=maven_env)
        check("agent-flow-git-deps-json", git_workflow_deps.returncode == 0
              and "json-codec" in git_workflow_deps.stdout, git_workflow_deps.stdout + git_workflow_deps.stderr)
        git_workflow_api = sprig(git_workflow, "api", "@json-codec/codec.spr", "--json", env=maven_env)
        check("agent-flow-git-api", git_workflow_api.returncode == 0
              and "required_string" in git_workflow_api.stdout,
              git_workflow_api.stdout + git_workflow_api.stderr)
        git_workflow_test = sprig(git_workflow, "test", "--json", env=maven_env)
        check("agent-flow-git-test", git_workflow_test.returncode == 0,
              git_workflow_test.stdout + git_workflow_test.stderr)
        git_workflow_run = sprig(git_workflow, "run", "--json", env=maven_env)
        check("agent-flow-git-run", git_workflow_run.returncode == 0
              and json.loads(git_workflow_run.stdout).get("programOutput") == "Sprig\n",
              git_workflow_run.stdout + git_workflow_run.stderr)

        branch_add = sprig(project, "add", "branch-codec", "--git", git_url,
                           "--branch", "main", "--subdir", "packages/codec", "--json", env=env)
        try:
            branch_payload = json.loads(branch_add.stdout)
        except ValueError:
            branch_payload = {}
        check("git-branch-subdir-add", branch_add.returncode == 0
              and branch_payload.get("dependency", {}).get("subdir") == "packages/codec"
              and branch_payload.get("dependency", {}).get("resolvedCommit") == revision,
              branch_add.stdout + branch_add.stderr)
        tag_add = sprig(project, "add", "tag-codec", "--git", git_url,
                        "--tag", "v1", "--subdir", "packages/codec", "--json", env=env)
        check("git-tag-subdir-add", tag_add.returncode == 0
              and 'requested = "tag:v1"' in (project / "sprig.lock").read_text(encoding="utf-8"),
              tag_add.stdout + tag_add.stderr)
        rev_add = sprig(project, "add", "rev-codec", "--git", git_url,
                        "--rev", revision, "--subdir", "packages/codec", "--json", env=env)
        check("git-rev-subdir-add", rev_add.returncode == 0
              and f'requested = "rev:{revision}"' in (project / "sprig.lock").read_text(encoding="utf-8"),
              rev_add.stdout + rev_add.stderr)
        offline_path_add = sprig(project, "add", "offline-lib", "--path", "../local-lib",
                                 "--offline", "--json", env=env)
        check("offline-add-reuses-existing-locked-git-refs", offline_path_add.returncode == 0,
              offline_path_add.stdout + offline_path_add.stderr)

        before_bad_git_manifest = (project / "sprig.toml").read_bytes()
        before_bad_git_lock = (project / "sprig.lock").read_bytes()
        credentialed = sprig(project, "add", "credentialed", "--git",
                             "https://token@example.invalid/repo.git", "--branch", "main",
                             "--json", env=env)
        check("credentialed-git-url-rejected", credentialed.returncode == 1
              and (project / "sprig.toml").read_bytes() == before_bad_git_manifest
              and (project / "sprig.lock").read_bytes() == before_bad_git_lock
              and "authentication outside the manifest" in credentialed.stdout,
              credentialed.stdout + credentialed.stderr)
        bad_git = sprig(project, "add", "bad-git", "--git", (root / "missing.git").as_uri(),
                        "--branch", "main", "--json", env=env)
        try:
            bad_git_json = json.loads(bad_git.stdout)
        except ValueError:
            bad_git_json = {}
        check("bad-git-rollback-and-json-diagnostic", bad_git.returncode != 0
              and (project / "sprig.toml").read_bytes() == before_bad_git_manifest
              and (project / "sprig.lock").read_bytes() == before_bad_git_lock
              and bad_git_json.get("manifestChanged") is False
              and bool(bad_git_json.get("diagnostics"))
              and "lookup failed" in bad_git_json["diagnostics"][0]["message"],
              bad_git.stdout + bad_git.stderr)
        missing_subdir = sprig(project, "add", "missing-package", "--git", git_url,
                               "--tag", "v1", "--subdir", "missing", "--json", env=env)
        check("missing-git-package-rollback", missing_subdir.returncode != 0
              and (project / "sprig.toml").read_bytes() == before_bad_git_manifest
              and (project / "sprig.lock").read_bytes() == before_bad_git_lock,
              missing_subdir.stdout + missing_subdir.stderr)

        offline_miss = root / "offline-miss"
        write(offline_miss / "sprig.toml",
              '[project]\nname = "offline-miss"\nversion = "0.1.0"\nlanguage = "0.8"\n')
        write(offline_miss / "src/main.spr", "print(1)\n")
        offline_manifest = (offline_miss / "sprig.toml").read_bytes()
        offline_git = sprig(offline_miss, "add", "new-git", "--git", git_url,
                            "--branch", "main", "--offline", "--json", env=env)
        check("offline-git-cache-miss-keeps-project", offline_git.returncode != 0
              and (offline_miss / "sprig.toml").read_bytes() == offline_manifest
              and not (offline_miss / "sprig.lock").exists(),
              offline_git.stdout + offline_git.stderr)

        removed = sprig(project, "remove", "lib", "--json", env=env)
        check("remove-sprig-dependency", removed.returncode == 0
              and 'name = "lib"' not in (project / "sprig.toml").read_text(encoding="utf-8")
              and b"# keep this project comment" in (project / "sprig.toml").read_bytes(),
              removed.stdout + removed.stderr)
        check("remove-refreshes-lock", (project / "sprig.lock").is_file()
              and (project / "sprig.lock").read_bytes() != lock_after_add,
              "lock should reflect the dependency removal")

        comment_project = root / "remove-comments"
        write(comment_project / "sprig.toml",
              '# project heading\n[project]\nname = "remove-comments"\nversion = "0.1.0"\n'
              'language = "0.8"\n# comment before dependency\n[[dependency]]\n'
              'name = "discard"\npath = "../local-lib"\n# retain this user note\n')
        write(comment_project / "src/main.spr", "print(1)\n")
        initial_comment_lock = sprig(comment_project, "resolve", env=env)
        assert initial_comment_lock.returncode == 0, initial_comment_lock.stderr
        comment_remove = sprig(comment_project, "remove", "discard", "--json", env=env)
        remaining_text = (comment_project / "sprig.toml").read_text(encoding="utf-8")
        check("remove-preserves-surrounding-comments", comment_remove.returncode == 0
              and "# project heading" in remaining_text
              and "# comment before dependency" in remaining_text
              and "# retain this user note" in remaining_text,
              comment_remove.stdout + comment_remove.stderr + remaining_text)

        invalid_coord_before = (project / "sprig.toml").read_bytes()
        invalid_coord_lock = (project / "sprig.lock").read_bytes()
        invalid_coordinate = sprig(project, "add", "--jvm", "bad!:tiny:1.0", "--json", env=maven_env)
        check("invalid-jvm-coordinate-rejected", invalid_coordinate.returncode != 0
              and (project / "sprig.toml").read_bytes() == invalid_coord_before
              and (project / "sprig.lock").read_bytes() == invalid_coord_lock,
              invalid_coordinate.stdout + invalid_coordinate.stderr)
        jvm_add = sprig(project, "add", "--jvm", "fixture:tiny:1.0", "--json", env=maven_env)
        check("jvm-coordinate-add", jvm_add.returncode == 0
              and 'artifact = "tiny"' in (project / "sprig.toml").read_text(encoding="utf-8"),
              jvm_add.stdout + jvm_add.stderr)
        duplicate_jvm = sprig(project, "add", "--jvm", "fixture:tiny:2.0", "--json", env=maven_env)
        check("duplicate-jvm-artifact-rejected", duplicate_jvm.returncode != 0,
              duplicate_jvm.stdout + duplicate_jvm.stderr)
        jvm_remove = sprig(project / "src", "remove", "--jvm", "fixture:tiny", "--json", env=maven_env)
        check("jvm-remove-from-nested-cwd", jvm_remove.returncode == 0
              and 'artifact = "tiny"' not in (project / "sprig.toml").read_text(encoding="utf-8"),
              jvm_remove.stdout + jvm_remove.stderr)
        uncached_env = dict(maven_env, SPRIG_MAVEN_CACHE=str(root / "empty-maven-cache"))
        miss_before = (offline_miss / "sprig.toml").read_bytes()
        maven_miss = sprig(offline_miss, "add", "--jvm", "fixture:tiny:1.0",
                           "--offline", "--json", env=uncached_env)
        check("offline-maven-cache-miss-keeps-project", maven_miss.returncode != 0
              and (offline_miss / "sprig.toml").read_bytes() == miss_before
              and not (offline_miss / "sprig.lock").exists(),
              maven_miss.stdout + maven_miss.stderr)

        # An unpublishable lock destination forces rollback after manifest publication.
        failure_project = root / "rollback-app"
        write(failure_project / "sprig.toml",
              '[project]\nname = "rollback-app"\nversion = "0.1.0"\nlanguage = "0.8"\n')
        write(failure_project / "src/main.spr", "print(1)\n")
        before_failure = (failure_project / "sprig.toml").read_bytes()
        (failure_project / "sprig.lock").mkdir()
        write(failure_project / "sprig.lock/keep", "old lock destination marker\n")
        old_lock_marker = (failure_project / "sprig.lock/keep").read_bytes()
        lock_publish_failure = sprig(failure_project, "add", "lib", "--path", "../local-lib",
                                     "--json", env=env)
        check("lock-publication-failure-rolls-back-manifest", lock_publish_failure.returncode != 0
              and (failure_project / "sprig.toml").read_bytes() == before_failure
              and (failure_project / "sprig.lock/keep").read_bytes() == old_lock_marker,
              lock_publish_failure.stdout + lock_publish_failure.stderr)
        check("failure-cleans-temp-files", not list(failure_project.glob(".sprig-*.tmp")),
              str(list(failure_project.iterdir())))

    print(f"add/remove: {CHECKS} checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
