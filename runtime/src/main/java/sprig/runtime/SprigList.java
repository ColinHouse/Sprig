package sprig.runtime;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
        return items.contains(value);
    }

    public long indexOf(Object value) {
        return items.indexOf(value);
    }

    public SprigList<T> toList() {
        return this;
    }

    public SprigMutableList<T> toMutableList() {
        return new SprigMutableList<>(new ArrayList<>(items));
    }

    public <R> SprigList<R> map(Fn1<? super T, ? extends R> f) {
        List<R> out = new ArrayList<>(items.size());
        for (T item : items) {
            out.add(f.apply(item));
        }
        return new SprigList<>(out);
    }

    public SprigList<T> filter(Fn1<? super T, Boolean> predicate) {
        List<T> out = new ArrayList<>();
        for (T item : items) {
            if (Boolean.TRUE.equals(predicate.apply(item))) {
                out.add(item);
            }
        }
        return new SprigList<>(out);
    }

    public void forEachItem(Fn1<? super T, Void> action) {
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
