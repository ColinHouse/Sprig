package sprig.compiler.sem;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Expr;
import sprig.compiler.ast.Module;
import sprig.compiler.ast.Stmt;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;
import sprig.compiler.types.ClassType;
import sprig.compiler.types.EnumType;
import sprig.compiler.types.FunctionType;
import sprig.compiler.types.JavaType;
import sprig.compiler.types.ListType;
import sprig.compiler.types.MapType;
import sprig.compiler.types.NativeType;
import sprig.compiler.types.NullableType;
import sprig.compiler.types.Type;
import sprig.compiler.types.VariantCaseType;
import sprig.compiler.types.VariantType;
import sprig.runtime.SprigError;

/**
 * Static type checking, flow checking and match exhaustiveness over the resolved
 * AST. Runs after name resolution; its results (node types, resolved calls) are
 * the input of the Java code generator.
 */
public final class TypeChecker {
    /** Arity table for built-in methods; guards the switch below against bad calls. */
    private static final Map<String, int[]> BUILTIN_ARITY = Map.ofEntries(
            Map.entry("toString", new int[]{0, 0}),
            Map.entry("Int.toString", new int[]{0, 0}), Map.entry("Int.toFloat", new int[]{0, 0}),
            Map.entry("Int.toFloatExact", new int[]{0, 0}), Map.entry("Int.toFloatLossy", new int[]{0, 0}),
            Map.entry("Int.toInt32Exact", new int[]{0, 0}), Map.entry("Int.toDecimal", new int[]{0, 0}),
            Map.entry("Int.divTrunc", new int[]{1, 1}),
            Map.entry("Int32.toString", new int[]{0, 0}), Map.entry("Int32.toInt", new int[]{0, 0}),
            Map.entry("Int32.toFloat", new int[]{0, 0}), Map.entry("Int32.toDecimal", new int[]{0, 0}),
            Map.entry("Int32.divTrunc", new int[]{1, 1}),
            Map.entry("Int.parse", new int[]{1, 1}), Map.entry("Int.abs", new int[]{1, 1}),
            Map.entry("Int.min", new int[]{2, 2}), Map.entry("Int.max", new int[]{2, 2}),
            Map.entry("Float.toString", new int[]{0, 0}), Map.entry("Float.isNaN", new int[]{0, 0}),
            Map.entry("Float.abs", new int[]{1, 1}),
            Map.entry("Float.toInt", new int[]{0, 0}), Map.entry("Float.toIntExact", new int[]{0, 0}),
            Map.entry("Float.toIntTrunc", new int[]{0, 0}),
            Map.entry("Float.toFloat32Exact", new int[]{0, 0}),
            Map.entry("Float.toFloat32Lossy", new int[]{0, 0}),
            Map.entry("Float.isFinite", new int[]{0, 0}), Map.entry("Float.isInfinite", new int[]{0, 0}),
            Map.entry("Float.approxEqual", new int[]{2, 2}), Map.entry("Float.sqrt", new int[]{1, 1}),
            Map.entry("Float32.toString", new int[]{0, 0}), Map.entry("Float32.toFloat", new int[]{0, 0}),
            Map.entry("Float32.isNaN", new int[]{0, 0}), Map.entry("Float32.isInfinite", new int[]{0, 0}),
            Map.entry("Float32.isFinite", new int[]{0, 0}),
            Map.entry("Decimal.parse", new int[]{1, 1}), Map.entry("Decimal.fromInt", new int[]{1, 1}),
            Map.entry("Decimal.fromJava", new int[]{1, 1}),
            Map.entry("Decimal.divide", new int[]{3, 3}),
            Map.entry("Decimal.toString", new int[]{0, 0}),
            Map.entry("Decimal.toIntExact", new int[]{0, 0}),
            Map.entry("Decimal.toFloatExact", new int[]{0, 0}),
            Map.entry("Decimal.toFloatLossy", new int[]{0, 0}),
            Map.entry("Decimal.toJava", new int[]{0, 0}),
            Map.entry("BigInt.parse", new int[]{1, 1}), Map.entry("BigInt.fromInt", new int[]{1, 1}),
            Map.entry("BigInt.fromJava", new int[]{1, 1}),
            Map.entry("BigInt.divTrunc", new int[]{1, 1}),
            Map.entry("BigInt.toString", new int[]{0, 0}), Map.entry("BigInt.toIntExact", new int[]{0, 0}),
            Map.entry("BigInt.toFloatExact", new int[]{0, 0}),
            Map.entry("BigInt.toFloatLossy", new int[]{0, 0}),
            Map.entry("BigInt.toDecimal", new int[]{0, 0}), Map.entry("BigInt.toJava", new int[]{0, 0}),
            Map.entry("Float.floor", new int[]{1, 1}), Map.entry("Float.ceil", new int[]{1, 1}),
            Map.entry("Bool.toString", new int[]{0, 0}),
            Map.entry("String.length", new int[]{0, 0}), Map.entry("String.isEmpty", new int[]{0, 0}),
            Map.entry("String.charAt", new int[]{1, 1}), Map.entry("String.codeAt", new int[]{1, 1}),
            Map.entry("String.substring", new int[]{1, 2}), Map.entry("String.indexOf", new int[]{1, 1}),
            Map.entry("String.contains", new int[]{1, 1}), Map.entry("String.startsWith", new int[]{1, 1}),
            Map.entry("String.endsWith", new int[]{1, 1}), Map.entry("String.toUpperCase", new int[]{0, 0}),
            Map.entry("String.toLowerCase", new int[]{0, 0}), Map.entry("String.trim", new int[]{0, 0}),
            Map.entry("String.split", new int[]{1, 1}), Map.entry("String.replace", new int[]{2, 2}),
            Map.entry("String.repeat", new int[]{1, 1}), Map.entry("String.toInt", new int[]{0, 0}),
            Map.entry("String.toIntOrNull", new int[]{0, 0}), Map.entry("String.toFloat", new int[]{0, 0}),
            Map.entry("String.toString", new int[]{0, 0}), Map.entry("String.join", new int[]{2, 2}),
            Map.entry("String.fromCode", new int[]{1, 1}),
            Map.entry("List.size", new int[]{0, 0}), Map.entry("List.isEmpty", new int[]{0, 0}),
            Map.entry("List.get", new int[]{1, 1}), Map.entry("List.contains", new int[]{1, 1}),
            Map.entry("List.indexOf", new int[]{1, 1}), Map.entry("List.toMutableList", new int[]{0, 0}),
            Map.entry("List.toList", new int[]{0, 0}), Map.entry("List.map", new int[]{1, 1}),
            Map.entry("List.filter", new int[]{1, 1}), Map.entry("List.forEach", new int[]{1, 1}),
            Map.entry("MutableList.append", new int[]{1, 1}), Map.entry("MutableList.set", new int[]{2, 2}),
            Map.entry("MutableList.insert", new int[]{2, 2}), Map.entry("MutableList.removeAt", new int[]{1, 1}),
            Map.entry("MutableList.remove", new int[]{1, 1}), Map.entry("MutableList.contains", new int[]{1, 1}),
            Map.entry("MutableList.clear", new int[]{0, 0}), Map.entry("MutableList.sort", new int[]{0, 0}),
            Map.entry("Map.size", new int[]{0, 0}), Map.entry("Map.isEmpty", new int[]{0, 0}),
            Map.entry("Map.get", new int[]{1, 1}), Map.entry("Map.containsKey", new int[]{1, 1}),
            Map.entry("Map.keys", new int[]{0, 0}), Map.entry("Map.values", new int[]{0, 0}),
            Map.entry("Map.toMutableMap", new int[]{0, 0}), Map.entry("Map.toMap", new int[]{0, 0}),
            Map.entry("MutableMap.set", new int[]{2, 2}), Map.entry("MutableMap.remove", new int[]{1, 1}),
            Map.entry("MutableMap.clear", new int[]{0, 0}));

    private final Diagnostics diagnostics;

    private Module module;
    private Decl.Func currentFunction;
    private Decl.ClassDecl currentClass;
    private int loopDepth;
    private int lambdaDepth;
    private final Deque<Map<Symbol, Type>> narrowing = new ArrayDeque<>();
    private final Deque<List<Type>> caughtStack = new ArrayDeque<>();
    private final Deque<List<Type>> effectCollectors = new ArrayDeque<>();

