# PostgreSQL migrations

## Two paths, same application schema

- Empty database: Flyway runs the cumulative PostgreSQL B14 snapshot. This creates
  the nine current entity tables plus legacy app_users, foreign keys, indexes,
  RLS and the restrictions on Supabase anon/authenticated roles.
- Existing Flyway history: B14 is ignored; the original V1-V14 files are unchanged.
  An installation at V13 runs V14 normally.
- Non-empty database without history: startup fails safely. Automatic baselining
  is disabled in both local and prod profiles. Never baseline a live database
  based only on this project's test fixture.

This uses [Flyway baseline migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/baseline-migrations),
not an automatic baseline command and not a schema reset.

## Verification

`./mvnw verify` starts real PostgreSQL 18.3 using a test-only embedded-postgres
dependency. It never reads DB_URL/DB_PASSWORD and never connects to Supabase.
The tests cover an empty install, JPA schema validation for all entities, storing
and reading a message longer than 255 characters, upgrades from a known legacy
fixture, message preservation, owner-role normalization, RLS/API privileges,
repeat migration and rejection of unknown non-empty schemas.

The fixture models the historical core schema; it is NOT a dump of production.
It cannot prove that the actual Supabase schema has no manual drift.
CI runs the same tests on every push and pull request; production builds are
unchanged. Do not confuse a local passing run with a completed production rollout.

## Production gate (pending)

1. Take a database backup and verify restoration into a disposable database.
2. Export schema-only DDL and flyway_schema_history from Supabase securely.
   Do not publish connection strings, secrets, user data or password hashes.
3. Compare deployed columns, constraints and applied migration checksums with
   the repository. Inspect effective Flyway properties: ignored .properties
   files or environment variables may override YAML.
4. Restore the backup into an isolated PostgreSQL and run migrate + validate.
   Check login, refresh, existing/new messages, DM, server membership and files.
5. Schedule deployment only after these checks. Never run clean, blanket repair,
   baseline or ddl-auto=create/update against the live database.

The snapshot assumes the public schema, as do the existing security migrations.
Future schema changes must use a new versioned migration, not edit V1-V14/B14.
