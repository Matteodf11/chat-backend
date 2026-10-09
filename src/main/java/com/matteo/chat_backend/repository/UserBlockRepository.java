package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.*;

public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {
    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
    @Query("SELECT COUNT(b) > 0 FROM UserBlock b WHERE (b.blocker.id = :one AND b.blocked.id = :two) OR (b.blocker.id = :two AND b.blocked.id = :one)")
    boolean existsBetween(UUID one, UUID two);
    List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(UUID blockerId);
    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
}
