package sprig.compiler.sem;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Module;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.types.ClassType;
import sprig.compiler.types.EnumType;
import sprig.compiler.types.FunctionType;
import sprig.compiler.types.JavaType;
import sprig.compiler.types.ListType;
import sprig.compiler.types.MapType;
import sprig.compiler.types.NativeType;
import sprig.compiler.types.NullableType;
import sprig.compiler.types.Type;
import sprig.compiler.types.VariantCaseType;
import sprig.compiler.types.VariantType;

/**
 * Validates {@code conform C to J} declarations: the Sprig class must already
 * satisfy every supported abstract instance requirement of the imported Java
 * interface. No methods are created and no adaptation happens; a successful
 * declaration records the foreign assignability edge and the interface to emit.
 *
 * Requirements are the effective Java contract, not raw reflection order: a
 * declaration in a subinterface (including a default method) shadows the one it
 * overrides, covariant returns collapse to the most-derived declaration, and
 * inherited checked exceptions come from the effective declaration. Public
 * concrete {@code java.lang.Object} methods satisfy matching requirements, as
 * they do for any Java class.
 */
public final class ConformanceChecker {
    private final Diagnostics diagnostics;

    public ConformanceChecker(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public void check(Module module) {
        for (Decl decl : module.decls) {
            if (decl instanceof Decl.Conform conform) {
                check(module, conform);
            }
        }
    }

    private void check(Module module, Decl.Conform conform) {
        if (!source(module, conform) || !target(module, conform)) {
            return;
        }
        Decl.ClassDecl classDecl = conform.source;
        Class<?> target = conform.target;
        if (classDecl.conformedInterfaces.contains(target)) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_TARGET, Phase.TYPE,
                    "Class '" + classDecl.name + "' already conforms to " + target.getName(),
                    module.uri, conform.span)
                    .withHint("Keep one conform declaration per interface."));
            return;
        }
        Map<String, List<Method>> declarations = new LinkedHashMap<>();
        collect(target, declarations);
        Map<String, Requirement> requirements = effective(declarations);
        List<String> overloaded = new ArrayList<>();
        Map<String, Integer> byName = new LinkedHashMap<>();
        for (Requirement requirement : requirements.values()) {
            byName.merge(requirement.name(), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : byName.entrySet()) {
            if (entry.getValue() > 1) {
                overloaded.add(entry.getKey());
            }
        }
        if (!overloaded.isEmpty()) {
            overloaded.sort(String::compareTo);
            diagnostics.add(Diagnostic.error(Codes.CONFORM_OVERLOAD, Phase.TYPE,
                    "Java interface " + target.getName() + " requires overloaded abstract methods ("
                            + String.join(", ", overloaded) + "); Sprig classes cannot represent this contract",
                    module.uri, conform.span)
                    .withHint("Use a Java interface whose abstract methods have unique names."));
            return;
        }
        boolean ok = true;
        for (Requirement requirement : requirements.values()) {
            if (satisfiedByObject(requirement)) {
                continue; // inherited public Object method; boundary marking below
            }
            ok &= witness(module.uri, classDecl, target, requirement);
        }
        if (!ok) {
            return; // no state is mutated until every requirement is satisfied
        }
        for (Requirement requirement : requirements.values()) {
            markBoundary(classDecl, requirement);
        }
        classDecl.conformedInterfaces.add(target);
    }

    // ------------------------------------------------------------------
    // Effective Java contract
    // ------------------------------------------------------------------

    private record Requirement(String name, Class<?>[] params, Class<?> returnType,
            Set<Class<?>> permitted) {}

    /** Collect every abstract/default/static-free declaration in the hierarchy. */
    private void collect(Class<?> iface, Map<String, List<Method>> out) {
        for (Class<?> parent : iface.getInterfaces()) {
            collect(parent, out);
        }
        for (Method method : iface.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || Modifier.isPrivate(method.getModifiers())
                    || method.isBridge() || method.isSynthetic()) {
                continue;
            }
            out.computeIfAbsent(key(method), ignored -> new ArrayList<>()).add(method);
        }
    }

    private static String key(Method method) {
        StringBuilder out = new StringBuilder(method.getName()).append('(');
        for (Class<?> param : method.getParameterTypes()) {
            out.append(param.getName()).append(',');
        }
        return out.append(')').toString();
    }

    /**
     * Reduce declarations to the effective contract: a declaration in a strict
     * subinterface shadows the one it overrides, so covariance collapses and
     * child throws/default declarations win. Default-only signatures need no
     * witness; when any maximal declaration is abstract, a witness is required
     * and the permitted checked exceptions are the intersection of every
     * maximal declaration.
     */
    private Map<String, Requirement> effective(Map<String, List<Method>> declarations) {
        Map<String, Requirement> out = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>(declarations.keySet());
        keys.sort(String::compareTo);
        for (String key : keys) {
            List<Method> maximal = maximal(declarations.get(key));
            if (maximal.stream().noneMatch(ConformanceChecker::isAbstract)) {
                continue;
            }
            List<Method> abstractMaximal = new ArrayList<>();
            for (Method method : maximal) {
                if (isAbstract(method)) {
                    abstractMaximal.add(method);
                }
            }
            abstractMaximal.sort((left, right) -> {
                int byOwner = left.getDeclaringClass().getName().compareTo(right.getDeclaringClass().getName());
                return byOwner != 0 ? byOwner : left.toGenericString().compareTo(right.toGenericString());
            });
            Method chosen = abstractMaximal.get(0);
            Set<Class<?>> permitted = null;
            for (Method method : maximal) {
                Set<Class<?>> exceptions = new LinkedHashSet<>(List.of(method.getExceptionTypes()));
                if (permitted == null) {
                    permitted = exceptions;
                } else {
                    permitted.retainAll(exceptions);
                }
            }
            out.put(key, new Requirement(chosen.getName(), chosen.getParameterTypes(),
                    chosen.getReturnType(), permitted == null ? Set.of() : permitted));
        }
        return out;
    }

    private static List<Method> maximal(List<Method> all) {
        List<Method> out = new ArrayList<>();
        for (Method candidate : all) {
            boolean shadowed = false;
            for (Method other : all) {
                if (other == candidate || other.getDeclaringClass() == candidate.getDeclaringClass()) {
                    continue;
                }
                if (candidate.getDeclaringClass().isAssignableFrom(other.getDeclaringClass())) {
                    shadowed = true;
                    break;
                }
            }
            if (!shadowed) {
                out.add(candidate);
            }
        }
        return out;
    }

    private static boolean isAbstract(Method method) {
        return Modifier.isAbstract(method.getModifiers());
    }

    /** Public concrete Object methods implement matching interface methods. */
    private static boolean satisfiedByObject(Requirement requirement) {
        try {
            Method inherited = Object.class.getMethod(requirement.name(), requirement.params());
            return !Modifier.isAbstract(inherited.getModifiers())
                    && inherited.getReturnType().equals(requirement.returnType());
        } catch (NoSuchMethodException | SecurityException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Source and target restrictions
    // ------------------------------------------------------------------

    private boolean source(Module module, Decl.Conform conform) {
        Symbol symbol = module.scope.types.get(conform.sourceName);
        if (symbol == null || symbol.kind != Symbol.Kind.CLASS
                || !(symbol.decl instanceof Decl.ClassDecl classDecl) || symbol.module != module) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_SOURCE, Phase.TYPE,
                    "conform source '" + conform.sourceName
                            + "' must be a Sprig class declared in this module",
                    module.uri, conform.span)
                    .withHint("Declare conform next to a local class; dependency types cannot be conformed."));
            return false;
        }
        if (!classDecl.typeParams.isEmpty()) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_SOURCE, Phase.TYPE,
                    "Generic class '" + classDecl.name
                            + "' cannot declare a foreign JVM conformance in v1",
                    module.uri, conform.span)
                    .withHint("Use a non-generic class, or adapt through composition."));
            return false;
        }
        conform.source = classDecl;
        return true;
    }

    private boolean target(Module module, Decl.Conform conform) {
        Symbol imported = module.scope.importAliases.get(conform.targetAlias);
        if (imported == null || imported.kind != Symbol.Kind.JAVA_TYPE || imported.javaClass == null) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_TARGET, Phase.TYPE,
                    "conform target '" + conform.targetAlias + "' must be an imported Java interface",
                    module.uri, conform.span)
                    .withHint("Add: import java.lang.Runnable as Runnable"));
            return false;
        }
        Class<?> target = imported.javaClass;
        if (target.isAnnotation()) {
            return rejectTarget(module, conform, target, "annotation interfaces cannot be implemented");
        }
        if (!target.isInterface()) {
            return rejectTarget(module, conform, target,
                    "only Java interfaces are supported in v1; abstract classes and ordinary classes are rejected");
        }
        if (target.getTypeParameters().length > 0 || genericAncestor(target)) {
            return rejectTarget(module, conform, target, "generic Java interfaces are rejected in v1");
        }
        if (target.isSealed()) {
            return rejectTarget(module, conform, target,
                    "this interface is sealed and does not allow implementations outside its permitted set");
        }
        if (!Modifier.isPublic(target.getModifiers())) {
            return rejectTarget(module, conform, target,
                    "this interface is not public; generated code could not implement it");
        }
        conform.target = target;
        return true;
    }

    private boolean rejectTarget(Module module, Decl.Conform conform, Class<?> target, String because) {
        diagnostics.add(Diagnostic.error(Codes.CONFORM_TARGET, Phase.TYPE,
                "conform target " + target.getName() + " is not a supported Java interface: " + because,
                module.uri, conform.span)
                .withHint("Import a public, non-generic, non-sealed Java interface with unique abstract method names."));
        return false;
    }

    private boolean genericAncestor(Class<?> iface) {
        for (Class<?> parent : iface.getInterfaces()) {
            if (parent.getTypeParameters().length > 0 || genericAncestor(parent)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Witness verification
    // ------------------------------------------------------------------

    private boolean witness(String uri, Decl.ClassDecl classDecl, Class<?> target,
            Requirement requirement) {
        Decl.Func method = null;
        for (Decl.Func candidate : classDecl.methods) {
            if (candidate.name.equals(requirement.name())) {
                method = candidate;
                break;
            }
        }
        if (method == null) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_MEMBER, Phase.TYPE,
                    "Class '" + classDecl.name + "' has no method '" + requirement.name()
                            + "' required by " + target.getName(),
                    uri, classDecl.span)
                    .withHint("Add the missing method, or adapt through a separate class."));
            return false;
        }
        Class<?>[] expectedParams = requirement.params();
        if (method.params.size() != expectedParams.length) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_MEMBER, Phase.TYPE,
                    "Method '" + method.name + "' takes " + method.params.size()
                            + " parameter(s), but " + target.getName() + "." + requirement.name()
                            + " requires " + expectedParams.length,
                    uri, method.span)
                    .withTypes(Integer.toString(expectedParams.length), Integer.toString(method.params.size())));
            return false;
        }
        for (int i = 0; i < expectedParams.length; i++) {
            String expected = shape(expectedParams[i]);
            String actual = shape(method.params.get(i).type);
            if (!expected.equals(actual)) {
                diagnostics.add(Diagnostic.error(Codes.CONFORM_MEMBER, Phase.TYPE,
                        "Method '" + method.name + "' parameter " + (i + 1)
                                + " does not match " + target.getName() + "." + requirement.name(),
                        uri, method.span)
                        .withTypes(expected, actual)
                        .withHint("Match the Java signature exactly: name, arity and JVM shapes."));
                return false;
            }
        }
        String expectedReturn = shape(requirement.returnType());
        String actualReturn = shape(method.returnType);
        if (!expectedReturn.equals(actualReturn)) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_MEMBER, Phase.TYPE,
                    "Method '" + method.name + "' return shape does not match "
                            + target.getName() + "." + requirement.name(),
                    uri, method.span)
                    .withTypes(expectedReturn, actualReturn)
                    .withHint("Match the Java signature exactly: name, arity and JVM shapes."));
            return false;
        }
        return effects(uri, method, target, requirement);
    }

    private boolean effects(String uri, Decl.Func method, Class<?> target, Requirement requirement) {
        for (Type thrown : method.throwsTypes) {
            if (thrown == null || thrown == NativeType.ERROR) {
                continue; // SprigError is unchecked at the JVM boundary
            }
            if (!(thrown instanceof JavaType javaType)) {
                diagnostics.add(Diagnostic.error(Codes.CONFORM_EFFECTS, Phase.TYPE,
                        "Method '" + method.name + "' declares throws " + thrown.display()
                                + ", which " + target.getName() + "." + requirement.name()
                                + " cannot accept",
                        uri, method.span));
                return false;
            }
            Class<?> exception = javaType.clazz;
            boolean checked = Throwable.class.isAssignableFrom(exception)
                    && !RuntimeException.class.isAssignableFrom(exception)
                    && !Error.class.isAssignableFrom(exception);
            if (!checked) {
                continue;
            }
            boolean permitted = false;
            for (Class<?> allowed : requirement.permitted()) {
                if (allowed.isAssignableFrom(exception)) {
                    permitted = true;
                    break;
                }
            }
            if (!permitted) {
                diagnostics.add(Diagnostic.error(Codes.CONFORM_EFFECTS, Phase.TYPE,
                        "Method '" + method.name + "' declares checked " + exception.getName()
                                + ", but " + target.getName() + "." + requirement.name()
                                + " does not permit it",
                        uri, method.span)
                        .withHint("Remove the throws clause or catch the exception inside the method."));
                return false;
            }
        }
        return true;
    }

    /** Marks a declared Sprig method that overrides a requirement for the boundary guard. */
    private static void markBoundary(Decl.ClassDecl classDecl, Requirement requirement) {
        for (Decl.Func method : classDecl.methods) {
            if (!method.name.equals(requirement.name()) || method.params.size() != requirement.params().length) {
                continue;
            }
            boolean match = true;
            for (int i = 0; i < method.params.size(); i++) {
                if (!shape(requirement.params()[i]).equals(shape(method.params.get(i).type))) {
                    match = false;
                    break;
                }
            }
            if (match && shape(requirement.returnType()).equals(shape(method.returnType))) {
                method.foreignBoundary = true;
            }
        }
    }

    /** JVM shape of a Sprig type, as a stable comparable name. */
    static String shape(Type type) {
        if (type == null || type == NativeType.ERROR) {
            return "java.lang.Object";
        }
        boolean nullable = type instanceof NullableType;
        Type base = nullable ? ((NullableType) type).inner : type;
        if (base == NativeType.INT) return nullable ? "java.lang.Long" : "long";
        if (base == NativeType.INT32) return nullable ? "java.lang.Integer" : "int";
        if (base == NativeType.FLOAT) return nullable ? "java.lang.Double" : "double";
        if (base == NativeType.FLOAT32) return nullable ? "java.lang.Float" : "float";
        if (base == NativeType.BOOL) return nullable ? "java.lang.Boolean" : "boolean";
        if (base == NativeType.STRING) return "java.lang.String";
        if (base == NativeType.DECIMAL) return "sprig.runtime.SprigDecimal";
        if (base == NativeType.BIGINT) return "sprig.runtime.SprigBigInt";
        if (base == NativeType.UNIT) return "void";
        if (base instanceof ListType list) {
            return list.mutable ? "sprig.runtime.SprigMutableList" : "sprig.runtime.SprigList";
        }
        if (base instanceof MapType map) {
            return map.mutable ? "sprig.runtime.SprigMutableMap" : "sprig.runtime.SprigMap";
        }
        if (base instanceof JavaType javaType) return javaType.clazz.getName();
        if (base instanceof FunctionType function) {
            return "sprig.runtime.Fn" + function.params.size();
        }
        if (base instanceof ClassType classType) return "sprig.class:" + classType.decl.name;
        if (base instanceof EnumType enumType) return "sprig.enum:" + enumType.decl.name;
        if (base instanceof VariantType variantType) return "sprig.variant:" + variantType.decl.name;
        if (base instanceof VariantCaseType caseType) {
            return "sprig.variant:" + caseType.variant.name + "." + caseType.variantCase.name;
        }
        return "java.lang.Object";
    }

    private static String shape(Class<?> clazz) {
        if (clazz == void.class) return "void";
        if (clazz == long.class) return "long";
        if (clazz == int.class) return "int";
        if (clazz == short.class) return "short";
        if (clazz == byte.class) return "byte";
        if (clazz == double.class) return "double";
        if (clazz == float.class) return "float";
        if (clazz == boolean.class) return "boolean";
        if (clazz == char.class) return "char";
        return clazz.getName();
    }
}
