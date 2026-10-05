package sprig.compiler.lsp;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Expr;
import sprig.compiler.ast.Module;
import sprig.compiler.ast.Stmt;
import sprig.compiler.ast.TypeRef;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.front.AstBuilder;
import sprig.compiler.front.ParserFrontend;
import sprig.compiler.jvm.JvmMetadata;
import sprig.compiler.parser.SprigLexer;
import sprig.compiler.sem.BuiltinMembers;
import sprig.compiler.sem.ImportNames;
import sprig.compiler.sem.ResolvedField;
import sprig.compiler.sem.Symbol;
import sprig.compiler.types.ClassType;
import sprig.compiler.types.EnumType;
import sprig.compiler.types.JavaType;
import sprig.compiler.types.Type;
import sprig.compiler.types.VariantCaseType;
import sprig.compiler.types.VariantType;

/**
 * Completion items at a position. The word at the cursor is replaced by a
 * placeholder identifier and the result is parsed (names in scope) or checked
 * (members after a dot), so suggestions come from the same front end and
 * checker as diagnostics.
 */
final class Completion {
    static final String PLACEHOLDER = "__sprig_complete__";

    // LSP CompletionItemKind values.
    private static final int METHOD = 2;
    private static final int FUNCTION = 3;
    private static final int FIELD = 5;
    private static final int VARIABLE = 6;
    private static final int CLASS = 7;
    private static final int MODULE = 9;
    private static final int ENUM = 13;
    private static final int KEYWORD = 14;
    private static final int ENUM_MEMBER = 20;
    private static final int CONSTANT = 21;
    private static final int TYPE_PARAMETER = 25;

    private static final List<String> BUILTIN_TYPES = List.of("Int", "Int32", "Float", "Float32", "Decimal",
            "BigInt", "Bool", "String", "Unit", "Error");
    private static final List<String> COLLECTION_TYPES = List.of("List", "MutableList", "Map", "MutableMap");
    private static final List<String> BUILTIN_FUNCTIONS = List.of("print", "range", "assert");
    private static final Set<String> DECLARING_KEYWORDS = Set.of("let", "var", "func", "class", "enum", "variant",
            "for", "catch", "generic", "as", "conform", "import");
    /**
     * Where an unfinished line puts a type: after {@code ->}, after the colon
     * of a let/var/catch binding or a parameter, or inside {@code Type[...]}.
     */
    private static final java.util.regex.Pattern TYPE_CONTEXT = java.util.regex.Pattern.compile(
            "(?:->|(?:\\b(?:let|var|catch)\\s+[A-Za-z_]\\w*|[(,]\\s*[A-Za-z_]\\w*)\\s*:"
                    + "|\\b[A-Z]\\w*\\[(?:[^\\]]*,)?)\\s*$");
    static final List<String> KEYWORDS = lexerKeywords();

    /** Runs the compiler on a text with the placeholder; the result is the main module's analysis. */
    interface Checker {
        Analysis check(String placeholderText);
    }

    private final Map<String, Map<String, Object>> items = new LinkedHashMap<>();

    private Completion() {
    }

    static List<Map<String, Object>> complete(Path path, TextLines lines, int line, int character,
                                              Module lastGood, Checker checker) {
        String content = lines.line(line);
        int cursor = Math.max(0, Math.min(character, content.length()));
        if (insideCommentOrString(content, cursor)) {
            return List.of();
        }
        int start = cursor;
        while (start > 0 && identifierPart(content.charAt(start - 1))) {
            start--;
        }
        int end = cursor;
        while (end < content.length() && identifierPart(content.charAt(end))) {
            end++;
        }
        if (start < content.length() && Character.isDigit(content.charAt(start))) {
            return List.of();
        }
        String before = content.substring(0, start);
        if (declaresName(before)) {
            return List.of();
        }
        boolean member = before.endsWith(".");
        if (member && before.length() > 1 && Character.isDigit(before.charAt(before.length() - 2))) {
            return List.of();
        }
        int from = lines.offset(line, start);
        int to = lines.offset(line, end);
        String text = lines.text.substring(0, from) + PLACEHOLDER + lines.text.substring(to);
        Completion completion = new Completion();
        if (member) {
            completion.members(path, checker.check(text));
        } else {
            Module module = parse(path, text);
            if (module != null) {
                ScopeFinder finder = new ScopeFinder(PLACEHOLDER).find(module);
                completion.names(module, finder, finder.found instanceof TypeRef);
            } else if (lastGood != null) {
                // The unfinished line does not parse: no local scope, and the
                // line itself tells whether a type is expected.
                completion.names(lastGood, new ScopeFinder(PLACEHOLDER), TYPE_CONTEXT.matcher(before).find());
            } else {
                completion.keywords();
            }
        }
        return new ArrayList<>(completion.items.values());
    }

