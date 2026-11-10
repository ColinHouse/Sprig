package sprig.runtime.sqlite;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.*;
import sprig.runtime.SprigError;

/** Real SQLite plus a package-private connection factory for observing and failing cleanup. */
public final class TransactionProbe {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static Parameters none() { return new Parameters(); }
    static Parameters text(String value) { Parameters p = none(); p.text(value); return p; }
    static RuntimeException failure(Runnable action) {
        try { action.run(); } catch (RuntimeException expected) { return expected; }
        throw new AssertionError("expected failure");
    }
    static long count(Database db) { return db.query("SELECT COUNT(*) AS n FROM entries", none()).integer(0, "n"); }
    static void usable(Database db, long expected) {
        check(count(db) == expected, "no partial writes");
        db.execute("INSERT INTO entries VALUES ('usability')", none());
        db.execute("DELETE FROM entries WHERE name='usability'", none());
    }
    public static void main(String[] args) throws Exception {
        Database db = new Database(args[0]);
        db.execute("CREATE TABLE entries(name TEXT UNIQUE)", none());
        Transaction[] escaped = new Transaction[1];
        Rows snapshot = db.transaction(tx -> {
            escaped[0] = tx;
            check(tx.query("SELECT COUNT(*) AS n FROM entries", none()).integer(0, "n") == 0, "query before write");
            tx.execute("INSERT INTO entries VALUES (?)", text("first"));
            check(tx.query("SELECT COUNT(*) AS n FROM entries", none()).integer(0, "n") == 1, "read uncommitted own write");
            return tx.query("INSERT INTO entries VALUES ('second') RETURNING name", none());
        });
        check(snapshot.text(0, "name").equals("second"), "detached returned rows");
        usable(db, 2);
        check(failure(() -> escaped[0].execute("DELETE FROM entries", none())) instanceof SqliteError, "ended execute is Error");
        check(failure(() -> escaped[0].query("SELECT 1", none())).getMessage().contains("scope"), "ended query");
        SprigError original = new SprigError("callback failure");
        check(failure(() -> db.transaction(tx -> {
            escaped[0] = tx;
            tx.execute("INSERT INTO entries VALUES ('partial')", none()); throw original;
        })) == original, "callback identity retained");
        check(failure(() -> escaped[0].query("SELECT 1", none())).getMessage().contains("scope"), "failed scope ended");
        usable(db, 2);
        RuntimeException sql = failure(() -> db.transaction(tx -> {
            tx.execute("INSERT INTO entries VALUES ('partial')", none());
            return tx.execute("INSERT INTO entries VALUES ('first')", none());
        }));
        check(sql instanceof SqliteError && sql.getCause() instanceof SQLException, "SQL cause retained");
        usable(db, 2);
        failure(() -> db.transaction(tx -> {
            tx.execute("INSERT INTO entries VALUES ('partial')", none());
            try { tx.execute("INSERT INTO missing VALUES (1)", none()); } catch (SqliteError ignored) {}
            return 1;
        }));
        usable(db, 2);
        for (String control : new String[]{"BEGIN", "COMMIT", "END", "ROLLBACK", "SAVEPOINT s", "RELEASE s"}) {
            failure(() -> db.transaction(tx -> {
                tx.execute("INSERT INTO entries VALUES ('partial')", none());
                return tx.execute("; /* leading */ " + control, none());
            }));
            usable(db, 2);
        }
        failure(() -> db.transaction(tx -> tx.query("SELECT 1; DELETE FROM entries", none())));
        failure(() -> db.transaction(tx -> {
            tx.execute("INSERT INTO entries VALUES ('partial')", none());
            return tx.query("INSERT INTO entries VALUES ('blob') RETURNING CAST(name AS BLOB) AS data", none());
        }));
        usable(db, 2);
        failure(() -> db.transaction(tx -> new Database(args[0]).transaction(inner -> 1)));
        usable(db, 2);
        db.transaction(tx -> {
            final RuntimeException[] problem = new RuntimeException[1];
            Thread thread = new Thread(() -> problem[0] = failure(() -> tx.query("SELECT 1", none())));
            thread.start();
            try { thread.join(); } catch (InterruptedException e) { throw new AssertionError(e); }
            check(problem[0] instanceof SqliteError, "scope confined to callback thread");
            return 1;
        });
        // Inside the callback the database itself refuses work at once. A second connection
        // would not see the transaction's writes, and a write through it would wait for the
        // lock this thread holds. The transaction goes on, and another file is another database.
        Database other = new Database(args[0] + ".other");
        other.execute("CREATE TABLE notes(text TEXT)", none());
        long guarded = db.transaction(tx -> {
            tx.execute("INSERT INTO entries VALUES ('guarded')", none());
            Runnable[] misuses = {
                () -> db.query("SELECT COUNT(*) AS n FROM entries", none()),
                () -> db.execute("DELETE FROM entries", none()),
                () -> db.batch(new Batch()),
                () -> new Database(args[0]).initialize(),
            };
            for (Runnable misuse : misuses) {
                RuntimeException refused = failure(misuse);
                check(refused instanceof SqliteError && refused.getCause() == null
                        && refused.getMessage().equals("This database has a transaction running on this thread; "
                                + "call query and execute on the Transaction passed to the callback"),
                        "database call inside its transaction: " + refused);
            }
            other.execute("INSERT INTO notes VALUES ('another file')", none());
            return tx.query("SELECT COUNT(*) AS n FROM entries", none()).integer(0, "n");
        });
        check(guarded == 3, "the transaction commits after a refused database call");
        check(other.query("SELECT COUNT(*) AS n FROM notes", none()).integer(0, "n") == 1,
              "another database works inside the callback");
        check(db.execute("DELETE FROM entries WHERE name='guarded'", none()) == 1, "the database works again afterwards");
        usable(db, 2);
        // Counters track actual close calls; injected failures happen after the real JDBC close.
        Observed observed = new Observed();
        Database measured = new Database(args[0], observed::open);
        measured.transaction(tx -> tx.query("SELECT * FROM entries", none()));
        observed.closed(1, 2, 1);
        observed.reset();
        failure(() -> measured.transaction(tx -> tx.execute("INSERT INTO entries VALUES ('first')", none())));
        observed.closed(1, 2, 0);
        check(observed.rollbacks == 1, "SQL failure rolled back");
        observed.reset();
        failure(() -> measured.transaction(tx -> { tx.query("SELECT * FROM entries", none()); throw original; }));
        observed.closed(1, 2, 1);
        check(observed.rollbacks == 1, "callback failure rolled back");
        observed.reset();
        failure(() -> measured.transaction(tx -> tx.query("SELECT 1 AS same, 2 AS same", none())));
        observed.closed(1, 2, 1);
        check(observed.rollbacks == 1, "snapshot validation closes resources and rolls back");
        observed.reset(); observed.failResult = true;
        check(failure(() -> measured.transaction(tx -> tx.query("SELECT * FROM entries", none()))).getCause() instanceof SQLException,
              "result close failure preserves SQL cause");
        observed.closed(1, 2, 1);
        check(observed.rollbacks == 1, "result close failure rolls back");
        observed.reset(); observed.failStatement = true;
        RuntimeException close = failure(() -> measured.transaction(tx -> tx.execute("INSERT INTO entries VALUES ('partial')", none())));
        check(close.getCause() instanceof SQLException, "statement close error has SQL cause");
        observed.closed(1, 2, 0); usable(db, 2);
        observed.reset(); observed.failStatement = true;
        RuntimeException primarySql = failure(() -> measured.transaction(tx -> tx.execute("INSERT INTO entries VALUES ('first')", none())));
        check(((SqliteError) primarySql).constraint(), "statement cleanup preserves constraint failure");
        check(primarySql.getCause().getSuppressed().length == 1, "statement cleanup suppressed on SQL cause");
        observed.reset(); observed.failRollback = observed.failConnection = true;
        check(failure(() -> measured.transaction(tx -> { throw original; })) == original, "cleanup never replaces callback error");
        check(original.getSuppressed().length == 2, "rollback and connection close failures suppressed");
        observed.closed(1, 1, 0);
        observed.reset(); observed.failCommit = true;
        RuntimeException commit = failure(() -> measured.transaction(tx -> tx.execute("INSERT INTO entries VALUES ('partial')", none())));
        check(commit.getCause() instanceof SQLException && observed.rollbacks == 1, "commit failure rolls back");
        observed.closed(1, 2, 0); usable(db, 2);
        observed.reset(); observed.failConnection = true;
        check(failure(() -> measured.transaction(tx -> 42)).getCause() instanceof SQLException, "close failure after success reported");
        observed.closed(1, 1, 0);
        observed.reset(); observed.failSetup = true;
        failure(() -> measured.transaction(tx -> 42));
        observed.closed(1, 1, 0);
        System.out.println("SQLite transactions: commit/rollback/scope/causes/observed cleanup PASS");
    }
    static final class Observed {
        int connections, statements, results, rollbacks;
        boolean failStatement, failResult, failConnection, failRollback, failCommit, failSetup;
        void reset() { connections = statements = results = rollbacks = 0; failStatement = failResult = failConnection = failRollback = failCommit = failSetup = false; }
        void closed(int c, int s, int r) { check(connections == c && statements == s && results == r, "close counts: " + connections + "/" + statements + "/" + results); }
        Connection open(String url) throws SQLException { return wrap(DriverManager.getConnection(url), Connection.class); }
        @SuppressWarnings("unchecked")
        <T> T wrap(T real, Class<T> type) {
            return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("commit") && failCommit) throw new SQLException("injected commit");
                if (name.equals("setAutoCommit") && failSetup) throw new SQLException("injected setup");
                Object value;
                try { value = method.invoke(real, args); }
                catch (InvocationTargetException error) { throw error.getCause(); }
                if (name.equals("rollback")) { rollbacks++; if (failRollback) throw new SQLException("injected rollback"); }
                if (name.equals("close")) {
                    if (type == Connection.class) { connections++; if (failConnection) throw new SQLException("injected connection close"); }
                    else if (type == ResultSet.class) { results++; if (failResult) throw new SQLException("injected result close"); }
                    else { statements++; if (type == PreparedStatement.class && failStatement) throw new SQLException("injected statement close"); }
                }
                if (method.getReturnType() == PreparedStatement.class) return wrap((PreparedStatement) value, PreparedStatement.class);
                if (method.getReturnType() == Statement.class) return wrap((Statement) value, Statement.class);
                if (method.getReturnType() == ResultSet.class) return wrap((ResultSet) value, ResultSet.class);
                return value;
            });
        }
    }
}
