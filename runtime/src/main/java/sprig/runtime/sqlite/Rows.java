package sprig.runtime.sqlite;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Detached result snapshot. No ResultSet, connection or statement escapes. */
public final class Rows {
    private final List<String> columns;
    private final List<List<Object>> rows;
    private Rows(List<String> columns, List<List<Object>> rows) {
        this.columns = List.copyOf(columns);
        this.rows = List.copyOf(rows);
    }
    static Rows read(ResultSet result) throws SQLException {
        var metadata = result.getMetaData();
        List<String> columns = new ArrayList<>();
        for (int column = 1; column <= metadata.getColumnCount(); column++) {
            String label = metadata.getColumnLabel(column).toLowerCase(Locale.ROOT);
            if (columns.contains(label)) throw new SqliteError("Duplicate query column label: " + label);
            columns.add(label);
        }
        List<List<Object>> rows = new ArrayList<>();
        while (result.next()) {
            if (rows.size() >= 10000) throw new SqliteError("Query exceeds 10000 row snapshot limit");
            List<Object> row = new ArrayList<>();
            for (int column = 1; column <= columns.size(); column++) {
                Object value = result.getObject(column);
                if (!(value == null || value instanceof String || value instanceof Number))
                    throw new SqliteError("Unsupported SQLite result type; select integer or text columns explicitly");
                row.add(value);
            }
            rows.add(java.util.Collections.unmodifiableList(row));
        }
        return new Rows(columns, rows);
    }
    public long size() { return rows.size(); }
    private Object value(long row, String column) {
        if (row < 0 || row >= rows.size()) throw new SqliteError("Query row index out of bounds");
        int index = columns.indexOf(column.toLowerCase(Locale.ROOT));
        if (index < 0) throw new SqliteError("Unknown query column: " + column);
        return rows.get((int) row).get(index);
    }
    public boolean isNull(long row, String column) { return value(row, column) == null; }
    public long integer(long row, String column) {
        Object value = value(row, column);
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte)
            return ((Number) value).longValue();
        throw new SqliteError("Expected non-null SQLite INTEGER in column " + column);
    }
    public String text(long row, String column) {
        Object value = value(row, column);
        if (value instanceof String text) return text;
        throw new SqliteError("Expected non-null SQLite TEXT in column " + column);
    }
    public boolean bool(long row, String column) {
        long value = integer(row, column);
        if (value == 0) return false;
        if (value == 1) return true;
        throw new SqliteError("Expected SQLite boolean integer 0 or 1 in column " + column);
    }
}
