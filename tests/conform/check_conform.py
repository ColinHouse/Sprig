#!/usr/bin/env python3
"""Foreign JVM conformance: Runnable, framework callbacks, restrictions, boundaries.

Java fixtures stand in for Fabric interfaces; the contract is the same JVM
nominal one: an existing Sprig class implements an imported Java interface
without method generation or adaptation.
"""
from pathlib import Path
import json
import os
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
PACKAGE = "conformfixture"

JAVA = {
    "Initializer.java": """package conformfixture;
public interface Initializer {
    void onInitialize();
}
""",
    "Server.java": """package conformfixture;
public interface Server {
    long id();
}
""",
    "TickCallback.java": """package conformfixture;
public interface TickCallback {
    void onTick(Server server);
}
""",
    "WithDefault.java": """package conformfixture;
public interface WithDefault {
    void required();
    default String helper() { return "help"; }
}
""",
    "Overloaded.java": """package conformfixture;
public interface Overloaded {
    void visit(String value);
    void visit(long value);
}
""",
    "Checked.java": """package conformfixture;
public interface Checked {
    void work() throws java.io.IOException;
}
""",
    "Quiet.java": """package conformfixture;
public interface Quiet {
    void work();
}
""",
    "AbstractThing.java": """package conformfixture;
public abstract class AbstractThing {
    public abstract void go();
}
""",
    "Ordinary.java": """package conformfixture;
public class Ordinary {
    public void go() {}
}
""",
    "Marker.java": """package conformfixture;
public @interface Marker {
}
""",
    "SealedThing.java": """package conformfixture;
public sealed interface SealedThing permits SealedImpl {
}
""",
    "SealedImpl.java": """package conformfixture;
public final class SealedImpl implements SealedThing {
}
""",
    "GenericBox.java": """package conformfixture;
public interface GenericBox<T> {
    void accept(T value);
}
""",
    "ExtendsGeneric.java": """package conformfixture;
public interface ExtendsGeneric extends GenericBox<String> {
}
""",
    "Parent.java": """package conformfixture;
public interface Parent {
    void a();
}
""",
    "Child.java": """package conformfixture;
public interface Child extends Parent {
    void b();
}
""",
    "AbstractF.java": """package conformfixture;
public interface AbstractF { void f(); }
""",
    "DefaultFChild.java": """package conformfixture;
public interface DefaultFChild extends AbstractF { default void f() {} }
""",
    "CovBase.java": """package conformfixture;
public interface CovBase { Object value(); }
""",
    "CovDerived.java": """package conformfixture;
public interface CovDerived extends CovBase { String value(); }
""",
    "ThrowBase.java": """package conformfixture;
public interface ThrowBase { void f() throws java.io.IOException; }
""",
    "ThrowNarrow.java": """package conformfixture;
public interface ThrowNarrow extends ThrowBase { void f(); }
""",
    "ThrowWide.java": """package conformfixture;
public interface ThrowWide { void f() throws Exception; }
""",
    "MixAbstract.java": """package conformfixture;
public interface MixAbstract { void g(); }
""",
    "MixDefault.java": """package conformfixture;
public interface MixDefault { default void g() {} }
""",
    "MixAbstractC.java": """package conformfixture;
public interface MixAbstractC extends MixAbstract, MixDefault { void g(); }
""",
    "MixDefaultC.java": """package conformfixture;
public interface MixDefaultC extends MixAbstract, MixDefault { default void g() {} }
""",
    "ObjectBag.java": """package conformfixture;
public interface ObjectBag {
    boolean equals(Object value);
    int hashCode();
    String toString();
}
""",
    "GenericAccept.java": """package conformfixture;
public interface GenericAccept { <T> void accept(T value); }
""",
    "BoxedShort.java": """package conformfixture;
public interface BoxedShort { void take(Short value); }
""",
    "BoxedCharacter.java": """package conformfixture;
public interface BoxedCharacter { void take(Character value); }
""",
    "Hidden.java": """package conformfixture;
interface Hidden {
    void hidden();
}
""",
    "Support.java": """package conformfixture;
public final class Support {
    public static String runIt(Runnable runnable) { runnable.run(); return "ok"; }
    public static void initialize(Initializer initializer) { initializer.onInitialize(); }
    public static void tick(TickCallback callback, Server server) { callback.onTick(server); }
    public static void tickNull(TickCallback callback) { callback.onTick(null); }
}
""",
}

