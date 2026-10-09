-- V14: Add soft delete (deletedAt) to all main tables
-- Permite borrado lógico en lugar de físico para auditoría, recuperación, GDPR

-- Users
ALTER TABLE users
    ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_users_deleted_at ON users(deleted_at) WHERE deleted_at IS NOT NULL;

-- Servers
ALTER TABLE servers
    ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_servers_deleted_at ON servers(deleted_at) WHERE deleted_at IS NOT NULL;

-- Channels
ALTER TABLE channels
    ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_channels_deleted_at ON channels(deleted_at) WHERE deleted_at IS NOT NULL;

-- Server Members
ALTER TABLE server_members
    ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_server_members_deleted_at ON server_members(deleted_at) WHERE deleted_at IS NOT NULL;

-- Direct Messages
ALTER TABLE direct_messages
    ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_direct_messages_deleted_at ON direct_messages(deleted_at) WHERE deleted_at IS NOT NULL;

-- Messages
ALTER TABLE messages
    ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_messages_deleted_at ON messages(deleted_at) WHERE deleted_at IS NOT NULL;

-- Friendships
ALTER TABLE friendships
    ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_friendships_deleted_at ON friendships(deleted_at) WHERE deleted_at IS NOT NULL;

-- Server Invitations
ALTER TABLE server_invitations
    ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_server_invitations_deleted_at ON server_invitations(deleted_at) WHERE deleted_at IS NOT NULL;

-- Auth Sessions (ya tienen revoked_at que funciona similar, pero añadimos deleted_at para consistencia)
ALTER TABLE auth_sessions
    ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_auth_sessions_deleted_at ON auth_sessions(deleted_at) WHERE deleted_at IS NOT NULL;