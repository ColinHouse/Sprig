package sprig.compiler.lsp;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Expr;
import sprig.compiler.ast.Module;
import sprig.compiler.ast.Stmt;
import sprig.compiler.ast.TypeRef;
import sprig.compiler.diag.Span;
import sprig.compiler.sem.ResolvedCall;
import sprig.compiler.sem.ResolvedField;
import sprig.compiler.sem.Symbol;
import sprig.compiler.types.ClassType;
import sprig.compiler.types.EnumType;
import sprig.compiler.types.JavaType;
import sprig.compiler.types.ListType;
import sprig.compiler.types.MapType;
import sprig.compiler.types.NativeType;
import sprig.compiler.types.NullableType;
import sprig.compiler.types.Type;
import sprig.compiler.types.TypeParameterType;
import sprig.compiler.types.VariantCaseType;
import sprig.compiler.types.VariantType;

/**
 * Every declaration and name occurrence of one compilation, taken from the
 * symbols and resolved members the compiler recorded. Nothing here resolves
 * names again: an occurrence the compiler did not resolve has no target.
 */
final class SymbolIndex {
    enum Kind {
        FUNCTION, METHOD, CLASS, ENUM, VARIANT, VARIANT_CASE, ENUM_CASE, FIELD, PAYLOAD_FIELD,
        PARAM, LOCAL, TOP_VAR, MODULE
    }

    /** A Sprig declaration that names can refer to. */
    static final class Target {
        final Kind kind;
        final String name;
        final Path path;
        final Span nameSpan;
        final Span span;
        final Object node;
        /** Function, class, variant case or enum that contains this declaration. */
        final String container;
        Symbol symbol;

        Target(Kind kind, String name, Path path, Span nameSpan, Span span, Object node, String container) {
            this.kind = kind;
            this.name = name;
            this.path = path;
            this.nameSpan = nameSpan;
            this.span = span;
            this.node = node;
            this.container = container;
        }

        /** Locals and parameters are visible only inside one function, lambda or block. */
        boolean local() {
            return kind == Kind.LOCAL || kind == Kind.PARAM;
        }

        /** Identifies the same declaration across separate compilations of the same text. */
        String key() {
            return path + ":" + (nameSpan == null ? "-" : nameSpan.startLine + ":" + nameSpan.startColumn)
                    + ":" + kind + ":" + name;
        }
    }

    /** Hover facts for a name that has no Sprig declaration (built-ins, Java). */
    record Info(String code, String note) {
    }

    static final class Occurrence {
        final Path path;
        final Span span;
        final Target target;
        final Info info;
        final boolean declaration;
        /** The static type of the name expression here, when the checker recorded one. */
        Type type;
        /** Result type of the call this member name is the callee of. */
        Type resultType;
        /**
         * The call this name is the callee of, written with the type arguments
         * the compiler inferred for it, e.g. {@code identity[Int](...) -> Int}.
         */
        String inferredCall;

        Occurrence(Path path, Span span, Target target, Info info, boolean declaration) {
            this.path = path;
            this.span = span;
            this.target = target;
            this.info = info;
            this.declaration = declaration;
        }
    }

    private record EnumCase(Decl.EnumDecl decl, String name) {
    }

    private final Analysis analysis;
    private final Map<Object, Target> byNode = new HashMap<>();
    private final Map<Symbol, Target> bySymbol = new IdentityHashMap<>();
    private final Map<Module, Target> modules = new IdentityHashMap<>();
    private final Map<Path, List<Occurrence>> occurrences = new HashMap<>();
    private final List<Target> targets = new ArrayList<>();

    SymbolIndex(Analysis analysis) {
        this.analysis = analysis;
        if (analysis.compilation == null) {
            return;
        }
        for (Module module : analysis.compilation.modules) {
            declareModule(module);
        }
        for (Module module : analysis.compilation.modules) {
            new Walker(module).walkModule();
        }
    }

    List<Target> targets() {
        return targets;
    }

    List<Occurrence> occurrences(Path path) {
        return occurrences.getOrDefault(path, List.of());
    }

