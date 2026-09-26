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
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.types.ClassType;
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
    private final Diagnostics diagnostics;
    private final java.util.Map<Decl, List<Expr.Subscript>> applications = new java.util.HashMap<>();
    private Decl collecting;
    private final java.util.Set<Decl> validating = new java.util.HashSet<>();

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
            diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                    "Unknown type '" + ref.display() + "'", module.uri, ref.span));
            return NativeType.ERROR;
        }
        return symbol.type;
    }

    private Type resolveBase(Module module, TypeRef ref, Map<String, Type> typeParams) {
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
                diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                        "Unknown type '" + ref.display() + "'", module.uri, ref.span));
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
        diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                "Unknown type '" + ref.display() + "'", module.uri, ref.span));
        return NativeType.ERROR;
    }

    /**
     * Applies invariant type arguments to a user-declared class/variant.
     */
    private Type instantiateUserType(Module module, TypeRef ref, Symbol symbol,
                                     Map<String, Type> typeParams) {
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
     * Resolves and validates explicit type arguments for a generic declaration.
     * Returns {@code null} after reporting a diagnostic. This is the single
     * arity/nullability implementation shared by type positions and expression
     * sites such as {@code Box[Int](value=1)}.
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
                Map<String, Type> bindings = new java.util.LinkedHashMap<>();
                for (int i = 0; i < args.size(); i++) bindings.put(decl.typeParams.get(i), args.get(i));
                Module owner = decl.symbol != null && decl.symbol.module != null ? decl.symbol.module : module;
                int before = diagnostics.errorCount();
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
                if (diagnostics.errorCount() != before) return null;
            } finally {
                validating.remove(decl);
            }
        }
        return args;
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
        return false;
    }

    private TypeRef copyRef(TypeRef ref) {
        List<TypeRef> args = new ArrayList<>();
        for (TypeRef arg : ref.args) args.add(copyRef(arg));
        TypeRef copy = new TypeRef(ref.parts, args, ref.nullable);
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
                            "Float and Float32 cannot be Map keys: NaN and signed zero have no stable key equality",
                            module.uri, ref.args.get(0).span)
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

}
