package sprig.compiler.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import sprig.compiler.Compilation;
import sprig.compiler.Compiler;
import sprig.compiler.diag.CodeDocs;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.JsonWriter;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;
import sprig.compiler.diag.Version;
import sprig.compiler.gen.JavaGenerator;
import sprig.compiler.jvm.JavaRunner;
import sprig.compiler.jvm.JavacRunner;

/**
 * {@code sprig} command line: check, run, build, explain, version.
 *
 * <pre>
 *   sprig check file.spr [--json] [--syntax-only]
 *   sprig run file.spr [--json] [--keep] [-- args...]
 *   sprig build file.spr [-d outDir]
 *   sprig explain SPR-CODE
 * </pre>
 */
public final class Main {
    public static void main(String[] args) {
        int exit;
        try {
            exit = dispatch(args);
        } catch (IOException e) {
            if (jsonRequested(args)) {
                printFatalJson(args, Codes.JVM_INTERNAL, "I/O error: " + e.getMessage());
            } else System.err.println("sprig: i/o error: " + e.getMessage());
            exit = 2;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (jsonRequested(args)) printFatalJson(args, Codes.JVM_INTERNAL, "Operation interrupted");
            exit = 130;
        } catch (RuntimeException e) {
            if (jsonRequested(args)) {
                printFatalJson(args, Codes.JVM_INTERNAL, "Internal compiler error: " + e);
            } else {
                System.err.println("sprig: internal error: " + e);
                e.printStackTrace();
            }
            exit = 2;
        }
        System.exit(exit);
    }

    private static int dispatch(String[] args) throws IOException, InterruptedException {
        if (args.length == 0 || args[0].equals("help") || args[0].equals("--help") || args[0].equals("-h")) {
            usage(System.out);
            return args.length == 0 ? 2 : 0;
        }
        if (args[0].equals("version") || args[0].equals("--version")) {
            System.out.println(Version.VERSION);
            return 0;
        }
        return switch (args[0]) {
            case "check" -> check(args);
            case "run" -> run(args);
            case "build" -> build(args);
            case "explain" -> explain(args);
            case "codes" -> codes(args);
            default -> {
                System.err.println("sprig: unknown command '" + args[0] + "'");
                usage(System.err);
                yield 2;
            }
        };
    }

    private static void usage(java.io.PrintStream out) {
        out.println("Usage: sprig <command> [options]");
        out.println();
        out.println("  check <file.spr> [--json] [--syntax-only]   parse and type-check");
        out.println("  run   <file.spr> [--json] [--keep] [-- a b] compile and execute on the JVM");
        out.println("  build <file.spr> [-d dir] [--json]          emit Java sources + .class files");
        out.println("  explain <SPR-CODE>                          explain a diagnostic code");
        out.println("  codes [--json]                              list every diagnostic code");
        out.println("  version");
    }

    // ------------------------------------------------------------------

    private static int check(String[] args) throws IOException {
        Options options = Options.parse(args, 1);
        if (options.file == null) {
            System.err.println("sprig check: missing <file.spr>");
            return 2;
        }
        Diagnostics diagnostics = new Diagnostics();
        if (options.syntaxOnly) {
            new Compiler(diagnostics).parseOnly(options.file);
        } else {
            new Compiler(diagnostics).compile(options.file);
        }
        int status = diagnostics.hasErrors() ? 1 : 0;
        report(diagnostics, options.json, "check", status, null);
        return status;
    }