    /** The innermost name at a compiler position; a position just after a name still finds it. */
    Occurrence at(Path path, int line, int column) {
        Occurrence best = null;
        boolean bestInside = false;
        for (Occurrence occurrence : occurrences(path)) {
            Span span = occurrence.span;
            if (span.startLine != line || span.endLine != line
                    || column < span.startColumn || column > span.endColumn) {
                continue;
            }
            boolean inside = column < span.endColumn;
            if (best == null || (inside && !bestInside)
                    || (inside == bestInside && width(span) < width(best.span))) {
                best = occurrence;
                bestInside = inside;
            }
        }
        return best;
    }

    /** Declaration and references of a target, by its cross-compilation key. */
    List<Occurrence> occurrencesOf(String targetKey) {
        List<Occurrence> out = new ArrayList<>();
        for (List<Occurrence> list : occurrences.values()) {
            for (Occurrence occurrence : list) {
                if (occurrence.target != null && occurrence.target.key().equals(targetKey)) {
                    out.add(occurrence);
                }
            }
        }
        return out;
    }

    Target moduleTarget(Module module) {
        return modules.computeIfAbsent(module, m -> {
            Target target = new Target(Kind.MODULE, m.name, m.path, null, null, m, null);
            targets.add(target);
            return target;
        });
    }

    private static int width(Span span) {
        return span.endColumn - span.startColumn;
    }

    // ------------------------------------------------------------------
    // Pass 1: module-level declarations, referable before their position
    // ------------------------------------------------------------------

    private void declareModule(Module module) {
        for (Decl decl : module.decls) {
            if (decl instanceof Decl.Func func) {
                declareFunction(module, func, Kind.FUNCTION, null);
            } else if (decl instanceof Decl.ClassDecl classDecl) {
                declare(module, Kind.CLASS, classDecl.name, classDecl.nameSpan, classDecl.span, classDecl,
                        classDecl.symbol, null);
                for (Decl.Field field : classDecl.fields) {
                    declare(module, Kind.FIELD, field.name, field.nameSpan, field.span, field, field.symbol,
                            classDecl.name);
                }
                for (Decl.Func method : classDecl.methods) {
                    declareFunction(module, method, Kind.METHOD, classDecl.name);
                }
            } else if (decl instanceof Decl.EnumDecl enumDecl) {
                declare(module, Kind.ENUM, enumDecl.name, enumDecl.nameSpan, enumDecl.span, enumDecl,
                        enumDecl.symbol, null);
                for (int i = 0; i < enumDecl.cases.size(); i++) {
                    Span caseSpan = i < enumDecl.caseSpans.size() ? enumDecl.caseSpans.get(i) : null;
                    declare(module, Kind.ENUM_CASE, enumDecl.cases.get(i), caseSpan, caseSpan,
                            new EnumCase(enumDecl, enumDecl.cases.get(i)), null, enumDecl.name);
                }
            } else if (decl instanceof Decl.VariantDecl variantDecl) {
                declare(module, Kind.VARIANT, variantDecl.name, variantDecl.nameSpan, variantDecl.span,
                        variantDecl, variantDecl.symbol, null);
                for (Decl.VariantCase variantCase : variantDecl.cases) {
                    declare(module, Kind.VARIANT_CASE, variantCase.name, variantCase.nameSpan, variantCase.span,
                            variantCase, null, variantDecl.name);
                    for (Decl.Field field : variantCase.fields) {
                        declare(module, Kind.PAYLOAD_FIELD, field.name, field.nameSpan, field.span, field,
                                field.symbol, variantDecl.name + "." + variantCase.name);
                    }
                }
            }
        }
        for (Stmt stmt : module.topStatements) {
            if (stmt instanceof Stmt.VarDecl varDecl && varDecl.symbol != null
                    && varDecl.symbol.kind == Symbol.Kind.TOP_VAR) {
                declare(module, Kind.TOP_VAR, varDecl.name, varDecl.nameSpan, varDecl.span, varDecl,
                        varDecl.symbol, null);
            }
        }
    }

    private void declareFunction(Module module, Decl.Func func, Kind kind, String owner) {
        declare(module, kind, func.name, func.nameSpan, func.span, func, func.symbol, owner);
        String container = owner == null ? func.name : owner + "." + func.name;
        for (Decl.Param param : func.params) {
            declare(module, Kind.PARAM, param.name, param.nameSpan, param.nameSpan, param, param.symbol, container);
        }
    }

