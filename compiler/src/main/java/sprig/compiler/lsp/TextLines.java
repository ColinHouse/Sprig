package sprig.compiler.lsp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import sprig.compiler.diag.Span;

/**
 * Line table for one source text. Compiler spans count Unicode code points
 * per line; LSP positions count UTF-16 code units. Lines break at {@code \n},
 * as the lexer counts them.
 */
final class TextLines {
    final String text;
    private final int[] starts;

    TextLines(String text) {
        this.text = text;
        List<Integer> lineStarts = new ArrayList<>();
        lineStarts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lineStarts.add(i + 1);
            }
        }
        starts = lineStarts.stream().mapToInt(Integer::intValue).toArray();
    }

    int lineCount() {
        return starts.length;
    }

    /** Line text without its line break. */
    String line(int line) {
        if (line < 0 || line >= starts.length) {
            return "";
        }
        int end = line + 1 < starts.length ? starts[line + 1] - 1 : text.length();
        return text.substring(starts[line], Math.max(starts[line], end));
    }

    /** UTF-16 offset of an LSP position, clamped to the text. */
    int offset(int line, int character) {
        if (line < 0) {
            return 0;
        }
        if (line >= starts.length) {
            return text.length();
        }
        return starts[line] + Math.max(0, Math.min(character, line(line).length()));
    }

    /** UTF-16 character of a compiler (code point) column. */
    int utf16(int line, int codePointColumn) {
        String content = line(line);
        int index = 0;
        for (int i = 0; i < codePointColumn && index < content.length(); i++) {
            index += Character.charCount(content.codePointAt(index));
        }
        return index;
    }

    /** Compiler (code point) column of an LSP UTF-16 character. */
    int codePoints(int line, int character) {
        String content = line(line);
        int end = Math.max(0, Math.min(character, content.length()));
        return content.codePointCount(0, end);
    }

    Map<String, Object> position(int line, int codePointColumn) {
        int clampedLine = Math.max(0, Math.min(line, starts.length - 1));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("line", clampedLine);
        out.put("character", line == clampedLine ? utf16(clampedLine, codePointColumn) : 0);
        return out;
    }

    Map<String, Object> range(Span span) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (span == null) {
            out.put("start", position(0, 0));
            out.put("end", position(0, 0));
            return out;
        }
        out.put("start", position(span.startLine, span.startColumn));
        out.put("end", position(span.endLine, span.endColumn));
        return out;
    }

    /** The range of the whole text. */
    Map<String, Object> fullRange() {
        int last = starts.length - 1;
        Map<String, Object> end = new LinkedHashMap<>();
        end.put("line", last);
        end.put("character", line(last).length());
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("line", 0);
        start.put("character", 0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("start", start);
        out.put("end", end);
        return out;
    }
}
