ALTER TABLE messages ADD COLUMN attachment_key VARCHAR(500);
ALTER TABLE messages ADD COLUMN attachment_name VARCHAR(255);
ALTER TABLE messages ADD COLUMN attachment_content_type VARCHAR(150);
ALTER TABLE messages ADD COLUMN attachment_size BIGINT;
