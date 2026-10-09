package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    @Query("SELECT m FROM Message m WHERE m.directMessage.id = :dmId AND m.deletedAt IS NULL " +
            "AND (m.createdAt < :at OR (m.createdAt = :at AND m.id < :id)) ORDER BY m.createdAt DESC, m.id DESC")
    List<Message> findSharedCandidates(UUID dmId, LocalDateTime at, UUID id, Pageable pageable);
    Optional<Message> findBySenderIdAndClientMessageId(UUID senderId, UUID clientMessageId);

    @Query("SELECT m FROM Message m WHERE m.channel.id = :channelId AND m.deletedAt IS NULL AND m.createdAt < :before ORDER BY m.createdAt DESC")
    List<Message> findByChannelIdAndCreatedAtBeforeOrderByCreatedAtDesc(UUID channelId, LocalDateTime before, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.directMessage.id = :dmId AND m.deletedAt IS NULL AND m.createdAt < :before ORDER BY m.createdAt DESC")
    List<Message> findByDirectMessageIdAndCreatedAtBeforeOrderByCreatedAtDesc(UUID dmId, LocalDateTime before, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.channel.id = :channelId AND m.deletedAt IS NULL AND m.createdAt > :after ORDER BY m.createdAt ASC")
    List<Message> findByChannelIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID channelId, LocalDateTime after, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.directMessage.id = :dmId AND m.deletedAt IS NULL AND m.createdAt > :after ORDER BY m.createdAt ASC")
    List<Message> findByDirectMessageIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID dmId, LocalDateTime after, Pageable pageable);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.directMessage.id = :dmId " +
            "AND m.sender.id <> :userId AND m.createdAt > :lastReadAt AND m.deletedAt IS NULL")
    long countUnreadDmMessages(
            @Param("dmId") UUID dmId,
            @Param("userId") UUID userId,
            @Param("lastReadAt") LocalDateTime lastReadAt
    );

    @Modifying
    @Query("UPDATE Message m SET m.deletedAt = CURRENT_TIMESTAMP WHERE m.channel.id IN :channelIds")
    void softDeleteByChannelIds(@Param("channelIds") List<UUID> channelIds);

    @Modifying
    @Query("UPDATE Message m SET m.deletedAt = CURRENT_TIMESTAMP WHERE m.id = :id")
    void softDeleteById(UUID id);

    @Query("SELECT m FROM Message m WHERE m.id = :id AND m.deletedAt IS NULL")
    Optional<Message> findActiveById(UUID id);

    @Query("SELECT m FROM Message m WHERE m.channel.id = :channelId AND m.deletedAt IS NULL AND m.createdAt < :before ORDER BY m.createdAt DESC")
    List<Message> findByChannelIdBefore(UUID channelId, LocalDateTime before, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.directMessage.id = :dmId AND m.deletedAt IS NULL AND m.createdAt < :before ORDER BY m.createdAt DESC")
    List<Message> findByDirectMessageIdBefore(UUID dmId, LocalDateTime before, Pageable pageable);
}
