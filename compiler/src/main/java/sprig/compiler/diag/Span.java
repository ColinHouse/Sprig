package sprig.compiler.diag;

/** Half-open source range; line/character are zero-based for machine output. */
public final class Span {
    public final int startLine;
    public final int startColumn;
    public final int endLine;
    public final int endColumn;
    public final int startOffset;
    public final int endOffset;

    public Span(int startLine, int startColumn, int endLine, int endColumn,
                int startOffset, int endOffset) {
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    public static Span point(int line, int column) {
        return new Span(line, column, line, column, -1, -1);
    }

    public Span through(Span other) {
        if (other == null) {
            return this;
        }
        return new Span(startLine, startColumn, other.endLine, other.endColumn,
                Math.min(startOffset, other.startOffset), Math.max(endOffset, other.endOffset));
    }

    public String display() {
        return (startLine + 1) + ":" + (startColumn + 1);
    }

    @Override
    public String toString() {
        return display();
    }
}
