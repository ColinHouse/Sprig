package sprig.compiler.sem;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Module;

/** Lexical scope used while binding names. */
public final class Scope {
    public final Scope parent;
    public final Module module;
    public final Decl.Func function;       // enclosing function, may be null
    public final Decl.ClassDecl classDecl; // enclosing class, may be null
    public final Map<String, Symbol> locals = new LinkedHashMap<>();
    /** Fields that must not be referenced here (class field initializers). */
    public final Set<String> forbiddenFields = new LinkedHashSet<>();

    public Scope(Scope parent, Module module, Decl.Func function, Decl.ClassDecl classDecl) {
        this.parent = parent;
        this.module = module;
        this.function = function;
        this.classDecl = classDecl;
    }

    public Symbol findLocal(String name) {
        for (Scope scope = this; scope != null; scope = scope.parent) {
            Symbol symbol = scope.locals.get(name);
            if (symbol != null) {
                return symbol;
            }
        }
        return null;
    }

    public Symbol resolve(String name) {
        Symbol local = findLocal(name);
        if (local != null) {
            return local;
        }
        Scope root = this;
        while (root.parent != null) {
            root = root.parent;
        }
        if (root.classDecl != null) {
            for (Decl.Field field : root.classDecl.fields) {
                if (field.name.equals(name)) {
                    return field.symbol;
                }
            }
            for (Decl.Func method : root.classDecl.methods) {
                if (method.name.equals(name)) {
                    return method.symbol;
                }
            }
        }
        ModuleScope moduleScope = root.module.scope;
        Symbol symbol = moduleScope.topVars.get(name);
        if (symbol != null) {
            return symbol;
        }
        symbol = moduleScope.functions.get(name);
        if (symbol != null) {
            return symbol;
        }
        return null;
    }

    /** Whether this scope chain is inside a class method. */
    public Decl.ClassDecl enclosingClass() {
        for (Scope scope = this; scope != null; scope = scope.parent) {
            if (scope.classDecl != null) {
                return scope.classDecl;
            }
        }
        return null;
    }

    public Decl.Func enclosingFunction() {
        for (Scope scope = this; scope != null; scope = scope.parent) {
            if (scope.function != null) {
                return scope.function;
            }
        }
        return null;
    }
}
