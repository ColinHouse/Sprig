package sprig.compiler.sem;

import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Module;
import sprig.compiler.ast.TypeRef;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.types.JavaType;
import sprig.compiler.types.ListType;
import sprig.compiler.types.MapType;
import sprig.compiler.types.NativeType;
import sprig.compiler.types.NullableType;
import sprig.compiler.types.Type;

/** Resolves written {@link TypeRef}s to {@link Type}s. */
public final class TypeRefResolver {
    private final Diagnostics diagnostics;

    public TypeRefResolver(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public Type resolve(Module module, TypeRef ref) {
        return resolve(module, ref, false);
    }

    public Type resolveReturn(Module module, TypeRef ref) {
        return resolve(module, ref, true);
    }

    private Type resolve(Module module, TypeRef ref, boolean allowUnit) {
        if (ref == null) {
            return NativeType.ERROR;
        }
        Type result = resolveBase(module, ref);
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

    private Type resolveBase(Module module, TypeRef ref) {
        String last = ref.simpleName();
        if (ref.parts.size() == 1) {
            Type generic = resolveBuiltinGeneric(module, ref, last);
            if (generic != null) {
                return generic;
            }
            Symbol symbol = findModuleType(module, last);
            if (symbol == null) {
                diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                        "Unknown type '" + ref.display() + "'", module.uri, ref.span));
                return NativeType.ERROR;
            }
            if (!ref.args.isEmpty()) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                        "Type '" + last + "' does not accept type arguments", module.uri, ref.span));
                return NativeType.ERROR;
            }
            return symbol.type;
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
                if (!ref.args.isEmpty()) {
                    diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                            "Type '" + ref.display() + "' does not accept type arguments",
                            module.uri, ref.span));
                    return NativeType.ERROR;
                }
                return symbol.type;
            }
        }
        diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                "Unknown type '" + ref.display() + "'", module.uri, ref.span));
        return NativeType.ERROR;
    }

    private Type resolveBuiltinGeneric(Module module, TypeRef ref, String name) {
        switch (name) {
            case "List", "MutableList" -> {
                if (ref.args.size() != 1) {
                    diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                            name + " requires exactly one type argument", module.uri, ref.span));
                    return NativeType.ERROR;
                }
                Type element = resolve(module, ref.args.get(0));
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
                Type key = resolve(module, ref.args.get(0));
                Type value = resolve(module, ref.args.get(1));
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
