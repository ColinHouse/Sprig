package sprig.compiler.diag;

import java.util.Map;

/** Central, code-driven repair hints; additive to the stable diagnostic schema. */
final class RepairHints {
    private RepairHints() {}

    private record Hint(String helpTopic, String repairKind) {}

    private static final Map<String, Hint> HINTS = Map.ofEntries(
            Map.entry(Codes.TYPE_NULLABLE, new Hint("nullability", "narrow-before-use")),
            Map.entry(Codes.TYPE_NULL, new Hint("nullability", "use-nullable-type-or-handle-absence")),
            Map.entry(Codes.NUM_DIVISION, new Hint("numerics", "use-divTrunc-only-if-truncation-is-intended")),
            Map.entry(Codes.NUM_CONVERSION, new Hint("numerics", "make-numeric-conversion-explicit")),
            Map.entry(Codes.NUM_MIXED, new Hint("numerics", "make-numeric-conversion-explicit")),
            Map.entry(Codes.COLLECTION_IMMUTABLE, new Hint("collections", "convert-with-toMutableList-or-toMutableMap")),
            Map.entry(Codes.MATCH_NONEXHAUSTIVE, new Hint("match", "add-explicit-case-for-every-missing-case")),
            Map.entry(Codes.GENERIC_ARGS_REQUIRED, new Hint("generics", "write-explicit-type-arguments")),
            Map.entry(Codes.GENERIC_CONSTRAINT, new Hint("generics", "remove-unsupported-capability")),
            Map.entry(Codes.FLOW_THROWS, new Hint("errors", "declare-throws-or-catch")),
            Map.entry(Codes.JVM_MEMBER, new Hint("jvm", "inspect-api-and-make-arguments-explicit")),
            Map.entry(Codes.JVM_AMBIGUOUS, new Hint("jvm", "inspect-api-and-make-arguments-explicit")),
            Map.entry(Codes.PROJECT_LOCK_MISSING, new Hint("dependencies", "run-sprig-resolve")),
            Map.entry(Codes.PROJECT_LOCK_STALE, new Hint("dependencies", "run-sprig-resolve")),
            Map.entry(Codes.PROJECT_NOT_EXPORTED, new Hint("dependencies", "add-module-to-dependency-exports")),
            Map.entry(Codes.API_MEMBER, new Hint("agents", "inspect-module-api-without-member-filter")));

    static String helpTopic(String code) {
        Hint hint = HINTS.get(code);
        return hint == null ? null : hint.helpTopic();
    }

    static String repairKind(String code) {
        Hint hint = HINTS.get(code);
        return hint == null ? null : hint.repairKind();
    }

    static void apply(Diagnostic diagnostic) {
        if (diagnostic.relatedHelp == null) {
            diagnostic.relatedHelp = helpTopic(diagnostic.code);
        }
        if (diagnostic.repair == null && repairKind(diagnostic.code) != null) {
            diagnostic.withRepair(repairKind(diagnostic.code), false);
        }
    }
}
