package sprig.compiler.sem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Expr;
import sprig.compiler.ast.Module;
import sprig.compiler.ast.Stmt;
import sprig.compiler.ast.TypeRef;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Newcomer;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;
import sprig.compiler.types.ClassType;
import sprig.compiler.types.EnumType;
import sprig.compiler.types.FunctionType;
import sprig.compiler.types.JavaType;
import sprig.compiler.types.NativeType;
import sprig.compiler.types.TypeParameterType;
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
            if (!(decl instanceof Decl.Func) && !(decl instanceof Decl.Conform)) {
                declareType(module, decl);
            }
        }
        for (Decl decl : module.decls) {
            if (decl instanceof Decl.Conform conform && conform.parentAlias != null) {
                declareParentAlias(module, conform);
            }
        }
        for (Decl decl : module.decls) {
            if (decl instanceof Decl.Func func && !func.isMethod()) {
                declareFunction(module, func, null);
            }
        }
        collectTopVars(module);
        declareExports(module);
        for (Decl decl : module.decls) {
            resolveDeclTypes(module, decl);
        }
        for (Stmt stmt : module.topStatements) {
            if (stmt instanceof Stmt.VarDecl varDecl && varDecl.typeRef != null) {
                typeResolver.resolve(module, varDecl.typeRef);
            }
        }
    }

    private void declareExports(Module module) {
        for (Module.Export exported : module.exports) {
            Symbol alias = module.scope.importAliases.get(exported.alias);
            if (alias == null || alias.kind != Symbol.Kind.MODULE || alias.module == null) {
                diagnostics.add(Diagnostic.error(Codes.MODULE_EXPORT, Phase.NAME,
                    "Export requires a Sprig module alias: '" + exported.alias + "'", module.uri, exported.span));
                continue;
            }
            ModuleScope target = alias.module.scope;
            Symbol symbol = target.types.get(exported.name);
            if (symbol == null) symbol = target.functions.get(exported.name);
            if (symbol == null) symbol = target.topVars.get(exported.name);
            if (symbol == null || (symbol.decl == null && symbol.kind != Symbol.Kind.TOP_VAR)) {
                diagnostics.add(Diagnostic.error(Codes.MODULE_EXPORT, Phase.NAME,
                    "Module '" + exported.alias + "' has no exportable declaration '" + exported.name + "'",
                    module.uri, exported.span));
                continue;
            }
            Symbol clash = module.scope.types.get(exported.name);
            if (clash == null) clash = module.scope.functions.get(exported.name);
            if (clash == null) clash = module.scope.topVars.get(exported.name);
            if (clash == null) clash = module.scope.importAliases.get(exported.name);
            if (clash != null) {
                String origin = clash.module == null ? "local/builtin" : clash.module.path.getFileName().toString();
                diagnostics.add(Diagnostic.error(Codes.MODULE_EXPORT, Phase.NAME,
                    "Export name '" + exported.name + "' from " + symbol.module.path.getFileName()
                        + " conflicts with " + origin, module.uri, exported.span)
                        .withRelated("Conflicting declaration", clash.span));
                continue;
            }
            exported.symbol = symbol; // share identity, type and initialization with the defining module
            if (symbol.isType()) module.scope.types.put(exported.name,symbol);
            else if (symbol.kind == Symbol.Kind.FUNCTION) module.scope.functions.put(exported.name,symbol);
            else module.scope.topVars.put(exported.name,symbol);
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

    /**
     * The parent view of {@code conform C to J(...) as NAME}: a class-scope name
     * the conformance checker verifies later. It is bound here so method bodies
     * resolve it whatever the textual order of the class and the conform.
     */
    private void declareParentAlias(Module module, Decl.Conform conform) {
        Symbol owner = module.scope.types.get(conform.sourceName);
        if (owner == null || !(owner.decl instanceof Decl.ClassDecl classDecl) || owner.module != module
                || classDecl.parentAlias != null) {
            return; // the conformance checker reports the source or the second class conform
        }
        Symbol symbol = new Symbol(Symbol.Kind.PARENT_VIEW, conform.parentAlias, NativeType.ERROR);
        symbol.owner = classDecl;
        symbol.module = module;
        symbol.span = conform.parentAliasSpan != null ? conform.parentAliasSpan : conform.span;
        classDecl.parentAlias = conform.parentAlias;
        classDecl.parentSymbol = symbol;
    }

    private void declareType(Module module, Decl decl) {
        if (decl instanceof Decl.Conform) {
            return; // resolved and validated by the conformance checker
        }
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

    /** Lexical generic parameters of the function/class body being resolved. */
    private Map<String, Type> bodyTypeParams = Map.of();
    /**
     * Index of the top-level statement being resolved, or -1 inside functions,
     * methods and field defaults. Top-level statements run once in source order,
     * so straight-line top-level code may only use bindings declared above it.
     */
    private int topStatementIndex = -1;
    private int lambdaNesting;
    private final Map<Symbol, Integer> topVarIndex = new java.util.HashMap<>();

    private Map<String, Type> typeParamsOf(Module module, Decl decl) {
        Map<String, Type> map = new HashMap<>();
        for (String name : decl.typeParams) {
            if (map.containsKey(name)) {
                diagnostics.add(Diagnostic.error(Codes.NAME_DUPLICATE, Phase.NAME,
                        "Duplicate generic parameter '" + name + "'", module.uri, decl.span));
                continue;
            }
            map.put(name, new TypeParameterType(decl, name));
        }
        return map;
    }

    private void resolveDeclTypes(Module module, Decl decl) {
        if (decl instanceof Decl.ClassDecl classDecl) {
            classDecl.typeParamTypes.putAll(typeParamsOf(module, classDecl));
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
                field.type = typeResolver.resolve(module, field.typeRef, classDecl.typeParamTypes);
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
            // A method without a body makes the class a contract: only such
            // methods, no fields; it is generated as an interface.
            boolean anyAbstract = false;
            boolean anyConcrete = false;
            for (Decl.Func method : classDecl.methods) {
                if (method.abstractMethod) anyAbstract = true; else anyConcrete = true;
            }
            if (anyAbstract) {
                classDecl.contract = true;
                if (!classDecl.typeParams.isEmpty()) {
                    // A contract is a type and never generic in the 0.8 language:
                    // no Repository[T], no bounds, no associated types.
                    String element = classDecl.typeParams.get(0);
                    Decl.Func first = classDecl.methods.get(0);
                    diagnostics.add(Diagnostic.error(Codes.CLASS_ABSTRACT, Phase.NAME,
                            "Contract class '" + classDecl.name + "' is generic ('" + String.join(", ", classDecl.typeParams)
                                    + "'); a contract is never generic in the 0.8 language",
                            module.uri, classDecl.span)
                            .withHint("Declare one non-generic contract per element type (class Int" + classDecl.name
                                    + " with " + element + " written as Int), or keep a generic class that holds the single operation as a "
                                    + "fn field (let " + first.name + ": fn(...) -> ...) instead of a contract."));
                }
                if (!classDecl.fields.isEmpty()) {
                    diagnostics.add(Diagnostic.error(Codes.CLASS_ABSTRACT, Phase.NAME,
                            "Contract class '" + classDecl.name + "' declares fields; a class whose methods have "
                                    + "no body lists only the methods a conforming class must have",
                            module.uri, classDecl.fields.get(0).span)
                            .withHint("Move the fields into the classes that conform to '" + classDecl.name
                                    + "', or give every method a body to make this an ordinary class."));
                }
                if (anyConcrete) {
                    Decl.Func concrete = classDecl.methods.stream().filter(m -> !m.abstractMethod).findFirst().get();
                    diagnostics.add(Diagnostic.error(Codes.CLASS_ABSTRACT, Phase.NAME,
                            "Contract class '" + classDecl.name + "' mixes methods with and without a body",
                            module.uri, concrete.span)
                            .withHint("A contract has no default methods: remove the body of '" + concrete.name
                                    + "' and write the shared behavior as a module function that takes the contract, "
                                    + "for example func " + concrete.name + "(target: " + classDecl.name
                                    + ", ...) -> Unit; or give every method a body to make an ordinary class."));
                }
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
            variantDecl.typeParamTypes.putAll(typeParamsOf(module, variantDecl));
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
                    field.type = typeResolver.resolve(module, field.typeRef, variantDecl.typeParamTypes);
                    symbol.type = field.type;
                }
            }
        } else if (decl instanceof Decl.Func func) {
            resolveFunctionTypes(module, func);
        }
    }

    private void resolveFunctionTypes(Module module, Decl.Func func) {
        if (func.owner != null) {
            func.typeParamTypes.putAll(func.owner.typeParamTypes);
        } else {
            func.typeParamTypes.putAll(typeParamsOf(module, func));
        }
        List<Type> paramTypes = new ArrayList<>();
        for (Decl.Param param : func.params) {
            param.type = typeResolver.resolve(module, param.typeRef, func.typeParamTypes);
            param.symbol.type = param.type;
            paramTypes.add(param.type);
        }
        func.returnType = typeResolver.resolveReturn(module, func.returnTypeRef, func.typeParamTypes);
        for (TypeRef ref : func.throwsRefs) {
            // Whether the type is an error type is checked by the type checker,
            // once conformance has recorded which classes extend Error.
            func.throwsTypes.add(typeResolver.resolve(module, ref, func.typeParamTypes));
        }
        if (func.rethrows && func.params.stream().noneMatch(param -> param.type instanceof FunctionType fn && fn.throwsAny())) {
            diagnostics.add(Diagnostic.error(Codes.FLOW_RETHROWS, Phase.FLOW,
                    "'" + func.name + "' is declared rethrows but no parameter has a function type with 'throws Error'",
                    module.uri, func.nameSpan != null ? func.nameSpan : func.span)
                    .withHint("Write the callable parameter as fn(T) -> R throws Error, or drop rethrows."));
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
        for (int i = 0; i < module.topStatements.size(); i++) {
            if (module.topStatements.get(i) instanceof Stmt.VarDecl varDecl && varDecl.symbol != null) {
                topVarIndex.put(varDecl.symbol, i);
            }
        }
        for (int i = 0; i < module.topStatements.size(); i++) {
            topStatementIndex = i;
            resolveStmt(module, topScope, module.topStatements.get(i));
        }
        topStatementIndex = -1;
    }

    private void resolveFieldDefault(Module module, Decl.ClassDecl owner, Decl.Field field) {
        Scope scope = new Scope(null, module, null, null);
        for (Decl.Field sibling : owner.fields) {
            scope.forbiddenFields.add(sibling.name);
        }
        Map<String, Type> previous = bodyTypeParams;
        bodyTypeParams = owner.typeParamTypes;
        resolveExpr(module, scope, field.defaultExpr);
        bodyTypeParams = previous;
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
        Map<String, Type> previous = bodyTypeParams;
        bodyTypeParams = func.typeParamTypes;
        for (Stmt stmt : func.body) {
            resolveStmt(module, scope, stmt);
        }
        bodyTypeParams = previous;
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
                    typeResolver.resolve(module, varDecl.typeRef, bodyTypeParams);
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
                clause.caughtType = typeResolver.resolve(module, clause.typeRef, bodyTypeParams);
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
                            "Match case must be written as Type.Case", module.uri, branch.caseTypeRef.span)
                            .withHint("match is for enums and variants; there are no type tests on class or contract values. "
                                    + "A closed set of types is a variant (declare one with a case per type and match on it); "
                                    + "an open set is a contract (call its methods instead of testing the type)."));
                } else {
                    // The branch names the declaration; explicit generic
                    // arguments are not needed (the scrutinee supplies them).
                    branch.caseTypeRef.resolved = typeResolver.resolveUnapplied(module, branch.caseTypeRef);
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
        } else if (expr instanceof Expr.Subscript subscript) {
            resolveExpr(module, scope, subscript.base);
            if (subscript.index != null) {
                resolveExpr(module, scope, subscript.index);
            } else if (subscript.typeArgs != null) {
                // A bracket payload that parsed as type references may still be
                // ordinary indexing, for example values[index]. When the base
                // is not a type name, build and resolve the index expression
                // now so name references keep their symbols.
                Symbol baseSymbol = subscript.base instanceof Expr.Name baseName
                        ? baseName.symbol : null;
                boolean typeLike = baseSymbol != null && (baseSymbol.isType()
                        || baseSymbol.kind == Symbol.Kind.BUILTIN_TYPE
                        || (baseSymbol.kind == Symbol.Kind.FUNCTION
                            && baseSymbol.decl != null && baseSymbol.decl.isGeneric()));
                if (!typeLike && subscript.base instanceof Expr.FieldAccess member
                        && member.receiver instanceof Expr.Name qualifier
                        && qualifier.symbol != null
                        && qualifier.symbol.kind == Symbol.Kind.MODULE) {
                    // Module-qualified generic use: math.Box[Int](...)
                    typeLike = true;
                }
                if (!typeLike) {
                    // Defer to the checker when the bracket payload cannot be a
                    // value expression in this scope (e.g. a Java explicit
                    // generic call Shapes.repeat[T](...) inside generic T).
                    Expr candidate = TypeChecker.indexFromTypeArgs(subscript.typeArgs);
                    if (candidate != null && indexNameResolvable(scope, candidate)) {
                        resolveExpr(module, scope, candidate);
                        subscript.resolvedIndex = candidate;
                    }
                }
            }
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
        } else if (expr instanceof Expr.Match match) {
            resolveStmt(module,scope,match.cases);
        } else if (expr instanceof Expr.If ifExpr) {
            // Branches are single expressions: they declare nothing, so they
            // resolve in the enclosing scope, in source order.
            for (int i = 0; i < ifExpr.conditions.size(); i++) {
                resolveExpr(module, scope, ifExpr.conditions.get(i));
                resolveExpr(module, scope, ifExpr.values.get(i));
            }
            if (ifExpr.elseValue != null) {
                resolveExpr(module, scope, ifExpr.elseValue);
            }
        } else if (expr instanceof Expr.Lambda lambda) {
            Scope lambdaScope = childScope(scope);
            for (Decl.Param param : lambda.params) {
                param.type = typeResolver.resolve(module, param.typeRef, bodyTypeParams);
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
            // A lambda body runs when it is called, not where it is written.
            lambdaNesting++;
            resolveExpr(module, lambdaScope, lambda.body);
            lambdaNesting--;
        }
    }

    /** Whether an index candidate's leftmost name resolves as a value here. */
    private static boolean indexNameResolvable(Scope scope, Expr candidate) {
        if (candidate instanceof Expr.Name name) {
            return scope.resolve(name.name) != null;
        }
        if (candidate instanceof Expr.FieldAccess access) {
            return indexNameResolvable(scope, access.receiver);
        }
        return true;
    }

    private void resolveName(Module module, Scope scope, Expr.Name name) {
        Scope restriction = scope;
        while (restriction != null && !restriction.forbiddenFields.contains(name.name)) {
            restriction = restriction.parent;
        }
        // Branch and lambda scopes retain initializer restrictions, while an
        // explicit local binder/parameter may legitimately shadow a field.
        if (restriction != null && scope.findLocal(name.name) == null) {
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
            Diagnostic unresolved = Diagnostic.error(Codes.NAME_UNRESOLVED, Phase.NAME,
                    "Unresolved name '" + name.name + "'", module.uri, name.span);
            String hint = Newcomer.nameHint(name.name);
            if (hint == null && sprig.compiler.project.StdLibrary.bundledModuleNames().contains(name.name)) {
                // The bundled module's own name used without its import: the
                // one line that fixes it is known, so it is the hint and the edit.
                String importLine = "import \"@std/" + name.name + ".spr\" as " + name.name;
                hint = "'" + name.name + "' is a bundled module; add the line " + importLine
                        + " at the top of the file (imports precede declarations).";
                unresolved.withEdit(sprig.compiler.diag.Span.point(0, 0), importLine + "\n", "add the import");
            }
            if (hint != null) {
                unresolved.withHint(hint);
            }
            diagnostics.add(unresolved);
            symbol = errorSymbol(name.name, name.span);
        }
        name.symbol = symbol;
        if (topStatementIndex >= 0 && lambdaNesting == 0 && symbol.kind == Symbol.Kind.TOP_VAR
                && symbol.module == module) {
            Integer declared = topVarIndex.get(symbol);
            if (declared != null && declared >= topStatementIndex) {
                diagnostics.add(Diagnostic.error(Codes.NAME_FORWARD_REFERENCE, Phase.NAME,
                        "Top-level '" + name.name + "' is used before its declaration; top-level statements "
                                + "run once, in source order", module.uri, name.span)
                        .withHint("Move the declaration of '" + name.name + "' above this statement.")
                        .withRelated("Declared here", symbol.span));
            }
        }
    }

    private static Symbol errorSymbol(String name, Span span) {
        Symbol symbol = new Symbol(Symbol.Kind.LOCAL, name, NativeType.ERROR);
        symbol.span = span;
        return symbol;
    }
}
