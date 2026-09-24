package sprig.compiler.types;

/** Built-in non-nullable value types. */
public enum NativeType implements Type {
    INT("Int"),
    INT32("Int32"),
    FLOAT("Float"),
    FLOAT32("Float32"),
    DECIMAL("Decimal"),
    BIGINT("BigInt"),
    BOOL("Bool"),
    STRING("String"),
    UNIT("Unit"),
    /** Type of the {@code null} literal; only assignable to nullable types. */
    NULL("null"),
    /** Poison type after a reported error; silences cascades. */
    ERROR("<error>");

    private final String display;

    NativeType(String display) {
        this.display = display;
    }

    @Override
    public String display() {
        return display;
    }
}
