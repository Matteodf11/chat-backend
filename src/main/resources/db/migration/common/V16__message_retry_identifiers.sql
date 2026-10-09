ALTER TABLE messages ADD COLUMN client_message_id UUID;
CREATE UNIQUE INDEX uq_messages_sender_client_id ON messages(sender_id, client_message_id);
