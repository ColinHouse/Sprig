package sprig.compiler.lsp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON reader for language server messages. Objects become
 * {@link LinkedHashMap}, arrays {@link ArrayList}, integral numbers
 * {@link Long} and other numbers {@link Double}. Writing uses
 * {@link sprig.compiler.tooling.ToolJson}.
 */
final class Json {
    private Json() {
    }

    static final class ParseException extends RuntimeException {
        ParseException(String message) {
            super(message);
        }
    }

    static Object parse(String text) {
        Reader reader = new Reader(text);
        reader.skipSpace();
        Object value = reader.value();
        reader.skipSpace();
        if (reader.pos != text.length()) {
            throw new ParseException("Unexpected text after JSON value at " + reader.pos);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    static Map<String, Object> object(Map<String, Object> parent, String key) {
        return parent == null ? null : object(parent.get(key));
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : null;
    }

    static String string(Map<String, Object> parent, String key) {
        return parent != null && parent.get(key) instanceof String text ? text : null;
    }

    static int integer(Map<String, Object> parent, String key, int fallback) {
        return parent != null && parent.get(key) instanceof Number number ? number.intValue() : fallback;
    }

    static boolean bool(Map<String, Object> parent, String key) {
        return parent != null && Boolean.TRUE.equals(parent.get(key));
    }

    private static final class Reader {
        private final String text;
        private int pos;

        Reader(String text) {
            this.text = text;
        }

        void skipSpace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                    return;
                }
                pos++;
            }
        }

        Object value() {
            if (pos >= text.length()) {
                throw new ParseException("Unexpected end of JSON");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, pos)) {
                throw new ParseException("Invalid JSON literal at " + pos);
            }
            pos += word.length();
            return value;
        }

        private Map<String, Object> object() {
            Map<String, Object> out = new LinkedHashMap<>();
            pos++;
            skipSpace();
            if (peek() == '}') {
                pos++;
                return out;
            }
            while (true) {
                skipSpace();
                if (peek() != '"') {
                    throw new ParseException("Expected an object key at " + pos);
                }
                String key = string();
                skipSpace();
                expect(':');
                skipSpace();
                out.put(key, value());
                skipSpace();
                char next = next();
                if (next == '}') {
                    return out;
                }
                if (next != ',') {
                    throw new ParseException("Expected ',' or '}' at " + (pos - 1));
                }
            }
        }

        private List<Object> array() {
            List<Object> out = new ArrayList<>();
            pos++;
            skipSpace();
            if (peek() == ']') {
                pos++;
                return out;
            }
            while (true) {
                skipSpace();
                out.add(value());
                skipSpace();
                char next = next();
                if (next == ']') {
                    return out;
                }
                if (next != ',') {
                    throw new ParseException("Expected ',' or ']' at " + (pos - 1));
                }
            }
        }

        private String string() {
            pos++;
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escape = next();
                switch (escape) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw new ParseException("Truncated \\u escape at " + pos);
                        }
                        try {
                            out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException e) {
                            throw new ParseException("Invalid \\u escape at " + pos);
                        }
                        pos += 4;
                    }
                    default -> throw new ParseException("Invalid escape at " + (pos - 1));
                }
            }
        }

        private Object number() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            boolean integral = true;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    integral = false;
                    pos++;
                } else {
                    break;
                }
            }
            String digits = text.substring(start, pos);
            if (digits.isEmpty() || digits.equals("-")) {
                throw new ParseException("Invalid JSON value at " + start);
            }
            try {
                if (integral) {
                    try {
                        return Long.parseLong(digits);
                    } catch (NumberFormatException tooLarge) {
                        return Double.parseDouble(digits);
                    }
                }
                return Double.parseDouble(digits);
            } catch (NumberFormatException e) {
                throw new ParseException("Invalid JSON number at " + start);
            }
        }

        private char peek() {
            return pos < text.length() ? text.charAt(pos) : '\0';
        }

        private char next() {
            if (pos >= text.length()) {
                throw new ParseException("Unexpected end of JSON");
            }
            return text.charAt(pos++);
        }

        private void expect(char c) {
            if (next() != c) {
                throw new ParseException("Expected '" + c + "' at " + (pos - 1));
            }
        }
    }
}