    private Target declare(Module module, Kind kind, String name, Span nameSpan, Span span, Object node,
                           Symbol symbol, String container) {
        Target target = new Target(kind, name, module.path, nameSpan, span, node, container);
        target.symbol = symbol;
        targets.add(target);
        byNode.put(node, target);
        if (symbol != null) {
            bySymbol.put(symbol, target);
        }
        if (nameSpan != null) {
            add(new Occurrence(module.path, nameSpan, target, null, true));
        }
        return target;
    }

    private void add(Occurrence occurrence) {
        occurrences.computeIfAbsent(occurrence.path, p -> new ArrayList<>()).add(occurrence);
    }

    // ------------------------------------------------------------------
    // Pass 2: bodies, local declarations and every reference
    // ------------------------------------------------------------------

    private final class Walker {
        private final Module module;
        private String container;

        Walker(Module module) {
            this.module = module;
        }

        void walkModule() {
            for (Decl.Import imp : module.imports) {
                Module imported = imp.fileImport ? importedModule(imp) : null;
                if (imported != null) {
                    Target target = moduleTarget(imported);
                    reference(imp.targetSpan, target, null);
                    reference(imp.aliasSpan, target, null);
                } else if (!imp.fileImport) {
                    Class<?> clazz = module.javaImports.get(sprig.compiler.sem.ImportNames.aliasFor(imp));
                    if (clazz != null) {
                        Info info = javaTypeInfo(clazz);
                        reference(imp.targetSpan, null, info);
                        reference(imp.aliasSpan, null, info);
                    }
                }
            }
            for (Decl decl : module.decls) {
                if (decl instanceof Decl.Func func) {
                    walkFunction(func, func.name);
                } else if (decl instanceof Decl.ClassDecl classDecl) {
                    for (Decl.Field field : classDecl.fields) {
                        typeRef(field.typeRef);
                        if (field.defaultExpr != null) {
                            container = classDecl.name;
                            expr(field.defaultExpr);
                        }
                    }
                    for (Decl.Func method : classDecl.methods) {
                        walkFunction(method, classDecl.name + "." + method.name);
                    }
                } else if (decl instanceof Decl.VariantDecl variantDecl) {
                    for (Decl.VariantCase variantCase : variantDecl.cases) {
                        for (Decl.Field field : variantCase.fields) {
                            typeRef(field.typeRef);
                        }
                    }
                } else if (decl instanceof Decl.Conform conform && conform.source != null) {
                    reference(conform.nameSpan, byNode.get(conform.source), null);
                }
            }
            container = null;
            statements(module.topStatements);
        }

        private Module importedModule(Decl.Import imp) {
            String alias = sprig.compiler.sem.ImportNames.aliasFor(imp);
            return alias == null ? null : module.importedModules.get(alias);
        }

        private void walkFunction(Decl.Func func, String name) {
            container = name;
            for (Decl.Param param : func.params) {
                typeRef(param.typeRef);
            }
            typeRef(func.returnTypeRef);
            for (TypeRef ref : func.throwsRefs) {
                typeRef(ref);
            }
            statements(func.body);
        }

        private void statements(List<Stmt> body) {
            if (body == null) {
                return;
            }
            for (Stmt stmt : body) {
                statement(stmt);
            }
        }

        private void statement(Stmt stmt) {
            if (stmt instanceof Stmt.VarDecl varDecl) {
                typeRef(varDecl.typeRef);
                // The resolver binds the name before its initializer, so do the same.
                if (!byNode.containsKey(varDecl)) {
                    local(varDecl.name, varDecl.nameSpan, varDecl.span, varDecl, varDecl.symbol);
                }
                expr(varDecl.init);
            } else if (stmt instanceof Stmt.Assign assign) {
                expr(assign.target);
                expr(assign.value);
            } else if (stmt instanceof Stmt.ExprStmt exprStmt) {
                expr(exprStmt.expr);
            } else if (stmt instanceof Stmt.Return ret) {
                expr(ret.value);
            } else if (stmt instanceof Stmt.Throw thr) {
                expr(thr.value);
            } else if (stmt instanceof Stmt.IfStmt ifStmt) {
                expr(ifStmt.cond);
                statements(ifStmt.thenBody);
                for (Stmt.IfStmt.Elif elif : ifStmt.elifs) {
                    expr(elif.cond);
                    statements(elif.body);
                }
                statements(ifStmt.elseBody);
            } else if (stmt instanceof Stmt.WhileStmt whileStmt) {
                expr(whileStmt.cond);
                statements(whileStmt.body);
            } else if (stmt instanceof Stmt.ForStmt forStmt) {
                expr(forStmt.iterable);
                local(forStmt.varName, forStmt.nameSpan, forStmt.span, forStmt, forStmt.symbol);
                statements(forStmt.body);
            } else if (stmt instanceof Stmt.Try tryStmt) {
                statements(tryStmt.body);
                for (Stmt.Try.CatchClause clause : tryStmt.catches) {
                    typeRef(clause.typeRef);
                    local(clause.name, clause.nameSpan, clause.nameSpan, clause, clause.symbol);
                    statements(clause.body);
                }
                statements(tryStmt.finallyBody);
            } else if (stmt instanceof Stmt.Match match) {
                match(match);
            }
        }

