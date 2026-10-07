#!/usr/bin/env python3
"""Exercise the user-scoped installer against a local fake release server."""
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

ROOT = Path(__file__).resolve().parents[2]
INSTALLER = ROOT / "scripts/install-sprig.sh"


class ReleaseHandler(BaseHTTPRequestHandler):
    releases = []
    assets = {}

    def do_GET(self):
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


def sdk_zip(tag, printed_version=None):
    printed_version = printed_version or tag
    data = f'''#!/bin/sh
if [ "$1" = version ]; then echo "{printed_version}"; exit 0; fi
echo "fake sdk"
'''.encode()
    import io
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr(f"sprig-{tag}-jdk/", b"")
        item = zipfile.ZipInfo(f"sprig-{tag}-jdk/bin/sprig")
        item.external_attr = (stat.S_IFREG | 0o755) << 16
        archive.writestr(item, data)
    return output.getvalue()


def traversal_zip(tag):
    import io
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr(f"sprig-{tag}-jdk/../../outside-sprig-test", b"must not escape staging")
    return output.getvalue()


def main():
    if not INSTALLER.is_file():
        raise AssertionError("missing scripts/install-sprig.sh")
    if os.name == "nt":
        print("SKIP managed installer: documented Linux/macOS only")
        return
    with tempfile.TemporaryDirectory(prefix="sprig-install-test-") as temporary:
        work = Path(temporary)
        server = ThreadingHTTPServer(("127.0.0.1", 0), ReleaseHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base = f"http://127.0.0.1:{server.server_port}"
        env = dict(os.environ)
        env.update({
            "HOME": str(work / "home with spaces"),
            "SPRIG_TEST_RELEASE_BASE_URL": base + "/download",
            "SPRIG_TEST_RELEASES_API_URL": base + "/releases",
        })
        sdk_root = Path(env["HOME"]) / ".sprig"
        current = sdk_root / "current"
        try:
            def publish(tag, actual_tag=None, checksum_valid=True):
                payload = sdk_zip(tag, actual_tag)
                asset = f"sprig-{tag}-jdk.zip"
                digest = hashlib.sha256(payload).hexdigest()
                if not checksum_valid:
                    digest = "0" * 64
                ReleaseHandler.assets[f"/download/{tag}/{asset}"] = payload
                ReleaseHandler.assets[f"/download/{tag}/{asset}.sha256"] = f"{digest}  {asset}\n".encode()
                return hashlib.sha256(payload).hexdigest()

            def run(*args, expect=0, extra_env=None):
                result = subprocess.run(["sh", str(INSTALLER), *args], env={**env, **(extra_env or {})},
                                        text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20)
                if result.returncode != expect:
                    raise AssertionError(f"installer {args} returned {result.returncode}, expected {expect}\n"
                                         f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}")
                return result

            first_digest = publish("v1.2.3")
            ReleaseHandler.releases = [{"tag_name": "v1.2.3", "draft": False}]
            first = run()
            assert current.is_symlink()
            assert current.resolve() == (sdk_root / "versions/v1.2.3").resolve()
            metadata = json.loads((current / "sprig-install.json").read_text())
            assert metadata == {"installationKind": "managed", "version": "v1.2.3",
                                "sourceReleaseTag": "v1.2.3", "sdkArchiveSha256": first_digest}
            assert "export PATH=\"$HOME/.local/bin:$PATH\"" in first.stdout
            assert "fake sdk" not in first.stdout
            launcher = Path(env["HOME"]) / ".local/bin/sprig"
            assert subprocess.run([str(launcher), "version"], env=env, text=True, capture_output=True).stdout.strip() == "v1.2.3"
            run("--version", "v1.2.3")

            no_jdk = subprocess.run(["/bin/sh", str(INSTALLER), "--version", "v1.2.3"],
                                    env={**env, "HOME": str(work / "no-jdk"), "PATH": str(work)},
                                    text=True, capture_output=True, timeout=5)
            assert no_jdk.returncode != 0 and "JDK 21 or newer" in no_jdk.stderr
            fake_tools = work / "old-java-bin"
            fake_tools.mkdir()
            for name, content in {
                "java": '#!/bin/sh\necho \'openjdk version "11.0.2"\' >&2\n',
                "javac": '#!/bin/sh\necho \'javac 21.0.1\' >&2\n',
            }.items():
                tool = fake_tools / name
                tool.write_text(content)
                tool.chmod(0o755)
            old_java = subprocess.run(["/bin/sh", str(INSTALLER), "--version", "v1.2.3"],
                                      env={**env, "HOME": str(work / "old-java"),
                                           "PATH": str(fake_tools) + os.pathsep + os.environ["PATH"]},
                                      text=True, capture_output=True, timeout=5)
            assert old_java.returncode != 0 and "java reports version 11" in old_java.stderr

            # Every failed install must leave the active SDK untouched.
            publish("v2.0.0", checksum_valid=False)
            failed_hash = run("--version", "v2.0.0", expect=1)
            assert "SHA-256" in failed_hash.stderr
            assert current.resolve() == (sdk_root / "versions/v1.2.3").resolve()
            publish("v2.0.0", actual_tag="wrong-version")
            failed_smoke = run("--version", "v2.0.0", expect=1)
            assert "smoke test" in failed_smoke.stderr.lower()
            assert current.resolve() == (sdk_root / "versions/v1.2.3").resolve()

            unsafe_tag = "v2.1.0"
            unsafe_payload = traversal_zip(unsafe_tag)
            unsafe_name = f"sprig-{unsafe_tag}-jdk.zip"
            unsafe_digest = hashlib.sha256(unsafe_payload).hexdigest()
            ReleaseHandler.assets[f"/download/{unsafe_tag}/{unsafe_name}"] = unsafe_payload
            ReleaseHandler.assets[f"/download/{unsafe_tag}/{unsafe_name}.sha256"] = f"{unsafe_digest}  {unsafe_name}\n".encode()
            unsafe = run("--version", unsafe_tag, expect=1)
            assert "unsafe path" in unsafe.stderr, unsafe.stderr
            assert current.resolve() == (sdk_root / "versions/v1.2.3").resolve()
            assert not (work / "outside-sprig-test").exists()

            # Latest release selection is deterministic and successful upgrades retain old SDKs.
            publish("v2.0.0")
            ReleaseHandler.releases = [{"tag_name": "v2.0.0", "draft": False},
                                       {"tag_name": "v1.2.3", "draft": False}]
            latest = run()
            assert "Installed Sprig v2.0.0" in latest.stdout, latest.stdout
            assert current.resolve() == (sdk_root / "versions/v2.0.0").resolve(), current.resolve()
            assert (sdk_root / "versions/v1.2.3/bin/sprig").is_file()

            # A user-owned launcher is never overwritten.
            other_home = work / "other-home"
            owned = other_home / ".local/bin/sprig"
            owned.parent.mkdir(parents=True)
            owned.write_text("#!/bin/sh\necho user-owned\n")
            refused = subprocess.run(["sh", str(INSTALLER), "--version", "v1.2.3"],
                                     env={**env, "HOME": str(other_home)}, text=True,
                                     capture_output=True, timeout=20)
            assert refused.returncode != 0 and "already exists" in refused.stderr.lower()
            assert owned.read_text().find("user-owned") >= 0
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)
    print("managed installer: checksum, ZIP-path safety, smoke, switch, retention, PATH and launcher ownership passed")


if __name__ == "__main__":
    main()
