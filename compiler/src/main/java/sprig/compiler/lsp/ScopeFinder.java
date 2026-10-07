package sprig.compiler.lsp;

import java.util.ArrayList;
import java.util.List;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Expr;
import sprig.compiler.ast.Module;
import sprig.compiler.ast.Stmt;
import sprig.compiler.ast.TypeRef;

/**
 * Finds the completion placeholder in a parsed module and records what is
 * in scope there: the local bindings declared before it in enclosing blocks,
 * and the enclosing function, class and generic parameters.
 */
final class ScopeFinder {
    /** A local name visible at the placeholder. */
    record Binding(String name, String detail, boolean parameter) {
    }

    private final String placeholder;
    private final List<Binding> scope = new ArrayList<>();

    /** The placeholder node: an {@link Expr.Name}, {@link Expr.FieldAccess} or {@link TypeRef}. */
    Object found;
    List<Binding> visible = List.of();
    Decl.Func function;
    Decl.ClassDecl classDecl;
    List<String> typeParams = List.of();
    /** Index of the top-level statement holding the placeholder, or -1 inside declarations. */
    int topStatement = -1;

    private Decl.Func currentFunction;
    private Decl.ClassDecl currentClass;
    private List<String> currentTypeParams = List.of();

    ScopeFinder(String placeholder) {
        this.placeholder = placeholder;
    }

    ScopeFinder find(Module module) {
        for (Decl decl : module.decls) {
            decl(decl);
            if (found != null) {
                return this;
            }
        }
        currentFunction = null;
        currentClass = null;
        currentTypeParams = List.of();
        for (int i = 0; i < module.topStatements.size(); i++) {
            statement(module.topStatements.get(i));
            if (found != null) {
                topStatement = i;
                return this;
            }
        }
        return this;
    }

    private void decl(Decl decl) {
        if (decl instanceof Decl.Func func) {
            currentClass = null;
            function(func, func.typeParams);
        } else if (decl instanceof Decl.ClassDecl classDecl) {
            currentClass = classDecl;
            currentFunction = null;
            currentTypeParams = classDecl.typeParams;
            for (Decl.Field field : classDecl.fields) {
                typeRef(field.typeRef);
                expr(field.defaultExpr);
                if (found != null) {
                    return;
                }
            }
            for (Decl.Func method : classDecl.methods) {
                function(method, classDecl.typeParams);
                if (found != null) {
                    return;
                }
            }
        } else if (decl instanceof Decl.VariantDecl variant) {
            currentClass = null;
            currentFunction = null;
            currentTypeParams = variant.typeParams;
            for (Decl.VariantCase variantCase : variant.cases) {
                for (Decl.Field field : variantCase.fields) {
                    typeRef(field.typeRef);
                }
            }
        }
    }

    private void function(Decl.Func func, List<String> typeParams) {
        currentFunction = func;
        currentTypeParams = typeParams;
        int mark = scope.size();
        for (Decl.Param param : func.params) {
            typeRef(param.typeRef);
        }
        typeRef(func.returnTypeRef);
        for (TypeRef ref : func.throwsRefs) {
            typeRef(ref);
        }
        for (Decl.Param param : func.params) {
            scope.add(new Binding(param.name, param.name + ": " + param.typeRef.display(), true));
        }
        block(func.body);
        truncate(mark);
    }

    private void block(List<Stmt> body) {
        if (body == null || found != null) {
            return;
        }
        int mark = scope.size();
        for (Stmt stmt : body) {
            statement(stmt);
            if (found != null) {
                return;
            }
        }
        truncate(mark);
    }