IMPORTS = """import java.lang.Runnable as Runnable
import java.io.Closeable as Closeable
import java.io.IOException as IOException
import conformfixture.AbstractF as AbstractF
import conformfixture.DefaultFChild as DefaultFChild
import conformfixture.CovBase as CovBase
import conformfixture.CovDerived as CovDerived
import conformfixture.ThrowBase as ThrowBase
import conformfixture.ThrowNarrow as ThrowNarrow
import conformfixture.ThrowWide as ThrowWide
import conformfixture.MixAbstractC as MixAbstractC
import conformfixture.MixDefaultC as MixDefaultC
import conformfixture.ObjectBag as ObjectBag
import conformfixture.GenericAccept as GenericAccept
import conformfixture.BoxedShort as BoxedShort
import conformfixture.BoxedCharacter as BoxedCharacter
import java.lang.Object as Object
import java.lang.Exception as Exception
import conformfixture.Initializer as Initializer
import conformfixture.Server as Server
import conformfixture.TickCallback as TickCallback
import conformfixture.WithDefault as WithDefault
import conformfixture.Overloaded as Overloaded
import conformfixture.Checked as Checked
import conformfixture.Quiet as Quiet
import conformfixture.AbstractThing as AbstractThing
import conformfixture.Ordinary as Ordinary
import conformfixture.Marker as Marker
import conformfixture.SealedThing as SealedThing
import conformfixture.GenericBox as GenericBox
import conformfixture.ExtendsGeneric as ExtendsGeneric
import conformfixture.Parent as Parent
import conformfixture.Child as Child
import conformfixture.Hidden as Hidden
import conformfixture.Support as Support
"""

POSITIVE = {
    "child_default_contract": (IMPORTS + """
class Example:
    pass

conform Example to DefaultFChild

let example = Example()
print("ok")
""", "ok\n"),
    "mixed_default_contract": (IMPORTS + """
class Example:
    pass

conform Example to MixDefaultC

let example = Example()
print("ok")
""", "ok\n"),
    "mixed_abstract_override": (IMPORTS + """
class Example:
    func g() -> Unit:
        pass

conform Example to MixAbstractC

let example = Example()
print("ok")
""", "ok\n"),
    "covariant_return": (IMPORTS + """
class Example:
    func value() -> String:
        return "v"

conform Example to CovDerived

print(Example().value())
""", "v\n"),
    "object_methods_satisfied": (IMPORTS + """
class Example:
    pass

conform Example to ObjectBag

let view: ObjectBag = Example()
print(view.toString() != null)
""", "true\n"),
    "generic_method_erasure": (IMPORTS + """
class Example:
    func accept(value: Object) -> Unit:
        pass

conform Example to GenericAccept

let example = Example()
print("ok")
""", "ok\n"),
    "throws_within_declared": (IMPORTS + """
class Example:
    func f() -> Unit throws IOException:
        pass

conform Example to ThrowWide

let example = Example()
print("ok")
""", "ok\n"),
    "inferred_global_conformance": (IMPORTS + """
class Task:
    func run() -> Unit:
        print("run")

conform Task to Runnable

let handle = Support.runIt(Task())
print(handle)
""", "run\nok\n"),
    "runnable": (IMPORTS + """
class Task:
    func run() -> Unit:
        print("running")

conform Task to Runnable

Support.runIt(Task())
let task: Runnable = Task()
task.run()
""", "running\nrunning\n"),
    "mod_initializer": (IMPORTS + """
class SprigMod:
    func onInitialize() -> Unit:
        print("initialized")

conform SprigMod to Initializer

Support.initialize(SprigMod())
""", "initialized\n"),
    "event_callback": (IMPORTS + """
class ServerImpl:
    func id() -> Int:
        return 7

conform ServerImpl to Server

class Listener:
    let seen: MutableList[Int]
    func onTick(server: Server) -> Unit:
        seen.append(server.id())

conform Listener to TickCallback

let listener = Listener(seen=[])
Support.tick(listener, ServerImpl())
print(listener.seen)
""", "[7]\n"),
    "default_method": (IMPORTS + """
class Impl:
    func required() -> Unit:
        pass

conform Impl to WithDefault

let value: WithDefault = Impl()
print(value.helper())
""", "help\n"),
    "multiple_conformances": (IMPORTS + """
class Worker:
    func run() -> Unit:
        print("work")
    func close() -> Unit:
        print("closed")

conform Worker to Runnable
conform Worker to Closeable

let worker = Worker()
Support.runIt(worker)
worker.close()
""", "work\nclosed\n"),
    "inherited_interface": (IMPORTS + """
class Both:
    func a() -> Unit:
        print("a")
    func b() -> Unit:
        print("b")

conform Both to Child

let parent: Parent = Both()
parent.a()
let child: Child = Both()
child.b()
""", "a\nb\n"),
}

