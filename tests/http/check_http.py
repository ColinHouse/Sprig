#!/usr/bin/env python3
"""Black-box Sprig HTTP contract against a deterministic loopback server."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import socket
import subprocess
import os
import tempfile
import threading
import time

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
LIBRARY = ROOT / "libraries" / "sprig-http"


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def handle_error(self, *_args):
        pass

    def reply(self, status, body=b"", headers=()):
        self.send_response(status)
        for name, value in headers:
            self.send_header(name, value)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if body:
            self.wfile.write(body)

    def do_GET(self):
        if self.path == "/unicode":
            self.reply(200, "雪✓".encode("utf-8"), [("Content-Type", "text/plain; charset=utf-8")])
        elif self.path == "/status":
            self.reply(418, b"still a response")
        elif self.path == "/headers":
            self.reply(200, b"headers", [("X-Repeat", "one"), ("X-Repeat", "two")])
        elif self.path == "/request-header":
            self.reply(200, self.headers.get("X-Request", "missing").encode("utf-8"))
        elif self.path == "/empty":
            self.reply(204)
        elif self.path == "/invalid-utf8":
            self.reply(200, b"\xff")
        elif self.path == "/slow":
            time.sleep(0.35)
            self.reply(200, b"late")
        elif self.path == "/redirect":
            self.reply(302, b"redirect", [("Location", "/unicode")])
        else:
            self.reply(200, b"get")

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        self.reply(200, body, [("X-Echo", self.headers.get("X-Request", "missing"))])

    def do_PATCH(self):
        self.reply(200, b"patch")


SOURCE = '''import "@http/http.spr" as http
import "@std/text.spr" as text
import "@std/process.spr" as process

let base = process.argument(0)
let refused = process.argument(1)
let basic = http.get(base + "/unicode", [], 2000)
print(basic.status == 200)
print(basic.body)
let status = http.get(base + "/status", [], 2000)
print(status.status == 418)
print(status.body)
let custom_headers: List[http.Header] = [http.Header(name="X-Request", value="arrived")]
let header_response = http.get(base + "/request-header", custom_headers, 2000)
print(header_response.body)
let post = http.post_text(base + "/post", "你好 🪴", custom_headers, 2000)
print(post.body)
var post_header = false
for header in post.headers:
    if header.name == "x-echo" and header.value == "arrived":
        post_header = true
print(post_header)
let repeated_response = http.get(base + "/headers", [], 2000)
let repeated: MutableList[String] = []
for header in repeated_response.headers:
    if header.name == "x-repeat":
        repeated.append(header.value)
print(text.join(repeated.toList(), ","))
let empty = http.get(base + "/empty", [], 2000)
print(empty.status == 204)
print("empty=" + empty.body)
let redirect = http.get(base + "/redirect", [], 2000)
print(redirect.status == 302)
print(redirect.body)
let patched = http.send(http.Request(method="PATCH", url=base + "/custom", headers=[], body=null, timeout_ms=2000))
print(patched.status == 200)
print(patched.body)
var invalid_uri = false
try:
    http.get("file:///etc/passwd", [], 2000)
catch problem: Error:
    invalid_uri = problem.message == "HTTP URL must be an absolute http or https URI with a host"
print(invalid_uri)
var malformed_uri = false
try:
    http.get("http://[", [], 2000)
catch problem: Error:
    malformed_uri = problem.message == "Invalid HTTP URI"
print(malformed_uri)
var invalid_utf8 = false
try:
    http.get(base + "/invalid-utf8", [], 2000)
catch problem: Error:
    invalid_utf8 = problem.message == "HTTP response body is not valid UTF-8"
print(invalid_utf8)
var timed_out = false
try:
    http.get(base + "/slow", [], 40)
catch problem: Error:
    timed_out = problem.message == "HTTP request timed out"
print(timed_out)
var invalid_timeout = false
try:
    http.get(base + "/", [], 0)
catch problem: Error:
    invalid_timeout = problem.message == "HTTP timeout_ms must be greater than zero"
print(invalid_timeout)
var https_accepted = false
try:
    http.get("https://127.0.0.1:" + process.argument(2) + "/refused", [], 1000)
catch problem: Error:
    https_accepted = text.starts_with(problem.message, "HTTP transport failure (")
print(https_accepted)
var connection_failed = false
try:
    http.get(refused, [], 1000)
catch problem: Error:
    connection_failed = text.starts_with(problem.message, "HTTP transport failure (")
print(connection_failed)
'''


def run(cwd, *args):
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=cwd,
                          text=True, encoding="utf-8", capture_output=True)


def main():
    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        refused_port = probe.getsockname()[1]
    try:
        with tempfile.TemporaryDirectory(prefix="sprig-http-") as temp:
            project = Path(temp)
            (project / "src").mkdir()
            (project / "sprig.toml").write_text(
                '[project]\nname = "http-consumer"\nversion = "0.1.0"\nlanguage = "0.8"\n\n'
                '[[dependency]]\nname = "http"\npath = "' + str(LIBRARY).replace("\\", "\\\\") + '"\n',
                encoding="utf-8")
            (project / "src/main.spr").write_text(SOURCE, encoding="utf-8")
            resolved = run(project, "resolve")
            assert resolved.returncode == 0, (resolved.stdout, resolved.stderr)
            base = f"http://127.0.0.1:{server.server_port}"
            refused = f"http://127.0.0.1:{refused_port}/refused"
            result = run(project, "run", "src/main.spr", "--", base, refused, str(refused_port))
            assert result.returncode == 0, (result.stdout, result.stderr)
            expected = [
                "true", "雪✓", "true", "still a response", "arrived", "你好 🪴", "true",
                "one,two", "true", "empty=", "true", "redirect", "true", "patch",
                "true", "true", "true", "true", "true", "true", "true",
            ]
            assert result.stdout.splitlines() == expected, repr(result.stdout)
            print("http: 21 local loopback assertions passed through a real Sprig consumer")
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)


if __name__ == "__main__":
    main()
