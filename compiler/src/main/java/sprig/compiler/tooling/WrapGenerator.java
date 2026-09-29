package sprig.compiler.tooling;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import sprig.compiler.jvm.JvmMetadata;
import sprig.compiler.sem.JavaTypes;
import sprig.compiler.types.FunctionType;
import sprig.compiler.types.JavaType;
import sprig.compiler.types.ListType;
import sprig.compiler.types.MapType;
import sprig.compiler.types.NativeType;
import sprig.compiler.types.NullableType;
import sprig.compiler.types.Type;
import sprig.compiler.types.TypeParameterType;

/**
 * Java-to-Sprig wrapper source generator. Consumes the shared interop
 * classification ({@link JvmMetadata.Support}) instead of re-deciding JVM
 * support; the output is ordinary, editable Sprig source with no runtime
 * dependency on this generator.
 */
public final class WrapGenerator {
    private WrapGenerator() {}

    public record GeneratedMember(String kind, String name, String javaSignature) {}

    public record SkippedMember(String javaSignature, List<String> reasonCodes, String explanation) {}

    public record Outcome(String source, List<GeneratedMember> generated, List<SkippedMember> skipped,
                          List<String> warnings, boolean usesAdapters) {}

    private static final Set<String> NESTED_COLLECTIONS = Set.of(
            "java.util.List", "java.util.Map", "java.util.Optional",
            "java.util.Set", "java.util.Collection", "java.lang.Iterable");

    /** Generator-level skip reasons on top of the shared interop ids. */
    private static final String VALUE_ADAPTER_UNSUPPORTED = "value-adapter-unsupported";
    private static final String NESTED_COLLECTION_UNSUPPORTED = "nested-collection-unsupported";
    private static final String OVERLOAD_COLLISION = "overload-collision";
    private static final String MEMBER_NAME_COLLISION = "member-name-collision";

    private static final class UnsupportedShape extends RuntimeException {
        final List<String> reasons;

        UnsupportedShape(List<String> reasons, String explanation) {
            super(explanation);
            this.reasons = reasons;
        }
    }

    private static final class PlanContext {
        final Imports imports;
        boolean adapters;
        boolean optionalHelper;

        PlanContext(Imports imports) {
            this.imports = imports;
        }
    }

