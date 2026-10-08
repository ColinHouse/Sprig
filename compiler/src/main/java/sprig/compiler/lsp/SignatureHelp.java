package sprig.compiler.lsp;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Expr;
import sprig.compiler.jvm.JvmMetadata;
import sprig.compiler.sem.Symbol;
import sprig.compiler.types.ClassType;
import sprig.compiler.types.FunctionType;
import sprig.compiler.types.JavaType;
import sprig.compiler.types.Type;

/** Call signatures, using a temporary completed expression without changing the editor's text. */
final class SignatureHelp {
    private static final String PLACEHOLDER = "__sprig_signature_probe__";

    private record Token(String text, int start, int end) {
    }

    private record Site(int start, int end, int argument, String named, String callee) {
    }

    private record Signature(String label, List<String> parameters, List<String> names, boolean varargs) {
        Map<String, Object> json(int argument, String named) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("label", label);
            out.put("parameters", parameters.stream().map(p -> Map.of("label", p)).toList());
            if (!parameters.isEmpty()) {
                int active = named == null ? argument : names.indexOf(named);
                if (active >= 0 && active < parameters.size()) out.put("activeParameter", active);
                else if (varargs && active >= parameters.size()) out.put("activeParameter", parameters.size() - 1);
            }
            return out;
        }
    }

    private SignatureHelp() {
    }

    static Map<String, Object> help(Path path, TextLines lines, int cursor, Function<String, Analysis> checker) {
        Site site = site(lines.text, cursor);
        if (site == null) return null;
        // Drop this call's arguments; the callee and its receiver retain their real lexical scope.
        // Closing surrounding open delimiters permits e.g. print(add(1, <cursor> with no right parens.
        String prefix = lines.text.substring(0, site.start) + site.callee + "()." + PLACEHOLDER;
        String suffix = lines.text.substring(site.end);
        String probe = prefix + (site.end == lines.text.length() || suffix.startsWith("\n") || suffix.startsWith("\r")
                ? closers(prefix) : "") + suffix;
        Analysis analysis = checker.apply(probe);
        var module = analysis.module(path);
        if (module == null) return null;
        ScopeFinder finder = new ScopeFinder(PLACEHOLDER).find(module);
        if (!(finder.found instanceof Expr.FieldAccess field) || !(field.receiver instanceof Expr.Call call)) return null;
        List<Signature> signatures = signatures(call.callee);
        if (signatures.isEmpty()) return null;
        List<Map<String, Object>> items = signatures.stream().map(s -> s.json(site.argument, site.named)).toList();
        int active = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).containsKey("activeParameter")) { active = i; break; }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signatures", items);
        result.put("activeSignature", active);
        if (items.get(active).containsKey("activeParameter")) {
            result.put("activeParameter", items.get(active).get("activeParameter"));
        }
        return result;
    }

    private static List<Signature> signatures(Expr callee) {
        if (callee instanceof Expr.Subscript subscript) return signatures(subscript.base);
        if (callee instanceof Expr.Name name && name.symbol != null) {
            Symbol symbol = name.symbol;
            if (symbol.decl instanceof Decl.Func function) return List.of(function(function));
            if (symbol.decl instanceof Decl.ClassDecl type) return List.of(constructor(type.name, type.fields));
            if (symbol.kind == Symbol.Kind.JAVA_TYPE && symbol.javaClass != null) return javaSignatures(symbol.javaClass, null, true);
            if (symbol.type instanceof FunctionType function) return List.of(callable(name.name, function));
        }
        if (callee instanceof Expr.FieldAccess access) {
            if (access.resolved != null) {
                var resolved = access.resolved;
                if (resolved.methodDecl != null) return List.of(function(resolved.methodDecl));
                if (resolved.symbol != null && resolved.symbol.decl instanceof Decl.Func function) return List.of(function(function));
                if (resolved.classDecl != null) return List.of(constructor(resolved.classDecl.name, resolved.classDecl.fields));
                if (resolved.variantCase != null) return List.of(constructor(access.name, resolved.variantCase.fields));
                if (resolved.symbol != null && resolved.symbol.kind == Symbol.Kind.JAVA_TYPE && resolved.symbol.javaClass != null) {
                    return javaSignatures(resolved.symbol.javaClass, null, true);
                }
            }
            Expr receiver = access.receiver;
            Type type = receiver.type;
            boolean statics = false;
            if (receiver instanceof Expr.Name name && name.symbol != null) {
                if (name.symbol.kind == Symbol.Kind.JAVA_TYPE && name.symbol.javaClass != null) {
                    type = new JavaType(name.symbol.javaClass);
                    statics = true;
                } else if (type == null || type.isError()) type = name.symbol.type;
            }
            if (type != null && type.nonNull() instanceof JavaType javaType) return javaSignatures(javaType.clazz, access.name, statics);
            if (type != null && type.nonNull() instanceof ClassType classType) {
                for (Decl.Func method : classType.decl.methods) {
                    if (method.name.equals(access.name)) return List.of(function(method));
                }
            }
        }
        return List.of();
    }

    private static Signature function(Decl.Func func) {
        return new Signature(Describe.functionLine(func), func.params.stream()
                .map(p -> p.name + ": " + p.typeRef.display()).toList(), func.params.stream().map(p -> p.name).toList(), false);
    }

    private static Signature constructor(String name, List<Decl.Field> fields) {
        List<String> parameters = fields.stream().map(f -> f.name + ": " + f.typeRef.display()).toList();
        return new Signature(name + "(" + String.join(", ", parameters) + ")", parameters,
                fields.stream().map(f -> f.name).toList(), false);
    }

    private static Signature callable(String name, FunctionType function) {
        List<String> names = new ArrayList<>(), parameters = new ArrayList<>();
        for (int i = 0; i < function.params.size(); i++) {
            names.add("arg" + i);
            parameters.add("arg" + i + ": " + function.params.get(i).display());
        }
        return new Signature(name + "(" + String.join(", ", parameters) + ") -> " + function.result.display(), parameters, names, false);
    }

    private static List<Signature> javaSignatures(Class<?> clazz, String name, boolean statics) {
        List<Executable> candidates = new ArrayList<>();
        try {
            if (name == null) candidates.addAll(List.of(clazz.getConstructors()));
            else for (Method method : clazz.getMethods()) {
                if (method.getName().equals(name) && Modifier.isStatic(method.getModifiers()) == statics
                        && !method.isSynthetic() && !method.isBridge()) candidates.add(method);
            }
            candidates.sort(Comparator.comparing(Executable::toGenericString));
            List<Signature> out = new ArrayList<>();
            for (Executable executable : candidates) {
                if (!JvmMetadata.support(executable).usable()) continue;
                Map<String, Object> metadata = JvmMetadata.describe(executable);
                @SuppressWarnings("unchecked") List<String> types = (List<String>) metadata.get("sprigParameterTypes");
                List<String> parameters = new ArrayList<>(), names = new ArrayList<>();
                for (int i = 0; i < types.size(); i++) {
                    String parameter = executable.getParameters()[i].isNamePresent() ? executable.getParameters()[i].getName() : "arg" + i;
                    names.add(parameter);
                    parameters.add(parameter + ": " + types.get(i));
                }
                String label = (name == null ? clazz.getSimpleName() : clazz.getSimpleName() + "." + name)
                        + "(" + String.join(", ", parameters) + ") -> " + metadata.get("sprigReturnType");
                out.add(new Signature(label, parameters, names, executable.isVarArgs()));
            }
            return out;
        } catch (LinkageError | SecurityException e) {
            return List.of();
        }
    }

    /** Lightweight delimiter accounting, not an alternative Sprig parser or resolver. */
    private static Site site(String text, int cursor) {
        List<Token> tokens = tokens(text);
        List<Integer> stack = new ArrayList<>();
        Map<Integer, Integer> pairs = new LinkedHashMap<>();
        List<Integer> atCursor = null;
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (atCursor == null && token.start >= cursor) atCursor = List.copyOf(stack);
            if (List.of("(", "[", "{").contains(token.text)) stack.add(i);
            else if (List.of(")", "]", "}").contains(token.text) && !stack.isEmpty()) {
                int open = stack.get(stack.size() - 1);
                if (matches(tokens.get(open).text, token.text)) { stack.remove(stack.size() - 1); pairs.put(open, i); }
            }
        }
        if (atCursor == null) atCursor = List.copyOf(stack);
        int lineStart = text.lastIndexOf('\n', Math.max(0, cursor - 1)) + 1;
        if (Completion.insideCommentOrString(text.substring(lineStart, cursor), cursor - lineStart)) return null;
        for (int p = atCursor.size() - 1; p >= 0; p--) {
            int open = atCursor.get(p);
            if (!tokens.get(open).text.equals("(") || open == 0) continue;
            int start = expressionStart(text, tokens, pairs, open - 1);
            if (start < 0) continue;
            if (start > 0 && List.of("func", "class", "conform").contains(tokens.get(start - 1).text)) continue;
            if (List.of("fn", "if", "while", "match", "catch").contains(tokens.get(start).text)) continue;
            int argument = 0, depth = 0, argumentStart = open + 1;
            for (int i = open + 1; i < tokens.size() && tokens.get(i).start < cursor; i++) {
                String value = tokens.get(i).text;
                if (List.of("(", "[", "{").contains(value)) depth++;
                else if (List.of(")", "]", "}").contains(value)) depth--;
                else if (value.equals(",") && depth == 0) { argument++; argumentStart = i + 1; }
            }
            String named = argumentStart + 1 < tokens.size() && tokens.get(argumentStart + 1).start < cursor
                    && tokens.get(argumentStart + 1).text.equals("=")
                    && (argumentStart + 2 >= tokens.size() || !tokens.get(argumentStart + 2).text.equals("="))
                    ? tokens.get(argumentStart).text : null;
            Integer close = pairs.get(open);
            int end = close == null ? text.indexOf('\n', cursor) : tokens.get(close).end;
            if (end < 0) end = text.length();
            return new Site(tokens.get(start).start, end, argument, named,
                    text.substring(tokens.get(start).start, tokens.get(open).start).strip());
        }
        return null;
    }

    private static int expressionStart(String text, List<Token> tokens, Map<Integer, Integer> pairs, int end) {
        String value = tokens.get(end).text;
        int start = end;
        if (value.equals("]") || value.equals(")")) {
            int open = -1;
            for (var entry : pairs.entrySet()) if (entry.getValue() == end) { open = entry.getKey(); break; }
            if (open < 0) return -1;
            // A pair is either a postfix call/subscript or a grouped receiver/literal.
            // Do not consume an unrelated expression on the preceding source line.
            int receiver = open > 0 && !text.substring(tokens.get(open - 1).end, tokens.get(open).start).contains("\n")
                    ? expressionStart(text, tokens, pairs, open - 1) : -1;
            start = receiver < 0 ? open : receiver;
        } else if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) return -1;
        if (start > 1 && tokens.get(start - 1).text.equals(".")) return expressionStart(text, tokens, pairs, start - 2);
        return start;
    }

    private static String closers(String text) {
        List<String> stack = new ArrayList<>();
        for (Token token : tokens(text)) {
            if (List.of("(", "[", "{").contains(token.text)) stack.add(token.text);
            else if (!stack.isEmpty() && matches(stack.get(stack.size() - 1), token.text)) stack.remove(stack.size() - 1);
        }
        StringBuilder out = new StringBuilder();
        for (int i = stack.size() - 1; i >= 0; i--) out.append(switch (stack.get(i)) { case "(" -> ")"; case "[" -> "]"; default -> "}"; });
        return out.toString();
    }

    private static boolean matches(String open, String close) {
        return open.equals("(") && close.equals(")") || open.equals("[") && close.equals("]") || open.equals("{") && close.equals("}");
    }

    private static List<Token> tokens(String text) {
        List<Token> out = new ArrayList<>();
        for (int i = 0; i < text.length();) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '#') { while (i < text.length() && text.charAt(i) != '\n') i++; continue; }
            int start = i++;
            if (c == '"') {
                while (i < text.length() && text.charAt(i) != '\n') {
                    char next = text.charAt(i++);
                    if (next == '\\' && i < text.length()) i++;
                    else if (next == '"') break;
                }
            } else if (Character.isLetterOrDigit(c) || c == '_') {
                while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) i++;
            }
            out.add(new Token(text.substring(start, i), start, i));
        }
        return out;
    }
}
