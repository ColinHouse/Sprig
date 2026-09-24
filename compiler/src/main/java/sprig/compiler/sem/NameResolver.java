package sprig.compiler.sem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Expr;
import sprig.compiler.ast.Module;
import sprig.compiler.ast.Stmt;
import sprig.compiler.ast.TypeRef;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;
import sprig.compiler.types.ClassType;
import sprig.compiler.types.EnumType;
import sprig.compiler.types.FunctionType;
import sprig.compiler.types.JavaType;
import sprig.compiler.types.NativeType;
import sprig.compiler.types.Type;
import sprig.compiler.types.VariantType;
import sprig.runtime.SprigError;

/**
 * Name resolution: builds module/member/parameter/local symbols, resolves every
 * written type reference and binds every {@code Name} expression to a symbol.
 * Type checking runs afterwards over these bindings.
 */
public final class NameResolver {
    private static final Set<String> RESERVED_TYPE_NAMES = Set.of(
            "Int", "Int32", "Float", "Float32", "Decimal", "BigInt", "Bool", "String", "Unit", "Error",
            "List", "MutableList", "Map", "MutableMap");

    private final Diagnostics diagnostics;
    private final TypeRefResolver typeResolver;

    public NameResolver(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
        this.typeResolver = new TypeRefResolver(diagnostics);
    }

    public TypeRefResolver types() {
        return typeResolver;
    }

    // ------------------------------------------------------------------
    // Declaration collection
    // ------------------------------------------------------------------

    public void declare(Module module) {
        ModuleScope scope = new ModuleScope(module);
        module.scope = scope;
        registerBuiltins(scope);
        declareImports(module);
        for (Decl decl : module.decls) {
            if (!(decl instanceof Decl.Func)) {
                declareType(module, decl);
            }
        }
        for (Decl decl : module.decls) {
            if (decl instanceof Decl.Func func && !func.isMethod()) {
                declareFunction(module, func, null);
            }
        }
        collectTopVars(module);
        for (Decl decl : module.decls) {
            resolveDeclTypes(module, decl);
        }
        for (Stmt stmt : module.topStatements) {
            if (stmt instanceof Stmt.VarDecl varDecl && varDecl.typeRef != null) {
                typeResolver.resolve(module, varDecl.typeRef);
            }
        }
    }

    private void declareImports(Module module) {
        for (Decl.Import imp : module.imports) {
            String alias = ImportNames.aliasFor(imp);
            if (alias == null || alias.isEmpty()) {
                diagnostics.add(Diagnostic.error(Codes.NAME_IMPORT, Phase.NAME,
                        "Cannot derive an import alias; add 'as name'", module.uri, imp.span));
                continue;
            }
            if (module.scope.types.containsKey(alias) || module.scope.functions.containsKey(alias)
                    || module.scope.topVars.containsKey(alias)) {
                diagnostics.add(Diagnostic.error(Codes.NAME_DUPLICATE, Phase.NAME,
                        "Import alias '" + alias + "' collides with a module declaration", module.uri, imp.span));
                continue;
            }
            Symbol existing = module.scope.importAliases.get(alias);
            if (existing != null) {
                diagnostics.add(Diagnostic.error(Codes.NAME_DUPLICATE, Phase.NAME,
                        "Duplicate import alias '" + alias + "'", module.uri, imp.span)
                        .withRelated("First import declared here", existing.span));
                continue;
            }
            Symbol symbol;
            if (imp.fileImport) {
                Module imported = module.importedModules.get(alias);
                if (imported == null) {
                    diagnostics.add(Diagnostic.error(Codes.NAME_IMPORT, Phase.NAME,
                            "Cannot resolve file import '" + imp.pathOrClass + "'", module.uri, imp.span));
                    continue;
                }
                symbol = new Symbol(Symbol.Kind.MODULE, alias, null);
                symbol.module = imported;
            } else {
                Class<?> clazz = module.javaImports.get(alias);
                if (clazz == null) {
                    continue; // loader already reported it
                }
                symbol = new Symbol(Symbol.Kind.JAVA_TYPE, alias, JavaTypes.map(clazz));
                symbol.javaClass = clazz;
            }
            symbol.span = imp.span;
            module.scope.importAliases.put(alias, symbol);
        }
    }

