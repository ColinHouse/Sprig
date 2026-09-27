package sprig.runtime.sqlite;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Locale;
import java.util.Set;

/** Narrow file-backed JDBC boundary. SQL and business rules belong to Sprig. */
public final class Database {
    private final String file;
    public Database(String file) {
        Objects.requireNonNull(file, "database file");
        if (file.isBlank() || file.equals(":memory:") || file.startsWith("file:"))
            throw new SqliteError("Use an ordinary SQLite file path; URI and in-memory databases are unsupported");
        this.file = Path.of(file).toAbsolutePath().normalize().toString();
    }
    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        try {
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=5000");
            }
            return connection;
        } catch (SQLException | RuntimeException failure) {
            try { connection.close(); } catch (SQLException close) { failure.addSuppressed(close); }
            throw failure;
        }
    }
    public void initialize() {
        try (Connection connection = connect()) {
            // Establish the driver and file now; each later operation owns its resources.
        } catch (SQLException failure) { throw new SqliteError("Cannot open SQLite database", failure); }
    }
    /** Transaction lifetimes belong to batch(), never SQL control statements. */
    private static void checkSql(String sql) {
        Objects.requireNonNull(sql, "SQL");
        int offset = 0;
        while (offset < sql.length()) {
            if (Character.isWhitespace(sql.charAt(offset)) || sql.charAt(offset) == ';'
                    || sql.charAt(offset) == '\uFEFF') { offset++; continue; }
            if (sql.startsWith("--", offset)) {
                int end = sql.indexOf('\n', offset + 2);
                offset = end < 0 ? sql.length() : end + 1;
                continue;
            }
            if (sql.startsWith("/*", offset)) {
                int end = sql.indexOf("*/", offset + 2);
                if (end < 0) throw new SqliteError("Unterminated SQL comment");
                offset = end + 2;
                continue;
            }
            break;
        }
        int end = offset;
        while (end < sql.length() && Character.isLetter(sql.charAt(end))) end++;
        String keyword = sql.substring(offset, end).toUpperCase(Locale.ROOT);
        if (Set.of("BEGIN", "COMMIT", "END", "ROLLBACK", "SAVEPOINT", "RELEASE").contains(keyword))
            throw new SqliteError("SQL transaction control is unsupported; use an atomic batch");
    }
    public long execute(String sql, Parameters parameters) {
        checkSql(sql);
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            Parameters.bind(statement, parameters.snapshot());
            return statement.executeUpdate();
        } catch (SQLException failure) { throw new SqliteError("SQLite execute failed", failure); }
    }
    public Rows query(String sql, Parameters parameters) {
        checkSql(sql);
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                Rows rows;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    Parameters.bind(statement, parameters.snapshot());
                    try (var result = statement.executeQuery()) { rows = Rows.read(result); }
                }
                connection.commit();
                return rows;
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        } catch (SQLException failure) { throw new SqliteError("SQLite query failed", failure); }
    }

    public long batch(Batch batch) {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                long changed = 0;
                for (Batch.Command command : batch.snapshot()) {
                    checkSql(command.sql());
                    try (PreparedStatement statement = connection.prepareStatement(command.sql())) {
                        Parameters.bind(statement, command.parameters());
                        changed = Math.addExact(changed, statement.executeUpdate());
                    }
                }
                connection.commit();
                return changed;
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        } catch (SQLException failure) { throw new SqliteError("SQLite transaction failed", failure); }
    }
}
