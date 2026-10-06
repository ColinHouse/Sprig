package sprig.compiler.tooling;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Module;
import sprig.compiler.project.DepError;
import sprig.compiler.project.StdLibrary;
import sprig.compiler.sem.ImportNames;
import sprig.compiler.sem.Symbol;
import sprig.compiler.types.Type;

/**
 * Compiler-owned, resolved API metadata for one checked Sprig module: the
 * signatures, and as {@code doc} the comment written directly above a
 * declaration or member, the text editor hover shows.
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
            Map<String, Object> item = declaration(module.source, decl);
            if (item != null) out.add(item);
        }
        for (Module.Export exported : module.exports) {
            if (exported.symbol == null || exported.symbol.decl == null) continue;
            // A reexported declaration keeps the comment written at its origin.
            String origin = exported.symbol.module != null ? exported.symbol.module.source : null;
            Map<String,Object> item = declaration(origin, exported.symbol.decl);
            if (item != null) { exportOrigin(module,exported.symbol,item); out.add(item); }
        }
        return out;
    }

    private static void exportOrigin(Module module, Symbol symbol, Map<String,Object> item) {
        if (symbol.module != null && symbol.module != module) {
            item.put("reexported",true);
            item.put("originModule",originModule(module.path,symbol.module.path));
        }
    }

    /**
     * A bundled module is named by its import, which is the same on every
     * machine; a path from the facade to the SDK would not be. Other origins
     * stay relative to the facade.
     */
    private static String originModule(Path facade, Path origin) {
        Path absolute = origin.toAbsolutePath().normalize();
        try {
            if (absolute.getParent().equals(StdLibrary.root().toRealPath())) return "@std/" + absolute.getFileName();
        } catch (DepError | IOException e) {
            // No bundled std in this launch, so the origin is an ordinary file.
        }
        String relative = facade.toAbsolutePath().getParent().relativize(absolute).toString().replace('\\','/');
        return relative.startsWith(".") ? relative : "./" + relative;
    }

    private static Map<String, Object> declaration(String source, Decl decl) {
        Map<String, Object> item = null;
        if (decl instanceof Decl.Func func && !func.isMethod()) {
            item = function(source, func);
        } else if (decl instanceof Decl.ClassDecl clazz) {
            item = head("class", clazz.name, clazz.typeParams);
            List<Map<String, Object>> fields = new ArrayList<>();
            for (Decl.Field field : clazz.fields) fields.add(field(source, field));
            List<Map<String, Object>> methods = new ArrayList<>();
            for (Decl.Func method : clazz.methods) methods.add(function(source, method));
            item.put("fields", fields);
            item.put("methods", methods);
        } else if (decl instanceof Decl.EnumDecl enums) {
            item = head("enum", enums.name, List.of());
            item.put("cases", new ArrayList<>(enums.cases));
        } else if (decl instanceof Decl.VariantDecl variant) {
            item = head("variant", variant.name, variant.typeParams);
            List<Map<String, Object>> cases = new ArrayList<>();
            for (Decl.VariantCase variantCase : variant.cases) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", variantCase.name);
                List<Map<String, Object>> fields = new ArrayList<>();
                for (Decl.Field field : variantCase.fields) fields.add(field(source, field));
                entry.put("fields", fields);
                doc(entry, source, variantCase.span);
                cases.add(entry);
            }
            item.put("cases", cases);
        }
        if (item != null && !(decl instanceof Decl.Func)) {
            doc(item, source, decl.span);
        }
        return item;
    }

    /** The comment written directly above a declaration, as hover shows it. */
    private static void doc(Map<String, Object> item, String source, sprig.compiler.diag.Span span) {
        String doc = span == null ? null : DocComments.above(source, span.startLine);
        if (doc != null) item.put("doc", doc);
    }

    private static Map<String, Object> head(String kind, String name, List<String> typeParams) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("kind", kind);
        item.put("name", name);
        if (!typeParams.isEmpty()) item.put("genericParameters", List.copyOf(typeParams));
        return item;
    }

    private static Map<String, Object> field(String source, Decl.Field field) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", field.name);
        item.put("type", display(field.type));
        item.put("mutable", field.mutable);
        item.put("required", field.defaultExpr == null);
        doc(item, source, field.span);
        return item;
    }

    private static Map<String, Object> function(String source, Decl.Func func) {
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
        if (func.rethrows) {
            item.put("rethrows", true);
        }
        doc(item, source, func.span);
        if (!func.equatableParams.isEmpty() || !func.comparableParams.isEmpty()) {
            List<String> requires = new ArrayList<>();
            for (String name : func.equatableParams) requires.add(name + ": Equatable");
            for (String name : func.comparableParams) requires.add(name + ": Comparable");
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
