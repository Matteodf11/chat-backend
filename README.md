# Chimera · Backend

Java 21 / Spring Boot backend for Chimera, a messaging application for Android
and Windows. Provides authentication with renewable sessions, private and server
messaging, roles, invitations, read state, mentions and media storage.

See the [Chimera client](https://github.com/Matteodf11/chat-app) for screenshots,
features and the demonstration guide. This repository contains the API,
WebSocket integration, database migrations and backend tests.

## Tech Stack

- Java 21
- Spring Boot
- PostgreSQL
- Flyway
- JPA
- WebSocket
- Maven

## Local Requirements

- JDK 21
- Maven Wrapper
- PostgreSQL 18 or Docker
- Git

## Run Locally (Windows)

The application does not automatically import `.env` into Spring's process
environment. Use the checked-in helper rather than assuming that copying an
example file configures the server.

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\scripts\create-local-env.ps1
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\scripts\run-local.ps1 -CheckOnly
```

The first command creates an ignored `.env` with fresh random JWT and encryption
keys and the local database defaults below. It refuses to overwrite an existing
file. Keep the encryption key with its database: replacing it prevents reading
content encrypted with the old key. `-CheckOnly` validates configuration without
starting the server or changing the shell environment.

Start PostgreSQL with Docker if Docker is available:

```powershell
docker compose up -d
```

Or create the local database in your installed PostgreSQL:

```powershell
psql -U postgres -h localhost -p 5432 -c "CREATE USER chat_mattu WITH PASSWORD 'chat_mattu';"
psql -U postgres -h localhost -p 5432 -c "CREATE DATABASE chat_mattu OWNER chat_mattu;"
```

These are local development defaults matching the generated `.env`. If you use
another database name, user or password, update `DB_URL`, `DB_USERNAME` and
`DB_PASSWORD` in that private file before starting the application.

Run the backend:

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\scripts\run-local.ps1
```

`RemoteSigned` applies only to that PowerShell process; these commands do not
change the machine's script policy. The helper imports literal values from `.env`, selects the `local` profile and
restores its environment changes when Maven exits. It only accepts a PostgreSQL
database on localhost/127.0.0.1. It does not execute `.env` content or print secrets.
Flyway applies the checked-in migrations; Hibernate validates the resulting
schema. Unknown pre-existing schemas must not be silently adopted.

These defaults support authentication and text messaging. Avatars and attachments
require a separate Supabase project with configured storage buckets. Set
`SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_AVATAR_BUCKET` and
`SUPABASE_ATTACHMENT_BUCKET` in the private `.env`. The service-role key belongs
only in the backend environment, never in the client or a demo archive.

The client's backend URL is currently configured in its `HttpClient.kt`; a local
server needs a client build pointing to that host. Existing APK/MSI demo packages
connect to the deployed Render service, not to the newly started local server.

Health endpoints are protected by the current security configuration. An
unauthenticated request is not an installation success check:

```text
GET http://localhost:8080/actuator/health
```

## Configuration

`.env.example` documents the variables. Do not commit real credentials.

Production uses the `prod` Spring profile and Supabase PostgreSQL. The local helper
is not a Render deployment command.

## Tests and demo scope

```powershell
.\mvnw.cmd test
```

The previous full run passed 57 tests, including real PostgreSQL migration
checks. Tests use isolated databases and do not need production credentials.
Client tests and packaged installation are separate checks.

Messages are encrypted by the backend at rest, not end-to-end. Password changes
with an authenticated session exist; email-based account recovery is not yet
implemented. Local notifications require an active client process/connection.
