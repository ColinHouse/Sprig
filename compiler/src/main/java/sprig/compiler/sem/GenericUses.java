package sprig.compiler.sem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Module;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;
import sprig.compiler.types.NativeType;
import sprig.compiler.types.Substitution;
import sprig.compiler.types.Type;
import sprig.compiler.types.TypeParameterType;

/**
 * Generic code is checked once, with its type parameters standing for any
 * type, so a rule that depends on the actual type is checked again for each
 * use with type arguments. {@link TypeRefResolver} does that for the types a
 * declaration writes; this class does it for the types its body infers: the
 * key of a map literal, such as {@code {value: true}} keyed by {@code T}, and
 * the type arguments of the generic calls and constructions it makes, such
 * as {@code sets.of(values)} inside another generic function.
 *
 * <p>The checker records both while it checks bodies, and verifies the uses a
 * module makes once the module is checked, when every body they reach has
 * been checked too. A failure that the type arguments bring in, such as a
 * Float that becomes a Map key three calls down, is reported at the use that
 * gave them, with the code and message the failure has where it arises.
 */
final class GenericUses {
    /** A generic class, function or variant used with type arguments, written or inferred. */
    private record Use(Decl target, List<Type> args, Module module, Span span) {
    }

    /** A map literal whose key type is a type parameter of the declaration around it. */
    private record Key(Type type, Module module, Span span) {
    }

    private record Instance(Decl decl, List<Type> args) {
    }

    /** The uses inside each generic declaration that pass its type parameters on. */
    private final Map<Decl, Set<Use>> usesIn = new IdentityHashMap<>();
    /** The map literals inside each generic declaration keyed by one of its type parameters. */
    private final Map<Decl, Set<Key>> keysIn = new IdentityHashMap<>();
    /** The uses in the module being checked, verified when it is done. */
    private final Set<Use> pending = new LinkedHashSet<>();
    /** What each instantiation brings in, once computed; every body it reaches is final by then. */
    private final Map<Instance, List<Diagnostic>> verified = new HashMap<>();

    /** Records a generic declaration used with these type arguments at the span. */
    void use(Decl target, List<Type> args, Module module, Span span) {
        if (target == null || args == null || args.isEmpty() || module == null || span == null) {
            return;
        }
        for (Type arg : args) {
            if (arg == null || TypeArgumentInference.containsError(arg) || TypeArgumentInference.containsUnknown(arg)) {
                return;
            }
        }
        Use use = new Use(target, List.copyOf(args), module, span);
        pending.add(use);
        for (Type arg : args) {
            Decl owner = parameterOwner(arg);
            if (owner != null) {
                usesIn.computeIfAbsent(owner, ignored -> new LinkedHashSet<>()).add(use);
                break;
            }
        }
    }

    /** Records a map literal at the span whose key type is a type parameter (possibly nullable). */
    void key(Type type, Module module, Span span) {
        if (type != null && type.nonNull() instanceof TypeParameterType parameter && parameter.owner != null
                && module != null && span != null) {
            keysIn.computeIfAbsent(parameter.owner, ignored -> new LinkedHashSet<>())
                    .add(new Key(type, module, span));
        }
    }

    /**
     * Reports, at each use the module made, what its type arguments bring in
     * inside the generic code it reaches. The declaration's written types were
     * already validated at the use; what it reports with its own parameters is
     * its own error and is reported inside it.
     */
    void verify(TypeRefResolver resolver, Diagnostics diagnostics) {
        List<Use> uses = new ArrayList<>(pending);
        pending.clear();
        for (Use use : uses) {
            List<Diagnostic> found = findings(resolver, use.target(), use.args());
            if (found.isEmpty()) {
                continue;
            }
            List<Diagnostic> own = findings(resolver, use.target(), TypeRefResolver.ownArguments(use.target()));
            for (Diagnostic diagnostic : found) {
                if (!TypeRefResolver.containsReport(own, diagnostic)) {
                    diagnostics.add(TypeRefResolver.atUse(diagnostic, use.target(), use.args(),
                            use.module(), use.span()));
                }
            }
        }
    }

    private List<Diagnostic> findings(TypeRefResolver resolver, Decl target, List<Type> args) {
        Instance instance = new Instance(target, args);
        List<Diagnostic> known = verified.get(instance);
        if (known == null) {
            known = new ArrayList<>();
            explore(resolver, target, args, null, new HashSet<>(), new HashSet<>(), known);
            verified.put(instance, known);
        }
        return known;
    }

    /**
     * What the declaration's body infers, with the type arguments substituted.
     * A use inside it is validated like a written one (the use argument says
     * where it is), and its own body is explored the same way. A declaration
     * already on the path is not entered again, which ends recursion, also a
     * recursion that grows its type arguments.
     */
    private void explore(TypeRefResolver resolver, Decl decl, List<Type> args, Use at,
                         Set<Instance> seen, Set<Decl> path, List<Diagnostic> out) {
        if (path.contains(decl) || !seen.add(new Instance(decl, args))) {
            return;
        }
        path.add(decl);
        try {
            if (at != null) {
                out.addAll(resolver.argumentErrors(at.module(), decl, args, at.span()));
            }
            Map<TypeParameterType, Type> bindings = new HashMap<>();
            for (int i = 0; i < decl.typeParams.size() && i < args.size(); i++) {
                bindings.put(new TypeParameterType(decl, decl.typeParams.get(i)), args.get(i));
            }
            for (Key key : keysIn.getOrDefault(decl, Set.of())) {
                Type actual = Substitution.apply(key.type(), bindings).nonNull();
                if (actual == NativeType.FLOAT || actual == NativeType.FLOAT32) {
                    out.add(Diagnostic.error(Codes.NUM_CONVERSION, Phase.TYPE, TypeRefResolver.FLOAT_MAP_KEY,
                            key.module().uri, key.span()).withHint(TypeChecker.FLOAT_MAP_KEY_HINT));
                }
            }
            for (Use use : usesIn.getOrDefault(decl, Set.of())) {
                List<Type> substituted = new ArrayList<>();
                for (Type arg : use.args()) substituted.add(Substitution.apply(arg, bindings));
                explore(resolver, use.target(), substituted, use, seen, path, out);
            }
        } finally {
            path.remove(decl);
        }
    }

    /** The generic declaration whose type parameter the type names, or null. */
    private static Decl parameterOwner(Type type) {
        Decl[] owner = new Decl[1];
        TypeArgumentInference.visit(type, part -> {
            if (part instanceof TypeParameterType parameter && parameter.owner != null) {
                owner[0] = parameter.owner;
                return true;
            }
            return false;
        });
        return owner[0];
    }
}
