package sprig.compiler.ast;

import java.util.List;
import sprig.compiler.diag.Span;
import sprig.compiler.sem.Symbol;
import sprig.compiler.types.Type;

/** Top-level declarations. */
public abstract class Decl extends Node {
    public final String name;
    public Symbol symbol;
    /** The declared name's identifier token; editor tooling navigates to it. */
    public Span nameSpan;
    /**
     * v0.8 generic parameter names declared by an enclosing
     * {@code generic T:} block. Empty for ordinary declarations. The list is
     * supports one or more ordered parameters.
     */
    public final List<String> typeParams = new java.util.ArrayList<>();

    protected Decl(String name) {
        this.name = name;
    }

    public boolean isGeneric() {
        return !typeParams.isEmpty();
    }

    public static final class Param {
        public final String name;
        public final TypeRef typeRef;
        public Symbol symbol;
        public Type type;
        public Span nameSpan;

        public Param(String name, TypeRef typeRef) {
            this.name = name;
            this.typeRef = typeRef;
        }
    }

    public static final class Func extends Decl {
        public final List<Param> params;
        public final TypeRef returnTypeRef;
        public final List<TypeRef> throwsRefs;
        public final List<Stmt> body;
        public Type returnType;
        public final List<Type> throwsTypes = new java.util.ArrayList<>();
        public ClassDecl owner; // non-null for methods
        /** True when this method witnesses a declared foreign conformance. */
        public boolean foreignBoundary;
        /**
         * Lexical generic parameters visible while checking this function:
         * its own {@code generic T:} parameters, or its class's parameters for
         * a method. Filled by the name resolver.
         */
        public final java.util.Map<String, Type> typeParamTypes = new java.util.LinkedHashMap<>();
        /**
         * v0.8 capability set established by {@code requires X: Equatable}
         * clauses in this function's body. Equality on those parameters is
         * checked with value equality in generated Java.
         */
        public final java.util.Set<String> equatableParams = new java.util.LinkedHashSet<>();

        public Func(String name, List<Param> params, TypeRef returnTypeRef,
                    List<TypeRef> throwsRefs, List<Stmt> body) {
            super(name);
            this.params = List.copyOf(params);
            this.returnTypeRef = returnTypeRef;
            this.throwsRefs = List.copyOf(throwsRefs);
            this.body = List.copyOf(body);
        }

        public boolean isMethod() {
            return owner != null;
        }
    }

    public static final class Field extends Decl {
        public final boolean mutable;
        public final TypeRef typeRef;
        public final Expr defaultExpr; // may be null
        public Type type;
        public ClassDecl owner;
        /** Unhandled exceptions evaluated only when a caller omits this field. */
        public final List<Type> defaultThrowsTypes = new java.util.ArrayList<>();

        public Field(String name, boolean mutable, TypeRef typeRef, Expr defaultExpr) {
            super(name);
            this.mutable = mutable;
            this.typeRef = typeRef;
            this.defaultExpr = defaultExpr;
        }
    }

    public static final class ClassDecl extends Decl {
        public final List<Field> fields;
        public final List<Func> methods;
        public final java.util.Map<String, Type> typeParamTypes = new java.util.LinkedHashMap<>();
        /** Verified foreign JVM interfaces emitted in this class's interface list. */
        public final java.util.Set<Class<?>> conformedInterfaces = new java.util.LinkedHashSet<>();

        public ClassDecl(String name, List<Field> fields, List<Func> methods) {
            super(name);
            this.fields = List.copyOf(fields);
            this.methods = List.copyOf(methods);
            for (Field field : this.fields) {
                field.owner = this;
            }
            for (Func method : this.methods) {
                method.owner = this;
            }
        }
    }

    /**
     * A {@code conform C to J} declaration. It defines no methods and performs
     * no adaptation: the checker verifies that the existing class methods
     * satisfy every supported abstract instance requirement of the imported
     * Java interface, records a foreign assignability edge, and the generator
     * emits the interface in the class's JVM interface list.
     */
    public static final class Conform extends Decl {
        public final String sourceName;
        public final String targetAlias;
        public ClassDecl source;
        public Class<?> target;

        public Conform(String sourceName, String targetAlias) {
            super(sourceName + " to " + targetAlias);
            this.sourceName = sourceName;
            this.targetAlias = targetAlias;
        }
    }

    public static final class EnumDecl extends Decl {
        public final List<String> cases;
        /** Identifier spans of {@link #cases}, in the same order. */
        public final List<Span> caseSpans = new java.util.ArrayList<>();

        public EnumDecl(String name, List<String> cases) {
            super(name);
            this.cases = List.copyOf(cases);
        }
    }

    public static final class VariantDecl extends Decl {
        public final List<VariantCase> cases;
        public final java.util.Map<String, Type> typeParamTypes = new java.util.LinkedHashMap<>();

        public VariantDecl(String name, List<VariantCase> cases) {
            super(name);
            this.cases = List.copyOf(cases);
            for (VariantCase variantCase : this.cases) {
                variantCase.owner = this;
            }
        }
    }

    public static final class VariantCase extends Node {
        public final String name;
        public final List<Field> fields;
        public VariantDecl owner;
        public Span nameSpan;

        public VariantCase(String name, List<Field> fields) {
            this.name = name;
            this.fields = List.copyOf(fields);
            for (Field field : this.fields) {
                field.owner = null;
            }
        }

        public boolean payloadless() {
            return fields.isEmpty();
        }
    }

    public static final class Import extends Node {
        public final String pathOrClass; // "./lib.spr" or java.time.LocalDate
        public final boolean fileImport;
        public final String alias;       // may be null -> derived
        /** The quoted path or the Java class name. */
        public Span targetSpan;
        /** The identifier after {@code as}; null without an explicit alias. */
        public Span aliasSpan;

        public Import(String pathOrClass, boolean fileImport, String alias) {
            this.pathOrClass = pathOrClass;
            this.fileImport = fileImport;
            this.alias = alias;
        }
    }
}
