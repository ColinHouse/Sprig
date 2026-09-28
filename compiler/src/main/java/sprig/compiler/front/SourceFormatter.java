package sprig.compiler.front;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.parser.SprigLexer;

/** Canonical token formatter. Literal spelling and comment order are immutable. */
public final class SourceFormatter {
    private SourceFormatter() {}

    public static String format(Path path, String source, Diagnostics diagnostics) {
        var before = ParserFrontend.parse(path, path.toUri().toString(), source, diagnostics);
        if (diagnostics.hasErrors()) return null;
        var lexer = new SprigLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        List<List<Token>> lines = new ArrayList<>();
        lines.add(new ArrayList<>());
        for (Token t : lexer.getAllTokens()) {
            if (t.getType() == SprigLexer.NEWLINE) lines.add(new ArrayList<>());
            else if (t.getType() != SprigLexer.SPACE) lines.get(lines.size() - 1).add(t);
        }
        List<Integer> columns = new ArrayList<>(List.of(0));
        StringBuilder out = new StringBuilder();
        int brackets = 0;
        boolean blank = false;
        for (List<Token> line : lines) {
            if (line.isEmpty()) {
                if (out.length() > 0 && !blank) { out.append('\n'); blank = true; }
                continue;
            }
            Token first = line.get(0);
            boolean commentOnly = first.getType() == SprigLexer.COMMENT;
            int column = first.getCharPositionInLine();
            int depth;
            if (!commentOnly && brackets == 0) {
                while (columns.size() > 1 && column < columns.get(columns.size() - 1)) columns.remove(columns.size() - 1);
                if (column > columns.get(columns.size() - 1)) columns.add(column);
                depth = columns.size() - 1;
            } else if (brackets > 0) {
                depth = columns.size();
                if (closing(first)) depth--;
            } else {
                depth = 0;
                for (int i = 1; i < columns.size(); i++) if (columns.get(i) <= column) depth = i;
                if (column > columns.get(columns.size() - 1)) depth++;
            }
            out.append("    ".repeat(depth));
            Token previous = null;
            boolean previousUnary = false;
            for (Token token : line) {
                if (token.getType() == SprigLexer.COMMENT) {
                    if (previous != null) out.append("  ");
                    out.append(token.getText().stripTrailing());
                    break;
                }
                boolean unary = (token.getType() == SprigLexer.PLUS || token.getType() == SprigLexer.MINUS)
                        && (previous == null || opening(previous) || operator(previous) || previous.getType() == SprigLexer.COMMA
                            || previous.getType() == SprigLexer.COLON || keywordPrefix(previous));
                if (previous != null && space(previous, token, previousUnary)) out.append(' ');
                out.append(token.getText());
                if (opening(token)) brackets++;
                if (closing(token)) brackets--;
                previous = token;
                previousUnary = unary;
            }
            out.append('\n');
            blank = false;
        }
        // Exactly one final newline for nonempty files; empty source remains empty.
        while (out.length() >= 2 && out.charAt(out.length()-2) == '\n') out.setLength(out.length()-1);
        String result = out.toString();
        var after = ParserFrontend.parse(path, path.toUri().toString(), result, diagnostics);
        if (diagnostics.hasErrors()) return null;
        List<String> left = new ArrayList<>(), right = new ArrayList<>();
        semanticTree(before, left); semanticTree(after, right);
        if (!left.equals(right)) throw new IllegalStateException("Formatter changed semantic parse structure");
        return result;
    }

    /** Rule identities plus terminal text, excluding positions and trivia. */
    private static void semanticTree(ParseTree tree, List<String> result) {
        if (tree instanceof TerminalNode terminal) {
            Token t = terminal.getSymbol();
            if (t.getType() != Token.EOF) result.add(t.getType() + ":" + t.getText());
        } else {
            result.add("(" + tree.getClass().getSimpleName());
            for (int i = 0; i < tree.getChildCount(); i++) semanticTree(tree.getChild(i), result);
            result.add(")");
        }
    }
    private static boolean opening(Token t) { return List.of(SprigLexer.LPAREN,SprigLexer.LBRACK,SprigLexer.LBRACE).contains(t.getType()); }
    private static boolean closing(Token t) { return List.of(SprigLexer.RPAREN,SprigLexer.RBRACK,SprigLexer.RBRACE).contains(t.getType()); }
    private static boolean keywordPrefix(Token t) { return List.of(SprigLexer.RETURN,SprigLexer.THROW,SprigLexer.NOT,SprigLexer.IN,SprigLexer.IF,SprigLexer.ELIF,SprigLexer.WHILE).contains(t.getType()); }
    private static boolean operator(Token t) {
        return List.of(SprigLexer.ASSIGN,SprigLexer.PLUS_ASSIGN,SprigLexer.MINUS_ASSIGN,SprigLexer.STAR_ASSIGN,SprigLexer.SLASH_ASSIGN,
            SprigLexer.PLUS,SprigLexer.MINUS,SprigLexer.STAR,SprigLexer.SLASH,SprigLexer.PERCENT,SprigLexer.EQEQ,SprigLexer.NEQ,
            SprigLexer.LT,SprigLexer.GT,SprigLexer.LE,SprigLexer.GE,SprigLexer.ARROW,SprigLexer.FAT_ARROW,SprigLexer.AND,SprigLexer.OR,
            SprigLexer.IN,SprigLexer.AS).contains(t.getType());
    }
    private static boolean space(Token left, Token right, boolean unary) {
        int a = left.getType(), b = right.getType();
        if (closing(right) || b == SprigLexer.COMMA || b == SprigLexer.COLON || b == SprigLexer.DOT || b == SprigLexer.QUESTION) return false;
        if (opening(left) || a == SprigLexer.DOT || unary) return false;
        if (a == SprigLexer.COMMA || a == SprigLexer.COLON || operator(left) || operator(right)) return true;
        if (b == SprigLexer.LPAREN || b == SprigLexer.LBRACK) return keywordPrefix(left);
        return true;
    }
}