    private static int build(String[] args) throws IOException {
        Options options = Options.parse(args, 1);
        if (options.file == null) {
            System.err.println("sprig build: missing <file.spr>");
            return 2;
        }
        Diagnostics diagnostics = new Diagnostics();
        Compilation compilation = new Compiler(diagnostics).compile(options.file);
        if (diagnostics.hasErrors()) {
            report(diagnostics, options.json, "build", 1, null);
            return 1;
        }
        JavaGenerator.Output output = new JavaGenerator(compilation, diagnostics).generate();
        Path outDir = options.outDir != null ? options.outDir : Path.of("sprig-build");
        Path javaDir = outDir.resolve("java");
        Path classesDir = outDir.resolve("classes");
        deleteRecursively(javaDir);
        deleteRecursively(classesDir);
        writeSources(output, javaDir);
        Map<Path, Map<Integer, Span>> lineMaps = new HashMap<>();
        Map<Path, String> uris = new HashMap<>();
        for (String file : output.sources.keySet()) {
            Path path = javaDir.resolve(file).toAbsolutePath();
            lineMaps.put(path, output.lineMaps.get(file));
            uris.put(path, output.uris.get(file));
        }
        List<Path> sources = new ArrayList<>(listJavaFiles(javaDir));
        sources.addAll(runtimeSources(diagnostics));
        boolean compiled = JavacRunner.compile(classesDir, sources, diagnostics, lineMaps, uris);
        if (!compiled || diagnostics.hasErrors()) {
            report(diagnostics, options.json, "build", 1, null);
            return 1;
        }
        report(diagnostics, options.json, "build", 0, null);
        if (options.json) return 0;
        System.out.println("Built " + options.file + " -> " + outDir.toAbsolutePath());
        System.out.println("  Java sources: " + javaDir.toAbsolutePath());
        System.out.println("  Classes:      " + classesDir.toAbsolutePath());
        System.out.println("  Main class:   " + output.mainClass);
        return 0;
    }

    private static int run(String[] args) throws IOException, InterruptedException {
        Options options = Options.parse(args, 1);
        if (options.file == null) {
            System.err.println("sprig run: missing <file.spr>");
            return 2;
        }
        Diagnostics diagnostics = new Diagnostics();
        Compilation compilation = new Compiler(diagnostics).compile(options.file);
        if (!diagnostics.hasErrors()) {
            JavaGenerator.Output output = new JavaGenerator(compilation, diagnostics).generate();
            Path work = Files.createTempDirectory("sprig-run-");
            try {
                Path javaDir = work.resolve("java");
                Path classesDir = work.resolve("classes");
                writeSources(output, javaDir);
                Map<Path, Map<Integer, Span>> lineMaps = new HashMap<>();
                Map<Path, String> uris = new HashMap<>();
                for (String file : output.sources.keySet()) {
                    Path path = javaDir.resolve(file).toAbsolutePath();
                    lineMaps.put(path, output.lineMaps.get(file));
                    uris.put(path, output.uris.get(file));
                }
                List<Path> sources = new ArrayList<>(listJavaFiles(javaDir));
                sources.addAll(runtimeSources(diagnostics));
                boolean compiled = JavacRunner.compile(classesDir, sources, diagnostics, lineMaps, uris);
                if (!compiled || diagnostics.hasErrors()) {
                    report(diagnostics, options.json, "run", 1, null);
                    return 1;
                }
                JavaRunner.Result result = JavaRunner.run(classesDir, output.mainClass,
                        options.programArgs, work);
                if (!options.json) {
                    System.out.print(result.stdout);
                    System.out.flush();
                }
                if (!options.json && !result.stderr.isEmpty()) {
                    System.err.print(result.stderr);
                }
                if (result.exitCode != 0) {
                    diagnostics.add(runtimeDiagnostic(result.stderr, options.file.toAbsolutePath().toUri().toString()));
                }
                report(diagnostics, options.json, "run", result.exitCode, options.json ? result.stdout : null);
                return result.exitCode;
            } finally {
                if (!options.keep) {
                    deleteRecursively(work);
                } else {
                    System.err.println("sprig: kept generated sources in " + work);
                }
            }
        }
        report(diagnostics, options.json, "run", 1, null);
        return 1;
    }

