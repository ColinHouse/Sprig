package sprig.compiler.tooling;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import sprig.compiler.sem.BuiltinMembers;
import sprig.compiler.types.ListType;
import sprig.compiler.types.MapType;
import sprig.compiler.types.NativeType;

/** Versioned, packaged source of truth for agent-facing language/tool metadata. */
public final class Catalog {
    private static final Properties DATA = load();
    public static final String COMPILER_VERSION = get("compilerVersion");
    public static final String LANGUAGE_VERSION = get("languageVersion");
    public static final int MINIMUM_JDK = Integer.parseInt(get("jdkMinimum"));
    public static final int SCHEMA_VERSION = Integer.parseInt(get("schemaVersion"));

    private Catalog() {}

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream in = Catalog.class.getResourceAsStream("catalog.properties")) {
            if (in == null) throw new IllegalStateException("Missing packaged tooling catalog");
            properties.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read tooling catalog", e);
        }
    }

    public static String get(String key) {
        String value = DATA.getProperty(key);
        if (value == null) throw new IllegalArgumentException("Missing catalog key: " + key);
        return value;
    }

    public static List<String> list(String key) {
        return List.of(get(key).split(key.equals("collectionTypes") ? "\\s*;\\s*" : "\\s*,\\s*"));
    }

    public static List<String> topics() {
        return List.of("language", "types", "strings", "functions", "classes", "variants", "match",
                "nullability", "errors", "collections", "numerics", "modules", "jvm", "conform",
                "generics", "concurrency", "projects", "dependencies", "agents", "upgrade", "fmt",
                "testing", "wrap", "lsp");
    }

    public static Map<String, Object> help(String topic) {
        if (!topics().contains(topic)) throw new IllegalArgumentException("Unknown help topic: " + topic);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", SCHEMA_VERSION);
        result.put("compilerVersion", COMPILER_VERSION);
        result.put("languageVersion", LANGUAGE_VERSION);
        result.put("topic", topic);
        result.put("syntax", splitLines(get("help." + topic + ".syntax")));
        result.put("rules", List.of(get("help." + topic + ".rules").split(";\\s*")));
        Map<String, List<String>> methods = builtinMethods(topic);
        if (!methods.isEmpty()) {
            result.put("methods", methods);
        }
        result.put("examples", splitLines(get("help." + topic + ".examples")));
        return result;
    }

    /**
     * The built-in members a topic covers, read from the same tables the checker
     * resolves against, so help cannot list a method the compiler rejects.
     */
    public static Map<String, List<String>> builtinMethods(String topic) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        switch (topic) {
            case "language" -> out.put("built-in functions", List.of("print", "range", "assert"));
            case "strings" -> {
                out.put("String", BuiltinMembers.instanceNames(NativeType.STRING));
                out.put("String (static)", statics(NativeType.STRING));
            }
            case "collections" -> {
                out.put("List[T]", BuiltinMembers.instanceNames(new ListType(NativeType.INT, false)));
                out.put("MutableList[T]", BuiltinMembers.instanceNames(new ListType(NativeType.INT, true)));
                out.put("Map[K, V]", BuiltinMembers.instanceNames(new MapType(NativeType.STRING, NativeType.INT, false)));
                out.put("MutableMap[K, V]", BuiltinMembers.instanceNames(new MapType(NativeType.STRING, NativeType.INT, true)));
            }
            case "numerics" -> {
                for (NativeType type : List.of(NativeType.INT, NativeType.INT32, NativeType.FLOAT, NativeType.FLOAT32,
                        NativeType.DECIMAL, NativeType.BIGINT)) {
                    List<String> instance = BuiltinMembers.instanceNames(type);
                    if (!instance.isEmpty()) out.put(type.display(), instance);
                    List<String> statics = statics(type);
                    if (!statics.isEmpty()) out.put(type.display() + " (static)", statics);
                }
            }
            default -> {
            }
        }
        return out;
    }

    private static List<String> statics(NativeType type) {
        List<String> out = new ArrayList<>();
        for (String name : BuiltinMembers.staticNames(type)) {
            out.add(type.display() + "." + name);
        }
        return out;
    }

    /** An example's source text, read from the installed SDK or checkout, or null when absent. */
    public static String exampleSource(String example) {
        String home = System.getProperty("sprig.home");
        if (home == null || !example.endsWith(".spr")) return null;
        try {
            java.nio.file.Path path = java.nio.file.Path.of(home).resolve(example).normalize();
            if (!path.startsWith(java.nio.file.Path.of(home).normalize()) || !java.nio.file.Files.isRegularFile(path)) {
                return null;
            }
            return java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static List<String> splitLines(String value) {
        List<String> out = new ArrayList<>();
        for (String line : value.split("\\s*\\|")) {
            out.add(line.startsWith(" ") ? line.substring(1) : line);
        }
        return out;
    }

    public static Map<String, Object> capabilities() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", SCHEMA_VERSION);
        result.put("compilerVersion", COMPILER_VERSION);
        result.put("languageVersion", LANGUAGE_VERSION);
        result.put("jdk", Map.of("minimum", MINIMUM_JDK));
        result.put("license", get("license"));
        result.put("releaseStatus", get("releaseStatus"));
        result.put("supportedPlatforms", list("supportedPlatforms"));
        result.put("experimentalPlatforms", list("experimentalPlatforms"));
        for (String key : List.of("commands", "nativeTypes", "collectionTypes", "supportedSyntax", "unsupportedSyntax")) {
            result.put(key, list(key));
        }
        Map<String, Object> features = new LinkedHashMap<>();
        DATA.stringPropertyNames().stream().filter(k -> k.startsWith("feature.")).sorted()
                .forEach(k -> features.put(k.substring(8), Boolean.parseBoolean(get(k))));
        result.put("features", features);
        result.put("featureGuidance", featureGuidance());
        result.put("genericCapabilities", list("genericCapabilities"));
        result.put("lambdaMaxArity", Integer.parseInt(get("lambdaMaxArity")));
        result.put("matchBehavior", "statement and expression; exhaustive; no wildcard; expression branches contain one expression");
        Map<String, Object> strings = new LinkedHashMap<>();
        strings.put("hasCharType", false);
        strings.put("elementType", "String");
        strings.put("positionUnit", get("stringPositionUnit"));
        strings.put("graphemeClusters", false);
        strings.put("javaCharInterop", get("stringJavaCharInterop"));
        result.put("stringSemantics", strings);
        result.put("numericSemanticsProfile", get("numericProfile"));
        result.put("jvmInterop", get("jvmProfile"));
        for (String key : List.of("sourceArrays", "jvmArrayPassThrough", "jvmByteArrayHelpers",
                "jvmConcreteGenerics", "jvmCollectionAdapters", "jvmGenericInference",
                "jvmWildcards", "jvmVarargs", "jvmFunctionalInterfaces")) {
            result.put(key, Boolean.parseBoolean(get(key)));
        }
        result.put("classpath", get("classpathPolicy"));
        result.put("sourceModules", true);
        result.put("diagnosticSchemaVersion", 1);
        Map<String, Object> testing = new LinkedHashMap<>();
        testing.put("discovery", "tests/**/*.spr");
        testing.put("compileFailExpectations", "tests/compile_fail/**/*.expect.toml");
        testing.put("separateJvmPerRuntimeTest", true);
        testing.put("temporaryDirectoryEnvironment", "SPRIG_TEST_TMPDIR");
        testing.put("processApi", "@std/test.spr");
        testing.put("runtimeTimeoutSeconds", 30);
        testing.put("externalClasspath", true);
        result.put("testRunner", testing);
        return result;
    }

    /**
     * Alternatives for unsupported or limited capabilities. This reflects the
     * current compiler only; it must never advertise unimplemented syntax.
     */
    private static Map<String, Object> featureGuidance() {
        Map<String, Object> out = new LinkedHashMap<>();
        guidance(out, "inheritance", "classes", "composition", "narrow Java host adapter");
        guidance(out, "interfaces", "classes", "composition", "narrow Java adapter with fn(A) -> R or Fn0..Fn3");
        out.put("arbitraryJavaSam", Map.of("supported", true, "helpTopic", "jvm",
                "rules", List.of("a Java functional-interface parameter accepts a Sprig fn(...) -> R value; the compiler emits the adapter",
                        "parameters match exactly after the Java mapping; void accepts any result; wildcards inside the interface's type arguments read as their bound",
                        "method type variables are bound through the receiver, through written method[Type] arguments, or inferred from the plain arguments (never from the lambda)",
                        "a function value with throws Error cannot cross into Java")));
        out.put("genericTypeInference", Map.of("supported", true, "helpTopic", "generics",
                "rules", List.of("type arguments of a generic Sprig function call, class constructor or variant case with a payload are inferred from the call's arguments when they are left out",
                        "never from the expected type, the assignment target or the result; a type position still writes Type[Arg]",
                        "written [Type] arguments still work and win; write all of them or none",
                        "an unannotated numeric literal, or a list or map literal passed for a bare T, counts only when no other argument says what the parameter is; null, [] and {} say nothing",
                        "Java generic types keep explicit type arguments; a Java method's own type parameters are inferred when the plain arguments fix every one of them exactly, and written otherwise")));
        out.put("matchExpression", Map.of("supported", true, "helpTopic", "match",
                "rules", List.of("one expression per case", "strict result typing", "no block expressions")));
        out.put("ifExpression", Map.of("supported", true, "helpTopic", "language",
                "rules", List.of("if, any elifs and a required else, each with one expression on its own indented line",
                        "the result typing of expression match; the narrowing of the if statement: a branch sees its own condition true and every earlier condition false, and else sees every condition false",
                        "a whole value: after an assignment, return, throw or '=>', or as a branch of another if or match expression; never inside parentheses, brackets or braces, and never an operand",
                        "an if at the start of a statement is the if statement")));
        guidance(out, "wildcardMatch", "match", "list every enum/variant case explicitly");
        guidance(out, "asyncAwait", "concurrency", "the sprig-concurrent library: spawn[T](fn() -> T) -> Task[T] and task.await()",
                "parallel_map, await_all, pool(threads) and spawn_on for bounded parallelism",
                "channel[T](capacity) for values between threads; counter, lock and latch for shared state");
        guidance(out, "errorClasses", "errors", "a class with a message: String field plus conform C to Error(message); throw it, declare throws C, catch it by name or as Error");
        guidance(out, "arrays", "jvm", "foreign JVM array pass-through with exact classes", "byte[] helpers via sprig.runtime.jvm.HostBytes", "List[T] and @std/jvm adapters");
        out.put("varargs", Map.of("supported", true, "helpTopic", "jvm",
                "rules", List.of("trailing arguments are packed into the final array parameter; zero of them is allowed",
                        "an opaque Java array of exactly the element class is passed through",
                        "fixed-arity overloads are preferred; the expanded form is tried only when none applies",
                        "a type-variable element (T...) stays unsupported")));
        guidance(out, "annotations", "language", "explicit typed metadata", "ordinary functions");
        guidance(out, "decorators", "language", "ordinary functions and modules");
        guidance(out, "macros", "language", "ordinary functions and modules");
        guidance(out, "blockLambdas", "functions", "named function plus expression lambda fn(x: T) => named(x)");
        guidance(out, "namedFunctionReferences", "functions", "expression lambda forwarding fn(x: T) => named(x)");
        out.put("callableThrows", Map.of("supported", true, "helpTopic", "errors",
                "rules", List.of("a lambda that calls a function throwing Error has the type fn(A) -> R throws Error",
                        "a value without the clause is accepted where the clause is expected, never the reverse",
                        "a rethrows function throws exactly what its callable arguments throw",
                        "only Error crosses a function value; checked Java exceptions stay in named functions")));
        guidance(out, "reflectionDerivedSchemas", "jvm", "explicit typed schema and JSON construction");
        guidance(out, "async", "jvm", "synchronous host adapter");
        guidance(out, "genericVariance", "generics", "invariant generics", "explicit conversion helpers");
        guidance(out, "operatorOverloading", "language", "named methods");
        guidance(out, "pipeline", "language", "ordinary statements");
        guidance(out, "stringInterpolation", "strings", "+ concatenation");
        guidance(out, "charType", "strings", "one-code-point String elements via indexing and iteration");
        guidance(out, "tuples", "language", "classes or variants with named fields");
        guidance(out, "destructuring", "language", "explicit field access");
        out.put("packageRegistry", Map.of("supported", true, "helpTopic", "dependencies",
                "rules", List.of("a registry is an index of where packages live: packages/NAME.toml with the Git repository, subdir and releases",
                        "sprig add NAME [--version V] writes the Git dependency the index names; the lock pins the commit as for any Git dependency",
                        "sprig search [TEXT] lists packages; sprig publish writes this package's entry into a local registry directory",
                        "[[registry]] tables name registries by path or Git url; without any, the Sprig repository's registry directory is the default",
                        "there is no central hosted registry, no authentication and no upload: publishing is a commit to an index repository")));
        guidance(out, "centralSprigRegistry", "dependencies", "local path dependencies", "Git dependencies");
        return out;
    }

    private static void guidance(Map<String, Object> out, String feature, String helpTopic, String... alternatives) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("supported", false);
        entry.put("alternatives", List.of(alternatives));
        entry.put("helpTopic", helpTopic);
        out.put(feature, entry);
    }
}
