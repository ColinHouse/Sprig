package sprig.compiler.project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * Minimal, deliberately strict TOML subset used by {@code sprig.toml}:
 * {@code [table]}, {@code [[array-of-table]]}, {@code key = "string"} and
 * {@code key = ["a", "b"]}. Comments start with {@code #}. Anything else is a
 * manifest error; Sprig does not guess at unsupported TOML.
 */
public final class Toml {
    /** Thrown with a 1-based line number for user-facing diagnostics. */
    public static final class TomlException extends RuntimeException {
        public final int line;

        public TomlException(String message, int line) {
            super(message);
            this.line = line;
        }
    }

    private final Map<String, String> scalars = new LinkedHashMap<>();
    private final Map<String, List<String>> arrays = new LinkedHashMap<>();
    private final Map<String, List<String>> scopedArrays = new LinkedHashMap<>();
    private final Map<String, List<Map<String, String>>> tables = new LinkedHashMap<>();
    private final Map<String, List<Map<String, String>>> tableArrays = new LinkedHashMap<>();
    private String currentTable = "";
    private Map<String, String> currentEntry;
    private boolean allowBareValues;

    private Toml() {
    }

    public static Toml parse(List<String> lines) {
        return parse(lines, false);
    }

    /** Generated files such as sprig.lock may use bare numbers/booleans. */
    public static Toml parse(List<String> lines, boolean allowBareValues) {
        Toml toml = new Toml();
        toml.allowBareValues = allowBareValues;
        for (int i = 0; i < lines.size(); i++) {
            toml.line(lines.get(i), i + 1);
        }
        return toml;
    }

    private void line(String raw, int lineNumber) {
        String line = stripComment(raw).trim();
        if (line.isEmpty()) {
            return;
        }
        if (line.startsWith("[[") && line.endsWith("]]")) {
            String name = line.substring(2, line.length() - 2).trim();
            requireName(name, lineNumber);
            if (!allowBareValues && !Set.of("bin", "dependency", "jvm").contains(name)) {
                throw new TomlException("Unknown array table '" + name + "'", lineNumber);
            }
            currentTable = name;
            currentEntry = new LinkedHashMap<>();
            tableArrays.computeIfAbsent(name, key -> new ArrayList<>()).add(currentEntry);
            return;
        }
        if (line.startsWith("[") && line.endsWith("]")) {
            String name = line.substring(1, line.length() - 1).trim();
            requireName(name, lineNumber);
            if (!allowBareValues) {
                if (!name.equals("project")) {
                    throw new TomlException("Unknown table '" + name + "'", lineNumber);
                }
                if (tables.containsKey(name)) {
                    throw new TomlException("Duplicate table '" + name + "'", lineNumber);
                }
            }
            currentTable = name;
            currentEntry = null;
            tables.computeIfAbsent(name, key -> new ArrayList<>()).add(new LinkedHashMap<>());
            return;
        }
        int equals = line.indexOf('=');
        if (equals < 0) {
            throw new TomlException("Expected 'key = value'", lineNumber);
        }
        String key = line.substring(0, equals).trim();
        String value = line.substring(equals + 1).trim();
        if (key.isEmpty()) {
            throw new TomlException("Missing key before '='", lineNumber);
        }
        if (!allowBareValues) {
            Set<String> allowed = switch (currentTable) {
                case "" -> currentEntry == null ? Set.of("exports") : Set.of();
                case "project" -> Set.of("name", "version", "language", "source", "entry", "exports");
                case "bin" -> Set.of("name", "entry");
                case "dependency" -> Set.of("name", "path", "git", "branch");
                case "jvm" -> Set.of("group", "artifact", "version");
                default -> Set.of();
            };
            if (!allowed.contains(key)) {
                throw new TomlException("Unknown key '" + key + "' in '"
                        + currentTable + "'", lineNumber);
            }
        }
        boolean topLevel = currentEntry == null && currentTable.isEmpty();
        if (value.startsWith("[")) {
            List<String> parsedArray = parseArray(value, lineNumber);
            if (currentEntry == null) {
                if (topLevel) {
                    if (!allowBareValues && arrays.containsKey(key)) {
                        throw new TomlException("Duplicate key '" + key + "'", lineNumber);
                    }
                    arrays.put(key, parsedArray);
                } else {
                    scopedArrays.put(currentTable + "." + key, parsedArray);
                }
            }
            record(key, null, lineNumber);
            return;
        }
        String parsedValue = parseString(value, lineNumber);
        if (topLevel) {
            if (!allowBareValues && scalars.containsKey(key)) {
                throw new TomlException("Duplicate key '" + key + "'", lineNumber);
            }
            scalars.put(key, parsedValue);
        }
        record(key, parsedValue, lineNumber);
    }

    private void record(String key, String value, int lineNumber) {
        if (currentEntry != null) {
            if (currentEntry.containsKey(key)) {
                throw new TomlException("Duplicate key '" + key + "'", lineNumber);
            }
            currentEntry.put(key, value == null ? "" : value);
        } else if (!currentTable.isEmpty()) {
            Map<String, String> table = last(tables.get(currentTable));
            if (table.containsKey(key)) {
                throw new TomlException("Duplicate key '" + key + "'", lineNumber);
            }
            table.put(key, value == null ? "" : value);
        }
    }

    private static void requireName(String name, int lineNumber) {
        if (name.isEmpty() || !name.matches("[A-Za-z0-9_.-]+")) {
            throw new TomlException("Invalid table name '" + name + "'", lineNumber);
        }
    }

    private static List<String> parseArray(String value, int lineNumber) {
        if (!value.endsWith("]")) {
            throw new TomlException("Unterminated array", lineNumber);
        }
        String body = value.substring(1, value.length() - 1).trim();
        List<String> items = new ArrayList<>();
        if (body.isEmpty()) {
            return items;
        }
        int index = 0;
        while (index < body.length()) {
            while (index < body.length() && body.charAt(index) == ' ') {
                index++;
            }
            if (index >= body.length()) {
                break;
            }
            if (body.charAt(index) != '"') {
                throw new TomlException("Array items must be quoted strings", lineNumber);
            }
            int end = index + 1;
            while (end < body.length() && body.charAt(end) != '"') {
                end++;
            }
            if (end >= body.length()) {
                throw new TomlException("Unterminated string in array", lineNumber);
            }
            items.add(body.substring(index + 1, end));
            index = end + 1;
            while (index < body.length() && body.charAt(index) == ' ') {
                index++;
            }
            if (index < body.length()) {
                if (body.charAt(index) != ',') {
                    throw new TomlException("Expected ',' between array items", lineNumber);
                }
                index++;
                int probe = index;
                while (probe < body.length() && body.charAt(probe) == ' ') {
                    probe++;
                }
                if (probe >= body.length()) {
                    break;
                }
                if (body.charAt(probe) == ',') {
                    throw new TomlException("Empty array item", lineNumber);
                }
                index = probe;
            }
        }
        return items;
    }

    private String parseString(String value, int lineNumber) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            String body = value.substring(1, value.length() - 1);
            if (!allowBareValues) {
                for (int i = 0; i < body.length(); i++) {
                    if (body.charAt(i) == '"' && (i == 0 || body.charAt(i - 1) != '\\')) {
                        throw new TomlException("Unexpected quote inside a string value", lineNumber);
                    }
                }
            }
            return body;
        }
        if (allowBareValues && value.matches("[A-Za-z0-9_.+-]+")) {
            return value;
        }
        throw new TomlException("Values must be quoted strings in sprig.toml", lineNumber);
    }

    private static String stripComment(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inString = !inString;
            } else if (c == '#' && !inString) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static Map<String, String> last(List<Map<String, String>> list) {
        return list.get(list.size() - 1);
    }

    // ---- typed accessors -------------------------------------------------

    public String scalar(String table, String key) {
        if (table.isEmpty()) {
            return scalars.get(key);
        }
        List<Map<String, String>> list = tables.get(table);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return last(list).get(key);
    }

    public List<String> array(String key) {
        List<String> value = arrays.get(key);
        return value == null ? List.of() : value;
    }

    /** Array value declared inside {@code [table]}. */
    public List<String> array(String table, String key) {
        List<String> value = scopedArrays.get(table + "." + key);
        return value == null ? List.of() : value;
    }

    public List<Map<String, String>> entries(String table) {
        List<Map<String, String>> value = tableArrays.get(table);
        return value == null ? List.of() : value;
    }

    public Map<String, String> table(String table) {
        List<Map<String, String>> list = tables.get(table);
        return list == null || list.isEmpty() ? Map.of() : last(list);
    }

    public Map<String, List<Map<String, String>>> tableArrays() {
        return tableArrays;
    }
}
