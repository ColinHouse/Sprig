package sprig.compiler.types;

import java.util.List;
import java.util.StringJoiner;

/** Type of a lambda or other first-class function value (not writable in source). */
public final class FunctionType implements Type {
    public final List<Type> params;
    public final Type result;

    public FunctionType(List<Type> params, Type result) {
        this.params = List.copyOf(params);
        this.result = result;
    }

    @Override
    public String display() {
        StringJoiner joiner = new StringJoiner(", ", "(", ")");
        for (Type param : params) {
            joiner.add(param.display());
        }
        return joiner + " -> " + result.display();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FunctionType that
                && params.equals(that.params) && result.equals(that.result);
    }

    @Override
    public int hashCode() {
        return 31 * params.hashCode() + result.hashCode();
    }

    @Override
    public String toString() {
        return display();
    }
}
