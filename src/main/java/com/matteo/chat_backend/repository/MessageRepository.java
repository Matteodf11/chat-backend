package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByChannelIdOrderByCreatedAtAsc(UUID channelId);
    List<Message> findByDirectMessageIdOrderByCreatedAtAsc(UUID dmId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM Message m WHERE m.channel.id IN :channelIds")
    void deleteByChannelIds(@Param("channelIds") List<UUID> channelIds);
}
