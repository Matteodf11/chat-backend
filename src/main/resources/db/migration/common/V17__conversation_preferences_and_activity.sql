ALTER TABLE direct_messages ADD COLUMN last_activity_at TIMESTAMP;
ALTER TABLE direct_messages ADD COLUMN user1_pinned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE direct_messages ADD COLUMN user2_pinned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE direct_messages ADD COLUMN user1_muted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE direct_messages ADD COLUMN user2_muted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE direct_messages ADD COLUMN user1_archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE direct_messages ADD COLUMN user2_archived BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE direct_messages dm SET last_activity_at = COALESCE(
    (SELECT MAX(m.created_at) FROM messages m WHERE m.dm_id = dm.id), dm.created_at);
CREATE INDEX idx_dm_activity ON direct_messages(last_activity_at DESC, id);
