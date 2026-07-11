CREATE TABLE IF NOT EXISTS server_invitations (
    id UUID PRIMARY KEY,
    server_id UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    inviter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invitee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP,
    responded_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_server_invitations_invitee_status
    ON server_invitations(invitee_id, status);

CREATE INDEX IF NOT EXISTS idx_server_invitations_server_invitee_status
    ON server_invitations(server_id, invitee_id, status);
