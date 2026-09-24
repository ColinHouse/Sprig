package sprig.compiler.ast;

import java.util.List;
import sprig.compiler.sem.Symbol;
import sprig.compiler.types.Type;

/** Top-level declarations. */
public abstract class Decl extends Node {
    public final String name;
    public Symbol symbol;

    protected Decl(String name) {
        this.name = name;
    }

    public static final class Param {
        public final String name;
        public final TypeRef typeRef;
        public Symbol symbol;
        public Type type;

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

    public static final class EnumDecl extends Decl {
        public final List<String> cases;

        public EnumDecl(String name, List<String> cases) {
            super(name);
            this.cases = List.copyOf(cases);
        }
    }

    public static final class VariantDecl extends Decl {
        public final List<VariantCase> cases;

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

        public Import(String pathOrClass, boolean fileImport, String alias) {
            this.pathOrClass = pathOrClass;
            this.fileImport = fileImport;
            this.alias = alias;
        }
    }
}
