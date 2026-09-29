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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import sprig.compiler.Compilation;
import sprig.compiler.Compiler;
import sprig.compiler.diag.CodeDocs;
import sprig.compiler.diag.Codes;
import sprig.compiler.project.DepError;
import sprig.compiler.project.GitCache;
import sprig.compiler.project.DependencyResolver;
import sprig.compiler.project.Lockfile;
import sprig.compiler.project.MavenResolver;
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
import sprig.compiler.tooling.SprigApi;
import sprig.compiler.tooling.ToolJson;
import sprig.runtime.SprigRuntime;

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
            case "fmt" -> FormatCommand.run(args);
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
            case "upgrade" -> ManagedSdkUpgrade.run(args);
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
        out.println("  run   <file.spr> [--json] [--keep] [--stacktrace] [-- a b] compile and execute on the JVM");
        out.println("  build <file.spr> [-d dir] [--emit-java-only] [--json]          emit Java sources + .class files");
        out.println("  explain <SPR-CODE>                          explain a diagnostic code");
        out.println("  codes [--json]                              list every diagnostic code");
        out.println("  help [topic] [--json]                       language reference (topics: "
                + String.join(", ", Catalog.topics()) + ")");
        out.println("  capabilities [--json]                      implemented feature inventory");
        out.println("  api <Java.Class> [--member NAME] [--classpath PATH] [--json] inspect JVM signatures");
        out.println("  api <module.spr|@pkg/module.spr|.> [--member NAME] [--json]   inspect checked Sprig API");
        out.println("  doctor [--classpath PATH] [--json]          inspect compiler environment");
        out.println("  init [dir]                                  create sprig.toml and src/main.spr");
        out.println("  resolve [--offline] [--json]               resolve dependencies and write sprig.lock");
        out.println("  project [--json]                            project discovery and manifest metadata");
        out.println("  deps [--json]                               declared Sprig/JVM dependencies");
        out.println("  upgrade [--check]                           upgrade a managed SDK or inspect available updates");
        out.println("  check/build/run accept repeated --classpath JAR_OR_DIR");
        out.println("  fmt <file.spr|directory> [--check] [--json] canonical comment-preserving formatting");
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

    private static int api(String[] args) throws IOException {
        Options options = Options.parse(args, 1);
        Diagnostics diagnostics = new Diagnostics();
        int prepared = prepare(options, diagnostics, "api");
        if (prepared != 0) return prepared;
        if (options.file == null) {
            return commandError("api", "Missing Sprig module path, @package/module.spr, project directory, or Java class name", options.json);
        }
        String raw = options.file.toString().replace('\\', '/');
        if (raw.startsWith("@") || raw.endsWith(".spr") || Files.isDirectory(options.file)) {
            return sprigApi(options, diagnostics, raw);
        }
        return javaApi(options, diagnostics);
    }

    private static int javaApi(Options options, Diagnostics diagnostics) {
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

    /**
     * Sprig module or project API inspection. It runs the normal project and
     * dependency pipeline, never executes application code, and never loads
     * arbitrary Java classes for a Sprig target.
     */
    private static int sprigApi(Options options, Diagnostics diagnostics, String raw) throws IOException {
        if (Files.isDirectory(options.file)) {
            return sprigProjectApi(options, diagnostics);
        }
        Project project = null;
        DependencyResolver.Result graph = null;
        Path source;
        if (raw.startsWith("@")) {
            try {
                project = Project.discover(Path.of(""));
            } catch (Toml.TomlException e) {
                diagnostics.add(Diagnostic.error(Codes.PROJECT_MANIFEST, Phase.CLI,
                        "Invalid sprig.toml (line " + e.line + "): " + e.getMessage(),
                        manifestUri(), Span.point(Math.max(0, e.line - 1), 0)));
                report(diagnostics, options.json, "api", 1, null);
                return 1;
            }
            if (project == null) {
                diagnostics.add(Diagnostic.error(Codes.API_TARGET, Phase.CLI,
                        "Package module '" + raw + "' requires a sprig.toml project", null, null)
                        .withHint("Run sprig api from inside a project or pass a .spr path."));
                report(diagnostics, options.json, "api", 1, null);
                return 1;
            }
            try {
                graph = loadProjectGraph(project, options);
            } catch (DepError e) {
                diagnostics.add(depDiagnostic(e));
                report(diagnostics, options.json, "api", 1, null);
                return 1;
            }
            Path from = project.entryPath();
            if (!Files.isRegularFile(from)) {
                from = project.root.resolve(project.source).resolve("__api__.spr");
            }
            source = graph.resolve(from, raw, diagnostics, project.manifest.toUri().toString(), null);
            if (source == null || diagnostics.hasErrors()) {
                report(diagnostics, options.json, "api", 1, null);
                return 1;
            }
        } else {
            Prepared sourcePrep = prepareSource(options, diagnostics, "api");
            if (diagnostics.hasErrors()) {
                report(diagnostics, options.json, "api", 1, null);
                return 1;
            }
            source = sourcePrep.source;
            graph = sourcePrep.graph;
            if (source == null) {
                return commandError("api", "Missing Sprig module path", options.json);
            }
        }
        if (!Files.isRegularFile(source)) {
            diagnostics.add(Diagnostic.error(Codes.API_TARGET, Phase.CLI,
                    "Sprig module not found: " + raw, null, null)
                    .withHint("Pass an existing .spr path, @package/module.spr, or a project directory."));
            report(diagnostics, options.json, "api", 1, null);
            return 1;
        }
        Map<String, Object> data = checkedSprigModule(graph, source, raw, diagnostics);
        if (data == null) {
            report(diagnostics, options.json, "api", 1, null);
            return 1;
        }
        if (options.memberFilter != null) {
            int count = SprigApi.filterMembers(data, options.memberFilter);
            data.put("memberFilter", options.memberFilter);
            data.put("memberCount", Math.max(0, count));
            if (count <= 0) {
                String message = options.memberFilter.contains(".")
                        ? "No Sprig member '" + options.memberFilter + "' in module " + raw
                        : "No Sprig declaration named '" + options.memberFilter + "' in module " + raw;
                diagnostics.add(Diagnostic.error(Codes.API_MEMBER, Phase.CLI, message, null, null)
                        .withHint("Run sprig api " + raw + " --json to list resolved declarations."));
                report(diagnostics, options.json, "api", 1, null);
                return 1;
            }
        }
        if (options.json) System.out.println(ToolJson.encode(data));
        else printSprigModule(data);
        return 0;
    }

    private static int sprigProjectApi(Options options, Diagnostics diagnostics) throws IOException {
        if (options.memberFilter != null) {
            return commandError("api", "--member requires a single module target", options.json);
        }
        Project project;
        try {
            project = Project.discover(options.file.toAbsolutePath().normalize());
        } catch (Toml.TomlException e) {
            diagnostics.add(Diagnostic.error(Codes.PROJECT_MANIFEST, Phase.CLI,
                    "Invalid sprig.toml (line " + e.line + "): " + e.getMessage(),
                    manifestUri(), Span.point(Math.max(0, e.line - 1), 0)));
            report(diagnostics, options.json, "api", 1, null);
            return 1;
        }
        if (project == null) {
            diagnostics.add(Diagnostic.error(Codes.API_TARGET, Phase.CLI,
                    "No sprig.toml found for directory API target '" + options.file + "'", null, null)
                    .withHint("Run sprig api . from a project, or pass a .spr module path."));
            report(diagnostics, options.json, "api", 1, null);
            return 1;
        }
        DependencyResolver.Result graph;
        try {
            graph = loadProjectGraph(project, options);
        } catch (DepError e) {
            diagnostics.add(depDiagnostic(e));
            report(diagnostics, options.json, "api", 1, null);
            return 1;
        }
        Path sourceRoot = project.root.resolve(project.source).normalize().toAbsolutePath();
        List<Map<String, Object>> modules = new ArrayList<>();
        for (Path file : listSprigFiles(sourceRoot)) {
            Map<String, Object> metadata = checkedSprigModule(graph, file,
                    SprigApi.relativeLabel(sourceRoot, file), diagnostics);
            if (metadata == null) {
                report(diagnostics, options.json, "api", 1, null);
                return 1;
            }
            metadata.put("origin", "source");
            modules.add(metadata);
        }
        for (DependencyResolver.Package pkg : graph.packages) {
            if ("root".equals(pkg.kind)) continue;
            for (String exported : pkg.exports) {
                Path file = pkg.sourceRoot.resolve(exported).normalize().toAbsolutePath();
                if (!Files.isRegularFile(file)) {
                    diagnostics.add(Diagnostic.error(Codes.DEP_NOT_FOUND, Phase.CLI,
                            "Dependency '" + pkg.alias + "' exports a missing module '" + exported + "'", null, null));
                    report(diagnostics, options.json, "api", 1, null);
                    return 1;
                }
                Map<String, Object> metadata = checkedSprigModule(graph, file,
                        "@" + pkg.alias + "/" + exported, diagnostics);
                if (metadata == null) {
                    report(diagnostics, options.json, "api", 1, null);
                    return 1;
                }
                metadata.put("origin", "dependency");
                metadata.put("dependency", pkg.alias);
                metadata.put("exported", true);
                modules.add(metadata);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", 1);
        data.put("kind", "sprig-project");
        data.put("compilerVersion", Catalog.COMPILER_VERSION);
        data.put("languageVersion", Catalog.LANGUAGE_VERSION);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", project.name);
        info.put("version", project.version);
        info.put("language", project.language);
        info.put("root", project.root.toString());
        info.put("source", project.source);
        info.put("entry", project.defaultEntry);
        info.put("lockStatus", lockState(project));
        data.put("project", info);
        data.put("modules", modules);
        if (options.json) System.out.println(ToolJson.encode(data));
        else {
            System.out.println("Sprig project API: " + project.name);
            for (Map<String, Object> module : modules) {
                System.out.println("  " + module.get("module") + " [" + module.get("origin") + "]");
                for (Map<String, Object> declaration : (List<Map<String, Object>>) module.get("declarations")) {
                    System.out.println("    " + declaration.get("kind") + " " + declaration.get("name"));
                }
            }
        }
        return 0;
    }

    private static Map<String, Object> checkedSprigModule(DependencyResolver.Result graph, Path source,
                                                          String label, Diagnostics diagnostics) throws IOException {
        Compiler compiler = new Compiler(diagnostics);
        if (graph != null) {
            compiler.setImportResolver(graph);
        }
        Compilation compilation = compiler.compile(source);
        if (diagnostics.hasErrors() || compilation.main == null) {
            return null;
        }
        return SprigApi.module(compilation.main, label);
    }

    private static List<Path> listSprigFiles(Path sourceRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) return List.of();
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".spr"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private static void printSprigModule(Map<String, Object> data) {
        System.out.println("Sprig module: " + data.get("module"));
        System.out.println("path: " + data.get("path"));
        List<Map<String, Object>> variables = (List<Map<String, Object>>) data.get("variables");
        if (!variables.isEmpty()) {
            System.out.println("variables:");
            for (Map<String, Object> variable : variables) {
                System.out.println("  " + (Boolean.TRUE.equals(variable.get("mutable")) ? "var " : "let ")
                        + variable.get("name") + ": " + variable.get("type"));
            }
        }
        System.out.println("declarations:");
        for (Map<String, Object> declaration : (List<Map<String, Object>>) data.get("declarations")) {
            System.out.println("  " + declaration.get("kind") + " " + declaration.get("name"));
            if (declaration.get("fields") instanceof List<?> fields) {
                for (Object field : fields) {
                    Map<String, Object> entry = (Map<String, Object>) field;
                    System.out.println("    field " + entry.get("name") + ": " + entry.get("type")
                            + (Boolean.TRUE.equals(entry.get("required")) ? "" : " = default"));
                }
            }
            if (declaration.get("methods") instanceof List<?> methods) {
                for (Object method : methods) {
                    Map<String, Object> entry = (Map<String, Object>) method;
                    System.out.println("    method " + signature(entry));
                }
            }
            if (declaration.get("cases") instanceof List<?> cases) {
                for (Object item : cases) {
                    if (item instanceof Map<?, ?> variantCase) {
                        System.out.println("    case " + variantCase.get("name") + "("
                                + payload((List<Map<String, Object>>) variantCase.get("fields")) + ")");
                    } else {
                        System.out.println("    case " + item);
                    }
                }
            }
            if (declaration.get("kind").equals("function")) {
                System.out.println("    " + signature(declaration));
            }
        }
    }

    private static String signature(Map<String, Object> function) {
        StringBuilder sb = new StringBuilder(function.get("name").toString()).append('(');
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) function.get("parameters");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(parameters.get(i).get("name")).append(": ").append(parameters.get(i).get("type"));
        }
        sb.append("): ").append(function.get("result"));
        List<String> throwsTypes = (List<String>) function.get("throws");
        if (throwsTypes != null && !throwsTypes.isEmpty()) sb.append(" throws ").append(String.join(", ", throwsTypes));
        return sb.toString();
    }

    private static String payload(List<Map<String, Object>> fields) {
        List<String> parts = new ArrayList<>();
        if (fields != null) {
            for (Map<String, Object> field : fields) {
                parts.add(field.get("name") + ": " + field.get("type"));
            }
        }
        return String.join(", ", parts);
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
        data.put("javaHome", System.getProperty("java.home"));
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
        if (!diagnostics.hasErrors() && List.of("api", "doctor").contains(command)) {
            try {
                Project project = Project.discover(Path.of(""));
                if (project != null && (Files.isRegularFile(project.lockPath())
                        || !project.jvmDependencies.isEmpty() || !project.dependencies.isEmpty()))
                    configureProjectClasspath(loadProjectGraph(project, options), options, diagnostics);
            } catch (Toml.TomlException e) {
                diagnostics.error(Codes.PROJECT_MANIFEST, Phase.CLI, e.getMessage(), manifestUri(),
                        Span.point(Math.max(0, e.line - 1), 0));
            } catch (DepError e) { diagnostics.add(depDiagnostic(e)); }
            catch (IOException e) { diagnostics.error(Codes.PROJECT_MANIFEST, Phase.CLI, e.getMessage(), null, null); }
            if (diagnostics.hasErrors()) {
                report(diagnostics, options.json, command, 1, null);
                return 1;
            }
        }
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
        if (options.emitJavaOnly) {
            if (diagnostics.hasErrors()) {
                report(diagnostics, options.json, "build", 1, null);
                return 1;
            }
            Map<String, Object> details = new java.util.LinkedHashMap<>();
            details.put("javaSources", output.sources.keySet().stream().map(f -> javaDir.resolve(f).toAbsolutePath().toString()).toList());
            details.put("mainClass", output.mainClass);
            details.put("javacInvoked", false);
            if (options.json) System.out.print(JsonWriter.result(diagnostics.all(), null, "build", 0, null, details));
            else report(diagnostics, false, "build", 0, null);
            if (!options.json) {
                System.out.println("Java sources: " + javaDir.toAbsolutePath());
                System.out.println("Main class: " + output.mainClass);
            }
            return 0;
        }
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
                        options.programArgs, work, !options.json, options.stacktrace);
                if (!options.json) {
                    System.out.print(result.stdout);
                    System.out.flush();
                }
                if (!options.json && !result.stderr.isEmpty()) {
                    result.stderr.lines()
                            .filter(line -> !line.startsWith(SprigRuntime.FAILURE_PREFIX)
                                    && !line.startsWith(SprigRuntime.FRAME_PREFIX))
                            .forEach(System.err::println);
                }
                if (result.exitCode != 0) {
                    diagnostics.add(runtimeDiagnostic(result.stderr, result.exitCode,
                            source.toAbsolutePath().toUri().toString(), lineMaps, uris,
                            options.stacktrace));
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
            configureProjectClasspath(prepared.graph, options, diagnostics);
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

    private static void configureProjectClasspath(DependencyResolver.Result graph, Options options,
                                                    Diagnostics diagnostics) {
        List<String> entries = new ArrayList<>();
        for (Path p : MavenResolver.load(graph.lock)) entries.add(p.toString());
        entries.addAll(options.classpath); // Locked entries win duplicate names consistently in every consumer.
        JvmClasspath.configure(entries, diagnostics);
    }

    private static DependencyResolver.Result loadProjectGraph(Project project, Options options)
            throws IOException {
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
                if (options.offline && lockCurrent(project)) {
                DependencyResolver.load(project, Lockfile.parse(Files.readString(project.lockPath())), true);
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
            MavenResolver.resolve(result, options.offline);
            Path temp = Files.createTempFile(project.root, ".sprig-lock-", ".tmp");
            Files.writeString(temp, lock.render());
            try {
                try { Files.move(temp, project.lockPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(temp, project.lockPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temp); }
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
                Map<String, String> runtimePaths = new HashMap<>();
                for (DependencyResolver.Package pkg : result.packages) {
                    if (pkg.kind.equals("local")) runtimePaths.put(pkg.id, pkg.root.toString());
                }
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
                        item.put("portable", entry.portable);
                        String runtimePath = runtimePaths.get(entry.id);
                        if (runtimePath != null) item.put("runtimeResolvedPath", runtimePath);
                    }
                    entries.add(item);
                }
                data.put("sprig", entries);
                data.put("jvm", result.lock.jvm.stream().map(e -> Map.of(
                        "coordinate", e.coordinate(), "sha256", e.sha256,
                        "classpathOrder", e.classpathOrder, "direct", e.direct)).toList());
                data.put("jvmEdges", result.lock.jvmEdges.stream()
                        .map(e -> Map.of("parent", e.parent(), "child", e.child())).toList());
            }
            printJson(data);
        } else if (!already) {
            System.out.println("Resolved " + (result == null ? 0 : result.entries().size())
                    + " Sprig dependency entries and " + (result == null ? 0 : result.lock.jvm.size())
                    + " Maven artifacts/models into " + project.lockPath());
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
        Map<String, String> runtimePaths = new HashMap<>();
        if (Files.isRegularFile(project.lockPath())) {
            try {
                lock = Lockfile.parse(Files.readString(project.lockPath()));
                DependencyResolver.Result graph = DependencyResolver.load(project, lock, true);
                for (DependencyResolver.Package pkg : graph.packages) {
                    if (pkg.kind.equals("local")) runtimePaths.put(pkg.id, pkg.root.toString());
                }
            } catch (DepError e) {
                diagnostics.add(depDiagnostic(e));
                report(diagnostics, options.json, "deps", 1, null);
                return 1;
            }
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
                    item.put("id", entry.id);
                    item.put("owner", entry.owner);
                    item.put("direct", entry.owner.equals("root"));
                    item.put("resolved", true);
                    if (entry.kind.equals("git")) {
                        item.put("url", GitCache.redact(entry.url));
                        item.put("requested", entry.requested);
                        item.put("revision", entry.revision);
                    } else {
                        item.put("path", entry.path);
                        item.put("portable", entry.portable);
                        item.put("manifestSha256", entry.manifestSha);
                        String runtimePath = runtimePaths.get(entry.id);
                        if (runtimePath != null) item.put("runtimeResolvedPath", runtimePath);
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
                    item.put("extension", entry.extension);
                    item.put("classifier", entry.classifier);
                    item.put("classpathOrder", entry.classpathOrder);
                    item.put("repository", entry.repository);
                    item.put("resolved", true);
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
            data.put("jvmEdges", lock == null ? List.of() : lock.jvmEdges.stream()
                    .map(e -> Map.of("parent", e.parent(), "child", e.child())).toList());
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
                            + (entry.portable ? " (portable)" : " (not portable)"));
                }
            }
            if (lock != null && !lock.jvm.isEmpty()) {
                for (Lockfile.JvmEntry entry : lock.jvm) {
                    System.out.println("jvm " + entry.group + ":" + entry.artifact + ":"
                            + entry.version + " sha256=" + entry.sha256);
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
                    if (entry.id.equals(Lockfile.edgeId("root", dependency.name))) {
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

    private record RuntimeFailure(String className, String message, String frameFile, int frameLine) {
    }

    private record RuntimeOrigin(String id, String hint) {
    }

    private static final Pattern FRAME_TAIL = Pattern.compile("^(.+\\.java):(\\d+)$");
    private static final Pattern STRING_INDEX =
            Pattern.compile("index (-?\\d+), code point length (-?\\d+)");
    private static final Pattern STRING_RANGE =
            Pattern.compile("begin (-?\\d+), end (-?\\d+), code point length (-?\\d+)");
    private static final Pattern LIST_INDEX = Pattern.compile("Index: (\\d+), Size: (\\d+)");
    private static final Pattern LIST_INDEX_LENGTH =
            Pattern.compile("Index (\\d+) out of bounds for length (\\d+)");

    private static Diagnostic runtimeDiagnostic(String stderr, int exitCode, String sourceUri,
            Map<Path, Map<Integer, Span>> lineMaps, Map<Path, String> uris, boolean stacktrace) {
        RuntimeFailure failure = parseRuntimeFailure(stderr);
        if (failure == null) {
            return Diagnostic.error(Codes.PROGRAM_EXIT, Phase.RUNTIME,
                    "Program exited with status " + exitCode, sourceUri, null)
                    .withHint("The run command forwards the Sprig program's process exit status.")
                    .withData(Map.of("programExitCode", exitCode));
        }
        String code = failure.className.equals("sprig.runtime.SprigError") ? Codes.RUNTIME_ERROR
                : Codes.RUNTIME_EXCEPTION;
        RuntimeOrigin origin = runtimeOrigin(failure.className, failure.message);
        String uri = sourceUri;
        Span span = null;
        if (failure.frameFile != null) {
            for (Map.Entry<Path, Map<Integer, Span>> entry : lineMaps.entrySet()) {
                if (!entry.getKey().getFileName().toString().equals(failure.frameFile)) continue;
                span = JavacRunner.mapBack(entry.getKey(), failure.frameLine, lineMaps);
                String mapped = uris.get(entry.getKey());
                if (mapped != null) uri = mapped;
                break;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exception", failure.className);
        data.put("origin", origin.id());
        if (stacktrace && !stderr.isBlank()) {
            data.put("jvmStack", stderr.strip());
        }
        return Diagnostic.error(code, Phase.RUNTIME, runtimeMessage(failure), uri, span)
                .withHint(origin.hint() + " Run with --stacktrace to see the JVM stack.")
                .withData(data);
    }

    /**
     * Reads the transport marker emitted by {@link SprigRuntime#reportRuntimeFailure}.
     * Older or externally launched JVMs fall back to the raw stack-trace format.
     */
    private static RuntimeFailure parseRuntimeFailure(String stderr) {
        String[] lines = stderr.split("\n");
        for (String line : lines) {
            if (!line.startsWith(SprigRuntime.FAILURE_PREFIX)) continue;
            String rest = line.substring(SprigRuntime.FAILURE_PREFIX.length()).trim();
            int colon = rest.indexOf(": ");
            String className = colon < 0 ? rest : rest.substring(0, colon).trim();
            if (className.isEmpty()) return null;
            String message = colon < 0 ? null : rest.substring(colon + 2).trim();
            String frameFile = null;
            int frameLine = -1;
            for (String frameLineText : lines) {
                if (!frameLineText.startsWith(SprigRuntime.FRAME_PREFIX)) continue;
                Matcher frame = FRAME_TAIL.matcher(
                        frameLineText.substring(SprigRuntime.FRAME_PREFIX.length()).trim());
                if (frame.matches()) {
                    frameFile = frame.group(1);
                    frameLine = Integer.parseInt(frame.group(2));
                }
                break;
            }
            return new RuntimeFailure(className, message, frameFile, frameLine);
        }
        boolean jvmFailure = stderr.lines().anyMatch(line -> line.startsWith("Exception in thread ")
                || line.startsWith("sprig.runtime.SprigError") || line.startsWith("\tat "));
        if (!jvmFailure) return null;
        String className = "java.lang.Throwable";
        String message = null;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("at ") || (!trimmed.contains("Exception") && !trimmed.contains("Error"))) {
                continue;
            }
            trimmed = trimmed.replaceFirst("^Exception in thread \"[^\"]*\" ", "");
            int colon = trimmed.indexOf(": ");
            className = colon < 0 ? trimmed : trimmed.substring(0, colon).trim();
            message = colon < 0 ? null : trimmed.substring(colon + 2).trim();
            break;
        }
        String frameFile = null;
        int frameLine = -1;
        for (String line : lines) {
            String trimmed = line.trim();
            int paren = trimmed.lastIndexOf('(');
            int close = trimmed.lastIndexOf(')');
            if (!trimmed.startsWith("at ") || paren < 0 || close < paren) continue;
            String holder = trimmed.substring(3, paren).trim();
            if (holder.startsWith("java.") || holder.startsWith("jdk.") || holder.startsWith("sun.")
                    || holder.startsWith("sprig.runtime.")) {
                continue;
            }
            Matcher frame = FRAME_TAIL.matcher(trimmed.substring(paren + 1, close));
            if (!frame.matches()) continue;
            frameFile = frame.group(1);
            frameLine = Integer.parseInt(frame.group(2));
            break;
        }
        return new RuntimeFailure(className, message, frameFile, frameLine);
    }

    private static String runtimeMessage(RuntimeFailure failure) {
        String className = failure.className;
        String raw = failure.message;
        if (className.equals("sprig.runtime.SprigError")) {
            return raw == null || raw.isBlank() ? "Uncaught Sprig Error" : "Uncaught Error: " + raw;
        }
        if (className.equals("sprig.runtime.SprigNumericError")) {
            return raw == null || raw.isBlank() ? "Numeric operation failed" : "Numeric error: " + raw;
        }
        if (raw != null) {
            Matcher stringIndex = STRING_INDEX.matcher(raw);
            if (stringIndex.find()) {
                return "String index " + stringIndex.group(1) + " is out of bounds; the string has "
                        + codePoints(Integer.parseInt(stringIndex.group(2)));
            }
            Matcher stringRange = STRING_RANGE.matcher(raw);
            if (stringRange.find()) {
                int begin = Integer.parseInt(stringRange.group(1));
                int end = Integer.parseInt(stringRange.group(2));
                if (begin > end) {
                    return "String slice start " + begin + " is after end " + end;
                }
                return "String slice [" + begin + ", " + end + ") is out of bounds; the string has "
                        + codePoints(Integer.parseInt(stringRange.group(3)));
            }
            String listBounds = listBoundMessage(raw);
            if (listBounds != null) return listBounds;
        }
        if (className.equals("java.lang.NullPointerException")) {
            if (raw == null || raw.isBlank() || raw.startsWith("Cannot ")) {
                return "Null value where a non-null value is required";
            }
            return raw;
        }
        String simple = className.substring(className.lastIndexOf('.') + 1);
        return raw == null || raw.isBlank() ? simple + " at runtime" : simple + ": " + raw;
    }

    private static String codePoints(int count) {
        return count + (count == 1 ? " code point" : " code points");
    }

    private static String listBoundMessage(String raw) {
        for (Pattern pattern : List.of(LIST_INDEX, LIST_INDEX_LENGTH)) {
            Matcher matcher = pattern.matcher(raw);
            if (matcher.find()) {
                return "List index " + matcher.group(1) + " is out of bounds; size is "
                        + matcher.group(2);
            }
        }
        return null;
    }

    private static RuntimeOrigin runtimeOrigin(String className, String message) {
        if (className.equals("sprig.runtime.SprigError")) {
            return new RuntimeOrigin("sprig-error",
                    "Catch it with try/catch or declare throws in the calling function.");
        }
        if (className.equals("sprig.runtime.SprigNumericError")) {
            return new RuntimeOrigin("checked-arithmetic",
                    "Guard the checked arithmetic or use an explicit conversion; see `sprig help numerics`.");
        }
        if (className.equals("java.lang.StringIndexOutOfBoundsException")) {
            return new RuntimeOrigin("string-bounds",
                    "Check the string length in code points before indexing; see `sprig help strings`.");
        }
        if (className.equals("java.lang.ArrayIndexOutOfBoundsException")) {
            return new RuntimeOrigin("array-bounds", "Check the array length before indexing.");
        }
        if (className.equals("java.lang.NullPointerException")
                && message != null && message.contains("foreign boundary")) {
            return new RuntimeOrigin("foreign-boundary",
                    "Java passed null to a non-null foreign parameter; the boundary rejected it.");
        }
        if (className.equals("java.lang.NullPointerException")
                && message != null && message.contains("non-null callable")) {
            return new RuntimeOrigin("null-boundary",
                    "Java returned or passed null for a non-null callable; handle the null case before the call.");
        }
        if (className.equals("java.lang.NullPointerException")) {
            return new RuntimeOrigin("null-value", "Check for null before using the value.");
        }
        if (className.equals("java.lang.IndexOutOfBoundsException")
                || (message != null && listBoundMessage(message) != null)) {
            return new RuntimeOrigin("list-bounds", "Check the list length before indexing.");
        }
        return new RuntimeOrigin("jvm", "Inspect the failing operation and the values it received.");
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
        boolean emitJavaOnly;
        boolean stacktrace;
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
                    case "--stacktrace" -> options.stacktrace = true;
                    case "--emit-java-only" -> options.emitJavaOnly = true;
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
            if (emitJavaOnly && !command.equals("build")) return "--emit-java-only is only valid with build";
            if (keep && !command.equals("run")) return "--keep is only valid with run";
            if (stacktrace && !command.equals("run")) return "--stacktrace is only valid with run";
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
