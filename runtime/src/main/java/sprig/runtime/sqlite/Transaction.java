package sprig.runtime.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import sprig.runtime.NonNull;
import sprig.runtime.SprigError;

/** Callback-scoped operations; statements and results never escape a call. */
public final class Transaction {
    private Connection connection;
    private final Thread owner = Thread.currentThread();
    private RuntimeException failed;

    Transaction(Connection connection) { this.connection = connection; }

    private void checkActive() {
        if (Thread.currentThread() != owner)
            throw new SqliteError("SQLite transaction must stay on its callback thread");
        if (connection == null) throw new SqliteError("SQLite transaction scope has ended");
    }

    void checkUsable() {
        checkActive();
        if (failed != null) throw failed;
    }

    void end() { connection = null; }

    private RuntimeException fail(RuntimeException failure) {
        if (failed == null) failed = failure;
        return failure;
    }

    /** Internal Sprig/Java callback bridge: preserve the exact recoverable error. */
    @NonNull
    public <T> T abort(SprigError failure) {
        checkActive();
        throw fail(failure);
    }

    public long execute(String sql, Parameters parameters) {
        checkUsable();
        try {
            Database.checkSql(sql);
            Database.checkOneStatement(sql);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                Parameters.bind(statement, parameters.snapshot());
                return statement.executeUpdate();
            }
        } catch (SQLException failure) {
            throw fail(new SqliteError("SQLite transaction execute failed", failure));
        } catch (RuntimeException failure) {
            throw fail(failure);
        }
    }

    public Rows query(String sql, Parameters parameters) {
        checkUsable();
        try {
            Database.checkSql(sql);
            Database.checkOneStatement(sql);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                Parameters.bind(statement, parameters.snapshot());
                try (var result = statement.executeQuery()) { return Rows.read(result); }
            }
        } catch (SQLException failure) {
            throw fail(new SqliteError("SQLite transaction query failed", failure));
        } catch (RuntimeException failure) {
            throw fail(failure);
        }
    }
}
