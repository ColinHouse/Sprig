package sprig.compiler.sem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Expr;
import sprig.compiler.types.ClassType;
import sprig.compiler.types.FunctionType;
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
 * Infers the type arguments of one generic Sprig call from its argument
 * expressions, and from nothing else: not from the expected type, the
 * assignment target or the result.
 *
 * <p>Matching is structural over Sprig types: a type parameter, {@code T?},
 * {@code List}/{@code MutableList}, {@code Map}/{@code MutableMap}, function
 * types and generic class or variant applications. Java generic types are never
 * looked into. A position inside another type is exact, because generic types
 * are invariant; an argument passed straight to a parameter, an element of a
 * list or map literal and the result of a lambda are flexible, because there
 * the value only has to be assignable. Of the flexible types the one every
 * other is assignable to wins, so {@code Int32} and {@code Int} give
 * {@code Int}, {@code Int32?} and {@code Int} give {@code Int?}, and a variant
 * case gives its variant. An unannotated numeric literal, and a list or map
 * literal typed as a whole, is weak: it counts only when nothing else says
 * what the parameter is, so a written {@code List[String]} wins over the
 * {@code MutableList[String]} a literal has on its own. {@code null},
 * {@code []} and {@code {}} say nothing.
 *
 * <p>Nothing here reports a diagnostic. The checker turns an incomplete
 * {@link Solution} into {@code SPR-TYPE-GENERIC-ARGS-REQUIRED} and checks every
 * argument against the substituted parameter types afterwards, so a literal
 * adapts and a wrong argument gets the error it gets with written type
 * arguments.
 */
final class TypeArgumentInference {
    /** What the checker knows about the arguments of the call. */
    interface Arguments {
        /**
         * The type of an argument or of a part of one, checked without an
         * expected type; null when checking it reported an error, which makes
         * it no evidence.
         */
        Type typeOf(Expr part);

        /** Whether a written nullable type argument for the parameter would be accepted. */
        boolean acceptsNullable(String parameter, Type argument);
    }

    /** One piece of evidence: a position in an argument says the parameter is this type. */
    private record Bound(Type type, boolean exact, boolean weak, String source) {
    }

    /** Why an argument gave a parameter no type. */
    enum Silence {
        /** null, an empty literal or one holding only those: no type of its own. */
        NOTHING,
        /** Its type has another shape than the parameter's type, so the argument check reports it. */
        MISFIT,
        /** A Unit result, which is not a value. */
        UNIT,
        /** Only a Java type mentions the parameter, and Java type arguments are never inferred. */
        JAVA
    }

    /** The first argument that gave a parameter no type, why, and the top-level argument it is part of. */
    record Failure(Silence kind, String reason, Expr argument) {
    }

    /** The inferred arguments in declaration order, or why some could not be inferred. */
    static final class Solution {
        /** One entry per type parameter; null where it is unknown. */
        final List<Type> arguments;
        /** Parameter to the reason it is unknown, in declaration order. */
        final Map<String, String> failures;
        /** Unknown parameters whose only word came from arguments that gave no type, and why. */
        final Map<String, Failure> silences;
        /** Unknown parameters whose only evidence was an argument that has an error of its own. */
        final Set<String> blocked;
        /** Unknown parameters that two arguments gave different types. */
        final Set<String> conflicts;
        /** Inferred parameters to the argument their type came from, as in "argument 2". */
        final Map<String, String> sources;

        private Solution(List<Type> arguments, Map<String, String> failures, Map<String, Failure> silences,
                         Set<String> blocked, Set<String> conflicts, Map<String, String> sources) {
            this.arguments = arguments;
            this.failures = failures;
            this.silences = silences;
            this.blocked = blocked;
            this.conflicts = conflicts;
            this.sources = sources;
        }

        boolean complete() {
            return failures.isEmpty() && blocked.isEmpty();
        }
    }

