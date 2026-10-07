package sprig.compiler.sem;

import sprig.compiler.types.FunctionType;
import sprig.compiler.types.JavaType;
import sprig.compiler.types.ListType;
import sprig.compiler.types.MapType;
import sprig.compiler.types.NativeType;
import sprig.compiler.types.NullableType;
import sprig.compiler.types.Type;
import sprig.compiler.types.VariantCaseType;
import sprig.compiler.types.VariantType;

/** Shared type predicates used by the checker and code generator. */
public final class Semantics {
    private Semantics() {
    }

    public static boolean isAssignable(Type target, Type source) {
        if (target == null || source == null) {
            return true;
        }
        if (target == NativeType.ERROR || source == NativeType.ERROR) {
            return true;
        }
        if (target.equals(source)) {
            return true;
        }
        // A MutableList is a List that can also be changed, so it goes where a
        // List is expected: the callee sees the same list read-only (no copy; a
        // later change through the mutable name is visible, toList() snapshots).
        // Element types stay invariant, and a List never becomes a MutableList.
        if (target instanceof ListType targetList && source instanceof ListType sourceList
                && !targetList.mutable && sourceList.mutable && targetList.element.equals(sourceList.element)) {
            return true;
        }
        if (target instanceof MapType targetMap && source instanceof MapType sourceMap
                && !targetMap.mutable && sourceMap.mutable && targetMap.key.equals(sourceMap.key)
                && targetMap.value.equals(sourceMap.value)) {
            return true;
        }
        if (target instanceof FunctionType targetFunction && source instanceof FunctionType sourceFunction) {
            // Parameters and results are invariant. A value that throws less
            // than the target declares is accepted; never the other way round.
            return targetFunction.params.equals(sourceFunction.params)
                    && targetFunction.result.equals(sourceFunction.result)
                    && targetFunction.throwsTypes.containsAll(sourceFunction.throwsTypes);
        }
        if ((target == NativeType.INT && source == NativeType.INT32)
                || (target == NativeType.FLOAT && source == NativeType.FLOAT32)) {
            return true;
        }
        if (source == NativeType.NULL) {
            return target.isNullable();
        }
        if (target instanceof NullableType nullable) {
            return isAssignable(nullable.inner, source.isNullable() ? source.nonNull() : source);
        }
        if (source.isNullable()) {
            return false;
        }
        if (target instanceof VariantType variant && source instanceof VariantCaseType caseType) {
            return variant.decl == caseType.variant && variant.args.equals(caseType.variantArgs);
        }
        if (target instanceof JavaType javaTarget) {
            if (!javaTarget.args.isEmpty()) {
                // Concrete generic targets never accept blanket references;
                // native scalars and Sprig collections project through the
                // same hierarchy as Java values.
                return JavaTypes.javaArgumentCompatible(javaTarget, source);
            }
            if (javaTarget.clazz == Object.class || javaTarget.clazz == java.io.Serializable.class
                    || javaTarget.clazz == Comparable.class) {
                return isReference(source) || source == NativeType.INT
                        || source == NativeType.FLOAT || source == NativeType.BOOL;
            }
            if (source instanceof JavaType javaSource) {
                return javaTarget.clazz.isAssignableFrom(javaSource.clazz);
            }
            if (source instanceof ListType list) {
                if (javaTarget.clazz == sprig.runtime.SprigList.class) {
                    return true;
                }
                if (javaTarget.clazz == sprig.runtime.SprigMutableList.class) {
                    return list.mutable;
                }
                return false;
            }
            if (source instanceof MapType map) {
                if (javaTarget.clazz == sprig.runtime.SprigMap.class) {
                    return true;
                }
                if (javaTarget.clazz == sprig.runtime.SprigMutableMap.class) {
                    return map.mutable;
                }
                return false;
            }
            if (source instanceof sprig.compiler.types.ClassType classSource) {
                if (classSource.decl.superclass != null
                        && javaTarget.clazz.isAssignableFrom(classSource.decl.superclass)) {
                    return true; // the class extends the Java class or one of its ancestors
                }
                if (javaTarget.clazz.isInterface()) {
                    for (Class<?> declared : classSource.decl.conformedInterfaces) {
                        if (javaTarget.clazz.isAssignableFrom(declared)) {
                            return true; // declared foreign conformance conversion
                        }
                    }
                }
            }
            return false;
        }
        if (target instanceof ListType list && source instanceof JavaType javaSource) {
            Class<?> expected = list.mutable ? sprig.runtime.SprigMutableList.class : sprig.runtime.SprigList.class;
            if (javaSource.clazz == expected) {
                // A raw SprigList is erased evidence and never becomes List[T].
                return javaSource.args.equals(java.util.List.of(list.element));
            }
        }
        if (target instanceof MapType map && source instanceof JavaType javaSource) {
            Class<?> expected = map.mutable ? sprig.runtime.SprigMutableMap.class : sprig.runtime.SprigMap.class;
            if (javaSource.clazz == expected) {
                return javaSource.args.equals(java.util.List.of(map.key, map.value));
            }
        }
        return false;
    }

