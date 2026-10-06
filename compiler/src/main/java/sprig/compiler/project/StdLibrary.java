package sprig.compiler.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;

/** Explicit, reserved @std imports from the installed compiler/SDK. */
public final class StdLibrary {
    private StdLibrary() {}

    public static Path root() {
        String home = System.getProperty("sprig.home");
        if (home == null) throw new DepError(Codes.DEP_NOT_FOUND,
                "Bundled std requires the installed Sprig launcher (-Dsprig.home)", null);
        Path root = Path.of(home).resolve("std").toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new DepError(Codes.DEP_NOT_FOUND,
                "Bundled std is missing from this SDK: " + root, null);
        return root;
    }

    /**
     * The bundled module names as a program refers to them after an import
     * (text, files, process, ...), or an empty list outside the installed
     * launcher, where no std is reachable anyway.
     */
    public static java.util.List<String> bundledModuleNames() {
        try {
            return available(root()).stream()
                    .map(name -> name.substring("@std/".length(), name.length() - ".spr".length()))
                    .toList();
        } catch (DepError e) {
            return java.util.List.of();
        }
    }

    /** The bundled module names, as they are imported: @std/text.spr and so on. */
    static java.util.List<String> available(Path base) {
        try (java.util.stream.Stream<Path> files = Files.list(base)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".spr"))
                    .sorted()
                    .map(name -> "@std/" + name)
                    .toList();
        } catch (IOException e) {
            return java.util.List.of();
        }
    }

    public static Path resolve(String spec, Diagnostics diagnostics, String uri, Span span) {
        try {
            String module = spec.substring("@std/".length());
            // The first bundled package exports these flat, explicitly named modules.
            if (!module.matches("[A-Za-z][A-Za-z0-9_]*\\.spr"))
                throw new DepError(Codes.DEP_NOT_FOUND, "Invalid bundled std import: " + spec, null);
            Path base = root().toRealPath();
            Path target = base.resolve(module);
            if (!Files.isRegularFile(target) || Files.isSymbolicLink(target)
                    || !target.toRealPath().startsWith(base))
                throw new DepError(Codes.DEP_NOT_FOUND, "Bundled std module not found: " + spec
                        + "; the bundled modules are " + String.join(", ", available(base)), null);
            return target;
        } catch (DepError | IOException e) {
            diagnostics.error(Codes.DEP_NOT_FOUND, Phase.NAME, e.getMessage(), uri, span);
            return null;
        }
    }
}
