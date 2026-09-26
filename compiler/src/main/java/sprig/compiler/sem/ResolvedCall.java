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
    /** v0.8 explicit generic type arguments at this use site, when present. */
    public List<Type> typeArgs = List.of();
    /** v0.8 substitution used for this call: T := concrete argument. */
    public java.util.Map<sprig.compiler.types.TypeParameterType, Type> substitution = java.util.Map.of();
    /** Instantiated receiver/constructor type for generic classes and variants. */
    public Type instantiatedType;

    public static ResolvedCall of(Kind kind, Type returnType) {
        ResolvedCall call = new ResolvedCall();
        call.kind = kind;
        call.returnType = returnType;
        return call;
    }
}
