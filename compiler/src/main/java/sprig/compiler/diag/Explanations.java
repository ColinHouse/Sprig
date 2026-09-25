package sprig.compiler.diag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import sprig.compiler.tooling.Catalog;

/** Structured explanations with conservative fixes; no automatic semantic edits. */
public final class Explanations {
    private Explanations() {}

    public static Map<String, Object> describe(String code) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schemaVersion", 1);
        out.put("compilerVersion", Catalog.COMPILER_VERSION);
        out.put("code", code);
        out.put("known", CodeDocs.all().containsKey(code));
        out.put("meaning", CodeDocs.describe(code));
        String topic = code.startsWith("SPR-JVM-") ? "jvm"
                : code.startsWith("SPR-NUM-") ? "numerics"
                : code.startsWith("SPR-MATCH-") ? "match"
                : code.startsWith("SPR-COLLECTION-") ? "collections"
                : code.contains("NULL") ? "nullability" : "language";
        out.put("documentationTopic", topic);
        out.put("commonCauses", List.of("The source violates the " + topic + " rule described by this code."));
        out.put("safeFixes", List.of("Inspect the source range and expected/actual types, then change the program explicitly."));
        out.put("badExample", null);
        out.put("goodExample", null);
        out.put("relatedCodes", List.of());
        switch (code) {
            case Codes.TYPE_NULLABLE -> {
                out.put("commonCauses", List.of("A nullable Sprig value is dereferenced or passed to a non-null Java parameter."));
                out.put("safeFixes", List.of("Check value != null before use, and handle the absent branch."));
                out.put("badExample", "let value: String? = null\nprint(value.length())");
                out.put("goodExample", "if value != null:\n    print(value.length())");
                out.put("relatedCodes", List.of(Codes.TYPE_NULL));
            }
            case Codes.MATCH_NONEXHAUSTIVE -> {
                out.put("commonCauses", List.of("A variant or enum gained a case, or the match omitted one."));
                out.put("safeFixes", List.of("Add an explicit case branch for every missing case reported by the diagnostic."));
                out.put("badExample", "match expr:\n    case Expr.Literal as lit:\n        print(lit.value)  # Add all other cases");
                out.put("goodExample", "match expr:\n    case Expr.Literal as lit:\n        print(lit.value)\n    case Expr.Add as add:\n        print(add.left)");
                out.put("relatedCodes", List.of(Codes.MATCH_DUPLICATE, Codes.MATCH_WRONG_TYPE));
            }
            case Codes.JVM_MEMBER, Codes.JVM_AMBIGUOUS -> {
                out.put("commonCauses", List.of("Wrong arity, numeric narrowing, nullable argument, array/varargs boundary, or ambiguous overload."));
                out.put("safeFixes", List.of("Run sprig api <fully.qualified.Class> --json; choose a supported signature and make conversions explicit."));
                out.put("relatedCodes", List.of(Codes.TYPE_NULLABLE, Codes.JVM_CLASS));
            }
            case Codes.JVM_CLASSPATH -> {
                out.put("commonCauses", List.of("A JAR or directory passed to --classpath does not exist or cannot be read."));
                out.put("safeFixes", List.of("Use an existing local JAR/directory path; paths resolve against the current working directory."));
            }
            case Codes.NUM_CONVERSION, Codes.NUM_MIXED -> {
                out.put("commonCauses", List.of("Implicit conversion could lose range, precision, or numeric meaning."));
                out.put("safeFixes", List.of("Use an explicit exact conversion or an explicitly lossy conversion after deciding the intended precision."));
            }
            default -> { }
        }
        return out;
    }
}
