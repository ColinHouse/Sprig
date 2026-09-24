package sprig.compiler.types;

import sprig.compiler.ast.Decl;

/** Nominal type of a user-declared Sprig class. */
public final class ClassType implements Type {
    public final Decl.ClassDecl decl;

    public ClassType(Decl.ClassDecl decl) {
        this.decl = decl;
    }

    @Override
    public String display() {
        return decl.name;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ClassType that && decl == that.decl;
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
