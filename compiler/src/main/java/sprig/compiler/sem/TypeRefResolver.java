package sprig.compiler.sem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import sprig.compiler.ast.Decl;
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
     * Applies type arguments to a user-declared class/variant. v0.8 exposes one
     * parameter, but the arity check is written against the declaration's
     * parameter list so multiple parameters remain a data change.
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
        return args;
    }

    /**
     * Whether the declaration applies {@code ?} directly to the named type
     * parameter, e.g. {@code let value: T?}. Such a parameter requires a
     * non-nullable argument (v0.8 Practical Strict rule).
     */
    private boolean declaresNullableParameter(Decl decl, String paramName) {
        for (Type type : declaredTypes(decl)) {
            if (type instanceof NullableType nullable
                    && nullable.inner instanceof TypeParameterType parameter
                    && parameter.owner == decl && parameter.name.equals(paramName)) {
                return true;
            }
        }
        return false;
    }

    private List<Type> declaredTypes(Decl decl) {
        List<Type> types = new ArrayList<>();
        if (decl instanceof Decl.ClassDecl classDecl) {
            for (Decl.Field field : classDecl.fields) {
                if (field.type != null) {
                    types.add(field.type);
                }
            }
            for (Decl.Func method : classDecl.methods) {
                collectFunctionTypes(method, types);
            }
        } else if (decl instanceof Decl.VariantDecl variantDecl) {
            for (Decl.VariantCase variantCase : variantDecl.cases) {
                for (Decl.Field field : variantCase.fields) {
                    if (field.type != null) {
                        types.add(field.type);
                    }
                }
            }
        } else if (decl instanceof Decl.Func func) {
            collectFunctionTypes(func, types);
        }
        return types;
    }

    private void collectFunctionTypes(Decl.Func func, List<Type> types) {
        for (Decl.Param param : func.params) {
            if (param.type != null) {
                types.add(param.type);
            }
        }
        if (func.returnType != null) {
            types.add(func.returnType);
        }
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
