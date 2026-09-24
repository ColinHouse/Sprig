import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import sprig.compiler.diag.JsonWriter;
import sprig.compiler.jvm.JavacRunner;
import sprig.compiler.diag.Diagnostics;

/** Exercises a real javac failure whose source has no Sprig URI mapping. */
public final class JavacJsonProbe {
    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("sprig-javac-json-");
        Path source = work.resolve("Broken.java");
        Files.writeString(source, "class Broken { void f() { int x = ; } }\n");
        Diagnostics diagnostics = new Diagnostics();
        boolean ok = JavacRunner.compile(work.resolve("classes"), List.of(source), diagnostics,
                Map.of(), Map.of());
        System.out.print(JsonWriter.result(diagnostics.all(), null, "build", ok ? 0 : 1, null));
    }
}