        private void match(Stmt.Match match) {
            expr(match.scrutinee);
            for (Stmt.Match.Branch branch : match.branches) {
                Type owner = branch.caseTypeRef.resolved;
                Target ownerTarget = owner == null ? null : typeTarget(owner);
                reference(branch.caseTypeRef.nameSpan, ownerTarget, null);
                Target caseTarget = null;
                if (owner instanceof VariantType variant) {
                    for (Decl.VariantCase variantCase : variant.decl.cases) {
                        if (variantCase.name.equals(branch.caseName)) {
                            caseTarget = byNode.get(variantCase);
                        }
                    }
                } else if (owner instanceof EnumType enumType) {
                    caseTarget = byNode.get(new EnumCase(enumType.decl, branch.caseName));
                }
                reference(branch.caseSpan, caseTarget, null);
                if (branch.binder != null) {
                    local(branch.binder, branch.binderSpan, branch.binderSpan, branch, branch.binderSymbol);
                }
                statements(branch.body);
            }
        }

        private void local(String name, Span nameSpan, Span span, Object node, Symbol symbol) {
            declare(module, Kind.LOCAL, name, nameSpan, span, node, symbol, container);
        }

        private void expr(Expr expr) {
            if (expr == null) {
                return;
            }
            if (expr instanceof Expr.Name name) {
                Occurrence occurrence = symbolReference(name.span, name.symbol);
                if (occurrence != null) {
                    occurrence.type = name.type;
                }
            } else if (expr instanceof Expr.FieldAccess access) {
                expr(access.receiver);
                Occurrence occurrence = member(access);
                if (occurrence != null) {
                    occurrence.type = access.type;
                }
            } else if (expr instanceof Expr.Call call) {
                if (call.callee instanceof Expr.FieldAccess access) {
                    expr(access.receiver);
                    Occurrence occurrence = member(access);
                    if (occurrence != null) {
                        occurrence.type = access.type;
                        occurrence.resultType = call.type;
                        occurrence.inferredCall = inferredCall(call);
                    }
                } else if (call.callee instanceof Expr.Name name) {
                    Occurrence occurrence = symbolReference(name.span, name.symbol);
                    if (occurrence != null) {
                        occurrence.type = name.type;
                        occurrence.inferredCall = inferredCall(call);
                    }
                } else {
                    expr(call.callee);
                }
                for (Expr.Arg arg : call.args) {
                    if (arg.name != null && arg.nameSpan != null) {
                        reference(arg.nameSpan, namedArgumentTarget(call.resolved, arg.name), null);
                    }
                    expr(arg.value);
                }
            } else if (expr instanceof Expr.Index index) {
                expr(index.receiver);
                expr(index.index);
            } else if (expr instanceof Expr.Subscript subscript) {
                expr(subscript.base);
                if (subscript.index != null) {
                    expr(subscript.index);
                } else if (subscript.resolvedIndex != null) {
                    expr(subscript.resolvedIndex);
                } else if (subscript.typeArgs != null) {
                    for (TypeRef ref : subscript.typeArgs) {
                        typeRef(ref);
                    }
                }
            } else if (expr instanceof Expr.Unary unary) {
                expr(unary.operand);
            } else if (expr instanceof Expr.Binary binary) {
                expr(binary.left);
                expr(binary.right);
            } else if (expr instanceof Expr.ListLit list) {
                for (Expr item : list.items) {
                    expr(item);
                }
            } else if (expr instanceof Expr.MapLit map) {
                for (int i = 0; i < map.keys.size(); i++) {
                    expr(map.keys.get(i));
                    expr(map.values.get(i));
                }
            } else if (expr instanceof Expr.Match match) {
                match(match.cases);
            } else if (expr instanceof Expr.Lambda lambda) {
                String outer = container;
                for (Decl.Param param : lambda.params) {
                    typeRef(param.typeRef);
                    declare(module, Kind.PARAM, param.name, param.nameSpan, param.nameSpan, param, param.symbol,
                            "lambda");
                }
                expr(lambda.body);
                container = outer;
            }
        }