    private final Decl owner;
    private final List<String> parameters;
    private final Arguments arguments;
    private final Map<String, List<Bound>> bounds = new LinkedHashMap<>();
    /** The first reason an argument said nothing usable about a parameter. */
    private final Map<String, Failure> silent = new LinkedHashMap<>();
    private final Set<String> poisoned = new LinkedHashSet<>();
    private final Set<String> mentioned = new LinkedHashSet<>();
    /** The top-level argument being read. */
    private Expr current;

    TypeArgumentInference(Decl owner, Arguments arguments) {
        this.owner = owner;
        this.parameters = owner.typeParams;
        this.arguments = arguments;
    }

    /** Adds the evidence of one argument passed for its declared parameter or field type. */
    void argument(Type declared, Expr value, String source) {
        mentioned.addAll(ownParameters(declared));
        current = value;
        part(declared, value, source, false);
    }

    /**
     * One argument or part of one. Literals are taken apart here, so each
     * element counts on its own; any other expression is typed as a whole.
     * Inside a lambda the checker typed every part already ({@code typed}).
     */
    private void part(Type declared, Expr value, String source, boolean typed) {
        if (!mentionsParameter(declared)) {
            return;
        }
        if (value instanceof Expr.NullLit) {
            silent(declared, source + " is null", Silence.NOTHING);
            return;
        }
        if (isNumericLiteral(value)) {
            bind(declared, literalType(value), source, false, true);
            return;
        }
        Type target = declared.nonNull();
        if (value instanceof Expr.ListLit list) {
            if (list.items.isEmpty()) {
                silent(declared, source + " is an empty list", Silence.NOTHING);
                return;
            }
            if (target instanceof ListType listType) {
                for (int i = 0; i < list.items.size(); i++) {
                    part(listType.element, list.items.get(i), "element " + (i + 1) + " of " + source, typed);
                }
                return;
            }
        }
        if (value instanceof Expr.MapLit map) {
            if (map.keys.isEmpty()) {
                silent(declared, source + " is an empty map", Silence.NOTHING);
                return;
            }
            if (target instanceof MapType mapType) {
                for (int i = 0; i < map.keys.size(); i++) {
                    part(mapType.key, map.keys.get(i), "key " + (i + 1) + " of " + source, typed);
                    part(mapType.value, map.values.get(i), "value " + (i + 1) + " of " + source, typed);
                }
                return;
            }
        }
        if (saysNothing(value)) {
            // [null] or [[]] has no type of its own to give T.
            silent(declared, source + " holds only null or empty literals", Silence.NOTHING);
            return;
        }
        Type type = typed ? value.type : arguments.typeOf(value);
        if (type == null) {
            poison(declared);
            return;
        }
        if (value instanceof Expr.Lambda lambda && target instanceof FunctionType function
                && type instanceof FunctionType && function.params.size() == lambda.params.size()) {
            // The parameters are written, so they are exact; the body is
            // the lambda's result, which only has to be assignable.
            for (int i = 0; i < lambda.params.size(); i++) {
                Type param = lambda.params.get(i).type;
                bind(function.params.get(i), param == null ? NativeType.ERROR : param, source, true, false);
            }
            part(function.result, lambda.body, "the result of the lambda in " + source, true);
            return;
        }
        bind(declared, type, source, false, weak(value));
    }

