package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.Channel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChannelRepository extends JpaRepository<Channel, UUID> {
    @Query("SELECT c FROM Channel c WHERE c.server.id = :serverId AND c.deletedAt IS NULL")
    Page<Channel> findByServerId(UUID serverId, Pageable pageable);

    @Query("SELECT c FROM Channel c WHERE c.server.id = :serverId AND c.deletedAt IS NULL")
    List<Channel> findByServerId(UUID serverId);

    @Query("SELECT c FROM Channel c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Channel> findActiveById(UUID id);

    @Modifying
    @Query("UPDATE Channel c SET c.deletedAt = CURRENT_TIMESTAMP WHERE c.server.id = :serverId")
    void softDeleteByServerId(UUID serverId);

    @Modifying
    @Query("UPDATE Channel c SET c.deletedAt = CURRENT_TIMESTAMP WHERE c.id = :id")
    void softDeleteById(UUID id);
}
