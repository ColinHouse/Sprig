package sprig.compiler.jvm;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;

/**
 * Java's {@code @Deprecated} annotation, for {@code sprig api} and for the
 * warning where a program uses what the annotation marks. Reading it never
 * changes which member a call selects or whether the call is accepted.
 */
public final class JvmDeprecation {
    /** The same words on every warning, so the text report shows them once. */
    private static final String HINT = "The Java library's documentation names the replacement;"
            + " 'sprig api CLASS --member NAME' shows which overloads are deprecated.";

    private JvmDeprecation() {}

    public static void describe(AnnotatedElement element, Map<String, Object> out) {
        Deprecated annotation = element.getDeclaredAnnotation(Deprecated.class);
        out.put("deprecated", annotation != null);
        out.put("forRemoval", annotation != null && annotation.forRemoval());
    }

    public static String text(Map<String, Object> metadata) {
        return Boolean.TRUE.equals(metadata.get("deprecated"))
                ? "  (deprecated: true, forRemoval: " + metadata.get("forRemoval") + ")" : "";
    }

    /**
     * Warns at a use of a deprecated constructor, method or field. The message
     * names the member with its parameter types, because one overload may be
     * deprecated while the next is not.
     */
    public static <T extends AnnotatedElement & Member> void warn(
            Diagnostics diagnostics, T member, String action, String uri, Span span) {
        Deprecated annotation = member.getDeclaredAnnotation(Deprecated.class);
        if (annotation == null) return;
        String owner = className(member.getDeclaringClass());
        boolean constructor = member instanceof Constructor<?>;
        String kind = constructor ? "constructor" : member instanceof Field ? "field" : "method";
        String shown = constructor ? owner : owner + "." + member.getName();
        if (member instanceof Executable executable) {
            StringJoiner parameters = new StringJoiner(", ", "(", ")");
            for (Class<?> parameter : executable.getParameterTypes()) {
                parameters.add(parameter.getSimpleName());
            }
            shown += parameters;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("member", owner + "." + (constructor ? "<init>" : member.getName()));
        data.put("javaSignature", member instanceof Executable executable
                ? executable.toGenericString() : ((Field) member).toGenericString());
        data.put("deprecated", true);
        data.put("forRemoval", annotation.forRemoval());
        diagnostics.add(new Diagnostic(Codes.JVM_DEPRECATED, Phase.TYPE, Diagnostic.Severity.WARNING,
                action + " deprecated Java " + kind + " " + shown
                        + (annotation.forRemoval() ? " (marked for removal)" : ""), uri, span)
                .withHint(HINT)
                .withRelatedHelp("jvm")
                .withData(data));
    }

    /** Warns at the import of a deprecated class: the one place a program names it in full. */
    public static void warnImport(Diagnostics diagnostics, Class<?> imported, String uri, Span span) {
        Deprecated annotation = imported.getDeclaredAnnotation(Deprecated.class);
        if (annotation == null) return;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("class", className(imported));
        data.put("deprecated", true);
        data.put("forRemoval", annotation.forRemoval());
        diagnostics.add(new Diagnostic(Codes.JVM_DEPRECATED, Phase.TYPE, Diagnostic.Severity.WARNING,
                "Importing deprecated Java class " + className(imported)
                        + (annotation.forRemoval() ? " (marked for removal)" : ""), uri, span)
                .withHint(HINT)
                .withRelatedHelp("jvm")
                .withData(data));
    }

    /** The name an import writes: a nested class with dots, as in java.util.Map.Entry. */
    private static String className(Class<?> clazz) {
        String canonical = clazz.getCanonicalName();
        return canonical != null ? canonical : clazz.getName();
    }
}
