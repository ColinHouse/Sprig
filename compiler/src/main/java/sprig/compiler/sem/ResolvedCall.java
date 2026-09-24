package sprig.compiler.sem;

import java.util.List;
import sprig.compiler.ast.Decl;
import sprig.compiler.types.FunctionType;
import sprig.compiler.types.Type;

/** How a call expression was resolved; consumed by the code generator. */
public final class ResolvedCall {
    public enum Kind {
        CLASS_CTOR, VARIANT_CTOR, FUNCTION, METHOD, FUNCTION_VALUE, BUILTIN, BUILTIN_METHOD,
        JVM_CTOR, JVM_METHOD, MODULE_FUNCTION
    }

    public Kind kind;
    public Symbol symbol;
    public JvmMember jvm;
    public String builtinId;
    public Decl.ClassDecl classDecl;
    public Decl.VariantCase variantCase;
    public Decl.Func methodDecl;
    public FunctionType functionType;
    public List<Type> paramTypes = List.of();
    public Type returnType;
    public Type receiverType;
    public boolean staticJvm;

    public static ResolvedCall of(Kind kind, Type returnType) {
        ResolvedCall call = new ResolvedCall();
        call.kind = kind;
        call.returnType = returnType;
        return call;
    }
}
