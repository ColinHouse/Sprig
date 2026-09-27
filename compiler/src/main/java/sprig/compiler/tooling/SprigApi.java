package sprig.compiler.tooling;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Module;
import sprig.compiler.sem.ImportNames;
import sprig.compiler.sem.Symbol;
import sprig.compiler.types.Type;

/**
 * Compiler-owned, resolved API metadata for one checked Sprig module. This
 * describes signatures only; behavior and policy belong in human docs.
 */
public final class SprigApi {
    private SprigApi() {}

    public static Map<String, Object> module(Module module, String label) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("kind", "sprig-module");
        result.put("compilerVersion", Catalog.COMPILER_VERSION);
        result.put("languageVersion", Catalog.LANGUAGE_VERSION);
        result.put("module", label != null ? label : module.name);
        result.put("moduleName", module.name);
        result.put("path", module.path.toAbsolutePath().toString());
        result.put("uri", module.uri);
        result.put("imports", imports(module));
        result.put("variables", variables(module));
        result.put("declarations", declarations(module));
        return result;
    }

    private static List<Map<String, Object>> imports(Module module) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Decl.Import imp : module.imports) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("spec", imp.pathOrClass);
            item.put("alias", ImportNames.aliasFor(imp));
            String kind;
            if (!imp.fileImport) {
                kind = "java";
            } else if (imp.pathOrClass.startsWith("@std/")) {
                kind = "std";
            } else if (imp.pathOrClass.startsWith("@")) {
                kind = "package";
            } else {
                kind = "file";
            }
            item.put("kind", kind);
            out.add(item);
        }
        return out;
    }

    private static List<Map<String, Object>> variables(Module module) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (module.scope == null) return out;
        for (Symbol symbol : module.scope.topVars.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", symbol.name);
            item.put("type", display(symbol.type));
            item.put("mutable", symbol.mutable);
            exportOrigin(module,symbol,item);
            out.add(item);
        }
        return out;
    }

    private static List<Map<String, Object>> declarations(Module module) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Decl decl : module.decls) {
            Map<String, Object> item = declaration(decl);
            if (item != null) out.add(item);
        }
        for (Module.Export exported : module.exports) {
            if (exported.symbol == null || exported.symbol.decl == null) continue;
            Map<String,Object> item = declaration(exported.symbol.decl);
            if (item != null) { exportOrigin(module,exported.symbol,item); out.add(item); }
        }
        return out;
    }

    private static void exportOrigin(Module module, Symbol symbol, Map<String,Object> item) {
        if (symbol.module != null && symbol.module != module) {
            item.put("reexported",true);
            String origin = module.path.toAbsolutePath().getParent().relativize(symbol.module.path.toAbsolutePath()).toString().replace('\\','/');
            item.put("originModule",origin.startsWith(".") ? origin : "./" + origin);
        }
    }

    private static Map<String, Object> declaration(Decl decl) {
        if (decl instanceof Decl.Func func && !func.isMethod()) {
            return function(func);
        }
        if (decl instanceof Decl.ClassDecl clazz) {
            Map<String, Object> item = head("class", clazz.name, clazz.typeParams);
            List<Map<String, Object>> fields = new ArrayList<>();
            for (Decl.Field field : clazz.fields) fields.add(field(field));
            List<Map<String, Object>> methods = new ArrayList<>();
            for (Decl.Func method : clazz.methods) methods.add(function(method));
            item.put("fields", fields);
            item.put("methods", methods);
            return item;
        }
        if (decl instanceof Decl.EnumDecl enums) {
            Map<String, Object> item = head("enum", enums.name, List.of());
            item.put("cases", new ArrayList<>(enums.cases));
            return item;
        }
        if (decl instanceof Decl.VariantDecl variant) {
            Map<String, Object> item = head("variant", variant.name, variant.typeParams);
            List<Map<String, Object>> cases = new ArrayList<>();
            for (Decl.VariantCase variantCase : variant.cases) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", variantCase.name);
                List<Map<String, Object>> fields = new ArrayList<>();
                for (Decl.Field field : variantCase.fields) fields.add(field(field));
                entry.put("fields", fields);
                cases.add(entry);
            }
            item.put("cases", cases);
            return item;
        }
        return null;
    }

    private static Map<String, Object> head(String kind, String name, List<String> typeParams) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("kind", kind);
        item.put("name", name);
        if (!typeParams.isEmpty()) item.put("genericParameters", List.copyOf(typeParams));
        return item;
    }

    private static Map<String, Object> field(Decl.Field field) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", field.name);
        item.put("type", display(field.type));
        item.put("mutable", field.mutable);
        item.put("required", field.defaultExpr == null);
        return item;
    }

    private static Map<String, Object> function(Decl.Func func) {
        Map<String, Object> item = head("function", func.name, func.typeParams);
        List<Map<String, Object>> parameters = new ArrayList<>();
        for (Decl.Param param : func.params) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", param.name);
            entry.put("type", display(param.type));
            parameters.add(entry);
        }
        item.put("parameters", parameters);
        item.put("result", display(func.returnType));
        if (!func.throwsTypes.isEmpty()) {
            item.put("throws", func.throwsTypes.stream().map(SprigApi::display).toList());
        }
        if (!func.equatableParams.isEmpty()) {
            List<String> requires = new ArrayList<>();
            for (String name : func.equatableParams) requires.add(name + ": Equatable");
            item.put("requires", requires);
        }
        return item;
    }

    private static String display(Type type) {
        if (type == null) return "Unit";
        if (type instanceof sprig.compiler.types.JavaType javaType
                && javaType.clazz == sprig.runtime.SprigError.class) {
            return "Error";
        }
        return type.display();
    }

    /**
     * Keeps only declarations or members matching {@code filter}:
     * {@code Type.member} selects one class/variant member; a bare name selects
     * top-level declarations. Returns the number of matches, or -1 when the
     * named type itself is absent.
     */
    @SuppressWarnings("unchecked")
    public static int filterMembers(Map<String, Object> module, String filter) {
        List<Map<String, Object>> declarations =
                (List<Map<String, Object>>) module.get("declarations");
        int dot = filter.indexOf('.');
        if (dot < 0) {
            List<Map<String, Object>> kept = new ArrayList<>();
            for (Map<String, Object> declaration : declarations) {
                if (filter.equals(declaration.get("name"))) kept.add(declaration);
            }
            declarations.clear();
            declarations.addAll(kept);
            return kept.size();
        }
        String typeName = filter.substring(0, dot);
        String member = filter.substring(dot + 1);
        Map<String, Object> owner = null;
        for (Map<String, Object> declaration : declarations) {
            if (typeName.equals(declaration.get("name"))) owner = declaration;
        }
        if (owner == null) return -1;
        int matches = 0;
        if (owner.get("methods") instanceof List<?> methods) {
            List<Map<String, Object>> kept = new ArrayList<>();
            for (Object method : methods) {
                Map<String, Object> entry = (Map<String, Object>) method;
                if (member.equals(entry.get("name"))) {
                    kept.add(entry);
                    matches++;
                }
            }
            ((List<Map<String, Object>>) owner.get("methods")).clear();
            ((List<Map<String, Object>>) owner.get("methods")).addAll(kept);
        }
        if (owner.get("fields") instanceof List<?> fields) {
            List<Map<String, Object>> kept = new ArrayList<>();
            for (Object field : fields) {
                Map<String, Object> entry = (Map<String, Object>) field;
                if (member.equals(entry.get("name"))) {
                    kept.add(entry);
                    matches++;
                }
            }
            ((List<Map<String, Object>>) owner.get("fields")).clear();
            ((List<Map<String, Object>>) owner.get("fields")).addAll(kept);
        }
        if (owner.get("cases") instanceof List<?> cases && matches == 0) {
            List<Object> kept = new ArrayList<>();
            for (Object variantCase : cases) {
                String caseName = variantCase instanceof Map<?, ?> entry
                        ? String.valueOf(entry.get("name")) : String.valueOf(variantCase);
                if (member.equals(caseName)) {
                    kept.add(variantCase);
                    matches++;
                }
            }
            ((List<Object>) owner.get("cases")).clear();
            ((List<Object>) owner.get("cases")).addAll(kept);
        }
        List<Map<String, Object>> kept = new ArrayList<>();
        kept.add(owner);
        declarations.clear();
        declarations.addAll(kept);
        return matches;
    }

    /** Stable label for a file inside a project source root. */
    public static String relativeLabel(Path sourceRoot, Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        if (sourceRoot != null && normalized.startsWith(sourceRoot)) {
            return sourceRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return file.toString().replace('\\', '/');
    }
}
