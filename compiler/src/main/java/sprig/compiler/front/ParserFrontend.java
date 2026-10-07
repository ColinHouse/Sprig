package sprig.compiler.front;
import sprig.compiler.parser.SprigLexer;
import sprig.compiler.parser.SprigParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.FailedPredicateException;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.IntervalSet;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Newcomer;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;

/** Runs lexer + layout + parser over one source file. */
public final class ParserFrontend {
    private ParserFrontend() {
    }

    public static SprigParser.ProgramContext parse(Path path, String uri, String text, Diagnostics diagnostics) {
        SprigLexer lexer = new SprigLexer(CharStreams.fromString(text, uri));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                    int charPositionInLine, String msg, RecognitionException e) {
                String code = msg.contains("\"") ? Codes.LEX_STRING : Codes.LEX_CHAR;
                String message = msg.startsWith("token recognition error")
                        ? "Unrecognized or unterminated token: " + msg.substring(msg.indexOf(':') + 1).trim()
                        : msg;
                diagnostics.add(Diagnostic.error(code, Phase.LEX, message, uri,
                        new Span(line - 1, charPositionInLine, line - 1, charPositionInLine + 1, -1, -1)));
            }
        });
        LayoutTokenSource layout = new LayoutTokenSource(lexer, diagnostics, uri);
        CommonTokenStream tokens = new CommonTokenStream(layout);
        SprigParser parser = new SprigParser(tokens);
        parser.setErrorHandler(new IfBranchErrorStrategy());
        boolean[] reported = {false};
        java.util.Set<Integer> errorLines = new java.util.HashSet<>();
        java.util.Set<Integer> ifExpressionsReported = new java.util.HashSet<>();
        int[] ifExpressionEnd = {-1};
        // Whether a reported if expression runs to the end of the file, where ANTLR's
        // recovery can leave its enclosing blocks unclosed.
        boolean[] ifExpressionAtEnd = {false};
        int[] firstErrorTokenIndex = {-1};
        boolean[] missingBlockReported = {false};
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                    int charPositionInLine, String msg, RecognitionException e) {
                // The lexer already identifies the unclosed opener. Parser recovery over
                // the swallowed physical newlines would only cascade on each remaining line.
                if (layout.hasUnclosedGrouping()) {
                    return;
                }
                // An INDENT or DEDENT that only fails because an earlier error broke the
                // block structure says nothing new, and reads like an indentation mistake.
                if (reported[0] && offendingSymbol instanceof Token layout
                        && (layout.getType() == SprigLexer.INDENT || layout.getType() == SprigLexer.DEDENT)) {
                    return;
                }
                // If recovery already found the source error and only layout remains,
                // the EOF diagnostic is a cascade rather than another useful problem.
                if (offendingSymbol instanceof Token end) {
                    if (end.getType() == Token.EOF && (missingBlockReported[0]
                            || (reported[0] && firstErrorTokenIndex[0] >= 0
                                && onlyLayoutAfter(parser.getTokenStream(), firstErrorTokenIndex[0])))) {
                        return;
                    }
                    if (end.getType() != Token.EOF) {
                        missingBlockReported[0] = false;
                    }
                }
                // After an if expression's error, what ANTLR's recovery trips over inside
                // that if expression says nothing new. When the if expression runs to the
                // end of the file, recovering can also leave an enclosing block unclosed,
                // so the end of the file says nothing new either.
                if (!ifExpressionsReported.isEmpty() && offendingSymbol instanceof Token after
                        && (after.getType() == Token.EOF ? ifExpressionAtEnd[0]
                            : after.getTokenIndex() >= 0 && after.getTokenIndex() <= ifExpressionEnd[0])) {
                    return;
                }
                // Recovering from a broken if expression can leave the parser inside a block
                // that has already ended; the declaration after it then reads as misplaced.
                if (!ifExpressionsReported.isEmpty() && offendingSymbol instanceof Token next
                        && closesBlockEarly(parser, next)) {
                    return;
                }
                IfExpressionError ifError = offendingSymbol instanceof Token token
                        ? ifExpressionError(parser, token) : null;
                // One if expression with a broken shape is one error, however many
                // tokens ANTLR's recovery trips over afterwards.
                if (ifError != null && !ifExpressionsReported.add(ifError.start())) {
                    return;
                }
                // A grammar predicate that fails after an earlier error is ANTLR's recovery
                // tripping over that same mistake; a predicate's text is never a message.
                if (ifError == null && e instanceof FailedPredicateException && reported[0]) {
                    return;
                }
                // ANTLR's recovery reports a run of follow-on errors on a line it could not
                // parse; the first one is the one to fix.
                if (!errorLines.add(line)) {
                    return;
                }
                reported[0] = true;
                if (offendingSymbol instanceof Token token) {
                    firstErrorTokenIndex[0] = token.getTokenIndex();
                }
                String pretty = readableParserMessage(msg);
                String targetedHint = null;
                String foreign = ifError == null && offendingSymbol instanceof org.antlr.v4.runtime.Token token
                        ? foreignSyntax(parser, token, msg) : null;
                String missingBlock = offendingSymbol instanceof Token token
                        ? missingBlockHeader(parser, token) : null;
                if (ifError != null) {
                    Span at = spanOf(ifError.at());
                    // The line the error is shown on is reported too.
                    errorLines.add(at.startLine + 1);
                    ifExpressionEnd[0] = Math.max(ifExpressionEnd[0], ifError.end());
                    ifExpressionAtEnd[0] |= onlyLayoutAfter(parser.getTokenStream(), ifError.end());
                    diagnostics.add(Diagnostic.error(Codes.SYNTAX_ERROR, Phase.SYNTAX, ifError.message(), uri, at)
                            .withHint(ifError.hint()).withRelatedHelp("language"));
                    return;
                }
                if (foreign != null) {
                    int split = foreign.indexOf('\n');
                    pretty = foreign.substring(0, split);
                    targetedHint = foreign.substring(split + 1);
                } else if (offendingSymbol instanceof Token token && token.getType() == SprigLexer.INDENT) {
                    pretty = "Unexpected indentation: this line is indented but the previous line does not open a block";
                } else if (missingBlock != null) {
                    pretty = "Expected an indented block after '" + missingBlock + ":'";
                    missingBlockReported[0] = true;
                } else if (e instanceof FailedPredicateException failed
                        && offendingSymbol instanceof org.antlr.v4.runtime.Token token) {
                    pretty = predicateMessage(parser, failed, token);
                } else if (offendingSymbol instanceof org.antlr.v4.runtime.Token token
                        && token.getType() == org.antlr.v4.runtime.Token.EOF) {
                    pretty = message(msg, "unexpected end of file");
                } else if (offendingSymbol instanceof org.antlr.v4.runtime.Token token
                        && token.getType() == SprigLexer.THROWS) {
                    pretty = "A function that declares 'throws' must also declare its return type; "
                            + "write '-> Unit throws Error' when nothing is returned.";
                    targetedHint = "Add the result type before 'throws', for example "
                            + "'func name(...) -> Unit throws Error:'.";
                } else if (offendingSymbol instanceof org.antlr.v4.runtime.Token token) {
                    pretty = message(msg, "unexpected '" + token.getText().replace("\n", "\\n") + "'");
                }
                Diagnostic diagnostic = Diagnostic.error(Codes.SYNTAX_ERROR, Phase.SYNTAX, pretty, uri,
                        new Span(line - 1, charPositionInLine, line - 1, charPositionInLine + 1, -1, -1));
                if (foreign != null) {
                    diagnostic.withHint(targetedHint).withRelatedHelp("language");
                    if (offendingSymbol instanceof org.antlr.v4.runtime.Token token) {
                        elifEdit(parser, token, diagnostic);
                    }
                } else if (targetedHint != null) {
                    diagnostic.withHint(targetedHint).withRelatedHelp("functions");
                } else {
                    for (var context = parser.getContext(); context != null; context = context.getParent()) {
                        if (context instanceof SprigParser.MatchExpressionBranchContext) {
                            diagnostic.withHint("Expression-match branches contain exactly one expression; use statement match for multi-statement branches.");
                            break;
                        }
                    }
                }
                diagnostics.add(diagnostic);
            }
        });
        return parser.program();
    }

    /**
     * ANTLR's recovery, except where an if-expression branch must start a line: a
     * value on the header's line is not repaired by deleting it and then inserting
     * the missing indent, which would take the next statement for the branch and
     * the end of the enclosing block for the branch's end, and then report the
     * next declaration as misplaced. The rest of the line is skipped instead, and
     * the if expression reports its one error.
     */
    private static final class IfBranchErrorStrategy extends DefaultErrorStrategy {
        @Override
        public Token recoverInline(Parser recognizer) throws RecognitionException {
            ParserRuleContext context = recognizer.getContext();
            if (context instanceof SprigParser.IfExpressionBranchContext) {
                IntervalSet expected = recognizer.getExpectedTokens();
                if (expected.contains(SprigLexer.NEWLINE)) {
                    throw new InputMismatchException(recognizer);
                }
            }
            // A guard on a match case ('case X as c if cond:') is one mistake: it is
            // reported at its 'if' and skipped up to the header's ':', so the case and
            // the cases after it still parse.
            if ((context instanceof SprigParser.MatchBranchContext
                    || context instanceof SprigParser.MatchExpressionBranchContext)
                    && recognizer.getInputStream().LA(1) == SprigLexer.IF
                    && recognizer.getExpectedTokens().contains(SprigLexer.COLON)) {
                Token colon = headerEnd(recognizer.getInputStream());
                if (colon != null) {
                    reportUnwantedToken(recognizer);
                    while (recognizer.getCurrentToken() != colon) {
                        recognizer.consume();
                    }
                    reportMatch(recognizer);
                    recognizer.consume();
                    return colon;
                }
            }
            return super.recoverInline(recognizer);
        }

        /** The ':' that ends the current logical line, outside any grouping, or null. */
        private static Token headerEnd(TokenStream stream) {
            int depth = 0;
            for (int k = 1; ; k++) {
                Token token = stream.LT(k);
                int type = token.getType();
                if (type == Token.EOF || type == SprigLexer.NEWLINE || type == SprigLexer.INDENT
                        || type == SprigLexer.DEDENT) {
                    return null;
                }
                if (type == SprigLexer.LPAREN || type == SprigLexer.LBRACK || type == SprigLexer.LBRACE) {
                    depth++;
                } else if (type == SprigLexer.RPAREN || type == SprigLexer.RBRACK || type == SprigLexer.RBRACE) {
                    depth--;
                } else if (type == SprigLexer.COLON && depth == 0
                        && stream.LT(k + 1).getType() == SprigLexer.NEWLINE) {
                    return token;
                }
            }
        }
    }

    /** A readable message for a grammar predicate that failed as the first error. */
    private static String predicateMessage(SprigParser parser, FailedPredicateException failed, Token token) {
        String unexpected = token.getType() == Token.EOF ? "the end of the file"
                : "'" + token.getText().replace("\n", "\\n") + "'";
        String rule = failed.getRuleIndex() >= 0 && failed.getRuleIndex() < parser.getRuleNames().length
                ? parser.getRuleNames()[failed.getRuleIndex()] : "";
        return switch (rule) {
            case "statementEnd" -> "Expected the end of the line, found " + unexpected;
            case "toClause" -> "Expected 'to' in 'conform ClassName to InterfaceName', found " + unexpected;
            default -> "Unexpected " + unexpected;
        };
    }

    /** Replace synthetic layout-token names that ANTLR exposes in generic messages. */
    private static String readableParserMessage(String message) {
        return message.replace("<INDENT>", "indentation")
                .replace("<DEDENT>", "the end of an indented block")
                .replace("<EOF>", "the end of the file")
                .replace("INDENT", "indentation")
                .replace("DEDENT", "the end of an indented block")
                .replace("EOF", "the end of the file");
    }

    /** The block opener immediately before a missing suite, if it is recognized here. */
    private static String missingBlockHeader(SprigParser parser, Token token) {
        if (!parser.getExpectedTokens().contains(SprigLexer.INDENT)) {
            return null;
        }
        TokenStream stream = parser.getTokenStream();
        int index = token.getTokenIndex() - 1;
        while (index >= 0) {
            int type = typeAt(stream, index);
            if (type != SprigLexer.NEWLINE && type != SprigLexer.INDENT && type != SprigLexer.DEDENT) {
                break;
            }
            index--;
        }
        if (index < 1 || typeAt(stream, index) != SprigLexer.COLON
                || typeAt(stream, index - 1) != SprigLexer.ELSE) {
            return null;
        }
        return "else";
    }

    /**
     * Whether the parser still wants a DEDENT at the token right after one: a block
     * that already ended in the source is still open in the parser.
     */
    private static boolean closesBlockEarly(SprigParser parser, Token token) {
        int index = token.getTokenIndex();
        if (index <= 0 || typeAt(parser.getTokenStream(), index - 1) != SprigLexer.DEDENT) {
            return false;
        }
        try {
            return parser.getExpectedTokens().contains(SprigLexer.DEDENT);
        } catch (RuntimeException unknownState) {
            return false;
        }
    }

    /** Whether only line breaks and indentation follow the token at index until the end of the file. */
    private static boolean onlyLayoutAfter(TokenStream stream, int index) {
        for (int i = index + 1; i < stream.size(); i++) {
            int type = typeAt(stream, i);
            if (type == Token.EOF) {
                return true;
            }
            if (type != SprigLexer.NEWLINE && type != SprigLexer.INDENT && type != SprigLexer.DEDENT) {
                return false;
            }
        }
        return true;
    }

    /**
     * A targeted syntax error for an if expression: the index of its IF token
     * (the key that keeps one if expression to one error), message, hint, the
     * token to report it at, and the index of the if expression's last token,
     * up to which ANTLR's follow-on errors are not reported.
     */
    private record IfExpressionError(int start, String message, String hint, Token at, int end) {
    }

    private enum IfShape { MISSING_ELSE, INLINE, IN_GROUPING, BLOCK }

    /** The error of an if expression without an else branch, shared with the AST builder. */
    static final String MISSING_ELSE_MESSAGE = "An if expression needs an else branch";
    static final String MISSING_ELSE_HINT = "Add 'else:' at the same indentation as the line that starts the "
            + "if expression, with the value to use otherwise indented on the line below it. When there is no "
            + "value to produce, write an if statement on a line of its own.";

    /** A shape problem of the if expression whose IF is at ifIndex, found at the token at index at. */
    private record IfProblem(IfShape shape, int ifIndex, int at) {
    }

    /**
     * The error for an if expression in a position or shape the grammar rejects,
     * or null. An 'if' the parser could not take right after an operator is an
     * if expression used as an operand. Otherwise the if expression is the
     * innermost one the parser was inside, or the offending 'if' itself where the
     * parser could not even start one; its shape is read from the tokens, so the
     * message does not depend on where ANTLR's prediction happened to give up.
     */
    private static IfExpressionError ifExpressionError(SprigParser parser, Token token) {
        TokenStream stream = parser.getTokenStream();
        int index = token.getTokenIndex();
        if (index < 0) {
            return null;
        }
        // The shape is read past the parser's lookahead; the layout adapter has
        // already produced every token, so buffering them all is cheap.
        if (stream instanceof org.antlr.v4.runtime.BufferedTokenStream buffered) {
            buffered.fill();
        }
        Token before = index > 0 ? stream.get(index - 1) : null;
        int previous = before == null ? Token.INVALID_TYPE : before.getType();
        if (token.getType() == SprigLexer.IF && previous == SprigLexer.ELSE) {
            return null; // 'else if' has its own hint and edit
        }
        if (token.getType() == SprigLexer.IF && takesOperand(previous)) {
            return new IfExpressionError(index,
                    "An if expression cannot be the operand of '" + before.getText() + "'",
                    "An if expression is a whole value: it follows '=', return, throw or '=>'. Bind it to a "
                            + "let first, then use the name, for example 'let extra = if ...' and then "
                            + "'total + extra'.", token, ifExpressionExtent(stream, index));
        }
        Token start = null;
        for (ParserRuleContext context = parser.getContext(); context != null; context = context.getParent()) {
            if (context instanceof SprigParser.IfExpressionContext ifContext) {
                start = ifContext.getStart();
                break;
            }
        }
        // An 'if' the parser could not take starts an if expression only where an
        // expression is expected; after a block header's ':', 'class', 'let' and the
        // like it is some other mistake, which keeps its own message.
        if (start == null && token.getType() == SprigLexer.IF && expectsExpression(stream, index)) {
            start = token;
        }
        if (start == null || start.getTokenIndex() < 0) {
            return null;
        }
        IfProblem problem = ifExpressionProblem(stream, start.getTokenIndex());
        if (problem == null) {
            return null;
        }
        // A problem of a nested if expression is reported on that one, and the
        // follow-on errors of the whole enclosing one are left out.
        int owner = problem.ifIndex();
        Token ifToken = stream.get(owner);
        int end = Math.max(ifExpressionExtent(stream, owner), ifExpressionExtent(stream, start.getTokenIndex()));
        return switch (problem.shape()) {
            case MISSING_ELSE -> new IfExpressionError(owner, MISSING_ELSE_MESSAGE, MISSING_ELSE_HINT, ifToken, end);
            case INLINE -> new IfExpressionError(owner,
                    "Each branch of an if expression goes on its own indented line",
                    "Write 'if condition:' with the value indented on the next line, then 'else:' with the "
                            + "other value indented below it.", stream.get(problem.at()), end);
            case IN_GROUPING -> new IfExpressionError(owner,
                    "An if expression cannot be written inside parentheses, brackets or braces",
                    "Line breaks are ignored there, so the branches cannot go on their own lines. Bind the if "
                            + "expression, or the lambda that holds it, to a let first, then use the name.",
                    ifToken, end);
            case BLOCK -> new IfExpressionError(owner,
                    "An if-expression branch holds exactly one expression",
                    "Use an if statement when a branch needs several statements, or compute the value "
                            + "in a let before the if expression.", stream.get(problem.at()), end);
        };
    }

    /**
     * The first shape problem of the if expression whose 'if' is at index start,
     * or of an if expression nested in one of its branches; null when its
     * headers, one-line branches and else branch are all there. Inside (), []
     * and {} the layout adapter drops line breaks, so a branch there can never
     * start on its own line.
     */
    private static IfProblem ifExpressionProblem(TokenStream stream, int start) {
        int keyword = start;
        while (true) {
            int colon = headerColon(stream, keyword);
            if (colon < 0) {
                return null; // a header without ':' is reported as it is
            }
            int afterColon = typeAt(stream, colon + 1);
            if (afterColon == Token.EOF) {
                return null; // the file ends after the header; the parser says so
            }
            if (afterColon != SprigLexer.NEWLINE || typeAt(stream, colon + 2) != SprigLexer.INDENT) {
                int at = afterColon == SprigLexer.NEWLINE ? colon + 2 : colon + 1;
                return new IfProblem(insideGrouping(stream, start) ? IfShape.IN_GROUPING : IfShape.INLINE,
                        start, at);
            }
            // At its own level the branch holds one line, which may itself hold a
            // nested if expression; deeper lines belong to what that line opened.
            int close = closingDedent(stream, colon + 2);
            int limit = close < 0 ? stream.size() : close;
            int level = 1;
            int grouping = 0;
            int lineBegin = colon + 3;
            for (int i = colon + 3; i < limit; i++) {
                int type = typeAt(stream, i);
                if (type == SprigLexer.INDENT) {
                    level++;
                } else if (type == SprigLexer.DEDENT) {
                    level--;
                } else if (level == 1) {
                    int before = typeAt(stream, i - 1);
                    if (type == SprigLexer.LPAREN || type == SprigLexer.LBRACK || type == SprigLexer.LBRACE) {
                        grouping++;
                    } else if (type == SprigLexer.RPAREN || type == SprigLexer.RBRACK || type == SprigLexer.RBRACE) {
                        grouping = Math.max(0, grouping - 1);
                    }
                    if (type == SprigLexer.NEWLINE) {
                        int after = typeAt(stream, i + 1);
                        if (after != SprigLexer.NEWLINE && after != SprigLexer.INDENT && after != SprigLexer.DEDENT
                                && after != Token.EOF) {
                            return new IfProblem(IfShape.BLOCK, start, i + 1);
                        }
                        lineBegin = i + 1;
                    } else if ((before == SprigLexer.INDENT || before == SprigLexer.NEWLINE)
                            && startsStatementOnly(type)) {
                        return new IfProblem(IfShape.BLOCK, start, i);
                    } else if (grouping == 0 && assigns(type)) {
                        // An assignment is a statement; outside parentheses an '=' is never part
                        // of a value (named arguments sit inside a call's parentheses).
                        return new IfProblem(IfShape.BLOCK, start, lineBegin);
                    } else if (type == SprigLexer.IF && !endsExpression(before)) {
                        IfProblem nested = ifExpressionProblem(stream, i);
                        if (nested != null) {
                            return nested;
                        }
                    }
                }
            }
            if (close < 0 || typeAt(stream, keyword) == SprigLexer.ELSE) {
                return null;
            }
            int follow = typeAt(stream, close + 1);
            if (follow != SprigLexer.ELIF && follow != SprigLexer.ELSE) {
                return new IfProblem(IfShape.MISSING_ELSE, start, close + 1);
            }
            keyword = close + 1;
        }
    }

    /**
     * The index of the last token of the if expression whose 'if' is at start:
     * the DEDENT that closes its last branch. A branch whose value sits on the
     * header's line, or on the next line without an indent, ends with that line,
     * and an elif or else line right after it still belongs to the if expression.
     */
    private static int ifExpressionExtent(TokenStream stream, int start) {
        int end = logicalLineEnd(stream, start);
        int keyword = start;
        while (true) {
            int colon = headerColon(stream, keyword);
            if (colon < 0) {
                return Math.max(end, logicalLineEnd(stream, keyword));
            }
            int close;
            if (typeAt(stream, colon + 1) != SprigLexer.NEWLINE) {
                close = logicalLineEnd(stream, colon);
            } else if (typeAt(stream, colon + 2) != SprigLexer.INDENT) {
                close = logicalLineEnd(stream, colon + 2);
            } else {
                close = closingDedent(stream, colon + 2);
                if (close < 0) {
                    return stream.size() - 1;
                }
            }
            end = Math.max(end, close);
            int follow = typeAt(stream, close + 1);
            if (typeAt(stream, keyword) == SprigLexer.ELSE
                    || follow != SprigLexer.ELIF && follow != SprigLexer.ELSE) {
                return end;
            }
            keyword = close + 1;
        }
    }

    /** The index of the ':' that ends the header starting at keyword, or -1 when its logical line ends first. */
    private static int headerColon(TokenStream stream, int keyword) {
        int depth = 0;
        for (int i = keyword + 1; ; i++) {
            int type = typeAt(stream, i);
            if (type == Token.EOF || type == SprigLexer.NEWLINE || type == SprigLexer.INDENT
                    || type == SprigLexer.DEDENT) {
                return -1;
            }
            if (type == SprigLexer.LPAREN || type == SprigLexer.LBRACK || type == SprigLexer.LBRACE) {
                depth++;
            } else if (type == SprigLexer.RPAREN || type == SprigLexer.RBRACK || type == SprigLexer.RBRACE) {
                depth--;
            } else if (type == SprigLexer.COLON && depth == 0) {
                return i;
            }
        }
    }

    /** The index of the DEDENT that closes the INDENT at index indent, or -1. */
    private static int closingDedent(TokenStream stream, int indent) {
        int level = 0;
        for (int i = indent; i < stream.size(); i++) {
            int type = typeAt(stream, i);
            if (type == SprigLexer.INDENT) {
                level++;
            } else if (type == SprigLexer.DEDENT && --level == 0) {
                return i;
            } else if (type == Token.EOF) {
                return -1;
            }
        }
        return -1;
    }

    /** The index of the NEWLINE or layout token that ends the logical line holding the token at index. */
    private static int logicalLineEnd(TokenStream stream, int index) {
        for (int i = index; i < stream.size(); i++) {
            int type = typeAt(stream, i);
            if (type == SprigLexer.NEWLINE || type == SprigLexer.INDENT || type == SprigLexer.DEDENT
                    || type == Token.EOF) {
                return i;
            }
        }
        return stream.size() - 1;
    }

    private static int typeAt(TokenStream stream, int index) {
        return index >= 0 && index < stream.size() ? stream.get(index).getType() : Token.EOF;
    }

    private static boolean assigns(int type) {
        return type == SprigLexer.ASSIGN || type == SprigLexer.PLUS_ASSIGN || type == SprigLexer.MINUS_ASSIGN
                || type == SprigLexer.STAR_ASSIGN || type == SprigLexer.SLASH_ASSIGN;
    }

    /** Keywords that begin a statement and can never begin a branch value. */
    private static boolean startsStatementOnly(int type) {
        return type == SprigLexer.LET || type == SprigLexer.VAR || type == SprigLexer.RETURN
                || type == SprigLexer.BREAK || type == SprigLexer.CONTINUE || type == SprigLexer.PASS
                || type == SprigLexer.THROW || type == SprigLexer.WHILE || type == SprigLexer.FOR
                || type == SprigLexer.TRY;
    }

    /** Tokens that can end an expression, so an 'if' right after one is not where an expression starts. */
    private static boolean endsExpression(int type) {
        return type == SprigLexer.IDENT || type == SprigLexer.INT || type == SprigLexer.FLOAT
                || type == SprigLexer.STRING || type == SprigLexer.TRUE || type == SprigLexer.FALSE
                || type == SprigLexer.NULL || type == SprigLexer.RPAREN || type == SprigLexer.RBRACK
                || type == SprigLexer.RBRACE;
    }

    /**
     * Whether the token at index stands where an expression is expected: after an
     * assignment, return, throw, '=>', an opening (, [ or {, a ',', or the ':' of a
     * map entry (inside braces), never after a block header's ':'.
     */
    private static boolean expectsExpression(TokenStream stream, int index) {
        int previous = typeAt(stream, index - 1);
        return previous == SprigLexer.ASSIGN || previous == SprigLexer.PLUS_ASSIGN
                || previous == SprigLexer.MINUS_ASSIGN || previous == SprigLexer.STAR_ASSIGN
                || previous == SprigLexer.SLASH_ASSIGN || previous == SprigLexer.RETURN
                || previous == SprigLexer.THROW || previous == SprigLexer.FAT_ARROW
                || previous == SprigLexer.LPAREN || previous == SprigLexer.LBRACK
                || previous == SprigLexer.LBRACE || previous == SprigLexer.COMMA
                || previous == SprigLexer.COLON && innermostGrouping(stream, index) == SprigLexer.LBRACE;
    }

    /** The type of the innermost (, [ or { still open before the token at index on its logical line, or -1. */
    private static int innermostGrouping(TokenStream stream, int index) {
        java.util.ArrayDeque<Integer> open = new java.util.ArrayDeque<>();
        for (int i = lineStart(stream, index); i < index; i++) {
            int type = stream.get(i).getType();
            if (type == SprigLexer.LPAREN || type == SprigLexer.LBRACK || type == SprigLexer.LBRACE) {
                open.push(type);
            } else if ((type == SprigLexer.RPAREN || type == SprigLexer.RBRACK || type == SprigLexer.RBRACE)
                    && !open.isEmpty()) {
                open.pop();
            }
        }
        return open.isEmpty() ? -1 : open.peek();
    }

    /** Operators that take an operand, so an 'if' the parser rejected right after one is used as an operand. */
    private static boolean takesOperand(int type) {
        return type == SprigLexer.PLUS || type == SprigLexer.MINUS || type == SprigLexer.STAR
                || type == SprigLexer.SLASH || type == SprigLexer.PERCENT || type == SprigLexer.EQEQ
                || type == SprigLexer.NEQ || type == SprigLexer.LT || type == SprigLexer.LE
                || type == SprigLexer.GT || type == SprigLexer.GE || type == SprigLexer.IN
                || type == SprigLexer.AND || type == SprigLexer.OR || type == SprigLexer.NOT;
    }

    /** Whether the token at index sits inside an open (, [ or { of its logical line. */
    private static boolean insideGrouping(TokenStream stream, int index) {
        int open = 0;
        for (int i = lineStart(stream, index); i < index; i++) {
            int type = stream.get(i).getType();
            if (type == SprigLexer.LPAREN || type == SprigLexer.LBRACK || type == SprigLexer.LBRACE) {
                open++;
            } else if (type == SprigLexer.RPAREN || type == SprigLexer.RBRACK || type == SprigLexer.RBRACE) {
                open--;
            }
        }
        return open > 0;
    }

    private static Span spanOf(Token token) {
        int line = Math.max(0, token.getLine() - 1);
        int column = Math.max(0, token.getCharPositionInLine());
        int length = token.getText() == null || token.getType() == Token.EOF ? 1 : Math.max(1, token.getText().length());
        return new Span(line, column, line, column + length, -1, -1);
    }

    /**
     * A message and hint (separated by a newline) for a construct from another
     * language that the grammar rejects, or null. These are the mistakes people
     * and models make first when they have only seen Python, Java or C.
     */
    /** 'else if' on one line is the one foreign spelling with a mechanical rewrite: 'elif'. */
    private static void elifEdit(SprigParser parser, Token token, Diagnostic diagnostic) {
        TokenStream stream = parser.getTokenStream();
        int index = token.getTokenIndex();
        if (token.getType() != SprigLexer.IF || index == 0) {
            return;
        }
        Token previous = stream.get(index - 1);
        if (previous.getType() != SprigLexer.ELSE || previous.getLine() != token.getLine()) {
            return;
        }
        int line = token.getLine() - 1;
        diagnostic.withEdit(new Span(line, previous.getCharPositionInLine(), line,
                token.getCharPositionInLine() + token.getText().length(), -1, -1), "elif",
                "replace 'else if' with 'elif'");
    }

    private static String foreignSyntax(SprigParser parser, Token token, String raw) {
        TokenStream stream = parser.getTokenStream();
        int index = token.getTokenIndex();
        int previous = index > 0 ? stream.get(index - 1).getType() : Token.INVALID_TYPE;
        int beforePrevious = index > 1 ? stream.get(index - 2).getType() : Token.INVALID_TYPE;
        int type = token.getType();
        String declaration = previous == SprigLexer.IDENT ? Newcomer.declarationHint(stream.get(index - 1).getText()) : null;
        if (declaration == null && type == SprigLexer.IDENT && lineStart(stream, index) == index) {
            declaration = Newcomer.declarationHint(token.getText());
        }
        if (declaration != null) {
            return "'" + (previous == SprigLexer.IDENT ? stream.get(index - 1).getText() : token.getText())
                    + "' is not Sprig syntax\n" + declaration;
        }
        if (within(parser, SprigParser.CatchClauseContext.class)) {
            return "This catch clause is not written the Sprig way\n" + Newcomer.CATCH;
        }
        // Python's f"..." (or F, fr, rf): a one-letter prefix glued to the string.
        if (type == SprigLexer.STRING && previous == SprigLexer.IDENT) {
            Token prefix = stream.get(index - 1);
            if (prefix.getStopIndex() + 1 == token.getStartIndex()
                    && List.of("f", "F", "fr", "rf", "Fr", "fR", "FR").contains(prefix.getText())) {
                return "Sprig has no f-strings or string interpolation\n" + Newcomer.INTERPOLATION;
            }
        }
        if (type == SprigLexer.STAR && previous == SprigLexer.DOT && lineHas(stream, index, SprigLexer.IMPORT)) {
            return "Java classes are imported one at a time\n"
                    + "Write 'import java.io.BufferedReader as BufferedReader', one line per class; "
                    + "for standard input, 'import \"@std/process.spr\" as process' is simpler.";
        }
        Token next = index + 1 < stream.size() ? stream.get(index + 1) : null;
        if (type == SprigLexer.SLASH && next != null
                && (next.getType() == SprigLexer.SLASH || next.getType() == SprigLexer.STAR)) {
            return "Comments start with '#'\n"
                    + "Write '# comment'; Sprig has no // or /* */ comments.";
        }
        if (type == SprigLexer.DOT && previous == SprigLexer.DOT) {
            return "Sprig has no '..' range operator\n"
                    + "Write range(start, stop), which stops before stop, for example 'for i in range(0, count):'.";
        }
        int lineFirst = lineStart(stream, index);
        int first = stream.get(lineFirst).getType();
        // class Pair(first: Int, second: Int) takes immutable fields only; a ':' body,
        // a var or a default after the parenthesis asks for the block form.
        if (first == SprigLexer.CLASS && lineFirst != index && lineHas(stream, index, SprigLexer.LPAREN)
                && (type == SprigLexer.COLON || type == SprigLexer.VAR || type == SprigLexer.ASSIGN
                        || (type == SprigLexer.RPAREN && previous == SprigLexer.LPAREN))) {
            return "A one-line class lists immutable fields only\n"
                    + "Write class Pair(first: Int, second: Int) for a record of let fields; for var fields, "
                    + "default values, methods or no fields, write 'class Pair:' with the fields and methods "
                    + "indented on the following lines.";
        }
        // A guard on a match case or a filter on a for loop, as in Python, Scala or
        // Rust: the 'if' comes before the header's ':'. Sprig tests the condition
        // inside the body.
        boolean inHeader = type == SprigLexer.IF && lineFirst != index && !lineHas(stream, index, SprigLexer.COLON)
                && endsExpression(previous);
        if (inHeader && first == SprigLexer.CASE) {
            return "A match case has no 'if' guard\n"
                    + "Match the case, then put 'if condition:' inside the case's body.";
        }
        if (inHeader && first == SprigLexer.FOR) {
            return "A for loop has no 'if' filter\n"
                    + "Loop over every item and put 'if condition:' inside the loop body.";
        }
        // Python's `a if c else b` and C's `c ? a : b`: the if comes right after a
        // complete value and an 'else' comes later on the line, or a '?' follows a
        // value and a ':' with a value after it comes later on the line (not a block
        // header's ':', `x?.y` or `x ?: y`).
        if (type == SprigLexer.IF && endsExpression(previous) && laterOnLine(stream, index, SprigLexer.ELSE)) {
            return "Sprig writes a value that depends on a condition as an if expression, "
                    + "not 'value if condition else other'\n" + Newcomer.IF_EXPRESSION;
        }
        if (type == SprigLexer.QUESTION && endsExpression(previous) && next != null
                && next.getType() != SprigLexer.DOT && next.getType() != SprigLexer.COLON
                && ternaryColon(stream, index)) {
            return "Sprig has no '?:' operator; a value that depends on a condition is an if expression\n"
                    + Newcomer.IF_EXPRESSION;
        }
        boolean blockHeader = first == SprigLexer.IF || first == SprigLexer.ELIF || first == SprigLexer.WHILE
                || first == SprigLexer.FOR;
        if (blockHeader && type == SprigLexer.ASSIGN) {
            return "'=' assigns, and an assignment is a statement of its own\n"
                    + "Compare with '==', or assign on the line before the condition.";
        }
        if ((blockHeader || first == SprigLexer.ELSE) && lineFirst != index && type != SprigLexer.NEWLINE
                && type != SprigLexer.COLON && type != SprigLexer.LBRACE
                && !(type == SprigLexer.IF && previous == SprigLexer.ELSE) && isStatementStart(type)) {
            if (!lineHas(stream, index, SprigLexer.COLON)) {
                return "A block header ends with ':' and its body goes on the following lines\n"
                        + "Write the condition, then ':', then the body indented on the next line, for example "
                        + "'if count < 3:' followed by '    return false'. Parentheses around the condition are optional.";
            }
            return "A block's body goes on its own lines after ':'\n"
                    + "Sprig has no one-line if or loop: put the body on the next line, indented by four spaces.";
        }
        if (type == SprigLexer.IF && previous == SprigLexer.ELSE) {
            return "Sprig spells else-if as 'elif'\n"
                    + "Write 'elif condition:' in place of 'else if condition:'.";
        }
        if (type == SprigLexer.NEWLINE && (previous == SprigLexer.PLUS && beforePrevious == SprigLexer.PLUS
                || previous == SprigLexer.MINUS && beforePrevious == SprigLexer.MINUS)) {
            return "Sprig has no ++ or -- operator\n"
                    + "Write 'count += 1' or 'count -= 1'.";
        }
        if (type == SprigLexer.NEWLINE && within(parser, SprigParser.VariableDeclarationContext.class)
                && !lineHas(stream, index, SprigLexer.ASSIGN)) {
            return "A variable declaration needs an initial value\n"
                    + "Write 'var name: Type = value', for example 'var label: String = \"\"' or "
                    + "'var count = 0'; Sprig has no uninitialized variables.";
        }
        // A '<' the grammar rejects while it could still take '[' follows a type name.
        if (type == SprigLexer.LT && previous == SprigLexer.IDENT && raw.contains("'['")) {
            return "Type arguments are written in square brackets\n"
                    + "Write List[Int], MutableList[String] or Map[String, Int], not List<Int>.";
        }
        if (type == SprigLexer.LBRACE && startsBlockHeader(stream, index)) {
            return "Sprig blocks are indented after ':', not wrapped in braces\n"
                    + "End the header with ':' and indent the body on the following lines, for example "
                    + "'if count > 0:' then the body indented by four spaces.";
        }
        if (type == SprigLexer.COLON && stream.get(lineStart(stream, index)).getType() == SprigLexer.FUNC
                && !lineHas(stream, index, SprigLexer.ARROW)) {
            String header = sourceBefore(stream, lineStart(stream, index), index, token).trim();
            if (header.startsWith("func ")) {
                return "'" + header + "' has no result type\n"
                        + "Write '" + header + " -> Unit:' when it returns nothing, or put the result type after "
                        + "'->', for example '" + header + " -> Int:'.";
            }
            return "A function declares its result type before ':'\n"
                    + "Write 'func name(parameter: Type) -> ResultType:', and '-> Unit' when it returns nothing.";
        }
        return null;
    }

    /** Tokens that begin a statement, where a header's body was expected on the next line. */
    private static boolean isStatementStart(int type) {
        return type == SprigLexer.RETURN || type == SprigLexer.BREAK || type == SprigLexer.CONTINUE
                || type == SprigLexer.PASS || type == SprigLexer.THROW || type == SprigLexer.VAR
                || type == SprigLexer.LET || type == SprigLexer.IDENT || type == SprigLexer.IF
                || type == SprigLexer.WHILE || type == SprigLexer.FOR || type == SprigLexer.MATCH
                || type == SprigLexer.TRY;
    }

    /** Whether a token of the given type follows the token at index on its logical line. */
    private static boolean laterOnLine(TokenStream stream, int index, int wanted) {
        if (stream instanceof org.antlr.v4.runtime.BufferedTokenStream buffered) {
            buffered.fill(); // the rest of the line may lie past the parser's lookahead
        }
        for (int i = index + 1; i < stream.size(); i++) {
            int found = typeAt(stream, i);
            if (found == wanted) {
                return true;
            }
            if (found == SprigLexer.NEWLINE || found == SprigLexer.INDENT || found == SprigLexer.DEDENT
                    || found == Token.EOF) {
                return false;
            }
        }
        return false;
    }

    private static boolean within(SprigParser parser, Class<? extends ParserRuleContext> rule) {
        for (ParserRuleContext context = parser.getContext(); context != null; context = context.getParent()) {
            if (rule.isInstance(context)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the line holding the token at index begins with a block keyword. */
    private static boolean startsBlockHeader(TokenStream stream, int index) {
        int first = stream.get(lineStart(stream, index)).getType();
        return first == SprigLexer.IF || first == SprigLexer.ELIF || first == SprigLexer.ELSE
                || first == SprigLexer.WHILE || first == SprigLexer.FOR || first == SprigLexer.FUNC
                || first == SprigLexer.CLASS || first == SprigLexer.TRY || first == SprigLexer.CATCH;
    }

    /** The source text from the token at start up to (not including) the token at end. */
    private static String sourceBefore(TokenStream stream, int start, int end, Token endToken) {
        Token first = stream.get(start);
        if (first.getStartIndex() < 0 || endToken.getStartIndex() <= first.getStartIndex()) {
            return "";
        }
        return endToken.getInputStream().getText(
                org.antlr.v4.runtime.misc.Interval.of(first.getStartIndex(), endToken.getStartIndex() - 1));
    }

    /**
     * Whether a ':' with a value after it follows the '?' at index on its logical
     * line, as in C's `c ? a : b`; a ':' that ends the line closes a block header.
     */
    private static boolean ternaryColon(TokenStream stream, int index) {
        if (stream instanceof org.antlr.v4.runtime.BufferedTokenStream buffered) {
            buffered.fill(); // the rest of the line may lie past the parser's lookahead
        }
        for (int i = index + 1; i < stream.size(); i++) {
            int found = stream.get(i).getType();
            if (found == SprigLexer.NEWLINE || found == SprigLexer.INDENT || found == SprigLexer.DEDENT
                    || found == Token.EOF) {
                return false;
            }
            if (found == SprigLexer.COLON) {
                int after = typeAt(stream, i + 1);
                return after != SprigLexer.NEWLINE && after != SprigLexer.INDENT && after != SprigLexer.DEDENT
                        && after != Token.EOF;
            }
        }
        return false;
    }

    /** Whether a token of the given type appears on the line before the token at index. */
    private static boolean lineHas(TokenStream stream, int index, int type) {
        for (int i = lineStart(stream, index); i < index; i++) {
            if (stream.get(i).getType() == type) {
                return true;
            }
        }
        return false;
    }

    private static int lineStart(TokenStream stream, int index) {
        int start = index;
        while (start > 0) {
            int type = stream.get(start - 1).getType();
            if (type == SprigLexer.NEWLINE || type == SprigLexer.INDENT || type == SprigLexer.DEDENT) {
                break;
            }
            start--;
        }
        return start;
    }

    private static String message(String raw, String unexpected) {
        int at = raw.indexOf(" at ");
        String head = at > 0 ? raw.substring(0, at) : raw;
        return head + " (" + unexpected + ")";
    }

    /** Reads a file as UTF-8 and parses it. */
    public static SprigParser.ProgramContext parseFile(Path path, Diagnostics diagnostics) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        return parse(path, path.toAbsolutePath().toUri().toString(), text, diagnostics);
    }
}
