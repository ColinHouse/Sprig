package sprig.runtime;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.RandomAccess;

/**
 * Runtime representation of Sprig's read-only {@code List[T]}.
 *
 * <p>A {@code SprigList} exposes only read operations. It does not protect the
 * elements themselves (Sprig finds "an immutable outer collection does not
 * recursively freeze its elements"). The backing list must not be mutated after
 * construction; mutation goes through {@link SprigMutableList#toList()} snapshots
 * or {@link #toMutableList()} copies.
 */
public class SprigList<T> implements Iterable<T> {
    protected final List<T> items;

    public SprigList(List<T> items) {
        this.items = items;
    }

    public long size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public T get(long index) {
        return items.get(intIndex(index));
    }

    public boolean contains(Object value) {
        return indexOf(value) >= 0;
    }

    /**
     * The first position whose element is {@code ==} the value, or -1; {@code
     * contains}, {@code remove} and {@code in} search the same way. A Float or
     * Float32 value compares with IEEE equality, as {@code ==} does: NaN is
     * found nowhere and -0.0 finds 0.0, where {@code Double.equals} would find
     * a NaN and tell the zeros apart. Every other value compares with equals,
     * which is what {@code ==} does for it, lists and variants holding floats
     * included.
     */
    public long indexOf(Object value) {
        if (value instanceof Double || value instanceof Float) {
            int index = 0;
            for (T item : items) {
                if (SprigRuntime.equalsValue(value, item)) {
                    return index;
                }
                index++;
            }
            return -1;
        }
        return items.indexOf(value);
    }

    public SprigList<T> toList() {
        return this;
    }

    public SprigMutableList<T> toMutableList() {
        return new SprigMutableList<>(new ArrayList<>(items));
    }

    // map, filter and forEach are eager: each runs its function on every item,
    // in order, before it returns, so in xs.map(f).filter(g) every call of f
    // comes before any call of g. Nothing changes a read-only list's items
    // while a function runs, so walking them by index visits exactly what the
    // iterator would. A MutableList, also when it is seen as a List, keeps its
    // iterator: a function that changes the list it walks gets the iterator's
    // behavior, usually a ConcurrentModificationException, as it always has.
    // Each loop has a method of its own, so the JIT compiles and profiles it
    // separately.

    public <R> SprigList<R> map(Fn1<? super T, ? extends R> f) {
        return new SprigList<>(indexed() ? mapByIndex(items, f) : mapByIterator(items, f));
    }

    public SprigList<T> filter(Fn1<? super T, Boolean> predicate) {
        int size = items.size();
        ArrayList<T> out = indexed() ? filterByIndex(items, predicate) : filterByIterator(items, predicate);
        // The result had room for every item, so it never regrew while it
        // filled; one that keeps fewer than half gives the spare room back.
        if (out.size() < size / 2) {
            out.trimToSize();
        }
        return new SprigList<>(out);
    }

    public void forEachItem(Fn1<? super T, Void> action) {
        if (indexed()) {
            forEachByIndex(items, action);
        } else {
            forEachByIterator(items, action);
        }
    }

    private boolean indexed() {
        return items instanceof RandomAccess && !(this instanceof SprigMutableList);
    }

    private static <T, R> List<R> mapByIndex(List<T> items, Fn1<? super T, ? extends R> f) {
        int size = items.size();
        List<R> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(f.apply(items.get(i)));
        }
        return out;
    }

    private static <T, R> List<R> mapByIterator(List<T> items, Fn1<? super T, ? extends R> f) {
        List<R> out = new ArrayList<>(items.size());
        for (T item : items) {
            out.add(f.apply(item));
        }
        return out;
    }

    private static <T> ArrayList<T> filterByIndex(List<T> items, Fn1<? super T, Boolean> predicate) {
        int size = items.size();
        ArrayList<T> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            T item = items.get(i);
            if (Boolean.TRUE.equals(predicate.apply(item))) {
                out.add(item);
            }
        }
        return out;
    }

    private static <T> ArrayList<T> filterByIterator(List<T> items, Fn1<? super T, Boolean> predicate) {
        ArrayList<T> out = new ArrayList<>(items.size());
        for (T item : items) {
            if (Boolean.TRUE.equals(predicate.apply(item))) {
                out.add(item);
            }
        }
        return out;
    }

    private static <T> void forEachByIndex(List<T> items, Fn1<? super T, Void> action) {
        int size = items.size();
        for (int i = 0; i < size; i++) {
            action.apply(items.get(i));
        }
    }

    private static <T> void forEachByIterator(List<T> items, Fn1<? super T, Void> action) {
        for (T item : items) {
            action.apply(item);
        }
    }

    @Override
    public Iterator<T> iterator() {
        return items.iterator();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SprigList<?> that)) {
            return false;
        }
        if (items.size() != that.items.size()) return false;
        for (int i = 0; i < items.size(); i++) {
            if (!SprigRuntime.equalsValue(items.get(i), that.items.get(i))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (T item : items) hash = 31 * hash + SprigRuntime.hashValue(item);
        return hash;
    }

    @Override
    public String toString() {
        return SprigRuntime.format(this);
    }

    static int intIndex(long index) {
        if (index < 0 || index > Integer.MAX_VALUE) {
            throw new IndexOutOfBoundsException("list index out of range: " + index);
        }
        return (int) index;
    }

    // ---- constructors used by generated code ----

    public static <T> SprigList<T> ofItems(T... items) {
        List<T> copy = new ArrayList<>(items.length);
        for (T item : items) {
            copy.add(item);
        }
        return new SprigList<>(java.util.Collections.unmodifiableList(copy));
    }

    public static <T> SprigList<T> empty() {
        return new SprigList<>(java.util.Collections.emptyList());
    }
}
