package sprig.compiler.diag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects diagnostics across phases. */
public final class Diagnostics {
    private final List<Diagnostic> items = new ArrayList<>();
    private int errorCount;
    private int errorReports;
    private int blockingErrorCount;

    public void add(Diagnostic diagnostic) {
        if (diagnostic.isError()) {
            errorReports++;
        }
        // A phase may check one expression twice, such as a top-level
        // initializer that is typed once to establish the binding's type and
        // once more in statement order. The same report twice says nothing new.
        for (Diagnostic existing : items) {
            if (same(existing, diagnostic)) {
                return;
            }
        }
        RepairHints.apply(diagnostic);
        items.add(diagnostic);
        if (diagnostic.isError()) {
            errorCount++;
            if (!diagnostic.recoverable) {
                blockingErrorCount++;
            }
        }
    }

    private static boolean same(Diagnostic a, Diagnostic b) {
        return a.code.equals(b.code) && a.phase == b.phase && a.severity == b.severity
                && a.message.equals(b.message) && java.util.Objects.equals(a.uri, b.uri)
                && java.util.Objects.equals(a.span == null ? null : a.span.toString(),
                        b.span == null ? null : b.span.toString());
    }

    /**
     * Whether an error stops the compiler from going on to the next phase.
     * A recoverable error ({@link Diagnostic#recoverable}) does not; it still
     * counts as an error for {@link #hasErrors()} and the exit code.
     */
    public boolean hasBlockingErrors() {
        return blockingErrorCount > 0;
    }

    public void error(String code, Phase phase, String message, String uri, Span span) {
        add(Diagnostic.error(code, phase, message, uri, span));
    }

    public boolean hasErrors() {
        return errorCount > 0;
    }

    public int errorCount() {
        return errorCount;
    }

    /**
     * Every error report, duplicates included. {@link #errorCount()} counts the
     * errors kept; a caller that only asks whether an error was reported (a
     * failed inference must not stay silent when its own pass drops a repeat)
     * uses this mark instead.
     */
    public int errorReportCount() {
        return errorReports;
    }

    public List<Diagnostic> all() {
        return Collections.unmodifiableList(items);
    }

    public List<Diagnostic> errors() {
        List<Diagnostic> out = new ArrayList<>();
        for (Diagnostic d : items) {
            if (d.isError()) {
                out.add(d);
            }
        }
        return out;
    }
}
