package sprig.compiler.diag;

import java.util.LinkedHashMap;
import java.util.Map;

/** Short explanations for every stable diagnostic code ({@code sprig explain}). */
public final class CodeDocs {
    private static final Map<String, String> DOCS = Map.ofEntries(
            Map.entry(Codes.CLI_OPTION, "A CLI command is missing a required argument or has an unknown option."),
            Map.entry(Codes.LEX_TAB, "Tabs are forbidden; Sprig indentation uses spaces only."),
            Map.entry(Codes.LEX_INDENT_FIRST, "The first code line of a file must start at column 1."),
            Map.entry(Codes.LEX_INDENT_INCONSISTENT, "A dedent must return to a previous indentation level."),
            Map.entry(Codes.LEX_UNCLOSED, "A '(' '[' or '{' was opened and never closed."),
            Map.entry(Codes.LEX_UNMATCHED, "A closing delimiter has no matching opener."),
            Map.entry(Codes.LEX_CHAR, "The input contains a character outside the Sprig lexer."),
            Map.entry(Codes.LEX_STRING, "A string literal is unterminated or contains an invalid escape."),
            Map.entry(Codes.SYNTAX_ERROR, "The token sequence does not match the Sprig grammar."),
            Map.entry(Codes.NAME_UNRESOLVED, "A name has no declaration in the current scope chain."),
            Map.entry(Codes.NAME_DUPLICATE, "Two declarations share one name in one namespace."),
            Map.entry(Codes.NAME_DUPLICATE_MEMBER, "A class/variant declares the same member twice."),
            Map.entry(Codes.NAME_FIELD_SHADOW, "A parameter/local cannot shadow a current-class field."),
            Map.entry(Codes.NAME_LET_ASSIGN, "let bindings and let fields cannot be reassigned."),
            Map.entry(Codes.NAME_NOT_A_TYPE, "A value name was used where a type is required."),
            Map.entry(Codes.NAME_NOT_A_VALUE, "A type or module name was used as a value."),
            Map.entry(Codes.NAME_MODULE, "Module import/alias problem."),
            Map.entry(Codes.NAME_IMPORT, "An imported file or class cannot be resolved."),
            Map.entry(Codes.NAME_IMPORT_CYCLE, "Sprig modules form an import cycle."),
            Map.entry(Codes.TYPE_MISMATCH, "Expected and actual types are not compatible."),
            Map.entry(Codes.TYPE_NULL, "null is only assignable to an explicit nullable type T?."),
            Map.entry(Codes.TYPE_NULLABLE, "A possibly-null value is used where non-null is required; check for null first."),
            Map.entry(Codes.TYPE_CONDITION, "Conditions must be Bool; Sprig has no truthiness."),
            Map.entry(Codes.TYPE_OPERAND, "Operator or method is not defined for this operand type."),
            Map.entry(Codes.TYPE_RETURN, "Returned value does not match the declared return type."),
            Map.entry(Codes.TYPE_ASSIGN, "Assignment value does not match the target type."),
            Map.entry(Codes.TYPE_INFER, "The type cannot be inferred without an annotation."),
            Map.entry(Codes.TYPE_NOT_CALLABLE, "The callee is not callable (or a method name was used as a value)."),
            Map.entry(Codes.TYPE_UNIT, "Unit is only valid as a function/method result; it cannot be used as a field, parameter, collection element, or ordinary value."),
            Map.entry(Codes.TYPE_CAPTURE, "A lambda captures a var local; copy it into a let binding first."),
            Map.entry(Codes.GENERIC_ARITY, "A generic declaration was used with the wrong number of type arguments (supply every parameter in declaration order)."),
            Map.entry(Codes.GENERIC_ARGS_REQUIRED, "A generic function or constructor needs explicit [Type] arguments; Sprig does not infer them."),
            Map.entry(Codes.GENERIC_NULLABLE, "This type parameter is used with '?' in the declaration, so its argument must be non-nullable."),
            Map.entry(Codes.GENERIC_CONSTRAINT, "An unsupported capability was requested; v0.8 implements Equatable only."),
            Map.entry(Codes.PROJECT_MANIFEST, "sprig.toml is missing, malformed, or lacks a required field."),
            Map.entry(Codes.PROJECT_ENTRY, "The requested project entry point does not exist or the named --bin is unknown."),
            Map.entry(Codes.PROJECT_UNSUPPORTED, "The operation needs project features that are not implemented yet (dependency resolution, lockfiles)."),
            Map.entry(Codes.NUM_RANGE, "A numeric literal is outside the target range or underflows to zero."),
            Map.entry(Codes.NUM_CONVERSION, "Numeric conversion may lose precision or range; use an explicit exact or lossy method."),
            Map.entry(Codes.NUM_DIVISION, "Integer / is rejected; use divTrunc for deliberate truncation or explicit Float/Decimal arithmetic."),
            Map.entry(Codes.NUM_MIXED, "Mixed binary/decimal or integer/floating arithmetic requires an explicit conversion."),
            Map.entry(Codes.COLLECTION_IMMUTABLE, "List/Map are read-only; convert with toMutableList()/toMutableMap()."),
            Map.entry(Codes.CALL_NAMED_REQUIRED, "Sprig class and variant constructors require named arguments."),
            Map.entry(Codes.CALL_POSITIONAL_REQUIRED, "Functions and JVM calls require positional arguments."),
            Map.entry(Codes.CALL_ARITY, "Wrong number of arguments."),
            Map.entry(Codes.CALL_UNKNOWN_FIELD, "Named argument does not match any field."),
            Map.entry(Codes.CALL_MISSING_FIELD, "A required field was not provided."),
            Map.entry(Codes.CALL_DUPLICATE_FIELD, "The same named field was provided twice."),
            Map.entry(Codes.MATCH_NONEXHAUSTIVE, "Every enum/variant case must have a match branch; there is no default."),
            Map.entry(Codes.MATCH_DUPLICATE, "A match branch repeats a case."),
            Map.entry(Codes.MATCH_WRONG_TYPE, "A match branch belongs to a different enum/variant."),
            Map.entry(Codes.MATCH_ENUM_BINDER, "Payloadless enum cases cannot bind 'as name'."),
            Map.entry(Codes.MATCH_SCRUTINEE, "match requires a non-nullable enum or variant value."),
            Map.entry(Codes.MATCH_UNKNOWN_CASE, "The case name does not exist on the matched type."),
            Map.entry(Codes.FLOW_MISSING_RETURN, "A non-Unit function must return on every path."),
            Map.entry(Codes.FLOW_UNREACHABLE, "Statement follows a statement that always exits."),
            Map.entry(Codes.FLOW_BREAK, "break is only valid inside a loop."),
            Map.entry(Codes.FLOW_CONTINUE, "continue is only valid inside a loop."),
            Map.entry(Codes.FLOW_THROWS, "A recoverable error must be declared with throws or caught."),
            Map.entry(Codes.JVM_CLASS, "The imported Java class could not be loaded."),
            Map.entry(Codes.JVM_CLASSPATH, "A --classpath entry is missing, empty, or not a JAR/directory."),
            Map.entry(Codes.JVM_MEMBER, "No Java method/constructor/field matches this call."),
            Map.entry(Codes.JVM_AMBIGUOUS, "The Java overload is ambiguous for these argument types."),
            Map.entry(Codes.JVM_COMPILE, "The generated Java source did not compile; may be a compiler bug."),
            Map.entry(Codes.JVM_INTERNAL, "Internal compiler or tooling failure."),
            Map.entry(Codes.RUNTIME_ERROR, "Uncaught Sprig Error value at runtime."),
            Map.entry(Codes.RUNTIME_EXCEPTION, "Uncaught JVM exception at runtime."),
            Map.entry(Codes.PROGRAM_EXIT, "The Sprig program exited with a non-zero process status."));

    private CodeDocs() {
    }

    public static String describe(String code) {
        return DOCS.getOrDefault(code, "Unknown diagnostic code.");
    }

    /** All codes in stable code order (used by {@code sprig codes}). */
    public static Map<String, String> all() {
        Map<String, String> sorted = new LinkedHashMap<>();
        DOCS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }
}