    private void bind(Type declared, Type actual, String source, boolean exact, boolean weak) {
        if (!mentionsParameter(declared)) {
            return;
        }
        if (actual == null || containsError(actual)) {
            poison(declared);
            return;
        }
        if (actual == NativeType.UNIT) {
            silent(declared, source + " is a Unit result, which is not a value", Silence.UNIT);
            return;
        }
        if (actual == NativeType.NULL) {
            silent(declared, source + " is null", Silence.NOTHING);
            return;
        }
        if (visit(actual, part -> part == NativeType.NULL)) {
            // {"a": null} is typed MutableMap[String, null], which no written type argument can be.
            silent(declared, source + " is " + actual.display(), Silence.NOTHING);
            return;
        }
        if (declared instanceof TypeParameterType parameter && own(parameter)) {
            Type bound = actual;
            // A bare T takes X? only where a written [X?] would be accepted;
            // otherwise T is X, and the argument check reports the nullable
            // value. An exact position keeps X?: List[T] against List[X?] is
            // T = X? or nothing, so the call reports the nullable argument the
            // way a written [X?] is reported.
            if (bound.isNullable() && !exact && !arguments.acceptsNullable(parameter.name, bound)) {
                bound = bound.nonNull();
            }
            bounds.computeIfAbsent(parameter.name, ignored -> new ArrayList<>())
                    .add(new Bound(bound, exact, weak, source));
            return;
        }
        if (declared instanceof NullableType optional) {
            bind(optional.inner, actual.nonNull(), source, exact, weak);
            return;
        }
        // A nullable argument for a non-null shape still says what is inside
        // it; the argument check reports the null.
        Type value = actual.nonNull();
        if (declared instanceof ListType list && value instanceof ListType other) {
            bind(list.element, other.element, source, true, weak);
            return;
        }
        if (declared instanceof MapType map && value instanceof MapType other) {
            bind(map.key, other.key, source, true, weak);
            bind(map.value, other.value, source, true, weak);
            return;
        }
        if (declared instanceof FunctionType function && value instanceof FunctionType other
                && other.params.size() == function.params.size()) {
            for (int i = 0; i < function.params.size(); i++) {
                bind(function.params.get(i), other.params.get(i), source, true, weak);
            }
            bind(function.result, other.result, source, true, weak);
            return;
        }
        if (declared instanceof ClassType type && value instanceof ClassType other
                && other.decl == type.decl && other.args.size() == type.args.size()) {
            for (int i = 0; i < type.args.size(); i++) {
                bind(type.args.get(i), other.args.get(i), source, true, weak);
            }
            return;
        }
        if (declared instanceof VariantType type) {
            List<Type> args = value instanceof VariantType other && other.decl == type.decl ? other.args
                    : value instanceof VariantCaseType other && other.variant == type.decl ? other.variantArgs
                    : null;
            if (args != null && args.size() == type.args.size()) {
                for (int i = 0; i < type.args.size(); i++) {
                    bind(type.args.get(i), args.get(i), source, true, weak);
                }
                return;
            }
        }
        if (declared instanceof VariantCaseType type && value instanceof VariantCaseType other
                && other.variant == type.variant && other.variantCase == type.variantCase
                && other.variantArgs.size() == type.variantArgs.size()) {
            for (int i = 0; i < type.variantArgs.size(); i++) {
                bind(type.variantArgs.get(i), other.variantArgs.get(i), source, true, weak);
            }
            return;
        }
        if (declared instanceof JavaType javaType) {
            silent(declared, "only the Java type " + javaType.display() + " of " + source
                    + " mentions it, and Java type arguments are never inferred", Silence.JAVA);
            return;
        }
        silent(declared, source + " is " + actual.display() + ", which does not fit " + declared.display(),
                Silence.MISFIT);
    }

    Solution solve() {
        List<Type> solved = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();
        Map<String, Failure> silences = new LinkedHashMap<>();
        Set<String> blocked = new LinkedHashSet<>();
        Set<String> conflicts = new LinkedHashSet<>();
        Map<String, String> sources = new LinkedHashMap<>();
        for (String parameter : parameters) {
            List<Bound> all = bounds.getOrDefault(parameter, List.of());
            List<Bound> pool = new ArrayList<>();
            for (Bound bound : all) {
                if (!bound.weak()) pool.add(bound);
            }
            // A numeric literal counts only when nothing else says what it is.
            if (pool.isEmpty()) {
                pool.addAll(all);
            }
            if (pool.isEmpty()) {
                solved.add(null);
                if (poisoned.contains(parameter)) {
                    blocked.add(parameter);
                } else if (silent.containsKey(parameter)) {
                    Failure failure = silent.get(parameter);
                    failures.put(parameter, failure.reason());
                    silences.put(parameter, failure);
                } else if (mentioned.contains(parameter)) {
                    failures.put(parameter, "no argument says what " + parameter + " is");
                } else {
                    failures.put(parameter, "no argument mentions " + parameter);
                }
                continue;
            }
            Bound[] decided = new Bound[2];
            Type type = resolve(pool, decided);
            solved.add(type);
            if (type == null) {
                conflicts.add(parameter);
                failures.put(parameter, parameter + " is " + decided[0].type().display() + " from "
                        + decided[0].source() + " but " + decided[1].type().display() + " from "
                        + decided[1].source());
            } else {
                sources.put(parameter, decided[0].source());
            }
        }
        return new Solution(solved, failures, silences, blocked, conflicts, sources);
    }

