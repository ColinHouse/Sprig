package sprig.compiler.ast;

import sprig.compiler.diag.Span;

/** Base class of every AST node; every node carries its Sprig source span. */
public abstract class Node {
    public Span span;

    public Span span() {
        return span;
    }
}