    private void registerBuiltins(ModuleScope scope) {
        addBuiltin(scope, "Int", NativeType.INT);
        addBuiltin(scope, "Int32", NativeType.INT32);
        addBuiltin(scope, "Float", NativeType.FLOAT);
        addBuiltin(scope, "Float32", NativeType.FLOAT32);
        addBuiltin(scope, "Decimal", NativeType.DECIMAL);
        addBuiltin(scope, "BigInt", NativeType.BIGINT);
        addBuiltin(scope, "Bool", NativeType.BOOL);
        addBuiltin(scope, "String", NativeType.STRING);
        addBuiltin(scope, "Unit", NativeType.UNIT);
        addBuiltin(scope, "Error", new JavaType(SprigError.class));
        addBuiltinFunction(scope, "print");
        addBuiltinFunction(scope, "range");
        addBuiltinFunction(scope, "assert");
    }

    /** Built-in free functions have a symbol without a declaration. */
    private void addBuiltinFunction(ModuleScope scope, String name) {
        Symbol symbol = new Symbol(Symbol.Kind.FUNCTION, name, NativeType.ERROR);
        scope.functions.put(name, symbol);
    }

    private void addBuiltin(ModuleScope scope, String name, Type type) {
        Symbol symbol = new Symbol(Symbol.Kind.BUILTIN_TYPE, name, type);
        scope.types.put(name, symbol);
    }

    private void declareType(Module module, Decl decl) {
        if (RESERVED_TYPE_NAMES.contains(decl.name)) {
            diagnostics.add(Diagnostic.error(Codes.NAME_DUPLICATE, Phase.NAME,
                    "'" + decl.name + "' is a built-in type name", module.uri, decl.span));
            return;
        }
        Symbol existing = module.scope.types.get(decl.name);
        if (existing != null) {
            diagnostics.add(Diagnostic.error(Codes.NAME_DUPLICATE, Phase.NAME,
                    "Duplicate type '" + decl.name + "'", module.uri, decl.span)
                    .withRelated("First declared here", existing.span));
            return;
        }
        Type type;
        Symbol.Kind kind;
        if (decl instanceof Decl.ClassDecl classDecl) {
            type = new ClassType(classDecl);
            kind = Symbol.Kind.CLASS;
        } else if (decl instanceof Decl.EnumDecl enumDecl) {
            type = new EnumType(enumDecl);
            kind = Symbol.Kind.ENUM;
        } else {
            type = new VariantType((Decl.VariantDecl) decl);
            kind = Symbol.Kind.VARIANT;
        }
        Symbol symbol = new Symbol(kind, decl.name, type);
        symbol.decl = decl;
        symbol.module = module;
        symbol.span = decl.span;
        decl.symbol = symbol;
        module.scope.types.put(decl.name, symbol);
    }

    private void declareFunction(Module module, Decl.Func func, Decl.ClassDecl owner) {
        Set<String> paramNames = new HashSet<>();
        for (Decl.Param param : func.params) {
            if (!paramNames.add(param.name)) {
                diagnostics.add(Diagnostic.error(Codes.NAME_DUPLICATE, Phase.NAME,
                        "Duplicate parameter '" + param.name + "'", module.uri, func.span));
            }
        }
        if (owner == null) {
            Symbol existing = module.scope.functions.get(func.name) != null
                    ? module.scope.functions.get(func.name) : module.scope.types.get(func.name);
            if (existing != null) {
                diagnostics.add(Diagnostic.error(Codes.NAME_DUPLICATE, Phase.NAME,
                        "Duplicate declaration '" + func.name + "'", module.uri, func.span)
                        .withRelated("First declared here", existing.span));
            }
            Symbol symbol = new Symbol(Symbol.Kind.FUNCTION, func.name, NativeType.ERROR);
            symbol.decl = func;
            symbol.module = module;
            symbol.span = func.span;
            func.symbol = symbol;
            module.scope.functions.put(func.name, symbol);
        } else {
            Symbol symbol = new Symbol(Symbol.Kind.METHOD, func.name, NativeType.ERROR);
            symbol.decl = func;
            symbol.owner = owner;
            symbol.module = module;
            symbol.span = func.span;
            func.symbol = symbol;
        }
        for (Decl.Param param : func.params) {
            Symbol symbol = new Symbol(Symbol.Kind.PARAM, param.name, NativeType.ERROR);
            symbol.span = func.span;
            param.symbol = symbol;
        }
    }

