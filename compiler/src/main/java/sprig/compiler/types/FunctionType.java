package sprig.compiler.types;

import java.util.List;
import java.util.StringJoiner;

/**
 * Type of a lambda or other first-class function value, written
 * {@code fn(A) -> R} or {@code fn(A) -> R throws Error}. The throws clause is
 * part of the type: a value without one is accepted where one is expected,
 * never the other way round.
 */
public final class FunctionType implements Type {
    public final List<Type> params;
    public final Type result;
    /** Declared recoverable errors of a call through this value; empty for most values. */
    public final List<Type> throwsTypes;

    public FunctionType(List<Type> params, Type result) {
        this(params, result, List.of());
    }

    public FunctionType(List<Type> params, Type result, List<Type> throwsTypes) {
        this.params = List.copyOf(params);
        this.result = result;
        this.throwsTypes = List.copyOf(throwsTypes);
    }

    public boolean throwsAny() {
        return !throwsTypes.isEmpty();
    }

    /** The same signature without a throws clause. */
    public FunctionType withoutThrows() {
        return throwsTypes.isEmpty() ? this : new FunctionType(params, result);
    }

    @Override
    public String display() {
        StringJoiner joiner = new StringJoiner(", ", "fn(", ")");
        for (Type param : params) {
            joiner.add(param.display());
        }
        StringBuilder text = new StringBuilder(joiner.toString()).append(" -> ").append(result.display());
        if (!throwsTypes.isEmpty()) {
            StringJoiner thrown = new StringJoiner(", ", " throws ", "");
            for (Type type : throwsTypes) {
                thrown.add(type instanceof JavaType javaType && javaType.clazz == sprig.runtime.SprigError.class
                        ? "Error" : type.display());
            }
            text.append(thrown);
        }
        return text.toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FunctionType that
                && params.equals(that.params) && result.equals(that.result)
                && throwsTypes.equals(that.throwsTypes);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * params.hashCode() + result.hashCode()) + throwsTypes.hashCode();
    }

    @Override
    public String toString() {
        return display();
    }
}
