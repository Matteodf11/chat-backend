# Local Setup

## Requirements

- JDK 21
- Git
- PostgreSQL 18
- Maven Wrapper

## PostgreSQL

The backend expects this local database by default:

```text
Database: chat_mattu
User: chat_mattu
Password: chat_mattu
Port: 5432
```

Create the user and database from a local terminal:

```powershell
psql -U postgres -h localhost -p 5432 -c "CREATE USER chat_mattu WITH PASSWORD 'chat_mattu';"
psql -U postgres -h localhost -p 5432 -c "CREATE DATABASE chat_mattu OWNER chat_mattu;"
```

If your PostgreSQL admin user has a password, `psql` will ask for it in your terminal.

Verify the connection:

```powershell
$env:PGPASSWORD='chat_mattu'
psql -U chat_mattu -h localhost -p 5432 -d chat_mattu -c "SELECT current_database(), current_user;"
```

Run the backend:

```powershell
.\mvnw.cmd spring-boot:run
```

Before starting, export JWT_SECRET and MESSAGE_ENCRYPTION_KEY into the process
environment. The Maven wrapper does not automatically load a .env file.
Use the existing encryption key if the database already contains encrypted messages.
Private application.properties/application-local.properties are preserved on disk
but excluded from Maven resources: they must not leak into the JAR or override
YAML. Supply credentials via process environment. For IDE launches, also check
the IDE does not copy these private files into its own runtime classpath.

An empty PostgreSQL database is provisioned by B14__complete_chat_schema.sql.
Existing databases with Flyway history continue using the original V migrations.
Non-empty databases without Flyway history are intentionally rejected: do not
enable baseline-on-migrate or use baseline/repair without inspecting the actual
schema and taking a verified backup. See postgres-migrations.md.

Run all tests (no Docker, Supabase account or production credentials required):

```powershell
.\mvnw.cmd verify
```

The first run downloads test-only PostgreSQL 18.3 binaries. Tests start a temporary
loopback-only instance on a random port and shut it down afterwards.
