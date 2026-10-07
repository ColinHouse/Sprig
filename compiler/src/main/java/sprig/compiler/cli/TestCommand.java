package sprig.compiler.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import sprig.compiler.Compilation;
import sprig.compiler.Compiler;
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
import sprig.compiler.project.DepError;
import sprig.compiler.project.DependencyResolver;
import sprig.compiler.project.Lockfile;
import sprig.compiler.project.MavenResolver;
import sprig.compiler.project.Project;
import sprig.compiler.project.Toml;
import sprig.compiler.tooling.ToolJson;

/** Runs ordinary Sprig project programs and checks expected compiler failures. */
public final class TestCommand {
    private static final long RUNTIME_TIMEOUT_MILLIS = 30_000;

    private TestCommand() {}

    private record Options(Path path, String filter, List<String> classpath,
                           boolean json, boolean offline, String error) {}
    private record Case(Path source, String name, String mode) {}
    private record Expectation(Set<String> codes, boolean exact) {}
    private record Outcome(String status, List<Diagnostic> diagnostics,
                           String stdout, String stderr, String failure) {}

    public static int run(String[] args) throws IOException, InterruptedException {
        Options options = parse(args);
        if (options.error != null) {
            return globalError(options.json, Codes.CLI_OPTION, options.error, null, 2);
        }
        Path selection = options.path == null ? Path.of("") : options.path;
        if (options.path != null && !Files.exists(selection)) {
            return globalError(options.json, Codes.CLI_OPTION,
                    "Test path does not exist: " + selection, null, 2);
        }
        Project project;
        try {
            Path search = Files.isRegularFile(selection) ? selection.toAbsolutePath().getParent() : selection;
            project = Project.discover(search);
        } catch (Toml.TomlException e) {
            return globalError(options.json, Codes.PROJECT_MANIFEST,
                    "Invalid sprig.toml (line " + e.line + "): " + e.getMessage(),
                    selection.toAbsolutePath().toUri().toString(), 2);
        }
        if (project == null) {
            return globalError(options.json, Codes.PROJECT_MANIFEST,
                    "No sprig.toml project found for sprig test", null, 2);
        }
        Path testsRoot = project.root.resolve("tests").toAbsolutePath().normalize();
        Path target = options.path == null || selection.toAbsolutePath().normalize().equals(project.root)
                ? testsRoot : selection.toAbsolutePath().normalize();
        if (!target.startsWith(project.root)) {
            return globalError(options.json, Codes.CLI_OPTION,
                    "Test path must be inside the project: " + target, null, 2);
        }
        DependencyResolver.Result graph;
        Diagnostics projectDiagnostics = new Diagnostics();
        try {
            if (!Files.isRegularFile(project.lockPath())) {
                return globalError(options.json, Codes.PROJECT_LOCK_MISSING,
                        "Project has no sprig.lock; run `sprig resolve`", project.manifest.toUri().toString(), 2);
            }
            Lockfile lock = Lockfile.parse(Files.readString(project.lockPath(), StandardCharsets.UTF_8));
            graph = DependencyResolver.load(project, lock, options.offline);
            List<String> entries = new ArrayList<>();
            for (Path path : MavenResolver.load(graph.lock)) entries.add(path.toString());
            entries.addAll(options.classpath);
            JvmClasspath.configure(entries, projectDiagnostics);
        } catch (DepError e) {
            return globalError(options.json, e.code, e.getMessage(), project.manifest.toUri().toString(), 2);
        }
        if (projectDiagnostics.hasErrors()) {
            return globalDiagnostic(options.json, projectDiagnostics.all().get(0), 2);
        }

        List<Case> cases;
        try {
            cases = discover(target, testsRoot, options.filter);
        } catch (IOException e) {
            return globalError(options.json, Codes.CLI_OPTION,
                    "Cannot discover tests: " + e.getMessage(), target.toUri().toString(), 2);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int passed = 0;
        int failed = 0;
        boolean runnerError = false;
        for (Case test : cases) {
            Outcome outcome = test.mode.equals("compile_fail")
                    ? compileFail(test, graph) : runProgram(test, graph, project.root);
            if (outcome.status.equals("passed")) passed++;
            else {
                failed++;
                if (outcome.status.equals("error")) runnerError = true;
            }
            rows.add(row(test, outcome));
            if (!options.json) printHuman(test, outcome);
        }
        int exit = runnerError ? 2 : failed == 0 ? 0 : 1;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", cases.size());
        summary.put("passed", passed);
        summary.put("failed", failed);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", 1);
        data.put("toolVersion", Version.VERSION);
        data.put("command", "test");
        data.put("exitCode", exit);
        data.put("summary", summary);
        data.put("tests", rows);
        data.put("diagnostics", List.of());
        if (options.json) System.out.println(ToolJson.encode(data));
        else System.out.println("\n" + passed + " passed, " + failed + " failed");
        return exit;
    }

    private static Options parse(String[] args) {
        Path path = null;
        String filter = null;
        List<String> classpath = new ArrayList<>();
        boolean json = false;
        boolean offline = false;
        String error = null;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--json" -> json = true;
                case "--offline" -> offline = true;
                case "--filter" -> {
                    if (i + 1 >= args.length || args[i + 1].startsWith("--"))
                        error = "--filter requires text";
                    else filter = args[++i];
                }
                case "--classpath" -> {
                    if (i + 1 >= args.length || args[i + 1].startsWith("--"))
                        error = "--classpath requires a path or path-separated classpath";
                    else classpath.add(args[++i]);
                }
                case "--classpath-file" -> {
                    if (i + 1 >= args.length || args[i + 1].startsWith("--"))
                        error = "--classpath-file requires a file with one JAR or directory per line";
                    else {
                        try {
                            classpath.addAll(sprig.compiler.Compiler.readClasspathFile(Path.of(args[++i])));
                        } catch (IOException e) {
                            error = e.getMessage();
                        }
                    }
                }
                default -> {
                    if (args[i].startsWith("-")) error = "Unknown test option: " + args[i];
                    else if (path == null) path = Path.of(args[i]);
                    else error = "Expected at most one test path";
                }
            }
        }
        return new Options(path, filter, List.copyOf(classpath), json, offline, error);
    }

    private static List<Case> discover(Path target, Path testsRoot, String filter) throws IOException {
        if (!Files.exists(target)) return List.of();
        List<Path> sources;
        if (Files.isRegularFile(target)) {
            if (!target.getFileName().toString().endsWith(".spr"))
                throw new IOException("Expected a .spr test file: " + target);
            sources = List.of(target);
        }
        else if (Files.isDirectory(target)) {
            try (Stream<Path> walk = Files.walk(target)) {
                sources = walk.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".spr"))
                        .toList();
            }
        } else throw new IOException("Not a regular file or directory: " + target);
        List<Case> cases = new ArrayList<>();
        for (Path source : sources) {
            if (!source.getFileName().toString().endsWith(".spr")) continue;
            String name = testsRoot.getParent().relativize(source).toString().replace('\\', '/');
            if (name.startsWith("tests/")) name = name.substring("tests/".length());
            if (filter != null && !name.contains(filter)) continue;
            String mode = source.startsWith(testsRoot.resolve("compile_fail")) ? "compile_fail" : "run";
            cases.add(new Case(source, name, mode));
        }
        cases.sort(Comparator.comparing(Case::name));
        return cases;
    }

    private static Outcome compileFail(Case test, DependencyResolver.Result graph) throws IOException {
        Path expectationPath = test.source.resolveSibling(
                test.source.getFileName().toString().replaceFirst("\\.spr$", ".expect.toml"));
        Expectation expectation;
        try {
            expectation = readExpectation(expectationPath);
        } catch (Toml.TomlException e) {
            Diagnostic diagnostic = Diagnostic.error(Codes.CLI_OPTION, Phase.CLI,
                    "Invalid compile-fail expectation: " + e.getMessage(),
                    expectationPath.toUri().toString(), Span.point(Math.max(0, e.line - 1), 0));
            return new Outcome("error", List.of(diagnostic), "", "", diagnostic.message);
        } catch (IOException | IllegalArgumentException e) {
            Diagnostic diagnostic = Diagnostic.error(Codes.CLI_OPTION, Phase.CLI,
                    "Invalid compile-fail expectation: " + e.getMessage(),
                    expectationPath.toUri().toString(), null);
            return new Outcome("error", List.of(diagnostic), "", "", diagnostic.message);
        }
        Diagnostics diagnostics = new Diagnostics();
        Compiler compiler = new Compiler(diagnostics);
        compiler.setImportResolver(graph);
        compiler.compile(test.source);
        Set<String> actual = new LinkedHashSet<>();
        for (Diagnostic diagnostic : diagnostics.all()) {
            if (diagnostic.isError()) actual.add(diagnostic.code);
        }
        Set<String> missing = new LinkedHashSet<>(expectation.codes);
        missing.removeAll(actual);
        Set<String> extra = new LinkedHashSet<>(actual);
        extra.removeAll(expectation.codes);
        boolean passed = !actual.isEmpty() && missing.isEmpty() && (!expectation.exact || extra.isEmpty());
        String failure = null;
        if (!passed) {
            failure = actual.isEmpty() ? "Expected Sprig check to fail, but it passed"
                    : "Diagnostic code mismatch: missing " + missing
                    + (expectation.exact ? ", unexpected " + extra : "");
        }
        return new Outcome(passed ? "passed" : "failed", diagnostics.all(), "", "", failure);
    }

    private static Expectation readExpectation(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Missing " + path.getFileName());
        Toml toml = Toml.parse(Files.readAllLines(path, StandardCharsets.UTF_8), true);
        if (toml.hasTables() || !Set.of("codes", "exact").containsAll(toml.rootKeys()))
            throw new IllegalArgumentException("Only root keys codes and exact are supported");
        if (!toml.rootKeys().contains("codes") || toml.scalar("", "codes") != null)
            throw new IllegalArgumentException("codes must be a nonempty array of diagnostic code strings");
        List<String> entries = toml.array("codes");
        if (entries.isEmpty() || entries.stream().anyMatch(code -> !code.matches("SPR-[A-Z0-9-]+"))
                || new LinkedHashSet<>(entries).size() != entries.size())
            throw new IllegalArgumentException("codes must contain unique SPR-* diagnostic codes");
        String exactText = toml.scalar("", "exact");
        if (toml.rootKeys().contains("exact")
                && (!toml.rootScalarIsBare("exact")
                || !List.of("true", "false").contains(exactText)))
            throw new IllegalArgumentException("exact must be true or false");
        return new Expectation(new LinkedHashSet<>(entries), Boolean.parseBoolean(exactText));
    }

    private static Outcome runProgram(Case test, DependencyResolver.Result graph, Path projectRoot)
            throws IOException, InterruptedException {
        Diagnostics diagnostics = new Diagnostics();
        Compiler compiler = new Compiler(diagnostics);
        compiler.setImportResolver(graph);
        Compilation compilation = compiler.compile(test.source);
        if (diagnostics.hasErrors())
            return new Outcome("failed", diagnostics.all(), "", "", "Sprig check failed");
        JavaGenerator.Output generated = new JavaGenerator(compilation, diagnostics).generate();
        if (diagnostics.hasErrors())
            return new Outcome("failed", diagnostics.all(), "", "", "Java generation failed");
        Path work = Files.createTempDirectory("sprig-test-");
        try {
            Path testTemp = Files.createDirectory(work.resolve("tmp"));
            Path javaDir = work.resolve("java");
            Path classesDir = work.resolve("classes");
            Map<Path, Map<Integer, Span>> lineMaps = new HashMap<>();
            Map<Path, String> uris = new HashMap<>();
            for (String name : generated.sources.keySet()) {
                Path javaPath = javaDir.resolve(name).toAbsolutePath();
                lineMaps.put(javaPath, generated.lineMaps.get(name));
                uris.put(javaPath, generated.uris.get(name));
            }
            RuntimeClasses runtime = RuntimeClasses.locate();
            if (runtime == null) RuntimeClasses.reportMissing(diagnostics);
            Path cacheRoot = runtime == null ? null : JavacCache.root();
            String cacheKey = cacheRoot == null ? null : JavacCache.key(generated, runtime.digest());
            Path cached = JavacCache.lookup(cacheRoot, cacheKey);
            if (cached != null) {
                classesDir = cached;
            } else {
                Main.writeSources(generated, javaDir);
                List<Path> javaSources = Main.listJavaFiles(javaDir);
                boolean compiled = runtime == null
                        ? JavacRunner.compile(classesDir, javaSources, diagnostics, lineMaps, uris)
                        : runtime.compileProgram(classesDir, javaSources, diagnostics, lineMaps, uris);
                if (!compiled || diagnostics.hasErrors())
                    return new Outcome("failed", diagnostics.all(), "", "", "javac failed");
                JavacCache.store(cacheRoot, cacheKey, classesDir);
            }
            JavaRunner.Result result = JavaRunner.run(classesDir, generated.mainClass,
                    List.of(), work, false, false,
                    Map.of("SPRIG_TEST_TMPDIR", testTemp.toString()),
                    RUNTIME_TIMEOUT_MILLIS, projectRoot);
            if (result.timedOut) {
                diagnostics.add(Diagnostic.error(Codes.PROGRAM_EXIT, Phase.RUNTIME,
                        "Test timed out after " + (RUNTIME_TIMEOUT_MILLIS / 1000) + " seconds",
                        test.source.toUri().toString(), null)
                        .withData(Map.of("timeoutMillis", RUNTIME_TIMEOUT_MILLIS)));
            } else if (result.exitCode != 0) {
                diagnostics.add(Main.runtimeDiagnostic(result.stderr, result.exitCode,
                        test.source.toUri().toString(), lineMaps, uris, false));
            }
            return new Outcome(result.exitCode == 0 && !result.timedOut ? "passed" : "failed",
                    diagnostics.all(), result.stdout, result.stderr,
                    result.timedOut ? "Runtime timeout" : result.exitCode == 0 ? null
                            : "Program exited with status " + result.exitCode);
        } finally {
            Main.deleteRecursively(work);
        }
    }

    private static Map<String, Object> row(Case test, Outcome outcome) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", test.name);
        row.put("path", test.source.toString());
        row.put("mode", test.mode);
        row.put("status", outcome.status);
        row.put("diagnostics", outcome.diagnostics.stream()
                .map(diagnostic -> JsonWriter.diagnosticMap(diagnostic, test.source.toUri().toString()))
                .toList());
        row.put("programOutput", outcome.stdout);
        row.put("programErrorOutput", outcome.stderr);
        if (outcome.failure != null) row.put("failure", outcome.failure);
        return row;
    }

    private static void printHuman(Case test, Outcome outcome) {
        System.out.println((outcome.status.equals("passed") ? "PASS " : "FAIL ") + test.name);
        if (outcome.status.equals("passed")) return;
        if (outcome.failure != null) System.err.println("  " + outcome.failure);
        for (Diagnostic diagnostic : outcome.diagnostics) System.err.println("  " + diagnostic.format());
        if (!outcome.stdout.isBlank()) {
            String shown = outcome.stdout.length() > 8192
                    ? outcome.stdout.substring(0, 8192) + "\n... output truncated"
                    : outcome.stdout;
            System.out.println(shown.stripTrailing());
        }
        if (outcome.diagnostics.isEmpty() && !outcome.stderr.isBlank())
            System.err.println(outcome.stderr.strip());
    }

    private static int globalError(boolean json, String code, String message, String uri, int exit) {
        return globalDiagnostic(json, Diagnostic.error(code, Phase.CLI, message, uri, null), exit);
    }

    private static int globalDiagnostic(boolean json, Diagnostic diagnostic, int exit) {
        if (json) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("schemaVersion", 1);
            data.put("toolVersion", Version.VERSION);
            data.put("command", "test");
            data.put("exitCode", exit);
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("total", 0);
            summary.put("passed", 0);
            summary.put("failed", 0);
            data.put("summary", summary);
            data.put("tests", List.of());
            data.put("diagnostics", List.of(JsonWriter.diagnosticMap(diagnostic, null)));
            System.out.println(ToolJson.encode(data));
        } else System.err.println(diagnostic.format());
        return exit;
    }
}
