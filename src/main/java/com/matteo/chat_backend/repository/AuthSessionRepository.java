package com.matteo.chat_backend.repository;

import com.matteo.chat_backend.entity.AuthSession;
import com.matteo.chat_backend.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AuthSession a WHERE a.tokenHash = :tokenHash AND a.deletedAt IS NULL")
    Optional<AuthSession> findByTokenHash(String tokenHash);

    @Query("SELECT a FROM AuthSession a WHERE a.user = :user AND a.revokedAt IS NULL AND a.deletedAt IS NULL ORDER BY a.lastUsedAt DESC")
    List<AuthSession> findByUserAndRevokedAtIsNullOrderByLastUsedAtDesc(User user);

    @Modifying
    @Query("UPDATE AuthSession a SET a.deletedAt = CURRENT_TIMESTAMP WHERE a.id = :id")
    void softDeleteById(UUID id);

    @Query("SELECT a FROM AuthSession a WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<AuthSession> findActiveById(UUID id);
}
