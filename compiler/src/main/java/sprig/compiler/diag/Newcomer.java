package sprig.compiler.diag;

import java.util.List;
import java.util.Map;

/**
 * Hints for spellings that newcomers bring from Python, Java, JavaScript and C.
 * Each hint names the Sprig spelling at the place the mistake is reported, so a
 * reader (or a model) that has never seen Sprig can repair the line without
 * leaving the diagnostic.
 */
public final class Newcomer {
    private Newcomer() {
    }

    static final String STDIN = "Read standard input with the bundled process module: "
            + "import \"@std/process.spr\" as process, then process.read_lines() (every line), "
            + "process.read_line() (one line, null at the end) or process.read_all().";
    private static final String PRINT = "Write output with print(value), which ends the line; "
            + "join text with +, for example print(\"total \" + total).";
    private static final String TO_INT = "Convert text to a whole number with text.toIntOrNull(), "
            + "which is null when the text is not one, or text.toInt(), which fails instead.";
    private static final String TO_FLOAT = "Convert text with text.toFloat(), and an Int with n.toFloatExact().";
    private static final String TO_STRING = "Turn a value into text with value.toString(), "
            + "or join it to a String with +.";
    private static final String LENGTH = "A String has value.length(); a list or map has items.size().";
    private static final String BOOLEAN = "Sprig writes true and false in lowercase.";
    private static final String NULL = "Sprig writes the absent value as null, and its type as T?.";
    /** How a catch clause is written; used where a catch or except does not parse. */
    public static final String CATCH = "A catch clause names the error and its type: 'catch problem: Error:' "
            + "(or an imported Java exception type), with the handling code indented below it, "
            + "and problem.message holds the text.";
    /** How text is built from values; for f"...", $"...", `...${}` and "...".format(). */
    public static final String INTERPOLATION = "Build text with +, which joins a String to any value: "
            + "write \"hello \" + name + \"!\" for f\"hello {name}!\"; put an expression in a let first "
            + "(let total = price * count) and join the name.";
    private static final String SELF = "A method uses the object's fields and methods by name; "
            + "there is no self or this.";
    /** How a value that depends on a condition is written; for Python's a if c else b and C's c ? a : b. */
    public static final String IF_EXPRESSION = "Write an if expression, with each value on its own indented line: "
            + "'let size = if count > 9:', then '    \"big\"' on the next line, then 'else:' and '    \"small\"'. "
            + "Line breaks are ignored inside parentheses, so bind the if expression to a let first and use the name.";

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("readLine", STDIN), Map.entry("readline", STDIN), Map.entry("readln", STDIN),
            Map.entry("input", STDIN), Map.entry("raw_input", STDIN), Map.entry("read_line", STDIN),
            Map.entry("read_lines", STDIN), Map.entry("read_all", STDIN), Map.entry("stdin", STDIN),
            Map.entry("scanf", STDIN), Map.entry("gets", STDIN),
            Map.entry("println", PRINT), Map.entry("printf", PRINT), Map.entry("puts", PRINT),
            Map.entry("echo", PRINT), Map.entry("console", PRINT),
            Map.entry("int", TO_INT), Map.entry("parseInt", TO_INT), Map.entry("parse_int", TO_INT),
            Map.entry("atoi", TO_INT), Map.entry("Number", TO_INT),
            Map.entry("float", TO_FLOAT), Map.entry("parseFloat", TO_FLOAT),
            Map.entry("str", TO_STRING), Map.entry("toString", TO_STRING),
            Map.entry("len", LENGTH), Map.entry("length", LENGTH), Map.entry("size", LENGTH),
            Map.entry("True", BOOLEAN), Map.entry("False", BOOLEAN), Map.entry("TRUE", BOOLEAN),
            Map.entry("FALSE", BOOLEAN),
            Map.entry("None", NULL), Map.entry("nil", NULL), Map.entry("NULL", NULL),
            Map.entry("undefined", NULL), Map.entry("nullptr", NULL),
            Map.entry("self", SELF), Map.entry("this", SELF));

    /** Java classes newcomers use without an import, outside java.lang. */
    private static final Map<String, String> JAVA_CLASSES = Map.ofEntries(
            Map.entry("Scanner", "java.util.Scanner"), Map.entry("BufferedReader", "java.io.BufferedReader"),
            Map.entry("InputStreamReader", "java.io.InputStreamReader"),
            Map.entry("ArrayList", "java.util.ArrayList"), Map.entry("HashMap", "java.util.HashMap"),
            Map.entry("HashSet", "java.util.HashSet"), Map.entry("TreeMap", "java.util.TreeMap"),
            Map.entry("ArrayDeque", "java.util.ArrayDeque"), Map.entry("Arrays", "java.util.Arrays"),
            Map.entry("Collections", "java.util.Collections"), Map.entry("LocalDate", "java.time.LocalDate"),
            Map.entry("BigDecimal", "java.math.BigDecimal"), Map.entry("BigInteger", "java.math.BigInteger"));

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("str", "String"), Map.entry("string", "String"), Map.entry("char", "String"),
            Map.entry("Character", "String"),
            Map.entry("int", "Int"), Map.entry("integer", "Int"), Map.entry("Integer", "Int"),
            Map.entry("long", "Int"), Map.entry("Long", "Int"), Map.entry("short", "Int"),
            Map.entry("byte", "Int"), Map.entry("i64", "Int"), Map.entry("i32", "Int32"),
            Map.entry("bool", "Bool"), Map.entry("boolean", "Bool"), Map.entry("Boolean", "Bool"),
            Map.entry("float", "Float"), Map.entry("double", "Float"), Map.entry("Double", "Float"),
            Map.entry("f64", "Float"), Map.entry("number", "Int or Float"),
            Map.entry("void", "Unit"), Map.entry("None", "Unit"),
            Map.entry("list", "List[T] or MutableList[T]"), Map.entry("ArrayList", "MutableList[T]"),
            Map.entry("dict", "Map[K, V] or MutableMap[K, V]"), Map.entry("HashMap", "MutableMap[K, V]"),
            Map.entry("Dictionary", "Map[K, V] or MutableMap[K, V]"));

    /** A hint for an unresolved value name, or null. */
    public static String nameHint(String name) {
        String hint = NAMES.get(name);
        if (hint != null) {
            return hint;
        }
        if (name.equals("System")) {
            return PRINT + " " + STDIN;
        }
        String javaClass = JAVA_CLASSES.get(name);
        if (javaClass == null && isJavaLang(name)) {
            javaClass = "java.lang." + name;
        }
        if (javaClass != null) {
            String advice = "A Java class needs an import first: import " + javaClass + " as " + name + ".";
            if (List.of("Scanner", "BufferedReader", "InputStreamReader").contains(name)) {
                advice += " For standard input the bundled module is simpler. " + STDIN;
            } else if (List.of("Integer", "Long").contains(name)) {
                advice += " " + TO_INT;
            }
            return advice;
        }
        return null;
    }

    /** A hint for an unknown type name spelled the way another language spells it, or null. */
    public static String typeHint(String name) {
        String sprig = TYPES.get(name);
        if (sprig == null) {
            return null;
        }
        String extra = switch (sprig) {
            case "String" -> name.toLowerCase().startsWith("char")
                    ? "; Sprig has no Char type, so a character is a one-character String" : "";
            case "Int" -> "; Int is a signed 64-bit integer";
            default -> "";
        };
        return "Sprig spells this type " + sprig + extra
                + ". The built-in types are Int, Int32, Float, Bool, String, Unit, List[T], MutableList[T], "
                + "Map[K, V] and MutableMap[K, V]; T? admits null.";
    }

    /** A hint for calling a built-in type like a conversion function, such as Int("3"), or null. */
    public static String conversionHint(String typeName) {
        return switch (typeName) {
            case "Int", "Int32", "BigInt" -> "A type is not a conversion function. " + TO_INT
                    + " A Float converts with value.toIntTrunc() or value.toIntExact().";
            case "Float", "Float32" -> "A type is not a conversion function. " + TO_FLOAT;
            case "String" -> "A type is not a conversion function. " + TO_STRING;
            case "Bool" -> "A type is not a conversion function; compare to get a Bool, for example text == \"true\".";
            default -> null;
        };
    }

    /**
     * A hint for a word another language uses to start a declaration, seen just
     * before a syntax error (public static ..., def f(), const x = 1), or null.
     */
    public static String declarationHint(String word) {
        return switch (word) {
            case "public", "private", "protected", "static", "final", "abstract" ->
                    "Sprig has no access or static modifiers, and a program needs no class Main or main method: "
                            + "top-level statements run in order. Run 'sprig help language' for a complete program.";
            case "def", "function", "fun", "fn", "sub", "proc" ->
                    "Functions are declared with func: 'func name(parameter: Type) -> ResultType:', "
                            + "with '-> Unit' when nothing is returned.";
            case "except", "rescue" -> CATCH;
            case "const", "val", "auto", "mut" ->
                    "Declare with let (cannot be reassigned) or var (can be): 'let limit = 10', 'var count = 0'.";
            default -> null;
        };
    }

    /** A hint for a member that a built-in type does not have but a newcomer expects, or null. */
    /**
     * The one spelling of a fallible numeric conversion: an Int has no
     * toFloat() and a Float no toInt(), because each can lose information.
     */
    public static String exactConversion(String typeDisplay, String member) {
        if (typeDisplay.equals("Int") && member.equals("toFloat")) {
            return "toFloatExact";
        }
        if (typeDisplay.equals("Float") && member.equals("toInt")) {
            return "toIntExact";
        }
        return null;
    }

    /** A hint for a static member that Sprig spells another way. */
    public static String staticMemberHint(String typeDisplay, String member) {
        if (typeDisplay.equals("Decimal") && member.equals("fromInt")) {
            return "Convert an integer with value.toDecimal().";
        }
        if (List.of("Int", "Float").contains(typeDisplay) && List.of("parse", "valueOf").contains(member)) {
            return typeDisplay.equals("Int") ? TO_INT : "Convert text with text.toFloat().";
        }
        return null;
    }

    public static String memberHint(String typeDisplay, String member) {
        if (typeDisplay.equals("Int") && member.equals("toFloat")) {
            return "An Int beyond 2^53 has no exact Float: write value.toFloatExact(), which fails instead of "
                    + "rounding, or value.toFloatLossy(), which rounds.";
        }
        if (typeDisplay.equals("Float") && member.equals("toInt")) {
            return "A Float with a fraction has no exact Int: write value.toIntExact(), which fails on a "
                    + "fraction, or value.toIntTrunc(), which drops it.";
        }
        boolean text = typeDisplay.equals("String");
        boolean collection = typeDisplay.startsWith("List[") || typeDisplay.startsWith("MutableList[")
                || typeDisplay.startsWith("Map[") || typeDisplay.startsWith("MutableMap[");
        if (text && List.of("size", "len", "count").contains(member)) {
            return "A String's length is value.length().";
        }
        if (collection && List.of("length", "len", "count").contains(member)) {
            return "A list or map reports its length with items.size().";
        }
        if (collection && List.of("add", "push", "push_back").contains(member)) {
            return "Add to a MutableList with items.append(value).";
        }
        if (text && List.of("strip").contains(member)) {
            return "Remove surrounding spaces with value.trim().";
        }
        if (text && List.of("lower", "upper").contains(member)) {
            return "Use value.toLowerCase() or value.toUpperCase().";
        }
        if (text && List.of("parseInt", "toInteger", "asInt").contains(member)) {
            return TO_INT;
        }
        if (text && List.of("format", "formatted", "interpolate").contains(member)) {
            return "Build text with +, for example \"total: \" + total + \" items\"; Sprig has no format strings.";
        }
        if (text && List.of("lines", "splitlines", "splitLines").contains(member)) {
            return "Split into lines with text.lines(value) after 'import \"@std/text.spr\" as text'.";
        }
        if (text && List.of("padStart", "padEnd", "ljust", "rjust", "padLeft", "padRight", "center").contains(member)) {
            return "Pad with text.pad_left(value, width, fill) or text.pad_right(value, width, fill) "
                    + "after 'import \"@std/text.spr\" as text'.";
        }
        if (text && List.of("removeprefix", "removesuffix", "stripPrefix", "stripSuffix", "removePrefix",
                "removeSuffix").contains(member)) {
            return "Remove a prefix or suffix with text.strip_prefix(value, prefix) or text.strip_suffix(value, suffix) "
                    + "after 'import \"@std/text.spr\" as text'.";
        }
        if (text && List.of("find", "index", "search").contains(member)) {
            return "Find text with value.indexOf(needle) (-1 when absent) or value.lastIndexOf(needle).";
        }
        if (text && List.of("replaceAll", "replaceFirst", "sub").contains(member)) {
            return "Replace every occurrence with value.replace(old, new); Sprig has no regular expressions here.";
        }
        if (text && List.of("startswith", "endswith", "beginsWith", "hasPrefix", "hasSuffix").contains(member)) {
            return "Use value.startsWith(prefix) or value.endsWith(suffix).";
        }
        if (text && List.of("chars", "toCharArray", "characters", "codePoints", "iter").contains(member)) {
            return "Iterate a String one code point at a time: 'for ch in value:'; value[i] is one code point.";
        }
        if (text && List.of("isdigit", "isDigit", "isNumeric", "isalpha", "isAlpha", "isLetter").contains(member)) {
            return "Classify ASCII text with text.is_ascii_digit(value) or text.is_ascii_letter(value) "
                    + "after 'import \"@std/text.spr\" as text'; value.toIntOrNull() tests for a whole number.";
        }
        if (text && List.of("reversed", "reverse").contains(member)) {
            return "There is no reverse method: loop 'for ch in value:' and prepend, or collect value[i] into a MutableList and sort/reverse there.";
        }
        if (text && List.of("join", "concat", "append").contains(member)) {
            return "Join text with +, or a list of strings with String.join(separator, items).";
        }
        if (collection && List.of("has", "includes", "containsKey").contains(member) && typeDisplay.contains("List")) {
            return "Test membership with items.contains(value) or value in items.";
        }
        if (collection && List.of("has", "contains", "includes").contains(member) && typeDisplay.contains("Map")) {
            return "Test for a key with map.containsKey(key) or key in map.";
        }
        return null;
    }

    private static boolean isJavaLang(String name) {
        if (name.isEmpty() || !Character.isUpperCase(name.charAt(0))) {
            return false;
        }
        try {
            Class.forName("java.lang." + name, false, Newcomer.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
