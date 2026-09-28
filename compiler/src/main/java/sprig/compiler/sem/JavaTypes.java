package sprig.compiler.sem;

import sprig.compiler.types.JavaType;
import sprig.compiler.types.NativeType;
import sprig.compiler.types.Type;
import sprig.compiler.types.NullableType;

/** Maps JVM reflection types to Sprig types (the interop boundary). */
public final class JavaTypes {
    private JavaTypes() {
    }

    public static Type map(Class<?> clazz) {
        if (clazz == void.class || clazz == Void.class) {
            return NativeType.UNIT;
        }
        if (clazz == boolean.class || clazz == Boolean.class) {
            return NativeType.BOOL;
        }
        if (clazz == long.class || clazz == Long.class) {
            return NativeType.INT;
        }
        if (clazz == int.class || clazz == Integer.class
                || clazz == short.class || clazz == Short.class
                || clazz == byte.class || clazz == Byte.class) {
            return NativeType.INT32;
        }
        if (clazz == double.class || clazz == Double.class) {
            return NativeType.FLOAT;
        }
        if (clazz == float.class || clazz == Float.class) {
            return NativeType.FLOAT32;
        }
        if (clazz == String.class) {
            return NativeType.STRING;
        }
        if (clazz == char.class || clazz == Character.class) {
            return NativeType.STRING;
        }
        return new JavaType(clazz);
    }

    /** Maps a Java value-producing member. Reference and boxed results may be null. */
    public static Type mapValue(Class<?> clazz) {
        Type mapped = map(clazz);
        if (clazz.isPrimitive() || clazz == void.class) {
            return mapped;
        }
        if (clazz == Boolean.class) return NullableType.of(NativeType.BOOL);
        if (clazz == Long.class) return NullableType.of(NativeType.INT);
        if (clazz == Integer.class || clazz == Short.class || clazz == Byte.class) {
            return NullableType.of(NativeType.INT32);
        }
        if (clazz == Double.class) return NullableType.of(NativeType.FLOAT);
        if (clazz == Float.class) return NullableType.of(NativeType.FLOAT32);
        if (clazz == String.class || clazz == Character.class) return NullableType.of(NativeType.STRING);
        return new JavaType(clazz, java.util.List.of(), true);
    }

    /** Only Sprig-owned Fn0..Fn3 carry a source callable contract. No Java SAM inference. */
    public static boolean isCallableClass(Class<?> clazz) {
        return clazz == sprig.runtime.Fn0.class || clazz == sprig.runtime.Fn1.class
                || clazz == sprig.runtime.Fn2.class || clazz == sprig.runtime.Fn3.class;
    }

    public static Class<?> rawClass(java.lang.reflect.Type type) {
        if (type instanceof Class<?> clazz) return clazz;
        if (type instanceof java.lang.reflect.ParameterizedType p && p.getRawType() instanceof Class<?> clazz)
            return clazz;
        return null;
    }

    /** Concrete invariant boxed signature, or null when unresolved/raw/wildcard/unsupported. */
    public static sprig.compiler.types.FunctionType callable(java.lang.reflect.Type type) {
        if (!(type instanceof java.lang.reflect.ParameterizedType p)
                || !(p.getRawType() instanceof Class<?> raw) || !isCallableClass(raw)) return null;
        java.lang.reflect.Type[] written = p.getActualTypeArguments();
        if (written.length != raw.getTypeParameters().length) return null;
        java.util.List<Type> params = new java.util.ArrayList<>();
        Type result = null;
        for (int i = 0; i < written.length; i++) {
            Type mapped;
            if (written[i] instanceof Class<?> clazz) {
                // Character/Short/Byte need value adapters; an erased Fn cannot perform those implicitly.
                if (clazz.isArray() || clazz.isPrimitive() || isCallableClass(clazz)
                        || clazz == Character.class || clazz == Short.class || clazz == Byte.class)
                    return null;
                mapped = map(clazz);
            } else {
                mapped = callable(written[i]);
                if (mapped == null) return null;
            }
            if (i == written.length - 1) result = mapped;
            else {
                if (mapped == NativeType.UNIT) return null;
                params.add(mapped);
            }
        }
        return new sprig.compiler.types.FunctionType(params, result);
    }

