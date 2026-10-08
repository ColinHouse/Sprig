#!/usr/bin/env python3
"""Real local HTTP and structural OpenAPI evidence, without CDN access."""
import json
from pathlib import Path
import shutil
import tempfile

from http_support import ROOT, command, request, server


def main():
    with tempfile.TemporaryDirectory(prefix="sprig-web-") as directory:
        root = Path(directory)
        project = root / "examples/mini_web"
        shutil.copytree(ROOT / "examples/mini_web", project, ignore=shutil.ignore_patterns("sprig.lock", "sprig-build"))
        shutil.copytree(ROOT / "libraries/sprig-web", root / "libraries/sprig-web", ignore=shutil.ignore_patterns("sprig.lock", "sprig-build"))
        command(project, "resolve")
        shutil.copy(ROOT / "tests/web/contract.spr", project / "src/contract.spr")
        evidence = command(project, "run", "src/contract.spr").splitlines()
        assert evidence[:9] == ["1", "0", "true", "true", "true", "true", "true", "true", "true"], evidence
        assert evidence[9:11] == ["PUT patch", "3"], evidence
        assert json.loads(evidence[11]) == {"type":"array", "items":{"type":"integer", "format":"int64"}}
        assert json.loads(evidence[12]) == {"type":"object", "properties":{"enabled":{"type":"boolean"}}}
        assert evidence[13:15] == ["cannot write the response as JSON: JSON at code point offset 0: expected number", "1"], evidence
        # A hand-built Request: query() looks up query_values and never decodes raw's query.
        assert evidence[15:] == ["null", "sprig 你好", "true", "null"], evidence
        # Signatures say what can fail: Request.json and json_response throw Error,
        # and a handler may let such an Error escape to the server. Request.query
        # cannot fail: it reads query_values, which the server decodes once.
        api = json.loads(command(project, "api", "@web/web.spr", "--json"))
        declarations = {d["name"]: d for d in api["declarations"]}
        request_methods = {m["name"]: m for m in declarations["Request"]["methods"]}
        assert request_methods["json"].get("throws") == ["Error"], request_methods["json"]
        assert request_methods["query"].get("throws") is None, request_methods["query"]
        request_fields = {f["name"]: f for f in declarations["Request"]["fields"]}
        assert request_fields["query_values"]["type"] == "Map[String, String]", request_fields
        assert request_fields["query_values"]["required"] is False, request_fields
        assert declarations["json_response"].get("throws") == ["Error"], declarations["json_response"]
        assert declarations["text"].get("throws") is None, declarations["text"]
        handler = "fn(Request) -> Response throws Error"
        assert {f["name"]: f["type"] for f in declarations["Route"]["fields"]}["handler"] == handler
        for method in declarations["App"]["methods"]:
            if method["name"] in ("get", "post", "put", "patch", "delete"):
                assert method["parameters"][1]["type"] == handler, method
        shutil.copy(ROOT / "tests/web/registration.spr", project / "src/registration.spr")
        registration = command(project, "run", "src/registration.spr").splitlines()
        assert registration[:5] == ["true", "true", "true", "true", "3"], registration
        registered_paths = json.loads(registration[5])["paths"]
        assert set(registered_paths) == {"/items/{id}", "/nested/{id}/children/{child}"}
        assert set(registered_paths["/items/{id}"]) == {"get", "post"}
        with server(project, entry="src/host_probe.spr") as base:
            assert request(base, "/")[2] == "Hello host"
            assert request(base, "/hello/%E4%BD%A0%E5%A5%BD")[2] == "Hello, 你好"
            assert request(base, "/echo", "POST", "UTF-8 你好")[2] == "UTF-8 你好"
        with server(project) as base:
            status, headers, body = request(base, "/")
            assert (status, body) == (200, "Hello, Sprig!")
            assert headers["X-Sprig"] == "mini-web"
            assert headers["Content-Type"] == "text/plain; charset=utf-8"
            assert headers["Access-Control-Allow-Origin"] == "http://localhost:5173"
            assert request(base, "/hello/%E4%BD%A0%E5%A5%BD?greeting=Hey+there")[2] == "Hey there, 你好!"
            assert request(base, "/hello/a%2Fb?greeting=x%3Dy")[2] == "x=y, a/b!"
            assert request(base, "/hello/name?greeting=")[2] == ", name!"
            assert request(base, "/hello/name?greeting=first&greeting=second")[2] == "first, name!"
            assert request(base, "/hello/name?greeting")[2] == ", name!"
            assert request(base, "/hello/name?gr%65eting=a=b&greeting=c")[2] == "a=b, name!"
            # A malformed query is a 400 even when the handler does not read it, or reads
            # only the first, valid, occurrence of the name.
            assert request(base, "/hello/name?greeting=ok&greeting=%E4")[0] == 400
            assert request(base, "/hello/%FF")[0] == 400
            assert request(base, "/?greeting=%ZZ")[0] == 400
            assert request(base, "/missing")[0] == 404
            status, headers, body = request(base, "/fail")
            assert status == 500 and body == "Internal server error"
            assert headers["Access-Control-Allow-Origin"] == "http://localhost:5173"
            payload = '{"message":"你好","empty":"","zero":0,"false":false,"null":null}'
            status, headers, body = request(base, "/echo", "POST", payload, {"Content-Type": "application/json"})
            assert status == 200 and json.loads(body) == json.loads(payload)
            assert headers["Content-Type"] == "application/json; charset=utf-8"
            assert request(base, "/echo", "POST", '{')[0] == 400
            assert request(base, "/echo", "POST", b'\xff')[0] == 400
            assert request(base, "/echo", "GET")[0] == 404
            status, _, body = request(base, "/delete/42", "DELETE")
            assert status == 204 and body == ""
            status, headers, body = request(base, "/echo", "OPTIONS", headers={"Origin":"http://localhost:5173", "Access-Control-Request-Method":"POST"})
            assert status == 204 and body == ""
            assert "POST" in headers["Access-Control-Allow-Methods"]
            assert headers["Access-Control-Allow-Headers"] == "Content-Type"
            document = json.loads(request(base, "/openapi.json")[2])
            assert document["openapi"] == "3.0.3"
            assert document["info"] == {"title":"Mini Web", "version":"1.0.0"}
            paths = document["paths"]
            assert set(paths) == {"/", "/hello/{name}", "/echo", "/fail", "/delete/{id}"}
            parameters = paths["/hello/{name}"]["get"]["parameters"]
            assert parameters[0] == {"name":"name", "in":"path", "required":True, "schema":{"type":"string"}}
            assert parameters[1] == {"name":"greeting", "in":"query", "required":False, "schema":{"type":"string"}}
            echo = paths["/echo"]["post"]
            schema = echo["requestBody"]["content"]["application/json"]["schema"]
            assert schema == {"type":"object", "properties":{"message":{"type":"string"}}, "required":["message"]}
            assert echo["responses"]["200"]["content"]["application/json"]["schema"] == schema
            status, _, html = request(base, "/docs")
            assert status == 200 and "SwaggerUIBundle" in html and "url:'/openapi.json'" in html
            port = int(base.rsplit(":", 1)[1])
            # Built-in docs are structural HTML only; the test never fetches CDN assets.
        # Terminating the CLI must terminate its persistent child and release the port.
        import socket
        with socket.socket() as probe:
            assert probe.connect_ex(("127.0.0.1", port)) != 0, "orphan HTTP server after CLI termination"
    print("Web passed: real HTTP GET/POST/DELETE, Unicode/query/headers/CORS, 400/404/500 and structural OpenAPI/docs")


if __name__ == "__main__":
    main()
