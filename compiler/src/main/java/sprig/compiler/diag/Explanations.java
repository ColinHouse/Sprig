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
                        "A Java reference result was used without a null check.",
                        "A T? value, such as a map lookup, is joined into a String with +."));
                out.put("safeFixes", List.of("Check value != null before use, and handle the absent branch.",
                        "Return a default, throw Error, or narrow through an if/elif chain.",
                        "Give a fallback with or_else from @std/nulls.spr."));
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
                out.put("safeFixes", List.of("Use an explicit exact conversion (toIntExact, toFloatExact) or an explicitly lossy one after deciding the precision.",
                        "Float to Int: toIntExact() requires a whole value, toIntTrunc() drops the fraction, java.lang.Math.round(x) rounds to the nearest Int.",
                        "Int to Float: toFloat() fails on precision loss; toFloatLossy() rounds."));
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
            case Codes.GENERIC_ARGS_REQUIRED -> {
                out.put("whyMatters", "Type arguments come only from a call's arguments, so a call means the same wherever its result goes; when the arguments cannot say, the call writes them.");
                out.put("confusedWith", List.of("Java diamond inference from the assignment target", "TypeScript contextual typing"));
                out.put("commonCauses", List.of("No argument mentions a type parameter, as in a make() -> List[T] call.",
                        "The only argument for a parameter is null, [] or {}, which say nothing about it.",
                        "Two arguments give a parameter different types, such as String and Bool.",
                        "A variant case's payload does not mention every parameter, as in Result.Ok(value=1)."));
                out.put("safeFixes", List.of("Write every type argument in declaration order: lists.first[String]([]), Result[Int, String].Ok(value=1).",
                        "Pass an argument whose type is known, such as a typed local instead of null."));
                out.put("badExample", "let first = lists.first([])");
                out.put("goodExample", "let first = lists.first[String]([])");
                out.put("relatedCodes", List.of(Codes.GENERIC_ARITY, Codes.GENERIC_CONSTRAINT));
            }
            case Codes.GENERIC_ARITY -> {
                out.put("whyMatters", "Written type arguments name every parameter in declaration order, so a partial list is never guessed.");
                out.put("confusedWith", List.of("Java raw types", "TypeScript default type parameters"));
                out.put("commonCauses", List.of("A generic declaration was written with the wrong number of [Type] arguments, a bare generic name was used in a type position, or a non-generic type was given type arguments."));
                out.put("safeFixes", List.of("Write Box[Int], identity[String](...) or Entry[String, Int](...) with every parameter in declaration order, or leave the arguments of a call out entirely when its arguments determine them."));
                out.put("relatedCodes", List.of(Codes.GENERIC_ARGS_REQUIRED));
            }
            case Codes.GENERIC_CONSTRAINT -> {
                out.put("whyMatters", "Capabilities are closed and checked: a generic function states what it needs, and every call proves it.");
                out.put("commonCauses", List.of("A requires clause names a capability other than Equatable or Comparable, or is not a leading statement.",
                        "A function that requires X: Comparable was called with a type argument that has no ordering, such as Bool, a nullable type or a class."));
                out.put("safeFixes", List.of("Use requires X: Equatable for value equality and requires X: Comparable for ordering.",
                        "Call it with Int, Int32, Float, Float32, Decimal, BigInt or String, or pass an explicit comparison function for other types.",
                        "Inside another generic function, declare requires X: Comparable there too."));
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
                out.put("commonCauses", List.of("A typo, a missing import, or a declaration placed after first use.",
                        "A spelling from another language, such as readLine, input, len, str, True, None, self "
                                + "or the types int and str; the hint names the Sprig spelling.",
                        "A Java class such as Math or Scanner used without 'import java.lang.Math as Math'."));
                out.put("safeFixes", List.of("Check the spelling, add the import, or move the declaration before use.",
                        "Read standard input with @std/process.spr (read_lines, read_line, read_all)."));
                out.put("relatedCodes", List.of(Codes.NAME_IMPORT, Codes.NAME_NOT_A_VALUE));
            }
            case Codes.JVM_MEMBER, Codes.JVM_AMBIGUOUS -> {
                out.put("whyMatters", "Java calls are resolved against real signatures; unsupported boundaries are reported instead of guessed.");
                out.put("confusedWith", List.of("Java automatic boxing", "Kotlin SAM conversion"));
                out.put("commonCauses", List.of("The argument types or arity match no declared overload.",
                        "A nullable Sprig value was passed to a non-null Java parameter.",
                        "The signature needs an array, varargs or a raw generic boundary that Sprig cannot bind.",
                        "Two overloads are equally applicable, so the call is ambiguous."));
                out.put("safeFixes", List.of("Run sprig api <fully.qualified.Class> --json and read the reported candidates.",
                        "Choose a supported signature and convert explicitly (toIntExact(), toString(), ...).",
                        "Narrow or restructure nullable arguments before the call."));
                out.put("relatedCodes", List.of(Codes.TYPE_NULLABLE, Codes.JVM_CLASS));
            }
            case Codes.JVM_CLASSPATH -> {
                out.put("whyMatters", "Classpath state is explicit so builds are reproducible instead of environment-dependent.");
                out.put("commonCauses", List.of("A JAR or directory passed to --classpath does not exist or cannot be read."));
                out.put("safeFixes", List.of("Use an existing local JAR/directory path; paths resolve against the current working directory."));
            }
            case Codes.PROJECT_LOCK_MISSING, Codes.PROJECT_LOCK_STALE -> {
                out.put("whyMatters", "The lockfile makes dependency resolution deterministic and offline-safe.");
                out.put("commonCauses", List.of("The recorded compiler identity differs from the running compiler.",
                        "sprig.toml or a dependency manifest changed after the lock was written.",
                        "A build step deleted or replaced sprig.lock."));
                out.put("safeFixes", List.of("Run sprig resolve in the project root, then retry the command.",
                        "Commit the refreshed sprig.lock when dependencies legitimately change.",
                        "Do not hand-edit sprig.lock; resolve rewrites it deterministically."));
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
            case Codes.TYPE_CALLABLE_THROWS -> {
                out.put("whyMatters", "A function value's throws clause is part of its type, so every call through it is checked like a call to a throwing function.");
                out.put("confusedWith", List.of("Java lambdas that wrap checked exceptions", "Kotlin lambdas with no checked exceptions"));
                out.put("commonCauses", List.of("A lambda that calls a function declaring throws Error is passed where fn(...) -> R without throws is expected.",
                        "A function type names a Java exception in its throws clause; only Error crosses a function value.",
                        "A function value with throws Error is passed to a Java method; Java cannot see the clause."));
                out.put("safeFixes", List.of("Write the parameter or variable type as fn(T) -> R throws Error and mark the receiving function rethrows or throws Error.",
                        "Handle the error inside a named function and pass a lambda that calls it."));
                out.put("badExample", "func apply(f: fn(String) -> Int, text: String) -> Int:\n    return f(text)\nlet n = apply(fn(s: String) => parse(s), \"1\")");
                out.put("goodExample", "func apply(f: fn(String) -> Int throws Error, text: String) -> Int rethrows:\n    return f(text)\nlet n = apply(fn(s: String) => parse(s), \"1\")");
                out.put("relatedCodes", List.of(Codes.FLOW_THROWS, Codes.FLOW_RETHROWS));
            }
            case Codes.FLOW_RETHROWS -> {
                out.put("whyMatters", "A rethrows function throws exactly what its callable arguments throw, so a call with a non-throwing lambda needs no try.");
                out.put("confusedWith", List.of("Swift rethrows", "a function that declares throws Error"));
                out.put("commonCauses", List.of("rethrows is written but no parameter has a function type with throws Error."));
                out.put("safeFixes", List.of("Declare the callable parameter as fn(T) -> R throws Error.",
                        "If the function fails on its own, declare throws Error instead of rethrows."));
                out.put("badExample", "func twice(f: fn(Int) -> Int, x: Int) -> Int rethrows:\n    return f(f(x))");
                out.put("goodExample", "func twice(f: fn(Int) -> Int throws Error, x: Int) -> Int rethrows:\n    return f(f(x))");
                out.put("relatedCodes", List.of(Codes.FLOW_THROWS, Codes.TYPE_CALLABLE_THROWS));
            }
            case Codes.FLOW_CATCH_NEVER_THROWN -> {
                out.put("whyMatters", "A catch that can never run hides where failures really go: when a called function stops throwing the exception, its failures silently move to another catch.");
                out.put("confusedWith", List.of("Java's 'exception is never thrown in body of corresponding try statement'", "catch problem: Error, which is not checked this way"));
                out.put("commonCauses", List.of("A called function no longer throws the exception; @std/files, for example, fails with Error.",
                        "An inner try already catches the exception.",
                        "The exception class is unrelated to everything the block calls."));
                out.put("safeFixes", List.of("Remove the catch clause.",
                        "Catch Error instead if the block calls Sprig functions that fail with Error."));
                out.put("badExample", "import java.io.IOException as IOException\nimport \"@std/files.spr\" as files\ntry:\n    print(files.read_utf8(\"notes.txt\"))\ncatch problem: IOException:\n    print(problem.message)");
                out.put("goodExample", "import \"@std/files.spr\" as files\ntry:\n    print(files.read_utf8(\"notes.txt\"))\ncatch problem: Error:\n    print(problem.message)");
                out.put("relatedCodes", List.of(Codes.FLOW_THROWS, Codes.FLOW_THROWS_UNUSED));
            }
            case Codes.FLOW_THROWS_UNUSED -> {
                out.put("whyMatters", "A signature promises what a call can do. An exception that can never happen makes every caller handle it, and keeps their catch clauses alive after the code changed.");
                out.put("confusedWith", List.of("Java, which allows declaring exceptions that are never thrown", "throws Error, which is not checked this way"));
                out.put("commonCauses", List.of("The body called something that used to throw the exception, such as @std/files before it failed with Error.",
                        "The body already catches the exception itself."));
                out.put("safeFixes", List.of("Remove the exception from the throws clause, then remove the catch clauses the compiler reports in the callers."));
                out.put("badExample", "import java.io.IOException as IOException\nimport \"@std/files.spr\" as files\nfunc load(path: String) -> String throws IOException, Error:\n    return files.read_utf8(path)");
                out.put("goodExample", "import \"@std/files.spr\" as files\nfunc load(path: String) -> String throws Error:\n    return files.read_utf8(path)");
                out.put("relatedCodes", List.of(Codes.FLOW_THROWS, Codes.FLOW_CATCH_NEVER_THROWN));
            }
            case Codes.MATCH_RESULT -> {
                out.put("whyMatters", "An expression match has one result type; every branch must produce a value assignable to it without implicit conversion.");
                out.put("confusedWith", List.of("Java switch statement fallthrough", "Rust match arm coercion"));
                out.put("commonCauses", List.of("A branch returns a different type than the contextual or first non-null branch.",
                        "Branches mix Int/Int32 or Int/Float without an explicit conversion.",
                        "A branch yields null but the result type is not nullable."));
                out.put("safeFixes", List.of("Make every branch produce the declared result type.",
                        "Annotate the target (let value: T = match ...) so the contextual type is explicit.",
                        "Convert explicitly per branch with toString(), toIntExact() or another named conversion."));
                out.put("relatedCodes", List.of(Codes.MATCH_INFERENCE, Codes.TYPE_MISMATCH, Codes.TYPE_RETURN));
            }
            case Codes.MATCH_INFERENCE -> {
                out.put("whyMatters", "An expression match needs a result type; all-null or unresolved branches cannot supply one.");
                out.put("commonCauses", List.of("Every branch is null or an unannotated empty collection.",
                        "Branch results do not agree on one type."));
                out.put("safeFixes", List.of("Annotate the target: let name: String? = match ...",
                        "Give at least one branch a value of the intended type."));
                out.put("relatedCodes", List.of(Codes.MATCH_RESULT, Codes.TYPE_INFER));
            }
            case Codes.MODULE_EXPORT -> {
                out.put("whyMatters", "A facade export forwards exactly one public declaration; identity is preserved, not copied.");
                out.put("commonCauses", List.of("The exported name is not imported or declared in this module.",
                        "The name refers to a non-public or unexported dependency declaration.",
                        "The export name collides with another declaration."));
                out.put("safeFixes", List.of("Run sprig api <module.spr> --json to list declared and exported names, then export the exact public name.",
                        "Import the source module first with import \"...\" as alias."));
                out.put("relatedCodes", List.of(Codes.MODULE_EXPORT_ORDER, Codes.PROJECT_NOT_EXPORTED, Codes.NAME_UNRESOLVED));
            }
            case Codes.MODULE_EXPORT_ORDER -> {
                out.put("whyMatters", "Module structure is fixed: imports, then exports, then declarations and statements.");
                out.put("confusedWith", List.of("Python imports anywhere", "JavaScript hoisted exports"));
                out.put("commonCauses", List.of("An export appears after a declaration or statement.",
                        "An export precedes its import."));
                out.put("safeFixes", List.of("Move every export directly after the import block.",
                        "Declare local helpers after the export block."));
                out.put("relatedCodes", List.of(Codes.MODULE_EXPORT));
            }
            case Codes.TYPE_INFER -> {
                out.put("whyMatters", "Local inference only reads an initializer; ambiguous initializers must be annotated.");
                out.put("commonCauses", List.of("let x = null has no inferable type.",
                        "An empty list or map literal has no expected type to take its element types from.",
                        "Top-level initializers reference each other in a cycle.",
                        "The initializer is a Unit call."));
                out.put("safeFixes", List.of("Write the type: let x: String? = null, let names: List[String] = [].",
                        "Break a cycle by annotating one binding or reordering initialization."));
                out.put("relatedCodes", List.of(Codes.TYPE_UNIT, Codes.GENERIC_ARGS_REQUIRED, Codes.TYPE_MISMATCH));
            }
            case Codes.TYPE_RETURN -> {
                out.put("whyMatters", "The declared result type is the function's contract at every call site.");
                out.put("commonCauses", List.of("The returned value has a different type.",
                        "A generic argument differs from the declared result type.",
                        "A nullable value is returned from a non-null function."));
                out.put("safeFixes", List.of("Return the declared type, converting explicitly if needed.",
                        "Change the declared result type if the contract should be different.",
                        "Handle null before returning when the result is non-null."));
                out.put("relatedCodes", List.of(Codes.TYPE_MISMATCH, Codes.TYPE_NULLABLE, Codes.FLOW_MISSING_RETURN));
            }
            case Codes.TYPE_ASSIGN -> {
                out.put("whyMatters", "Assignment never converts implicitly; every visible type change is deliberate.");
                out.put("commonCauses", List.of("The value has a different type than the target.",
                        "A nullable value is assigned to a non-null target.",
                        "An immutable binding is reassigned (also reports SPR-NAME-LET-ASSIGN)."));
                out.put("safeFixes", List.of("Convert explicitly or change the target's declared type.",
                        "Check for null, or declare the target as T?.",
                        "Use var when the binding should be reassignable."));
                out.put("relatedCodes", List.of(Codes.TYPE_MISMATCH, Codes.TYPE_NULL, Codes.NAME_LET_ASSIGN));
            }
            case Codes.TYPE_OPERAND -> {
                out.put("whyMatters", "Operators and methods exist only for types that define them; there is no implicit coercion.");
                out.put("commonCauses", List.of("Arithmetic or ordering on a type that does not support it (String, Bool, nullable).",
                        "A method name that does not exist on the value's type.",
                        "Mixed numeric kinds that need an explicit conversion (see SPR-NUM-MIXED)."));
                out.put("safeFixes", List.of("Use an operation the type defines; check sprig help types --json.",
                        "Convert explicitly (toInt(), toFloatExact(), ...) before the operation.",
                        "Narrow nullable values before calling methods on them."));
                out.put("relatedCodes", List.of(Codes.TYPE_MISMATCH, Codes.NUM_MIXED, Codes.TYPE_NULLABLE));
            }
            case Codes.TYPE_NOT_CALLABLE -> {
                out.put("whyMatters", "Call syntax requires a function value or callable type; method names are not first-class values.");
                out.put("commonCauses", List.of("Calling a non-function value, or a field/lambda without the call.",
                        "Using a method name as a value (named function references are not implemented).",
                        "The callee resolved to an error, which then reports as not callable."));
                out.put("safeFixes", List.of("Call the target with matching arguments.",
                        "Wrap a named function in a lambda: fn(x: Int) => named(x)."));
                out.put("relatedCodes", List.of(Codes.TYPE_FUNCTION_ARITY, Codes.NAME_NOT_A_VALUE));
            }
            case Codes.TYPE_UNIT -> {
                out.put("whyMatters", "Unit marks a result-less call; it is not a value you can store or pass.");
                out.put("commonCauses", List.of("Binding a Unit result: let x = print(\"hi\").",
                        "Using Unit as a field, parameter, element, or value result type.",
                        "Calling a method on a Unit result, such as save().toString().",
                        "Putting a Unit result in a list or map, comparing it with == or !=, or passing it to a Java method.",
                        "Writing return save() in a function that returns Unit."));
                out.put("safeFixes", List.of("Call the function for its effect without binding the result.",
                        "In a Unit function, call it as a statement and then write a bare return.",
                        "Return a real value from functions whose result is used."));
                out.put("relatedCodes", List.of(Codes.TYPE_INFER, Codes.TYPE_MISMATCH));
            }
            case Codes.CALL_ARITY -> {
                out.put("whyMatters", "Calls are resolved against explicit parameter lists; there are no default values.");
                out.put("commonCauses", List.of("Wrong number of arguments.",
                        "Positional arguments for a constructor (reports SPR-CALL-NAMED-REQUIRED).",
                        "Named arguments for a function (reports SPR-CALL-POSITIONAL-REQUIRED)."));
                out.put("safeFixes", List.of("Pass exactly the declared parameter count.",
                        "Check the declaration or sprig api <module> --json for the signature."));
                out.put("relatedCodes", List.of(Codes.CALL_NAMED_REQUIRED, Codes.CALL_POSITIONAL_REQUIRED, Codes.TYPE_FUNCTION_ARITY));
            }
            case Codes.CALL_MISSING_FIELD, Codes.CALL_UNKNOWN_FIELD, Codes.CALL_DUPLICATE_FIELD -> {
                out.put("whyMatters", "Named constructors make every field explicit; a typo or omission cannot be silently ignored.");
                out.put("commonCauses", List.of("A required field was omitted and has no default.",
                        "A field name is misspelled or belongs to another class/variant.",
                        "The same field is passed twice."));
                out.put("safeFixes", List.of("Read the class/variant declaration or sprig api <module.spr> --json for the field list.",
                        "Pass each required field once; add a default in the declaration if omission is intended."));
                out.put("relatedCodes", List.of(Codes.CALL_ARITY, Codes.TYPE_MISMATCH));
            }
            case Codes.PROJECT_MANIFEST -> {
                out.put("whyMatters", "sprig.toml is the project contract; the compiler refuses ambiguous manifests.");
                out.put("commonCauses", List.of("Missing [project] section or a required key.",
                        "Unknown or duplicate keys, or a value of the wrong kind.",
                        "An entry/source path that does not exist."));
                out.put("safeFixes", List.of("Compare with the manifest produced by sprig init.",
                        "Run sprig project --json to inspect the resolved model.",
                        "See sprig help projects --json for the accepted keys."));
                out.put("relatedCodes", List.of(Codes.PROJECT_ENTRY, Codes.PROJECT_LOCK_MISSING));
            }
            case Codes.PROJECT_ENTRY -> {
                out.put("whyMatters", "An entry selects the file to run; ambiguous projects must select explicitly.");
                out.put("commonCauses", List.of("The entry file does not exist.",
                        "sprig run was used in a multi-bin project without --bin.",
                        "The requested --bin name is not declared."));
                out.put("safeFixes", List.of("Run sprig project --json to list resolved bins and entries.",
                        "Create the entry file, or pass run --bin <name> or an explicit .spr file."));
                out.put("relatedCodes", List.of(Codes.PROJECT_MANIFEST));
            }
            case Codes.PROJECT_LOCK_SCHEMA -> {
                out.put("whyMatters", "Lock schemas are versioned; an old lock cannot be silently trusted by a new compiler.");
                out.put("commonCauses", List.of("The lock was written by an older compiler or hand-edited."));
                out.put("safeFixes", List.of("Run sprig resolve to rewrite the lock with the current schema.",
                        "Do not hand-edit sprig.lock."));
                out.put("relatedCodes", List.of(Codes.PROJECT_LOCK_STALE, Codes.PROJECT_LOCK_MISSING));
            }
            case Codes.PROJECT_UNSUPPORTED -> {
                out.put("whyMatters", "Only project features the current compiler implements are accepted.");
                out.put("commonCauses", List.of("A dependency declares a language version this compiler does not support.",
                        "The manifest requests a project feature outside the implemented set."));
                out.put("safeFixes", List.of("Align the dependency's language/version with the compiler.",
                        "See sprig help projects --json and sprig help dependencies --json."));
                out.put("relatedCodes", List.of(Codes.PROJECT_MANIFEST));
            }
            case Codes.DEP_OFFLINE -> {
                out.put("whyMatters", "Offline mode never touches the network, so a cold cache fails instead of silently fetching.");
                out.put("commonCauses", List.of("A locked artifact or Git revision is missing from the local cache.",
                        "The project was never resolved on this machine."));
                out.put("safeFixes", List.of("Run sprig resolve once with network access, then reuse --offline.",
                        "Check that the expected cache directory exists under the user home."));
                out.put("relatedCodes", List.of(Codes.DEP_CHECKSUM, Codes.PROJECT_LOCK_STALE));
            }
            case Codes.DEP_REGISTRY -> {
                out.put("whyMatters", "A registry is only an index of where packages live; add still writes an ordinary Git dependency and the lock pins the commit.");
                out.put("commonCauses", List.of("The package name is not listed in any configured registry, or the requested version is not one of its releases.",
                        "The [[registry]] path does not exist, or the index has no packages/NAME.toml files.",
                        "Two registries list the same name and no --registry was given."));
                out.put("safeFixes", List.of("Run sprig search to see the listed packages and versions.",
                        "Pass --registry NAME when several registries are declared, or --version to pick a release.",
                        "Add the package with --git URL --tag TAG directly when it is not in a registry."));
                out.put("relatedCodes", List.of(Codes.DEP_GIT, Codes.DEP_OFFLINE));
            }
            case Codes.DEP_CHECKSUM -> {
                out.put("whyMatters", "Locked artifacts are content-addressed; a mismatch means the cache entry is not the locked bytes.");
                out.put("commonCauses", List.of("A corrupted or truncated cache entry.",
                        "An upstream branch or tag moved and the lock references different bytes."));
                out.put("safeFixes", List.of("Discard the corrupted cache entry and run sprig resolve again with network access.",
                        "Do not edit sprig.lock or the cache by hand."));
                out.put("relatedCodes", List.of(Codes.DEP_OFFLINE, Codes.DEP_GIT));
            }
            case Codes.DEP_MAVEN -> {
                out.put("whyMatters", "Maven coordinates resolve to exact locked models and JARs; unsupported packaging is refused.");
                out.put("commonCauses", List.of("Wrong group/artifact/version coordinates.",
                        "No network access during the first resolve for that artifact.",
                        "The artifact is not a JAR or needs unsupported Maven behavior."));
                out.put("safeFixes", List.of("Verify exact release coordinates in [[jvm]].",
                        "Run sprig resolve with network access.",
                        "See docs/projects/dependencies.md; there is no Maven CLI/plugin path."));
                out.put("relatedCodes", List.of(Codes.DEP_NOT_FOUND, Codes.DEP_OFFLINE));
            }
            case Codes.DEP_NOT_FOUND -> {
                out.put("whyMatters", "Dependency paths and aliases are resolved explicitly; nothing is found by accident.");
                out.put("commonCauses", List.of("A [[dependency]] path is wrong or missing.",
                        "An @alias/module.spr import names a module the dependency does not export.",
                        "Only direct dependency aliases are visible; transitive aliases are package-local."));
                out.put("safeFixes", List.of("Run sprig project --json and sprig deps --json to inspect the resolved graph.",
                        "Check the manifest path/name and the dependency's exports list."));
                out.put("relatedCodes", List.of(Codes.PROJECT_NOT_EXPORTED, Codes.DEP_CYCLE));
            }
            case Codes.DEP_CYCLE -> {
                out.put("whyMatters", "Acyclic dependencies keep resolution and initialization order defined.");
                out.put("commonCauses", List.of("Two project packages depend on each other directly or transitively."));
                out.put("safeFixes", List.of("Extract the shared code into a third package both depend on.",
                        "Break the edge by inlining or moving the declaration."));
                out.put("relatedCodes", List.of(Codes.NAME_IMPORT_CYCLE, Codes.DEP_NOT_FOUND));
            }
            case Codes.DEP_GIT -> {
                out.put("whyMatters", "Git dependencies are locked to a revision; resolution never moves branches by itself.");
                out.put("commonCauses", List.of("git is unavailable or the remote/ref cannot be reached.",
                        "The requested branch/tag/revision does not exist.",
                        "Another Sprig process still holds the repository cache lock."));
                out.put("safeFixes", List.of("Verify the remote URL and branch/ref in sprig.toml.",
                        "Ensure git is installed and the network is reachable for the first resolve.",
                        "If the diagnostic reports lock contention, retry after the other resolve finishes.",
                        "Run sprig resolve to refresh the locked revision deliberately."));
                out.put("relatedCodes", List.of(Codes.DEP_CHECKSUM, Codes.DEP_OFFLINE));
            }
            case Codes.JVM_CLASS -> {
                out.put("whyMatters", "Java imports resolve against real classpath metadata without initializing the class.");
                out.put("commonCauses", List.of("A class name is misspelled or not on the compile classpath.",
                        "The class lives in the unnamed package, which generated code cannot reference.",
                        "A --classpath entry or locked JAR is missing."));
                out.put("safeFixes", List.of("Run sprig api <fully.qualified.Class> --classpath ... --json to confirm resolution.",
                        "Add the missing --classpath entry or resolve the manifest dependency.",
                        "Move unnamed-package classes into a named package."));
                out.put("relatedCodes", List.of(Codes.JVM_CLASSPATH, Codes.JVM_MEMBER));
            }
            case Codes.SYNTAX_ERROR -> {
                out.put("whyMatters", "The grammar is small and explicit; fixing the first syntax error removes most later ones.");
                out.put("commonCauses", List.of("A block header without a trailing ':'.",
                        "Mismatched indentation or an unexpected token.",
                        "A construct from another language: 'else if' (Sprig writes elif), braces around a block, "
                                + "a declaration without an initial value, ++, List<Int> (Sprig writes List[Int]) "
                                + "or a function header without '-> Type'. The message and hint name the Sprig spelling.",
                        "An if expression without its else branch, with a value on a header's line, inside "
                                + "parentheses or used as an operand, or Python's 'a if c else b' and C's 'c ? a : b', "
                                + "which Sprig writes as an if expression.",
                        "An 'if' guard on a match case or an 'if' filter on a for loop: Sprig tests the condition "
                                + "inside the body."));
                out.put("safeFixes", List.of("Fix the first reported error, then re-check; cascades are common.",
                        "Query sprig help language --json and sprig help <topic> --json for accepted syntax."));
                out.put("relatedCodes", List.of(Codes.LEX_INDENT_INCONSISTENT, Codes.LEX_INDENT_FIRST, Codes.LEX_CHAR));
            }
            case Codes.LEX_INDENT_INCONSISTENT -> {
                out.put("whyMatters", "Indentation is syntax; a dedent must match an earlier block level exactly.");
                out.put("commonCauses", List.of("Spaces and tabs were mixed, or a level uses a different width.",
                        "A dedent lands between two earlier indentation levels."));
                out.put("safeFixes", List.of("Use spaces only, and align dedents with a previous block level.",
                        "Format with sprig fmt after the file parses."));
                out.put("relatedCodes", List.of(Codes.LEX_TAB, Codes.LEX_INDENT_FIRST, Codes.SYNTAX_ERROR));
            }
            case Codes.NAME_DUPLICATE, Codes.NAME_DUPLICATE_MEMBER -> {
                out.put("whyMatters", "One namespace has one meaning per name; duplicates are always explicit errors.");
                out.put("commonCauses", List.of("Two declarations share a name in the same scope.",
                        "A class or variant declares the same member twice."));
                out.put("safeFixes", List.of("Rename one declaration or member.",
                        "Move the competing declaration into another module if both are needed."));
                out.put("relatedCodes", List.of(Codes.NAME_FIELD_SHADOW, Codes.NAME_UNRESOLVED));
            }
            case Codes.NAME_FORWARD_REFERENCE -> {
                out.put("whyMatters", "Top-level statements run once, in source order; reading a binding before its "
                        + "declaration would observe 0, false or null instead of its value.");
                out.put("confusedWith", List.of("Function declarations, which are visible before their position",
                        "Java static fields, whose forward references javac also rejects"));
                out.put("commonCauses", List.of("A top-level statement uses a binding declared further down.",
                        "An initializer refers to its own binding or to a later one."));
                out.put("safeFixes", List.of("Move the declaration above its first top-level use.",
                        "Wrap the code in a function and call it after the declaration."));
                out.put("relatedCodes", List.of(Codes.NAME_UNRESOLVED, Codes.RUNTIME_EXCEPTION));
                out.put("badExample", "print(limit)\nlet limit: Int = 21");
                out.put("goodExample", "let limit: Int = 21\nprint(limit)");
            }
            case Codes.NAME_LET_ASSIGN -> {
                out.put("whyMatters", "let bindings and let fields are immutable; mutation is visible in the declaration.");
                out.put("confusedWith", List.of("JavaScript let reassignment", "Python variables"));
                out.put("commonCauses", List.of("Reassigning a let binding or let field."));
                out.put("safeFixes", List.of("Declare it with var if reassignment is intended.",
                        "Compute a new let value instead of mutating."));
                out.put("relatedCodes", List.of(Codes.TYPE_ASSIGN));
            }
            case Codes.NAME_IMPORT -> {
                out.put("whyMatters", "Every import resolves to a real file, bundled module, or class; nothing is implicit.");
                out.put("commonCauses", List.of("A relative module path is wrong or missing.",
                        "An @alias module is not exported by the dependency.",
                        "A Java class import cannot be loaded (name or classpath)."));
                out.put("safeFixes", List.of("Check the path relative to the importing file, or @std/@alias spelling.",
                        "Run sprig project --json / sprig deps --json for dependency aliases and exports.",
                        "For Java, confirm the class name and classpath."));
                out.put("relatedCodes", List.of(Codes.PROJECT_NOT_EXPORTED, Codes.JVM_CLASS, Codes.NAME_IMPORT_CYCLE));
            }
            case Codes.MATCH_SCRUTINEE -> {
                out.put("whyMatters", "match can only dispatch on a non-null enum or variant value.");
                out.put("commonCauses", List.of("The matched expression is nullable or of another type."));
                out.put("safeFixes", List.of("Narrow the nullable value first, or convert it to an enum/variant.",
                        "Change the function result type contract upstream."));
                out.put("relatedCodes", List.of(Codes.TYPE_NULLABLE, Codes.MATCH_WRONG_TYPE));
            }
            case Codes.MATCH_DUPLICATE, Codes.MATCH_WRONG_TYPE, Codes.MATCH_UNKNOWN_CASE, Codes.MATCH_ENUM_BINDER -> {
                out.put("whyMatters", "Match branches are a closed, checked enumeration of cases.");
                out.put("commonCauses", List.of("A case is repeated, belongs to another enum/variant, or is misspelled.",
                        "A payloadless enum case binds 'as name'."));
                out.put("safeFixes", List.of("Compare against the declared enum/variant cases.",
                        "Remove duplicate/foreign branches and binders on payloadless cases."));
                out.put("relatedCodes", List.of(Codes.MATCH_NONEXHAUSTIVE, Codes.MATCH_RESULT));
            }
            case Codes.FLOW_MISSING_RETURN -> {
                out.put("whyMatters", "A non-Unit function must return on every path; control cannot fall off the end.");
                out.put("commonCauses", List.of("A branch or loop body is missing a return.",
                        "try/finally or match paths do not all complete."));
                out.put("safeFixes", List.of("Return on every path, or make the function's result type Unit."));
                out.put("relatedCodes", List.of(Codes.FLOW_UNREACHABLE, Codes.TYPE_RETURN));
            }
            case Codes.FLOW_UNREACHABLE -> {
                out.put("whyMatters", "Statements after a guaranteed return/throw/break/continue can never run.");
                out.put("commonCauses", List.of("Code follows a return, throw, break or continue.",
                        "A finally block already overrides the surrounding exit."));
                out.put("safeFixes", List.of("Remove the unreachable statement, or restructure the control flow."));
                out.put("relatedCodes", List.of(Codes.FLOW_MISSING_RETURN, Codes.FLOW_BREAK, Codes.FLOW_CONTINUE));
            }
            case Codes.API_TARGET -> {
                out.put("whyMatters", "sprig api reports resolved compiler metadata; the target must be a real module or project.");
                out.put("commonCauses", List.of("The .spr path or @package/module.spr target does not exist.",
                        "The project has no current lock (resolve first)."));
                out.put("safeFixes", List.of("Check the path and run sprig resolve for project targets.",
                        "Use sprig api . or sprig api <module.spr> --json and read the diagnostic."));
                out.put("relatedCodes", List.of(Codes.API_MEMBER, Codes.PROJECT_LOCK_STALE));
            }
            case Codes.CONFORM_SOURCE, Codes.CONFORM_TARGET -> {
                out.put("whyMatters", "conform declares a foreign JVM contract for an existing local class; it adds no methods and performs no adaptation.");
                out.put("commonCauses", List.of("The left name is not a class declared in this module (imported, dependency or value name).",
                        "The class is generic.",
                        "The target alias is not an imported public Java interface, or the interface is generic, sealed or an annotation.",
                        "A Java class is named without parentheses, an interface with them, the class is final, generic or sealed, or the named fields select no public or protected constructor."));
                out.put("safeFixes", List.of("Declare conform in the same file as a non-generic class.",
                        "Import the target: import java.lang.Runnable as Runnable.",
                        "To extend a Java class, name the fields its constructor takes: conform C to JavaClass(field1, field2).",
                        "For different signatures, write a separate class that composes the original."));
                out.put("relatedCodes", List.of(Codes.CONFORM_MEMBER, Codes.CONFORM_OVERLOAD, Codes.JVM_CLASS));
            }
            case Codes.CONFORM_MEMBER -> {
                out.put("whyMatters", "A witness or override must match the Java signature exactly: name, arity, JVM parameter shapes and return shape; a method named like a Java method with another shape would silently become a new overload.");
                out.put("commonCauses", List.of("The class is missing a required method.",
                        "A parameter's JVM shape differs (Int vs String, Int vs Int32, nullable primitive).",
                        "The return shape differs (Unit vs a value, primitive vs boxed).",
                        "The method would override a final Java method, hide a static one, or has the name of a Java method but none of its shapes."));
                out.put("safeFixes", List.of("Add or rename the method to the interface's method name.",
                        "Run sprig api <Java.Class> --json to inspect the exact interface signature.",
                        "Use composition and a separate adapter class when the signature cannot match."));
                out.put("relatedCodes", List.of(Codes.CONFORM_SOURCE, Codes.CONFORM_OVERLOAD, Codes.JVM_MEMBER));
            }
            case Codes.CONFORM_OVERLOAD -> {
                out.put("whyMatters", "Sprig classes have one method per name, so an interface with overloaded abstract methods cannot be represented.");
                out.put("commonCauses", List.of("The Java interface declares abstract methods that share a name with different parameters."));
                out.put("safeFixes", List.of("Use an interface whose abstract methods have unique names.",
                        "Keep the overloaded Java type behind ordinary Java interop instead of conforming to it."));
                out.put("relatedCodes", List.of(Codes.CONFORM_MEMBER, Codes.JVM_MEMBER));
            }
            case Codes.CONFORM_EFFECTS -> {
                out.put("whyMatters", "A Java interface method only permits the checked exceptions it declares; the witness cannot add more.");
                out.put("commonCauses", List.of("The witness declares a checked exception while the interface method declares none.",
                        "The declared checked exception is not a subtype of any exception the interface permits."));
                out.put("safeFixes", List.of("Catch the exception inside the method and handle it.",
                        "Remove the throws clause and surface failure another way.",
                        "Use an interface method that declares a compatible exception."));
                out.put("relatedCodes", List.of(Codes.FLOW_THROWS, Codes.CONFORM_MEMBER));
            }
            case Codes.CONFORM_PARENT -> {
                out.put("whyMatters", "The parent view declared by 'conform C to J(...) as NAME' stands for the inherited Java implementation of the current object; it exists only to call methods, like Java's super, and is not a value.");
                out.put("commonCauses", List.of("NAME is used as a value, passed as an argument, or one of its fields is read.",
                        "The alias is declared on an interface conform, which has no inherited implementation.",
                        "The called method is abstract in the Java class, so nothing inherited can run.",
                        "The alias has the name of a field or method of the class."));
                out.put("safeFixes", List.of("Write NAME.method(...) inside a method of the class.",
                        "Implement the behaviour in the class's own method when the Java method is abstract.",
                        "Choose an alias that no member of the class uses."));
                out.put("relatedCodes", List.of(Codes.CONFORM_TARGET, Codes.CONFORM_MEMBER, Codes.JVM_MEMBER));
            }
            case Codes.NUM_RANGE -> {
                out.put("whyMatters", "Numeric literals are checked against the target type's exact range and precision.");
                out.put("commonCauses", List.of("A literal is outside Int/Int32 range.",
                        "A literal is too small to represent without underflowing to zero.",
                        "A BigInt or Decimal literal is written without the matching construction."));
                out.put("safeFixes", List.of("Use a literal within the target range.",
                        "Use BigInt/Decimal construction for large or exact values.",
                        "Check sprig help numerics --json."));
                out.put("relatedCodes", List.of(Codes.NUM_CONVERSION, Codes.TYPE_MISMATCH));
            }
            case Codes.LEX_STRING -> {
                out.put("whyMatters", "Strings are single-line, double-quoted literals with a small escape set.");
                out.put("commonCauses", List.of("A closing quote is missing.",
                        "A raw newline appears inside the literal.",
                        "An unsupported escape (such as \\u or a backslash before a normal character)."));
                out.put("safeFixes", List.of("Close the literal on the same line.",
                        "Use the supported escapes \\\" \\\\ \\n \\r \\t, or String.fromCode for other code points.",
                        "Concatenate with + across lines if a long text is needed."));
                out.put("relatedCodes", List.of(Codes.LEX_UNCLOSED, Codes.LEX_CHAR));
            }
            case Codes.LEX_UNCLOSED, Codes.LEX_UNMATCHED -> {
                out.put("whyMatters", "Delimiters must balance; the parser trusts the token stream after lexing.");
                out.put("commonCauses", List.of("A '(' '[' or '{' was never closed.",
                        "A closing delimiter has no matching opener.",
                        "A multi-line expression was split incorrectly."));
                out.put("safeFixes", List.of("Balance the delimiters around the reported span.",
                        "Re-check the first reported delimiter; later errors usually cascade from it."));
                out.put("relatedCodes", List.of(Codes.SYNTAX_ERROR));
            }
            case Codes.LEX_CHAR, Codes.LEX_INDENT_FIRST -> {
                out.put("whyMatters", "The lexer is strict and indentation is syntax; invisible input still fails loudly.");
                out.put("commonCauses", List.of("A character outside the Sprig lexer (smart quotes, $, emoji outside strings).",
                        "The first code line of the file is indented."));
                out.put("safeFixes", List.of("Replace or remove the reported character; keep escapes inside strings.",
                        "Start the first code line at column 1."));
                out.put("relatedCodes", List.of(Codes.LEX_TAB, Codes.SYNTAX_ERROR));
            }
            case Codes.FLOW_BREAK, Codes.FLOW_CONTINUE -> {
                out.put("whyMatters", "Loop control is only meaningful inside a loop body.");
                out.put("commonCauses", List.of("break or continue appears outside any while/for body.",
                        "The statement was moved out of a loop during editing."));
                out.put("safeFixes", List.of("Move the statement inside the intended loop.",
                        "Replace it with an early return or a condition if a loop is not intended."));
                out.put("relatedCodes", List.of(Codes.FLOW_UNREACHABLE));
            }
            case Codes.NAME_NOT_A_VALUE -> {
                out.put("whyMatters", "Types and modules name declarations, not runtime values.");
                out.put("commonCauses", List.of("A class, variant, enum or module name was used where a value is required.",
                        "A constructor call is missing."));
                out.put("safeFixes", List.of("Construct a value: Person(name=\"Ada\"), Expr.Literal(value=1).",
                        "Reference a declared value or call a function instead."));
                out.put("relatedCodes", List.of(Codes.TYPE_NOT_CALLABLE, Codes.NAME_NOT_A_TYPE));
            }
            case Codes.GENERIC_NULLABLE -> {
                out.put("whyMatters", "A type parameter declared with '?' must be instantiated with a non-nullable type.");
                out.put("commonCauses", List.of("A nullable type argument was supplied for a '?'-declared parameter."));
                out.put("safeFixes", List.of("Pass the non-nullable form: Box[String] instead of Box[String?].",
                        "Declare the parameter without '?' if nullable arguments should be allowed."));
                out.put("relatedCodes", List.of(Codes.TYPE_NULL, Codes.GENERIC_ARITY));
            }
            case Codes.RUNTIME_ERROR, Codes.RUNTIME_EXCEPTION -> {
                out.put("whyMatters", "An uncaught error aborts the program; the diagnostic is runtime evidence, not a compile error.");
                out.put("commonCauses", List.of("A thrown Sprig Error reached the top level without a catch.",
                        "A Java exception crossed an interop boundary unhandled.",
                        "A runtime assumption failed (null from Java, numeric edge, file/process failure)."));
                out.put("safeFixes", List.of("Catch or declare the error at the appropriate function boundary.",
                        "Narrow Java reference results before use; the diagnostic origin names the failing category.",
                        "Re-run with `sprig run --stacktrace` when the raw JVM stack is needed for debugging."));
                out.put("relatedCodes", List.of(Codes.FLOW_THROWS, Codes.TYPE_NULLABLE));
            }
            case Codes.WRAP_CHECK -> {
                out.put("whyMatters", "The wrapper generator must emit ordinary Sprig source that checks under the same classpath; a failed check means no file was written.");
                out.put("commonCauses", List.of("The Java shape slipped outside the shared interop profile during generation.",
                        "The generator produced a conversion the checker rejects (a generator bug worth reporting)."));
                out.put("safeFixes", List.of("Run sprig api on the class and check interopReasonCodes for the member.",
                        "Generate with --member to isolate the member, and report the exact javaSignature with the diagnostic."));
                out.put("relatedCodes", List.of(Codes.API_MEMBER, Codes.JVM_MEMBER));
            }
            case Codes.CLI_OPTION -> {
                out.put("whyMatters", "The CLI contract is exact: missing arguments and unknown options fail instead of guessing.");
                out.put("commonCauses", List.of("A required argument or file path is missing.",
                        "An option is misspelled or not supported by that command."));
                out.put("safeFixes", List.of("Run the command with the documented arguments; sprig help agents --json lists the query commands.",
                        "Use --json consistently when scripting."));
                out.put("relatedCodes", List.of(Codes.API_TARGET));
            }
            default -> { }
        }
        return out;
    }

    private static String topic(String code) {
        if (code.contains("GENERIC")) return "generics";
        if (code.contains("NULL")) return "nullability";
        if (code.contains("FUNCTION") || code.contains("CALLABLE")) return "functions";
        if (code.startsWith("SPR-JVM-")) return "jvm";
        if (code.startsWith("SPR-NUM-")) return "numerics";
        if (code.startsWith("SPR-MATCH-")) return "match";
        if (code.startsWith("SPR-COLLECTION-")) return "collections";
        if (code.startsWith("SPR-DEP-")) return "dependencies";
        if (code.startsWith("SPR-PROJECT-")) return "projects";
        if (code.startsWith("SPR-MODULE-") || code.equals(Codes.NAME_IMPORT)
                || code.equals(Codes.NAME_IMPORT_CYCLE) || code.equals(Codes.NAME_MODULE)) return "modules";
        if (code.startsWith("SPR-API-") || code.startsWith("SPR-CLI-") || code.startsWith("SPR-WRAP-")) return "agents";
        if (code.startsWith("SPR-LEX-") || code.startsWith("SPR-SYNTAX-")) return "language";
        if (code.startsWith("SPR-FLOW-") || code.startsWith("SPR-RUNTIME-")) return "errors";
        if (code.startsWith("SPR-CALL-")) return "classes";
        if (code.startsWith("SPR-TYPE-")) return "types";
        if (code.startsWith("SPR-NAME-")) return "language";
        return "language";
    }
}
