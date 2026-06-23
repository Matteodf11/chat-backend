package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByChannelIdOrderByCreatedAtAsc(UUID channelId);
    List<Message> findByDirectMessageIdOrderByCreatedAtAsc(UUID dmId, Pageable pageable);
}