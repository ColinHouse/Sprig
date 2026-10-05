package sprig.compiler.lsp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Module;
import sprig.compiler.ast.Stmt;
import sprig.compiler.diag.Span;

/** Hierarchical document symbols for one parsed module. */
final class Outline {
    // LSP SymbolKind values.
    private static final int CLASS = 5;
    private static final int METHOD = 6;
    private static final int FIELD = 8;
    private static final int ENUM = 10;
    private static final int FUNCTION = 12;
    private static final int VARIABLE = 13;
    private static final int CONSTANT = 14;
    private static final int ENUM_MEMBER = 22;

    private final TextLines lines;

    private Outline(TextLines lines) {
        this.lines = lines;
    }

    static List<Map<String, Object>> symbols(Module module, TextLines lines) {
        Outline outline = new Outline(lines);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Decl decl : module.decls) {
            Map<String, Object> symbol = outline.decl(decl);
            if (symbol != null) {
                out.add(symbol);
            }
        }
        for (Stmt stmt : module.topStatements) {
            if (stmt instanceof Stmt.VarDecl varDecl) {
                out.add(outline.symbol(varDecl.name, varDecl.mutable ? VARIABLE : CONSTANT,
                        varDecl.typeRef == null ? null : varDecl.typeRef.display(),
                        varDecl.span, varDecl.nameSpan, List.of()));
            }
        }
        out.sort(Comparator.comparingInt(Outline::startLine));
        return out;
    }

    private Map<String, Object> decl(Decl decl) {
        if (decl instanceof Decl.Func func) {
            return symbol(func.name, FUNCTION, Describe.functionLine(func), func.span, func.nameSpan, List.of());
        }
        if (decl instanceof Decl.ClassDecl classDecl) {
            List<Map<String, Object>> children = new ArrayList<>();
            for (Decl.Field field : classDecl.fields) {
                children.add(symbol(field.name, FIELD, field.typeRef.display(), field.span, field.nameSpan,
                        List.of()));
            }
            for (Decl.Func method : classDecl.methods) {
                children.add(symbol(method.name, METHOD, Describe.functionLine(method), method.span,
                        method.nameSpan, List.of()));
            }
            children.sort(Comparator.comparingInt(Outline::startLine));
            return symbol(classDecl.name, CLASS, null, classDecl.span, classDecl.nameSpan, children);
        }
        if (decl instanceof Decl.EnumDecl enumDecl) {
            List<Map<String, Object>> children = new ArrayList<>();
            for (int i = 0; i < enumDecl.cases.size() && i < enumDecl.caseSpans.size(); i++) {
                Span span = enumDecl.caseSpans.get(i);
                children.add(symbol(enumDecl.cases.get(i), ENUM_MEMBER, null, span, span, List.of()));
            }
            return symbol(enumDecl.name, ENUM, null, enumDecl.span, enumDecl.nameSpan, children);
        }
        if (decl instanceof Decl.VariantDecl variantDecl) {
            List<Map<String, Object>> children = new ArrayList<>();
            for (Decl.VariantCase variantCase : variantDecl.cases) {
                List<Map<String, Object>> fields = new ArrayList<>();
                for (Decl.Field field : variantCase.fields) {
                    fields.add(symbol(field.name, FIELD, field.typeRef.display(), field.span, field.nameSpan,
                            List.of()));
                }
                children.add(symbol(variantCase.name, ENUM_MEMBER, null, variantCase.span, variantCase.nameSpan,
                        fields));
            }
            return symbol(variantDecl.name, ENUM, "variant", variantDecl.span, variantDecl.nameSpan, children);
        }
        return null;
    }

    private Map<String, Object> symbol(String name, int kind, String detail, Span span, Span nameSpan,
                                       List<Map<String, Object>> children) {
        Span full = span != null ? span : nameSpan;
        Span selection = nameSpan != null ? nameSpan : full;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", name);
        if (detail != null) {
            out.put("detail", detail);
        }
        out.put("kind", kind);
        out.put("range", lines.range(full));
        out.put("selectionRange", lines.range(selection));
        if (!children.isEmpty()) {
            out.put("children", children);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static int startLine(Map<String, Object> symbol) {
        Map<String, Object> range = (Map<String, Object>) symbol.get("range");
        Map<String, Object> start = (Map<String, Object>) range.get("start");
        return (Integer) start.get("line");
    }
}
