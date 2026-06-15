# Architecture

Chat Mattu is a Discord-style chat application with a Spring Boot backend, PostgreSQL persistence, and future Android and desktop clients.

## Backend

- Java 21
- Spring Boot
- PostgreSQL
- Flyway for database migrations
- JPA for persistence
- WebSocket support for realtime features

## Environments

- `local`: development against local PostgreSQL.
- `prod`: deployed backend connected to Supabase PostgreSQL.
- `test`: in-memory H2 database for fast backend tests.

## Planned Modules

- `auth`: registration, login, tokens, and account security.
- `users`: user profiles and account data.
- `guilds`: Discord-style servers.
- `channels`: text and future voice channels.
- `messages`: message persistence and history.
- `realtime`: WebSocket events such as messages, presence, and typing.
- `common`: shared API contracts, errors, and utilities.
