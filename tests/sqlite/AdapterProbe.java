import java.nio.file.Path;
import sprig.runtime.sqlite.*;

/** Independent JVM oracle: real file, bound injection text, strict values and rollback. */
public class AdapterProbe {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static Parameters empty() { return new Parameters(); }
    static void fail(Runnable action) {
        try { action.run(); throw new AssertionError("expected SqliteError"); }
        catch (SqliteError expected) {}
    }
    public static void main(String[] args) {
        Database db = new Database(args[0]); db.initialize();
        db.execute("CREATE TABLE items(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE, amount INTEGER, flag INTEGER)", empty());
        Parameters values = new Parameters(); values.text("x'); DROP TABLE items;-- 世界"); values.integer(Long.MAX_VALUE); values.bool(false);
        db.execute("INSERT INTO items(name,amount,flag) VALUES (?,?,?)", values);
        Parameters nullable = new Parameters(); nullable.text(""); nullable.nil(); nullable.bool(true);
        db.execute("INSERT INTO items(name,amount,flag) VALUES (?,?,?)", nullable);
        Rows rows = new Database(args[0]).query("SELECT id,name,amount,flag FROM items ORDER BY id", empty());
        check(rows.size() == 2, "persistent rows");
        check(rows.text(0,"name").endsWith("世界"), "bound Unicode/injection remains text");
        check(rows.integer(0,"amount") == Long.MAX_VALUE, "exact signed64 integer");
        check(!rows.bool(0,"flag") && rows.bool(1,"flag"), "boolean");
        check(rows.text(1,"name").isEmpty() && rows.isNull(1,"amount"), "null and empty distinct");
        fail(() -> rows.integer(1,"amount")); fail(() -> rows.text(0,"amount"));
        fail(() -> rows.integer(99,"id")); fail(() -> rows.integer(0,"missing"));
        Rows floating = db.query("SELECT 1.25 AS value", empty()); fail(() -> floating.integer(0,"value"));
        fail(() -> db.execute("INSERT INTO items(name) VALUES (?)", empty()));
        fail(() -> db.query("SELECT nope FROM missing", empty()));
        fail(() -> db.query("SELECT 1 AS same,2 AS same", empty()));
        fail(() -> db.query("INSERT INTO items(name) VALUES ('bad alias snapshot') RETURNING id AS same,name AS same", empty()));
        fail(() -> db.query("INSERT INTO items(name) VALUES ('bad blob snapshot') RETURNING CAST(name AS BLOB) AS bytes", empty()));
        check(db.query("SELECT COUNT(*) AS n FROM items",empty()).integer(0,"n") == 2,
              "failed INSERT RETURNING snapshot must rollback the insertion");
        Batch batch = new Batch(); Parameters first = new Parameters(); first.text("must rollback");
        batch.add("INSERT INTO items(name) VALUES (?)", first);
        batch.add("INSERT INTO missing(name) VALUES (?)", first);
        fail(() -> db.batch(batch));
        check(db.query("SELECT COUNT(*) AS n FROM items",empty()).integer(0,"n") == 2, "batch rollback");
        Batch control = new Batch();
        control.add("INSERT INTO items(name) VALUES (?)", first);
        control.add("/* do not escape adapter transaction */ COMMIT", empty());
        fail(() -> db.batch(control));
        check(db.query("SELECT COUNT(*) AS n FROM items",empty()).integer(0,"n") == 2, "transaction control rollback");
        fail(() -> db.execute("-- control\nBEGIN",empty()));
        for (String prefix : new String[]{";", "\uFEFF", "; /* comment */ \uFEFF"}) {
            Batch prefixed = new Batch();
            prefixed.add("INSERT INTO items(name) VALUES (?)", first);
            prefixed.add(prefix + "COMMIT", empty());
            fail(() -> db.batch(prefixed));
            check(db.query("SELECT COUNT(*) AS n FROM items",empty()).integer(0,"n") == 2,
                  "prefixed transaction control must not commit earlier writes");
            fail(() -> db.execute(prefix + "BEGIN",empty()));
        }
        Batch committed = new Batch(); Parameters next = new Parameters(); next.text("committed");
        committed.add("INSERT INTO items(name) VALUES (?)",next); next.text("late mutation ignored");
        check(db.batch(committed) == 1, "snapshot batch parameters");
        check(new Database(args[0]).query("SELECT COUNT(*) AS n FROM items",empty()).integer(0,"n") == 3, "committed persistence");
        db.execute("CREATE TABLE child(parent INTEGER REFERENCES items(id))",empty());
        fail(() -> db.execute("INSERT INTO child(parent) VALUES (999)",empty()));
        fail(() -> new Database(":memory:"));
        // Failed operations left no transaction/statement/connection locks behind.
        db.execute("INSERT INTO items(name) VALUES ('after failure')",empty());
        check(Path.of(args[0]).toFile().isFile(), "real SQLite file");
        System.out.println("SQLite adapter: persistence/binding/null/typing/rollback/resources PASS");
    }
}
