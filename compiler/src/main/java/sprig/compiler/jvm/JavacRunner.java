package sprig.compiler.jvm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;

/** Invokes javac in-process and translates Java errors back to Sprig spans. */
public final class JavacRunner {
    private JavacRunner() {
    }

    public static boolean compile(Path classesDir, List<Path> sources, Diagnostics diagnostics,
                                  Map<Path, Map<Integer, Span>> lineMaps, Map<Path, String> uriByPath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            diagnostics.error(Codes.JVM_INTERNAL, Phase.JVM,
                    "No system Java compiler found; run with a JDK (not a JRE)", null, null);
            return false;
        }
        try {
            Files.createDirectories(classesDir);
        } catch (IOException e) {
            diagnostics.error(Codes.JVM_INTERNAL, Phase.JVM,
                    "Cannot create classes directory: " + e.getMessage(), null, null);
            return false;
        }
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null,
                StandardCharsets.UTF_8)) {
            DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
            List<String> options = new ArrayList<>(List.of(
                    "-d", classesDir.toString(), "-encoding", "UTF-8", "-proc:none", "-g"));
            options.add("-classpath");
            options.add(JvmClasspath.entries().isEmpty() ? classesDir.toString() : JvmClasspath.forProcess());
            boolean ok = compiler.getTask(null, fileManager, collector, options, null,
                    fileManager.getJavaFileObjectsFromPaths(sources)).call();
            for (javax.tools.Diagnostic<? extends JavaFileObject> javaDiagnostic : collector.getDiagnostics()) {
                if (javaDiagnostic.getKind() != javax.tools.Diagnostic.Kind.ERROR) {
                    continue;
                }
                String generatedFile = javaDiagnostic.getSource() == null ? null
                        : javaDiagnostic.getSource().getName();
                Path path = generatedFile == null ? null : Path.of(generatedFile);
                Span span = mapBack(path, javaDiagnostic.getLineNumber(), lineMaps);
                String uri = path != null && uriByPath.containsKey(path) ? uriByPath.get(path) : null;
                String message = javaDiagnostic.getMessage(null);
                diagnostics.add(Diagnostic.error(Codes.JVM_COMPILE, Phase.JVM,
                        "Java compiler: " + message, uri, span)
                        .withHint(hintFor(message)));
            }
            return ok;
        } catch (IOException e) {
            diagnostics.error(Codes.JVM_INTERNAL, Phase.JVM,
                    "javac failed: " + e.getMessage(), null, null);
            return false;
        }
    }

    /** Maps a 1-based generated-Java line back to the nearest recorded Sprig span. */
    private static String hintFor(String javacMessage) {
        if (javacMessage != null && (javacMessage.contains("code too large")
                || javacMessage.contains("too many constants"))) {
            return "The generated Java exceeds a JVM class-file limit (64 KB of bytecode per method, "
                    + "65,535 constants per class). Load large literal data from a file at runtime, "
                    + "or split the code into smaller functions or modules.";
        }
        return "This reports generated-code rejection; it may indicate a compiler bug or an interop mismatch.";
    }

    public static Span mapBack(Path generatedFile, long javaLine,
                               Map<Path, Map<Integer, Span>> lineMaps) {
        if (generatedFile == null || javaLine < 0) {
            return null;
        }
        Map<Integer, Span> map = lineMaps.get(generatedFile);
        if (map == null) {
            return null;
        }
        Span best = null;
        int bestLine = -1;
        for (Map.Entry<Integer, Span> entry : map.entrySet()) {
            if (entry.getKey() <= javaLine && entry.getKey() > bestLine) {
                bestLine = entry.getKey();
                best = entry.getValue();
            }
        }
        return best;
    }
}