NEGATIVE = {
    "source_value": ("conform notAClass to Runnable\n", "SPR-CONFORM-SOURCE"),
    "source_generic": (IMPORTS + """
generic T:
    class Box:
        let value: T

conform Box to Runnable
""", "SPR-CONFORM-SOURCE"),
    "source_imported_reexport": (IMPORTS + """
import "./conform_dep.spr" as dep
export dep.Dep
conform Dep to Runnable
""", "SPR-CONFORM-SOURCE"),
    "target_sprig_class": (IMPORTS + """
class A:
    pass
class B:
    pass
conform A to B
""", "SPR-CONFORM-TARGET"),
    "target_abstract": (IMPORTS + """
class A:
    func go() -> Unit:
        pass
conform A to AbstractThing
""", "SPR-CONFORM-TARGET"),
    "target_ordinary": (IMPORTS + """
class A:
    func go() -> Unit:
        pass
conform A to Ordinary
""", "SPR-CONFORM-TARGET"),
    "target_annotation": (IMPORTS + """
class A:
    pass
conform A to Marker
""", "SPR-CONFORM-TARGET"),
    "target_generic": (IMPORTS + """
class A:
    func accept(value: String) -> Unit:
        pass
conform A to GenericBox
""", "SPR-CONFORM-TARGET"),
    "target_generic_ancestor": (IMPORTS + """
class A:
    func accept(value: String) -> Unit:
        pass
conform A to ExtendsGeneric
""", "SPR-CONFORM-TARGET"),
    "target_sealed": (IMPORTS + """
class A:
    pass
conform A to SealedThing
""", "SPR-CONFORM-TARGET"),
    "target_non_public": (IMPORTS + """
class A:
    func hidden() -> Unit:
        pass
conform A to Hidden
""", "SPR-CONFORM-TARGET"),
    "covariant_parent_witness": (IMPORTS + """
class Example:
    func value() -> Object:
        return "v"

conform Example to CovDerived
""", "SPR-CONFORM-MEMBER"),
    "throws_narrowed_child": (IMPORTS + """
class Example:
    func f() -> Unit throws IOException:
        pass

conform Example to ThrowNarrow
""", "SPR-CONFORM-EFFECTS"),
    "throws_broader_than_declared": (IMPORTS + """
class Example:
    func f() -> Unit throws Exception:
        pass

conform Example to ThrowBase
""", "SPR-CONFORM-EFFECTS"),
    "boxed_short_parameter": (IMPORTS + """
class Example:
    func take(value: Int) -> Unit:
        pass

conform Example to BoxedShort
""", "SPR-CONFORM-MEMBER"),
    "character_parameter": (IMPORTS + """
class Example:
    func take(value: String) -> Unit:
        pass

conform Example to BoxedCharacter
""", "SPR-CONFORM-MEMBER"),
    "member_missing": (IMPORTS + """
class A:
    pass
conform A to Runnable
""", "SPR-CONFORM-MEMBER"),
    "member_arity": (IMPORTS + """
class A:
    func run(extra: Int) -> Unit:
        pass
conform A to Runnable
""", "SPR-CONFORM-MEMBER"),
    "member_param_shape": (IMPORTS + """
class A:
    func onTick(server: String) -> Unit:
        pass
conform A to TickCallback
""", "SPR-CONFORM-MEMBER"),
    "member_return_shape": (IMPORTS + """
class A:
    func run() -> Int:
        return 1
conform A to Runnable
""", "SPR-CONFORM-MEMBER"),
    "member_nullable_primitive": (IMPORTS + """
class A:
    func onTick(server: Int?) -> Unit:
        pass
conform A to TickCallback
""", "SPR-CONFORM-MEMBER"),
    "overloaded": (IMPORTS + """
class A:
    func visit(value: String) -> Unit:
        pass
conform A to Overloaded
""", "SPR-CONFORM-OVERLOAD"),
    "effects": (IMPORTS + """
class A:
    func work() -> Unit throws IOException:
        pass
conform A to Quiet
""", "SPR-CONFORM-EFFECTS"),
    "nullable_conversion": (IMPORTS + """
class Task:
    func run() -> Unit:
        pass
conform Task to Runnable
let task: Task? = null
let runnable: Runnable = task
""", "SPR-TYPE-NULLABLE"),
    "no_collection_variance": (IMPORTS + """
class Task:
    func run() -> Unit:
        pass
conform Task to Runnable
let tasks: List[Task] = [Task()]
let runnables: List[Runnable] = tasks
""", "SPR-TYPE-ASSIGN"),
    "requires_not_merged": (IMPORTS + """
generic T:
    func f(value: T) -> Bool:
        requires T: Runnable
        return true
print(f[Int](1))
""", "SPR-GENERIC-CONSTRAINT"),
    "no_rename_body": ("conform Task to Runnable:\n    run = tick\n", "SPR-SYNTAX-ERROR"),
}


