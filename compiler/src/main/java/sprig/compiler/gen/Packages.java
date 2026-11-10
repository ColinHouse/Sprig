package sprig.compiler.gen;

/**
 * The Java packages one compilation writes its classes to and finds the
 * runtime in.
 *
 * <p>A project's generated classes get a package of their own,
 * {@code sprig.user.<project>}, so two Sprig-built jars in one class loader
 * (two mods in one game) share no class name. A file compiled outside a
 * project has no name to take and stays in {@code sprig.user}.
 *
 * <p>The runtime is {@code sprig.runtime}, the classes the SDK ships, unless
 * the build asks for a copy of its own. That copy is
 * {@code <generated>.runtime}: everything the project adds to a jar then sits
 * under one package, and no other jar's runtime can stand in for it.
 */
public record Packages(String generated, String runtime) {
    /** The package of a file compiled outside a project, and the parent of every project's. */
    public static final String USER = "sprig.user";
    /** The runtime the SDK ships. */
    public static final String SHARED_RUNTIME = "sprig.runtime";

    /**
     * @param projectName the manifest's {@code name}, or null outside a project
     * @param ownRuntime whether the build writes its own copy of the runtime
     */
    public static Packages of(String projectName, boolean ownRuntime) {
        String generated = projectName == null ? USER : USER + "." + segment(projectName);
        return new Packages(generated, ownRuntime ? generated + ".runtime" : SHARED_RUNTIME);
    }

    /** The directory of the generated package below a Java source root. */
    public String generatedDirectory() {
        return generated.replace('.', '/');
    }

    /**
     * The name a class of the shipped runtime has in this compilation, or the
     * name unchanged when it is not a runtime class.
     */
    public String runtimeName(String qualifiedName) {
        if (runtime.equals(SHARED_RUNTIME) || !qualifiedName.startsWith(SHARED_RUNTIME + ".")) {
            return qualifiedName;
        }
        return runtime + qualifiedName.substring(SHARED_RUNTIME.length());
    }

    /**
     * A project name as one segment of a Java package name: every character a
     * Java identifier cannot hold becomes {@code _} ({@code $} too, which only
     * generated class names use), a name that starts with a digit or is a Java
     * keyword gets a {@code _} before or after it.
     */
    static String segment(String projectName) {
        StringBuilder segment = new StringBuilder();
        projectName.codePoints().forEach(c -> segment.appendCodePoint(
                c != '$' && Character.isJavaIdentifierPart(c) && !Character.isIdentifierIgnorable(c) ? c : '_'));
        if (segment.isEmpty() || !Character.isJavaIdentifierStart(segment.codePointAt(0))) {
            segment.insert(0, '_');
        }
        return JavaGenerator.JAVA_RESERVED.contains(segment.toString()) ? segment + "_" : segment.toString();
    }
}
