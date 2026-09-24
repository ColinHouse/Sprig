package sprig.compiler.types;

/** {@code List[T]} (immutable view) or {@code MutableList[T]}. */
public final class ListType implements Type {
    public final Type element;
    public final boolean mutable;

    public ListType(Type element, boolean mutable) {
        this.element = element;
        this.mutable = mutable;
    }

    @Override
    public String display() {
        return (mutable ? "MutableList[" : "List[") + element.display() + "]";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ListType that && mutable == that.mutable && element.equals(that.element);
    }

    @Override
    public int hashCode() {
        return 31 * element.hashCode() + (mutable ? 1 : 0);
    }

    @Override
    public String toString() {
        return display();
    }
}
