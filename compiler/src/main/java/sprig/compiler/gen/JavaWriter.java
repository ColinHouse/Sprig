package sprig.compiler.gen;

import java.util.LinkedHashMap;
import java.util.Map;
import sprig.compiler.diag.Span;

/** Indentation-aware Java source writer that also records a Sprig line map. */
public final class JavaWriter {
    private final StringBuilder sb = new StringBuilder();
    private final Map<Integer, Span> lineMap = new LinkedHashMap<>();
    private final String uri;
    private int indent;
    private int line;

    public JavaWriter(String uri) {
        this.uri = uri;
    }

    public void line(String text) {
        for (int i = 0; i < indent; i++) {
            sb.append("    ");
        }
        sb.append(text).append('\n');
        line += 1 + countNewlines(text);
    }

    private static int countNewlines(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    public void indent() {
        indent++;
    }

    public void dedent() {
        indent--;
    }

    public void blank() {
        sb.append('\n');
        line++;
    }

    public void open(String text) {
        line(text + " {");
        indent++;
    }

    public void close() {
        indent--;
        line("}");
    }

    /** Records the Sprig span for the next emitted line. */
    public void map(Span span) {
        if (span != null) {
            lineMap.put(line, span);
        }
    }

    public String source() {
        return sb.toString();
    }

    public Map<Integer, Span> lineMap() {
        return lineMap;
    }

    public String uri() {
        return uri;
    }
}
