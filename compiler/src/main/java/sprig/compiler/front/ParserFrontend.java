package sprig.compiler.front;
import sprig.compiler.parser.SprigLexer;
import sprig.compiler.parser.SprigParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
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
        CommonTokenStream tokens = new CommonTokenStream(new LayoutTokenSource(lexer, diagnostics, uri));
        SprigParser parser = new SprigParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                    int charPositionInLine, String msg, RecognitionException e) {
                String pretty = msg;
                String targetedHint = null;
                String foreign = offendingSymbol instanceof org.antlr.v4.runtime.Token token
                        ? foreignSyntax(parser, token, msg) : null;
                if (foreign != null) {
                    int split = foreign.indexOf('\n');
                    pretty = foreign.substring(0, split);
                    targetedHint = foreign.substring(split + 1);
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
     * A message and hint (separated by a newline) for a construct from another
     * language that the grammar rejects, or null. These are the mistakes people
     * and models make first when they have only seen Python, Java or C.
     */
    private static String foreignSyntax(SprigParser parser, Token token, String raw) {
        TokenStream stream = parser.getTokenStream();
        int index = token.getTokenIndex();
        int previous = index > 0 ? stream.get(index - 1).getType() : Token.INVALID_TYPE;
        int beforePrevious = index > 1 ? stream.get(index - 2).getType() : Token.INVALID_TYPE;
        int type = token.getType();
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
        if (type == SprigLexer.COLON && raw.contains("expecting '->'")) {
            return "A function declares its result type before ':'\n"
                    + "Write 'func name(parameter: Type) -> ResultType:', and '-> Unit' when it returns nothing.";
        }
        return null;
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
