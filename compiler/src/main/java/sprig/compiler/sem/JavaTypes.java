package sprig.compiler.sem;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import sprig.compiler.types.JavaType;
import sprig.compiler.types.ListType;
import sprig.compiler.types.MapType;
import sprig.compiler.types.NativeType;
import sprig.compiler.types.Type;
import sprig.compiler.types.NullableType;

/** Maps JVM reflection types to Sprig types (the interop boundary). */
public final class JavaTypes {
    private JavaTypes() {
    }

    /** Java type-variable bindings established by a concrete receiver or explicit arguments. */
    public static final Map<TypeVariable<?>, Type> NO_BINDINGS = Map.of();

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

    /**
     * Maps a parameter/field formal with receiver and explicit type bindings.
     * Falls back to the raw boundary when the generic shape is outside the
     * concrete profile (wildcards, generic arrays, unresolved variables).
     */
    public static Type mapFormal(java.lang.reflect.Type generic, Class<?> raw,
            Map<TypeVariable<?>, Type> bindings) {
        Type mapped = mapBoundary(generic, bindings, false);
        return mapped != null ? mapped : mapFormal(generic, raw);
    }

    /**
     * Maps a value-producing member with receiver and explicit type bindings.
     * Reference and boxed results stay conservatively nullable.
     */
    public static Type mapValue(java.lang.reflect.Type generic, Class<?> raw,
            Map<TypeVariable<?>, Type> bindings) {
        Type mapped = mapBoundary(generic, bindings, true);
        return mapped != null ? mapped : mapValue(generic, raw);
    }

    /**
     * Recursive concrete-generic mapping. Returns {@code null} when the shape
     * is outside the supported profile so callers can keep the raw boundary.
     */
    private static Type mapBoundary(java.lang.reflect.Type generic,
            Map<TypeVariable<?>, Type> bindings, boolean valuePosition) {
        if (generic instanceof Class<?> clazz) {
            return valuePosition ? mapValue(clazz) : map(clazz);
        }
        if (generic instanceof TypeVariable<?> variable) {
            Type bound = bindings.get(variable);
            if (bound == null) return null;
            return valuePosition ? NullableType.of(bound) : bound;
        }
        if (generic instanceof ParameterizedType applied
                && applied.getRawType() instanceof Class<?> rawClass) {
            java.lang.reflect.Type[] written = applied.getActualTypeArguments();
            List<Type> args = new ArrayList<>(written.length);
            for (java.lang.reflect.Type argument : written) {
                Type mapped = mapArgument(argument, bindings);
                if (mapped == null) return null;
                args.add(mapped);
            }
            if (isCallableClass(rawClass)) {
                sprig.compiler.types.FunctionType fn = callable(generic);
                return fn == null ? null : valuePosition ? NullableType.of(fn) : fn;
            }
            if (rawClass == sprig.runtime.SprigList.class && args.size() == 1) {
                Type list = new ListType(args.get(0), false);
                return valuePosition ? NullableType.of(list) : list;
            }
            if (rawClass == sprig.runtime.SprigMutableList.class && args.size() == 1) {
                Type list = new ListType(args.get(0), true);
                return valuePosition ? NullableType.of(list) : list;
            }
            if (rawClass == sprig.runtime.SprigMap.class && args.size() == 2) {
                Type map = new MapType(args.get(0), args.get(1), false);
                return valuePosition ? NullableType.of(map) : map;
            }
            if (rawClass == sprig.runtime.SprigMutableMap.class && args.size() == 2) {
                Type map = new MapType(args.get(0), args.get(1), true);
                return valuePosition ? NullableType.of(map) : map;
            }
            return new JavaType(rawClass, args, valuePosition);
        }
        return null;
    }

    /** Maps one concrete generic argument; no nullability, no wildcard capture. */
    public static Type mapArgument(java.lang.reflect.Type generic,
            Map<TypeVariable<?>, Type> bindings) {
        if (generic instanceof Class<?> clazz) {
            // Short/Byte/Character need an element adapter (as Fn slots already
            // document); mapping them to Int32/String would cast wrongly.
            if (clazz == Short.class || clazz == Byte.class || clazz == Character.class) {
                return null;
            }
            return map(clazz);
        }
        if (generic instanceof TypeVariable<?> variable) {
            return bindings.get(variable);
        }
        if (generic instanceof ParameterizedType applied
                && applied.getRawType() instanceof Class<?> rawClass) {
            java.lang.reflect.Type[] written = applied.getActualTypeArguments();
            List<Type> args = new ArrayList<>(written.length);
            for (java.lang.reflect.Type argument : written) {
                Type mapped = mapArgument(argument, bindings);
                if (mapped == null) return null;
                args.add(mapped);
            }
            if (rawClass == sprig.runtime.SprigList.class && args.size() == 1) {
                return new ListType(args.get(0), false);
            }
            if (rawClass == sprig.runtime.SprigMutableList.class && args.size() == 1) {
                return new ListType(args.get(0), true);
            }
            if (rawClass == sprig.runtime.SprigMap.class && args.size() == 2) {
                return new MapType(args.get(0), args.get(1), false);
            }
            if (rawClass == sprig.runtime.SprigMutableMap.class && args.size() == 2) {
                return new MapType(args.get(0), args.get(1), true);
            }
            return new JavaType(rawClass, args, false);
        }
        return null;
    }