    private void collectTopVars(Module module) {
        for (Stmt stmt : module.topStatements) {
            if (!(stmt instanceof Stmt.VarDecl varDecl)) {
                continue;
            }
            Symbol clash = module.scope.functions.get(varDecl.name);
            if (clash == null) {
                clash = module.scope.types.get(varDecl.name);
            }
            if (clash == null) {
                clash = module.scope.topVars.get(varDecl.name);
            }
            if (clash != null) {
                diagnostics.add(Diagnostic.error(Codes.NAME_DUPLICATE, Phase.NAME,
                        "Duplicate top-level name '" + varDecl.name + "'", module.uri, varDecl.span)
                        .withRelated("First declared here", clash.span));
                continue;
            }
            Symbol symbol = new Symbol(Symbol.Kind.TOP_VAR, varDecl.name, null);
            symbol.mutable = varDecl.mutable;
            symbol.module = module;
            symbol.span = varDecl.span;
            varDecl.symbol = symbol;
            module.scope.topVars.put(varDecl.name, symbol);
        }
    }

    private void resolveDeclTypes(Module module, Decl decl) {
        if (decl instanceof Decl.ClassDecl classDecl) {
            Set<String> fieldNames = new HashSet<>();
            for (Decl.Field field : classDecl.fields) {
                if (!fieldNames.add(field.name)) {
                    diagnostics.add(Diagnostic.error(Codes.NAME_DUPLICATE_MEMBER, Phase.NAME,
                            "Duplicate field '" + field.name + "' in class " + classDecl.name,
                            module.uri, field.span));
                }
                Symbol symbol = new Symbol(Symbol.Kind.FIELD, field.name, NativeType.ERROR);
                symbol.mutable = field.mutable;
                symbol.owner = classDecl;
                symbol.module = module;
                symbol.span = field.span;
                field.symbol = symbol;
                field.type = typeResolver.resolve(module, field.typeRef);
                symbol.type = field.type;
            }
            Set<String> methodNames = new HashSet<>();
            for (Decl.Func method : classDecl.methods) {
                if (!methodNames.add(method.name)) {
                    diagnostics.add(Diagnostic.error(Codes.NAME_DUPLICATE_MEMBER, Phase.NAME,
                            "Duplicate method '" + method.name + "' in class " + classDecl.name,
                            module.uri, method.span));
                }
                declareFunction(module, method, classDecl);
                resolveFunctionTypes(module, method);
            }
        } else if (decl instanceof Decl.EnumDecl enumDecl) {
            Set<String> caseNames = new HashSet<>();
            for (String caseName : enumDecl.cases) {
                if (!caseNames.add(caseName)) {
                    diagnostics.add(Diagnostic.error(Codes.NAME_DUPLICATE, Phase.NAME,
                            "Duplicate enum case '" + caseName + "'", module.uri, enumDecl.span));
                }
            }
        } else if (decl instanceof Decl.VariantDecl variantDecl) {
            Set<String> caseNames = new HashSet<>();
            for (Decl.VariantCase variantCase : variantDecl.cases) {
                if (!caseNames.add(variantCase.name)) {
                    diagnostics.add(Diagnostic.error(Codes.NAME_DUPLICATE, Phase.NAME,
                            "Duplicate variant case '" + variantCase.name + "'", module.uri, variantCase.span));
                }
                Set<String> fieldNames = new HashSet<>();
                for (Decl.Field field : variantCase.fields) {
                    if (!fieldNames.add(field.name)) {
                        diagnostics.add(Diagnostic.error(Codes.NAME_DUPLICATE_MEMBER, Phase.NAME,
                                "Duplicate payload field '" + field.name + "' in case "
                                        + variantDecl.name + "." + variantCase.name,
                                module.uri, field.span));
                    }
                    Symbol symbol = new Symbol(Symbol.Kind.FIELD, field.name, NativeType.ERROR);
                    symbol.mutable = false;
                    symbol.module = module;
                    symbol.span = field.span;
                    field.symbol = symbol;
                    field.type = typeResolver.resolve(module, field.typeRef);
                    symbol.type = field.type;
                }
            }
        } else if (decl instanceof Decl.Func func) {
            resolveFunctionTypes(module, func);
        }
    }

