package sprig.compiler.sem;

import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Module;
import sprig.compiler.types.Type;

/** How a field access was resolved; consumed by the code generator. */
public final class ResolvedField {
    public enum Kind {
        CLASS_FIELD, VARIANT_PAYLOAD, ENUM_CASE, VARIANT_CASE_VALUE,
        MODULE_VAR, MODULE_FUNCTION, MODULE_TYPE, JAVA_FIELD, ERROR_MESSAGE,
        METHOD, JVM_METHOD, BUILTIN_METHOD
    }

    public Kind kind;
    public Symbol symbol;
    public Decl.Field fieldDecl;
    public Decl.VariantCase variantCase;
    public Decl.EnumDecl enumDecl;
    public String enumCaseName;
    public JvmMember jvm;
    public Module module;
    public Type type;
    public Decl.Func methodDecl;
    public Decl.ClassDecl classDecl;
    public String builtinId;
    public Type receiverType;
    public boolean constructorRef;
    public boolean payloadless;
    /** v0.8 substitution used to instantiate a generic member or payload. */
    public java.util.Map<sprig.compiler.types.TypeParameterType, Type> substitution = java.util.Map.of();
    /** v0.8 explicit generic type arguments at this use site, when present. */
    public java.util.List<Type> typeArgs = java.util.List.of();
}
