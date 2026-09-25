package sprig.compiler.diag;

/** Tool version reported in JSON output. */
public final class Version {
    public static final String VERSION = "sprig-compiler " + sprig.compiler.tooling.Catalog.COMPILER_VERSION;

    private Version() {
    }
}
