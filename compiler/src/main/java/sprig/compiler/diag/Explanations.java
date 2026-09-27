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
        boolean known = CodeDocs.all().containsKey(code);
        out.put("known", known);
        out.put("meaning", CodeDocs.describe(code));
        String topic = topic(code);
        out.put("documentationTopic", topic);
        out.put("relatedHelp", RepairHints.helpTopic(code) == null ? topic : RepairHints.helpTopic(code));
        out.put("whyMatters", "The rule keeps Sprig programs explicit and statically predictable.");
        out.put("confusedWith", List.of());
        out.put("commonCauses", List.of("The source violates the " + topic + " rule described by this code."));
        out.put("safeFixes", List.of("Inspect the source range and expected/actual types, then change the program explicitly."));
        out.put("badExample", null);
        out.put("goodExample", null);
        out.put("relatedCodes", List.of());
        String kind = RepairHints.repairKind(code);
        out.put("repair", kind == null ? null : Map.of("kind", kind, "machineApplicable", false));
        switch (code) {
            case Codes.TYPE_NULLABLE -> {
                out.put("whyMatters", "null must be handled explicitly; Sprig never dereferences a possibly-absent value.");
                out.put("confusedWith", List.of("Kotlin safe calls (?.)", "Java unchecked references"));
                out.put("commonCauses", List.of("A nullable Sprig value is dereferenced or passed to a non-null parameter.",
                        "A Java reference result was used without a null check."));
                out.put("safeFixes", List.of("Check value != null before use, and handle the absent branch.",
                        "Return a default, throw Error, or narrow through an if/elif chain."));
                out.put("badExample", "let value: String? = null\nprint(value.length())");
                out.put("goodExample", "if value != null:\n    print(value.length())");
                out.put("relatedCodes", List.of(Codes.TYPE_NULL));
            }
            case Codes.TYPE_NULL -> {
                out.put("whyMatters", "Only T? admits null; this keeps non-null types honest without annotations or flow analysis.");
                out.put("confusedWith", List.of("Java nullable annotations", "TypeScript strictNullChecks"));
                out.put("commonCauses", List.of("null was assigned or passed where the declared type is not nullable."));
                out.put("safeFixes", List.of("Declare the target as T? and check for null before use.",
                        "Substitute a real value."));
                out.put("badExample", "let name: String = null");
                out.put("goodExample", "let name: String? = null");
                out.put("relatedCodes", List.of(Codes.TYPE_NULLABLE));
            }
            case Codes.TYPE_MISMATCH -> {
                out.put("whyMatters", "Sprig has no implicit conversions; every type change is visible in source.");
                out.put("confusedWith", List.of("Java widening conversions", "Python duck typing"));
                out.put("commonCauses", List.of("A value of one type was passed where another is declared."));
                out.put("safeFixes", List.of("Use the expected type, or an explicit conversion method such as toString(), toInt(), toFloatExact()."));
                out.put("relatedCodes", List.of(Codes.NUM_CONVERSION, Codes.TYPE_ASSIGN, Codes.CALL_POSITIONAL_REQUIRED));
            }
            case Codes.NUM_DIVISION -> {
                out.put("whyMatters", "Integer / silently truncates in many languages; Sprig requires the intent to be explicit.");
                out.put("confusedWith", List.of("Java / on integers", "Python //"));
                out.put("commonCauses", List.of("Integer division was written with / instead of divTrunc."));
                out.put("safeFixes", List.of("Use value.divTrunc(divisor) only when truncation is intended.",
                        "Convert to Float or Decimal when fractional arithmetic is intended."));
                out.put("badExample", "let q = 7 / 2");
                out.put("goodExample", "let q = (7).divTrunc(2)");
                out.put("relatedCodes", List.of(Codes.NUM_CONVERSION, Codes.NUM_MIXED));
            }
            case Codes.NUM_CONVERSION, Codes.NUM_MIXED -> {
                out.put("whyMatters", "Numeric conversions can lose range or precision; Sprig names the loss instead of hiding it.");
                out.put("confusedWith", List.of("Java implicit numeric promotion", "C integer promotion"));
                out.put("commonCauses", List.of("Implicit conversion could lose range, precision, or numeric meaning."));
                out.put("safeFixes", List.of("Use an explicit exact conversion (toIntExact, toFloatExact) or an explicitly lossy one after deciding the precision."));
                out.put("relatedCodes", List.of(Codes.NUM_RANGE, Codes.NUM_DIVISION));
            }
            case Codes.COLLECTION_IMMUTABLE -> {
                out.put("whyMatters", "List and Map are read-only; mutation requires a Mutable value, so aliasing is explicit.");
                out.put("confusedWith", List.of("Java List mutation", "Python list mutation"));
                out.put("commonCauses", List.of("append/set/remove/clear/sort was called on List or Map."));
                out.put("safeFixes", List.of("Copy with toMutableList()/toMutableMap(), mutate the copy, then toList()/toMap().",
                        "Build the collection as MutableList/MutableMap from the start."));
                out.put("badExample", "let xs: List[Int] = [1]\nxs.append(2)");
                out.put("goodExample", "let xs: MutableList[Int] = [1].toMutableList()\nxs.append(2)");
                out.put("relatedCodes", List.of(Codes.TYPE_MISMATCH));
            }
            case Codes.MATCH_NONEXHAUSTIVE -> {
                out.put("whyMatters", "An exhaustive match makes adding a variant case a compile-time change, not a silent fallthrough.");
                out.put("confusedWith", List.of("Java switch default", "Rust non-exhaustive matches", "Kotlin else branches"));
                out.put("commonCauses", List.of("A variant or enum gained a case, or the match omitted one."));
                out.put("safeFixes", List.of("Add an explicit case branch for every missing case reported by the diagnostic."));
                out.put("badExample", "match expr:\n    case Expr.Literal as lit:\n        print(lit.value)  # other cases missing");
                out.put("goodExample", "match expr:\n    case Expr.Literal as lit:\n        print(lit.value)\n    case Expr.Add as add:\n        print(add.left)");
                out.put("relatedCodes", List.of(Codes.MATCH_DUPLICATE, Codes.MATCH_WRONG_TYPE));
            }
            case Codes.GENERIC_ARGS_REQUIRED, Codes.GENERIC_ARITY -> {
                out.put("whyMatters", "Sprig does not infer type arguments, so generic uses are fully written out and readable.");
                out.put("confusedWith", List.of("Java diamond inference", "TypeScript generic inference"));
                out.put("commonCauses", List.of("A generic declaration was used without explicit [Type] arguments, or with the wrong count."));
                out.put("safeFixes", List.of("Write Box[Int](...), identity[String](...), or Entry[String, Int](...) with every parameter in declaration order."));
                out.put("relatedCodes", List.of(Codes.GENERIC_ARITY, Codes.GENERIC_ARGS_REQUIRED));
            }
            case Codes.GENERIC_CONSTRAINT -> {
                out.put("whyMatters", "Only capabilities the compiler can enforce are accepted; unimplemented ones are refused, not ignored.");
                out.put("commonCauses", List.of("requires X: Comparable or another unsupported capability was requested."));
                out.put("safeFixes", List.of("Use requires X: Equatable for value equality.",
                        "Write an explicit comparison function for ordered logic."));
                out.put("relatedCodes", List.of(Codes.GENERIC_ARGS_REQUIRED));
            }
            case Codes.FLOW_THROWS -> {
                out.put("whyMatters", "Checked errors are visible at every call site; nothing fails silently.");
                out.put("confusedWith", List.of("Java checked exceptions", "Python uncaught exceptions"));
                out.put("commonCauses", List.of("A call may throw Error or a checked Java exception that this function neither catches nor declares."));
                out.put("safeFixes", List.of("Wrap the call in try/catch, or add throws to the function signature and propagate."));
                out.put("badExample", "func load(path: String) -> String:\n    return files.read_utf8(path)");
                out.put("goodExample", "func load(path: String) -> String throws Error, IOException:\n    return files.read_utf8(path)");
                out.put("relatedCodes", List.of(Codes.NAME_IMPORT, Codes.TYPE_MISMATCH));
            }
            case Codes.NAME_UNRESOLVED -> {
                out.put("whyMatters", "Every name is declared before use; there is no implicit global or dynamic lookup.");
                out.put("confusedWith", List.of("Python implicit globals", "JavaScript hoisting"));
                out.put("commonCauses", List.of("A typo, a missing import, or a declaration placed after first use."));
                out.put("safeFixes", List.of("Check the spelling, add the import, or move the declaration before use."));
                out.put("relatedCodes", List.of(Codes.NAME_IMPORT, Codes.NAME_NOT_A_VALUE));
            }
            case Codes.JVM_MEMBER, Codes.JVM_AMBIGUOUS -> {
                out.put("whyMatters", "Java calls are resolved against real signatures; unsupported boundaries are reported instead of guessed.");
                out.put("confusedWith", List.of("Java automatic boxing", "Kotlin SAM conversion"));
                out.put("commonCauses", List.of("Wrong arity, numeric narrowing, nullable argument, array/varargs boundary, or ambiguous overload."));
                out.put("safeFixes", List.of("Run sprig api <fully.qualified.Class> --json; choose a supported signature and make conversions explicit."));
                out.put("relatedCodes", List.of(Codes.TYPE_NULLABLE, Codes.JVM_CLASS));
            }
            case Codes.JVM_CLASSPATH -> {
                out.put("whyMatters", "Classpath state is explicit so builds are reproducible instead of environment-dependent.");
                out.put("commonCauses", List.of("A JAR or directory passed to --classpath does not exist or cannot be read."));
                out.put("safeFixes", List.of("Use an existing local JAR/directory path; paths resolve against the current working directory."));
            }
            case Codes.PROJECT_LOCK_MISSING, Codes.PROJECT_LOCK_STALE -> {
                out.put("whyMatters", "The lockfile makes dependency resolution deterministic and offline-safe.");
                out.put("commonCauses", List.of("sprig.toml changed, the SDK std bundle changed, or the project was never resolved."));
                out.put("safeFixes", List.of("Run sprig resolve in the project root, then retry the command."));
                out.put("relatedCodes", List.of(Codes.PROJECT_NOT_EXPORTED, Codes.DEP_OFFLINE));
            }
            case Codes.PROJECT_NOT_EXPORTED -> {
                out.put("whyMatters", "A dependency exposes an explicit module list; internal modules stay internal.");
                out.put("commonCauses", List.of("An @alias/module.spr import names a module missing from the dependency's exports."));
                out.put("safeFixes", List.of("Import an exported module, or add the module to the dependency package's [project] exports."));
            }
            case Codes.API_MEMBER -> {
                out.put("whyMatters", "API queries should fail loudly rather than return an empty success.");
                out.put("commonCauses", List.of("The --member name does not exist, or uses the wrong Type.member spelling."));
                out.put("safeFixes", List.of("Run sprig api <module> --json without --member and copy the exact declaration/member name."));
            }
            case Codes.NAME_IMPORT_CYCLE -> {
                out.put("whyMatters", "Acyclic imports keep module initialization order defined.");
                out.put("commonCauses", List.of("Two modules import each other directly or transitively."));
                out.put("safeFixes", List.of("Move the shared declarations into a third module that both import."));
            }
            case Codes.LEX_TAB -> {
                out.put("whyMatters", "Indentation is syntax; one invisible tab would make block structure ambiguous.");
                out.put("confusedWith", List.of("Python tab/space mixing", "Makefiles"));
                out.put("commonCauses", List.of("An editor inserted a literal tab."));
                out.put("safeFixes", List.of("Replace tabs with spaces (four per level in repository examples)."));
            }
            case Codes.TYPE_CONDITION -> {
                out.put("whyMatters", "No implicit truthiness means conditions are always Bool comparisons.");
                out.put("confusedWith", List.of("Python truthiness", "JavaScript coercions"));
                out.put("commonCauses", List.of("A non-Bool value was used as a condition."));
                out.put("safeFixes", List.of("Write an explicit comparison such as xs.size() > 0 or value != null."));
            }
            case Codes.CALL_NAMED_REQUIRED, Codes.CALL_POSITIONAL_REQUIRED -> {
                out.put("whyMatters", "Constructors are named for readability; functions and Java calls stay positional and unambiguous.");
                out.put("confusedWith", List.of("Kotlin named arguments everywhere", "Python keyword arguments"));
                out.put("commonCauses", List.of("Positional arguments were used for a class/variant constructor, or named arguments for a function."));
                out.put("safeFixes", List.of("Use Person(name=\"Ada\") for constructors; use add(1, 2) for functions."));
            }
            case Codes.TYPE_FUNCTION_ARITY -> {
                out.put("whyMatters", "The callable ABI is explicit and bounded; no variadic or untyped signatures exist.");
                out.put("confusedWith", List.of("JavaScript variadic lambdas", "Java functional interfaces"));
                out.put("commonCauses", List.of("A lambda or fn type has more than three parameters."));
                out.put("safeFixes", List.of("Group parameters into a class or variant, then pass one value."));
            }
            case Codes.TYPE_CAPTURE -> {
                out.put("whyMatters", "Lambdas capture immutable bindings, avoiding shared mutable state surprises.");
                out.put("confusedWith", List.of("Java effectively-final captures", "JavaScript closures over let"));
                out.put("commonCauses", List.of("A lambda body reads a var local."));
                out.put("safeFixes", List.of("Copy the var into a let binding before the lambda."));
            }
            default -> { }
        }
        return out;
    }

    private static String topic(String code) {
        if (code.startsWith("SPR-JVM-")) return "jvm";
        if (code.startsWith("SPR-NUM-")) return "numerics";
        if (code.startsWith("SPR-MATCH-")) return "match";
        if (code.startsWith("SPR-COLLECTION-")) return "collections";
        if (code.startsWith("SPR-GENERIC-") || code.contains("GENERIC")) return "generics";
        if (code.startsWith("SPR-PROJECT-") || code.startsWith("SPR-DEP-")) return "dependencies";
        if (code.startsWith("SPR-API-")) return "agents";
        if (code.startsWith("SPR-LEX-") || code.startsWith("SPR-SYNTAX-")) return "language";
        if (code.startsWith("SPR-FLOW-")) return "errors";
        if (code.startsWith("SPR-CALL-")) return "classes";
        if (code.contains("NULL")) return "nullability";
        if (code.contains("FUNCTION") || code.contains("CALLABLE")) return "functions";
        return "language";
    }
}
