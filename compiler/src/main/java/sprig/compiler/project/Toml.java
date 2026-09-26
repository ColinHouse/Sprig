package sprig.compiler.project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final Map<String, List<Map<String, String>>> tables = new LinkedHashMap<>();
    private final Map<String, List<Map<String, String>>> tableArrays = new LinkedHashMap<>();
    private String currentTable = "";
    private Map<String, String> currentEntry;

    private Toml() {
    }

    public static Toml parse(List<String> lines) {
        Toml toml = new Toml();
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
            if (!Set.of("bin", "dependency", "jvm").contains(name))
                throw new TomlException("Unknown array table '" + name + "'", lineNumber);
            currentTable = name;
            currentEntry = new LinkedHashMap<>();
            tableArrays.computeIfAbsent(name, key -> new ArrayList<>()).add(currentEntry);
            return;
        }
        if (line.startsWith("[") && line.endsWith("]")) {
            String name = line.substring(1, line.length() - 1).trim();
            requireName(name, lineNumber);
            if (!name.equals("project"))
                throw new TomlException("Unknown table '" + name + "'", lineNumber);
            if (tables.containsKey(name))
                throw new TomlException("Duplicate table '" + name + "'", lineNumber);
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
        Set<String> allowed = switch (currentTable) {
            case "" -> Set.of("exports");
            case "project" -> Set.of("name", "version", "language", "source", "entry");
            case "bin" -> Set.of("name", "entry");
            case "dependency" -> Set.of("name", "path", "git", "branch");
            case "jvm" -> Set.of("group", "artifact", "version");
            default -> Set.of();
        };
        if (!allowed.contains(key))
            throw new TomlException("Unknown key '" + key + "' in '" + currentTable + "'", lineNumber);
        if (currentTable.isEmpty()) {
            if (arrays.containsKey(key)) throw new TomlException("Duplicate key '" + key + "'", lineNumber);
            if (!value.startsWith("[")) throw new TomlException("exports must be a string array", lineNumber);
            arrays.put(key, parseArray(value, lineNumber));
        } else {
            String parsed = parseString(value, lineNumber);
            record(key, parsed, lineNumber);
        }
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

    private String scoped(String key) {
        if (currentEntry != null || currentTable.isEmpty()) {
            return key;
        }
        return currentTable + "." + key;
    }

    private static void requireName(String name, int lineNumber) {
        if (name.isEmpty() || !name.matches("[A-Za-z0-9_.-]+")) {
            throw new TomlException("Invalid table name '" + name + "'", lineNumber);
        }
    }

    private static List<String> parseArray(String value, int lineNumber) {
        if (!value.endsWith("]")) throw new TomlException("Unterminated array", lineNumber);
        String body = value.substring(1, value.length() - 1).trim();
        List<String> items = new ArrayList<>();
        int index = 0;
        while (index < body.length()) {
            int end = stringEnd(body, index, lineNumber);
            items.add(parseString(body.substring(index, end), lineNumber));
            index = end;
            while (index < body.length() && Character.isWhitespace(body.charAt(index))) index++;
            if (index == body.length()) break;
            if (body.charAt(index++) != ',') throw new TomlException("Expected ',' between array items", lineNumber);
            while (index < body.length() && Character.isWhitespace(body.charAt(index))) index++;
            // A single trailing comma is legal TOML; leading/doubled commas are not.
        }
        return items;
    }

    private static int stringEnd(String value, int start, int lineNumber) {
        if (start >= value.length() || value.charAt(start) != '"')
            throw new TomlException("Values must be quoted strings in sprig.toml", lineNumber);
        boolean escaped = false;
        for (int i = start + 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!escaped && c == '"') return i + 1;
            if (!escaped && c == '\\') escaped = true;
            else escaped = false;
        }
        throw new TomlException("Unterminated string", lineNumber);
    }

    private static String parseString(String value, int lineNumber) {
        if (stringEnd(value, 0, lineNumber) != value.length())
            throw new TomlException("Unexpected text after string", lineNumber);
        StringBuilder out = new StringBuilder();
        for (int i = 1; i < value.length() - 1; i++) {
            char c = value.charAt(i);
            if (c == '\\') {
                char escape = value.charAt(++i);
                c = switch (escape) {
                    case '"' -> '"'; case '\\' -> '\\';
                    case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                    default -> throw new TomlException("Unsupported string escape: " + escape, lineNumber);
                };
            } else if (c < 32 || c == 127) {
                throw new TomlException("Control character in string", lineNumber);
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String stripComment(String line) {
        boolean inString = false, escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inString && !escaped && c == '\\') { escaped = true; continue; }
            if (!escaped && c == '"') inString = !inString;
            else if (c == '#' && !inString) return line.substring(0, i);
            escaped = false;
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
