package sprig.compiler.ast;

import java.util.List;
import sprig.compiler.ast.Decl;
import sprig.compiler.sem.ResolvedCall;
import sprig.compiler.sem.ResolvedField;
import sprig.compiler.sem.Symbol;
import sprig.compiler.types.Type;

/** Expressions. The checker stores the static {@link #type} on every node. */
public abstract class Expr extends Node {
    public Type type;

    public static final class IntLit extends Expr {
        public final java.math.BigInteger value;
        public final String sourceText;

        public IntLit(String sourceText) {
            this.sourceText = sourceText;
            this.value = new java.math.BigInteger(sourceText);
        }
    }

    public static final class FloatLit extends Expr {
        public final double value;
        public final String sourceText;

        public FloatLit(String sourceText) {
            this.sourceText = sourceText;
            this.value = Double.parseDouble(sourceText);
        }
    }

    public static final class StringLit extends Expr {
        public final String value;

        public StringLit(String value) {
            this.value = value;
        }
    }

    public static final class BoolLit extends Expr {
        public final boolean value;

        public BoolLit(boolean value) {
            this.value = value;
        }
    }

    public static final class NullLit extends Expr {
    }

    /** A bare identifier resolved to a local, parameter, field or top-level name. */
    public static final class Name extends Expr {
        public final String name;
        public Symbol symbol;

        public Name(String name) {
            this.name = name;
        }
    }

    /** {@code receiver.name}; also covers module members and {@code Type.Case}. */
    public static final class FieldAccess extends Expr {
        public final Expr receiver;
        public final String name;
        public ResolvedField resolved;

        public FieldAccess(Expr receiver, String name) {
            this.receiver = receiver;
            this.name = name;
        }
    }

    public static final class Arg {
        public final String name; // null for positional
        public final Expr value;

        public Arg(String name, Expr value) {
            this.name = name;
            this.value = value;
        }
    }

    public static final class Call extends Expr {
        public final Expr callee;
        public final List<Arg> args;
        public ResolvedCall resolved;

        public Call(Expr callee, List<Arg> args) {
            this.callee = callee;
            this.args = List.copyOf(args);
        }

        public boolean hasNamedArgs() {
            for (Arg arg : args) {
                if (arg.name != null) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasPositionalArgs() {
            for (Arg arg : args) {
                if (arg.name == null) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class Index extends Expr {
        public final Expr receiver;
        public final Expr index;

        public Index(Expr receiver, Expr index) {
            this.receiver = receiver;
            this.index = index;
        }
    }

    public static final class Unary extends Expr {
        public final String op; // "-", "+", "not"
        public final Expr operand;

        public Unary(String op, Expr operand) {
            this.op = op;
            this.operand = operand;
        }
    }

    /** Binary and comparison operators (including {@code and}, {@code or}, {@code in}). */
    public static final class Binary extends Expr {
        public final String op;
        public final Expr left;
        public final Expr right;
        public boolean stringConcat;
        public boolean valueEquality; // use equals() instead of ==

        public Binary(String op, Expr left, Expr right) {
            this.op = op;
            this.left = left;
            this.right = right;
        }
    }

    public static final class ListLit extends Expr {
        public final List<Expr> items;
        public boolean mutable;

        public ListLit(List<Expr> items) {
            this.items = List.copyOf(items);
        }
    }

    public static final class MapLit extends Expr {
        public final List<Expr> keys;
        public final List<Expr> values;
        public boolean mutable;

        public MapLit(List<Expr> keys, List<Expr> values) {
            this.keys = List.copyOf(keys);
            this.values = List.copyOf(values);
        }
    }

    public static final class Lambda extends Expr {
        public final List<Decl.Param> params;
        public final Expr body;

        public Lambda(List<Decl.Param> params, Expr body) {
            this.params = List.copyOf(params);
            this.body = body;
        }
    }
}
