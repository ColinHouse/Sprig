package sprig.compiler.types;

import java.util.List;
import java.util.StringJoiner;
import sprig.compiler.ast.Decl;

/**
 * Nominal type of a user-declared Sprig class. v0.8 generic instantiations
 * carry their type arguments, for example {@code Box[Int]}; a plain class has
 * an empty argument list. Generic types are invariant.
 */
public final class ClassType implements Type {
    public final Decl.ClassDecl decl;
    public final List<Type> args;

    public ClassType(Decl.ClassDecl decl) {
        this(decl, List.of());
    }

    public ClassType(Decl.ClassDecl decl, List<Type> args) {
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
        return other instanceof ClassType that && decl == that.decl && args.equals(that.args);
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
