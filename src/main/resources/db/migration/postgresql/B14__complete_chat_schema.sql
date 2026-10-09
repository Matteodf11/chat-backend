-- Cumulative state at V14, used ONLY for a new, empty PostgreSQL database.
-- Existing Flyway histories continue to use the unchanged V migrations.
CREATE TABLE app_users (
    id UUID PRIMARY KEY, email VARCHAR(254) NOT NULL,
    username VARCHAR(32) NOT NULL, display_name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX ux_app_users_email_lower ON app_users (LOWER(email));
CREATE UNIQUE INDEX ux_app_users_username_lower ON app_users (LOWER(username));

CREATE TABLE users (
    id UUID PRIMARY KEY, username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE, password_hash VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(255), profile_banner_url VARCHAR(500), status VARCHAR(255),
    profile_color VARCHAR(20) DEFAULT '#7C5CFF', is_online BOOLEAN,
    last_seen TIMESTAMP, created_at TIMESTAMP,
    token_version INTEGER NOT NULL DEFAULT 0, deleted_at TIMESTAMP
);
CREATE TABLE servers (
    id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL, description VARCHAR(255),
    icon_url VARCHAR(255), owner_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP, deleted_at TIMESTAMP
);
CREATE TABLE channels (
    id UUID PRIMARY KEY, server_id UUID NOT NULL REFERENCES servers(id),
    name VARCHAR(100) NOT NULL, type VARCHAR(20),
    created_at TIMESTAMP, deleted_at TIMESTAMP
);
CREATE TABLE server_members (
    id UUID PRIMARY KEY, server_id UUID NOT NULL REFERENCES servers(id),
    user_id UUID NOT NULL REFERENCES users(id), role VARCHAR(20),
    joined_at TIMESTAMP, deleted_at TIMESTAMP
);
CREATE TABLE direct_messages (
    id UUID PRIMARY KEY, user1_id UUID NOT NULL REFERENCES users(id),
    user2_id UUID NOT NULL REFERENCES users(id), created_at TIMESTAMP,
    user1_last_read_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user2_last_read_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);
CREATE TABLE friendships (
    id UUID PRIMARY KEY, requester_id UUID NOT NULL REFERENCES users(id),
    receiver_id UUID NOT NULL REFERENCES users(id), status VARCHAR(20),
    created_at TIMESTAMP, deleted_at TIMESTAMP
);
CREATE TABLE messages (
    id UUID PRIMARY KEY, sender_id UUID NOT NULL REFERENCES users(id),
    channel_id UUID REFERENCES channels(id), dm_id UUID REFERENCES direct_messages(id),
    reply_to_id UUID CONSTRAINT fk_messages_reply_to REFERENCES messages(id) ON DELETE SET NULL,
    content TEXT NOT NULL, attachment_key VARCHAR(500), attachment_name VARCHAR(255),
    attachment_content_type VARCHAR(150), attachment_size BIGINT,
    edited BOOLEAN, created_at TIMESTAMP, deleted_at TIMESTAMP
);
CREATE INDEX idx_messages_reply_to_id ON messages(reply_to_id);
CREATE TABLE server_invitations (
    id UUID PRIMARY KEY, server_id UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    inviter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invitee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'pending', created_at TIMESTAMP,
    responded_at TIMESTAMP, deleted_at TIMESTAMP
);
CREATE INDEX idx_server_invitations_invitee_status ON server_invitations(invitee_id, status);
CREATE INDEX idx_server_invitations_server_invitee_status ON server_invitations(server_id, invitee_id, status);
CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE, device_name VARCHAR(120) NOT NULL,
    persistent BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP NOT NULL, expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP, deleted_at TIMESTAMP
);
CREATE INDEX idx_auth_sessions_user ON auth_sessions(user_id);
CREATE INDEX idx_auth_sessions_token_hash ON auth_sessions(token_hash);

-- Match V8/V10/V14, including Supabase API-role restrictions when present.
DO $$
DECLARE app_table TEXT;
BEGIN
    FOREACH app_table IN ARRAY ARRAY[
        'app_users', 'users', 'servers', 'channels', 'server_members',
        'direct_messages', 'friendships', 'messages', 'server_invitations', 'auth_sessions'
    ] LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', app_table);
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
            EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.%I FROM anon', app_table);
        END IF;
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
            EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.%I FROM authenticated', app_table);
        END IF;
        IF app_table <> 'app_users' THEN
            EXECUTE format('CREATE INDEX %I ON public.%I(deleted_at) WHERE deleted_at IS NOT NULL',
                'idx_' || app_table || '_deleted_at', app_table);
        END IF;
    END LOOP;
END $$;
