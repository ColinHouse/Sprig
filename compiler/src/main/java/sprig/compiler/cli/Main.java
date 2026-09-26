package sprig.compiler.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import sprig.compiler.Compilation;
import sprig.compiler.Compiler;
import sprig.compiler.diag.CodeDocs;
import sprig.compiler.diag.Codes;
import sprig.compiler.project.DepError;
import sprig.compiler.project.GitCache;
import sprig.compiler.project.DependencyResolver;
import sprig.compiler.project.Lockfile;
import sprig.compiler.project.Project;
import sprig.compiler.project.Toml;
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
            if (args.length != 1) return commandError("version", "Unexpected argument", jsonRequested(args));
            System.out.println(Version.VERSION);
            return 0;
        }
        return switch (args[0]) {
            case "check" -> check(args);
            case "run" -> run(args);
            case "build" -> build(args);
            case "init" -> init(args);
            case "resolve" -> resolve(args);
            case "project" -> project(args);
            case "deps" -> deps(args);
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
        out.println("  api <Java.Class> [--member NAME] [--classpath PATH] [--json] inspect JVM signatures");
        out.println("  doctor [--classpath PATH] [--json]          inspect compiler environment");
        out.println("  init [dir]                                  create sprig.toml and src/main.spr");
        out.println("  resolve [--offline] [--json]               resolve dependencies and write sprig.lock");
        out.println("  project [--json]                            project discovery and manifest metadata");
        out.println("  deps [--json]                               declared Sprig/JVM dependencies");
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
        if (args.length > 2 || (args.length == 2 && !args[1].equals("--json")))
            return commandError("capabilities", "Unexpected argument or option", json);
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
        int prepared = prepare(options, diagnostics, "api");
        if (prepared != 0) return prepared;
        if (options.file == null) return commandError("api", "Missing Java class name", options.json);
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
        if (options.memberFilter != null) {
            int count = filterApiMembers(data, options.memberFilter);
            data.put("memberFilter", options.memberFilter);
            data.put("memberCount", count);
            if (count == 0) {
                diagnostics.error(Codes.JVM_MEMBER, Phase.JVM,
                        "No public JVM member named '" + options.memberFilter + "' on " + clazz.getName(), null, null);
                report(diagnostics, options.json, "api", 1, null);
                return 1;
            }
        }
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
        Diagnostics diagnostics = new Diagnostics();
        int prepared = prepare(options, diagnostics, "doctor");
        if (prepared != 0) return prepared;
        if (options.file != null || !options.extraPositionals.isEmpty())
            return commandError("doctor", "Unexpected argument", options.json);
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
        data.put("offline", options.offline);
        GitCache gitCache = new GitCache(GitCache.defaultRoot(), options.offline);
        Map<String, Object> git = new LinkedHashMap<>();
        git.put("available", gitCache.available());
        if (gitCache.available()) {
            git.put("version", gitCache.version());
        }
        data.put("git", git);
        data.put("cacheRoot", Path.of(System.getProperty("user.home"), ".sprig").toString());
        try {
            Project context = Project.discover(Path.of(""));
            if (context != null) {
                data.put("projectRoot", context.root.toString());
                data.put("projectManifest", context.manifest.toString());
                data.put("lockStatus", lockState(context));
                Lockfile lock = Files.isRegularFile(context.lockPath())
                        ? Lockfile.parse(Files.readString(context.lockPath())) : null;
                data.put("resolvedSprigDependencies", lock == null ? 0 : lock.sprig.size());
                data.put("resolvedJvmDependencies", lock == null ? 0 : lock.jvm.size());
            }
        } catch (Toml.TomlException e) {
            data.put("manifestError", "line " + e.line + ": " + e.getMessage());
        } catch (DepError | IOException e) {
            data.put("lockError", e.getMessage());
        }
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

    private static int prepare(Options options, Diagnostics diagnostics, String command) {
        if (options.optionError != null) {
            diagnostics.error(Codes.CLI_OPTION, Phase.CLI, options.optionError, null, null);
            report(diagnostics, options.json, command, 2, null);
            return 2;
        }
        String violation = options.violation(command);
        if (violation != null) return commandError(command, violation, options.json);
        JvmClasspath.configure(options.classpath, diagnostics);
        if (diagnostics.hasErrors()) {
            report(diagnostics, options.json, command, 2, null);
            return 2;
        }
        return 0;
    }

    // ------------------------------------------------------------------

    private static int check(String[] args) throws IOException {
        Options options = Options.parse(args, 1);
        Diagnostics diagnostics = new Diagnostics();
        int prepared = prepare(options, diagnostics, "check");
        if (prepared != 0) return prepared;
        Prepared sourcePrep = prepareSource(options, diagnostics, "check");
        if (diagnostics.hasErrors()) {
            report(diagnostics, options.json, "check", 1, null);
            return 1;
        }
        if (sourcePrep.source == null) {
            return commandError("check", "Missing <file.spr> and no sprig.toml found", options.json);
        }
        if (options.syntaxOnly) {
            new Compiler(diagnostics).parseOnly(sourcePrep.source);
        } else {
            Compiler compiler = new Compiler(diagnostics);
            if (sourcePrep.graph != null) {
                compiler.setImportResolver(sourcePrep.graph);
            }
            compiler.compile(sourcePrep.source);
        }
        int status = diagnostics.hasErrors() ? 1 : 0;
        report(diagnostics, options.json, "check", status, null);
        return status;
    }

    private static int build(String[] args) throws IOException {
        Options options = Options.parse(args, 1);
        Diagnostics diagnostics = new Diagnostics();
        int prepared = prepare(options, diagnostics, "build");
        if (prepared != 0) return prepared;
        Prepared sourcePrep = prepareSource(options, diagnostics, "build");
        if (diagnostics.hasErrors()) {
            report(diagnostics, options.json, "build", 1, null);
            return 1;
        }
        if (sourcePrep.source == null) {
            return commandError("build", "Missing <file.spr> and no sprig.toml found", options.json);
        }
        Compiler buildCompiler = new Compiler(diagnostics);
        if (sourcePrep.graph != null) {
            buildCompiler.setImportResolver(sourcePrep.graph);
        }
        Compilation compilation = buildCompiler.compile(sourcePrep.source);
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
        Diagnostics diagnostics = new Diagnostics();
        int prepared = prepare(options, diagnostics, "run");
        if (prepared != 0) return prepared;
        Prepared sourcePrep = prepareSource(options, diagnostics, "run");
        if (diagnostics.hasErrors()) {
            report(diagnostics, options.json, "run", 1, null);
            return 1;
        }
        Path source = sourcePrep.source;
        if (source == null) {
            return commandError("run", "Missing <file.spr> and no sprig.toml found", options.json);
        }
        Compiler runCompiler = new Compiler(diagnostics);
        if (sourcePrep.graph != null) {
            runCompiler.setImportResolver(sourcePrep.graph);
        }
        Compilation compilation = runCompiler.compile(source);
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
                    diagnostics.add(runtimeDiagnostic(result.stderr, result.exitCode,
                            source.toAbsolutePath().toUri().toString()));
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

    private static final class Prepared {
        Path source;
        DependencyResolver.Result graph;
    }

    /**
     * Explicit source files win; project context applies when compiling the
     * project entry or a file under the project source root. Manifest projects
     * require a current sprig.lock (run `sprig resolve`).
     */
    private static Prepared prepareSource(Options options, Diagnostics diagnostics, String command) {
        Prepared prepared = new Prepared();
        try {
            Project project = Project.discover(Path.of(""));
            if (project == null) {
                prepared.source = options.file;
                return prepared;
            }
            Path sourceRoot = project.root.resolve(project.source).normalize().toAbsolutePath();
            Path explicit = options.file == null ? null : options.file.toAbsolutePath().normalize();
            if (explicit != null && !explicit.startsWith(sourceRoot)) {
                prepared.source = options.file;
                return prepared;
            }
            if (options.bin != null) {
                Path entry = project.entryForBin(options.bin);
                if (entry == null) {
                    diagnostics.add(Diagnostic.error(Codes.PROJECT_ENTRY, Phase.CLI,
                            "Project '" + project.name + "' has no --bin '" + options.bin + "'",
                            project.manifest.toString(), null));
                    return prepared;
                }
                prepared.source = entry;
            } else if (project.bins.size() > 1 && !project.hasExplicitEntry) {
                diagnostics.add(Diagnostic.error(Codes.PROJECT_ENTRY, Phase.CLI,
                        "Multiple binaries require --bin or an explicit [project] entry",
                        project.manifest.toUri().toString(), null));
                return prepared;
            } else {
                prepared.source = explicit != null ? explicit : project.entryPath();
            }
            if (explicit == null && !Files.isRegularFile(prepared.source)) {
                diagnostics.add(Diagnostic.error(Codes.PROJECT_ENTRY, Phase.CLI,
                        "Project entry does not exist: " + project.defaultEntry,
                        project.manifest.toString(), null));
                return prepared;
            }
            prepared.graph = loadProjectGraph(project, options);
            return prepared;
        } catch (Toml.TomlException e) {
            diagnostics.add(Diagnostic.error(Codes.PROJECT_MANIFEST, Phase.CLI,
                    "Invalid sprig.toml (line " + e.line + "): " + e.getMessage(),
                    manifestUri(), Span.point(Math.max(0, e.line - 1), 0)));
            return prepared;
        } catch (DepError e) {
            diagnostics.add(depDiagnostic(e));
            return prepared;
        } catch (IOException e) {
            diagnostics.add(Diagnostic.error(Codes.PROJECT_MANIFEST, Phase.CLI,
                    "Project I/O failure: " + e.getMessage(), null, null));
            return prepared;
        }
    }

    /**
     * Maven resolution is not implemented in this alpha. Declaring [[jvm]]
     * dependencies fails loudly with a precise blocker instead of silently
     * dropping them; single-file builds and --classpath keep working.
     */
    private static void requireNoJvmDependencies(Project project) {
        if (project.jvmDependencies.isEmpty()) {
            return;
        }
        Project.JvmDependency first = project.jvmDependencies.get(0);
        throw new DepError(Codes.DEP_MAVEN,
                "Maven dependency resolution is not implemented in this alpha: "
                        + first.group + ":" + first.artifact + ":" + first.version,
                project.manifest.toString())
                .with("group", first.group)
                .with("artifact", first.artifact)
                .with("version", first.version)
                .with("hint", "Remove [[jvm]] or pass the jar with --classpath.");
    }

    private static DependencyResolver.Result loadProjectGraph(Project project, Options options)
            throws IOException {
        requireNoJvmDependencies(project);
        Path lockPath = project.lockPath();
        if (!Files.isRegularFile(lockPath)) {
            throw new DepError(Codes.PROJECT_LOCK_MISSING,
                    "Project '" + project.name + "' has no sprig.lock", project.manifest.toString())
                    .with("hint", "Run `sprig resolve`.");
        }
        Lockfile lock = Lockfile.parse(Files.readString(lockPath));
        return DependencyResolver.load(project, lock, options.offline);
    }

    private static Diagnostic depDiagnostic(DepError error) {
        Diagnostic diagnostic = Diagnostic.error(error.code, Phase.CLI, error.getMessage(), null, null);
        if (!error.data.isEmpty()) {
            diagnostic.withData(error.data);
        }
        Object hint = error.data.get("hint");
        if (hint instanceof String text) {
            diagnostic.withHint(text);
        }
        return diagnostic;
    }

    private static int resolve(String[] args) throws IOException {
        Options options = Options.parse(args, 1);
        Diagnostics diagnostics = new Diagnostics();
        int prepared = prepare(options, diagnostics, "resolve");
        if (prepared != 0) return prepared;
        if (options.file != null) {
            return commandError("resolve", "resolve takes no file argument", options.json);
        }
        Project project;
        try {
            project = Project.discover(Path.of(""));
        } catch (Toml.TomlException e) {
            diagnostics.error(Codes.PROJECT_MANIFEST, Phase.CLI,
                    "Invalid sprig.toml (line " + e.line + "): " + e.getMessage(),
                    manifestUri(), Span.point(Math.max(0, e.line - 1), 0));
            report(diagnostics, options.json, "resolve", 1, null);
            return 1;
        }
        if (project == null) {
            return commandError("resolve", "No sprig.toml found in this directory or above",
                    options.json);
        }
        try {
            requireNoJvmDependencies(project);
            if (options.offline && lockCurrent(project)) {
                if (!options.json) {
                    System.out.println("already resolved: sprig.lock matches sprig.toml");
                }
                return reportResolve(options, project, null, true);
            }
            DependencyResolver.Result result = DependencyResolver.resolve(project, options.offline);
            Lockfile lock = result.lock;
            lock.manifestSha = Lockfile.digest(project.manifest);
            lock.language = project.language;
            lock.compiler = sprig.compiler.tooling.Catalog.COMPILER_VERSION;
            lock.jvm.clear();
            for (Project.JvmDependency ignored : project.jvmDependencies) {
                // Maven artifact resolution is not implemented in this alpha;
                // declared JVM coordinates are recorded as unresolved and the
                // build keeps using explicit --classpath.
            }
            Path temp = project.lockPath().resolveSibling(Project.LOCKFILE + ".tmp");
            Files.writeString(temp, lock.render());
            Files.move(temp, project.lockPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return reportResolve(options, project, result, false);
        } catch (DepError e) {
            diagnostics.add(depDiagnostic(e));
            report(diagnostics, options.json, "resolve", 1, null);
            return 1;
        }
    }

    private static boolean lockCurrent(Project project) {
        try {
            if (!Files.isRegularFile(project.lockPath())) {
                return false;
            }
            Lockfile lock = Lockfile.parse(Files.readString(project.lockPath()));
            if (lock.manifestSha == null
                    || !lock.manifestSha.equals(Lockfile.digest(project.manifest))) {
                return false;
            }
            DependencyResolver.load(project, lock, true);
            return true;
        } catch (IOException | DepError e) {
            return false;
        }
    }

    private static int reportResolve(Options options, Project project,
                                     DependencyResolver.Result result, boolean already) {
        if (options.json) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("schemaVersion", 1);
            data.put("command", "resolve");
            data.put("exitCode", 0);
            data.put("alreadyResolved", already);
            data.put("lockfile", project.lockPath().toString());
            if (result != null) {
                List<Object> entries = new ArrayList<>();
                for (Lockfile.SprigEntry entry : result.entries()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", entry.name);
                    item.put("kind", entry.kind);
                    item.put("projectName", entry.projectName);
                    item.put("resolved", true);
                    if (entry.kind.equals("git")) {
                        item.put("url", GitCache.redact(entry.url));
                        item.put("requested", entry.requested);
                        item.put("revision", entry.revision);
                    } else {
                        item.put("path", entry.path);
                        item.put("portable", false);
                    }
                    entries.add(item);
                }
                data.put("sprig", entries);
                data.put("jvm", List.of());
            }
            printJson(data);
        } else if (!already) {
            System.out.println("Resolved " + (result == null ? 0 : result.entries().size())
                    + " Sprig dependency entries into " + project.lockPath());
        }
        return 0;
    }

    private static String manifestUri() {
        Path manifest = Project.findManifest(Path.of(""));
        return manifest == null ? null : manifest.toUri().toString();
    }

    private static void printJson(Map<String, Object> data) {
        System.out.println(sprig.compiler.tooling.ToolJson.encode(data));
    }

    private static int init(String[] args) throws IOException {
        Options options = Options.parse(args, 1);
        Diagnostics diagnostics = new Diagnostics();
        int prepared = prepare(options, diagnostics, "init");
        if (prepared != 0) return prepared;
        Path dir = options.file == null ? Path.of("").toAbsolutePath() : options.file.toAbsolutePath();
        Path manifest = dir.resolve(Project.MANIFEST);
        Path entry = dir.resolve("src/main.spr");
        if (Files.exists(manifest) || Files.exists(entry)) {
            return commandError("init", "Refusing to overwrite existing sprig.toml or src/main.spr",
                    options.json);
        }
        Files.createDirectories(entry.getParent());
        String name = dir.getFileName() == null ? "sprig-app" : dir.getFileName().toString();
        Files.writeString(manifest, "[project]\nname = \"" + name.replace("\\", "\\\\")
                .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
                + "\"\nversion = \"0.1.0\"\nlanguage = \"0.8\"\n");
        Files.writeString(entry, "# " + name.replace("\n", " ").replace("\r", " ")
                + " entry point.\n\nfunc main() -> Unit:\n"
                + "    print(\"Hello, Sprig!\")\n\nmain()\n");
        if (options.json) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("schemaVersion", 1);
            data.put("command", "init");
            data.put("exitCode", 0);
            data.put("created", List.of(manifest.toString(), entry.toString()));
            data.put("next", "Run `sprig resolve` to create sprig.lock.");
            printJson(data);
        } else {
            System.out.println("Created " + manifest);
            System.out.println("Created " + entry);
            System.out.println("Run `sprig resolve` to create sprig.lock.");
        }
        return 0;
    }

    private static int project(String[] args) throws IOException {
        Options options = Options.parse(args, 1);
        Diagnostics diagnostics = new Diagnostics();
        int prepared = prepare(options, diagnostics, "project");
        if (prepared != 0) return prepared;
        if (options.file != null) {
            return commandError("project", "project takes no file argument", options.json);
        }
        Project project;
        try {
            project = Project.discover(Path.of(""));
        } catch (Toml.TomlException e) {
            diagnostics.error(Codes.PROJECT_MANIFEST, Phase.CLI,
                    "Invalid sprig.toml (line " + e.line + "): " + e.getMessage(),
                    manifestUri(), Span.point(Math.max(0, e.line - 1), 0));
            report(diagnostics, options.json, "project", 1, null);
            return 1;
        }
        if (project == null) {
            return commandError("project", "No sprig.toml found in this directory or above",
                    options.json);
        }
        if (options.json) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("schemaVersion", 1);
            data.put("command", "project");
            data.put("exitCode", 0);
            Map<String, Object> map = project.toJsonMap();
            String state = lockState(project);
            map.put("lockStatus", state);
            if (Files.isRegularFile(project.lockPath())) {
                try {
                    Lockfile lock = Lockfile.parse(Files.readString(project.lockPath()));
                    map.put("resolvedSprigDependencies", lock.sprig.size());
                    map.put("resolvedJvmDependencies", lock.jvm.size());
                } catch (DepError e) {
                    map.put("resolvedSprigDependencies", 0);
                    map.put("resolvedJvmDependencies", 0);
                }
            }
            map.put("cacheRoot", Path.of(System.getProperty("user.home"), ".sprig").toString());
            GitCache git = new GitCache(GitCache.defaultRoot(), true);
            map.put("gitAvailable", git.available());
            data.put("project", map);
            printJson(data);
        } else {
            System.out.println("project: " + project.name + " " + project.version
                    + " (language " + project.language + ")");
            System.out.println("root: " + project.root);
            System.out.println("source: " + project.source);
            System.out.println("entry: " + project.defaultEntry);
            for (Project.Bin bin : project.bins) {
                System.out.println("bin: " + bin.name + " -> " + bin.entry);
            }
            System.out.println("lockfile: " + (Files.isRegularFile(project.lockPath())
                    ? "present" : "absent"));
            System.out.println("dependencies: " + project.dependencies.size()
                    + " Sprig, " + project.jvmDependencies.size() + " JVM");
        }
        return 0;
    }

    private static int deps(String[] args) throws IOException {
        Options options = Options.parse(args, 1);
        Diagnostics diagnostics = new Diagnostics();
        int prepared = prepare(options, diagnostics, "deps");
        if (prepared != 0) return prepared;
        if (options.file != null) {
            return commandError("deps", "deps takes no file argument", options.json);
        }
        Project project;
        try {
            project = Project.discover(Path.of(""));
        } catch (Toml.TomlException e) {
            diagnostics.error(Codes.PROJECT_MANIFEST, Phase.CLI,
                    "Invalid sprig.toml (line " + e.line + "): " + e.getMessage(),
                    manifestUri(), Span.point(Math.max(0, e.line - 1), 0));
            report(diagnostics, options.json, "deps", 1, null);
            return 1;
        }
        if (project == null) {
            return commandError("deps", "No sprig.toml found in this directory or above", options.json);
        }
        String state = lockState(project);
        if (!state.equals("current") && !state.equals("empty")) {
            String code = state.equals("missing") ? Codes.PROJECT_LOCK_MISSING
                    : Codes.PROJECT_LOCK_STALE;
            diagnostics.error(code, Phase.CLI,
                    state.equals("missing")
                            ? "Project '" + project.name + "' has no sprig.lock"
                            : "sprig.toml and sprig.lock do not match",
                    project.manifest.toString(), null);
            report(diagnostics, options.json, "deps", 1, null);
            return 1;
        }
        Lockfile lock = null;
        if (Files.isRegularFile(project.lockPath())) {
            lock = Lockfile.parse(Files.readString(project.lockPath()));
        }
        if (options.json) {
            List<Object> sprig = new ArrayList<>();
            List<Object> jvm = new ArrayList<>();
            if (lock != null) {
                for (Lockfile.SprigEntry entry : lock.sprig) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", entry.name);
                    item.put("kind", entry.kind);
                    item.put("projectName", entry.projectName);
                    item.put("direct", isDirect(project, entry.name));
                    item.put("resolved", true);
                    if (entry.kind.equals("git")) {
                        item.put("url", GitCache.redact(entry.url));
                        item.put("requested", entry.requested);
                        item.put("revision", entry.revision);
                    } else {
                        item.put("path", entry.path);
                        item.put("portable", false);
                        item.put("manifestSha256", entry.manifestSha);
                    }
                    sprig.add(item);
                }
                for (Lockfile.JvmEntry entry : lock.jvm) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("group", entry.group);
                    item.put("artifact", entry.artifact);
                    item.put("version", entry.version);
                    item.put("direct", entry.direct);
                    item.put("sha256", entry.sha256);
                    item.put("resolved", entry.sha256 != null);
                    jvm.add(item);
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("schemaVersion", 1);
            data.put("command", "deps");
            data.put("exitCode", 0);
            data.put("lockStatus", state);
            data.put("sprigDependencies", sprig);
            data.put("jvmDependencies", jvm);
            printJson(data);
        } else {
            if (lock == null || lock.sprig.isEmpty()) {
                System.out.println("no Sprig dependencies resolved");
            }
            for (Lockfile.SprigEntry entry : lock == null ? List.<Lockfile.SprigEntry>of() : lock.sprig) {
                if (entry.kind.equals("git")) {
                    System.out.println("sprig " + entry.name + " git " + GitCache.redact(entry.url)
                            + " " + entry.requested + " -> " + entry.revision);
                } else {
                    System.out.println("sprig " + entry.name + " local " + entry.path
                            + " (not portable)");
                }
            }
            if (lock != null && !lock.jvm.isEmpty()) {
                for (Lockfile.JvmEntry entry : lock.jvm) {
                    System.out.println("jvm " + entry.group + ":" + entry.artifact + ":"
                            + entry.version + " (not resolved)");
                }
            }
        }
        return 0;
    }

    private static boolean isDirect(Project project, String name) {
        for (Project.Dependency dependency : project.dependencies) {
            if (dependency.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** "missing" | "empty" | "stale" | "malformed" | "current". */
    private static String lockState(Project project) {
        if (!Files.isRegularFile(project.lockPath())) {
            return project.dependencies.isEmpty() ? "missing" : "missing";
        }
        try {
            Lockfile lock = Lockfile.parse(Files.readString(project.lockPath()));
            if (lock.manifestSha == null
                    || !lock.manifestSha.equals(Lockfile.digest(project.manifest))) {
                return "stale";
            }
            for (Project.Dependency dependency : project.dependencies) {
                boolean found = false;
                for (Lockfile.SprigEntry entry : lock.sprig) {
                    if (entry.name.equals(dependency.name)) {
                        found = true;
                    }
                }
                if (!found) {
                    return "stale";
                }
            }
            if (!project.jvmDependencies.isEmpty() && lock.jvm.isEmpty()) {
                return "stale";
            }
            return "current";
        } catch (DepError | IOException e) {
            return "malformed";
        }
    }

    private static Diagnostic runtimeDiagnostic(String stderr, int exitCode, String uri) {
        boolean jvmFailure = stderr.lines().anyMatch(line -> line.startsWith("Exception in thread ")
                || line.startsWith("sprig.runtime.SprigError") || line.startsWith("\tat "));
        if (!jvmFailure) {
            return Diagnostic.error(Codes.PROGRAM_EXIT, Phase.RUNTIME,
                    "Program exited with status " + exitCode, uri, null)
                    .withHint("The run command forwards the Sprig program's process exit status.")
                    .withData(Map.of("programExitCode", exitCode));
        }
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
        for (String arg : args) {
            if (arg.equals("--")) break;
            if (arg.equals("--json")) return true;
        }
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
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--json")) {
                json = true;
            } else return commandError("codes", "Unexpected argument or option: " + arg, json || jsonRequested(args));
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
        boolean json = jsonRequested(args);
        if (args.length < 2) {
            return commandError("explain", "Missing diagnostic code", json);
        }
        if (args.length != 2 + (json && args[args.length - 1].equals("--json") ? 1 : 0)
                || args[1].startsWith("--"))
            return commandError("explain", "Expected one diagnostic code and optional --json", json);
        Map<String, Object> detail = sprig.compiler.diag.Explanations.describe(args[1]);
        if (json) System.out.println(ToolJson.encode(detail));
        else {
            System.out.println(args[1] + ": " + detail.get("meaning"));
            System.out.println("Common causes: " + detail.get("commonCauses"));
            System.out.println("Safe fixes: " + detail.get("safeFixes"));
            if (detail.get("goodExample") != null) System.out.println("Good: " + detail.get("goodExample"));
            if (detail.get("badExample") != null) System.out.println("Bad: " + detail.get("badExample"));
        }
        return 0;
    }

    private static int filterApiMembers(Map<String, Object> data, String member) {
        int count = 0;
        for (String category : List.of("constructors", "staticMethods", "instanceMethods", "fields")) {
            List<Map<String, Object>> all = (List<Map<String, Object>>) data.get(category);
            List<Map<String, Object>> filtered = all.stream()
                    .filter(item -> member.equals(item.get("name"))
                            || (category.equals("constructors") && member.equals("<init>")))
                    .toList();
            data.put(category, filtered);
            count += filtered.size();
        }
        return count;
    }

    // ------------------------------------------------------------------

    private static final class Options {
        Path file;
        boolean json;
        boolean syntaxOnly;
        boolean keep;
        Path outDir;
        boolean outDirSpecified;
        boolean separatorProvided;
        String memberFilter;
        String bin;
        boolean offline;
        List<String> classpath = new ArrayList<>();
        String optionError;
        List<String> programArgs = new ArrayList<>();
        List<String> extraPositionals = new ArrayList<>();

        static Options parse(String[] args, int start) {
            Options options = new Options();
            for (int i = start; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--json" -> options.json = true;
                    case "--offline" -> options.offline = true;
                    case "--syntax-only", "--parse-only" -> options.syntaxOnly = true;
                    case "--keep" -> options.keep = true;
                    case "--bin" -> {
                        if (i + 1 < args.length && !args[i + 1].startsWith("-")) options.bin = args[++i];
                        else options.optionError = "--bin requires a binary name";
                    }
                    case "--member" -> {
                        if (i + 1 < args.length && !args[i + 1].startsWith("-")) options.memberFilter = args[++i];
                        else options.optionError = "--member requires a JVM member name";
                    }
                    case "--classpath" -> {
                        if (i + 1 < args.length && !args[i + 1].startsWith("-")) options.classpath.add(args[++i]);
                        else options.optionError = "--classpath requires a JAR or directory path";
                    }
                    case "-d", "--out" -> {
                        options.outDirSpecified = true;
                        if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                            options.outDir = Path.of(args[++i]);
                        } else options.optionError = arg + " requires an output directory";
                    }
                    case "--" -> {
                        options.separatorProvided = true;
                        for (int j = i + 1; j < args.length; j++) {
                            options.programArgs.add(args[j]);
                        }
                        return options;
                    }
                    default -> {
                        if (arg.startsWith("-")) {
                            options.optionError = "Unknown option '" + arg + "'";
                        } else if (options.file == null) {
                            options.file = Path.of(arg);
                        } else {
                            options.extraPositionals.add(arg);
                        }
                    }
                }
            }
            return options;
        }

        String violation(String command) {
            if (syntaxOnly && !command.equals("check")) return "--syntax-only is only valid with check";
            if (keep && !command.equals("run")) return "--keep is only valid with run";
            if (outDirSpecified && !command.equals("build")) return "-d/--out is only valid with build";
            if (memberFilter != null && !command.equals("api")) return "--member is only valid with api";
            if (bin != null && !command.equals("run")) return "--bin is only valid with run";
            if (offline && !List.of("resolve", "check", "build", "run", "api", "doctor")
                    .contains(command)) return "--offline is not valid with " + command;
            if (!classpath.isEmpty() && !List.of("check", "build", "run", "api", "doctor").contains(command))
                return "--classpath is not valid with " + command;
            if (separatorProvided && !command.equals("run")) return "-- is only valid with run";
            if (!extraPositionals.isEmpty()) {
                if (command.equals("run")) return "Program arguments must follow --";
                return "Unexpected extra argument: " + extraPositionals.get(0);
            }
            return null;
        }
    }
}
