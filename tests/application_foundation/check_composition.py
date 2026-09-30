#!/usr/bin/env python3
"""Compose HTTP, JSON, codec and standard file/time/text APIs locally."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import os
import subprocess
import tempfile
import threading

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
EXAMPLE = ROOT / "examples" / "application_foundation"
BODY = '{"label":"sprig \\u2713","receivedAt":"1969-12-31T23:59:59.999Z"}'.encode("utf-8")
REQUESTS = 0


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def do_GET(self):
        global REQUESTS
        if self.path != "/message":
            self.send_response(404)
            self.end_headers()
            return
        REQUESTS += 1
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(BODY)))
        self.end_headers()
        self.wfile.write(BODY)


def run(cwd, *args, env):
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=cwd, env=env,
                          text=True, encoding="utf-8", capture_output=True, timeout=30)


def main():
    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    with tempfile.TemporaryDirectory(prefix="sprig-foundation-") as temp:
        root = Path(temp)
        home = root / "home"
        home.mkdir()
        env = dict(os.environ, HOME=str(home), JAVA_TOOL_OPTIONS="-Duser.home=" + str(home))
        output = root / "cache" / "received.txt"
        try:
            resolved = run(EXAMPLE, "resolve", env=env)
            assert resolved.returncode == 0, (resolved.stdout, resolved.stderr)
            url = f"http://127.0.0.1:{server.server_port}/message"
            result = run(EXAMPLE, "run", "--", url, output, env=env)
            expected = "sprig \u2713 | 1969-12-31T23:59:59.999Z"
            assert result.returncode == 0, (result.stdout, result.stderr)
            assert result.stdout == expected + "\n", repr(result.stdout)
            assert output.read_text(encoding="utf-8") == expected
            assert REQUESTS == 1, REQUESTS
            print("application foundation: HTTP -> JSON codec -> UTC -> atomic UTF-8 file -> text join passed")
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)


if __name__ == "__main__":
    main()
