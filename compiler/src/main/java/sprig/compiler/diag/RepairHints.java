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
            Map.entry(Codes.MATCH_RESULT, new Hint("match", "make-branch-results-assignable-without-lossy-conversions")),
            Map.entry(Codes.MATCH_INFERENCE, new Hint("match", "write-explicit-match-result-type")),
            Map.entry(Codes.MATCH_NONEXHAUSTIVE, new Hint("match", "add-explicit-case-for-every-missing-case")),
            Map.entry(Codes.GENERIC_ARGS_REQUIRED, new Hint("generics", "write-explicit-type-arguments")),
            Map.entry(Codes.GENERIC_CONSTRAINT, new Hint("generics", "remove-unsupported-capability")),
            Map.entry(Codes.FLOW_THROWS, new Hint("errors", "declare-throws-or-catch")),
            Map.entry(Codes.FLOW_RETHROWS, new Hint("errors", "give-a-callable-parameter-throws-Error-or-drop-rethrows")),
            Map.entry(Codes.FLOW_CATCH_NEVER_THROWN, new Hint("errors", "remove-the-catch-clause-nothing-throws")),
            Map.entry(Codes.FLOW_THROWS_UNUSED, new Hint("errors", "remove-the-exception-the-body-never-throws")),
            Map.entry(Codes.TYPE_CALLABLE_THROWS, new Hint("errors", "add-throws-Error-to-the-function-type-or-handle-inside")),
            Map.entry(Codes.JVM_MEMBER, new Hint("jvm", "inspect-api-and-make-arguments-explicit")),
            Map.entry(Codes.JVM_AMBIGUOUS, new Hint("jvm", "inspect-api-and-make-arguments-explicit")),
            Map.entry(Codes.PROJECT_LOCK_MISSING, new Hint("dependencies", "run-sprig-resolve")),
            Map.entry(Codes.PROJECT_LOCK_STALE, new Hint("dependencies", "run-sprig-resolve")),
            Map.entry(Codes.PROJECT_NOT_EXPORTED, new Hint("dependencies", "add-module-to-dependency-exports")),
            Map.entry(Codes.API_MEMBER, new Hint("agents", "inspect-module-api-without-member-filter")),
            Map.entry(Codes.MODULE_EXPORT, new Hint("modules", "export-one-public-declaration")),
            Map.entry(Codes.MODULE_EXPORT_ORDER, new Hint("modules", "move-exports-after-imports")),
            Map.entry(Codes.TYPE_INFER, new Hint("types", "write-an-explicit-type-annotation")),
            Map.entry(Codes.TYPE_RETURN, new Hint("types", "make-return-value-match-declaration")),
            Map.entry(Codes.TYPE_ASSIGN, new Hint("types", "make-assignment-types-match")),
            Map.entry(Codes.TYPE_OPERAND, new Hint("types", "use-an-operation-defined-for-this-type")),
            Map.entry(Codes.TYPE_NOT_CALLABLE, new Hint("functions", "call-a-function-value-with-arguments")),
            Map.entry(Codes.TYPE_UNIT, new Hint("types", "use-a-non-unit-result")),
            Map.entry(Codes.CALL_ARITY, new Hint("classes", "match-the-declared-parameter-count")),
            Map.entry(Codes.CALL_MISSING_FIELD, new Hint("classes", "provide-every-required-field-or-add-a-default")),
            Map.entry(Codes.CALL_UNKNOWN_FIELD, new Hint("classes", "use-a-declared-field-name")),
            Map.entry(Codes.CALL_DUPLICATE_FIELD, new Hint("classes", "pass-each-field-once")),
            Map.entry(Codes.PROJECT_MANIFEST, new Hint("projects", "compare-with-sprig-init-and-project-json")),
            Map.entry(Codes.PROJECT_ENTRY, new Hint("projects", "create-the-entry-or-select-a-declared-bin")),
            Map.entry(Codes.PROJECT_LOCK_SCHEMA, new Hint("dependencies", "re-resolve-with-the-current-compiler")),
            Map.entry(Codes.DEP_OFFLINE, new Hint("dependencies", "resolve-once-with-network-then-use-offline")),
            Map.entry(Codes.DEP_CHECKSUM, new Hint("dependencies", "discard-the-corrupt-cache-entry-and-resolve")),
            Map.entry(Codes.DEP_MAVEN, new Hint("dependencies", "verify-exact-coordinates-and-network")),
            Map.entry(Codes.DEP_NOT_FOUND, new Hint("dependencies", "check-manifest-path-name-and-exports")),
            Map.entry(Codes.DEP_CYCLE, new Hint("dependencies", "break-the-dependency-cycle")),
            Map.entry(Codes.DEP_GIT, new Hint("dependencies", "verify-git-remote-ref-and-availability")),
            Map.entry(Codes.JVM_CLASS, new Hint("jvm", "check-class-name-classpath-and-package")),
            Map.entry(Codes.SYNTAX_ERROR, new Hint("language", "fix-the-first-syntax-error-then-recheck")),
            Map.entry(Codes.LEX_INDENT_INCONSISTENT, new Hint("language", "align-indentation-with-an-earlier-level")),
            Map.entry(Codes.NAME_DUPLICATE, new Hint("language", "rename-one-declaration")),
            Map.entry(Codes.NAME_LET_ASSIGN, new Hint("language", "declare-with-var-if-mutation-is-intended")),
            Map.entry(Codes.NAME_IMPORT, new Hint("modules", "check-import-path-alias-and-exports")),
            Map.entry(Codes.MATCH_SCRUTINEE, new Hint("match", "narrow-or-convert-to-a-non-null-enum-variant")),
            Map.entry(Codes.MATCH_DUPLICATE, new Hint("match", "remove-or-merge-the-duplicate-case")),
            Map.entry(Codes.MATCH_WRONG_TYPE, new Hint("match", "remove-the-foreign-case")),
            Map.entry(Codes.MATCH_UNKNOWN_CASE, new Hint("match", "use-a-declared-case-name")),
            Map.entry(Codes.MATCH_ENUM_BINDER, new Hint("match", "remove-the-binder-from-a-payloadless-enum-case")),
            Map.entry(Codes.FLOW_MISSING_RETURN, new Hint("errors", "return-on-every-path")),
            Map.entry(Codes.FLOW_UNREACHABLE, new Hint("errors", "remove-the-unreachable-statement")),
            Map.entry(Codes.API_TARGET, new Hint("agents", "inspect-a-valid-module-or-project-target")),
            Map.entry(Codes.NUM_RANGE, new Hint("numerics", "use-a-literal-within-the-target-range")),
            Map.entry(Codes.LEX_STRING, new Hint("language", "close-the-string-and-use-supported-escapes")),
            Map.entry(Codes.LEX_UNCLOSED, new Hint("language", "balance-delimiters")),
            Map.entry(Codes.LEX_UNMATCHED, new Hint("language", "balance-delimiters")),
            Map.entry(Codes.LEX_CHAR, new Hint("language", "replace-the-reported-character")),
            Map.entry(Codes.LEX_INDENT_FIRST, new Hint("language", "start-the-first-code-line-at-column-1")),
            Map.entry(Codes.FLOW_BREAK, new Hint("errors", "use-loop-control-inside-a-loop")),
            Map.entry(Codes.FLOW_CONTINUE, new Hint("errors", "use-loop-control-inside-a-loop")),
            Map.entry(Codes.NAME_NOT_A_VALUE, new Hint("language", "construct-or-reference-a-value")),
            Map.entry(Codes.GENERIC_NULLABLE, new Hint("generics", "pass-a-non-nullable-type-argument")),
            Map.entry(Codes.RUNTIME_ERROR, new Hint("errors", "catch-or-declare-the-failing-operation")),
            Map.entry(Codes.RUNTIME_EXCEPTION, new Hint("errors", "catch-or-declare-the-failing-operation")),
            Map.entry(Codes.CLI_OPTION, new Hint("agents", "use-documented-command-arguments")),
            Map.entry(Codes.CONFORM_SOURCE, new Hint("conform", "declare-conform-next-to-a-local-non-generic-class")),
            Map.entry(Codes.CONFORM_TARGET, new Hint("conform", "import-a-public-non-generic-java-interface")),
            Map.entry(Codes.CONFORM_MEMBER, new Hint("conform", "match-the-java-signature-exactly")),
            Map.entry(Codes.CONFORM_OVERLOAD, new Hint("conform", "use-an-interface-with-unique-abstract-method-names")),
            Map.entry(Codes.CONFORM_EFFECTS, new Hint("conform", "make-throws-compatible-with-the-interface")),
            Map.entry(Codes.CONFORM_PARENT, new Hint("conform", "call-the-parent-view-only-as-a-method")));

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