    public static Outcome generate(Class<?> clazz, String memberFilter) {
        Imports imports = new Imports(simpleName(clazz));
        String hostAlias = imports.hostAlias(clazz);
        PlanContext ctx = new PlanContext(imports);
        List<GeneratedMember> generated = new ArrayList<>();
        List<SkippedMember> skipped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> classMembers = new ArrayList<>();
        List<String> freeMembers = new ArrayList<>();
        Set<String> classNames = new LinkedHashSet<>();
        Set<String> freeNames = new LinkedHashSet<>();
        freeNames.add(OPTIONAL_HELPER); // reserved for the generated conversion helper
        

        List<Constructor<?>> constructors = new ArrayList<>(List.of(clazz.getConstructors()));
        constructors.sort(Comparator.comparing(Constructor::toGenericString));
        List<Method> methods = new ArrayList<>();
        for (Method method : clazz.getMethods()) {
            if (method.getDeclaringClass() == Object.class || method.isBridge() || method.isSynthetic()) {
                continue;
            }
            methods.add(method);
        }
        methods.sort(Comparator.comparing(Method::getName).thenComparing(Method::toGenericString));
        List<Field> fields = new ArrayList<>(List.of(clazz.getFields()));
        fields.sort(Comparator.comparing(Field::getName).thenComparing(Field::toGenericString));

        // Constructors: one free function per constructor; overload suffixes when needed.
        Map<String, List<Plan>> constructorGroups = new TreeMap<>();
        for (Constructor<?> constructor : constructors) {
            if (!matchesConstructor(constructor, clazz, memberFilter)) continue;
            Plan plan = plan(constructor, () -> {
                JvmMetadata.Support support = JvmMetadata.support(constructor);
                if (!support.usable()) {
                    throw new UnsupportedShape(support.reasonCodes(), support.unusableReason());
                }
                rejectShapes(constructor);
                if (constructor.getTypeParameters().length > 0) {
                    throw new UnsupportedShape(List.of("explicit-type-arguments-required"),
                            "constructors with method type parameters are outside the v1 generator profile");
                }
                return planConstructor(clazz, hostAlias, constructor, ctx);
            }, skipped);
            if (plan != null) {
                constructorGroups.computeIfAbsent(plan.sprigBase, k -> new ArrayList<>()).add(plan);
            }
        }
        for (Map.Entry<String, List<Plan>> group : constructorGroups.entrySet()) {
            Map<String, Integer> tokens = tokenCounts(group.getValue());
            boolean suffix = group.getValue().size() > 1;
            for (Plan plan : group.getValue()) {
                if (suffix && tokens.get(plan.token()) > 1) {
                    skipped.add(new SkippedMember(plan.javaSignature, List.of(OVERLOAD_COLLISION),
                            "Two Java constructors collapse to the same Sprig signature; skipped"));
                    continue;
                }
                String name = suffix ? group.getKey() + "_" + plan.token() : group.getKey();
                if (!freeNames.add(name)) {
                    skipped.add(new SkippedMember(plan.javaSignature, List.of(MEMBER_NAME_COLLISION),
                            "Generated member name is already used by another wrapper"));
                    continue;
                }
                freeMembers.add(plan.render(name));
                generated.add(new GeneratedMember("constructor", name, plan.javaSignature));
            }
        }

        // Static methods become free functions; instance methods become class methods.
        Map<String, List<Plan>> methodGroups = new LinkedHashMap<>();
        for (Method method : methods) {
            if (memberFilter != null && !method.getName().equals(memberFilter)) continue;
            Plan plan = plan(method, () -> {
                JvmMetadata.Support support = JvmMetadata.support(method);
                if (!support.usable()) {
                    throw new UnsupportedShape(support.reasonCodes(), support.unusableReason());
                }
                if (!Modifier.isStatic(method.getModifiers())
                        && (method.getName().equals("toString") || method.getName().equals("equals")
                        || method.getName().equals("hashCode"))) {
                    throw new UnsupportedShape(List.of("object-method-unsupported"),
                            "instance Object-method overrides are not wrapped; Sprig classes define toString themselves");
                }
                rejectShapes(method);
                boolean generic = method.getTypeParameters().length > 0;
                if (generic && !Modifier.isStatic(method.getModifiers())) {
                    throw new UnsupportedShape(List.of("raw-generic-boundary"),
                            "instance generic methods cannot bind the receiver's type variables");
                }
                if (generic && !simpleBounds(method)) {
                    throw new UnsupportedShape(List.of("generic-bound-unsupported"),
                            "method type parameters with class/interface bounds are outside the generator profile");
                }
                return planMethod(clazz, hostAlias, method, ctx);
            }, skipped);
            if (plan != null) {
                String groupKey = (plan.isStatic ? "static:" : "instance:") + method.getName();
                methodGroups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(plan);
            }
        }
        for (List<Plan> plans : methodGroups.values()) {
            plans.sort(Comparator.comparing(p -> p.javaSignature));
            Map<String, Integer> tokens = tokenCounts(plans);
            boolean suffix = plans.size() > 1;
            for (Plan plan : plans) {
                if (suffix && tokens.get(plan.token()) > 1) {
                    skipped.add(new SkippedMember(plan.javaSignature, List.of(OVERLOAD_COLLISION),
                            "Two Java overloads collapse to the same Sprig signature; skipped"));
                    continue;
                }
                String name = suffix ? plan.sprigBase + "_" + plan.token() : plan.sprigBase;
                boolean added = plan.isStatic ? freeNames.add(name) : classNames.add(name);
                if (!added) {
                    skipped.add(new SkippedMember(plan.javaSignature, List.of(MEMBER_NAME_COLLISION),
                            "Generated member name is already used by another wrapper"));
                    continue;
                }
                (plan.isStatic ? freeMembers : classMembers).add(plan.render(name));
                generated.add(new GeneratedMember(plan.isStatic ? "static-method" : "instance-method",
                        name, plan.javaSignature));
            }
        }

        // Fields: read-only accessors only.
        for (Field field : fields) {
            if (memberFilter != null && !field.getName().equals(memberFilter)) continue;
            Plan plan = plan(field, () -> {
                JvmMetadata.Support support = JvmMetadata.support(field);
                if (!support.usable()) {
                    throw new UnsupportedShape(support.reasonCodes(), support.unusableReason());
                }
                rejectNestedShapes(field.getGenericType());
                return planField(clazz, hostAlias, field, ctx);
            }, skipped);
            if (plan == null) continue;
            String name = plan.sprigBase;
            boolean added = plan.isStatic ? freeNames.add(name) : classNames.add(name);
            if (!added) {
                skipped.add(new SkippedMember(plan.javaSignature, List.of(MEMBER_NAME_COLLISION),
                        "Generated member name is already used by another wrapper"));
                continue;
            }
            (plan.isStatic ? freeMembers : classMembers).add(plan.render(name));
            generated.add(new GeneratedMember(plan.isStatic ? "static-field" : "instance-field",
                    name, plan.javaSignature));
        }

        if (clazz.getTypeParameters().length > 0) {
            warnings.add("generic host class " + clazz.getName()
                    + ": remaining type variables stay erased at the wrapper boundary");
        }
        if (ctx.optionalHelper) {
            freeMembers.add(0, optionalHelper(imports));
        }

        StringBuilder source = new StringBuilder();
        source.append("# Generated by sprig wrap from ").append(clazz.getName()).append(".\n");
        source.append("# Ordinary Sprig source; safe to edit.\n");
        source.append("# Regeneration overwrites only with --force.\n\n");
        for (String line : imports.render()) {
            source.append(line).append('\n');
        }
        source.append('\n');
        String className = simpleName(clazz);
        source.append("class ").append(className).append(":\n");
        source.append("    let host: ").append(hostAlias).append('\n');
        for (String member : classMembers) {
            source.append('\n').append(indent(member, 1)).append('\n');
        }
        for (String member : freeMembers) {
            source.append('\n').append(member).append('\n');
        }
        return new Outcome(source.toString(), generated, skipped, warnings, ctx.adapters);
    }

