package sprig.compiler.ast;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import sprig.compiler.sem.ModuleScope;

/** One Sprig source module (file). */
public final class Module {
    public final Path path;
    public final String name; // file base name without extension
    public final String uri;
    public final List<Decl.Import> imports;
    public final List<Decl> decls = new ArrayList<>();
    public final List<Stmt> topStatements = new ArrayList<>();
    public ModuleScope scope;
    public final Map<String, Module> importedModules = new LinkedHashMap<>();
    public final Map<String, Class<?>> javaImports = new LinkedHashMap<>();
    public final Map<Path, Module> dependencyClosure = new LinkedHashMap<>();

    public Module(Path path, String uri, List<Decl.Import> imports) {
        this.path = path;
        this.name = baseName(path);
        this.uri = uri;
        this.imports = List.copyOf(imports);
    }

    private static String baseName(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public Decl findType(String name) {
        for (Decl decl : decls) {
            if (decl.name.equals(name) && !(decl instanceof Decl.Func)) {
                return decl;
            }
        }
        return null;
    }

    public Decl.Func findFunction(String name) {
        for (Decl decl : decls) {
            if (decl instanceof Decl.Func func && func.name.equals(name) && !func.isMethod()) {
                return func;
            }
        }
        return null;
    }
}
