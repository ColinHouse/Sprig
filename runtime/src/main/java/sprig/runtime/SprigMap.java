package sprig.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime representation of Sprig's read-only {@code Map[K, V]}.
 * Iteration order is insertion order (deterministic output for tests).
 */
public class SprigMap<K, V> {
    protected final Map<K, V> entries;

    public SprigMap(Map<K, V> entries) {
        this.entries = entries;
    }

    public long size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Returns {@code null} when the key is absent (Sprig type: {@code V?}). */
    public V get(K key) {
        return entries.get(key);
    }

    public boolean containsKey(K key) {
        return entries.containsKey(key);
    }

    public SprigList<K> keys() {
        return new SprigList<>(new ArrayList<>(entries.keySet()));
    }

    public SprigList<V> values() {
        return new SprigList<>(new ArrayList<>(entries.values()));
    }

    public SprigMap<K, V> toMap() {
        return this;
    }

    public SprigMutableMap<K, V> toMutableMap() {
        return new SprigMutableMap<>(new LinkedHashMap<>(entries));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SprigMap<?, ?> that)) {
            return false;
        }
        if (entries.size() != that.entries.size()) return false;
        for (var entry : entries.entrySet()) {
            if (!that.entries.containsKey(entry.getKey())) return false;
            if (!SprigRuntime.equalsValue(entry.getValue(), that.entries.get(entry.getKey()))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (var entry : entries.entrySet()) {
            hash += SprigRuntime.hashValue(entry.getKey()) ^ SprigRuntime.hashValue(entry.getValue());
        }
        return hash;
    }

    @Override
    public String toString() {
        return SprigRuntime.format(this);
    }

    public static <K, V> SprigMap<K, V> ofEntries(Object... kv) {
        return new SprigMap<>(pairs(kv));
    }

    public static <K, V> SprigMap<K, V> empty() {
        return new SprigMap<>(java.util.Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    static <K, V> Map<K, V> pairs(Object... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("map literal requires key/value pairs");
        }
        Map<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((K) kv[i], (V) kv[i + 1]);
        }
        return map;
    }
}
