#!/usr/bin/env python3
"""Adversarial command ownership, arity, and child exit contract checks."""
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / "sprig"


def invoke(*args, cwd=ROOT):
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=cwd,
                          text=True, capture_output=True)


def main():
    with tempfile.TemporaryDirectory(prefix="sprig-cli-contract-") as temp:
        directory = Path(temp)
        source = directory / "ok.spr"
        source.write_text('print("ok")\n', encoding="utf-8")
        invalid = [
            ("version-extra", ["version", "extra", "--json"]),
            ("codes-extra", ["codes", "extra", "--json"]),
            ("explain-foreign-option", ["explain", "SPR-TYPE-NULLABLE", "--keep", "--json"]),
            ("api-foreign-option", ["api", "java.time.LocalDate", "--keep", "--json"]),
            ("doctor-foreign-option", ["doctor", "--syntax-only", "--json"]),
            ("capabilities-classpath", ["capabilities", "--classpath", "foo.jar", "--json"]),
            ("doctor-extra-positional", ["doctor", "foo", "--json"]),
            ("capabilities-extra-positional", ["capabilities", "foo", "--json"]),
            ("api-extra-positional", ["api", "java.time.LocalDate", "extra", "--json"]),
            ("check-foreign-option", ["check", source, "--keep", "--json"]),
            ("build-foreign-option", ["build", source, "--syntax-only", "--json"]),
            ("run-foreign-option", ["run", source, "--syntax-only", "--json"]),
            ("run-argument-needs-separator", ["run", source, "arg", "--json"]),
            ("codes-foreign-option", ["codes", "--keep", "--json"]),
            ("missing-classpath-value", ["check", "--classpath", "--json"]),
            ("missing-source-foreign-option", ["check", "--keep", "--json"]),
            ("missing-classpath-with-source", ["check", source, "--classpath", "--json"]),
            ("missing-build-dir-value", ["build", source, "-d", "--json"]),
            ("missing-api-member-value", ["api", "java.time.LocalDate", "--member", "--json"]),
        ]
        for name, args in invalid:
            result = invoke(*args, cwd=directory)
            try:
                payload = json.loads(result.stdout)
            except json.JSONDecodeError as exc:
                raise AssertionError(f"{name}: expected JSON, got {result.stdout!r} {result.stderr!r}") from exc
            codes = [d["code"] for d in payload.get("diagnostics", [])]
            assert result.returncode == 2 and payload.get("exitCode") == 2 and "SPR-CLI-OPTION" in codes, (
                name, result.returncode, payload)
        valid = invoke("check", source, "--syntax-only", "--json", cwd=directory)
        assert valid.returncode == 0 and json.loads(valid.stdout)["diagnostics"] == []
        assert invoke("codes", "--json", cwd=directory).returncode == 0
        app_json = invoke("run", source, "--", "--json", cwd=directory)
        assert app_json.returncode == 0 and app_json.stdout == "ok\n" and app_json.stderr == ""
        build_missing = invoke("build", source, "-d", cwd=directory)
        assert build_missing.returncode == 2 and not (directory / "sprig-build").exists()
        filtered = invoke("api", "java.time.LocalDate", "--member", "of", "--json", cwd=directory)
        metadata = json.loads(filtered.stdout)
        assert filtered.returncode == 0 and metadata["memberFilter"] == "of"
        assert metadata["staticMethods"] and all(m["name"] == "of" for m in metadata["staticMethods"])
        absent = invoke("api", "java.time.LocalDate", "--member", "noSuchMethod", "--json", cwd=directory)
        absent_json = json.loads(absent.stdout)
        assert absent.returncode == 1 and absent_json["diagnostics"][0]["code"] == "SPR-JVM-MEMBER"
        collections = json.loads(invoke("api", "java.util.Collections", "--member", "emptyList", "--json",
                                        cwd=directory).stdout)
        erased = collections["staticMethods"]
        assert erased and all(m["usableFromSprig"] and m["interopLevel"] == "erased-generic"
                              and m["signatureSupported"] for m in erased)
        files = json.loads(invoke("api", "java.nio.file.Files", "--member", "readAllBytes", "--json",
                                  cwd=directory).stdout)
        unsupported = files["staticMethods"]
        assert unsupported and all(not m["signatureSupported"] and m["interopLevel"] == "unsupported"
                                   for m in unsupported)
        string_builder = json.loads(invoke("api", "java.lang.StringBuilder", "--member", "append", "--json",
                                            cwd=directory).stdout)
        optional = json.loads(invoke("api", "java.util.Optional", "--member", "of", "--json",
                                     cwd=directory).stdout)
        java_list = json.loads(invoke("api", "java.util.List", "--member", "get", "--json",
                                      cwd=directory).stdout)
        checked_api = json.loads(invoke("api", "java.nio.file.Files", "--member", "writeString", "--json",
                                        cwd=directory).stdout)
        assert string_builder["instanceMethods"] and optional["staticMethods"]
        assert java_list["instanceMethods"]
        assert any("java.io.IOException" in m["checkedExceptions"]
                   for m in checked_api["staticMethods"])
        function_help = json.loads(invoke("help", "functions", "--json", cwd=directory).stdout)
        assert function_help["examples"] and all(Path(ROOT / path).is_file()
                                                   for path in function_help["examples"])
        assert all("main" not in line for line in function_help["syntax"])

        exit_source = directory / "exit.spr"
        exit_template = "import java.lang.System as System\nSystem.exit({code})\n"
        for code in (0, 1, 7):
            exit_source.write_text(exit_template.format(code=code), encoding="utf-8")
            for json_mode in (False, True):
                args = ["run", exit_source] + (["--json"] if json_mode else [])
                result = invoke(*args, cwd=directory)
                assert result.returncode == code, (code, json_mode, result.returncode, result.stderr)
                if json_mode:
                    payload = json.loads(result.stdout)
                    diagnostics = payload["diagnostics"]
                    if code == 0:
                        assert payload["exitCode"] == 0 and diagnostics == []
                    else:
                        assert payload["exitCode"] == code and len(diagnostics) == 1
                        assert diagnostics[0]["code"] == "SPR-PROGRAM-EXIT"
                elif code:
                    assert "SPR-PROGRAM-EXIT" in result.stderr and "status" in result.stderr
        print(f"CLI contract: {len(invalid)} rejected-option cases and end-to-end contracts passed")


if __name__ == "__main__":
    main()
