package sprig.compiler.lsp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import sprig.compiler.Compilation;
import sprig.compiler.ast.Module;
import sprig.compiler.diag.Diagnostic;

/** One compiler run over a root file with the editor's unsaved buffers. */
final class Analysis {
    final Path root;
    /** Null when the project context could not be prepared. */
    final Compilation compilation;
    final List<Diagnostic> diagnostics;
    /** Buffer texts this run used instead of the file system. */
    final Map<Path, String> overlays;
    private final Map<Path, TextLines> texts = new HashMap<>();
    private SymbolIndex index;

    Analysis(Path root, Compilation compilation, List<Diagnostic> diagnostics, Map<Path, String> overlays) {
        this.root = root;
        this.compilation = compilation;
        this.diagnostics = List.copyOf(diagnostics);
        this.overlays = Map.copyOf(overlays);
    }

    Compilation.Stage stage() {
        return compilation == null ? null : compilation.stage;
    }

    /** Whether name resolution finished for every module, so names carry their symbols. */
    boolean resolved() {
        Compilation.Stage stage = stage();
        return stage == Compilation.Stage.RESOLVED || stage == Compilation.Stage.CHECKED;
    }

    boolean checked() {
        return stage() == Compilation.Stage.CHECKED;
    }

    Module module(Path path) {
        if (compilation == null) {
            return null;
        }
        for (Module module : compilation.modules) {
            if (module.path.equals(path)) {
                return module;
            }
        }
        return null;
    }

    /** The text the compiler read for a module, for position conversion. */
    TextLines text(Path path) {
        return texts.computeIfAbsent(path, p -> {
            String overlay = overlays.get(p);
            if (overlay != null) {
                return new TextLines(overlay);
            }
            try {
                return new TextLines(Files.readString(p, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                return new TextLines("");
            }
        });
    }

    SymbolIndex index() {
        if (index == null) {
            index = new SymbolIndex(this);
        }
        return index;
    }
}
