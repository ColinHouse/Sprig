package sprig.compiler.types;

import java.util.Objects;

/** {@code T?}: explicit absence. */
public final class NullableType implements Type {
    public final Type inner;

    public NullableType(Type inner) {
        this.inner = inner == NativeType.ERROR ? NativeType.ERROR : inner;
    }

    public static Type of(Type inner) {
        if (inner == NativeType.ERROR || inner.isNullable() || inner == NativeType.NULL) {
            return inner;
        }
        if (inner == NativeType.UNIT) {
            return inner;
        }
        return new NullableType(inner);
    }

    @Override
    public String display() {
        return inner.display() + "?";
    }

    @Override
    public boolean isNullable() {
        return true;
    }

    @Override
    public Type nonNull() {
        return inner;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NullableType that && inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return 31 * inner.hashCode() + 7;
    }

    @Override
    public String toString() {
        return display();
    }
}