    public static Type mapFormal(java.lang.reflect.Type generic, Class<?> raw) {
        sprig.compiler.types.FunctionType fn = callable(generic);
        return fn != null ? fn : map(raw);
    }

    public static Type mapValue(java.lang.reflect.Type generic, Class<?> raw) {
        sprig.compiler.types.FunctionType fn = callable(generic);
        return fn != null ? NullableType.of(fn) : mapValue(raw);
    }

    /** JVM values whose source representation differs from the mapped Sprig value. */
    public static boolean needsValueAdapter(Class<?> clazz) {
        return clazz == char.class || clazz == Character.class
                || clazz == Short.class || clazz == Byte.class;
    }

    /** Whether a Sprig type can be passed where the JVM expects the given class. */
    public static boolean rawAssignable(Class<?> target, Type source) {
        Class<?> boxed = boxed(target);
        if (source == NativeType.INT) {
            return boxed == Long.class || boxed == Number.class || boxed == Object.class
                    || boxed == Comparable.class || boxed == java.io.Serializable.class;
        }
        if (source == NativeType.INT32) {
            return boxed == Integer.class || target == long.class || boxed == Long.class
                    || boxed == Number.class || boxed == Object.class
                    || boxed == Comparable.class || boxed == java.io.Serializable.class;
        }
        if (source == NativeType.FLOAT) {
            return boxed == Double.class || boxed == Number.class
                    || boxed == Object.class || boxed == Comparable.class || boxed == java.io.Serializable.class;
        }
        if (source == NativeType.FLOAT32) {
            return boxed == Float.class || target == double.class || boxed == Double.class
                    || boxed == Number.class || boxed == Object.class
                    || boxed == Comparable.class || boxed == java.io.Serializable.class;
        }
        if (source == NativeType.DECIMAL) {
            return boxed == sprig.runtime.SprigDecimal.class || boxed == Object.class;
        }
        if (source == NativeType.BIGINT) {
            return boxed == sprig.runtime.SprigBigInt.class || boxed == Object.class;
        }
        if (source == NativeType.BOOL) {
            return boxed == Boolean.class || boxed == Object.class
                    || boxed == Comparable.class || boxed == java.io.Serializable.class;
        }
        if (source == NativeType.STRING) {
            return boxed == String.class
                    || boxed == Object.class || boxed == CharSequence.class
                    || boxed == Comparable.class || boxed == java.io.Serializable.class;
        }
        if (source instanceof JavaType javaType) {
            return boxed.isAssignableFrom(javaType.clazz);
        }
        if (source instanceof sprig.compiler.types.ClassType classSource && target.isInterface()) {
            for (Class<?> declared : classSource.decl.conformedInterfaces) {
                if (target.isAssignableFrom(declared)) {
                    return true; // declared foreign conformance conversion
                }
            }
        }
        return boxed == Object.class;
    }

    public static Class<?> boxed(Class<?> clazz) {
        if (clazz == long.class) {
            return Long.class;
        }
        if (clazz == int.class) {
            return Integer.class;
        }
        if (clazz == short.class) {
            return Short.class;
        }
        if (clazz == byte.class) {
            return Byte.class;
        }
        if (clazz == double.class) {
            return Double.class;
        }
        if (clazz == float.class) {
            return Float.class;
        }
        if (clazz == boolean.class) {
            return Boolean.class;
        }
        if (clazz == char.class) {
            return Character.class;
        }
        return clazz;
    }

    /** JVM class for a Sprig native type usable in generic argument position. */
    public static Class<?> boxedFor(Type type) {
        if (type == NativeType.INT) {
            return Long.class;
        }
        if (type == NativeType.INT32) {
            return Integer.class;
        }
        if (type == NativeType.FLOAT) {
            return Double.class;
        }
        if (type == NativeType.FLOAT32) {
            return Float.class;
        }
        if (type == NativeType.BOOL) {
            return Boolean.class;
        }
        return Object.class;
    }
}
