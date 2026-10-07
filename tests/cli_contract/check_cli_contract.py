#!/usr/bin/env python3
"""Adversarial command ownership, arity, and child exit contract checks."""
import os
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")


def invoke(*args, cwd=ROOT):
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=cwd,
                          text=True, capture_output=True)


SLOW_EXIT = """import java.lang.InterruptedException as InterruptedException
import java.lang.Runtime as Runtime
import java.lang.Thread as Thread
import java.net.ServerSocket as ServerSocket
import java.util.concurrent.CountDownLatch as CountDownLatch

func linger() -> Unit:
    try:
        Thread.sleep(1500)
    catch problem: InterruptedException:
        return

func wait_forever() -> Unit:
    try:
        CountDownLatch(1).await()
    catch problem: InterruptedException:
        return

let socket = ServerSocket(0)
let runtime = Runtime.getRuntime()
if runtime != null:
    runtime.addShutdownHook(Thread(fn() => linger()))
print("PORT=" + socket.getLocalPort())
wait_forever()
"""


def terminating_run_waits_for_the_program(directory):
    """A terminated `sprig run` exits only after its program has: a program that
    takes 1.5 s to shut down still holds its port until then, and the CLI used to
    exit first and leave it behind. Signals and shutdown hooks are POSIX here."""
    import socket
    source = directory / "slow_exit.spr"
    source.write_text(SLOW_EXIT, encoding="utf-8")
    proc = subprocess.Popen([str(SPRIG), "run", str(source)], cwd=directory, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    port = None
    for line in proc.stdout:
        if line.startswith("PORT="):
            port = int(line.strip().removeprefix("PORT="))
            break
    assert port is not None, "the program never reported its port"
    proc.terminate()
    proc.wait(timeout=30)
    proc.stdout.close()
    with socket.socket() as probe:
        assert probe.connect_ex(("127.0.0.1", port)) != 0, "the program outlived the terminated CLI"


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
            ("wrap-foreign-option", ["check", source, "--force", "--json"]),
            ("wrap-missing-out", ["wrap", "java.lang.String", "--json"]),
            ("wrap-missing-class", ["wrap", "--out", "wrapped.spr", "--json"]),
            ("wrap-extra-positional", ["wrap", "java.lang.String", "extra", "--out", "wrapped.spr", "--json"]),
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
        wrapped = invoke("wrap", "java.time.LocalDate", "--out", directory / "local_date.spr", "--json",
                         cwd=directory)
        wrapped_json = json.loads(wrapped.stdout)
        assert wrapped.returncode == 0 and wrapped_json["inputClass"] == "java.time.LocalDate" \
            and (directory / "local_date.spr").is_file()
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
        assert erased and all(m["usableFromSprig"] and m["signatureSupported"]
                              and m["interopLevel"] == "adaptable"
                              and m["adaptation"]["kind"] == "collection-adapter"
                              and "explicit-type-arguments-required" in m["interopReasonCodes"]
                              for m in erased)
        files = json.loads(invoke("api", "java.nio.file.Files", "--member", "readAllBytes", "--json",
                                  cwd=directory).stdout)
        arrays = files["staticMethods"]
        assert arrays and all(m["signatureSupported"] and m["interopLevel"] == "opaque-array"
                              and m["adaptation"]["kind"] == "byte-array"
                              for m in arrays)
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
                    assert "SPR-PROGRAM-EXIT" not in result.stderr and "error(s)" not in result.stderr

        process_exit = directory / "process_exit.spr"
        process_exit.write_text('import "@std/process.spr" as process\n'
                                'process.print_error("usage: x FILE")\nprocess.exit(2)\n', encoding="utf-8")
        human_exit = invoke("run", process_exit, cwd=directory)
        assert human_exit.returncode == 2 and human_exit.stdout == "" \
            and human_exit.stderr == "usage: x FILE\n", (human_exit.returncode, human_exit.stdout, human_exit.stderr)
        json_exit = invoke("run", process_exit, "--json", cwd=directory)
        json_exit_payload = json.loads(json_exit.stdout)
        assert json_exit.returncode == 2 and json_exit_payload["exitCode"] == 2 \
            and json_exit_payload["diagnostics"][0]["code"] == "SPR-PROGRAM-EXIT" \
            and json_exit_payload["diagnostics"][0]["data"]["programExitCode"] == 2, json_exit_payload

        nested_main = directory / "nested_main.spr"
        nested_main.write_text('func main() -> Unit:\n'
                               '    print("hello from main")\n\n'
                               'try:\n'
                               '    main()\n'
                               'catch problem: Error:\n'
                               '    print(problem)\n', encoding="utf-8")
        nested_main_run = invoke("run", nested_main, cwd=directory)
        assert nested_main_run.returncode == 0 and nested_main_run.stdout == "hello from main\n" \
            and nested_main_run.stderr == "", (nested_main_run.returncode,
                                                 nested_main_run.stdout, nested_main_run.stderr)

        conditional_main = directory / "conditional_main.spr"
        conditional_main.write_text('func main() -> Unit:\n'
                                    '    print("hello from main")\n\n'
                                    'if true:\n'
                                    '    main()\n', encoding="utf-8")
        conditional_main_run = invoke("run", conditional_main, cwd=directory)
        assert conditional_main_run.returncode == 0 \
            and conditional_main_run.stdout == "hello from main\n" \
            and conditional_main_run.stderr == "", (conditional_main_run.returncode,
                                                       conditional_main_run.stdout,
                                                       conditional_main_run.stderr)

        uncalled_main = directory / "uncalled_main.spr"
        uncalled_main.write_text('func main() -> Unit:\n    print("hello")\n', encoding="utf-8")
        uncalled_main_run = invoke("run", uncalled_main, cwd=directory)
        assert uncalled_main_run.returncode == 0 and uncalled_main_run.stdout == "" \
            and "nothing was printed" in uncalled_main_run.stderr, (uncalled_main_run.returncode,
                                                                     uncalled_main_run.stdout,
                                                                     uncalled_main_run.stderr)

        printed_uncalled_main = directory / "printed_uncalled_main.spr"
        printed_uncalled_main.write_text('func main() -> Unit:\n'
                                         '    print("main")\n\n'
                                         'print("top level")\n', encoding="utf-8")
        printed_uncalled_main_run = invoke("run", printed_uncalled_main, cwd=directory)
        assert printed_uncalled_main_run.returncode == 0 \
            and printed_uncalled_main_run.stdout == "top level\n" \
            and printed_uncalled_main_run.stderr == "", (printed_uncalled_main_run.returncode,
                                                           printed_uncalled_main_run.stdout,
                                                           printed_uncalled_main_run.stderr)
        if os.name != "nt":
            terminating_run_waits_for_the_program(directory)
        print(f"CLI contract: {len(invalid)} rejected-option cases and end-to-end contracts passed")


if __name__ == "__main__":
    main()
