package sprig.compiler.types;

import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

/** A type imported from the JVM/JDK world (e.g. {@code java.time.LocalDate}). */
public final class JavaType implements Type {
    public final Class<?> clazz;
    public final List<Type> args;
    public final boolean platformNullable;

    public JavaType(Class<?> clazz) {
        this(clazz, List.of(), false);
    }

    public JavaType(Class<?> clazz, List<Type> args, boolean platformNullable) {
        this.clazz = clazz;
        this.args = List.copyOf(args);
        this.platformNullable = platformNullable;
    }

    @Override
    public String display() {
        if (clazz.isArray()) {
            return new JavaType(clazz.getComponentType()).display() + "[]";
        }
        String name = clazz.getSimpleName();
        if (args.isEmpty()) {
            return name + (platformNullable ? "?" : "");
        }
        StringJoiner joiner = new StringJoiner(", ", name + "[", "]");
        for (Type arg : args) {
            joiner.add(arg.display());
        }
        return joiner + (platformNullable ? "?" : "");
    }

    @Override
    public boolean isNullable() {
        return platformNullable;
    }

    @Override
    public Type nonNull() {
        return platformNullable ? new JavaType(clazz, args, false) : this;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof JavaType that)) {
            return false;
        }
        return clazz.equals(that.clazz) && args.equals(that.args) && platformNullable == that.platformNullable;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * clazz.hashCode() + args.hashCode()) + Arrays.hashCode(new boolean[]{platformNullable});
    }

    @Override
    public String toString() {
        return display();
    }
}
