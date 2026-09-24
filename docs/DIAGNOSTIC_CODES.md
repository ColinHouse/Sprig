# Sprig diagnostic codes (stable)

Generated from `bin/sprig codes`. Codes never change meaning;
new behavior gets a new code. See also `sprig explain <code>`.

| Code | Meaning |
|---|---|
| SPR-CALL-ARITY | Wrong number of arguments. |
| SPR-CALL-DUPLICATE-FIELD | The same named field was provided twice. |
| SPR-CALL-MISSING-FIELD | A required field was not provided. |
| SPR-CALL-NAMED-REQUIRED | Sprig class and variant constructors require named arguments. |
| SPR-CALL-POSITIONAL-REQUIRED | Functions and JVM calls require positional arguments. |
| SPR-CALL-UNKNOWN-FIELD | Named argument does not match any field. |
| SPR-COLLECTION-IMMUTABLE | List/Map are read-only; convert with toMutableList()/toMutableMap(). |
| SPR-FLOW-BREAK | break is only valid inside a loop. |
| SPR-FLOW-CONTINUE | continue is only valid inside a loop. |
| SPR-FLOW-MISSING-RETURN | A non-Unit function must return on every path. |
| SPR-FLOW-THROWS | A recoverable error must be declared with throws or caught. |
| SPR-FLOW-UNREACHABLE | Statement follows a statement that always exits. |
| SPR-JVM-AMBIGUOUS | The Java overload is ambiguous for these argument types. |
| SPR-JVM-CLASS | The imported Java class could not be loaded. |
| SPR-JVM-COMPILE | The generated Java source did not compile; may be a compiler bug. |
| SPR-JVM-INTERNAL | Internal compiler or tooling failure. |
| SPR-JVM-MEMBER | No Java method/constructor/field matches this call. |
| SPR-LEX-CHAR | The input contains a character outside the Sprig lexer. |
| SPR-LEX-INDENT-FIRST | The first code line of a file must start at column 1. |
| SPR-LEX-INDENT-INCONSISTENT | A dedent must return to a previous indentation level. |
| SPR-LEX-STRING | A string literal is unterminated or contains an invalid escape. |
| SPR-LEX-TAB | Tabs are forbidden; Sprig indentation uses spaces only. |
| SPR-LEX-UNCLOSED | A '(' '[' or '{' was opened and never closed. |
| SPR-LEX-UNMATCHED | A closing delimiter has no matching opener. |
| SPR-MATCH-DUPLICATE | A match branch repeats a case. |
| SPR-MATCH-ENUM-BINDER | Payloadless enum cases cannot bind 'as name'. |
| SPR-MATCH-NONEXHAUSTIVE | Every enum/variant case must have a match branch; there is no default. |
| SPR-MATCH-SCRUTINEE | match requires a non-nullable enum or variant value. |
| SPR-MATCH-UNKNOWN-CASE | The case name does not exist on the matched type. |
| SPR-MATCH-WRONG-TYPE | A match branch belongs to a different enum/variant. |
| SPR-NAME-DUPLICATE | Two declarations share one name in one namespace. |
| SPR-NAME-DUPLICATE-MEMBER | A class/variant declares the same member twice. |
| SPR-NAME-FIELD-SHADOW | A parameter/local cannot shadow a current-class field. |
| SPR-NAME-IMPORT | An imported file or class cannot be resolved. |
| SPR-NAME-IMPORT-CYCLE | Sprig modules form an import cycle. |
| SPR-NAME-LET-ASSIGN | let bindings and let fields cannot be reassigned. |
| SPR-NAME-MODULE | Module import/alias problem. |
| SPR-NAME-NOT-A-TYPE | A value name was used where a type is required. |
| SPR-NAME-NOT-A-VALUE | A type or module name was used as a value. |
| SPR-NAME-UNRESOLVED | A name has no declaration in the current scope chain. |
| SPR-NUM-RANGE | A numeric literal is outside its target range or underflows to zero. |
| SPR-NUM-CONVERSION | An implicit numeric conversion risks precision or range loss. |
| SPR-NUM-DIVISION | Integer/BigInt `/` would truncate, or Decimal `/` lacks a rounding policy. |
| SPR-NUM-MIXED | A numeric operator cannot implicitly mix these numeric families. |
| SPR-RUNTIME-ERROR | Uncaught Sprig Error value at runtime. |
| SPR-RUNTIME-EXCEPTION | Uncaught JVM exception at runtime. |
| SPR-SYNTAX-ERROR | The token sequence does not match the Sprig grammar. |
| SPR-TYPE-ASSIGN | Assignment value does not match the target type. |
| SPR-TYPE-CAPTURE | A lambda captures a var local; copy it into a let binding first. |
| SPR-TYPE-CONDITION | Conditions must be Bool; Sprig has no truthiness. |
| SPR-TYPE-INFER | The type cannot be inferred without an annotation. |
| SPR-TYPE-MISMATCH | Expected and actual types are not compatible. |
| SPR-TYPE-NOT-CALLABLE | The callee is not callable (or a method name was used as a value). |
| SPR-TYPE-NULL | null is only assignable to an explicit nullable type T?. |
| SPR-TYPE-NULLABLE | A possibly-null value is used where non-null is required; check for null first. |
| SPR-TYPE-OPERAND | Operator or method is not defined for this operand type. |
| SPR-TYPE-RETURN | Returned value does not match the declared return type. |
| SPR-TYPE-UNIT | Unit is only a function/method result; it cannot be a field, parameter, collection element, or ordinary value. |
