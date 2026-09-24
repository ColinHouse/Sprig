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
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
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
                if (offendingSymbol instanceof org.antlr.v4.runtime.Token token
                        && token.getType() == org.antlr.v4.runtime.Token.EOF) {
                    pretty = message(msg, "unexpected end of file");
                } else if (offendingSymbol instanceof org.antlr.v4.runtime.Token token) {
                    pretty = message(msg, "unexpected '" + token.getText().replace("\n", "\\n") + "'");
                }
                diagnostics.add(Diagnostic.error(Codes.SYNTAX_ERROR, Phase.SYNTAX, pretty, uri,
                        new Span(line - 1, charPositionInLine, line - 1, charPositionInLine + 1, -1, -1)));
            }
        });
        return parser.program();
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
