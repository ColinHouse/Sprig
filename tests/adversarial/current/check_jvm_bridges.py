#!/usr/bin/env python3
"""Java source methods, compiler bridges, and public inheritance boundaries."""
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")

JAVA = {
    "Bridge.java": '''package audit;
public class Bridge extends Base<String> {
 public String value() { return "bridge"; }
 public static String number(long x) { return "long"; }
 public static String number(int x) { return "int"; }
 public static Character character() { return '\\u03bb'; }
 public static Integer nullable() { return null; }
 public static String boxed(Long x) { return "boxed"; }
 public static String characterArg(char x) { return String.valueOf(x); }
}
class Base<T> { public T value() { return null; } }
''',
    "Sink.java": '''package audit;
public class Sink extends GenericSink<String> {
 public String echo(String input) { return input; }
}
class GenericSink<T> {
 public T echo(T input) throws java.io.IOException { return input; }
}
''',
    "Access.java": '''package audit;
public class Access extends Hidden<Object> {
 public String echo(String input) { return "string"; }
}
class Hidden<T> {
 public T echo(T input) { return input; }
 public String inherited() { return "inherited"; }
}
''',
    "InterfaceSink.java": '''package audit;
public class InterfaceSink implements Middle<String> {
 public String echo(String value) { return value; }
}
interface Middle<T> extends Echo<T> {}
interface Echo<T> { T echo(T value) throws java.io.IOException; }
''',
    "Oracle.java": '''package audit;
public class Oracle {
 public static void main(String[] args) throws Exception {
  System.out.println(new Bridge().value().length());
  System.out.println(Bridge.number(1L));
  System.out.println(Bridge.number(1));
  System.out.println(Bridge.boxed(1L));
  System.out.println(Bridge.character());
  System.out.println(Bridge.nullable());
  System.out.println(Bridge.characterArg('x'));
  System.out.println(new Sink().echo("echo"));
  System.out.println(new Access().inherited());
  System.out.println(new Access().echo(7L));
  System.out.println(new InterfaceSink().echo("interface"));
 }
}
''',
    "DefaultPkg.java": '''public class DefaultPkg {
 public static String ping() { return "unnamed"; }
}
''',
}

SOURCE = '''import audit.Bridge as Bridge
import audit.Sink as Sink
import audit.Access as Access
import audit.InterfaceSink as InterfaceSink
let b = Bridge()
let s = b.value()
if s != null:
    print(s.length())
print(Bridge.number(1))
let small: Int32 = 1
print(Bridge.number(small))
print(Bridge.boxed(1))
print(Bridge.character())
print(Bridge.nullable())
print(Bridge.characterArg("x"))
func echo() -> String?:
    return Sink().echo("echo")
print(echo())
print(Access().inherited())
print(Access().echo(7))
func interfaceEcho() -> String?:
    return InterfaceSink().echo("interface")
print(interfaceEcho())
'''
EXPECTED = "6\nlong\nint\nboxed\nλ\nnull\nx\necho\ninherited\n7\ninterface\n"


def main():
    failures = []
    checks = 0

    def record(name, ok, detail):
        nonlocal checks
        checks += 1
        print(f"{'PASS' if ok else 'FAIL'} {name}")
        if not ok:
            failures.append(f"{name}: {detail}")

    with tempfile.TemporaryDirectory(prefix="sprig-jvm-bridges-") as temp:
        folder = Path(temp)
        for name, source in JAVA.items():
            (folder / name).write_text(source)
        compile_java = subprocess.run(["javac", "-d", temp, *map(str, folder.glob("*.java"))],
                                      capture_output=True, text=True, timeout=60)
        record("javac-fixture", compile_java.returncode == 0, compile_java.stderr)
        if compile_java.returncode != 0:
            return 1
        oracle = subprocess.run(["java", "-cp", temp, "audit.Oracle"], capture_output=True,
                                text=True, timeout=60)
        record("java-oracle", oracle.returncode == 0 and oracle.stdout == EXPECTED,
               oracle.stdout + oracle.stderr)
        source = folder / "main.spr"
        source.write_text(SOURCE)
        for command in ("check", "run"):
            result = subprocess.run([str(SPRIG), command, str(source), "--classpath", temp],
                                    capture_output=True, text=True, timeout=60)
            record(f"source-methods/{command}", result.returncode == 0
                   and (command == "check" or result.stdout == EXPECTED),
                   result.stdout + result.stderr)
        bad_java = folder / "Bad.java"
        bad_java.write_text('class Bad { Object value() throws Exception { return new audit.Sink().echo(1L); } }')
        rejected_java = subprocess.run(["javac", "-cp", temp, str(bad_java)],
                                       capture_output=True, text=True, timeout=60)
        record("javac-erased-bridge-rejected", rejected_java.returncode != 0, rejected_java.stderr)
        for owner in ("Sink", "InterfaceSink"):
            source.write_text(f'import audit.{owner} as Target\nprint(Target().echo(1))\n')
            rejected = subprocess.run([str(SPRIG), "check", str(source), "--classpath", temp, "--json"],
                                      capture_output=True, text=True, timeout=60)
            try:
                codes = [d["code"] for d in json.loads(rejected.stdout)["diagnostics"]]
            except (ValueError, KeyError, TypeError):
                codes = []
            record(f"{owner}/erased-bridge-rejected-before-javac", rejected.returncode == 1
                   and codes == ["SPR-JVM-MEMBER"], rejected.stdout + rejected.stderr)
        source.write_text('import DefaultPkg as Target\nprint(Target.ping())\n')
        unnamed = subprocess.run([str(SPRIG), "check", str(source), "--classpath", temp, "--json"],
                                 capture_output=True, text=True, timeout=60)
        try:
            codes = [d["code"] for d in json.loads(unnamed.stdout)["diagnostics"]]
        except (ValueError, KeyError, TypeError):
            codes = []
        record("unnamed-package-rejected-before-javac", unnamed.returncode == 1
               and codes == ["SPR-JVM-CLASS"], unnamed.stdout + unnamed.stderr)
    for failure in failures:
        print(failure)
    print(f"JVM bridges: {checks} checks, {len(failures)} failures")
    return bool(failures)


if __name__ == "__main__":
    raise SystemExit(main())
