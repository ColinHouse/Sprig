#!/usr/bin/env python3
"""Verify `sprig upgrade` using local release assets and an isolated SDK home."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import tempfile
import threading
import zipfile
import io

ROOT = Path(__file__).resolve().parents[2]
CLI = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
BASE_TAG = "v" + subprocess.check_output([str(CLI), "version"], text=True).split()[-1].strip()


class ReleaseHandler(BaseHTTPRequestHandler):
    releases = []
    assets = {}
    requested = []

    def do_GET(self):
        self.requested.append(self.path)
        if self.path == "/releases":
            body = json.dumps(self.releases, indent=2).encode()
        elif self.path in self.assets:
            body = self.assets[self.path]
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_args):
        pass


def fake_archive(tag, output_version=None):
    output_version = output_version or tag.removeprefix("v")
    script = f'''#!/bin/sh
if [ "$1" = version ]; then echo "sprig-compiler {output_version}"; exit 0; fi
echo "fake sdk {output_version}"
'''.encode()
    data = io.BytesIO()
    with zipfile.ZipFile(data, "w") as archive:
        entry = zipfile.ZipInfo(f"sprig-{tag}-jdk/bin/sprig")
        entry.external_attr = (stat.S_IFREG | 0o755) << 16
        archive.writestr(entry, script)
    return data.getvalue()


def main():
    assert CLI.is_file(), "build Sprig before running this integration suite"
    with tempfile.TemporaryDirectory(prefix="sprig-upgrade-test-") as temporary:
        temp = Path(temporary)
        home = temp / "managed home with spaces"
        sdk = home / ".sprig"
        version_dir = sdk / "versions" / BASE_TAG
        (version_dir / "bin").mkdir(parents=True)
        shutil.copy2(CLI, version_dir / "bin/sprig")
        (version_dir / "build").mkdir()
        shutil.copy2(ROOT / "build/sprig-compiler.jar", version_dir / "build")
        (version_dir / "tools/resolver").mkdir(parents=True)
        shutil.copy2(ROOT / "tools/antlr-4.13.2-complete.jar", version_dir / "tools")
        for jar in (ROOT / "tools/resolver").glob("*.jar"):
            shutil.copy2(jar, version_dir / "tools/resolver")
        (version_dir / "sprig-install.json").write_text(json.dumps({
            "installationKind": "managed", "version": BASE_TAG,
            "sourceReleaseTag": BASE_TAG,
        }))
        sdk.joinpath("current").symlink_to(Path("versions") / BASE_TAG)
        launcher = home / ".local/bin/sprig"
        launcher.parent.mkdir(parents=True)
        launcher.write_text('#!/bin/sh\nexec "$HOME/.sprig/current/bin/sprig" "$@"\n')
        launcher.chmod(0o755)

        server = ThreadingHTTPServer(("127.0.0.1", 0), ReleaseHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base = f"http://127.0.0.1:{server.server_port}"
        project = temp / "untouched-project"
        project.mkdir()
        manifest = project / "sprig.toml"
        lock = project / "sprig.lock"
        manifest.write_text('[project]\nname="unchanged"\n')
        lock.write_bytes(b"project-lock-must-not-change\x00\n")
        before = (manifest.read_bytes(), lock.read_bytes())
        env = dict(os.environ)
        env.update({
            "HOME": str(home),
            "PATH": str(launcher.parent) + os.pathsep + os.environ.get("PATH", ""),
            "SPRIG_TEST_RELEASES_API_URL": base + "/releases",
            "SPRIG_TEST_RELEASE_BASE_URL": base + "/download",
        })
        try:
            def publish(tag, valid=True):
                archive_name = f"sprig-{tag}-jdk.zip"
                payload = fake_archive(tag)
                digest = hashlib.sha256(payload).hexdigest() if valid else "0" * 64
                ReleaseHandler.assets[f"/download/{tag}/{archive_name}"] = payload
                ReleaseHandler.assets[f"/download/{tag}/{archive_name}.sha256"] = f"{digest}  {archive_name}\n".encode()

            def run(*args, expect=0, binary=launcher):
                result = subprocess.run([str(binary), *args], cwd=project, env=env, text=True,
                                        stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60)
                if result.returncode != expect:
                    raise AssertionError(f"sprig {args} returned {result.returncode}, expected {expect}\n"
                                         f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}")
                return result

            ReleaseHandler.releases = [{"tag_name": BASE_TAG, "draft": False}]
            before_assets = list(ReleaseHandler.requested)
            checked = run("upgrade", "--check")
            assert "already the latest" in checked.stdout.lower(), checked.stdout
            assert not any(path.endswith(".zip") for path in ReleaseHandler.requested[len(before_assets):])

            newer = "v0.4.0-alpha.1"
            publish(newer)
            ReleaseHandler.releases = [{"tag_name": newer, "draft": False},
                                       {"tag_name": BASE_TAG, "draft": False}]
            available = run("upgrade", "--check")
            assert newer in available.stdout and "available" in available.stdout.lower(), available.stdout
            assert not any(path.endswith(".zip") for path in ReleaseHandler.requested)

            upgraded = run("upgrade")
            current = sdk / "current"
            assert current.resolve() == (sdk / "versions" / newer).resolve(), (upgraded.stdout, os.readlink(current), current.resolve())
            assert (sdk / "versions" / BASE_TAG / "bin/sprig").is_file()
            assert "0.4.0-alpha.1" in run("version").stdout
            assert (manifest.read_bytes(), lock.read_bytes()) == before

            latest = "v0.5.0-alpha.1"
            publish(latest, valid=False)
            ReleaseHandler.releases = [{"tag_name": latest, "draft": False}]
            bad = run("upgrade", expect=1, binary=version_dir / "bin/sprig")
            assert "SHA-256" in bad.stderr
            assert current.resolve() == (sdk / "versions" / newer).resolve()
            assert (manifest.read_bytes(), lock.read_bytes()) == before

            malformed = "v0.6.0-alpha.1"
            ReleaseHandler.assets[f"/download/{malformed}/sprig-{malformed}-jdk.zip"] = b"not a ZIP archive"
            name = f"sprig-{malformed}-jdk.zip"
            digest = hashlib.sha256(b"not a ZIP archive").hexdigest()
            ReleaseHandler.assets[f"/download/{malformed}/{name}.sha256"] = f"{digest}  {name}\n".encode()
            ReleaseHandler.releases = [{"tag_name": malformed, "draft": False}]
            invalid_zip = run("upgrade", expect=1, binary=version_dir / "bin/sprig")
            assert "ZIP" in invalid_zip.stderr or "archive" in invalid_zip.stderr
            assert current.resolve() == (sdk / "versions" / newer).resolve()
            assert (manifest.read_bytes(), lock.read_bytes()) == before

            bad_smoke = "v0.7.0-alpha.1"
            archive_name = f"sprig-{bad_smoke}-jdk.zip"
            payload = fake_archive(bad_smoke, output_version="9.9.9")
            ReleaseHandler.assets[f"/download/{bad_smoke}/{archive_name}"] = payload
            ReleaseHandler.assets[f"/download/{bad_smoke}/{archive_name}.sha256"] = (
                f"{hashlib.sha256(payload).hexdigest()}  {archive_name}\n").encode()
            ReleaseHandler.releases = [{"tag_name": bad_smoke, "draft": False}]
            rejected_smoke = run("upgrade", expect=1, binary=version_dir / "bin/sprig")
            assert "smoke" in rejected_smoke.stderr.lower()
            assert current.resolve() == (sdk / "versions" / newer).resolve()
            assert (manifest.read_bytes(), lock.read_bytes()) == before

            traversal = "v0.8.0-alpha.1"
            traversal_name = f"sprig-{traversal}-jdk.zip"
            attack = io.BytesIO()
            with zipfile.ZipFile(attack, "w") as archive:
                archive.writestr(f"sprig-{traversal}-jdk/../../escape", b"outside")
            payload = attack.getvalue()
            ReleaseHandler.assets[f"/download/{traversal}/{traversal_name}"] = payload
            ReleaseHandler.assets[f"/download/{traversal}/{traversal_name}.sha256"] = (
                f"{hashlib.sha256(payload).hexdigest()}  {traversal_name}\n").encode()
            ReleaseHandler.releases = [{"tag_name": traversal, "draft": False}]
            unsafe_archive = run("upgrade", expect=1, binary=version_dir / "bin/sprig")
            assert "unsafe path" in unsafe_archive.stderr.lower()
            assert current.resolve() == (sdk / "versions" / newer).resolve()
            assert (manifest.read_bytes(), lock.read_bytes()) == before

            unmanaged = temp / "unmanaged zip"
            shutil.copytree(version_dir, unmanaged)
            rejected = subprocess.run([str(unmanaged / "bin/sprig"), "upgrade", "--check"], env=env,
                                      text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20)
            assert rejected.returncode != 0 and "unmanaged" in rejected.stderr.lower(), rejected.stderr
            source = subprocess.run([str(CLI), "upgrade", "--check"], cwd=ROOT, env=env, text=True,
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20)
            assert source.returncode != 0 and "source checkout" in source.stderr.lower(), source.stderr
            invalid = run("upgrade", "--version", "v1", expect=2, binary=version_dir / "bin/sprig")
            assert "usage" in invalid.stderr.lower(), invalid.stderr
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)
    print("managed upgrade: latest check, no-op, verified switch, checksum/ZIP-path/smoke failure preservation, install-kind refusal and project immutability passed")


if __name__ == "__main__":
    main()