    private interface Planner {
        Plan run();
    }

    private static Plan plan(Executable executable, Planner planner, List<SkippedMember> skipped) {
        try {
            return planner.run();
        } catch (UnsupportedShape shape) {
            skipped.add(new SkippedMember(executable.toGenericString(), shape.reasons, shape.getMessage()));
            return null;
        }
    }

    private static Plan plan(Field field, Planner planner, List<SkippedMember> skipped) {
        try {
            return planner.run();
        } catch (UnsupportedShape shape) {
            skipped.add(new SkippedMember(field.toGenericString(), shape.reasons, shape.getMessage()));
            return null;
        }
    }

    /**
     * Generator-side shape rejection for v1: wildcards, generic arrays and
     * Short/Byte/Character inside parameterized types are skipped even when a
     * raw boundary would technically bind.
     */
    private static void rejectNestedShapes(java.lang.reflect.Type type) {
        if (type instanceof java.lang.reflect.WildcardType) {
            throw new UnsupportedShape(List.of("wildcard-unsupported"),
                    "wildcard shapes are not generated in v1");
        }
        if (type instanceof java.lang.reflect.GenericArrayType) {
            throw new UnsupportedShape(List.of("generic-array-unsupported"),
                    "Java generic array types (T[]) are not generated in v1");
        }
        if (type instanceof java.lang.reflect.ParameterizedType applied) {
            for (java.lang.reflect.Type argument : applied.getActualTypeArguments()) {
                if (argument == Short.class || argument == Byte.class || argument == Character.class) {
                    throw new UnsupportedShape(List.of("generic-wrapper-unsupported"),
                            "Short/Byte/Character generic arguments need an element adapter");
                }
                rejectNestedShapes(argument);
            }
        }
    }

    private static void rejectShapes(Executable executable) {
        for (java.lang.reflect.Type type : executable.getGenericParameterTypes()) {
            rejectNestedShapes(type);
        }
        if (executable instanceof Method method) {
            rejectNestedShapes(method.getGenericReturnType());
        }
    }