    /**
     * Type-variable bindings for every class in a receiver's generic
     * hierarchy. The receiver entry binds its own variables to the written
     * arguments; inherited declarations resolve through supertypes, e.g.
     * {@code ArrayList[String]} binds {@code List.E} as well.
     */
    public static Map<Class<?>, Map<TypeVariable<?>, Type>> hierarchyBindings(
            Class<?> receiver, List<Type> arguments) {
        Map<Class<?>, Map<TypeVariable<?>, Type>> byClass = new java.util.IdentityHashMap<>();
        if (receiver == null) {
            return byClass;
        }
        TypeVariable<?>[] variables = receiver.getTypeParameters();
        Map<TypeVariable<?>, Type> root = new java.util.IdentityHashMap<>();
        if (arguments.size() == variables.length) {
            for (int i = 0; i < variables.length; i++) {
                root.put(variables[i], arguments.get(i));
            }
        }
        byClass.put(receiver, root);
        java.util.ArrayDeque<Class<?>> queue = new java.util.ArrayDeque<>();
        queue.add(receiver);
        while (!queue.isEmpty()) {
            Class<?> current = queue.poll();
            Map<TypeVariable<?>, Type> bindings = byClass.get(current);
            List<java.lang.reflect.Type> parents = new ArrayList<>();
            if (current.getGenericSuperclass() != null) {
                parents.add(current.getGenericSuperclass());
            }
            parents.addAll(List.of(current.getGenericInterfaces()));
            for (java.lang.reflect.Type parent : parents) {
                Class<?> raw = rawClass(parent);
                if (raw == null) continue;
                Map<TypeVariable<?>, Type> mapped = new java.util.IdentityHashMap<>();
                TypeVariable<?>[] parentVariables = raw.getTypeParameters();
                if (parent instanceof ParameterizedType applied
                        && applied.getActualTypeArguments().length == parentVariables.length) {
                    for (int i = 0; i < parentVariables.length; i++) {
                        Type bound = substituteArgument(applied.getActualTypeArguments()[i], bindings);
                        if (bound != null) {
                            mapped.put(parentVariables[i], bound);
                        }
                    }
                }
                if (byClass.putIfAbsent(raw, mapped) == null) {
                    queue.add(raw);
                }
            }
        }
        return byClass;
    }

    private static Type substituteArgument(java.lang.reflect.Type generic,
            Map<TypeVariable<?>, Type> bindings) {
        if (generic instanceof TypeVariable<?> variable) {
            return bindings.get(variable);
        }
        return mapArgument(generic, bindings);
    }

    /**
     * Invariant Java generic compatibility. Concrete arguments must project
     * exactly onto the target's type variables through the source hierarchy:
     * concrete -> raw is an erased boundary, raw -> concrete is never allowed.
     */
    public static boolean javaTypeCompatible(JavaType target, JavaType source) {
        if (source == null || !target.clazz.isAssignableFrom(source.clazz)) {
            return false;
        }
        if (target.args.isEmpty()) {
            return true; // concrete source to raw target stays an erased boundary
        }
        if (target.clazz == source.clazz) {
            return target.args.equals(source.args);
        }
        List<Type> projected = projectedArguments(target.clazz, source.clazz, source.args);
        return projected != null && target.args.equals(projected);
    }

    /** Projects source arguments onto target type variables through the hierarchy. */
    public static List<Type> projectedArguments(Class<?> target, Class<?> source,
            List<Type> sourceArguments) {
        Map<Class<?>, Map<TypeVariable<?>, Type>> hierarchy =
                hierarchyBindings(source, sourceArguments);
        Map<TypeVariable<?>, Type> bindings = hierarchy.get(target);
        if (bindings == null) {
            return null;
        }
        TypeVariable<?>[] variables = target.getTypeParameters();
        List<Type> out = new ArrayList<>(variables.length);
        for (TypeVariable<?> variable : variables) {
            Type bound = bindings.get(variable);
            if (bound == null) {
                return null; // an unbound variable means the source is raw here
            }
            out.add(bound);
        }
        return out;
    }

