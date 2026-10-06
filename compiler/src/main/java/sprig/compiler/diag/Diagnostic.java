package sprig.compiler.diag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    public String relatedHelp;
    public Map<String, Object> repair;
    public Map<String, Object> data;

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

    public Diagnostic withRelatedHelp(String topic) {
        this.relatedHelp = topic;
        return this;
    }

    /**
     * A repair strategy is guidance, not permission to invent semantics.
     * {@code machineApplicable} is true only for a semantics-preserving,
     * unambiguous source edit.
     */
    public Diagnostic withRepair(String kind, boolean machineApplicable) {
        java.util.LinkedHashMap<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("kind", kind);
        value.put("machineApplicable", machineApplicable);
        this.repair = value;
        return this;
    }

    public Diagnostic withData(Map<String, Object> data) {
        this.data = data;
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
        return format(true);
    }

    /** The text form; a caller that already showed this hint can leave it out. */
    public String format(boolean includeHint) {
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
        if (hint != null && includeHint) {
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