    private void resolveFunctionTypes(Module module, Decl.Func func) {
        List<Type> paramTypes = new ArrayList<>();
        for (Decl.Param param : func.params) {
            param.type = typeResolver.resolve(module, param.typeRef);
            param.symbol.type = param.type;
            paramTypes.add(param.type);
        }
        func.returnType = typeResolver.resolveReturn(module, func.returnTypeRef);
        for (TypeRef ref : func.throwsRefs) {
            Type type = typeResolver.resolve(module, ref);
            if (!Semantics.isErrorType(type)) {
                diagnostics.add(Diagnostic.error(Codes.TYPE_MISMATCH, Phase.TYPE,
                        "throws requires an error type (Error or an imported Throwable)",
                        module.uri, ref.span).withTypes("Error or imported Throwable", type.display()));
            }
            func.throwsTypes.add(type);
        }
        func.symbol.type = new FunctionType(paramTypes, func.returnType);
    }

    // ------------------------------------------------------------------
    // Body resolution
    // ------------------------------------------------------------------

    public void resolveBodies(Module module) {
        for (Decl decl : module.decls) {
            if (decl instanceof Decl.ClassDecl classDecl) {
                for (Decl.Field field : classDecl.fields) {
                    if (field.defaultExpr != null) {
                        resolveFieldDefault(module, classDecl, field);
                    }
                }
                for (Decl.Func method : classDecl.methods) {
                    resolveFunctionBody(module, method, classDecl);
                }
            } else if (decl instanceof Decl.Func func) {
                resolveFunctionBody(module, func, null);
            }
        }
        Scope topScope = new Scope(null, module, null, null);
        for (Stmt stmt : module.topStatements) {
            resolveStmt(module, topScope, stmt);
        }
    }

    private void resolveFieldDefault(Module module, Decl.ClassDecl owner, Decl.Field field) {
        Scope scope = new Scope(null, module, null, null);
        for (Decl.Field sibling : owner.fields) {
            scope.forbiddenFields.add(sibling.name);
        }
        resolveExpr(module, scope, field.defaultExpr);
    }

    private void resolveFunctionBody(Module module, Decl.Func func, Decl.ClassDecl owner) {
        Scope scope = new Scope(null, module, func, owner);
        for (Decl.Param param : func.params) {
            if (owner != null && hasField(owner, param.name)) {
                diagnostics.add(Diagnostic.error(Codes.NAME_FIELD_SHADOW, Phase.NAME,
                        "Parameter '" + param.name + "' shadows field '" + param.name + "' of class "
                                + owner.name, module.uri, func.span)
                        .withRelated("Field declared here", fieldSpan(owner, param.name)));
            }
            scope.locals.put(param.name, param.symbol);
        }
        for (Stmt stmt : func.body) {
            resolveStmt(module, scope, stmt);
        }
    }

