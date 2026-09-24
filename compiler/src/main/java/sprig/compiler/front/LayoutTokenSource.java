package sprig.compiler.front;
import sprig.compiler.parser.SprigLexer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;
import org.antlr.v4.runtime.TokenSource;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;

/**
 * Layout adapter for Sprig: converts physical NEWLINE plus leading columns into
 * logical NEWLINE/INDENT/DEDENT. Newlines inside (), [] and {} are ignored.
 *
 * <p>Unlike the design-kit reference harness this version reports diagnostics
 * through the shared sink and keeps going (error recovery), so a single run can
 * report several layout problems.
 */
public final class LayoutTokenSource implements TokenSource {
    private final TokenSource lexer;
    private final Diagnostics diagnostics;
    private final String uri;
    private final List<Token> output = new ArrayList<>();
    private int cursor = 0;

    public LayoutTokenSource(TokenSource lexer, Diagnostics diagnostics, String uri) {
        this.lexer = lexer;
        this.diagnostics = diagnostics;
        this.uri = uri;
        List<Token> raw = new ArrayList<>();
        while (true) {
            Token t = lexer.nextToken();
            raw.add(t);
            if (t.getType() == Token.EOF) {
                break;
            }
        }
        normalize(raw);
    }

    private static Span spanOf(Token token) {
        int line = Math.max(0, token.getLine() - 1);
        int column = Math.max(0, token.getCharPositionInLine());
        int length = token.getText() == null ? 0 : token.getText().length();
        return new Span(line, column, line, column + length,
                token.getStartIndex(), token.getStopIndex() + 1);
    }

    private CommonToken generated(int type, String text, Token near) {
        CommonToken token = new CommonToken(
                new org.antlr.v4.runtime.misc.Pair<>(lexer, lexer.getInputStream()),
                type, Token.DEFAULT_CHANNEL, near.getStartIndex(), near.getStartIndex() - 1);
        token.setText(text);
        token.setLine(near.getLine());
        token.setCharPositionInLine(near.getCharPositionInLine());
        token.setStartIndex(near.getStartIndex());
        token.setStopIndex(near.getStartIndex() - 1);
        return token;
    }

    private void error(String code, String message, Token near, String hint) {
        Diagnostic diagnostic = Diagnostic.error(code, Phase.LEX, message, uri, spanOf(near));
        if (hint != null) {
            diagnostic.withHint(hint);
        }
        diagnostics.add(diagnostic);
    }

    private void newline(Token near) {
        if (output.isEmpty()) {
            return;
        }
        int last = output.get(output.size() - 1).getType();
        if (last != SprigLexer.NEWLINE && last != SprigLexer.INDENT && last != SprigLexer.DEDENT) {
            output.add(generated(SprigLexer.NEWLINE, "\\n", near));
        }
    }

    private void normalize(List<Token> raw) {
        Deque<Integer> indents = new ArrayDeque<>();
        indents.push(0);
        int brackets = 0;
        for (int i = 0; i < raw.size(); i++) {
            Token token = raw.get(i);
            int type = token.getType();
            if (type == SprigLexer.TAB) {
                error(Codes.LEX_TAB, "Tabs are not allowed for indentation or inline whitespace",
                        token, "Sprig code blocks use spaces only; replace the tab with spaces.");
                continue;
            }
            if (type == SprigLexer.ERROR_CHAR) {
                error(Codes.LEX_CHAR, "Invalid character '" + token.getText() + "'", token, null);
                continue;
            }
            if (type == SprigLexer.NEWLINE) {
                if (brackets > 0) {
                    continue;
                }
                int j = i + 1;
                while (j < raw.size() && raw.get(j).getType() == SprigLexer.NEWLINE) {
                    j++;
                }
                Token next = raw.get(j);
                newline(token);
                if (next.getType() != Token.EOF) {
                    adjustIndent(next, indents);
                }
                i = j - 1;
                continue;
            }
            if (type == Token.EOF) {
                if (brackets != 0) {
                    error(Codes.LEX_UNCLOSED, "Unclosed grouping delimiter at end of file", token, null);
                    brackets = 0;
                }
                newline(token);
                while (indents.size() > 1) {
                    indents.pop();
                    output.add(generated(SprigLexer.DEDENT, "<DEDENT>", token));
                }
                output.add(token);
                return;
            }
            if (output.isEmpty() && token.getCharPositionInLine() != 0) {
                error(Codes.LEX_INDENT_FIRST, "Unexpected indentation on the first code line",
                        token, null);
            }
            output.add(token);
            if (type == SprigLexer.LPAREN || type == SprigLexer.LBRACK || type == SprigLexer.LBRACE) {
                brackets++;
            } else if (type == SprigLexer.RPAREN || type == SprigLexer.RBRACK || type == SprigLexer.RBRACE) {
                brackets--;
                if (brackets < 0) {
                    error(Codes.LEX_UNMATCHED, "Unmatched closing delimiter", token, null);
                    brackets = 0;
                }
            }
        }
    }

    private void adjustIndent(Token next, Deque<Integer> indents) {
        int column = next.getCharPositionInLine();
        int current = indents.peek();
        if (column > current) {
            indents.push(column);
            output.add(generated(SprigLexer.INDENT, "<INDENT>", next));
        } else if (column < current) {
            while (indents.size() > 1 && column < indents.peek()) {
                indents.pop();
                output.add(generated(SprigLexer.DEDENT, "<DEDENT>", next));
            }
            if (column != indents.peek()) {
                error(Codes.LEX_INDENT_INCONSISTENT,
                        "Inconsistent indentation: column " + (column + 1)
                                + " does not match any enclosing block",
                        next, "Use a consistent number of spaces per nesting level.");
            }
        }
    }

    @Override
    public Token nextToken() {
        if (cursor >= output.size()) {
            return output.get(output.size() - 1); // EOF is the final synthetic token
        }
        return output.get(cursor++);
    }

    @Override
    public int getLine() {
        return lexer.getLine();
    }

    @Override
    public int getCharPositionInLine() {
        return lexer.getCharPositionInLine();
    }

    @Override
    public CharStream getInputStream() {
        return lexer.getInputStream();
    }

    @Override
    public String getSourceName() {
        return lexer.getSourceName();
    }

    @Override
    public void setTokenFactory(TokenFactory<?> factory) {
        lexer.setTokenFactory(factory);
    }

    @Override
    public TokenFactory<?> getTokenFactory() {
        return lexer.getTokenFactory();
    }
}
