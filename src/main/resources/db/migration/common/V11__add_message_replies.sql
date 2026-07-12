ALTER TABLE messages ADD COLUMN reply_to_id UUID;

ALTER TABLE messages
    ADD CONSTRAINT fk_messages_reply_to
    FOREIGN KEY (reply_to_id) REFERENCES messages(id) ON DELETE SET NULL;

CREATE INDEX idx_messages_reply_to_id ON messages(reply_to_id);
