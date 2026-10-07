package sprig.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime representation of Sprig's {@code MutableList[T]}. A MutableList is
 * accepted where a {@code List} is expected as the same list seen read-only,
 * which this inheritance carries without a copy; the reverse direction and
 * mutation through a {@code List} are rejected by the checker.
 */
public class SprigMutableList<T> extends SprigList<T> {
    public SprigMutableList(List<T> items) {
        super(items);
    }

    public SprigMutableList() {
        super(new ArrayList<>());
    }

    public void append(T value) {
        items.add(value);
    }

    public void set(long index, T value) {
        items.set(intIndex(index), value);
    }

    public void insert(long index, T value) {
        int i = intIndex(index);
        if (i < 0 || i > items.size()) {
            throw new IndexOutOfBoundsException("insert index out of range: " + index);
        }
        items.add(i, value);
    }

    public T removeAt(long index) {
        return items.remove(intIndex(index));
    }

    public boolean remove(Object value) {
        return items.remove(value);
    }

    public void clear() {
        items.clear();
    }

    /**
     * Ascending and stable in compareTo order, which sort() has always used:
     * UTF-16 code units for String (as {@code <}), and for Float/Float32 the
     * JDK's compare, so -0.0 before 0.0 and NaN last. A null comparator sorts
     * by exactly those compareTo calls (ComparableTimSort is TimSort without
     * the Comparator indirection).
     */
    public void sortInPlace() {
        items.sort(null);
    }

    @Override
    public SprigList<T> toList() {
        return new SprigList<>(new ArrayList<>(items));
    }

    @Override
    public SprigMutableList<T> toMutableList() {
        return this;
    }

    // ---- constructors used by generated code ----

    @SafeVarargs
    public static <T> SprigMutableList<T> ofItems(T... items) {
        List<T> copy = new ArrayList<>(items.length);
        for (T item : items) {
            copy.add(item);
        }
        return new SprigMutableList<>(copy);
    }

    public static <T> SprigMutableList<T> empty() {
        return new SprigMutableList<>();
    }
}