    /**
     * The one type every bound agrees with: all exact bounds must be equal and
     * every flexible bound assignable to it. Without an exact bound, the least
     * candidate every flexible bound is assignable to wins. A candidate is a
     * bound's type with a variant case read as its variant, so Some(value=1)
     * makes T Option[Int], as written type arguments would, and cases of one
     * variant meet at the variant; when some bound is nullable, each type is
     * also a candidate as nullable, so Int32? and Int meet at Int? in either
     * order. An exact position keeps the case: List[Option[Int].Some] is not
     * List[Option[Int]]. On success {@code decided[0]} is the bound the type
     * came from; on a conflict it holds two bounds that disagree.
     */
    private static Type resolve(List<Bound> pool, Bound[] decided) {
        Bound anchor = null;
        for (Bound bound : pool) {
            if (bound.exact()) {
                anchor = bound;
                break;
            }
        }
        if (anchor != null) {
            for (Bound bound : pool) {
                boolean fits = bound.exact() ? bound.type().equals(anchor.type())
                        : Semantics.isAssignable(anchor.type(), bound.type());
                if (!fits) {
                    decided[0] = anchor;
                    decided[1] = bound;
                    return null;
                }
            }
            decided[0] = anchor;
            return anchor.type();
        }
        boolean nullable = false;
        for (Bound bound : pool) {
            nullable |= bound.type().isNullable();
        }
        List<Type> candidates = new ArrayList<>();
        List<Bound> origins = new ArrayList<>();
        for (Bound bound : pool) {
            Type widened = widenCase(bound.type());
            candidates.add(widened);
            origins.add(bound);
            if (nullable && !widened.isNullable()) {
                candidates.add(NullableType.of(widened));
                origins.add(bound);
            }
        }
        Type best = null;
        Bound origin = null;
        for (int i = 0; i < candidates.size(); i++) {
            Type candidate = candidates.get(i);
            boolean fits = true;
            for (Bound bound : pool) {
                if (!Semantics.isAssignable(candidate, bound.type())) {
                    fits = false;
                    break;
                }
            }
            // Of two fitting candidates the narrower one wins, whichever came first.
            if (fits && (best == null || Semantics.isAssignable(best, candidate) && !best.equals(candidate))) {
                best = candidate;
                origin = origins.get(i);
            }
        }
        if (best != null) {
            decided[0] = origin;
            return best;
        }
        Bound first = pool.get(0);
        decided[0] = first;
        decided[1] = pool.get(pool.size() - 1);
        for (Bound bound : pool) {
            if (!Semantics.isAssignable(first.type(), bound.type())
                    && !Semantics.isAssignable(bound.type(), first.type())) {
                decided[1] = bound;
                break;
            }
        }
        return null;
    }

    /** A variant case type, possibly nullable, as its variant; any other type unchanged. */
    private static Type widenCase(Type type) {
        if (type.nonNull() instanceof VariantCaseType caseType) {
            Type variant = new VariantType(caseType.variant, caseType.variantArgs);
            return type.isNullable() ? NullableType.of(variant) : variant;
        }
        return type;
    }

    private boolean own(TypeParameterType parameter) {
        return parameter.owner == owner && parameters.contains(parameter.name);
    }

