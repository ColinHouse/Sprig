package sprig.compiler.diag;

/** Stable diagnostic codes. Never renumber or reuse a code for a new meaning. */
public final class Codes {
    private Codes() {
    }

    // CLI
    public static final String CLI_OPTION = "SPR-CLI-OPTION";

    // LEX
    public static final String LEX_TAB = "SPR-LEX-TAB";
    public static final String LEX_INDENT_FIRST = "SPR-LEX-INDENT-FIRST";
    public static final String LEX_INDENT_INCONSISTENT = "SPR-LEX-INDENT-INCONSISTENT";
    public static final String LEX_UNCLOSED = "SPR-LEX-UNCLOSED";
    public static final String LEX_UNMATCHED = "SPR-LEX-UNMATCHED";
    public static final String LEX_CHAR = "SPR-LEX-CHAR";
    public static final String LEX_STRING = "SPR-LEX-STRING";

    // SYNTAX
    public static final String SYNTAX_ERROR = "SPR-SYNTAX-ERROR";

    // NAME
    public static final String NAME_UNRESOLVED = "SPR-NAME-UNRESOLVED";
    public static final String NAME_DUPLICATE = "SPR-NAME-DUPLICATE";
    public static final String NAME_DUPLICATE_MEMBER = "SPR-NAME-DUPLICATE-MEMBER";
    public static final String NAME_FIELD_SHADOW = "SPR-NAME-FIELD-SHADOW";
    public static final String NAME_LET_ASSIGN = "SPR-NAME-LET-ASSIGN";
    public static final String NAME_NOT_A_TYPE = "SPR-NAME-NOT-A-TYPE";
    public static final String NAME_NOT_A_VALUE = "SPR-NAME-NOT-A-VALUE";
    public static final String NAME_MODULE = "SPR-NAME-MODULE";
    public static final String NAME_IMPORT = "SPR-NAME-IMPORT";
    public static final String NAME_IMPORT_CYCLE = "SPR-NAME-IMPORT-CYCLE";

    // TYPE
    public static final String TYPE_MISMATCH = "SPR-TYPE-MISMATCH";
    public static final String TYPE_NULL = "SPR-TYPE-NULL";
    public static final String TYPE_NULLABLE = "SPR-TYPE-NULLABLE";
    public static final String TYPE_CONDITION = "SPR-TYPE-CONDITION";
    public static final String TYPE_OPERAND = "SPR-TYPE-OPERAND";
    public static final String TYPE_RETURN = "SPR-TYPE-RETURN";
    public static final String TYPE_ASSIGN = "SPR-TYPE-ASSIGN";
    public static final String TYPE_INFER = "SPR-TYPE-INFER";
    public static final String TYPE_NOT_CALLABLE = "SPR-TYPE-NOT-CALLABLE";
    public static final String TYPE_UNIT = "SPR-TYPE-UNIT";
    public static final String TYPE_CAPTURE = "SPR-TYPE-CAPTURE";
    public static final String NUM_RANGE = "SPR-NUM-RANGE";
    public static final String NUM_CONVERSION = "SPR-NUM-CONVERSION";
    public static final String NUM_DIVISION = "SPR-NUM-DIVISION";
    public static final String NUM_MIXED = "SPR-NUM-MIXED";
    public static final String COLLECTION_IMMUTABLE = "SPR-COLLECTION-IMMUTABLE";

    // CALL
    public static final String CALL_NAMED_REQUIRED = "SPR-CALL-NAMED-REQUIRED";
    public static final String CALL_POSITIONAL_REQUIRED = "SPR-CALL-POSITIONAL-REQUIRED";
    public static final String CALL_ARITY = "SPR-CALL-ARITY";
    public static final String CALL_UNKNOWN_FIELD = "SPR-CALL-UNKNOWN-FIELD";
    public static final String CALL_MISSING_FIELD = "SPR-CALL-MISSING-FIELD";
    public static final String CALL_DUPLICATE_FIELD = "SPR-CALL-DUPLICATE-FIELD";

    // MATCH
    public static final String MATCH_NONEXHAUSTIVE = "SPR-MATCH-NONEXHAUSTIVE";
    public static final String MATCH_DUPLICATE = "SPR-MATCH-DUPLICATE";
    public static final String MATCH_WRONG_TYPE = "SPR-MATCH-WRONG-TYPE";
    public static final String MATCH_ENUM_BINDER = "SPR-MATCH-ENUM-BINDER";
    public static final String MATCH_SCRUTINEE = "SPR-MATCH-SCRUTINEE";
    public static final String MATCH_UNKNOWN_CASE = "SPR-MATCH-UNKNOWN-CASE";

    // FLOW
    public static final String FLOW_MISSING_RETURN = "SPR-FLOW-MISSING-RETURN";
    public static final String FLOW_UNREACHABLE = "SPR-FLOW-UNREACHABLE";
    public static final String FLOW_BREAK = "SPR-FLOW-BREAK";
    public static final String FLOW_CONTINUE = "SPR-FLOW-CONTINUE";
    public static final String FLOW_THROWS = "SPR-FLOW-THROWS";

    // JVM
    public static final String JVM_CLASS = "SPR-JVM-CLASS";
    public static final String JVM_CLASSPATH = "SPR-JVM-CLASSPATH";
    public static final String JVM_MEMBER = "SPR-JVM-MEMBER";
    public static final String JVM_AMBIGUOUS = "SPR-JVM-AMBIGUOUS";
    public static final String JVM_COMPILE = "SPR-JVM-COMPILE";
    public static final String JVM_INTERNAL = "SPR-JVM-INTERNAL";

    // RUNTIME
    public static final String RUNTIME_ERROR = "SPR-RUNTIME-ERROR";
    public static final String RUNTIME_EXCEPTION = "SPR-RUNTIME-EXCEPTION";
    public static final String PROGRAM_EXIT = "SPR-PROGRAM-EXIT";
}
