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
import sprig.compiler.jvm.JvmClasspath;
import sprig.compiler.jvm.JvmMetadata;
import sprig.compiler.tooling.Catalog;
import sprig.compiler.tooling.ToolJson;

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
        } catch (LinkageError e) {
            if (jsonRequested(args)) {
                printFatalJson(args, Codes.JVM_CLASS, "JVM class metadata cannot be linked: " + e);
            } else System.err.println("sprig: JVM class metadata cannot be linked: " + e);
            exit = 1;
        }
        System.exit(exit);
    }

    private static int dispatch(String[] args) throws IOException, InterruptedException {
        if (args.length == 0) { usage(System.out); return 2; }
        if (args[0].equals("help") || args[0].equals("--help") || args[0].equals("-h")) return help(args);
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
            case "capabilities" -> capabilities(args);
            case "api" -> api(args);
            case "doctor" -> doctor(args);
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
        out.println("  help [topic] [--json]                       language reference (topics: "
                + String.join(", ", Catalog.topics()) + ")");
        out.println("  capabilities [--json]                      implemented feature inventory");
        out.println("  api <Java.Class> [--classpath PATH] [--json] inspect real JVM signatures");
        out.println("  doctor [--json]                            inspect compiler environment");
        out.println("  check/build/run accept repeated --classpath JAR_OR_DIR");
        out.println("  version");
    }

    private static int help(String[] args) {
        String topic = null;
        boolean json = jsonRequested(args);
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--json")) continue;
            if (topic != null || !Catalog.topics().contains(args[i])) {
                return commandError("help", "Unknown help topic: " + args[i], json);
            }
            topic = args[i];
        }
        if (topic == null) {
            if (json) System.out.println(ToolJson.encode(Map.of(
                    "schemaVersion", Catalog.SCHEMA_VERSION,
                    "compilerVersion", Catalog.COMPILER_VERSION,
                    "languageVersion", Catalog.LANGUAGE_VERSION,
                    "topics", Catalog.topics(), "commands", Catalog.list("commands"))));
            else usage(System.out);
            return 0;
        }
        Map<String, Object> data = Catalog.help(topic);
        if (json) System.out.println(ToolJson.encode(data));
        else {
            System.out.println("Sprig " + topic + " (language " + Catalog.LANGUAGE_VERSION + ")");
            System.out.println("Syntax:");
            for (String line : (List<String>) data.get("syntax")) System.out.println("  " + line);
            System.out.println("Rules:");
            for (String line : (List<String>) data.get("rules")) System.out.println("  - " + line);
            System.out.println("Example: " + String.join(", ", (List<String>) data.get("examples")));
        }
        return 0;
    }

    private static int capabilities(String[] args) {
        boolean json = jsonRequested(args);
        if (args.length > (json ? 2 : 1)) return commandError("capabilities", "Unexpected argument", json);
        Map<String, Object> data = Catalog.capabilities();
        if (json) System.out.println(ToolJson.encode(data));
        else {
            System.out.println("Sprig compiler " + Catalog.COMPILER_VERSION + " / language " + Catalog.LANGUAGE_VERSION);
            System.out.println("JDK minimum: " + Catalog.MINIMUM_JDK);
            System.out.println("Commands: " + String.join(", ", Catalog.list("commands")));
            System.out.println("Types: " + String.join(", ", Catalog.list("nativeTypes")));
            System.out.println("Implemented: " + String.join(", ", Catalog.list("supportedSyntax")));
            System.out.println("Unsupported: " + String.join(", ", Catalog.list("unsupportedSyntax")));
            System.out.println("Use 'sprig help <topic>' for syntax and rules.");
        }
        return 0;
    }

    private static int api(String[] args) {
        Options options = Options.parse(args, 1);
        Diagnostics diagnostics = new Diagnostics();
        if (!prepare(options, diagnostics, "api")) return 1;
        if (options.file == null) return commandError("api", "Missing Java class name", options.json);
        if (!options.programArgs.isEmpty()) return commandError("api", "Unexpected extra argument", options.json);
        Class<?> clazz = Compiler.loadJavaClass(options.file.toString());
        if (clazz == null) {
            diagnostics.add(Diagnostic.error(Codes.JVM_CLASS, Phase.JVM,
                    "Cannot load Java class '" + options.file + "'", null, null)
                    .withHint("Check the fully qualified name and --classpath entries."));
            report(diagnostics, options.json, "api", 1, null);
            return 1;
        }
        Map<String, Object> data;
        try {
            data = JvmMetadata.inspect(clazz);
        } catch (LinkageError | TypeNotPresentException e) {
            diagnostics.add(Diagnostic.error(Codes.JVM_CLASS, Phase.JVM,
                    "Cannot inspect JVM class metadata: " + e, null, null)
                    .withHint("Supply all required JAR dependencies with --classpath."));
            report(diagnostics, options.json, "api", 1, null);
            return 1;
        }
        data.put("compilerVersion", Catalog.COMPILER_VERSION);
        if (options.json) System.out.println(ToolJson.encode(data));
        else {
            System.out.println("Java API: " + clazz.getName());
            for (String category : List.of("constructors", "staticMethods", "instanceMethods", "fields")) {
                System.out.println(category + ":");
                for (Map<String, Object> item : (List<Map<String, Object>>) data.get(category)) {
                    System.out.println("  " + item.get("javaSignature") + " => "
                            + item.getOrDefault("sprigSignature", item.getOrDefault("sprigType", "")));
                    if (item.get("unusableReason") != null) System.out.println("    unavailable: " + item.get("unusableReason"));
                }
            }
        }
        return 0;
    }

    private static int doctor(String[] args) {
        Options options = Options.parse(args, 1);
        if (options.file != null) return commandError("doctor", "Unexpected argument", options.json);
        if (!options.programArgs.isEmpty()) return commandError("doctor", "Unexpected extra argument", options.json);
        Diagnostics diagnostics = new Diagnostics();
        if (!prepare(options, diagnostics, "doctor")) return 1;
        boolean json = options.json;
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("schemaVersion", 1);
        data.put("compilerVersion", Catalog.COMPILER_VERSION);
        data.put("languageVersion", Catalog.LANGUAGE_VERSION);
        data.put("jdkMinimum", Catalog.MINIMUM_JDK);
        data.put("javaVersion", System.getProperty("java.version"));
        data.put("javaVendor", System.getProperty("java.vendor"));
        data.put("javacAvailable", javax.tools.ToolProvider.getSystemJavaCompiler() != null);
        data.put("compilerHome", System.getProperty("sprig.home", "unknown"));
        data.put("runtimeSource", runtimeSourceDir() == null ? null : runtimeSourceDir().toString());
        data.put("antlrAvailable", Main.class.getClassLoader().getResource("org/antlr/v4/runtime/Parser.class") != null);
        data.put("classpath", JvmClasspath.entries().stream().map(Path::toString).toList());
        data.put("compilerClasspath", System.getProperty("java.class.path"));
        data.put("platform", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        data.put("locale", java.util.Locale.getDefault().toLanguageTag());
        data.put("encoding", System.getProperty("file.encoding"));
        data.put("cwd", Path.of("").toAbsolutePath().toString());
        if (json) System.out.println(ToolJson.encode(data));
        else for (Map.Entry<String, Object> entry : data.entrySet())
            System.out.println(entry.getKey() + ": " + entry.getValue());
        return 0;
    }

    private static int commandError(String command, String message, boolean json) {
        if (json) System.out.print(JsonWriter.result(List.of(Diagnostic.error(Codes.CLI_OPTION,
                Phase.CLI, message, null, null)), null, command, 2, null));
        else System.err.println("sprig " + command + ": " + message);
        return 2;
    }

    private static boolean prepare(Options options, Diagnostics diagnostics, String command) {
        if (options.optionError != null) {
            diagnostics.error(options.optionErrorCode, Phase.CLI, options.optionError, null, null);
        } else JvmClasspath.configure(options.classpath, diagnostics);
        if (diagnostics.hasErrors()) report(diagnostics, options.json, command, 1, null);
        return !diagnostics.hasErrors();
    }

    // ------------------------------------------------------------------

    private static int check(String[] args) throws IOException {
        Options options = Options.parse(args, 1);
        if (options.file == null) {
            return commandError("check", "Missing <file.spr>", options.json);
        }
        Diagnostics diagnostics = new Diagnostics();
        if (!prepare(options, diagnostics, "check")) return 1;
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
            return commandError("build", "Missing <file.spr>", options.json);
        }
        Diagnostics diagnostics = new Diagnostics();
        if (!prepare(options, diagnostics, "build")) return 1;
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
            return commandError("run", "Missing <file.spr>", options.json);
        }
        Diagnostics diagnostics = new Diagnostics();
        if (!prepare(options, diagnostics, "run")) return 1;
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
            return commandError("explain", "Missing diagnostic code", jsonRequested(args));
        }
        Map<String, Object> detail = sprig.compiler.diag.Explanations.describe(args[1]);
        if (jsonRequested(args)) System.out.println(ToolJson.encode(detail));
        else {
            System.out.println(args[1] + ": " + detail.get("meaning"));
            System.out.println("Common causes: " + detail.get("commonCauses"));
            System.out.println("Safe fixes: " + detail.get("safeFixes"));
            if (detail.get("goodExample") != null) System.out.println("Good: " + detail.get("goodExample"));
            if (detail.get("badExample") != null) System.out.println("Bad: " + detail.get("badExample"));
        }
        return 0;
    }

    // ------------------------------------------------------------------

    private static final class Options {
        Path file;
        boolean json;
        boolean syntaxOnly;
        boolean keep;
        Path outDir;
        List<String> classpath = new ArrayList<>();
        String optionError;
        String optionErrorCode = Codes.CLI_OPTION;
        List<String> programArgs = new ArrayList<>();

        static Options parse(String[] args, int start) {
            Options options = new Options();
            for (int i = start; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--json" -> options.json = true;
                    case "--syntax-only", "--parse-only" -> options.syntaxOnly = true;
                    case "--keep" -> options.keep = true;
                    case "--classpath" -> {
                        if (i + 1 < args.length && !args[i + 1].startsWith("--")) options.classpath.add(args[++i]);
                        else {
                            options.optionError = "--classpath requires a JAR or directory path";
                            options.optionErrorCode = Codes.JVM_CLASSPATH;
                        }
                    }
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
                            options.optionError = "Unknown option '" + arg + "'";
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
