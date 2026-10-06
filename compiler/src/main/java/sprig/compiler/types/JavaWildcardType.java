package sprig.compiler.types;

import java.util.Objects;

/**
 * A Java wildcard type argument as it stands in an imported signature:
 * {@code ? extends Number}, {@code ? super Integer} or a bare {@code ?}. It
 * only ever appears inside the arguments of a {@link JavaType}; where a value
 * of that argument type is read it stands for its upper bound, and where one
 * is written it stands for its lower bound (none for {@code ? extends}).
 */
public final class JavaWildcardType implements Type {
    /** The upper bound, or null for Object. */
    public final Type upper;
    /** The lower bound of {@code ? super X}, or null. */
    public final Type lower;

    public JavaWildcardType(Type upper, Type lower) {
        this.upper = upper;
        this.lower = lower;
    }

    /** What a read of this argument yields: the upper bound, else Object. */
    public Type readAs() {
        return upper != null ? upper : new JavaType(Object.class);
    }

    @Override
    public String display() {
        if (lower != null) {
            return "? super " + lower.display();
        }
        return upper != null ? "? extends " + upper.display() : "?";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JavaWildcardType that
                && Objects.equals(upper, that.upper) && Objects.equals(lower, that.lower);
    }

    @Override
    public int hashCode() {
        return Objects.hash(upper, lower);
    }

    @Override
    public String toString() {
        return display();
    }
}
