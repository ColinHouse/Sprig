package sprig.compiler.sem;

import java.lang.reflect.Constructor;
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
import sprig.compiler.diag.Span;
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
 *
 * <p>{@code conform C to J(field, ...) as NAME} makes the generated class of
 * {@code C} extend the Java class {@code J}: the named fields select one of
 * its constructors, every abstract method of the chain needs a witness, a
 * method named like an inherited method must match one of its shapes exactly
 * (it overrides it) and {@code NAME} is the parent view that calls the
 * inherited implementation. Sprig itself gains no inheritance.
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
        if (conform.classTarget()) {
            checkClass(module, conform, classDecl, target);
            return;
        }
        if (conform.parentAlias != null) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_PARENT, Phase.TYPE,
                    "'as " + conform.parentAlias + "' names an inherited implementation, which the interface "
                            + target.getName() + " does not provide",
                    module.uri, conform.parentAliasSpan != null ? conform.parentAliasSpan : conform.span)
                    .withHint("Declare the parent view on the class conform: conform " + classDecl.name
                            + " to JavaClass(...) as " + conform.parentAlias + "."));
            return;
        }
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
    // Class targets: extend a Java class
    // ------------------------------------------------------------------

    private void checkClass(Module module, Decl.Conform conform, Decl.ClassDecl classDecl, Class<?> target) {
        String uri = module.uri;
        if (classDecl.superclass != null) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_TARGET, Phase.TYPE,
                    "Class '" + classDecl.name + "' already extends " + classDecl.superclass.getName()
                            + "; a class extends one Java class",
                    uri, conform.span)
                    .withHint("Keep one class conform per class; add interfaces with further conform declarations."));
            return;
        }
        List<Decl.Field> arguments = new ArrayList<>();
        boolean named = true;
        for (int i = 0; i < conform.superArguments.size(); i++) {
            String name = conform.superArguments.get(i);
            Decl.Field field = fieldNamed(classDecl, name);
            if (field == null) {
                diagnostics.add(Diagnostic.error(Codes.CONFORM_TARGET, Phase.TYPE,
                        "Constructor argument '" + name + "' is not a field of class " + classDecl.name,
                        uri, i < conform.superArgumentSpans.size() ? conform.superArgumentSpans.get(i) : conform.span)
                        .withHint("Name fields of the class; their values are passed to the "
                                + target.getSimpleName() + " constructor in this order."));
                named = false;
            } else {
                arguments.add(field);
            }
        }
        if (!named) {
            return;
        }
        Constructor<?> constructor = selectConstructor(uri, conform, target, arguments);
        if (constructor == null) {
            return;
        }
        if (conform.parentAlias != null && !parentAliasFree(uri, conform, classDecl)) {
            return;
        }
        Map<String, List<Method>> declarations = new LinkedHashMap<>();
        Map<String, Method> statics = new LinkedHashMap<>();
        collectChain(target, declarations, statics);
        Map<String, Requirement> contract = effectiveContract(declarations);
        boolean ok = true;
        for (Requirement requirement : contract.values()) {
            if (!requirement.isAbstract() || satisfiedByObject(requirement)
                    || methodNamed(classDecl, requirement.name()) != null) {
                continue; // a present method is checked as an override below
            }
            diagnostics.add(Diagnostic.error(Codes.CONFORM_MEMBER, Phase.TYPE,
                    "Class '" + classDecl.name + "' has no method '" + requirement.name()
                            + "' required by the abstract " + target.getName(),
                    uri, classDecl.span)
                    .withHint("Add the missing method with the Java signature; run sprig api "
                            + target.getName() + " --json to see it."));
            ok = false;
        }
        for (Decl.Func method : classDecl.methods) {
            ok &= override(uri, classDecl, target, method, contract, statics);
        }
        if (!ok) {
            return; // no state is mutated until every rule holds
        }
        for (Requirement requirement : contract.values()) {
            markBoundary(classDecl, requirement);
        }
        classDecl.superclass = target;
        classDecl.superConstructor = constructor;
        classDecl.superArguments.addAll(arguments);
    }

    /** The one accessible constructor whose parameter shapes the named fields match exactly. */
    private Constructor<?> selectConstructor(String uri, Decl.Conform conform, Class<?> target,
            List<Decl.Field> arguments) {
        List<String> written = new ArrayList<>();
        for (Decl.Field field : arguments) {
            written.add(shape(field.type));
        }
        List<String> available = new ArrayList<>();
        Constructor<?> match = null;
        for (Constructor<?> candidate : target.getDeclaredConstructors()) {
            int modifiers = candidate.getModifiers();
            if (!Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers)) {
                continue;
            }
            List<String> shapes = new ArrayList<>();
            for (Class<?> param : candidate.getParameterTypes()) {
                shapes.add(shape(param));
            }
            available.add("(" + String.join(", ", shapes) + ")");
            if (shapes.equals(written)) {
                match = candidate;
            }
        }
        if (match != null) {
            return match;
        }
        available.sort(String::compareTo);
        diagnostics.add(Diagnostic.error(Codes.CONFORM_TARGET, Phase.TYPE,
                target.getName() + " has no public or protected constructor taking ("
                        + String.join(", ", written) + ")",
                uri, conform.span)
                .withTypes(available.isEmpty() ? "no accessible constructor" : String.join(" | ", available),
                        "(" + String.join(", ", written) + ")")
                .withHint("Name fields whose JVM shapes match one constructor exactly, in its parameter order."));
        return null;
    }

    private boolean parentAliasFree(String uri, Decl.Conform conform, Decl.ClassDecl classDecl) {
        String alias = conform.parentAlias;
        Span span = conform.parentAliasSpan != null ? conform.parentAliasSpan : conform.span;
        if (fieldNamed(classDecl, alias) != null || methodNamed(classDecl, alias) != null) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_PARENT, Phase.TYPE,
                    "Parent view '" + alias + "' has the name of a member of class " + classDecl.name,
                    uri, span)
                    .withHint("Choose a name no field or method of the class uses."));
            return false;
        }
        return true;
    }

    /**
     * A method of the class against the chain: a name no inherited method has
     * is an ordinary Sprig method (unless it would hide a static), otherwise it
     * must match one inherited shape exactly and override a non-final method.
     */
    private boolean override(String uri, Decl.ClassDecl classDecl, Class<?> target, Decl.Func method,
            Map<String, Requirement> contract, Map<String, Method> statics) {
        List<Requirement> sameName = new ArrayList<>();
        for (Requirement requirement : contract.values()) {
            if (requirement.name().equals(method.name)) {
                sameName.add(requirement);
            }
        }
        if (sameName.isEmpty()) {
            for (Method hidden : statics.values()) {
                if (hidden.getName().equals(method.name)
                        && shapesMatch(method, hidden.getParameterTypes(), hidden.getReturnType())) {
                    diagnostics.add(Diagnostic.error(Codes.CONFORM_MEMBER, Phase.TYPE,
                            "Method '" + method.name + "' has the signature of the static "
                                    + hidden.getDeclaringClass().getName() + "." + method.name
                                    + "; an instance method cannot hide it",
                            uri, method.span)
                            .withHint("Rename the method."));
                    return false;
                }
            }
            return true;
        }
        Requirement match = null;
        for (Requirement requirement : sameName) {
            if (shapesMatch(method, requirement.params(), requirement.returnType())) {
                match = requirement;
                break;
            }
        }
        if (match == null) {
            if (sameName.size() == 1) {
                return witness(uri, classDecl, target, sameName.get(0)); // names the exact mismatch
            }
            List<String> shapes = new ArrayList<>();
            for (Requirement requirement : sameName) {
                shapes.add(signature(requirement));
            }
            diagnostics.add(Diagnostic.error(Codes.CONFORM_MEMBER, Phase.TYPE,
                    "Method '" + method.name + "' has the name of a " + target.getName()
                            + " method but none of its shapes; it would be a new overload, not an override",
                    uri, method.span)
                    .withHint("Match one of: " + String.join("; ", shapes) + "; or rename the method."));
            return false;
        }
        if (match.isFinal()) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_MEMBER, Phase.TYPE,
                    "Method '" + method.name + "' cannot override the final " + target.getName() + "." + method.name,
                    uri, method.span)
                    .withHint("Rename the method; a final Java method keeps its implementation."));
            return false;
        }
        return effects(uri, method, target, match);
    }

    private static boolean shapesMatch(Decl.Func method, Class<?>[] params, Class<?> returnType) {
        if (method.params.size() != params.length) {
            return false;
        }
        for (int i = 0; i < params.length; i++) {
            if (!shape(params[i]).equals(shape(method.params.get(i).type))) {
                return false;
            }
        }
        return shape(returnType).equals(shape(method.returnType));
    }

    private static String signature(Requirement requirement) {
        List<String> params = new ArrayList<>();
        for (Class<?> param : requirement.params()) {
            params.add(shape(param));
        }
        return requirement.name() + "(" + String.join(", ", params) + ") -> " + shape(requirement.returnType());
    }

    private static Decl.Field fieldNamed(Decl.ClassDecl classDecl, String name) {
        for (Decl.Field field : classDecl.fields) {
            if (field.name.equals(name)) {
                return field;
            }
        }
        return null;
    }

    private static Decl.Func methodNamed(Decl.ClassDecl classDecl, String name) {
        for (Decl.Func method : classDecl.methods) {
            if (method.name.equals(name)) {
                return method;
            }
        }
        return null;
    }

    /**
     * Every overridable declaration of a class chain and its interfaces, up
     * to and including {@code Object}; public or protected instance methods
     * only (package-private ones cannot be overridden from sprig.user).
     * Static methods are kept apart: an instance method cannot hide one.
     */
    private void collectChain(Class<?> target, Map<String, List<Method>> out, Map<String, Method> statics) {
        for (Class<?> current = target; current != null; current = current.getSuperclass()) {
            for (Class<?> parent : current.getInterfaces()) {
                collect(parent, out);
            }
            for (Method method : current.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                if (method.isBridge() || method.isSynthetic() || Modifier.isPrivate(modifiers)
                        || (!Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers))) {
                    continue;
                }
                if (Modifier.isStatic(modifiers)) {
                    statics.putIfAbsent(key(method), method);
                    continue;
                }
                out.computeIfAbsent(key(method), ignored -> new ArrayList<>()).add(method);
            }
        }
    }

    /**
     * The effective contract of a class chain: for every signature the most
     * derived declaration decides the shapes, whether a witness is required
     * (it is abstract) and whether an override is allowed (it is not final). A
     * class declaration wins over interface declarations of the same
     * signature, as Java resolves them; the permitted checked exceptions are
     * the intersection over the maximal declarations, as for interfaces.
     */
    private Map<String, Requirement> effectiveContract(Map<String, List<Method>> declarations) {
        Map<String, Requirement> out = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>(declarations.keySet());
        keys.sort(String::compareTo);
        for (String key : keys) {
            List<Method> maximal = maximal(declarations.get(key));
            maximal.sort((left, right) -> {
                int byOwner = left.getDeclaringClass().getName().compareTo(right.getDeclaringClass().getName());
                return byOwner != 0 ? byOwner : left.toGenericString().compareTo(right.toGenericString());
            });
            Method classDeclaration = null;
            for (Method method : maximal) {
                if (!method.getDeclaringClass().isInterface()) {
                    classDeclaration = method;
                    break;
                }
            }
            Method chosen = classDeclaration != null ? classDeclaration : maximal.get(0);
            boolean required = classDeclaration != null
                    ? isAbstract(classDeclaration)
                    : maximal.stream().anyMatch(ConformanceChecker::isAbstract);
            Set<Class<?>> permitted = null;
            for (Method method : maximal) {
                Set<Class<?>> exceptions = new LinkedHashSet<>(List.of(method.getExceptionTypes()));
                if (permitted == null) {
                    permitted = exceptions;
                } else {
                    permitted.retainAll(exceptions);
                }
            }
            out.put(key, new Requirement(chosen.getName(), chosen.getParameterTypes(), chosen.getReturnType(),
                    permitted == null ? Set.of() : permitted, required, Modifier.isFinal(chosen.getModifiers())));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Effective Java contract
    // ------------------------------------------------------------------

    private record Requirement(String name, Class<?>[] params, Class<?> returnType,
            Set<Class<?>> permitted, boolean isAbstract, boolean isFinal) {}

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
                    chosen.getReturnType(), permitted == null ? Set.of() : permitted, true, false));
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
        Class<?> target = null;
        if (imported != null && imported.kind == Symbol.Kind.JAVA_TYPE && imported.javaClass != null) {
            target = imported.javaClass;
        } else {
            // The built-in Error is the one target that needs no import: a class
            // that extends it (conform C to Error(message)) is an error class.
            Symbol builtin = module.scope.types.get(conform.targetAlias);
            if (builtin != null && builtin.kind == Symbol.Kind.BUILTIN_TYPE
                    && builtin.type instanceof sprig.compiler.types.JavaType javaType
                    && javaType.clazz == sprig.runtime.SprigError.class) {
                target = sprig.runtime.SprigError.class;
            }
        }
        if (target == null) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_TARGET, Phase.TYPE,
                    "conform target '" + conform.targetAlias
                            + "' must be an imported Java interface or class, or Error",
                    module.uri, conform.span)
                    .withHint("Add: import java.lang.Runnable as Runnable; an error class is declared with "
                            + "'conform " + conform.sourceName + " to Error(message)'."));
            return false;
        }
        if (target.isAnnotation()) {
            return rejectTarget(module, conform, target, "annotation interfaces cannot be implemented");
        }
        if (conform.classTarget()) {
            if (target.isInterface()) {
                return rejectTarget(module, conform, target, "it is an interface; write conform "
                        + conform.sourceName + " to " + conform.targetAlias + " without parentheses");
            }
            if (target.isEnum() || target.isRecord() || target.isArray() || target.isPrimitive()) {
                return rejectTarget(module, conform, target, "only a class can be extended");
            }
            if (Modifier.isFinal(target.getModifiers())) {
                return rejectTarget(module, conform, target, "the class is final");
            }
        } else if (!target.isInterface()) {
            return rejectTarget(module, conform, target, "it is a class; to extend it, name the fields its "
                    + "constructor takes: conform " + conform.sourceName + " to " + conform.targetAlias + "(field, ...)");
        }
        if (target.getTypeParameters().length > 0 || genericAncestor(target)) {
            return rejectTarget(module, conform, target, "generic Java "
                    + (target.isInterface() ? "interfaces" : "classes") + " are rejected in v1");
        }
        if (target.isSealed()) {
            return rejectTarget(module, conform, target,
                    "it is sealed and does not allow implementations outside its permitted set");
        }
        if (!Modifier.isPublic(target.getModifiers())) {
            return rejectTarget(module, conform, target,
                    "it is not public; generated code could not reach it");
        }
        conform.target = target;
        return true;
    }

    private boolean rejectTarget(Module module, Decl.Conform conform, Class<?> target, String because) {
        diagnostics.add(Diagnostic.error(Codes.CONFORM_TARGET, Phase.TYPE,
                "conform target " + target.getName() + " is not supported: " + because,
                module.uri, conform.span)
                .withHint("Import a public, non-generic, non-sealed Java interface with unique abstract method names, "
                        + "or extend a public, non-final, non-generic Java class with conform C to X(field, ...)."));
        return false;
    }

    private boolean genericAncestor(Class<?> type) {
        for (Class<?> parent : type.getInterfaces()) {
            if (parent.getTypeParameters().length > 0 || genericAncestor(parent)) {
                return true;
            }
        }
        Class<?> superclass = type.getSuperclass();
        return superclass != null && superclass != Object.class
                && (superclass.getTypeParameters().length > 0 || genericAncestor(superclass));
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
