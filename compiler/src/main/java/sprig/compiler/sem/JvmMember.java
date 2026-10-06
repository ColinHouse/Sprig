package sprig.compiler.sem;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.List;
import sprig.compiler.types.Type;

/** A resolved JDK/JVM member (method, constructor or field). */
public final class JvmMember {
    public Executable executable; // method or constructor
    public Field field;
    public Class<?> owner;
    public String name;
    public List<Type> paramTypes;
    public Type returnType;
    /** Concrete receiver/explicit-argument bindings used to resolve generics. */
    public java.util.Map<java.lang.reflect.TypeVariable<?>, Type> bindings = java.util.Map.of();
    /** The call writes the trailing arguments that the generator packs into the varargs array. */
    public boolean varargsExpanded;

    public boolean isStatic() {
        if (field != null) {
            return java.lang.reflect.Modifier.isStatic(field.getModifiers());
        }
        return java.lang.reflect.Modifier.isStatic(executable.getModifiers());
    }

    public String display() {
        StringBuilder sb = new StringBuilder(owner.getSimpleName()).append('.').append(name).append('(');
        if (paramTypes != null) {
            for (int i = 0; i < paramTypes.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(paramTypes.get(i).display());
            }
        }
        return sb.append(')').toString();
    }
}
