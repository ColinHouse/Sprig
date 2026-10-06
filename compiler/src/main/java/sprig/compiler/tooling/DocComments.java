package sprig.compiler.tooling;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * The comment written directly above a declaration. `sprig api` and editor
 * hover read it with the same rule, so both show the same text.
 */
public final class DocComments {
    private DocComments() {}

    /**
     * The '#' lines directly above {@code declarationLine}, without their '#'
     * and one following space, or null. Lines are zero-based, as in
     * {@link sprig.compiler.diag.Span}. A {@code generic} header above the
     * declaration is skipped; a blank line or code ends the comment.
     */
    public static String above(IntFunction<String> lineAt, int declarationLine) {
        int line = declarationLine - 1;
        if (line >= 0 && lineAt.apply(line).trim().startsWith("generic ")) {
            line--;
        }
        List<String> lines = new ArrayList<>();
        while (line >= 0) {
            String content = lineAt.apply(line).trim();
            if (!content.startsWith("#")) {
                break;
            }
            String body = content.substring(1);
            lines.add(0, body.startsWith(" ") ? body.substring(1) : body);
            line--;
        }
        return lines.isEmpty() ? null : String.join("\n", lines).strip();
    }

    /** {@link #above(IntFunction, int)} over a whole source text; null without source. */
    public static String above(String source, int declarationLine) {
        if (source == null) {
            return null;
        }
        String[] lines = source.split("\r?\n", -1);
        return above(index -> index >= 0 && index < lines.length ? lines[index] : "", declarationLine);
    }
}
