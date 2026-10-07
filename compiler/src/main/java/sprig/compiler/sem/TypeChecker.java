package sprig.compiler.sem;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Expr;
import sprig.compiler.ast.Module;
import sprig.compiler.ast.Stmt;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Newcomer;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;
import sprig.compiler.jvm.JvmMetadata;
import sprig.compiler.jvm.JvmNullability;
import sprig.compiler.types.ClassType;
import sprig.compiler.types.EnumType;
import sprig.compiler.types.FunctionType;
import sprig.compiler.types.JavaType;
import sprig.compiler.types.ListType;
import sprig.compiler.types.MapType;
import sprig.compiler.types.NativeType;
import sprig.compiler.types.NullableType;
import sprig.compiler.types.Substitution;
import sprig.compiler.types.Type;
import sprig.compiler.types.TypeParameterType;
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
            Map.entry("Int.divTrunc", new int[]{1, 1}), Map.entry("Int.compareTo", new int[]{1, 1}),
            Map.entry("Int32.compareTo", new int[]{1, 1}), Map.entry("Float.compareTo", new int[]{1, 1}),
            Map.entry("Float32.compareTo", new int[]{1, 1}), Map.entry("Decimal.compareTo", new int[]{1, 1}),
            Map.entry("BigInt.compareTo", new int[]{1, 1}), Map.entry("String.lastIndexOf", new int[]{1, 1}),
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
            Map.entry("String.endsWith", new int[]{1, 1}), Map.entry("String.compareTo", new int[]{1, 1}),
            Map.entry("String.toUpperCase", new int[]{0, 0}),
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

    /** Where the expression being checked reports; a {@link #probe} swaps in a scratch sink. */
    private Diagnostics diagnostics;
    /** The sink this checker was created with; checkers for global initializers report here. */
    private final Diagnostics reported;
    private TypeRefResolver typeResolver;

    private Module module;
    private Decl.Func currentFunction;
    private Decl.ClassDecl currentClass;
    private int loopDepth;
    private int lambdaDepth;
    private final Deque<Map<Symbol, Type>> narrowing = new ArrayDeque<>();
    private final Deque<List<Type>> caughtStack = new ArrayDeque<>();
    /** Per open try, innermost first: the types its block can throw that no inner catch handles. */
    private final Deque<List<Type>> tryThrown = new ArrayDeque<>();
    /** Types the current function's body lets escape; null outside a function body. */
    private List<Type> escaping;
    private final Deque<List<Type>> effectCollectors = new ArrayDeque<>();
    /** Thrown types met inside the lambda bodies being checked, innermost first; they become the lambda's throws clause. */
    private final Deque<List<Type>> lambdaEffects = new ArrayDeque<>();
    /** v0.8 lexical generic parameters of the declaration being checked. */
    private Map<String, Type> activeTypeParams = Map.of();

    public TypeChecker(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
        this.reported = diagnostics;
        this.typeResolver = new TypeRefResolver(diagnostics);
    }

    /**
     * Checks an expression without an expected type only to learn its type;
     * what it reports is dropped and returned. The expression is checked again
     * afterwards, for real, so its errors are reported once and the tree gets
     * its final types. Recorded effects are only ever repeated.
     *
     * <p>Inside a probe a generic call whose type arguments are inferred stops
     * once it knows them: its type is all a probe needs, and checking its
     * arguments against them is left to the real check. So a probe costs one
     * pass over the expression however deeply generic calls nest in it, and
     * checking stays polynomial instead of doubling with every level.
     */
    private Diagnostics probe(Expr expr) {
        Diagnostics outer = diagnostics;
        TypeRefResolver outerResolver = typeResolver;
        Diagnostics scratch = new Diagnostics();
        diagnostics = scratch;
        typeResolver = new TypeRefResolver(scratch);
        probing++;
        try {
            checkExpr(expr, null);
        } finally {
            probing--;
            diagnostics = outer;
            typeResolver = outerResolver;
        }
        return scratch;
    }

    /** How many probes are open; see {@link #probe}. */
    private int probing;

    // Module signatures include inferred binding types before any body can use
    // them. Initializers are validated again in normal source order after default
    // effects are known; the preparation pass must never silently publish ERROR.
    private Map<Symbol, Stmt.VarDecl> globalInitializers = Map.of();
    private Set<Symbol> inferringGlobals = new HashSet<>();

    private void inferGlobal(Symbol symbol) {
        if (symbol.type != null) return;
        Stmt.VarDecl declaration = globalInitializers.get(symbol);
        if (declaration == null) return;
        // Reported to this checker's own sink even from inside a probe: the
        // global's type is established once, and a probe would drop its errors.
        if (!inferringGlobals.add(symbol)) {
            reported.add(Diagnostic.error(Codes.TYPE_INFER, Phase.TYPE,
                    "Cyclic initializer cannot establish the type of '" + symbol.name
                            + "'; write an explicit type annotation", module.uri, symbol.span));
            symbol.type = NativeType.ERROR;
            return;
        }
        // An initializer has its own module context, not the narrowing, lambda
        // depth or expected result of the expression that triggered inference.
        TypeChecker initializer = new TypeChecker(reported);
        initializer.module = module;
        initializer.globalInitializers = globalInitializers;
        initializer.inferringGlobals = inferringGlobals;
        initializer.checkVarDecl(declaration);
        inferringGlobals.remove(symbol);
    }

    public void check(Module module) {
        this.module = module;
        // Conformance edges must exist before any expression is checked,
        // including the unannotated-global inference prepass below.
        new ConformanceChecker(diagnostics).check(module);
        TypeChecker inference = new TypeChecker(diagnostics);
        inference.module = module;
        inference.globalInitializers = new LinkedHashMap<>();
        for (Stmt statement : module.topStatements) {
            if (statement instanceof Stmt.VarDecl declaration && declaration.symbol != null
                    && declaration.typeRef == null) {
                inference.globalInitializers.put(declaration.symbol, declaration);
            }
        }
        // An initializer that fails gives its binding the error type, which is
        // silent afterwards, so the rest of the module is still checked and
        // reports its own errors in the same round. The statement-order check of
        // the same initializer repeats its reports, which the sink drops.
        for (Symbol symbol : inference.globalInitializers.keySet()) inference.inferGlobal(symbol);
        prepareDefaultEffects();
        for (Decl decl : module.decls) {
            if (decl instanceof Decl.ClassDecl classDecl) {
                for (Decl.Field field : classDecl.fields) {
                    if (field.defaultExpr != null) {
                        checkDefault(classDecl, field);
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
        activeTypeParams = Map.of();
        loopDepth = 0;
        narrowing.clear();
        caughtStack.clear();
        tryThrown.clear();
        effectCollectors.clear();
        // The base frame a function body also gets: early-exit narrowing
        // (if x == null: throw ...) records into it for the statements after.
        narrowing.push(new HashMap<>());
        checkSequence(module.topStatements);
        narrowing.pop();
    }

    private Decl.Field collectingDefault;
    private Map<Decl.Field, Set<Decl.Field>> defaultDependencies;

    private void checkDefault(Decl.ClassDecl owner, Decl.Field field) {
        currentFunction = null;
        currentClass = owner;
        activeTypeParams = owner.typeParamTypes;
        narrowing.clear();
        caughtStack.clear();
        tryThrown.clear();
        List<Type> effects = new ArrayList<>();
        effectCollectors.push(effects);
        collectingDefault = field;
        Type type = checkExpr(field.defaultExpr, field.type);
        collectingDefault = null;
        effectCollectors.pop();
        field.defaultThrowsTypes.clear();
        field.defaultThrowsTypes.addAll(new LinkedHashSet<>(effects));
        requireAssignable(field.type, type, field.defaultExpr.span, Codes.TYPE_MISMATCH,
                "field default");
    }

    /** Resolve the finite default-effect graph before checking any caller body.
     * A later class's omitted defaults must be visible to an earlier caller.
     * Discovery diagnostics are discarded; normal checking below validates each
     * expression with the completed effects, including lambda restrictions.
     */
    private void prepareDefaultEffects() {
        TypeChecker discovery = new TypeChecker(new Diagnostics());
        discovery.module = module;
        discovery.defaultDependencies = new LinkedHashMap<>();
        for (Decl declaration : module.decls) {
            if (declaration instanceof Decl.ClassDecl owner) {
                for (Decl.Field field : owner.fields) {
                    if (field.defaultExpr != null) {
                        discovery.defaultDependencies.put(field, new LinkedHashSet<>());
                        discovery.checkDefault(owner, field);
                    }
                }
            }
        }
        Map<Decl.Field, Set<Decl.Field>> callers = new LinkedHashMap<>();
        for (var entry : discovery.defaultDependencies.entrySet()) {
            for (Decl.Field dependency : entry.getValue()) {
                callers.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(entry.getKey());
            }
        }
        Deque<Decl.Field> work = new ArrayDeque<>(callers.keySet());
        while (!work.isEmpty()) {
            Decl.Field field = work.removeFirst();
            for (Decl.Field caller : callers.getOrDefault(field, Set.of())) {
                boolean changed = false;
                for (Type effect : field.defaultThrowsTypes) {
                    if (!caller.defaultThrowsTypes.contains(effect)) {
                        caller.defaultThrowsTypes.add(effect);
                        changed = true;
                    }
                }
                if (changed) work.addLast(caller);
            }
        }
    }

    // ------------------------------------------------------------------
    // Functions and statements
    // ------------------------------------------------------------------

    private final Set<Stmt.Requires> leadingRequirements = new HashSet<>();

    private void checkFunction(Decl.Func func, Decl.ClassDecl owner) {
        for (int i = 0; i < func.throwsTypes.size() && i < func.throwsRefs.size(); i++) {
            Type declared = func.throwsTypes.get(i);
            if (declared != null && declared != NativeType.ERROR && !Semantics.isErrorType(declared)) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                        "throws requires an error type (Error, a class that conforms to Error, or an imported Throwable)",
                        module.uri, func.throwsRefs.get(i).span)
                        .withTypes("Error, an error class or an imported Throwable", declared.display())
                        .withHint("Declare an error class with its fields and 'conform " + declared.display()
                                + " to Error(message)', or throw Error(\"text\")."));
            }
        }
        leadingRequirements.clear();
        func.equatableParams.clear();
        func.comparableParams.clear();
        for (Stmt stmt : func.body) {
            if (!(stmt instanceof Stmt.Requires requires)) break;
            leadingRequirements.add(requires);
        }
        Map<String, Type> previousTypeParams = activeTypeParams;
        currentFunction = func;
        currentClass = owner;
        activeTypeParams = func.typeParamTypes;
        loopDepth = 0;
        narrowing.clear();
        caughtStack.clear();
        tryThrown.clear();
        escaping = new ArrayList<>();
        narrowing.push(new HashMap<>());
        checkSequence(func.body);
        narrowing.pop();
        if (!func.abstractMethod) {
            checkDeclaredThrows(func, escaping);
        }
        escaping = null;
        activeTypeParams = previousTypeParams;
        Type returnType = func.returnType;
        if (returnType != NativeType.UNIT && returnType != NativeType.ERROR && !func.abstractMethod
                && !definitelyReturns(func.body)) {
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
                        .withTypes("Error, an error class or imported Throwable", type.display())
                        .withHint("Throw Error(\"text\"), or make the class an error class with "
                                + "'conform " + type.display() + " to Error(message)' (its message field is the text)."));
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
        } else if (stmt instanceof Stmt.Requires requires) {
            checkRequires(requires);
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
        Type javaWriteType = null; // a Java field whose write type differs from its read type
        Class<?> javaFieldClass = null;
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
                if (field.symbol == null) {
                    // An unresolved member (errorField): the lookup already reported it.
                    return;
                }
                if (!field.symbol.mutable) {
                    diagnostics.add(Diagnostic.error(Codes.NAME_LET_ASSIGN, Phase.TYPE,
                            "Cannot assign to immutable top-level binding '" + access.name + "'",
                            module.uri, target.span));
                }
            } else if (field.kind == ResolvedField.Kind.JAVA_FIELD) {
                javaFieldClass = field.jvm.field.getType();
                if (java.lang.reflect.Modifier.isFinal(field.jvm.field.getModifiers())) {
                    diagnostics.add(Diagnostic.error(Codes.NAME_LET_ASSIGN, Phase.TYPE,
                            "Cannot assign to final Java field '" + access.name + "'",
                            module.uri, target.span));
                } else if (JavaTypes.needsValueAdapter(field.jvm.field.getType())) {
                    diagnostics.add(Diagnostic.error(Codes.JVM_MEMBER, Phase.JVM,
                            "Cannot assign to Java field '" + access.name + "' of type "
                                    + field.jvm.field.getType().getTypeName()
                                    + "; this boundary requires an explicit Java setter or adapter",
                            module.uri, target.span));
                } else if (field.jvm.bindings != null
                        && JavaTypes.capturedWrite(field.jvm.field.getGenericType(), field.jvm.bindings)) {
                    diagnostics.add(Diagnostic.error(Codes.JVM_MEMBER, Phase.JVM,
                            "Cannot assign to Java field '" + access.name
                                    + "' through a '? extends' wildcard of the receiver; no type can be written",
                            module.uri, target.span));
                    return;
                } else if (field.jvm.bindings != null
                        && field.jvm.field.getGenericType() instanceof TypeVariable<?> variable
                        && field.jvm.bindings.get(variable) instanceof sprig.compiler.types.JavaWildcardType wildcard
                        && wildcard.lower != null) {
                    // T on a Holder<? super Long> receiver reads as Object but takes only an Int.
                    javaWriteType = NullableType.of(wildcard.lower);
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
            targetType = javaWriteType != null ? javaWriteType : field.type;
            access.type = field.type;
        } else if (target instanceof Expr.Index index) {
            Type receiver = checkExpr(index.receiver, null);
            if (receiver.isNullable()) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_NULLABLE, Phase.TYPE,
                        "Cannot index a value that may be null", module.uri, target.span));
                receiver = receiver.nonNull();
            }
            if (receiver instanceof ListType list) {
                Type idx = checkExpr(index.index, NativeType.INT);
                requireAssignable(NativeType.INT, idx, index.index.span, Codes.TYPE_MISMATCH, "list index");
                if (!list.mutable) {
                    diagnostics.add(Diagnostic.error(Codes.COLLECTION_IMMUTABLE, Phase.TYPE,
                            "Cannot mutate List[" + list.element.display() + "]; convert it with toMutableList()",
                            module.uri, target.span));
                }
                targetType = list.element;
            } else if (receiver instanceof MapType map) {
                Type idx = checkExpr(index.index, map.key);
                requireAssignable(map.key, idx, index.index.span, Codes.TYPE_MISMATCH, "map key");
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
            if ((javaFieldClass == int.class || javaFieldClass == Integer.class)
                    && valueType == NativeType.INT) {
                return; // an Int written to an int/Integer Java field narrows with a run-time check
            }
            requireAssignable(targetType, valueType, assign.value.span, Codes.TYPE_ASSIGN, "assignment");
        } else {
            String op = assign.op.substring(0, 1);
            // The left-hand side supplies the context, exactly like plain `=`
            // and arithmetic: `Int32 += 1` follows the same literal rule as
            // `x = x + 1`. Variables still never narrow implicitly. A String's
            // `+=` joins its operand instead, so an if or match expression there
            // is a join operand, typed on its own as `s += 2` is.
            boolean joined = op.equals("+") && targetType == NativeType.STRING
                    && (assign.value instanceof Expr.If || assign.value instanceof Expr.Match);
            Type valueType = checkExpr(assign.value, joined ? null : targetType);
            Type result = checkArithmetic(op, targetType, valueType, assign.span, true, target, assign.value);
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
            if (actual == NativeType.UNIT) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_UNIT, Phase.TYPE,
                        "Cannot return a Unit result; Unit is not a value", module.uri, ret.value.span)
                        .withHint("Call the function as a statement of its own, then write a bare return."));
            } else if (actual != NativeType.ERROR) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_RETURN, Phase.TYPE,
                        "Unit function cannot return a value", module.uri, ret.span)
                        .withTypes("Unit", actual.display()));
            }
            return;
        }
        requireAssignable(expected, actual, ret.value.span, Codes.TYPE_RETURN, "return");
    }

    /**
     * An if chain narrows by every condition that is known to be false on
     * the way to a branch: each {@code elif}, its condition included, and the
     * {@code else} see every earlier condition false, in addition to what
     * their own condition establishes. Only immutable names narrow, so a
     * condition's result does not change between the branches.
     */
    private void checkIf(Stmt.IfStmt ifStmt) {
        checkCondition(ifStmt.cond);
        narrowing.push(narrowTrue(ifStmt.cond));
        checkSequence(ifStmt.thenBody);
        narrowing.pop();
        Map<Symbol, Type> earlierFalse = narrowFalse(ifStmt.cond);
        for (Stmt.IfStmt.Elif elif : ifStmt.elifs) {
            narrowing.push(earlierFalse);
            checkCondition(elif.cond);
            narrowing.pop();
            Map<Symbol, Type> branch = new HashMap<>(earlierFalse);
            branch.putAll(narrowTrue(elif.cond));
            narrowing.push(branch);
            checkSequence(elif.body);
            narrowing.pop();
            earlierFalse = new HashMap<>(earlierFalse);
            earlierFalse.putAll(narrowFalse(elif.cond));
        }
        if (ifStmt.elseBody != null) {
            narrowing.push(earlierFalse);
            checkSequence(ifStmt.elseBody);
            narrowing.pop();
        }
        // Early-exit narrowing: if (x == null) { ...exits... } -> x is non-null
        // after the statement. With a chain, the code after it was reached
        // through the first branch whose body can complete, or through no
        // branch at all, so every condition before that branch was false.
        if (!narrowing.isEmpty()) {
            Map<Symbol, Type> after = narrowing.peek();
            if (definitelyExitsSequence(ifStmt.thenBody)) {
                after.putAll(narrowFalse(ifStmt.cond));
                for (Stmt.IfStmt.Elif elif : ifStmt.elifs) {
                    if (!definitelyExitsSequence(elif.body)) {
                        break;
                    }
                    after.putAll(narrowFalse(elif.cond));
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
        if (kind == ForKind.LIST && forStmt.iterable instanceof Expr.Call call && call.resolved != null
                && call.resolved.kind == ResolvedCall.Kind.BUILTIN && "range".equals(call.resolved.builtinId)) {
            kind = ForKind.RANGE;
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
                        .withTypes("Error, an error class or imported Throwable", type.display()));
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
        tryThrown.push(new ArrayList<>());
        narrowing.push(new HashMap<>());
        checkSequence(tryStmt.body);
        narrowing.pop();
        caughtStack.pop();
        List<Type> thrown = tryThrown.pop();
        for (Stmt.Try.CatchClause clause : tryStmt.catches) {
            Type type = clause.caughtType;
            if (isTrackedChecked(type) && !related(type, thrown)) {
                diagnostics.add(Diagnostic.error(Codes.FLOW_CATCH_NEVER_THROWN, Phase.FLOW,
                        "Catch of " + type.display() + " can never run: nothing in the try block can throw it",
                        module.uri, clause.typeRef.span)
                        .withHint("Remove this catch clause; catch Error if the block calls Sprig functions that fail with Error."));
            }
        }
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

    /** Only leading function clauses may grant equality capability. */
    private void checkRequires(Stmt.Requires requires) {
        if (currentFunction != null && !leadingRequirements.contains(requires)) {
            diagnostics.add(Diagnostic.error(Codes.GENERIC_CONSTRAINT, Phase.TYPE,
                    "requires must appear before all other statements in the function body",
                    module.uri, requires.span));
            return;
        }
        boolean parameterVisible = activeTypeParams.containsKey(requires.parameter);
        if (!parameterVisible) {
            diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                    "requires names unknown type parameter '" + requires.parameter + "'",
                    module.uri, requires.span));
        }
        if (requires.capability.equals("Equatable")) {
            if (parameterVisible && currentFunction != null) {
                currentFunction.equatableParams.add(requires.parameter);
            }
            return;
        }
        if (requires.capability.equals("Comparable")) {
            if (parameterVisible && currentFunction != null) {
                currentFunction.comparableParams.add(requires.parameter);
            }
            return;
        }
        Symbol named = capabilityNamesType(requires.capability);
        if (named != null) {
            // Contracts and classes are types, never bounds: a type parameter is
            // constrained only by the closed capabilities.
            String kind = named.decl instanceof Decl.ClassDecl classDecl ? (classDecl.contract ? "contract" : "class")
                    : named.kind == Symbol.Kind.VARIANT ? "variant" : named.kind == Symbol.Kind.ENUM ? "enum" : "type";
            String lower = Character.toLowerCase(requires.capability.charAt(requires.capability.lastIndexOf('.') + 1))
                    + requires.capability.substring(requires.capability.lastIndexOf('.') + 2);
            diagnostics.add(Diagnostic.error(Codes.GENERIC_CONSTRAINT, Phase.TYPE,
                    "'" + requires.capability + "' is a " + kind + ", not a capability; a " + kind
                            + " is a type, never a bound on a type parameter",
                    module.uri, requires.span)
                    .withHint("Take '" + requires.capability + "' as the parameter type instead of a type parameter bounded by it: "
                            + "replace '" + requires.parameter + "' with '" + requires.capability + "' in the signature ("
                            + lower + ": " + requires.capability + ") and drop the requires clause. Capabilities are the closed set "
                            + "Equatable and Comparable."));
            return;
        }
        diagnostics.add(Diagnostic.error(Codes.GENERIC_CONSTRAINT, Phase.TYPE,
                "Unknown capability '" + requires.capability
                        + "'; v0.8 defines Comparable and Equatable",
                module.uri, requires.span));
    }

    /** The user type a requires clause names in place of a capability, or null. */
    private Symbol capabilityNamesType(String capability) {
        int dot = capability.indexOf('.');
        if (dot < 0) {
            Symbol symbol = module.scope.types.get(capability);
            return symbol != null && symbol.decl != null ? symbol : null;
        }
        Symbol alias = module.scope.importAliases.get(capability.substring(0, dot));
        if (alias == null || alias.kind != Symbol.Kind.MODULE || alias.module == null) {
            return null;
        }
        Symbol symbol = alias.module.scope.types.get(capability.substring(dot + 1));
        return symbol != null && symbol.decl != null ? symbol : null;
    }

    /**
     * Comparable is closed: the types that already have ordering operators, or
     * a type parameter whose function declares {@code requires X: Comparable}.
     */
    private boolean isComparableType(Type type) {
        if (type instanceof TypeParameterType parameter) {
            return currentFunction != null
                    && currentFunction.comparableParams.contains(parameter.name)
                    && activeTypeParams.containsKey(parameter.name);
        }
        return type == NativeType.INT || type == NativeType.INT32 || type == NativeType.FLOAT
                || type == NativeType.FLOAT32 || type == NativeType.DECIMAL || type == NativeType.BIGINT
                || type == NativeType.STRING;
    }

    /** Each {@code requires X: Comparable} of the callee must hold for its type argument at this use. */
    private void checkComparableArguments(Decl.Func func, Map<TypeParameterType, Type> map, Span span) {
        for (Stmt stmt : func.body) {
            if (!(stmt instanceof Stmt.Requires requires)) break;
            if (!requires.capability.equals("Comparable")) continue;
            Type argument = null;
            for (Map.Entry<TypeParameterType, Type> entry : map.entrySet()) {
                if (entry.getKey().name.equals(requires.parameter)) argument = entry.getValue();
            }
            // No substitution: a call from inside the generic declaration itself.
            if (argument == null) argument = activeTypeParams.get(requires.parameter);
            if (argument == null || argument == NativeType.ERROR || isComparableType(argument)) continue;
            if (argument instanceof TypeParameterType parameter) {
                diagnostics.add(Diagnostic.error(Codes.GENERIC_CONSTRAINT, Phase.TYPE,
                        "Type parameter '" + parameter.name + "' is not Comparable here, but '" + func.name
                                + "' requires a Comparable type argument",
                        module.uri, span)
                        .withTypes("Comparable type", argument.display())
                        .withHint("Begin this function with 'requires " + parameter.name + ": Comparable'."));
                continue;
            }
            diagnostics.add(Diagnostic.error(Codes.GENERIC_CONSTRAINT, Phase.TYPE,
                    "Type argument '" + argument.display() + "' for " + requires.parameter
                            + " is not Comparable, which '" + func.name + "' requires",
                    module.uri, span)
                    .withTypes("Comparable type", argument.display())
                    .withHint("Comparable types are Int, Int32, Float, Float32, Decimal, BigInt and String;"
                            + " for other types, pass an explicit comparison function."));
        }
    }

    /** Whether equality on this parameter is justified by a requires clause. */
    private boolean isEquatableParameter(Type type) {
        if (!(type instanceof TypeParameterType parameter)) {
            return false;
        }
        return currentFunction != null
                && currentFunction.equatableParams.contains(parameter.name)
                && activeTypeParams.containsKey(parameter.name);
    }

    private void checkMatch(Stmt.Match match) {
        checkMatch(match, branch -> checkSequence(branch.body));
    }

    private void checkMatch(Stmt.Match match, java.util.function.Consumer<Stmt.Match.Branch> checkBody) {
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
            Diagnostic error = Diagnostic.error(Codes.MATCH_SCRUTINEE, Phase.TYPE,
                    "match requires an enum or variant value", module.uri, match.scrutinee.span)
                    .withTypes("enum or variant", scrutinee.display());
            if (scrutinee instanceof ClassType classType) {
                error.withHint("There are no type tests on " + (classType.decl.contract ? "contract" : "class")
                        + " values. A closed set of types is a variant: declare one with a case per type and match on it; "
                        + "an open set is a contract: call its methods instead of testing the type.");
            }
            diagnostics.add(error);
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
            if (caseOwner != null && caseOwner != NativeType.ERROR
                    && (valid ? !sameMatchOwner(caseOwner, scrutinee) : true)) {
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
                    branch.binderType = new VariantCaseType(variantType.decl, variantType.args, variantCase);
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
            checkBody.accept(branch);
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

    /**
     * Match branches name the variant/enum declaration, while the scrutinee
     * may be an instantiation such as {@code Option[Int]}; v0.8 compares the
     * declaration identity so exhaustiveness survives generic instantiation.
     */
    private static boolean sameMatchOwner(Type caseOwner, Type scrutinee) {
        if (scrutinee instanceof VariantCaseType caseType) {
            if (caseOwner instanceof VariantType variantType) {
                return variantType.decl == caseType.variant;
            }
            return caseOwner instanceof VariantCaseType ownerCase
                    && ownerCase.variant == caseType.variant;
        }
        if (scrutinee instanceof VariantType variantType) {
            return caseOwner instanceof VariantType owner && owner.decl == variantType.decl;
        }
        if (scrutinee instanceof EnumType enumType) {
            return caseOwner instanceof EnumType owner && owner.decl == enumType.decl;
        }
        return false;
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
        Type reference = functionReference(expr, expected);
        if (reference != null) {
            expr.type = reference;
            return reference;
        }
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
        } else if (expr instanceof Expr.Subscript subscript) {
            type = checkSubscript(subscript);
        } else if (expr instanceof Expr.Unary unary) {
            type = checkUnary(unary, expected);
        } else if (expr instanceof Expr.Binary binary) {
            type = checkBinary(binary);
        } else if (expr instanceof Expr.ListLit listLit) {
            type = checkListLit(listLit, expected);
        } else if (expr instanceof Expr.MapLit mapLit) {
            type = checkMapLit(mapLit, expected);
        } else if (expr instanceof Expr.Match match) {
            type = checkMatchExpression(match, expected);
        } else if (expr instanceof Expr.If ifExpr) {
            type = checkIfExpression(ifExpr, expected);
        } else if (expr instanceof Expr.Lambda lambda) {
            type = checkLambda(lambda, expected);
        } else {
            type = NativeType.ERROR;
        }
        expr.type = type;
        return type;
    }

    /**
     * Result typing shared by expression match and if expressions. An expected
     * type (annotation, parameter, return type) checks every branch. Without
     * one, the first non-null branch establishes the type, later non-null
     * branches must be assignable to it, and a null branch makes it nullable.
     * There is no common-supertype inference and no new numeric promotion.
     */
    private final class BranchResults {
        private final Type expected;
        private final String code;
        private final String role;
        /** The hint of a plain mismatch between the established type and a branch, or null. */
        private final java.util.function.BiFunction<Type, Type, String> mismatchHint;
        /** Whether a Unit branch is set aside as having no value, instead of establishing Unit. */
        private final boolean unitHasNoValue;
        private Type candidate;
        private boolean sawNull;
        private boolean sawUnit;
        private boolean sawError;

        BranchResults(Type expected, String code, String role,
                      java.util.function.BiFunction<Type, Type, String> mismatchHint, boolean unitHasNoValue) {
            this.expected = expected;
            this.candidate = expected;
            this.code = code;
            this.role = role;
            this.mismatchHint = mismatchHint;
            this.unitHasNoValue = unitHasNoValue;
        }

        void check(Expr value) {
            Type actual = checkExpr(value, candidate);
            if (actual == NativeType.ERROR) sawError = true;
            if (actual == NativeType.UNIT && unitHasNoValue) {
                sawUnit = true;
                return;
            }
            if (actual == NativeType.NULL) sawNull = true;
            if (candidate == null && actual != NativeType.NULL && actual != NativeType.ERROR) candidate = actual;
            if (candidate != null && actual != NativeType.NULL) {
                requireAssignable(candidate, actual, value.span, code, role, mismatchHint.apply(candidate, actual));
            } else if (expected != null) {
                requireAssignable(expected, actual, value.span, code, role, mismatchHint.apply(expected, actual));
            }
        }

        /** The type the branches established, or null when none did. */
        Type established() {
            return candidate;
        }

        Type result() {
            return expected == null && sawNull ? NullableType.of(candidate) : candidate;
        }
    }

    private Type checkMatchExpression(Expr.Match expr, Type expected) {
        BranchResults results = new BranchResults(expected, Codes.MATCH_RESULT, "match branch result",
                (target, actual) -> null, false);
        checkMatch(expr.cases, branch -> results.check(((Stmt.ExprStmt) branch.body.get(0)).expr));
        Type result = results.established();
        if (result == null) {
            diagnostics.add(Diagnostic.error(Codes.MATCH_INFERENCE,Phase.TYPE,
                "Cannot infer match result; write an explicit result type annotation",module.uri,expr.span));
            return NativeType.ERROR;
        }
        if (result == NativeType.UNIT) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_UNIT,Phase.TYPE,
                "Expression match must produce a value; use statement match for side effects",module.uri,expr.span));
            return NativeType.ERROR;
        }
        return results.result();
    }

    /**
     * An if expression: every condition is Bool and the narrowing is the if
     * statement's. A branch sees its own condition true and every earlier
     * condition false, the else sees all conditions false. The result typing is
     * the expression-match typing above.
     */
    private Type checkIfExpression(Expr.If expr, Type context) {
        // Unit is no value type, so it gives the branches no context; the return of
        // a Unit function then reports the value itself.
        Type expected = context == NativeType.UNIT || context == NativeType.ERROR ? null : context;
        BranchResults results = new BranchResults(expected, Codes.TYPE_MISMATCH, "if branch result",
                (target, actual) -> ifBranchHint(expected, target, actual), true);
        Map<Symbol, Type> earlierFalse = new HashMap<>();
        for (int i = 0; i < expr.conditions.size(); i++) {
            Expr condition = expr.conditions.get(i);
            narrowing.push(earlierFalse);
            try {
                checkCondition(condition);
            } finally {
                narrowing.pop();
            }
            Map<Symbol, Type> branch = new HashMap<>(earlierFalse);
            branch.putAll(narrowTrue(condition));
            narrowing.push(branch);
            try {
                results.check(expr.values.get(i));
            } finally {
                narrowing.pop();
            }
            earlierFalse = new HashMap<>(earlierFalse);
            earlierFalse.putAll(narrowFalse(condition));
        }
        // A missing else was reported by the front end; the branches that exist
        // still give the expression its type, so the code around it checks as usual.
        if (expr.elseValue != null) {
            narrowing.push(earlierFalse);
            try {
                results.check(expr.elseValue);
            } finally {
                narrowing.pop();
            }
        }
        // A branch without a value (Unit) is the one error to report; a branch that
        // failed on its own already has its error, and leaves nothing to infer from.
        if (results.sawUnit) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_UNIT, Phase.TYPE,
                    "An if expression must produce a value in every branch; use an if statement for side effects",
                    module.uri, expr.span)
                    .withHint("Write an if statement instead, with each call on its own line in its branches; "
                            + "in a lambda, call a named function that holds that if statement."));
            return NativeType.ERROR;
        }
        Type result = results.established();
        if (result == null) {
            if (!results.sawError) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_INFER, Phase.TYPE,
                        "Cannot infer the type of this if expression: no branch has a value other than null",
                        module.uri, expr.span)
                        .withHint("Write the type on the target, for example 'let name: String? = if ...'."));
            }
            return NativeType.ERROR;
        }
        return results.result();
    }

    /** The hint for an if-expression branch whose type does not fit the result type target. */
    private String ifBranchHint(Type expected, Type target, Type actual) {
        if (expected != null) {
            return "Every branch of an if expression must produce " + expected.display()
                    + ", the type expected here; convert this branch.";
        }
        // Two cases of one variant: the first branch made the result that one case.
        // The variant is spelled as this module writes it (through its import alias
        // when it comes from another module), since the hint shows code to write.
        if (target.nonNull() instanceof VariantCaseType first && actual.nonNull() instanceof VariantCaseType other
                && first.variant == other.variant) {
            return "The first branch makes the result type " + first.display() + "; write the variant on "
                    + "the target so every case fits, for example 'let value: " + TypeSpelling.in(module, first)
                    + " = if ...'.";
        }
        return "Every branch of an if expression has the type of its first branch that is not null. "
                + "Convert this branch, or write the type on the target, for example 'let ratio: Float = if ...'.";
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
            case PARENT_VIEW -> {
                diagnostics.add(Diagnostic.error(Codes.CONFORM_PARENT, Phase.TYPE,
                        "'" + name.name + "' names the inherited implementation and is not a value; call a method on it: "
                                + name.name + ".method(...)",
                        module.uri, name.span)
                        .withHint("The parent view only calls inherited methods of the Java class; there is no self value."));
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
        } else if (field.kind == ResolvedField.Kind.JVM_METHOD || field.kind == ResolvedField.Kind.BUILTIN_METHOD) {
            String owner = field.kind == ResolvedField.Kind.JVM_METHOD ? "Java method" : "Built-in method";
            diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                    owner + " '" + access.name + "' is not a value; call it, or pass a lambda",
                    module.uri, access.span)
                    .withHint("Only Sprig functions and methods are referenced by name. Write the lambda: "
                            + "fn(value: Type) => receiver." + access.name + "(value)."));
        } else if (field.kind == ResolvedField.Kind.METHOD) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                    "Method '" + access.name + "' is not a first-class value; call it",
                    module.uri, access.span));
        }
        return field.type == null ? NativeType.ERROR : field.type;
    }

    private Type checkIndex(Expr.Index index) {
        return checkIndexOn(index.receiver, index.index, index.span);
    }

    private Type checkIndexOn(Expr receiverExpr, Expr indexExpr, Span span) {
        Type receiver = checkExpr(receiverExpr, null);
        if (receiver.isNullable()) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_NULLABLE, Phase.TYPE,
                    "Cannot index a value that may be null", module.uri, span));
            receiver = receiver.nonNull();
        }
        if (receiver instanceof ListType list) {
            Type idx = checkExpr(indexExpr, NativeType.INT);
            requireAssignable(NativeType.INT, idx, indexExpr.span, Codes.TYPE_MISMATCH, "list index");
            return list.element;
        }
        if (receiver instanceof MapType map) {
            Type idx = checkExpr(indexExpr, map.key);
            requireAssignable(map.key, idx, indexExpr.span, Codes.TYPE_MISMATCH, "map key");
            return NullableType.of(map.value);
        }
        if (receiver == NativeType.STRING) {
            Type idx = checkExpr(indexExpr, NativeType.INT);
            requireAssignable(NativeType.INT, idx, indexExpr.span, Codes.TYPE_MISMATCH, "string index");
            return NativeType.STRING;
        }
        if (receiver != NativeType.ERROR) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                    "Type " + receiver.display() + " does not support indexing", module.uri, span));
        }
        checkExpr(indexExpr, null);
        return NativeType.ERROR;
    }

    /**
     * v0.8 bracket form used as a plain expression. When the base is a value,
     * a single plain type-argument-looking name is reinterpreted as indexing
     * so {@code values[index]} keeps its v0.7 meaning; when the base is a
     * generic type, a bare application is not a value.
     */
    private Type checkSubscript(Expr.Subscript subscript) {
        if (subscript.index != null) {
            return checkIndexOn(subscript.base, subscript.index, subscript.span);
        }
        Type application = genericApplicationType(subscript, false);
        if (application != null) {
            diagnostics.add(Diagnostic.error(Codes.NAME_NOT_A_VALUE, Phase.NAME,
                    "Generic type application " + application.display()
                            + " is not a value; construct a case or instance instead",
                    module.uri, subscript.span));
            return NativeType.ERROR;
        }
        Expr index = subscript.resolvedIndex != null ? subscript.resolvedIndex
                : indexFromTypeArgs(subscript.typeArgs);
        if (index != null) {
            subscript.resolvedIndex = index;
            return checkIndexOn(subscript.base, index, subscript.span);
        }
        checkExpr(subscript.base, null);
        return NativeType.ERROR;
    }

    /** Converts a single plain type argument back into an index expression. */
    public static Expr indexFromTypeArgs(java.util.List<sprig.compiler.ast.TypeRef> typeArgs) {
        if (typeArgs == null || typeArgs.size() != 1) {
            return null;
        }
        sprig.compiler.ast.TypeRef ref = typeArgs.get(0);
        if (ref.functionResult != null || ref.nullable || !ref.args.isEmpty() || ref.parts.isEmpty()) {
            return null;
        }
        Expr current = new Expr.Name(ref.parts.get(0));
        current.span = ref.span;
        for (int i = 1; i < ref.parts.size(); i++) {
            Expr.FieldAccess access = new Expr.FieldAccess(current, ref.parts.get(i));
            access.span = ref.span;
            current = access;
        }
        return current;
    }

    /**
     * Resolves a {@code Base[Args]} form as a generic type application when
     * the base names a generic declaration. Returns {@code null} when the form
     * is ordinary indexing or unresolvable; diagnostics are reported only when
     * the base is clearly a generic type used incorrectly.
     */
    private Type genericApplicationType(Expr.Subscript subscript, boolean forMemberAccess) {
        Symbol symbol = subscript.base instanceof Expr.Name name ? name.symbol : null;
        Type baseType = null;
        if (symbol != null) {
            if (symbol.kind == Symbol.Kind.VARIANT || symbol.kind == Symbol.Kind.ENUM) {
                baseType = symbol.type;
            } else if (symbol.kind == Symbol.Kind.CLASS) {
                baseType = symbol.type;
            }
        } else if (subscript.base instanceof Expr.FieldAccess access) {
            ResolvedField resolved = resolveFieldAccess(access, false);
            if (resolved.kind == ResolvedField.Kind.MODULE_TYPE) {
                baseType = resolved.type;
            }
        }
        if (baseType instanceof VariantType variantType && !variantType.decl.typeParams.isEmpty()) {
            List<Type> args = resolveGenericArgs(variantType.decl, subscript);
            if (args == null) {
                return NativeType.ERROR;
            }
            VariantType applied = new VariantType(variantType.decl, args);
            subscript.applicationType = applied;
            return applied;
        }
        if (baseType instanceof ClassType classType && !classType.decl.typeParams.isEmpty()) {
            List<Type> args = resolveGenericArgs(classType.decl, subscript);
            if (args == null) {
                return NativeType.ERROR;
            }
            ClassType applied = new ClassType(classType.decl, args);
            subscript.applicationType = applied;
            return applied;
        }
        if (baseType != null && subscript.typeArgs != null && !subscript.typeArgs.isEmpty()) {
            Decl decl = baseType instanceof ClassType c ? c.decl
                    : baseType instanceof VariantType v ? v.decl
                    : baseType instanceof EnumType e ? e.decl : null;
            if (decl != null) {
                diagnostics.add(Diagnostic.error(Codes.GENERIC_ARITY, Phase.TYPE,
                        "Type '" + decl.name + "' is not generic and accepts no type arguments",
                        module.uri, subscript.span));
                return NativeType.ERROR;
            }
        }
        return null;
    }

    private List<Type> resolveGenericArgs(Decl decl, Expr.Subscript subscript) {
        return typeResolver.resolveArguments(module, decl, subscript.typeArgs, activeTypeParams,
                subscript.span, subscript.span);
    }

    /** The generic class or function a {@code Base[Args]} call site refers to. */
    private Type checkGenericCall(Expr.Subscript subscript, Expr.Call call, Type expected) {
        Symbol symbol = subscript.base instanceof Expr.Name name ? name.symbol : null;
        if (symbol != null && symbol.kind == Symbol.Kind.CLASS) {
            return checkGenericClassConstruction((ClassType) symbol.type, symbol, subscript, call);
        }
        if (symbol != null && (symbol.kind == Symbol.Kind.FUNCTION || symbol.kind == Symbol.Kind.METHOD)) {
            Decl.Func func = (Decl.Func) symbol.decl;
            if (func != null) {
                return checkGenericFunctionCall(func, symbol, subscript, call);
            }
        }
        if (symbol != null && symbol.kind == Symbol.Kind.JAVA_TYPE) {
            return checkJavaConstruction(symbol, subscript, call);
        }
        if (symbol != null && symbol.kind == Symbol.Kind.VARIANT) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                    "Construct generic variant values with Type[Arg].Case(...) or Type[Arg].Case",
                    module.uri, call.span));
            checkArgsUnchecked(call);
            return NativeType.ERROR;
        }
        if (subscript.base instanceof Expr.FieldAccess access) {
            ResolvedField field = resolveFieldAccess(access, true);
            if (field.kind == ResolvedField.Kind.MODULE_TYPE && field.type instanceof ClassType classType) {
                return checkGenericClassConstruction(classType, field.symbol, subscript, call);
            }
            if (field.kind == ResolvedField.Kind.MODULE_FUNCTION && field.methodDecl != null) {
                return checkGenericFunctionCall(field.methodDecl, field.symbol, subscript, call);
            }
            if (field.kind == ResolvedField.Kind.JVM_METHOD) {
                return checkExplicitJvmMethod(field, subscript, call);
            }
        }
        // Ordinary indexing followed by a call; v0.8 does not support
        // index-then-call for function values through this surface form.
        Expr index = subscript.resolvedIndex != null ? subscript.resolvedIndex
                : indexFromTypeArgs(subscript.typeArgs);
        if (index != null) {
            subscript.resolvedIndex = index;
            Type receiver = checkIndexOn(subscript.base, index, subscript.span);
            if (receiver instanceof FunctionType functionType) {
                ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.FUNCTION_VALUE,
                        functionType.result);
                resolved.functionType = functionType;
                checkFunctionValueCall(functionType, call);
                return functionType.result;
            }
            if (receiver != NativeType.ERROR) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                        "The indexed value is not callable", module.uri, call.span)
                        .withTypes("function value", receiver.display()));
            }
            checkArgsUnchecked(call);
            return NativeType.ERROR;
        }
        checkArgsUnchecked(call);
        return NativeType.ERROR;
    }

    private Type checkGenericClassConstruction(ClassType base, Symbol symbol, Expr.Subscript subscript,
                                               Expr.Call call) {
        Decl.ClassDecl decl = base.decl;
        if (decl.typeParams.isEmpty()) {
            if (subscript.typeArgs != null && !subscript.typeArgs.isEmpty()) {
                diagnostics.add(Diagnostic.error(Codes.GENERIC_ARITY, Phase.TYPE,
                        "Class '" + decl.name + "' is not generic and accepts no type arguments",
                        module.uri, subscript.span));
            }
            ClassType plain = new ClassType(decl);
            ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.CLASS_CTOR, plain);
            resolved.classDecl = decl;
            resolved.symbol = symbol;
            checkClassConstructor(plain, call);
            return plain;
        }
        List<Type> args = resolveGenericArgs(decl, subscript);
        if (args == null) {
            checkArgsUnchecked(call);
            return NativeType.ERROR;
        }
        ClassType classType = new ClassType(decl, args);
        subscript.applicationType = classType;
        ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.CLASS_CTOR, classType);
        resolved.classDecl = decl;
        resolved.symbol = symbol;
        resolved.typeArgs = args;
        resolved.substitution = Substitution.forClass(classType);
        resolved.instantiatedType = classType;
        checkClassConstructor(classType, call);
        return classType;
    }

    private Type checkGenericFunctionCall(Decl.Func func, Symbol symbol, Expr.Subscript subscript,
                                          Expr.Call call) {
        if (func.typeParams.isEmpty()) {
            if (subscript.typeArgs != null && !subscript.typeArgs.isEmpty()) {
                diagnostics.add(Diagnostic.error(Codes.GENERIC_ARITY, Phase.TYPE,
                        "Function '" + func.name + "' is not generic and accepts no type arguments",
                        module.uri, subscript.span));
            }
            ResolvedCall resolved = resolvedCall(call,
                    func.isMethod() ? ResolvedCall.Kind.METHOD : ResolvedCall.Kind.FUNCTION,
                    func.returnType);
            resolved.symbol = symbol;
            resolved.methodDecl = func;
            checkPositionalCall(func, call, func.name);
            return func.returnType;
        }
        List<Type> args = resolveGenericArgs(func, subscript);
        if (args == null) {
            checkArgsUnchecked(call);
            return NativeType.ERROR;
        }
        Map<TypeParameterType, Type> map = Substitution.forFunction(func, args);
        checkComparableArguments(func, map, subscript.span);
        Type returnType = Substitution.apply(func.returnType, map);
        ResolvedCall resolved = resolvedCall(call,
                func.isMethod() ? ResolvedCall.Kind.METHOD : ResolvedCall.Kind.FUNCTION, returnType);
        resolved.symbol = symbol;
        resolved.methodDecl = func;
        resolved.typeArgs = args;
        resolved.substitution = map;
        checkPositionalCallSubstituted(func, call, func.name, map);
        return returnType;
    }

    /** {@code ArrayList[String](...)}: explicit arguments on an imported Java class. */
    private Type checkJavaConstruction(Symbol symbol, Expr.Subscript subscript, Expr.Call call) {
        Class<?> clazz = symbol.javaClass;
        if (clazz == null) {
            diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                    "Unknown Java type '" + symbol.name + "'", module.uri, subscript.span));
            checkArgsUnchecked(call);
            return NativeType.ERROR;
        }
        List<Type> args = typeResolver.resolveJavaArguments(module, clazz, subscript.typeArgs,
                activeTypeParams, subscript.span);
        if (args == null) {
            checkArgsUnchecked(call);
            return NativeType.ERROR;
        }
        JavaType applied = new JavaType(clazz, args, false);
        subscript.applicationType = applied;
        if (call.resolved != null) {
            call.resolved.typeArgs = args;
        }
        return checkJvmConstructor(applied, call, args);
    }

    /**
     * {@code Host.method[Type](...)} or {@code receiver.method[Type](...)};
     * arguments are written at the call site because the profile never infers
     * Java method type variables.
     */
    private Type checkExplicitJvmMethod(ResolvedField field, Expr.Subscript subscript, Expr.Call call) {
        List<Type> args = new ArrayList<>();
        for (sprig.compiler.ast.TypeRef ref : subscript.typeArgs) {
            args.add(typeResolver.resolve(module, ref, activeTypeParams));
        }
        ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.JVM_METHOD, null);
        resolved.receiverType = field.receiverType;
        resolved.typeArgs = args;
        Type result = checkJvmMethod(field, call, args);
        resolved.jvm = field.jvm;
        resolved.returnType = result;
        return result;
    }

    private void checkPositionalCallSubstituted(Decl.Func func, Expr.Call call, String label,
                                                Map<TypeParameterType, Type> map) {
        checkPositionalCallSubstituted(func, call, label, map, null);
    }

    /** With {@code inference}, the call's type arguments were inferred; see {@link #checkArgument}. */
    private void checkPositionalCallSubstituted(Decl.Func func, Expr.Call call, String label,
                                                Map<TypeParameterType, Type> map, Inference inference) {
        if (call.hasNamedArgs()) {
            diagnostics.add(Diagnostic.error(Codes.CALL_POSITIONAL_REQUIRED, Phase.TYPE,
                    "Function '" + label + "' takes positional arguments only; remove the names",
                    module.uri, call.span));
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
            Type expected = Substitution.apply(func.params.get(i).type, map);
            checkArgument(args.get(i).value, expected, inference, "argument " + (i + 1) + " of " + label);
        }
        for (int i = count; i < args.size(); i++) {
            checkExpr(args.get(i).value, null);
        }
        requireFunctionEffects(func, call);
    }

    /**
     * Effects of a call to a named function. A rethrows function throws what
     * its callable arguments throw, so each throwing argument is checked at
     * the call site; one of the current rethrows function's own callable
     * parameters passed straight through contributes nothing here, because
     * the current function's callers already account for it.
     */
    private void requireFunctionEffects(Decl.Func func, Expr.Call call) {
        if (!func.rethrows) {
            requireHandled(func.throwsTypes, call.span);
            return;
        }
        int count = Math.min(call.args.size(), func.params.size());
        for (int i = 0; i < count; i++) {
            if (func.params.get(i).type instanceof FunctionType declared && declared.throwsAny()) {
                Expr argument = call.args.get(i).value;
                if (argument.type instanceof FunctionType actual) {
                    requireCallableEffects(actual, argument, call.span);
                }
            }
        }
    }

    /** A call through, or a pass of, a function value: its throws clause needs handling unless it is passed through. */
    private void requireCallableEffects(FunctionType type, Expr argument, Span span) {
        if (!type.throwsAny() || isPassThrough(argument)) {
            return;
        }
        requireHandled(type.throwsTypes, span);
    }

    /**
     * Inside the body of a rethrows function (not inside a lambda written in
     * it), one of its own callable parameters with a throws clause is passed
     * through: calling it, or handing it to another rethrows function or to
     * map/filter/forEach, needs no handling.
     */
    private boolean isPassThrough(Expr argument) {
        if (currentFunction == null || !currentFunction.rethrows || !lambdaEffects.isEmpty()) {
            return false;
        }
        if (!(argument instanceof Expr.Name name) || name.symbol == null) {
            return false;
        }
        for (Decl.Param param : currentFunction.params) {
            if (param.symbol == name.symbol) {
                return param.type instanceof FunctionType fn && fn.throwsAny();
            }
        }
        return false;
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
            // Short-circuit context: the right operand runs with the left
            // operand known true for `and` and known false for `or`, so the
            // existing null narrowing applies inside the condition itself.
            narrowing.push(op.equals("and") ? narrowTrue(binary.left) : narrowFalse(binary.left));
            Type right;
            try {
                right = checkExpr(binary.right, NativeType.BOOL);
            } finally {
                narrowing.pop();
            }
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
        // v0.8: a bare type parameter guarantees no operators. Null checks are
        // the one universally valid comparison and stay allowed.
        boolean nullCheck = left == NativeType.NULL || right == NativeType.NULL;
        if (!nullCheck && (containsTypeParameter(left) || containsTypeParameter(right))) {
            if ((op.equals("==") || op.equals("!="))
                    && left.equals(right) && isEquatableParameter(left)) {
                binary.valueEquality = true;
                return NativeType.BOOL;
            }
            boolean ordering = op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">=");
            if (ordering && left.equals(right) && left instanceof TypeParameterType && isComparableType(left)) {
                binary.genericOrdering = true;
                return NativeType.BOOL;
            }
            if (left != NativeType.ERROR && right != NativeType.ERROR) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                        "Operator '" + op + "' is not available for generic type parameter "
                                + (containsTypeParameter(left) ? left.display() : right.display())
                                + "; this operation needs a supported leading capability",
                        module.uri, binary.span)
                        .withHint("Use a concrete type, or begin the function with 'requires "
                                + (containsTypeParameter(left) && left instanceof TypeParameterType p
                                        ? p.name : "T")
                                + (ordering ? ": Comparable' for ordering." : ": Equatable' for equality.")));
            }
            return NativeType.ERROR;
        }
        return switch (op) {
            case "==", "!=" -> checkEquality(binary, left, right);
            case "+", "-", "*", "/", "%" -> checkArithmetic(op, left, right, binary.span, false, binary.left, binary.right);
            case "<", "<=", ">", ">=" -> checkOrdering(binary, left, right);
            default -> NativeType.ERROR;
        };
    }

    static boolean containsTypeParameter(Type type) {
        if (type == null) {
            return false;
        }
        if (type instanceof TypeParameterType) {
            return true;
        }
        if (type instanceof NullableType nullable) {
            return containsTypeParameter(nullable.inner);
        }
        if (type instanceof ListType list) {
            return containsTypeParameter(list.element);
        }
        if (type instanceof MapType map) {
            return containsTypeParameter(map.key) || containsTypeParameter(map.value);
        }
        if (type instanceof ClassType classType) {
            return classType.args.stream().anyMatch(TypeChecker::containsTypeParameter);
        }
        if (type instanceof VariantType variantType) {
            return variantType.args.stream().anyMatch(TypeChecker::containsTypeParameter);
        }
        if (type instanceof VariantCaseType caseType) {
            return caseType.variantArgs.stream().anyMatch(TypeChecker::containsTypeParameter);
        }
        if (type instanceof FunctionType function) {
            return function.params.stream().anyMatch(TypeChecker::containsTypeParameter)
                    || containsTypeParameter(function.result);
        }
        return false;
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
        if (left != null && right != null
                && (left.nonNull() instanceof FunctionType || right.nonNull() instanceof FunctionType)
                && left != NativeType.NULL && right != NativeType.NULL) {
            // Two mentions of the same function make two values, so identity
            // would answer false where a reader expects true: not comparable.
            diagnostics.add(Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                    "Function values cannot be compared with '" + binary.op + "'", module.uri, binary.span)
                    .withTypes("comparable values", left.display() + " and " + right.display())
                    .withHint("Compare what the functions compute, or compare a name, enum or variant that chooses "
                            + "the function; a nullable function value may still be compared with null."));
            return NativeType.BOOL;
        }
        if (left == NativeType.UNIT || right == NativeType.UNIT) {
            requireValue(left, binary.left, "an operand of '" + binary.op + "'");
            requireValue(right, binary.right, "an operand of '" + binary.op + "'");
            return NativeType.BOOL;
        }
        if (left == NativeType.NULL || right == NativeType.NULL) {
            Type other = left == NativeType.NULL ? right : left;
            if (other == NativeType.NULL) {
                return NativeType.BOOL;
            }
            if (other.isNullable() || Semantics.isReference(other)) {
                return NativeType.BOOL;
            }
            // A name declared nullable keeps comparing with null while it is
            // narrowed: the check is redundant there, not a type confusion,
            // and a program written for the rule before elif chains narrowed
            // (`elif x != null and x > 5:` after `if x == null:`) keeps compiling.
            if (declaredNullable(left == NativeType.NULL ? binary.right : binary.left)) {
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
        // Nullable scalars use null-safe value equality on the common Sprig
        // numeric/boolean type; primitive local comparisons stay primitive.
        if (left.isNullable() || right.isNullable()) {
            if (isInteger(leftBase) && isInteger(rightBase)) {
                binary.comparisonType = NullableType.of(NativeType.INT);
            } else if (isBinaryFloat(leftBase) && isBinaryFloat(rightBase)) {
                binary.comparisonType = NullableType.of(NativeType.FLOAT);
            } else if (leftBase == NativeType.BOOL && rightBase == NativeType.BOOL) {
                binary.comparisonType = NullableType.of(NativeType.BOOL);
            }
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
            Diagnostic operands = Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                    "Operator '" + binary.op + "' requires matching Int, Float or String operands",
                    module.uri, binary.span).withTypes("Int/Float/String pair", left.display() + " and " + right.display());
            if (left instanceof NullableType || right instanceof NullableType
                    || (isInteger(left) && isBinaryFloat(right)) || (isBinaryFloat(left) && isInteger(right))) {
                operands.withHint(operandHint(left, right, binary.left, binary.right));
            }
            diagnostics.add(operands);
        }
        return NativeType.BOOL;
    }

    private Type checkArithmetic(String op, Type left, Type right, Span span, boolean compound,
                                 Expr leftExpr, Expr rightExpr) {
        if (left == NativeType.ERROR || right == NativeType.ERROR) {
            return NativeType.ERROR;
        }
        if (op.equals("+") && (left.nonNull() == NativeType.STRING || right.nonNull() == NativeType.STRING)) {
            Type other = left.nonNull() == NativeType.STRING ? right : left;
            if (other == NativeType.UNIT || other == NativeType.NULL) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                        "Cannot concatenate " + other.display() + " with String", module.uri, span));
                return NativeType.ERROR;
            }
            // A null would be joined as the text "null"; absence must be handled
            // before the value becomes text, as for any other use.
            if (left.isNullable() || right.isNullable()) {
                Type nullable = left.isNullable() ? left : right;
                diagnostics.add(Diagnostic.error(Codes.TYPE_NULLABLE, Phase.TYPE,
                        "Cannot join " + nullable.display() + " into a String; it may be null", module.uri, span)
                        .withTypes(nullable.nonNull().display(), nullable.display())
                        .withHint(operandHint(left, right, leftExpr, rightExpr)));
                return NativeType.ERROR;
            }
            return NativeType.STRING;
        }
        if (isInteger(left) && isInteger(right)) {
            if (op.equals("/")) {
                String a = operandText(leftExpr, "a");
                String b = operandText(rightExpr, "b");
                diagnostics.add(Diagnostic.error(Codes.NUM_DIVISION, Phase.TYPE,
                        "Integer / would truncate; use a.divTrunc(b), or convert both operands explicitly",
                        module.uri, span).withTypes("explicit division", left.display() + " / " + right.display())
                        .withHint("Write " + a + ".divTrunc(" + b + ") to drop the remainder on purpose, or "
                                + a + ".toFloat() / " + b + ".toFloat() for a Float result. "
                                + "text.fixed(value, decimals) from @std/text.spr prints a Float with that many decimals."));
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
        if (left.isNullable() || right.isNullable()) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_NULLABLE, Phase.TYPE,
                    "Operator '" + op + "' cannot use a value that may be null", module.uri, span)
                    .withTypes("non-null operands", left.display() + " and " + right.display())
                    .withHint(operandHint(left, right, leftExpr, rightExpr)));
            return NativeType.ERROR;
        }
        if (!isNumeric(left) || !isNumeric(right)) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                    "Operator '" + op + "' is not defined for " + left.display() + " and " + right.display(),
                    module.uri, span)
                    .withTypes("numeric operands", left.display() + " and " + right.display()));
            return NativeType.ERROR;
        }
        diagnostics.add(Diagnostic.error(Codes.NUM_MIXED, Phase.TYPE,
                "Operator '" + op + "' has no implicit conversion between " + left.display()
                        + " and " + right.display(), module.uri, span)
                .withTypes("matching numeric families", left.display() + " and " + right.display())
                .withHint(operandHint(left, right, leftExpr, rightExpr)));
        return NativeType.ERROR;
    }

    /** A short source spelling for an operand in a hint: a name or a.b, else the fallback. */
    private static String operandText(Expr expr, String fallback) {
        if (expr instanceof Expr.Name name) {
            return name.name;
        }
        if (expr instanceof Expr.FieldAccess access && access.receiver instanceof Expr.Name receiver) {
            return receiver.name + "." + access.name;
        }
        Expr receiver = expr instanceof Expr.Index index ? index.receiver
                : expr instanceof Expr.Subscript subscript ? subscript.base : null;
        Expr key = expr instanceof Expr.Index index ? index.index
                : expr instanceof Expr.Subscript subscript
                        ? (subscript.resolvedIndex != null ? subscript.resolvedIndex : subscript.index) : null;
        if (receiver instanceof Expr.Name base) {
            if (key instanceof Expr.Name name) return base.name + "[" + name.name + "]";
            if (key instanceof Expr.IntLit literal) return base.name + "[" + literal.value + "]";
            if (key instanceof Expr.StringLit literal && literal.value.matches("[A-Za-z0-9_ .-]*")) {
                return base.name + "[\"" + literal.value + "\"]";
            }
        }
        return fallback;
    }

    /**
     * The concrete repair for two numeric operands that do not combine: a
     * nullable one is checked first, an Int next to a Float is converted.
     */
    private static String operandHint(Type left, Type right, Expr leftExpr, Expr rightExpr) {
        for (int side = 0; side < 2; side++) {
            Type type = side == 0 ? left : right;
            // A Java result is nullable as a platform JavaType, not a NullableType.
            if (type != null && type.isNullable()) {
                Expr operand = side == 0 ? leftExpr : rightExpr;
                if (!(operand instanceof Expr.Name)) {
                    // Only a binding narrows; a field, an index or a call is read again.
                    String text = operandText(operand, null);
                    return (text == null ? "This value" : text) + " may be null (" + type.display()
                            + "), and only a let narrows: write 'let value = " + (text == null ? "..." : text)
                            + "', check 'if value != null:', and use value inside that block"
                            + ", or give a fallback with or_else from @std/nulls.spr.";
                }
                String name = operandText(operand, "the value");
                if (operand instanceof Expr.Name binding && binding.symbol != null && binding.symbol.mutable) {
                    return name + " may be null (" + type.display() + "), and a var never narrows: copy it into a let "
                            + "(let current = " + name + "), check 'if current != null:', and use current inside that block.";
                }
                return name + " may be null (" + type.display() + "): check it first with 'if " + name
                        + " != null:', and inside that block it is " + type.nonNull().display()
                        + ", or give a fallback with or_else from @std/nulls.spr.";
            }
        }
        if (isInteger(left) && isBinaryFloat(right)) {
            return "Convert the Int side: " + operandText(leftExpr, "value") + ".toFloat().";
        }
        if (isBinaryFloat(left) && isInteger(right)) {
            return "Convert the Int side: " + operandText(rightExpr, "value") + ".toFloat().";
        }
        return "Convert deliberately with an exact or explicitly lossy numeric method.";
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
            Type itemType = requireValue(checkExpr(item, element), item, "a list element");
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
            Type keyActual = requireValue(checkExpr(key, keyType), key, "a map key");
            Type valueActual = requireValue(checkExpr(value, valueType), value, "a map value");
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
                && caseA.variant == caseB.variant && caseA.variantArgs.equals(caseB.variantArgs)) {
            return new VariantType(caseA.variant, caseA.variantArgs);
        }
        return null;
    }

    private boolean isPrintReference(Expr expr) {
        return expr instanceof Expr.Name name && name.symbol != null && name.symbol.kind == Symbol.Kind.FUNCTION
                && name.symbol.decl == null && name.name.equals("print");
    }

    /**
     * A named function, module function, method or {@code print} used as a value
     * is a function reference. It is checked and generated as the lambda that
     * forwards to it ({@code fn(a: T) => f(a)}), so it has the ordinary function
     * type, including the throws clause of what it calls. A method reference
     * evaluates its receiver once, when the value is created. Returns null when
     * the expression is not a reference.
     */
    private Type functionReference(Expr expr, Type expected) {
        if (expr.rewritten != null) {
            return checkExpr(expr.rewritten, expected);
        }
        Decl.Func func = null;
        Expr callee = null;
        Expr.FieldAccess methodAccess = null;
        Expr.Subscript written = null;
        Map<TypeParameterType, Type> map = Map.of();
        boolean print = false;
        if (expr instanceof Expr.Name name) {
            Symbol symbol = name.symbol;
            if (symbol == null) {
                return null;
            }
            if (symbol.kind == Symbol.Kind.FUNCTION && symbol.decl == null) {
                if (!name.name.equals("print")) {
                    return null; // other built-ins are reported as "must be called"
                }
                print = true;
            } else if (symbol.kind == Symbol.Kind.FUNCTION || symbol.kind == Symbol.Kind.METHOD) {
                func = (Decl.Func) symbol.decl;
            } else {
                return null;
            }
            Expr.Name forward = new Expr.Name(name.name);
            forward.symbol = symbol;
            forward.span = name.span;
            callee = forward;
        } else if (expr instanceof Expr.FieldAccess access) {
            ResolvedField field = resolveFieldAccess(access, false);
            if (field.kind == ResolvedField.Kind.MODULE_FUNCTION && field.symbol != null
                    && field.symbol.decl instanceof Decl.Func moduleFunc) {
                func = moduleFunc;
                callee = access;
            } else if (field.kind == ResolvedField.Kind.METHOD && field.methodDecl != null) {
                func = field.methodDecl;
                methodAccess = access;
                map = field.substitution;
            } else {
                return null;
            }
        } else if (expr instanceof Expr.Subscript subscript && subscript.typeArgs != null) {
            Decl.Func generic = null;
            if (subscript.base instanceof Expr.Name name && name.symbol != null
                    && (name.symbol.kind == Symbol.Kind.FUNCTION || name.symbol.kind == Symbol.Kind.METHOD)
                    && name.symbol.decl instanceof Decl.Func decl) {
                generic = decl;
            } else if (subscript.base instanceof Expr.FieldAccess access) {
                ResolvedField field = resolveFieldAccess(access, false);
                if (field.kind == ResolvedField.Kind.MODULE_FUNCTION && field.symbol != null
                        && field.symbol.decl instanceof Decl.Func decl) {
                    generic = decl;
                } else if (field.kind == ResolvedField.Kind.METHOD && field.methodDecl != null) {
                    generic = field.methodDecl;
                    methodAccess = access;
                }
            }
            if (generic == null || generic.typeParams.isEmpty()) {
                return null;
            }
            List<Type> args = resolveGenericArgs(generic, subscript);
            if (args == null) {
                return NativeType.ERROR;
            }
            func = generic;
            written = subscript;
            map = Substitution.forFunction(generic, args);
            callee = subscript;
        } else {
            return null;
        }
        String display = func != null ? func.name : "print";
        // Parameter types: from the referenced function, or for print from the
        // place the value goes to (never inferred from anything else).
        List<Type> paramTypes = new ArrayList<>();
        if (print) {
            if (expected == null || !(expected.nonNull() instanceof FunctionType target) || target.params.size() != 1) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                        "print as a value needs the parameter type of the place it goes to", module.uri, expr.span)
                        .withHint("Pass print where a fn(T) -> Unit is expected, such as items.forEach(print), "
                                + "or write the lambda: fn(value: String) => print(value)."));
                return NativeType.ERROR;
            }
            paramTypes.add(target.params.get(0));
        } else {
            if (!func.typeParams.isEmpty() && written == null) {
                diagnostics.add(Diagnostic.error(Codes.GENERIC_ARGS_REQUIRED, Phase.TYPE,
                        "Generic function '" + display + "' as a value needs its type arguments", module.uri, expr.span)
                        .withHint("Write " + display + "[" + String.join(", ", func.typeParams) + "] with the types "
                                + "spelled out, or a lambda: fn(value: Type) => " + display + "(value). Type arguments "
                                + "are never inferred from where a value goes."));
                return NativeType.ERROR;
            }
            if (func.params.size() > 3) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_FUNCTION_ARITY, Phase.TYPE,
                        "Function '" + display + "' takes " + func.params.size()
                                + " parameters; a function value takes zero to three", module.uri, expr.span));
                return NativeType.ERROR;
            }
            for (Decl.Param param : func.params) {
                Type type = param.type == null ? NativeType.ERROR : Substitution.apply(param.type, map);
                paramTypes.add(type);
            }
        }
        List<Decl.Param> params = new ArrayList<>();
        List<Expr.Arg> args = new ArrayList<>();
        for (int i = 0; i < paramTypes.size(); i++) {
            Decl.Param param = new Decl.Param("value" + i, null);
            param.type = paramTypes.get(i);
            param.symbol = new Symbol(Symbol.Kind.PARAM, param.name, param.type);
            param.symbol.span = expr.span;
            param.nameSpan = expr.span;
            params.add(param);
            Expr.Name use = new Expr.Name(param.name);
            use.symbol = param.symbol;
            use.span = expr.span;
            args.add(new Expr.Arg(null, use));
        }
        Expr.Lambda lambda;
        if (methodAccess != null) {
            // The receiver is evaluated once: an immutable name is captured as
            // it is, anything else is bound to a hidden let first.
            Expr receiver = written == null ? methodAccess.receiver : ((Expr.FieldAccess) written.base).receiver;
            Expr use = receiver;
            Symbol bound = null;
            if (!(receiver instanceof Expr.Name receiverName && receiverName.symbol != null
                    && !receiverName.symbol.mutable
                    && (receiverName.symbol.kind == Symbol.Kind.LOCAL || receiverName.symbol.kind == Symbol.Kind.PARAM
                        || receiverName.symbol.kind == Symbol.Kind.TOP_VAR))) {
                Type receiverType = receiver.type == null ? checkExpr(receiver, null) : receiver.type;
                bound = new Symbol(Symbol.Kind.LOCAL, "receiver", receiverType);
                bound.mutable = false;
                bound.span = expr.span;
                Expr.Name boundName = new Expr.Name("receiver");
                boundName.symbol = bound;
                boundName.span = expr.span;
                use = boundName;
            }
            Expr.FieldAccess forward = new Expr.FieldAccess(use, func.name);
            forward.span = expr.span;
            forward.nameSpan = methodAccess.nameSpan;
            Expr target = forward;
            if (written != null) {
                Expr.Subscript generic = new Expr.Subscript(forward, null, written.typeArgs);
                generic.span = expr.span;
                target = generic;
            }
            Expr.Call call = new Expr.Call(target, args);
            call.span = expr.span;
            lambda = new Expr.Lambda(params, call);
            if (bound != null) {
                lambda.boundReceiver = receiver;
                lambda.receiverSymbol = bound;
            }
        } else {
            Expr.Call call = new Expr.Call(callee, args);
            call.span = expr.span;
            lambda = new Expr.Lambda(params, call);
        }
        lambda.span = expr.span;
        expr.rewritten = lambda;
        Type type = checkLambda(lambda, expected);
        lambda.type = type;
        return type;
    }

    private Type checkLambda(Expr.Lambda lambda, Type expected) {
        if (lambda.params.size() > 3) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_FUNCTION_ARITY, Phase.TYPE,
                    "Lambdas support zero to three parameters", module.uri, lambda.span));
        }
        List<Type> params = new ArrayList<>();
        for (Decl.Param param : lambda.params) {
            params.add(param.type == null ? NativeType.ERROR : param.type);
        }
        lambdaDepth++;
        List<Type> effects = new ArrayList<>();
        lambdaEffects.push(effects);
        Type bodyType;
        try {
            bodyType = checkExpr(lambda.body, expected != null && expected.nonNull() instanceof FunctionType fn
                    ? fn.result : null);
        } finally {
            lambdaEffects.pop();
            lambdaDepth--;
        }
        Set<Symbol> captures = new LinkedHashSet<>();
        collectMutableCaptures(lambda.body, captures);
        for (Symbol capture : captures) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_CAPTURE, Phase.TYPE,
                    "Lambda captures mutable local '" + capture.name + "'", module.uri, lambda.span)
                    .withHint("Copy it into a 'let' binding before the lambda, or use a class field."));
        }
        // What the body may throw becomes the lambda's throws clause. Only
        // Error crosses a function value: it is unchecked on the JVM, so it
        // leaves Fn.apply unchanged and still matches catch Error. A checked
        // Java exception would have to be wrapped, and then the caller's
        // catch would no longer see it.
        List<Type> thrown = new ArrayList<>();
        for (Type effect : effects) {
            if (Semantics.isSprigError(effect) || Semantics.isErrorClass(effect)) {
                // An error class is an Error at the boundary: a function type
                // declares throws Error and nothing more specific.
                Type asError = Semantics.isSprigError(effect) ? effect : new JavaType(SprigError.class);
                if (!thrown.contains(asError)) {
                    thrown.add(asError);
                }
            } else if (Semantics.isJvmChecked(effect)) {
                diagnostics.add(Diagnostic.error(Codes.FLOW_THROWS, Phase.FLOW,
                        "A lambda cannot throw " + effect.display() + "; only Error crosses a function value",
                        module.uri, lambda.span)
                        .withHint("Call it from a named function that declares 'throws " + effect.display()
                                + "' or handles it with try/catch, and pass a lambda that calls that function."));
            }
        }
        // A written target supplies the result type while constructing a lambda;
        // already-created function values remain invariant.
        Type resultType = bodyType;
        if (expected != null && expected.nonNull() instanceof FunctionType fn
                && fn.params.equals(params) && Semantics.isAssignable(fn.result, bodyType))
            resultType = fn.result;
        FunctionType functionType = new FunctionType(params, resultType, thrown);
        if (expected != null && expected.nonNull() instanceof FunctionType expectedFunction
                && !(expectedFunction.params.equals(params) && expectedFunction.result.equals(resultType))) {
            // A difference in the throws clause alone is reported once, by the
            // assignment, argument or return check that owns the target type.
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
        } else if (expr instanceof Expr.Subscript subscript) {
            collectMutableCaptures(subscript.base, out);
            Expr index = subscript.index != null ? subscript.index : subscript.resolvedIndex;
            if (index != null) collectMutableCaptures(index, out);
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
        } else if (expr instanceof Expr.Match match) {
            collectMutableCaptures(match.cases.scrutinee,out);
            for (var branch : match.cases.branches) collectMutableCaptures(((Stmt.ExprStmt)branch.body.get(0)).expr,out);
        } else if (expr instanceof Expr.If ifExpr) {
            for (int i = 0; i < ifExpr.conditions.size(); i++) {
                collectMutableCaptures(ifExpr.conditions.get(i), out);
                collectMutableCaptures(ifExpr.values.get(i), out);
            }
            if (ifExpr.elseValue != null) {
                collectMutableCaptures(ifExpr.elseValue, out);
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
        if (callee instanceof Expr.Subscript subscript && subscript.typeArgs != null) {
            return checkGenericCall(subscript, call, expected);
        }
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
                    if (!func.typeParams.isEmpty()) {
                        return inferFunctionCall(func, symbol, call, ResolvedCall.Kind.FUNCTION);
                    }
                    ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.FUNCTION, func.returnType);
                    resolved.symbol = symbol;
                    resolved.methodDecl = func;
                    checkPositionalCall(func, call, func.name);
                    return func.returnType;
                }
                case METHOD -> {
                    Decl.Func func = (Decl.Func) symbol.decl;
                    if (!func.typeParams.isEmpty()) {
                        return inferFunctionCall(func, symbol, call, ResolvedCall.Kind.METHOD);
                    }
                    checkComparableArguments(func, Map.of(), call.span);
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
                    if (rejectNullableCallable(type, call)) return NativeType.ERROR;
                    diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                            "'" + name.name + "' is not callable", module.uri, call.span)
                            .withTypes("function value", type.display()));
                    checkArgsUnchecked(call);
                    return NativeType.ERROR;
                }
                case CLASS -> {
                    ClassType classType = (ClassType) symbol.type;
                    if (classType.decl.isGeneric()) {
                        return inferClassConstruction(classType.decl, symbol, call);
                    }
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
                    Diagnostic notConstructible = Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                            "Type " + name.name + " is not constructible", module.uri, call.span);
                    String conversion = Newcomer.conversionHint(name.name);
                    if (conversion != null) {
                        notConstructible.withHint(conversion);
                    }
                    diagnostics.add(notConstructible);
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
            Decl.VariantDecl generic = unappliedGenericVariant(access.receiver);
            if (generic != null) {
                Decl.VariantCase variantCase = findVariantCase(generic, access.name);
                if (variantCase != null && !variantCase.payloadless()) {
                    return inferVariantConstruction(generic, variantCase, access, call);
                }
            }
            ResolvedField field = resolveFieldAccess(access, true);
            switch (field.kind) {
                case VARIANT_CASE_VALUE -> {
                    ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.VARIANT_CTOR, field.type);
                    resolved.variantCase = field.variantCase;
                    return checkVariantConstructor(field, call);
                }
                case METHOD -> {
                    Decl.Func func = field.methodDecl;
                    Map<TypeParameterType, Type> map = field.substitution;
                    checkComparableArguments(func, map, call.span);
                    Type returnType = Substitution.apply(func.returnType, map);
                    ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.METHOD, returnType);
                    resolved.symbol = func.symbol;
                    resolved.methodDecl = func;
                    resolved.receiverType = field.receiverType;
                    resolved.substitution = map;
                    checkPositionalCallSubstituted(func, call, func.name, map);
                    return returnType;
                }
                case MODULE_FUNCTION -> {
                    Decl.Func func = field.methodDecl;
                    if (!func.typeParams.isEmpty()) {
                        return inferFunctionCall(func, field.symbol, call, ResolvedCall.Kind.MODULE_FUNCTION);
                    }
                    ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.MODULE_FUNCTION,
                            func.returnType);
                    resolved.symbol = field.symbol;
                    resolved.methodDecl = func;
                    checkPositionalCall(func, call, func.name);
                    return func.returnType;
                }
                case MODULE_TYPE -> {
                    if (field.type instanceof ClassType classType && classType.decl.isGeneric()) {
                        return inferClassConstruction(classType.decl, field.symbol, call);
                    }
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
                    if (rejectNullableCallable(type, call)) return NativeType.ERROR;
                    if (type == NativeType.ERROR) {
                        // The member already failed to resolve and was reported.
                        checkArgsUnchecked(call);
                        return NativeType.ERROR;
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
        if (rejectNullableCallable(calleeType, call)) return NativeType.ERROR;
        if (calleeType != NativeType.ERROR) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_NOT_CALLABLE, Phase.TYPE,
                    "Expression is not callable", module.uri, call.span)
                    .withTypes("function value", calleeType.display()));
        }
        checkArgsUnchecked(call);
        return NativeType.ERROR;
    }

    // ------------------------------------------------------------------
    // Generic calls without type arguments: inference from the arguments
    // ------------------------------------------------------------------

    /** One argument of a generic call and the declared parameter or field type it is passed for. */
    private record GenericSlot(Expr value, Type declared, String source) {
    }

    /**
     * What inference learned about one call: the solution, the arguments it
     * already checked for real with their types, and the arguments whose own
     * check reported an error. {@link #arguments} are the type arguments the
     * call's arguments are checked against: the inferred ones when they are
     * valid, otherwise a stand-in for each one the call could not establish.
     */
    private static final class Inference {
        final Map<Expr, Type> checked = new java.util.IdentityHashMap<>();
        final Set<Expr> broken = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        /** Arguments probed as a whole without an error; the tree holds their types. */
        final Set<Expr> probed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        /** Arguments whose shape cannot fit their parameter: reported as the mismatch they are. */
        final Set<Expr> mismatched = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        /** Arguments whose Unit result left a parameter without a type, with the reason. */
        final Map<Expr, String> units = new java.util.IdentityHashMap<>();
        /** For each argument, where the inferred types its parameter mentions came from. */
        final Map<Expr, String> notes = new java.util.IdentityHashMap<>();
        TypeArgumentInference.Solution solution;
        List<Type> arguments;
        boolean valid;
    }

    /** Whether an expression's type follows from where it goes: a literal, a lambda, a match or an if. */
    private static boolean takesExpectedType(Expr value) {
        if (value instanceof Expr.Unary unary && !unary.op.equals("not")) {
            return takesExpectedType(unary.operand);
        }
        return value instanceof Expr.IntLit || value instanceof Expr.FloatLit || value instanceof Expr.NullLit
                || value instanceof Expr.ListLit || value instanceof Expr.MapLit || value instanceof Expr.Lambda
                || value instanceof Expr.Match || value instanceof Expr.If;
    }

    /**
     * Infers the type arguments of a generic call from its arguments alone.
     * An argument whose type does not depend on an expected type is checked
     * here, once, for real. Literals are taken apart and their elements,
     * lambdas and matches are probed without an expected type; all of them
     * are checked again against the substituted types afterwards. An argument
     * whose check reports an error, or whose type holds an error, is no
     * evidence, and is checked again for real so its error is reported.
     */
    private Inference inferTypeArguments(Decl decl, List<GenericSlot> slots, Span span) {
        Inference inference = new Inference();
        Expr[] current = new Expr[1];
        TypeArgumentInference engine = new TypeArgumentInference(decl, new TypeArgumentInference.Arguments() {
            @Override
            public Type typeOf(Expr part) {
                if (part == current[0] && !takesExpectedType(part)) {
                    int before = diagnostics.errorCount();
                    Type type = checkExpr(part, null);
                    inference.checked.put(part, type);
                    if (diagnostics.errorCount() != before) {
                        inference.broken.add(part);
                        return null;
                    }
                    return type;
                }
                if (probe(part).hasErrors() || part.type == null
                        || TypeArgumentInference.containsError(part.type)) {
                    inference.broken.add(current[0]);
                    return null;
                }
                if (part == current[0]) {
                    inference.probed.add(part);
                }
                return part.type;
            }

            @Override
            public boolean acceptsNullable(String parameter, Type argument) {
                return typeResolver.acceptsNullableArgument(module, decl, parameter, argument, span);
            }
        });
        for (GenericSlot slot : slots) {
            current[0] = slot.value();
            engine.argument(slot.declared(), slot.value(), slot.source());
        }
        TypeArgumentInference.Solution solution = engine.solve();
        inference.solution = solution;
        for (GenericSlot slot : slots) {
            String note = inferredFrom(decl, slot, solution);
            if (note != null) {
                inference.notes.put(slot.value(), note);
            }
        }
        if (solution.complete()) {
            List<Type> checked = typeResolver.checkInferredArguments(module, decl, solution.arguments, span);
            if (checked != null) {
                inference.arguments = checked;
                inference.valid = true;
                return inference;
            }
        }
        // Inferred arguments that are invalid like written ones count as unknown.
        List<Type> partial = new ArrayList<>();
        for (int i = 0; i < decl.typeParams.size(); i++) {
            Type known = solution.complete() ? null : solution.arguments.get(i);
            partial.add(known != null ? known : TypeArgumentInference.unknown(decl.typeParams.get(i)));
        }
        inference.arguments = partial;
        return inference;
    }

    /**
     * Where the inferred types a slot's parameter mentions came from, when
     * that is another argument: "T is String, from argument 2". Attached to a
     * mismatch on this argument, it says why the argument was expected to
     * have that type.
     */
    private String inferredFrom(Decl decl, GenericSlot slot, TypeArgumentInference.Solution solution) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < decl.typeParams.size(); i++) {
            String parameter = decl.typeParams.get(i);
            String source = solution.sources.get(parameter);
            // A type that came from this argument itself explains nothing.
            if (source == null || source.equals(slot.source()) || source.endsWith(" " + slot.source())
                    || !mentions(slot.declared(), decl, parameter)) {
                continue;
            }
            parts.add(parameter + " is " + spell(solution.arguments.get(i)) + ", from " + source);
        }
        return parts.isEmpty() ? null : String.join("; ", parts) + ".";
    }

    /** Whether a declared parameter or field type names the declaration's type parameter. */
    private static boolean mentions(Type declared, Decl decl, String parameter) {
        return !Substitution.apply(declared, Map.of(new TypeParameterType(decl, parameter), NativeType.ERROR))
                .equals(declared);
    }

    /**
     * Inside a probe a generic call stops once it knows its type arguments;
     * see {@link #probe}. A call whose inference failed still leaves an error
     * in the probe, so the argument it is part of is checked again for real.
     */
    private boolean stopAfterInference(Inference inference, int errorsBefore, Span span) {
        if (probing == 0) {
            return false;
        }
        if (!inference.valid && diagnostics.errorCount() == errorsBefore) {
            diagnostics.add(Diagnostic.error(Codes.GENERIC_ARGS_REQUIRED, Phase.TYPE,
                    "Cannot infer type arguments", module.uri, span));
        }
        return true;
    }

    /** A generic function called without type arguments: lists.sorted(names). */
    private Type inferFunctionCall(Decl.Func func, Symbol symbol, Expr.Call call, ResolvedCall.Kind kind) {
        int errors = diagnostics.errorCount();
        List<GenericSlot> slots = new ArrayList<>();
        int count = Math.min(call.args.size(), func.params.size());
        for (int i = 0; i < count; i++) {
            slots.add(new GenericSlot(call.args.get(i).value, func.params.get(i).type, "argument " + (i + 1)));
        }
        Inference inference = inferTypeArguments(func, slots, call.span);
        Map<TypeParameterType, Type> map = Substitution.forFunction(func, inference.arguments);
        String callee = calleeText(call.callee, func.name);
        Type returnType = inference.valid ? Substitution.apply(func.returnType, map) : NativeType.ERROR;
        ResolvedCall resolved = resolvedCall(call, kind, returnType);
        resolved.symbol = symbol;
        resolved.methodDecl = func;
        if (inference.valid) {
            resolved.typeArgs = inference.arguments;
            resolved.substitution = map;
        }
        if (stopAfterInference(inference, errors, call.span)) {
            return returnType;
        }
        if (inference.valid) {
            checkComparableArguments(func, map, calleeSpan(call));
        } else if (call.args.size() == func.params.size() && !call.hasNamedArgs()) {
            // A wrong argument count or named arguments explain a missing type argument themselves.
            reportUninferred(func, inference, callee, typeArgs -> callee + typeArgs + "(...)", call.span);
        }
        checkPositionalCallSubstituted(func, call, func.name, map, inference);
        requireAnError(inference, errors, func.name, callee + "[Type](...)", call.span);
        return returnType;
    }

    /** A generic class constructed without type arguments: Box(value=42). */
    private Type inferClassConstruction(Decl.ClassDecl decl, Symbol symbol, Expr.Call call) {
        int errors = diagnostics.errorCount();
        List<GenericSlot> slots = new ArrayList<>();
        Set<String> given = new HashSet<>();
        // Positional, unknown or missing fields explain a missing type argument themselves.
        boolean structural = call.hasPositionalArgs();
        for (Expr.Arg arg : call.args) {
            if (arg.name == null) {
                continue;
            }
            Decl.Field field = findField(decl, arg.name);
            if (field == null) {
                structural = true;
            } else if (given.add(arg.name)) {
                slots.add(new GenericSlot(arg.value, field.type, "field '" + arg.name + "'"));
            }
        }
        for (Decl.Field field : decl.fields) {
            structural |= field.defaultExpr == null && !given.contains(field.name);
        }
        Inference inference = inferTypeArguments(decl, slots, call.span);
        ClassType classType = new ClassType(decl, inference.arguments);
        String name = calleeText(call.callee, decl.name);
        ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.CLASS_CTOR,
                inference.valid ? classType : NativeType.ERROR);
        resolved.classDecl = decl;
        resolved.symbol = symbol;
        if (inference.valid) {
            resolved.typeArgs = inference.arguments;
            resolved.substitution = Substitution.forClass(classType);
            resolved.instantiatedType = classType;
        }
        if (stopAfterInference(inference, errors, call.span)) {
            return inference.valid ? classType : NativeType.ERROR;
        }
        if (!inference.valid && !structural) {
            reportUninferred(decl, inference, name, typeArgs -> name + typeArgs + "(...)", call.span);
        }
        checkClassConstructor(classType, call, inference);
        if (!inference.valid) {
            resolved.substitution = Map.of();
            resolved.instantiatedType = null;
        }
        requireAnError(inference, errors, decl.name, name + "[Type](...)", call.span);
        return inference.valid ? classType : NativeType.ERROR;
    }

    /** A case of a generic variant constructed without type arguments: Option.Some(value=1). */
    private Type inferVariantConstruction(Decl.VariantDecl decl, Decl.VariantCase variantCase,
                                          Expr.FieldAccess access, Expr.Call call) {
        int errors = diagnostics.errorCount();
        if (access.receiver instanceof Expr.FieldAccess qualifier) {
            resolveFieldAccess(qualifier, false); // module.Variant: recorded for tooling, like any member
        }
        List<GenericSlot> slots = new ArrayList<>();
        Set<String> given = new HashSet<>();
        boolean structural = call.hasPositionalArgs();
        for (Expr.Arg arg : call.args) {
            if (arg.name == null) {
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
                structural = true;
            } else if (given.add(arg.name)) {
                slots.add(new GenericSlot(arg.value, payload.type, "field '" + arg.name + "'"));
            }
        }
        for (Decl.Field payload : variantCase.fields) {
            structural |= !given.contains(payload.name);
        }
        Inference inference = inferTypeArguments(decl, slots, call.span);
        ResolvedField field = variantCaseValue(new VariantType(decl, inference.arguments), access);
        access.resolved = field;
        String owner = calleeText(access.receiver, decl.name);
        ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.VARIANT_CTOR,
                inference.valid ? field.type : NativeType.ERROR);
        resolved.variantCase = variantCase;
        if (inference.valid) {
            resolved.typeArgs = inference.arguments;
            resolved.substitution = field.substitution;
            resolved.instantiatedType = field.type;
        }
        if (stopAfterInference(inference, errors, call.span)) {
            return inference.valid ? field.type : NativeType.ERROR;
        }
        if (!inference.valid && !structural) {
            reportUninferred(decl, inference, owner + "." + access.name,
                    typeArgs -> owner + typeArgs + "." + access.name + "(...)", call.span);
        }
        Type type = checkVariantConstructor(field, call, inference);
        if (!inference.valid) {
            resolved.substitution = Map.of();
            resolved.instantiatedType = null;
        }
        requireAnError(inference, errors, decl.name, owner + "[Type]." + access.name + "(...)", call.span);
        return inference.valid ? type : NativeType.ERROR;
    }

    /**
     * Reports the type arguments a call left unknown, naming each one and why:
     * no argument mentions it, an argument says nothing about it, or two
     * arguments disagree. The hint writes the call with the inferred arguments
     * filled in, spelled as this module writes them. When every unknown
     * parameter is unknown only because an argument has another shape than its
     * parameter, or is a Unit result, those arguments are reported instead,
     * as the mismatch or the Unit result they are; a written type argument
     * would not help. A parameter that waits only on an argument with an error
     * of its own is left to that error.
     */
    private void reportUninferred(Decl decl, Inference inference, String name,
                                  java.util.function.Function<String, String> explicitForm, Span span) {
        TypeArgumentInference.Solution solution = inference.solution;
        if (solution.failures.isEmpty()) {
            return;
        }
        boolean byArguments = true;
        for (String parameter : solution.failures.keySet()) {
            TypeArgumentInference.Failure failure = solution.silences.get(parameter);
            byArguments &= failure != null && (failure.kind() == TypeArgumentInference.Silence.MISFIT
                    || failure.kind() == TypeArgumentInference.Silence.UNIT);
        }
        if (byArguments) {
            for (String parameter : solution.failures.keySet()) {
                TypeArgumentInference.Failure failure = solution.silences.get(parameter);
                if (failure.kind() == TypeArgumentInference.Silence.MISFIT) {
                    inference.mismatched.add(failure.argument());
                } else {
                    inference.units.putIfAbsent(failure.argument(), failure.reason() + ", so it cannot give "
                            + parameter + " of '" + name + "' a type");
                }
            }
            return;
        }
        List<String> unknown = new ArrayList<>(solution.failures.keySet());
        List<String> written = new ArrayList<>();
        for (int i = 0; i < decl.typeParams.size(); i++) {
            Type known = solution.arguments.get(i);
            written.add(known != null ? spell(known) : "Type");
        }
        String form = explicitForm.apply("[" + String.join(", ", written) + "]");
        String parameters = String.join(" and ", unknown);
        String hint = solution.conflicts.containsAll(unknown)
                ? "Write " + form + " with the type you mean for " + parameters + "."
                : "Write " + form + " with " + parameters + " spelled out; type arguments are inferred only "
                        + "from a call's arguments, never from where its result goes.";
        diagnostics.add(Diagnostic.error(Codes.GENERIC_ARGS_REQUIRED, Phase.TYPE,
                "Cannot infer type argument" + (unknown.size() > 1 ? "s " : " ") + String.join(", ", unknown)
                        + " of '" + name + "': " + String.join("; ", solution.failures.values()),
                module.uri, span).withHint(hint));
    }

    /**
     * A failed inference is never silent: when nothing reported an error for
     * the call, it is reported here, unless an argument carries an error that
     * was reported before the call.
     */
    private void requireAnError(Inference inference, int errorsBefore, String name, String form, Span span) {
        if (inference.valid || diagnostics.errorCount() != errorsBefore) {
            return;
        }
        if (!inference.solution.blocked.isEmpty() && errorsBefore > 0) {
            return;
        }
        diagnostics.add(Diagnostic.error(Codes.GENERIC_ARGS_REQUIRED, Phase.TYPE,
                "Cannot infer the type arguments of '" + name + "' from its arguments",
                module.uri, span).withHint("Write " + form + " with the type arguments spelled out."));
    }

    /**
     * A type as the module being checked writes it, for hints that give code
     * to write: a declaration from another module through its import alias
     * (lists.Pair), a Java class through its import alias, and a variant case
     * as its variant, because a case is not a type a program can write.
     */
    private String spell(Type type) {
        return TypeSpelling.in(module, type);
    }

    /** The generic variant a case constructor's receiver names without type arguments, or null. */
    private static Decl.VariantDecl unappliedGenericVariant(Expr receiver) {
        Symbol symbol = null;
        if (receiver instanceof Expr.Name name) {
            symbol = name.symbol;
        } else if (receiver instanceof Expr.FieldAccess qualifier && qualifier.receiver instanceof Expr.Name alias
                && alias.symbol != null && alias.symbol.kind == Symbol.Kind.MODULE && alias.symbol.module != null) {
            symbol = alias.symbol.module.scope.types.get(qualifier.name);
        }
        if (symbol != null && symbol.kind == Symbol.Kind.VARIANT && symbol.type instanceof VariantType variant
                && variant.decl.isGeneric()) {
            return variant.decl;
        }
        return null;
    }

    /** Where a call names its callee: the bare name, or the member name of 'module.function'. */
    private static Span calleeSpan(Expr.Call call) {
        if (call.callee instanceof Expr.Name name && name.span != null) {
            return name.span;
        }
        if (call.callee instanceof Expr.FieldAccess access && access.nameSpan != null) {
            return access.nameSpan;
        }
        return call.span;
    }

    /**
     * Checks one argument against its expected type, as for written type
     * arguments. An argument inference already checked for real keeps its
     * type, and a mismatch on an argument whose type came from inference says
     * where the expected type came from. Where a type argument stayed unknown
     * nothing is required of an argument, because the call's own diagnostic
     * covers it: it is only typed, unless its own check reported an error, or
     * its shape or Unit result is what left the type argument unknown; those
     * are reported here.
     */
    private Type checkArgument(Expr value, Type expected, Inference inference, String what) {
        Type known = inference == null ? null : inference.checked.get(value);
        if (inference != null && expected != null && TypeArgumentInference.containsUnknown(expected)) {
            if (inference.mismatched.contains(value)) {
                Type actual = known != null ? known : checkExpr(value, expected);
                requireAssignable(expected, actual, value.span, Codes.TYPE_MISMATCH, what);
                return actual;
            }
            String unit = inference.units.get(value);
            if (unit != null) {
                Type actual = known != null ? known : checkExpr(value, null);
                diagnostics.add(Diagnostic.error(Codes.TYPE_UNIT, Phase.TYPE,
                        Character.toUpperCase(unit.charAt(0)) + unit.substring(1), module.uri, value.span)
                        .withTypes("a value type", "Unit")
                        .withHint("Pass a function or lambda that returns a value; Unit is only the result "
                                + "type of a function that returns nothing."));
                return actual;
            }
            if (known != null) {
                return known;
            }
            // A lambda is checked for real without an expected type, as its
            // probe was, so errors in generic calls nested in it, whose
            // arguments a probe does not check, are reported now.
            if (inference.broken.contains(value) || value instanceof Expr.Lambda) {
                return checkExpr(value, null);
            }
            if (!inference.probed.contains(value)) {
                probe(value);
            }
            return value.type;
        }
        Type actual = known != null ? known : checkExpr(value, expected);
        int before = diagnostics.all().size();
        requireAssignable(expected, actual, value.span, Codes.TYPE_MISMATCH, what);
        String note = inference == null ? null : inference.notes.get(value);
        if (note != null) {
            List<Diagnostic> all = diagnostics.all();
            for (int i = before; i < all.size(); i++) {
                Diagnostic mismatch = all.get(i);
                mismatch.withHint(mismatch.hint == null ? note : mismatch.hint + " " + note);
            }
        }
        return actual;
    }

    /** How the call names its function: 'sorted', 'lists.sorted' or 'box.get'. */
    private static String calleeText(Expr callee, String fallback) {
        if (callee instanceof Expr.Name name) {
            return name.name;
        }
        if (callee instanceof Expr.FieldAccess access && access.receiver instanceof Expr.Name receiver) {
            return receiver.name + "." + access.name;
        }
        return fallback;
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
        requireFunctionEffects(func, call);
    }

    private boolean rejectNullableCallable(Type type, Expr.Call call) {
        if (type == null || !type.isNullable() || !(type.nonNull() instanceof FunctionType)) return false;
        diagnostics.add(Diagnostic.error(Codes.TYPE_NULLABLE, Phase.TYPE,
                "Cannot invoke a nullable function value; check for null first", module.uri, call.span)
                .withTypes(type.nonNull().display(), type.display()));
        checkArgsUnchecked(call);
        return true;
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
        requireCallableEffects(functionType, call.callee, call.span);
    }

    private void checkClassConstructor(ClassType classType, Expr.Call call) {
        checkClassConstructor(classType, call, null);
    }

    /** With {@code inference}, the class's type arguments were inferred; see {@link #checkArgument}. */
    private void checkClassConstructor(ClassType classType, Expr.Call call, Inference inference) {
        Decl.ClassDecl decl = classType.decl;
        if (decl.contract) {
            diagnostics.add(Diagnostic.error(Codes.CLASS_ABSTRACT, Phase.TYPE,
                    "Contract class '" + decl.name + "' has methods without a body and cannot be constructed",
                    module.uri, call.span)
                    .withHint("Write a class with those methods and 'conform C to " + decl.name
                            + "', then construct that class; a value of type " + decl.name + " is any conforming object."));
            checkArgsUnchecked(call);
            return;
        }
        Map<TypeParameterType, Type> map = classType.args.isEmpty()
                ? Map.of() : Substitution.forClass(classType);
        if (call.resolved != null) {
            call.resolved.substitution = map;
            call.resolved.instantiatedType = classType;
        }
        boolean positional = call.hasPositionalArgs();
        if (positional) {
            Diagnostic named = Diagnostic.error(Codes.CALL_NAMED_REQUIRED, Phase.TYPE,
                    "Constructor of class " + decl.name + " requires named arguments, e.g. "
                            + decl.name + "(" + firstFieldSnippet(decl) + ")",
                    module.uri, call.span);
            String rewrite = namedConstructorRewrite(decl, call);
            if (rewrite != null) {
                Span first = call.args.get(0).value.span;
                Span last = call.args.get(call.args.size() - 1).value.span;
                named.withHint("Write " + decl.name + "(" + rewrite + "); each argument names its field.")
                        .withEdit(new Span(first.startLine, first.startColumn, last.endLine, last.endColumn,
                                first.startOffset, last.endOffset), rewrite, "name each argument after its field");
            } else {
                named.withHint("Write " + decl.name + "(" + fieldSnippets(decl) + "); each argument names its field.");
            }
            diagnostics.add(named);
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
            Type fieldType = Substitution.apply(field.type, map);
            checkArgument(arg.value, fieldType, inference, "field '" + arg.name + "'");
        }
        Set<Type> omittedDefaultEffects = new LinkedHashSet<>();
        for (Decl.Field field : decl.fields) {
            // Positional arguments were reported once above; the fields they
            // failed to name are not missing on top of that.
            if (field.defaultExpr == null && !provided.contains(field.name) && !positional) {
                diagnostics.add(Diagnostic.error(Codes.CALL_MISSING_FIELD, Phase.TYPE,
                        "Missing required field '" + field.name + ":"
                                + Substitution.apply(field.type, map).display() + "'",
                        module.uri, call.span));
            }
            if (field.defaultExpr != null && !provided.contains(field.name)) {
                omittedDefaultEffects.addAll(field.defaultThrowsTypes);
                if (defaultDependencies != null && collectingDefault != null && lambdaDepth == 0) {
                    defaultDependencies.get(collectingDefault).add(field);
                }
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

    private static String fieldSnippets(Decl.ClassDecl decl) {
        List<String> parts = new ArrayList<>();
        for (Decl.Field field : decl.fields) {
            parts.add(field.name + "=" + (field.type == null ? "?" : field.type.display()));
        }
        return String.join(", ", parts);
    }

    /**
     * The written call with each positional argument named after its field,
     * when the arguments line up with the fields (all of them, or exactly
     * the ones without a default) and the source is at hand; else null.
     */
    private String namedConstructorRewrite(Decl.ClassDecl decl, Expr.Call call) {
        if (module.source == null || call.args.isEmpty()) {
            return null;
        }
        List<Decl.Field> required = new ArrayList<>();
        for (Decl.Field field : decl.fields) {
            if (field.defaultExpr == null) required.add(field);
        }
        List<Decl.Field> targets = call.args.size() == decl.fields.size() ? decl.fields
                : call.args.size() == required.size() ? required : null;
        if (targets == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        int length = module.source.codePointCount(0, module.source.length());
        for (int i = 0; i < call.args.size(); i++) {
            Expr.Arg arg = call.args.get(i);
            Span span = arg.value.span;
            if (arg.name != null || span == null || span.startOffset < 0 || span.endOffset > length
                    || span.endOffset <= span.startOffset) {
                return null;
            }
            // Offsets count code points, as the lexer reads the text; String indexes
            // count UTF-16 units, which differ after an emoji or another supplementary character.
            int from = module.source.offsetByCodePoints(0, span.startOffset);
            int to = module.source.offsetByCodePoints(from, span.endOffset - span.startOffset);
            parts.add(targets.get(i).name + "=" + module.source.substring(from, to));
        }
        return String.join(", ", parts);
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
        return checkVariantConstructor(field, call, null);
    }

    /** With {@code inference}, the variant's type arguments were inferred; see {@link #checkArgument}. */
    private Type checkVariantConstructor(ResolvedField field, Expr.Call call, Inference inference) {
        Decl.VariantCase variantCase = field.variantCase;
        Map<TypeParameterType, Type> map = Map.of();
        if (field.type instanceof VariantCaseType caseType && !caseType.variantArgs.isEmpty()) {
            map = Substitution.forVariant(new VariantType(caseType.variant, caseType.variantArgs));
        }
        if (call.resolved != null) {
            call.resolved.substitution = map;
            call.resolved.instantiatedType = field.type;
        }
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
            Type payloadType = Substitution.apply(payload.type, map);
            checkArgument(arg.value, payloadType, inference, "field '" + arg.name + "'");
        }
        for (Decl.Field payload : variantCase.fields) {
            if (!provided.contains(payload.name)) {
                diagnostics.add(Diagnostic.error(Codes.CALL_MISSING_FIELD, Phase.TYPE,
                        "Missing required field '" + payload.name + ":"
                                + Substitution.apply(payload.type, map).display() + "'",
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
        if (access.receiver instanceof Expr.Subscript subscript && subscript.typeArgs != null) {
            // An application whose arguments failed is resolved again, so its
            // error is reported by this check too: the cached one may come
            // from a check whose diagnostics were dropped, such as the probe
            // of an inferred call's argument or the pass that collects
            // field default effects.
            Type application = subscript.applicationType != null
                    && !TypeArgumentInference.containsError(subscript.applicationType)
                    ? subscript.applicationType
                    : genericApplicationType(subscript, true);
            if (application instanceof VariantType variantType) {
                ResolvedField resolved = variantCaseValue(variantType, access);
                access.resolved = resolved;
                return resolved;
            }
            if (application != null && application != NativeType.ERROR) {
                diagnostics.add(Diagnostic.error(Codes.NAME_NOT_A_VALUE, Phase.NAME,
                        "Type " + application.display() + " has no case '" + access.name + "'",
                        module.uri, access.span));
                ResolvedField resolved = errorField(access);
                access.resolved = resolved;
                return resolved;
            }
        }
        if (access.receiver instanceof Expr.Name name && name.symbol != null) {
            Symbol symbol = name.symbol;
            ResolvedField resolved = switch (symbol.kind) {
                case MODULE -> moduleMember(symbol.module, access);
                case VARIANT -> {
                    VariantType variantType = (VariantType) symbol.type;
                    if (variantType.decl.isGeneric()) {
                        diagnostics.add(Diagnostic.error(Codes.GENERIC_ARGS_REQUIRED, Phase.TYPE,
                                "Variant '" + variantType.decl.name + "' is generic; write "
                                        + variantType.decl.name + "[Type]." + access.name,
                                module.uri, access.span));
                    }
                    yield variantCaseValue(variantType, access);
                }
                case ENUM -> enumCaseValue((EnumType) symbol.type, access);
                case BUILTIN_TYPE -> builtinStatic(symbol.type, access);
                case PARENT_VIEW -> parentMember(symbol, access, forCall);
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
        if (access.receiver instanceof Expr.FieldAccess qualifier) {
            ResolvedField namespace = resolveFieldAccess(qualifier, false);
            if (namespace.kind == ResolvedField.Kind.MODULE_TYPE) {
                ResolvedField resolved = null;
                if (namespace.type instanceof VariantType variant) {
                    if (variant.decl.isGeneric()) {
                        diagnostics.add(Diagnostic.error(Codes.GENERIC_ARGS_REQUIRED, Phase.TYPE,
                                "Generic variant '" + variant.decl.name + "' requires explicit type arguments",
                                module.uri, access.span));
                    }
                    resolved = variantCaseValue(variant, access);
                } else if (namespace.type instanceof EnumType enumType) {
                    resolved = enumCaseValue(enumType, access);
                } else if (namespace.type instanceof JavaType javaType) {
                    resolved = javaMember(javaType, access, true);
                }
                if (resolved != null) {
                    access.resolved = resolved;
                    return resolved;
                }
            }
        }
        Type receiver = checkExpr(access.receiver, null);
        ResolvedField resolved;
        if (receiver == null || receiver == NativeType.ERROR) {
            resolved = errorField(access);
        } else if (receiver == NativeType.UNIT) {
            // A Unit result is not a value, so it has no members, toString included.
            diagnostics.add(Diagnostic.error(Codes.TYPE_UNIT, Phase.TYPE,
                    "Cannot access '" + access.name + "' on a Unit result; Unit is not a value",
                    module.uri, access.nameSpan != null ? access.nameSpan : access.span)
                    .withHint("Call the function as a statement of its own; it returns nothing to use."));
            resolved = errorField(access);
        } else if (receiver.isNullable()) {
            // Point at the member: in a chain such as a.b().c() every access
            // shares the chain's span, so the name tells the errors apart.
            diagnostics.add(Diagnostic.error(Codes.TYPE_NULLABLE, Phase.TYPE,
                    "Cannot access '" + access.name + "' on a value that may be null (receiver type "
                            + receiver.display() + ")",
                    module.uri, access.nameSpan != null ? access.nameSpan : access.span)
                    .withHint("Bind the receiver to a let and check it with 'if value != null:' before using '"
                            + access.name + "'."));
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
            // toString() is the text print shows, as for every value: an Error's
            // message, also for the errors built on it and one caught as
            // RuntimeException, and Java's own text for any other object, an
            // interface-typed one included. Java code keeps Throwable.toString,
            // whose "class: message" header stack traces and `run --stacktrace`
            // read. A class with a toString overload that takes arguments
            // (BigInteger.toString(int)) stays a Java call.
            if (access.name.equals("toString") && !hasToStringWithArguments(javaType.clazz)) {
                return builtin(access, "toString", NativeType.STRING, javaType);
            }
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
            field.module = function.module;
            field.methodDecl = (Decl.Func) function.decl;
            field.type = function.type;
            field.symbol = function;
            return field;
        }
        Symbol variable = target.scope.topVars.get(access.name);
        if (variable != null) {
            ResolvedField field = new ResolvedField();
            field.kind = ResolvedField.Kind.MODULE_VAR;
            field.module = variable.module;
            field.symbol = variable;
            field.type = variable.type == null ? NativeType.ERROR : variable.type;
            return field;
        }
        Symbol type = target.scope.types.get(access.name);
        if (type != null && type.decl != null) {
            ResolvedField field = new ResolvedField();
            field.kind = ResolvedField.Kind.MODULE_TYPE;
            field.module = type.module;
            field.symbol = type;
            field.type = type.type;
            if (type.decl instanceof Decl.ClassDecl classDecl) {
                field.methodDecl = null;
                field.variantCase = null;
                field.classDecl = classDecl;
            }
            return field;
        }
        Diagnostic missing = Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                "Module '" + target.name + "' has no member '" + access.name + "'",
                module.uri, access.span);
        List<String> members = new ArrayList<>();
        for (Decl decl : target.decls) {
            if (!(decl instanceof Decl.Func func) || !func.isMethod()) {
                members.add(decl.name);
            }
        }
        if (!members.isEmpty()) {
            missing.withHint("Module '" + target.name + "' has: " + String.join(", ", members)
                    + ". 'sprig api' on the module shows their types.");
        }
        diagnostics.add(missing);
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
        field.type = new VariantCaseType(variantType.decl, variantType.args, variantCase);
        field.payloadless = variantCase.payloadless();
        field.constructorRef = true;
        field.typeArgs = variantType.args;
        if (!variantType.args.isEmpty()) {
            field.substitution = Substitution.forVariant(variantType);
        }
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
        String id = BuiltinMembers.staticId(receiver, access.name);
        if (id == null) {
            diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                    "Type " + receiver.display() + " has no static member '" + access.name + "'",
                    module.uri, access.span));
            return errorField(access);
        }
        return builtin(access, id, NativeType.ERROR, receiver);
    }

    private ResolvedField classMember(ClassType classType, Expr.FieldAccess access) {
        Map<TypeParameterType, Type> map = classType.args.isEmpty()
                ? Map.of() : Substitution.forClass(classType);
        for (Decl.Field field : classType.decl.fields) {
            if (field.name.equals(access.name)) {
                ResolvedField resolved = new ResolvedField();
                resolved.kind = ResolvedField.Kind.CLASS_FIELD;
                resolved.fieldDecl = field;
                resolved.symbol = field.symbol;
                resolved.type = Substitution.apply(field.type, map);
                resolved.receiverType = classType;
                resolved.substitution = map;
                return resolved;
            }
        }
        for (Decl.Func method : classType.decl.methods) {
            if (method.name.equals(access.name)) {
                ResolvedField resolved = new ResolvedField();
                resolved.kind = ResolvedField.Kind.METHOD;
                resolved.methodDecl = method;
                resolved.type = Substitution.apply(method.returnType, map);
                resolved.receiverType = classType;
                resolved.substitution = map;
                return resolved;
            }
        }
        if (access.name.equals("toString")) {
            return builtin(access, "toString", NativeType.STRING, classType);
        }
        if (classType.decl.superclass != null) {
            // A class that extends a Java class: inherited public members are
            // reached through the Java view, as on any value of that class.
            return javaMember(new JavaType(classType.decl.superclass), access, false);
        }
        diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                "Class " + classType.decl.name + " has no member '" + access.name + "'",
                module.uri, access.span));
        return errorField(access);
    }

    private ResolvedField variantPayload(VariantCaseType caseType, Expr.FieldAccess access) {
        Map<TypeParameterType, Type> map = caseType.variantArgs.isEmpty()
                ? Map.of()
                : Substitution.forVariant(new VariantType(caseType.variant, caseType.variantArgs));
        for (Decl.Field field : caseType.variantCase.fields) {
            if (field.name.equals(access.name)) {
                ResolvedField resolved = new ResolvedField();
                resolved.kind = ResolvedField.Kind.VARIANT_PAYLOAD;
                resolved.fieldDecl = field;
                resolved.symbol = field.symbol;
                resolved.type = Substitution.apply(field.type, map);
                resolved.receiverType = caseType;
                resolved.substitution = map;
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

    private static boolean hasToStringWithArguments(Class<?> clazz) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals("toString") && method.getParameterCount() > 0
                    && !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                return true;
            }
        }
        return false;
    }

    /** "Java class X" in a diagnostic, but Sprig's own Error under its Sprig name. */
    private static String javaOwner(Class<?> clazz) {
        return clazz == SprigError.class ? "Error" : "Java class " + clazz.getSimpleName();
    }

    /**
     * A member reached through the parent view of {@code conform C to J(...)
     * as NAME}: the inherited implementation of a public method of {@code J}
     * on the current object. The view is not a value and has no fields.
     */
    private ResolvedField parentMember(Symbol symbol, Expr.FieldAccess access, boolean forCall) {
        Decl.ClassDecl owner = symbol.owner;
        if (owner == null || owner.superclass == null) {
            return errorField(access); // the conformance checker reported why the class extends nothing
        }
        if (!forCall) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_PARENT, Phase.TYPE,
                    "'" + symbol.name + "' names the inherited " + owner.superclass.getSimpleName()
                            + " implementation and can only be called: " + symbol.name + "." + access.name + "(...)",
                    module.uri, access.span)
                    .withHint("Read inherited state through a method of " + owner.superclass.getSimpleName()
                            + ", or keep it in a Sprig field."));
            return errorField(access);
        }
        ResolvedField field = javaMember(new JavaType(owner.superclass), access, false, true);
        if (field.kind == ResolvedField.Kind.JVM_METHOD) {
            field.parentView = true;
        } else if (field.kind == ResolvedField.Kind.JAVA_FIELD || field.kind == ResolvedField.Kind.ERROR_MESSAGE) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_PARENT, Phase.TYPE,
                    "'" + symbol.name + "." + access.name + "' is a field of " + owner.superclass.getSimpleName()
                            + "; the parent view only calls methods",
                    module.uri, access.span)
                    .withHint("Read the field on a value of the class, outside the class body."));
            return errorField(access);
        }
        return field;
    }

    private ResolvedField javaMember(JavaType javaType, Expr.FieldAccess access, boolean staticContext) {
        return javaMember(javaType, access, staticContext, false);
    }

    /** The methods a receiver offers: public ones, plus protected ones through the parent view. */
    private static List<Method> methodCandidates(Class<?> clazz, boolean parentView) {
        return parentView ? JvmMetadata.inheritable(clazz) : List.of(clazz.getMethods());
    }

    private ResolvedField javaMember(JavaType javaType, Expr.FieldAccess access, boolean staticContext,
            boolean parentView) {
        Class<?> clazz = javaType.clazz;
        if (!staticContext && Throwable.class.isAssignableFrom(clazz) && access.name.equals("message")) {
            // An Error always has a message; Java's getMessage() may return null.
            ResolvedField field = new ResolvedField();
            field.kind = ResolvedField.Kind.ERROR_MESSAGE;
            field.type = SprigError.class.isAssignableFrom(clazz)
                    ? NativeType.STRING : NullableType.of(NativeType.STRING);
            return field;
        }
        try {
            java.lang.reflect.Field javaField = clazz.getField(access.name);
            if (staticContext == java.lang.reflect.Modifier.isStatic(javaField.getModifiers())) {
                JvmMetadata.Support fieldSupport = JvmMetadata.support(javaField);
                if (!fieldSupport.usable()) {
                    diagnostics.add(Diagnostic.error(Codes.JVM_MEMBER, Phase.JVM,
                            "Java field '" + access.name + "' is unsupported: "
                                    + fieldSupport.unusableReason(),
                            module.uri, access.span)
                            .withData(Map.of("interopReasonCodes", fieldSupport.reasonCodes(),
                                    "interopLevel", fieldSupport.level())));
                    return errorField(access);
                }
                ResolvedField field = new ResolvedField();
                field.kind = ResolvedField.Kind.JAVA_FIELD;
                JvmMember member = new JvmMember();
                member.field = javaField;
                member.owner = clazz;
                member.name = access.name;
                Map<TypeVariable<?>, Type> fieldBindings = JavaTypes
                        .hierarchyBindings(javaType.clazz, javaType.args)
                        .getOrDefault(javaField.getDeclaringClass(), Map.of());
                member.bindings = fieldBindings;
                member.returnType = JavaTypes.mapValue(javaField.getGenericType(), javaField.getType(), fieldBindings);
                if (member.returnType.isNullable() && JvmNullability.fieldNonNull(javaField)) {
                    member.returnType = member.returnType.nonNull(); // declared non-null by annotation
                }
                field.jvm = member;
                field.type = member.returnType;
                return field;
            }
        } catch (NoSuchFieldException ignored) {
            // Fall through to method lookup.
        }
        for (Method method : methodCandidates(clazz, parentView)) {
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
                javaOwner(clazz) + " has no "
                        + (staticContext ? "static " : "") + "member '" + access.name + "'",
                module.uri, access.span));
        return errorField(access);
    }

    private ResolvedField builtinInstance(Type receiver, Expr.FieldAccess access) {
        String id = BuiltinMembers.instanceId(receiver, access.name);
        if (id == null) {
            if (access.name.equals("toString")) {
                return builtin(access, "toString", NativeType.STRING, receiver);
            }
            Diagnostic missing = Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                    "Type " + receiver.display() + " has no method '" + access.name + "'",
                    module.uri, access.span);
            String hint = Newcomer.memberHint(receiver.display(), access.name);
            List<String> available = BuiltinMembers.instanceNames(receiver);
            if (hint == null && !available.isEmpty()) {
                hint = receiver.display() + " methods: " + String.join(", ", available) + ".";
            }
            if (hint != null) {
                missing.withHint(hint);
            }
            diagnostics.add(missing);
            return errorField(access);
        }
        return builtin(access, id, NativeType.ERROR, receiver);
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
            case "Int.compareTo", "Int32.compareTo", "Float.compareTo", "Float32.compareTo", "Decimal.compareTo",
                 "BigInt.compareTo" -> {
                // Int32 like String.compareTo, so fn(a: Int, b: Int) => a.compareTo(b) is a
                // Java Comparator; the argument is the receiver's own type, no conversion.
                checkArity(call, 1, 1, id);
                Type actual = checkExpr(first.value, receiver);
                requireAssignable(receiver, actual, first.value.span, Codes.NUM_MIXED, "compareTo argument");
                return NativeType.INT32;
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
            case "String.lastIndexOf" -> {
                checkArity(call, 1, 1, id);
                requireString(call.args.get(0));
                return NativeType.INT;
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
            case "String.compareTo" -> {
                // Int32, like the int a Java Comparator returns, so
                // fn(a: String, b: String) => a.compareTo(b) is a comparator.
                checkArity(call, 1, 1, id);
                requireString(call.args.get(0));
                return NativeType.INT32;
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
                    requireCallableEffects(fn, call.args.get(0).value, call.span);
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
                    requireCallableEffects(fn, call.args.get(0).value, call.span);
                }
                return new ListType(list.element, false);
            }
            case "List.forEach" -> {
                checkArity(call, 1, 1, id);
                ListType list = (ListType) receiver;
                FunctionType fn = requireFunctionArg(call.args.get(0), 1,
                        new FunctionType(List.of(list.element), NativeType.UNIT));
                if (fn != null) {
                    requireAssignable(list.element, fn.params.get(0), call.args.get(0).value.span,
                            Codes.TYPE_MISMATCH, "forEach argument");
                    requireCallableEffects(fn, call.args.get(0).value, call.span);
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
                    boolean sortable = element == NativeType.BOOL || isComparableType(element);
                    if (!sortable && element != NativeType.ERROR) {
                        diagnostics.add(Diagnostic.error(Codes.TYPE_OPERAND, Phase.TYPE,
                                "sort requires Bool or Comparable elements (Int, Int32, Float, Float32,"
                                        + " Decimal, BigInt, String or a Comparable type parameter)",
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
        return requireFunctionArg(arg, arity, null);
    }

    /** {@code expected} types a bare {@code print} reference; a lambda keeps its own types. */
    private FunctionType requireFunctionArg(Expr.Arg arg, int arity, FunctionType expected) {
        Type type = checkExpr(arg.value, isPrintReference(arg.value) ? expected : null);
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
        return checkJvmConstructor(javaType, call, List.of());
    }

    private Type checkJvmConstructor(JavaType javaType, Expr.Call call, List<Type> classArgs) {
        if (call.hasNamedArgs()) {
            diagnostics.add(Diagnostic.error(Codes.CALL_POSITIONAL_REQUIRED, Phase.TYPE,
                    "Java constructors take positional arguments", module.uri, call.span));
        }
        List<Type> argTypes = new ArrayList<>();
        for (Expr.Arg arg : call.args) {
            argTypes.add(requireValue(checkExpr(arg.value, null), arg.value, "a Java argument"));
        }
        Map<TypeVariable<?>, Type> receiverBindings = JavaTypes
                .hierarchyBindings(javaType.clazz, javaType.args)
                .getOrDefault(javaType.clazz, Map.of());
        boolean bound = !receiverBindings.isEmpty();
        Constructor<?> best = null;
        int bestScore = -1;
        boolean ambiguous = false;
        boolean bestExpanded = false;
        // Fixed-arity candidates first; the varargs expanded form only when none applies.
        for (int pass = 0; pass < 2 && best == null; pass++) {
            boolean expand = pass == 1;
            for (Constructor<?> constructor : javaType.clazz.getConstructors()) {
                if (JvmMetadata.unsupportedReason(constructor) != null) continue;
                if (constructor.getTypeParameters().length > 0) continue; // no Java inference
                int score = scoreExecutable(constructor, argTypes, call.args, receiverBindings, bound, expand);
                if (score < 0) {
                    continue;
                }
                if (score > bestScore) {
                    best = constructor;
                    bestScore = score;
                    bestExpanded = expand;
                    ambiguous = false;
                } else if (score == bestScore) {
                    ambiguous = true;
                }
            }
        }
        if (best == null) {
            List<Constructor<?>> candidates = List.of(javaType.clazz.getConstructors());
            if (reportNullableJavaArgument(javaType.clazz.getSimpleName(), candidates, argTypes, call)
                    || reportThrowingCallableArgument(javaType.clazz.getSimpleName(), candidates, argTypes, call)) {
                return NativeType.ERROR;
            }
            Diagnostic diagnostic = Diagnostic.error(Codes.JVM_MEMBER, Phase.JVM,
                    "No constructor of " + javaType.clazz.getSimpleName() + " matches "
                            + argTypes.size() + " argument(s)", module.uri, call.span);
            Map<String, Object> data = jvmDiagnosticData(javaType.clazz, "<init>", argTypes, call, candidates);
            if (!classArgs.isEmpty()) {
                data.put("explicitTypeArguments", classArgs.stream().map(Type::display).toList());
            }
            diagnostics.add(diagnostic.withData(data));
            return NativeType.ERROR;
        }
        if (ambiguous) {
            diagnostics.add(Diagnostic.error(Codes.JVM_AMBIGUOUS, Phase.JVM,
                    "Ambiguous constructor overload for " + javaType.clazz.getSimpleName(),
                    module.uri, call.span)
                    .withData(jvmDiagnosticData(javaType.clazz, "<init>", argTypes, call,
                            List.of(javaType.clazz.getConstructors()))));
        }
        ResolvedCall resolved = resolvedCall(call, ResolvedCall.Kind.JVM_CTOR, javaType);
        resolved.typeArgs = classArgs;
        JvmMember member = new JvmMember();
        member.owner = javaType.clazz;
        member.name = "<init>";
        member.executable = best;
        member.bindings = receiverBindings;
        member.varargsExpanded = bestExpanded;
        member.paramTypes = new ArrayList<>();
        for (int i = 0; i < best.getParameterCount(); i++) {
            member.paramTypes.add(bound
                    ? JavaTypes.mapFormal(best.getGenericParameterTypes()[i], best.getParameterTypes()[i], receiverBindings)
                    : JavaTypes.mapFormal(best.getGenericParameterTypes()[i], best.getParameterTypes()[i]));
        }
        member.returnType = javaType;
        resolved.jvm = member;
        requireHandled(jvmExceptions(best.getExceptionTypes()), call.span);
        return javaType;
    }

    private Type checkJvmMethod(ResolvedField field, Expr.Call call) {
        return checkJvmMethod(field, call, null);
    }

    /**
     * {@code explicitMethodArgs} is {@code null} for an ordinary call and a
     * list for {@code receiver.method[Type](...)}. A method with its own type
     * variables is only a candidate when those arguments are written; the
     * profile never infers Java method generics.
     */
    private Type checkJvmMethod(ResolvedField field, Expr.Call call, List<Type> explicitMethodArgs) {
        Type receiverType = field.receiverType;
        JavaType receiver = (JavaType) receiverType;
        Class<?> clazz = receiver.clazz;
        if (call.hasNamedArgs()) {
            diagnostics.add(Diagnostic.error(Codes.CALL_POSITIONAL_REQUIRED, Phase.TYPE,
                    "Java methods take positional arguments", module.uri, call.span));
        }
        List<Type> argTypes = new ArrayList<>();
        for (Expr.Arg arg : call.args) {
            argTypes.add(requireValue(checkExpr(arg.value, null), arg.value, "a Java argument"));
        }
        Map<Class<?>, Map<TypeVariable<?>, Type>> hierarchy =
                JavaTypes.hierarchyBindings(receiver.clazz, receiver.args);
        boolean receiverBound = !hierarchy.getOrDefault(receiver.clazz, Map.of()).isEmpty();
        Method best = null;
        int bestScore = -1;
        boolean ambiguous = false;
        boolean bestExpanded = false;
        Map<TypeVariable<?>, Type> bestBindings = Map.of();
        List<Type> bestMethodArgs = null;
        // Fixed-arity candidates first; the varargs expanded form only when none applies.
        for (int pass = 0; pass < 2 && best == null; pass++) {
            boolean expand = pass == 1;
            for (Method method : methodCandidates(clazz, field.parentView)) {
                if (!method.getName().equals(field.jvm.name)) {
                    continue;
                }
                boolean isStatic = java.lang.reflect.Modifier.isStatic(method.getModifiers());
                if (isStaticJvmReceiver(call) != isStatic) {
                    continue;
                }
                if (JvmMetadata.unsupportedReason(method) != null) continue;
                int methodVariables = method.getTypeParameters().length;
                List<Type> methodArgs = explicitMethodArgs;
                if (methodVariables > 0) {
                    if (methodArgs == null) {
                        // Written arguments decide; without them the type variables
                        // are inferred only when the arguments fix every one of them
                        // exactly, and the bounds are checked as for written ones.
                        methodArgs = inferMethodTypeArguments(method, argTypes,
                                hierarchy.getOrDefault(method.getDeclaringClass(), Map.of()), expand);
                        if (methodArgs == null) continue;
                    }
                    if (methodArgs.size() != methodVariables) continue;
                    if (!typeArgumentsSatisfyBounds(method, methodArgs)) continue;
                }
                Map<TypeVariable<?>, Type> bindings = new java.util.IdentityHashMap<>(
                        hierarchy.getOrDefault(method.getDeclaringClass(), Map.of()));
                if (methodVariables > 0) {
                    TypeVariable<?>[] variables = method.getTypeParameters();
                    for (int i = 0; i < variables.length; i++) {
                        bindings.put(variables[i], methodArgs.get(i));
                    }
                }
                boolean bound = receiverBound || methodVariables > 0;
                int score = scoreExecutable(method, argTypes, call.args, bindings, bound, expand);
                if (score < 0) {
                    continue;
                }
                if (score > bestScore) {
                    best = method;
                    bestScore = score;
                    bestBindings = bindings;
                    bestExpanded = expand;
                    bestMethodArgs = methodArgs;
                    ambiguous = false;
                } else if (score == bestScore && !sameSignature(best, method)) {
                    ambiguous = true;
                }
            }
        }
        if (best == null) {
            List<Method> candidates = new ArrayList<>();
            for (Method method : methodCandidates(clazz, field.parentView)) {
                if (!method.getName().equals(field.jvm.name)
                        || java.lang.reflect.Modifier.isStatic(method.getModifiers()) != isStaticJvmReceiver(call)) {
                    continue;
                }
                candidates.add(method);
            }
            if (reportNullableJavaArgument(clazz.getSimpleName() + "." + field.jvm.name,
                    candidates, argTypes, call)
                    || reportThrowingCallableArgument(clazz.getSimpleName() + "." + field.jvm.name,
                    candidates, argTypes, call)) {
                return NativeType.ERROR;
            }
            Diagnostic diagnostic = Diagnostic.error(Codes.JVM_MEMBER, Phase.JVM,
                    javaOwner(clazz) + " has no method '" + field.jvm.name
                            + "' matching " + argTypes.size() + " argument(s)",
                    module.uri, call.span);
            Map<String, Object> data = jvmDiagnosticData(clazz, field.jvm.name, argTypes, call, candidates, hierarchy);
            if (explicitMethodArgs != null) {
                data.put("explicitTypeArguments", explicitMethodArgs.stream().map(Type::display).toList());
            }
            diagnostics.add(diagnostic.withData(data));
            return NativeType.ERROR;
        }
        if (ambiguous) {
            diagnostics.add(Diagnostic.error(Codes.JVM_AMBIGUOUS, Phase.JVM,
                    "Ambiguous overload for " + clazz.getSimpleName() + "." + best.getName(),
                    module.uri, call.span)
                    .withData(jvmDiagnosticData(clazz, best.getName(), argTypes, call,
                            methodCandidates(clazz, field.parentView).stream()
                                    .filter(m -> m.getName().equals(field.jvm.name)).toList())));
        }
        JvmMember member = new JvmMember();
        member.owner = clazz;
        member.name = best.getName();
        member.executable = best;
        member.bindings = bestBindings;
        member.varargsExpanded = bestExpanded;
        member.paramTypes = new ArrayList<>();
        for (int i = 0; i < best.getParameterCount(); i++) {
            member.paramTypes.add(JavaTypes.mapFormal(best.getGenericParameterTypes()[i],
                    best.getParameterTypes()[i], bestBindings));
        }
        if (field.parentView && java.lang.reflect.Modifier.isAbstract(best.getModifiers())) {
            diagnostics.add(Diagnostic.error(Codes.CONFORM_PARENT, Phase.TYPE,
                    javaOwner(clazz) + "." + best.getName()
                            + " is abstract; there is no inherited implementation to call",
                    module.uri, call.span)
                    .withHint("Implement the behaviour in this class's own method instead of calling the parent view."));
        }
        member.superCall = field.parentView;
        member.returnType = JavaTypes.mapValue(best.getGenericReturnType(), best.getReturnType(), bestBindings);
        // toString() returns a representation by Object's contract, never null,
        // so "at " + date.toString() needs no check; Kotlin reads it the same way.
        if (best.getName().equals("toString") && best.getParameterCount() == 0
                && best.getReturnType() == String.class) {
            member.returnType = NativeType.STRING;
        }
        if (member.returnType.isNullable() && JvmNullability.returnsNonNull(best)) {
            // Declared non-null by a run-time visible annotation, or by a
            // NullMarked-style default on the class or package.
            member.returnType = member.returnType.nonNull();
        }
        field.jvm = member;
        if (call.resolved != null && bestMethodArgs != null) {
            call.resolved.typeArgs = bestMethodArgs;
        }
        requireHandled(jvmExceptions(best.getExceptionTypes()), call.span);
        return member.returnType;
    }

    /**
     * The type arguments of a generic Java method, read off its arguments: a
     * formal {@code T} takes the argument's type, a formal {@code List<T>} takes
     * the element type of a Java argument such as {@code ArrayList[String]}, and
     * a {@code T...} tail takes the type its arguments share. Every type variable
     * must be fixed, each exactly once or to the same type; a lambda, a Java
     * callable, {@code null}, a nullable value, a Sprig collection or a raw Java
     * value says nothing, and a variable that only appears in the result cannot
     * be inferred (the expected type is never used). Returns null when the
     * arguments do not decide; the call then needs written arguments.
     */
    private static List<Type> inferMethodTypeArguments(Executable executable, List<Type> args,
                                                       Map<TypeVariable<?>, Type> classBindings, boolean expand) {
        Class<?>[] raw = executable.getParameterTypes();
        java.lang.reflect.Type[] generic = executable.getGenericParameterTypes();
        if (expand && (!executable.isVarArgs() || raw.length == 0)) {
            return null; // the expanded form is only for a varargs candidate
        }
        int fixed = expand ? raw.length - 1 : raw.length;
        if (expand ? args.size() < fixed : args.size() != raw.length) {
            return null;
        }
        Map<TypeVariable<?>, Type> inferred = new java.util.IdentityHashMap<>();
        for (int i = 0; i < fixed; i++) {
            if (JavaTypes.isCallableClass(raw[i]) || JavaTypes.functionalFormal(generic[i], raw[i])) {
                continue; // callables are checked, never used for inference
            }
            if (!unifyJavaFormal(generic[i], args.get(i), inferred, classBindings)) {
                return null;
            }
        }
        if (expand) {
            java.lang.reflect.Type element = JavaTypes.varargsElementType(executable);
            for (int i = fixed; i < args.size(); i++) {
                if (!unifyJavaFormal(element, args.get(i), inferred, classBindings)) {
                    return null;
                }
            }
        }
        List<Type> out = new ArrayList<>();
        for (TypeVariable<?> variable : executable.getTypeParameters()) {
            Type type = inferred.get(variable);
            if (type == null) {
                return null;
            }
            out.add(type);
        }
        return out;
    }

    private static boolean unifyJavaFormal(java.lang.reflect.Type formal, Type arg,
                                           Map<TypeVariable<?>, Type> inferred,
                                           Map<TypeVariable<?>, Type> classBindings) {
        if (arg == null || arg == NativeType.ERROR) {
            return true; // its own error is reported; it says nothing here
        }
        if (formal instanceof TypeVariable<?> variable) {
            if (classBindings.containsKey(variable)) {
                return true; // the receiver's variable is already known
            }
            if (arg.isNullable() || !inferableJavaArgument(arg)) {
                return false;
            }
            Type previous = inferred.putIfAbsent(variable, arg);
            return previous == null || previous.equals(arg);
        }
        if (formal instanceof java.lang.reflect.ParameterizedType applied) {
            Class<?> rawFormal = JavaTypes.rawClass(applied);
            if (rawFormal == null || !mentionsTypeVariable(applied)) {
                return true; // nothing of the method's to learn; the argument check decides
            }
            if (arg.isNullable() || !(arg instanceof JavaType javaArg) || !rawFormal.isAssignableFrom(javaArg.clazz)) {
                return false;
            }
            Map<TypeVariable<?>, Type> argBindings =
                    JavaTypes.hierarchyBindings(javaArg.clazz, javaArg.args).get(rawFormal);
            TypeVariable<?>[] formalVariables = rawFormal.getTypeParameters();
            java.lang.reflect.Type[] formalArgs = applied.getActualTypeArguments();
            for (int i = 0; i < formalVariables.length && i < formalArgs.length; i++) {
                java.lang.reflect.Type formalArg = formalArgs[i];
                if (formalArg instanceof java.lang.reflect.WildcardType wildcard) {
                    java.lang.reflect.Type[] lower = wildcard.getLowerBounds();
                    java.lang.reflect.Type[] upper = wildcard.getUpperBounds();
                    formalArg = lower.length == 1 ? lower[0] : upper.length == 1 ? upper[0] : null;
                    if (formalArg == null || formalArg == Object.class) continue;
                }
                if (formalArg instanceof Class<?>) continue;
                Type bound = argBindings == null ? null : argBindings.get(formalVariables[i]);
                if (bound == null || bound instanceof sprig.compiler.types.JavaWildcardType) {
                    return false; // a raw or wildcard argument fixes nothing
                }
                if (!unifyJavaFormal(formalArg, bound, inferred, classBindings)) {
                    return false;
                }
            }
            return true;
        }
        return true; // a class formal is checked by the scoring, not inferred from
    }

    private static boolean mentionsTypeVariable(java.lang.reflect.Type type) {
        if (type instanceof TypeVariable<?>) return true;
        if (type instanceof java.lang.reflect.ParameterizedType applied) {
            for (java.lang.reflect.Type argument : applied.getActualTypeArguments()) {
                if (mentionsTypeVariable(argument)) return true;
            }
        }
        if (type instanceof java.lang.reflect.WildcardType wildcard) {
            for (java.lang.reflect.Type bound : wildcard.getLowerBounds()) if (mentionsTypeVariable(bound)) return true;
            for (java.lang.reflect.Type bound : wildcard.getUpperBounds()) if (mentionsTypeVariable(bound)) return true;
        }
        return false;
    }

    /** Argument types that can stand as a Java type argument: scalars, String and nominal reference types. */
    private static boolean inferableJavaArgument(Type type) {
        if (type == NativeType.INT || type == NativeType.INT32 || type == NativeType.FLOAT
                || type == NativeType.FLOAT32 || type == NativeType.BOOL || type == NativeType.STRING
                || type == NativeType.DECIMAL || type == NativeType.BIGINT) {
            return true;
        }
        if (type instanceof JavaType javaType) {
            for (Type argument : javaType.args) {
                if (argument instanceof sprig.compiler.types.JavaWildcardType || !inferableJavaArgument(argument)) {
                    return false;
                }
            }
            return !javaType.clazz.isArray();
        }
        return type instanceof ClassType || type instanceof EnumType || type instanceof VariantType;
    }

    /**
     * Whether written or inferred type arguments satisfy every bound of the
     * method's type parameters: a class or interface bound by assignability of
     * the boxed class, and a parameterized bound such as
     * {@code T extends Comparable<? super T>} by the argument's own view of that
     * supertype ({@code String} is {@code Comparable<String>}).
     */
    private static boolean typeArgumentsSatisfyBounds(Executable executable, List<Type> arguments) {
        TypeVariable<?>[] variables = executable.getTypeParameters();
        if (arguments.size() != variables.length) {
            return false;
        }
        Map<TypeVariable<?>, Type> byVariable = new java.util.IdentityHashMap<>();
        for (int i = 0; i < variables.length; i++) {
            byVariable.put(variables[i], arguments.get(i));
        }
        for (int i = 0; i < variables.length; i++) {
            Type argument = arguments.get(i);
            Class<?> erased = JavaTypes.boxedFor(argument);
            for (java.lang.reflect.Type bound : variables[i].getBounds()) {
                if (bound == Object.class) continue;
                if (erased == null || erased == Object.class) return false;
                if (bound instanceof Class<?> clazz) {
                    if (!clazz.isAssignableFrom(erased)) return false;
                    continue;
                }
                if (!(bound instanceof java.lang.reflect.ParameterizedType applied)) return false;
                Class<?> rawBound = JavaTypes.rawClass(applied);
                if (rawBound == null || !rawBound.isAssignableFrom(erased)) return false;
                Map<TypeVariable<?>, Type> view = JavaTypes.hierarchyBindings(erased,
                        argument instanceof JavaType javaType ? javaType.args : List.of()).get(rawBound);
                TypeVariable<?>[] boundVariables = rawBound.getTypeParameters();
                java.lang.reflect.Type[] boundArgs = applied.getActualTypeArguments();
                for (int k = 0; k < boundVariables.length && k < boundArgs.length; k++) {
                    java.lang.reflect.Type boundArg = boundArgs[k];
                    boolean lower = false;
                    if (boundArg instanceof java.lang.reflect.WildcardType wildcard) {
                        if (wildcard.getLowerBounds().length == 1) {
                            boundArg = wildcard.getLowerBounds()[0];
                            lower = true;
                        } else if (wildcard.getUpperBounds().length == 1) {
                            boundArg = wildcard.getUpperBounds()[0];
                        } else continue;
                        if (boundArg == Object.class) continue;
                    }
                    Type required = boundArg instanceof TypeVariable<?> variable ? byVariable.get(variable)
                            : boundArg instanceof Class<?> clazz ? JavaTypes.map(clazz) : null;
                    Type actual = view == null ? null : view.get(boundVariables[k]);
                    if (required == null || actual == null) return false;
                    // Comparable<? super T> with T = Long: Long is Comparable<Long>, and a
                    // supertype view would do; Comparable<T> needs the same type.
                    boolean fits = actual.equals(required)
                            || JavaTypes.boxedFor(actual) == JavaTypes.boxedFor(required)
                            || (lower && Semantics.isAssignable(actual, required));
                    if (!fits) return false;
                }
            }
        }
        return true;
    }

    /**
     * Scoring for a bound receiver or an explicit generic method. The formal
     * side is the mapped concrete type; a shape outside the concrete profile
     * falls back to the raw class inside {@link JavaTypes#mapFormal}.
     */
    private static int scoreBoundCandidate(Executable executable, List<Type> args,
                                           List<Expr.Arg> writtenArgs,
                                           Map<TypeVariable<?>, Type> bindings) {
        Class<?>[] raw = executable.getParameterTypes();
        if (raw.length != args.size()) return -1;
        int score = 0;
        for (int i = 0; i < raw.length; i++) {
            Type arg = args.get(i);
            if (arg == NativeType.ERROR) continue;
            int next = scoreBoundFormal(executable, i, arg, writtenArgs.get(i).value, bindings);
            if (next < 0) return -1;
            score += next;
        }
        return score;
    }

    private static int scoreBoundFormal(Executable executable, int index, Type arg, Expr expr,
                                        Map<TypeVariable<?>, Type> bindings) {
        Class<?>[] raw = executable.getParameterTypes();
        java.lang.reflect.Type[] generic = executable.getGenericParameterTypes();
        if (JavaTypes.isCallableClass(raw[index])) {
            // Fn0<T> with T bound by the receiver or the method's type arguments
            // takes the function type those bindings give.
            Type bound = JavaTypes.mapFormal(generic[index], raw[index], bindings);
            FunctionType expected = bound instanceof FunctionType boundFn ? boundFn : JavaTypes.callable(generic[index]);
            if (arg.isNullable() || expected == null || !expected.equals(arg)) return -1;
            return 4;
        }
        if (arg.nonNull() instanceof FunctionType && JavaTypes.functionalMethod(raw[index]) != null) {
            return scoreJavaCallable(generic[index], raw[index], arg, bindings);
        }
        if (JavaTypes.capturedWrite(generic[index], bindings)) {
            // add(E) or addAll(Collection<? extends E>) on a List<? extends Number>
            // receiver: nothing can be passed in, as Java's capture rule says.
            return -1;
        }
        Type formal = JavaTypes.mapFormal(generic[index], raw[index], bindings);
        return scoreBoundArgument(formal, arg, expr);
    }

    /**
     * A Sprig function value against a Java functional-interface formal.
     * Parameters match exactly, as for Fn0..Fn3; a void method accepts any
     * result, as Java does; a value with a throws clause never crosses,
     * because Java cannot see the clause.
     */
    private static int scoreJavaCallable(java.lang.reflect.Type generic, Class<?> raw, Type arg,
                                         Map<TypeVariable<?>, Type> bindings) {
        if (arg.isNullable() || !(arg instanceof FunctionType actual)) return -1;
        FunctionType expected = JavaTypes.javaCallable(generic, raw, bindings);
        if (expected == null || actual.throwsAny() || !expected.params.equals(actual.params)) return -1;
        if (expected.result != NativeType.UNIT && !Semantics.isAssignable(expected.result, actual.result)
                && !narrowsToInt32(expected.result, actual.result)) return -1;
        return 4;
    }

    /**
     * Whether an {@code Int} crosses into an {@code int}/{@code Integer} Java
     * slot: a comparator written in Sprig returns {@code Int}; the adapter
     * narrows the result with a run-time range check, as a parameter does.
     */
    public static boolean narrowsToInt32(Type expected, Type actual) {
        return expected == NativeType.INT32 && actual == NativeType.INT;
    }

    /**
     * Score of one Java candidate. Without {@code expand} the written
     * arguments must match the parameters one to one, which also covers an
     * opaque array passed to a varargs parameter. With it, a varargs
     * candidate takes the trailing arguments one by one against its element
     * type, the form Java tries only when no fixed-arity candidate applies.
     */
    private static int scoreExecutable(Executable executable, List<Type> args, List<Expr.Arg> writtenArgs,
                                       Map<TypeVariable<?>, Type> bindings, boolean bound, boolean expand) {
        args = acceptNullableParameters(executable, args, expand);
        if (!expand) {
            return bound ? scoreBoundCandidate(executable, args, writtenArgs, bindings)
                    : scoreCandidate(executable, args, writtenArgs);
        }
        Class<?> element = JavaTypes.varargsElement(executable);
        int fixed = executable.getParameterCount() - 1;
        if (element == null || args.size() < fixed) return -1;
        int score = 0;
        for (int i = 0; i < fixed; i++) {
            Type arg = args.get(i);
            if (arg == NativeType.ERROR) continue;
            int next = bound ? scoreBoundFormal(executable, i, arg, writtenArgs.get(i).value, bindings)
                    : scoreJvmArgument(executable, i, arg, writtenArgs.get(i).value);
            if (next < 0) return -1;
            score += next;
        }
        java.lang.reflect.Type elementType = JavaTypes.varargsElementType(executable);
        Type formal = bound ? JavaTypes.mapFormal(elementType, element, bindings)
                : JavaTypes.mapFormal(elementType, element);
        for (int i = fixed; i < args.size(); i++) {
            Type arg = args.get(i);
            if (arg == NativeType.ERROR) continue;
            int next = elementType == element && !bound
                    ? scoreArgument(element, arg, writtenArgs.get(i).value)
                    : scoreBoundArgument(formal, arg, writtenArgs.get(i).value);
            if (next < 0) return -1;
            score += next;
        }
        return score;
    }

    private static int scoreBoundArgument(Type formal, Type arg, Expr expr) {
        if (arg.isNullable()) {
            return -1;
        }
        Type base = arg.nonNull();
        Type target = formal instanceof NullableType nullable ? nullable.inner : formal;
        if (target == null) {
            return -1;
        }
        if (target.equals(base)) {
            return 3;
        }
        if (target instanceof JavaType javaTarget && base instanceof JavaType javaSource) {
            if (!JavaTypes.javaTypeCompatible(javaTarget, javaSource)) {
                return -1;
            }
            if (javaTarget.args.isEmpty()) {
                return 1; // raw target keeps the erased-boundary weight
            }
            return javaTarget.clazz.equals(javaSource.clazz) ? 3 : 2;
        }
        if (target instanceof JavaType javaTarget && !javaTarget.args.isEmpty()) {
            // Concrete generic target with a native scalar or Sprig collection
            // source: project the source image, never accept by raw class.
            return JavaTypes.javaArgumentCompatible(javaTarget, base) ? 2 : -1;
        }
        if (target instanceof ListType listTarget && base instanceof ListType listSource) {
            return listTarget.equals(listSource) ? 3 : -1;
        }
        if (target instanceof MapType mapTarget && base instanceof MapType mapSource) {
            return mapTarget.equals(mapSource) ? 3 : -1;
        }
        Class<?> preferred = boundRawClass(target);
        if (preferred == null) {
            return -1;
        }
        return scoreArgument(preferred, arg, expr);
    }

    private static Class<?> boundRawClass(Type type) {
        return JavaTypes.preferredRaw(type);
    }

    /** Whether a Java call names the class (static) rather than a value receiver. */
    private static boolean isStaticJvmReceiver(Expr.Call call) {
        Expr callee = call.callee instanceof Expr.Subscript subscript ? subscript.base : call.callee;
        return callee instanceof Expr.FieldAccess access
                && access.receiver instanceof Expr.Name name
                && name.symbol != null && name.symbol.kind == Symbol.Kind.JAVA_TYPE;
    }

    private static boolean sameSignature(Method a, Method b) {
        return a != null && b != null && java.util.Arrays.equals(a.getParameterTypes(), b.getParameterTypes());
    }

    private static int scoreCandidate(java.lang.reflect.Executable executable, List<Type> args,
                                      List<Expr.Arg> writtenArgs) {
        Class<?>[] raw = executable.getParameterTypes();
        if (raw.length != args.size()) return -1;
        int score = 0;
        for (int i = 0; i < raw.length; i++) {
            Type arg = args.get(i);
            if (arg == NativeType.ERROR) continue;
            int next = scoreJvmArgument(executable, i, arg, writtenArgs.get(i).value);
            if (next < 0) return -1;
            score += next;
        }
        return score;
    }

    /**
     * A {@code T?} or {@code null} argument for a parameter annotated nullable
     * scores as its non-null type when the value crosses without conversion
     * (an {@code Int?} is already a {@code Long}; one that would need
     * narrowing or widening stays rejected). The trailing arguments of an
     * expanded varargs call are left alone.
     */
    private static List<Type> acceptNullableParameters(Executable executable, List<Type> args, boolean expand) {
        Class<?>[] params = executable.getParameterTypes();
        int fixed = expand ? params.length - 1 : params.length;
        List<Type> out = null;
        for (int i = 0; i < args.size() && i < fixed; i++) {
            Type arg = args.get(i);
            boolean absent = arg == NativeType.NULL;
            if ((!arg.isNullable() && !absent) || params[i].isPrimitive()
                    || !JvmNullability.parameterNullable(executable, i)) {
                continue;
            }
            Type accepted;
            if (absent) {
                accepted = JavaTypes.mapFormal(executable.getGenericParameterTypes()[i], params[i]);
            } else {
                Class<?> image = JavaTypes.boxedFor(arg.nonNull());
                if (image == null || !params[i].isAssignableFrom(image)) {
                    continue;
                }
                accepted = arg.nonNull();
            }
            if (out == null) {
                out = new ArrayList<>(args);
            }
            out.set(i, accepted);
        }
        return out == null ? args : out;
    }

    /**
     * One Java formal, using the concrete generic type when the signature
     * carries one. A parameterized formal never silently accepts arguments by
     * raw class alone ({@code ArrayList<Integer>} is not {@code List<String>}).
     */
    private static int scoreJvmArgument(java.lang.reflect.Executable executable, int index,
                                        Type arg, Expr expr) {
        Class<?>[] raw = executable.getParameterTypes();
        java.lang.reflect.Type[] generic = executable.getGenericParameterTypes();
        if (JavaTypes.isCallableClass(raw[index])) {
            FunctionType expected = JavaTypes.callable(generic[index]);
            if (arg.isNullable() || expected == null || !expected.equals(arg)) return -1;
            return 4;
        }
        if (arg.nonNull() instanceof FunctionType && JavaTypes.functionalMethod(raw[index]) != null) {
            return scoreJavaCallable(generic[index], raw[index], arg, JavaTypes.NO_BINDINGS);
        }
        if (generic[index] != raw[index]) {
            return scoreBoundArgument(JavaTypes.mapFormal(generic[index], raw[index]), arg, expr);
        }
        return scoreArgument(raw[index], arg, expr);
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
        // Java formals carry no nullability contract, including Object.
        if (arg.isNullable()) {
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
            if (param == int.class || param == Integer.class) {
                // Checked narrowing at the boundary: the generator emits
                // NumericOps.toInt32Exact, which fails outside the Int32 range.
                // The lowest weight keeps an exact long or a widening Object
                // formal ahead, as Java's own phases order them for a long.
                return 0;
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
            if (param == char.class || param == Character.class) {
                return expr instanceof Expr.StringLit literal && literal.value.length() == 1 ? 2 : -1;
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
    private boolean reportNullableJavaArgument(String memberLabel, List<? extends Executable> candidates,
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
        for (Executable candidate : candidates) {
            Class<?>[] params = candidate.getParameterTypes();
            if (params.length == argTypes.size()
                    && scoreCandidate(candidate, projected, call.args) >= 0) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_NULLABLE, Phase.TYPE,
                        "Nullable value is not accepted by Java parameter " + (nullableIndex + 1)
                                + " of " + memberLabel + "; Java parameters are treated as non-null",
                        module.uri, call.args.get(nullableIndex).value.span)
                        .withTypes(JavaTypes.mapFormal(candidate.getGenericParameterTypes()[nullableIndex], params[nullableIndex]).display(),
                                argTypes.get(nullableIndex).display())
                        .withHint("Check for null first (if x != null), or handle the absent case in Sprig.")
                        .withData(jvmDiagnosticData(candidate.getDeclaringClass(), memberLabel,
                                argTypes, call, candidates)));
                return true;
            }
        }
        return false;
    }

    /**
     * A Sprig function value with a throws clause never crosses into Java,
     * because Java cannot see the clause: say so instead of "no match" when
     * a candidate has a callable formal at that position.
     */
    private boolean reportThrowingCallableArgument(String memberLabel, List<? extends Executable> candidates,
                                                   List<Type> argTypes, Expr.Call call) {
        for (int i = 0; i < argTypes.size(); i++) {
            if (!(argTypes.get(i) instanceof FunctionType actual) || !actual.throwsAny()) {
                continue;
            }
            for (Executable candidate : candidates) {
                Class<?>[] params = candidate.getParameterTypes();
                if (i < params.length && (JavaTypes.isCallableClass(params[i])
                        || JavaTypes.functionalMethod(params[i]) != null)) {
                    diagnostics.add(Diagnostic.error(Codes.TYPE_CALLABLE_THROWS, Phase.TYPE,
                            "A function value that may throw Error cannot be passed to Java parameter "
                                    + (i + 1) + " of " + memberLabel + "; Java cannot see the throws clause",
                            module.uri, call.args.get(i).value.span)
                            .withTypes(actual.withoutThrows().display(), actual.display())
                            .withHint("Handle the error inside a named function and pass a lambda that calls it.")
                            .withData(jvmDiagnosticData(candidate.getDeclaringClass(), memberLabel,
                                    argTypes, call, candidates)));
                    return true;
                }
            }
        }
        return false;
    }

    private static Map<String, Object> jvmDiagnosticData(Class<?> owner, String member,
            List<Type> argumentTypes, Expr.Call call, List<? extends Executable> candidates) {
        return jvmDiagnosticData(owner, member, argumentTypes, call, candidates, Map.of());
    }

    private static Map<String, Object> jvmDiagnosticData(Class<?> owner, String member,
            List<Type> argumentTypes, Expr.Call call, List<? extends Executable> candidates,
            Map<Class<?>, Map<TypeVariable<?>, Type>> hierarchy) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("member", member);
        data.put("receiverType", owner.getName());
        data.put("argumentTypes", argumentTypes.stream().map(Type::display).toList());
        List<Map<String, Object>> details = new ArrayList<>();
        candidates.stream().sorted(java.util.Comparator.comparing(Executable::toGenericString))
                .forEach(candidate -> {
                    Map<String, Object> item = new LinkedHashMap<>(JvmMetadata.describe(candidate));
                    Class<?>[] params = candidate.getParameterTypes();
                    JvmMetadata.Support support = JvmMetadata.support(candidate);
                    String reason = null;
                    if (!support.usable()) {
                        reason = support.unusableReason();
                    } else if (params.length != argumentTypes.size()) {
                        reason = "wrong arity";
                    } else if (support.reasonCodes().contains("generic-bound-unsupported")) {
                        reason = "recursive or intersection bound";
                    } else if (support.reasonCodes().contains("explicit-type-arguments-required")) {
                        reason = "type arguments not inferable from the arguments; write them, as in name[Type](...)";
                    } else {
                        Map<TypeVariable<?>, Type> bindings =
                                hierarchy.getOrDefault(candidate.getDeclaringClass(), Map.of());
                        for (int i = 0; i < params.length; i++) {
                            if (argumentTypes.get(i).isNullable() && !JvmNullability.parameterNullable(candidate, i)) {
                                reason = "nullable argument " + (i + 1); break;
                            }
                            if (JavaTypes.capturedWrite(candidate.getGenericParameterTypes()[i], bindings)) {
                                reason = "argument " + (i + 1) + " would write through a '? extends' wildcard of the receiver; no type can be passed in";
                                break;
                            }
                            if (scoreJvmArgument(candidate, i, argumentTypes.get(i), call.args.get(i).value) < 0) {
                                reason = "incompatible or narrowing argument " + (i + 1); break;
                            }
                        }
                    }
                    item.put("rejectedBecause", reason);
                    details.add(item);
                });
        data.put("candidates", details);
        return data;
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
        if (!lambdaEffects.isEmpty()) {
            // Inside a lambda body a thrown type is recorded on the lambda;
            // checkLambda decides which of them the lambda's type may carry.
            List<Type> effects = lambdaEffects.peek();
            for (Type type : thrown) {
                if (type != null && type != NativeType.ERROR && !effects.contains(type)) {
                    effects.add(type);
                }
            }
            return;
        }
        recordThrown(thrown);
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
                Diagnostic unhandled = Diagnostic.error(Codes.FLOW_THROWS, Phase.FLOW,
                        "Call may throw " + name + "; declare 'throws " + name + "' or handle it with try/catch",
                        module.uri, span).withHint(throwsHint(name));
                throwsEdit(unhandled, name);
                diagnostics.add(unhandled);
            }
        }
    }

    /** Names the two repairs with the enclosing function's own header, so they can be applied as written. */
    private String throwsHint(String name) {
        String handle = "or handle it where it happens: 'try:' around the call, then 'catch problem: " + name
                + ":' with what to do instead. Sprig keeps recoverable errors explicit; there is no implicit propagation.";
        if (lambdaDepth > 0 || currentFunction == null || currentFunction.returnTypeRef == null) {
            return "Declare it on the enclosing function, " + handle;
        }
        StringBuilder header = new StringBuilder("func ").append(currentFunction.name).append('(');
        for (int i = 0; i < currentFunction.params.size(); i++) {
            Decl.Param param = currentFunction.params.get(i);
            if (i > 0) header.append(", ");
            header.append(param.name).append(": ").append(param.typeRef == null ? "?" : param.typeRef.display());
        }
        header.append(") -> ").append(currentFunction.returnTypeRef.display()).append(" throws ");
        List<String> declared = new ArrayList<>();
        for (sprig.compiler.ast.TypeRef ref : currentFunction.throwsRefs) declared.add(ref.display());
        if (!declared.contains(name)) declared.add(name);
        header.append(String.join(", ", declared)).append(':');
        return "Declare it on '" + currentFunction.name + "' by changing its header to '" + header + "', and its callers "
                + "then handle or declare it too (top-level statements need neither), " + handle;
    }

    /**
     * The edit behind the throws hint: 'throws name' after the enclosing
     * header's result type, or ', name' after its last declared type. A
     * rethrows header and a lambda have no such place.
     */
    private void throwsEdit(Diagnostic diagnostic, String name) {
        if (lambdaDepth > 0 || currentFunction == null || currentFunction.returnTypeRef == null
                || currentFunction.rethrows) {
            return;
        }
        for (sprig.compiler.ast.TypeRef ref : currentFunction.throwsRefs) {
            if (ref.display().equals(name)) return;
        }
        boolean first = currentFunction.throwsRefs.isEmpty();
        Span anchor = first ? currentFunction.returnTypeRef.span
                : currentFunction.throwsRefs.get(currentFunction.throwsRefs.size() - 1).span;
        if (anchor == null) {
            return;
        }
        diagnostic.withEdit(Span.point(anchor.endLine, anchor.endColumn), (first ? " throws " : ", ") + name,
                "declare throws " + name + " on '" + currentFunction.name + "'");
    }

    /** Unit is a function's result, never a value: report it where a value is needed. */
    private Type requireValue(Type type, Expr expr, String role) {
        if (type != NativeType.UNIT) {
            return type;
        }
        diagnostics.add(Diagnostic.error(Codes.TYPE_UNIT, Phase.TYPE,
                "Cannot use a Unit result as " + role + "; Unit is not a value", module.uri, expr.span)
                .withHint("Call the function as a statement of its own; it returns nothing to use."));
        return NativeType.ERROR;
    }

    /**
     * Note where a thrown type can go: every open try it reaches, innermost
     * first, until a catch handles it; when none does, it escapes the function.
     * The two flow checks below compare catch and throws clauses with this.
     */
    private void recordThrown(List<Type> thrown) {
        if (lambdaDepth != 0) {
            return;
        }
        for (Type type : thrown) {
            if (type == null || type == NativeType.ERROR) {
                continue;
            }
            boolean handled = false;
            var frames = caughtStack.iterator();
            var seen = tryThrown.iterator();
            while (frames.hasNext() && seen.hasNext()) {
                List<Type> frame = frames.next();
                seen.next().add(type);
                if (frame.stream().anyMatch(caught -> caught != NativeType.ERROR && Semantics.isAssignable(caught, type))) {
                    handled = true;
                    break;
                }
            }
            if (!handled && escaping != null) {
                escaping.add(type);
            }
        }
    }

    /** Checked Java exceptions follow Java's rule; Exception and Throwable also cover unchecked ones. */
    private static boolean isTrackedChecked(Type type) {
        return type instanceof JavaType javaType && Semantics.isJvmChecked(type)
                && javaType.clazz != Exception.class && javaType.clazz != Throwable.class;
    }

    /** Whether some thrown type is the given class, a subclass of it, or a superclass of it. */
    private static boolean related(Type type, List<Type> thrown) {
        for (Type candidate : thrown) {
            if (Semantics.isAssignable(type, candidate) || Semantics.isAssignable(candidate, type)) {
                return true;
            }
        }
        return false;
    }

    private void checkDeclaredThrows(Decl.Func func, List<Type> escaped) {
        for (int i = 0; i < func.throwsTypes.size(); i++) {
            Type declared = func.throwsTypes.get(i);
            if (!isTrackedChecked(declared) || related(declared, escaped)) {
                continue;
            }
            Span span = i < func.throwsRefs.size() ? func.throwsRefs.get(i).span : func.span;
            diagnostics.add(Diagnostic.error(Codes.FLOW_THROWS_UNUSED, Phase.FLOW,
                    "'" + func.name + "' declares throws " + declared.display()
                            + ", but nothing in its body can throw it", module.uri, span)
                    .withHint("Remove " + declared.display() + " from the throws clause, then remove the catch clauses this reports in callers."));
        }
    }

    /** Whether the expression is a name whose declared type, before any narrowing, is nullable. */
    private boolean declaredNullable(Expr expr) {
        if (!(expr instanceof Expr.Name name) || name.symbol == null) {
            return false;
        }
        Type declared = name.symbol.type;
        return declared != null && declared.isNullable();
    }

    private Type narrowedType(Symbol symbol) {
        if (symbol.type == null) inferGlobal(symbol);
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
        requireAssignable(target, actual, span, code, what, null);
    }

    /** As above; {@code mismatchHint} is the hint of a plain type mismatch that no more specific rule explains. */
    private void requireAssignable(Type target, Type actual, Span span, String code, String what,
                                   String mismatchHint) {
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
        if (target != null && actual != null && target.nonNull() instanceof ClassType contract && contract.decl.contract
                && actual.nonNull() instanceof ClassType given && !given.decl.contract) {
            diagnostics.add(Diagnostic.error(code, Phase.TYPE, "Type mismatch in " + what, module.uri, span)
                    .withTypes(expected, got)
                    .withHint("'" + given.decl.name + "' does not conform to the contract '" + contract.decl.name
                            + "': declare 'conform " + given.decl.name + " to " + contract.display()
                            + "' after the class, with every method of the contract matched exactly."));
            return;
        }
        if (target != null && actual != null && actual.nonNull() instanceof ClassType contract && contract.decl.contract
                && target.nonNull() instanceof ClassType wanted && !wanted.decl.contract) {
            diagnostics.add(Diagnostic.error(code, Phase.TYPE, "Type mismatch in " + what, module.uri, span)
                    .withTypes(expected, got)
                    .withHint("A value of the contract '" + contract.decl.name + "' is never converted back to the class '"
                            + wanted.decl.name + "': there is no downcast and no type test. Call the contract's methods, "
                            + "or, when the set of types is closed, declare a variant with a case per type and match on it "
                            + "(a closed set of types is a variant; an open set is a contract)."));
            return;
        }
        if (target != null && actual != null && isNumeric(target.nonNull()) && isNumeric(actual.nonNull())) {
            diagnostics.add(Diagnostic.error(Codes.NUM_CONVERSION, Phase.TYPE,
                    "Cannot implicitly convert " + actual.display() + " to " + target.display()
                            + " in " + what + "; precision or range may change",
                    module.uri, span).withTypes(target.display(), actual.display())
                    .withHint(conversionHint(target.nonNull(), actual.nonNull())));
            return;
        }
        if (target != null && actual != null
                && target.nonNull() instanceof FunctionType targetFunction
                && actual.nonNull() instanceof FunctionType actualFunction
                && targetFunction.params.equals(actualFunction.params)
                && targetFunction.result.equals(actualFunction.result)) {
            diagnostics.add(Diagnostic.error(Codes.TYPE_CALLABLE_THROWS, Phase.TYPE,
                    "A function value that may throw Error cannot be used as " + expected + " (" + what + ")",
                    module.uri, span).withTypes(expected, got)
                    .withHint("Declare the target as " + actualFunction.display()
                            + " and make the receiving function rethrows or throws Error, or handle the error inside a named function."));
            return;
        }
        Diagnostic mismatch = Diagnostic.error(code, Phase.TYPE,
                "Type mismatch in " + what, module.uri, span).withTypes(expected, got);
        String collection = collectionHint(target, actual);
        if (collection != null) {
            mismatch.withHint(collection);
        } else if (mismatchHint != null) {
            mismatch.withHint(mismatchHint);
        }
        diagnostics.add(mismatch);
    }

    /** The snapshot method between read-only and mutable versions of the same collection. */
    private static String collectionHint(Type target, Type actual) {
        if (target == null || actual == null) {
            return null;
        }
        Type want = target.nonNull();
        Type have = actual.nonNull();
        if (want instanceof ListType wantList && have instanceof ListType haveList
                && wantList.element.equals(haveList.element) && wantList.mutable != haveList.mutable) {
            return wantList.mutable
                    ? "This is a read-only List; call .toMutableList() for a copy you can change, or declare it as List."
                    : "Call .toList() for a read-only snapshot, or declare it as MutableList.";
        }
        if (want instanceof MapType wantMap && have instanceof MapType haveMap
                && wantMap.key.equals(haveMap.key) && wantMap.value.equals(haveMap.value)
                && wantMap.mutable != haveMap.mutable) {
            return wantMap.mutable
                    ? "This is a read-only Map; call .toMutableMap() for a copy you can change, or declare it as Map."
                    : "Call .toMap() for a read-only snapshot, or declare it as MutableMap.";
        }
        return null;
    }

    /** Names the explicit conversion methods that exist for this numeric pair. */
    private static String conversionHint(Type target, Type actual) {
        if (actual == NativeType.FLOAT && target == NativeType.INT) {
            return "Use toIntExact() if the value must be whole, toIntTrunc() to drop the fraction, "
                    + "or java.lang.Math.round(x) to round to the nearest Int.";
        }
        if (actual == NativeType.INT && target == NativeType.FLOAT) {
            return "Use toFloat() (fails if precision would be lost) or toFloatLossy() to round to the nearest Float.";
        }
        if (actual == NativeType.INT && target == NativeType.INT32) {
            return "Use toInt32Exact(), which fails outside the Int32 range.";
        }
        if (actual == NativeType.FLOAT && target == NativeType.FLOAT32) {
            return "Use toFloat32Exact() or toFloat32Lossy().";
        }
        if ((actual == NativeType.DECIMAL || actual == NativeType.BIGINT)
                && (target == NativeType.INT || target == NativeType.FLOAT)) {
            return "Use toIntExact(), toFloatExact() or toFloatLossy().";
        }
        return "Use an explicit exact conversion, or a method named Lossy/Trunc when intended.";
    }

    // ------------------------------------------------------------------
    // Definite return / exit analysis
    // ------------------------------------------------------------------

    private enum Completion { NORMAL, RETURN, THROW, BREAK, CONTINUE }

    private Set<Completion> completions(List<Stmt> body) {
        Set<Completion> out = java.util.EnumSet.of(Completion.NORMAL);
        for (Stmt statement : body) {
            if (!out.remove(Completion.NORMAL)) break;
            out.addAll(completions(statement));
        }
        return out;
    }

    private Set<Completion> completions(Stmt statement) {
        if (statement instanceof Stmt.Return) return java.util.EnumSet.of(Completion.RETURN);
        if (statement instanceof Stmt.Throw) return java.util.EnumSet.of(Completion.THROW);
        if (statement instanceof Stmt.Break) return java.util.EnumSet.of(Completion.BREAK);
        if (statement instanceof Stmt.Continue) return java.util.EnumSet.of(Completion.CONTINUE);
        if (statement instanceof Stmt.IfStmt branch) {
            Set<Completion> out = completions(branch.thenBody);
            for (Stmt.IfStmt.Elif elif : branch.elifs) out.addAll(completions(elif.body));
            if (branch.elseBody == null) out.add(Completion.NORMAL);
            else out.addAll(completions(branch.elseBody));
            return out;
        }
        if (statement instanceof Stmt.Match match && !match.branches.isEmpty()) {
            Set<Completion> out = java.util.EnumSet.noneOf(Completion.class);
            for (Stmt.Match.Branch branch : match.branches) out.addAll(completions(branch.body));
            return out;
        }
        if (statement instanceof Stmt.Try attempt) {
            Set<Completion> out = completions(attempt.body);
            for (Stmt.Try.CatchClause clause : attempt.catches) out.addAll(completions(clause.body));
            if (attempt.finallyBody != null) {
                Set<Completion> cleanup = completions(attempt.finallyBody);
                // An abrupt finally overrides the pending return/throw/break.
                // A normally completing finally preserves that pending outcome.
                if (!cleanup.remove(Completion.NORMAL)) out.clear();
                out.addAll(cleanup);
            }
            return out;
        }
        if (statement instanceof Stmt.WhileStmt loop) {
            Set<Completion> out = completions(loop.body);
            // A break in the body (not in a nested loop) leaves this loop normally.
            boolean breaks = out.remove(Completion.BREAK);
            out.remove(Completion.CONTINUE);
            if (loop.cond instanceof Expr.BoolLit literal && literal.value && !breaks) {
                // `while true` without break completes only through return or throw,
                // like Java's constant-true loops.
                out.remove(Completion.NORMAL);
            } else {
                out.add(Completion.NORMAL); // conservatively allow zero iterations
            }
            return out;
        }
        if (statement instanceof Stmt.ForStmt loop) {
            Set<Completion> out = completions(loop.body);
            out.remove(Completion.BREAK);
            out.remove(Completion.CONTINUE);
            out.add(Completion.NORMAL);
            return out;
        }
        return java.util.EnumSet.of(Completion.NORMAL);
    }

    private boolean definitelyReturns(List<Stmt> body) {
        Set<Completion> outcomes = completions(body);
        return !outcomes.contains(Completion.NORMAL) && !outcomes.contains(Completion.BREAK)
                && !outcomes.contains(Completion.CONTINUE);
    }

    private boolean definitelyExitsSequence(List<Stmt> body) {
        return !completions(body).contains(Completion.NORMAL);
    }

    private boolean definitelyExits(Stmt statement) {
        return !completions(statement).contains(Completion.NORMAL);
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
