package sprig.runtime.jvm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import sprig.runtime.SprigError;
import sprig.runtime.SprigList;
import sprig.runtime.SprigMap;

/**
 * Explicit Java collection adapters. Java collections never convert
 * implicitly; a Sprig snapshot is complete when the adapter returns and a
 * Sprig-to-Java copy is an independent mutable container. v1 snapshot adapters
 * validate non-null contents because a Java generic argument is not a
 * nullability proof.
 */
public final class HostCollections {
    private HostCollections() {}

    @SuppressWarnings("unchecked")
    public static <T> SprigList<T> listSnapshot(List<T> source) {
        if (source == null) throw new SprigError("java.util.List is null");
        List<T> out = new ArrayList<>(source.size());
        for (Object value : source) {
            if (value == null) {
                throw new SprigError("java.util.List contains null; "
                        + "the v1 snapshot adapter copies validated non-null contents only");
            }
            out.add((T) value);
        }
        return new SprigList<>(out);
    }

    @SuppressWarnings("unchecked")
    public static <K, V> SprigMap<K, V> mapSnapshot(Map<K, V> source) {
        if (source == null) throw new SprigError("java.util.Map is null");
        Map<K, V> out = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new SprigError("java.util.Map contains a null key or value; "
                        + "the v1 snapshot adapter copies validated non-null contents only");
            }
            out.put(entry.getKey(), entry.getValue());
        }
        return new SprigMap<>(out);
    }

    @SuppressWarnings("unchecked")
    public static <T> ArrayList<T> listCopy(SprigList<T> source) {
        if (source == null) throw new SprigError("Sprig List is null");
        ArrayList<T> out = new ArrayList<>((int) source.size());
        for (Object value : source) {
            if (value == null) throw new SprigError("Sprig List contains null");
            out.add((T) value);
        }
        return out;
    }

    public static <K, V> LinkedHashMap<K, V> mapCopy(SprigMap<K, V> source) {
        if (source == null) throw new SprigError("Sprig Map is null");
        LinkedHashMap<K, V> out = new LinkedHashMap<>();
        for (K key : source.keys()) {
            if (key == null) throw new SprigError("Sprig Map contains a null key");
            V value = source.get(key);
            if (value == null) throw new SprigError("Sprig Map contains a null value");
            out.put(key, value);
        }
        return out;
    }
}
