# Chat Mattu Backend

Backend for a Discord-style chat application built with Java 21 and Spring Boot.

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

## Run Locally

Start PostgreSQL with Docker if Docker is available:

```powershell
docker compose up -d
```

Or create the local database in your installed PostgreSQL:

```powershell
psql -U postgres -h localhost -p 5432 -c "CREATE USER chat_mattu WITH PASSWORD 'change_me';"
psql -U postgres -h localhost -p 5432 -c "CREATE DATABASE chat_mattu OWNER chat_mattu;"
```

Run the backend:

```powershell
.\mvnw.cmd spring-boot:run
```

Check the API:

```text
GET http://localhost:8080/api/status
GET http://localhost:8080/actuator/health
```

## Configuration

Copy `.env.example` to `.env` for local secrets. Do not commit real credentials.

Production will use the `prod` Spring profile and Supabase PostgreSQL.
