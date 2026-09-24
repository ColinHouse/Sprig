package sprig.compiler.sem;

import java.util.LinkedHashMap;
import java.util.Map;
import sprig.compiler.ast.Module;

/** Module-level names: types, functions, top-level variables and import aliases. */
public final class ModuleScope {
    public final Module module;
    public final Map<String, Symbol> types = new LinkedHashMap<>();
    public final Map<String, Symbol> functions = new LinkedHashMap<>();
    public final Map<String, Symbol> topVars = new LinkedHashMap<>();
    public final Map<String, Symbol> importAliases = new LinkedHashMap<>();

    public ModuleScope(Module module) {
        this.module = module;
    }
}
