package sprig.runtime.host;

/** Ascending order for Comparable Sprig values: the same total order MutableList.sort() uses. */
public final class HostOrder {
    private HostOrder() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int compare(Object left, Object right) {
        return ((Comparable) left).compareTo(right);
    }
}
