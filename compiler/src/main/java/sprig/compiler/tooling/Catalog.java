package sprig.compiler.tooling;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
                "generics", "projects", "dependencies", "agents", "upgrade", "fmt",
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
        result.put("examples", splitLines(get("help." + topic + ".examples")));
        return result;
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
                "jvmWildcards", "jvmVarargs")) {
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
        guidance(out, "arbitraryJavaSam", "jvm", "narrow Java adapter", "Sprig-owned fn(A) -> R and Fn0..Fn3");
        guidance(out, "genericTypeInference", "generics", "write every explicit Type[Arg] argument");
        out.put("matchExpression", Map.of("supported", true, "helpTopic", "match",
                "rules", List.of("one expression per case", "strict result typing", "no block expressions")));
        guidance(out, "wildcardMatch", "match", "list every enum/variant case explicitly");
        guidance(out, "arrays", "jvm", "foreign JVM array pass-through with exact classes", "byte[] helpers via sprig.runtime.jvm.HostBytes", "List[T] and @std/jvm adapters");
        guidance(out, "varargs", "jvm", "List[T]", "explicit repeated calls");
        guidance(out, "annotations", "language", "explicit typed metadata", "ordinary functions");
        guidance(out, "decorators", "language", "ordinary functions and modules");
        guidance(out, "macros", "language", "ordinary functions and modules");
        guidance(out, "blockLambdas", "functions", "named function plus expression lambda fn(x: T) => named(x)");
        guidance(out, "namedFunctionReferences", "functions", "expression lambda forwarding fn(x: T) => named(x)");
        guidance(out, "reflectionDerivedSchemas", "jvm", "explicit typed schema and JSON construction");
        guidance(out, "async", "jvm", "synchronous host adapter");
        guidance(out, "genericVariance", "generics", "invariant generics", "explicit conversion helpers");
        guidance(out, "operatorOverloading", "language", "named methods");
        guidance(out, "pipeline", "language", "ordinary statements");
        guidance(out, "stringInterpolation", "strings", "+ concatenation");
        guidance(out, "charType", "strings", "one-code-point String elements via indexing and iteration");
        guidance(out, "tuples", "language", "classes or variants with named fields");
        guidance(out, "destructuring", "language", "explicit field access");
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
