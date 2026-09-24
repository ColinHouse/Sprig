package sprig.compiler.sem;

import sprig.compiler.ast.Decl;

/** Canonical import alias derivation (no magic: explicit, deterministic). */
public final class ImportNames {
    private ImportNames() {
    }

    public static String aliasFor(Decl.Import imp) {
        if (imp.alias != null && !imp.alias.isEmpty()) {
            return imp.alias;
        }
        String source = imp.pathOrClass;
        if (imp.fileImport) {
            int slash = Math.max(source.lastIndexOf('/'), source.lastIndexOf('\\'));
            String base = slash >= 0 ? source.substring(slash + 1) : source;
            int dot = base.lastIndexOf('.');
            return dot > 0 ? base.substring(0, dot) : base;
        }
        int dot = source.lastIndexOf('.');
        return dot >= 0 ? source.substring(dot + 1) : source;
    }
}
