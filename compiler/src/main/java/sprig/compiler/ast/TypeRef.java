package sprig.compiler.ast;

import java.util.ArrayList;
import java.util.List;
import sprig.compiler.diag.Span;
import sprig.compiler.types.Type;

/**
 * A written type such as {@code Int}, {@code List[Expr]?} or {@code Date}
 * (an import alias). Resolved to a {@link Type} by the checker.
 */
public final class TypeRef extends Node {
    public final List<String> parts;
    public final List<TypeRef> args;
    public final boolean nullable;
    public Type resolved;
    /** The last name segment; null for source callable types. */
    public Span nameSpan;
    /** Non-null for source callable types; args then hold the parameter types. */
    public final TypeRef functionResult;

    public TypeRef(List<String> parts, List<TypeRef> args, boolean nullable) {
        this(parts, args, nullable, null);
    }

    public TypeRef(List<String> parts, List<TypeRef> args, boolean nullable, TypeRef functionResult) {
        this.functionResult = functionResult;
        this.parts = List.copyOf(parts);
        this.args = List.copyOf(args);
        this.nullable = nullable;
    }

    public String display() {
        if (functionResult != null) {
            String text = "fn(" + String.join(", ", args.stream().map(TypeRef::display).toList())
                    + ") -> " + functionResult.display();
            return nullable ? "(" + text + ")?" : text;
        }
        StringBuilder sb = new StringBuilder(String.join(".", parts));
        if (!args.isEmpty()) {
            sb.append('[');
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(args.get(i).display());
            }
            sb.append(']');
        }
        if (nullable) {
            sb.append('?');
        }
        return sb.toString();
    }

    /** Last name segment, e.g. {@code List} for {@code sprig.List}. */
    public String simpleName() {
        return parts.get(parts.size() - 1);
    }

    public List<Type> argTypes() {
        List<Type> out = new ArrayList<>();
        for (TypeRef arg : args) {
            out.add(arg.resolved);
        }
        return out;
    }
}
