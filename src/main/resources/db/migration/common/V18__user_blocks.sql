CREATE TABLE user_blocks (
    id UUID PRIMARY KEY,
    blocker_id UUID NOT NULL REFERENCES users(id),
    blocked_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_user_block UNIQUE (blocker_id, blocked_id),
    CONSTRAINT ck_user_block_not_self CHECK (blocker_id <> blocked_id)
);
CREATE INDEX idx_user_blocks_blocked ON user_blocks(blocked_id);
