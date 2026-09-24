package sprig.runtime;

@FunctionalInterface
public interface Fn1<A, R> {
    R apply(A a);
}
