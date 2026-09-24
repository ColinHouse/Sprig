package sprig.compiler.types;

import sprig.compiler.ast.Decl;

/** Static type of one concrete variant case, e.g. {@code Expr.Literal}. */
public final class VariantCaseType implements Type {
    public final Decl.VariantDecl variant;
    public final Decl.VariantCase variantCase;

    public VariantCaseType(Decl.VariantDecl variant, Decl.VariantCase variantCase) {
        this.variant = variant;
        this.variantCase = variantCase;
    }

    @Override
    public String display() {
        return variant.name + "." + variantCase.name;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VariantCaseType that
                && variant == that.variant && variantCase == that.variantCase;
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(variant) + System.identityHashCode(variantCase);
    }

    @Override
    public String toString() {
        return display();
    }
}
