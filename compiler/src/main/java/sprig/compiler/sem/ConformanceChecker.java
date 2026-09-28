package sprig.compiler.sem;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        Map<String, Method> requirements = new LinkedHashMap<>();
        Map<String, List<String>> overloads = new LinkedHashMap<>();
        collect(target, requirements, overloads);
        List<String> overloaded = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : overloads.entrySet()) {
            if (entry.getValue().size() > 1) {
                overloaded.add(entry.getKey());
            }
        }
        if (!overloaded.isEmpty()) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_OVERLOAD, Phase.TYPE,
                    "Java interface " + target.getName() + " requires overloaded abstract methods ("
                            + String.join(", ", overloaded) + "); Sprig classes cannot represent this contract",
                    module.uri, conform.span)
                    .withHint("Use a Java interface whose abstract methods have unique names."));
            return;
        }
        boolean ok = true;
        for (Method required : requirements.values()) {
            ok &= witness(module.uri, classDecl, target, required);
        }
        if (!ok) {
            return;
        }
        for (Method required : requirements.values()) {
            for (Decl.Func method : classDecl.methods) {
                if (method.name.equals(required.getName())) {
                    method.foreignBoundary = true;
                }
            }
        }
        classDecl.conformedInterfaces.add(target);
    }

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

    private void collect(Class<?> iface, Map<String, Method> requirements,
            Map<String, List<String>> overloads) {
        for (Class<?> parent : iface.getInterfaces()) {
            collect(parent, requirements, overloads);
        }
        for (Method method : iface.getDeclaredMethods()) {
            if (!Modifier.isAbstract(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            String key = method.getName() + descriptor(method);
            if (requirements.putIfAbsent(key, method) == null) {
                overloads.computeIfAbsent(method.getName(), ignored -> new ArrayList<>()).add(key);
            }
        }
    }

    private boolean witness(String uri, Decl.ClassDecl classDecl, Class<?> target, Method required) {
        Decl.Func method = null;
        for (Decl.Func candidate : classDecl.methods) {
            if (candidate.name.equals(required.getName())) {
                method = candidate;
                break;
            }
        }
        if (method == null) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_MEMBER, Phase.TYPE,
                    "Class '" + classDecl.name + "' has no method '" + required.getName()
                            + "' required by " + target.getName(),
                    uri, classDecl.span)
                    .withHint("Add the missing method, or adapt through a separate class."));
            return false;
        }
        Class<?>[] expectedParams = required.getParameterTypes();
        if (method.params.size() != expectedParams.length) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_MEMBER, Phase.TYPE,
                    "Method '" + method.name + "' takes " + method.params.size()
                            + " parameter(s), but " + target.getName() + "." + required.getName()
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
                                + " does not match " + target.getName() + "." + required.getName(),
                        uri, method.span)
                        .withTypes(expected, actual)
                        .withHint("Match the Java signature exactly: name, arity and JVM shapes."));
                return false;
            }
        }
        String expectedReturn = shape(required.getReturnType());
        String actualReturn = shape(method.returnType);
        if (!expectedReturn.equals(actualReturn)) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_MEMBER, Phase.TYPE,
                    "Method '" + method.name + "' return shape does not match "
                            + target.getName() + "." + required.getName(),
                    uri, method.span)
                    .withTypes(expectedReturn, actualReturn)
                    .withHint("Match the Java signature exactly: name, arity and JVM shapes."));
            return false;
        }
        if (!effects(uri, method, target, required)) {
            return false;
        }
        return true;
    }

    private boolean effects(String uri, Decl.Func method, Class<?> target, Method required) {
        for (Type thrown : method.throwsTypes) {
            if (thrown == null || thrown == NativeType.ERROR) {
                continue; // SprigError is unchecked at the JVM boundary
            }
            if (!(thrown instanceof JavaType javaType)) {
                diagnostics.add(Diagnostic.error(Codes.CONFORM_EFFECTS, Phase.TYPE,
                        "Method '" + method.name + "' declares throws " + thrown.display()
                                + ", which " + target.getName() + "." + required.getName()
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
            for (Class<?> allowed : required.getExceptionTypes()) {
                if (allowed.isAssignableFrom(exception)) {
                    permitted = true;
                    break;
                }
            }
            if (!permitted) {
                diagnostics.add(Diagnostic.error(Codes.CONFORM_EFFECTS, Phase.TYPE,
                        "Method '" + method.name + "' declares checked " + exception.getName()
                                + ", but " + target.getName() + "." + required.getName()
                                + " does not permit it",
                        uri, method.span)
                        .withHint("Remove the throws clause or catch the exception inside the method."));
                return false;
            }
        }
        return true;
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

    private static String descriptor(Method method) {
        StringBuilder out = new StringBuilder("(");
        for (Class<?> param : method.getParameterTypes()) {
            out.append(shape(param)).append(',');
        }
        return out.append(')').append(shape(method.getReturnType())).toString();
    }
}
