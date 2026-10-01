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
                throw new DepError(Codes.DEP_NOT_FOUND, "Bundled std module not found: " + spec, null);
            return target;
        } catch (DepError | IOException e) {
            diagnostics.error(Codes.DEP_NOT_FOUND, Phase.NAME, e.getMessage(), uri, span);
            return null;
        }
    }
}