        private Occurrence symbolReference(Span span, Symbol symbol) {
            if (symbol == null || span == null) {
                return null;
            }
            Target target = bySymbol.get(symbol);
            if (target == null && symbol.decl != null) {
                target = byNode.get(symbol.decl);
            }
            if (target == null && symbol.kind == Symbol.Kind.MODULE && symbol.module != null) {
                target = moduleTarget(symbol.module);
            }
            if (target != null) {
                return reference(span, target, null);
            }
            Info info = switch (symbol.kind) {
                case BUILTIN_TYPE -> builtinTypeInfo(symbol.name, symbol.type);
                case JAVA_TYPE -> symbol.javaClass == null ? null : javaTypeInfo(symbol.javaClass);
                case FUNCTION -> symbol.decl == null
                        ? new Info("func " + symbol.name + "(...)", "Built-in function.") : null;
                default -> null;
            };
            return info == null ? null : reference(span, null, info);
        }

        private Occurrence member(Expr.FieldAccess access) {
            ResolvedField resolved = access.resolved;
            if (resolved == null || access.nameSpan == null || resolved.kind == null) {
                return null;
            }
            return switch (resolved.kind) {
                case CLASS_FIELD, VARIANT_PAYLOAD -> reference(access.nameSpan, byNode.get(resolved.fieldDecl), null);
                case METHOD -> reference(access.nameSpan, byNode.get(resolved.methodDecl), null);
                case MODULE_FUNCTION, MODULE_VAR, MODULE_TYPE -> resolved.symbol == null
                        ? null : symbolReference(access.nameSpan, resolved.symbol);
                case ENUM_CASE -> reference(access.nameSpan,
                        byNode.get(new EnumCase(resolved.enumDecl, resolved.enumCaseName)), null);
                case VARIANT_CASE_VALUE -> reference(access.nameSpan, byNode.get(resolved.variantCase), null);
                case BUILTIN_METHOD -> reference(access.nameSpan, null, new Info(
                        access.name + "(...)",
                        "Built-in method of `" + display(resolved.receiverType) + "`."));
                case JVM_METHOD, JAVA_FIELD -> reference(access.nameSpan, null, new Info(
                        resolved.jvm == null ? access.name : resolved.jvm.owner.getSimpleName() + "." + access.name,
                        "Java " + (resolved.kind == ResolvedField.Kind.JAVA_FIELD ? "field" : "method")
                                + (resolved.jvm == null ? "." : " of `" + resolved.jvm.owner.getName() + "`.")));
                case ERROR_MESSAGE -> reference(access.nameSpan, null,
                        new Info("message: " + display(resolved.type), resolved.type instanceof NullableType
                                ? "The exception's message from Java's getMessage(), which may be null."
                                : "The error's message."));
            };
        }

        private Target namedArgumentTarget(ResolvedCall call, String name) {
            if (call == null || call.kind == null) {
                return null;
            }
            List<Decl.Field> fields = switch (call.kind) {
                case CLASS_CTOR -> call.classDecl == null ? List.of() : call.classDecl.fields;
                case VARIANT_CTOR -> call.variantCase == null ? List.of() : call.variantCase.fields;
                default -> List.of();
            };
            for (Decl.Field field : fields) {
                if (field.name.equals(name)) {
                    return byNode.get(field);
                }
            }
            return null;
        }

        private void typeRef(TypeRef ref) {
            if (ref == null) {
                return;
            }
            if (ref.functionResult != null) {
                for (TypeRef arg : ref.args) {
                    typeRef(arg);
                }
                typeRef(ref.functionResult);
                return;
            }
            if (ref.nameSpan != null && ref.resolved != null && !ref.resolved.isError()) {
                Type type = ref.resolved.nonNull();
                Target target = typeTarget(type);
                if (target != null) {
                    reference(ref.nameSpan, target, null);
                } else {
                    Info info = typeInfo(type);
                    if (info != null) {
                        reference(ref.nameSpan, null, info);
                    }
                }
            }
            for (TypeRef arg : ref.args) {
                typeRef(arg);
            }
        }

