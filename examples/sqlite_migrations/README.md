# SQLite migrations

This example uses the experimental `@sqlite/migrations.spr` module. Migration
files are UTF-8 files named `NNN_description.sql`, applied in lexicographic
filename order. Names and three-digit prefixes must be unique and should be
immutable after deployment. Sprig stores applied filenames in
`sprig_schema_migrations`; applying an already recorded migration is a no-op.
Before it runs anything, the runner checks the whole directory against that
ledger: a misnamed entry, a repeated prefix, an applied migration that sorts
after a pending one or an unreadable pending file is an error, and no migration
runs. Each SQL script and its ledger insert run in one SQLite transaction, so a
failed script can be fixed and retried; the migrations before it stay applied.

```sh
sprig resolve
sprig run --offline -- /tmp/demo.sqlite migrations
sprig run --offline -- /tmp/demo.sqlite migrations # prints applied=0
```

Migration SQL is trusted project code. Do not put transaction-control
statements (`BEGIN`, `COMMIT`, `ROLLBACK`, `SAVEPOINT`, `RELEASE`, or `END`) in
migrations: the runner owns the transaction. SQL identifiers and migration
contents are not sandboxed. This first version records names only; it does not
detect edits to files already applied.
