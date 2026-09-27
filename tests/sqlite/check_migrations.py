#!/usr/bin/env python3
"""Integration checks for Sprig-owned SQLite migration policy and JDBC atomicity."""
from pathlib import Path
import os
import shutil
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
    print('SQLite migrations: order, idempotence, quoted semicolons/triggers, rollback/retry, filename validation and transaction ownership passed')


if __name__ == '__main__':
    main()
