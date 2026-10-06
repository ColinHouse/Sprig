package sprig.compiler.lsp;

import java.util.ArrayList;
import java.util.List;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Stmt;
import sprig.compiler.ast.TypeRef;
import sprig.compiler.lsp.SymbolIndex.Kind;
import sprig.compiler.lsp.SymbolIndex.Occurrence;
import sprig.compiler.lsp.SymbolIndex.Target;
import sprig.compiler.types.Type;

/** Sprig-syntax descriptions of declarations for hover and completion details. */
final class Describe {
    private static final int MAX_MEMBERS = 30;

    private Describe() {
    }

    /** Markdown hover text for a name occurrence. */
    static String hover(Analysis analysis, Occurrence occurrence) {
        if (occurrence.info != null) {
            String code = occurrence.info.code();
            if (occurrence.resultType != null && !occurrence.resultType.isError() && code.endsWith("(...)")) {
                code = code + " -> " + occurrence.resultType.display();
            }
            return block(code) + "\n\n" + occurrence.info.note();
        }
        Target target = occurrence.target;
        StringBuilder out = new StringBuilder(block(signature(target)));
        Type declared = target.symbol == null ? null : target.symbol.type;
        if (!occurrence.declaration && occurrence.type != null && !occurrence.type.isError()
                && declared != null && !declared.isError()
                && !occurrence.type.display().equals(declared.display())
                && (target.kind == Kind.LOCAL || target.kind == Kind.PARAM || target.kind == Kind.TOP_VAR)) {
            out.append("\n\nHere: `").append(occurrence.type.display()).append('`');
        }
        String note = note(target);
        if (note != null) {
            out.append("\n\n").append(note);
        }
        String comment = docComment(analysis.text(target.path), target);
        if (comment != null) {
            out.append("\n\n").append(comment);
        }
        return out.toString();
    }

    static String signature(Target target) {
        Object node = target.node;
        return switch (target.kind) {
            case FUNCTION, METHOD -> function((Decl.Func) node);
            case CLASS -> classDecl((Decl.ClassDecl) node);
            case ENUM -> enumDecl((Decl.EnumDecl) node);
            case VARIANT -> variant((Decl.VariantDecl) node);
            case VARIANT_CASE -> target.container + "." + variantCase((Decl.VariantCase) node);
            case ENUM_CASE -> target.container + "." + target.name;
            case FIELD -> field((Decl.Field) node);
            case PAYLOAD_FIELD -> target.name + ": " + ((Decl.Field) node).typeRef.display();
            case PARAM -> target.name + typeSuffix(target, ((Decl.Param) node).typeRef);
            case TOP_VAR -> binding((Stmt.VarDecl) node, target);
            case LOCAL -> local(target);
            case MODULE -> "module " + target.name;
        };
    }

    /** One-line detail for completion items. */
    static String detail(Target target) {
        return switch (target.kind) {
            case FUNCTION, METHOD -> functionLine((Decl.Func) target.node);
            case CLASS -> "class " + target.name;
            case ENUM -> "enum " + target.name;
            case VARIANT -> "variant " + target.name;
            default -> signature(target);
        };
    }

    private static String note(Target target) {
        return switch (target.kind) {
            case METHOD -> "Method of `" + target.container + "`.";
            case FIELD -> "Field of `" + target.container + "`.";
            case PAYLOAD_FIELD -> "Payload field of `" + target.container + "`.";
            case PARAM -> target.container == null || target.container.equals("lambda")
                    ? "Lambda parameter." : "Parameter of `" + target.container + "`.";
            case LOCAL -> localNote(target);
            case TOP_VAR -> "Module variable.";
            case MODULE -> "`" + target.path + "`";
            case VARIANT_CASE, ENUM_CASE -> "Case of `" + target.container + "`.";
            default -> null;
        };
    }

    private static String localNote(Target target) {
        String kind = target.node instanceof Stmt.ForStmt ? "Loop variable"
                : target.node instanceof Stmt.Try.CatchClause ? "Caught error"
                : target.node instanceof Stmt.Match.Branch ? "Match binding"
                : "Local variable";
        return target.container == null ? kind + "." : kind + " in `" + target.container + "`.";
    }

    static String function(Decl.Func func) {
        String line = functionLine(func);
        if (func.typeParams.isEmpty() || func.owner != null) {
            return line;
        }
        return "generic " + String.join(", ", func.typeParams) + ":\n    " + line;
    }

