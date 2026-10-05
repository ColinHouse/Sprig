package sprig.compiler.sem;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import sprig.compiler.types.ListType;
import sprig.compiler.types.MapType;
import sprig.compiler.types.NativeType;
import sprig.compiler.types.Type;

/**
 * Member names of the built-in types. The checker resolves member access
 * against these tables, and editor completion lists the same names, so the
 * two cannot drift apart.
 */
public final class BuiltinMembers {
    private BuiltinMembers() {
    }

    private static final Map<NativeType, List<String>> STATIC = new EnumMap<>(NativeType.class);
    private static final Map<NativeType, List<String>> INSTANCE = new EnumMap<>(NativeType.class);
    private static final List<String> LIST_READ = List.of("size", "isEmpty", "get", "contains", "indexOf",
            "toMutableList", "toList", "map", "filter", "forEach");
    private static final List<String> LIST_MUTATE = List.of("append", "set", "insert", "removeAt", "remove",
            "clear", "sort");
    private static final List<String> MAP_READ = List.of("size", "isEmpty", "get", "containsKey", "keys",
            "values", "toMutableMap", "toMap");
    private static final List<String> MAP_MUTATE = List.of("set", "remove", "clear");

    static {
        STATIC.put(NativeType.INT, List.of("parse", "abs", "min", "max"));
        STATIC.put(NativeType.FLOAT, List.of("sqrt", "floor", "ceil", "abs"));
        STATIC.put(NativeType.DECIMAL, List.of("parse", "fromInt", "fromJava"));
        STATIC.put(NativeType.BIGINT, List.of("parse", "fromInt", "fromJava"));
        STATIC.put(NativeType.STRING, List.of("join", "fromCode"));

        INSTANCE.put(NativeType.INT, List.of("toFloat", "toFloatExact", "toFloatLossy", "toInt32Exact",
                "toDecimal", "divTrunc", "toString"));
        INSTANCE.put(NativeType.INT32, List.of("toInt", "toFloat", "toDecimal", "divTrunc", "toString"));
        INSTANCE.put(NativeType.FLOAT, List.of("toInt", "toIntExact", "toIntTrunc", "toFloat32Exact",
                "toFloat32Lossy", "isNaN", "isInfinite", "isFinite", "approxEqual", "toString"));
        INSTANCE.put(NativeType.FLOAT32, List.of("toFloat", "isNaN", "isInfinite", "isFinite", "toString"));
        INSTANCE.put(NativeType.DECIMAL, List.of("divide", "toIntExact", "toFloatExact", "toFloatLossy",
                "toJava", "toString"));
        INSTANCE.put(NativeType.BIGINT, List.of("divTrunc", "toIntExact", "toFloatExact", "toFloatLossy",
                "toDecimal", "toJava", "toString"));
        INSTANCE.put(NativeType.BOOL, List.of("toString"));
        INSTANCE.put(NativeType.STRING, List.of("length", "isEmpty", "charAt", "codeAt", "substring",
                "indexOf", "contains", "startsWith", "endsWith", "toUpperCase", "toLowerCase", "trim", "split",
                "replace", "repeat", "toInt", "toIntOrNull", "toFloat", "toString"));
    }

    /** The checker's id for {@code Type.name} on a built-in type name, or null. */
    public static String staticId(Type receiver, String name) {
        if (receiver instanceof NativeType nativeType
                && STATIC.getOrDefault(nativeType, List.of()).contains(name)) {
            return nativeType.display() + "." + name;
        }
        return null;
    }

    /** The checker's id for {@code value.name} on a built-in value, or null. */
    public static String instanceId(Type receiver, String name) {
        if (receiver instanceof NativeType nativeType) {
            return INSTANCE.getOrDefault(nativeType, List.of()).contains(name)
                    ? nativeType.display() + "." + name : null;
        }
        if (receiver instanceof ListType list) {
            if (LIST_READ.contains(name)) {
                return "List." + name;
            }
            if (name.equals("toString")) {
                return "toString";
            }
            if (LIST_MUTATE.contains(name)) {
                return list.mutable ? "MutableList." + name : "List.immutable." + name;
            }
            return null;
        }
        if (receiver instanceof MapType map) {
            if (MAP_READ.contains(name)) {
                return "Map." + name;
            }
            if (name.equals("toString")) {
                return "toString";
            }
            if (MAP_MUTATE.contains(name)) {
                return map.mutable ? "MutableMap." + name : "Map.immutable." + name;
            }
            return null;
        }
        return null;
    }

    /** Static member names of a built-in type, in declaration order. */
    public static List<String> staticNames(Type receiver) {
        return receiver instanceof NativeType nativeType
                ? STATIC.getOrDefault(nativeType, List.of()) : List.of();
    }

    /**
     * Callable member names of a built-in value. Mutating collection methods
     * are listed only for mutable collections, where a call can type-check.
     */
    public static List<String> instanceNames(Type receiver) {
        if (receiver instanceof NativeType nativeType) {
            return INSTANCE.getOrDefault(nativeType, List.of());
        }
        List<String> names = new ArrayList<>();
        if (receiver instanceof ListType list) {
            names.addAll(LIST_READ);
            if (list.mutable) {
                names.addAll(LIST_MUTATE);
            }
            names.add("toString");
        } else if (receiver instanceof MapType map) {
            names.addAll(MAP_READ);
            if (map.mutable) {
                names.addAll(MAP_MUTATE);
            }
            names.add("toString");
        }
        return names;
    }
}
