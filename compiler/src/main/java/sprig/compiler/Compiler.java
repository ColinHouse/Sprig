package sprig.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import sprig.compiler.front.AstBuilder;
import sprig.compiler.front.ParserFrontend;
import sprig.compiler.sem.NameResolver;
import sprig.compiler.sem.TypeChecker;

/**
 * Compiler driver: loads the module closure, then runs name resolution followed
 * by static type checking. Code generation is a separate phase over the result.
 */
public final class Compiler {
    private final Diagnostics diagnostics;
    private final Set<String> loadedSyntaxOnly = new LinkedHashSet<>();

    public Compiler(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    /** Parse one file without semantics (used by the syntax test tier). */
    public Module parseOnly(Path file) throws IOException {
        var tree = ParserFrontend.parseFile(file, diagnostics);
        if (diagnostics.hasErrors()) {
            return null;
        }
        AstBuilder builder = new AstBuilder(file.toUri().toString(), diagnostics);
        return builder.build(file, tree);
    }

    public Compilation compile(Path mainFile) throws IOException {
        Map<Path, Module> modules = new LinkedHashMap<>();
        List<Module> order = new ArrayList<>();
        load(mainFile.toAbsolutePath().normalize(), modules, order, new ArrayList<>());
        Module main = modules.get(mainFile.toAbsolutePath().normalize());
        if (main == null || diagnostics.hasErrors()) {
            return new Compilation(main, order);
        }
        for (Module module : order) {
            new NameResolver(diagnostics).declare(module);
        }
        if (diagnostics.hasErrors()) {
            return new Compilation(main, order);
        }
        for (Module module : order) {
            NameResolver resolver = new NameResolver(diagnostics);
            resolver.resolveBodies(module);
        }
        if (diagnostics.hasErrors()) {
            return new Compilation(main, order);
        }
        TypeChecker checker = new TypeChecker(diagnostics);
        for (Module module : order) {
            checker.check(module);
        }
        return new Compilation(main, order);
    }

    private Module parseFile(Path file) throws IOException {
        var tree = ParserFrontend.parseFile(file, diagnostics);
        if (diagnostics.hasErrors()) {
            // ANTLR error recovery can leave incomplete parse trees whose child
            // nodes are absent (e.g. `let x = ` has no expression). Never hand
            // such a tree to the AST builder: report the lexical/syntax
            // diagnostics and stop this module's front end here.
            return new Module(file, file.toUri().toString(), List.of());
        }
        AstBuilder builder = new AstBuilder(file.toUri().toString(), diagnostics);
        return builder.build(file, tree);
    }

    private Module load(Path abs, Map<Path, Module> modules, List<Module> order, List<Path> stack)
            throws IOException {
        Module existing = modules.get(abs);
        if (existing != null) {
            return existing;
        }
        if (stack.contains(abs)) {
            StringBuilder cycle = new StringBuilder();
            for (Path path : stack) {
                cycle.append(path.getFileName()).append(" -> ");
            }
            cycle.append(abs.getFileName());
            diagnostics.add(Diagnostic.error(Codes.NAME_IMPORT_CYCLE, Phase.NAME,
                    "Circular module import: " + cycle, abs.toUri().toString(), null));
            return null;
        }
        if (!Files.isRegularFile(abs)) {
            diagnostics.add(Diagnostic.error(Codes.NAME_IMPORT, Phase.NAME,
                    "Cannot find imported module: " + abs, abs.toUri().toString(), null));
            return null;
        }
        stack.add(abs);
        Module module = parseFile(abs);
        for (Decl.Import imp : module.imports) {
            String alias = sprig.compiler.sem.ImportNames.aliasFor(imp);
            if (imp.fileImport) {
                Path base = abs.getParent() == null ? Path.of(".") : abs.getParent();
                Path dependency = base.resolve(imp.pathOrClass).normalize().toAbsolutePath();
                Module dependencyModule = load(dependency, modules, order, stack);
                if (dependencyModule != null && alias != null) {
                    module.importedModules.put(alias, dependencyModule);
                    module.dependencyClosure.put(dependency, dependencyModule);
                }
            } else {
                Class<?> clazz = loadJavaClass(imp.pathOrClass);
                if (clazz == null) {
                    diagnostics.add(Diagnostic.error(Codes.JVM_CLASS, Phase.JVM,
                            "Cannot load Java class '" + imp.pathOrClass + "'",
                            module.uri, imp.span)
                            .withHint("Check the class name and the compile classpath."));
                } else if (alias != null) {
                    module.javaImports.put(alias, clazz);
                }
            }
        }
        stack.remove(stack.size() - 1);
        modules.put(abs, module);
        order.add(module);
        return module;
    }

    /** Loads a class without running static initializers. */
    public static Class<?> loadJavaClass(String name) {
        String candidate = name;
        while (true) {
            try {
                return Class.forName(candidate, false, sprig.compiler.jvm.JvmClasspath.loader());
            } catch (ClassNotFoundException | LinkageError e) {
                int dot = candidate.lastIndexOf('.');
                if (dot < 0) {
                    return null;
                }
                candidate = candidate.substring(0, dot) + "$" + candidate.substring(dot + 1);
            }
        }
    }
}
