package sprig.compiler.ast;

import java.util.ArrayList;
import java.util.List;
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

    public TypeRef(List<String> parts, List<TypeRef> args, boolean nullable) {
        this.parts = List.copyOf(parts);
        this.args = List.copyOf(args);
        this.nullable = nullable;
    }

    public String display() {
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