    private void silent(Type declared, String fact, Silence kind) {
        for (String parameter : ownParameters(declared)) {
            silent.putIfAbsent(parameter, new Failure(kind,
                    kind == Silence.NOTHING ? fact + ", which says nothing about " + parameter : fact, current));
        }
    }

    private void poison(Type declared) {
        poisoned.addAll(ownParameters(declared));
    }

    private boolean mentionsParameter(Type type) {
        return visit(type, part -> part instanceof TypeParameterType parameter && own(parameter));
    }

    private Set<String> ownParameters(Type type) {
        Set<String> out = new LinkedHashSet<>();
        visit(type, part -> {
            if (part instanceof TypeParameterType parameter && own(parameter)) {
                out.add(parameter.name);
            }
            return false;
        });
        return out;
    }

    /** Whether a type contains the poison type of an already reported error. */
    static boolean containsError(Type type) {
        return visit(type, part -> part == NativeType.ERROR);
    }

    /**
     * Whether a type contains the stand-in for a type argument the call could
     * not establish; see {@link #unknown(String)}.
     */
    static boolean containsUnknown(Type type) {
        return visit(type, part -> part instanceof TypeParameterType parameter && parameter.owner == null);
    }

    /**
     * Stands for a type argument a failed inference could not establish, so the
     * arguments can still be checked against what is known. It never reaches
     * code generation: a call that has one is an error.
     */
    static TypeParameterType unknown(String parameter) {
        return new TypeParameterType(null, parameter);
    }

    /** Visits a type and its parts until the test is true. */
    private static boolean visit(Type type, Predicate<Type> test) {
        if (type == null) {
            return false;
        }
        if (test.test(type)) {
            return true;
        }
        if (type instanceof NullableType nullableType) {
            return visit(nullableType.inner, test);
        }
        if (type instanceof ListType list) {
            return visit(list.element, test);
        }
        if (type instanceof MapType map) {
            return visit(map.key, test) || visit(map.value, test);
        }
        if (type instanceof FunctionType function) {
            for (Type param : function.params) {
                if (visit(param, test)) return true;
            }
            return visit(function.result, test);
        }
        List<Type> args = type instanceof ClassType classType ? classType.args
                : type instanceof VariantType variant ? variant.args
                : type instanceof VariantCaseType variantCase ? variantCase.variantArgs
                : type instanceof JavaType javaType ? javaType.args
                : List.of();
        for (Type arg : args) {
            if (visit(arg, test)) return true;
        }
        return false;
    }

    /** An unannotated numeric literal, possibly signed: its type follows from where it goes. */
    static boolean isNumericLiteral(Expr expr) {
        if (expr instanceof Expr.IntLit || expr instanceof Expr.FloatLit) {
            return true;
        }
        return expr instanceof Expr.Unary unary && !unary.op.equals("not") && isNumericLiteral(unary.operand);
    }

    private static Type literalType(Expr expr) {
        if (expr instanceof Expr.Unary unary) {
            return literalType(unary.operand);
        }
        return expr instanceof Expr.FloatLit ? NativeType.FLOAT : NativeType.INT;
    }

    /** null, or a list or map literal made only of null and empty literals. */
    private static boolean saysNothing(Expr expr) {
        if (expr instanceof Expr.NullLit) {
            return true;
        }
        if (expr instanceof Expr.ListLit list) {
            return list.items.stream().allMatch(TypeArgumentInference::saysNothing);
        }
        if (expr instanceof Expr.MapLit map) {
            return map.keys.stream().allMatch(TypeArgumentInference::saysNothing)
                    && map.values.stream().allMatch(TypeArgumentInference::saysNothing);
        }
        return false;
    }

    /**
     * A numeric literal, or a list or map literal typed as a whole: its type
     * follows from where it goes, so a written argument's type wins over it.
     * On its own, [x] is a MutableList, as in a let without a type.
     */
    private static boolean weak(Expr expr) {
        return isNumericLiteral(expr) || expr instanceof Expr.ListLit || expr instanceof Expr.MapLit;
    }
}