    private static Diagnostic runtimeDiagnostic(String stderr, String uri) {
        String code = stderr.contains("sprig.runtime.SprigError") ? Codes.RUNTIME_ERROR
                : Codes.RUNTIME_EXCEPTION;
        String message = "Program failed at runtime";
        for (String line : stderr.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.contains("Exception") || trimmed.contains("Error")) {
                message = trimmed;
                break;
            }
        }
        return Diagnostic.error(code, Phase.RUNTIME, message, uri, null)
                .withHint("See the JVM stack trace above; use --keep to inspect generated Java.");
    }

    private static void writeSources(JavaGenerator.Output output, Path javaDir) throws IOException {
        for (Map.Entry<String, String> entry : output.sources.entrySet()) {
            Path path = javaDir.resolve(entry.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, entry.getValue(), StandardCharsets.UTF_8);
        }
    }

    private static List<Path> listJavaFiles(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(p -> p.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    private static List<Path> runtimeSources(Diagnostics diagnostics) throws IOException {
        Path dir = runtimeSourceDir();
        if (dir == null) {
            diagnostics.error(Codes.JVM_INTERNAL, Phase.JVM,
                    "Sprig runtime sources not found; set -Dsprig.home or build via scripts/build.sh",
                    null, null);
            return List.of();
        }
        return listJavaFiles(dir);
    }

    private static Path runtimeSourceDir() {
        List<Path> candidates = new ArrayList<>();
        String home = System.getProperty("sprig.home");
        if (home != null) {
            candidates.add(Path.of(home, "runtime", "src", "main", "java"));
        }
        candidates.add(Path.of("runtime", "src", "main", "java"));
        try {
            Path self = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path base = Files.isDirectory(self) ? self : self.getParent();
            candidates.add(base.resolve("../../runtime/src/main/java").normalize());
            candidates.add(base.resolve("../runtime/src/main/java").normalize());
        } catch (Exception ignored) {
            // Fall back to the candidates collected so far.
        }
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath();
            }
        }
        return null;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Best effort cleanup.
                }
            });
        }
    }

    private static void report(Diagnostics diagnostics, boolean json, String command,
                               int exitCode, String programOutput) {
        if (json) {
            System.out.print(JsonWriter.result(diagnostics.all(), null, command, exitCode, programOutput));
            return;
        }
        for (Diagnostic diagnostic : diagnostics.all()) {
            System.err.println(diagnostic.format());
        }
        if (diagnostics.hasErrors()) {
            System.err.println(diagnostics.errorCount() + " error(s); "
                    + "run 'sprig explain <code>' for details on a diagnostic code.");
        }
    }

    private static boolean jsonRequested(String[] args) {
        for (String arg : args) if (arg.equals("--json")) return true;
        return false;
    }

    private static void printFatalJson(String[] args, String code, String message) {
        String command = args.length == 0 ? "unknown" : args[0];
        Diagnostic diagnostic = Diagnostic.error(code, Phase.JVM, message, null, null);
        System.out.print(JsonWriter.result(List.of(diagnostic), null, command, 2, null));
    }

    private static int codes(String[] args) {
        Map<String, String> all = CodeDocs.all();
        boolean json = false;
        for (String arg : args) {
            if (arg.equals("--json")) {
                json = true;
            }
        }
        if (json) {
            StringBuilder sb = new StringBuilder("{\n  \"schemaVersion\": 1,\n  \"codes\": [");
            boolean first = true;
            for (Map.Entry<String, String> entry : all.entrySet()) {
                sb.append(first ? "\n" : ",\n");
                first = false;
                sb.append("    {\"code\": \"").append(entry.getKey())
                  .append("\", \"description\": \"").append(JsonWriter.escape(entry.getValue())).append("\"}");
            }
            sb.append("\n  ]\n}\n");
            System.out.print(sb);
        } else {
            for (Map.Entry<String, String> entry : all.entrySet()) {
                System.out.println(entry.getKey() + "  " + entry.getValue());
            }
        }
        return 0;
    }

    private static int explain(String[] args) {
        if (args.length < 2) {
            System.err.println("sprig explain: missing diagnostic code");
            return 2;
        }
        System.out.println(args[1] + ": " + CodeDocs.describe(args[1]));
        return 0;
    }

    // ------------------------------------------------------------------

    private static final class Options {
        Path file;
        boolean json;
        boolean syntaxOnly;
        boolean keep;
        Path outDir;
        List<String> programArgs = new ArrayList<>();

        static Options parse(String[] args, int start) {
            Options options = new Options();
            for (int i = start; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--json" -> options.json = true;
                    case "--syntax-only", "--parse-only" -> options.syntaxOnly = true;
                    case "--keep" -> options.keep = true;
                    case "-d", "--out" -> {
                        if (i + 1 < args.length) {
                            options.outDir = Path.of(args[++i]);
                        }
                    }
                    case "--" -> {
                        for (int j = i + 1; j < args.length; j++) {
                            options.programArgs.add(args[j]);
                        }
                        return options;
                    }
                    default -> {
                        if (arg.startsWith("--")) {
                            System.err.println("sprig: unknown option '" + arg + "'");
                        } else if (options.file == null) {
                            options.file = Path.of(arg);
                        } else {
                            options.programArgs.add(arg);
                        }
                    }
                }
            }
            return options;
        }
    }
}