    private void statement(Stmt stmt) {
        if (found != null) {
            return;
        }
        if (stmt instanceof Stmt.VarDecl varDecl) {
            typeRef(varDecl.typeRef);
            expr(varDecl.init);
            scope.add(new Binding(varDecl.name, (varDecl.mutable ? "var " : "let ") + varDecl.name
                    + (varDecl.typeRef == null ? "" : ": " + varDecl.typeRef.display()), false));
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
            block(ifStmt.thenBody);
            for (Stmt.IfStmt.Elif elif : ifStmt.elifs) {
                expr(elif.cond);
                block(elif.body);
            }
            block(ifStmt.elseBody);
        } else if (stmt instanceof Stmt.WhileStmt whileStmt) {
            expr(whileStmt.cond);
            block(whileStmt.body);
        } else if (stmt instanceof Stmt.ForStmt forStmt) {
            expr(forStmt.iterable);
            int mark = scope.size();
            scope.add(new Binding(forStmt.varName, forStmt.varName, false));
            block(forStmt.body);
            truncate(mark);
        } else if (stmt instanceof Stmt.Try tryStmt) {
            block(tryStmt.body);
            for (Stmt.Try.CatchClause clause : tryStmt.catches) {
                typeRef(clause.typeRef);
                int mark = scope.size();
                scope.add(new Binding(clause.name, clause.name + ": " + clause.typeRef.display(), false));
                block(clause.body);
                truncate(mark);
            }
            block(tryStmt.finallyBody);
        } else if (stmt instanceof Stmt.Match match) {
            match(match);
        }
    }

    private void match(Stmt.Match match) {
        expr(match.scrutinee);
        for (Stmt.Match.Branch branch : match.branches) {
            if (found != null) {
                return;
            }
            int mark = scope.size();
            if (branch.binder != null) {
                scope.add(new Binding(branch.binder, branch.binder + ": " + branch.caseTypeRef.display()
                        + (branch.caseTypeRef.parts.isEmpty() ? "" : ".") + branch.caseName, false));
            }
            block(branch.body);
            truncate(mark);
        }
    }

    private void expr(Expr expr) {
        if (expr == null || found != null) {
            return;
        }
        if (expr instanceof Expr.Name name) {
            if (name.name.equals(placeholder)) {
                capture(name);
            }
        } else if (expr instanceof Expr.FieldAccess access) {
            expr(access.receiver);
            if (found == null && access.name.equals(placeholder)) {
                capture(access);
            }
        } else if (expr instanceof Expr.Call call) {
            expr(call.callee);
            for (Expr.Arg arg : call.args) {
                expr(arg.value);
            }
        } else if (expr instanceof Expr.Index index) {
            expr(index.receiver);
            expr(index.index);
        } else if (expr instanceof Expr.Subscript subscript) {
            expr(subscript.base);
            expr(subscript.index);
            if (subscript.typeArgs != null) {
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
        } else if (expr instanceof Expr.If ifExpr) {
            for (int i = 0; i < ifExpr.conditions.size(); i++) {
                expr(ifExpr.conditions.get(i));
                expr(ifExpr.values.get(i));
            }
            if (ifExpr.elseValue != null) {
                expr(ifExpr.elseValue);
            }
        } else if (expr instanceof Expr.Lambda lambda) {
            int mark = scope.size();
            for (Decl.Param param : lambda.params) {
                typeRef(param.typeRef);
                scope.add(new Binding(param.name, param.name + ": " + param.typeRef.display(), true));
            }
            expr(lambda.body);
            truncate(mark);
        }
    }

    private void typeRef(TypeRef ref) {
        if (ref == null || found != null) {
            return;
        }
        if (!ref.parts.isEmpty() && ref.parts.get(ref.parts.size() - 1).equals(placeholder)) {
            capture(ref);
            return;
        }
        for (TypeRef arg : ref.args) {
            typeRef(arg);
        }
        typeRef(ref.functionResult);
    }

    private void capture(Object node) {
        found = node;
        visible = List.copyOf(scope);
        function = currentFunction;
        classDecl = currentClass;
        typeParams = currentTypeParams;
    }

    private void truncate(int mark) {
        if (found != null) {
            return;
        }
        while (scope.size() > mark) {
            scope.remove(scope.size() - 1);
        }
    }
}
