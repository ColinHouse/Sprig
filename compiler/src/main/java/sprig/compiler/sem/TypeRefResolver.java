package sprig.compiler.sem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Expr;
import sprig.compiler.ast.Stmt;
import sprig.compiler.ast.Module;
import sprig.compiler.ast.TypeRef;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Newcomer;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.types.ClassType;
import sprig.compiler.types.JavaType;
import sprig.compiler.types.ListType;
import sprig.compiler.types.MapType;
import sprig.compiler.types.NativeType;
import sprig.compiler.types.NullableType;
import sprig.compiler.types.Type;
import sprig.compiler.types.TypeParameterType;
import sprig.compiler.types.VariantType;

/**
 * Resolves written {@link TypeRef}s to {@link Type}s. v0.8 adds a lexical
 * map of generic type parameters; {@code T} is only visible while the
 * declarations inside its {@code generic T:} block are resolved.
 */
public final class TypeRefResolver {
    /** Where reports go; a scratch sink while a declaration's types are resolved again. */
    private Diagnostics diagnostics;
    private final java.util.Map<Decl, List<Expr.Subscript>> applications = new java.util.HashMap<>();
    private Decl collecting;
    private final java.util.Set<Decl> validating = new java.util.HashSet<>();
    /**
     * Above zero while a declaration's written types are resolved again with
     * type arguments: an instantiation they contain reports where the
     * declaration writes it, and the outermost use moves the report to itself.
     */
    private int substituting;

    /** Why Float and Float32 are not Map keys; the same words wherever the rule is applied. */
    static final String FLOAT_MAP_KEY =
            "Float and Float32 cannot be Map keys: NaN and signed zero have no stable key equality";

