package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ChannelRepository extends JpaRepository<Channel, UUID> {
    List<Channel> findByServerId(UUID serverId);
}