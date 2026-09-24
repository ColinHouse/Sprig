package sprig.compiler.types;

/** {@code Map[K,V]} (immutable view) or {@code MutableMap[K,V]}. */
public final class MapType implements Type {
    public final Type key;
    public final Type value;
    public final boolean mutable;

    public MapType(Type key, Type value, boolean mutable) {
        this.key = key;
        this.value = value;
        this.mutable = mutable;
    }

    @Override
    public String display() {
        return (mutable ? "MutableMap[" : "Map[") + key.display() + ", " + value.display() + "]";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MapType that
                && mutable == that.mutable && key.equals(that.key) && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * key.hashCode() + value.hashCode()) + (mutable ? 1 : 0);
    }

    @Override
    public String toString() {
        return display();
    }
}