    public TypeChecker(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public void check(Module module) {
        this.module = module;
        for (Decl decl : module.decls) {
            if (decl instanceof Decl.ClassDecl classDecl) {
                for (Decl.Field field : classDecl.fields) {
                    if (field.defaultExpr != null) {
                        currentFunction = null;
                        currentClass = null;
                        narrowing.clear();
                        caughtStack.clear();
                        List<Type> effects = new ArrayList<>();
                        effectCollectors.push(effects);
                        Type type = checkExpr(field.defaultExpr, field.type);
                        effectCollectors.pop();
                        field.defaultThrowsTypes.clear();
                        field.defaultThrowsTypes.addAll(effects);
                        requireAssignable(field.type, type, field.defaultExpr.span, Codes.TYPE_MISMATCH,
                                "field default");
                    }
                }
                for (Decl.Func method : classDecl.methods) {
                    checkFunction(method, classDecl);
                }
            } else if (decl instanceof Decl.Func func) {
                checkFunction(func, null);
            }
        }
        currentFunction = null;
        currentClass = null;
        loopDepth = 0;
        narrowing.clear();
        caughtStack.clear();
        effectCollectors.clear();
        checkSequence(module.topStatements);
    }

    // ------------------------------------------------------------------
    // Functions and statements
    // ------------------------------------------------------------------

    private void checkFunction(Decl.Func func, Decl.ClassDecl owner) {
        currentFunction = func;
        currentClass = owner;
        loopDepth = 0;
        narrowing.clear();
        caughtStack.clear();
        narrowing.push(new HashMap<>());
        checkSequence(func.body);
        narrowing.pop();
        Type returnType = func.returnType;
        if (returnType != NativeType.UNIT && returnType != NativeType.ERROR && !definitelyReturns(func.body)) {
            diagnostics.add(Diagnostic.error(Codes.FLOW_MISSING_RETURN, Phase.FLOW,
                    "Function '" + func.name + "' must return " + returnType.display() + " on every path",
                    module.uri, func.span));
        }
        currentFunction = null;
        currentClass = null;
    }

    private void checkSequence(List<Stmt> body) {
        boolean terminated = false;
        for (Stmt stmt : body) {
            if (terminated) {
                diagnostics.add(Diagnostic.error(Codes.FLOW_UNREACHABLE, Phase.FLOW,
                        "Unreachable statement", module.uri, stmt.span));
            }
            checkStmt(stmt);
            if (definitelyExits(stmt)) {
                terminated = true;
            }
        }
    }

    private void checkStmt(Stmt stmt) {
        if (stmt instanceof Stmt.VarDecl varDecl) {
            checkVarDecl(varDecl);
        } else if (stmt instanceof Stmt.Assign assign) {
            checkAssign(assign);
        } else if (stmt instanceof Stmt.ExprStmt exprStmt) {
            checkExpr(exprStmt.expr, null);
        } else if (stmt instanceof Stmt.Return ret) {
            checkReturn(ret);
        } else if (stmt instanceof Stmt.Throw thr) {
            Type type = checkExpr(thr.value, null);
            if (!Semantics.isErrorType(type)) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                        "throw requires an error value", module.uri, thr.span)
                        .withTypes("Error or imported Throwable", type.display()));
            } else {
                requireHandled(List.of(type), thr.span);
            }
        } else if (stmt instanceof Stmt.IfStmt ifStmt) {
            checkIf(ifStmt);
        } else if (stmt instanceof Stmt.WhileStmt whileStmt) {
            checkCondition(whileStmt.cond);
            loopDepth++;
            narrowing.push(narrowTrue(whileStmt.cond));
            checkSequence(whileStmt.body);
            narrowing.pop();
            loopDepth--;
        } else if (stmt instanceof Stmt.ForStmt forStmt) {
            checkFor(forStmt);
        } else if (stmt instanceof Stmt.Try tryStmt) {
            checkTry(tryStmt);
        } else if (stmt instanceof Stmt.Match match) {
            checkMatch(match);
        } else if (stmt instanceof Stmt.Break) {
            if (loopDepth == 0) {
                diagnostics.add(Diagnostic.error(Codes.FLOW_BREAK, Phase.FLOW,
                        "break outside a loop", module.uri, stmt.span));
            }
        } else if (stmt instanceof Stmt.Continue) {
            if (loopDepth == 0) {
                diagnostics.add(Diagnostic.error(Codes.FLOW_CONTINUE, Phase.FLOW,
                        "continue outside a loop", module.uri, stmt.span));
            }
        }
        // Pass: nothing to check.
    }

    private void checkVarDecl(Stmt.VarDecl varDecl) {
        Type declared = varDecl.symbol == null ? NativeType.ERROR : varDecl.symbol.type;
        if (varDecl.init == null) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_INFER, Phase.TYPE,
                    "Variable declaration requires an initializer", module.uri, varDecl.span));
            if (varDecl.symbol != null) {
                varDecl.symbol.type = declared == null ? NativeType.ERROR : declared;
            }
            return;
        }
        if (declared == null) {
            Type initType = checkExpr(varDecl.init, null);
            if (initType == NativeType.NULL) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_INFER, Phase.TYPE,
                        "Cannot infer a type from null; write an explicit type annotation",
                        module.uri, varDecl.span));
                initType = NativeType.ERROR;
            } else if (initType == NativeType.UNIT) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_UNIT, Phase.TYPE,
                        "Cannot bind a Unit result to a variable", module.uri, varDecl.span));
                initType = NativeType.ERROR;
            }
            varDecl.symbol.type = initType;
        } else {
            Type initType = checkExpr(varDecl.init, declared);
            requireAssignable(declared, initType, varDecl.init.span, Codes.TYPE_ASSIGN, "initializer");
        }
    }

    private void checkAssign(Stmt.Assign assign) {
        Expr target = assign.target;
        Type targetType = null;
        if (target instanceof Expr.Name name) {
            Symbol symbol = name.symbol;
            if (symbol == null) {
                return;
            }
            boolean assignableKind = symbol.kind == Symbol.Kind.LOCAL || symbol.kind == Symbol.Kind.PARAM
                    || symbol.kind == Symbol.Kind.TOP_VAR || symbol.kind == Symbol.Kind.FIELD;
            if (!assignableKind) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_ASSIGN, Phase.TYPE,
                        "Cannot assign to '" + name.name + "'", module.uri, target.span));
                return;
            }
            if (!symbol.mutable) {
                diagnostics.add(Diagnostic.error(Codes.NAME_LET_ASSIGN, Phase.TYPE,
                        "Cannot assign to immutable " + (symbol.kind == Symbol.Kind.FIELD ? "field" : "binding")
                                + " '" + name.name + "'; declare it with var",
                        module.uri, target.span));
            }
            targetType = symbol.type;
            name.type = targetType;
        } else if (target instanceof Expr.FieldAccess access) {
            ResolvedField field = resolveFieldAccess(access, false);
            if (field.kind == ResolvedField.Kind.CLASS_FIELD) {
                if (!field.fieldDecl.mutable) {
                    diagnostics.add(Diagnostic.error(Codes.NAME_LET_ASSIGN, Phase.TYPE,
                            "Cannot assign to immutable field '" + access.name + "'; declare it with var",
                            module.uri, target.span));
                }
            } else if (field.kind == ResolvedField.Kind.MODULE_VAR) {
                if (!field.symbol.mutable) {
                    diagnostics.add(Diagnostic.error(Codes.NAME_LET_ASSIGN, Phase.TYPE,
                            "Cannot assign to immutable top-level binding '" + access.name + "'",
                            module.uri, target.span));
                }
            } else if (field.kind == ResolvedField.Kind.JAVA_FIELD) {
                if (java.lang.reflect.Modifier.isFinal(field.jvm.field.getModifiers())) {
                    diagnostics.add(Diagnostic.error(Codes.NAME_LET_ASSIGN, Phase.TYPE,
                            "Cannot assign to final Java field '" + access.name + "'",
                            module.uri, target.span));
                }
            } else if (field.kind == ResolvedField.Kind.VARIANT_PAYLOAD
                    || field.kind == ResolvedField.Kind.ERROR_MESSAGE) {
                diagnostics.add(Diagnostic.error(Codes.NAME_LET_ASSIGN, Phase.TYPE,
                        "Cannot assign to '" + access.name + "'; payloads are immutable",
                        module.uri, target.span));
            } else {
                diagnostics.add(Diagnostic.error(Codes.TYPE_ASSIGN, Phase.TYPE,
                        "Cannot assign to '" + access.name + "'", module.uri, target.span));
                return;
            }
            targetType = field.type;
            access.type = targetType;
        } else if (target instanceof Expr.Index index) {
            Type receiver = checkExpr(index.receiver, null);
            if (receiver.isNullable()) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_NULLABLE, Phase.TYPE,
                        "Cannot index a value that may be null", module.uri, target.span));
                receiver = receiver.nonNull();
            }
            if (receiver instanceof ListType list) {
                checkExpr(index.index, NativeType.INT);
                if (!list.mutable) {
                    diagnostics.add(Diagnostic.error(Codes.COLLECTION_IMMUTABLE, Phase.TYPE,
                            "Cannot mutate List[" + list.element.display() + "]; convert it with toMutableList()",
                            module.uri, target.span));
                }
                targetType = list.element;
            } else if (receiver instanceof MapType map) {
                checkExpr(index.index, map.key);
                if (!map.mutable) {
                    diagnostics.add(Diagnostic.error(Codes.COLLECTION_IMMUTABLE, Phase.TYPE,
                            "Cannot mutate Map[" + map.key.display() + ", " + map.value.display()
                                    + "]; convert it with toMutableMap()", module.uri, target.span));
                }
                targetType = map.value;
            } else if (receiver != NativeType.ERROR) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                        "Cannot assign through an index on " + receiver.display(), module.uri, target.span));
                return;
            } else {
                targetType = NativeType.ERROR;
            }
            index.type = targetType;
        } else {
            diagnostics.add(Diagnostic.error(Codes.TYPE_ASSIGN, Phase.TYPE,
                    "Invalid assignment target", module.uri, target.span));
            return;
        }

        if (targetType == null) {
            return;
        }
        if (assign.op.equals("=")) {
            Type valueType = checkExpr(assign.value, targetType);
            requireAssignable(targetType, valueType, assign.value.span, Codes.TYPE_ASSIGN, "assignment");
        } else {
            String op = assign.op.substring(0, 1);
            // The left-hand side supplies the context, exactly like plain `=`
            // and arithmetic: `Int32 += 1` follows the same literal rule as
            // `x = x + 1`. Variables still never narrow implicitly.
            Type valueType = checkExpr(assign.value, targetType);
            Type result = checkArithmetic(op, targetType, valueType, assign.span, true);
            requireAssignable(targetType, result, assign.value.span, Codes.TYPE_ASSIGN, "assignment");
        }
    }

    private void checkReturn(Stmt.Return ret) {
        if (currentFunction == null) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_RETURN, Phase.FLOW,
                    "return is only allowed inside a function or method", module.uri, ret.span));
            if (ret.value != null) {
                checkExpr(ret.value, null);
            }
            return;
        }
        Type expected = currentFunction.returnType;
        if (ret.value == null) {
            if (expected != NativeType.UNIT && expected != NativeType.ERROR) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_RETURN, Phase.TYPE,
                        "Function '" + currentFunction.name + "' must return " + expected.display(),
                        module.uri, ret.span).withTypes(expected.display(), "Unit"));
            }
            return;
        }
        Type actual = checkExpr(ret.value, expected);
        if (expected == NativeType.UNIT) {
            if (actual != NativeType.UNIT && actual != NativeType.ERROR) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_RETURN, Phase.TYPE,
                        "Unit function cannot return a value", module.uri, ret.span)
                        .withTypes("Unit", actual.display()));
            }
            return;
        }
        requireAssignable(expected, actual, ret.value.span, Codes.TYPE_RETURN, "return");
    }

    private void checkIf(Stmt.IfStmt ifStmt) {
        checkCondition(ifStmt.cond);
        narrowing.push(narrowTrue(ifStmt.cond));
        checkSequence(ifStmt.thenBody);
        narrowing.pop();
        for (Stmt.IfStmt.Elif elif : ifStmt.elifs) {
            checkCondition(elif.cond);
            narrowing.push(narrowTrue(elif.cond));
            checkSequence(elif.body);
            narrowing.pop();
        }
        if (ifStmt.elseBody != null) {
            narrowing.push(ifStmt.elifs.isEmpty() ? narrowFalse(ifStmt.cond) : new HashMap<>());
            checkSequence(ifStmt.elseBody);
            narrowing.pop();
        }
        // Early-exit narrowing: if (x == null) { ...exits... } -> x is non-null after.
        if (ifStmt.elifs.isEmpty() && definitelyExitsSequence(ifStmt.thenBody)) {
            for (Map.Entry<Symbol, Type> entry : narrowFalse(ifStmt.cond).entrySet()) {
                if (!narrowing.isEmpty()) {
                    narrowing.peek().put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private void checkCondition(Expr cond) {
        Type type = checkExpr(cond, NativeType.BOOL);
        if (type != NativeType.BOOL && type != NativeType.ERROR) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_CONDITION, Phase.TYPE,
                    "Condition must be Bool (no truthiness)", module.uri, cond.span)
                    .withTypes("Bool", type.display()));
        }
    }

    private void checkFor(Stmt.ForStmt forStmt) {
        Type iterable = checkExpr(forStmt.iterable, null);
        if (iterable.isNullable()) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_NULLABLE, Phase.TYPE,
                    "Cannot iterate a value that may be null", module.uri, forStmt.iterable.span));
            iterable = iterable.nonNull();
        }
        Type element = null;
        ForKind kind = ForKind.LIST;
        if (iterable instanceof ListType list) {
            element = list.element;
        } else if (iterable instanceof MapType map) {
            element = map.key;
            kind = ForKind.MAP_KEYS;
        } else if (iterable == NativeType.STRING) {
            element = NativeType.STRING;
            kind = ForKind.STRING;
        } else if (iterable != NativeType.ERROR) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                    "for requires a collection or String", module.uri, forStmt.iterable.span)
                    .withTypes("List, MutableList, Map, MutableMap or String", iterable.display()));
            element = NativeType.ERROR;
        }
        forStmt.forKind = kind;
        if (forStmt.symbol != null) {
            forStmt.symbol.type = element == null ? NativeType.ERROR : element;
        }
        loopDepth++;
        narrowing.push(new HashMap<>());
        checkSequence(forStmt.body);
        narrowing.pop();
        loopDepth--;
    }

    private void checkTry(Stmt.Try tryStmt) {
        List<Type> caught = new ArrayList<>();
        for (Stmt.Try.CatchClause clause : tryStmt.catches) {
            Type type = clause.caughtType;
            if (type == null) {
                type = NativeType.ERROR;
            }
            if (type != NativeType.ERROR && !Semantics.isErrorType(type)) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                        "catch requires an error type", module.uri, clause.typeRef.span)
                        .withTypes("Error or imported Throwable", type.display()));
                type = NativeType.ERROR;
            }
            clause.caughtType = type;
            if (clause.symbol != null) {
                clause.symbol.type = type;
            }
            for (Type previous : caught) {
                if (Semantics.isAssignable(previous, type)
                        && previous != NativeType.ERROR && type != NativeType.ERROR) {
                    diagnostics.add(Diagnostic.error(Codes.FLOW_THROWS, Phase.FLOW,
                            "Catch of " + type.display() + " is unreachable: " + previous.display()
                                    + " already catches it", module.uri, clause.typeRef.span));
                }
            }
            caught.add(type);
        }
        caughtStack.push(caught);
        narrowing.push(new HashMap<>());
        checkSequence(tryStmt.body);
        narrowing.pop();
        caughtStack.pop();
        for (Stmt.Try.CatchClause clause : tryStmt.catches) {
            narrowing.push(new HashMap<>());
            if (clause.symbol != null) {
                narrowing.peek().put(clause.symbol, clause.caughtType.nonNull());
            }
            checkSequence(clause.body);
            narrowing.pop();
        }
        if (tryStmt.finallyBody != null) {
            narrowing.push(new HashMap<>());
            checkSequence(tryStmt.finallyBody);
            narrowing.pop();
        }
    }

    private void checkMatch(Stmt.Match match) {
        Type scrutinee = checkExpr(match.scrutinee, null);
        if (scrutinee.isNullable()) {
            diagnostics.add(Diagnostic.error(Codes.MATCH_SCRUTINEE, Phase.TYPE,
                    "Match scrutinee may be null; check for null first",
                    module.uri, match.scrutinee.span).withTypes("non-nullable enum or variant", scrutinee.display()));
            scrutinee = scrutinee.nonNull();
        }
        boolean valid = scrutinee instanceof EnumType || scrutinee instanceof VariantType
                || scrutinee instanceof VariantCaseType;
        if (!valid && scrutinee != NativeType.ERROR) {
            diagnostics.add(Diagnostic.error(Codes.MATCH_SCRUTINEE, Phase.TYPE,
                    "match requires an enum or variant value", module.uri, match.scrutinee.span)
                    .withTypes("enum or variant", scrutinee.display()));
        }
        match.matchedType = scrutinee;
        Set<String> seen = new LinkedHashSet<>();
        List<String> expectedCases = new ArrayList<>();
        if (scrutinee instanceof EnumType enumType) {
            expectedCases.addAll(enumType.decl.cases);
        } else if (scrutinee instanceof VariantType variantType) {
            for (Decl.VariantCase variantCase : variantType.decl.cases) {
                expectedCases.add(variantCase.name);
            }
        } else if (scrutinee instanceof VariantCaseType caseType) {
            expectedCases.add(caseType.variantCase.name);
        }
        for (Stmt.Match.Branch branch : match.branches) {
            Type caseOwner = branch.caseTypeRef.resolved;
            Type expectedOwner = scrutinee instanceof VariantCaseType caseType
                    ? new VariantType(caseType.variant) : scrutinee;
            if (caseOwner != null && caseOwner != NativeType.ERROR
                    && (valid ? !caseOwner.equals(expectedOwner) : true)) {
                diagnostics.add(Diagnostic.error(Codes.MATCH_WRONG_TYPE, Phase.TYPE,
                        "Case '" + branch.caseName + "' does not belong to matched type "
                                + scrutinee.display(), module.uri, branch.caseTypeRef.span)
                        .withTypes(scrutinee.display(), caseOwner.display()));
            }
            if (scrutinee instanceof EnumType enumType) {
                if (!enumType.decl.cases.contains(branch.caseName)) {
                    diagnostics.add(Diagnostic.error(Codes.MATCH_UNKNOWN_CASE, Phase.TYPE,
                            "Enum " + enumType.decl.name + " has no case '" + branch.caseName + "'",
                            module.uri, branch.caseTypeRef.span));
                } else if (branch.binder != null) {
                    diagnostics.add(Diagnostic.error(Codes.MATCH_ENUM_BINDER, Phase.TYPE,
                            "Enum case " + enumType.decl.name + "." + branch.caseName
                                    + " has no payload; remove 'as " + branch.binder + "'",
                            module.uri, branch.caseTypeRef.span));
                }
            } else if (scrutinee instanceof VariantType variantType) {
                Decl.VariantCase variantCase = findVariantCase(variantType.decl, branch.caseName);
                if (variantCase == null) {
                    diagnostics.add(Diagnostic.error(Codes.MATCH_UNKNOWN_CASE, Phase.TYPE,
                            "Variant " + variantType.decl.name + " has no case '" + branch.caseName + "'",
                            module.uri, branch.caseTypeRef.span));
                } else {
                    branch.binderType = new VariantCaseType(variantType.decl, variantCase);
                    if (branch.binderSymbol != null) {
                        branch.binderSymbol.type = branch.binderType;
                    }
                }
            } else if (scrutinee instanceof VariantCaseType knownCase) {
                if (branch.caseName.equals(knownCase.variantCase.name)) {
                    branch.binderType = knownCase;
                    if (branch.binderSymbol != null) branch.binderSymbol.type = knownCase;
                } else if (findVariantCase(knownCase.variant, branch.caseName) == null) {
                    diagnostics.add(Diagnostic.error(Codes.MATCH_UNKNOWN_CASE, Phase.TYPE,
                            "Variant " + knownCase.variant.name + " has no case '" + branch.caseName + "'",
                            module.uri, branch.caseTypeRef.span));
                } else {
                    diagnostics.add(Diagnostic.error(Codes.FLOW_UNREACHABLE, Phase.FLOW,
                            "Case '" + knownCase.variant.name + "." + branch.caseName
                                    + "' is impossible for statically known " + knownCase.display(),
                            module.uri, branch.caseTypeRef.span));
                }
            }
            if (!seen.add(branch.caseName) && expectedCases.contains(branch.caseName)) {
                diagnostics.add(Diagnostic.error(Codes.MATCH_DUPLICATE, Phase.TYPE,
                        "Duplicate match case " + branch.caseName, module.uri, branch.caseTypeRef.span));
            }
            narrowing.push(new HashMap<>());
            if (branch.binderSymbol != null && branch.binderType != null) {
                narrowing.peek().put(branch.binderSymbol, branch.binderType);
            }
            checkSequence(branch.body);
            narrowing.pop();
        }
        if (valid) {
            for (String expected : expectedCases) {
                if (!seen.contains(expected)) {
                    String typeName = match.matchedType instanceof EnumType e ? e.decl.name
                            : match.matchedType instanceof VariantType v ? v.decl.name
                            : ((VariantCaseType) match.matchedType).variant.name;
                    diagnostics.add(Diagnostic.error(Codes.MATCH_NONEXHAUSTIVE, Phase.FLOW,
                            "Missing case: " + typeName + "." + expected, module.uri, match.span)
                            .withHint("Add 'case " + typeName + "." + expected + ":' (there is no default case)"));
                }
            }
        }
    }

    private static Decl.VariantCase findVariantCase(Decl.VariantDecl decl, String name) {
        for (Decl.VariantCase variantCase : decl.cases) {
            if (variantCase.name.equals(name)) {
                return variantCase;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Expressions
    // ------------------------------------------------------------------

    Type checkExpr(Expr expr, Type expected) {
        Type type;
        if (expr instanceof Expr.IntLit literal) {
            type = checkIntLiteral(literal, expected);
        } else if (expr instanceof Expr.FloatLit literal) {
            type = checkFloatLiteral(literal, expected);
        } else if (expr instanceof Expr.StringLit) {
            type = NativeType.STRING;
        } else if (expr instanceof Expr.BoolLit) {
            type = NativeType.BOOL;
        } else if (expr instanceof Expr.NullLit) {
            type = NativeType.NULL;
        } else if (expr instanceof Expr.Name name) {
            type = checkName(name);
        } else if (expr instanceof Expr.FieldAccess access) {
            type = checkFieldAccessExpr(access);
        } else if (expr instanceof Expr.Call call) {
            type = checkCall(call, expected);
        } else if (expr instanceof Expr.Index index) {
            type = checkIndex(index);
        } else if (expr instanceof Expr.Unary unary) {
            type = checkUnary(unary, expected);
        } else if (expr instanceof Expr.Binary binary) {
            type = checkBinary(binary);
        } else if (expr instanceof Expr.ListLit listLit) {
            type = checkListLit(listLit, expected);
        } else if (expr instanceof Expr.MapLit mapLit) {
            type = checkMapLit(mapLit, expected);
        } else if (expr instanceof Expr.Lambda lambda) {
            type = checkLambda(lambda, expected);
        } else {
            type = NativeType.ERROR;
        }
        expr.type = type;
        return type;
    }

    private Type checkIntLiteral(Expr.IntLit literal, Type expected) {
        Type target = expected == null ? NativeType.INT : expected.nonNull();
        if (target != NativeType.INT32 && target != NativeType.FLOAT
                && target != NativeType.FLOAT32) target = NativeType.INT;
        BigInteger max = target == NativeType.INT32 ? BigInteger.valueOf(Integer.MAX_VALUE)
                : target == NativeType.INT ? BigInteger.valueOf(Long.MAX_VALUE) : null;
        boolean inRange;
        if (max != null) {
            inRange = literal.value.compareTo(max) <= 0;
        } else if (target == NativeType.FLOAT32) {
            float f = literal.value.floatValue();
            inRange = Float.isFinite(f) && new BigDecimal(f).toBigInteger().equals(literal.value);
        } else {
            double d = literal.value.doubleValue();
            inRange = Double.isFinite(d) && new BigDecimal(d).toBigInteger().equals(literal.value);
        }
        if (!inRange) {
            diagnostics.add(Diagnostic.error(Codes.NUM_RANGE, Phase.TYPE,
                    "Integer literal " + literal.sourceText + " is not exactly representable as " + target.display(),
                    module.uri, literal.span).withTypes(target.display(), literal.sourceText));
            return NativeType.ERROR;
        }
        return target;
    }

    private Type checkFloatLiteral(Expr.FloatLit literal, Type expected) {
        Type target = expected != null && expected.nonNull() == NativeType.FLOAT32
                ? NativeType.FLOAT32 : NativeType.FLOAT;
        double value = target == NativeType.FLOAT32
                ? Float.parseFloat(literal.sourceText) : literal.value;
        boolean nonzeroText = nonzeroMantissa(literal.sourceText);
        if (!Double.isFinite(value) || (value == 0.0 && nonzeroText)) {
            diagnostics.add(Diagnostic.error(Codes.NUM_RANGE, Phase.TYPE,
                    "Floating literal " + literal.sourceText + " overflows or underflows " + target.display(),
                    module.uri, literal.span).withTypes(target.display(), literal.sourceText));
            return NativeType.ERROR;
        }
        return target;
    }

    private static boolean nonzeroMantissa(String sourceText) {
        for (int i = 0; i < sourceText.length(); i++) {
            char c = sourceText.charAt(i);
            if (c == 'e' || c == 'E') break;
            if (c >= '1' && c <= '9') return true;
        }
        return false;
    }

    private Type checkName(Expr.Name name) {
        Symbol symbol = name.symbol;
        if (symbol == null) {
            return NativeType.ERROR;
        }
        switch (symbol.kind) {
            case LOCAL, PARAM, TOP_VAR, FIELD -> {
                return narrowedType(symbol);
            }
            case FUNCTION -> {
                if (symbol.decl == null) {
                    diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                            "Built-in function '" + name.name + "' must be called", module.uri, name.span));
                } else {
                    diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                            "Function '" + name.name + "' is not a first-class value; call it",
                            module.uri, name.span));
                }
                return NativeType.ERROR;
            }
            case METHOD -> {
                diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                        "Method '" + name.name + "' is not a first-class value; call it",
                        module.uri, name.span));
                return NativeType.ERROR;
            }
            case CLASS, ENUM, VARIANT, BUILTIN_TYPE, JAVA_TYPE -> {
                diagnostics.add(Diagnostic.error(Codes.NAME_NOT_A_VALUE, Phase.NAME,
                        "Type name '" + name.name + "' cannot be used as a value", module.uri, name.span));
                return NativeType.ERROR;
            }
            case MODULE -> {
                diagnostics.add(Diagnostic.error(Codes.NAME_NOT_A_VALUE, Phase.NAME,
                        "Module name '" + name.name + "' cannot be used as a value", module.uri, name.span));
                return NativeType.ERROR;
            }
            default -> {
                return NativeType.ERROR;
            }
        }
    }

    private Type checkFieldAccessExpr(Expr.FieldAccess access) {
        ResolvedField field = resolveFieldAccess(access, false);
        if (field.kind == ResolvedField.Kind.VARIANT_CASE_VALUE && !field.payloadless) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                    "Variant case " + field.type.display()
                            + " has a payload; construct it with named arguments",
                    module.uri, access.span));
        } else if (field.kind == ResolvedField.Kind.MODULE_TYPE) {
            diagnostics.add(Diagnostic.error(Codes.NAME_NOT_A_VALUE, Phase.NAME,
                    "Type '" + access.name + "' from module " + field.module.name
                            + " cannot be used as a value",
                    module.uri, access.span));
        } else if (field.kind == ResolvedField.Kind.METHOD || field.kind == ResolvedField.Kind.JVM_METHOD
                || field.kind == ResolvedField.Kind.BUILTIN_METHOD) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                    "Method '" + access.name + "' is not a first-class value; call it",
                    module.uri, access.span));
        }
        return field.type == null ? NativeType.ERROR : field.type;
    }

    private Type checkIndex(Expr.Index index) {
        Type receiver = checkExpr(index.receiver, null);
        if (receiver.isNullable()) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_NULLABLE, Phase.TYPE,
                    "Cannot index a value that may be null", module.uri, index.span));
            receiver = receiver.nonNull();
        }
        if (receiver instanceof ListType list) {
            Type idx = checkExpr(index.index, NativeType.INT);
            requireAssignable(NativeType.INT, idx, index.index.span, Codes.TYPE_MISMATCH, "list index");
            return list.element;
        }
        if (receiver instanceof MapType map) {
            Type idx = checkExpr(index.index, map.key);
            requireAssignable(map.key, idx, index.index.span, Codes.TYPE_MISMATCH, "map key");
            return NullableType.of(map.value);
        }
        if (receiver == NativeType.STRING) {
            Type idx = checkExpr(index.index, NativeType.INT);
            requireAssignable(NativeType.INT, idx, index.index.span, Codes.TYPE_MISMATCH, "string index");
            return NativeType.STRING;
        }
        if (receiver != NativeType.ERROR) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                    "Type " + receiver.display() + " does not support indexing", module.uri, index.span));
        }
        checkExpr(index.index, null);
        return NativeType.ERROR;
    }

    private Type checkUnary(Expr.Unary unary, Type expected) {
        if (unary.op.equals("not")) {
            Type type = checkExpr(unary.operand, NativeType.BOOL);
            if (type != NativeType.BOOL && type != NativeType.ERROR) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                        "Operand of 'not' must be Bool", module.uri, unary.span)
                        .withTypes("Bool", type.display()));
            }
            return NativeType.BOOL;
        }
        Type target = expected == null ? NativeType.INT : expected.nonNull();
        if (unary.op.equals("-") && unary.operand instanceof Expr.IntLit literal
                && (target == NativeType.INT || target == NativeType.INT32)) {
            BigInteger magnitude = target == NativeType.INT32
                    ? BigInteger.ONE.shiftLeft(31) : BigInteger.ONE.shiftLeft(63);
            if (literal.value.equals(magnitude)) {
                literal.type = target;
                return target;
            }
        }
        Type type = checkExpr(unary.operand, expected);
        if (type != NativeType.INT && type != NativeType.INT32
                && type != NativeType.FLOAT && type != NativeType.FLOAT32
                && type != NativeType.DECIMAL && type != NativeType.BIGINT
                && type != NativeType.ERROR) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                    "Operand of unary '" + unary.op + "' must be numeric", module.uri, unary.span)
                    .withTypes("numeric", type.display()));
            return NativeType.ERROR;
        }
        return type;
    }

    private Type checkBinary(Expr.Binary binary) {
        String op = binary.op;
        if (op.equals("and") || op.equals("or")) {
            Type left = checkExpr(binary.left, NativeType.BOOL);
            Type right = checkExpr(binary.right, NativeType.BOOL);
            if ((left != NativeType.BOOL && left != NativeType.ERROR)
                    || (right != NativeType.BOOL && right != NativeType.ERROR)) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                        "'" + op + "' requires Bool operands", module.uri, binary.span)
                        .withTypes("Bool", left != NativeType.BOOL && left != NativeType.ERROR
                                ? left.display() : right.display()));
            }
            return NativeType.BOOL;
        }
        if (op.equals("in")) {
            return checkIn(binary);
        }
        Type left = checkExpr(binary.left, null);
        Type right = checkExpr(binary.right,
                binary.right instanceof Expr.IntLit || binary.right instanceof Expr.FloatLit ? left : null);
        return switch (op) {
            case "==", "!=" -> checkEquality(binary, left, right);
            case "+", "-", "*", "/", "%" -> checkArithmetic(op, left, right, binary.span, false);
            case "<", "<=", ">", ">=" -> checkOrdering(binary, left, right);
            default -> NativeType.ERROR;
        };
    }

    private Type checkIn(Expr.Binary binary) {
        Type left = checkExpr(binary.left, null);
        Type right = checkExpr(binary.right, null);
        if (right.isNullable()) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_NULLABLE, Phase.TYPE,
                    "Right side of 'in' may be null", module.uri, binary.span));
            right = right.nonNull();
        }
        if (right instanceof ListType list) {
            requireAssignable(list.element, left, binary.left.span, Codes.TYPE_MISMATCH, "list element");
            return NativeType.BOOL;
        }
        if (right instanceof MapType map) {
            requireAssignable(map.key, left, binary.left.span, Codes.TYPE_MISMATCH, "map key");
            return NativeType.BOOL;
        }
        if (right == NativeType.STRING) {
            if (left != NativeType.STRING) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                        "'in' on String requires a String left operand", module.uri, binary.span)
                        .withTypes("String", left.display()));
            }
            return NativeType.BOOL;
        }
        if (right != NativeType.ERROR) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                    "'in' requires List, Map or String on the right", module.uri, binary.span)
                    .withTypes("List, Map or String", right.display()));
        }
        return NativeType.BOOL;
    }

    private Type checkEquality(Expr.Binary binary, Type left, Type right) {
        if (left == NativeType.NULL || right == NativeType.NULL) {
            Type other = left == NativeType.NULL ? right : left;
            if (other == NativeType.NULL) {
                return NativeType.BOOL;
            }
            if (other.isNullable() || Semantics.isReference(other)) {
                return NativeType.BOOL;
            }
            if (other != NativeType.ERROR) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                        "Cannot compare " + other.display() + " with null", module.uri, binary.span)
                        .withTypes("nullable or reference type", other.display()));
            }
            return NativeType.BOOL;
        }
        if (left == NativeType.ERROR || right == NativeType.ERROR) {
            return NativeType.BOOL;
        }
        Type leftBase = left.nonNull();
        Type rightBase = right.nonNull();
        boolean safeNumericPair = (isInteger(leftBase) && isInteger(rightBase))
                || (isBinaryFloat(leftBase) && isBinaryFloat(rightBase));
        if (!safeNumericPair && !leftBase.equals(rightBase)
                && !Semantics.isAssignable(leftBase, rightBase)
                && !Semantics.isAssignable(rightBase, leftBase)) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                    "Cannot compare " + left.display() + " with " + right.display(),
                    module.uri, binary.span).withTypes(left.display(), right.display()));
            return NativeType.BOOL;
        }
        // Reference-like values (including String) use value equality in Sprig;
        // Int/Float/Bool use primitive comparison.
        binary.valueEquality = Semantics.isReference(leftBase);
        return NativeType.BOOL;
    }

    private Type checkOrdering(Expr.Binary binary, Type left, Type right) {
        boolean numeric = (isInteger(left) && isInteger(right))
                || (isBinaryFloat(left) && isBinaryFloat(right))
                || (left == NativeType.DECIMAL && right == NativeType.DECIMAL)
                || (left == NativeType.BIGINT && right == NativeType.BIGINT);
        boolean strings = left == NativeType.STRING && right == NativeType.STRING;
        if (left == NativeType.ERROR || right == NativeType.ERROR) {
            return NativeType.BOOL;
        }
        if (!numeric && !strings) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                    "Operator '" + binary.op + "' requires matching Int, Float or String operands",
                    module.uri, binary.span).withTypes("Int/Float/String pair", left.display() + " and " + right.display()));
        }
        return NativeType.BOOL;
    }

    private Type checkArithmetic(String op, Type left, Type right, Span span, boolean compound) {
        if (left == NativeType.ERROR || right == NativeType.ERROR) {
            return NativeType.ERROR;
        }
        if (op.equals("+") && (left == NativeType.STRING || right == NativeType.STRING)) {
            Type other = left == NativeType.STRING ? right : left;
            if (other == NativeType.UNIT || other == NativeType.NULL) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                        "Cannot concatenate " + other.display() + " with String", module.uri, span));
                return NativeType.ERROR;
            }
            return NativeType.STRING;
        }
        if (isInteger(left) && isInteger(right)) {
            if (op.equals("/")) {
                diagnostics.add(Diagnostic.error(Codes.NUM_DIVISION, Phase.TYPE,
                        "Integer / would truncate; use a.divTrunc(b), or convert both operands explicitly",
                        module.uri, span).withTypes("explicit division", left.display() + " / " + right.display())
                        .withHint("Use divTrunc for deliberate truncation; use checked toFloat() for exact Float conversion."));
                return NativeType.ERROR;
            }
            return left == NativeType.INT32 && right == NativeType.INT32 ? NativeType.INT32 : NativeType.INT;
        }
        if (isBinaryFloat(left) && isBinaryFloat(right)) {
            return left == NativeType.FLOAT32 && right == NativeType.FLOAT32
                    ? NativeType.FLOAT32 : NativeType.FLOAT;
        }
        if (left == NativeType.DECIMAL && right == NativeType.DECIMAL) {
            if (op.equals("/") || op.equals("%")) {
                diagnostics.add(Diagnostic.error(Codes.NUM_DIVISION, Phase.TYPE,
                        "Decimal division needs an explicit scale and rounding mode",
                        module.uri, span).withHint("Use a.divide(b, scale, \"HALF_EVEN\") or another rounding mode."));
                return NativeType.ERROR;
            }
            return NativeType.DECIMAL;
        }
        if (left == NativeType.BIGINT && right == NativeType.BIGINT) {
            if (op.equals("/")) {
                diagnostics.add(Diagnostic.error(Codes.NUM_DIVISION, Phase.TYPE,
                        "BigInt / would truncate; use a.divTrunc(b)", module.uri, span));
                return NativeType.ERROR;
            }
            return NativeType.BIGINT;
        }
        diagnostics.add(Diagnostic.error(Codes.NUM_MIXED, Phase.TYPE,
                "Operator '" + op + "' has no implicit conversion between " + left.display()
                        + " and " + right.display(), module.uri, span)
                .withTypes("matching numeric families", left.display() + " and " + right.display())
                .withHint("Convert deliberately with an exact or explicitly lossy numeric method."));
        return NativeType.ERROR;
    }

    private static boolean isInteger(Type type) {
        return type == NativeType.INT || type == NativeType.INT32;
    }

    private static boolean isBinaryFloat(Type type) {
        return type == NativeType.FLOAT || type == NativeType.FLOAT32;
    }

    private static boolean isNumeric(Type type) {
        return isInteger(type) || isBinaryFloat(type)
                || type == NativeType.DECIMAL || type == NativeType.BIGINT;
    }

    private Type checkListLit(Expr.ListLit lit, Type expected) {
        Type element = null;
        boolean mutable = true;
        Type expectedBase = expected == null ? null : expected.nonNull();
        if (expectedBase instanceof ListType listType) {
            element = listType.element;
            mutable = listType.mutable;
        }
        Type inferred = null;
        for (Expr item : lit.items) {
            Type itemType = checkExpr(item, element);
            if (element != null) {
                requireAssignable(element, itemType, item.span, Codes.TYPE_MISMATCH, "list element");
            } else {
                Type common = commonType(inferred, itemType);
                if (common == null) {
                    diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                            "List elements must share one type", module.uri, item.span)
                            .withTypes(inferred == null ? "?" : inferred.display(), itemType.display()));
                } else {
                    inferred = common;
                }
            }
        }
        if (element == null) {
            if (lit.items.isEmpty()) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_INFER, Phase.TYPE,
                        "Cannot infer the type of an empty list literal; add a type annotation",
                        module.uri, lit.span));
                element = NativeType.ERROR;
            } else {
                element = inferred == null ? NativeType.ERROR : inferred;
                if (element == NativeType.NULL) {
                    diagnostics.add(Diagnostic.error(Codes.TYPE_INFER, Phase.TYPE,
                            "Cannot infer the element type of [null]; add a type annotation",
                            module.uri, lit.span));
                    element = NativeType.ERROR;
                }
            }
        }
        lit.mutable = mutable;
        return new ListType(element, mutable);
    }

    private Type checkMapLit(Expr.MapLit lit, Type expected) {
        Type keyType = null;
        Type valueType = null;
        boolean mutable = true;
        Type expectedBase = expected == null ? null : expected.nonNull();
        if (expectedBase instanceof MapType mapType) {
            keyType = mapType.key;
            valueType = mapType.value;
            mutable = mapType.mutable;
        }
        Type inferredKey = null;
        Type inferredValue = null;
        for (int i = 0; i < lit.keys.size(); i++) {
            Expr key = lit.keys.get(i);
            Expr value = lit.values.get(i);
            Type keyActual = checkExpr(key, keyType);
            Type valueActual = checkExpr(value, valueType);
            if (keyType != null) {
                requireAssignable(keyType, keyActual, key.span, Codes.TYPE_MISMATCH, "map key");
            } else {
                Type common = commonType(inferredKey, keyActual);
                if (common == null) {
                    diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                            "Map keys must share one type", module.uri, key.span)
                            .withTypes(inferredKey == null ? "?" : inferredKey.display(), keyActual.display()));
                } else {
                    inferredKey = common;
                }
            }
            if (valueType != null) {
                requireAssignable(valueType, valueActual, value.span, Codes.TYPE_MISMATCH, "map value");
            } else {
                Type common = commonType(inferredValue, valueActual);
                if (common == null) {
                    diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                            "Map values must share one type", module.uri, value.span)
                            .withTypes(inferredValue == null ? "?" : inferredValue.display(), valueActual.display()));
                } else {
                    inferredValue = common;
                }
            }
        }
        if (keyType == null) {
            if (lit.keys.isEmpty()) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_INFER, Phase.TYPE,
                        "Cannot infer the type of an empty map literal; add a type annotation",
                        module.uri, lit.span));
                keyType = NativeType.ERROR;
                valueType = NativeType.ERROR;
            } else {
                keyType = inferredKey == null ? NativeType.ERROR : inferredKey;
                valueType = inferredValue == null ? NativeType.ERROR : inferredValue;
            }
        }
        if (keyType.nonNull() == NativeType.FLOAT || keyType.nonNull() == NativeType.FLOAT32) {
            diagnostics.add(Diagnostic.error(Codes.NUM_CONVERSION, Phase.TYPE,
                    "Float and Float32 cannot be Map keys: NaN and signed zero have no stable key equality",
                    module.uri, lit.span).withHint("Use an explicit quantized Int key or Decimal key."));
            keyType = NativeType.ERROR;
        }
        lit.mutable = mutable;
        return new MapType(keyType, valueType, mutable);
    }

    /** Least common type used for list/map literal inference (no Any fallback). */
    private Type commonType(Type a, Type b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        if (a == NativeType.ERROR || b == NativeType.ERROR) {
            return NativeType.ERROR;
        }
        if (a.equals(b)) {
            return a;
        }
        if (Semantics.isAssignable(a, b)) {
            return a;
        }
        if (Semantics.isAssignable(b, a)) {
            return b;
        }
        if (a instanceof VariantCaseType caseA && b instanceof VariantCaseType caseB
                && caseA.variant == caseB.variant) {
            return new VariantType(caseA.variant);
        }
        return null;
    }

    private Type checkLambda(Expr.Lambda lambda, Type expected) {
        List<Type> params = new ArrayList<>();
        for (Decl.Param param : lambda.params) {
            params.add(param.type == null ? NativeType.ERROR : param.type);
        }
        lambdaDepth++;
        Type bodyType;
        try {
            bodyType = checkExpr(lambda.body, null);
        } finally {
            lambdaDepth--;
        }
        Set<Symbol> captures = new LinkedHashSet<>();
        collectMutableCaptures(lambda.body, captures);
        for (Symbol capture : captures) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_CAPTURE, Phase.TYPE,
                    "Lambda captures mutable local '" + capture.name + "'", module.uri, lambda.span)
                    .withHint("Copy it into a 'let' binding before the lambda, or use a class field."));
        }
        FunctionType functionType = new FunctionType(params, bodyType);
        if (expected != null && expected.nonNull() instanceof FunctionType expectedFunction) {
            requireAssignable(expectedFunction, functionType, lambda.span, Codes.TYPE_MISMATCH, "lambda");
        }
        return functionType;
    }

    private void collectMutableCaptures(Expr expr, Set<Symbol> out) {
        if (expr instanceof Expr.Name name) {
            Symbol symbol = name.symbol;
            if (symbol != null && symbol.kind == Symbol.Kind.LOCAL && symbol.mutable) {
                out.add(symbol);
            }
        } else if (expr instanceof Expr.FieldAccess access) {
            collectMutableCaptures(access.receiver, out);
        } else if (expr instanceof Expr.Call call) {
            collectMutableCaptures(call.callee, out);
            for (Expr.Arg arg : call.args) {
                collectMutableCaptures(arg.value, out);
            }
        } else if (expr instanceof Expr.Index index) {
            collectMutableCaptures(index.receiver, out);
            collectMutableCaptures(index.index, out);
        } else if (expr instanceof Expr.Unary unary) {
            collectMutableCaptures(unary.operand, out);
        } else if (expr instanceof Expr.Binary binary) {
            collectMutableCaptures(binary.left, out);
            collectMutableCaptures(binary.right, out);
        } else if (expr instanceof Expr.ListLit list) {
            for (Expr item : list.items) {
                collectMutableCaptures(item, out);
            }
        } else if (expr instanceof Expr.MapLit map) {
            for (Expr key : map.keys) {
                collectMutableCaptures(key, out);
            }
            for (Expr value : map.values) {
                collectMutableCaptures(value, out);
            }
        } else if (expr instanceof Expr.Lambda lambda) {
            collectMutableCaptures(lambda.body, out);
        }
    }

    // ------------------------------------------------------------------
    // Calls
    // ------------------------------------------------------------------

    private Type checkCall(Expr.Call call, Type expected) {
        Expr callee = call.callee;
        if (callee instanceof Expr.Name name) {
            Symbol symbol = name.symbol;
            if (symbol == null) {
                return NativeType.ERROR;
            }
            switch (symbol.kind) {
                case FUNCTION -> {
                    if (symbol.decl == null) {
                        return checkBuiltinFunction(name.name, call);
                    }
                    Decl.Func func = (Decl.Func) symbol.decl;
                    ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.FUNCTION, func.returnType);
                    resolved.symbol = symbol;
                    resolved.methodDecl = func;
                    checkPositionalCall(func, call, func.name);
                    return func.returnType;
                }
                case METHOD -> {
                    Decl.Func func = (Decl.Func) symbol.decl;
                    ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.METHOD, func.returnType);
                    resolved.symbol = symbol;
                    resolved.methodDecl = func;
                    checkPositionalCall(func, call, func.name);
                    return func.returnType;
                }
                case LOCAL, PARAM, TOP_VAR, FIELD -> {
                    Type type = narrowedType(symbol);
                    if (type instanceof FunctionType functionType) {
                        ResolvedCall resolved = resolvedCall(call,
                                ResolvedCall.Kind.FUNCTION_VALUE, functionType.result);
                        resolved.functionType = functionType;
                        checkFunctionValueCall(functionType, call);
                        return functionType.result;
                    }
                    diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                            "'" + name.name + "' is not callable", module.uri, call.span)
                            .withTypes("function value", type.display()));
                    checkArgsUnchecked(call);
                    return NativeType.ERROR;
                }
                case CLASS -> {
                    ClassType classType = (ClassType) symbol.type;
                    ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.CLASS_CTOR, classType);
                    resolved.classDecl = classType.decl;
                    resolved.symbol = symbol;
                    checkClassConstructor(classType, call);
                    return classType;
                }
                case JAVA_TYPE -> {
                    Class<?> javaClass = symbol.javaClass != null ? symbol.javaClass
                            : ((JavaType) symbol.type).clazz;
                    return checkJvmConstructor(new JavaType(javaClass), call);
                }
                case BUILTIN_TYPE -> {
                    if (name.name.equals("Error")) {
                        ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.JVM_CTOR,
                                new JavaType(SprigError.class));
                        JvmMember member = new JvmMember();
                        member.owner = SprigError.class;
                        member.name = "Error";
                        member.returnType = new JavaType(SprigError.class);
                        member.paramTypes = List.of(NativeType.STRING);
                        try {
                            member.executable = SprigError.class.getConstructor(String.class);
                        } catch (NoSuchMethodException e) {
                            throw new IllegalStateException(e);
                        }
                        resolved.jvm = member;
                        return checkErrorConstructor(call);
                    }
                    diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                            "Type " + name.name + " is not constructible", module.uri, call.span));
                    checkArgsUnchecked(call);
                    return NativeType.ERROR;
                }
                case VARIANT, ENUM -> {
                    diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                            "Construct a value with Type.Case(...) instead", module.uri, call.span));
                    checkArgsUnchecked(call);
                    return NativeType.ERROR;
                }
                case MODULE -> {
                    diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                            "Cannot call module '" + name.name + "'", module.uri, call.span));
                    checkArgsUnchecked(call);
                    return NativeType.ERROR;
                }
                default -> {
                    checkArgsUnchecked(call);
                    return NativeType.ERROR;
                }
            }
        }
        if (callee instanceof Expr.FieldAccess access) {
            ResolvedField field = resolveFieldAccess(access, true);
            switch (field.kind) {
                case VARIANT_CASE_VALUE -> {
                    ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.VARIANT_CTOR, field.type);
                    resolved.variantCase = field.variantCase;
                    return checkVariantConstructor(field, call);
                }
                case METHOD -> {
                    Decl.Func func = field.methodDecl;
                    ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.METHOD, func.returnType);
                    resolved.symbol = func.symbol;
                    resolved.methodDecl = func;
                    resolved.receiverType = field.receiverType;
                    checkPositionalCall(func, call, func.name);
                    return func.returnType;
                }
                case MODULE_FUNCTION -> {
                    Decl.Func func = field.methodDecl;
                    ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.MODULE_FUNCTION,
                            func.returnType);
                    resolved.symbol = field.symbol;
                    resolved.methodDecl = func;
                    checkPositionalCall(func, call, func.name);
                    return func.returnType;
                }
                case MODULE_TYPE -> {
                    if (field.type instanceof ClassType classType) {
                        ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.CLASS_CTOR, classType);
                        resolved.classDecl = classType.decl;
                        resolved.symbol = field.symbol;
                        checkClassConstructor(classType, call);
                        return classType;
                    }
                    diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                            "Construct a value with " + access.name + ".Case(...) instead",
                            module.uri, call.span));
                    checkArgsUnchecked(call);
                    return field.type == null ? NativeType.ERROR : field.type;
                }
                case BUILTIN_METHOD -> {
                    Type result = checkBuiltinMethod(field, call);
                    ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.BUILTIN_METHOD, result);
                    resolved.builtinId = field.builtinId;
                    resolved.receiverType = field.receiverType;
                    return result;
                }
                case JVM_METHOD -> {
                    Type result = checkJvmMethod(field, call);
                    ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.JVM_METHOD, result);
                    resolved.receiverType = field.receiverType;
                    resolved.jvm = field.jvm;
                    return result;
                }
                case CLASS_FIELD, MODULE_VAR -> {
                    Type type = field.type;
                    if (type instanceof FunctionType functionType) {
                        ResolvedCall resolved = resolvedCall(call,
                                ResolvedCall.Kind.FUNCTION_VALUE, functionType.result);
                        resolved.functionType = functionType;
                        checkFunctionValueCall(functionType, call);
                        return functionType.result;
                    }
                    diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                            "'" + access.name + "' is not callable", module.uri, call.span)
                            .withTypes("function value", type == null ? "?" : type.display()));
                    checkArgsUnchecked(call);
                    return NativeType.ERROR;
                }
                case ENUM_CASE -> {
                    diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                            "Enum case " + field.type.display()
                                    + " is a value, not a constructor; write it without parentheses",
                            module.uri, call.span));
                    checkArgsUnchecked(call);
                    return field.type;
                }
                default -> {
                    checkArgsUnchecked(call);
                    return NativeType.ERROR;
                }
            }
        }
        Type calleeType = checkExpr(callee, null);
        if (calleeType instanceof FunctionType functionType) {
            ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.FUNCTION_VALUE, functionType.result);
            resolved.functionType = functionType;
            checkFunctionValueCall(functionType, call);
            return functionType.result;
        }
        if (calleeType != NativeType.ERROR) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                    "Expression is not callable", module.uri, call.span)
                    .withTypes("function value", calleeType.display()));
        }
        checkArgsUnchecked(call);
        return NativeType.ERROR;
    }

    private ResolvedCall resolvedCall(Expr.Call call, ResolvedCall.Kind kind, Type returnType) {
        ResolvedCall resolved = ResolvedCall.of(kind, returnType);
        call.resolved = resolved;
        return resolved;
    }

    private void checkArgsUnchecked(Expr.Call call) {
        for (Expr.Arg arg : call.args) {
            checkExpr(arg.value, null);
        }
    }

    private void checkPositionalCall(Decl.Func func, Expr.Call call, String label) {
        if (call.hasNamedArgs()) {
            diagnostics.add(Diagnostic.error(Codes.CALL_POSITIONAL_REQUIRED, Phase.TYPE,
                    "Function '" + label + "' takes positional arguments only; remove the names",
                    module.uri, call.span)
                    .withHint("Ordinary functions and JVM methods use positional arguments; only Sprig class/variant constructors use named arguments."));
        }
        List<Expr.Arg> args = call.args;
        if (args.size() != func.params.size()) {
            diagnostics.add(Diagnostic.error(Codes.CALL_ARITY, Phase.TYPE,
                    "Function '" + label + "' expects " + func.params.size()
                            + " argument(s) but got " + args.size(),
                    module.uri, call.span));
        }
        int count = Math.min(args.size(), func.params.size());
        for (int i = 0; i < count; i++) {
            Type expected = func.params.get(i).type;
            Type actual = checkExpr(args.get(i).value, expected);
            requireAssignable(expected, actual, args.get(i).value.span, Codes.TYPE_MISMATCH,
                    "argument " + (i + 1) + " of " + label);
        }
        for (int i = count; i < args.size(); i++) {
            checkExpr(args.get(i).value, null);
        }
        requireHandled(func.throwsTypes, call.span);
    }

    private void checkFunctionValueCall(FunctionType functionType, Expr.Call call) {
        if (call.hasNamedArgs()) {
            diagnostics.add(Diagnostic.error(Codes.CALL_POSITIONAL_REQUIRED, Phase.TYPE,
                    "Function values take positional arguments only", module.uri, call.span));
        }
        if (call.args.size() != functionType.params.size()) {
            diagnostics.add(Diagnostic.error(Codes.CALL_ARITY, Phase.TYPE,
                    "Function value expects " + functionType.params.size()
                            + " argument(s) but got " + call.args.size(),
                    module.uri, call.span));
        }
        int count = Math.min(call.args.size(), functionType.params.size());
        for (int i = 0; i < count; i++) {
            Type expected = functionType.params.get(i);
            Type actual = checkExpr(call.args.get(i).value, expected);
            requireAssignable(expected, actual, call.args.get(i).value.span, Codes.TYPE_MISMATCH,
                    "argument " + (i + 1) + " of function value");
        }
    }

    private void checkClassConstructor(ClassType classType, Expr.Call call) {
        Decl.ClassDecl decl = classType.decl;
        if (call.hasPositionalArgs()) {
            diagnostics.add(Diagnostic.error(Codes.CALL_NAMED_REQUIRED, Phase.TYPE,
                    "Constructor of class " + decl.name + " requires named arguments, e.g. "
                            + decl.name + "(" + firstFieldSnippet(decl) + ")",
                    module.uri, call.span));
        }
        Set<String> provided = new HashSet<>();
        for (Expr.Arg arg : call.args) {
            if (arg.name == null) {
                checkExpr(arg.value, null);
                continue;
            }
            Decl.Field field = findField(decl, arg.name);
            if (field == null) {
                diagnostics.add(Diagnostic.error(Codes.CALL_UNKNOWN_FIELD, Phase.TYPE,
                        "Class " + decl.name + " has no field '" + arg.name + "'", module.uri, arg.value.span));
                checkExpr(arg.value, null);
                continue;
            }
            if (!provided.add(arg.name)) {
                diagnostics.add(Diagnostic.error(Codes.CALL_DUPLICATE_FIELD, Phase.TYPE,
                        "Duplicate constructor field '" + arg.name + "'", module.uri, arg.value.span));
            }
            Type actual = checkExpr(arg.value, field.type);
            requireAssignable(field.type, actual, arg.value.span, Codes.TYPE_MISMATCH,
                    "field '" + arg.name + "'");
        }
        Set<Type> omittedDefaultEffects = new LinkedHashSet<>();
        for (Decl.Field field : decl.fields) {
            if (field.defaultExpr == null && !provided.contains(field.name)) {
                diagnostics.add(Diagnostic.error(Codes.CALL_MISSING_FIELD, Phase.TYPE,
                        "Missing required field '" + field.name + ":" + field.type.display() + "'",
                        module.uri, call.span));
            }
            if (field.defaultExpr != null && !provided.contains(field.name)) {
                omittedDefaultEffects.addAll(field.defaultThrowsTypes);
            }
        }
        requireHandled(new ArrayList<>(omittedDefaultEffects), call.span);
    }

    private static String firstFieldSnippet(Decl.ClassDecl decl) {
        if (decl.fields.isEmpty()) {
            return "";
        }
        Decl.Field first = decl.fields.get(0);
        return first.name + "=" + (first.type == null ? "?" : first.type.display());
    }

    private static Decl.Field findField(Decl.ClassDecl decl, String name) {
        for (Decl.Field field : decl.fields) {
            if (field.name.equals(name)) {
                return field;
            }
        }
        return null;
    }

    private Type checkVariantConstructor(ResolvedField field, Expr.Call call) {
        Decl.VariantCase variantCase = field.variantCase;
        if (variantCase.payloadless()) {
            diagnostics.add(Diagnostic.error(Codes.CALL_ARITY, Phase.TYPE,
                    "Variant case " + field.type.display() + " has no payload; write "
                            + field.type.display() + " without parentheses",
                    module.uri, call.span));
            checkArgsUnchecked(call);
            return field.type;
        }
        if (call.hasPositionalArgs()) {
            diagnostics.add(Diagnostic.error(Codes.CALL_NAMED_REQUIRED, Phase.TYPE,
                    "Variant case " + field.type.display() + " requires named arguments, e.g. "
                            + field.type.display() + "(" + variantSnippet(variantCase) + ")",
                    module.uri, call.span));
        }
        Set<String> provided = new HashSet<>();
        for (Expr.Arg arg : call.args) {
            if (arg.name == null) {
                checkExpr(arg.value, null);
                continue;
            }
            Decl.Field payload = null;
            for (Decl.Field candidate : variantCase.fields) {
                if (candidate.name.equals(arg.name)) {
                    payload = candidate;
                    break;
                }
            }
            if (payload == null) {
                diagnostics.add(Diagnostic.error(Codes.CALL_UNKNOWN_FIELD, Phase.TYPE,
                        field.type.display() + " has no field '" + arg.name + "'", module.uri, arg.value.span));
                checkExpr(arg.value, null);
                continue;
            }
            if (!provided.add(arg.name)) {
                diagnostics.add(Diagnostic.error(Codes.CALL_DUPLICATE_FIELD, Phase.TYPE,
                        "Duplicate field '" + arg.name + "'", module.uri, arg.value.span));
            }
            Type actual = checkExpr(arg.value, payload.type);
            requireAssignable(payload.type, actual, arg.value.span, Codes.TYPE_MISMATCH,
                    "field '" + arg.name + "'");
        }
        for (Decl.Field payload : variantCase.fields) {
            if (!provided.contains(payload.name)) {
                diagnostics.add(Diagnostic.error(Codes.CALL_MISSING_FIELD, Phase.TYPE,
                        "Missing required field '" + payload.name + ":" + payload.type.display() + "'",
                        module.uri, call.span));
            }
        }
        return field.type;
    }

    private static String variantSnippet(Decl.VariantCase variantCase) {
        if (variantCase.fields.isEmpty()) {
            return "";
        }
        Decl.Field first = variantCase.fields.get(0);
        return first.name + "=" + (first.type == null ? "?" : first.type.display());
    }

    private Type checkErrorConstructor(Expr.Call call) {
        if (call.hasNamedArgs()) {
            diagnostics.add(Diagnostic.error(Codes.CALL_POSITIONAL_REQUIRED, Phase.TYPE,
                    "Error takes a positional message argument", module.uri, call.span));
        }
        if (call.args.size() != 1) {
            diagnostics.add(Diagnostic.error(Codes.CALL_ARITY, Phase.TYPE,
                    "Error expects 1 argument (message) but got " + call.args.size(),
                    module.uri, call.span));
        }
        if (!call.args.isEmpty()) {
            Type actual = checkExpr(call.args.get(0).value, NativeType.STRING);
            requireAssignable(NativeType.STRING, actual, call.args.get(0).value.span,
                    Codes.TYPE_MISMATCH, "Error message");
        }
        return new JavaType(SprigError.class);
    }

    private Type checkBuiltinFunction(String name, Expr.Call call) {
        ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.BUILTIN, NativeType.UNIT);
        resolved.builtinId = name;
        switch (name) {
            case "print" -> {
                if (call.hasNamedArgs()) {
                    diagnostics.add(Diagnostic.error(Codes.CALL_POSITIONAL_REQUIRED, Phase.TYPE,
                            "print takes a positional argument", module.uri, call.span));
                }
                if (call.args.size() != 1) {
                    diagnostics.add(Diagnostic.error(Codes.CALL_ARITY, Phase.TYPE,
                            "print expects 1 argument but got " + call.args.size(), module.uri, call.span));
                }
                if (!call.args.isEmpty()) {
                    Type actual = checkExpr(call.args.get(0).value, null);
                    if (actual == NativeType.UNIT && actual != NativeType.ERROR) {
                        diagnostics.add(Diagnostic.error(Codes.TYPE_UNIT, Phase.TYPE,
                                "Cannot print a Unit value", module.uri, call.args.get(0).value.span));
                    }
                }
                return NativeType.UNIT;
            }
            case "range" -> {
                if (call.hasNamedArgs()) {
                    diagnostics.add(Diagnostic.error(Codes.CALL_POSITIONAL_REQUIRED, Phase.TYPE,
                            "range takes positional arguments", module.uri, call.span));
                }
                if (call.args.isEmpty() || call.args.size() > 3) {
                    diagnostics.add(Diagnostic.error(Codes.CALL_ARITY, Phase.TYPE,
                            "range expects 1 to 3 Int arguments but got " + call.args.size(),
                            module.uri, call.span));
                }
                for (Expr.Arg arg : call.args) {
                    Type actual = checkExpr(arg.value, NativeType.INT);
                    requireAssignable(NativeType.INT, actual, arg.value.span, Codes.TYPE_MISMATCH,
                            "range bound");
                }
                return new ListType(NativeType.INT, true);
            }
            case "assert" -> {
                if (call.hasNamedArgs()) {
                    diagnostics.add(Diagnostic.error(Codes.CALL_POSITIONAL_REQUIRED, Phase.TYPE,
                            "assert takes positional arguments", module.uri, call.span));
                }
                if (call.args.isEmpty() || call.args.size() > 2) {
                    diagnostics.add(Diagnostic.error(Codes.CALL_ARITY, Phase.TYPE,
                            "assert expects 1 or 2 arguments but got " + call.args.size(),
                            module.uri, call.span));
                }
                if (!call.args.isEmpty()) {
                    Type actual = checkExpr(call.args.get(0).value, NativeType.BOOL);
                    requireAssignable(NativeType.BOOL, actual, call.args.get(0).value.span,
                            Codes.TYPE_MISMATCH, "assert condition");
                }
                if (call.args.size() == 2) {
                    Type actual = checkExpr(call.args.get(1).value, NativeType.STRING);
                    requireAssignable(NativeType.STRING, actual, call.args.get(1).value.span,
                            Codes.TYPE_MISMATCH, "assert message");
                }
                return NativeType.UNIT;
            }
            default -> {
                diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                        "Unknown built-in function '" + name + "'", module.uri, call.span));
                checkArgsUnchecked(call);
                return NativeType.ERROR;
            }
        }
    }

    // ------------------------------------------------------------------
    // Field access resolution
    // ------------------------------------------------------------------

    private ResolvedField resolveFieldAccess(Expr.FieldAccess access, boolean forCall) {
        if (access.receiver instanceof Expr.Name name && name.symbol != null) {
            Symbol symbol = name.symbol;
            ResolvedField resolved = switch (symbol.kind) {
                case MODULE -> moduleMember(symbol.module, access);
                case VARIANT -> variantCaseValue((VariantType) symbol.type, access);
                case ENUM -> enumCaseValue((EnumType) symbol.type, access);
                case BUILTIN_TYPE -> builtinStatic(symbol.type, access);
                case JAVA_TYPE -> javaMember(symbol.javaClass != null
                        ? new JavaType(symbol.javaClass) : (JavaType) symbol.type, access, true);
                case CLASS -> {
                    diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                            "Class " + name.name + " has no static members", module.uri, access.span));
                    yield errorField(access);
                }
                default -> null;
            };
            if (resolved != null) {
                access.resolved = resolved;
                return resolved;
            }
        }
        Type receiver = checkExpr(access.receiver, null);
        ResolvedField resolved;
        if (receiver == null || receiver == NativeType.ERROR) {
            resolved = errorField(access);
        } else if (receiver.isNullable()) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_NULLABLE, Phase.TYPE,
                    "Cannot access '" + access.name + "' on a value that may be null",
                    module.uri, access.span));
            receiver = receiver.nonNull();
            resolved = memberOf(receiver, access);
        } else {
            resolved = memberOf(receiver, access);
        }
        access.resolved = resolved;
        return resolved;
    }

    private ResolvedField memberOf(Type receiver, Expr.FieldAccess access) {
        if (receiver instanceof ClassType classType) {
            return classMember(classType, access);
        }
        if (receiver instanceof VariantCaseType caseType) {
            return variantPayload(caseType, access);
        }
        if (receiver instanceof JavaType javaType) {
            return javaMember(javaType, access, false);
        }
        if (receiver instanceof EnumType enumType) {
            if (access.name.equals("toString")) {
                return builtin(access, "toString", NativeType.STRING, receiver);
            }
            diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                    "Enum " + enumType.decl.name + " values have no member '" + access.name + "'",
                    module.uri, access.span));
            return errorField(access);
        }
        if (receiver instanceof VariantType variantType) {
            diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                    "Variant " + variantType.decl.name + " has no member '" + access.name
                            + "'; bind a case with match first",
                    module.uri, access.span));
            return errorField(access);
        }
        return builtinInstance(receiver, access);
    }

    private ResolvedField moduleMember(Module target, Expr.FieldAccess access) {
        Symbol function = target.scope.functions.get(access.name);
        if (function != null) {
            ResolvedField field = new ResolvedField();
            field.kind = ResolvedField.Kind.MODULE_FUNCTION;
            field.module = target;
            field.methodDecl = (Decl.Func) function.decl;
            field.type = function.type;
            field.symbol = function;
            return field;
        }
        Symbol variable = target.scope.topVars.get(access.name);
        if (variable != null) {
            ResolvedField field = new ResolvedField();
            field.kind = ResolvedField.Kind.MODULE_VAR;
            field.module = target;
            field.symbol = variable;
            field.type = variable.type == null ? NativeType.ERROR : variable.type;
            return field;
        }
        Symbol type = target.scope.types.get(access.name);
        if (type != null && type.decl != null) {
            ResolvedField field = new ResolvedField();
            field.kind = ResolvedField.Kind.MODULE_TYPE;
            field.module = target;
            field.symbol = type;
            field.type = type.type;
            if (type.decl instanceof Decl.ClassDecl classDecl) {
                field.methodDecl = null;
                field.variantCase = null;
                field.classDecl = classDecl;
            }
            return field;
        }
        diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                "Module '" + target.name + "' has no member '" + access.name + "'",
                module.uri, access.span));
        return errorField(access);
    }

    private ResolvedField variantCaseValue(VariantType variantType, Expr.FieldAccess access) {
        Decl.VariantCase variantCase = findVariantCase(variantType.decl, access.name);
        if (variantCase == null) {
            diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                    "Variant " + variantType.decl.name + " has no case '" + access.name + "'",
                    module.uri, access.span)
                    .withHint("Cases: " + variantType.decl.cases.stream()
                            .map(c -> c.name).toList()));
            return errorField(access);
        }
        ResolvedField field = new ResolvedField();
        field.kind = ResolvedField.Kind.VARIANT_CASE_VALUE;
        field.variantCase = variantCase;
        field.type = new VariantCaseType(variantType.decl, variantCase);
        field.payloadless = variantCase.payloadless();
        field.constructorRef = true;
        return field;
    }

    private ResolvedField enumCaseValue(EnumType enumType, Expr.FieldAccess access) {
        if (!enumType.decl.cases.contains(access.name)) {
            diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                    "Enum " + enumType.decl.name + " has no case '" + access.name + "'",
                    module.uri, access.span));
            return errorField(access);
        }
        ResolvedField field = new ResolvedField();
        field.kind = ResolvedField.Kind.ENUM_CASE;
        field.enumDecl = enumType.decl;
        field.enumCaseName = access.name;
        field.type = enumType;
        return field;
    }

    private ResolvedField builtinStatic(Type receiver, Expr.FieldAccess access) {
        if (receiver instanceof JavaType javaType) {
            return javaMember(javaType, access, true);
        }
        String id = builtinStaticId(receiver, access.name);
        if (id == null) {
            diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                    "Type " + receiver.display() + " has no static member '" + access.name + "'",
                    module.uri, access.span));
            return errorField(access);
        }
        return builtin(access, id, NativeType.ERROR, receiver);
    }

    private String builtinStaticId(Type receiver, String name) {
        if (receiver == NativeType.INT) {
            return switch (name) {
                case "parse", "abs", "min", "max" -> "Int." + name;
                default -> null;
            };
        }
        if (receiver == NativeType.FLOAT) {
            return switch (name) {
                case "sqrt", "floor", "ceil", "abs" -> "Float." + name;
                default -> null;
            };
        }
        if (receiver == NativeType.DECIMAL) {
            return switch (name) {
                case "parse", "fromInt", "fromJava" -> "Decimal." + name;
                default -> null;
            };
        }
        if (receiver == NativeType.BIGINT) {
            return switch (name) {
                case "parse", "fromInt", "fromJava" -> "BigInt." + name;
                default -> null;
            };
        }
        if (receiver == NativeType.STRING) {
            return switch (name) {
                case "join", "fromCode" -> "String." + name;
                default -> null;
            };
        }
        return null;
    }

    private ResolvedField classMember(ClassType classType, Expr.FieldAccess access) {
        for (Decl.Field field : classType.decl.fields) {
            if (field.name.equals(access.name)) {
                ResolvedField resolved = new ResolvedField();
                resolved.kind = ResolvedField.Kind.CLASS_FIELD;
                resolved.fieldDecl = field;
                resolved.symbol = field.symbol;
                resolved.type = field.type;
                resolved.receiverType = classType;
                return resolved;
            }
        }
        for (Decl.Func method : classType.decl.methods) {
            if (method.name.equals(access.name)) {
                ResolvedField resolved = new ResolvedField();
                resolved.kind = ResolvedField.Kind.METHOD;
                resolved.methodDecl = method;
                resolved.type = method.returnType;
                resolved.receiverType = classType;
                return resolved;
            }
        }
        if (access.name.equals("toString")) {
            return builtin(access, "toString", NativeType.STRING, classType);
        }
        diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                "Class " + classType.decl.name + " has no member '" + access.name + "'",
                module.uri, access.span));
        return errorField(access);
    }

    private ResolvedField variantPayload(VariantCaseType caseType, Expr.FieldAccess access) {
        for (Decl.Field field : caseType.variantCase.fields) {
            if (field.name.equals(access.name)) {
                ResolvedField resolved = new ResolvedField();
                resolved.kind = ResolvedField.Kind.VARIANT_PAYLOAD;
                resolved.fieldDecl = field;
                resolved.symbol = field.symbol;
                resolved.type = field.type;
                resolved.receiverType = caseType;
                return resolved;
            }
        }
        if (access.name.equals("toString")) {
            return builtin(access, "toString", NativeType.STRING, caseType);
        }
        diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                caseType.display() + " has no payload field '" + access.name + "'",
                module.uri, access.span));
        return errorField(access);
    }

    private ResolvedField javaMember(JavaType javaType, Expr.FieldAccess access, boolean staticContext) {
        Class<?> clazz = javaType.clazz;
        if (Throwable.class.isAssignableFrom(clazz) && access.name.equals("message")) {
            ResolvedField field = new ResolvedField();
            field.kind = ResolvedField.Kind.ERROR_MESSAGE;
            field.type = NativeType.STRING;
            return field;
        }
        try {
            java.lang.reflect.Field javaField = clazz.getField(access.name);
            if (staticContext == java.lang.reflect.Modifier.isStatic(javaField.getModifiers())) {
                ResolvedField field = new ResolvedField();
                field.kind = ResolvedField.Kind.JAVA_FIELD;
                JvmMember member = new JvmMember();
                member.field = javaField;
                member.owner = clazz;
                member.name = access.name;
                member.returnType = JavaTypes.mapValue(javaField.getType());
                field.jvm = member;
                field.type = member.returnType;
                return field;
            }
        } catch (NoSuchFieldException ignored) {
            // Fall through to method lookup.
        }
        for (Method method : clazz.getMethods()) {
            if (!method.getName().equals(access.name)) {
                continue;
            }
            boolean isStatic = java.lang.reflect.Modifier.isStatic(method.getModifiers());
            if (staticContext && !isStatic) {
                continue;
            }
            if (!staticContext && isStatic) {
                continue;
            }
            ResolvedField field = new ResolvedField();
            field.kind = ResolvedField.Kind.JVM_METHOD;
            JvmMember member = new JvmMember();
            member.owner = clazz;
            member.name = access.name;
            field.jvm = member;
            field.receiverType = javaType;
            return field;
        }
        diagnostics.add(Diagnostic.error(Codes.JVM_MEMBER, Phase.JVM,
                "Java class " + clazz.getSimpleName() + " has no "
                        + (staticContext ? "static " : "") + "member '" + access.name + "'",
                module.uri, access.span));
        return errorField(access);
    }

    private ResolvedField builtinInstance(Type receiver, Expr.FieldAccess access) {
        String id = builtinInstanceId(receiver, access.name);
        if (id == null) {
            if (access.name.equals("toString")) {
                return builtin(access, "toString", NativeType.STRING, receiver);
            }
            diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                    "Type " + receiver.display() + " has no method '" + access.name + "'",
                    module.uri, access.span));
            return errorField(access);
        }
        return builtin(access, id, NativeType.ERROR, receiver);
    }

    private String builtinInstanceId(Type receiver, String name) {
        if (receiver == NativeType.INT) {
            return switch (name) {
                case "toFloat", "toFloatExact", "toFloatLossy", "toInt32Exact",
                     "toDecimal", "divTrunc", "toString" -> "Int." + name;
                default -> null;
            };
        }
        if (receiver == NativeType.INT32) {
            return switch (name) {
                case "toInt", "toFloat", "toDecimal", "divTrunc", "toString" -> "Int32." + name;
                default -> null;
            };
        }
        if (receiver == NativeType.FLOAT) {
            return switch (name) {
                case "toInt", "toIntExact", "toIntTrunc", "toFloat32Exact",
                     "toFloat32Lossy", "isNaN", "isInfinite", "isFinite",
                     "approxEqual", "toString" -> "Float." + name;
                default -> null;
            };
        }
        if (receiver == NativeType.FLOAT32) {
            return switch (name) {
                case "toFloat", "isNaN", "isInfinite", "isFinite", "toString" -> "Float32." + name;
                default -> null;
            };
        }
        if (receiver == NativeType.DECIMAL) {
            return switch (name) {
                case "divide", "toIntExact", "toFloatExact", "toFloatLossy", "toJava", "toString" -> "Decimal." + name;
                default -> null;
            };
        }
        if (receiver == NativeType.BIGINT) {
            return switch (name) {
                case "divTrunc", "toIntExact", "toFloatExact", "toFloatLossy",
                     "toDecimal", "toJava", "toString" -> "BigInt." + name;
                default -> null;
            };
        }
        if (receiver == NativeType.BOOL) {
            return name.equals("toString") ? "Bool.toString" : null;
        }
        if (receiver == NativeType.STRING) {
            return switch (name) {
                case "length", "isEmpty", "charAt", "codeAt", "substring", "indexOf", "contains",
                     "startsWith", "endsWith", "toUpperCase", "toLowerCase", "trim", "split",
                     "replace", "repeat", "toInt", "toIntOrNull", "toFloat", "toString" -> "String." + name;
                default -> null;
            };
        }
        if (receiver instanceof ListType list) {
            return switch (name) {
                case "size", "isEmpty", "get", "contains", "indexOf", "toMutableList", "toList",
                     "map", "filter", "forEach" -> "List." + name;
                case "toString" -> "toString";
                case "append", "set", "insert", "removeAt", "remove", "clear", "sort" -> list.mutable
                        ? "MutableList." + name : "List.immutable." + name;
                default -> null;
            };
        }
        if (receiver instanceof MapType map) {
            return switch (name) {
                case "size", "isEmpty", "get", "containsKey", "keys", "values", "toMutableMap",
                     "toMap" -> "Map." + name;
                case "toString" -> "toString";
                case "set", "remove", "clear" -> map.mutable ? "MutableMap." + name : "Map.immutable." + name;
                default -> null;
            };
        }
        return null;
    }

    private ResolvedField builtin(Expr.FieldAccess access, String id, Type type, Type receiver) {
        ResolvedField field = new ResolvedField();
        field.kind = ResolvedField.Kind.BUILTIN_METHOD;
        field.builtinId = id;
        field.type = type;
        field.receiverType = receiver;
        access.resolved = field;
        return field;
    }

    private ResolvedField errorField(Expr.FieldAccess access) {
        ResolvedField field = new ResolvedField();
        field.kind = ResolvedField.Kind.MODULE_VAR;
        field.type = NativeType.ERROR;
        access.resolved = field;
        return field;
    }

    // ------------------------------------------------------------------
    // Built-in methods
    // ------------------------------------------------------------------

    private Type checkBuiltinMethod(ResolvedField field, Expr.Call call) {
        String id = field.builtinId;
        Type receiver = field.receiverType;
        if (id == null) {
            checkArgsUnchecked(call);
            return NativeType.ERROR;
        }
        if (id.startsWith("List.immutable.") || id.startsWith("Map.immutable.")) {
            diagnostics.add(Diagnostic.error(Codes.COLLECTION_IMMUTABLE, Phase.TYPE,
                    "Cannot call mutating method '" + id.substring(id.lastIndexOf('.') + 1)
                            + "' on an immutable collection; take an explicit snapshot with "
                            + (id.startsWith("List") ? "toMutableList()" : "toMutableMap()"),
                    module.uri, call.span));
            checkArgsUnchecked(call);
            return NativeType.ERROR;
        }
        int[] arity = BUILTIN_ARITY.get(id);
        if (arity != null && (call.args.size() < arity[0] || call.args.size() > arity[1])) {
            diagnostics.add(Diagnostic.error(Codes.CALL_ARITY, Phase.TYPE,
                    "Method '" + id.substring(id.lastIndexOf('.') + 1) + "' expects "
                            + (arity[0] == arity[1] ? String.valueOf(arity[0])
                                    : arity[0] + " to " + arity[1])
                            + " argument(s) but got " + call.args.size(),
                    module.uri, call.span));
            checkArgsUnchecked(call);
            return NativeType.ERROR;
        }
        Expr.Arg first = call.args.isEmpty() ? null : call.args.get(0);
        switch (id) {
            case "toString" -> {
                checkArity(call, 0, 0, id);
                return NativeType.STRING;
            }
            case "Int.toString", "Int.toFloat", "Int.toFloatExact", "Int.toFloatLossy" -> {
                checkArity(call, 0, 0, id);
                return id.contains("Float") ? NativeType.FLOAT : NativeType.STRING;
            }
            case "Int.toInt32Exact" -> {
                return NativeType.INT32;
            }
            case "Int.toDecimal", "Int32.toDecimal" -> {
                return NativeType.DECIMAL;
            }
            case "Int.divTrunc", "Int32.divTrunc" -> {
                Type target = id.startsWith("Int32") ? NativeType.INT32 : NativeType.INT;
                Type actual = checkExpr(first.value, target);
                requireAssignable(target, actual, first.value.span, Codes.NUM_CONVERSION, "division argument");
                return target;
            }
            case "Int32.toInt", "Int32.toFloat", "Int32.toString" -> {
                return id.endsWith("toInt") ? NativeType.INT
                        : id.endsWith("toFloat") ? NativeType.FLOAT : NativeType.STRING;
            }
            case "Float.toString", "Float.isNaN", "Float.isInfinite", "Float.isFinite" -> {
                checkArity(call, 0, 0, id);
                return id.endsWith("toString") ? NativeType.STRING : NativeType.BOOL;
            }
            case "Float.toInt", "Float.toIntExact", "Float.toIntTrunc" -> {
                checkArity(call, 0, 0, id);
                return NativeType.INT;
            }
            case "Float.toFloat32Exact", "Float.toFloat32Lossy" -> {
                return NativeType.FLOAT32;
            }
            case "Float.approxEqual" -> {
                for (Expr.Arg arg : call.args) {
                    Type actual = checkExpr(arg.value, NativeType.FLOAT);
                    requireAssignable(NativeType.FLOAT, actual, arg.value.span,
                            Codes.NUM_CONVERSION, "approxEqual argument");
                }
                return NativeType.BOOL;
            }
            case "Float32.toFloat", "Float32.toString", "Float32.isNaN",
                 "Float32.isInfinite", "Float32.isFinite" -> {
                return id.endsWith("toFloat") ? NativeType.FLOAT
                        : id.endsWith("toString") ? NativeType.STRING : NativeType.BOOL;
            }
            case "Decimal.parse" -> {
                requireString(first);
                return NativeType.DECIMAL;
            }
            case "Decimal.fromInt" -> {
                requireInt(first);
                return NativeType.DECIMAL;
            }
            case "Decimal.fromJava" -> {
                Type actual = checkExpr(first.value, null);
                requireAssignable(new JavaType(BigDecimal.class), actual,
                        first.value.span, Codes.TYPE_MISMATCH, "BigDecimal argument");
                return NativeType.DECIMAL;
            }
            case "Decimal.divide" -> {
                Type divisor = checkExpr(call.args.get(0).value, NativeType.DECIMAL);
                requireAssignable(NativeType.DECIMAL, divisor, call.args.get(0).value.span,
                        Codes.NUM_CONVERSION, "Decimal divisor");
                requireInt(call.args.get(1));
                requireString(call.args.get(2));
                return NativeType.DECIMAL;
            }
            case "Decimal.toString" -> { return NativeType.STRING; }
            case "Decimal.toIntExact" -> { return NativeType.INT; }
            case "Decimal.toFloatExact", "Decimal.toFloatLossy" -> { return NativeType.FLOAT; }
            case "Decimal.toJava" -> { return new JavaType(BigDecimal.class); }
            case "BigInt.parse" -> {
                requireString(first);
                return NativeType.BIGINT;
            }
            case "BigInt.fromInt" -> {
                requireInt(first);
                return NativeType.BIGINT;
            }
            case "BigInt.fromJava" -> {
                Type actual = checkExpr(first.value, null);
                requireAssignable(new JavaType(BigInteger.class), actual,
                        first.value.span, Codes.TYPE_MISMATCH, "BigInteger argument");
                return NativeType.BIGINT;
            }
            case "BigInt.divTrunc" -> {
                Type actual = checkExpr(first.value, NativeType.BIGINT);
                requireAssignable(NativeType.BIGINT, actual, first.value.span,
                        Codes.NUM_CONVERSION, "BigInt divisor");
                return NativeType.BIGINT;
            }
            case "BigInt.toString" -> { return NativeType.STRING; }
            case "BigInt.toIntExact" -> { return NativeType.INT; }
            case "BigInt.toFloatExact", "BigInt.toFloatLossy" -> { return NativeType.FLOAT; }
            case "BigInt.toDecimal" -> { return NativeType.DECIMAL; }
            case "BigInt.toJava" -> { return new JavaType(BigInteger.class); }
            case "Bool.toString" -> {
                checkArity(call, 0, 0, id);
                return NativeType.STRING;
            }
            case "String.length", "String.indexOf" -> {
                checkArity(call, id.endsWith("indexOf") ? 1 : 0, id.endsWith("indexOf") ? 1 : 0, id);
                if (first != null) {
                    requireString(call.args.get(0));
                }
                return NativeType.INT;
            }
            case "String.isEmpty", "String.toUpperCase", "String.toLowerCase", "String.trim",
                 "String.toString" -> {
                checkArity(call, 0, 0, id);
                return id.endsWith("isEmpty") ? NativeType.BOOL : NativeType.STRING;
            }
            case "String.charAt", "String.codeAt", "String.repeat" -> {
                checkArity(call, 1, 1, id);
                requireInt(call.args.get(0));
                return id.startsWith("String.codeAt") ? NativeType.INT : NativeType.STRING;
            }
            case "String.substring" -> {
                checkArity(call, 1, 2, id);
                for (Expr.Arg arg : call.args) {
                    requireInt(arg);
                }
                return NativeType.STRING;
            }
            case "String.contains", "String.startsWith", "String.endsWith" -> {
                checkArity(call, 1, 1, id);
                requireString(call.args.get(0));
                return NativeType.BOOL;
            }
            case "String.replace" -> {
                checkArity(call, 2, 2, id);
                for (Expr.Arg arg : call.args) {
                    requireString(arg);
                }
                return NativeType.STRING;
            }
            case "String.split" -> {
                checkArity(call, 1, 1, id);
                requireString(call.args.get(0));
                return new ListType(NativeType.STRING, false);
            }
            case "String.toInt" -> {
                checkArity(call, 0, 0, id);
                return NativeType.INT;
            }
            case "String.toIntOrNull" -> {
                checkArity(call, 0, 0, id);
                return NullableType.of(NativeType.INT);
            }
            case "String.toFloat" -> {
                checkArity(call, 0, 0, id);
                return NativeType.FLOAT;
            }
            case "List.size", "Map.size" -> {
                checkArity(call, 0, 0, id);
                return NativeType.INT;
            }
            case "List.isEmpty", "Map.isEmpty" -> {
                checkArity(call, 0, 0, id);
                return NativeType.BOOL;
            }
            case "List.get" -> {
                checkArity(call, 1, 1, id);
                requireInt(call.args.get(0));
                return ((ListType) receiver).element;
            }
            case "List.contains" -> {
                checkArity(call, 1, 1, id);
                ListType list = (ListType) receiver;
                Type actual = checkExpr(call.args.get(0).value, list.element);
                requireAssignable(list.element, actual, call.args.get(0).value.span,
                        Codes.TYPE_MISMATCH, "element");
                return NativeType.BOOL;
            }
            case "List.indexOf" -> {
                checkArity(call, 1, 1, id);
                ListType list = (ListType) receiver;
                Type actual = checkExpr(call.args.get(0).value, list.element);
                requireAssignable(list.element, actual, call.args.get(0).value.span,
                        Codes.TYPE_MISMATCH, "element");
                return NativeType.INT;
            }
            case "List.toMutableList" -> {
                checkArity(call, 0, 0, id);
                return new ListType(((ListType) receiver).element, true);
            }
            case "List.toList" -> {
                checkArity(call, 0, 0, id);
                return new ListType(((ListType) receiver).element, false);
            }
            case "List.map" -> {
                checkArity(call, 1, 1, id);
                ListType list = (ListType) receiver;
                FunctionType fn = requireFunctionArg(call.args.get(0), 1);
                if (fn != null) {
                    requireAssignable(list.element, fn.params.get(0), call.args.get(0).value.span,
                            Codes.TYPE_MISMATCH, "map argument");
                    return new ListType(fn.result, false);
                }
                return new ListType(list.element, false);
            }
            case "List.filter" -> {
                checkArity(call, 1, 1, id);
                ListType list = (ListType) receiver;
                FunctionType fn = requireFunctionArg(call.args.get(0), 1);
                if (fn != null) {
                    requireAssignable(list.element, fn.params.get(0), call.args.get(0).value.span,
                            Codes.TYPE_MISMATCH, "filter argument");
                    if (fn.result != NativeType.BOOL && fn.result != NativeType.ERROR) {
                        diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                                "filter predicate must return Bool", module.uri, call.span)
                                .withTypes("Bool", fn.result.display()));
                    }
                }
                return new ListType(list.element, false);
            }
            case "List.forEach" -> {
                checkArity(call, 1, 1, id);
                ListType list = (ListType) receiver;
                FunctionType fn = requireFunctionArg(call.args.get(0), 1);
                if (fn != null) {
                    requireAssignable(list.element, fn.params.get(0), call.args.get(0).value.span,
                            Codes.TYPE_MISMATCH, "forEach argument");
                }
                return NativeType.UNIT;
            }
            case "MutableList.append" -> {
                checkArity(call, 1, 1, id);
                ListType list = (ListType) receiver;
                Type actual = checkExpr(call.args.get(0).value, list.element);
                requireAssignable(list.element, actual, call.args.get(0).value.span,
                        Codes.TYPE_MISMATCH, "element");
                return NativeType.UNIT;
            }
            case "MutableList.set" -> {
                checkArity(call, 2, 2, id);
                ListType list = (ListType) receiver;
                requireInt(call.args.get(0));
                Type actual = checkExpr(call.args.get(1).value, list.element);
                requireAssignable(list.element, actual, call.args.get(1).value.span,
                        Codes.TYPE_MISMATCH, "element");
                return NativeType.UNIT;
            }
            case "MutableList.insert" -> {
                checkArity(call, 2, 2, id);
                ListType list = (ListType) receiver;
                requireInt(call.args.get(0));
                Type actual = checkExpr(call.args.get(1).value, list.element);
                requireAssignable(list.element, actual, call.args.get(1).value.span,
                        Codes.TYPE_MISMATCH, "element");
                return NativeType.UNIT;
            }
            case "MutableList.removeAt" -> {
                checkArity(call, 1, 1, id);
                ListType list = (ListType) receiver;
                requireInt(call.args.get(0));
                return list.element;
            }
            case "MutableList.remove", "MutableList.contains" -> {
                checkArity(call, 1, 1, id);
                ListType list = (ListType) receiver;
                Type actual = checkExpr(call.args.get(0).value, list.element);
                requireAssignable(list.element, actual, call.args.get(0).value.span,
                        Codes.TYPE_MISMATCH, "element");
                return NativeType.BOOL;
            }
            case "MutableList.clear", "MutableList.sort" -> {
                checkArity(call, 0, 0, id);
                if (id.endsWith("sort")) {
                    Type element = ((ListType) receiver).element;
                    boolean sortable = element == NativeType.INT || element == NativeType.FLOAT
                            || element == NativeType.STRING || element == NativeType.BOOL;
                    if (!sortable && element != NativeType.ERROR) {
                        diagnostics.add(Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                                "sort requires Int, Float, String or Bool elements",
                                module.uri, call.span).withTypes("comparable element", element.display()));
                    }
                }
                return NativeType.UNIT;
            }
            case "Map.get" -> {
                checkArity(call, 1, 1, id);
                MapType map = (MapType) receiver;
                Type actual = checkExpr(call.args.get(0).value, map.key);
                requireAssignable(map.key, actual, call.args.get(0).value.span,
                        Codes.TYPE_MISMATCH, "key");
                return NullableType.of(map.value);
            }
            case "Map.containsKey" -> {
                checkArity(call, 1, 1, id);
                MapType map = (MapType) receiver;
                Type actual = checkExpr(call.args.get(0).value, map.key);
                requireAssignable(map.key, actual, call.args.get(0).value.span,
                        Codes.TYPE_MISMATCH, "key");
                return NativeType.BOOL;
            }
            case "Map.keys" -> {
                checkArity(call, 0, 0, id);
                return new ListType(((MapType) receiver).key, false);
            }
            case "Map.values" -> {
                checkArity(call, 0, 0, id);
                return new ListType(((MapType) receiver).value, false);
            }
            case "Map.toMutableMap" -> {
                checkArity(call, 0, 0, id);
                MapType map = (MapType) receiver;
                return new MapType(map.key, map.value, true);
            }
            case "Map.toMap" -> {
                checkArity(call, 0, 0, id);
                MapType map = (MapType) receiver;
                return new MapType(map.key, map.value, false);
            }
            case "MutableMap.set" -> {
                checkArity(call, 2, 2, id);
                MapType map = (MapType) receiver;
                Type keyActual = checkExpr(call.args.get(0).value, map.key);
                requireAssignable(map.key, keyActual, call.args.get(0).value.span,
                        Codes.TYPE_MISMATCH, "key");
                Type valueActual = checkExpr(call.args.get(1).value, map.value);
                requireAssignable(map.value, valueActual, call.args.get(1).value.span,
                        Codes.TYPE_MISMATCH, "value");
                return NativeType.UNIT;
            }
            case "MutableMap.remove" -> {
                checkArity(call, 1, 1, id);
                MapType map = (MapType) receiver;
                Type keyActual = checkExpr(call.args.get(0).value, map.key);
                requireAssignable(map.key, keyActual, call.args.get(0).value.span,
                        Codes.TYPE_MISMATCH, "key");
                return NullableType.of(map.value);
            }
            case "MutableMap.clear" -> {
                checkArity(call, 0, 0, id);
                return NativeType.UNIT;
            }
            case "Int.parse", "Int.abs" -> {
                checkArity(call, 1, 1, id);
                requireStringOrInt(call.args.get(0), id.equals("Int.abs"));
                return NativeType.INT;
            }
            case "Int.min", "Int.max" -> {
                checkArity(call, 2, 2, id);
                for (Expr.Arg arg : call.args) {
                    requireInt(arg);
                }
                return NativeType.INT;
            }
            case "Float.sqrt", "Float.floor", "Float.ceil", "Float.abs" -> {
                checkArity(call, 1, 1, id);
                Type actual = checkExpr(call.args.get(0).value, NativeType.FLOAT);
                requireAssignable(NativeType.FLOAT, actual, call.args.get(0).value.span,
                        Codes.TYPE_MISMATCH, "argument");
                return NativeType.FLOAT;
            }
            case "String.join" -> {
                checkArity(call, 2, 2, id);
                Type parts = checkExpr(call.args.get(0).value, new ListType(NativeType.STRING, false));
                requireAssignable(new ListType(NativeType.STRING, false), parts,
                        call.args.get(0).value.span, Codes.TYPE_MISMATCH, "parts");
                requireString(call.args.get(1));
                return NativeType.STRING;
            }
            case "String.fromCode" -> {
                checkArity(call, 1, 1, id);
                requireInt(call.args.get(0));
                return NativeType.STRING;
            }
            default -> {
                diagnostics.add(Diagnostic.error(Codes.JVM_INTERNAL, Phase.TYPE,
                        "Unhandled built-in method '" + id + "'", module.uri, call.span));
                checkArgsUnchecked(call);
                return NativeType.ERROR;
            }
        }
    }

    private FunctionType requireFunctionArg(Expr.Arg arg, int arity) {
        Type type = checkExpr(arg.value, null);
        if (type instanceof FunctionType functionType) {
            if (functionType.params.size() != arity) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                        "Function argument must take " + arity + " parameter(s)",
                        module.uri, arg.value.span));
            }
            return functionType;
        }
        if (type != NativeType.ERROR) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                    "Expected a function value", module.uri, arg.value.span)
                    .withTypes("(T) -> R", type.display()));
        }
        return null;
    }

    private void requireString(Expr.Arg arg) {
        Type actual = checkExpr(arg.value, NativeType.STRING);
        requireAssignable(NativeType.STRING, actual, arg.value.span, Codes.TYPE_MISMATCH, "String argument");
    }

    private void requireStringOrInt(Expr.Arg arg, boolean intExpected) {
        Type actual = checkExpr(arg.value, intExpected ? NativeType.INT : NativeType.STRING);
        requireAssignable(intExpected ? NativeType.INT : NativeType.STRING, actual,
                arg.value.span, Codes.TYPE_MISMATCH, "argument");
    }

    private void requireInt(Expr.Arg arg) {
        Type actual = checkExpr(arg.value, NativeType.INT);
        requireAssignable(NativeType.INT, actual, arg.value.span, Codes.TYPE_MISMATCH, "Int argument");
    }

    private void checkArity(Expr.Call call, int min, int max, String id) {
        if (call.args.size() < min || call.args.size() > max) {
            diagnostics.add(Diagnostic.error(Codes.CALL_ARITY, Phase.TYPE,
                    "Method '" + id.substring(id.lastIndexOf('.') + 1) + "' expects "
                            + (min == max ? String.valueOf(min) : min + " to " + max)
                            + " argument(s) but got " + call.args.size(),
                    module.uri, call.span));
            for (Expr.Arg arg : call.args) {
                checkExpr(arg.value, null);
            }
        }
    }

    // ------------------------------------------------------------------
    // JVM members
    // ------------------------------------------------------------------

    private Type checkJvmConstructor(JavaType javaType, Expr.Call call) {
        if (call.hasNamedArgs()) {
            diagnostics.add(Diagnostic.error(Codes.CALL_POSITIONAL_REQUIRED, Phase.TYPE,
                    "Java constructors take positional arguments", module.uri, call.span));
        }
        List<Type> argTypes = new ArrayList<>();
        for (Expr.Arg arg : call.args) {
            argTypes.add(checkExpr(arg.value, null));
        }
        Constructor<?> best = null;
        int bestScore = -1;
        boolean ambiguous = false;
        for (Constructor<?> constructor : javaType.clazz.getConstructors()) {
            int score = scoreCandidate(constructor.getParameterTypes(), argTypes, call.args);
            if (score < 0) {
                continue;
            }
            if (score > bestScore) {
                best = constructor;
                bestScore = score;
                ambiguous = false;
            } else if (score == bestScore) {
                ambiguous = true;
            }
        }
        if (best == null) {
            List<Class<?>[]> candidates = new ArrayList<>();
            for (Constructor<?> constructor : javaType.clazz.getConstructors()) {
                candidates.add(constructor.getParameterTypes());
            }
            if (reportNullableJavaArgument(javaType.clazz.getSimpleName(), candidates, argTypes, call)) {
                return NativeType.ERROR;
            }
            diagnostics.add(Diagnostic.error(Codes.JVM_MEMBER, Phase.JVM,
                    "No constructor of " + javaType.clazz.getSimpleName() + " matches "
                            + argTypes.size() + " argument(s)", module.uri, call.span));
            return NativeType.ERROR;
        }
        if (ambiguous) {
            diagnostics.add(Diagnostic.error(Codes.JVM_AMBIGUOUS, Phase.JVM,
                    "Ambiguous constructor overload for " + javaType.clazz.getSimpleName(),
                    module.uri, call.span));
        }
        ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.JVM_CTOR, javaType);
        JvmMember member = new JvmMember();
        member.owner = javaType.clazz;
        member.name = "<init>";
        member.executable = best;
        member.paramTypes = new ArrayList<>();
        for (Class<?> param : best.getParameterTypes()) {
            member.paramTypes.add(JavaTypes.map(param));
        }
        member.returnType = javaType;
        resolved.jvm = member;
        requireHandled(jvmExceptions(best.getExceptionTypes()), call.span);
        return javaType;
    }

    private Type checkJvmMethod(ResolvedField field, Expr.Call call) {
        Type receiverType = field.receiverType;
        Class<?> clazz = ((JavaType) receiverType).clazz;
        if (call.hasNamedArgs()) {
            diagnostics.add(Diagnostic.error(Codes.CALL_POSITIONAL_REQUIRED, Phase.TYPE,
                    "Java methods take positional arguments", module.uri, call.span));
        }
        List<Type> argTypes = new ArrayList<>();
        for (Expr.Arg arg : call.args) {
            argTypes.add(checkExpr(arg.value, null));
        }
        Method best = null;
        int bestScore = -1;
        boolean ambiguous = false;
        for (Method method : clazz.getMethods()) {
            if (!method.getName().equals(field.jvm.name)) {
                continue;
            }
            boolean isStatic = java.lang.reflect.Modifier.isStatic(method.getModifiers());
            boolean receiverIsClass = call.callee instanceof Expr.FieldAccess access
                    && access.receiver instanceof Expr.Name name
                    && name.symbol != null && name.symbol.kind == Symbol.Kind.JAVA_TYPE;
            if (receiverIsClass != isStatic) {
                continue;
            }
            int score = scoreCandidate(method.getParameterTypes(), argTypes, call.args);
            if (score < 0) {
                continue;
            }
            if (score > bestScore) {
                best = method;
                bestScore = score;
                ambiguous = false;
            } else if (score == bestScore && !sameSignature(best, method)) {
                ambiguous = true;
            }
        }
        if (best == null) {
            List<Class<?>[]> candidates = new ArrayList<>();
            boolean receiverIsClass = call.callee instanceof Expr.FieldAccess access
                    && access.receiver instanceof Expr.Name name
                    && name.symbol != null && name.symbol.kind == Symbol.Kind.JAVA_TYPE;
            for (Method method : clazz.getMethods()) {
                if (!method.getName().equals(field.jvm.name)
                        || java.lang.reflect.Modifier.isStatic(method.getModifiers()) != receiverIsClass) {
                    continue;
                }
                candidates.add(method.getParameterTypes());
            }
            if (reportNullableJavaArgument(clazz.getSimpleName() + "." + field.jvm.name,
                    candidates, argTypes, call)) {
                return NativeType.ERROR;
            }
            diagnostics.add(Diagnostic.error(Codes.JVM_MEMBER, Phase.JVM,
                    "Java class " + clazz.getSimpleName() + " has no method '" + field.jvm.name
                            + "' matching " + argTypes.size() + " argument(s)",
                    module.uri, call.span));
            return NativeType.ERROR;
        }
        if (ambiguous) {
            diagnostics.add(Diagnostic.error(Codes.JVM_AMBIGUOUS, Phase.JVM,
                    "Ambiguous overload for " + clazz.getSimpleName() + "." + best.getName(),
                    module.uri, call.span));
        }
        JvmMember member = new JvmMember();
        member.owner = clazz;
        member.name = best.getName();
        member.executable = best;
        member.paramTypes = new ArrayList<>();
        for (Class<?> param : best.getParameterTypes()) {
            member.paramTypes.add(JavaTypes.map(param));
        }
        member.returnType = JavaTypes.mapValue(best.getReturnType());
        field.jvm = member;
        requireHandled(jvmExceptions(best.getExceptionTypes()), call.span);
        return member.returnType;
    }

    private static boolean isStaticReceiver(Expr.Call call) {
        return call.callee instanceof Expr.FieldAccess access
                && access.receiver instanceof Expr.Name name
                && name.symbol != null && name.symbol.kind == Symbol.Kind.JAVA_TYPE;
    }

    private static boolean sameSignature(Method a, Method b) {
        return a != null && b != null && java.util.Arrays.equals(a.getParameterTypes(), b.getParameterTypes());
    }

    private static int scoreCandidate(Class<?>[] params, List<Type> args, List<Expr.Arg> writtenArgs) {
        if (params.length != args.size()) {
            return -1;
        }
        int score = 0;
        for (int i = 0; i < params.length; i++) {
            Type arg = args.get(i);
            if (arg == NativeType.ERROR) {
                continue;
            }
            int argScore = scoreArgument(params[i], arg, writtenArgs.get(i).value);
            if (argScore < 0) {
                return -1;
            }
            score += argScore;
        }
        return score;
    }

    /**
     * Deterministic overload scoring. Sprig Int is a signed 64-bit value, so a
     * {@code long} is the only primitive integer target for Int, and
     * {@code double} the only primitive floating target for Float.
     */
    private static int scoreArgument(Class<?> param, Type arg, Expr expr) {
        // Java formals carry no nullability contract. A Sprig nullable value may
        // only cross into a formal that accepts null by contract
        // (java.lang.Object); everything else requires an explicit null check.
        if (arg.isNullable() && JavaTypes.boxed(param) != Object.class) {
            return -1;
        }
        Type base = arg.nonNull();
        if (base == NativeType.INT) {
            if (expr instanceof Expr.IntLit literal) {
                BigInteger max = param == int.class || param == Integer.class
                        ? BigInteger.valueOf(Integer.MAX_VALUE)
                        : param == short.class || param == Short.class
                        ? BigInteger.valueOf(Short.MAX_VALUE)
                        : param == byte.class || param == Byte.class
                        ? BigInteger.valueOf(Byte.MAX_VALUE) : null;
                if (max != null && literal.value.compareTo(max) <= 0) return 2;
            }
            if (param == long.class) {
                return 3;
            }
            if (param == Long.class) {
                return 2;
            }
            return JavaTypes.rawAssignable(param, arg) ? 1 : -1;
        }
        if (base == NativeType.INT32) {
            if (param == int.class) return 3;
            if (param == Integer.class) return 2;
            if (param == long.class) return 2;
            return JavaTypes.rawAssignable(param, arg) ? 1 : -1;
        }
        if (base == NativeType.FLOAT) {
            if (expr instanceof Expr.FloatLit literal && (param == float.class || param == Float.class)) {
                float value = Float.parseFloat(literal.sourceText);
                if (Float.isFinite(value) && (value != 0.0f
                        || !nonzeroMantissa(literal.sourceText))) return 2;
            }
            if (param == double.class) {
                return 3;
            }
            if (param == Double.class) {
                return 2;
            }
            return JavaTypes.rawAssignable(param, arg) ? 1 : -1;
        }
        if (base == NativeType.FLOAT32) {
            if (param == float.class) return 3;
            if (param == Float.class) return 2;
            if (param == double.class) return 2;
            return JavaTypes.rawAssignable(param, arg) ? 1 : -1;
        }
        if (base == NativeType.BOOL) {
            if (param == boolean.class) {
                return 3;
            }
            if (param == Boolean.class) {
                return 2;
            }
            return JavaTypes.rawAssignable(param, arg) ? 1 : -1;
        }
        if (base == NativeType.STRING) {
            if (param == String.class) {
                return 3;
            }
            if (param == char.class) {
                return 1;
            }
            return JavaTypes.rawAssignable(param, arg) ? 1 : -1;
        }
        if (base instanceof JavaType javaType) {
            if (param.equals(javaType.clazz)) {
                return 3;
            }
            return JavaTypes.rawAssignable(param, arg) ? 1 : -1;
        }
        return JavaTypes.rawAssignable(param, arg) ? 1 : -1;
    }

    /**
     * When overload resolution fails only because a nullable argument is passed
     * to a Java formal, report that precisely instead of "no matching method".
     */
    private boolean reportNullableJavaArgument(String memberLabel, List<Class<?>[]> candidates,
                                               List<Type> argTypes, Expr.Call call) {
        int nullableIndex = -1;
        for (int i = 0; i < argTypes.size(); i++) {
            if (argTypes.get(i).isNullable() && argTypes.get(i) != NativeType.ERROR) {
                nullableIndex = i;
                break;
            }
        }
        if (nullableIndex < 0) {
            return false;
        }
        List<Type> projected = new ArrayList<>(argTypes);
        projected.set(nullableIndex, argTypes.get(nullableIndex).nonNull());
        for (Class<?>[] params : candidates) {
            if (params.length == argTypes.size()
                    && scoreCandidate(params, projected, call.args) >= 0) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_NULLABLE, Phase.TYPE,
                        "Nullable value is not accepted by Java parameter " + (nullableIndex + 1)
                                + " of " + memberLabel + "; Java parameters are treated as non-null",
                        module.uri, call.args.get(nullableIndex).value.span)
                        .withTypes(JavaTypes.map(params[nullableIndex]).display(),
                                argTypes.get(nullableIndex).display())
                        .withHint("Check for null first (if x != null), or handle the absent case in Sprig."));
                return true;
            }
        }
        return false;
    }

    private static List<Type> jvmExceptions(Class<?>[] exceptionTypes) {
        List<Type> out = new ArrayList<>();
        for (Class<?> exception : exceptionTypes) {
            out.add(JavaTypes.map(exception));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Exceptions, narrowing and small helpers
    // ------------------------------------------------------------------

    private void requireHandled(List<Type> thrown, Span span) {
        if (thrown == null || thrown.isEmpty()) {
            return;
        }
        if (!effectCollectors.isEmpty() && lambdaDepth == 0) {
            for (Type type : thrown) {
                if (type == null || type == NativeType.ERROR) continue;
                boolean caught = false;
                for (List<Type> frame : caughtStack) {
                    for (Type caughtType : frame) {
                        if (caughtType != NativeType.ERROR && Semantics.isAssignable(caughtType, type)) {
                            caught = true;
                            break;
                        }
                    }
                    if (caught) break;
                }
                if (!caught) effectCollectors.peek().add(type);
            }
            return;
        }
        // Top-level statements have no caller to declare errors; an uncaught
        // error aborts the program at runtime.
        if (currentFunction == null && lambdaDepth == 0) {
            return;
        }
        for (Type type : thrown) {
            if (type == null || type == NativeType.ERROR) {
                continue;
            }
            boolean declared = lambdaDepth == 0 && currentFunction != null && currentFunction.throwsTypes.stream()
                    .anyMatch(declaredType -> Semantics.isAssignable(declaredType, type));
            if (declared) {
                continue;
            }
            boolean caught = false;
            for (List<Type> frame : lambdaDepth == 0 ? caughtStack : List.<List<Type>>of()) {
                for (Type caughtType : frame) {
                    if (caughtType != NativeType.ERROR && Semantics.isAssignable(caughtType, type)) {
                        caught = true;
                        break;
                    }
                }
                if (caught) {
                    break;
                }
            }
            if (!caught) {
                boolean sprigError = type instanceof JavaType javaType && javaType.clazz == SprigError.class;
                boolean unchecked = !sprigError && !Semantics.isJvmChecked(type) && type instanceof JavaType;
                if (unchecked) {
                    continue;
                }
                String name = type instanceof JavaType javaType && javaType.clazz == SprigError.class
                        ? "Error" : type.display();
                diagnostics.add(Diagnostic.error(Codes.FLOW_THROWS, Phase.FLOW,
                        "Call may throw " + name + "; declare 'throws " + name + "' or handle it with try/catch",
                        module.uri, span).withHint("Sprig keeps recoverable errors explicit; there is no implicit propagation."));
            }
        }
    }

    private Type narrowedType(Symbol symbol) {
        for (Map<Symbol, Type> frame : narrowing) {
            Type narrowed = frame.get(symbol);
            if (narrowed != null) {
                return narrowed;
            }
        }
        return symbol.type == null ? NativeType.ERROR : symbol.type;
    }

    private Map<Symbol, Type> narrowTrue(Expr cond) {
        Map<Symbol, Type> out = new HashMap<>();
        collectNarrowing(cond, true, out);
        return out;
    }

    private Map<Symbol, Type> narrowFalse(Expr cond) {
        Map<Symbol, Type> out = new HashMap<>();
        collectNarrowing(cond, false, out);
        return out;
    }

    private void collectNarrowing(Expr cond, boolean positive, Map<Symbol, Type> out) {
        if (cond instanceof Expr.Binary binary) {
            if (binary.op.equals("and") && positive) {
                collectNarrowing(binary.left, true, out);
                collectNarrowing(binary.right, true, out);
            } else if (binary.op.equals("or") && !positive) {
                collectNarrowing(binary.left, false, out);
                collectNarrowing(binary.right, false, out);
            } else if (binary.op.equals("==") || binary.op.equals("!=")) {
                Expr left = binary.left;
                Expr right = binary.right;
                Expr.Name name = null;
                if (left instanceof Expr.NullLit && right instanceof Expr.Name n) {
                    name = n;
                } else if (right instanceof Expr.NullLit && left instanceof Expr.Name n) {
                    name = n;
                }
                if (name != null && name.symbol != null) {
                    boolean nonNull = (binary.op.equals("!=")) == positive;
                    addNarrowing(name.symbol, nonNull, out);
                }
            }
        } else if (cond instanceof Expr.Unary unary && unary.op.equals("not")) {
            collectNarrowing(unary.operand, !positive, out);
        }
    }

    private void addNarrowing(Symbol symbol, boolean nonNull, Map<Symbol, Type> out) {
        if (symbol == null || symbol.mutable) {
            return;
        }
        // Immutable locals, parameters and immutable top-level bindings can narrow.
        if (symbol.kind != Symbol.Kind.LOCAL && symbol.kind != Symbol.Kind.PARAM
                && symbol.kind != Symbol.Kind.TOP_VAR) {
            return;
        }
        Type type = symbol.type;
        if (type == null || !type.isNullable()) {
            return;
        }
        if (nonNull) {
            out.put(symbol, type.nonNull());
        }
    }

    private void requireAssignable(Type target, Type actual, Span span, String code, String what) {
        if (Semantics.isAssignable(target, actual)) {
            return;
        }
        String expected = target == null ? "?" : target.display();
        String got = actual == null ? "?" : actual.display();
        if (actual == NativeType.NULL) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_NULL, Phase.TYPE,
                    "null is not assignable to " + expected + " (" + what + "); use " + expected + "?",
                    module.uri, span).withTypes(expected, got));
            return;
        }
        if (actual != null && actual.isNullable()) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_NULLABLE, Phase.TYPE,
                    "Nullable value is not assignable to " + expected + " (" + what
                            + "); check for null first",
                    module.uri, span).withTypes(expected, got));
            return;
        }
        if (target != null && actual != null && isNumeric(target.nonNull()) && isNumeric(actual.nonNull())) {
            diagnostics.add(Diagnostic.error(Codes.NUM_CONVERSION, Phase.TYPE,
                    "Cannot implicitly convert " + actual.display() + " to " + target.display()
                            + " in " + what + "; precision or range may change",
                    module.uri, span).withTypes(target.display(), actual.display())
                    .withHint("Use an explicit exact conversion, or a method named Lossy/Trunc when intended."));
            return;
        }
        diagnostics.add(Diagnostic.error(code, Phase.TYPE,
                "Type mismatch in " + what, module.uri, span).withTypes(expected, got));
    }

    // ------------------------------------------------------------------
    // Definite return / exit analysis
    // ------------------------------------------------------------------

    private boolean definitelyReturns(List<Stmt> body) {
        for (Stmt stmt : body) {
            if (definitelyReturns(stmt)) {
                return true;
            }
        }
        return false;
    }

    private boolean definitelyReturns(Stmt stmt) {
        if (stmt instanceof Stmt.Return || stmt instanceof Stmt.Throw) {
            return true;
        }
        if (stmt instanceof Stmt.IfStmt ifStmt) {
            if (ifStmt.elseBody == null) {
                return false;
            }
            if (!definitelyReturns(ifStmt.thenBody) || !definitelyReturns(ifStmt.elseBody)) {
                return false;
            }
            for (Stmt.IfStmt.Elif elif : ifStmt.elifs) {
                if (!definitelyReturns(elif.body)) {
                    return false;
                }
            }
            return true;
        }
        if (stmt instanceof Stmt.Match match) {
            if (match.branches.isEmpty()) {
                return false;
            }
            for (Stmt.Match.Branch branch : match.branches) {
                if (!definitelyReturns(branch.body)) {
                    return false;
                }
            }
            return true;
        }
        if (stmt instanceof Stmt.Try tryStmt) {
            if (tryStmt.catches.isEmpty()) {
                return false;
            }
            if (!definitelyReturns(tryStmt.body)) {
                return false;
            }
            for (Stmt.Try.CatchClause clause : tryStmt.catches) {
                if (!definitelyReturns(clause.body)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private boolean definitelyExitsSequence(List<Stmt> body) {
        for (Stmt stmt : body) {
            if (definitelyExits(stmt)) {
                return true;
            }
        }
        return false;
    }

    private boolean definitelyExits(Stmt stmt) {
        if (stmt instanceof Stmt.Break || stmt instanceof Stmt.Continue) {
            return true;
        }
        if (stmt instanceof Stmt.IfStmt ifStmt) {
            if (ifStmt.elseBody == null) {
                return false;
            }
            if (!definitelyExitsSequence(ifStmt.thenBody) || !definitelyExitsSequence(ifStmt.elseBody)) {
                return false;
            }
            for (Stmt.IfStmt.Elif elif : ifStmt.elifs) {
                if (!definitelyExitsSequence(elif.body)) {
                    return false;
                }
            }
            return true;
        }
        if (stmt instanceof Stmt.Match match) {
            if (match.branches.isEmpty()) {
                return false;
            }
            for (Stmt.Match.Branch branch : match.branches) {
                if (!definitelyExitsSequence(branch.body)) {
                    return false;
                }
            }
            return true;
        }
        if (stmt instanceof Stmt.Try tryStmt) {
            if (tryStmt.catches.isEmpty()) {
                return false;
            }
            if (!definitelyExitsSequence(tryStmt.body)) {
                return false;
            }
            for (Stmt.Try.CatchClause clause : tryStmt.catches) {
                if (!definitelyExitsSequence(clause.body)) {
                    return false;
                }
            }
            return true;
        }
        return definitelyReturns(stmt);
    }

    /** Exposed for the code generator: whether a match statement's branches all exit. */
    public static boolean allBranchesExit(Stmt.Match match) {
        for (Stmt.Match.Branch branch : match.branches) {
            boolean exits = false;
            for (Stmt stmt : branch.body) {
                if (stmt instanceof Stmt.Return || stmt instanceof Stmt.Throw
                        || stmt instanceof Stmt.Break || stmt instanceof Stmt.Continue) {
                    exits = true;
                    break;
                }
            }
            if (!exits) {
                return false;
            }
        }
        return !match.branches.isEmpty();
    }
}
