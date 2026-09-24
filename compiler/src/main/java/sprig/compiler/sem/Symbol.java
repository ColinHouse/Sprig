package sprig.compiler.sem;

import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Module;
import sprig.compiler.diag.Span;
import sprig.compiler.types.Type;

/** A resolved name. */
public final class Symbol {
    public enum Kind {
        LOCAL, PARAM, FIELD, METHOD, FUNCTION, CLASS, ENUM, VARIANT, VARIANT_CASE,
        ENUM_CASE, TOP_VAR, MODULE, JAVA_TYPE, BUILTIN_TYPE
    }

    public final Kind kind;
    public final String name;
    public Type type;
    public Decl decl;
    public Decl.ClassDecl owner;
    public boolean mutable;
    public Module module;
    public Span span;
    public Class<?> javaClass;

    public Symbol(Kind kind, String name, Type type) {
        this.kind = kind;
        this.name = name;
        this.type = type;
    }

    public boolean isType() {
        return kind == Kind.CLASS || kind == Kind.ENUM || kind == Kind.VARIANT
                || kind == Kind.JAVA_TYPE || kind == Kind.BUILTIN_TYPE;
    }

    @Override
    public String toString() {
        return kind + ":" + name;
    }
}
