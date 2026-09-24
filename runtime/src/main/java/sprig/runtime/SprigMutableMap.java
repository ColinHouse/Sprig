package sprig.runtime;

import java.util.LinkedHashMap;

/** Runtime representation of Sprig's {@code MutableMap[K, V]}. */
public class SprigMutableMap<K, V> extends SprigMap<K, V> {
    public SprigMutableMap(LinkedHashMap<K, V> entries) {
        super(entries);
    }

    public SprigMutableMap() {
        super(new LinkedHashMap<>());
    }

    public void set(K key, V value) {
        entries.put(key, value);
    }

    /** Removes the key and returns its previous value, or {@code null}. */
    public V remove(K key) {
        return entries.remove(key);
    }

    public void clear() {
        entries.clear();
    }

    @Override
    public SprigMap<K, V> toMap() {
        return new SprigMap<>(new LinkedHashMap<>(entries));
    }

    @Override
    public SprigMutableMap<K, V> toMutableMap() {
        return this;
    }

    public static <K, V> SprigMutableMap<K, V> ofEntries(Object... kv) {
        LinkedHashMap<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            @SuppressWarnings("unchecked")
            K key = (K) kv[i];
            @SuppressWarnings("unchecked")
            V value = (V) kv[i + 1];
            map.put(key, value);
        }
        return new SprigMutableMap<>(map);
    }

    public static <K, V> SprigMutableMap<K, V> empty() {
        return new SprigMutableMap<>();
    }
}