        private Occurrence reference(Span span, Target target, Info info) {
            if (span == null || (target == null && info == null)) {
                return null;
            }
            Occurrence occurrence = new Occurrence(module.path, span, target, info, false);
            add(occurrence);
            return occurrence;
        }
    }

    private Target typeTarget(Type type) {
        if (type instanceof ClassType classType) {
            return byNode.get(classType.decl);
        }
        if (type instanceof EnumType enumType) {
            return byNode.get(enumType.decl);
        }
        if (type instanceof VariantType variantType) {
            return byNode.get(variantType.decl);
        }
        if (type instanceof VariantCaseType caseType) {
            return byNode.get(caseType.variantCase);
        }
        return null;
    }

    private static Info typeInfo(Type type) {
        if (type instanceof NativeType nativeType) {
            return builtinTypeInfo(nativeType.display(), nativeType);
        }
        if (type instanceof ListType || type instanceof MapType) {
            String name = type.display();
            int bracket = name.indexOf('[');
            return new Info(bracket < 0 ? name : name.substring(0, bracket),
                    "Built-in collection type.");
        }
        if (type instanceof JavaType javaType) {
            return javaTypeInfo(javaType.clazz);
        }
        if (type instanceof TypeParameterType parameter) {
            return new Info(parameter.display(), "Type parameter.");
        }
        return null;
    }

    static Info builtinTypeInfo(String name, Type type) {
        if (type instanceof JavaType javaType && javaType.clazz == sprig.runtime.SprigError.class) {
            return new Info("Error", "Built-in error type; `catch problem: Error` catches it.");
        }
        String note = switch (name) {
            case "Int" -> "Signed 64-bit integer; overflow is a runtime error.";
            case "Int32" -> "Signed 32-bit integer; overflow is a runtime error.";
            case "Float" -> "IEEE 754 binary64 floating point.";
            case "Float32" -> "IEEE 754 binary32 floating point.";
            case "Decimal" -> "Exact decimal number.";
            case "BigInt" -> "Arbitrary-precision integer.";
            case "Bool" -> "true or false.";
            case "String" -> "Immutable text; lengths and positions count Unicode code points.";
            case "Unit" -> "The result of a function that returns nothing.";
            default -> "Built-in type.";
        };
        return new Info(name, note);
    }

    static Info javaTypeInfo(Class<?> clazz) {
        String kind = clazz.isInterface() ? "interface" : clazz.isEnum() ? "enum" : "class";
        return new Info(kind + " " + clazz.getName(), "Java " + kind + " from the classpath.");
    }

    static String display(Type type) {
        return type == null ? "?" : type.display();
    }

    /**
     * A generic call written without type arguments, as it reads with the
     * ones the compiler inferred: {@code identity[Int](...) -> Int},
     * {@code Box[Int](...)} or {@code Option[Int].Some(...)}. Null for any
     * other call, including one that writes its type arguments.
     */
    static String inferredCall(Expr.Call call) {
        ResolvedCall resolved = call.resolved;
        if (resolved == null || resolved.typeArgs.isEmpty() || call.callee instanceof Expr.Subscript
                || call.callee instanceof Expr.FieldAccess access && access.receiver instanceof Expr.Subscript) {
            return null;
        }
        List<String> written = new ArrayList<>();
        for (Type type : resolved.typeArgs) {
            written.add(display(type));
        }
        String arguments = "[" + String.join(", ", written) + "]";
        return switch (resolved.kind) {
            case FUNCTION, MODULE_FUNCTION, METHOD -> resolved.methodDecl == null ? null
                    : resolved.methodDecl.name + arguments + "(...) -> " + display(resolved.returnType);
            case CLASS_CTOR -> resolved.classDecl == null ? null : resolved.classDecl.name + arguments + "(...)";
            case VARIANT_CTOR -> resolved.returnType instanceof VariantCaseType caseType
                    ? caseType.variant.name + arguments + "." + caseType.variantCase.name + "(...)" : null;
            default -> null;
        };
    }
}
