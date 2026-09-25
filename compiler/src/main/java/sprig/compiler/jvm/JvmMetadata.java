package sprig.compiler.jvm;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import sprig.compiler.sem.JavaTypes;

/** Shared reflection view for {@code api} and compiler overload diagnostics. */
public final class JvmMetadata {
    private JvmMetadata() {}

    public static Map<String, Object> inspect(Class<?> clazz) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schemaVersion", 1);
        out.put("className", clazz.getName());
        out.put("classKind", clazz.isInterface() ? "interface" : clazz.isEnum() ? "enum" : "class");
        List<Map<String, Object>> constructors = new ArrayList<>();
        Arrays.stream(clazz.getConstructors()).sorted(Comparator.comparing(Constructor::toGenericString))
                .forEach(ctor -> constructors.add(describe(ctor)));
        out.put("constructors", constructors);
        List<Map<String, Object>> staticMethods = new ArrayList<>();
        List<Map<String, Object>> instanceMethods = new ArrayList<>();
        Arrays.stream(clazz.getMethods()).sorted(Comparator.comparing(Method::toGenericString))
                .forEach(method -> (Modifier.isStatic(method.getModifiers()) ? staticMethods : instanceMethods)
                        .add(describe(method)));
        out.put("staticMethods", staticMethods);
        out.put("instanceMethods", instanceMethods);
        List<Map<String, Object>> fields = new ArrayList<>();
        Arrays.stream(clazz.getFields()).sorted(Comparator.comparing(Field::getName))
                .forEach(field -> fields.add(describe(field)));
        out.put("fields", fields);
        out.put("nullabilityPolicy", "Java reference results are nullable; parameters require non-null values unless future metadata proves otherwise");
        out.put("classpath", JvmClasspath.entries().stream().map(java.nio.file.Path::toString).toList());
        return out;
    }

    public static Map<String, Object> describe(Executable executable) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", executable instanceof Method method ? method.getName() : "<init>");
        out.put("javaSignature", executable.toGenericString());
        out.put("sprigSignature", sprigSignature(executable));
        out.put("static", Modifier.isStatic(executable.getModifiers()));
        out.put("varargs", executable.isVarArgs());
        out.put("javaParameterTypes", Arrays.stream(executable.getParameterTypes()).map(Class::getTypeName).toList());
        out.put("sprigParameterTypes", Arrays.stream(executable.getParameterTypes())
                .map(c -> JavaTypes.map(c).display()).toList());
        out.put("genericParameterTypes", Arrays.stream(executable.getGenericParameterTypes())
                .map(Type::getTypeName).toList());
        if (executable instanceof Method method) {
            out.put("javaReturnType", method.getReturnType().getTypeName());
            out.put("sprigReturnType", JavaTypes.mapValue(method.getReturnType()).display());
            out.put("genericReturnType", method.getGenericReturnType().getTypeName());
            out.put("nullableResult", !method.getReturnType().isPrimitive());
        } else {
            out.put("javaReturnType", executable.getDeclaringClass().getTypeName());
            out.put("sprigReturnType", executable.getDeclaringClass().getSimpleName());
            out.put("nullableResult", false);
        }
        out.put("checkedExceptions", Arrays.stream(executable.getExceptionTypes())
                .filter(e -> !RuntimeException.class.isAssignableFrom(e) && !Error.class.isAssignableFrom(e))
                .map(Class::getTypeName).toList());
        String unusable = unsupportedReason(executable);
        out.put("usableFromSprig", unusable == null);
        out.put("signatureSupported", unusable == null);
        out.put("interopLevel", unusable != null ? "unsupported"
                : genericBoundary(executable) ? "erased-generic" : "direct");
        out.put("unusableReason", unusable);
        out.put("genericBoundary", genericBoundary(executable));
        List<String> interopNotes = new ArrayList<>();
        if (genericBoundary(executable)) interopNotes.add(
                "Generic type arguments are erased at the Sprig boundary; no List[T] or Map[K,V] guarantee is inferred.");
        if (Arrays.stream(executable.getParameterTypes())
                .anyMatch(c -> c == char.class || c == Character.class)) interopNotes.add(
                "Java char/Character arguments accept only a one-UTF-16-unit Sprig String literal.");
        out.put("interopNotes", interopNotes);
        return out;
    }

    public static Map<String, Object> describe(Field field) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean array = field.getType().isArray();
        boolean generic = !field.getGenericType().equals(field.getType());
        out.put("name", field.getName());
        out.put("javaSignature", field.toGenericString());
        out.put("javaType", field.getType().getTypeName());
        out.put("sprigType", JavaTypes.mapValue(field.getType()).display());
        out.put("genericType", field.getGenericType().getTypeName());
        out.put("static", Modifier.isStatic(field.getModifiers()));
        out.put("nullableResult", !field.getType().isPrimitive());
        out.put("usableFromSprig", !array);
        out.put("signatureSupported", !array);
        out.put("genericBoundary", generic);
        out.put("interopLevel", array ? "unsupported" : generic ? "erased-generic" : "direct");
        out.put("unusableReason", field.getType().isArray() ? "Java arrays have no Sprig source type or adapter" : null);
        if (JavaTypes.needsValueAdapter(field.getType())) out.put("writePolicy",
                "Direct assignment is unsupported; use an explicit Java setter or adapter.");
        return out;
    }

    private static String sprigSignature(Executable executable) {
        StringBuilder out = new StringBuilder(executable instanceof Method method ? method.getName() : executable.getDeclaringClass().getSimpleName());
        out.append('(');
        Class<?>[] params = executable.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) out.append(", ");
            out.append(JavaTypes.map(params[i]).display());
        }
        out.append(')');
        if (executable instanceof Method method) out.append(" -> ").append(JavaTypes.mapValue(method.getReturnType()).display());
        return out.toString();
    }

    public static String unsupportedReason(Executable executable) {
        if (executable.isVarArgs()) return "Java varargs are not supported";
        for (Class<?> param : executable.getParameterTypes()) {
            if (param.isArray()) return "Java array parameter has no Sprig source type or adapter";
        }
        if (executable instanceof Method method && method.getReturnType().isArray()) {
            return "Java array result has no Sprig source type or adapter";
        }
        return null;
    }

    private static boolean genericBoundary(Executable executable) {
        if (executable instanceof Method method && method.getGenericReturnType() != method.getReturnType()) return true;
        Type[] generic = executable.getGenericParameterTypes();
        Class<?>[] raw = executable.getParameterTypes();
        for (int i = 0; i < raw.length; i++) if (generic[i] != raw[i]) return true;
        return false;
    }
}