    // ------------------------------------------------------------------
    // Members after a dot
    // ------------------------------------------------------------------

    private void members(Path path, Analysis analysis) {
        Module module = analysis.module(path);
        if (module == null || !analysis.resolved()) {
            return;
        }
        ScopeFinder finder = new ScopeFinder(PLACEHOLDER).find(module);
        if (finder.found instanceof TypeRef ref && ref.parts.size() == 2) {
            Symbol alias = module.scope == null ? null : module.scope.importAliases.get(ref.parts.get(0));
            if (alias != null && alias.kind == Symbol.Kind.MODULE && alias.module != null) {
                moduleMembers(alias.module, true);
            }
            return;
        }
        if (!(finder.found instanceof Expr.FieldAccess access)) {
            return;
        }
        Expr receiver = access.receiver;
        if (receiver instanceof Expr.Name name && name.symbol != null) {
            Symbol symbol = name.symbol;
            switch (symbol.kind) {
                case MODULE -> {
                    if (symbol.module != null) {
                        moduleMembers(symbol.module, false);
                    }
                    return;
                }
                case ENUM, VARIANT, BUILTIN_TYPE, JAVA_TYPE -> {
                    staticMembers(symbol.kind == Symbol.Kind.JAVA_TYPE && symbol.javaClass != null
                            ? new JavaType(symbol.javaClass) : symbol.type);
                    return;
                }
                case CLASS -> {
                    return;
                }
                default -> {
                    Type type = receiver.type != null ? receiver.type : symbol.type;
                    if (type != null && !type.isError()) {
                        valueMembers(type.nonNull());
                    }
                    return;
                }
            }
        }
        if (receiver instanceof Expr.FieldAccess qualifier && qualifier.resolved != null
                && qualifier.resolved.kind == ResolvedField.Kind.MODULE_TYPE) {
            staticMembers(qualifier.resolved.type);
            return;
        }
        if (receiver instanceof Expr.Subscript subscript && subscript.applicationType != null) {
            staticMembers(subscript.applicationType);
            return;
        }
        if (receiver.type != null && !receiver.type.isError()) {
            valueMembers(receiver.type.nonNull());
        }
    }

    private void moduleMembers(Module target, boolean typesOnly) {
        if (target.scope == null) {
            return;
        }
        for (Symbol symbol : target.scope.types.values()) {
            if (symbol.decl != null) {
                typeItem(symbol.decl, "1");
            }
        }
        if (typesOnly) {
            return;
        }
        for (Symbol symbol : target.scope.functions.values()) {
            if (symbol.decl instanceof Decl.Func func) {
                add(func.name, FUNCTION, Describe.functionLine(func), "1");
            }
        }
        for (Symbol symbol : target.scope.topVars.values()) {
            add(symbol.name, symbol.mutable ? VARIABLE : CONSTANT,
                    (symbol.mutable ? "var " : "let ") + symbol.name
                            + (symbol.type == null || symbol.type.isError() ? "" : ": " + symbol.type.display()), "1");
        }
    }

