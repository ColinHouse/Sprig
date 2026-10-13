package sprig.compiler.jvm;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.Map;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;

/** Reads Java's runtime annotation without changing member eligibility or overload selection. */
public final class JvmDeprecation {
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

    public static <T extends AnnotatedElement & Member> void warn(
            Diagnostics diagnostics, T member, String action, String uri, Span span) {
        Deprecated annotation = member.getDeclaredAnnotation(Deprecated.class);
        if (annotation == null) return;
        String name = member.getDeclaringClass().getName() + "."
                + (member instanceof java.lang.reflect.Constructor<?> ? "<init>" : member.getName());
        diagnostics.add(new Diagnostic(Codes.JVM_DEPRECATED, Phase.TYPE, Diagnostic.Severity.WARNING,
                action + " deprecated Java member " + name
                        + (annotation.forRemoval() ? " (marked for removal)" : ""), uri, span)
                .withHint("Inspect the Java API documentation for a supported replacement.")
                .withRelatedHelp("jvm")
                .withData(Map.of("member", name, "deprecated", true, "forRemoval", annotation.forRemoval())));
    }
}
