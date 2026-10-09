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
Ignored application.properties/application-local.properties may also override YAML;
review these local files without committing their credentials.

The checked-in Flyway history does not yet create all core tables in a fresh
database. It requires an existing compatible schema; do not assume the commands
above provision a working empty database. See restart-audit-2026-10-09.md before
changing or deploying migrations.
