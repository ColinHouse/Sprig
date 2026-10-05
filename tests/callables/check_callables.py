#!/usr/bin/env python3
"""Independent source type/codegen and concrete Sprig-owned JVM callable ABI checks."""
import json, os, subprocess, tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
CLI=ROOT/"bin"/("sprig.cmd" if os.name=="nt" else "sprig")
HERE=Path(__file__).resolve().parent

def run(*args):
    return subprocess.run([str(CLI),*map(str,args)],cwd=ROOT,capture_output=True,text=True,encoding="utf-8",timeout=60)

def main():
    passed=0
    def expect(ok, detail):
        nonlocal passed
        if not ok: raise AssertionError(detail)
        passed+=1
    with tempfile.TemporaryDirectory(prefix="sprig-callables-") as tmp:
        cp=Path(tmp)/"classes";cp.mkdir()
        p=subprocess.run(["javac","--release","17","-cp",str(ROOT/"build/sprig-compiler.jar"),"-d",str(cp),str(HERE/"CallableFixture.java")],capture_output=True,text=True,errors="replace")
        expect(p.returncode==0,p.stderr)
        for name in ("types","bridge"):
            p=run("run",HERE/(name+".spr"),"--classpath",cp)
            expect(p.returncode==0 and p.stdout==(HERE/(name+".out")).read_text(),p.stdout+p.stderr)
        cases={
         "wrong-parameter": ('let f: fn(Int) -> Int = fn(s: String) => 1',"SPR-TYPE-ASSIGN"),
         "wrong-result": ('let f: fn(Int) -> Int = fn(x: Int) => "bad"',"SPR-TYPE-ASSIGN"),
         "arity-type": ('let f: fn(Int,Int,Int,Int) -> Int = fn(x: Int) => x',"SPR-TYPE-FUNCTION-ARITY"),
         "arity-lambda": ('let f = fn(a: Int,b: Int,c: Int,d: Int) => a',"SPR-TYPE-FUNCTION-ARITY"),
         "unit-parameter": ('let f: fn(Unit) -> Int = fn(x: Int) => x',"SPR-TYPE-UNIT"),
         "nullable-call": ('let f: (fn() -> Int)? = null\nprint(f())',"SPR-TYPE-NULLABLE"),
         "wrong-call": ('let f: fn(Int) -> Int = fn(x: Int) => x\nprint(f())',"SPR-CALL-ARITY"),
         "invariance": ('let f: fn(Int32) -> Int = fn(x: Int) => x',"SPR-TYPE-ASSIGN"),
         "jvm-arity": ('Fixture.zero(fn(x: Int) => x)',"SPR-JVM-MEMBER"),
         "jvm-parameter": ('Fixture.one(fn(x: String) => x)',"SPR-JVM-MEMBER"),
         "jvm-result": ('Fixture.one(fn(x: Int) => x)',"SPR-JVM-MEMBER"),
         "jvm-null": ('let f: (fn() -> Int)? = null\nFixture.zero(f)',"SPR-TYPE-NULLABLE"),
         "jvm-null-result": ('let s: String? = null\nFixture.one(fn(x: Int) => s)',"SPR-JVM-MEMBER"),
         "jvm-sam": ('Fixture.sam(fn(x: Int) => x.toString())',"SPR-JVM-MEMBER"),
         "jvm-raw": ('Fixture.raw(fn(x: Int) => x)',"SPR-JVM-MEMBER"),
         "jvm-adapter-slot": ('Fixture.shortSlot(fn(x: Int32) => x)',"SPR-JVM-MEMBER"),
         "jvm-wildcard": ('Fixture.wildcard(fn(x: Int) => x.toString())',"SPR-JVM-MEMBER"),
         "jvm-unresolved": ('Fixture.unresolved(fn(x: Int) => x)',"SPR-JVM-MEMBER"),
         "jvm-ambiguity": ('Fixture.ambiguous(fn() => 1, "x")',"SPR-JVM-AMBIGUOUS"),
        }
        for name,(source,code) in cases.items():
            file=Path(tmp)/(name+".spr");file.write_text("import fixture.CallableFixture as Fixture\n"+source+"\n")
            p=run("check",file,"--classpath",cp,"--json")
            data=json.loads(p.stdout)
            codes={d["code"] for d in data["diagnostics"]}
            expect(p.returncode!=0 and code in codes,(name,p.stdout,p.stderr))
        for name, source, message in (
            ("null-result", "let f = Fixture.badResult()\nif f != null:\n    print(f(1))\n", "non-null callable result"),
            ("null-input", "print(Fixture.nullInput(fn(s: String) => s))\n", "non-null callable argument"),
        ):
            file=Path(tmp)/(name+".spr");file.write_text("import fixture.CallableFixture as Fixture\n"+source)
            p=run("run",file,"--classpath",cp)
            expect(p.returncode!=0 and message in p.stderr and "SPR-JVM-COMPILE" not in p.stderr,p.stdout+p.stderr)
        p=run("api","fixture.CallableFixture","--classpath",cp,"--json")
        data=json.loads(p.stdout)
        expect(p.returncode==0 and 'fn(Int) -> String' in p.stdout,p.stdout+p.stderr)
    print(f"callables: {passed} checks passed (source semantics, generated Java/JVM, reflection metadata)")
    return 0
if __name__=="__main__": raise SystemExit(main())
