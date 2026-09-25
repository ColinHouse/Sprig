package sprig.compiler.tooling;

import java.util.Map;
import sprig.compiler.diag.JsonWriter;

/** Small JSON encoder for the read-only CLI metadata structures. */
public final class ToolJson {
    private ToolJson() {}

    public static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof String string) return "\"" + JsonWriter.escape(string) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (out.length() > 1) out.append(',');
                out.append(encode(entry.getKey().toString())).append(':').append(encode(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> items) {
            StringBuilder out = new StringBuilder("[");
            for (Object item : items) {
                if (out.length() > 1) out.append(',');
                out.append(encode(item));
            }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
    }
}
