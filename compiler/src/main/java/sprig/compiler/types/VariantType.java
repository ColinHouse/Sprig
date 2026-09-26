package sprig.compiler.types;

import java.util.List;
import java.util.StringJoiner;
import sprig.compiler.ast.Decl;

/**
 * Nominal type of a sealed {@code variant}. v0.8 generic instantiations carry
 * their type arguments, for example {@code Option[Int]}. Generic types are
 * invariant; there is no variance in this version.
 */
public final class VariantType implements Type {
    public final Decl.VariantDecl decl;
    public final List<Type> args;

    public VariantType(Decl.VariantDecl decl) {
        this(decl, List.of());
    }

    public VariantType(Decl.VariantDecl decl, List<Type> args) {
        this.decl = decl;
        this.args = List.copyOf(args);
    }

    @Override
    public String display() {
        if (args.isEmpty()) {
            return decl.name;
        }
        StringJoiner joiner = new StringJoiner(", ", decl.name + "[", "]");
        for (Type arg : args) {
            joiner.add(arg.display());
        }
        return joiner.toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VariantType that && decl == that.decl && args.equals(that.args);
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(decl) + args.hashCode();
    }

    @Override
    public String toString() {
        return display();
    }
}
