package sprig.compiler.sem;

import java.util.ArrayList;
import java.util.List;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Module;
import sprig.compiler.types.ClassType;
import sprig.compiler.types.EnumType;
import sprig.compiler.types.FunctionType;
import sprig.compiler.types.JavaType;
import sprig.compiler.types.ListType;
import sprig.compiler.types.MapType;
import sprig.compiler.types.NullableType;
import sprig.compiler.types.Type;
import sprig.compiler.types.VariantCaseType;
import sprig.compiler.types.VariantType;

/**
 * Types as one module writes them, for hints and hovers that show code to
 * write: a declaration from another module through the alias it is imported
 * under ({@code lists.Pair[Int, String]}, also when a facade re-exports it), a
 * Java class through its import alias ({@code ArrayList[String]}), and a
 * variant case as its variant, because a case is not a type a program can
 * write. {@link Type#display()} stays the form messages use.
 */
public final class TypeSpelling {
    private TypeSpelling() {
    }

    public static String in(Module module, Type type) {
        if (type == null) {
            return "?";
        }
        if (type instanceof NullableType nullable) {
            String inner = in(module, nullable.inner);
            return (nullable.inner instanceof FunctionType ? "(" + inner + ")" : inner) + "?";
        }
        if (type instanceof ListType list) {
            return (list.mutable ? "MutableList[" : "List[") + in(module, list.element) + "]";
        }
        if (type instanceof MapType map) {
            return (map.mutable ? "MutableMap[" : "Map[") + in(module, map.key) + ", " + in(module, map.value) + "]";
        }
        if (type instanceof FunctionType function) {
            List<String> params = new ArrayList<>();
            for (Type param : function.params) {
                params.add(in(module, param));
            }
            String text = "fn(" + String.join(", ", params) + ") -> " + in(module, function.result);
            return function.throwsAny() ? text + " throws Error" : text;
        }
        if (type instanceof ClassType classType) {
            return applied(module, classType.decl, classType.decl.name, classType.args);
        }
        if (type instanceof VariantType variant) {
            return applied(module, variant.decl, variant.decl.name, variant.args);
        }
        if (type instanceof VariantCaseType caseType) {
            return applied(module, caseType.variant, caseType.variant.name, caseType.variantArgs);
        }
        if (type instanceof EnumType enumType) {
            return name(module, enumType.decl, enumType.decl.name);
        }
        if (type instanceof JavaType javaType && !javaType.clazz.isArray()) {
            String name = javaName(module, javaType.clazz);
            String nullable = javaType.platformNullable ? "?" : "";
            if (javaType.args.isEmpty()) {
                return name + nullable;
            }
            List<String> args = new ArrayList<>();
            for (Type arg : javaType.args) {
                args.add(in(module, arg));
            }
            return name + "[" + String.join(", ", args) + "]" + nullable;
        }
        return type.display();
    }

    private static String applied(Module module, Decl decl, String fallback, List<Type> args) {
        String name = name(module, decl, fallback);
        if (args.isEmpty()) {
            return name;
        }
        List<String> spelled = new ArrayList<>();
        for (Type arg : args) {
            spelled.add(in(module, arg));
        }
        return name + "[" + String.join(", ", spelled) + "]";
    }

    /** The name a declaration has in the module, qualified by an import alias when it lives elsewhere. */
    private static String name(Module module, Decl decl, String fallback) {
        if (module == null || module.scope == null) {
            return fallback;
        }
        for (var entry : module.scope.types.entrySet()) {
            if (entry.getValue().decl == decl) {
                return entry.getKey();
            }
        }
        for (var alias : module.scope.importAliases.entrySet()) {
            Symbol symbol = alias.getValue();
            if (symbol.decl == decl && symbol.isType()) {
                return alias.getKey();
            }
            if (symbol.kind == Symbol.Kind.MODULE && symbol.module != null && symbol.module.scope != null) {
                for (var entry : symbol.module.scope.types.entrySet()) {
                    if (entry.getValue().decl == decl) {
                        return alias.getKey() + "." + entry.getKey();
                    }
                }
            }
        }
        return fallback;
    }

    /** A Java class by its import alias, or by its full name when the module does not import it. */
    private static String javaName(Module module, Class<?> clazz) {
        if (module != null && module.scope != null) {
            for (var alias : module.scope.importAliases.entrySet()) {
                if (alias.getValue().javaClass == clazz) {
                    return alias.getKey();
                }
            }
            for (var entry : module.scope.types.entrySet()) {
                if (entry.getValue().javaClass == clazz) {
                    return entry.getKey();
                }
            }
        }
        if (clazz == sprig.runtime.SprigError.class) {
            return "Error";
        }
        return clazz.getName();
    }
}
