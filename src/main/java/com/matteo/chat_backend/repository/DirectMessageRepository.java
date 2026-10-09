package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.DirectMessage;
import com.matteo.chat_backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {
    @Query("SELECT dm FROM DirectMessage dm WHERE (dm.user1 = :user OR dm.user2 = :user) AND dm.deletedAt IS NULL")
    Page<DirectMessage> findAllByUser(User user, Pageable pageable);

    @Query("SELECT dm FROM DirectMessage dm WHERE dm.user1 = :user1 AND dm.user2 = :user2 AND dm.deletedAt IS NULL")
    Optional<DirectMessage> findByUser1AndUser2(User user1, User user2);

    @Query("SELECT dm FROM DirectMessage dm WHERE dm.user2 = :user1 AND dm.user1 = :user2 AND dm.deletedAt IS NULL")
    Optional<DirectMessage> findByUser2AndUser1(User user1, User user2);

    @Query("SELECT dm FROM DirectMessage dm WHERE dm.user1 = :user1 AND dm.deletedAt IS NULL")
    Page<DirectMessage> findByUser1(User user1, Pageable pageable);

    @Query("SELECT dm FROM DirectMessage dm WHERE dm.user1 = :user1 AND dm.deletedAt IS NULL")
    List<DirectMessage> findByUser1(User user1);

    @Query("SELECT dm FROM DirectMessage dm WHERE dm.user2 = :user2 AND dm.deletedAt IS NULL")
    Page<DirectMessage> findByUser2(User user2, Pageable pageable);

    @Query("SELECT dm FROM DirectMessage dm WHERE dm.user2 = :user2 AND dm.deletedAt IS NULL")
    List<DirectMessage> findByUser2(User user2);

    @Modifying
    @Query("UPDATE DirectMessage dm SET dm.deletedAt = CURRENT_TIMESTAMP WHERE dm.id = :id")
    void softDeleteById(UUID id);

    @Query("SELECT dm FROM DirectMessage dm WHERE dm.id = :id AND dm.deletedAt IS NULL")
    Optional<DirectMessage> findActiveById(UUID id);
}
