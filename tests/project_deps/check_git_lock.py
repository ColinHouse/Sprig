#!/usr/bin/env python3
"""A held cooperative Git cache lock fails boundedly and succeeds on retry."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[2]
CLI = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")


def main():
    with tempfile.TemporaryDirectory(prefix="sprig git lock ") as directory:
        base = Path(directory)
        home = base / "home with spaces"
        repo = base / "repo"
        repo.mkdir()
        (repo / "src").mkdir()
        (repo / "sprig.toml").write_text('[project]\nname = "dep"\nexports = ["src"]\n')
        (repo / "src" / "main.spr").write_text('print("dep")\n')
        git_env = dict(os.environ, GIT_CONFIG_GLOBAL="/dev/null",
                       GIT_CONFIG_NOSYSTEM="1", GIT_AUTHOR_NAME="Sprig test",
                       GIT_AUTHOR_EMAIL="test@example.invalid", GIT_COMMITTER_NAME="Sprig test",
                       GIT_COMMITTER_EMAIL="test@example.invalid")
        subprocess.run(["git", "init", "--quiet", str(repo)], check=True, env=git_env)
        subprocess.run(["git", "-C", str(repo), "add", "."], check=True, env=git_env)
        subprocess.run(["git", "-C", str(repo), "commit", "--quiet", "-m", "fixture"],
                       check=True, env=git_env)
        revision = subprocess.check_output(["git", "-C", str(repo), "rev-parse", "HEAD"],
                                           text=True).strip()
        url = repo.as_uri()
        project = base / "project"
        project.mkdir()
        (project / "src").mkdir()
        (project / "src" / "main.spr").write_text('import dep.main\n')
        (project / "sprig.toml").write_text(
            '[project]\nname = "app"\n\n[[dependency]]\n'
            f'name = "dep"\ngit = "{url}"\nrev = "{revision}"\n')
        lock_path = home / ".sprig" / "git" / "locks" / (hashlib.sha256(url.encode()).hexdigest()[:24] + ".lock")
        lock_path.parent.mkdir(parents=True)

        helper = base / "LockHolder.java"
        helper.write_text("""import java.nio.channels.*;
import java.nio.file.*;
public class LockHolder {
  public static void main(String[] args) throws Exception {
    try (var channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
         var lock = channel.lock()) {
      System.out.println("READY"); System.out.flush(); Thread.sleep(30000);
    }
  }
}
""")
        subprocess.run(["javac", "--release", "17", str(helper)], check=True)
        holder = subprocess.Popen(["java", "-cp", str(base), "LockHolder", str(lock_path)],
                                  stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            assert holder.stdout is not None and holder.stdout.readline().strip() == "READY"
            env = dict(os.environ, JAVA_TOOL_OPTIONS=f'-Duser.home="{home}"')
            start = time.monotonic()
            blocked = subprocess.run([str(CLI), "resolve", "--json"], cwd=project, env=env,
                                     text=True, capture_output=True, timeout=15)
            elapsed = time.monotonic() - start
            try:
                data = json.loads(blocked.stdout)
            except json.JSONDecodeError as exc:
                raise AssertionError((blocked.returncode, blocked.stdout, blocked.stderr)) from exc
            assert blocked.returncode == 1, (blocked.returncode, data, blocked.stderr)
            diagnostic = data["diagnostics"][0]
            assert diagnostic["code"] == "SPR-DEP-GIT", diagnostic
            assert "timed out waiting" in diagnostic["message"].lower(), diagnostic
            assert "retry" in diagnostic["message"].lower(), diagnostic
            assert elapsed < 12, f"lock wait exceeded the bounded deadline: {elapsed:.2f}s"
            assert holder.poll() is None, "resolver terminated or stole another process's lock holder"
            assert lock_path.stat().st_size == 0, "timed-out resolver changed the cooperative lock file"
        finally:
            holder.terminate()
            holder.wait(timeout=10)

        retry = subprocess.run([str(CLI), "resolve", "--json"], cwd=project, env=env,
                               text=True, capture_output=True, timeout=60)
        assert retry.returncode == 0, (retry.stdout, retry.stderr)
        assert (project / "sprig.lock").is_file()
        print("Git cache lock: bounded failure, untouched lock resource and post-release retry passed")


if __name__ == "__main__":
    main()
