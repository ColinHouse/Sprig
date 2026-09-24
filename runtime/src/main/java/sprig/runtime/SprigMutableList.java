package sprig.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime representation of Sprig's {@code MutableList[T]}. Distinct from
 * {@link SprigList} at the Sprig type level; the Java inheritance below is an
 * implementation detail (the Sprig checker rejects implicit mutable/immutable
 * conversion, so a {@code MutableList} value never silently flows into a
 * {@code List} variable).
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void sortInPlace() {
        items.sort((a, b) -> ((Comparable) a).compareTo(b));
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