    /**
     * Whether written arguments satisfy type-parameter bounds. Only ordinary
     * concrete class/interface bounds are checked; parameterized or variable
     * bounds are outside the profile.
     */
    public static boolean boundsSatisfied(TypeVariable<?>[] variables, List<Type> arguments) {
        if (arguments == null || arguments.size() != variables.length) {
            return false;
        }
        for (int i = 0; i < variables.length; i++) {
            Class<?> erased = boxedFor(arguments.get(i));
            for (java.lang.reflect.Type bound : variables[i].getBounds()) {
                if (bound == Object.class) continue;
                if (!(bound instanceof Class<?> clazz)) return false;
                if (erased == null || erased == Object.class || !clazz.isAssignableFrom(erased)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean boundsSatisfied(java.lang.reflect.Executable executable, List<Type> arguments) {
        return boundsSatisfied(executable.getTypeParameters(), arguments);
    }

    /**
     * Whether a signature puts Short/Byte/Character inside a parameterized
     * type, where erasure hides the value adapter. A direct wrapper
     * parameter/result keeps its normal adapter and is not flagged.
     */
    public static boolean wrapperArgument(java.lang.reflect.Type type) {
        if (type instanceof ParameterizedType applied) {
            for (java.lang.reflect.Type argument : applied.getActualTypeArguments()) {
                if (wrapperInside(argument)) return true;
            }
        }
        return false;
    }

    private static boolean wrapperInside(java.lang.reflect.Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz == Short.class || clazz == Byte.class || clazz == Character.class;
        }
        if (type instanceof ParameterizedType applied) {
            for (java.lang.reflect.Type argument : applied.getActualTypeArguments()) {
                if (wrapperInside(argument)) return true;
            }
        }
        return false;
    }

    /** Where a generic shape sits in the supported concrete profile. */
    public enum Shape {
        CLASS,
        PARAMETERIZED,
        TYPE_VARIABLE,
        WILDCARD,
        GENERIC_ARRAY,
        ARRAY
    }

    public static Shape shape(java.lang.reflect.Type generic) {
        if (generic instanceof WildcardType) return Shape.WILDCARD;
        if (generic instanceof GenericArrayType) return Shape.GENERIC_ARRAY;
        if (generic instanceof TypeVariable<?>) return Shape.TYPE_VARIABLE;
        if (generic instanceof ParameterizedType) return Shape.PARAMETERIZED;
        if (generic instanceof Class<?> clazz && clazz.isArray()) return Shape.ARRAY;
        return Shape.CLASS;
    }

    /** Recursive generic shape for structured API metadata. */
    public static Map<String, Object> shapeJson(java.lang.reflect.Type generic) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        Shape kind = shape(generic);
        out.put("kind", switch (kind) {
            case CLASS -> "class";
            case PARAMETERIZED -> "parameterized";
            case TYPE_VARIABLE -> "type-variable";
            case WILDCARD -> "wildcard";
            case GENERIC_ARRAY -> "generic-array";
            case ARRAY -> "array";
        });
        if (generic instanceof Class<?> clazz) {
            out.put("name", clazz.getTypeName());
        } else if (generic instanceof ParameterizedType applied) {
            out.put("raw", applied.getRawType().getTypeName());
            List<Map<String, Object>> arguments = new ArrayList<>();
            for (java.lang.reflect.Type argument : applied.getActualTypeArguments()) {
                arguments.add(shapeJson(argument));
            }
            out.put("arguments", arguments);
        } else if (generic instanceof TypeVariable<?> variable) {
            out.put("name", variable.getName());
            // Bounds are names only: a recursive bound like T extends Comparable<T>
            // must not expand forever.
            List<String> bounds = new ArrayList<>();
            for (java.lang.reflect.Type bound : variable.getBounds()) bounds.add(bound.getTypeName());
            out.put("bounds", bounds);
        } else if (generic instanceof WildcardType wildcard) {
            List<String> upper = new ArrayList<>();
            for (java.lang.reflect.Type bound : wildcard.getUpperBounds()) upper.add(bound.getTypeName());
            List<String> lower = new ArrayList<>();
            for (java.lang.reflect.Type bound : wildcard.getLowerBounds()) lower.add(bound.getTypeName());
            out.put("upperBounds", upper);
            out.put("lowerBounds", lower);
        } else if (generic instanceof GenericArrayType array) {
            out.put("component", shapeJson(array.getGenericComponentType()));
        }
        return out;
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
        Type mapped = mapBoundary(generic, NO_BINDINGS, false);
        return mapped != null ? mapped : map(raw);
    }

    public static Type mapValue(java.lang.reflect.Type generic, Class<?> raw) {
        Type mapped = mapBoundary(generic, NO_BINDINGS, true);
        return mapped != null ? mapped : mapValue(raw);
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
        if (source instanceof ListType list) {
            if (boxed == sprig.runtime.SprigList.class) return true;
            if (boxed == sprig.runtime.SprigMutableList.class) return list.mutable;
            return boxed == Object.class;
        }
        if (source instanceof MapType map) {
            if (boxed == sprig.runtime.SprigMap.class) return true;
            if (boxed == sprig.runtime.SprigMutableMap.class) return map.mutable;
            return boxed == Object.class;
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
        if (type instanceof NullableType nullable) {
            return boxedFor(nullable.inner);
        }
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
        if (type == NativeType.STRING) {
            return String.class;
        }
        if (type == NativeType.DECIMAL) {
            return sprig.runtime.SprigDecimal.class;
        }
        if (type == NativeType.BIGINT) {
            return sprig.runtime.SprigBigInt.class;
        }
        if (type instanceof JavaType javaType) {
            return boxed(javaType.clazz);
        }
        if (type instanceof ListType) {
            return sprig.runtime.SprigList.class;
        }
        if (type instanceof MapType) {
            return sprig.runtime.SprigMap.class;
        }
        return Object.class;
    }
}