    public TypeRefResolver(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public Type resolve(Module module, TypeRef ref) {
        return resolve(module, ref, Map.of(), false);
    }

    public Type resolve(Module module, TypeRef ref, Map<String, Type> typeParams) {
        return resolve(module, ref, typeParams, false);
    }

    public Type resolveReturn(Module module, TypeRef ref) {
        return resolve(module, ref, Map.of(), true);
    }

    public Type resolveReturn(Module module, TypeRef ref, Map<String, Type> typeParams) {
        return resolve(module, ref, typeParams, true);
    }

    private Type resolve(Module module, TypeRef ref, Map<String, Type> typeParams, boolean allowUnit) {
        if (ref == null) {
            return NativeType.ERROR;
        }
        Type result = resolveBase(module, ref, typeParams);
        if (result == NativeType.UNIT && !allowUnit) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_UNIT, Phase.TYPE,
                    "Unit is only valid as a function or method return type", module.uri, ref.span)
                    .withTypes("a value type", "Unit"));
            result = NativeType.ERROR;
        }
        if (ref.nullable) {
            if (result == NativeType.UNIT) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                        "Unit cannot be nullable", module.uri, ref.span).withTypes("a nullable-capable type", "Unit?"));
                result = NativeType.ERROR;
            } else {
                result = NullableType.of(result);
            }
        }
        ref.resolved = result;
        return result;
    }

    /**
     * Resolves a bare type name without applying generic arguments. Used by
     * match branches, where {@code case Option.Some} names the declaration and
     * the scrutinee supplies any instantiation. The declaration identity is
     * what the checker compares.
     */
    public Type resolveUnapplied(Module module, TypeRef ref) {
        Symbol symbol = null;
        if (ref.parts.size() == 1) {
            symbol = module.scope.types.get(ref.simpleName());
            if (symbol == null) {
                Symbol alias = module.scope.importAliases.get(ref.simpleName());
                if (alias != null && alias.isType()) {
                    symbol = alias;
                }
            }
        } else if (ref.parts.size() == 2) {
            Symbol alias = module.scope.importAliases.get(ref.parts.get(0));
            if (alias != null && alias.kind == Symbol.Kind.MODULE && alias.module != null) {
                symbol = alias.module.scope.types.get(ref.simpleName());
            }
        }
        if (symbol == null) {
            diagnostics.add(unknownType(module, ref));
            return NativeType.ERROR;
        }
        return symbol.type;
    }

    private Type resolveBase(Module module, TypeRef ref, Map<String, Type> typeParams) {
        if (ref.functionResult != null) {
            if (ref.args.size() > 3) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_FUNCTION_ARITY, Phase.TYPE,
                        "Function types support zero to three parameters", module.uri, ref.span));
                return NativeType.ERROR;
            }
            List<Type> params = new ArrayList<>();
            for (TypeRef param : ref.args) params.add(resolve(module, param, typeParams, false));
            Type result = resolve(module, ref.functionResult, typeParams, true);
            List<Type> thrown = new ArrayList<>();
            if (ref.functionThrows != null) {
                // Only Error crosses a callable: it is unchecked on the JVM, so
                // it passes through Fn.apply and still matches catch Error.
                Type type = resolve(module, ref.functionThrows, typeParams, false);
                if (Semantics.isSprigError(type)) {
                    thrown.add(type);
                } else if (type != NativeType.ERROR) {
                    diagnostics.add(Diagnostic.error(Codes.TYPE_CALLABLE_THROWS, Phase.TYPE,
                            "A function type can only declare 'throws Error'; " + type.display()
                                    + " cannot cross a function value. Handle or declare it in a named function.",
                            module.uri, ref.functionThrows.span).withTypes("Error", type.display()));
                }
            }
            return new sprig.compiler.types.FunctionType(params, result, thrown);
        }
        String last = ref.simpleName();
        if (ref.parts.size() == 1) {
            Type parameter = typeParams.get(last);
            if (parameter != null) {
                if (!ref.args.isEmpty()) {
                    diagnostics.add(Diagnostic.error(Codes.GENERIC_ARITY, Phase.TYPE,
                            "Type parameter '" + last + "' does not accept type arguments",
                            module.uri, ref.span));
                    return NativeType.ERROR;
                }
                return parameter;
            }
            Type generic = resolveBuiltinGeneric(module, ref, last, typeParams);
            if (generic != null) {
                return generic;
            }
            Symbol symbol = findModuleType(module, last);
            if (symbol == null) {
                diagnostics.add(unknownType(module, ref));
                return NativeType.ERROR;
            }
            return instantiateUserType(module, ref, symbol, typeParams);
        }
        if (ref.parts.size() == 2) {
            String qualifier = ref.parts.get(0);
            Symbol alias = module.scope.importAliases.get(qualifier);
            if (alias != null && alias.kind == Symbol.Kind.MODULE && alias.module != null) {
                Symbol symbol = alias.module.scope.types.get(last);
                if (symbol == null) {
                    diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                            "Module '" + qualifier + "' has no type '" + last + "'",
                            module.uri, ref.span));
                    return NativeType.ERROR;
                }
                return instantiateUserType(module, ref, symbol, typeParams);
            }
        }
        diagnostics.add(unknownType(module, ref));
        return NativeType.ERROR;
    }

    /**
     * Applies invariant type arguments to a user-declared class/variant.
     */
    private Type instantiateUserType(Module module, TypeRef ref, Symbol symbol,
                                     Map<String, Type> typeParams) {
        if (symbol.kind == Symbol.Kind.JAVA_TYPE || symbol.javaClass != null) {
            return instantiateJavaType(module, ref, symbol, typeParams);
        }
        Decl decl = symbol.decl;
        if (decl == null || decl.typeParams.isEmpty()) {
            if (!ref.args.isEmpty()) {
                diagnostics.add(Diagnostic.error(Codes.GENERIC_ARITY, Phase.TYPE,
                        "Type '" + ref.display() + "' is not generic and accepts no type arguments",
                        module.uri, ref.span));
                return NativeType.ERROR;
            }
            return symbol.type;
        }
        List<Type> args = resolveArguments(module, decl, ref.args, typeParams,
                ref.span, ref.span);
        if (args == null) {
            return NativeType.ERROR;
        }
        if (decl instanceof Decl.ClassDecl classDecl) {
            return new ClassType(classDecl, args);
        }
        if (decl instanceof Decl.VariantDecl variantDecl) {
            return new VariantType(variantDecl, args);
        }
        diagnostics.add(Diagnostic.error(Codes.GENERIC_ARITY, Phase.TYPE,
                "Type '" + decl.name + "' cannot carry type arguments",
                module.uri, ref.span));
        return NativeType.ERROR;
    }

    /**
     * Applies explicit type arguments to an imported Java class. Arity is
     * exact, arguments are invariant, non-nullable and must have a concrete
     * JVM representation; wildcards and inference are not part of the profile.
     */
    private Type instantiateJavaType(Module module, TypeRef ref, Symbol symbol,
                                     Map<String, Type> typeParams) {
        Class<?> clazz = symbol.javaClass;
        if (clazz == null) {
            diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                    "Unknown Java type '" + ref.display() + "'", module.uri, ref.span));
            return NativeType.ERROR;
        }
        if (ref.args.isEmpty()) {
            return symbol.type;
        }
        List<Type> args = resolveJavaArguments(module, clazz, ref.args, typeParams, ref.span);
        if (args == null) {
            return NativeType.ERROR;
        }
        return new JavaType(clazz, args, false);
    }

    /**
     * Resolves explicit type arguments written for an imported Java class in a
     * type position or call site. Exact arity, invariant, non-nullable and
     * representable; wildcards, arrays of type variables and inference are
     * outside the profile.
     */
    public List<Type> resolveJavaArguments(Module module, Class<?> clazz, List<TypeRef> argRefs,
                                           Map<String, Type> typeParams,
                                           sprig.compiler.diag.Span span) {
        if (argRefs == null || argRefs.isEmpty()) {
            return List.of();
        }
        int arity = clazz.getTypeParameters().length;
        if (arity == 0) {
            diagnostics.add(Diagnostic.error(Codes.GENERIC_ARITY, Phase.TYPE,
                    "Java type '" + clazz.getName() + "' is not generic and accepts no type arguments",
                    module.uri, span));
            return null;
        }
        if (argRefs.size() != arity) {
            diagnostics.add(Diagnostic.error(Codes.GENERIC_ARITY, Phase.TYPE,
                    "Java type '" + clazz.getName() + "' requires exactly " + arity
                            + " type argument" + (arity == 1 ? "" : "s") + " but got " + argRefs.size(),
                    module.uri, span));
            return null;
        }
        List<Type> args = new ArrayList<>();
        for (int i = 0; i < argRefs.size(); i++) {
            TypeRef argRef = argRefs.get(i);
            Type arg = resolve(module, argRef, typeParams, false);
            sprig.compiler.diag.Span argSpan = argRef != null && argRef.span != null ? argRef.span : span;
            if (arg.isNullable()) {
                diagnostics.add(Diagnostic.error(Codes.GENERIC_NULLABLE, Phase.TYPE,
                        "Java type argument '" + arg.display()
                                + "' is nullable; Java generic arguments carry no null contract",
                        module.uri, argSpan));
                return null;
            }
            if (!namesTypeParameter(argRef, typeParams) && !representableJavaArgument(arg)) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                        "Type '" + arg.display() + "' has no JVM representation as a Java type argument",
                        module.uri, argSpan)
                        .withHint("Use a native Sprig type, a concrete Java class, a Sprig collection or a generic parameter."));
                return null;
            }
            if (!satisfiesJavaBound(clazz, i, arg, module, argSpan)) {
                return null;
            }
            args.add(arg);
        }
        return args;
    }

    /** Simple class/interface bounds only; recursive or intersection bounds are rejected. */
    private boolean satisfiesJavaBound(Class<?> clazz, int index, Type arg, Module module,
                                       sprig.compiler.diag.Span span) {
        for (java.lang.reflect.Type bound : clazz.getTypeParameters()[index].getBounds()) {
            if (bound == Object.class) {
                continue;
            }
            if (!(bound instanceof Class<?> boundClass)) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                        "Java type '" + clazz.getName() + "' declares a recursive or intersection bound; "
                                + "only simple class/interface bounds are supported",
                        module.uri, span));
                return false;
            }
            Class<?> erased = JavaTypes.boxedFor(arg);
            if (erased == null || erased == Object.class || !boundClass.isAssignableFrom(erased)) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                        "Type argument '" + arg.display() + "' does not satisfy Java bound '"
                                + boundClass.getName() + "' required by '" + clazz.getName() + "'",
                        module.uri, span));
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a Java type argument is written as a type parameter of the
     * enclosing generic declaration, such as the {@code T} of
     * {@code let inner: HostTask[T]} in {@code @std/concurrent}'s {@code Task[T]}.
     * When the declaration is used ({@code Task[Outcome]}), any Sprig type may
     * stand in for it: the Java type stays inside the generic Sprig code, which
     * was checked against the parameter (#145). A Java type argument written as
     * a concrete type keeps the representability rule, because Java member
     * calls on it do not know Sprig subtyping.
     */
    private static boolean namesTypeParameter(TypeRef argRef, Map<String, Type> typeParams) {
        return argRef != null && argRef.functionResult == null && argRef.parts.size() == 1
                && argRef.args.isEmpty() && !argRef.nullable
                && typeParams != null && typeParams.containsKey(argRef.parts.get(0));
    }

    private boolean representableJavaArgument(Type arg) {
        if (arg instanceof JavaType || arg instanceof ListType || arg instanceof MapType
                || arg instanceof sprig.compiler.types.TypeParameterType
                || arg instanceof sprig.compiler.types.FunctionType) {
            return true;
        }
        return arg == NativeType.INT || arg == NativeType.INT32 || arg == NativeType.FLOAT
                || arg == NativeType.FLOAT32 || arg == NativeType.BOOL || arg == NativeType.STRING
                || arg == NativeType.DECIMAL || arg == NativeType.BIGINT;
    }

    /**
     * Resolves and validates explicit type arguments for a generic declaration.
     * Returns {@code null} after reporting a diagnostic. This is the single
     * arity/nullability implementation shared by type positions and expression
     * sites such as {@code Box[Int](value=1)}. An argument the declaration
     * rejects inside, such as a Float that it uses as a Map key, is reported
     * at {@code reportSpan}, the use, not inside the declaration.
     */
    public List<Type> resolveArguments(Module module, Decl decl, List<TypeRef> argRefs,
                                       Map<String, Type> typeParams,
                                       sprig.compiler.diag.Span span,
                                       sprig.compiler.diag.Span reportSpan) {
        List<Type> args = new ArrayList<>();
        for (TypeRef argRef : argRefs) {
            args.add(resolve(module, argRef, typeParams, false));
        }
        if (args.size() != decl.typeParams.size()) {
            diagnostics.add(Diagnostic.error(Codes.GENERIC_ARITY, Phase.TYPE,
                    "Type '" + decl.name + "' requires exactly " + decl.typeParams.size()
                            + " type argument" + (decl.typeParams.size() == 1 ? "" : "s")
                            + " but got " + args.size(),
                    module.uri, reportSpan));
            return null;
        }
        return validateArguments(module, decl, args, argRefs, reportSpan);
    }

    /**
     * Type arguments a call inferred from its arguments get the checks written
     * ones get: the nullability rule and every annotation and generic
     * application the declaration writes with them. Returns {@code null} after
     * reporting a diagnostic.
     */
    public List<Type> checkInferredArguments(Module module, Decl decl, List<Type> args,
                                             sprig.compiler.diag.Span span) {
        return validateArguments(module, decl, args, List.of(), span);
    }

    /**
     * Whether a written nullable type argument for one parameter would be
     * accepted: the declaration does not apply {@code ?} to the parameter, and
     * everything it writes with it stays valid. The other parameters stand for
     * themselves. Nothing is reported.
     */
    public boolean acceptsNullableArgument(Module module, Decl decl, String parameter, Type argument,
                                           sprig.compiler.diag.Span span) {
        if (declaresNullableParameter(decl, parameter)) {
            return false;
        }
        List<Type> args = new ArrayList<>();
        for (String name : decl.typeParams) {
            args.add(name.equals(parameter) ? argument : new TypeParameterType(decl, name));
        }
        return new TypeRefResolver(new Diagnostics()).validateArguments(module, decl, args, List.of(), span) != null;
    }

    /**
     * What validating type arguments for a generic use reports, without
     * reporting anything: the nullable rule at the use, and every type the
     * declaration writes, with the arguments substituted, where it writes
     * it. {@link GenericUses} validates an instantiation made inside generic
     * code again this way once that code has type arguments of its own.
     */
    List<Diagnostic> argumentErrors(Module module, Decl decl, List<Type> args,
                                    sprig.compiler.diag.Span span) {
        Diagnostics outer = diagnostics;
        Diagnostics scratch = new Diagnostics();
        diagnostics = scratch;
        substituting++;
        try {
            validateArguments(module, decl, args, List.of(), span);
        } finally {
            substituting--;
            diagnostics = outer;
        }
        return scratch.errors();
    }

    private List<Type> validateArguments(Module module, Decl decl, List<Type> args, List<TypeRef> argRefs,
                                         sprig.compiler.diag.Span reportSpan) {
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).isNullable() && declaresNullableParameter(decl, decl.typeParams.get(i))) {
                sprig.compiler.diag.Span argSpan = reportSpan;
                if (i < argRefs.size() && argRefs.get(i) != null && argRefs.get(i).span != null) {
                    argSpan = argRefs.get(i).span;
                }
                diagnostics.add(Diagnostic.error(Codes.GENERIC_NULLABLE, Phase.TYPE,
                        "Type argument '" + args.get(i).display() + "' for "
                                + decl.typeParams.get(i) + " is nullable, but '" + decl.name
                                + "' applies '?' to " + decl.typeParams.get(i)
                                + " in its declaration",
                        module.uri, argSpan)
                        .withHint("Use a non-nullable type argument such as "
                                + args.get(i).nonNull().display() + "."));
                return null;
            }
        }
        // Validate substituted written annotations, including forward declarations
        // and function locals. Never mutate template TypeRefs with an instantiation.
        if (validating.add(decl)) {
            try {
                List<Diagnostic> found = substitutedErrors(module, decl, bindings(decl, args));
                if (found.isEmpty()) {
                    return args;
                }
                if (substituting > 0) {
                    // Inside another declaration's substituted types: the use of
                    // that declaration reports them.
                    for (Diagnostic diagnostic : found) diagnostics.add(diagnostic);
                    return null;
                }
                // The use brought these in, so they are reported at the use. What
                // the declaration reports with its own parameters is its own error,
                // already reported where it is written.
                List<Diagnostic> own = substitutedErrors(module, decl, bindings(decl, ownArguments(decl)));
                boolean rejected = false;
                for (Diagnostic diagnostic : found) {
                    if (!containsReport(own, diagnostic)) {
                        diagnostics.add(atUse(diagnostic, decl, args, module, reportSpan));
                        rejected = true;
                    }
                }
                if (rejected) return null;
            } finally {
                validating.remove(decl);
            }
        }
        return args;
    }

    /**
     * What resolving the declaration's written annotations and generic
     * applications again with these bindings reports, where the declaration
     * writes them. Nothing is reported; an instantiation they contain reports
     * the same way, so a failure deep inside keeps its own location.
     */
    private List<Diagnostic> substitutedErrors(Module module, Decl decl, Map<String, Type> bindings) {
        Module owner = decl.symbol != null && decl.symbol.module != null ? decl.symbol.module : module;
        Diagnostics outer = diagnostics;
        Diagnostics scratch = new Diagnostics();
        diagnostics = scratch;
        substituting++;
        try {
            for (TypeRef annotation : declaredRefs(decl)) {
                resolveReturn(owner, copyRef(annotation), bindings);
            }
            for (Expr.Subscript use : applications.getOrDefault(decl, List.of())) {
                Symbol target = applicationSymbol(owner, use.base);
                if (target != null && target.decl != null && target.decl.isGeneric()) {
                    List<TypeRef> copies = new ArrayList<>();
                    for (TypeRef arg : use.typeArgs) copies.add(copyRef(arg));
                    resolveArguments(owner, target.decl, copies, bindings, use.span, use.span);
                }
            }
        } finally {
            substituting--;
            diagnostics = outer;
        }
        return scratch.errors();
    }

    private static Map<String, Type> bindings(Decl decl, List<Type> args) {
        Map<String, Type> bindings = new java.util.LinkedHashMap<>();
        for (int i = 0; i < args.size() && i < decl.typeParams.size(); i++) {
            bindings.put(decl.typeParams.get(i), args.get(i));
        }
        return bindings;
    }

    /** The declaration's own type parameters, as its body sees them. */
    static List<Type> ownArguments(Decl decl) {
        List<Type> args = new ArrayList<>();
        for (String name : decl.typeParams) args.add(new TypeParameterType(decl, name));
        return args;
    }

    /** Whether the list holds the same report: code, message and location. */
    static boolean containsReport(List<Diagnostic> reports, Diagnostic diagnostic) {
        for (Diagnostic report : reports) {
            if (report.code.equals(diagnostic.code) && report.message.equals(diagnostic.message)
                    && java.util.Objects.equals(report.uri, diagnostic.uri)
                    && java.util.Objects.equals(String.valueOf(report.span), String.valueOf(diagnostic.span))) {
                return true;
            }
        }
        return false;
    }

    /**
     * A failure inside a generic declaration that its type arguments caused,
     * reported at the use that gave them, with the same code and message. The
     * hint says where inside the declaration it arises.
     */
    static Diagnostic atUse(Diagnostic inside, Decl decl, List<Type> args, Module module,
                            sprig.compiler.diag.Span span) {
        StringBuilder bindings = new StringBuilder();
        for (int i = 0; i < args.size() && i < decl.typeParams.size(); i++) {
            if (i > 0) bindings.append(", ");
            bindings.append(decl.typeParams.get(i)).append(" = ").append(args.get(i).display());
        }
        String where = "Rejected inside '" + decl.name + "' with " + bindings + ", at "
                + location(inside.uri, inside.span) + ".";
        return Diagnostic.error(inside.code, inside.phase, inside.message, module.uri, span)
                .withTypes(inside.expectedType, inside.actualType)
                .withHint(inside.hint == null ? where : where + " " + inside.hint);
    }

    /** file.spr:line:column, or @std/name.spr:line:column for a bundled module. */
    private static String location(String uri, sprig.compiler.diag.Span span) {
        String file = uri;
        try {
            java.nio.file.Path path = java.nio.file.Path.of(java.net.URI.create(uri));
            String bundled = sprig.compiler.project.StdLibrary.importName(path);
            file = bundled != null ? bundled : path.getFileName().toString();
        } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException e) {
            // Not a file URI; keep it as it is.
        }
        return span == null ? file : file + ":" + span.display();
    }

    /**
     * Whether the declaration applies {@code ?} directly to the named type
     * parameter, e.g. {@code let value: T?}. Such a parameter requires a
     * non-nullable argument (v0.8 Practical Strict rule).
     */
    private boolean declaresNullableParameter(Decl decl, String paramName) {
        for (TypeRef ref : declaredRefs(decl)) {
            if (nullableUse(ref, paramName)) return true;
        }
        return false;
    }

    private boolean nullableUse(TypeRef ref, String name) {
        if (ref == null) return false;
        if (ref.nullable && ref.parts.size() == 1 && ref.simpleName().equals(name)) return true;
        for (TypeRef arg : ref.args) if (nullableUse(arg, name)) return true;
        return nullableUse(ref.functionResult, name);
    }

    private TypeRef copyRef(TypeRef ref) {
        List<TypeRef> args = new ArrayList<>();
        for (TypeRef arg : ref.args) args.add(copyRef(arg));
        TypeRef copy = new TypeRef(ref.parts, args, ref.nullable,
                ref.functionResult == null ? null : copyRef(ref.functionResult));
        copy.span = ref.span;
        return copy;
    }

    /** Written syntax is available before signature resolution; order cannot affect validity. */
    private List<TypeRef> declaredRefs(Decl decl) {
        Decl previous = collecting;
        collecting = decl;
        applications.put(decl, new ArrayList<>());
        List<TypeRef> refs = new ArrayList<>();
        if (decl instanceof Decl.ClassDecl c) {
            for (Decl.Field f : c.fields) { refs.add(f.typeRef); collectExpr(f.defaultExpr, refs); }
            for (Decl.Func f : c.methods) collectFunctionRefs(f, refs);
        } else if (decl instanceof Decl.VariantDecl v) {
            for (Decl.VariantCase c : v.cases) for (Decl.Field f : c.fields) refs.add(f.typeRef);
        } else if (decl instanceof Decl.Func f) collectFunctionRefs(f, refs);
        collecting = previous;
        return refs;
    }

    private void collectFunctionRefs(Decl.Func f, List<TypeRef> refs) {
        for (Decl.Param p : f.params) refs.add(p.typeRef);
        refs.add(f.returnTypeRef);
        refs.addAll(f.throwsRefs);
        collectStatements(f.body, refs);
    }

    private void collectStatements(List<Stmt> body, List<TypeRef> refs) {
        if (body == null) return;
        for (Stmt stmt : body) {
            if (stmt instanceof Stmt.VarDecl v) {
                if (v.typeRef != null) refs.add(v.typeRef);
                collectExpr(v.init, refs);
            } else if (stmt instanceof Stmt.Assign a) {
                collectExpr(a.target, refs); collectExpr(a.value, refs);
            } else if (stmt instanceof Stmt.ExprStmt e) collectExpr(e.expr, refs);
            else if (stmt instanceof Stmt.Return r) collectExpr(r.value, refs);
            else if (stmt instanceof Stmt.Throw t) collectExpr(t.value, refs);
            else if (stmt instanceof Stmt.IfStmt i) {
                collectExpr(i.cond, refs); collectStatements(i.thenBody, refs);
                for (Stmt.IfStmt.Elif e : i.elifs) { collectExpr(e.cond, refs); collectStatements(e.body, refs); }
                collectStatements(i.elseBody, refs);
            } else if (stmt instanceof Stmt.WhileStmt w) {
                collectExpr(w.cond, refs); collectStatements(w.body, refs);
            } else if (stmt instanceof Stmt.ForStmt f) {
                collectExpr(f.iterable, refs); collectStatements(f.body, refs);
            } else if (stmt instanceof Stmt.Try t) {
                collectStatements(t.body, refs); collectStatements(t.finallyBody, refs);
                for (Stmt.Try.CatchClause c : t.catches) { refs.add(c.typeRef); collectStatements(c.body, refs); }
            } else if (stmt instanceof Stmt.Match m) {
                collectExpr(m.scrutinee, refs);
                // Case owners are intentionally unapplied, not value type annotations.
                for (Stmt.Match.Branch b : m.branches) collectStatements(b.body, refs);
            }
        }
    }

    private void collectExpr(Expr expr, List<TypeRef> refs) {
        if (expr == null) return;
        if (expr instanceof Expr.Lambda l) {
            for (Decl.Param p : l.params) if (p.typeRef != null) refs.add(p.typeRef);
            collectExpr(l.body, refs);
        } else if (expr instanceof Expr.Call c) {
            collectExpr(c.callee, refs);
            for (Expr.Arg a : c.args) collectExpr(a.value, refs);
        } else if (expr instanceof Expr.FieldAccess a) collectExpr(a.receiver, refs);
        else if (expr instanceof Expr.Subscript s) {
            collectExpr(s.base, refs); collectExpr(s.index, refs);
            if (collecting != null && s.typeArgs != null)
                applications.get(collecting).add(s);
            // Brackets can also be indexing; their arguments are resolved by the
            // checker after it has established the base symbol's kind.
        } else if (expr instanceof Expr.Index i) {
            collectExpr(i.receiver, refs); collectExpr(i.index, refs);
        } else if (expr instanceof Expr.Unary u) collectExpr(u.operand, refs);
        else if (expr instanceof Expr.Binary b) { collectExpr(b.left, refs); collectExpr(b.right, refs); }
        else if (expr instanceof Expr.ListLit l) for (Expr e : l.items) collectExpr(e, refs);
        else if (expr instanceof Expr.MapLit m) {
            for (Expr e : m.keys) collectExpr(e, refs);
            for (Expr e : m.values) collectExpr(e, refs);
        } else if (expr instanceof Expr.If i) {
            for (int k = 0; k < i.conditions.size(); k++) {
                collectExpr(i.conditions.get(k), refs);
                collectExpr(i.values.get(k), refs);
            }
            if (i.elseValue != null) {
                collectExpr(i.elseValue, refs);
            }
        }
    }

    private Symbol applicationSymbol(Module module, Expr base) {
        if (base instanceof Expr.Name n) {
            if (n.symbol != null) return n.symbol;
            Symbol type = findModuleType(module, n.name);
            return type != null ? type : module.scope.functions.get(n.name);
        }
        if (base instanceof Expr.FieldAccess a && a.receiver instanceof Expr.Name n) {
            Symbol alias = module.scope.importAliases.get(n.name);
            if (alias != null && alias.kind == Symbol.Kind.MODULE) {
                Symbol type = alias.module.scope.types.get(a.name);
                return type != null ? type : alias.module.scope.functions.get(a.name);
            }
        }
        return null;
    }

    private Type resolveBuiltinGeneric(Module module, TypeRef ref, String name,
                                       Map<String, Type> typeParams) {
        switch (name) {
            case "List", "MutableList" -> {
                if (ref.args.size() != 1) {
                    diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                            name + " requires exactly one type argument", module.uri, ref.span));
                    return NativeType.ERROR;
                }
                Type element = resolve(module, ref.args.get(0), typeParams, false);
                if (element == NativeType.UNIT || element == NativeType.NULL) {
                    diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                            name + " element type cannot be " + element.display(), module.uri, ref.span));
                    return NativeType.ERROR;
                }
                return new ListType(element, name.equals("MutableList"));
            }
            case "Map", "MutableMap" -> {
                if (ref.args.size() != 2) {
                    diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                            name + " requires exactly two type arguments", module.uri, ref.span));
                    return NativeType.ERROR;
                }
                Type key = resolve(module, ref.args.get(0), typeParams, false);
                Type value = resolve(module, ref.args.get(1), typeParams, false);
                if (key.nonNull() == NativeType.FLOAT || key.nonNull() == NativeType.FLOAT32) {
                    diagnostics.add(Diagnostic.error(Codes.NUM_CONVERSION, Phase.TYPE,
                            FLOAT_MAP_KEY, module.uri, ref.args.get(0).span)
                            .withHint("Use an explicit quantized Int key, or a Decimal key when decimal identity is intended."));
                    return NativeType.ERROR;
                }
                if (value == NativeType.UNIT || key == NativeType.NULL) {
                    diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                            name + " key/value types are invalid", module.uri, ref.span));
                    return NativeType.ERROR;
                }
                return new MapType(key, value, name.equals("MutableMap"));
            }
            default -> {
                return null;
            }
        }
    }

    private Symbol findModuleType(Module module, String name) {
        Symbol symbol = module.scope.types.get(name);
        if (symbol != null) {
            return symbol;
        }
        Symbol alias = module.scope.importAliases.get(name);
        if (alias != null && alias.isType()) {
            return alias;
        }
        return null;
    }

    /** Whether a resolved symbol represents a type name. */
    public static boolean isTypeSymbol(Symbol symbol) {
        return symbol != null && (symbol.isType() || symbol.kind == Symbol.Kind.BUILTIN_TYPE);
    }

    /** Convenience for tests/tools: build the display of a resolved ref. */
    public static String display(TypeRef ref) {
        return ref == null ? "?" : ref.resolved == null ? ref.display() : ref.resolved.display();
    }

    private static Diagnostic unknownType(Module module, TypeRef ref) {
        Diagnostic diagnostic = Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                "Unknown type '" + ref.display() + "'", module.uri, ref.span);
        String hint = Newcomer.typeHint(ref.simpleName());
        if (hint != null) {
            diagnostic.withHint(hint);
        }
        return diagnostic;
    }
}