    private void staticMembers(Type type) {
        if (type instanceof EnumType enumType) {
            for (String name : enumType.decl.cases) {
                add(name, ENUM_MEMBER, enumType.decl.name + "." + name, "1");
            }
        } else if (type instanceof VariantType variantType) {
            for (Decl.VariantCase variantCase : variantType.decl.cases) {
                add(variantCase.name, ENUM_MEMBER,
                        variantType.decl.name + "." + Describe.variantCase(variantCase), "1");
            }
        } else if (type instanceof JavaType javaType) {
            javaMembers(javaType.clazz, true);
        } else if (type != null) {
            for (String name : BuiltinMembers.staticNames(type)) {
                add(name, METHOD, type.display() + "." + name + "(...)", "1");
            }
        }
    }

    private void valueMembers(Type type) {
        if (type instanceof ClassType classType) {
            for (Decl.Field field : classType.decl.fields) {
                add(field.name, FIELD, Describe.field(field), "1");
            }
            for (Decl.Func method : classType.decl.methods) {
                add(method.name, METHOD, Describe.functionLine(method), "1");
            }
            add("toString", METHOD, "func toString() -> String", "2");
        } else if (type instanceof VariantCaseType caseType) {
            for (Decl.Field field : caseType.variantCase.fields) {
                add(field.name, FIELD, field.name + ": " + field.typeRef.display(), "1");
            }
            add("toString", METHOD, "func toString() -> String", "2");
        } else if (type instanceof EnumType) {
            add("toString", METHOD, "func toString() -> String", "2");
        } else if (type instanceof JavaType javaType) {
            if (Throwable.class.isAssignableFrom(javaType.clazz)) {
                add("message", FIELD, "message: String", "1");
            }
            javaMembers(javaType.clazz, false);
        } else {
            for (String name : BuiltinMembers.instanceNames(type)) {
                add(name, METHOD, type.display() + "." + name + "(...)", "1");
            }
        }
    }