    private static Map<String, Integer> tokenCounts(List<Plan> plans) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Plan plan : plans) {
            counts.merge(plan.token(), 1, Integer::sum);
        }
        return counts;
    }

    private static boolean matchesConstructor(Constructor<?> constructor, Class<?> clazz, String filter) {
        return filter == null || filter.equals("<init>") || filter.equals(clazz.getSimpleName());
    }

    private static String indent(String code, int levels) {
        String prefix = "    ".repeat(levels);
        return prefix + code.replace("\n", "\n" + prefix);
    }

    static String simpleName(Class<?> clazz) {
        String name = clazz.getSimpleName();
        if (name.isEmpty()) name = clazz.getName().replaceAll(".*[.$]", "");
        return identifier(name);
    }

    private static String identifier(String raw) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            out.append(Character.isJavaIdentifierPart(c) ? c : '_');
        }
        if (out.length() == 0 || !Character.isJavaIdentifierStart(out.charAt(0))) {
            out.insert(0, '_');
        }
        return out.toString();
    }

    private static String snake(String raw) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isUpperCase(c)) {
                if (out.length() > 0 && out.charAt(out.length() - 1) != '_') out.append('_');
                out.append(Character.toLowerCase(c));
            } else if (Character.isJavaIdentifierPart(c)) {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        String text = out.toString();
        return text.isEmpty() ? "_" : identifier(text);
    }

    /** Deterministic import aliases; {@code jvm} is reserved for the adapter facade. */
    private static final class Imports {
        private final Map<String, String> aliasByClass = new TreeMap<>();
        private final Set<String> used = new TreeSet<>(List.of("jvm"));
        private boolean adapters;

        Imports(String className) {
            used.add(className);
        }

        String hostAlias(Class<?> clazz) {
            return aliasByClass.computeIfAbsent(clazz.getName(), key -> alias("Host", clazz));
        }

        String alias(Class<?> clazz) {
            return aliasByClass.computeIfAbsent(clazz.getName(), key -> alias(simpleName(clazz), clazz));
        }

        void useAdapters() {
            adapters = true;
        }

        private String alias(String base, Class<?> clazz) {
            String stem = identifier(base);
            String candidate = stem;
            int suffix = 2;
            while (!used.add(candidate)) {
                candidate = stem + suffix++;
            }
            return candidate;
        }

        List<String> render() {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, String> entry : aliasByClass.entrySet()) {
                lines.add("import " + entry.getKey() + " as " + entry.getValue());
            }
            if (adapters) {
                lines.add("import \"@std/jvm.spr\" as jvm");
            }
            return lines;
        }
    }

    // ------------------------------------------------------------------
    // Member plans
    // ------------------------------------------------------------------

    private record Plan(String javaSignature, String sprigBase, boolean isStatic, List<Param> params,
                        ReturnShape returns, List<String> throwsTypes, boolean generic,
                        List<String> typeParams, String code) {
        String token() {
            if (params.isEmpty()) return "no_args";
            List<String> tokens = new ArrayList<>();
            for (Param param : params) tokens.add(param.token);
            return String.join("_", tokens);
        }

        String render(String sprigName) {
            return code.replace("$NAME$", sprigName);
        }
    }

    private record Param(String name, String sprigType, String token, Conversion conversion,
                         String optionalAlias) {}

    private enum Kind { DIRECT, OPTIONAL, LIST, MAP, HOST, UNIT }

    private record Conversion(Kind kind, String element, String element2) {}

    private record ReturnShape(Kind kind, String sprigType, String element, String element2) {
        static ReturnShape unit() {
            return new ReturnShape(Kind.UNIT, "Unit", null, null);
        }
    }

    private static TypeParameterType typeParameter(String name) {
        // Generator-only rendering placeholder; the emitted source is re-parsed
        // with the real type parameter declarations.
        return new TypeParameterType(null, name);
    }

    private static Map<TypeVariable<?>, Type> methodBindings(Executable executable) {
        Map<TypeVariable<?>, Type> bindings = new java.util.IdentityHashMap<>();
        for (TypeVariable<?> variable : executable.getTypeParameters()) {
            bindings.put(variable, typeParameter(variable.getName()));
        }
        return bindings;
    }

    private static boolean simpleBounds(Executable executable) {
        for (TypeVariable<?> variable : executable.getTypeParameters()) {
            for (java.lang.reflect.Type bound : variable.getBounds()) {
                if (bound != Object.class) return false;
            }
        }
        return true;
    }

    private static List<String> typeVarNames(Executable executable) {
        List<String> names = new ArrayList<>();
        for (TypeVariable<?> variable : executable.getTypeParameters()) names.add(variable.getName());
        return names;
    }

    private static Plan planConstructor(Class<?> clazz, String hostAlias, Constructor<?> constructor,
                                        PlanContext ctx) {
        Imports imports = ctx.imports;
        String className = simpleName(clazz);
        List<Param> params = planParams(constructor, ctx);
        List<String> throwsTypes = new ArrayList<>(throwsTypes(constructor, ctx.imports, params));
        if (!throwsTypes.contains("Error")) {
            throwsTypes.add("Error"); // the null guard is a recoverable Sprig Error
        }
        StringBuilder body = new StringBuilder();
        body.append("func $NAME$(").append(parameterList(params)).append(") -> ").append(className);
        appendThrows(body, throwsTypes);
        body.append(":\n");
        appendPreparations(body, params, "");
        body.append("    let host = ").append(hostAlias)
                .append('(').append(hostArguments(params)).append(")\n");
        body.append("    if host != null:\n");
        body.append("        return ").append(className).append("(host=host)\n");
        body.append("    throw Error(\"").append(className).append(" constructor returned null\")\n");
        return new Plan(constructor.toGenericString(), snake(className) + "_new", true, params, null,
                throwsTypes, false, List.of(), body.toString());
    }

    private static Plan planMethod(Class<?> clazz, String hostAlias, Method method,
                                   PlanContext ctx) {
        Imports imports = ctx.imports;
        boolean generic = method.getTypeParameters().length > 0;
        List<Param> params = planParams(method, ctx);
        ReturnShape returns = planReturn(method, clazz, simpleName(clazz), ctx);
        List<String> throwsTypes = throwsTypes(method, ctx.imports, params);
        if (!throwsTypes.contains("Error") && requiresError(params, returns)) {
            throwsTypes = new ArrayList<>(throwsTypes);
            throwsTypes.add("Error");
        }
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        String receiver = isStatic ? hostAlias : "host";
        String witness = generic ? "[" + String.join(", ", typeVarNames(method)) + "]" : "";
        String call = receiver + "." + method.getName() + witness + "(" + hostArguments(params) + ")";
        String indentPrefix = generic ? "    " : "";

        StringBuilder body = new StringBuilder();
        if (generic) {
            body.append("generic ").append(String.join(", ", typeVarNames(method))).append(":\n");
        }
        body.append(indentPrefix).append("func $NAME$(").append(parameterList(params)).append(")");
        body.append(returns.kind == Kind.UNIT ? " -> Unit" : " -> " + returns.sprigType);
        appendThrows(body, throwsTypes);
        body.append(":\n");
        appendPreparations(body, params, indentPrefix);
        String pad = indentPrefix + "    ";
        if (returns.kind == Kind.UNIT) {
            body.append(pad).append(call).append('\n');
        } else if (returns.kind == Kind.DIRECT) {
            body.append(pad).append("return ").append(call).append('\n');
        } else if (returns.kind == Kind.HOST) {
            body.append(pad).append("let result = ").append(call).append('\n');
            body.append(pad).append("if result != null:\n");
            body.append(pad).append("    return ").append(simpleName(clazz)).append("(host=result)\n");
            body.append(pad).append("return null\n");
        } else if (returns.kind == Kind.OPTIONAL) {
            body.append(pad).append("let result = ").append(call).append('\n');
            body.append(pad).append("if result == null:\n");
            body.append(pad).append("    return null\n");
            body.append(pad).append("if result.isPresent():\n");
            body.append(pad).append("    let value = result.get()\n");
            body.append(pad).append("    if value != null:\n");
            body.append(pad).append("        return value\n");
            body.append(pad).append("return null\n");
        } else {
            imports.useAdapters();
            ctx.adapters = true;
            body.append(pad).append("let result = ").append(call).append('\n');
            body.append(pad).append("if result == null:\n");
            body.append(pad).append("    return null\n");
            String adapter = returns.kind == Kind.LIST ? "jvm.list_snapshot" : "jvm.map_snapshot";
            body.append(pad).append("return ").append(adapter).append('[').append(returns.element)
                    .append(returns.kind == Kind.MAP ? ", " + returns.element2 : "")
                    .append("](result)\n");
        }
        return new Plan(method.toGenericString(), identifier(method.getName()), isStatic, params,
                returns, throwsTypes, generic, typeVarNames(method), body.toString());
    }

    private static Plan planField(Class<?> clazz, String hostAlias, Field field, PlanContext ctx) {
        Imports imports = ctx.imports;
        Type mapped = JavaTypes.mapValue(field.getGenericType(), field.getType(), Map.of());
        String sprigType = render(mapped, imports, Map.of());
        if (sprigType == null) {
            throw new UnsupportedShape(List.of("raw-generic-boundary"),
                    "field type cannot be represented in Sprig source");
        }
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        String access = (isStatic ? hostAlias : "host") + "." + field.getName();
        String className = simpleName(clazz);
        if (mapped instanceof JavaType fieldType && fieldType.clazz == clazz) {
            String sprigHost = className + "?";
            String hostBody = "func $NAME$() -> " + sprigHost + ":\n"
                    + "    let value = " + access + "\n"
                    + "    if value != null:\n"
                    + "        return " + className + "(host=value)\n"
                    + "    return null\n";
            return new Plan(field.toGenericString(), identifier(field.getName()), isStatic, List.of(),
                    new ReturnShape(Kind.HOST, sprigHost, className, null), List.of(), false, List.of(), hostBody);
        }
        String body = "func $NAME$() -> " + sprigType + ":\n    return " + access + "\n";
        return new Plan(field.toGenericString(), identifier(field.getName()), isStatic, List.of(),
                new ReturnShape(Kind.DIRECT, sprigType, null, null), List.of(), false, List.of(), body);
    }

    private static boolean requiresError(List<Param> params, ReturnShape returns) {
        if (returns != null && (returns.kind == Kind.LIST || returns.kind == Kind.MAP)) return true;
        for (Param param : params) {
            if (param.conversion.kind != Kind.DIRECT) return true;
        }
        return false;
    }

    private static void appendThrows(StringBuilder out, List<String> throwsTypes) {
        if (!throwsTypes.isEmpty()) {
            out.append(" throws ").append(String.join(", ", throwsTypes));
        }
    }

    private static void appendPreparations(StringBuilder body, List<Param> params, String indentPrefix) {
        String optionalHelperName = OPTIONAL_HELPER;
        String pad = indentPrefix + "    ";
        for (Param param : params) {
            switch (param.conversion.kind) {
                case DIRECT, UNIT -> { }
                case OPTIONAL -> body.append(pad).append("let host_").append(param.name)
                        .append(" = ").append(optionalHelperName).append('[')
                        .append(param.conversion.element).append("](").append(param.name).append(")\n");
                case LIST -> body.append(pad).append("let host_").append(param.name)
                        .append(" = jvm.list_copy[").append(param.conversion.element).append("](")
                        .append(param.name).append(")\n");
                case MAP -> body.append(pad).append("let host_").append(param.name)
                        .append(" = jvm.map_copy[").append(param.conversion.element).append(", ")
                        .append(param.conversion.element2).append("](").append(param.name).append(")\n");
            }
        }
    }

    private static final String OPTIONAL_HELPER = "wrap_optional_of";

    private static String optionalHelper(Imports imports) {
        String name = OPTIONAL_HELPER;
        String alias = imports.alias(java.util.Optional.class);
        return "generic E:\n"
                + "    func " + name + "(value: E?) -> " + alias + "[E] throws Error:\n"
                + "        if value == null:\n"
                + "            let empty = " + alias + ".empty[E]()\n"
                + "            if empty != null:\n"
                + "                return empty\n"
                + "            throw Error(\"" + alias + ".empty returned null\")\n"
                + "        let present = " + alias + ".of[E](value)\n"
                + "        if present != null:\n"
                + "            return present\n"
                + "        throw Error(\"" + alias + ".of returned null\")\n";
    }

    private static String hostArguments(List<Param> params) {
        List<String> args = new ArrayList<>();
        for (Param param : params) {
            args.add(param.conversion.kind == Kind.DIRECT ? param.name : "host_" + param.name);
        }
        return String.join(", ", args);
    }

    private static String parameterList(List<Param> params) {
        List<String> out = new ArrayList<>();
        for (Param param : params) {
            out.add(param.name + ": " + param.sprigType);
        }
        return String.join(", ", out);
    }

    private static List<Param> planParams(Executable executable, PlanContext ctx) {
        Imports imports = ctx.imports;
        List<Param> params = new ArrayList<>();
        Class<?>[] raw = executable.getParameterTypes();
        java.lang.reflect.Type[] generic = executable.getGenericParameterTypes();
        Map<TypeVariable<?>, Type> bindings = methodBindings(executable);
        for (int i = 0; i < raw.length; i++) {
            Type formal = JavaTypes.mapFormal(generic[i], raw[i], bindings);
            String name = "value" + (i + 1);
            if (raw[i].isArray()) {
                throw new UnsupportedShape(List.of("array-source-syntax-unavailable"),
                        "array parameters cannot be named in Sprig source");
            }
            if (raw[i] == char.class || raw[i] == Character.class
                    || raw[i] == short.class || raw[i] == byte.class
                    || raw[i] == Short.class || raw[i] == Byte.class) {
                throw new UnsupportedShape(List.of(VALUE_ADAPTER_UNSUPPORTED),
                        "direct char/Short/Byte parameters need literal adapters and are outside the generator profile");
            }
            if (formal instanceof FunctionType) {
                throw new UnsupportedShape(List.of("sprig-callable-boundary"),
                        "callable parameters are not generated in v1");
            }
            if (formal instanceof JavaType charSequence
                    && charSequence.clazz == CharSequence.class && charSequence.args.isEmpty()) {
                // Every Sprig String is a CharSequence; the generated direct call
                // performs the existing interop conversion, no new semantics.
                params.add(new Param(name, "String", token(formal, bindings),
                        new Conversion(Kind.DIRECT, null, null), null));
                continue;
            }
            if (formal instanceof JavaType javaType) {
                if (javaType.clazz == java.util.Optional.class && javaType.args.size() == 1) {
                    Type element = javaType.args.get(0);
                    String rendered = render(element, imports, bindings);
                    if (rendered == null || !adapterElement(element)) {
                        throw new UnsupportedShape(List.of(NESTED_COLLECTION_UNSUPPORTED),
                                "Optional element shape is outside the generator adapter profile");
                    }
                    String alias = imports.alias(java.util.Optional.class);
                    ctx.optionalHelper = true;
                    params.add(new Param(name, rendered + "?", "optional_" + token(element, bindings),
                            new Conversion(Kind.OPTIONAL, rendered, null), alias));
                    continue;
                }
                if (javaType.clazz == java.util.List.class && javaType.args.size() == 1) {
                    Type element = javaType.args.get(0);
                    String rendered = render(element, imports, bindings);
                    if (rendered == null || !adapterElement(element)) {
                        throw new UnsupportedShape(List.of(NESTED_COLLECTION_UNSUPPORTED),
                                "List element shape is outside the generator adapter profile");
                    }
                    imports.useAdapters();
                    ctx.adapters = true;
                    params.add(new Param(name, "List[" + rendered + "]", "list_" + token(element, bindings),
                            new Conversion(Kind.LIST, rendered, null), null));
                    continue;
                }
                if (javaType.clazz == java.util.Map.class && javaType.args.size() == 2) {
                    Type key = javaType.args.get(0);
                    Type value = javaType.args.get(1);
                    String renderedKey = render(key, imports, bindings);
                    String renderedValue = render(value, imports, bindings);
                    if (renderedKey == null || renderedValue == null
                            || !adapterElement(key) || !adapterElement(value)) {
                        throw new UnsupportedShape(List.of(NESTED_COLLECTION_UNSUPPORTED),
                                "Map key/value shape is outside the generator adapter profile");
                    }
                    imports.useAdapters();
                    ctx.adapters = true;
                    params.add(new Param(name, "Map[" + renderedKey + ", " + renderedValue + "]",
                            "map_" + token(key, bindings) + "_" + token(value, bindings),
                            new Conversion(Kind.MAP, renderedKey, renderedValue), null));
                    continue;
                }
            }
            String sprigType = render(formal, imports, bindings);
            if (sprigType == null) {
                throw new UnsupportedShape(List.of("raw-generic-boundary"),
                        "parameter type cannot be represented in Sprig source");
            }
            params.add(new Param(name, sprigType, token(formal, bindings),
                    new Conversion(Kind.DIRECT, null, null), null));
        }
        return params;
    }

    private static ReturnShape planReturn(Method method, Class<?> hostClass, String className,
                                          PlanContext ctx) {
        Imports imports = ctx.imports;
        Map<TypeVariable<?>, Type> bindings = methodBindings(method);
        Type mapped = JavaTypes.mapValue(method.getGenericReturnType(), method.getReturnType(), bindings);
        if (method.getReturnType() == void.class || mapped == NativeType.UNIT) {
            return ReturnShape.unit();
        }
        if (method.getReturnType().isArray()) {
            throw new UnsupportedShape(List.of("array-source-syntax-unavailable"),
                    "array results cannot be named in Sprig source");
        }
        Type baseMapped = mapped instanceof NullableType nullable ? nullable.inner : mapped;
        if (baseMapped instanceof JavaType hostType && hostType.clazz == hostClass) {
            return new ReturnShape(Kind.HOST, className + "?", className, null);
        }
        if (baseMapped instanceof JavaType) {
            JavaType javaType = (JavaType) baseMapped;
                if (javaType.clazz == java.util.Optional.class && javaType.args.size() == 1) {
                    Type element = javaType.args.get(0);
                    String rendered = render(element, imports, bindings);
                    if (rendered == null || !adapterElement(element)) {
                        throw new UnsupportedShape(List.of(NESTED_COLLECTION_UNSUPPORTED),
                                "Optional element shape is outside the generator adapter profile");
                    }
                    imports.alias(java.util.Optional.class);
                    return new ReturnShape(Kind.OPTIONAL, rendered + "?", rendered, null);
                }
                if (javaType.clazz == java.util.List.class && javaType.args.size() == 1) {
                    Type element = javaType.args.get(0);
                    String rendered = render(element, imports, bindings);
                    if (rendered == null || !adapterElement(element)) {
                        throw new UnsupportedShape(List.of(NESTED_COLLECTION_UNSUPPORTED),
                                "List element shape is outside the generator adapter profile");
                    }
                    imports.useAdapters();
                    ctx.adapters = true;
                    return new ReturnShape(Kind.LIST, "List[" + rendered + "]?", rendered, null);
                }
                if (javaType.clazz == java.util.Map.class && javaType.args.size() == 2) {
                    Type key = javaType.args.get(0);
                    Type value = javaType.args.get(1);
                    String renderedKey = render(key, imports, bindings);
                    String renderedValue = render(value, imports, bindings);
                    if (renderedKey == null || renderedValue == null
                            || !adapterElement(key) || !adapterElement(value)) {
                        throw new UnsupportedShape(List.of(NESTED_COLLECTION_UNSUPPORTED),
                                "Map key/value shape is outside the generator adapter profile");
                    }
                    imports.useAdapters();
                    ctx.adapters = true;
                    return new ReturnShape(Kind.MAP, "Map[" + renderedKey + ", " + renderedValue + "]?",
                            renderedKey, renderedValue);
                }
        }
        if (mapped instanceof FunctionType) {
            throw new UnsupportedShape(List.of("sprig-callable-boundary"),
                    "callable results are not generated in v1");
        }
        String sprigType = render(mapped, imports, bindings);
        if (sprigType == null) {
            throw new UnsupportedShape(List.of("raw-generic-boundary"),
                    "result type cannot be represented in Sprig source");
        }
        return new ReturnShape(Kind.DIRECT, sprigType, null, null);
    }

    /** Adapter elements must be directly representable, never nested collections. */
    private static boolean adapterElement(Type type) {
        Type base = type instanceof NullableType nullable ? nullable.inner : type;
        if (base instanceof JavaType javaType) {
            if (javaType.clazz.isArray()) return false;
            return !NESTED_COLLECTIONS.contains(javaType.clazz.getName());
        }
        if (base instanceof ListType || base instanceof MapType || base instanceof FunctionType) {
            return false;
        }
        return base != null && base != NativeType.ERROR && base != NativeType.UNIT && base != NativeType.NULL;
    }

    private static String token(Type type, Map<TypeVariable<?>, Type> bindings) {
        Type base = type instanceof NullableType nullable ? nullable.inner : type;
        if (base == NativeType.INT) return "int";
        if (base == NativeType.INT32) return "int32";
        if (base == NativeType.FLOAT) return "float";
        if (base == NativeType.FLOAT32) return "float32";
        if (base == NativeType.BOOL) return "bool";
        if (base == NativeType.STRING) return "string";
        if (base == NativeType.DECIMAL) return "decimal";
        if (base == NativeType.BIGINT) return "bigint";
        if (base instanceof TypeParameterType parameter) return snake(parameter.name);
        if (base instanceof JavaType javaType) {
            StringBuilder out = new StringBuilder(snake(simpleName(javaType.clazz)));
            for (Type arg : javaType.args) {
                out.append('_').append(token(arg, bindings));
            }
            return out.toString();
        }
        return "value";
    }

    /** Renders a mapped type as Sprig source, registering imports; null when unsupported. */
    private static String render(Type type, Imports imports, Map<TypeVariable<?>, Type> bindings) {
        if (type == null || type == NativeType.ERROR || type == NativeType.NULL || type == NativeType.UNIT) {
            return null;
        }
        if (type instanceof NullableType nullable) {
            String inner = render(nullable.inner, imports, bindings);
            return inner == null ? null : inner + "?";
        }
        if (type == NativeType.INT) return "Int";
        if (type == NativeType.INT32) return "Int32";
        if (type == NativeType.FLOAT) return "Float";
        if (type == NativeType.FLOAT32) return "Float32";
        if (type == NativeType.BOOL) return "Bool";
        if (type == NativeType.STRING) return "String";
        if (type == NativeType.DECIMAL) return "Decimal";
        if (type == NativeType.BIGINT) return "BigInt";
        if (type instanceof TypeParameterType parameter) return parameter.name;
        if (type instanceof ListType list) {
            String element = render(list.element, imports, bindings);
            if (element == null) return null;
            return (list.mutable ? "MutableList[" : "List[") + element + "]";
        }
        if (type instanceof MapType map) {
            String key = render(map.key, imports, bindings);
            String value = render(map.value, imports, bindings);
            if (key == null || value == null) return null;
            return (map.mutable ? "MutableMap[" : "Map[") + key + ", " + value + "]";
        }
        if (type instanceof JavaType javaType) {
            if (javaType.clazz.isArray()) return null;
            StringBuilder out = new StringBuilder(imports.alias(javaType.clazz));
            if (!javaType.args.isEmpty()) {
                List<String> args = new ArrayList<>();
                for (Type arg : javaType.args) {
                    String rendered = render(arg, imports, bindings);
                    if (rendered == null) return null;
                    args.add(rendered);
                }
                out.append('[').append(String.join(", ", args)).append(']');
            }
            return javaType.isNullable() ? out.append('?').toString() : out.toString();
        }
        return null;
    }

    private static List<String> throwsTypes(Executable executable, Imports imports, List<Param> params) {
        List<String> out = new ArrayList<>();
        for (Class<?> exception : executable.getExceptionTypes()) {
            if (RuntimeException.class.isAssignableFrom(exception) || Error.class.isAssignableFrom(exception)) {
                continue;
            }
            String alias = imports.alias(exception);
            if (!out.contains(alias)) out.add(alias);
        }
        return out;
    }
}
