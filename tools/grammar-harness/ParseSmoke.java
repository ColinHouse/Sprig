import java.nio.file.Path;
import org.antlr.v4.runtime.*;

/** Usage: ParseSmoke file.spr; nonzero exit on lexical/layout/syntax errors. */
public final class ParseSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: ParseSmoke <file.spr>");
        var lexer = new SprigLexer(CharStreams.fromPath(Path.of(args[0])));
        var errors = new ErrorCounter();
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        var stream = new CommonTokenStream(new LayoutTokenSource(lexer));
        var parser = new SprigParser(stream);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        parser.program();
        if (errors.count > 0 || parser.getNumberOfSyntaxErrors() > 0) System.exit(1);
        System.out.println("PARSE OK: " + args[0]);
    }
    private static final class ErrorCounter extends BaseErrorListener {
        int count;
        @Override public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                int line, int charPositionInLine, String msg, RecognitionException e) {
            count++;
            System.err.println("line " + line + ":" + charPositionInLine + " " + msg);
        }
    }
}
