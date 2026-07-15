package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByChannelIdAndCreatedAtBeforeOrderByCreatedAtDesc(UUID channelId, LocalDateTime before, Pageable pageable);
    List<Message> findByDirectMessageIdAndCreatedAtBeforeOrderByCreatedAtDesc(UUID dmId, LocalDateTime before, Pageable pageable);
    List<Message> findByChannelIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID channelId, LocalDateTime after, Pageable pageable);
    List<Message> findByDirectMessageIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID dmId, LocalDateTime after, Pageable pageable);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.directMessage.id = :dmId " +
            "AND m.sender.id <> :userId AND m.createdAt > :lastReadAt")
    long countUnreadDmMessages(
            @Param("dmId") UUID dmId,
            @Param("userId") UUID userId,
            @Param("lastReadAt") LocalDateTime lastReadAt
    );

    @Modifying
    @Query("DELETE FROM Message m WHERE m.channel.id IN :channelIds")
    void deleteByChannelIds(@Param("channelIds") List<UUID> channelIds);
}
