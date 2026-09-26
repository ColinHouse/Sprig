package sprig.compiler.types;

import sprig.compiler.ast.Decl;

/**
 * A v0.8 generic type parameter inside its declaration block, for example the
 * {@code T} of {@code generic T: class Box}. It is only ever visible while the
 * owning declaration's signatures are resolved or substituted; it never leaks
 * into executable code, where {@link Substitution} replaces it with a concrete
 * type argument.
 */
public final class TypeParameterType implements Type {
    public final Decl owner;
    public final String name;

    public TypeParameterType(Decl owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    @Override
    public String display() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TypeParameterType that
                && owner == that.owner && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(owner) + name.hashCode();
    }

    @Override
    public String toString() {
        return display();
    }
}