    static String functionLine(Decl.Func func) {
        StringBuilder out = new StringBuilder("func ").append(func.name).append('(');
        for (int i = 0; i < func.params.size(); i++) {
            Decl.Param param = func.params.get(i);
            if (i > 0) {
                out.append(", ");
            }
            out.append(param.name).append(": ").append(param.typeRef.display());
        }
        out.append(')');
        if (func.returnTypeRef != null) {
            out.append(" -> ").append(func.returnTypeRef.display());
        }
        if (!func.throwsRefs.isEmpty()) {
            List<String> errors = new ArrayList<>();
            for (TypeRef ref : func.throwsRefs) {
                errors.add(ref.display());
            }
            out.append(" throws ").append(String.join(", ", errors));
        }
        return out.toString();
    }

    private static String classDecl(Decl.ClassDecl decl) {
        String indent = decl.typeParams.isEmpty() ? "" : "    ";
        StringBuilder out = new StringBuilder();
        if (!decl.typeParams.isEmpty()) {
            out.append("generic ").append(String.join(", ", decl.typeParams)).append(":\n");
        }
        out.append(indent).append("class ").append(decl.name).append(':');
        int shown = 0;
        for (Decl.Field field : decl.fields) {
            if (shown++ == MAX_MEMBERS) {
                out.append('\n').append(indent).append("    # ...");
                break;
            }
            out.append('\n').append(indent).append("    ").append(field(field));
        }
        if (decl.fields.isEmpty()) {
            out.append('\n').append(indent).append("    pass");
        }
        return out.toString();
    }

    private static String enumDecl(Decl.EnumDecl decl) {
        StringBuilder out = new StringBuilder("enum ").append(decl.name).append(':');
        for (int i = 0; i < decl.cases.size(); i++) {
            if (i == MAX_MEMBERS) {
                out.append("\n    # ...");
                break;
            }
            out.append("\n    ").append(decl.cases.get(i));
        }
        return out.toString();
    }

    private static String variant(Decl.VariantDecl decl) {
        String indent = decl.typeParams.isEmpty() ? "" : "    ";
        StringBuilder out = new StringBuilder();
        if (!decl.typeParams.isEmpty()) {
            out.append("generic ").append(String.join(", ", decl.typeParams)).append(":\n");
        }
        out.append(indent).append("variant ").append(decl.name).append(':');
        for (int i = 0; i < decl.cases.size(); i++) {
            if (i == MAX_MEMBERS) {
                out.append('\n').append(indent).append("    # ...");
                break;
            }
            out.append('\n').append(indent).append("    ").append(variantCase(decl.cases.get(i)));
        }
        return out.toString();
    }

    static String variantCase(Decl.VariantCase variantCase) {
        if (variantCase.fields.isEmpty()) {
            return variantCase.name;
        }
        List<String> fields = new ArrayList<>();
        for (Decl.Field field : variantCase.fields) {
            fields.add(field.name + ": " + field.typeRef.display());
        }
        return variantCase.name + "(" + String.join(", ", fields) + ")";
    }

    static String field(Decl.Field field) {
        return (field.mutable ? "var " : "let ") + field.name + ": " + field.typeRef.display();
    }

    private static String binding(Stmt.VarDecl varDecl, Target target) {
        return (varDecl.mutable ? "var " : "let ") + target.name + typeSuffix(target, varDecl.typeRef);
    }

    private static String local(Target target) {
        if (target.node instanceof Stmt.VarDecl varDecl) {
            return binding(varDecl, target);
        }
        if (target.node instanceof Stmt.Try.CatchClause clause) {
            return target.name + typeSuffix(target, clause.typeRef);
        }
        return target.name + typeSuffix(target, null);
    }

    /** The checker's type when it ran, else the written annotation, else nothing. */
    private static String typeSuffix(Target target, TypeRef written) {
        Type type = target.symbol == null ? null : target.symbol.type;
        if (type != null && !type.isError()) {
            return ": " + type.display();
        }
        return written == null ? "" : ": " + written.display();
    }

    /** Comment lines directly above a declaration, without their '#'. */
    static String docComment(TextLines text, Target target) {
        if (target.span == null || target.kind == Kind.LOCAL || target.kind == Kind.PARAM
                || target.kind == Kind.MODULE) {
            return null;
        }
        return sprig.compiler.tooling.DocComments.above(text::line, target.span.startLine);
    }

    static String block(String code) {
        return "```sprig\n" + code + "\n```";
    }
}
