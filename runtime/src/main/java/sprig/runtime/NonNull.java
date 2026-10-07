package sprig.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A Java kernel method or field whose result is never null. The compiler reads
 * nullability annotations by their simple name, so a Sprig program sees such a
 * result as {@code T} instead of {@code T?} and needs no null check.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
public @interface NonNull {
}
