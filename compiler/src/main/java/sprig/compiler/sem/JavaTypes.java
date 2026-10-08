package sprig.compiler.sem;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import sprig.compiler.types.JavaType;
import sprig.compiler.types.JavaWildcardType;
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
            if (bound instanceof JavaWildcardType wildcard) {
                // A variable bound to a wildcard through the receiver, as E in
                // List<? extends Number>: a result reads as the upper bound; a
                // parameter takes the lower bound and nothing for ? extends.
                if (valuePosition) {
                    return NullableType.of(wildcard.readAs());
                }
                return wildcard.lower;
            }
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
                // Fn0<T> with T bound through the receiver or the method's type
                // arguments is the concrete function type those bindings give;
                // without bindings the written arguments must already be concrete.
                sprig.compiler.types.FunctionType fn = callable(generic);
                if (fn == null && !bindings.isEmpty() && args.size() == rawClass.getTypeParameters().length) {
                    Type result = args.get(args.size() - 1);
                    List<Type> params = new ArrayList<>(args.subList(0, args.size() - 1));
                    if (params.stream().anyMatch(param -> param == NativeType.UNIT || param.isNullable()
                            && param.nonNull() instanceof JavaWildcardType) || result instanceof JavaWildcardType) {
                        return null;
                    }
                    fn = new sprig.compiler.types.FunctionType(params, result);
                }
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

    /**
     * Maps one generic argument; no nullability. A wildcard keeps its bound
     * as a {@link JavaWildcardType}, so {@code List<? extends Number>} stays
     * distinguishable from {@code List<Number>}: reads yield the bound,
     * writes through {@code ? extends} are rejected, as in Java.
     */
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
        if (generic instanceof WildcardType wildcard) {
            java.lang.reflect.Type[] lower = wildcard.getLowerBounds();
            java.lang.reflect.Type[] upper = wildcard.getUpperBounds();
            if (lower.length == 1) {
                Type bound = mapArgument(lower[0], bindings);
                return bound == null || bound instanceof JavaWildcardType ? null : new JavaWildcardType(null, bound);
            }
            if (upper.length == 1 && upper[0] != Object.class) {
                Type bound = mapArgument(upper[0], bindings);
                return bound == null || bound instanceof JavaWildcardType ? null : new JavaWildcardType(bound, null);
            }
            return new JavaWildcardType(null, null);
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
        if (source.args.isEmpty() && source.clazz.getTypeParameters().length > 0
                && unboundedArguments(target.args)) {
            // A raw value claims nothing and a target of bare wildcards asks
            // nothing, so raw List enters List<?>; List<String> would be an
            // unchecked conversion and fails the projection below.
            return true;
        }
        if (target.clazz == source.clazz) {
            return argumentsAccept(target.args, source.args);
        }
        List<Type> projected = projectedArguments(target.clazz, source.clazz, source.args);
        return projected != null && argumentsAccept(target.args, projected);
    }

    /**
     * Invariant argument matching, with Java's one relaxation: a wildcard in
     * the target accepts an argument within its bound ({@code ? extends Number}
     * takes {@code Integer}, {@code ? super Integer} takes {@code Number}, a
     * bare {@code ?} takes anything). A wildcard in the source fits a target
     * wildcard that contains it ({@code ? extends Integer} fits
     * {@code ? extends Number}, {@code ? super Number} fits
     * {@code ? super Integer}, anything fits {@code ?}).
     */
    public static boolean argumentsAccept(List<Type> targets, List<Type> sources) {
        if (targets.size() != sources.size()) {
            return false;
        }
        for (int i = 0; i < targets.size(); i++) {
            if (!argumentAccepts(targets.get(i), sources.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean argumentAccepts(Type target, Type source) {
        if (target.equals(source)) {
            return true;
        }
        if (!(target instanceof JavaWildcardType wildcard)) {
            return false;
        }
        if (source instanceof JavaWildcardType given) {
            if (wildcard.lower != null) {
                return given.lower != null && fitsBound(given.lower, wildcard.lower);
            }
            if (wildcard.upper == null) {
                return true;
            }
            return given.lower == null && given.upper != null && fitsBound(wildcard.upper, given.upper);
        }
        if (wildcard.lower != null) {
            return fitsBound(source, wildcard.lower);
        }
        return wildcard.upper == null || fitsBound(wildcard.upper, source);
    }

    /**
     * Whether {@code source} lies within {@code bound} on the JVM: a Java
     * bound compares against the source's boxed image ({@code Int} is a
     * {@code Long}, so it fits {@code ? extends Number}); a Sprig bound uses
     * ordinary assignability.
     */
    private static boolean fitsBound(Type bound, Type source) {
        if (bound.equals(source)) {
            return true;
        }
        if (bound instanceof JavaType javaBound) {
            if (source instanceof JavaType javaSource) {
                return javaTypeCompatible(javaBound, javaSource);
            }
            if (!javaBound.args.isEmpty()) {
                return javaArgumentCompatible(javaBound, source);
            }
            Class<?> image = boxedFor(source);
            return image != null && image != Object.class && javaBound.clazz.isAssignableFrom(image);
        }
        return Semantics.isAssignable(bound, source);
    }

    /** Whether every argument is a bare {@code ?}, the one shape a raw value may enter. */
    public static boolean unboundedArguments(List<Type> arguments) {
        for (Type argument : arguments) {
            if (!(argument instanceof JavaWildcardType wildcard) || wildcard.upper != null || wildcard.lower != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a formal written against {@code bindings} would write through
     * a wildcard: it mentions a type variable the receiver binds to a wildcard
     * anywhere except as the whole formal bound to {@code ? super X}, whose
     * lower bound is the one type Java lets in. Such a parameter, field or
     * callback result has no type to offer, as Java's capture rule says.
     */
    public static boolean capturedWrite(java.lang.reflect.Type generic, Map<TypeVariable<?>, Type> bindings) {
        if (generic instanceof TypeVariable<?> variable) {
            return bindings.get(variable) instanceof JavaWildcardType wildcard && wildcard.lower == null;
        }
        return mentionsWildcardBound(generic, bindings);
    }

    private static boolean mentionsWildcardBound(java.lang.reflect.Type generic, Map<TypeVariable<?>, Type> bindings) {
        if (generic instanceof TypeVariable<?> variable) {
            return bindings.get(variable) instanceof JavaWildcardType;
        }
        if (generic instanceof ParameterizedType applied) {
            for (java.lang.reflect.Type argument : applied.getActualTypeArguments()) {
                if (mentionsWildcardBound(argument, bindings)) return true;
            }
            return false;
        }
        if (generic instanceof WildcardType wildcard) {
            for (java.lang.reflect.Type bound : wildcard.getUpperBounds()) {
                if (mentionsWildcardBound(bound, bindings)) return true;
            }
            for (java.lang.reflect.Type bound : wildcard.getLowerBounds()) {
                if (mentionsWildcardBound(bound, bindings)) return true;
            }
            return false;
        }
        if (generic instanceof GenericArrayType array) {
            return mentionsWildcardBound(array.getGenericComponentType(), bindings);
        }
        return false;
    }

    /**
     * Compatibility of any Sprig source with a Java generic target, using the
     * source's JVM image (boxed scalars, Sprig collections) and projecting it
     * through the hierarchy. Raw targets stay erased boundaries; concrete
     * targets require matching arguments.
     */
    public static boolean javaArgumentCompatible(JavaType target, Type source) {
        if (source instanceof NullableType nullable) {
            return javaArgumentCompatible(target, nullable.inner);
        }
        if (source instanceof JavaType javaSource) {
            return javaTypeCompatible(target, javaSource);
        }
        if (target.args.isEmpty()) {
            return boxedFor(source) != null;
        }
        if (target.clazz == sprig.runtime.SprigList.class && source instanceof ListType list) {
            return argumentsAccept(target.args, List.of(list.element));
        }
        if (target.clazz == sprig.runtime.SprigMutableList.class && source instanceof ListType list) {
            return list.mutable && argumentsAccept(target.args, List.of(list.element));
        }
        if (target.clazz == sprig.runtime.SprigMap.class && source instanceof MapType map) {
            return argumentsAccept(target.args, List.of(map.key, map.value));
        }
        if (target.clazz == sprig.runtime.SprigMutableMap.class && source instanceof MapType map) {
            return map.mutable && argumentsAccept(target.args, List.of(map.key, map.value));
        }
        Class<?> image = boxedFor(source);
        if (image == null || image == Object.class || !target.clazz.isAssignableFrom(image)) {
            return false;
        }
        List<Type> projected = projectedArguments(target.clazz, image, List.of());
        return projected != null && argumentsAccept(target.args, projected);
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

    // ------------------------------------------------------------------
    // Java functional interfaces: where a Sprig fn value crosses into Java
    // ------------------------------------------------------------------

    /**
     * The single abstract method of a public functional interface, or null.
     * Methods of java.lang.Object do not count, as in the JLS. A method with
     * type parameters of its own or more than three parameters is outside the
     * Fn0..Fn3 profile, and Sprig's own Fn0..Fn3 keep their separate contract.
     */
    public static java.lang.reflect.Method functionalMethod(Class<?> type) {
        if (type == null || !type.isInterface() || isCallableClass(type)
                || !java.lang.reflect.Modifier.isPublic(type.getModifiers())) {
            return null;
        }
        java.lang.reflect.Method found = null;
        for (java.lang.reflect.Method method : type.getMethods()) {
            if (!java.lang.reflect.Modifier.isAbstract(method.getModifiers()) || isObjectMethod(method)) {
                continue;
            }
            if (found == null) {
                found = method;
            } else if (!found.getName().equals(method.getName())
                    || !java.util.Arrays.equals(found.getParameterTypes(), method.getParameterTypes())) {
                return null; // two distinct abstract methods
            }
        }
        if (found == null || found.getTypeParameters().length > 0 || found.getParameterCount() > 3) {
            return null;
        }
        return found;
    }

    private static boolean isObjectMethod(java.lang.reflect.Method method) {
        try {
            Object.class.getMethod(method.getName(), method.getParameterTypes());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Type-variable bindings of the functional method's declaring interface
     * for a formal such as {@code Consumer<? super E>}: the formal's type
     * arguments, resolved through {@code bindings}, projected through the
     * interface hierarchy. A wildcard argument reads as its bound, because a
     * lambda implementing {@code Consumer<String>} satisfies
     * {@code Consumer<? super String>} and the same holds for every other
     * wildcard. Null when an argument stays unresolved.
     */
    public static Map<TypeVariable<?>, Type> functionalBindings(java.lang.reflect.Type generic, Class<?> raw,
            Map<TypeVariable<?>, Type> bindings) {
        java.lang.reflect.Method sam = functionalMethod(raw);
        if (sam == null) return null;
        TypeVariable<?>[] variables = raw.getTypeParameters();
        List<Type> arguments = new ArrayList<>();
        if (generic instanceof ParameterizedType applied) {
            java.lang.reflect.Type[] written = applied.getActualTypeArguments();
            if (written.length != variables.length) return null;
            for (java.lang.reflect.Type argument : written) {
                java.lang.reflect.Type bound = wildcardBound(argument);
                Type mapped = bound == null ? null : mapArgument(bound, bindings);
                if (mapped == null) return null;
                arguments.add(mapped);
            }
        } else if (variables.length > 0) {
            return null; // a raw functional interface names no element types
        }
        return hierarchyBindings(raw, arguments).getOrDefault(sam.getDeclaringClass(), Map.of());
    }

    /**
     * The reflection type of a formal after receiver and explicit bindings:
     * a type variable bound to a Java type becomes that type, so the erased
     * {@code V} of {@code AtomicReference<V>.set(V)} reads as its {@code Runnable}
     * argument and a Sprig function value can be adapted for it.
     */
    public static java.lang.reflect.Type boundFormalType(java.lang.reflect.Type generic,
            Map<TypeVariable<?>, Type> bindings) {
        Type bound = generic instanceof TypeVariable<?> variable && bindings != null
                ? bindings.get(variable) : null;
        return bound != null && bound.nonNull() instanceof JavaType javaType ? reflectionType(javaType) : generic;
    }

    /** The raw class of {@link #boundFormalType}. */
    public static Class<?> boundFormalClass(java.lang.reflect.Type generic, Class<?> raw,
            Map<TypeVariable<?>, Type> bindings) {
        java.lang.reflect.Type resolved = boundFormalType(generic, bindings);
        return resolved instanceof Class<?> clazz ? clazz
                : resolved instanceof ParameterizedType applied && applied.getRawType() instanceof Class<?> rawClass
                        ? rawClass : raw;
    }

    /** The reflection type a Sprig Java type stands for; the raw class outside the concrete profile. */
    private static java.lang.reflect.Type reflectionType(JavaType type) {
        if (type.args.isEmpty()) {
            return type.clazz;
        }
        java.lang.reflect.Type[] arguments = new java.lang.reflect.Type[type.args.size()];
        for (int i = 0; i < arguments.length; i++) {
            Type argument = type.args.get(i);
            java.lang.reflect.Type reflected = argument instanceof JavaType inner ? reflectionType(inner)
                    : boxedFor(argument);
            if (reflected == null) {
                return type.clazz;
            }
            arguments[i] = reflected;
        }
        return new ParameterizedType() {
            @Override
            public java.lang.reflect.Type[] getActualTypeArguments() {
                return arguments.clone();
            }

            @Override
            public java.lang.reflect.Type getRawType() {
                return type.clazz;
            }

            @Override
            public java.lang.reflect.Type getOwnerType() {
                return type.clazz.getDeclaringClass();
            }
        };
    }

    /**
     * The Sprig function type a functional-interface formal expects, or null
     * when a type argument or a method parameter stays unresolved, so nothing
     * is guessed. {@code void} is {@code Unit}; a lambda with any result
     * satisfies it, as in Java.
     */
    public static sprig.compiler.types.FunctionType javaCallable(java.lang.reflect.Type generic, Class<?> raw,
            Map<TypeVariable<?>, Type> bindings) {
        java.lang.reflect.Method sam = functionalMethod(raw);
        Map<TypeVariable<?>, Type> local = functionalBindings(generic, raw, bindings);
        if (sam == null || local == null) return null;
        List<Type> params = new ArrayList<>();
        for (java.lang.reflect.Type parameter : sam.getGenericParameterTypes()) {
            Type mapped = functionalSlot(parameter, local, false);
            if (mapped == null || mapped == NativeType.UNIT) return null;
            params.add(mapped);
        }
        Type result = sam.getReturnType() == void.class
                ? NativeType.UNIT : functionalSlot(sam.getGenericReturnType(), local, true);
        if (result == null) return null;
        return new sprig.compiler.types.FunctionType(params, result);
    }

    /**
     * One parameter or result of the functional method: a class, a bound
     * variable or a concrete generic. A variable bound to a wildcard through
     * the receiver (forEach on a List<? extends Number>) hands the lambda its
     * upper bound where the lambda receives a value and asks for the lower
     * bound where the lambda returns one; a result slot bound to
     * {@code ? extends} has no type the lambda could return, so it is null.
     */
    private static Type functionalSlot(java.lang.reflect.Type generic, Map<TypeVariable<?>, Type> bindings,
            boolean resultSlot) {
        if (generic instanceof Class<?> clazz) {
            // Character/Short/Byte need value adapters, as the Fn slots document.
            if (clazz.isArray() || needsValueAdapter(clazz) || clazz == short.class || clazz == byte.class) {
                return null;
            }
            return map(clazz);
        }
        if (generic instanceof TypeVariable<?> variable) {
            Type bound = bindings.get(variable);
            if (bound instanceof JavaWildcardType wildcard) {
                return resultSlot ? wildcard.lower : wildcard.readAs();
            }
            return bound;
        }
        if (generic instanceof ParameterizedType) {
            return mentionsWildcardBound(generic, bindings) ? null : mapBoundary(generic, bindings, false);
        }
        return null;
    }

    /** A wildcard type argument reads as its single bound; a bare {@code ?} stays unresolved. */
    private static java.lang.reflect.Type wildcardBound(java.lang.reflect.Type argument) {
        if (argument instanceof WildcardType wildcard) {
            java.lang.reflect.Type[] lower = wildcard.getLowerBounds();
            if (lower.length == 1) return lower[0];
            java.lang.reflect.Type[] upper = wildcard.getUpperBounds();
            return upper.length == 1 && upper[0] != Object.class ? upper[0] : null;
        }
        return argument;
    }

    /**
     * Whether a formal is a functional interface whose wildcards sit only in
     * its own type arguments, the one place a lambda satisfies them.
     */
    public static boolean functionalFormal(java.lang.reflect.Type generic, Class<?> raw) {
        if (functionalMethod(raw) == null) return false;
        if (!(generic instanceof ParameterizedType applied)) return generic instanceof Class<?>;
        for (java.lang.reflect.Type argument : applied.getActualTypeArguments()) {
            java.lang.reflect.Type bound = wildcardBound(argument);
            if (bound == null || containsWildcard(bound)) return false;
        }
        return true;
    }

    private static boolean containsWildcard(java.lang.reflect.Type type) {
        if (type instanceof WildcardType) return true;
        if (type instanceof ParameterizedType applied) {
            for (java.lang.reflect.Type argument : applied.getActualTypeArguments()) {
                if (containsWildcard(argument)) return true;
            }
        }
        if (type instanceof GenericArrayType array) return containsWildcard(array.getGenericComponentType());
        return false;
    }

    /**
     * The erased element class of a varargs parameter when trailing arguments
     * can be packed into it: a class element, or a parameterized element taken
     * at its raw class. A type-variable element ({@code T...}) stays null.
     */
    public static Class<?> varargsElement(java.lang.reflect.Executable executable) {
        if (!executable.isVarArgs()) return null;
        Class<?>[] raw = executable.getParameterTypes();
        java.lang.reflect.Type generic = executable.getGenericParameterTypes()[raw.length - 1];
        if (generic instanceof GenericArrayType array
                && !(array.getGenericComponentType() instanceof ParameterizedType)) {
            return null;
        }
        return raw[raw.length - 1].getComponentType();
    }

    /** The generic element type of a varargs parameter, for mapping one trailing argument. */
    public static java.lang.reflect.Type varargsElementType(java.lang.reflect.Executable executable) {
        Class<?>[] raw = executable.getParameterTypes();
        java.lang.reflect.Type generic = executable.getGenericParameterTypes()[raw.length - 1];
        return generic instanceof GenericArrayType array
                ? array.getGenericComponentType() : raw[raw.length - 1].getComponentType();
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
        if (source instanceof sprig.compiler.types.ClassType classSource) {
            if (classSource.decl.superclass != null && target.isAssignableFrom(classSource.decl.superclass)) {
                return true; // the class extends the Java class or one of its ancestors
            }
            if (target.isInterface()) {
                for (Class<?> declared : classSource.decl.conformedInterfaces) {
                    if (target.isAssignableFrom(declared)) {
                        return true; // declared foreign conformance conversion
                    }
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

    /**
     * Preferred source-level JVM class for a mapped Sprig type, used to box
     * arguments at a bound generic call (e.g. {@code Int32} binds to
     * {@code int}/{@code Integer}, never {@code long}).
     */
    public static Class<?> preferredRaw(Type type) {
        if (type instanceof NullableType nullable) {
            return preferredRaw(nullable.inner);
        }
        if (type == NativeType.INT) return long.class;
        if (type == NativeType.INT32) return int.class;
        if (type == NativeType.FLOAT) return double.class;
        if (type == NativeType.FLOAT32) return float.class;
        if (type == NativeType.BOOL) return boolean.class;
        if (type == NativeType.STRING) return String.class;
        if (type == NativeType.DECIMAL) return sprig.runtime.SprigDecimal.class;
        if (type == NativeType.BIGINT) return sprig.runtime.SprigBigInt.class;
        if (type instanceof JavaType javaType) return javaType.clazz;
        if (type instanceof JavaWildcardType wildcard) return preferredRaw(wildcard.readAs());
        if (type instanceof ListType) return sprig.runtime.SprigList.class;
        if (type instanceof MapType) return sprig.runtime.SprigMap.class;
        return null;
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
        if (type instanceof JavaWildcardType wildcard) {
            return boxedFor(wildcard.readAs());
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