def run(*args, env=None):
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=ROOT, env=env,
                          capture_output=True, text=True, timeout=120)


def main():
    if not SPRIG.is_file():
        print("Build Sprig before running this suite", file=sys.stderr)
        return 2
    failures = []
    checks = 0

    def record(name, ok, detail=""):
        nonlocal checks
        checks += 1
        print(("PASS " if ok else "FAIL ") + name)
        if not ok:
            failures.append(f"{name}: {detail}")

    with tempfile.TemporaryDirectory(prefix="sprig-conform-") as temp:
        root = Path(temp)
        sources = root / "src" / PACKAGE
        sources.mkdir(parents=True)
        for name, source in JAVA.items():
            (sources / name).write_text(source, encoding="utf-8")
        classes = root / "classes"
        classes.mkdir()
        compile_java = subprocess.run(
            ["javac", "-d", str(classes), *map(str, sources.glob("*.java"))],
            capture_output=True, text=True, errors="replace", timeout=120)
        record("javac-fixtures", compile_java.returncode == 0, compile_java.stderr)
        if compile_java.returncode != 0:
            return 1

        (root / "conform_dep.spr").write_text(
            "class Dep:\n    func run() -> Unit:\n        pass\n", encoding="utf-8")

        for name, (source, expected) in POSITIVE.items():
            path = root / f"positive_{name}.spr"
            path.write_text(source, encoding="utf-8")
            result = subprocess.run(
                [str(SPRIG), "run", str(path), "--classpath", str(classes)],
                cwd=root, capture_output=True, text=True, timeout=120)
            record(f"positive/{name}", result.returncode == 0 and result.stdout == expected,
                   result.stdout + result.stderr)
            checked = run("check", path, "--classpath", classes, "--json")
            record(f"positive/{name}/check", checked.returncode == 0
                   and not json.loads(checked.stdout)["diagnostics"], checked.stdout + checked.stderr)

        for name, (source, code) in NEGATIVE.items():
            path = root / f"negative_{name}.spr"
            if name == "source_imported_reexport":
                path = root / "conform_imported.spr"
            path.write_text(source, encoding="utf-8")
            result = run("check", path, "--classpath", classes, "--json")
            try:
                codes = [d["code"] for d in json.loads(result.stdout)["diagnostics"]]
            except (ValueError, KeyError, TypeError):
                codes = []
            record(f"negative/{name}", result.returncode == 1 and code in codes, codes)

        # The boundary guard turns a Java null into an explicit failure.
        boundary = root / "boundary_null.spr"
        boundary.write_text(IMPORTS + """
class Listener:
    func onTick(server: Server) -> Unit:
        print(server.id())

conform Listener to TickCallback

Support.tickNull(Listener())
""", encoding="utf-8")
        boundary_result = run("run", boundary, "--classpath", classes, "--json")
        try:
            diagnostics = json.loads(boundary_result.stdout)["diagnostics"]
        except (ValueError, KeyError, TypeError):
            diagnostics = []
        record("boundary/null-rejected", boundary_result.returncode == 1
               and any(d["code"] == "SPR-RUNTIME-EXCEPTION"
                       and "foreign boundary" in d["message"] for d in diagnostics),
               boundary_result.stdout + boundary_result.stderr)

        # Emitted Java declares the interface and keeps the entry guard.
        generated = root / "generated"
        emitted = run("build", root / "positive_event_callback.spr", "--classpath", classes,
                      "--emit-java-only", "-d", generated, "--json")
        java_text = ""
        for file in generated.rglob("*.java"):
            java_text += file.read_text(encoding="utf-8")
        record("emit/implements", emitted.returncode == 0
               and "implements conformfixture.TickCallback" in java_text
               and "implements conformfixture.Server" in java_text, java_text[:400])
        record("emit/boundary-guard", "Objects.requireNonNull(server" in java_text, java_text[:400])

    if failures:
        for failure in failures:
            print("FAIL", failure)
        return 1
    print(f"conform: {checks} checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
