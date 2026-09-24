import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;
import org.antlr.v4.runtime.TokenSource;

/**
 * Reference Java layout adapter for Sprig. Generates logical NEWLINE/INDENT/
 * DEDENT from physical NEWLINE and token source columns; ignores newlines in
 * (), [] and {}. This intentionally buffers the file (not an incremental LSP
 * lexer). User-friendly diagnostics and incremental lexing are future work.
 */
public final class LayoutTokenSource implements TokenSource {
    private final TokenSource lexer;
    private final List<Token> output = new ArrayList<>();
    private int cursor = 0;

    public LayoutTokenSource(TokenSource lexer) {
        this.lexer = lexer;
        List<Token> raw = new ArrayList<>();
        while (true) {
            Token t = lexer.nextToken();
            raw.add(t);
            if (t.getType() == Token.EOF) break;
        }
        normalize(raw);
    }

    private CommonToken generated(int type, String text, Token near) {
        // Attach a TokenSource so ANTLR error recovery never dereferences a null
        // source on synthetic layout tokens.
        CommonToken token = new CommonToken(
                new org.antlr.v4.runtime.misc.Pair<>(lexer, lexer.getInputStream()),
                type, Token.DEFAULT_CHANNEL, near.getStartIndex(), near.getStartIndex() - 1);
        token.setText(text);
        token.setLine(near.getLine());
        token.setCharPositionInLine(near.getCharPositionInLine());
        token.setStartIndex(near.getStartIndex());
        token.setStopIndex(near.getStartIndex() - 1); // synthetic, zero-width
        return token;
    }

    private void newline(Token near) {
        if (output.isEmpty()) return;
        int last = output.get(output.size() - 1).getType();
        if (last != SprigLexer.NEWLINE && last != SprigLexer.INDENT
                && last != SprigLexer.DEDENT) {
            output.add(generated(SprigLexer.NEWLINE, "\\n", near));
        }
    }

    private static String position(Token t) {
        return "line " + t.getLine() + ", column " + (t.getCharPositionInLine() + 1);
    }

    private void normalize(List<Token> raw) {
        Deque<Integer> indents = new ArrayDeque<>();
        indents.push(0);
        int brackets = 0;
        for (int i = 0; i < raw.size(); i++) {
            Token token = raw.get(i);
            int type = token.getType();
            if (type == SprigLexer.TAB) {
                throw new IllegalArgumentException("Tabs outside strings are not allowed: "
                        + position(token));
            }
            if (type == SprigLexer.ERROR_CHAR) {
                throw new IllegalArgumentException("Invalid character '" + token.getText()
                        + "' at " + position(token));
            }
            if (type == SprigLexer.NEWLINE) {
                if (brackets > 0) continue;
                int j = i + 1;
                while (j < raw.size() && raw.get(j).getType() == SprigLexer.NEWLINE) j++;
                Token next = raw.get(j); // EOF is always the final token
                newline(token);
                if (next.getType() != Token.EOF) {
                    adjustIndent(next.getCharPositionInLine(), next, indents);
                }
                i = j - 1;
                continue;
            }
            if (type == Token.EOF) {
                if (brackets != 0) throw new IllegalArgumentException("Unclosed grouping delimiter");
                newline(token);
                while (indents.size() > 1) {
                    indents.pop();
                    output.add(generated(SprigLexer.DEDENT, "<DEDENT>", token));
                }
                output.add(token);
                return;
            }
            if (output.isEmpty() && token.getCharPositionInLine() != 0) {
                throw new IllegalArgumentException("Unexpected indentation on first code line: "
                        + position(token));
            }
            output.add(token);
            if (type == SprigLexer.LPAREN || type == SprigLexer.LBRACK
                    || type == SprigLexer.LBRACE) {
                brackets++;
            } else if (type == SprigLexer.RPAREN || type == SprigLexer.RBRACK
                    || type == SprigLexer.RBRACE) {
                brackets--;
                if (brackets < 0) {
                    throw new IllegalArgumentException("Unmatched closing delimiter: " + position(token));
                }
            }
        }
        throw new IllegalStateException("Lexer terminated without EOF");
    }

    private void adjustIndent(int column, Token near, Deque<Integer> indents) {
        if (output.isEmpty() && column != 0) {
            throw new IllegalArgumentException("Unexpected indentation on first code line: " + position(near));
        }
        int current = indents.peek();
        if (column > current) {
            indents.push(column);
            output.add(generated(SprigLexer.INDENT, "<INDENT>", near));
        } else if (column < current) {
            while (indents.size() > 1 && column < indents.peek()) {
                indents.pop();
                output.add(generated(SprigLexer.DEDENT, "<DEDENT>", near));
            }
            if (column != indents.peek()) {
                throw new IllegalArgumentException("Inconsistent indentation: " + position(near));
            }
        }
    }

    @Override public Token nextToken() { return output.get(cursor++); }
    @Override public int getLine() { return lexer.getLine(); }
    @Override public int getCharPositionInLine() { return lexer.getCharPositionInLine(); }
    @Override public CharStream getInputStream() { return lexer.getInputStream(); }
    @Override public String getSourceName() { return lexer.getSourceName(); }
    @Override public void setTokenFactory(TokenFactory<?> factory) { lexer.setTokenFactory(factory); }
    @Override public TokenFactory<?> getTokenFactory() { return lexer.getTokenFactory(); }
}
