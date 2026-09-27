"""Shared deterministic local HTTP fixture using the normal streaming Sprig CLI."""
from contextlib import contextmanager
import os
from pathlib import Path
import queue
import subprocess
import threading
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")


def command(project, *args):
    result = subprocess.run([str(SPRIG), *map(str, args)], cwd=project, capture_output=True,
                            text=True, encoding="utf-8", timeout=90)
    assert result.returncode == 0, result.stdout + result.stderr
    return result.stdout


@contextmanager
def server(project, build=None, entry=None):
    args = [str(SPRIG), "run"]
    if entry:
        args.append(entry)
    proc = subprocess.Popen(args, cwd=project, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, encoding="utf-8")
    lines = queue.Queue()
    captured = []

    def reader():
        for line in proc.stdout:
            captured.append(line)
            lines.put(line)
        lines.put(None)

    thread = threading.Thread(target=reader, daemon=True)
    thread.start()
    try:
        # The application reports its bound ephemeral port only after start().
        while True:
            try:
                line = lines.get(timeout=30)
            except queue.Empty:
                raise AssertionError("server readiness timeout: " + "".join(captured))
            assert line is not None, "server exited: " + "".join(captured)
            if line.startswith("PORT="):
                yield "http://127.0.0.1:" + str(int(line.removeprefix("PORT=")))
                break
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait(timeout=10)
        proc.stdout.close()
        thread.join(timeout=2)


def request(base, path, method="GET", body=None, headers=None):
    data = body.encode("utf-8") if isinstance(body, str) else body
    req = urllib.request.Request(base + path, data=data, headers=headers or {}, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, response.headers, response.read().decode("utf-8")
