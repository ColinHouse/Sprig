package sprig.compiler.lsp;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import sprig.compiler.lsp.SymbolIndex.Occurrence;

/** Full semantic tokens from the current compilation's resolved names. */
final class SemanticTokens {
    static final List<String> TYPES = List.of("namespace", "class", "enum", "enumMember", "function",
            "method", "parameter", "variable", "property", "type", "typeParameter");
    static final List<String> MODIFIERS = List.of("declaration", "readonly");

    private SemanticTokens() {
    }

    static Map<String, Object> legend() {
        return Map.of("tokenTypes", TYPES, "tokenModifiers", MODIFIERS);
    }

    static List<Integer> full(Analysis analysis, Path path) {
        if (!analysis.resolved()) {
            return List.of();
        }
        TextLines lines = analysis.text(path);
        List<Occurrence> occurrences = new ArrayList<>(analysis.index().occurrences(path));
        occurrences.sort(Comparator.comparingInt((Occurrence o) -> o.span.startLine)
                .thenComparingInt(o -> o.span.startColumn));
        List<Integer> data = new ArrayList<>();
        int previousLine = 0, previousColumn = 0, previousEnd = -1;
        for (Occurrence occurrence : occurrences) {
            var span = occurrence.span;
            String kind = kind(occurrence);
            if (kind == null || span.startLine != span.endLine) {
                continue;
            }
            int line = span.startLine;
            int column = lines.utf16(line, span.startColumn);
            int end = lines.utf16(line, span.endColumn);
            if (end <= column || (line == previousLine && column < previousEnd)) {
                continue;
            }
            int modifiers = occurrence.declaration ? 1 : 0;
            if (occurrence.target != null && occurrence.target.symbol != null
                    && !occurrence.target.symbol.mutable
                    && (kind.equals("variable") || kind.equals("property") || kind.equals("parameter"))) {
                modifiers |= 2;
            }
            data.add(line - previousLine);
            data.add(line == previousLine ? column - previousColumn : column);
            data.add(end - column);
            data.add(TYPES.indexOf(kind));
            data.add(modifiers);
            previousLine = line;
            previousColumn = column;
            previousEnd = end;
        }
        return data;
    }

    private static String kind(Occurrence occurrence) {
        if (occurrence.target != null) {
            return switch (occurrence.target.kind) {
                case MODULE -> "namespace";
                case CLASS -> "class";
                case ENUM, VARIANT -> "enum";
                case ENUM_CASE, VARIANT_CASE -> "enumMember";
                case FUNCTION -> "function";
                case METHOD -> "method";
                case PARAM -> "parameter";
                case LOCAL, TOP_VAR -> "variable";
                case FIELD, PAYLOAD_FIELD -> "property";
            };
        }
        if (occurrence.info == null) {
            return null;
        }
        return occurrence.info.semanticKind();
    }
}
