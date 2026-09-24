package sprig.compiler.types;

import sprig.compiler.ast.Decl;

/** Nominal type of a sealed {@code variant}. */
public final class VariantType implements Type {
    public final Decl.VariantDecl decl;

    public VariantType(Decl.VariantDecl decl) {
        this.decl = decl;
    }

    @Override
    public String display() {
        return decl.name;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VariantType that && decl == that.decl;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(decl);
    }

    @Override
    public String toString() {
        return display();
    }
}
