package sprig.compiler.types;

/** Static type of a Sprig expression or declaration. */
public interface Type {
    /** Human-readable Sprig type name used in diagnostics. */
    String display();

    default boolean isNullable() {
        return false;
    }

    /** The non-nullable version of this type. */
    default Type nonNull() {
        return this;
    }

    default boolean isError() {
        return this == NativeType.ERROR;
    }

    default boolean isUnit() {
        return this == NativeType.UNIT;
    }
}
