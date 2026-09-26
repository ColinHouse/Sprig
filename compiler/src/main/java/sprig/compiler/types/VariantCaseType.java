package sprig.compiler.types;

import java.util.List;
import java.util.StringJoiner;
import sprig.compiler.ast.Decl;

/**
 * Static type of one concrete variant case, e.g. {@code Expr.Literal} or the
 * instantiated {@code Option[Int].Some}.
 */
public final class VariantCaseType implements Type {
    public final Decl.VariantDecl variant;
    /** Type arguments of the enclosing variant instantiation; empty when plain. */
    public final List<Type> variantArgs;
    public final Decl.VariantCase variantCase;

    public VariantCaseType(Decl.VariantDecl variant, Decl.VariantCase variantCase) {
        this(variant, List.of(), variantCase);
    }

    public VariantCaseType(Decl.VariantDecl variant, List<Type> variantArgs,
                           Decl.VariantCase variantCase) {
        this.variant = variant;
        this.variantArgs = List.copyOf(variantArgs);
        this.variantCase = variantCase;
    }

    @Override
    public String display() {
        String prefix = variant.name;
        if (!variantArgs.isEmpty()) {
            StringJoiner joiner = new StringJoiner(", ", "[", "]");
            for (Type arg : variantArgs) {
                joiner.add(arg.display());
            }
            prefix += joiner.toString();
        }
        return prefix + "." + variantCase.name;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VariantCaseType that
                && variant == that.variant && variantCase == that.variantCase
                && variantArgs.equals(that.variantArgs);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * System.identityHashCode(variant) + variantArgs.hashCode())
                + System.identityHashCode(variantCase);
    }

    @Override
    public String toString() {
        return display();
    }
}
