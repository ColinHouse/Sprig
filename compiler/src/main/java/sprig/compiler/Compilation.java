package sprig.compiler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import sprig.compiler.ast.Module;

/** Result of loading and checking a Sprig program. */
public final class Compilation {
    public final Module main;
    /** All modules in dependency order (dependencies before dependents). */
    public final List<Module> modules;

    public Compilation(Module main, List<Module> modules) {
        this.main = main;
        this.modules = List.copyOf(modules);
    }

    public Map<String, Module> byName() {
        Map<String, Module> out = new LinkedHashMap<>();
        for (Module module : modules) {
            out.put(module.name, module);
        }
        return out;
    }
}
