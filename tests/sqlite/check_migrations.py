#!/usr/bin/env python3
"""Integration checks for Sprig-owned SQLite migration policy and JDBC atomicity."""
from contextlib import closing
from pathlib import Path
import os
import shutil
import sqlite3
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
CLI = ROOT / 'bin' / ('sprig.cmd' if os.name == 'nt' else 'sprig')


def run(project, *args, success=True):
    result = subprocess.run([str(CLI), *map(str, args)], cwd=project, text=True,
                            encoding='utf-8', capture_output=True, timeout=120)
    if success and result.returncode:
        raise AssertionError((args, result.stdout, result.stderr))
    if not success and result.returncode == 0:
        raise AssertionError((args, 'expected failure', result.stdout, result.stderr))
    return result


def database_state(path):
    """The ledger rows and the user tables of a database file, read by Python's own SQLite."""
    connection = sqlite3.connect(path)
    try:
        tables = [row[0] for row in connection.execute(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name <> 'sprig_schema_migrations' ORDER BY name")]
        ledger = []
        if connection.execute("SELECT COUNT(*) FROM sqlite_master WHERE name = 'sprig_schema_migrations'").fetchone()[0]:
            ledger = [row[0] for row in connection.execute('SELECT name FROM sprig_schema_migrations ORDER BY name')]
        return ledger, tables
    finally:
        connection.close()


def rejected_before_running(project, root, name, files, expected):
    """A directory apply() must refuse as a whole: no migration in it may run."""
    directory = project / name
    directory.mkdir()
    for file_name, content in files.items():
        (directory / file_name).write_bytes(content if isinstance(content, bytes) else content.encode('utf-8'))
    database = root / (name + '.sqlite')
    failed = run(project, 'run', '--offline', '--', database, directory, success=False)
    assert expected in failed.stderr, (name, failed.stderr)
    assert database_state(database) == ([], []), (name, database_state(database))


def main():
    with tempfile.TemporaryDirectory(prefix='sprig migration integration ') as temp:
        root = Path(temp)
        shutil.copytree(ROOT / 'libraries/sprig-sqlite', root / 'libraries/sprig-sqlite')
        project = root / 'examples/sqlite_migrations'
        project.parent.mkdir(parents=True)
        shutil.copytree(ROOT / 'examples/sqlite_migrations', project,
                        ignore=shutil.ignore_patterns('sprig.lock', '*.sqlite', '*.sqlite-*', 'sprig-build'))
        run(project, 'resolve', '--offline')
        checked = run(project, 'check', '--offline', '--json')
        assert '"diagnostics": [  ]' in checked.stdout

        db = root / 'happy path.sqlite'
        first = run(project, 'run', '--offline', '--', db, project / 'migrations')
        assert 'applied=2' in first.stdout
        assert first.stdout.index('created by migration 001') < first.stdout.index('created by migration 002')
        repeat = run(project, 'run', '--offline', '--', db, project / 'migrations')
        assert 'applied=0' in repeat.stdout
        assert repeat.stdout.count('created by migration 001') == 1
        assert repeat.stdout.count('created by migration 002') == 1

        migration_dir = project / 'retry migrations'
        migration_dir.mkdir()
        (migration_dir / '001_initial.sql').write_text(
            "CREATE TABLE messages (id INTEGER PRIMARY KEY, message TEXT NOT NULL);\n"
            "CREATE TABLE audit (message TEXT NOT NULL);\n"
            "CREATE TRIGGER message_audit AFTER INSERT ON messages BEGIN\n"
            "  INSERT INTO audit(message) VALUES (CASE WHEN NEW.message = 'first; still quoted' THEN 'trigger; ok' ELSE NEW.message END);\n"
            "END;\n"
            "INSERT INTO messages(message) VALUES ('first; still quoted');\n", encoding='utf-8')
        (migration_dir / '002_retry.sql').write_text(
            "INSERT INTO messages(message) VALUES ('must roll back');\n"
            "INSERT INTO missing_table(message) VALUES ('failure');\n", encoding='utf-8')
        retry_db = root / 'retry.sqlite'
        failed = run(project, 'run', '--offline', '--', retry_db, migration_dir, success=False)
        assert 'SQLite transaction failed' in failed.stderr or 'no such table' in failed.stderr
        (migration_dir / '002_retry.sql').write_text(
            "INSERT INTO messages(message) VALUES ('retry succeeded');\n", encoding='utf-8')
        retried = run(project, 'run', '--offline', '--', retry_db, migration_dir)
        assert 'applied=1' in retried.stdout
        assert 'first; still quoted' in retried.stdout
        assert 'retry succeeded' in retried.stdout
        assert 'must roll back' not in retried.stdout
        assert retried.stdout.index('first; still quoted') < retried.stdout.index('retry succeeded')

        # As SQLite's own sqlite3_complete decides, a trigger body ends only at an END
        # that is the first token after a semicolon: neither the END of a CASE nor a
        # column named end ends it, and an END in a comment or quotes is no token.
        triggers = project / 'trigger bodies'
        triggers.mkdir()
        (triggers / '001_events.sql').write_text(
            "CREATE TABLE messages (id INTEGER PRIMARY KEY, message TEXT NOT NULL);\n"
            "CREATE TABLE events (id INTEGER PRIMARY KEY, start INTEGER NOT NULL, end INTEGER);\n"
            "CREATE TRIGGER events_end AFTER INSERT ON events BEGIN\n"
            "  UPDATE events SET end = NEW.start + 5 WHERE id = NEW.id AND end IS NULL;\n"
            "  INSERT INTO messages(message) VALUES ('event ' || NEW.id || ' ends at ' || (SELECT end FROM events WHERE id = NEW.id));\n"
            "  -- the body ends at the next END; not at this one\n"
            "end;\n"
            "CREATE TRIGGER events_label AFTER INSERT ON events BEGIN\n"
            "  INSERT INTO messages(message) VALUES (CASE WHEN NEW.end IS NULL THEN 'event ' || NEW.id || ' open; end later'\n"
            "    ELSE 'event ' || NEW.id || ' closed' END);\n"
            "END;\n"
            "INSERT INTO events(start) VALUES (10);\n"
            "INSERT INTO events(start, end) VALUES (20, 21);\n", encoding='utf-8')
        trigger_db = root / 'triggers.sqlite'
        trigger_run = run(project, 'run', '--offline', '--', trigger_db, triggers)
        lines = trigger_run.stdout.splitlines()
        assert lines[0] == 'applied=1', trigger_run.stdout
        # Both triggers fire for each row; sorted, since SQLite picks their order.
        assert sorted(lines[1:]) == ['event 1 ends at 15', 'event 1 open; end later',
                                     'event 2 closed', 'event 2 ends at 21'], trigger_run.stdout
        with closing(sqlite3.connect(trigger_db)) as connection:
            assert connection.execute('SELECT id, start, end FROM events ORDER BY id').fetchall() == [(1, 10, 15), (2, 20, 21)]

        malformed = project / 'bad names'
        malformed.mkdir()
        (malformed / "001_bad'; DROP TABLE messages;--.sql").write_text(
            "CREATE TABLE ignored (id INTEGER);", encoding='utf-8')
        invalid = run(project, 'run', '--offline', '--', root / 'invalid.sqlite', malformed, success=False)
        assert 'invalid migration filename' in invalid.stderr

        transaction = project / 'transaction control'
        transaction.mkdir()
        (transaction / '001_commit.sql').write_text(
            "CREATE TABLE should_rollback (id INTEGER); COMMIT;", encoding='utf-8')
        blocked = run(project, 'run', '--offline', '--', root / 'tx.sqlite', transaction, success=False)
        assert 'transaction control' in blocked.stderr

        drift = project / 'late migration'
        drift.mkdir()
        (drift / '002_existing.sql').write_text(
            "CREATE TABLE messages (id INTEGER PRIMARY KEY, message TEXT NOT NULL);\n"
            "INSERT INTO messages(message) VALUES ('already applied');\n", encoding='utf-8')
        drift_db = root / 'drift.sqlite'
        run(project, 'run', '--offline', '--', drift_db, drift)
        (drift / '001_added_late.sql').write_text(
            "INSERT INTO messages(message) VALUES ('late file');\n", encoding='utf-8')
        changed_order = run(project, 'run', '--offline', '--', drift_db, drift, success=False)
        assert 'migration order changed' in changed_order.stderr
        assert '002_existing.sql' in changed_order.stderr and '001_added_late.sql' in changed_order.stderr
        # The pending file that sorts first must not run before the order error is found.
        assert database_state(drift_db) == (['002_existing.sql'], ['messages']), database_state(drift_db)
        # closing(): a sqlite3 connection's own with block commits but stays open,
        # and Windows cannot remove a database file that is still open.
        with closing(sqlite3.connect(drift_db)) as connection:
            assert connection.execute('SELECT message FROM messages ORDER BY id').fetchall() == [('already applied',)]

        # The whole directory is checked before any migration runs, so a bad
        # entry after a pending migration leaves the database untouched.
        rejected_before_running(project, root, 'pending then readme', {
            '001_create.sql': 'CREATE TABLE created (id INTEGER);\n',
            'README.md': 'notes about the migrations\n',
        }, 'invalid migration filename: README.md')
        rejected_before_running(project, root, 'pending then duplicate', {
            '001_create.sql': 'CREATE TABLE created (id INTEGER);\n',
            '002_first.sql': 'CREATE TABLE first (id INTEGER);\n',
            '002_second.sql': 'CREATE TABLE second (id INTEGER);\n',
        }, 'duplicate migration sequence: 002')
        rejected_before_running(project, root, 'pending then unreadable', {
            '001_create.sql': 'CREATE TABLE created (id INTEGER);\n',
            '002_bytes.sql': b'CREATE TABLE bytes (id INTEGER); -- \xff\n',
        }, '002_bytes.sql')

        # A trailing comment or an empty statement after the last one is not a statement.
        trailing = project / 'trailing comment'
        trailing.mkdir()
        (trailing / '001_trailing.sql').write_text(
            "CREATE TABLE messages (id INTEGER PRIMARY KEY, message TEXT NOT NULL);\n"
            "INSERT INTO messages(message) VALUES ('trailing comment ok'); ;\n"
            "-- a closing note; with a semicolon\n/* and a block comment */\n", encoding='utf-8')
        trailing_run = run(project, 'run', '--offline', '--', root / 'trailing.sqlite', trailing)
        assert 'applied=1' in trailing_run.stdout and 'trailing comment ok' in trailing_run.stdout, trailing_run.stdout
    print('SQLite migrations: order, idempotence, quoted semicolons, trigger bodies (CASE ... END, a column named end), '
          'trailing comments, rollback/retry, '
          'whole-directory validation before running and transaction ownership passed')


if __name__ == '__main__':
    main()