    public static boolean isReference(Type type) {
        if (type instanceof NullableType nullable) {
            return isReference(nullable.inner);
        }
        return type instanceof JavaType || type instanceof ListType || type instanceof MapType
                || type instanceof VariantType || type instanceof VariantCaseType
                || type instanceof sprig.compiler.types.ClassType
                || type instanceof sprig.compiler.types.EnumType
                || type == NativeType.STRING || type == NativeType.DECIMAL || type == NativeType.BIGINT;
    }

    /**
     * Error values: the built-in Error type, an imported Throwable class, or a
     * Sprig class that extends Error through {@code conform C to Error(message)}
     * (an error class: its generated class extends sprig.runtime.SprigError).
     */
    public static boolean isErrorType(Type type) {
        if (type == NativeType.ERROR) {
            return true;
        }
        if (type instanceof JavaType javaType) {
            return Throwable.class.isAssignableFrom(javaType.clazz);
        }
        return isErrorClass(type);
    }

    /** A Sprig class whose generated class extends a Throwable, declared with {@code conform C to Error(...)}. */
    public static boolean isErrorClass(Type type) {
        return type instanceof sprig.compiler.types.ClassType classType
                && classType.decl.superclass != null
                && Throwable.class.isAssignableFrom(classType.decl.superclass);
    }

    /** The built-in Error type (sprig.runtime.SprigError), the only error a callable may declare. */
    public static boolean isSprigError(Type type) {
        return type instanceof JavaType javaType && javaType.clazz == sprig.runtime.SprigError.class;
    }

    /** JVM-checked exceptions must be declared or caught. */
    public static boolean isJvmChecked(Type type) {
        if (type instanceof JavaType javaType) {
            return Throwable.class.isAssignableFrom(javaType.clazz)
                    && !RuntimeException.class.isAssignableFrom(javaType.clazz)
                    && !Error.class.isAssignableFrom(javaType.clazz);
        }
        if (type instanceof NullableType nullable) {
            return isJvmChecked(nullable.inner);
        }
        return false;
    }

    /** Boxed JVM class used when a Sprig type crosses a generic/interop boundary. */
    public static Class<?> boxedClass(Type type) {
        Type base = type instanceof NullableType nullable ? nullable.inner : type;
        if (base == NativeType.INT) {
            return Long.class;
        }
        if (base == NativeType.INT32) {
            return Integer.class;
        }
        if (base == NativeType.FLOAT) {
            return Double.class;
        }
        if (base == NativeType.FLOAT32) {
            return Float.class;
        }
        if (base == NativeType.DECIMAL) {
            return sprig.runtime.SprigDecimal.class;
        }
        if (base == NativeType.BIGINT) {
            return sprig.runtime.SprigBigInt.class;
        }
        if (base == NativeType.BOOL) {
            return Boolean.class;
        }
        if (base == NativeType.STRING) {
            return String.class;
        }
        if (base instanceof JavaType javaType) {
            return JavaTypes.boxed(javaType.clazz);
        }
        if (base instanceof ListType) {
            return sprig.runtime.SprigList.class;
        }
        if (base instanceof MapType) {
            return sprig.runtime.SprigMap.class;
        }
        return Object.class;
    }

    public static FunctionType functionType(Type type) {
        return type instanceof FunctionType functionType ? functionType : null;
    }

    public static boolean isStringLike(Type type) {
        return type == NativeType.STRING;
    }
}
