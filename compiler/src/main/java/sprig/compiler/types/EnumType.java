package sprig.compiler.types;

import sprig.compiler.ast.Decl;

/** Nominal type of a user-declared {@code enum}. */
public final class EnumType implements Type {
    public final Decl.EnumDecl decl;

    public EnumType(Decl.EnumDecl decl) {
        this.decl = decl;
    }

    @Override
    public String display() {
        return decl.name;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EnumType that && decl == that.decl;
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
