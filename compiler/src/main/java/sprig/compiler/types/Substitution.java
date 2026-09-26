package sprig.compiler.types;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import sprig.compiler.ast.Decl;

/**
 * v0.8 generic substitution: replaces {@link TypeParameterType} occurrences
 * with concrete type arguments. Substitution is purely compile-time; there is
 * no runtime generic dispatch.
 */
public final class Substitution {
    private Substitution() {
    }

    public static Type apply(Type type, Map<TypeParameterType, Type> map) {
        if (map.isEmpty() || type == null) {
            return type;
        }
        if (type instanceof TypeParameterType parameter) {
            Type replacement = map.get(parameter);
            return replacement != null ? replacement : parameter;
        }
        if (type instanceof NullableType nullable) {
            Type inner = apply(nullable.inner, map);
            return inner == nullable.inner ? nullable : NullableType.of(inner);
        }
        if (type instanceof ListType list) {
            Type element = apply(list.element, map);
            return element == list.element ? list : new ListType(element, list.mutable);
        }
        if (type instanceof MapType mapType) {
            Type key = apply(mapType.key, map);
            Type value = apply(mapType.value, map);
            return key == mapType.key && value == mapType.value
                    ? mapType
                    : new MapType(key, value, mapType.mutable);
        }
        if (type instanceof ClassType classType) {
            return applyClass(classType, map);
        }
        if (type instanceof VariantType variantType) {
            return applyVariant(variantType, map);
        }
        if (type instanceof VariantCaseType caseType) {
            List<Type> args = applyAll(caseType.variantArgs, map);
            return args.equals(caseType.variantArgs)
                    ? caseType
                    : new VariantCaseType(caseType.variant, args, caseType.variantCase);
        }
        if (type instanceof FunctionType function) {
            List<Type> params = applyAll(function.params, map);
            Type result = apply(function.result, map);
            return params.equals(function.params) && result == function.result
                    ? function
                    : new FunctionType(params, result);
        }
        return type;
    }

    private static ClassType applyClass(ClassType type, Map<TypeParameterType, Type> map) {
        List<Type> args = applyAll(type.args, map);
        return args.equals(type.args) ? type : new ClassType(type.decl, args);
    }

    private static VariantType applyVariant(VariantType type, Map<TypeParameterType, Type> map) {
        List<Type> args = applyAll(type.args, map);
        return args.equals(type.args) ? type : new VariantType(type.decl, args);
    }

    private static List<Type> applyAll(List<Type> types, Map<TypeParameterType, Type> map) {
        List<Type> out = new ArrayList<>(types.size());
        for (Type type : types) {
            out.add(apply(type, map));
        }
        return out;
    }

    /** Substitution map for an instantiated class type: T := argument. */
    public static Map<TypeParameterType, Type> forClass(ClassType type) {
        return bind(type.decl, type.args, type.decl.typeParams);
    }

    /** Substitution map for an instantiated variant type: T := argument. */
    public static Map<TypeParameterType, Type> forVariant(VariantType type) {
        return bind(type.decl, type.args, type.decl.typeParams);
    }

    /** Substitution map for a generic function: T := argument. */
    public static Map<TypeParameterType, Type> forFunction(Decl.Func function, List<Type> args) {
        return bind(function, args, function.typeParams);
    }

    private static Map<TypeParameterType, Type> bind(
            Decl owner, List<Type> args, List<String> typeParams) {
        Map<TypeParameterType, Type> map = new LinkedHashMap<>();
        for (int i = 0; i < typeParams.size() && i < args.size(); i++) {
            map.put(new TypeParameterType(owner, typeParams.get(i)), args.get(i));
        }
        return map;
    }
}
