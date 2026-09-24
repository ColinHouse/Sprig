package sprig.compiler.diag;

import java.util.List;

/** Minimal JSON writer for machine-readable diagnostics (no external deps). */
public final class JsonWriter {
    private JsonWriter() {
    }

    public static String diagnostics(List<Diagnostic> diagnostics, String uriForNulls) {
        return result(diagnostics, uriForNulls, null, null, null);
    }

    public static String result(List<Diagnostic> diagnostics, String uriForNulls,
                                String command, Integer exitCode, String programOutput) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"schemaVersion\": 1,\n");
        sb.append("  \"toolVersion\": \"").append(escape(Version.VERSION)).append("\",\n");
        if (command != null) {
            sb.append("  \"command\": \"").append(escape(command)).append("\",\n");
        }
        if (exitCode != null) {
            sb.append("  \"exitCode\": ").append(exitCode).append(",\n");
        }
        if (programOutput != null) {
            sb.append("  \"programOutput\": \"").append(escape(programOutput)).append("\",\n");
        }
        sb.append("  \"diagnostics\": [");
        for (int i = 0; i < diagnostics.size(); i++) {
            Diagnostic d = diagnostics.get(i);
            sb.append(i == 0 ? "\n" : ",\n");
            appendDiagnostic(sb, d, uriForNulls, "    ");
        }
        if (!diagnostics.isEmpty()) {
            sb.append('\n');
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendDiagnostic(StringBuilder sb, Diagnostic d, String fallbackUri, String indent) {
        sb.append(indent).append("{\n");
        sb.append(indent).append("  \"code\": \"").append(escape(d.code)).append("\",\n");
        sb.append(indent).append("  \"phase\": \"").append(d.phase).append("\",\n");
        sb.append(indent).append("  \"severity\": \"").append(d.severity.name().toLowerCase()).append("\",\n");
        String uri = d.uri != null ? d.uri : fallbackUri;
        sb.append(indent).append("  \"uri\": ");
        if (uri == null) sb.append("null");
        else sb.append('"').append(escape(uri)).append('"');
        sb.append(",\n");
        sb.append(indent).append("  \"range\": ");
        if (d.span == null) {
            sb.append("null,\n");
        } else {
            sb.append("{\"start\": {\"line\": ").append(d.span.startLine)
              .append(", \"character\": ").append(d.span.startColumn)
              .append("}, \"end\": {\"line\": ").append(d.span.endLine)
              .append(", \"character\": ").append(d.span.endColumn).append("}},\n");
        }
        sb.append(indent).append("  \"message\": \"").append(escape(d.message)).append("\"");
        if (d.expectedType != null) {
            sb.append(",\n").append(indent).append("  \"expectedType\": \"").append(escape(d.expectedType)).append("\"");
        }
        if (d.actualType != null) {
            sb.append(",\n").append(indent).append("  \"actualType\": \"").append(escape(d.actualType)).append("\"");
        }
        if (d.hint != null) {
            sb.append(",\n").append(indent).append("  \"hint\": \"").append(escape(d.hint)).append("\"");
        }
        sb.append(",\n").append(indent).append("  \"related\": [");
        for (int i = 0; i < d.related.size(); i++) {
            Diagnostic.Related r = d.related.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("\n").append(indent).append("    {\"message\": \"").append(escape(r.message)).append("\"");
            if (r.span != null) {
                sb.append(", \"range\": {\"start\": {\"line\": ").append(r.span.startLine)
                  .append(", \"character\": ").append(r.span.startColumn)
                  .append("}, \"end\": {\"line\": ").append(r.span.endLine)
                  .append(", \"character\": ").append(r.span.endColumn).append("}}");
            }
            sb.append("}");
        }
        if (!d.related.isEmpty()) {
            sb.append("\n").append(indent).append("  ");
        }
        sb.append("],\n");
        sb.append(indent).append("  \"suggestedEdits\": []\n");
        sb.append(indent).append("}");
    }

    public static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
