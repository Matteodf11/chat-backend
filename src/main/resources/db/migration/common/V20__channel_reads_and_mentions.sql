CREATE TABLE channel_reads (
    user_id UUID NOT NULL REFERENCES users(id),
    channel_id UUID NOT NULL REFERENCES channels(id),
    read_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, channel_id)
);
CREATE TABLE message_mentions (
    message_id UUID NOT NULL REFERENCES messages(id),
    user_id UUID NOT NULL REFERENCES users(id),
    PRIMARY KEY (message_id, user_id)
);
CREATE INDEX idx_channel_unread ON messages(channel_id, created_at, sender_id);
CREATE INDEX idx_mentions_user ON message_mentions(user_id, message_id);

-- Reading history was not tracked before this release. Start existing memberships
-- at rollout instead of presenting every old message as newly unread.
INSERT INTO channel_reads(user_id, channel_id, read_at)
SELECT sm.user_id, c.id, CURRENT_TIMESTAMP
FROM server_members sm JOIN channels c ON c.server_id = sm.server_id
JOIN servers s ON s.id = sm.server_id
WHERE sm.deleted_at IS NULL AND c.deleted_at IS NULL AND s.deleted_at IS NULL;