    private static boolean hasField(Decl.ClassDecl owner, String name) {
        for (Decl.Field field : owner.fields) {
            if (field.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static Span fieldSpan(Decl.ClassDecl owner, String name) {
        for (Decl.Field field : owner.fields) {
            if (field.name.equals(name)) {
                return field.span;
            }
        }
        return null;
    }

    private void resolveStmt(Module module, Scope scope, Stmt stmt) {
        if (stmt instanceof Stmt.VarDecl varDecl) {
            if (varDecl.symbol == null) {
                if (varDecl.typeRef != null) {
                    typeResolver.resolve(module, varDecl.typeRef);
                }
                Symbol symbol = declareLocal(module, scope, varDecl.name, varDecl.span, varDecl.mutable,
                        varDecl.typeRef == null ? null : varDecl.typeRef.resolved);
                varDecl.symbol = symbol;
            } else if (varDecl.typeRef != null) {
                varDecl.symbol.type = varDecl.typeRef.resolved;
            }
            if (varDecl.init != null) {
                resolveExpr(module, scope, varDecl.init);
            }
        } else if (stmt instanceof Stmt.Assign assign) {
            resolveExpr(module, scope, assign.target);
            resolveExpr(module, scope, assign.value);
        } else if (stmt instanceof Stmt.ExprStmt exprStmt) {
            resolveExpr(module, scope, exprStmt.expr);
        } else if (stmt instanceof Stmt.Return ret) {
            if (ret.value != null) {
                resolveExpr(module, scope, ret.value);
            }
        } else if (stmt instanceof Stmt.Throw thr) {
            resolveExpr(module, scope, thr.value);
        } else if (stmt instanceof Stmt.IfStmt ifStmt) {
            resolveExpr(module, scope, ifStmt.cond);
            resolveBlock(module, scope, ifStmt.thenBody);
            for (Stmt.IfStmt.Elif elif : ifStmt.elifs) {
                resolveExpr(module, scope, elif.cond);
                resolveBlock(module, scope, elif.body);
            }
            if (ifStmt.elseBody != null) {
                resolveBlock(module, scope, ifStmt.elseBody);
            }
        } else if (stmt instanceof Stmt.WhileStmt whileStmt) {
            resolveExpr(module, scope, whileStmt.cond);
            resolveBlock(module, scope, whileStmt.body);
        } else if (stmt instanceof Stmt.ForStmt forStmt) {
            resolveExpr(module, scope, forStmt.iterable);
            Scope loopScope = childScope(scope);
            Symbol symbol = declareLocal(module, loopScope, forStmt.varName, forStmt.span, false, null);
            forStmt.symbol = symbol;
            for (Stmt child : forStmt.body) {
                resolveStmt(module, loopScope, child);
            }
        } else if (stmt instanceof Stmt.Try tryStmt) {
            resolveBlock(module, scope, tryStmt.body);
            for (Stmt.Try.CatchClause clause : tryStmt.catches) {
                clause.caughtType = typeResolver.resolve(module, clause.typeRef);
                Scope catchScope = childScope(scope);
                Symbol symbol = declareLocal(module, catchScope, clause.name, clause.typeRef.span, false,
                        clause.caughtType);
                clause.symbol = symbol;
                resolveBlockIn(module, catchScope, clause.body);
            }
            if (tryStmt.finallyBody != null) {
                resolveBlock(module, scope, tryStmt.finallyBody);
            }
        } else if (stmt instanceof Stmt.Match match) {
            resolveExpr(module, scope, match.scrutinee);
            for (Stmt.Match.Branch branch : match.branches) {
                if (branch.caseTypeRef.parts.isEmpty()) {
                    diagnostics.add(Diagnostic.error(Codes.MATCH_UNKNOWN_CASE, Phase.TYPE,
                            "Match case must be written as Type.Case", module.uri, branch.caseTypeRef.span));
                } else {
                    branch.caseTypeRef.resolved = typeResolver.resolve(module, branch.caseTypeRef);
                }
                Scope branchScope = childScope(scope);
                if (branch.binder != null) {
                    Symbol symbol = declareLocal(module, branchScope, branch.binder, branch.caseTypeRef.span,
                            false, NativeType.ERROR);
                    branch.binderSymbol = symbol;
                }
                for (Stmt child : branch.body) {
                    resolveStmt(module, branchScope, child);
                }
            }
        }
        // Break/Continue/Pass need no resolution.
    }

    private void resolveBlock(Module module, Scope parent, List<Stmt> body) {
        resolveBlockIn(module, childScope(parent), body);
    }

    private void resolveBlockIn(Module module, Scope blockScope, List<Stmt> body) {
        for (Stmt child : body) {
            resolveStmt(module, blockScope, child);
        }
    }

    private Scope childScope(Scope parent) {
        return new Scope(parent, parent.module, parent.function, parent.classDecl);
    }

    private Symbol declareLocal(Module module, Scope scope, String name, Span span, boolean mutable, Type type) {
        if (scope.locals.containsKey(name)) {
            diagnostics.add(Diagnostic.error(Codes.NAME_DUPLICATE, Phase.NAME,
                    "Duplicate local name '" + name + "' in the same scope", module.uri, span)
                    .withRelated("First declared here", scope.locals.get(name).span));
            return scope.locals.get(name);
        }
        Decl.ClassDecl owner = scope.enclosingClass();
        if (owner != null && hasField(owner, name)) {
            diagnostics.add(Diagnostic.error(Codes.NAME_FIELD_SHADOW, Phase.NAME,
                    "Local '" + name + "' shadows field '" + name + "' of class " + owner.name,
                    module.uri, span)
                    .withRelated("Field declared here", fieldSpan(owner, name)));
        }
        Symbol symbol = new Symbol(Symbol.Kind.LOCAL, name, type);
        symbol.mutable = mutable;
        symbol.module = module;
        symbol.span = span;
        scope.locals.put(name, symbol);
        return symbol;
    }

    private void resolveExpr(Module module, Scope scope, Expr expr) {
        if (expr instanceof Expr.Name name) {
            resolveName(module, scope, name);
        } else if (expr instanceof Expr.FieldAccess access) {
            resolveExpr(module, scope, access.receiver);
        } else if (expr instanceof Expr.Call call) {
            resolveExpr(module, scope, call.callee);
            for (Expr.Arg arg : call.args) {
                resolveExpr(module, scope, arg.value);
            }
        } else if (expr instanceof Expr.Index index) {
            resolveExpr(module, scope, index.receiver);
            resolveExpr(module, scope, index.index);
        } else if (expr instanceof Expr.Unary unary) {
            resolveExpr(module, scope, unary.operand);
        } else if (expr instanceof Expr.Binary binary) {
            resolveExpr(module, scope, binary.left);
            resolveExpr(module, scope, binary.right);
        } else if (expr instanceof Expr.ListLit list) {
            for (Expr item : list.items) {
                resolveExpr(module, scope, item);
            }
        } else if (expr instanceof Expr.MapLit map) {
            for (Expr key : map.keys) {
                resolveExpr(module, scope, key);
            }
            for (Expr value : map.values) {
                resolveExpr(module, scope, value);
            }
        } else if (expr instanceof Expr.Lambda lambda) {
            Scope lambdaScope = childScope(scope);
            for (Decl.Param param : lambda.params) {
                param.type = typeResolver.resolve(module, param.typeRef);
                Symbol symbol = new Symbol(Symbol.Kind.PARAM, param.name, param.type);
                symbol.span = param.typeRef.span;
                param.symbol = symbol;
                if (lambdaScope.locals.containsKey(param.name)) {
                    diagnostics.add(Diagnostic.error(Codes.NAME_DUPLICATE, Phase.NAME,
                            "Duplicate lambda parameter '" + param.name + "'", module.uri, param.typeRef.span));
                } else {
                    lambdaScope.locals.put(param.name, symbol);
                }
            }
            resolveExpr(module, lambdaScope, lambda.body);
        }
    }

    private void resolveName(Module module, Scope scope, Expr.Name name) {
        if (scope.forbiddenFields.contains(name.name)) {
            diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                    "Field initializer cannot reference field '" + name.name
                            + "'; fields initialize in declaration order without access to each other",
                    module.uri, name.span));
            name.symbol = errorSymbol(name.name, name.span);
            return;
        }
        Symbol symbol = scope.resolve(name.name);
        if (symbol == null) {
            symbol = module.scope.types.get(name.name);
        }
        if (symbol == null) {
            symbol = module.scope.importAliases.get(name.name);
        }
        if (symbol == null) {
            diagnostics.add(Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                    "Unresolved name '" + name.name + "'", module.uri, name.span));
            symbol = errorSymbol(name.name, name.span);
        }
        name.symbol = symbol;
    }

    private static Symbol errorSymbol(String name, Span span) {
        Symbol symbol = new Symbol(Symbol.Kind.LOCAL, name, NativeType.ERROR);
        symbol.span = span;
        return symbol;
    }
}
