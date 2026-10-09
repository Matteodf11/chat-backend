-- Independent fixture of the core tables historically created outside Flyway.
-- A compatibility model, NOT a dump or proof of the live Supabase schema.
CREATE TABLE users (
 id UUID PRIMARY KEY, username VARCHAR(50) NOT NULL UNIQUE,
 email VARCHAR(255) NOT NULL UNIQUE, password_hash VARCHAR(255) NOT NULL,
 avatar_url VARCHAR(255), status VARCHAR(255), is_online BOOLEAN,
 last_seen TIMESTAMP, created_at TIMESTAMP
);
CREATE TABLE servers (
 id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL, description VARCHAR(255),
 icon_url VARCHAR(255), owner_id UUID NOT NULL REFERENCES users(id), created_at TIMESTAMP
);
CREATE TABLE channels (
 id UUID PRIMARY KEY, server_id UUID NOT NULL REFERENCES servers(id),
 name VARCHAR(100) NOT NULL, type VARCHAR(20), created_at TIMESTAMP
);
CREATE TABLE server_members (
 id UUID PRIMARY KEY, server_id UUID NOT NULL REFERENCES servers(id),
 user_id UUID NOT NULL REFERENCES users(id), role VARCHAR(20), joined_at TIMESTAMP
);
CREATE TABLE direct_messages (
 id UUID PRIMARY KEY, user1_id UUID NOT NULL REFERENCES users(id),
 user2_id UUID NOT NULL REFERENCES users(id), created_at TIMESTAMP
);
CREATE TABLE friendships (
 id UUID PRIMARY KEY, requester_id UUID NOT NULL REFERENCES users(id),
 receiver_id UUID NOT NULL REFERENCES users(id), status VARCHAR(20), created_at TIMESTAMP
);
CREATE TABLE messages (
 id UUID PRIMARY KEY, sender_id UUID NOT NULL REFERENCES users(id),
 channel_id UUID REFERENCES channels(id), dm_id UUID REFERENCES direct_messages(id),
 content VARCHAR(255) NOT NULL, edited BOOLEAN, created_at TIMESTAMP
);
