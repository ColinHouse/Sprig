package sprig.compiler.diag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects diagnostics across phases. */
public final class Diagnostics {
    private final List<Diagnostic> items = new ArrayList<>();
    private int errorCount;

    public void add(Diagnostic diagnostic) {
        items.add(diagnostic);
        if (diagnostic.isError()) {
            errorCount++;
        }
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
