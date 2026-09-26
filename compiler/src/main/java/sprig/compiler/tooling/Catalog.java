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
        return List.of("language", "types", "functions", "classes", "variants", "match",
                "nullability", "errors", "collections", "numerics", "modules", "jvm",
                "generics", "projects", "dependencies", "agents");
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
        for (String key : List.of("commands", "nativeTypes", "collectionTypes", "supportedSyntax", "unsupportedSyntax")) {
            result.put(key, list(key));
        }
        Map<String, Object> features = new LinkedHashMap<>();
        DATA.stringPropertyNames().stream().filter(k -> k.startsWith("feature.")).sorted()
                .forEach(k -> features.put(k.substring(8), Boolean.parseBoolean(get(k))));
        result.put("features", features);
        result.put("lambdaMaxArity", Integer.parseInt(get("lambdaMaxArity")));
        result.put("matchBehavior", "statement; exhaustive; no wildcard");
        result.put("numericSemanticsProfile", get("numericProfile"));
        result.put("jvmInterop", get("jvmProfile"));
        result.put("classpath", get("classpathPolicy"));
        result.put("sourceModules", true);
        result.put("diagnosticSchemaVersion", 1);
        return result;
    }
}
