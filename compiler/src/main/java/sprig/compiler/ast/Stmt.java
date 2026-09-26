package sprig.compiler.ast;

import java.util.List;
import sprig.compiler.sem.ForKind;
import sprig.compiler.sem.Symbol;
import sprig.compiler.types.Type;

/** Statements. */
public abstract class Stmt extends Node {

    /** {@code let x = e} / {@code var x: T = e}. */
    public static final class VarDecl extends Stmt {
        public final boolean mutable;
        public final String name;
        public final TypeRef typeRef; // may be null (inferred)
        public final Expr init;       // may be null only for top-level? grammar requires it
        public Symbol symbol;

        public VarDecl(boolean mutable, String name, TypeRef typeRef, Expr init) {
            this.mutable = mutable;
            this.name = name;
            this.typeRef = typeRef;
            this.init = init;
        }
    }

    /**
     * v0.8 capability clause: {@code requires T: Comparable}. Valid only as
     * the leading clause of a function declared inside a {@code generic T:}
     * block. Capability checking itself is not implemented in this alpha; the
     * checker reports {@code SPR-GENERIC-CONSTRAINT} rather than guessing.
     */
    public static final class Requires extends Stmt {
        public final String parameter;
        public final String capability;

        public Requires(String parameter, String capability) {
            this.parameter = parameter;
            this.capability = capability;
        }
    }

    /** {@code target op= value}, including plain `=`. */
    public static final class Assign extends Stmt {
        public final Expr target;
        public final String op; // "=", "+=", "-=", "*=", "/="
        public final Expr value;
        public Symbol targetSymbol;

        public Assign(Expr target, String op, Expr value) {
            this.target = target;
            this.op = op;
            this.value = value;
        }
    }

    public static final class ExprStmt extends Stmt {
        public final Expr expr;

        public ExprStmt(Expr expr) {
            this.expr = expr;
        }
    }

    public static final class Return extends Stmt {
        public final Expr value; // may be null

        public Return(Expr value) {
            this.value = value;
        }
    }

    public static final class IfStmt extends Stmt {
        public final Expr cond;
        public final List<Stmt> thenBody;
        public final List<Elif> elifs;
        public final List<Stmt> elseBody; // may be null

        public IfStmt(Expr cond, List<Stmt> thenBody, List<Elif> elifs, List<Stmt> elseBody) {
            this.cond = cond;
            this.thenBody = List.copyOf(thenBody);
            this.elifs = List.copyOf(elifs);
            this.elseBody = elseBody == null ? null : List.copyOf(elseBody);
        }

        public static final class Elif {
            public final Expr cond;
            public final List<Stmt> body;

            public Elif(Expr cond, List<Stmt> body) {
                this.cond = cond;
                this.body = List.copyOf(body);
            }
        }
    }

    public static final class WhileStmt extends Stmt {
        public final Expr cond;
        public final List<Stmt> body;

        public WhileStmt(Expr cond, List<Stmt> body) {
            this.cond = cond;
            this.body = List.copyOf(body);
        }
    }

    public static final class ForStmt extends Stmt {
        public final String varName;
        public final Expr iterable;
        public final List<Stmt> body;
        public Symbol symbol;
        public ForKind forKind = ForKind.LIST;

        public ForStmt(String varName, Expr iterable, List<Stmt> body) {
            this.varName = varName;
            this.iterable = iterable;
            this.body = List.copyOf(body);
        }
    }

    public static final class Break extends Stmt {
    }

    public static final class Continue extends Stmt {
    }

    public static final class Pass extends Stmt {
    }

    public static final class Throw extends Stmt {
        public final Expr value;

        public Throw(Expr value) {
            this.value = value;
        }
    }

    public static final class Try extends Stmt {
        public final List<Stmt> body;
        public final List<CatchClause> catches;
        public final List<Stmt> finallyBody; // may be null

        public Try(List<Stmt> body, List<CatchClause> catches, List<Stmt> finallyBody) {
            this.body = List.copyOf(body);
            this.catches = List.copyOf(catches);
            this.finallyBody = finallyBody == null ? null : List.copyOf(finallyBody);
        }

        public static final class CatchClause {
            public final String name;
            public final TypeRef typeRef;
            public final List<Stmt> body;
            public Symbol symbol;
            public Type caughtType;

            public CatchClause(String name, TypeRef typeRef, List<Stmt> body) {
                this.name = name;
                this.typeRef = typeRef;
                this.body = List.copyOf(body);
            }
        }
    }

    public static final class Match extends Stmt {
        public final Expr scrutinee;
        public final List<Branch> branches;
        public Type matchedType; // EnumType or VariantType

        public Match(Expr scrutinee, List<Branch> branches) {
            this.scrutinee = scrutinee;
            this.branches = List.copyOf(branches);
        }

        public static final class Branch {
            public final TypeRef caseTypeRef; // e.g. Expr
            public final String caseName;     // e.g. Literal
            public final String binder;       // may be null
            public final List<Stmt> body;
            public Symbol binderSymbol;
            public Type binderType;
            public boolean payloadless;

            public Branch(TypeRef caseTypeRef, String caseName, String binder, List<Stmt> body) {
                this.caseTypeRef = caseTypeRef;
                this.caseName = caseName;
                this.binder = binder;
                this.body = List.copyOf(body);
            }
        }
    }
}
