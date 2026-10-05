package sprig.compiler.gen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import sprig.compiler.Compilation;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Expr;
import sprig.compiler.ast.Module;
import sprig.compiler.ast.Stmt;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Span;
import sprig.compiler.sem.ResolvedCall;
import sprig.compiler.sem.ResolvedField;
import sprig.compiler.sem.Symbol;
import sprig.compiler.sem.Semantics;
import sprig.compiler.types.ClassType;
import sprig.compiler.types.EnumType;
import sprig.compiler.types.FunctionType;
import sprig.compiler.types.JavaType;
import sprig.compiler.types.ListType;
import sprig.compiler.types.MapType;
import sprig.compiler.types.NativeType;
import sprig.compiler.types.NullableType;
import sprig.compiler.types.Type;
import sprig.compiler.types.Substitution;
import sprig.compiler.types.TypeParameterType;
import sprig.compiler.types.VariantCaseType;
import sprig.compiler.types.VariantType;

/**
 * Java source generation over the checked AST. All semantic decisions were made
 * by name resolution + type checking; this phase only lowers to Java.
 */
public final class JavaGenerator {
    public static final String PACKAGE = "sprig.user";
    public static final String PACKAGE_DIR = "sprig/user";

    private static final Set<String> JAVA_RESERVED = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "true", "false", "null", "_",
            // Restricted identifiers: legal for some Java declarations but not as
            // type names (record, var, yield, sealed, permits), so never emitted.
            "record", "var", "yield", "sealed", "permits");

    /**
     * Members every generated class inherits from java.lang.Object or receives
     * as generated value methods. A Sprig method with one of these names is
     * renamed instead of (illegally) overriding or clashing with them.
     */
    private static final Set<String> OBJECT_MEMBER_NAMES = Set.of(
            "clone", "equals", "finalize", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait");

    /** Package roots that generated code always spells as fully qualified names. */
    private static final Set<String> BUILTIN_PACKAGE_ROOTS = Set.of("java", "javax", "sprig");

    public static final class Output {
        public final Map<String, String> sources = new LinkedHashMap<>();
        public final Map<String, Map<Integer, Span>> lineMaps = new LinkedHashMap<>();
        public final Map<String, String> uris = new LinkedHashMap<>();
        public String mainClass;
    }

    private final Compilation compilation;
    private final Diagnostics diagnostics;
    private final Output output = new Output();
    // A Java variable or member type named like a package root obscures that
    // package (JLS 6.4.2), breaking every fully qualified name below it.
    private final Set<String> reservedRoots;
    private final Set<String> referencedRoots = new java.util.HashSet<>();
    private final Set<String> emittedNames = new java.util.HashSet<>();
    private final Map<Decl, String> typeNames = new IdentityHashMap<>();
    private final Map<Module, String> moduleClassNames = new IdentityHashMap<>();
    private final Map<Symbol, String> localNames = new HashMap<>();
    private final Map<String, Integer> localNameCounts = new HashMap<>();
    private int tempCounter;
    private int lambdaDepth;
    private Module currentModule;
    private Decl.ClassDecl currentClassDecl;
    private Type currentReturnType;

    public JavaGenerator(Compilation compilation, Diagnostics diagnostics) {
        this(compilation, diagnostics, BUILTIN_PACKAGE_ROOTS);
    }

    private JavaGenerator(Compilation compilation, Diagnostics diagnostics, Set<String> reservedRoots) {
        this.compilation = compilation;
        this.diagnostics = diagnostics;
        this.reservedRoots = reservedRoots;
    }

    public Output generate() {
        assignModuleNames();
        assignTypeNames();
        for (Module module : compilation.modules) {
            currentModule = module;
            generateModule(module);
            for (Decl decl : module.decls) {
                if (decl instanceof Decl.ClassDecl classDecl) {
                    generateClass(decl, classDecl);
                } else if (decl instanceof Decl.VariantDecl variantDecl) {
                    generateVariant(variantDecl);
                } else if (decl instanceof Decl.EnumDecl enumDecl) {
                    generateEnum(enumDecl);
                }
            }
        }
        if (compilation.main != null) {
            output.mainClass = PACKAGE + "." + moduleClassNames.get(compilation.main);
        }
        // JVM package roots are only known once their fully qualified names have
        // been emitted. If a user identifier was emitted with the same spelling,
        // generate again with those roots reserved; the second pass emits the
        // same JVM names, so it cannot discover new collisions.
        Set<String> collisions = new java.util.HashSet<>(referencedRoots);
        collisions.retainAll(emittedNames);
        if (!collisions.isEmpty()) {
            Set<String> roots = new java.util.HashSet<>(reservedRoots);
            roots.addAll(referencedRoots);
            return new JavaGenerator(compilation, diagnostics, Set.copyOf(roots)).generate();
        }
        return output;
    }

    // ------------------------------------------------------------------
    // Naming
    // ------------------------------------------------------------------

    private void assignTypeNames() {
        // All emitted classes share one Java package, including module holders
        // and the binary names of nested variant cases.
        Set<String> usedNames = new java.util.HashSet<>(moduleClassNames.values());
        Map<String, List<Decl>> byName = new LinkedHashMap<>();
        for (Module module : compilation.modules) {
            for (Decl decl : module.decls) {
                if (!(decl instanceof Decl.Func)) {
                    byName.computeIfAbsent(decl.name, k -> new ArrayList<>()).add(decl);
                }
            }
        }
        for (Map.Entry<String, List<Decl>> entry : byName.entrySet()) {
            List<Decl> decls = entry.getValue();
            for (Decl decl : decls) {
                String base = decls.size() == 1 ? "$" + entry.getKey()
                        : "$" + sanitize(findModule(decl).name) + "_" + entry.getKey();
                String name = base;
                int suffix = 2;
                while (!typeNameAvailable(name, decl, usedNames)) {
                    name = base + "$" + suffix++;
                }
                usedNames.add(name);
                if (decl instanceof Decl.VariantDecl variant) {
                    for (Decl.VariantCase variantCase : variant.cases) {
                        usedNames.add(name + "$" + mangle(variantCase.name));
                    }
                }
                typeNames.put(decl, PACKAGE + "." + name);
            }
        }
    }

    private boolean typeNameAvailable(String name, Decl decl, Set<String> usedNames) {
        if (usedNames.contains(name)) {
            return false;
        }
        if (decl instanceof Decl.VariantDecl variant) {
            for (Decl.VariantCase variantCase : variant.cases) {
                if (usedNames.contains(name + "$" + mangle(variantCase.name))) {
                    return false;
                }
            }
        }
        return true;
    }

    private Module findModule(Decl decl) {
        for (Module module : compilation.modules) {
            if (module.decls.contains(decl)) {
                return module;
            }
        }
        return compilation.modules.get(0);
    }

    private void assignModuleNames() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Set<String> usedNames = new java.util.HashSet<>();
        for (Module module : compilation.modules) {
            String base = "$M_" + sanitize(module.name);
            int count = counts.getOrDefault(base, 0) + 1;
            String name = count == 1 ? base : base + "$" + count;
            while (!usedNames.add(name)) {
                name = base + "$" + ++count;
            }
            counts.put(base, count);
            moduleClassNames.put(module, name);
        }
    }

    private String moduleClassName(Module module) {
        return PACKAGE + "." + moduleClassNames.get(module);
    }

    private static String sanitize(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            sb.append(Character.isJavaIdentifierPart(c) ? c : '_');
        }
        return sb.isEmpty() ? "anon" : sb.toString();
    }

    private String mangle(String name) {
        if (JAVA_RESERVED.contains(name) || reservedRoots.contains(name)) {
            return name + "$";
        }
        emittedNames.add(name);
        return name;
    }

    /**
     * Sprig method names that java.lang.Object or the generated value methods
     * already own are renamed. Foreign conformance methods keep their name: it
     * is the Java interface method they implement.
     */
    private String methodName(Decl.Func func) {
        if (!func.foreignBoundary && OBJECT_MEMBER_NAMES.contains(func.name)) {
            return func.name + "$";
        }
        return mangle(func.name);
    }

    /** Text for a generated // comment: no line breaks and no Java Unicode escapes. */
    private static String commentText(Object text) {
        return String.valueOf(text).replace("\\", "\\\\").replace('\n', ' ').replace('\r', ' ');
    }

    private String localName(Symbol symbol) {
        return localNames.computeIfAbsent(symbol, s -> {
            String base = mangle(s.name);
            int count = localNameCounts.merge(base, 1, Integer::sum);
            return count == 1 ? base : base + "$" + count;
        });
    }

    private String freshTemp(String base) {
        return "$" + base + (tempCounter++);
    }

    private void resetLocals() {
        localNames.clear();
        localNameCounts.clear();
        tempCounter = 0;
    }

    private String fnName(Decl.Func func) {
        return "fn$" + func.name;
    }

    // ------------------------------------------------------------------
    // Type mapping
    // ------------------------------------------------------------------

    private String javaType(Type type) {
        if (type == null || type == NativeType.ERROR || type == NativeType.NULL) {
            return "java.lang.Object";
        }
        // v0.8 generics are erased and boxed: a type parameter is Object in
        // generated Java, and casts/unboxing are inserted at use sites.
        if (type instanceof TypeParameterType) {
            return "java.lang.Object";
        }
        if (type == NativeType.INT) {
            return "long";
        }
        if (type == NativeType.INT32) {
            return "int";
        }
        if (type == NativeType.FLOAT) {
            return "double";
        }
        if (type == NativeType.FLOAT32) {
            return "float";
        }
        if (type == NativeType.DECIMAL) {
            return "sprig.runtime.SprigDecimal";
        }
        if (type == NativeType.BIGINT) {
            return "sprig.runtime.SprigBigInt";
        }
        if (type == NativeType.BOOL) {
            return "boolean";
        }
        if (type == NativeType.STRING) {
            return "java.lang.String";
        }
        if (type == NativeType.UNIT) {
            return "void";
        }
        if (type instanceof NullableType nullable) {
            return boxedJavaType(nullable.inner);
        }
        // A type whose arguments still contain a type parameter cannot be
        // emitted as a parameterized Java type: T erases to Object, and
        // SprigList<Object> is not SprigList<String>. Erase the whole shape to
        // its raw runtime class and cast at the substituted boundary.
        if (containsTypeParameter(type)) {
            if (type instanceof ListType list) {
                return list.mutable ? "sprig.runtime.SprigMutableList" : "sprig.runtime.SprigList";
            }
            if (type instanceof MapType map) {
                return map.mutable ? "sprig.runtime.SprigMutableMap" : "sprig.runtime.SprigMap";
            }
            if (type instanceof FunctionType function) {
                return "sprig.runtime.Fn" + function.params.size();
            }
        }
        if (type instanceof ListType list) {
            String raw = list.mutable ? "sprig.runtime.SprigMutableList" : "sprig.runtime.SprigList";
            return raw + "<" + boxedJavaType(list.element) + ">";
        }
        if (type instanceof MapType map) {
            String raw = map.mutable ? "sprig.runtime.SprigMutableMap" : "sprig.runtime.SprigMap";
            return raw + "<" + boxedJavaType(map.key) + ", " + boxedJavaType(map.value) + ">";
        }
        if (type instanceof ClassType classType) {
            return typeNames.get(classType.decl);
        }
        if (type instanceof EnumType enumType) {
            return typeNames.get(enumType.decl);
        }
        if (type instanceof VariantType variantType) {
            return typeNames.get(variantType.decl);
        }
        if (type instanceof VariantCaseType caseType) {
            return typeNames.get(caseType.variant) + "." + mangle(caseType.variantCase.name);
        }
        if (type instanceof JavaType javaType) {
            if (javaType.args.isEmpty() || containsTypeParameter(javaType)) {
                return sourceName(javaType.clazz);
            }
            StringBuilder parameterized = new StringBuilder(sourceName(javaType.clazz)).append('<');
            for (int i = 0; i < javaType.args.size(); i++) {
                if (i > 0) {
                    parameterized.append(", ");
                }
                parameterized.append(boxedJavaType(javaType.args.get(i)));
            }
            return parameterized.append('>').toString();
        }
        if (type instanceof FunctionType functionType) {
            StringBuilder sb = new StringBuilder("sprig.runtime.Fn").append(functionType.params.size()).append('<');
            for (Type param : functionType.params) {
                sb.append(boxedJavaType(param)).append(", ");
            }
            if (functionType.result == NativeType.UNIT) {
                sb.append("java.lang.Void");
            } else {
                sb.append(boxedJavaType(functionType.result));
            }
            return sb.append('>').toString();
        }
        return "java.lang.Object";
    }

    private String boxedJavaType(Type type) {
        if (type == null) {
            return "java.lang.Object";
        }
        if (type == NativeType.INT) {
            return "java.lang.Long";
        }
        if (type == NativeType.INT32) {
            return "java.lang.Integer";
        }
        if (type == NativeType.FLOAT) {
            return "java.lang.Double";
        }
        if (type == NativeType.FLOAT32) {
            return "java.lang.Float";
        }
        if (type == NativeType.DECIMAL) {
            return "sprig.runtime.SprigDecimal";
        }
        if (type == NativeType.BIGINT) {
            return "sprig.runtime.SprigBigInt";
        }
        if (type == NativeType.BOOL) {
            return "java.lang.Boolean";
        }
        if (type == NativeType.STRING) {
            return "java.lang.String";
        }
        if (type == NativeType.UNIT || type == NativeType.ERROR || type == NativeType.NULL) {
            return "java.lang.Object";
        }
        return javaType(type);
    }

    private String sourceName(Class<?> clazz) {
        if (clazz.isArray()) {
            return sourceName(clazz.getComponentType()) + "[]";
        }
        String canonical = clazz.getCanonicalName();
        String name = canonical != null ? canonical : clazz.getName().replace('$', '.');
        int dot = name.indexOf('.');
        if (dot > 0) {
            referencedRoots.add(name.substring(0, dot));
        }
        return name;
    }

    // ------------------------------------------------------------------
    // Types
    // ------------------------------------------------------------------

    private void generateEnum(Decl.EnumDecl decl) {
        JavaWriter w = writer(decl.span);
        w.line("package " + PACKAGE + ";");
        w.blank();
        w.line("// Sprig enum " + commentText(currentModule.name) + ":" + decl.span.display());
        w.map(decl.span);
        String enumName = simpleName(typeNames.get(decl));
        boolean renamed = decl.cases.stream().anyMatch(name -> !mangle(name).equals(name));
        if (!renamed) {
            w.line("public enum " + enumName + " { " + String.join(", ", decl.cases) + " }");
        } else {
            // A case spelled like a Java keyword gets a legal constant name; the
            // constant still prints with its Sprig spelling.
            w.open("public enum " + enumName);
            List<String> constants = new ArrayList<>();
            for (String name : decl.cases) {
                constants.add(mangle(name) + "(\"" + name + "\")");
            }
            w.line(String.join(", ", constants) + ";");
            w.blank();
            w.line("private final java.lang.String sprigName;");
            w.blank();
            w.line(enumName + "(java.lang.String sprigName) { this.sprigName = sprigName; }");
            w.blank();
            w.line("@Override public java.lang.String toString() { return sprigName; }");
            w.close();
        }
        emitFile(PACKAGE_DIR + "/" + simpleName(typeNames.get(decl)) + ".java", w);
    }

    private void generateVariant(Decl.VariantDecl decl) {
        JavaWriter w = writer(decl.span);
        w.line("package " + PACKAGE + ";");
        w.blank();
        w.line("// Sprig variant " + commentText(currentModule.name) + ":" + decl.span.display());
        w.map(decl.span);
        w.open("public interface " + simpleName(typeNames.get(decl)));
        for (Decl.VariantCase variantCase : decl.cases) {
            generateVariantCase(w, decl, variantCase);
        }
        w.close();
        String file = PACKAGE_DIR + "/" + simpleName(typeNames.get(decl)) + ".java";
        emitFile(file, w);
    }

    private void generateVariantCase(JavaWriter w, Decl.VariantDecl variant, Decl.VariantCase variantCase) {
        String className = mangle(variantCase.name);
        w.blank();
        w.line("// Sprig case " + variant.name + "." + variantCase.name);
        w.map(variantCase.span);
        w.open("final class " + className + " implements " + simpleName(typeNames.get(variant)));
        for (Decl.Field field : variantCase.fields) {
            w.line("public final " + javaType(field.type) + " " + mangle(field.name) + ";");
        }
        StringBuilder ctor = new StringBuilder("public " + className + "(");
        for (int i = 0; i < variantCase.fields.size(); i++) {
            if (i > 0) {
                ctor.append(", ");
            }
            Decl.Field field = variantCase.fields.get(i);
            ctor.append(javaType(field.type)).append(' ').append(mangle(field.name));
        }
        ctor.append(") {");
        w.line(ctor.toString());
        for (Decl.Field field : variantCase.fields) {
            w.line("    this." + mangle(field.name) + " = " + mangle(field.name) + ";");
        }
        w.line("}");
        emitVariantCaseObjectMethods(w, variant, variantCase);
        w.close();
    }

    private void emitVariantCaseObjectMethods(JavaWriter w, Decl.VariantDecl variant, Decl.VariantCase variantCase) {
        w.blank();
        w.open("@Override public java.lang.String toString()");
        StringBuilder text = new StringBuilder("return \"").append(variantCase.name);
        if (!variantCase.fields.isEmpty()) {
            text.append('(');
            for (int i = 0; i < variantCase.fields.size(); i++) {
                if (i > 0) {
                    text.append(", ");
                }
                text.append(variantCase.fields.get(i).name).append("=\" + sprig.runtime.SprigRuntime.str(")
                    .append(mangle(variantCase.fields.get(i).name)).append(") + \"");
            }
            text.append(')');
        }
        text.append("\";");
        w.line(text.toString());
        w.close();
        w.blank();
        w.open("@Override public boolean equals(java.lang.Object other)");
        w.line("if (this == other) return true;");
        String className = mangle(variantCase.name);
        w.line("if (!(other instanceof " + className + ")) return false;");
        w.line(className + " that = (" + className + ") other;");
        if (variantCase.fields.isEmpty()) {
            w.line("return true;");
        } else {
            StringBuilder comparison = new StringBuilder("return ");
            for (int i = 0; i < variantCase.fields.size(); i++) {
                if (i > 0) {
                    comparison.append(" && ");
                }
                Decl.Field field = variantCase.fields.get(i);
                String name = mangle(field.name);
                if (isPrimitive(field.type)) {
                    comparison.append("this.").append(name).append(" == that.").append(name);
                } else {
                    comparison.append("sprig.runtime.SprigRuntime.equalsValue(this.").append(name)
                            .append(", that.").append(name).append(')');
                }
            }
            w.line(comparison.append(';').toString());
        }
        w.close();
        w.blank();
        w.open("@Override public int hashCode()");
        StringBuilder hash = new StringBuilder("return java.util.Objects.hash(");
        for (int i = 0; i < variantCase.fields.size(); i++) {
            if (i > 0) {
                hash.append(", ");
            }
            hash.append("sprig.runtime.SprigRuntime.hashValue(this.")
                    .append(mangle(variantCase.fields.get(i).name)).append(")");
        }
        w.line(hash.append(");").toString());
        w.close();
    }

    private static boolean isPrimitive(Type type) {
        return type == NativeType.INT || type == NativeType.FLOAT || type == NativeType.BOOL;
    }

    private void generateClass(Decl decl, Decl.ClassDecl classDecl) {
        JavaWriter w = writer(classDecl.span);
        w.line("package " + PACKAGE + ";");
        w.blank();
        w.line("// Sprig class " + commentText(currentModule.name) + ":" + classDecl.span.display());
        w.map(classDecl.span);
        StringBuilder header = new StringBuilder("public final class ")
                .append(simpleName(typeNames.get(decl)));
        if (!classDecl.conformedInterfaces.isEmpty()) {
            List<String> interfaces = new ArrayList<>();
            for (Class<?> iface : classDecl.conformedInterfaces) {
                interfaces.add(sourceName(iface));
            }
            header.append(" implements ").append(String.join(", ", interfaces));
        }
        w.open(header.toString());
        for (Decl.Field field : classDecl.fields) {
            w.map(field.span);
            String prefix = "public " + (field.mutable ? "" : "final ");
            w.line(prefix + javaType(field.type) + " " + mangle(field.name) + ";");
        }
        emitConstructor(w, classDecl);
        for (Decl.Func method : classDecl.methods) {
            w.blank();
            emitFunction(w, method, classDecl);
        }
        w.blank();
        w.open("@Override public java.lang.String toString()");
        StringBuilder text = new StringBuilder("return \"").append(classDecl.name);
        if (!classDecl.fields.isEmpty()) {
            text.append('(');
            for (int i = 0; i < classDecl.fields.size(); i++) {
                if (i > 0) {
                    text.append(", ");
                }
                text.append(classDecl.fields.get(i).name).append("=\" + sprig.runtime.SprigRuntime.str(")
                    .append(mangle(classDecl.fields.get(i).name)).append(") + \"");
            }
            text.append(')');
        }
        text.append("\";");
        w.line(text.toString());
        w.close();
        w.close();
        String file = PACKAGE_DIR + "/" + simpleName(typeNames.get(decl)) + ".java";
        emitFile(file, w);
    }

    private void emitConstructor(JavaWriter w, Decl.ClassDecl classDecl) {
        StringBuilder ctor = new StringBuilder("public ").append(simpleName(typeNames.get(classDecl))).append('(');
        for (int i = 0; i < classDecl.fields.size(); i++) {
            if (i > 0) {
                ctor.append(", ");
            }
            Decl.Field field = classDecl.fields.get(i);
            ctor.append(javaType(field.type)).append(' ').append(mangle(field.name));
        }
        ctor.append(") {");
        w.blank();
        w.line("// Named constructor lowered to positional parameters; omitted fields");
        w.line("// receive their default expression at the call site, in declaration order.");
        w.line(ctor.toString());
        for (Decl.Field field : classDecl.fields) {
            w.line("    this." + mangle(field.name) + " = " + mangle(field.name) + ";");
        }
        w.line("}");
    }

    // ------------------------------------------------------------------
    // Module class and functions
    // ------------------------------------------------------------------

    private void generateModule(Module module) {
        JavaWriter w = writer(null);
        w.line("package " + PACKAGE + ";");
        w.blank();
        w.line("// Sprig module " + commentText(module.name) + " (" + commentText(module.path.getFileName()) + ")");
        w.map(null);
        w.open("public final class " + moduleClassNames.get(module));
        w.line("private " + moduleClassNames.get(module) + "() {");
        w.line("}");
        w.line("private static boolean $initialized;");
        for (Stmt stmt : module.topStatements) {
            if (stmt instanceof Stmt.VarDecl varDecl) {
                w.blank();
                w.map(varDecl.span);
                w.line("public static " + javaType(varDecl.symbol.type) + " " + mangle(varDecl.name) + ";");
            }
        }
        for (Decl decl : module.decls) {
            if (decl instanceof Decl.Func func) {
                w.blank();
                emitFunction(w, func, null);
            }
        }
        w.blank();
        w.line("// Top-level statements run once, in source order; imports first.");
        w.map(module.topStatements.isEmpty() ? null : module.topStatements.get(0).span);
        w.open("public static void $init() throws java.lang.Exception");
        w.line("if ($initialized) {");
        w.line("    return;");
        w.line("}");
        w.line("$initialized = true;");
        for (Decl.Import imp : module.imports) {
            if (!imp.fileImport) {
                continue;
            }
            String alias = sprig.compiler.sem.ImportNames.aliasFor(imp);
            Module dependency = module.importedModules.get(alias);
            if (dependency != null) {
                w.line(moduleClassName(dependency) + ".$init();");
            }
        }
        resetLocals();
        for (Stmt stmt : module.topStatements) {
            if (stmt instanceof Stmt.VarDecl varDecl) {
                w.map(varDecl.span);
                w.line(mangle(varDecl.name) + " = " + convertedExpression(varDecl.init, varDecl.symbol.type) + ";");
            } else {
                emitStmt(w, stmt);
            }
        }
        w.close();
        w.blank();
        w.line("// Checked JVM exceptions at top level surface as runtime failures.");
        w.open("public static void sprigMain() throws java.lang.Exception");
        w.line("$init();");
        w.close();
        w.blank();
        w.open("public static void main(java.lang.String[] args)");
        w.open("try");
        w.line("sprig.runtime.host.HostSystem.setArguments(args);");
        w.line("sprigMain();");
        w.close();
        w.open("catch (java.lang.Throwable failure)");
        w.line("sprig.runtime.SprigRuntime.reportRuntimeFailure(failure);");
        w.line("java.lang.System.exit(1);");
        w.close();
        w.close();
        w.close();
        String file = PACKAGE_DIR + "/" + moduleClassNames.get(module) + ".java";
        emitFile(file, w);
    }

    private String thisRef() {
        if (lambdaDepth > 0 && currentClassDecl != null) {
            return typeNames.get(currentClassDecl) + ".this";
        }
        return "this";
    }

    private void emitFunction(JavaWriter w, Decl.Func func, Decl.ClassDecl owner) {
        resetLocals();
        currentClassDecl = owner;
        currentReturnType = func.returnType;
        lambdaDepth = 0;
        w.map(func.span);
        w.line("// Sprig " + commentText(currentModule.path.getFileName()) + ":" + func.span.display()
                + (owner == null ? " func " + func.name : " method " + owner.name + "." + func.name));
        StringBuilder sig = new StringBuilder();
        if (owner == null) {
            sig.append("public static ");
        } else {
            sig.append("public ");
        }
        sig.append(func.returnType == NativeType.UNIT ? "void" : javaType(func.returnType));
        sig.append(' ').append(owner == null ? fnName(func) : methodName(func)).append('(');
        for (int i = 0; i < func.params.size(); i++) {
            if (i > 0) {
                sig.append(", ");
            }
            Decl.Param param = func.params.get(i);
            sig.append(javaType(param.type)).append(' ').append(localName(param.symbol));
        }
        sig.append(')');
        List<String> throwsClauses = new ArrayList<>();
        for (Type thrown : func.throwsTypes) {
            if (Semantics.isJvmChecked(thrown) && thrown instanceof JavaType javaType) {
                throwsClauses.add(sourceName(javaType.clazz));
            }
        }
        if (!throwsClauses.isEmpty()) {
            sig.append(" throws ").append(String.join(", ", throwsClauses));
        }
        w.open(sig.toString());
        if (func.foreignBoundary) {
            // Foreign conformance methods are a checked JVM boundary: a Java
            // caller can pass null, so non-null reference parameters are
            // guarded before any Sprig code observes them.
            for (Decl.Param param : func.params) {
                Type type = param.type;
                if (type == null || type.isNullable() || !Semantics.isReference(type)) {
                    continue;
                }
                w.line("java.util.Objects.requireNonNull(" + localName(param.symbol) + ", "
                        + "\"foreign boundary: parameter '" + param.name + "' must be non-null\");");
            }
        }
        for (Stmt stmt : func.body) {
            emitStmt(w, stmt);
        }
        w.close();
    }

    // ------------------------------------------------------------------
    // Statements
    // ------------------------------------------------------------------

    private void emitStmt(JavaWriter w, Stmt stmt) {
        if (stmt instanceof Stmt.VarDecl varDecl) {
            w.map(varDecl.span);
            w.line(javaType(varDecl.symbol.type) + " " + localName(varDecl.symbol) + " = "
                    + convertedExpression(varDecl.init, varDecl.symbol.type) + ";");
        } else if (stmt instanceof Stmt.Assign assign) {
            emitAssign(w, assign);
        } else if (stmt instanceof Stmt.ExprStmt exprStmt) {
            w.map(stmt.span);
            String expression = emitExpr(exprStmt.expr);
            if (exprStmt.expr.type != NativeType.UNIT) {
                // Casts/unboxing are value expressions, not legal Java statement
                // expressions. Preserve evaluation once in an inferred throwaway local; a JVM return type may be
                // inaccessible by name (e.g. a JDK covariant bridge).
                expression = (exprStmt.expr.type == NativeType.NULL ? "java.lang.Object" : "var")
                        + " " + freshTemp("discard") + " = " + expression;
            }
            w.line(expression + ";");
        } else if (stmt instanceof Stmt.Return ret) {
            w.map(stmt.span);
            if (ret.value == null) {
                w.line("return;");
            } else {
                w.line("return " + convertedExpression(ret.value, currentReturnType) + ";");
            }
        } else if (stmt instanceof Stmt.Throw thr) {
            w.map(stmt.span);
            w.line("throw " + emitExpr(thr.value) + ";");
        } else if (stmt instanceof Stmt.IfStmt ifStmt) {
            emitIf(w, ifStmt);
        } else if (stmt instanceof Stmt.WhileStmt whileStmt) {
            w.map(whileStmt.span);
            w.open("while (" + emitExpr(whileStmt.cond) + ")");
            for (Stmt child : whileStmt.body) {
                emitStmt(w, child);
            }
            w.close();
        } else if (stmt instanceof Stmt.ForStmt forStmt) {
            emitFor(w, forStmt);
        } else if (stmt instanceof Stmt.Try tryStmt) {
            emitTry(w, tryStmt);
        } else if (stmt instanceof Stmt.Match match) {
            emitMatch(w, match);
        } else if (stmt instanceof Stmt.Break) {
            w.map(stmt.span);
            w.line("break;");
        } else if (stmt instanceof Stmt.Continue) {
            w.map(stmt.span);
            w.line("continue;");
        } else if (stmt instanceof Stmt.Pass) {
            w.map(stmt.span);
            w.line("// pass");
        }
    }

    private void emitAssign(JavaWriter w, Stmt.Assign assign) {
        w.map(assign.span);
        Expr target = assign.target;
        if (target instanceof Expr.Name name) {
            String lhs = name.symbol.kind == Symbol.Kind.FIELD
                    ? thisRef() + "." + mangle(name.symbol.name) : localName(name.symbol);
            if (name.symbol.kind == Symbol.Kind.TOP_VAR) {
                lhs = moduleClassName(name.symbol.module) + "." + mangle(name.symbol.name);
            }
            w.line(lhs + " = " + assignmentValue(name.type, lhs, assign) + ";");
            return;
        }
        if (target instanceof Expr.FieldAccess access) {
            ResolvedField field = access.resolved;
            String receiver;
            if (field.kind == ResolvedField.Kind.MODULE_VAR) {
                receiver = moduleClassName(field.module);
            } else if (field.kind == ResolvedField.Kind.CLASS_FIELD) {
                receiver = emitExpr(access.receiver);
            } else if (field.kind == ResolvedField.Kind.JAVA_FIELD) {
                receiver = field.jvm.isStatic() ? sourceName(field.jvm.owner)
                        : emitExpr(access.receiver);
            } else {
                receiver = emitExpr(access.receiver);
            }
            if (!assign.op.equals("=") && field.kind != ResolvedField.Kind.MODULE_VAR
                    && !(field.kind == ResolvedField.Kind.JAVA_FIELD && field.jvm.isStatic())) {
                String temp = freshTemp("receiver");
                w.line("var " + temp + " = " + receiver + ";");
                receiver = temp;
            }
            String lhs = receiver + "." + mangle(access.name);
            String oldValue = lhs;
            if (!assign.op.equals("=") && field.fieldDecl != null
                    && !field.substitution.isEmpty() && containsTypeParameter(field.fieldDecl.type)) {
                oldValue = unboxGeneric(oldValue, field.type);
            }
            w.line(lhs + " = " + assignmentValue(field.type, oldValue, assign) + ";");
            return;
        }
        Expr.Index index = (Expr.Index) target;
        String receiver = freshTemp("collection");
        String key = freshTemp("key");
        w.line("var " + receiver + " = " + emitExpr(index.receiver) + ";");
        Type receiverBase = index.receiver.type.nonNull();
        Type keyType = receiverBase instanceof MapType map ? map.key : NativeType.INT;
        w.line("var " + key + " = " + convertedExpression(index.index, keyType) + ";");
        if (!assign.op.equals("=") && receiverBase instanceof MapType) {
            // Compound assignment reads an existing value; a missing key is a
            // Sprig Error, not a Java unboxing failure.
            w.line("if (!" + receiver + ".containsKeyObject(" + key + ")) throw new sprig.runtime.SprigError("
                    + "\"compound assignment requires an existing map key\");");
        }
        String oldValue = receiver + ".get(" + key + ")";
        w.line(receiver + ".set(" + key + ", " + assignmentValue(index.type, oldValue, assign) + ");");
    }

    private String assignmentValue(Type targetType, String oldValue, Stmt.Assign assign) {
        if (assign.op.equals("=")) return convertedExpression(assign.value, targetType);
        String value = emitExpr(assign.value);
        String op = assign.op.substring(0, 1);
        if (targetType == NativeType.STRING && op.equals("+")) {
            return "(sprig.runtime.SprigRuntime.str(" + oldValue + ") + sprig.runtime.SprigRuntime.str("
                    + value + "))";
        }
        return numericOp(op, targetType, oldValue, value);
    }

    private void emitIf(JavaWriter w, Stmt.IfStmt ifStmt) {
        w.map(ifStmt.span);
        w.open("if (" + emitExpr(ifStmt.cond) + ")");
        for (Stmt child : ifStmt.thenBody) {
            emitStmt(w, child);
        }
        w.close();
        for (Stmt.IfStmt.Elif elif : ifStmt.elifs) {
            w.open("else if (" + emitExpr(elif.cond) + ")");
            for (Stmt child : elif.body) {
                emitStmt(w, child);
            }
            w.close();
        }
        if (ifStmt.elseBody != null) {
            w.open("else");
            for (Stmt child : ifStmt.elseBody) {
                emitStmt(w, child);
            }
            w.close();
        }
    }

    private void emitFor(JavaWriter w, Stmt.ForStmt forStmt) {
        w.map(forStmt.span);
        String var = localName(forStmt.symbol);
        String iterable = "(" + emitExpr(forStmt.iterable) + ")";
        boolean erased = containsTypeParameter(forStmt.iterable.type);
        String values = forStmt.forKind == sprig.compiler.sem.ForKind.MAP_KEYS
                ? iterable + ".keys()" : iterable;
        if (erased) {
            values = "((java.lang.Iterable<" + boxedJavaType(forStmt.symbol.type)
                    + ">) (java.lang.Iterable<?>) (" + values + "))";
        }
        switch (forStmt.forKind) {
            case STRING -> w.open("for (java.lang.String " + var
                    + " : sprig.runtime.StringOps.codePoints(" + iterable + "))");
            case MAP_KEYS -> w.open("for (" + boxedJavaType(forStmt.symbol.type) + " " + var + " : "
                    + values + ")");
            default -> w.open("for (" + boxedJavaType(forStmt.symbol.type) + " " + var + " : " + values + ")");
        }
        for (Stmt child : forStmt.body) {
            emitStmt(w, child);
        }
        w.close();
    }

    private void emitTry(JavaWriter w, Stmt.Try tryStmt) {
        w.map(tryStmt.span);
        w.open("try");
        for (Stmt child : tryStmt.body) {
            emitStmt(w, child);
        }
        w.close();
        for (Stmt.Try.CatchClause clause : tryStmt.catches) {
            String type = javaType(clause.caughtType);
            w.open("catch (" + type + " " + localName(clause.symbol) + ")");
            for (Stmt child : clause.body) {
                emitStmt(w, child);
            }
            w.close();
        }
        if (tryStmt.finallyBody != null) {
            w.open("finally");
            for (Stmt child : tryStmt.finallyBody) {
                emitStmt(w, child);
            }
            w.close();
        }
    }

    private void emitMatch(JavaWriter w, Stmt.Match match) {
        w.map(match.span);
        String temp = freshTemp("m");
        w.line(javaType(match.scrutinee.type) + " " + temp + " = " + emitExpr(match.scrutinee) + ";");
        // When the scrutinee's static type is already the concrete variant case,
        // javac rejects a redundant `instanceof Case binder` pattern on older
        // JDKs. The checker guarantees exactly one reachable branch here, so
        // bind the scrutinee directly instead.
        boolean concreteScrutinee = match.scrutinee.type instanceof VariantCaseType
                && match.matchedType instanceof VariantCaseType;
        boolean unconditional = false;
        boolean first = true;
        for (Stmt.Match.Branch branch : match.branches) {
            String condition;
            if (match.matchedType instanceof EnumType enumType) {
                condition = temp + " == " + typeNames.get(enumType.decl) + "." + mangle(branch.caseName);
            } else if (concreteScrutinee) {
                condition = null;
                unconditional = true;
            } else {
                condition = temp + " instanceof " + javaType(branch.binderType);
            }
            if (condition != null && branch.binderSymbol != null) {
                condition += " " + localName(branch.binderSymbol);
            }
            w.map(branch.caseTypeRef.span);
            if (first) {
                w.line(condition == null ? "{" : "if (" + condition + ") {");
            } else {
                w.dedent();
                w.line(condition == null ? "} else {" : "} else if (" + condition + ") {");
            }
            w.indent();
            if (condition == null && branch.binderSymbol != null) {
                w.line(javaType(branch.binderType) + " " + localName(branch.binderSymbol) + " = " + temp + ";");
            }
            for (Stmt child : branch.body) {
                emitStmt(w, child);
            }
            first = false;
        }
        w.dedent();
        if (unconditional) {
            w.line("}");
        } else {
            w.line("} else {");
            w.indent();
            w.line("throw new java.lang.IllegalStateException(\"exhaustive match failed at runtime\");");
            w.dedent();
            w.line("}");
        }
    }

    // ------------------------------------------------------------------
    // Expressions
    // ------------------------------------------------------------------

    private String emitExpr(Expr expr) {
        if (expr instanceof Expr.IntLit intLit) {
            if (intLit.type == NativeType.INT32) return intLit.value.toString();
            if (intLit.type == NativeType.FLOAT32) return java.lang.Float.toString(intLit.value.floatValue()) + "f";
            if (intLit.type == NativeType.FLOAT) return java.lang.Double.toString(intLit.value.doubleValue());
            return intLit.value + "L";
        }
        if (expr instanceof Expr.FloatLit floatLit) {
            if (floatLit.type == NativeType.FLOAT32) {
                return java.lang.Float.toString(java.lang.Float.parseFloat(floatLit.sourceText)) + "f";
            }
            return Double.toString(floatLit.value);
        }
        if (expr instanceof Expr.StringLit stringLit) {
            return quote(stringLit.value);
        }
        if (expr instanceof Expr.BoolLit boolLit) {
            return boolLit.value ? "true" : "false";
        }
        if (expr instanceof Expr.NullLit) {
            return "null";
        }
        if (expr instanceof Expr.Name name) {
            return emitName(name);
        }
        if (expr instanceof Expr.FieldAccess access) {
            return emitFieldAccess(access);
        }
        if (expr instanceof Expr.Call call) {
            return emitCall(call);
        }
        if (expr instanceof Expr.Index index) {
            return emitIndex(index);
        }
        if (expr instanceof Expr.Subscript subscript) {
            return emitSubscript(subscript);
        }
        if (expr instanceof Expr.Unary unary) {
            return emitUnary(unary);
        }
        if (expr instanceof Expr.Binary binary) {
            return emitBinary(binary);
        }
        if (expr instanceof Expr.ListLit listLit) {
            return emitListLit(listLit);
        }
        if (expr instanceof Expr.MapLit mapLit) {
            return emitMapLit(mapLit);
        }
        if (expr instanceof Expr.Match match) return emitMatchExpression(match);
        if (expr instanceof Expr.Lambda lambda) {
            return emitLambda(lambda);
        }
        return "null";
    }

    private String emitMatchExpression(Expr.Match expr) {
        Stmt.Match match = expr.cases;
        String temp = freshTemp("matchExpr");
        StringBuilder code = new StringBuilder("((").append(javaType(expr.type)).append(") (switch (0) { default -> { ")
            .append(javaType(match.scrutinee.type)).append(" ").append(temp).append(" = ").append(emitExpr(match.scrutinee)).append("; ");
        boolean concrete = match.matchedType instanceof VariantCaseType;
        for (var branch : match.branches) {
            if (match.matchedType instanceof EnumType enumType) {
                code.append("if (").append(temp).append(" == ").append(typeNames.get(enumType.decl)).append(".").append(mangle(branch.caseName)).append(") { ");
            } else if (concrete) code.append("{ ");
            else {
                code.append("if (").append(temp).append(" instanceof ").append(javaType(branch.binderType));
                if (branch.binderSymbol != null) code.append(" ").append(localName(branch.binderSymbol));
                code.append(") { ");
            }
            if (concrete && branch.binderSymbol != null) code.append(javaType(branch.binderType)).append(" ").append(localName(branch.binderSymbol)).append(" = ").append(temp).append("; ");
            Expr value = ((Stmt.ExprStmt)branch.body.get(0)).expr;
            code.append("yield ").append(convertedExpression(value,expr.type)).append("; } ");
        }
        if (!concrete) code.append("throw new java.lang.IllegalStateException(\"exhaustive match failed at runtime\"); ");
        return code.append("} }))").toString();
    }

    private String emitName(Expr.Name name) {
        Symbol symbol = name.symbol;
        if (symbol == null) {
            return "null";
        }
        return switch (symbol.kind) {
            case LOCAL, PARAM -> localName(symbol);
            case FIELD -> thisRef() + "." + mangle(symbol.name);
            case TOP_VAR -> moduleClassName(symbol.module) + "." + mangle(symbol.name);
            default -> "null";
        };
    }

    private String emitFieldAccess(Expr.FieldAccess access) {
        ResolvedField field = access.resolved;
        if (field == null) {
            return "null";
        }
        switch (field.kind) {
            case CLASS_FIELD, VARIANT_PAYLOAD: {
                String code = "(" + emitExpr(access.receiver) + ")." + mangle(access.name);
                if (field.fieldDecl != null && !field.substitution.isEmpty()
                        && containsTypeParameter(field.fieldDecl.type)) {
                    return unboxGeneric(code, field.type);
                }
                return code;
            }
            case ENUM_CASE:
                return typeNames.get(field.enumDecl) + "." + mangle(field.enumCaseName);
            case VARIANT_CASE_VALUE:
                if (field.payloadless) {
                    return "new " + javaType(field.type) + "()";
                }
                return "null";
            case MODULE_VAR:
                return moduleClassName(field.module) + "." + mangle(access.name);
            case MODULE_FUNCTION:
                return moduleClassName(field.module) + "." + fnName(field.methodDecl);
            case JAVA_FIELD:
                String javaField = field.jvm.isStatic()
                        ? sourceName(field.jvm.owner) + "." + field.jvm.name
                        : "(" + emitExpr(access.receiver) + ")." + field.jvm.name;
                return convertJvmResult(field.jvm.field.getType(), javaField);
            case ERROR_MESSAGE:
                return "(" + emitExpr(access.receiver) + ").getMessage()";
            case BUILTIN_METHOD:
                if (field.builtinId.equals("toString")) {
                    return "sprig.runtime.SprigRuntime.str(" + emitExpr(access.receiver) + ")";
                }
                return "null";
            default:
                return "null";
        }
    }

    private String emitIndex(Expr.Index index) {
        return emitIndexOn(index.receiver, index.index);
    }

    private String emitSubscript(Expr.Subscript subscript) {
        Expr index = subscript.index != null ? subscript.index : subscript.resolvedIndex;
        if (index == null) {
            // A generic application has no standalone Java value; calls and
            // case accesses are emitted by their parents.
            return "null";
        }
        return emitIndexOn(subscript.base, index);
    }

    private String emitIndexOn(Expr receiverExpr, Expr indexExpr) {
        Type receiver = receiverExpr.type == null ? null : receiverExpr.type.nonNull();
        if (receiver == NativeType.STRING) {
            return "sprig.runtime.StringOps.elementAt(" + emitExpr(receiverExpr)
                    + ", sprig.runtime.NumericOps.toInt32Exact(" + emitExpr(indexExpr) + "))";
        }
        Type keyType = receiver instanceof MapType map ? map.key : NativeType.INT;
        String code = "(" + emitExpr(receiverExpr) + ").get(" + convertedExpression(indexExpr, keyType) + ")";
        if (containsTypeParameter(receiver)) {
            Type element = receiver instanceof ListType list ? list.element
                    : receiver instanceof MapType map ? NullableType.of(map.value) : NativeType.ERROR;
            code = unboxGeneric(code, element);
        }
        return code;
    }

    private String emitUnary(Expr.Unary unary) {
        if (unary.op.equals("not")) {
            return "(!" + emitExpr(unary.operand) + ")";
        }
        if (unary.op.equals("-") && unary.operand instanceof Expr.IntLit literal) {
            if (unary.type == NativeType.INT && literal.value.equals(java.math.BigInteger.ONE.shiftLeft(63)))
                return "java.lang.Long.MIN_VALUE";
            if (unary.type == NativeType.INT32 && literal.value.equals(java.math.BigInteger.ONE.shiftLeft(31)))
                return "java.lang.Integer.MIN_VALUE";
        }
        if (unary.op.equals("-")) {
            if (unary.type == NativeType.INT) return "sprig.runtime.NumericOps.neg(" + emitExpr(unary.operand) + ")";
            if (unary.type == NativeType.INT32) return "sprig.runtime.NumericOps.neg32(" + emitExpr(unary.operand) + ")";
            if (unary.type == NativeType.DECIMAL) return emitExpr(unary.operand) + ".negate()";
            if (unary.type == NativeType.BIGINT) return emitExpr(unary.operand) + ".negate()";
        }
        return "(" + unary.op + emitExpr(unary.operand) + ")";
    }

    private String emitBinary(Expr.Binary binary) {
        String op = binary.op;
        String left = emitExpr(binary.left);
        String right = emitExpr(binary.right);
        if (op.equals("and")) {
            return "(" + left + " && " + right + ")";
        }
        if (op.equals("or")) {
            return "(" + left + " || " + right + ")";
        }
        if (op.equals("in")) {
            Type rightType = binary.right.type == null ? null : binary.right.type.nonNull();
            Type expected = rightType instanceof MapType map ? map.key
                    : rightType instanceof ListType list ? list.element : NativeType.STRING;
            left = convertedExpression(binary.left, expected);
            // Runtime helpers keep Sprig's left-to-right operand evaluation;
            // `right.contains(left)` would evaluate the container first.
            if (rightType instanceof MapType) {
                return "(sprig.runtime.SprigRuntime.mapContainsKey(" + left + ", " + right + "))";
            }
            if (rightType instanceof ListType) {
                return "(sprig.runtime.SprigRuntime.listContains(" + left + ", " + right + "))";
            }
            return "(sprig.runtime.SprigRuntime.stringContains(" + left + ", " + right + "))";
        }
        if (op.equals("==") || op.equals("!=")) {
            boolean negate = op.equals("!=");
            if (binary.left instanceof Expr.NullLit || binary.right instanceof Expr.NullLit) {
                return wrapNegate(negate, "(" + left + " == " + right + ")");
            }
            if (binary.comparisonType != null) {
                String call = "sprig.runtime.SprigRuntime.equalsValue("
                        + convertedExpression(binary.left, binary.comparisonType) + ", "
                        + convertedExpression(binary.right, binary.comparisonType) + ")";
                return wrapNegate(negate, call);
            }
            Type base = binary.left.type == null ? null : binary.left.type.nonNull();
            if (binary.valueEquality) {
                String call = "sprig.runtime.SprigRuntime.equalsValue(" + left + ", " + right + ")";
                return negate ? "(!" + call + ")" : call;
            }
            if (base instanceof EnumType) {
                return wrapNegate(negate, "(" + left + " == " + right + ")");
            }
            String comparison = "(" + unbox(binary.left, left) + " == " + unbox(binary.right, right) + ")";
            return wrapNegate(negate, comparison);
        }
        if (op.equals("+") && (binary.type == NativeType.STRING)) {
            return "(sprig.runtime.SprigRuntime.str(" + left + ") + sprig.runtime.SprigRuntime.str(" + right + "))";
        }
        if ((op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">="))
                && binary.left.type == NativeType.STRING) {
            String call = left + ".compareTo(" + right + ")";
            return "(" + call + " " + op + " 0)";
        }
        if ((op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">="))
                && (binary.left.type == NativeType.DECIMAL || binary.left.type == NativeType.BIGINT)) {
            return "(" + left + ".compareTo(" + right + ") " + op + " 0)";
        }
        if (binary.type == NativeType.INT || binary.type == NativeType.INT32
                || binary.type == NativeType.DECIMAL || binary.type == NativeType.BIGINT) {
            return numericOp(op, binary.type, left, right);
        }
        return "(" + left + " " + op + " " + right + ")";
    }

    private String numericOp(String op, Type type, String left, String right) {
        if (type == NativeType.BIGINT) {
            String method = switch (op) {
                case "+" -> "add"; case "-" -> "subtract"; case "*" -> "multiply";
                case "%" -> "remainder"; default -> null;
            };
            if (method != null) return left + "." + method + "(" + right + ")";
        }
        if (type == NativeType.DECIMAL) {
            String method = switch (op) {
                case "+" -> "add"; case "-" -> "subtract"; case "*" -> "multiply";
                default -> null;
            };
            if (method != null) return left + "." + method + "(" + right + ")";
        }
        if (type == NativeType.INT || type == NativeType.INT32) {
            String method = switch (op) {
                case "+" -> "add"; case "-" -> "sub"; case "*" -> "mul";
                case "%" -> "rem"; default -> null;
            };
            if (method != null) return "sprig.runtime.NumericOps." + method
                    + (type == NativeType.INT32 ? "32" : "") + "(" + left + ", " + right + ")";
        }
        return "(" + left + " " + op + " " + right + ")";
    }

    private static String wrapNegate(boolean negate, String expression) {
        return negate ? "(!" + expression + ")" : expression;
    }

    /** Unboxes nullable numeric/boolean locals for primitive comparison. */
    private String unbox(Expr expr, String code) {
        Type type = expr.type;
        if (type == null) {
            return code;
        }
        Type base = type.nonNull();
        if (!type.isNullable()) {
            return code;
        }
        if (base == NativeType.INT) {
            return code + ".longValue()";
        }
        if (base == NativeType.FLOAT) {
            return code + ".doubleValue()";
        }
        if (base == NativeType.BOOL) {
            return code + ".booleanValue()";
        }
        return code;
    }

    private String emitListLit(Expr.ListLit lit) {
        StringBuilder sb = new StringBuilder();
        if (lit.mutable) {
            sb.append("sprig.runtime.SprigMutableList.<").append(boxedJavaType(lit.type == null
                    ? NativeType.ERROR : ((ListType) lit.type).element)).append(">ofItems(");
        } else {
            sb.append("sprig.runtime.SprigList.<").append(boxedJavaType(lit.type == null
                    ? NativeType.ERROR : ((ListType) lit.type).element)).append(">ofItems(");
        }
        for (int i = 0; i < lit.items.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(convertedExpression(lit.items.get(i), ((ListType) lit.type).element));
        }
        return sb.append(')').toString();
    }

    private String emitMapLit(Expr.MapLit lit) {
        MapType mapType = lit.type instanceof MapType mt ? mt : null;
        String keyType = mapType == null ? "java.lang.Object" : boxedJavaType(mapType.key);
        String valueType = mapType == null ? "java.lang.Object" : boxedJavaType(mapType.value);
        StringBuilder sb = new StringBuilder();
        sb.append(lit.mutable ? "sprig.runtime.SprigMutableMap" : "sprig.runtime.SprigMap")
          .append(".<").append(keyType).append(", ").append(valueType).append(">ofEntries(");
        for (int i = 0; i < lit.keys.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(convertedExpression(lit.keys.get(i), mapType.key)).append(", ")
                    .append(convertedExpression(lit.values.get(i), mapType.value));
        }
        return sb.append(')').toString();
    }

    private String emitLambda(Expr.Lambda lambda) {
        FunctionType functionType = lambda.type instanceof FunctionType ft ? ft : null;
        lambdaDepth++;
        int arity = lambda.params.size();
        String raw = "sprig.runtime.Fn" + arity;
        StringBuilder sb = new StringBuilder("new ").append(raw).append('<');
        for (Decl.Param param : lambda.params) {
            sb.append(boxedJavaType(param.type)).append(", ");
        }
        String resultJava = functionType == null || functionType.result == NativeType.UNIT
                ? "java.lang.Void" : boxedJavaType(functionType.result);
        sb.append(resultJava).append(">() {").append('\n');
        sb.append("    public ").append(resultJava).append(" apply(");
        for (int i = 0; i < lambda.params.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Decl.Param param = lambda.params.get(i);
            sb.append(boxedJavaType(param.type)).append(' ').append(localName(param.symbol));
        }
        sb.append(") {").append('\n');
        for (Decl.Param param : lambda.params) {
            if (!param.type.isNullable() && !(param.type instanceof TypeParameterType)) sb.append("        java.util.Objects.requireNonNull(")
                    .append(localName(param.symbol)).append(", \"non-null callable argument\");\n");
        }
        if (functionType != null && functionType.result != NativeType.UNIT) {
            sb.append("        return ").append(convertedExpression(lambda.body, functionType.result)).append(";\n");
        } else {
            sb.append("        ").append(emitExpr(lambda.body)).append(";\n");
            sb.append("        return null;\n");
        }
        sb.append("    }\n").append('}');
        lambdaDepth--;
        return sb.toString();
    }

    private String emitCall(Expr.Call call) {
        ResolvedCall resolved = call.resolved;
        if (resolved == null) {
            return "null";
        }
        switch (resolved.kind) {
            case FUNCTION:
            case MODULE_FUNCTION:
                return finishGenericCall(resolved,
                        fnCall(resolved.symbol.module, fnName(resolved.methodDecl), call));
            case METHOD: {
                Decl.Func func = resolved.methodDecl;
                String receiver = thisRef();
                if (call.callee instanceof Expr.FieldAccess access) {
                    receiver = "(" + emitExpr(access.receiver) + ")";
                }
                String args = resolved.substitution.isEmpty()
                        ? positionalArgs(call)
                        : positionalArgsFor(call, func.params);
                return finishGenericCall(resolved,
                        receiver + "." + methodName(func) + "(" + args + ")");
            }
            case CLASS_CTOR:
                return "new " + typeNames.get(resolved.classDecl) + "(" + ctorArgs(resolved, call) + ")";
            case VARIANT_CTOR: {
                StringBuilder sb = new StringBuilder("new ").append(javaType(resolved.returnType)).append('(');
                for (int i = 0; i < resolved.variantCase.fields.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    Decl.Field field = resolved.variantCase.fields.get(i);
                    Expr value = findNamedArg(call, field.name);
                    if (value == null) {
                        sb.append(field.name).append("$missing");
                    } else {
                        sb.append(genericArgument(value, field.type, resolved.substitution));
                    }
                }
                return sb.append(')').toString();
            }
            case FUNCTION_VALUE: {
                String invocation = emitExpr(call.callee) + ".apply(" + positionalArgs(call) + ")";
                // Java-produced callbacks may violate the Sprig-owned non-null ABI.
                return resolved.returnType != NativeType.UNIT && !resolved.returnType.isNullable()
                        && !(resolved.returnType instanceof TypeParameterType)
                        ? "java.util.Objects.requireNonNull(" + invocation + ", \"non-null callable result\")"
                        : invocation;
            }
            case BUILTIN:
                return emitBuiltinFunction(resolved.builtinId, call);
            case BUILTIN_METHOD:
                return emitBuiltinMethod(call, resolved);
            case JVM_METHOD:
                return emitJvmMethod(call, resolved);
            case JVM_CTOR: {
                String created = resolved.returnType instanceof JavaType javaType && !javaType.args.isEmpty()
                        ? javaType(javaType) : sourceName(resolved.jvm.owner);
                StringBuilder sb = new StringBuilder("new ").append(created).append('(');
                for (int i = 0; i < call.args.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(convertJvmArg(call.args.get(i).value, resolved.jvm,
                            jvmParameter(resolved.jvm, i)));
                }
                return sb.append(')').toString();
            }
            default:
                return "null";
        }
    }

    private static Decl.Func callFuncFromField(Expr.Call call) {
        Expr.FieldAccess access = (Expr.FieldAccess) call.callee;
        return access.resolved.methodDecl;
    }

    private static Decl.Func resolvedMethod(Expr.Call call) {
        Expr.FieldAccess access = (Expr.FieldAccess) call.callee;
        return access.resolved.methodDecl;
    }

    private String fnCall(Module module, String name, Expr.Call call) {
        return moduleClassName(module) + "." + name + "(" + positionalArgs(call) + ")";
    }

    // ------------------------------------------------------------------
    // v0.8 generics: erased and boxed
    // ------------------------------------------------------------------

    /** Unboxes a generic (Object-typed) call result back to the Sprig type. */
    private String finishGenericCall(ResolvedCall resolved, String code) {
        Decl.Func func = resolved.methodDecl;
        if (func != null && !resolved.substitution.isEmpty()
                && containsTypeParameter(func.returnType)) {
            return unboxGeneric(code, resolved.returnType);
        }
        return code;
    }

    private String positionalArgsFor(Expr.Call call, List<Decl.Param> params) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < call.args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Expr value = call.args.get(i).value;
            Type declared = i < params.size() ? params.get(i).type : null;
            sb.append(genericArgument(value, declared, call.resolved.substitution));
        }
        return sb.toString();
    }

    /** Lower only the safe scalar widenings already approved by the checker.
     * Nullable adapters preserve null and evaluate their argument exactly once.
     */
    private String convertedExpression(Expr value, Type target) {
        String code = emitExpr(value);
        Type source = value.type;
        if (source == null || target == null) return code;
        if (source.nonNull() == NativeType.INT32 && target.nonNull() == NativeType.INT) {
            return source.isNullable()
                    ? "sprig.runtime.NumericOps.widenInt32Nullable(" + code + ")"
                    : "((long) (" + code + "))";
        }
        if (source.nonNull() == NativeType.FLOAT32 && target.nonNull() == NativeType.FLOAT) {
            return source.isNullable()
                    ? "sprig.runtime.NumericOps.widenFloat32Nullable(" + code + ")"
                    : "((double) (" + code + "))";
        }
        return code;
    }

    /** Use the instantiated type before boxing into an erased generic slot. */
    private String genericArgument(Expr value, Type declared,
            Map<TypeParameterType, Type> substitution) {
        Type concrete = Substitution.apply(declared, substitution);
        String code = convertedExpression(value, concrete);
        if (declared == null || !containsTypeParameter(declared)
                || concrete == null || concrete.isNullable()) return code;
        Type base = concrete.nonNull();
        if (base == NativeType.INT) return "java.lang.Long.valueOf(" + code + ")";
        if (base == NativeType.INT32) return "java.lang.Integer.valueOf(" + code + ")";
        if (base == NativeType.FLOAT) return "java.lang.Double.valueOf(" + code + ")";
        if (base == NativeType.FLOAT32) return "java.lang.Float.valueOf(" + code + ")";
        if (base == NativeType.BOOL) return "java.lang.Boolean.valueOf(" + code + ")";
        return code;
    }

    private String unboxGeneric(String code, Type concrete) {
        if (concrete == null || concrete == NativeType.ERROR || concrete == NativeType.NULL) {
            return code;
        }
        Type base = concrete.nonNull();
        boolean nullable = concrete.isNullable();
        if (base == NativeType.INT) {
            return nullable ? "((java.lang.Long) " + code + ")"
                    : "((java.lang.Long) " + code + ").longValue()";
        }
        if (base == NativeType.INT32) {
            return nullable ? "((java.lang.Integer) " + code + ")"
                    : "((java.lang.Integer) " + code + ").intValue()";
        }
        if (base == NativeType.FLOAT) {
            return nullable ? "((java.lang.Double) " + code + ")"
                    : "((java.lang.Double) " + code + ").doubleValue()";
        }
        if (base == NativeType.FLOAT32) {
            return nullable ? "((java.lang.Float) " + code + ")"
                    : "((java.lang.Float) " + code + ").floatValue()";
        }
        if (base == NativeType.BOOL) {
            return nullable ? "((java.lang.Boolean) " + code + ")"
                    : "((java.lang.Boolean) " + code + ").booleanValue()";
        }
        if (base == NativeType.STRING) {
            return "((java.lang.String) " + code + ")";
        }
        if (base == NativeType.BIGINT) {
            return "((sprig.runtime.SprigBigInt) " + code + ")";
        }
        if (base == NativeType.DECIMAL) {
            return "((sprig.runtime.SprigDecimal) " + code + ")";
        }
        return "((" + javaType(concrete) + ") (" + code + "))";
    }

    private static boolean containsTypeParameter(Type type) {
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
            return classType.args.stream().anyMatch(JavaGenerator::containsTypeParameter);
        }
        if (type instanceof VariantType variantType) {
            return variantType.args.stream().anyMatch(JavaGenerator::containsTypeParameter);
        }
        if (type instanceof VariantCaseType caseType) {
            return caseType.variantArgs.stream().anyMatch(JavaGenerator::containsTypeParameter);
        }
        if (type instanceof FunctionType function) {
            return function.params.stream().anyMatch(JavaGenerator::containsTypeParameter)
                    || containsTypeParameter(function.result);
        }
        if (type instanceof JavaType javaType) {
            return javaType.args.stream().anyMatch(JavaGenerator::containsTypeParameter);
        }
        return false;
    }

    private static Expr findNamedArg(Expr.Call call, String fieldName) {
        for (Expr.Arg arg : call.args) {
            if (arg.name != null && arg.name.equals(fieldName)) {
                return arg.value;
            }
        }
        return null;
    }

    private String positionalArgs(Expr.Call call) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < call.args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Type expected = null;
            if (call.resolved.methodDecl != null && i < call.resolved.methodDecl.params.size()) {
                expected = Substitution.apply(call.resolved.methodDecl.params.get(i).type,
                        call.resolved.substitution);
            } else if (call.resolved.functionType != null
                    && i < call.resolved.functionType.params.size()) {
                expected = call.resolved.functionType.params.get(i);
            }
            sb.append(convertedExpression(call.args.get(i).value, expected));
        }
        return sb.toString();
    }

    private String ctorArgs(ResolvedCall resolved, Expr.Call call) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Decl.Field field : resolved.classDecl.fields) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            Expr value = findNamedArg(call, field.name);
            if (value != null) {
                sb.append(genericArgument(value, field.type, resolved.substitution));
            } else if (field.defaultExpr != null) {
                sb.append(genericArgument(field.defaultExpr, field.type, resolved.substitution));
            } else {
                sb.append(field.name).append("$missing");
            }
        }
        return sb.toString();
    }

    private String namedArg(Expr.Call call, String fieldName, Expr fallback) {
        for (Expr.Arg arg : call.args) {
            if (arg.name != null && arg.name.equals(fieldName)) {
                return emitExpr(arg.value);
            }
        }
        if (fallback != null) {
            return emitExpr(fallback);
        }
        return fieldName + "$missing";
    }

    private String emitBuiltinFunction(String id, Expr.Call call) {
        return switch (id) {
            case "print" -> "sprig.runtime.SprigRuntime.print(" + emitExpr(call.args.get(0).value) + ")";
            case "range" -> {
                StringBuilder sb = new StringBuilder("sprig.runtime.SprigRuntime.range(");
                for (int i = 0; i < call.args.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(emitExpr(call.args.get(i).value));
                }
                yield sb.append(')').toString();
            }
            case "assert" -> {
                StringBuilder sb = new StringBuilder("sprig.runtime.SprigRuntime.assertTrue(");
                for (int i = 0; i < call.args.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(emitExpr(call.args.get(i).value));
                }
                yield sb.append(')').toString();
            }
            default -> "null";
        };
    }

    private String emitBuiltinMethod(Expr.Call call, ResolvedCall resolved) {
        String id = resolved.builtinId;
        Expr.FieldAccess access = (Expr.FieldAccess) call.callee;
        String recv = "(" + emitExpr(access.receiver) + ")";
        List<Expr> args = new ArrayList<>();
        for (Expr.Arg arg : call.args) {
            args.add(arg.value);
        }
        String a0 = args.isEmpty() ? null : emitExpr(args.get(0));
        String a1 = args.size() < 2 ? null : emitExpr(args.get(1));
        // Sprig builtins have fixed result widths. Do not let Java choose an
        // int/float overload from narrower, safely assignable actual arguments.
        if (id.equals("Int.min") || id.equals("Int.max")) {
            a0 = convertedExpression(args.get(0), NativeType.INT);
            a1 = convertedExpression(args.get(1), NativeType.INT);
        } else if (id.equals("Float.abs")) {
            a0 = convertedExpression(args.get(0), NativeType.FLOAT);
        }
        Type receiver = access.receiver.type == null ? null : access.receiver.type.nonNull();
        if (receiver instanceof ListType list) {
            if (Set.of("List.contains", "List.indexOf", "MutableList.append",
                    "MutableList.remove", "MutableList.contains").contains(id)) {
                a0 = convertedExpression(args.get(0), list.element);
            } else if (id.equals("MutableList.set") || id.equals("MutableList.insert")) {
                a1 = convertedExpression(args.get(1), list.element);
            }
        } else if (receiver instanceof MapType map) {
            if (Set.of("Map.get", "Map.containsKey", "MutableMap.set", "MutableMap.remove").contains(id)) {
                a0 = convertedExpression(args.get(0), map.key);
            }
            if (id.equals("MutableMap.set")) a1 = convertedExpression(args.get(1), map.value);
        }
        String code = switch (id) {
            case "toString" -> "sprig.runtime.SprigRuntime.str(" + recv + ")";
            case "Int.toString" -> "java.lang.Long.toString(" + recv + ")";
            case "Int.toFloat", "Int.toFloatExact" -> "sprig.runtime.NumericOps.toFloatExact(" + recv + ")";
            case "Int.toFloatLossy" -> "((double) " + recv + ")";
            case "Int.toInt32Exact" -> "sprig.runtime.NumericOps.toInt32Exact(" + recv + ")";
            case "Int.toDecimal", "Int32.toDecimal" -> "sprig.runtime.SprigDecimal.fromInt(" + recv + ")";
            case "Int.divTrunc" -> "sprig.runtime.NumericOps.divTrunc(" + recv + ", " + a0 + ")";
            case "Int32.divTrunc" -> "sprig.runtime.NumericOps.divTrunc32(" + recv + ", " + a0 + ")";
            case "Int32.toInt" -> "((long) " + recv + ")";
            case "Int32.toFloat" -> "((double) " + recv + ")";
            case "Int32.toString" -> "java.lang.Integer.toString(" + recv + ")";
            case "Int.parse" -> "sprig.runtime.SprigRuntime.parseInt(" + a0 + ")";
            case "Int.abs" -> "sprig.runtime.NumericOps.abs(" + a0 + ")";
            case "Int.min" -> "java.lang.Math.min(" + a0 + ", " + a1 + ")";
            case "Int.max" -> "java.lang.Math.max(" + a0 + ", " + a1 + ")";
            case "Float.toString" -> "java.lang.Double.toString(" + recv + ")";
            case "Float.isNaN" -> "java.lang.Double.isNaN(" + recv + ")";
            case "Float.toInt", "Float.toIntExact" -> "sprig.runtime.NumericOps.floatToIntExact(" + recv + ")";
            case "Float.toIntTrunc" -> "sprig.runtime.NumericOps.floatToIntTrunc(" + recv + ")";
            case "Float.toFloat32Exact" -> "sprig.runtime.NumericOps.toFloat32Exact(" + recv + ")";
            case "Float.toFloat32Lossy" -> "((float) " + recv + ")";
            case "Float.isInfinite" -> "java.lang.Double.isInfinite(" + recv + ")";
            case "Float.isFinite" -> "java.lang.Double.isFinite(" + recv + ")";
            case "Float.approxEqual" -> "sprig.runtime.NumericOps.approxEqual(" + recv + ", " + a0 + ", " + a1 + ")";
            case "Float.sqrt" -> "java.lang.StrictMath.sqrt(" + a0 + ")";
            case "Float.abs" -> "java.lang.StrictMath.abs(" + a0 + ")";
            case "Float.floor" -> "java.lang.StrictMath.floor(" + a0 + ")";
            case "Float.ceil" -> "java.lang.StrictMath.ceil(" + a0 + ")";
            case "Float32.toFloat" -> "((double) " + recv + ")";
            case "Float32.toString" -> "java.lang.Float.toString(" + recv + ")";
            case "Float32.isNaN" -> "java.lang.Float.isNaN(" + recv + ")";
            case "Float32.isInfinite" -> "java.lang.Float.isInfinite(" + recv + ")";
            case "Float32.isFinite" -> "java.lang.Float.isFinite(" + recv + ")";
            case "Decimal.parse" -> "sprig.runtime.SprigDecimal.parse(" + a0 + ")";
            case "Decimal.fromInt" -> "sprig.runtime.SprigDecimal.fromInt(" + a0 + ")";
            case "Decimal.fromJava" -> "sprig.runtime.SprigDecimal.fromJava(" + a0 + ")";
            case "Decimal.divide" -> recv + ".divide(" + a0 + ", " + a1 + ", " + emitExpr(args.get(2)) + ")";
            case "Decimal.toString" -> recv + ".toString()";
            case "Decimal.toIntExact" -> recv + ".toIntExact()";
            case "Decimal.toFloatExact" -> recv + ".toFloatExact()";
            case "Decimal.toFloatLossy" -> recv + ".toFloatLossy()";
            case "Decimal.toJava" -> recv + ".toJava()";
            case "BigInt.parse" -> "sprig.runtime.SprigBigInt.parse(" + a0 + ")";
            case "BigInt.fromInt" -> "sprig.runtime.SprigBigInt.fromInt(" + a0 + ")";
            case "BigInt.fromJava" -> "sprig.runtime.SprigBigInt.fromJava(" + a0 + ")";
            case "BigInt.divTrunc" -> recv + ".divTrunc(" + a0 + ")";
            case "BigInt.toString" -> recv + ".toString()";
            case "BigInt.toIntExact" -> recv + ".toIntExact()";
            case "BigInt.toFloatExact" -> recv + ".toFloatExact()";
            case "BigInt.toFloatLossy" -> recv + ".toFloatLossy()";
            case "BigInt.toDecimal" -> recv + ".toDecimal()";
            case "BigInt.toJava" -> recv + ".toJava()";
            case "Bool.toString" -> "java.lang.Boolean.toString(" + recv + ")";
            case "String.length" -> "sprig.runtime.StringOps.length(" + recv + ")";
            case "String.isEmpty" -> recv + ".isEmpty()";
            case "String.charAt" -> "sprig.runtime.StringOps.elementAt(" + recv
                    + ", sprig.runtime.NumericOps.toInt32Exact(" + a0 + "))";
            case "String.codeAt" -> "sprig.runtime.StringOps.codePointAt(" + recv
                    + ", sprig.runtime.NumericOps.toInt32Exact(" + a0 + "))";
            case "String.substring" -> a1 == null
                    ? "sprig.runtime.StringOps.substring(" + recv
                        + ", sprig.runtime.NumericOps.toInt32Exact(" + a0 + "))"
                    : "sprig.runtime.StringOps.substring(" + recv
                        + ", sprig.runtime.NumericOps.toInt32Exact(" + a0
                        + "), sprig.runtime.NumericOps.toInt32Exact(" + a1 + "))";
            case "String.indexOf" -> "sprig.runtime.StringOps.indexOf(" + recv + ", " + a0 + ")";
            case "String.contains" -> recv + ".contains(" + a0 + ")";
            case "String.startsWith" -> recv + ".startsWith(" + a0 + ")";
            case "String.endsWith" -> recv + ".endsWith(" + a0 + ")";
            case "String.toUpperCase" -> recv + ".toUpperCase()";
            case "String.toLowerCase" -> recv + ".toLowerCase()";
            case "String.trim" -> recv + ".trim()";
            case "String.split" -> "sprig.runtime.SprigRuntime.stringSplit(" + recv + ", " + a0 + ")";
            case "String.replace" -> recv + ".replace(" + a0 + ", " + a1 + ")";
            case "String.repeat" -> recv + ".repeat(sprig.runtime.NumericOps.toInt32Exact(" + a0 + "))";
            case "String.toInt" -> "sprig.runtime.SprigRuntime.parseInt(" + recv + ")";
            case "String.toIntOrNull" -> "sprig.runtime.SprigRuntime.parseIntOrNull(" + recv + ")";
            case "String.toFloat" -> "sprig.runtime.SprigRuntime.parseFloat(" + recv + ")";
            case "String.toString" -> recv;
            case "String.join" -> "sprig.runtime.SprigRuntime.stringJoin(" + a0 + ", " + a1 + ")";
            case "String.fromCode" -> "new java.lang.String(java.lang.Character.toChars(sprig.runtime.NumericOps.toInt32Exact(" + a0 + ")))";
            case "List.size", "Map.size" -> recv + ".size()";
            case "List.isEmpty", "Map.isEmpty" -> recv + ".isEmpty()";
            case "List.get" -> recv + ".get(" + a0 + ")";
            case "List.contains", "MutableList.contains" -> recv + ".contains(" + a0 + ")";
            case "List.indexOf" -> recv + ".indexOf(" + a0 + ")";
            case "List.toMutableList" -> recv + ".toMutableList()";
            case "List.toList" -> recv + ".toList()";
            case "List.map" -> recv + ".map(" + a0 + ")";
            case "List.filter" -> recv + ".filter(" + a0 + ")";
            case "List.forEach" -> recv + ".forEachItem(" + a0 + ")";
            case "MutableList.append" -> recv + ".append(" + a0 + ")";
            case "MutableList.set" -> recv + ".set(" + a0 + ", " + a1 + ")";
            case "MutableList.insert" -> recv + ".insert(" + a0 + ", " + a1 + ")";
            case "MutableList.removeAt" -> recv + ".removeAt(" + a0 + ")";
            case "MutableList.remove" -> recv + ".remove(" + a0 + ")";
            case "MutableList.clear" -> recv + ".clear()";
            case "MutableList.sort" -> recv + ".sortInPlace()";
            case "Map.get" -> recv + ".get(" + a0 + ")";
            case "Map.containsKey" -> recv + ".containsKey(" + a0 + ")";
            case "Map.keys" -> recv + ".keys()";
            case "Map.values" -> recv + ".values()";
            case "Map.toMutableMap" -> recv + ".toMutableMap()";
            case "Map.toMap" -> recv + ".toMap()";
            case "MutableMap.set" -> recv + ".set(" + a0 + ", " + a1 + ")";
            case "MutableMap.remove" -> recv + ".remove(" + a0 + ")";
            case "MutableMap.clear" -> recv + ".clear()";
            default -> "null";
        };
        if (containsTypeParameter(access.receiver.type)
                && (id.equals("List.get") || id.equals("Map.get")
                    || id.equals("MutableList.removeAt") || id.equals("MutableMap.remove")
                    || resolved.returnType instanceof ListType || resolved.returnType instanceof MapType)) {
            code = unboxGeneric(code, resolved.returnType);
        }
        return code;
    }

    private String emitJvmMethod(Expr.Call call, ResolvedCall resolved) {
        Expr callee = call.callee instanceof Expr.Subscript subscript ? subscript.base : call.callee;
        boolean receiverIsClass = callee instanceof Expr.FieldAccess access
                && access.receiver instanceof Expr.Name name
                && name.symbol != null && name.symbol.kind == Symbol.Kind.JAVA_TYPE;
        StringBuilder sb = new StringBuilder();
        if (receiverIsClass) {
            sb.append(sourceName(resolved.jvm.owner));
        } else {
            sb.append(emitExpr(((Expr.FieldAccess) callee).receiver));
        }
        sb.append('.').append(resolved.jvm.name).append('(');
        Class<?>[] params = resolved.jvm.executable.getParameterTypes();
        for (int i = 0; i < call.args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Class<?> param = i < params.length ? jvmParameter(resolved.jvm, i) : Object.class;
            sb.append(convertJvmArg(call.args.get(i).value, resolved.jvm, param));
        }
        Class<?> rawReturn = ((java.lang.reflect.Method) resolved.jvm.executable).getReturnType();
        return convertJvmResult(rawReturn, boundJvmResult(rawReturn, resolved.returnType,
                sb.append(')').toString()));
    }

    /**
     * A concrete generic boundary can erase to {@code Object} at the JVM level
     * (e.g. {@code Box[String].get()} or an inherited {@code T get()}); insert
     * the statically justified cast when the raw result class cannot carry the
     * mapped Sprig type.
     */
    private String boundJvmResult(Class<?> rawReturn, Type mapped, String code) {
        if (mapped == null || mapped == NativeType.ERROR || mapped == NativeType.NULL
                || mapped == NativeType.UNIT || rawReturn == void.class) {
            return code;
        }
        // Primitive returns already carry the exact Java value type, and
        // convertJvmResult adapts the boxed char/Short/Byte shapes.
        if (rawReturn.isPrimitive() || rawReturn == Character.class
                || rawReturn == Short.class || rawReturn == Byte.class) {
            return code;
        }
        Class<?> mappedRaw = sprig.compiler.sem.JavaTypes.boxedFor(mapped);
        if (mappedRaw == null || mappedRaw == rawReturn) {
            return code;
        }
        if (mapped instanceof JavaType javaType && javaType.clazz == rawReturn) {
            return code;
        }
        return unboxGeneric(code, mapped);
    }

    private static String convertJvmResult(Class<?> javaType, String code) {
        if (javaType == char.class) return "java.lang.String.valueOf(" + code + ")";
        if (javaType == Character.class) return "sprig.runtime.SprigRuntime.fromJavaCharacter(" + code + ")";
        if (javaType == Short.class) return "sprig.runtime.SprigRuntime.fromJavaShort(" + code + ")";
        if (javaType == Byte.class) return "sprig.runtime.SprigRuntime.fromJavaByte(" + code + ")";
        return code;
    }

    /**
     * Source-level parameter class for a JVM member. Bound generic calls box
     * arguments to the mapped formal (Int32 binds to int/Integer), so an
     * erased Object parameter still receives the right wrapper.
     */
    private static Class<?> jvmParameter(sprig.compiler.sem.JvmMember member, int index) {
        Class<?> raw = member.executable.getParameterTypes()[index];
        if (member.bindings.isEmpty()) {
            return raw;
        }
        Type mapped = sprig.compiler.sem.JavaTypes.mapFormal(
                member.executable.getGenericParameterTypes()[index], raw, member.bindings);
        Class<?> preferred = sprig.compiler.sem.JavaTypes.preferredRaw(mapped);
        return preferred != null ? preferred : raw;
    }

    private String convertJvmArg(Expr arg, sprig.compiler.sem.JvmMember member, Class<?> param) {
        String code = emitExpr(arg);
        Type type = arg.type;
        Type base = type == null ? null : type.nonNull();
        if (arg instanceof Expr.IntLit && (param == int.class || param == Integer.class)) return "((int) " + code + ")";
        if (arg instanceof Expr.IntLit && (param == short.class || param == Short.class)) return "((short) " + code + ")";
        if (arg instanceof Expr.IntLit && (param == byte.class || param == Byte.class)) return "((byte) " + code + ")";
        if (arg instanceof Expr.FloatLit && (param == float.class || param == Float.class)) return "((float) " + code + ")";
        if (param == Long.class && base == NativeType.INT32) return "((long) " + code + ")";
        if (param == Double.class && base == NativeType.FLOAT32) return "((double) " + code + ")";
        if ((param == char.class || param == Character.class) && base == NativeType.STRING) {
            return code + ".charAt(0)";
        }
        if ((param == long.class || param == Long.class) && base == NativeType.INT) {
            return code;
        }
        if ((param == double.class || param == Double.class) && base == NativeType.FLOAT) {
            return code;
        }
        return code;
    }

    private static String quote(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private void emitFile(String file, JavaWriter w) {
        output.sources.put(file, w.source());
        output.lineMaps.put(file, w.lineMap());
        output.uris.put(file, currentModule.path.toUri().toString());
    }

    private JavaWriter writer(Span span) {
        return new JavaWriter(currentModule.path.toUri().toString());
    }

    private static String simpleName(String qualified) {
        int dot = qualified.lastIndexOf('.');
        return dot >= 0 ? qualified.substring(dot + 1) : qualified;
    }
}
