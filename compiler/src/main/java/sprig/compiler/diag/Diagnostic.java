package sprig.compiler.diag;

import java.util.ArrayList;
import java.util.List;

/** One compiler diagnostic with a stable code, phase, span and optional type info. */
public final class Diagnostic {
    public enum Severity { ERROR, WARNING }

    public final String code;
    public final Phase phase;
    public final Severity severity;
    public final String message;
    public final String uri;
    public final Span span;
    public final List<Related> related = new ArrayList<>();
    public String expectedType;
    public String actualType;
    public String hint;

    public Diagnostic(String code, Phase phase, Severity severity, String message, String uri, Span span) {
        this.code = code;
        this.phase = phase;
        this.severity = severity;
        this.message = message;
        this.uri = uri;
        this.span = span;
    }

    public static Diagnostic error(String code, Phase phase, String message, String uri, Span span) {
        return new Diagnostic(code, phase, Severity.ERROR, message, uri, span);
    }

    public Diagnostic withTypes(String expected, String actual) {
        this.expectedType = expected;
        this.actualType = actual;
        return this;
    }

    public Diagnostic withHint(String hint) {
        this.hint = hint;
        return this;
    }

    public Diagnostic withRelated(String message, Span span) {
        this.related.add(new Related(message, span));
        return this;
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append(code).append(" [").append(phase).append("] ");
        if (uri != null) {
            sb.append(shortUri(uri));
            if (span != null) {
                sb.append(':').append(span.display());
            }
            sb.append(": ");
        }
        sb.append(message);
        if (expectedType != null || actualType != null) {
            sb.append(" (expected ").append(expectedType == null ? "?" : expectedType)
              .append(", actual ").append(actualType == null ? "?" : actualType).append(')');
        }
        if (hint != null) {
            sb.append("\n  hint: ").append(hint);
        }
        for (Related r : related) {
            sb.append("\n  ").append(r.message);
            if (r.span != null) {
                sb.append(" at ").append(r.span.display());
            }
        }
        return sb.toString();
    }

    private static String shortUri(String uri) {
        int slash = uri.lastIndexOf('/');
        return slash >= 0 ? uri.substring(slash + 1) : uri;
    }

    public static final class Related {
        public final String message;
        public final Span span;

        public Related(String message, Span span) {
            this.message = message;
            this.span = span;
        }
    }
}
