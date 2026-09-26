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
            if (javaTarget.clazz == Object.class || javaTarget.clazz == java.io.Serializable.class
                    || javaTarget.clazz == Comparable.class) {
                return isReference(source) || source == NativeType.INT
                        || source == NativeType.FLOAT || source == NativeType.BOOL;
            }
            if (source instanceof JavaType javaSource) {
                return javaTarget.clazz.isAssignableFrom(javaSource.clazz);
            }
            return false;
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

    /** Error values: the built-in Error type or an imported Throwable class. */
    public static boolean isErrorType(Type type) {
        if (type == NativeType.ERROR) {
            return true;
        }
        if (type instanceof JavaType javaType) {
            return Throwable.class.isAssignableFrom(javaType.clazz);
        }
        return false;
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
