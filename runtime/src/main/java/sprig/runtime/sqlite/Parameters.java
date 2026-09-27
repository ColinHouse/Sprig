package sprig.runtime.sqlite;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Explicit parameter builder; no SQL interpolation or JDBC varargs surface. */
public final class Parameters {
    private final List<Object> values = new ArrayList<>();
    public Parameters() {}
    public void integer(long value) { values.add(value); }
    public void text(String value) { values.add(Objects.requireNonNull(value, "text parameter")); }
    public void bool(boolean value) { values.add(value ? 1L : 0L); }
    public void nil() { values.add(null); }
    List<Object> snapshot() { return java.util.Collections.unmodifiableList(new ArrayList<>(values)); }
    static void bind(PreparedStatement statement, List<Object> values) throws SQLException {
        if (statement.getParameterMetaData().getParameterCount() != values.size())
            throw new SqliteError("SQL parameter count does not match supplied values");
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (value == null) statement.setNull(index + 1, Types.NULL);
            else if (value instanceof Long number) statement.setLong(index + 1, number);
            else statement.setString(index + 1, (String) value);
        }
    }
}
