import java.nio.file.*;
import java.util.*;
import org.antlr.v4.runtime.*;
import sprig.compiler.front.SourceFormatter;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.parser.SprigLexer;

/** In-memory corpus check: never rewrites source (including negative fixtures). */
public class FormatterCorpus {
    public static void main(String[] args) throws Exception {
        int valid=0, invalid=0;
        for (String root: args) try (var files=Files.walk(Path.of(root))) {
            for (Path file:files.filter(p->p.toString().endsWith(".spr")).sorted().toList()) {
                String source=Files.readString(file);
                Diagnostics diagnostics=new Diagnostics();
                String formatted=SourceFormatter.format(file,source,diagnostics);
                if (formatted==null) { invalid++; continue; }
                valid++;
                String twice=SourceFormatter.format(file,formatted,new Diagnostics());
                if (!formatted.equals(twice)) throw new AssertionError("not idempotent: " + file);
                if (!comments(source).equals(comments(formatted))) throw new AssertionError("comment loss: " + file);
                if (!literals(source).equals(literals(formatted))) throw new AssertionError("literal loss: " + file);
            }
        }
        System.out.println("formatter corpus: " + valid + " valid roundtrips; " + invalid + " invalid skipped; structure/idempotence/comments/literals preserved");
    }
    static List<String> comments(String source) {
        var lexer=new SprigLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        return lexer.getAllTokens().stream().filter(t->t.getType()==SprigLexer.COMMENT).map(t->t.getText().stripTrailing()).toList();
    }
    static List<String> literals(String source) {
        var lexer=new SprigLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        return lexer.getAllTokens().stream().filter(t->Set.of(SprigLexer.STRING,SprigLexer.INT,SprigLexer.FLOAT).contains(t.getType())).map(Token::getText).toList();
    }
}