    private void javaMembers(Class<?> clazz, boolean statics) {
        Map<String, Map<String, Object>> sorted = new TreeMap<>();
        try {
            for (Field field : clazz.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) == statics && JvmMetadata.support(field).usable()) {
                    sorted.putIfAbsent(field.getName(), item(field.getName(), FIELD,
                            field.getType().getSimpleName() + " " + field.getName(), "1"));
                }
            }
            for (Method method : clazz.getMethods()) {
                if (Modifier.isStatic(method.getModifiers()) != statics || method.isSynthetic() || method.isBridge()
                        || (method.getDeclaringClass() == Object.class
                            && !Set.of("toString", "equals", "hashCode").contains(method.getName()))
                        || !JvmMetadata.support(method).usable()) {
                    continue;
                }
                sorted.putIfAbsent(method.getName(), item(method.getName(), METHOD,
                        clazz.getSimpleName() + "." + method.getName() + "(...)", "1"));
            }
        } catch (LinkageError | SecurityException e) {
            return;
        }
        items.putAll(sorted);
    }

    // ------------------------------------------------------------------
    // Names in scope
    // ------------------------------------------------------------------

    private void names(Module module, ScopeFinder finder, boolean typePosition) {
        if (!typePosition) {
            List<ScopeFinder.Binding> visible = finder.visible;
            for (int i = visible.size() - 1; i >= 0; i--) {
                ScopeFinder.Binding binding = visible.get(i);
                add(binding.name(), VARIABLE, binding.detail(), "0");
            }
            if (finder.classDecl != null && finder.function != null) {
                for (Decl.Field field : finder.classDecl.fields) {
                    add(field.name, FIELD, Describe.field(field), "1");
                }
                for (Decl.Func method : finder.classDecl.methods) {
                    add(method.name, METHOD, Describe.functionLine(method), "1");
                }
            }
        }
        for (String parameter : finder.typeParams) {
            add(parameter, TYPE_PARAMETER, "type parameter " + parameter, "1");
        }
        for (Decl decl : module.decls) {
            if (decl instanceof Decl.Func func) {
                if (!typePosition) {
                    add(func.name, FUNCTION, Describe.functionLine(func), "2");
                }
            } else if (!(decl instanceof Decl.Conform)) {
                typeItem(decl, "2");
            }
        }
        if (!typePosition) {
            boolean everyTopVar = finder.topStatement < 0;
            for (int i = 0; i < module.topStatements.size(); i++) {
                if (!everyTopVar && i >= finder.topStatement) {
                    break;
                }
                if (module.topStatements.get(i) instanceof Stmt.VarDecl varDecl) {
                    add(varDecl.name, varDecl.mutable ? VARIABLE : CONSTANT, (varDecl.mutable ? "var " : "let ")
                            + varDecl.name + (varDecl.typeRef == null ? "" : ": " + varDecl.typeRef.display()), "2");
                }
            }
        }
        for (Decl.Import imp : module.imports) {
            String alias = ImportNames.aliasFor(imp);
            if (alias != null) {
                add(alias, imp.fileImport ? MODULE : CLASS,
                        imp.fileImport ? "import \"" + imp.pathOrClass + "\"" : imp.pathOrClass, "3");
            }
        }
        for (String name : BUILTIN_TYPES) {
            add(name, CLASS, "built-in type", "4");
        }
        if (typePosition) {
            for (String name : COLLECTION_TYPES) {
                add(name, CLASS, "built-in collection type", "4");
            }
            add("fn", KEYWORD, "fn(A) -> R function type", "5");
            return;
        }
        for (String name : BUILTIN_FUNCTIONS) {
            add(name, FUNCTION, "built-in function", "4");
        }
        keywords();
    }

    private void keywords() {
        for (String keyword : KEYWORDS) {
            add(keyword, KEYWORD, null, "5");
        }
    }

    private void typeItem(Decl decl, String rank) {
        if (decl instanceof Decl.ClassDecl) {
            add(decl.name, CLASS, "class " + decl.name, rank);
        } else if (decl instanceof Decl.EnumDecl) {
            add(decl.name, ENUM, "enum " + decl.name, rank);
        } else if (decl instanceof Decl.VariantDecl) {
            add(decl.name, ENUM, "variant " + decl.name, rank);
        }
    }

    private void add(String label, int kind, String detail, String rank) {
        if (label.equals(PLACEHOLDER) || items.containsKey(label)) {
            return;
        }
        items.put(label, item(label, kind, detail, rank));
    }

    private static Map<String, Object> item(String label, int kind, String detail, String rank) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("kind", kind);
        if (detail != null) {
            item.put("detail", detail);
        }
        item.put("sortText", rank + "_" + label);
        return item;
    }

    // ------------------------------------------------------------------
    // Text helpers
    // ------------------------------------------------------------------

    private static Module parse(Path path, String text) {
        Diagnostics diagnostics = new Diagnostics();
        try {
            var tree = ParserFrontend.parse(path, path.toUri().toString(), text, diagnostics);
            if (diagnostics.hasErrors()) {
                return null;
            }
            return new AstBuilder(path.toUri().toString(), diagnostics).build(path, tree);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean identifierPart(char c) {
        return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    /** Strings cannot span lines in Sprig, so the line prefix decides. */
    static boolean insideCommentOrString(String line, int cursor) {
        boolean string = false;
        for (int i = 0; i < cursor; i++) {
            char c = line.charAt(i);
            if (string) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    string = false;
                }
            } else if (c == '"') {
                string = true;
            } else if (c == '#') {
                return true;
            }
        }
        return string;
    }

    private static boolean declaresName(String before) {
        String trimmed = before.stripTrailing();
        if (trimmed.length() == before.length()) {
            return false;
        }
        int start = trimmed.length();
        while (start > 0 && identifierPart(trimmed.charAt(start - 1))) {
            start--;
        }
        return DECLARING_KEYWORDS.contains(trimmed.substring(start));
    }

    private static List<String> lexerKeywords() {
        List<String> out = new ArrayList<>();
        for (int type = 1; type <= SprigLexer.VOCABULARY.getMaxTokenType(); type++) {
            String literal = SprigLexer.VOCABULARY.getLiteralName(type);
            if (literal != null && literal.matches("'[a-z]+'")) {
                out.add(literal.substring(1, literal.length() - 1));
            }
        }
        return List.copyOf(out);
    }
}
